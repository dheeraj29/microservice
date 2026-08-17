package com.da.demo.gateway.session;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import reactor.core.publisher.Mono;

@Service
public class DistributedSessionManager {

    private static final Logger log = LoggerFactory.getLogger(DistributedSessionManager.class);

    private static final Duration SESSION_TTL = Duration.ofMinutes(30);
    private static final Duration POINTER_TTL = Duration.ofSeconds(10);
    private static final Duration LOCK_TTL = Duration.ofSeconds(5);
    private static final Duration ARCHIVE_TTL = Duration.ofHours(1);

    private final ReactiveStringRedisTemplate redisTemplate;
    private final KeycloakAuthService keycloakAuthService;
    private final ObjectMapper objectMapper;

    public DistributedSessionManager(ReactiveStringRedisTemplate redisTemplate,
                                     KeycloakAuthService keycloakAuthService) {
        this.redisTemplate = redisTemplate;
        this.keycloakAuthService = keycloakAuthService;
        this.objectMapper = new ObjectMapper();
        this.objectMapper.registerModule(new JavaTimeModule());
        this.objectMapper.configure(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    }

    public Mono<SessionRecord> createSession(Map<String, Object> tokenPayload, String clientFingerprint) {
        String sessionId = "sid_" + UUID.randomUUID().toString();
        String accessToken = (String) tokenPayload.get("access_token");
        String refreshToken = (String) tokenPayload.get("refresh_token");
        Number expiresIn = (Number) tokenPayload.getOrDefault("expires_in", 300);

        SessionRecord session = new SessionRecord();
        session.setSessionId(sessionId);
        session.setAccessToken(accessToken);
        session.setRefreshToken(refreshToken);
        session.setAccessTokenExpiresAt(Instant.now().plusSeconds(expiresIn.longValue()));
        session.setUsername(keycloakAuthService.extractUsernameFromToken(accessToken));
        session.setRoles(keycloakAuthService.extractRolesFromToken(accessToken));
        session.setClientFingerprint(clientFingerprint);

        return saveSession(session, sessionId, SESSION_TTL).thenReturn(session);
    }

    public Mono<SessionResolutionResult> resolveSession(String incomingSessionId) {
        if (incomingSessionId == null || incomingSessionId.isBlank()) {
            return Mono.empty();
        }

        // 1. Check if incoming ID is a 10s forwarding pointer
        return redisTemplate.opsForValue().get("pointer:" + incomingSessionId)
                .flatMap(targetSessionId -> {
                    log.debug("Session ID {} is an in-flight pointer to {}", incomingSessionId, targetSessionId);
                    return loadAndExtendSession(targetSessionId)
                            .map(session -> new SessionResolutionResult(session, targetSessionId, true));
                })
                .switchIfEmpty(Mono.defer(() ->
                        loadAndExtendSession(incomingSessionId)
                                .map(session -> new SessionResolutionResult(session, incomingSessionId, false))
                ));
    }

    private Mono<SessionRecord> loadAndExtendSession(String sessionId) {
        return redisTemplate.opsForValue().get("session:" + sessionId)
                .flatMap(json -> {
                    try {
                        SessionRecord record = objectMapper.readValue(json, SessionRecord.class);
                        // Reset sliding window TTL in Valkey
                        return redisTemplate.expire("session:" + sessionId, SESSION_TTL)
                                .thenReturn(record);
                    } catch (Exception e) {
                        log.error("Failed to deserialize SessionRecord for {}: {}", sessionId, e.getMessage());
                        return Mono.empty();
                    }
                });
    }

    public Mono<SessionRecord> refreshAndRotateSession(SessionRecord currentSession) {
        String oldSid = currentSession.getSessionId();
        String lockKey = "lock:refresh:" + oldSid;
        String lockVal = UUID.randomUUID().toString();

        // 1. Attempt Distributed Lock in Valkey (SET lockKey lockVal NX PX 5000)
        return redisTemplate.opsForValue().setIfAbsent(lockKey, lockVal, LOCK_TTL)
                .flatMap(lockAcquired -> {
                    if (Boolean.TRUE.equals(lockAcquired)) {
                        log.info("Acquired distributed refresh lock for session {}", oldSid);
                        return executeRefreshAndRotation(currentSession, oldSid, lockKey);
                    } else {
                        // Concurrent request: Wait 80ms and resolve via pointer
                        log.info("Concurrent refresh in progress for {}. Waiting for thread handover...", oldSid);
                        return Mono.delay(Duration.ofMillis(80))
                                .flatMap(d -> resolveSession(oldSid))
                                .map(SessionResolutionResult::getSession);
                    }
                });
    }

    private Mono<SessionRecord> executeRefreshAndRotation(SessionRecord currentSession, String oldSid, String lockKey) {
        return keycloakAuthService.refreshAccessToken(currentSession.getRefreshToken())
                .flatMap(tokenResponse -> {
                    String newSid = "sid_" + UUID.randomUUID().toString();
                    String newAccessToken = (String) tokenResponse.get("access_token");
                    String newRefreshToken = (String) tokenResponse.get("refresh_token");
                    Number expiresIn = (Number) tokenResponse.getOrDefault("expires_in", 300);

                    SessionRecord newSession = new SessionRecord();
                    newSession.setSessionId(newSid);
                    newSession.setAccessToken(newAccessToken);
                    newSession.setRefreshToken(newRefreshToken);
                    newSession.setAccessTokenExpiresAt(Instant.now().plusSeconds(expiresIn.longValue()));
                    newSession.setUsername(currentSession.getUsername());
                    newSession.setRoles(keycloakAuthService.extractRolesFromToken(newAccessToken));
                    newSession.setClientFingerprint(currentSession.getClientFingerprint());
                    newSession.setRefreshSequence(currentSession.getRefreshSequence() + 1);

                    // 1. Save new session (30 min TTL)
                    // 2. Set 10s Forwarding Pointer from oldSid -> newSid
                    // 3. Keep 1hr archive of rotated SID for hijack detection
                    // 4. Delete old session & release lock
                    return saveSession(newSession, newSid, SESSION_TTL)
                            .then(redisTemplate.opsForValue().set("pointer:" + oldSid, newSid, POINTER_TTL))
                            .then(redisTemplate.opsForValue().set("revoked_archive:" + oldSid, "rotated", ARCHIVE_TTL))
                            .then(redisTemplate.delete("session:" + oldSid))
                            .then(redisTemplate.delete(lockKey))
                            .thenReturn(newSession);
                })
                .onErrorResume(err -> {
                    log.error("Failed to execute refresh for {}: {}", oldSid, err.getMessage());
                    return redisTemplate.delete(lockKey).then(Mono.error(err));
                });
    }

    public Mono<Void> handleSessionHijack(String compromisedSessionId, String refreshToken) {
        log.warn("🚨 SECURITY ALERT: Session hijacking / replay detected for ID {}", compromisedSessionId);
        return keycloakAuthService.revokeToken(refreshToken)
                .then(redisTemplate.delete("session:" + compromisedSessionId))
                .then(redisTemplate.delete("pointer:" + compromisedSessionId))
                .then();
    }

    public Mono<Void> destroySession(String sessionId, String refreshToken) {
        log.info("Clean session teardown for {}", sessionId);
        return redisTemplate.delete("session:" + sessionId)
                .then(redisTemplate.delete("pointer:" + sessionId))
                .then();
    }

    public Mono<Boolean> isRevokedArchive(String sessionId) {
        return redisTemplate.hasKey("revoked_archive:" + sessionId);
    }

    public static class PkceStateRecord {
        private String codeVerifier;
        private String targetUrl;

        public PkceStateRecord() {}

        public PkceStateRecord(String codeVerifier, String targetUrl) {
            this.codeVerifier = codeVerifier;
            this.targetUrl = targetUrl;
        }

        public String getCodeVerifier() { return codeVerifier; }
        public void setCodeVerifier(String codeVerifier) { this.codeVerifier = codeVerifier; }
        public String getTargetUrl() { return targetUrl; }
        public void setTargetUrl(String targetUrl) { this.targetUrl = targetUrl; }
    }

    public Mono<Boolean> savePkceState(String state, String codeVerifier, String targetUrl) {
        if (state == null || codeVerifier == null) {
            return Mono.just(false);
        }
        try {
            PkceStateRecord record = new PkceStateRecord(codeVerifier, targetUrl);
            String json = objectMapper.writeValueAsString(record);
            return redisTemplate.opsForValue().set("pkce:state:" + state, json, Duration.ofMinutes(5));
        } catch (Exception e) {
            log.error("Failed to serialize PKCE state {}: {}", state, e.getMessage());
            return Mono.just(false);
        }
    }

    public Mono<PkceStateRecord> consumePkceState(String state) {
        if (state == null || state.isBlank()) {
            return Mono.empty();
        }
        String key = "pkce:state:" + state;
        return redisTemplate.opsForValue().get(key)
                .flatMap(json -> redisTemplate.delete(key).thenReturn(json))
                .map(json -> {
                    try {
                        return objectMapper.readValue(json, PkceStateRecord.class);
                    } catch (Exception e) {
                        return new PkceStateRecord(json, null);
                    }
                });
    }

    private Mono<Boolean> saveSession(SessionRecord session, String sessionId, Duration ttl) {
        try {
            String json = objectMapper.writeValueAsString(session);
            return redisTemplate.opsForValue().set("session:" + sessionId, json, ttl);
        } catch (Exception e) {
            log.error("Failed to serialize session {}: {}", sessionId, e.getMessage());
            return Mono.just(false);
        }
    }

    public static class SessionResolutionResult {
        private final SessionRecord session;
        private final String effectiveSessionId;
        private final boolean wasRotated;

        public SessionResolutionResult(SessionRecord session, String effectiveSessionId, boolean wasRotated) {
            this.session = session;
            this.effectiveSessionId = effectiveSessionId;
            this.wasRotated = wasRotated;
        }

        public SessionRecord getSession() { return session; }
        public String getEffectiveSessionId() { return effectiveSessionId; }
        public boolean isWasRotated() { return wasRotated; }
    }
}

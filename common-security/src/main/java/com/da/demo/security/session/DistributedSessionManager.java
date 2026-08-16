package com.da.demo.security.session;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import com.da.demo.security.model.SessionRecord;
import com.da.demo.security.service.KeycloakAuthService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

@Service
public class DistributedSessionManager {

    private static final Logger log = LoggerFactory.getLogger(DistributedSessionManager.class);

    private static final Duration SESSION_TTL = Duration.ofMinutes(30);
    private static final Duration POINTER_TTL = Duration.ofSeconds(10);
    private static final Duration LOCK_TTL = Duration.ofSeconds(5);
    private static final Duration ARCHIVE_TTL = Duration.ofHours(1);

    private final StringRedisTemplate redisTemplate;
    private final KeycloakAuthService keycloakAuthService;
    private final ObjectMapper objectMapper;

    public DistributedSessionManager(StringRedisTemplate redisTemplate,
                                     KeycloakAuthService keycloakAuthService) {
        this.redisTemplate = redisTemplate;
        this.keycloakAuthService = keycloakAuthService;
        this.objectMapper = new ObjectMapper();
        this.objectMapper.registerModule(new JavaTimeModule());
        this.objectMapper.configure(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    }

    public SessionRecord createSession(Map<String, Object> tokenPayload, String clientFingerprint) {
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

        saveSession(session, sessionId, SESSION_TTL);
        return session;
    }

    public SessionResolutionResult resolveSession(String incomingSessionId) {
        if (incomingSessionId == null || incomingSessionId.isBlank()) {
            return null;
        }

        // 1. Check if incoming ID is a 10s forwarding pointer
        String targetSessionId = redisTemplate.opsForValue().get("pointer:" + incomingSessionId);
        if (targetSessionId != null && !targetSessionId.isBlank()) {
            log.debug("Session ID {} is an in-flight pointer to {}", incomingSessionId, targetSessionId);
            SessionRecord session = loadAndExtendSession(targetSessionId);
            if (session != null) {
                return new SessionResolutionResult(session, targetSessionId, true);
            }
        }

        SessionRecord session = loadAndExtendSession(incomingSessionId);
        if (session != null) {
            return new SessionResolutionResult(session, incomingSessionId, false);
        }

        return null;
    }

    private SessionRecord loadAndExtendSession(String sessionId) {
        String json = redisTemplate.opsForValue().get("session:" + sessionId);
        if (json == null || json.isBlank()) {
            return null;
        }

        try {
            SessionRecord record = objectMapper.readValue(json, SessionRecord.class);
            // Sliding window TTL in Valkey
            redisTemplate.expire("session:" + sessionId, SESSION_TTL);
            return record;
        } catch (Exception e) {
            log.error("Failed to deserialize SessionRecord for {}: {}", sessionId, e.getMessage());
            return null;
        }
    }

    public SessionRecord refreshAndRotateSession(SessionRecord currentSession) {
        String oldSid = currentSession.getSessionId();
        String lockKey = "lock:refresh:" + oldSid;
        String lockVal = UUID.randomUUID().toString();

        // 1. Distributed Lock in Valkey (SET NX PX 5000)
        Boolean lockAcquired = redisTemplate.opsForValue().setIfAbsent(lockKey, lockVal, LOCK_TTL);

        if (Boolean.TRUE.equals(lockAcquired)) {
            log.info("Acquired distributed refresh lock for session {}", oldSid);
            try {
                return executeRefreshAndRotation(currentSession, oldSid, lockKey);
            } finally {
                redisTemplate.delete(lockKey);
            }
        } else {
            // Concurrent request: wait 80ms and resolve via pointer
            log.info("Concurrent refresh in progress for {}. Waiting for thread handover...", oldSid);
            try {
                Thread.sleep(80);
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }
            SessionResolutionResult res = resolveSession(oldSid);
            return res != null ? res.getSession() : currentSession;
        }
    }

    private SessionRecord executeRefreshAndRotation(SessionRecord currentSession, String oldSid, String lockKey) {
        Map<String, Object> tokenResponse = keycloakAuthService.refreshAccessToken(currentSession.getRefreshToken());
        if (tokenResponse == null) {
            throw new RuntimeException("Failed to refresh token from Keycloak");
        }

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

        // Atomic multi-step rotation in Valkey:
        saveSession(newSession, newSid, SESSION_TTL);
        redisTemplate.opsForValue().set("pointer:" + oldSid, newSid, POINTER_TTL);
        redisTemplate.opsForValue().set("revoked_archive:" + oldSid, "rotated", ARCHIVE_TTL);
        redisTemplate.delete("session:" + oldSid);

        return newSession;
    }

    public void destroySession(String sessionId, String refreshToken) {
        log.info("Clean session teardown for {}", sessionId);
        keycloakAuthService.revokeToken(refreshToken);
        redisTemplate.delete("session:" + sessionId);
        redisTemplate.delete("pointer:" + sessionId);
    }

    public void handleSessionHijack(String compromisedSessionId, String refreshToken) {
        log.warn("🚨 SECURITY ALERT: Session hijacking / replay detected for ID {}", compromisedSessionId);
        keycloakAuthService.revokeToken(refreshToken);
        redisTemplate.delete("session:" + compromisedSessionId);
        redisTemplate.delete("pointer:" + compromisedSessionId);
    }

    public boolean isRevokedArchive(String sessionId) {
        return Boolean.TRUE.equals(redisTemplate.hasKey("revoked_archive:" + sessionId));
    }

    private void saveSession(SessionRecord session, String sessionId, Duration ttl) {
        try {
            String json = objectMapper.writeValueAsString(session);
            redisTemplate.opsForValue().set("session:" + sessionId, json, ttl);
        } catch (Exception e) {
            log.error("Failed to serialize session {}: {}", sessionId, e.getMessage());
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

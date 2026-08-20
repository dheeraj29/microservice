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

    @org.springframework.beans.factory.annotation.Value("${session.ttl-minutes:30}")
    private long sessionTtlMinutes = 30L;

    @org.springframework.beans.factory.annotation.Value("${session.rotation.grace-seconds:10}")
    private long rotationGraceSeconds = 10L;

    @org.springframework.beans.factory.annotation.Value("${session.lock-ttl-seconds:5}")
    private long lockTtlSeconds = 5L;

    @org.springframework.beans.factory.annotation.Value("${session.archive-ttl-hours:1}")
    private long archiveTtlHours = 1L;

    private final StringRedisTemplate redisTemplate;
    private final KeycloakAuthService keycloakAuthService;
    private final ObjectMapper objectMapper;

    public Duration getSessionTtl() { return Duration.ofMinutes(sessionTtlMinutes); }
    public Duration getPointerTtl() { return Duration.ofSeconds(rotationGraceSeconds); }
    public Duration getLockTtl() { return Duration.ofSeconds(lockTtlSeconds); }
    public Duration getArchiveTtl() { return Duration.ofHours(archiveTtlHours); }

    public DistributedSessionManager(StringRedisTemplate redisTemplate,
                                     KeycloakAuthService keycloakAuthService) {
        this.redisTemplate = redisTemplate;
        this.keycloakAuthService = keycloakAuthService;
        this.objectMapper = new ObjectMapper();
        this.objectMapper.registerModule(new JavaTimeModule());
        this.objectMapper.configure(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    }

    public SessionRecord createSession(Map<String, Object> tokenPayload, String clientFingerprint) {
        if (tokenPayload == null || !tokenPayload.containsKey("access_token")) {
            throw new IllegalArgumentException("Keycloak token exchange returned empty or invalid response");
        }
        String sessionId = "sid_" + UUID.randomUUID().toString();
        String accessToken = (String) tokenPayload.get("access_token");
        String refreshToken = (String) tokenPayload.get("refresh_token");
        Object rawExpiresIn = tokenPayload.getOrDefault("expires_in", 300);
        long expiresInSecs = 300L;
        if (rawExpiresIn instanceof Number) {
            expiresInSecs = ((Number) rawExpiresIn).longValue();
        } else if (rawExpiresIn instanceof String) {
            try { expiresInSecs = Long.parseLong((String) rawExpiresIn); } catch (Exception ignored) {}
        }

        SessionRecord session = new SessionRecord();
        session.setSessionId(sessionId);
        session.setAccessToken(accessToken);
        session.setRefreshToken(refreshToken);
        session.setAccessTokenExpiresAt(Instant.now().plusSeconds(expiresInSecs));
        session.setUsername(keycloakAuthService.extractUsernameFromToken(accessToken));
        session.setRoles(keycloakAuthService.extractRolesFromToken(accessToken));
        session.setClientFingerprint(clientFingerprint);
        session.setLanguage(keycloakAuthService.extractCustomClaim(accessToken, "language", "en"));
        session.setTimezone(keycloakAuthService.extractCustomClaim(accessToken, "timezone", "Asia/Kolkata"));
        session.setHomepage(keycloakAuthService.extractCustomClaim(accessToken, "homepage", "/booking"));
        session.setTheme(keycloakAuthService.extractCustomClaim(accessToken, "theme", "dark"));

        saveSession(session, sessionId, getSessionTtl());
        return session;
    }

    public SessionRecord updateUserPreferences(String sessionId, Map<String, String> prefs) {
        SessionRecord session = loadAndExtendSession(sessionId);
        if (session == null) return null;

        if (prefs.containsKey("language")) session.setLanguage(prefs.get("language"));
        if (prefs.containsKey("timezone")) session.setTimezone(prefs.get("timezone"));
        if (prefs.containsKey("homepage")) session.setHomepage(prefs.get("homepage"));
        if (prefs.containsKey("theme")) session.setTheme(prefs.get("theme"));

        saveSession(session, sessionId, getSessionTtl());
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
            redisTemplate.expire("session:" + sessionId, getSessionTtl());
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
        Boolean lockAcquired = redisTemplate.opsForValue().setIfAbsent(lockKey, lockVal, getLockTtl());

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

        newSession.setLanguage(currentSession.getLanguage());
        newSession.setTimezone(currentSession.getTimezone());
        newSession.setHomepage(currentSession.getHomepage());
        newSession.setTheme(currentSession.getTheme());
        newSession.setLastValidatedAt(Instant.now());

        // Atomic multi-step rotation in Valkey:
        saveSession(newSession, newSid, getSessionTtl());
        redisTemplate.opsForValue().set("pointer:" + oldSid, newSid, getPointerTtl());
        redisTemplate.opsForValue().set("revoked_archive:" + oldSid, "rotated", getArchiveTtl());
        redisTemplate.delete("session:" + oldSid);

        return newSession;
    }

    /**
     * Periodic bounded-staleness validation with Keycloak using Distributed Double-Checked Locking.
     */
    public SessionRecord validateSessionWithKeycloak(SessionRecord session, int maxStalenessSeconds) {
        if (session == null || session.getSessionId() == null) {
            return null;
        }

        // 1. Fast path: If validation is fresh (e.g. validated < 30s ago), return immediately (sub-millisecond)
        if (!session.isValidationStale(maxStalenessSeconds)) {
            return session;
        }

        String sid = session.getSessionId();
        String lockKey = "lock:validate:" + sid;
        String lockVal = UUID.randomUUID().toString();

        // 2. Distributed Double-Checked Locking in Valkey
        Boolean lockAcquired = redisTemplate.opsForValue().setIfAbsent(lockKey, lockVal, Duration.ofSeconds(4));

        if (Boolean.TRUE.equals(lockAcquired)) {
            try {
                // Double check if another thread updated it right before lock was acquired
                SessionRecord latest = loadAndExtendSession(sid);
                if (latest != null && !latest.isValidationStale(maxStalenessSeconds)) {
                    return latest;
                }

                // Call Keycloak Introspection
                boolean active = keycloakAuthService.introspectToken(session.getAccessToken());

                if (active) {
                    session.setLastValidatedAt(Instant.now());
                    saveSession(session, sid, getSessionTtl());
                    return session;
                }

                // Access token is inactive/revoked in Keycloak. Attempt silent healing with refresh token
                log.info("Access token for user {} reported inactive by Keycloak. Attempting refresh token healing...", session.getUsername());
                try {
                    SessionRecord refreshedSession = refreshAndRotateSession(session);
                    if (refreshedSession != null && !refreshedSession.getSessionId().equals(sid)) {
                        log.info("Session auto-healed via refresh token for {}", session.getUsername());
                        refreshedSession.setLastValidatedAt(Instant.now());
                        saveSession(refreshedSession, refreshedSession.getSessionId(), getSessionTtl());
                        return refreshedSession;
                    }
                } catch (Exception e) {
                    log.warn("Silent refresh failed for {}: {}", session.getUsername(), e.getMessage());
                }

                // Refresh also failed -> Session was truly revoked by admin / logout in Keycloak
                log.warn("🚨 Session {} for user {} is REVOKED in Keycloak. Purging from Valkey...", sid, session.getUsername());
                redisTemplate.opsForValue().set("revoked_archive:" + sid, "revoked_in_keycloak", getArchiveTtl());
                redisTemplate.delete("session:" + sid);
                redisTemplate.delete("pointer:" + sid);
                return null;
            } finally {
                // Safely release lock if still held by this thread
                String currentLock = redisTemplate.opsForValue().get(lockKey);
                if (lockVal.equals(currentLock)) {
                    redisTemplate.delete(lockKey);
                }
            }
        } else {
            // Lock is BUSY: Another thread is currently validating with Keycloak
            // Wait for lock release (up to 2.5s with 50ms intervals) and re-read from Valkey
            int maxAttempts = 50;
            while (maxAttempts-- > 0) {
                try {
                    Thread.sleep(50);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
                String currentLock = redisTemplate.opsForValue().get(lockKey);
                if (currentLock == null) {
                    // Lock was released! Re-check resolution
                    SessionResolutionResult res = resolveSession(sid);
                    if (res == null || res.getSession() == null) {
                        // Session was deleted (revoked) by the validating thread
                        return null;
                    }
                    return res.getSession();
                }
            }

            // Fallback if lock wait timed out
            SessionResolutionResult fallbackRes = resolveSession(sid);
            return fallbackRes != null ? fallbackRes.getSession() : null;
        }
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

    public void savePkceState(String state, String codeVerifier, String targetUrl) {
        if (state == null || state.isBlank()) {
            return;
        }
        try {
            String key = "pkce:state:" + state;
            PkceStateRecord record = new PkceStateRecord(codeVerifier, targetUrl);
            String json = objectMapper.writeValueAsString(record);
            redisTemplate.opsForValue().set(key, json, Duration.ofMinutes(5));
        } catch (Exception e) {
            log.error("Failed to save PKCE state {}: {}", state, e.getMessage());
        }
    }

    public PkceStateRecord consumePkceState(String state) {
        if (state == null || state.isBlank()) {
            return null;
        }
        String key = "pkce:state:" + state;
        String json = redisTemplate.opsForValue().get(key);
        if (json != null) {
            redisTemplate.delete(key);
            try {
                return objectMapper.readValue(json, PkceStateRecord.class);
            } catch (Exception e) {
                return new PkceStateRecord(json, null);
            }
        }
        return null;
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

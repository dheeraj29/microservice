package com.da.demo.security.passport;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Date;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jose.crypto.MACVerifier;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;

/**
 * Ephemeral Valkey Passport Manager for Inter-Service OpenFeign Calls.
 * Attaches X-Internal-Passport and X-Passport-User on outgoing Feign calls.
 * The passport records the caller app identity (iss) and is verified using
 * the caller app's 30s rotating key stored in Valkey with a 10s grace window.
 */
@Component
public class PassportManager {

    private static final Logger log = LoggerFactory.getLogger(PassportManager.class);

    public static final String PASSPORT_HEADER = "X-Internal-Passport";
    public static final String PASSPORT_USER_HEADER = "X-Passport-User";

    public static final long ROTATION_INTERVAL_SEC = 30L;
    public static final long GRACE_WINDOW_SEC = 10L;
    public static final long PASSPORT_TTL_SEC = 60L;

    private final StringRedisTemplate redisTemplate;
    private final SecureRandom secureRandom = new SecureRandom();

    // Local in-memory cache per caller app to achieve < 5 microsecond decoding
    private final ConcurrentHashMap<String, KeyCacheEntry> keyCache = new ConcurrentHashMap<>();

    public PassportManager(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    private record KeyCacheEntry(String currentKey, String previousKey, long expiresAt) {}

    /**
     * Mints a fresh short-lived (60s) HMAC-SHA256 Internal Passport JWT for OpenFeign calls.
     */
    public String mintPassport(String callerAppName, String username, List<String> roles) {
        if (callerAppName == null || callerAppName.isBlank()) {
            callerAppName = "unknown-service";
        }
        if (username == null || username.isBlank()) {
            username = "anonymous";
        }
        if (roles == null) {
            roles = List.of("ROLE_USER");
        }

        try {
            String signingKey = getActiveSigningKey(callerAppName);
            Date now = new Date();
            Date expiry = new Date(now.getTime() + (PASSPORT_TTL_SEC * 1000L));

            JWTClaimsSet claims = new JWTClaimsSet.Builder()
                    .issuer(callerAppName)
                    .subject(username)
                    .issueTime(now)
                    .expirationTime(expiry)
                    .claim("roles", roles)
                    .build();

            SignedJWT signedJWT = new SignedJWT(
                    new JWSHeader(JWSAlgorithm.HS256),
                    claims
            );

            signedJWT.sign(new MACSigner(signingKey.getBytes(StandardCharsets.UTF_8)));
            return signedJWT.serialize();
        } catch (Exception e) {
            log.error("Failed to mint internal passport from {} for user {}: {}", callerAppName, username, e.getMessage());
            throw new RuntimeException("Passport minting failure", e);
        }
    }

    /**
     * Cryptographically verifies the Internal Passport and ensures X-Passport-User matches.
     */
    public PassportRecord verifyPassport(String passportToken, String passportUserHeader) {
        if (passportToken == null || passportToken.isBlank()) {
            throw new SecurityException("Missing internal passport token");
        }

        try {
            SignedJWT signedJWT = SignedJWT.parse(passportToken);
            JWTClaimsSet claims = signedJWT.getJWTClaimsSet();

            Date exp = claims.getExpirationTime();
            if (exp != null && new Date().after(exp)) {
                throw new SecurityException("Internal passport has expired");
            }

            String callerApp = claims.getIssuer();
            if (callerApp == null || callerApp.isBlank()) {
                callerApp = "unknown-service";
            }

            String subject = claims.getSubject();

            // Validate X-Passport-User header consistency
            if (passportUserHeader != null && !passportUserHeader.isBlank() && !passportUserHeader.equals(subject)) {
                log.warn("🚨 PASSPORT TAMPER DETECTED: Header user '{}' does not match token subject '{}'", passportUserHeader, subject);
                throw new SecurityException("Passport user header mismatch");
            }

            KeyCacheEntry keys = getKeysForApp(callerApp);

            // 1. Try Current Active Key
            boolean valid = false;
            if (keys.currentKey() != null) {
                valid = signedJWT.verify(new MACVerifier(keys.currentKey().getBytes(StandardCharsets.UTF_8)));
            }

            // 2. Fallback to Previous Key during 10s Grace Window
            if (!valid && keys.previousKey() != null && !keys.previousKey().isBlank()) {
                valid = signedJWT.verify(new MACVerifier(keys.previousKey().getBytes(StandardCharsets.UTF_8)));
                if (valid) {
                    log.debug("Internal passport from '{}' verified via 10s grace key", callerApp);
                }
            }

            if (!valid) {
                throw new SecurityException("Invalid internal passport signature from caller app: " + callerApp);
            }

            List<String> roles = new ArrayList<>();
            Object rolesClaim = claims.getClaim("roles");
            if (rolesClaim instanceof List) {
                for (Object r : (List<?>) rolesClaim) {
                    roles.add(r.toString());
                }
            }
            if (roles.isEmpty()) {
                roles.add("ROLE_USER");
            }

            return new PassportRecord(subject, roles, callerApp, exp);
        } catch (SecurityException se) {
            throw se;
        } catch (Exception e) {
            log.warn("Passport verification failed: {}", e.getMessage());
            throw new SecurityException("Corrupt internal passport token", e);
        }
    }

    private String getActiveSigningKey(String appName) {
        KeyCacheEntry entry = getKeysForApp(appName);
        if (entry != null && entry.currentKey() != null) {
            return entry.currentKey();
        }
        rotateKeyForApp(appName);
        return getKeysForApp(appName).currentKey();
    }

    private KeyCacheEntry getKeysForApp(String appName) {
        long now = System.currentTimeMillis();
        KeyCacheEntry cached = keyCache.get(appName);
        if (cached != null && now < cached.expiresAt() && cached.currentKey() != null) {
            return cached;
        }

        try {
            String currentKeyName = "passport:key:" + appName + ":current";
            String previousKeyName = "passport:key:" + appName + ":previous";

            String current = redisTemplate.opsForValue().get(currentKeyName);
            String previous = redisTemplate.opsForValue().get(previousKeyName);

            if (current != null && !current.isBlank()) {
                Long ttlMs = redisTemplate.getExpire(currentKeyName, TimeUnit.MILLISECONDS);
                // Cache locally for remaining Valkey TTL (capped to max 5s to stay fresh)
                long cacheDuration = (ttlMs != null && ttlMs > 1000L) ? Math.min(ttlMs - 500L, 5000L) : 1000L;
                KeyCacheEntry entry = new KeyCacheEntry(current, previous, now + cacheDuration);
                keyCache.put(appName, entry);
                return entry;
            }
        } catch (Exception e) {
            log.warn("Valkey passport key read error for {}: {}", appName, e.getMessage());
        }

        // Rotate if missing
        rotateKeyForApp(appName);
        KeyCacheEntry entry = keyCache.get(appName);
        return entry != null ? entry : new KeyCacheEntry(generateSecureKey(), null, now + 1000L);
    }

    private synchronized void rotateKeyForApp(String appName) {
        String currentKeyName = "passport:key:" + appName + ":current";
        String previousKeyName = "passport:key:" + appName + ":previous";
        String lockKeyName = "lock:passport:rotate:" + appName;

        try {
            String current = redisTemplate.opsForValue().get(currentKeyName);
            if (current != null && !current.isBlank()) {
                String previous = redisTemplate.opsForValue().get(previousKeyName);
                keyCache.put(appName, new KeyCacheEntry(current, previous, System.currentTimeMillis() + 5000L));
                return;
            }

            Boolean acquired = redisTemplate.opsForValue().setIfAbsent(lockKeyName, "locked", Duration.ofSeconds(4));
            if (Boolean.TRUE.equals(acquired)) {
                try {
                    String oldKey = redisTemplate.opsForValue().get(currentKeyName);
                    String newKey = generateSecureKey();

                    if (oldKey != null && !oldKey.isBlank()) {
                        redisTemplate.opsForValue().set(previousKeyName, oldKey, Duration.ofSeconds(GRACE_WINDOW_SEC));
                    }
                    redisTemplate.opsForValue().set(currentKeyName, newKey, Duration.ofSeconds(ROTATION_INTERVAL_SEC));

                    keyCache.put(appName, new KeyCacheEntry(newKey, oldKey, System.currentTimeMillis() + 5000L));
                    log.info("🔐 Rotated Valkey passport key for microservice '{}'", appName);
                } finally {
                    redisTemplate.delete(lockKeyName);
                }
            } else {
                try { Thread.sleep(50); } catch (InterruptedException ignored) {}
                String c = redisTemplate.opsForValue().get(currentKeyName);
                String p = redisTemplate.opsForValue().get(previousKeyName);
                if (c != null) {
                    keyCache.put(appName, new KeyCacheEntry(c, p, System.currentTimeMillis() + 5000L));
                }
            }
        } catch (Exception e) {
            log.error("Valkey key rotation error for {}: {}", appName, e.getMessage());
            keyCache.put(appName, new KeyCacheEntry(generateSecureKey(), null, System.currentTimeMillis() + 5000L));
        }
    }

    private String generateSecureKey() {
        byte[] bytes = new byte[32];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    public record PassportRecord(String username, List<String> roles, String callerAppName, Date expiresAt) {}
}
package com.da.demo.gateway.security;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * Enterprise PKCE (Proof Key for Code Exchange - RFC 7636) Utility.
 * 
 * Provides cryptographically secure code_verifier generation using java.security.SecureRandom
 * and S256 code_challenge calculation using SHA-256 with unpadded URL-safe Base64 encoding.
 */
public final class PkceUtil {

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private PkceUtil() {
        // utility class
    }

    /**
     * Generates a high-entropy cryptographic random code_verifier (RFC 7636 Section 4.1).
     * 32 bytes of secure random entropy encoded in unpadded Base64URL yields 43 characters.
     */
    public static String generateCodeVerifier() {
        byte[] randomBytes = new byte[32];
        SECURE_RANDOM.nextBytes(randomBytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes);
    }

    /**
     * Calculates the S256 code_challenge from a code_verifier per RFC 7636 Section 4.2:
     * code_challenge = BASE64URL-ENCODE(SHA256(ASCII(code_verifier))) without padding.
     */
    public static String generateCodeChallenge(String codeVerifier) {
        if (codeVerifier == null || codeVerifier.isBlank()) {
            throw new IllegalArgumentException("code_verifier cannot be null or empty");
        }
        try {
            byte[] asciiBytes = codeVerifier.getBytes(StandardCharsets.US_ASCII);
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(asciiBytes);
            return Base64.getUrlEncoder().withoutPadding().encodeToString(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 message digest algorithm not available in JVM", e);
        }
    }
}

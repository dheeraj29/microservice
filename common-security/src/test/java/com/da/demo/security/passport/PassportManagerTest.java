package com.da.demo.security.passport;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import static org.mockito.Mockito.when;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

public class PassportManagerTest {

    private StringRedisTemplate redisTemplate;
    private ValueOperations<String, String> valueOperations;
    private PassportManager passportManager;

    @BeforeEach
    public void setUp() {
        redisTemplate = Mockito.mock(StringRedisTemplate.class);
        valueOperations = Mockito.mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);

        passportManager = new PassportManager(redisTemplate);
    }

    @Test
    public void testMintAndVerifyPassportSuccess() {
        String testKey = "dGVzdC1rZXktZm9yLXVuaXQtdGVzdGluZy0yNTYtYml0cw==";
        when(valueOperations.get("passport:key:bookingservice:current")).thenReturn(testKey);

        String passport = passportManager.mintPassport("bookingservice", "john_doe", List.of("ROLE_ADMIN", "ROLE_USER"));
        assertNotNull(passport);
        assertTrue(passport.startsWith("ey"));

        PassportManager.PassportRecord record = passportManager.verifyPassport(passport, "john_doe");
        assertNotNull(record);
        assertEquals("john_doe", record.username());
        assertEquals("bookingservice", record.callerAppName());
        assertTrue(record.roles().contains("ROLE_ADMIN"));
        assertTrue(record.roles().contains("ROLE_USER"));
    }

    @Test
    public void testUserHeaderMismatchThrowsSecurityException() {
        String testKey = "dGVzdC1rZXktZm9yLXVuaXQtdGVzdGluZy0yNTYtYml0cw==";
        when(valueOperations.get("passport:key:bookingservice:current")).thenReturn(testKey);

        String passport = passportManager.mintPassport("bookingservice", "john_doe", List.of("ROLE_USER"));

        // Header claims to be 'attacker_user', but passport was minted for 'john_doe'
        assertThrows(SecurityException.class, () -> {
            passportManager.verifyPassport(passport, "attacker_user");
        });
    }

    @Test
    public void testVerificationFallbackToGracePreviousKeyForCallerApp() {
        String oldKey = "b2xkLWtleS1mb3ItZ3JhY2Utd2luZG93LXRlc3RpbmctMjU2";
        String newKey = "bmV3LWtleS1mb3ItYWN0aXZlLXNpZ25pbmctdGVzdC0yNTY=";

        // Step 1: Mint with Old Key
        when(valueOperations.get("passport:key:adminservice:current")).thenReturn(oldKey);
        String oldPassport = passportManager.mintPassport("adminservice", "grace_user", List.of("ROLE_USER"));

        // Step 2: Simulate Key Rotation where oldKey moves to previous
        when(valueOperations.get("passport:key:adminservice:current")).thenReturn(newKey);
        when(valueOperations.get("passport:key:adminservice:previous")).thenReturn(oldKey);

        // Verification should succeed via fallback to previousKey!
        PassportManager.PassportRecord record = passportManager.verifyPassport(oldPassport, "grace_user");
        assertNotNull(record);
        assertEquals("grace_user", record.username());
        assertEquals("adminservice", record.callerAppName());
    }
}
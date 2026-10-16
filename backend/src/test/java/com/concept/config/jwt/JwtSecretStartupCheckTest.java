package com.concept.config.jwt;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * Outside dev mode, the application refuses to start on a JWT secret anyone
 * could know: missing, the public default, or too short to be a real HS256 key.
 */
class JwtSecretStartupCheckTest {

    private static final String DEFAULT =
            "dev-only-insecure-default-change-in-production-please-32bytes-min";
    private static final String REAL = "a-real-random-secret-of-more-than-32-bytes-0123456789";

    @Test
    void devModeStartsOnTheDefault() {
        assertDoesNotThrow(() -> new JwtUtils(DEFAULT, true));
    }

    @Test
    void productionStartsOnARealSecret() {
        assertDoesNotThrow(() -> new JwtUtils(REAL, false));
    }

    @Test
    void productionRefusesTheDefault() {
        IllegalStateException e = assertThrows(IllegalStateException.class,
                () -> new JwtUtils(DEFAULT, false));
        assertTrue(e.getMessage().contains("insecure dev-only default"), e.getMessage());
        assertTrue(e.getMessage().contains("JWT_SECRET"), e.getMessage());
    }

    @Test
    void productionRefusesAMissingSecret() {
        for (String missing : new String[] {null, "", "   "}) {
            IllegalStateException e = assertThrows(IllegalStateException.class,
                    () -> JwtUtils.requireUsableSecret(missing, false));
            assertTrue(e.getMessage().contains("not set"), e.getMessage());
        }
    }

    @Test
    void productionRefusesAShortSecretWithoutPrintingIt() {
        String shortSecret = "only-thirty-one-bytes-long-abcd";
        IllegalStateException e = assertThrows(IllegalStateException.class,
                () -> new JwtUtils(shortSecret, false));
        assertTrue(e.getMessage().contains("31 bytes"), e.getMessage());
        assertFalse(e.getMessage().contains(shortSecret), "the secret must not reach the logs");
    }

    @Test
    void exactlyThirtyTwoBytesIsEnough() {
        assertDoesNotThrow(() -> new JwtUtils("x".repeat(32), false));
    }
}

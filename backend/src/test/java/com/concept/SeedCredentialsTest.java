package com.concept;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * The seeded accounts' password comes from configuration, and nothing in source
 * stands in for it.
 */
class SeedCredentialsTest {

    @Test
    void usesTheConfiguredPassword() {
        SeedCredentials credentials = new SeedCredentials("a-value-somebody-chose");

        assertTrue(credentials.isConfigured());
        assertEquals("a-value-somebody-chose", credentials.password());
    }

    @Test
    void withNothingConfiguredEachRunGetsItsOwnRandomPassword() {
        SeedCredentials first = new SeedCredentials("");
        SeedCredentials second = new SeedCredentials(null);

        assertFalse(first.isConfigured());
        assertFalse(second.isConfigured());
        // Random, not a fixed fallback: a fixed one would be a default
        // password again, just spelt differently.
        assertNotEquals(first.password(), second.password());
        assertTrue(first.password().length() >= 32);
    }

    @Test
    void blankIsTreatedAsUnset() {
        assertFalse(new SeedCredentials("   ").isConfigured());
    }
}

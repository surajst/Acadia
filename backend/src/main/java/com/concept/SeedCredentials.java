package com.concept;

import java.security.SecureRandom;
import java.util.Base64;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * The password every dev-mode seeder gives the accounts it creates.
 *
 * <p>Read from {@code demo.admin.password} ({@code DEMO_ADMIN_PASSWORD}) and
 * nowhere else. There is deliberately no default in source: the repository was
 * public, and a default here is a password published to everyone who can read
 * it. CI sets a throwaway value in its workflow; a developer sets their own.
 *
 * <p>When it is not set, the seeded accounts get a random password for this
 * run, so a machine without the variable still starts and its tests still run
 * -- it just cannot be signed into as a seeded user, which is the right failure
 * for a value nobody chose. The random value is never printed.
 *
 * <p>One bean, shared by all three seeders: they write overlapping accounts,
 * and two independent random fallbacks would leave the same login with two
 * different passwords depending on which seeder ran last.
 */
@Component
@ConditionalOnProperty(name = "app.dev-mode", havingValue = "true")
public class SeedCredentials {

    private final String password;
    private final boolean configured;

    public SeedCredentials(@Value("${demo.admin.password:}") String configuredPassword) {
        this.configured = configuredPassword != null && !configuredPassword.isBlank();
        if (configured) {
            this.password = configuredPassword;
        } else {
            byte[] bytes = new byte[24];
            new SecureRandom().nextBytes(bytes);
            this.password = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
            System.out.println(">> DEMO_ADMIN_PASSWORD is not set: seeded accounts get a random password "
                    + "for this run and cannot be signed into. Set DEMO_ADMIN_PASSWORD to log in as them.");
        }
    }

    /** The raw password to encode onto seeded accounts. */
    public String password() {
        return password;
    }

    /** Whether the password came from configuration rather than the random fallback. */
    public boolean isConfigured() {
        return configured;
    }
}

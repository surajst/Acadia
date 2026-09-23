package com.concept.roster.app;

import com.concept.tenant.Tenant;
import com.concept.tenant.TenantRepository;
import com.concept.user.UserRepository;
import org.springframework.stereotype.Component;

import java.util.Locale;
import java.util.UUID;

/**
 * Builds the sign-in usernames handed to students and guardians, qualified by
 * the school's subdomain: "asha6a-01@greenwood".
 *
 * <p>The subdomain is not decoration. {@code User.email} carries a global unique
 * constraint, so an unqualified roll number puts every school in one namespace:
 * the first school to register "6A-01" claims it platform-wide, and every later
 * school falls through the {@code existsByEmail} check and silently provisions
 * no login at all — no error, no warning, just a child who cannot sign in. Same
 * story for a guardian's bare phone number.
 *
 * <p>This lived as private methods on {@link StudentAdminService}, which meant
 * the bulk CSV import in {@link RosterImportService} kept its own unqualified
 * version long after the single-add path was fixed — the two disagreed about
 * what a username was, and only one of them was right. One implementation, used
 * by both, is what stops that recurring.
 */
@Component
public class SchoolUsernames {

    /** Beyond this many collisions within one school, give up rather than loop. */
    private static final int MAX_SUFFIX = 20;

    private final TenantRepository tenantRepository;
    private final UserRepository userRepository;

    public SchoolUsernames(TenantRepository tenantRepository, UserRepository userRepository) {
        this.tenantRepository = tenantRepository;
        this.userRepository = userRepository;
    }

    /**
     * Student login: first name + roll number, qualified by the school. The
     * first name makes it less guessable from a class list than a bare roll.
     *
     * @return the username, or null when one cannot be formed or stays taken
     */
    public String forStudent(String firstName, String rollNumber, UUID tenantId) {
        return qualified(sanitise(firstName) + sanitise(rollNumber), tenantId);
    }

    /**
     * Guardian login: the guardian's own name, qualified by the school --
     * "rakesh.sharma@silverbrook", with a numeric suffix if that is taken.
     *
     * <p>This used to be first name + phone number, which published a parent's
     * full mobile number as their username: "anil919876543210@demo", printed on
     * the credentials banner, visible to anyone the sheet is handed to, and
     * unchangeable afterwards because it is the login. A phone number also
     * changes, and a username must not.
     *
     * <p>Existing guardians keep the username they were issued -- it is stored,
     * not derived -- so this changes only who is created from here on.
     *
     * @return the username, or null when one cannot be formed or stays taken
     */
    public String forGuardian(String firstName, String lastName, UUID tenantId) {
        String first = sanitise(firstName);
        String last = sanitise(lastName);
        String seed = last.isEmpty() ? first : first + "." + last;
        return qualified(seed, tenantId);
    }

    /**
     * Turns a local part into a school-qualified username, or null when one
     * cannot be formed. Null means "no login", and every caller has to treat it
     * as such rather than falling back to an unqualified value.
     */
    private String qualified(String localSeed, UUID tenantId) {
        String subdomain = tenantId == null ? null : tenantRepository.findById(tenantId)
                .map(Tenant::getSubdomain).orElse(null);
        if (subdomain == null || subdomain.isBlank()) {
            return null;
        }
        String local = localSeed == null ? "" : localSeed;
        if (local.isBlank()) {
            return null;
        }
        String domain = "@" + sanitise(subdomain);
        if (!userRepository.existsByEmail(local + domain)) {
            return local + domain;
        }
        // A clash within one school should be near-impossible, but it must not
        // silently mean "no login" the way the global namespace did.
        for (int i = 2; i <= MAX_SUFFIX; i++) {
            String next = local + i + domain;
            if (!userRepository.existsByEmail(next)) {
                return next;
            }
        }
        return null;
    }

    /** Lowercase, keeping only characters a family can retype without ambiguity. */
    private static String sanitise(String raw) {
        return raw == null ? "" : raw.trim().toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9.-]", "");
    }
}

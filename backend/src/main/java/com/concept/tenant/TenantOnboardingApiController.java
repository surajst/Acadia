package com.concept.tenant;

import com.concept.common.RateLimiter;
import com.concept.config.jwt.JwtUtils;
import com.concept.user.User;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import jakarta.servlet.http.HttpServletRequest;

import java.util.HashMap;
import java.util.Map;

/**
 * Public, unauthenticated school signup — the only way a brand-new Tenant
 * gets created. Solves the bootstrap problem (a tenant's first ADMIN can't
 * already hold the ADMIN role in a tenant that doesn't exist yet) via a
 * self-serve "create your school" step, matching how Slack/Notion-style
 * SaaS products create a new workspace.
 */
@RestController
@RequestMapping("/api/onboard")
public class TenantOnboardingApiController {

    /**
     * Enough for anyone filling in a signup form -- the field checks as you type,
     * so a handful per name -- and slow enough that walking the customer list
     * through it is not worth doing.
     */
    static final int SUBDOMAIN_CHECKS_PER_MINUTE = 30;

    private final TenantOnboardingService onboardingService;
    private final JwtUtils jwtUtils;
    private final RateLimiter rateLimiter;

    public TenantOnboardingApiController(TenantOnboardingService onboardingService, JwtUtils jwtUtils,
                                         RateLimiter rateLimiter) {
        this.onboardingService = onboardingService;
        this.jwtUtils = jwtUtils;
        this.rateLimiter = rateLimiter;
    }

    public static class CreateSchoolRequest {
        public String schoolName;
        public String subdomain;
        public String adminEmail;
        public String adminPassword;
        public String adminFullName;
        /** Optional; absent means a conventional school. */
        public String schoolType;
    }

    /**
     * Whether a subdomain is free, and what it will actually be.
     *
     * <p>The signup form previewed the typed value with only spaces replaced,
     * so "demo@ssc" was shown as an address that cannot exist -- and nothing
     * told anyone it was taken until the whole form came back rejected. Both
     * answers come from the server so the preview and the eventual create
     * cannot disagree about what the address is.
     */
    @GetMapping("/subdomain-available")
    public ResponseEntity<?> subdomainAvailable(@RequestParam("subdomain") String requested,
                                               HttpServletRequest httpRequest) {
        // Unauthenticated by necessity -- nobody has an account at signup -- and
        // it answers whether a given address belongs to a school on ACADIA. Left
        // open it is a directory anyone can walk, and a few thousand requests
        // enumerate the customer list. The limit is generous enough that typing
        // a name never trips it.
        if (!rateLimiter.tryAcquire(clientAddress(httpRequest), SUBDOMAIN_CHECKS_PER_MINUTE)) {
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                    .body(Map.of("error", "Too many checks. Wait a moment and try again."));
        }

        String normalised = TenantOnboardingService.normaliseSubdomain(requested);
        if (normalised.isEmpty()) {
            return ResponseEntity.ok(Map.of(
                    "subdomain", "", "available", false,
                    "reason", "Use letters and numbers, for example silverbrook."));
        }
        boolean taken = onboardingService.subdomainTaken(normalised);
        Map<String, Object> body = new java.util.LinkedHashMap<>();
        body.put("subdomain", normalised);
        body.put("available", !taken);
        if (taken) {
            body.put("reason", "That address is already taken.");
        }
        return ResponseEntity.ok(body);
    }

    @PostMapping("/create-school")
    public ResponseEntity<?> createSchool(@RequestBody CreateSchoolRequest request) {
        if (isBlank(request.schoolName) || isBlank(request.subdomain) || isBlank(request.adminEmail)
                || isBlank(request.adminPassword) || isBlank(request.adminFullName)) {
            return ResponseEntity.badRequest().body(Map.of("error", "All fields are required"));
        }

        try {
            TenantOnboardingService.NewSchool school = onboardingService.createSchool(
                    request.schoolName, request.subdomain, request.adminEmail,
                    request.adminPassword, request.adminFullName,
                    parseSchoolType(request.schoolType));

            User admin = school.adminUser;
            UserDetails userDetails = org.springframework.security.core.userdetails.User
                    .withUsername(admin.getEmail())
                    .password(admin.getPasswordHash())
                    .roles(admin.getRole().name())
                    .build();
            String jwt = jwtUtils.generateToken(userDetails, admin.getTenantId(), admin.getAcademicYearId());

            String[] nameParts = admin.getFullName().split(" ", 2);
            Map<String, Object> response = new HashMap<>();
            response.put("token", jwt);
            response.put("userId", admin.getId());
            response.put("tenantId", school.tenant.getId());
            response.put("role", admin.getRole().name());
            response.put("firstName", nameParts[0]);
            response.put("lastName", nameParts.length > 1 ? nameParts[1] : "");
            response.put("email", admin.getEmail());

            return ResponseEntity.ok(response);
        } catch (TenantOnboardingService.DuplicateSubdomainException | TenantOnboardingService.DuplicateEmailException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * The caller's address, as seen from behind Render's proxy.
     *
     * <p>{@code getRemoteAddr()} is the load balancer there, so every request
     * would share one bucket and thirty checks would lock out the whole
     * internet. The first entry in X-Forwarded-For is the original client; it is
     * client-supplied and therefore spoofable, which is the accepted limit of an
     * IP limit on a public endpoint -- it slows bulk scraping from one place, and
     * is not an authorisation decision.
     */
    private static String clientAddress(HttpServletRequest request) {
        if (request == null) {
            return null;
        }
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            int comma = forwarded.indexOf(',');
            return (comma < 0 ? forwarded : forwarded.substring(0, comma)).trim();
        }
        return request.getRemoteAddr();
    }

    private boolean isBlank(String s) {
        return s == null || s.trim().isEmpty();
    }

    /**
     * An unrecognised or absent type is a conventional school rather than an
     * error: this is the public signup endpoint, and refusing to create a
     * school over a bad dropdown value would be a worse trade than defaulting.
     */
    private static SchoolType parseSchoolType(String raw) {
        if (raw == null || raw.isBlank()) {
            return SchoolType.SECONDARY;
        }
        try {
            return SchoolType.valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return SchoolType.SECONDARY;
        }
    }
}

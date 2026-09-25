package com.concept.tenant;

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

    private final TenantOnboardingService onboardingService;
    private final JwtUtils jwtUtils;

    public TenantOnboardingApiController(TenantOnboardingService onboardingService, JwtUtils jwtUtils) {
        this.onboardingService = onboardingService;
        this.jwtUtils = jwtUtils;
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
    public ResponseEntity<?> subdomainAvailable(@RequestParam("subdomain") String requested) {
        // Throttled per IP by RateLimitFilter's "subdomain" rule, not here: this
        // endpoint is unauthenticated by necessity and answers whether an address
        // belongs to a school on ACADIA, so left open it is a directory anyone
        // can walk.
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

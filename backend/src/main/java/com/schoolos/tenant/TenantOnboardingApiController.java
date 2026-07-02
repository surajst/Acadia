package com.schoolos.tenant;

import com.schoolos.config.jwt.JwtUtils;
import com.schoolos.user.User;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
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
                    request.adminPassword, request.adminFullName);

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
}

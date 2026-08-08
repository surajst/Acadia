package com.concept.staff.web;

import com.concept.staff.app.StaffService;
import com.concept.staff.app.StaffView;
import com.concept.tenant.TenantContext;
import com.concept.user.UserRole;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Interface layer for admin staff management. Binds requests, resolves the
 * tenant, and returns flat JSON views — no entities, no persistence (ADR 0001).
 */
@Controller
public class StaffController {

    private final StaffService staffService;
    private final TenantContext tenantContext;

    public StaffController(StaffService staffService, TenantContext tenantContext) {
        this.staffService = staffService;
        this.tenantContext = tenantContext;
    }

    @GetMapping("/web/admin/staff")
    @ResponseBody
    public List<StaffView> listStaff() {
        return staffService.listStaff(tenantContext.getTenantId().orElse(null));
    }

    @PostMapping("/web/admin/staff/add")
    @ResponseBody
    public Object addStaff(@RequestParam("fullName") String fullName,
                           @RequestParam("email") String email,
                           @RequestParam("password") String password,
                           @RequestParam("role") UserRole role,
                           Authentication authentication) {
        try {
            UUID id = staffService.addStaff(fullName, email, password, role,
                    tenantContext.getTenantId().orElse(null), tenantContext.getAcademicYearId().orElse(null),
                    authentication);
            return Map.of("status", "created", "id", id, "approvalStatus", "PENDING");
        } catch (IllegalArgumentException e) {
            return Map.of("error", e.getMessage());
        }
    }
}

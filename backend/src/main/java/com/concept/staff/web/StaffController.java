package com.concept.staff.web;

import com.concept.staff.app.StaffInvite;
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

    /**
     * The temporary password is generated server-side and is no longer accepted
     * from the request. It used to be produced in browser JavaScript, which put
     * a security property in the client's hands and used Math.random rather
     * than the SecureRandom the server already uses for student credentials.
     */
    @PostMapping("/web/admin/staff/add")
    @ResponseBody
    public Object addStaff(@RequestParam("fullName") String fullName,
                           @RequestParam("email") String email,
                           @RequestParam("role") UserRole role,
                           @RequestParam(value = "schoolName", required = false) String schoolName,
                           Authentication authentication) {
        try {
            StaffInvite invite = staffService.inviteStaff(fullName, email, role, schoolName,
                    tenantContext.getTenantId().orElse(null), tenantContext.getAcademicYearId().orElse(null),
                    authentication);
            Map<String, Object> body = new java.util.LinkedHashMap<>();
            body.put("status", "created");
            body.put("id", invite.id());
            body.put("approvalStatus", "PENDING");
            // Returned whether or not the email went: the account exists either
            // way, and an admin with no visible credential cannot recover if
            // the mail never arrives.
            body.put("temporaryPassword", invite.temporaryPassword());
            body.put("emailed", invite.emailed());
            body.put("emailDetail", invite.emailDetail());
            return body;
        } catch (IllegalArgumentException e) {
            return Map.of("error", e.getMessage());
        }
    }
}

package com.concept.staff.app;

import com.concept.common.AuditLogService;
import com.concept.staff.data.StaffUserRepository;
import com.concept.user.User;
import com.concept.user.UserRole;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Application layer for admin staff management: listing staff and inviting new
 * staff (who land PENDING, awaiting PRINCIPAL/ADMIN approval). Owns the tenant
 * scoping, role whitelist, login creation, and audit trail; the web layer only
 * binds and shapes the JSON response (ADR 0001).
 */
@Service
public class StaffService {

    /** Roles that count as "staff" and may be invited from this console. */
    private static final List<UserRole> STAFF_ROLES =
            Arrays.asList(UserRole.ADMIN, UserRole.PRINCIPAL, UserRole.TEACHER, UserRole.DRIVER);

    private final StaffUserRepository staffUserRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuditLogService auditLogService;

    public StaffService(StaffUserRepository staffUserRepository,
                        PasswordEncoder passwordEncoder,
                        AuditLogService auditLogService) {
        this.staffUserRepository = staffUserRepository;
        this.passwordEncoder = passwordEncoder;
        this.auditLogService = auditLogService;
    }

    @Transactional(readOnly = true)
    public List<StaffView> listStaff(UUID tenantId) {
        if (tenantId == null) {
            return Collections.emptyList();
        }
        return staffUserRepository.findByTenantIdAndRoleIn(tenantId, STAFF_ROLES).stream()
                .map(u -> new StaffView(u.getId(), u.getFullName(), u.getEmail(), u.getRole().name(), u.isActive()))
                .collect(Collectors.toList());
    }

    /**
     * Invite a staff member (created PENDING). Throws
     * {@link IllegalArgumentException} for a non-staff role or a taken email;
     * the web layer maps that to an error response.
     */
    @Transactional
    public UUID addStaff(String fullName, String email, String password, UserRole role,
                         UUID tenantId, UUID academicYearId, Authentication authentication) {
        if (!STAFF_ROLES.contains(role)) {
            throw new IllegalArgumentException("Staff role must be ADMIN, PRINCIPAL, TEACHER, or DRIVER");
        }
        if (staffUserRepository.existsByEmail(email)) {
            throw new IllegalArgumentException("Email already in use: " + email);
        }

        User staff = new User();
        staff.setId(UUID.randomUUID());
        staff.setTenantId(tenantId);
        staff.setAcademicYearId(academicYearId);
        staff.setEmail(email);
        staff.setPasswordHash(passwordEncoder.encode(password));
        staff.setFullName(fullName);
        staff.setRole(role);
        staff.setActive(true);
        staff.setApprovalStatus(User.ApprovalStatus.PENDING);
        staffUserRepository.save(staff);
        auditLogService.log(authentication, "STAFF_INVITED", "User", staff.getId(),
                "Invited " + role.name() + " " + fullName + " (" + email + ") — awaiting PRINCIPAL/ADMIN approval");
        return staff.getId();
    }
}

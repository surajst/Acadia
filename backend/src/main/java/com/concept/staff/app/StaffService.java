package com.concept.staff.app;

import com.concept.common.AuditLogService;
import com.concept.common.EmailDeliveryService;
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
 * staff (who can sign in straight away — see addStaff). Owns the tenant
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
    private final EmailDeliveryService emailDeliveryService;

    public StaffService(StaffUserRepository staffUserRepository,
                        PasswordEncoder passwordEncoder,
                        AuditLogService auditLogService,
                        EmailDeliveryService emailDeliveryService) {
        this.staffUserRepository = staffUserRepository;
        this.passwordEncoder = passwordEncoder;
        this.auditLogService = auditLogService;
        this.emailDeliveryService = emailDeliveryService;
    }

    @Transactional(readOnly = true)
    public List<StaffView> listStaff(UUID tenantId) {
        if (tenantId == null) {
            return Collections.emptyList();
        }
        return staffUserRepository.findByTenantIdAndRoleIn(tenantId, STAFF_ROLES).stream()
                .map(u -> new StaffView(u.getId(), u.getFullName(), u.getEmail(), u.getRole().name(),
                        u.isActive(),
                        // Null predates the column and has always been treated
                        // as approved; say so rather than rendering a blank.
                        (u.getApprovalStatus() == null ? User.ApprovalStatus.APPROVED : u.getApprovalStatus()).name()))
                .collect(Collectors.toList());
    }

    /**
     * Invite a staff member. Throws
     * {@link IllegalArgumentException} for a non-staff role or a taken email;
     * the web layer maps that to an error response.
     */
    @Transactional
    /**
     * Creates a staff account and emails the sign-in details.
     *
     * @return the new user id, the generated password, and whether the email
     *         actually went. The password is returned even on a successful
     *         send: the account exists either way, and an admin who cannot see
     *         the credential has no way to recover if the mail never arrives.
     */
    public StaffInvite inviteStaff(String fullName, String email, UserRole role, String schoolName,
                                   UUID tenantId, UUID academicYearId, Authentication authentication) {
        String password = generateTempPassword();
        UUID id = addStaff(fullName, email, password, role, tenantId, academicYearId, authentication);

        EmailDeliveryService.EmailResult result = emailDeliveryService.send(
                email,
                "Your " + (schoolName == null || schoolName.isBlank() ? "school" : schoolName) + " staff account",
                inviteBody(fullName, email, password, role, schoolName));

        // Recorded separately from STAFF_INVITED: "the account was created" and
        // "the person was told" are different facts, and only the second one
        // determines whether anybody shows up.
        auditLogService.log(authentication,
                result.delivered() ? "STAFF_INVITE_EMAILED" : "STAFF_INVITE_EMAIL_FAILED",
                "User", id, "Invite email to " + email + ": " + result.detail());

        return new StaffInvite(id, password, result.delivered(), result.detail());
    }

    private String inviteBody(String fullName, String email, String password, UserRole role, String schoolName) {
        String school = schoolName == null || schoolName.isBlank() ? "your school" : schoolName;
        return """
                Hello %s,

                An account has been created for you at %s as %s.

                Username: %s
                Temporary password: %s

                Please sign in and change your password.
                """.formatted(fullName, school, role.name(), email, password);
    }

    private String generateTempPassword() {
        String chars = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnpqrstuvwxyz23456789";
        java.security.SecureRandom rnd = new java.security.SecureRandom();
        StringBuilder p = new StringBuilder();
        for (int i = 0; i < 10; i++) p.append(chars.charAt(rnd.nextInt(chars.length())));
        return p.append("!9").toString();
    }

    public UUID addStaff(String fullName, String email, String password, UserRole role,
                         UUID tenantId, UUID academicYearId, Authentication authentication) {
        if (!STAFF_ROLES.contains(role)) {
            throw new IllegalArgumentException("Staff role must be ADMIN, PRINCIPAL, TEACHER, or DRIVER");
        }
        // Canonicalised before the check, not after: the setter stores the
        // normalised form, so testing the raw one would let the same person be
        // invited twice in two different cases and fail on the unique index.
        email = User.normaliseEmail(email);
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
        // Approved by the act of inviting. This console is reachable only by an
        // ADMIN or PRINCIPAL -- the same two roles the approval queue waits
        // for -- so landing PENDING left the invitee waiting on a decision the
        // inviter had already made, and in a brand-new school that had not
        // appointed a principal yet, on nobody at all. The admin was shown a
        // temporary password and a status of "Active" while the account could
        // not be signed into; the sign-in failed as "Invalid username or
        // password", which named the wrong thing entirely.
        //
        // The queue is not dead: it still gates anything created without an
        // authenticated approver behind it, and decideStaff() is unchanged.
        staff.setApprovalStatus(User.ApprovalStatus.APPROVED);
        staffUserRepository.save(staff);
        auditLogService.log(authentication, "STAFF_INVITED", "User", staff.getId(),
                "Invited " + role.name() + " " + fullName + " (" + email + ") — approved by the inviter");
        return staff.getId();
    }
}

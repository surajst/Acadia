package com.schoolos.management;

import com.schoolos.common.AuditLogService;
import com.schoolos.user.CurrentUserService;
import com.schoolos.user.User;
import com.schoolos.user.UserRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Oversight + approval API for PRINCIPAL (and ADMIN, who can preview the same
 * view). Originally read-only; now also hosts PRINCIPAL's approval powers
 * (fee waivers, staff invites) added in this round, since they're the same
 * "oversight" surface — deliberately kept under /api/principal/** rather
 * than touching existing ADMIN-gated endpoints.
 */
@RestController
@RequestMapping("/api/principal")
@PreAuthorize("hasAnyRole('ADMIN', 'PRINCIPAL')")
public class PrincipalApiController {

    private final AdminProgressService adminProgressService;
    private final FeeManagementService feeManagementService;
    private final AttendanceRepository attendanceRepository;
    private final CurrentUserService currentUserService;
    private final FeeInvoiceRepository feeInvoiceRepository;
    private final StudentRepository studentRepository;
    private final UserRepository userRepository;
    private final AuditLogService auditLogService;

    public PrincipalApiController(AdminProgressService adminProgressService,
                                   FeeManagementService feeManagementService,
                                   AttendanceRepository attendanceRepository,
                                   CurrentUserService currentUserService,
                                   FeeInvoiceRepository feeInvoiceRepository,
                                   StudentRepository studentRepository,
                                   UserRepository userRepository,
                                   AuditLogService auditLogService) {
        this.adminProgressService = adminProgressService;
        this.feeManagementService = feeManagementService;
        this.attendanceRepository = attendanceRepository;
        this.currentUserService = currentUserService;
        this.feeInvoiceRepository = feeInvoiceRepository;
        this.studentRepository = studentRepository;
        this.userRepository = userRepository;
        this.auditLogService = auditLogService;
    }

    @GetMapping("/progress/school")
    public ResponseEntity<Map<String, Object>> getSchoolProgress() {
        return ResponseEntity.ok(adminProgressService.getSchoolWideProgress());
    }

    @GetMapping("/progress/class")
    public ResponseEntity<Map<String, Object>> getClassProgress(@RequestParam int standard) {
        return ResponseEntity.ok(adminProgressService.getClassProgress(standard));
    }

    @GetMapping("/fee-summary")
    public ResponseEntity<Map<String, Object>> getFeeSummary(Authentication authentication) {
        UUID tenantId = currentUserService.getCurrentTenantId(authentication).orElse(null);
        return ResponseEntity.ok(feeManagementService.getSchoolWideFeeSummary(tenantId));
    }

    @GetMapping("/attendance-summary")
    public ResponseEntity<Map<String, Object>> getAttendanceSummary() {
        LocalDate today = LocalDate.now();
        long present = attendanceRepository.countByAttendanceDateAndStatus(today, AttendanceStatus.PRESENT);
        long absent = attendanceRepository.countByAttendanceDateAndStatus(today, AttendanceStatus.ABSENT);
        long total = present + absent;
        int attendancePercent = total == 0 ? 0 : (int) Math.round((double) present * 100 / total);

        Map<String, Object> summary = new HashMap<>();
        summary.put("date", today.toString());
        summary.put("present", present);
        summary.put("absent", absent);
        summary.put("attendancePercent", attendancePercent);
        return ResponseEntity.ok(summary);
    }

    @GetMapping("/fees/waivers/pending")
    public ResponseEntity<?> getPendingWaivers(Authentication authentication) {
        UUID tenantId = currentUserService.getCurrentTenantId(authentication).orElse(null);
        List<FeeInvoice> pending = tenantId != null
                ? feeInvoiceRepository.findByTenantIdAndWaiverStatus(tenantId, FeeInvoice.FeeWaiverStatus.PENDING)
                : List.of();

        List<Map<String, Object>> rows = pending.stream().map(invoice -> {
            Student student = studentRepository.findById(invoice.getStudentId()).orElse(null);
            Map<String, Object> row = new HashMap<>();
            row.put("invoiceId", invoice.getId());
            row.put("studentName", student != null ? student.getFirstName() + " " + student.getLastName() : "Unknown");
            row.put("waiverAmount", invoice.getWaiverAmount());
            row.put("waiverReason", invoice.getWaiverReason());
            return row;
        }).collect(java.util.stream.Collectors.toList());

        return ResponseEntity.ok(rows);
    }

    @PostMapping("/fees/{invoiceId}/waiver/approve")
    public ResponseEntity<?> approveWaiver(@PathVariable UUID invoiceId, Authentication authentication) {
        try {
            FeeInvoice invoice = feeManagementService.decideWaiver(invoiceId, true, authentication);
            return ResponseEntity.ok(Map.of("status", "approved", "amountDue", invoice.getAmountDue()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/fees/{invoiceId}/waiver/reject")
    public ResponseEntity<?> rejectWaiver(@PathVariable UUID invoiceId, Authentication authentication) {
        try {
            feeManagementService.decideWaiver(invoiceId, false, authentication);
            return ResponseEntity.ok(Map.of("status", "rejected"));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/staff/pending")
    public ResponseEntity<?> getPendingStaff(Authentication authentication) {
        UUID tenantId = currentUserService.getCurrentTenantId(authentication).orElse(null);
        List<User> pending = tenantId != null
                ? userRepository.findByTenantIdAndApprovalStatus(tenantId, User.ApprovalStatus.PENDING)
                : List.of();

        List<Map<String, Object>> rows = pending.stream().map(u -> {
            Map<String, Object> row = new HashMap<>();
            row.put("id", u.getId());
            row.put("fullName", u.getFullName());
            row.put("email", u.getEmail());
            row.put("role", u.getRole().name());
            return row;
        }).collect(java.util.stream.Collectors.toList());

        return ResponseEntity.ok(rows);
    }

    @PostMapping("/staff/{userId}/approve")
    public ResponseEntity<?> approveStaff(@PathVariable UUID userId, Authentication authentication) {
        return decideStaff(userId, true, authentication);
    }

    @PostMapping("/staff/{userId}/reject")
    public ResponseEntity<?> rejectStaff(@PathVariable UUID userId, Authentication authentication) {
        return decideStaff(userId, false, authentication);
    }

    private ResponseEntity<?> decideStaff(UUID userId, boolean approve, Authentication authentication) {
        User staff = userRepository.findById(userId).orElse(null);
        if (staff == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "Staff member not found"));
        }
        if (staff.getApprovalStatus() != User.ApprovalStatus.PENDING) {
            return ResponseEntity.badRequest().body(Map.of("error", "This staff member has no pending approval"));
        }

        staff.setApprovalStatus(approve ? User.ApprovalStatus.APPROVED : User.ApprovalStatus.REJECTED);
        userRepository.save(staff);

        auditLogService.log(authentication, approve ? "STAFF_APPROVED" : "STAFF_REJECTED", "User", userId,
                (approve ? "Approved" : "Rejected") + " " + staff.getRole().name() + " " + staff.getFullName()
                        + " (" + staff.getEmail() + ")");

        return ResponseEntity.ok(Map.of("status", approve ? "approved" : "rejected"));
    }
}

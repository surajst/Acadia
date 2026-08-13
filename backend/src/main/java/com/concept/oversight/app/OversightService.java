package com.concept.oversight.app;

import com.concept.academics.StudentMetric;
import com.concept.academics.StudentMetricRepository;
import com.concept.common.AuditLogService;
import com.concept.common.NotificationDeliveryService;
import com.concept.shared.data.AcademicSubmission;
import com.concept.shared.data.AcademicSubmissionRepository;
import com.concept.oversight.app.AdminProgressService;
import com.concept.shared.data.AttendanceRepository;
import com.concept.shared.data.AttendanceStatus;
import com.concept.curriculum.data.Curriculum;
import com.concept.fees.data.FeeInvoice;
import com.concept.fees.data.FeeInvoiceRepository;
import com.concept.fees.app.FeeManagementService;
import com.concept.shared.data.Student;
import com.concept.oversight.data.StudentProgress;
import com.concept.oversight.data.StudentProgressRepository;
import com.concept.shared.data.StudentRepository;
import com.concept.user.CurrentUserService;
import com.concept.user.User;
import com.concept.user.UserRepository;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Application layer for the oversight + approval surface: principal/admin
 * progress and fee/attendance summaries, fee-waiver and staff-invite approvals,
 * and teacher progress/milestone approvals (with XP award + notification). Owns
 * tenant resolution, the cross-tenant staff-approval check, and the audit trail
 * so the web controllers stay thin (ADR 0001). Responses are Maps/Lists — no
 * entity reaches the web layer.
 */
@Service
public class OversightService {

    private final AdminProgressService adminProgressService;
    private final FeeManagementService feeManagementService;
    private final AttendanceRepository attendanceRepository;
    private final FeeInvoiceRepository feeInvoiceRepository;
    private final StudentRepository studentRepository;
    private final UserRepository userRepository;
    private final AuditLogService auditLogService;
    private final StudentProgressRepository studentProgressRepository;
    private final StudentMetricRepository studentMetricRepository;
    private final AcademicSubmissionRepository academicSubmissionRepository;
    private final NotificationDeliveryService notificationDeliveryService;
    private final CurrentUserService currentUserService;

    public OversightService(AdminProgressService adminProgressService,
                            FeeManagementService feeManagementService,
                            AttendanceRepository attendanceRepository,
                            FeeInvoiceRepository feeInvoiceRepository,
                            StudentRepository studentRepository,
                            UserRepository userRepository,
                            AuditLogService auditLogService,
                            StudentProgressRepository studentProgressRepository,
                            StudentMetricRepository studentMetricRepository,
                            AcademicSubmissionRepository academicSubmissionRepository,
                            NotificationDeliveryService notificationDeliveryService,
                            CurrentUserService currentUserService) {
        this.adminProgressService = adminProgressService;
        this.feeManagementService = feeManagementService;
        this.attendanceRepository = attendanceRepository;
        this.feeInvoiceRepository = feeInvoiceRepository;
        this.studentRepository = studentRepository;
        this.userRepository = userRepository;
        this.auditLogService = auditLogService;
        this.studentProgressRepository = studentProgressRepository;
        this.studentMetricRepository = studentMetricRepository;
        this.academicSubmissionRepository = academicSubmissionRepository;
        this.notificationDeliveryService = notificationDeliveryService;
        this.currentUserService = currentUserService;
    }

    // ─── Progress / summaries ───────────────────────────────────────────────

    public Map<String, Object> schoolProgress(Authentication authentication) {
        return adminProgressService.getSchoolWideProgress(tenant(authentication));
    }

    public Map<String, Object> classProgress(int standard, Authentication authentication) {
        return adminProgressService.getClassProgress(tenant(authentication), standard);
    }

    public Map<String, Object> feeSummary(Authentication authentication) {
        return feeManagementService.getSchoolWideFeeSummary(tenant(authentication));
    }

    public Map<String, Object> attendanceSummary() {
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
        return summary;
    }

    // ─── Fee waivers ────────────────────────────────────────────────────────

    public List<Map<String, Object>> pendingWaivers(Authentication authentication) {
        UUID tenantId = tenant(authentication);
        List<FeeInvoice> pending = tenantId != null
                ? feeInvoiceRepository.findByTenantIdAndWaiverStatus(tenantId, FeeInvoice.FeeWaiverStatus.PENDING)
                : List.of();
        return pending.stream().map(invoice -> {
            Student student = studentRepository.findByIdAndTenantId(invoice.getStudentId(), tenantId).orElse(null);
            Map<String, Object> row = new HashMap<>();
            row.put("invoiceId", invoice.getId());
            row.put("studentName", student != null ? student.getFirstName() + " " + student.getLastName() : "Unknown");
            row.put("waiverAmount", invoice.getWaiverAmount());
            row.put("waiverReason", invoice.getWaiverReason());
            return row;
        }).collect(Collectors.toList());
    }

    @Transactional
    public Map<String, Object> approveWaiver(UUID invoiceId, Authentication authentication) {
        try {
            FeeInvoice invoice = feeManagementService.decideWaiver(invoiceId, true, tenant(authentication), authentication);
            return Map.of("status", "approved", "amountDue", invoice.getAmountDue());
        } catch (IllegalArgumentException e) {
            throw OversightException.badRequest(e.getMessage());
        }
    }

    @Transactional
    public Map<String, Object> rejectWaiver(UUID invoiceId, Authentication authentication) {
        try {
            feeManagementService.decideWaiver(invoiceId, false, tenant(authentication), authentication);
            return Map.of("status", "rejected");
        } catch (IllegalArgumentException e) {
            throw OversightException.badRequest(e.getMessage());
        }
    }

    // ─── Staff approvals ────────────────────────────────────────────────────

    public List<Map<String, Object>> pendingStaff(Authentication authentication) {
        UUID tenantId = tenant(authentication);
        List<User> pending = tenantId != null
                ? userRepository.findByTenantIdAndApprovalStatus(tenantId, User.ApprovalStatus.PENDING)
                : List.of();
        return pending.stream().map(u -> {
            Map<String, Object> row = new HashMap<>();
            row.put("id", u.getId());
            row.put("fullName", u.getFullName());
            row.put("email", u.getEmail());
            row.put("role", u.getRole().name());
            return row;
        }).collect(Collectors.toList());
    }

    @Transactional
    public Map<String, Object> decideStaff(UUID userId, boolean approve, Authentication authentication) {
        UUID callerTenantId = tenant(authentication);
        User staff = userRepository.findByIdAndTenantId(userId, callerTenantId).orElse(null);
        if (staff == null) {
            throw OversightException.badRequest("Staff member not found");
        }
        if (staff.getApprovalStatus() != User.ApprovalStatus.PENDING) {
            throw OversightException.badRequest("This staff member has no pending approval");
        }
        staff.setApprovalStatus(approve ? User.ApprovalStatus.APPROVED : User.ApprovalStatus.REJECTED);
        userRepository.save(staff);
        auditLogService.log(authentication, approve ? "STAFF_APPROVED" : "STAFF_REJECTED", "User", userId,
                (approve ? "Approved" : "Rejected") + " " + staff.getRole().name() + " " + staff.getFullName()
                        + " (" + staff.getEmail() + ")");
        return Map.of("status", approve ? "approved" : "rejected");
    }

    // ─── Teacher progress approvals ─────────────────────────────────────────

    @Transactional
    public Map<String, Object> approveProgress(UUID studentProgressId) {
        try {
            StudentProgress progress = studentProgressRepository.findById(studentProgressId)
                    .orElseThrow(() -> new IllegalArgumentException("Invalid student progress ID"));
            if ("APPROVED".equals(progress.getStatus())) {
                throw new IllegalArgumentException("Progress is already approved");
            }
            progress.setStatus("APPROVED");
            progress.setCompleted(true);
            progress.setCompletedAt(LocalDateTime.now());
            studentProgressRepository.saveAndFlush(progress);

            Curriculum curriculum = progress.getCurriculum();
            Student student = progress.getStudent();
            StudentMetric metric = ensureMetric(student);
            metric.setSchoolXp((metric.getSchoolXp() != null ? metric.getSchoolXp() : 0) + curriculum.getXpReward());
            studentMetricRepository.saveAndFlush(metric);

            notificationDeliveryService.send(student.getFirstName() + " " + student.getLastName(),
                    "[ALERT WHATSAPP DISPATCH] Sending to Student " + student.getFirstName() + " " + student.getLastName()
                            + ": ✅ " + curriculum.getTopicName() + " verified! +" + curriculum.getXpReward() + " XP awarded.");
            return Map.of("message", "Progress approved successfully", "xpAwarded", curriculum.getXpReward());
        } catch (Exception e) {
            throw OversightException.badRequest(e.getMessage());
        }
    }

    @Transactional
    public Map<String, Object> rejectProgress(UUID studentProgressId, String reason) {
        try {
            StudentProgress progress = studentProgressRepository.findById(studentProgressId)
                    .orElseThrow(() -> new IllegalArgumentException("Invalid student progress ID"));
            progress.setStatus("REJECTED");
            progress.setRejectionReason(reason);
            studentProgressRepository.saveAndFlush(progress);

            Curriculum curriculum = progress.getCurriculum();
            Student student = progress.getStudent();
            String displayReason = (reason != null && !reason.trim().isEmpty()) ? reason : "No reason provided";
            notificationDeliveryService.send(student.getFirstName() + " " + student.getLastName(),
                    "[ALERT WHATSAPP DISPATCH] Sending to Student " + student.getFirstName() + " " + student.getLastName()
                            + ": ❌ " + curriculum.getTopicName() + " needs review — " + displayReason + ".");
            return Map.of("message", "Progress rejected");
        } catch (Exception e) {
            throw OversightException.badRequest(e.getMessage());
        }
    }

    // ─── Teacher milestone approvals ────────────────────────────────────────

    @Transactional
    public Map<String, Object> approveMilestone(UUID submissionId, Authentication authentication) {
        try {
            AcademicSubmission submission = academicSubmissionRepository.findById(submissionId)
                    .orElseThrow(() -> new IllegalArgumentException("Invalid submission ID"));
            if ("APPROVED".equals(submission.getStatus())) {
                throw new IllegalArgumentException("Submission is already approved");
            }
            submission.setStatus("APPROVED");
            academicSubmissionRepository.saveAndFlush(submission);

            Student student = studentRepository.findByIdAndTenantId(submission.getStudentId(), tenant(authentication))
                    .orElseThrow(() -> new IllegalArgumentException("Student not found"));
            StudentMetric metric = ensureMetric(student);
            metric.setSchoolXp((metric.getSchoolXp() != null ? metric.getSchoolXp() : 0) + submission.getXpBounty());
            studentMetricRepository.saveAndFlush(metric);
            return Map.of("message", "Milestone approved successfully");
        } catch (Exception e) {
            throw OversightException.badRequest(e.getMessage());
        }
    }

    @Transactional
    public Map<String, Object> rejectMilestone(UUID submissionId, String reason) {
        try {
            AcademicSubmission submission = academicSubmissionRepository.findById(submissionId)
                    .orElseThrow(() -> new IllegalArgumentException("Invalid submission ID"));
            submission.setStatus("REJECTED");
            submission.setRejectionReason(reason);
            academicSubmissionRepository.saveAndFlush(submission);
            return Map.of("message", "Milestone rejected");
        } catch (Exception e) {
            throw OversightException.badRequest(e.getMessage());
        }
    }

    // ─── helpers ────────────────────────────────────────────────────────────

    private UUID tenant(Authentication authentication) {
        return currentUserService.getCurrentTenantId(authentication).orElse(null);
    }

    private StudentMetric ensureMetric(Student student) {
        StudentMetric metric = studentMetricRepository.findByStudentId(student.getId()).orElse(null);
        if (metric == null) {
            metric = new StudentMetric();
            metric.setId(UUID.randomUUID());
            metric.setStudent(student);
            metric.setTenantId(student.getTenantId());
            metric.setAcademicYearId(student.getAcademicYearId());
            metric.setSchoolXp(0);
            metric.setParentXp(0);
            metric.setActiveStreak(0);
        }
        return metric;
    }
}

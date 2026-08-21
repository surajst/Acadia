package com.concept.fees.app;

import com.concept.fees.data.FeeStudentRepository;
import com.concept.fees.data.FeeInvoice;
import com.concept.fees.data.FeeInvoiceRepository;
import com.concept.fees.data.FeeTransaction;
import com.concept.fees.data.FeeTransactionRepository;
import com.concept.fees.app.FeeManagementService;
import com.concept.shared.data.Student;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Application layer for the admin fee ledger. Assembles the flat dashboard view
 * and fronts the write operations. The financial rules themselves live in
 * {@link FeeManagementService} (already tenant-scoped); this service adds the
 * tenant-scoped read/enrichment and keeps entities from reaching the web layer.
 */
@Service
public class FeeDashboardService {

    private final FeeManagementService feeManagementService;
    private final FeeInvoiceRepository feeInvoiceRepository;
    private final FeeStudentRepository studentRepository;
    private final FeeTransactionRepository feeTransactionRepository;
    private final com.concept.billing.app.InvoiceScheduleService invoiceScheduleService;

    public FeeDashboardService(FeeManagementService feeManagementService,
                               FeeInvoiceRepository feeInvoiceRepository,
                               FeeStudentRepository studentRepository,
                               FeeTransactionRepository feeTransactionRepository,
                               com.concept.billing.app.InvoiceScheduleService invoiceScheduleService) {
        this.feeManagementService = feeManagementService;
        this.feeInvoiceRepository = feeInvoiceRepository;
        this.studentRepository = studentRepository;
        this.feeTransactionRepository = feeTransactionRepository;
        this.invoiceScheduleService = invoiceScheduleService;
    }

    @Transactional(readOnly = true)
    public FeeDashboardView buildDashboard(UUID tenantId, int page, int size) {
        FeeManagementService.FeeSummary summary = feeManagementService.getFeeSummary(tenantId);

        Page<FeeInvoice> invoicePage = tenantId != null
                ? feeInvoiceRepository.findByTenantId(tenantId, PageRequest.of(page, size))
                : Page.empty();

        List<UUID> studentIds = invoicePage.getContent().stream()
                .map(FeeInvoice::getStudentId)
                .collect(Collectors.toList());

        // Tenant-scoped enrichment — never resolve an invoice's student outside the tenant.
        Map<UUID, Student> studentMap = (tenantId != null && !studentIds.isEmpty())
                ? studentRepository.findByIdInAndTenantId(studentIds, tenantId).stream()
                        .collect(Collectors.toMap(Student::getId, Function.identity()))
                : Map.of();

        List<FeeDashboardView.InvoiceRow> rows = invoicePage.getContent().stream()
                .map(inv -> toRow(inv, studentMap.get(inv.getStudentId()),
                        latestReversiblePayment(inv.getId(), tenantId)))
                .collect(Collectors.toList());

        List<FeeDashboardView.StudentOption> students = tenantId != null
                ? studentRepository.findByTenantId(tenantId).stream()
                        .map(s -> new FeeDashboardView.StudentOption(s.getId(), s.getFirstName(), s.getLastName()))
                        .collect(Collectors.toList())
                : List.of();

        return new FeeDashboardView(
                summary.totalExpected(),
                summary.totalCollected(),
                summary.totalOutstanding(),
                rows,
                students,
                page,
                invoicePage.getTotalPages(),
                invoicePage.getTotalElements(),
                size);
    }

    public void recordPayment(UUID invoiceId, BigDecimal amount, String paymentMode,
                              UUID tenantId, Authentication authentication) {
        feeManagementService.recordPayment(invoiceId, amount, paymentMode, tenantId, authentication);
    }

    public void createInvoice(UUID studentId, UUID tenantId, Authentication authentication) {
        createInvoice(studentId, tenantId, null, null, authentication);
    }

    /**
     * Raises the student's whole year at once now, not a single annual invoice:
     * one per instalment on the grade's plan, each with its own due date.
     */
    public void createInvoice(UUID studentId, UUID tenantId, java.math.BigDecimal overrideAmount,
                              String overrideReason, Authentication authentication) {
        invoiceScheduleService.generateForStudent(studentId, tenantId, overrideAmount, overrideReason, authentication);
    }

    public void reversePayment(java.util.UUID transactionId, String reason, java.util.UUID tenantId,
                               Authentication authentication) {
        feeManagementService.reversePayment(transactionId, reason, tenantId, authentication);
    }

    /** @return the resulting waiver status (e.g. PENDING), flattened to a string. */
    public String requestWaiver(UUID invoiceId, BigDecimal waiverAmount, String reason,
                                UUID tenantId, Authentication authentication) {
        FeeInvoice invoice = feeManagementService.requestWaiver(invoiceId, waiverAmount, reason, tenantId, authentication);
        return invoice.getWaiverStatus() != null ? invoice.getWaiverStatus().name() : "NONE";
    }

    /**
     * The most recent payment on this invoice that has not already been undone.
     * Reversal is offered one entry at a time, most recent first: correcting a
     * counter mistake almost always means the last thing typed, and offering
     * the whole history invites undoing the wrong one.
     */
    private FeeTransaction latestReversiblePayment(UUID invoiceId, UUID tenantId) {
        if (tenantId == null) {
            return null;
        }
        List<FeeTransaction> ledger =
                feeTransactionRepository.findByInvoiceIdAndTenantIdOrderByPaidAtAsc(invoiceId, tenantId);
        Set<UUID> alreadyReversed = ledger.stream()
                .map(FeeTransaction::getReversesTransactionId)
                .filter(java.util.Objects::nonNull)
                .collect(Collectors.toSet());
        for (int i = ledger.size() - 1; i >= 0; i--) {
            FeeTransaction t = ledger.get(i);
            if (!t.isReversal() && !alreadyReversed.contains(t.getId())) {
                return t;
            }
        }
        return null;
    }

    private FeeDashboardView.InvoiceRow toRow(FeeInvoice inv, Student student, FeeTransaction reversible) {
        String studentName = student != null
                ? (student.getFirstName() + " " + student.getLastName()).trim() : "Unknown Student";
        String initials = student != null
                ? initial(student.getFirstName()) + initial(student.getLastName()) : "ST";
        String rollNumber = student != null && student.getRollNumber() != null ? student.getRollNumber() : "--";
        String gradeLevel = student != null && student.getSchoolClass() != null
                ? student.getSchoolClass().getGradeLevel() : "—";
        String status = inv.getStatus() != null ? inv.getStatus().name() : "UNPAID";
        String waiverStatus = inv.getWaiverStatus() != null ? inv.getWaiverStatus().name() : "NONE";
        return new FeeDashboardView.InvoiceRow(
                inv.getId(), studentName, initials, rollNumber, gradeLevel, status,
                inv.getTotalAmount(), inv.getAmountPaid(), inv.getAmountDue(), waiverStatus,
                inv.getBaseAmount(), inv.getOverrideReason(), inv.getOverrideBy(),
                reversible != null ? reversible.getId() : null,
                reversible != null ? reversible.getAmountPaid() : null,
                inv.getInstalmentLabel(), inv.getDueDate(),
                inv.isOverdue(java.time.LocalDate.now()));
    }

    private String initial(String s) {
        return s != null && !s.isEmpty() ? s.substring(0, 1) : "";
    }
}

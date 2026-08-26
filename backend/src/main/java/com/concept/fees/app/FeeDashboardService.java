package com.concept.fees.app;

import com.concept.fees.data.ApprovalRequest;
import com.concept.fees.data.FeeStudentRepository;
import com.concept.fees.data.FeeInvoice;
import com.concept.fees.data.FeeInvoiceRepository;
import com.concept.fees.data.InvoiceLine;
import com.concept.fees.data.InvoiceLineRepository;
import com.concept.fees.data.FeeTransaction;
import com.concept.fees.data.FeeTransactionRepository;
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
    private final InvoiceLineRepository invoiceLineRepository;
    private final InvoiceScheduleService invoiceScheduleService;
    private final ApprovalService approvalService;

    public FeeDashboardService(FeeManagementService feeManagementService,
                               FeeInvoiceRepository feeInvoiceRepository,
                               FeeStudentRepository studentRepository,
                               FeeTransactionRepository feeTransactionRepository,
                               InvoiceLineRepository invoiceLineRepository,
                               InvoiceScheduleService invoiceScheduleService,
                               ApprovalService approvalService) {
        this.feeManagementService = feeManagementService;
        this.feeInvoiceRepository = feeInvoiceRepository;
        this.studentRepository = studentRepository;
        this.feeTransactionRepository = feeTransactionRepository;
        this.invoiceLineRepository = invoiceLineRepository;
        this.invoiceScheduleService = invoiceScheduleService;
        this.approvalService = approvalService;
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

        // Lines fetched in one query for the whole page rather than per row.
        List<UUID> invoiceIds = invoicePage.getContent().stream()
                .map(FeeInvoice::getId).collect(Collectors.toList());
        Map<UUID, List<FeeDashboardView.Line>> linesByInvoice =
                (tenantId != null && !invoiceIds.isEmpty())
                        ? invoiceLineRepository
                            .findByInvoiceIdInAndTenantIdOrderBySequenceNumberAsc(invoiceIds, tenantId)
                            .stream()
                            .collect(Collectors.groupingBy(
                                    InvoiceLine::getInvoiceId,
                                    Collectors.mapping(l -> new FeeDashboardView.Line(
                                            l.getDescription(), l.getAmount()), Collectors.toList())))
                        : Map.of();

        List<FeeDashboardView.InvoiceRow> rows = invoicePage.getContent().stream()
                .map(inv -> toRow(inv, studentMap.get(inv.getStudentId()),
                        latestReversiblePayment(inv.getId(), tenantId),
                        linesByInvoice.getOrDefault(inv.getId(), List.of())))
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

    public Integer recordPayment(UUID invoiceId, BigDecimal amount, String paymentMode,
                                 UUID tenantId, Authentication authentication) {
        return feeManagementService.recordPayment(invoiceId, amount, paymentMode, tenantId, authentication);
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

    /**
     * Asks for a reversal rather than performing one. Nothing moves until a
     * principal approves: this action un-records cash the school has already
     * receipted, which is the one fee action a single admin should not be able
     * to complete alone.
     *
     * @return the summary the admin is shown, describing what is now pending
     */
    public String requestPaymentReversal(java.util.UUID transactionId, String reason, java.util.UUID tenantId,
                                         Authentication authentication) {
        FeeTransaction original = feeManagementService.validateReversalRequest(transactionId, reason, tenantId);
        String summary = "Reverse a payment of " + original.getAmountPaid()
                + " (receipt #" + original.getReceiptNumber() + ") — " + reason.trim();
        approvalService.request(ApprovalRequest.Action.PAYMENT_REVERSAL,
                new PaymentReversalExecutor.Payload(transactionId, reason.trim()),
                summary, tenantId, authentication);
        return summary;
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

    private FeeDashboardView.InvoiceRow toRow(FeeInvoice inv, Student student, FeeTransaction reversible,
                                              List<FeeDashboardView.Line> lines) {
        String studentName = student != null
                ? (student.getFirstName() + " " + student.getLastName()).trim() : "Unknown Student";
        String initials = student != null
                ? initial(student.getFirstName()) + initial(student.getLastName()) : "ST";
        String rollNumber = student != null && student.getRollNumber() != null ? student.getRollNumber() : "--";
        String gradeLevel = student != null && student.getClassSection() != null
                ? student.getClassSection().getGradeName() : "—";
        String status = inv.getStatus() != null ? inv.getStatus().name() : "UNPAID";
        String waiverStatus = inv.getWaiverStatus() != null ? inv.getWaiverStatus().name() : "NONE";
        return new FeeDashboardView.InvoiceRow(
                inv.getId(), studentName, initials, rollNumber, gradeLevel, status,
                inv.getTotalAmount(), inv.getAmountPaid(), inv.getAmountDue(), waiverStatus,
                inv.getBaseAmount(), inv.getOverrideReason(), inv.getOverrideBy(),
                reversible != null ? reversible.getId() : null,
                reversible != null ? reversible.getAmountPaid() : null,
                inv.getInstalmentLabel(), inv.getDueDate(),
                inv.isOverdue(java.time.LocalDate.now()),
                lines);
    }

    private String initial(String s) {
        return s != null && !s.isEmpty() ? s.substring(0, 1) : "";
    }
}

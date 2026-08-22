package com.concept.fees.app;
import com.concept.fees.data.FeeTransactionRepository;
import com.concept.fees.data.FeeTransaction;
import com.concept.fees.data.FeeInvoiceRepository;
import com.concept.fees.data.FeeInvoice;
import com.concept.shared.data.StudentRepository;
import com.concept.shared.data.Student;

import com.concept.common.AuditLogService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import com.concept.user.CurrentUserService;

import java.util.UUID;

@Service
public class FeeManagementService {

    @Autowired
    private StudentRepository studentRepository;


    @Autowired
    private FeeInvoiceRepository feeInvoiceRepository;

    @Autowired
    private FeeTransactionRepository feeTransactionRepository;

    @Autowired
    private AuditLogService auditLogService;

    @Autowired
    private CurrentUserService currentUserService;

    /**
     * Read-only school-wide fee rollup — used by the PRINCIPAL oversight
     * dashboard. Aggregates existing FeeInvoice rows; no new business logic.
     */
    /**
     * Canonical fee roll-up for a tenant. This is the single place the
     * expected/collected/outstanding totals and collection percentage are
     * computed, so the admin dashboard, the principal summary, and any future
     * consumer share one implementation instead of each looping invoices.
     */
    public record FeeSummary(int totalInvoices,
                             BigDecimal totalExpected,
                             BigDecimal totalCollected,
                             BigDecimal totalOutstanding,
                             int collectionPercent,
                             long outstandingInvoiceCount) {}

    public FeeSummary getFeeSummary(UUID tenantId) {
        List<FeeInvoice> invoices = tenantId != null ? feeInvoiceRepository.findByTenantId(tenantId) : List.of();

        BigDecimal totalExpected = BigDecimal.ZERO;
        BigDecimal totalCollected = BigDecimal.ZERO;
        BigDecimal totalOutstanding = BigDecimal.ZERO;
        long overdueCount = 0;

        for (FeeInvoice invoice : invoices) {
            if (invoice.getTotalAmount() != null) totalExpected = totalExpected.add(invoice.getTotalAmount());
            if (invoice.getAmountPaid() != null) totalCollected = totalCollected.add(invoice.getAmountPaid());
            if (invoice.getAmountDue() != null) totalOutstanding = totalOutstanding.add(invoice.getAmountDue());
            if (invoice.getStatus() != FeeInvoice.FeeStatus.PAID) overdueCount++;
        }

        int collectionPercent = totalExpected.compareTo(BigDecimal.ZERO) > 0
                ? totalCollected.multiply(BigDecimal.valueOf(100)).divide(totalExpected, 0, java.math.RoundingMode.HALF_UP).intValue()
                : 0;

        return new FeeSummary(invoices.size(), totalExpected, totalCollected, totalOutstanding, collectionPercent, overdueCount);
    }

    /** Backwards-compatible map view of {@link #getFeeSummary} for JSON/API consumers. */
    public java.util.Map<String, Object> getSchoolWideFeeSummary(UUID tenantId) {
        FeeSummary s = getFeeSummary(tenantId);
        java.util.Map<String, Object> summary = new java.util.HashMap<>();
        summary.put("totalInvoices", s.totalInvoices());
        summary.put("totalExpected", s.totalExpected());
        summary.put("totalCollected", s.totalCollected());
        summary.put("totalOutstanding", s.totalOutstanding());
        summary.put("collectionPercent", s.collectionPercent());
        summary.put("outstandingInvoiceCount", s.outstandingInvoiceCount());
        return summary;
    }

    @Transactional
    public Integer recordPayment(UUID invoiceId, BigDecimal paymentAmount, String mode, UUID currentTenantId, Authentication authentication) {
        if (paymentAmount == null || paymentAmount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Payment amount must be greater than zero");
        }

        FeeInvoice invoice = feeInvoiceRepository.findByIdAndTenantId(invoiceId, currentTenantId)
            .orElseThrow(() -> new IllegalArgumentException("FeeInvoice not found with ID: " + invoiceId));

        BigDecimal currentPaid = invoice.getAmountPaid() != null ? invoice.getAmountPaid() : BigDecimal.ZERO;

        // Refuse more than is owed. The form sets max=remainingDue in the
        // browser, but client-side validation is not validation: a direct POST
        // used to be accepted, and updateBalances clamps amountDue at zero, so
        // the invoice looked settled while amountPaid quietly held money the
        // school could not account for.
        BigDecimal due = invoice.getAmountDue() != null ? invoice.getAmountDue() : BigDecimal.ZERO;
        if (paymentAmount.compareTo(due) > 0) {
            throw new IllegalArgumentException(
                    "Payment of " + paymentAmount + " is more than the " + due + " outstanding on this invoice.");
        }

        invoice.setAmountPaid(currentPaid.add(paymentAmount));
        invoice.updateBalances();
        feeInvoiceRepository.saveAndFlush(invoice);

        FeeTransaction txn = new FeeTransaction();
        txn.setId(UUID.randomUUID());
        txn.setInvoiceId(invoiceId);
        txn.setAmountPaid(paymentAmount);
        txn.setPaymentMode(mode);
        txn.setPaidAt(LocalDateTime.now());
        
        // Satisfy BaseTenantEntity keys
        txn.setTenantId(invoice.getTenantId());
        txn.setAcademicYearId(invoice.getAcademicYearId());

        // Sequential per school per year, starting at 1 -- what a receipt
        // needs to mean anything at a counter. Computed just before the write
        // rather than reserved in advance: two admins recording a payment in
        // the same instant is rare enough here that a lost race can simply
        // fail the whole write (nothing partially applied, since the invoice
        // update above shares this transaction) and ask for a retry, rather
        // than justifying a locking scheme this console does not need.
        Integer maxSoFar = feeTransactionRepository.findMaxReceiptNumber(
                invoice.getTenantId(), invoice.getAcademicYearId());
        txn.setReceiptNumber((maxSoFar == null ? 0 : maxSoFar) + 1);

        feeTransactionRepository.saveAndFlush(txn);

        auditLogService.log(authentication, "FEE_PAYMENT_RECORDED", "FeeInvoice", invoiceId,
                "Recorded payment of " + paymentAmount + " (" + mode + ") on invoice " + invoiceId
                        + " — receipt #" + txn.getReceiptNumber());

        return txn.getReceiptNumber();
    }

    @Transactional
    public FeeInvoice requestWaiver(UUID invoiceId, BigDecimal waiverAmount, String reason, UUID currentTenantId, Authentication authentication) {
        if (waiverAmount == null || waiverAmount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Waiver amount must be greater than zero");
        }

        FeeInvoice invoice = feeInvoiceRepository.findByIdAndTenantId(invoiceId, currentTenantId)
                .orElseThrow(() -> new IllegalArgumentException("FeeInvoice not found with ID: " + invoiceId));

        invoice.setWaiverAmount(waiverAmount);
        invoice.setWaiverReason(reason);
        invoice.setWaiverStatus(FeeInvoice.FeeWaiverStatus.PENDING);
        invoice.setWaiverRequestedByUserId(currentUserId(authentication));
        feeInvoiceRepository.saveAndFlush(invoice);

        auditLogService.log(authentication, "FEE_WAIVER_REQUESTED", "FeeInvoice", invoiceId,
                "Requested a waiver of " + waiverAmount + " on invoice " + invoiceId + " (" + reason + ")");

        return invoice;
    }

    @Transactional
    public FeeInvoice decideWaiver(UUID invoiceId, boolean approve, UUID currentTenantId, Authentication authentication) {
        FeeInvoice invoice = feeInvoiceRepository.findByIdAndTenantId(invoiceId, currentTenantId)
                .orElseThrow(() -> new IllegalArgumentException("FeeInvoice not found with ID: " + invoiceId));

        if (invoice.getWaiverStatus() != FeeInvoice.FeeWaiverStatus.PENDING) {
            throw new IllegalArgumentException("This invoice has no pending waiver request");
        }

        // The approve endpoint is open to ADMIN as well as PRINCIPAL, and the
        // request endpoint is ADMIN-only -- so without this the requester is
        // also an eligible approver and the two-step flow decides nothing.
        // Rejecting your own request is allowed: withdrawing costs the school
        // nothing, and forbidding it would strand a request its author regrets.
        UUID actorId = currentUserId(authentication);
        if (approve && actorId != null && actorId.equals(invoice.getWaiverRequestedByUserId())) {
            throw new IllegalArgumentException(
                    "You requested this waiver, so it needs a different admin or the principal to approve it.");
        }

        invoice.setWaiverStatus(approve ? FeeInvoice.FeeWaiverStatus.APPROVED : FeeInvoice.FeeWaiverStatus.REJECTED);
        invoice.updateBalances();
        feeInvoiceRepository.saveAndFlush(invoice);

        auditLogService.log(authentication, approve ? "FEE_WAIVER_APPROVED" : "FEE_WAIVER_REJECTED",
                "FeeInvoice", invoiceId,
                (approve ? "Approved" : "Rejected") + " waiver of " + invoice.getWaiverAmount() + " on invoice " + invoiceId);

        return invoice;
    }

    /**
     * Dev/seed-only bulk generator, gated behind app.dev-mode by its callers.
     *
     * <p>Seeds three instalments per student rather than one annual invoice, so
     * dev data has the shape real data now has -- due dates, instalment labels,
     * and more than one row per family.
     */
    @Transactional
    public void initializeInvoices() {
        if (feeInvoiceRepository.count() > 0) {
            return;
        }
        List<Student> students = studentRepository.findAll();
        System.out.println(">> FeeManagementService -> Generating baseline invoices for " + students.size() + " students...");

        String[] labels = {"Term 1", "Term 2", "Term 3"};
        BigDecimal[] amounts = {
                new BigDecimal("8000.00"), new BigDecimal("6000.00"), new BigDecimal("6000.00")
        };
        int[] offsets = {0, 120, 240};

        List<FeeInvoice> invoiceList = new java.util.ArrayList<>();
        java.time.LocalDate start = java.time.LocalDate.now().withDayOfMonth(1);
        for (Student student : students) {
            for (int i = 0; i < labels.length; i++) {
                FeeInvoice invoice = new FeeInvoice();
                invoice.setId(UUID.randomUUID());
                invoice.setStudentId(student.getId());
                invoice.setTotalAmount(amounts[i]);
                invoice.setAmountPaid(BigDecimal.ZERO);
                invoice.setInstalmentLabel(labels[i]);
                invoice.setDueDate(start.plusDays(offsets[i]));
                invoice.setTenantId(student.getTenantId());
                invoice.setAcademicYearId(student.getAcademicYearId());
                invoice.updateBalances();
                invoiceList.add(invoice);
            }
        }
        feeInvoiceRepository.saveAll(invoiceList);
        feeInvoiceRepository.flush();
        System.out.println(">> FeeManagementService -> Baseline FeeInvoices created successfully.");
    }


    /**
     * Undoes a payment by recording its opposite, never by editing or deleting
     * the original.
     *
     * <p>Money handled at a school counter gets mistyped, and until now the only
     * fix was editing the database directly. A ledger that can be rewritten is
     * not a ledger, so the mistake and its correction both stay on the invoice:
     * "we took 200,000 and gave 180,000 back" is a different fact from "we took
     * 20,000", and a family asking why their receipt does not match needs the
     * first one to still exist.
     */
    @Transactional
    public void reversePayment(UUID transactionId, String reason, UUID currentTenantId,
                               Authentication authentication) {
        String why = reason == null ? "" : reason.trim();
        if (why.isEmpty()) {
            throw new IllegalArgumentException("A reason is required to reverse a payment.");
        }

        FeeTransaction original = feeTransactionRepository.findByIdAndTenantId(transactionId, currentTenantId)
                .orElseThrow(() -> new IllegalArgumentException("Payment not found."));

        if (original.isReversal()) {
            throw new IllegalArgumentException("That entry is itself a reversal and cannot be reversed.");
        }
        if (feeTransactionRepository.existsByReversesTransactionId(transactionId)) {
            throw new IllegalArgumentException("That payment has already been reversed.");
        }

        FeeInvoice invoice = feeInvoiceRepository.findByIdAndTenantId(original.getInvoiceId(), currentTenantId)
                .orElseThrow(() -> new IllegalArgumentException("Invoice not found."));

        FeeTransaction reversal = new FeeTransaction();
        reversal.setId(UUID.randomUUID());
        reversal.setInvoiceId(original.getInvoiceId());
        reversal.setAmountPaid(original.getAmountPaid().negate());
        reversal.setPaymentMode("REVERSAL");
        reversal.setPaidAt(LocalDateTime.now());
        reversal.setReversesTransactionId(original.getId());
        reversal.setNote(why);
        reversal.setTenantId(invoice.getTenantId());
        reversal.setAcademicYearId(invoice.getAcademicYearId());
        feeTransactionRepository.saveAndFlush(reversal);

        BigDecimal paid = invoice.getAmountPaid() != null ? invoice.getAmountPaid() : BigDecimal.ZERO;
        invoice.setAmountPaid(paid.subtract(original.getAmountPaid()).max(BigDecimal.ZERO));
        invoice.updateBalances();
        feeInvoiceRepository.saveAndFlush(invoice);

        auditLogService.log(authentication, "FEE_PAYMENT_REVERSED", "FeeInvoice", invoice.getId(),
                "Reversed a payment of " + original.getAmountPaid() + " — " + why);
    }

    /**
     * The acting user's id, or null when it cannot be resolved. A null here
     * means the self-approval check cannot fire -- see decideWaiver, where the
     * comparison is skipped rather than guessed at.
     */
    private UUID currentUserId(Authentication authentication) {
        return currentUserService.getCurrentUser(authentication).map(u -> u.getId()).orElse(null);
    }
}

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

    /**
     * Who an invoice belongs to, for the audit trail.
     *
     * <p>The summaries printed the invoice's UUID, which tells a head teacher
     * reading the log nothing at all -- and the log is the one place they
     * would go to answer "who paid what". Falls back to the id only when the
     * student cannot be resolved, which is better than an empty sentence.
     */
    private String who(FeeInvoice invoice) {
        if (invoice == null) {
            return "an unknown invoice";
        }
        // Scoped to the invoice's own tenant: a bare findById on a tenant-scoped
        // repository would resolve a name from another school.
        return studentRepository.findByIdAndTenantId(invoice.getStudentId(), invoice.getTenantId())
                .map(st -> {
                    String roll = st.getRollNumber();
                    return (st.getFirstName() + " " + st.getLastName()).trim()
                            + (roll == null || roll.isBlank() ? "" : " (" + roll + ")");
                })
                .orElse("invoice " + invoice.getId());
    }


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
            // A withdrawn bill is not money the school expects, is owed, or
            // failed to collect. Leaving it in would keep it in the collection
            // percentage, which is the whole reason a cancellation exists
            // rather than a waiver.
            if (!invoice.isCountable()) continue;
            if (invoice.getTotalAmount() != null) totalExpected = totalExpected.add(invoice.getTotalAmount());
            if (invoice.getAmountPaid() != null) totalCollected = totalCollected.add(invoice.getAmountPaid());
            if (invoice.getAmountDue() != null) totalOutstanding = totalOutstanding.add(invoice.getAmountDue());
            if (invoice.getStatus() != FeeInvoice.FeeStatus.PAID) overdueCount++;
        }

        int collectionPercent = totalExpected.compareTo(BigDecimal.ZERO) > 0
                ? totalCollected.multiply(BigDecimal.valueOf(100)).divide(totalExpected, 0, java.math.RoundingMode.HALF_UP).intValue()
                : 0;

        long counted = invoices.stream().filter(FeeInvoice::isCountable).count();
        return new FeeSummary((int) counted, totalExpected, totalCollected, totalOutstanding,
                collectionPercent, overdueCount);
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
        return recordPayment(invoiceId, paymentAmount, mode, null, currentTenantId, authentication);
    }

    /**
     * @param reference the bank's reference for this payment -- a UPI id, a
     *                  cheque number -- or null for cash. Kept so a receipt can
     *                  be matched against the statement later.
     */
    public Integer recordPayment(UUID invoiceId, BigDecimal paymentAmount, String mode, String reference,
                                 UUID currentTenantId, Authentication authentication) {
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
        txn.setPaymentReference(reference == null || reference.isBlank() ? null : reference.trim());
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
                "Recorded payment of " + paymentAmount + " (" + mode + ") for " + who(invoice)
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
                "Requested a waiver of " + waiverAmount + " for " + who(invoice) + " (" + reason + ")");

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
                (approve ? "Approved" : "Rejected") + " waiver of " + invoice.getWaiverAmount()
                        + " for " + who(invoice));

        return invoice;
    }

    /**
     * Withdraw an invoice that should never have been raised.
     *
     * <p>Nothing could do this. The only actions on an invoice were record a
     * payment, request a waiver and reverse a payment, so a bill raised in
     * error stayed on the family's ledger and in the school's outstanding
     * total for good. A waiver is the wrong instrument for it: a waiver records
     * that the school forgave a debt it was owed, which is a different fact
     * from the debt never being owed, and it leaves the original amount in the
     * expected total.
     *
     * <p>Only with nothing paid. Once money has changed hands the invoice is
     * evidence of a receipt, and cancelling it would leave a payment attached
     * to a withdrawn bill; that case is a payment reversal first, which is
     * already a principal-approved action. The message says so rather than just
     * refusing.
     *
     * <p>ADMIN and PRINCIPAL only, and a reason is required -- a cancellation
     * with no name and no reason against it is indistinguishable from a bug
     * later on.
     */
    @Transactional
    public FeeInvoice cancelInvoice(UUID invoiceId, String reason, UUID currentTenantId,
                                    Authentication authentication) {
        if (reason == null || reason.isBlank()) {
            throw new IllegalArgumentException("A reason is required to cancel an invoice.");
        }

        com.concept.user.User actor = currentUserService.getCurrentUser(authentication).orElse(null);
        // Checked here rather than left to the URL rule alone: this is the only
        // action that makes a bill disappear from the ledger, and the rule has
        // to hold for any caller that reaches the service.
        if (actor == null || actor.getRole() == null
                || (actor.getRole() != com.concept.user.UserRole.ADMIN
                    && actor.getRole() != com.concept.user.UserRole.PRINCIPAL)) {
            throw new org.springframework.security.access.AccessDeniedException(
                    "Only an admin or the principal can cancel an invoice.");
        }

        FeeInvoice invoice = feeInvoiceRepository.findByIdAndTenantId(invoiceId, currentTenantId)
                .orElseThrow(() -> new IllegalArgumentException("FeeInvoice not found with ID: " + invoiceId));

        if (invoice.isCancelled()) {
            throw new IllegalArgumentException("This invoice is already cancelled.");
        }

        BigDecimal paid = invoice.getAmountPaid() == null ? BigDecimal.ZERO : invoice.getAmountPaid();
        if (paid.compareTo(BigDecimal.ZERO) > 0) {
            throw new IllegalArgumentException(
                    "This invoice has " + paid + " paid against it, so it cannot be cancelled. "
                            + "Reverse the payment first, then cancel it.");
        }

        // Order matters: cancelledAt is what makes the invoice cancelled, and
        // updateBalances reads it to stop the amounts putting the balance back.
        // Set it before touching them.
        invoice.setCancelledAt(java.time.Instant.now());
        invoice.setAmountPaid(BigDecimal.ZERO);
        invoice.setAmountDue(BigDecimal.ZERO);
        invoice.setCancelledBy(authentication != null && authentication.getName() != null
                ? authentication.getName() : "system");
        invoice.setCancellationReason(reason.trim());
        feeInvoiceRepository.saveAndFlush(invoice);

        auditLogService.log(authentication, "FEE_INVOICE_CANCELLED", "FeeInvoice", invoiceId,
                "Cancelled " + label(invoice) + " of " + invoice.getTotalAmount()
                        + " for " + who(invoice) + " - " + reason.trim());

        return invoice;
    }

    /** How an invoice reads in a log line: its instalment name, or just "invoice". */
    private String label(FeeInvoice invoice) {
        String instalment = invoice.getInstalmentLabel();
        return instalment == null || instalment.isBlank() ? "invoice" : instalment;
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
    /**
     * Checks a proposed reversal without writing anything.
     *
     * <p>Run both when the admin asks and again when the principal approves.
     * At request time so a hopeless request is refused while someone can still
     * fix it; at approval time because a payment can be reversed by another
     * route while this one waits in the queue.
     *
     * @return the payment that would be reversed, which the summary quotes
     */
    public FeeTransaction validateReversalRequest(UUID transactionId, String reason, UUID currentTenantId) {
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
        return original;
    }

    /**
     * Carries out a reversal. Named "Approved" because reaching it means a
     * principal has already agreed: the admin-facing route raises an
     * ApprovalRequest instead of calling this. The guards below run here, at
     * approval time, so a payment reversed by another route in the meantime is
     * caught rather than double-reversed.
     */
    public void reversePaymentApproved(UUID transactionId, String reason, UUID currentTenantId,
                                       Authentication authentication) {
        String why = reason == null ? "" : reason.trim();
        FeeTransaction original = validateReversalRequest(transactionId, reason, currentTenantId);

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

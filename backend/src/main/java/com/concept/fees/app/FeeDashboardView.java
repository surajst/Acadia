package com.concept.fees.app;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/** Flat, presentation-ready data for the admin fee ledger. No entities. */
public record FeeDashboardView(
        BigDecimal totalExpected,
        BigDecimal totalCollected,
        BigDecimal totalOutstanding,
        List<InvoiceRow> invoices,
        List<StudentOption> students,
        int currentPage,
        int totalPages,
        long totalItems,
        int pageSize
) {
    /** One row of the ledger table — invoice joined to its student, flattened. */
    public record InvoiceRow(
            UUID invoiceId,
            String studentName,
            String initials,
            String rollNumber,
            String gradeLevel,
            String status,        // PAID | PARTIALLY_PAID | UNPAID | CANCELLED
            BigDecimal totalAmount,
            BigDecimal amountPaid,
            BigDecimal amountDue,
            String waiverStatus,  // NONE | PENDING | APPROVED | REJECTED
            // Null unless the invoice was billed at something other than the
            // grade's fees; then it carries what the fee structure said, why it
            // was departed from, and who decided.
            BigDecimal baseAmount,
            String overrideReason,
            String overrideBy,
            // The most recent payment that has not already been reversed, so the
            // ledger can offer to undo it. Null when there is nothing to undo.
            UUID reversiblePaymentId,
            BigDecimal reversiblePaymentAmount,
            // Which instalment this is and when it falls due. A ledger of three
            // rows per family is unreadable without them.
            String instalmentLabel,
            java.time.LocalDate dueDate,
            boolean overdue,
            // What the invoice is actually charging for. Empty for a plan
            // instalment, whose label already says what it is.
            List<Line> lines,
            // Why this bill was withdrawn. Null on every invoice that stands.
            String cancellationReason
    ) {
        public boolean overridden() {
            return baseAmount != null;
        }

        public boolean hasReversiblePayment() {
            return reversiblePaymentId != null;
        }

        public boolean hasLines() {
            return lines != null && !lines.isEmpty();
        }

        public boolean cancelled() {
            return "CANCELLED".equals(status);
        }

        /**
         * Whether to offer the Cancel action on this row.
         *
         * <p>Not once anything has been paid: the invoice is then evidence of a
         * receipt, and that case is a payment reversal first. The server decides
         * this too -- the button only saves the admin a refusal.
         */
        public boolean cancellable() {
            return !cancelled()
                    && (amountPaid == null || amountPaid.compareTo(BigDecimal.ZERO) == 0);
        }
    }

    /** A student for the "create invoice" picker. */
    public record StudentOption(UUID id, String firstName, String lastName) {}

    /** One charge on an invoice. */
    public record Line(String description, BigDecimal amount) {}
}

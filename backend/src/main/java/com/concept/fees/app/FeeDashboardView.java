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
            String status,        // PAID | PARTIALLY_PAID | UNPAID
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
            boolean overdue
    ) {
        public boolean overridden() {
            return baseAmount != null;
        }

        public boolean hasReversiblePayment() {
            return reversiblePaymentId != null;
        }
    }

    /** A student for the "create invoice" picker. */
    public record StudentOption(UUID id, String firstName, String lastName) {}
}

package com.concept.fees.app;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * One child's fee position, as a family sees it.
 *
 * <p>Lives in the fees module rather than in either parent surface because both
 * the web portal and the mobile app show it, and two copies of "what does this
 * family owe" would drift. The accessor names are what the Thymeleaf template
 * binds to, so they are part of this record's contract.
 *
 * @param overdueCount instalments already past their due date and not settled
 * @param nextDueLabel e.g. "Term 2", or null when nothing is outstanding
 * @param dues         only the instalments still owing, soonest first
 * @param payments     every receipt against this child, newest first
 */
public record StudentFeeSummary(BigDecimal totalBilled,
                                BigDecimal totalPaid,
                                BigDecimal totalDue,
                                int instalmentCount,
                                int paidCount,
                                int overdueCount,
                                String nextDueLabel,
                                LocalDate nextDueDate,
                                BigDecimal nextDueAmount,
                                List<DueLine> dues,
                                List<PaymentLine> payments) {

    /** True when there is nothing left to pay. */
    public boolean settled() {
        return totalDue == null || totalDue.signum() <= 0;
    }

    /**
     * One instalment that still owes something.
     *
     * <p>Carries its invoice id so a parent can act on a specific instalment
     * rather than on "the fees" in general -- asking for help with one term is
     * a different request from asking for help with the year.
     */
    public record DueLine(java.util.UUID invoiceId, String label, BigDecimal amount,
                          LocalDate dueDate, boolean overdue) {}

    /**
     * One row of the family's receipt history.
     *
     * @param reversal true for the negative row written when a payment is
     *                 undone. Kept rather than filtered: a parent who saw a
     *                 receipt appear and then disappear is owed the explanation,
     *                 and dropping it makes the running total look wrong.
     */
    public record PaymentLine(LocalDate paidOn, BigDecimal amount, String mode,
                              Integer receiptNumber, String label, boolean reversal) {}
}

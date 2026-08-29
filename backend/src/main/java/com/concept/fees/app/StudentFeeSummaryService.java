package com.concept.fees.app;

import com.concept.fees.data.FeeInvoice;
import com.concept.fees.data.FeeInvoiceRepository;
import com.concept.fees.data.FeeTransaction;
import com.concept.fees.data.FeeTransactionRepository;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Sums a family's invoices into something a parent can act on.
 *
 * <p>Shared by the web portal and the mobile app so the two cannot disagree
 * about what a family owes. Caller-supplied student ids are expected to be
 * already established as the caller's own -- both parent surfaces derive them
 * from {@code findByParentsContaining} -- and the queries here are confined to
 * the tenant on top of that. Neither check alone survives the other being
 * refactored away, and a fee ledger must not cross a school boundary.
 */
@Service
public class StudentFeeSummaryService {

    private final FeeInvoiceRepository feeInvoiceRepository;
    private final FeeTransactionRepository feeTransactionRepository;

    public StudentFeeSummaryService(FeeInvoiceRepository feeInvoiceRepository,
                                    FeeTransactionRepository feeTransactionRepository) {
        this.feeInvoiceRepository = feeInvoiceRepository;
        this.feeTransactionRepository = feeTransactionRepository;
    }

    /**
     * Fee position per child, keyed by student id.
     *
     * <p>A child with no invoice raised is absent from the map rather than
     * present with zeroes: "nothing billed" and "billed and settled" are
     * different things to say to a parent.
     */
    public Map<UUID, StudentFeeSummary> forStudents(Collection<UUID> studentIds, UUID tenantId) {
        Map<UUID, StudentFeeSummary> out = new LinkedHashMap<>();
        if (studentIds == null || studentIds.isEmpty() || tenantId == null) {
            return out;
        }

        List<FeeInvoice> invoices = feeInvoiceRepository.findByStudentIdInAndTenantId(studentIds, tenantId);
        if (invoices.isEmpty()) {
            return out;
        }

        Map<UUID, List<FeeInvoice>> byStudent = new LinkedHashMap<>();
        List<UUID> invoiceIds = new ArrayList<>();
        for (FeeInvoice inv : invoices) {
            byStudent.computeIfAbsent(inv.getStudentId(), k -> new ArrayList<>()).add(inv);
            invoiceIds.add(inv.getId());
        }

        // One query for the whole family's receipts: a parent with three
        // children should not cost three round trips.
        Map<UUID, List<FeeTransaction>> txByInvoice = new LinkedHashMap<>();
        for (FeeTransaction tx : feeTransactionRepository
                .findByInvoiceIdInAndTenantIdOrderByPaidAtDesc(invoiceIds, tenantId)) {
            txByInvoice.computeIfAbsent(tx.getInvoiceId(), k -> new ArrayList<>()).add(tx);
        }

        LocalDate today = LocalDate.now();
        for (Map.Entry<UUID, List<FeeInvoice>> e : byStudent.entrySet()) {
            out.put(e.getKey(), summarise(e.getValue(), txByInvoice, today));
        }
        return out;
    }

    private StudentFeeSummary summarise(List<FeeInvoice> own,
                                        Map<UUID, List<FeeTransaction>> txByInvoice,
                                        LocalDate today) {
        BigDecimal billed = BigDecimal.ZERO;
        BigDecimal paid = BigDecimal.ZERO;
        BigDecimal due = BigDecimal.ZERO;
        int paidCount = 0;
        int overdue = 0;
        FeeInvoice next = null;
        List<StudentFeeSummary.DueLine> dues = new ArrayList<>();
        List<StudentFeeSummary.PaymentLine> payments = new ArrayList<>();

        for (FeeInvoice inv : own) {
            billed = billed.add(nz(inv.getTotalAmount()));
            paid = paid.add(nz(inv.getAmountPaid()));
            BigDecimal owing = nz(inv.getAmountDue());
            due = due.add(owing);

            for (FeeTransaction tx : txByInvoice.getOrDefault(inv.getId(), List.of())) {
                payments.add(new StudentFeeSummary.PaymentLine(
                        tx.getPaidAt() == null ? null : tx.getPaidAt().toLocalDate(),
                        tx.getAmountPaid(), tx.getPaymentMode(), tx.getReceiptNumber(),
                        inv.getInstalmentLabel(), tx.isReversal()));
            }

            if (owing.signum() <= 0) {
                paidCount++;
                continue;
            }
            boolean isOverdue = inv.getDueDate() != null && inv.getDueDate().isBefore(today);
            if (isOverdue) {
                overdue++;
            }
            dues.add(new StudentFeeSummary.DueLine(
                    inv.getId(), inv.getInstalmentLabel(), owing, inv.getDueDate(), isOverdue));
            if (next == null || earlier(inv.getDueDate(), next.getDueDate())) {
                next = inv;
            }
        }

        // Soonest first for what is owed; newest first for what has been paid.
        dues.sort((a, b) -> compareNullsLast(a.dueDate(), b.dueDate()));
        payments.sort((a, b) -> compareNullsLast(b.paidOn(), a.paidOn()));

        return new StudentFeeSummary(billed, paid, due, own.size(), paidCount, overdue,
                next == null ? null : next.getInstalmentLabel(),
                next == null ? null : next.getDueDate(),
                next == null ? null : nz(next.getAmountDue()),
                dues, payments);
    }

    private static int compareNullsLast(LocalDate a, LocalDate b) {
        if (a == null) {
            return b == null ? 0 : 1;
        }
        if (b == null) {
            return -1;
        }
        return a.compareTo(b);
    }

    private static BigDecimal nz(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }

    /** A missing due date sorts last, so a dated instalment is always preferred. */
    private static boolean earlier(LocalDate candidate, LocalDate current) {
        if (candidate == null) {
            return false;
        }
        return current == null || candidate.isBefore(current);
    }
}

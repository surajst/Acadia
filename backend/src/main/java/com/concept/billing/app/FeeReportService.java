package com.concept.billing.app;

import com.concept.fees.data.FeeInvoice;
import com.concept.fees.data.FeeInvoiceRepository;
import com.concept.fees.data.FeeTransaction;
import com.concept.fees.data.FeeTransactionRepository;
import com.concept.shared.data.Student;
import com.concept.shared.data.StudentRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Two read-only views that due dates and receipt numbers made possible.
 *
 * <p>Fee plans made "overdue" computable and payments made "receipted"
 * meaningful, but neither was useful until something actually showed them.
 * This is that something: who has not paid, and what the school actually
 * collected.
 */
@Service
public class FeeReportService {

    private final FeeInvoiceRepository feeInvoiceRepository;
    private final FeeTransactionRepository feeTransactionRepository;
    private final StudentRepository studentRepository;

    public FeeReportService(FeeInvoiceRepository feeInvoiceRepository,
                            FeeTransactionRepository feeTransactionRepository,
                            StudentRepository studentRepository) {
        this.feeInvoiceRepository = feeInvoiceRepository;
        this.feeTransactionRepository = feeTransactionRepository;
        this.studentRepository = studentRepository;
    }

    /** One family behind on a payment. */
    public record DefaulterRow(UUID studentId, String studentName, String rollNumber, String gradeLevel,
                               String instalmentLabel, java.math.BigDecimal amountDue,
                               LocalDate dueDate, long daysOverdue) {}

    /**
     * Every unpaid or partially-paid invoice already past its due date,
     * worst-overdue first -- the order a school actually chases arrears in.
     * Spans every academic year the school has billed, not just the current
     * one: an invoice from last year that was never settled is still owed.
     */
    @Transactional(readOnly = true)
    public List<DefaulterRow> defaulters(UUID tenantId) {
        if (tenantId == null) {
            return List.of();
        }
        LocalDate today = LocalDate.now();
        List<FeeInvoice> overdue = feeInvoiceRepository.findByTenantId(tenantId).stream()
                .filter(inv -> inv.isOverdue(today))
                .collect(Collectors.toList());

        Map<UUID, Student> studentMap = studentsFor(overdue.stream().map(FeeInvoice::getStudentId), tenantId);

        return overdue.stream()
                .map(inv -> toDefaulterRow(inv, studentMap.get(inv.getStudentId()), today))
                .sorted(Comparator.comparingLong(DefaulterRow::daysOverdue).reversed())
                .collect(Collectors.toList());
    }

    /** One payment actually collected, with the receipt number it was given. */
    public record ReceiptRow(Integer receiptNumber, String studentName, String rollNumber,
                             java.math.BigDecimal amount, String paymentMode,
                             java.time.LocalDateTime paidAt, boolean reversed) {}

    /**
     * Every receipted payment for one academic year, in the order they were
     * issued -- the school's own day-book. Reversals do not appear as their
     * own row; a reversed original is flagged instead, so the receipt count
     * still matches what the numbers on paper say.
     */
    @Transactional(readOnly = true)
    public List<ReceiptRow> collectionReport(UUID tenantId, UUID academicYearId) {
        if (tenantId == null || academicYearId == null) {
            return List.of();
        }
        List<FeeTransaction> receipted = feeTransactionRepository
                .findByTenantIdAndAcademicYearIdAndReceiptNumberIsNotNullOrderByReceiptNumberAsc(
                        tenantId, academicYearId);

        // A receipted transaction is "reversed" when some other row's
        // reversesTransactionId points at it -- the reversal itself carries
        // no receipt number of its own, so this is checked per row rather
        // than joined against a second receipted-only query.
        java.util.Set<UUID> reversedIds = receipted.stream()
                .filter(t -> feeTransactionRepository.existsByReversesTransactionId(t.getId()))
                .map(FeeTransaction::getId)
                .collect(Collectors.toSet());

        Map<UUID, FeeInvoice> invoiceMap = invoicesFor(
                receipted.stream().map(FeeTransaction::getInvoiceId), tenantId);
        Map<UUID, Student> studentMap = studentsFor(
                invoiceMap.values().stream().map(FeeInvoice::getStudentId), tenantId);

        return receipted.stream()
                .map(txn -> {
                    FeeInvoice invoice = invoiceMap.get(txn.getInvoiceId());
                    Student student = invoice != null ? studentMap.get(invoice.getStudentId()) : null;
                    return new ReceiptRow(
                            txn.getReceiptNumber(),
                            student != null ? student.getFirstName() + " " + student.getLastName() : "Unknown Student",
                            student != null && student.getRollNumber() != null ? student.getRollNumber() : "--",
                            txn.getAmountPaid(), txn.getPaymentMode(), txn.getPaidAt(),
                            reversedIds.contains(txn.getId()));
                })
                .collect(Collectors.toList());
    }

    private DefaulterRow toDefaulterRow(FeeInvoice inv, Student student, LocalDate today) {
        String name = student != null ? student.getFirstName() + " " + student.getLastName() : "Unknown Student";
        String roll = student != null && student.getRollNumber() != null ? student.getRollNumber() : "--";
        String grade = student != null && student.getSchoolClass() != null
                ? student.getSchoolClass().getGradeLevel() : "--";
        long days = java.time.temporal.ChronoUnit.DAYS.between(inv.getDueDate(), today);
        return new DefaulterRow(inv.getStudentId(), name, roll, grade,
                inv.getInstalmentLabel(), inv.getAmountDue(), inv.getDueDate(), days);
    }

    private Map<UUID, Student> studentsFor(java.util.stream.Stream<UUID> ids, UUID tenantId) {
        List<UUID> distinctIds = ids.filter(java.util.Objects::nonNull).distinct().collect(Collectors.toList());
        if (distinctIds.isEmpty()) {
            return Map.of();
        }
        return studentRepository.findByIdInAndTenantId(distinctIds, tenantId).stream()
                .collect(Collectors.toMap(Student::getId, Function.identity()));
    }

    private Map<UUID, FeeInvoice> invoicesFor(java.util.stream.Stream<UUID> ids, UUID tenantId) {
        List<UUID> distinctIds = ids.filter(java.util.Objects::nonNull).distinct().collect(Collectors.toList());
        if (distinctIds.isEmpty()) {
            return Map.of();
        }
        return distinctIds.stream()
                .map(id -> feeInvoiceRepository.findByIdAndTenantId(id, tenantId))
                .filter(java.util.Optional::isPresent)
                .map(java.util.Optional::get)
                .collect(Collectors.toMap(FeeInvoice::getId, Function.identity()));
    }
}

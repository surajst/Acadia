package com.concept.fees.app;

import com.concept.fees.data.InvoiceLine;
import com.concept.fees.data.InvoiceLineRepository;
import com.concept.common.AuditLogService;
import com.concept.fees.data.FeeInvoice;
import com.concept.fees.data.FeeInvoiceRepository;
import com.concept.shared.data.Student;
import com.concept.shared.data.StudentRepository;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Raises an invoice an admin composed by hand.
 *
 * <p>Not every charge belongs to a fee plan. A school trip, a replacement
 * textbook, a late exam entry — these arrive without warning and in amounts
 * nobody configured in advance. Until now the only way to bill for one was to
 * override a tuition invoice, which made the total wrong in a way no later
 * reader could unpick.
 *
 * <p>Deliberately free-form: the admin types each line. A fixed catalogue of
 * fee heads would cover the common cases and then be worked around for
 * everything else, which is how the hardcoded 20,000 and the
 * override-as-catch-all both happened.
 */
@Service
public class CustomInvoiceService {

    private final FeeInvoiceRepository feeInvoiceRepository;
    private final InvoiceLineRepository invoiceLineRepository;
    private final StudentRepository studentRepository;
    private final AuditLogService auditLogService;

    public CustomInvoiceService(FeeInvoiceRepository feeInvoiceRepository,
                                InvoiceLineRepository invoiceLineRepository,
                                StudentRepository studentRepository,
                                AuditLogService auditLogService) {
        this.feeInvoiceRepository = feeInvoiceRepository;
        this.invoiceLineRepository = invoiceLineRepository;
        this.studentRepository = studentRepository;
        this.auditLogService = auditLogService;
    }

    /** One line as the caller supplies it, before it becomes a row. */
    public record LineSpec(String description, BigDecimal amount) {}

    /**
     * Raises one invoice per student named, each carrying the same lines.
     *
     * <p>Billing a whole class for a trip is one action to an admin, and making
     * them repeat it forty times is how half a class ends up uninvoiced.
     *
     * @param dueDate when it falls due; required, because an invoice with no due
     *                date can never be overdue and so never appears in the
     *                arrears the school chases
     */
    @Transactional
    public List<FeeInvoice> raise(List<UUID> studentIds, List<LineSpec> lines, LocalDate dueDate,
                                  UUID tenantId, Authentication authentication) {
        if (studentIds == null || studentIds.isEmpty()) {
            throw new IllegalArgumentException("Choose at least one student to invoice.");
        }
        if (dueDate == null) {
            throw new IllegalArgumentException("A due date is required.");
        }
        List<LineSpec> cleaned = validateLines(lines);
        BigDecimal total = cleaned.stream()
                .map(LineSpec::amount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        List<FeeInvoice> raised = new ArrayList<>();
        for (UUID studentId : studentIds) {
            Student student = studentRepository.findByIdAndTenantId(studentId, tenantId)
                    .orElseThrow(() -> new IllegalArgumentException("Student not found: " + studentId));

            FeeInvoice invoice = new FeeInvoice();
            invoice.setId(UUID.randomUUID());
            invoice.setStudentId(student.getId());
            invoice.setTenantId(student.getTenantId());
            invoice.setAcademicYearId(student.getAcademicYearId());
            invoice.setTotalAmount(total);
            invoice.setAmountPaid(BigDecimal.ZERO);
            invoice.setDueDate(dueDate);
            invoice.setSource("CUSTOM");
            // The label is what the ledger shows, so use the first line rather
            // than a generic word: "Annual trip" reads; "Custom invoice" does not.
            invoice.setInstalmentLabel(cleaned.get(0).description());
            invoice.updateBalances();
            feeInvoiceRepository.saveAndFlush(invoice);

            int sequence = 1;
            for (LineSpec spec : cleaned) {
                InvoiceLine line = new InvoiceLine();
                line.setId(UUID.randomUUID());
                line.setTenantId(student.getTenantId());
                line.setAcademicYearId(student.getAcademicYearId());
                line.setInvoiceId(invoice.getId());
                line.setSequenceNumber(sequence++);
                line.setDescription(spec.description());
                line.setAmount(spec.amount());
                invoiceLineRepository.saveAndFlush(line);
            }

            raised.add(invoice);
        }

        auditLogService.log(authentication, "FEE_CUSTOM_INVOICE_RAISED", "FeeInvoice",
                raised.get(0).getId(),
                "Raised " + raised.size() + " custom invoice(s) of " + total
                        + " due " + dueDate + " — " + cleaned.get(0).description());

        return raised;
    }

    private List<LineSpec> validateLines(List<LineSpec> lines) {
        if (lines == null || lines.isEmpty()) {
            throw new IllegalArgumentException("An invoice needs at least one line.");
        }
        List<LineSpec> cleaned = new ArrayList<>();
        for (LineSpec spec : lines) {
            String description = spec.description() == null ? "" : spec.description().trim();
            if (description.isEmpty()) {
                // A line with an amount and no description is exactly the
                // unexplained charge this feature exists to prevent.
                throw new IllegalArgumentException("Every line needs a description.");
            }
            if (spec.amount() == null || spec.amount().signum() < 0) {
                throw new IllegalArgumentException("Line amounts cannot be negative.");
            }
            cleaned.add(new LineSpec(description, spec.amount()));
        }
        BigDecimal total = cleaned.stream().map(LineSpec::amount).reduce(BigDecimal.ZERO, BigDecimal::add);
        if (total.signum() <= 0) {
            throw new IllegalArgumentException("The invoice total must be more than zero.");
        }
        return cleaned;
    }
}

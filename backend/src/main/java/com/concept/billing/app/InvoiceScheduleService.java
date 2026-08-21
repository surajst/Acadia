package com.concept.billing.app;

import com.concept.billing.data.FeePlan;
import com.concept.billing.data.FeePlanInstalment;
import com.concept.billing.data.FeePlanInstalmentRepository;
import com.concept.billing.data.FeePlanRepository;
import com.concept.common.AuditLogService;
import com.concept.billing.app.FeePlanMissingException;
import com.concept.fees.data.FeeInvoice;
import com.concept.fees.data.FeeInvoiceRepository;
import com.concept.shared.data.Student;
import com.concept.shared.data.StudentRepository;
import com.concept.tenant.AcademicYear;
import com.concept.tenant.AcademicYearRepository;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Turns a student's fee plan into the invoices they will actually be sent.
 *
 * <p>One invoice per instalment, each with its own due date. Before this, a year
 * was a single invoice for the whole annual amount — which no family pays at
 * once, so the ledger described a way of collecting money that nobody uses.
 */
@Service
public class InvoiceScheduleService {

    private final FeePlanRepository feePlanRepository;
    private final FeePlanInstalmentRepository instalmentRepository;
    private final FeeInvoiceRepository feeInvoiceRepository;
    private final StudentRepository studentRepository;
    private final AcademicYearRepository academicYearRepository;
    private final AuditLogService auditLogService;

    public InvoiceScheduleService(FeePlanRepository feePlanRepository,
                                  FeePlanInstalmentRepository instalmentRepository,
                                  FeeInvoiceRepository feeInvoiceRepository,
                                  StudentRepository studentRepository,
                                  AcademicYearRepository academicYearRepository,
                                  AuditLogService auditLogService) {
        this.feePlanRepository = feePlanRepository;
        this.instalmentRepository = instalmentRepository;
        this.feeInvoiceRepository = feeInvoiceRepository;
        this.studentRepository = studentRepository;
        this.academicYearRepository = academicYearRepository;
        this.auditLogService = auditLogService;
    }

    /**
     * Raises the whole year's invoices for one student.
     *
     * @param overrideAnnualAmount optional total to bill instead of the plan's,
     *                             for a concession; instalments scale to it
     * @param overrideReason       required whenever an override is given
     */
    @Transactional
    public List<FeeInvoice> generateForStudent(UUID studentId, UUID tenantId,
                                               BigDecimal overrideAnnualAmount, String overrideReason,
                                               Authentication authentication) {
        Student student = studentRepository.findByIdAndTenantId(studentId, tenantId)
                .orElseThrow(() -> new IllegalArgumentException("Student not found with ID: " + studentId));

        String gradeLevel = student.getSchoolClass() != null ? student.getSchoolClass().getGradeLevel()
                : student.getClassSection() != null ? student.getClassSection().getGradeName()
                : null;
        if (gradeLevel == null) {
            throw new FeePlanMissingException(
                    "This student is not in a class yet, so there is no grade level to bill from.");
        }

        FeePlan plan = feePlanRepository
                .findByTenantIdAndAcademicYearIdAndGradeLevel(tenantId, student.getAcademicYearId(), gradeLevel)
                .orElseThrow(() -> new FeePlanMissingException(
                        "No fee plan is configured for " + gradeLevel
                                + ". Set it under Fee Settings before billing this student."));

        List<FeePlanInstalment> instalments =
                instalmentRepository.findByFeePlanIdAndTenantIdOrderBySequenceNumberAsc(plan.getId(), tenantId);
        if (instalments.isEmpty()) {
            throw new FeePlanMissingException(
                    "The plan for " + gradeLevel + " has no instalments, so there is nothing to bill.");
        }

        // Raising a second schedule on top of an existing one would double the
        // family's bill, so refuse rather than silently add to it.
        boolean alreadyBilled = feeInvoiceRepository.findByTenantId(tenantId).stream()
                .anyMatch(inv -> studentId.equals(inv.getStudentId())
                        && inv.getFeePlanInstalmentId() != null);
        if (alreadyBilled) {
            throw new IllegalArgumentException("This student already has a fee schedule for this year.");
        }

        BigDecimal billedTotal = plan.getAnnualAmount();
        boolean overridden = overrideAnnualAmount != null
                && overrideAnnualAmount.compareTo(plan.getAnnualAmount()) != 0;
        if (overridden) {
            if (overrideAnnualAmount.signum() < 0) {
                throw new IllegalArgumentException("An invoice amount cannot be negative.");
            }
            if (overrideReason == null || overrideReason.isBlank()) {
                throw new IllegalArgumentException(
                        "A reason is required when billing an amount other than the grade's fees.");
            }
            billedTotal = overrideAnnualAmount;
        }

        LocalDate billingStart = billingStartFor(student);
        List<FeeInvoice> created = new ArrayList<>();
        BigDecimal allocated = BigDecimal.ZERO;

        for (int i = 0; i < instalments.size(); i++) {
            FeePlanInstalment instalment = instalments.get(i);

            BigDecimal amount;
            if (!overridden) {
                amount = instalment.getAmount();
            } else if (i == instalments.size() - 1) {
                // The last instalment absorbs the rounding remainder, so the
                // parts always add up to exactly what the family was told.
                amount = billedTotal.subtract(allocated);
            } else {
                amount = billedTotal.multiply(instalment.getAmount())
                        .divide(plan.getAnnualAmount(), 2, RoundingMode.HALF_UP);
            }
            allocated = allocated.add(amount);

            FeeInvoice invoice = new FeeInvoice();
            invoice.setId(UUID.randomUUID());
            invoice.setStudentId(student.getId());
            invoice.setTenantId(student.getTenantId());
            invoice.setAcademicYearId(student.getAcademicYearId());
            invoice.setTotalAmount(amount);
            invoice.setAmountPaid(BigDecimal.ZERO);
            invoice.setDueDate(billingStart.plusDays(instalment.getDueOffsetDays()));
            invoice.setFeePlanInstalmentId(instalment.getId());
            invoice.setInstalmentLabel(instalment.getLabel());
            if (overridden) {
                invoice.setBaseAmount(instalment.getAmount());
                invoice.setOverrideReason(overrideReason.trim());
                invoice.setOverrideBy(authentication != null && authentication.getName() != null
                        ? authentication.getName() : "system");
            }
            invoice.updateBalances();
            created.add(feeInvoiceRepository.saveAndFlush(invoice));
        }

        auditLogService.log(authentication,
                overridden ? "FEE_SCHEDULE_OVERRIDDEN" : "FEE_SCHEDULE_GENERATED",
                "Student", student.getId(),
                "Raised " + created.size() + " invoices totalling " + billedTotal
                        + " for " + student.getFirstName() + " " + student.getLastName()
                        + (overridden ? " - " + overrideReason.trim() : ""));

        return created;
    }

    /**
     * The date a student's schedule counts from.
     *
     * <p>The academic year's start for someone who began with it; their own
     * admission date for someone who joined later. Without this a September
     * admission inherits April's schedule and is billed immediately for
     * instalments that fell due before the child was enrolled.
     */
    private LocalDate billingStartFor(Student student) {
        LocalDate yearStart = academicYearRepository.findById(student.getAcademicYearId())
                .map(AcademicYear::getStartDate)
                .orElse(null);

        LocalDate admission = student.getAdmissionDate();
        if (admission == null) {
            if (yearStart == null) {
                throw new IllegalArgumentException(
                        "This academic year has no start date, so instalment dates cannot be worked out.");
            }
            return yearStart;
        }
        // An admission recorded before the year opened still bills from the year.
        return yearStart != null && admission.isBefore(yearStart) ? yearStart : admission;
    }
}

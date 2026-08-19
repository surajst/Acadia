package com.concept.fees.app;
import com.concept.fees.data.FeeTransactionRepository;
import com.concept.fees.data.FeeTransaction;
import com.concept.fees.data.FeeStructureRepository;
import com.concept.fees.data.FeeStructure;
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
import java.util.UUID;

@Service
public class FeeManagementService {

    @Autowired
    private StudentRepository studentRepository;

    @Autowired
    private FeeStructureRepository feeStructureRepository;

    @Autowired
    private FeeInvoiceRepository feeInvoiceRepository;

    @Autowired
    private FeeTransactionRepository feeTransactionRepository;

    @Autowired
    private AuditLogService auditLogService;

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
    public void recordPayment(UUID invoiceId, BigDecimal paymentAmount, String mode, UUID currentTenantId, Authentication authentication) {
        if (paymentAmount == null || paymentAmount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Payment amount must be greater than zero");
        }

        FeeInvoice invoice = feeInvoiceRepository.findByIdAndTenantId(invoiceId, currentTenantId)
            .orElseThrow(() -> new IllegalArgumentException("FeeInvoice not found with ID: " + invoiceId));

        BigDecimal currentPaid = invoice.getAmountPaid() != null ? invoice.getAmountPaid() : BigDecimal.ZERO;
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

        feeTransactionRepository.saveAndFlush(txn);

        auditLogService.log(authentication, "FEE_PAYMENT_RECORDED", "FeeInvoice", invoiceId,
                "Recorded payment of " + paymentAmount + " (" + mode + ") on invoice " + invoiceId);
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

        invoice.setWaiverStatus(approve ? FeeInvoice.FeeWaiverStatus.APPROVED : FeeInvoice.FeeWaiverStatus.REJECTED);
        invoice.updateBalances();
        feeInvoiceRepository.saveAndFlush(invoice);

        auditLogService.log(authentication, approve ? "FEE_WAIVER_APPROVED" : "FEE_WAIVER_REJECTED",
                "FeeInvoice", invoiceId,
                (approve ? "Approved" : "Rejected") + " waiver of " + invoice.getWaiverAmount() + " on invoice " + invoiceId);

        return invoice;
    }

    /**
     * Real, per-student invoice creation for production use. initializeInvoices()
     * below is a dev/seed-only bulk generator (gated behind app.dev-mode via its
     * only callers) and was the sole way an invoice ever got created — meaning a
     * real school had no way to bill a real student. This is the fix.
     */
    @Transactional
    public FeeInvoice createInvoiceForStudent(UUID studentId, UUID currentTenantId, Authentication authentication) {
        return createInvoiceForStudent(studentId, currentTenantId, null, null, authentication);
    }

    /**
     * Raises an invoice, optionally at an amount other than the grade's fee
     * structure.
     *
     * <p>An override is only accepted with a reason. A different number on its
     * own is not an audit trail: months later nobody can tell a sibling
     * discount from a fee change from a typo, and the difference matters
     * because a family is being asked to pay it. What the structure said is
     * kept alongside, so the departure stays visible rather than being
     * flattened into a single figure.
     *
     * <p>Note this is a one-off amount for THIS invoice. Changing what a grade
     * costs is a different act with different consequences and lives in fee
     * settings.
     */
    @Transactional
    public FeeInvoice createInvoiceForStudent(UUID studentId, UUID currentTenantId,
                                              BigDecimal overrideAmount, String overrideReason,
                                              Authentication authentication) {
        Student student = studentRepository.findByIdAndTenantId(studentId, currentTenantId)
                .orElseThrow(() -> new IllegalArgumentException("Student not found with ID: " + studentId));

        String gradeLevel = student.getSchoolClass() != null ? student.getSchoolClass().getGradeLevel()
                : student.getClassSection() != null ? student.getClassSection().getGradeName()
                : null;

        if (gradeLevel == null) {
            throw new FeeStructureMissingException(
                    "This student is not in a class yet, so there is no grade level to price the invoice from.");
        }

        // Priced from the student's own academic year, not the school's current
        // one: an invoice raised late for last year must still use last year's
        // fees.
        FeeStructure structure = feeStructureRepository
                .findByTenantIdAndAcademicYearIdAndGradeLevel(
                        currentTenantId, student.getAcademicYearId(), gradeLevel)
                .orElseThrow(() -> new FeeStructureMissingException(
                        "No fee structure is configured for " + gradeLevel
                                + ". Set it under Fee Settings before invoicing this student."));

        BigDecimal structureTotal = structure.getTuitionFee().add(structure.getTermFee());

        FeeInvoice invoice = new FeeInvoice();
        invoice.setId(UUID.randomUUID());
        invoice.setStudentId(student.getId());
        invoice.setAmountPaid(BigDecimal.ZERO);
        invoice.setTenantId(student.getTenantId());
        invoice.setAcademicYearId(student.getAcademicYearId());

        boolean overridden = overrideAmount != null
                && overrideAmount.compareTo(structureTotal) != 0;
        if (overridden) {
            if (overrideAmount.signum() < 0) {
                throw new IllegalArgumentException("An invoice amount cannot be negative.");
            }
            String reason = overrideReason == null ? "" : overrideReason.trim();
            if (reason.isEmpty()) {
                throw new IllegalArgumentException(
                        "A reason is required when billing an amount other than the grade's fees.");
            }
            invoice.setTotalAmount(overrideAmount);
            invoice.setBaseAmount(structureTotal);
            invoice.setOverrideReason(reason);
            invoice.setOverrideBy(actorName(authentication));
        } else {
            invoice.setTotalAmount(structureTotal);
        }

        invoice.updateBalances();
        feeInvoiceRepository.saveAndFlush(invoice);

        String who = student.getFirstName() + " " + student.getLastName();
        if (overridden) {
            auditLogService.log(authentication, "FEE_INVOICE_OVERRIDDEN", "FeeInvoice", invoice.getId(),
                    "Created invoice for " + who + " at " + invoice.getTotalAmount()
                            + " instead of " + structureTotal + " — " + invoice.getOverrideReason());
        } else {
            auditLogService.log(authentication, "FEE_INVOICE_CREATED", "FeeInvoice", invoice.getId(),
                    "Created invoice for " + who + " (total " + invoice.getTotalAmount() + ")");
        }

        return invoice;
    }

    private String actorName(Authentication authentication) {
        return authentication != null && authentication.getName() != null
                ? authentication.getName() : "system";
    }

    @Transactional
    public void initializeInvoices() {
        UUID defaultTenantId = UUID.fromString("00000000-0000-0000-0000-000000000000");
        UUID defaultAcademicYearId = UUID.fromString("00000000-0000-0000-0000-111111111111");

        // 1. Seed FeeStructures if empty
        if (feeStructureRepository.count() == 0) {
            System.out.println(">> FeeManagementService -> Seeding dynamic FeeStructures...");
            
            String[] grades = {"KG", "Grade 1", "Grade 2", "Grade 3", "Grade 4", "Grade 5", "Grade 6", "Grade 7", "Grade 8", "Grade 9", "Grade 10"};
            BigDecimal[][] fees = {
                {new BigDecimal("8000.00"), new BigDecimal("2000.00")},  // KG
                {new BigDecimal("10000.00"), new BigDecimal("3000.00")}, // Grade 1
                {new BigDecimal("10000.00"), new BigDecimal("3000.00")}, // Grade 2
                {new BigDecimal("11000.00"), new BigDecimal("3500.00")}, // Grade 3
                {new BigDecimal("11000.00"), new BigDecimal("3500.00")}, // Grade 4
                {new BigDecimal("12000.00"), new BigDecimal("4000.00")}, // Grade 5
                {new BigDecimal("15000.00"), new BigDecimal("5000.00")}, // Grade 6 (15k Tuition, 5k Term as per prompt)
                {new BigDecimal("15000.00"), new BigDecimal("5000.00")}, // Grade 7
                {new BigDecimal("16000.00"), new BigDecimal("5500.00")}, // Grade 8
                {new BigDecimal("18000.00"), new BigDecimal("6000.00")}, // Grade 9
                {new BigDecimal("20000.00"), new BigDecimal("7000.00")}  // Grade 10
            };

            for (int i = 0; i < grades.length; i++) {
                FeeStructure struct = new FeeStructure(
                    UUID.randomUUID(),
                    grades[i],
                    fees[i][0],
                    fees[i][1]
                );
                struct.setTenantId(defaultTenantId);
                struct.setAcademicYearId(defaultAcademicYearId);
                feeStructureRepository.save(struct);
            }
            feeStructureRepository.flush();
            System.out.println(">> FeeManagementService -> 11 FeeStructures seeded successfully.");
        }

        // 2. Seed FeeInvoices if empty
        if (feeInvoiceRepository.count() == 0) {
            List<Student> students = studentRepository.findAll();
            System.out.println(">> FeeManagementService -> Generating baseline invoices for " + students.size() + " students...");

            List<FeeStructure> allStructures = feeStructureRepository.findAll();
            java.util.Map<String, FeeStructure> structureMap = new java.util.HashMap<>();
            for (FeeStructure fs : allStructures) {
                if (fs.getGradeLevel() != null) {
                    structureMap.put(fs.getGradeLevel(), fs);
                }
            }

            List<FeeInvoice> invoiceList = new java.util.ArrayList<>();
            for (Student student : students) {
                String gradeLevel = "Grade 6";
                if (student.getSchoolClass() != null) {
                    gradeLevel = student.getSchoolClass().getGradeLevel();
                } else if (student.getClassSection() != null) {
                    gradeLevel = student.getClassSection().getGradeName();
                }

                FeeStructure structure = structureMap.get(gradeLevel);
                if (structure == null) {
                    structure = new FeeStructure();
                    structure.setTuitionFee(new BigDecimal("15000.00"));
                    structure.setTermFee(new BigDecimal("5000.00"));
                }

                BigDecimal total = structure.getTuitionFee().add(structure.getTermFee());

                FeeInvoice invoice = new FeeInvoice();
                invoice.setId(UUID.randomUUID());
                invoice.setStudentId(student.getId());
                invoice.setTotalAmount(total);
                invoice.setAmountPaid(BigDecimal.ZERO);
                
                UUID tId = student.getTenantId() != null ? student.getTenantId() : defaultTenantId;
                UUID ayId = student.getAcademicYearId() != null ? student.getAcademicYearId() : defaultAcademicYearId;
                invoice.setTenantId(tId);
                invoice.setAcademicYearId(ayId);
                
                invoice.updateBalances();

                invoiceList.add(invoice);
            }
            feeInvoiceRepository.saveAll(invoiceList);
            feeInvoiceRepository.flush();
            System.out.println(">> FeeManagementService -> Baseline FeeInvoices created successfully.");
        }
    }
}

package com.schoolos.management;

import com.schoolos.common.AuditLogService;
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
    public java.util.Map<String, Object> getSchoolWideFeeSummary(UUID tenantId) {
        List<FeeInvoice> invoices = tenantId != null ? feeInvoiceRepository.findByTenantId(tenantId) : List.of();

        BigDecimal totalExpected = BigDecimal.ZERO;
        BigDecimal totalCollected = BigDecimal.ZERO;
        long overdueCount = 0;

        for (FeeInvoice invoice : invoices) {
            if (invoice.getTotalAmount() != null) totalExpected = totalExpected.add(invoice.getTotalAmount());
            if (invoice.getAmountPaid() != null) totalCollected = totalCollected.add(invoice.getAmountPaid());
            if (invoice.getStatus() != FeeInvoice.FeeStatus.PAID) overdueCount++;
        }

        int collectionPercent = totalExpected.compareTo(BigDecimal.ZERO) > 0
                ? totalCollected.multiply(BigDecimal.valueOf(100)).divide(totalExpected, 0, java.math.RoundingMode.HALF_UP).intValue()
                : 0;

        java.util.Map<String, Object> summary = new java.util.HashMap<>();
        summary.put("totalInvoices", invoices.size());
        summary.put("totalExpected", totalExpected);
        summary.put("totalCollected", totalCollected);
        summary.put("collectionPercent", collectionPercent);
        summary.put("outstandingInvoiceCount", overdueCount);
        return summary;
    }

    @Transactional
    public void recordPayment(UUID invoiceId, BigDecimal paymentAmount, String mode, UUID currentTenantId, Authentication authentication) {
        if (paymentAmount == null || paymentAmount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Payment amount must be greater than zero");
        }

        FeeInvoice invoice = feeInvoiceRepository.findById(invoiceId)
            .orElseThrow(() -> new IllegalArgumentException("FeeInvoice not found with ID: " + invoiceId));
        assertSameTenant(invoice, currentTenantId);

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

        FeeInvoice invoice = feeInvoiceRepository.findById(invoiceId)
                .orElseThrow(() -> new IllegalArgumentException("FeeInvoice not found with ID: " + invoiceId));
        assertSameTenant(invoice, currentTenantId);

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
        FeeInvoice invoice = feeInvoiceRepository.findById(invoiceId)
                .orElseThrow(() -> new IllegalArgumentException("FeeInvoice not found with ID: " + invoiceId));
        assertSameTenant(invoice, currentTenantId);

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
     * Every fee mutation takes an invoiceId from the caller — without this,
     * an admin from one tenant could act on another tenant's invoice by ID
     * alone. currentTenantId may be null only for legacy/system callers that
     * don't have a request-scoped tenant (none currently do); a real,
     * mismatched tenant always throws.
     */
    private void assertSameTenant(FeeInvoice invoice, UUID currentTenantId) {
        if (currentTenantId != null && !currentTenantId.equals(invoice.getTenantId())) {
            throw new IllegalArgumentException("FeeInvoice not found with ID: " + invoice.getId());
        }
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

package com.concept.fees.app;

import com.concept.common.AuditLogService;
import com.concept.fees.data.FeeStructure;
import com.concept.fees.data.FeeStructureRepository;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * Lets a school set what its own grades cost.
 *
 * <p>The FeeStructure table has existed since the beginning with a repository
 * and no writer anywhere, so the only rows in it belonged to the dev-mode
 * seeder. Every real school therefore fell through to the hardcoded fallback in
 * invoicing and billed 20,000 flat for every grade. This is the missing half.
 *
 * <p>Amounts are validated rather than trusted: a negative fee would produce a
 * negative invoice that the payment path would then treat as already overpaid.
 */
@Service
public class FeeStructureService {

    private final FeeStructureRepository feeStructureRepository;
    private final AuditLogService auditLogService;

    public FeeStructureService(FeeStructureRepository feeStructureRepository,
                               AuditLogService auditLogService) {
        this.feeStructureRepository = feeStructureRepository;
        this.auditLogService = auditLogService;
    }

    public List<FeeStructure> list(UUID tenantId, UUID academicYearId) {
        requireScope(tenantId, academicYearId);
        return feeStructureRepository.findByTenantIdAndAcademicYearIdOrderByGradeLevelAsc(
                tenantId, academicYearId);
    }

    /**
     * Creates or updates the fee for one grade level. Upsert rather than
     * separate create/edit paths because "set Grade 6 to 18000" is one intent to
     * an admin, and splitting it invites a duplicate-key error for what the user
     * experiences as an edit.
     */
    @Transactional
    public FeeStructure save(String gradeLevel, BigDecimal tuitionFee, BigDecimal termFee,
                             UUID tenantId, UUID academicYearId, Authentication authentication) {
        requireScope(tenantId, academicYearId);

        String grade = gradeLevel == null ? "" : gradeLevel.trim();
        if (grade.isEmpty()) {
            throw new IllegalArgumentException("Grade level is required.");
        }
        BigDecimal tuition = requireNonNegative(tuitionFee, "Tuition fee");
        BigDecimal term = requireNonNegative(termFee, "Term fee");

        FeeStructure structure = feeStructureRepository
                .findByTenantIdAndAcademicYearIdAndGradeLevel(tenantId, academicYearId, grade)
                .orElseGet(() -> {
                    FeeStructure created = new FeeStructure();
                    created.setId(UUID.randomUUID());
                    created.setTenantId(tenantId);
                    created.setAcademicYearId(academicYearId);
                    created.setGradeLevel(grade);
                    return created;
                });

        boolean isNew = structure.getTuitionFee() == null;
        structure.setTuitionFee(tuition);
        structure.setTermFee(term);
        feeStructureRepository.saveAndFlush(structure);

        auditLogService.log(authentication, isNew ? "FEE_STRUCTURE_CREATED" : "FEE_STRUCTURE_UPDATED",
                "FeeStructure", structure.getId(),
                grade + " set to " + tuition + " tuition + " + term + " term");

        return structure;
    }

    /**
     * Removing a grade's fees does not touch invoices already raised from them --
     * FeeInvoice snapshots its own total, so past billing stays priced as it was
     * sent. It only means new invoices for that grade will be refused until
     * fees are set again, which is the honest outcome.
     */
    @Transactional
    public void delete(UUID id, UUID tenantId, Authentication authentication) {
        FeeStructure structure = feeStructureRepository.findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new IllegalArgumentException("Fee structure not found."));
        feeStructureRepository.delete(structure);
        auditLogService.log(authentication, "FEE_STRUCTURE_DELETED", "FeeStructure", id,
                "Removed fees for " + structure.getGradeLevel());
    }

    private BigDecimal requireNonNegative(BigDecimal value, String label) {
        if (value == null) {
            throw new IllegalArgumentException(label + " is required.");
        }
        if (value.signum() < 0) {
            throw new IllegalArgumentException(label + " cannot be negative.");
        }
        return value;
    }

    private void requireScope(UUID tenantId, UUID academicYearId) {
        if (tenantId == null || academicYearId == null) {
            throw new IllegalArgumentException("Fee settings need a school and an academic year.");
        }
    }
}

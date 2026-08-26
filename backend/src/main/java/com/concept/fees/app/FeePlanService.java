package com.concept.fees.app;

import com.concept.fees.data.FeePlan;
import com.concept.fees.data.FeePlanInstalment;
import com.concept.fees.data.FeePlanInstalmentRepository;
import com.concept.fees.data.FeePlanRepository;
import com.concept.common.AuditLogService;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Lets a school say what a grade costs and how that total is collected.
 *
 * <p>The instalments are validated to sum to the annual amount. That check is
 * the whole point of storing both: a plan whose parts do not add up to the
 * whole produces invoices that quietly over- or under-bill a family across the
 * year, and nobody notices until the last instalment does not settle the
 * account.
 */
@Service
public class FeePlanService {

    private final FeePlanRepository feePlanRepository;
    private final FeePlanInstalmentRepository instalmentRepository;
    private final AuditLogService auditLogService;

    public FeePlanService(FeePlanRepository feePlanRepository,
                          FeePlanInstalmentRepository instalmentRepository,
                          AuditLogService auditLogService) {
        this.feePlanRepository = feePlanRepository;
        this.instalmentRepository = instalmentRepository;
        this.auditLogService = auditLogService;
    }

    /** One instalment as the caller supplies it, before it becomes a row. */
    public record InstalmentSpec(String label, BigDecimal amount, int dueOffsetDays) {}

    public List<FeePlan> listPlans(UUID tenantId, UUID academicYearId) {
        requireScope(tenantId, academicYearId);
        return feePlanRepository.findByTenantIdAndAcademicYearIdOrderByGradeLevelAsc(tenantId, academicYearId);
    }

    public List<FeePlanInstalment> instalmentsOf(UUID planId, UUID tenantId) {
        return instalmentRepository.findByFeePlanIdAndTenantIdOrderBySequenceNumberAsc(planId, tenantId);
    }

    /**
     * Creates or replaces the plan for one grade. Upsert rather than separate
     * create and edit paths: "Grade 6 costs this, in these parts" is one intent
     * to an admin, and splitting it turns an edit into a duplicate-key error.
     */
    @Transactional
    /**
     * Checks a proposed plan without writing anything.
     *
     * <p>Called when the admin submits, not only when the principal approves,
     * so a plan that could never be saved is refused at the point someone can
     * still fix it -- rather than sitting in the queue and failing on approval.
     *
     * @return the plan total, which the request summary quotes
     */
    public BigDecimal validatePlanRequest(String gradeLevel, List<InstalmentSpec> instalments) {
        String grade = gradeLevel == null ? "" : gradeLevel.trim();
        if (grade.isEmpty()) {
            throw new IllegalArgumentException("Grade level is required.");
        }
        if (instalments == null || instalments.isEmpty()) {
            throw new IllegalArgumentException("A plan needs at least one instalment.");
        }

        BigDecimal total = BigDecimal.ZERO;
        for (InstalmentSpec spec : instalments) {
            if (spec.label() == null || spec.label().isBlank()) {
                throw new IllegalArgumentException("Every instalment needs a name.");
            }
            if (spec.amount() == null || spec.amount().signum() < 0) {
                throw new IllegalArgumentException("Instalment amounts cannot be negative.");
            }
            if (spec.dueOffsetDays() < 0) {
                throw new IllegalArgumentException("An instalment cannot fall due before the year starts.");
            }
            total = total.add(spec.amount());
        }
        if (total.signum() <= 0) {
            throw new IllegalArgumentException("The plan total must be more than zero.");
        }
        return total;
    }

    /**
     * Writes the plan. Only reachable once a principal has approved -- the
     * admin-facing route raises an ApprovalRequest instead. See ApprovalService.
     */
    public FeePlan savePlanApproved(String gradeLevel, List<InstalmentSpec> instalments,
                                    UUID tenantId, UUID academicYearId, Authentication authentication) {
        requireScope(tenantId, academicYearId);

        String grade = gradeLevel == null ? "" : gradeLevel.trim();
        BigDecimal total = validatePlanRequest(gradeLevel, instalments);

        FeePlan plan = feePlanRepository
                .findByTenantIdAndAcademicYearIdAndGradeLevel(tenantId, academicYearId, grade)
                .orElseGet(() -> {
                    FeePlan created = new FeePlan();
                    created.setId(UUID.randomUUID());
                    created.setTenantId(tenantId);
                    created.setAcademicYearId(academicYearId);
                    created.setGradeLevel(grade);
                    return created;
                });
        boolean isNew = plan.getAnnualAmount() == null;
        plan.setAnnualAmount(total);
        feePlanRepository.saveAndFlush(plan);

        // Replace the schedule wholesale. Reconciling row by row would leave
        // orphans when the count changes, and invoices already raised keep
        // their own snapshotted amounts and dates regardless.
        instalmentRepository.deleteByFeePlanIdAndTenantId(plan.getId(), tenantId);
        // Flush the deletes before inserting. Hibernate orders inserts before
        // deletes within a transaction otherwise, so re-saving a plan collided
        // with its own old rows on (fee_plan_id, sequence_number) -- which is
        // the ordinary "edit a plan" path, not an edge case.
        instalmentRepository.flush();

        int sequence = 1;
        List<FeePlanInstalment> saved = new ArrayList<>();
        for (InstalmentSpec spec : instalments) {
            FeePlanInstalment row = new FeePlanInstalment();
            row.setId(UUID.randomUUID());
            row.setTenantId(tenantId);
            row.setAcademicYearId(academicYearId);
            row.setFeePlanId(plan.getId());
            row.setSequenceNumber(sequence++);
            row.setLabel(spec.label().trim());
            row.setAmount(spec.amount());
            row.setDueOffsetDays(spec.dueOffsetDays());
            saved.add(instalmentRepository.saveAndFlush(row));
        }

        auditLogService.log(authentication, isNew ? "FEE_PLAN_CREATED" : "FEE_PLAN_UPDATED",
                "FeePlan", plan.getId(),
                grade + ": " + total + " across " + saved.size() + " instalments");

        return plan;
    }

    @Transactional
    /** Deletes the plan. Approval-gated, same as {@link #savePlanApproved}. */
    public void deletePlanApproved(UUID planId, UUID tenantId, Authentication authentication) {
        FeePlan plan = feePlanRepository.findByIdAndTenantId(planId, tenantId)
                .orElseThrow(() -> new IllegalArgumentException("Fee plan not found."));
        instalmentRepository.deleteByFeePlanIdAndTenantId(planId, tenantId);
        feePlanRepository.delete(plan);
        auditLogService.log(authentication, "FEE_PLAN_DELETED", "FeePlan", planId,
                "Removed the plan for " + plan.getGradeLevel());
    }

    private void requireScope(UUID tenantId, UUID academicYearId) {
        if (tenantId == null || academicYearId == null) {
            throw new IllegalArgumentException("Fee plans need a school and an academic year.");
        }
    }

}

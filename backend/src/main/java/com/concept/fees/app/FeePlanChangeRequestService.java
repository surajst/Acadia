package com.concept.fees.app;

import com.concept.fees.data.ApprovalRequest;
import com.concept.fees.data.FeePlan;
import com.concept.fees.data.FeePlanRepository;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * The admin-facing half of a fee plan change: asks, never writes.
 *
 * <p>Separate from {@link FeePlanService} on purpose. The executors that apply
 * an approved change depend on FeePlanService, and ApprovalService depends on
 * the executors -- so if FeePlanService also depended on ApprovalService the
 * three would form a cycle. Keeping the request side here means the dependencies
 * run one way: this class -> ApprovalService -> executors -> FeePlanService.
 */
@Service
public class FeePlanChangeRequestService {

    private final FeePlanService feePlanService;
    private final FeePlanRepository feePlanRepository;
    private final ApprovalService approvalService;

    public FeePlanChangeRequestService(FeePlanService feePlanService,
                                       FeePlanRepository feePlanRepository,
                                       ApprovalService approvalService) {
        this.feePlanService = feePlanService;
        this.feePlanRepository = feePlanRepository;
        this.approvalService = approvalService;
    }

    /**
     * Asks for a plan change rather than making one. Re-pricing a grade affects
     * every family in it at once, so it waits for a principal. The current plan
     * is untouched until then.
     *
     * @return the summary the admin is shown, describing what is now pending
     */
    public String requestPlanSave(String gradeLevel, List<FeePlanService.InstalmentSpec> instalments,
                                  UUID tenantId, UUID academicYearId, Authentication authentication) {
        // Checked now rather than at approval time, so a plan that could never
        // be saved is refused while the admin can still fix it.
        BigDecimal total = feePlanService.validatePlanRequest(gradeLevel, instalments);
        String grade = gradeLevel.trim();
        String summary = "Set " + grade + " fees to " + total
                + " across " + instalments.size() + " instalments";
        approvalService.request(ApprovalRequest.Action.FEE_PLAN_SAVE,
                new FeePlanSaveExecutor.Payload(grade, instalments, academicYearId),
                summary, tenantId, authentication);
        return summary;
    }

    /** Asks to remove a plan. Approval-gated for the same reason as a change. */
    public String requestPlanDelete(UUID planId, UUID tenantId, Authentication authentication) {
        FeePlan plan = feePlanRepository.findByIdAndTenantId(planId, tenantId)
                .orElseThrow(() -> new IllegalArgumentException("Fee plan not found."));
        String summary = "Remove the fee plan for " + plan.getGradeLevel()
                + " (" + plan.getAnnualAmount() + ")";
        approvalService.request(ApprovalRequest.Action.FEE_PLAN_DELETE,
                new FeePlanDeleteExecutor.Payload(planId),
                summary, tenantId, authentication);
        return summary;
    }
}

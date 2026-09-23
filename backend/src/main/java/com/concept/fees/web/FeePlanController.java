package com.concept.fees.web;

import com.concept.fees.app.FeePlanChangeRequestService;
import com.concept.roster.app.ClassSectionDto;
import com.concept.roster.app.ClassStructureService;
import com.concept.fees.app.FeePlanService;
import com.concept.fees.app.FeePlanView;
import com.concept.tenant.TenantContext;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Interface layer for fee plans. Binds the instalment rows the form submits as
 * parallel arrays, resolves the tenant and year, and hands flat values to the
 * app layer — no repository, no entity type (ADR 0001).
 */
@Controller
public class FeePlanController {

    private final FeePlanService feePlanService;
    private final FeePlanChangeRequestService changeRequestService;
    private final TenantContext tenantContext;
    private final ClassStructureService classStructureService;

    public FeePlanController(FeePlanService feePlanService,
                             FeePlanChangeRequestService changeRequestService,
                             TenantContext tenantContext,
                             ClassStructureService classStructureService) {
        this.feePlanService = feePlanService;
        this.changeRequestService = changeRequestService;
        this.tenantContext = tenantContext;
        this.classStructureService = classStructureService;
    }

    @GetMapping("/web/admin/fees/settings")
    @PreAuthorize("hasRole('ADMIN')")
    public String showFeeSettings(Model model, Authentication authentication) {
        UUID tenantId = tenantContext.getTenantId().orElse(null);
        UUID yearId = tenantContext.getAcademicYearId().orElse(null);

        model.addAttribute("currentUserRole", "ADMIN");
        model.addAttribute("feePlans", FeePlanView.of(
                feePlanService.listPlans(tenantId, yearId),
                planId -> feePlanService.instalmentsOf(planId, tenantId)));
        // The school's own grades. As free text, "Grade 6" and "grade 6" were
        // two plans, and a plan priced against a grade with no section reaches
        // nobody -- while the invoice path refuses to bill a grade with no plan.
        model.addAttribute("gradeOptions", classStructureService.listSections(tenantId).stream()
                .map(ClassSectionDto::gradeName)
                .filter(g -> g != null && !g.isBlank())
                .map(String::trim)
                .distinct()
                .sorted()
                .toList());
        return "fee_settings";
    }

    /**
     * The instalment rows arrive as parallel arrays because the form lets an
     * admin add and remove rows freely — how many there are is the school's
     * choice, so nothing here assumes a count.
     */
    @PostMapping("/web/admin/fees/settings/save")
    @PreAuthorize("hasRole('ADMIN')")
    public String savePlan(@RequestParam("gradeLevel") String gradeLevel,
                           @RequestParam("label") List<String> labels,
                           @RequestParam("amount") List<BigDecimal> amounts,
                           @RequestParam("dueOffsetDays") List<Integer> offsets,
                           Authentication authentication,
                           RedirectAttributes ra) {
        try {
            if (labels == null || amounts == null || offsets == null
                    || labels.size() != amounts.size() || labels.size() != offsets.size()) {
                throw new IllegalArgumentException("Every instalment needs a name, an amount and a due day.");
            }
            List<FeePlanService.InstalmentSpec> specs = new ArrayList<>();
            for (int i = 0; i < labels.size(); i++) {
                specs.add(new FeePlanService.InstalmentSpec(labels.get(i), amounts.get(i), offsets.get(i)));
            }
            FeePlanChangeRequestService.Outcome outcome = changeRequestService.requestPlanSave(
                    gradeLevel, specs,
                    tenantContext.getTenantId().orElse(null),
                    tenantContext.getAcademicYearId().orElse(null),
                    authentication);
            ra.addFlashAttribute("successMessage", outcome.applied()
                    ? "Saved. " + gradeLevel.trim() + " fees are in force now. This school has no "
                            + "principal, so the change was applied without a second approver and "
                            + "recorded that way in the audit log."
                    : "Sent to the principal for approval. The current plan for " + gradeLevel.trim()
                            + " is unchanged until they agree.");
        } catch (IllegalArgumentException e) {
            ra.addFlashAttribute("errorMessage", e.getMessage());
            // Hand the submitted rows back so the page can refill the form. A
            // plan is a dozen typed figures, and clearing them on a validation
            // error means retyping all of it to fix one.
            ra.addFlashAttribute("submittedGradeLevel", gradeLevel);
            ra.addFlashAttribute("submittedLabels", labels);
            ra.addFlashAttribute("submittedAmounts", amounts);
            ra.addFlashAttribute("submittedOffsets", offsets);
        }
        return "redirect:/web/admin/fees/settings";
    }

    @PostMapping("/web/admin/fees/settings/{id}/delete")
    @PreAuthorize("hasRole('ADMIN')")
    public String deletePlan(@PathVariable("id") UUID id, Authentication authentication,
                             RedirectAttributes ra) {
        try {
            FeePlanChangeRequestService.Outcome outcome = changeRequestService.requestPlanDelete(
                    id, tenantContext.getTenantId().orElse(null), authentication);
            ra.addFlashAttribute("successMessage", outcome.applied()
                    ? "Removed. This school has no principal, so the change was applied without a "
                            + "second approver and recorded that way in the audit log."
                    : "Sent to the principal for approval. The plan is still in place until they agree.");
        } catch (IllegalArgumentException e) {
            ra.addFlashAttribute("errorMessage", e.getMessage());
        }
        return "redirect:/web/admin/fees/settings";
    }
}

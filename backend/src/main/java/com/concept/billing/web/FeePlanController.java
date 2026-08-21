package com.concept.billing.web;

import com.concept.billing.app.FeePlanService;
import com.concept.billing.app.FeePlanView;
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
    private final TenantContext tenantContext;

    public FeePlanController(FeePlanService feePlanService, TenantContext tenantContext) {
        this.feePlanService = feePlanService;
        this.tenantContext = tenantContext;
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
            feePlanService.savePlan(gradeLevel, specs,
                    tenantContext.getTenantId().orElse(null),
                    tenantContext.getAcademicYearId().orElse(null),
                    authentication);
            ra.addFlashAttribute("successMessage", "Fee plan saved for " + gradeLevel.trim() + ".");
        } catch (IllegalArgumentException e) {
            ra.addFlashAttribute("errorMessage", e.getMessage());
        }
        return "redirect:/web/admin/fees/settings";
    }

    @PostMapping("/web/admin/fees/settings/{id}/delete")
    @PreAuthorize("hasRole('ADMIN')")
    public String deletePlan(@PathVariable("id") UUID id, Authentication authentication,
                             RedirectAttributes ra) {
        try {
            feePlanService.deletePlan(id, tenantContext.getTenantId().orElse(null), authentication);
            ra.addFlashAttribute("successMessage", "Fee plan removed.");
        } catch (IllegalArgumentException e) {
            ra.addFlashAttribute("errorMessage", e.getMessage());
        }
        return "redirect:/web/admin/fees/settings";
    }
}

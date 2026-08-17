package com.concept.fees.web;

import com.concept.fees.app.FeeStructureService;
import com.concept.fees.app.FeeStructureView;
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
import java.util.UUID;

/**
 * Interface layer for per-grade fee settings. Binds requests, resolves the
 * tenant and academic year, and hands flat values to the app layer -- no
 * repository and no entity type (ADR 0001).
 */
@Controller
public class FeeStructureController {

    private final FeeStructureService feeStructureService;
    private final TenantContext tenantContext;

    public FeeStructureController(FeeStructureService feeStructureService, TenantContext tenantContext) {
        this.feeStructureService = feeStructureService;
        this.tenantContext = tenantContext;
    }

    @GetMapping("/web/admin/fees/settings")
    @PreAuthorize("hasRole('ADMIN')")
    public String showFeeSettings(Model model, Authentication authentication) {
        UUID tenantId = tenantContext.getTenantId().orElse(null);
        UUID yearId = tenantContext.getAcademicYearId().orElse(null);

        model.addAttribute("currentUserRole", "ADMIN");
        model.addAttribute("feeStructures", FeeStructureView.of(
                feeStructureService.list(tenantId, yearId)));
        return "fee_settings";
    }

    @PostMapping("/web/admin/fees/settings/save")
    @PreAuthorize("hasRole('ADMIN')")
    public String saveFeeStructure(@RequestParam("gradeLevel") String gradeLevel,
                                   @RequestParam("tuitionFee") BigDecimal tuitionFee,
                                   @RequestParam("termFee") BigDecimal termFee,
                                   Authentication authentication,
                                   RedirectAttributes ra) {
        try {
            feeStructureService.save(gradeLevel, tuitionFee, termFee,
                    tenantContext.getTenantId().orElse(null),
                    tenantContext.getAcademicYearId().orElse(null),
                    authentication);
            ra.addFlashAttribute("successMessage", "Fees saved for " + gradeLevel.trim() + ".");
        } catch (IllegalArgumentException e) {
            ra.addFlashAttribute("errorMessage", e.getMessage());
        }
        return "redirect:/web/admin/fees/settings";
    }

    @PostMapping("/web/admin/fees/settings/{id}/delete")
    @PreAuthorize("hasRole('ADMIN')")
    public String deleteFeeStructure(@PathVariable("id") UUID id, Authentication authentication,
                                     RedirectAttributes ra) {
        try {
            feeStructureService.delete(id, tenantContext.getTenantId().orElse(null), authentication);
            ra.addFlashAttribute("successMessage", "Fees removed.");
        } catch (IllegalArgumentException e) {
            ra.addFlashAttribute("errorMessage", e.getMessage());
        }
        return "redirect:/web/admin/fees/settings";
    }
}

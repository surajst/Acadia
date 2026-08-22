package com.concept.billing.web;

import com.concept.billing.app.FeeReportService;
import com.concept.tenant.TenantContext;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import java.util.UUID;

/**
 * Two views that due dates and receipt numbers made possible but nothing
 * previously showed: who has not paid, and what the school actually
 * collected. Read-only, so this is a thin binder over
 * {@link FeeReportService} with no logic of its own (ADR 0001).
 */
@Controller
public class FeeReportController {

    private final FeeReportService feeReportService;
    private final TenantContext tenantContext;

    public FeeReportController(FeeReportService feeReportService, TenantContext tenantContext) {
        this.feeReportService = feeReportService;
        this.tenantContext = tenantContext;
    }

    @GetMapping("/web/admin/fees/defaulters")
    @PreAuthorize("hasRole('ADMIN')")
    public String showDefaulters(Model model) {
        UUID tenantId = tenantContext.getTenantId().orElse(null);
        model.addAttribute("currentUserRole", "ADMIN");
        model.addAttribute("defaulters", feeReportService.defaulters(tenantId));
        return "fee_defaulters";
    }

    @GetMapping("/web/admin/fees/collections")
    @PreAuthorize("hasRole('ADMIN')")
    public String showCollections(Model model) {
        UUID tenantId = tenantContext.getTenantId().orElse(null);
        UUID academicYearId = tenantContext.getAcademicYearId().orElse(null);
        model.addAttribute("currentUserRole", "ADMIN");
        model.addAttribute("receipts", feeReportService.collectionReport(tenantId, academicYearId));
        return "fee_collections";
    }
}

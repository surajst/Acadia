package com.concept.fees.web;

import com.concept.fees.app.FeeDashboardService;
import com.concept.fees.app.FeeDashboardView;
import com.concept.tenant.TenantContext;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

/**
 * Interface layer for the admin fee ledger. Binds requests, enforces the ADMIN
 * gate, resolves the tenant, and maps a flat view onto the model. No financial
 * logic, no repository, no entity type (ADR 0001).
 */
@Controller
public class FeeController {

    private final FeeDashboardService feeDashboardService;
    private final TenantContext tenantContext;

    public FeeController(FeeDashboardService feeDashboardService, TenantContext tenantContext) {
        this.feeDashboardService = feeDashboardService;
        this.tenantContext = tenantContext;
    }

    @GetMapping("/web/admin/fees")
    public String showFeeDashboard(@RequestParam(value = "page", defaultValue = "0") int page,
                                   @RequestParam(value = "size", defaultValue = "20") int size,
                                   Model model, Authentication authentication) {
        String role = requireAdmin(authentication, "view the financial ledger");
        UUID tenantId = tenantContext.getTenantId().orElse(null);

        FeeDashboardView view = feeDashboardService.buildDashboard(tenantId, page, size);

        model.addAttribute("currentUserRole", role);
        model.addAttribute("systemScope", "ADMIN_FINANCE");
        model.addAttribute("totalExpectedRevenue", view.totalExpected());
        model.addAttribute("totalCollected", view.totalCollected());
        model.addAttribute("totalOutstandingDeficit", view.totalOutstanding());
        model.addAttribute("enrichedInvoices", view.invoices());
        model.addAttribute("allStudents", view.students());
        model.addAttribute("currentPage", view.currentPage());
        model.addAttribute("totalPages", view.totalPages());
        model.addAttribute("totalItems", view.totalItems());
        model.addAttribute("pageSize", view.pageSize());
        return "fee_management";
    }

    @PostMapping("/web/admin/fees/collect")
    public String collectPayment(@RequestParam("invoiceId") UUID invoiceId,
                                 @RequestParam("amount") BigDecimal amount,
                                 @RequestParam("paymentMode") String paymentMode,
                                 Authentication authentication) {
        requireAdmin(authentication, "record dynamic payments");
        UUID tenantId = tenantContext.getTenantId().orElse(null);
        feeDashboardService.recordPayment(invoiceId, amount, paymentMode, tenantId, authentication);
        return "redirect:/web/admin/fees?success=payment_recorded";
    }

    @PostMapping("/web/admin/fees/invoice/create")
    @PreAuthorize("hasRole('ADMIN')")
    public String createInvoice(@RequestParam("studentId") UUID studentId, Authentication authentication) {
        UUID tenantId = tenantContext.getTenantId().orElse(null);
        feeDashboardService.createInvoice(studentId, tenantId, authentication);
        return "redirect:/web/admin/fees?success=invoice_created";
    }

    @PostMapping("/api/admin/fees/{invoiceId}/waiver/request")
    @ResponseBody
    @PreAuthorize("hasRole('ADMIN')")
    public Object requestWaiver(@PathVariable UUID invoiceId,
                                @RequestParam("waiverAmount") BigDecimal waiverAmount,
                                @RequestParam("reason") String reason,
                                Authentication authentication) {
        try {
            UUID tenantId = tenantContext.getTenantId().orElse(null);
            String waiverStatus = feeDashboardService.requestWaiver(invoiceId, waiverAmount, reason, tenantId, authentication);
            return Map.of("status", "requested", "waiverStatus", waiverStatus);
        } catch (IllegalArgumentException e) {
            return Map.of("error", e.getMessage());
        }
    }

    private String requireAdmin(Authentication authentication, String action) {
        String role = "ADMIN";
        if (authentication != null) {
            boolean isAdmin = false;
            for (GrantedAuthority auth : authentication.getAuthorities()) {
                String authority = auth.getAuthority();
                if ("ROLE_ADMIN".equals(authority)) {
                    isAdmin = true;
                }
                if (authority.startsWith("ROLE_")) {
                    role = authority.substring(5);
                }
            }
            if (!isAdmin) {
                throw new RuntimeException("Access denied: Only administrators can " + action);
            }
        }
        return role;
    }

    @ExceptionHandler(Exception.class)
    public String handleException(Exception ex, Model model) {
        model.addAttribute("errorMessage", ex.getMessage());
        model.addAttribute("currentUserRole", "ADMIN");
        return "fee_management";
    }
}

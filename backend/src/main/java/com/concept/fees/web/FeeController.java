package com.concept.fees.web;

import com.concept.billing.app.CustomInvoiceService;
import com.concept.fees.app.FeeDashboardService;
import com.concept.billing.app.FeePlanMissingException;
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
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.math.BigDecimal;
import java.util.List;
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
    private final CustomInvoiceService customInvoiceService;
    private final TenantContext tenantContext;

    public FeeController(FeeDashboardService feeDashboardService,
                         CustomInvoiceService customInvoiceService,
                         TenantContext tenantContext) {
        this.feeDashboardService = feeDashboardService;
        this.customInvoiceService = customInvoiceService;
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
                                 Authentication authentication,
                                 RedirectAttributes ra) {
        requireAdmin(authentication, "record dynamic payments");
        UUID tenantId = tenantContext.getTenantId().orElse(null);
        try {
            Integer receiptNumber = feeDashboardService.recordPayment(invoiceId, amount, paymentMode, tenantId, authentication);
            ra.addFlashAttribute("successMessage", "Payment recorded — Receipt #" + receiptNumber + ".");
            return "redirect:/web/admin/fees?success=payment_recorded";
        } catch (IllegalArgumentException e) {
            // Overpayment and zero amounts are refused server-side now, so this
            // is a reachable outcome rather than a server fault.
            ra.addFlashAttribute("errorMessage", e.getMessage());
            return "redirect:/web/admin/fees";
        } catch (org.springframework.dao.DataIntegrityViolationException e) {
            // Two admins recording a payment in the exact same instant can
            // collide on the receipt number, which the unique constraint
            // catches. Nothing partially applied -- the invoice update shares
            // this transaction with the receipt write, so a failed write here
            // rolled both back. Ask for a retry rather than letting this reach
            // the generic handler, which cannot render this page.
            ra.addFlashAttribute("errorMessage",
                    "Another payment was being recorded at the same moment. Please try again.");
            return "redirect:/web/admin/fees";
        }
    }

    /**
     * Corrects a mistyped or bounced payment by recording its opposite. The
     * original entry is never edited or deleted -- see FeeManagementService.
     */
    @PostMapping("/web/admin/fees/payment/{transactionId}/reverse")
    @PreAuthorize("hasRole('ADMIN')")
    public String reversePayment(@PathVariable("transactionId") UUID transactionId,
                                 @RequestParam("reason") String reason,
                                 Authentication authentication,
                                 RedirectAttributes ra) {
        UUID tenantId = tenantContext.getTenantId().orElse(null);
        try {
            feeDashboardService.reversePayment(transactionId, reason, tenantId, authentication);
            ra.addFlashAttribute("successMessage", "Payment reversed.");
        } catch (IllegalArgumentException e) {
            ra.addFlashAttribute("errorMessage", e.getMessage());
        }
        return "redirect:/web/admin/fees";
    }

    @PostMapping("/web/admin/fees/invoice/create")
    @PreAuthorize("hasRole('ADMIN')")
    public String createInvoice(@RequestParam("studentId") UUID studentId,
                                @RequestParam(value = "overrideAmount", required = false) BigDecimal overrideAmount,
                                @RequestParam(value = "overrideReason", required = false) String overrideReason,
                                Authentication authentication,
                                RedirectAttributes ra) {
        UUID tenantId = tenantContext.getTenantId().orElse(null);
        try {
            feeDashboardService.createInvoice(studentId, tenantId, overrideAmount, overrideReason, authentication);
            return "redirect:/web/admin/fees?success=invoice_created";
        } catch (IllegalArgumentException e) {
            // A rejected override (no reason, negative amount) is user error,
            // not a server fault -- send the admin back with the reason.
            ra.addFlashAttribute("errorMessage", e.getMessage());
            return "redirect:/web/admin/fees";
        } catch (FeePlanMissingException e) {
            // Invoicing no longer invents an amount when fees are unset, so this
            // is now a reachable, expected outcome rather than a server error.
            // The message names the grade and where to fix it.
            ra.addFlashAttribute("errorMessage", e.getMessage());
            return "redirect:/web/admin/fees";
        }
    }

    /**
     * Raises an invoice the admin composed line by line, optionally for several
     * students at once. Billing a whole class for a trip is one action to an
     * admin; making them repeat it forty times is how half a class ends up
     * uninvoiced.
     */
    @PostMapping("/web/admin/fees/invoice/custom")
    @PreAuthorize("hasRole('ADMIN')")
    public String createCustomInvoice(@RequestParam(value = "studentIds", required = false) List<UUID> studentIds,
                                      @RequestParam(value = "description", required = false) List<String> descriptions,
                                      @RequestParam(value = "amount", required = false) List<BigDecimal> amounts,
                                      @RequestParam(value = "dueDate", required = false)
                                      @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE)
                                      java.time.LocalDate dueDate,
                                      Authentication authentication,
                                      RedirectAttributes ra) {
        UUID tenantId = tenantContext.getTenantId().orElse(null);
        try {
            if (descriptions == null || amounts == null || descriptions.size() != amounts.size()) {
                throw new IllegalArgumentException("Each invoice line needs both a description and an amount.");
            }
            List<CustomInvoiceService.LineSpec> lines = new java.util.ArrayList<>();
            for (int i = 0; i < descriptions.size(); i++) {
                lines.add(new CustomInvoiceService.LineSpec(descriptions.get(i), amounts.get(i)));
            }
            List<?> raised = customInvoiceService.raise(studentIds, lines, dueDate, tenantId, authentication);
            ra.addFlashAttribute("successMessage",
                    "Raised " + raised.size() + " invoice" + (raised.size() == 1 ? "" : "s") + ".");
        } catch (IllegalArgumentException e) {
            ra.addFlashAttribute("errorMessage", e.getMessage());
        }
        return "redirect:/web/admin/fees";
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

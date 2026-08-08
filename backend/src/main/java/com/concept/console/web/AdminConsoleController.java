package com.concept.console.web;

import com.concept.console.app.ConsoleService;
import com.concept.console.app.ConsoleView;
import com.concept.tenant.TenantContext;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * Interface layer for the admin console hub. Resolves the caller's role and
 * tenant, asks the application layer for a flat {@link ConsoleView}, and maps it
 * onto the model — no persistence, no entities (ADR 0001).
 */
@Controller
public class AdminConsoleController {

    private final ConsoleService consoleService;
    private final TenantContext tenantContext;

    public AdminConsoleController(ConsoleService consoleService, TenantContext tenantContext) {
        this.consoleService = consoleService;
        this.tenantContext = tenantContext;
    }

    @GetMapping("/web/admin/management")
    public String showAdminManagement(Model model, Authentication authentication) {
        model.addAttribute("currentUserRole", resolveRole(authentication));

        ConsoleView view = consoleService.getConsole(tenantContext.getTenantId().orElse(null));
        model.addAttribute("classList", view.classList());
        model.addAttribute("rewardInventoryList", view.rewardInventoryList());
        model.addAttribute("totalStudents", view.totalStudents());
        model.addAttribute("totalStaff", view.totalStaff());
        model.addAttribute("totalClassrooms", view.totalClassrooms());
        model.addAttribute("systemScope", "ADMIN_CONSOLE");

        return "admin_management";
    }

    private String resolveRole(Authentication authentication) {
        if (authentication != null) {
            for (GrantedAuthority auth : authentication.getAuthorities()) {
                if (auth.getAuthority().startsWith("ROLE_")) {
                    return auth.getAuthority().substring(5);
                }
            }
        }
        return "ADMIN";
    }

    /** Render the console with the error surfaced instead of a white-label page. */
    @ExceptionHandler(Exception.class)
    public String handleException(Exception ex, Model model) {
        model.addAttribute("errorMessage", ex.getMessage());
        model.addAttribute("currentUserRole", "ADMIN");
        return "admin_management";
    }
}

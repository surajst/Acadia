package com.concept.dashboard.web;

import com.concept.dashboard.app.DashboardService;
import com.concept.dashboard.app.RosterDashboardView;
import com.concept.dashboard.app.TeacherDashboardView;
import com.concept.tenant.TenantContext;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.UUID;

/**
 * Interface layer for the unified dashboard (admin/teacher roster + PRINCIPAL
 * rollups) and the teacher verification queues. Resolves role and tenant, asks
 * the application layer for flat views, and maps them onto the model — no
 * persistence, no entities (ADR 0001).
 */
@Controller
public class DashboardController {

    private final DashboardService dashboardService;
    private final TenantContext tenantContext;

    public DashboardController(DashboardService dashboardService, TenantContext tenantContext) {
        this.dashboardService = dashboardService;
        this.tenantContext = tenantContext;
    }

    /** Redirect bridge: /web/management/attendance → canonical teacher attendance route. */
    @GetMapping("/web/management/attendance")
    public String managementAttendanceRedirect() {
        return "redirect:/web/teacher/attendance";
    }

    @GetMapping("/web/admin/dashboard")
    public String showUnifiedDashboard(
            @RequestParam(value = "classId", required = false) UUID classId,
            @RequestParam(value = "name", required = false) String nameFilter,
            @RequestParam(value = "gradeLevel", required = false) String gradeLevelFilter,
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "20") int size,
            Model model, Authentication authentication) {
        String role = resolveRole(authentication);
        String username = authentication != null ? authentication.getName() : null;

        RosterDashboardView view = dashboardService.buildRosterDashboard(
                tenantContext.getTenantId().orElse(null), username, classId,
                nameFilter, gradeLevelFilter, page, size, "PRINCIPAL".equals(role));

        model.addAttribute("currentUserRole", role);
        model.addAttribute("systemScope", "RESTRICTED_VIEW");
        model.addAttribute("students", view.roster());
        model.addAttribute("allGradeNames", view.allGradeNames());
        model.addAttribute("totalStudents", view.totalStudents());
        model.addAttribute("activeAbsences", view.activeAbsences());
        model.addAttribute("attendancePercentage", view.attendancePercentage());
        model.addAttribute("filterName", nameFilter != null ? nameFilter : "");
        model.addAttribute("filterGrade", gradeLevelFilter != null ? gradeLevelFilter : "");
        model.addAttribute("currentPage", page);
        model.addAttribute("totalPages", view.totalPages());
        model.addAttribute("totalRosterItems", view.totalRosterItems());
        model.addAttribute("pageSize", size);
        if ("PRINCIPAL".equals(role)) {
            model.addAttribute("schoolProgress", view.schoolProgress());
        }
        // Fee collection is on the dashboard for admins as well. It used to be
        // added only for principals, so an admin's KPI fell through to its zero
        // fallback and reported that nothing had been collected -- on a school
        // where most of the year was in fact paid.
        model.addAttribute("feeSummary", view.feeSummary());

        return "unified_dashboard";
    }

    @GetMapping("/web/teacher/dashboard")
    public String viewTeacherDashboard(Model model, Authentication authentication) {
        String role = resolveRole(authentication);
        String email = authentication != null ? authentication.getName() : null;

        TeacherDashboardView view = dashboardService.buildTeacherQueues(
                email, role, tenantContext.getTenantId().orElse(null));

        model.addAttribute("currentUserRole", role);
        model.addAttribute("pendingSubmissions", view.pendingSubmissions());
        model.addAttribute("pendingProgressQueue", view.pendingProgress());
        return "teacher_dashboard";
    }

    private String resolveRole(Authentication authentication) {
        if (authentication != null) {
            for (GrantedAuthority auth : authentication.getAuthorities()) {
                if (auth.getAuthority().startsWith("ROLE_")) {
                    return auth.getAuthority().substring(5);
                }
            }
        }
        return "TEACHER";
    }

    /** Render the dashboard with the error surfaced instead of a white-label page. */
    @ExceptionHandler(Exception.class)
    public String handleException(Exception ex, Model model) {
        model.addAttribute("errorMessage", ex.getMessage());
        model.addAttribute("currentUserRole", "TEACHER");
        return "unified_dashboard";
    }
}

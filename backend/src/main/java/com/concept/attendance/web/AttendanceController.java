package com.concept.attendance.web;

import com.concept.attendance.app.AttendanceFormView;
import com.concept.attendance.app.AttendanceService;
import com.concept.attendance.app.MarkAttendanceCommand;
import com.concept.tenant.TenantContext;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Controller;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;
import java.util.UUID;

/**
 * Interface layer for attendance. Binds the request, resolves role + tenant,
 * delegates to the application layer, and maps the flat view onto the model.
 * Statuses are bound as plain strings so this layer never imports a persistence
 * type; no business logic, no repository, no entity (ADR 0001).
 */
@Controller
public class AttendanceController {

    private final AttendanceService attendanceService;
    private final TenantContext tenantContext;

    public AttendanceController(AttendanceService attendanceService, TenantContext tenantContext) {
        this.attendanceService = attendanceService;
        this.tenantContext = tenantContext;
    }

    @GetMapping("/web/teacher/attendance")
    public String showAttendanceForm(@RequestParam(value = "classId", required = false) UUID classId,
                                     Model model, Authentication authentication) {
        UUID tenantId = tenantContext.getTenantId().orElse(null);
        AttendanceFormView view = attendanceService.buildForm(tenantId, classId, authentication);

        model.addAttribute("currentUserRole", resolveRole(authentication));
        model.addAttribute("systemScope", "RESTRICTED_VIEW");
        model.addAttribute("currentClassId", view.currentClassId());
        model.addAttribute("currentClassLabel", view.currentClassLabel());
        model.addAttribute("classList", view.classList());
        model.addAttribute("studentList", view.students());
        // The date control had neither a value nor bounds, because currentDate
        // was never added here -- only StudentPortalController ever set it, so
        // the field rendered empty and would accept any date the browser
        // allowed, leaving the server to refuse it afterwards.
        model.addAttribute("currentDate", java.time.LocalDate.now());
        model.addAttribute("earliestAttendanceDate",
                java.time.LocalDate.now().minusDays(AttendanceService.BACKFILL_WINDOW_DAYS));
        return "attendance";
    }

    @PostMapping("/web/teacher/attendance/submit")
    public String submitAttendance(@RequestParam("studentIds") List<UUID> studentIds,
                                   @RequestParam("statuses") List<String> statuses,
                                   @RequestParam(value = "classId", required = false) UUID classId,
                                   @RequestParam(value = "attendanceDate", required = false)
                                   @org.springframework.format.annotation.DateTimeFormat(iso =
                                           org.springframework.format.annotation.DateTimeFormat.ISO.DATE)
                                   java.time.LocalDate attendanceDate,
                                   Authentication authentication,
                                   RedirectAttributes ra) {
        UUID tenantId = tenantContext.getTenantId().orElse(null);
        try {
            attendanceService.mark(
                    new MarkAttendanceCommand(tenantId, studentIds, statuses, attendanceDate), authentication);
        } catch (IllegalArgumentException e) {
            ra.addFlashAttribute("errorMessage", e.getMessage());
            return classId != null
                    ? "redirect:/web/teacher/attendance?classId=" + classId
                    : "redirect:/web/teacher/attendance";
        }

        String redirectUrl = "redirect:/web/teacher/attendance";
        if (classId != null) {
            redirectUrl += "?classId=" + classId + "&success=true";
        } else {
            redirectUrl += "?success=true";
        }
        return redirectUrl;
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
}

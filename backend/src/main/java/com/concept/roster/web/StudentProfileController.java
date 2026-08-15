package com.concept.roster.web;

import com.concept.roster.app.StudentProfileNotFoundException;
import com.concept.roster.app.StudentProfileService;
import com.concept.roster.app.StudentProfileView;
import com.concept.tenant.TenantContext;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.util.Collections;
import java.util.UUID;

/**
 * Interface layer for the student profile. Binds the request, resolves the
 * caller's role and tenant, delegates the decision to the application layer,
 * and maps the returned flat view onto the model. No business logic, no
 * repository, no entity types — the reference shape from ADR 0001.
 */
@Controller
public class StudentProfileController {

    private final StudentProfileService studentProfileService;
    private final TenantContext tenantContext;

    public StudentProfileController(StudentProfileService studentProfileService, TenantContext tenantContext) {
        this.studentProfileService = studentProfileService;
        this.tenantContext = tenantContext;
    }

    @GetMapping("/web/teacher/student/{id}")
    public String showProfile(@PathVariable("id") UUID id, Model model, Authentication authentication) {
        String role = resolveRole(authentication);
        UUID tenantId = tenantContext.getTenantId().orElse(null);

        StudentProfileView view;
        try {
            view = studentProfileService.getProfile(id, tenantId);
        } catch (StudentProfileNotFoundException e) {
            // Foreign or missing student: bounce to the caller's own roster, leak nothing.
            String dest = "TEACHER".equals(role) ? "/web/teacher/dashboard" : "/web/admin/dashboard";
            return "redirect:" + dest + "?error=student_not_found";
        }

        model.addAttribute("currentUserRole", role);
        model.addAttribute("systemScope", "RESTRICTED_VIEW");
        model.addAttribute("studentId", view.studentId());
        model.addAttribute("studentFirstName", view.firstName());
        model.addAttribute("studentLastName", view.lastName());
        model.addAttribute("rollNumber", view.rollNumber());
        model.addAttribute("gradeName", view.gradeName());
        model.addAttribute("sectionName", view.sectionName());
        model.addAttribute("presentCount", view.presentCount());
        model.addAttribute("absentCount", view.absentCount());
        model.addAttribute("attendancePercentage", view.attendancePercentage());
        model.addAttribute("schoolXp", view.schoolXp());
        model.addAttribute("parentXp", view.parentXp());
        model.addAttribute("activeStreak", view.activeStreak());
        model.addAttribute("primaryGuardian", view.primaryGuardian());
        model.addAttribute("guardianPhone", view.guardianPhone());
        model.addAttribute("primaryGuardianId", view.primaryGuardianId());
        model.addAttribute("primaryGuardianFirstName", view.primaryGuardianFirstName());
        model.addAttribute("primaryGuardianLastName", view.primaryGuardianLastName());
        model.addAttribute("guardianCount", view.guardianCount());
        model.addAttribute("householdStreak", view.householdStreak());
        model.addAttribute("recentParentNotes", null);
        model.addAttribute("dispatchLedger", Collections.emptyList());
        model.addAttribute("classList", view.classList());
        model.addAttribute("currentSchoolClassId", view.currentSchoolClassId());
        model.addAttribute("studentLoginUsername", view.studentLoginUsername());
        model.addAttribute("guardianLoginUsername", view.guardianLoginUsername());

        return "student_profile";
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

package com.concept.assessment.web;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * Interface layer for the teacher assessment-scores page. Renders the view;
 * all data loads client-side via the assessment JSON API (ADR 0001).
 */
@Controller
public class AssessmentsWebController {

    @GetMapping("/web/teacher/assessments")
    public String viewAssessments(Model model, Authentication authentication) {
        String role = "TEACHER";
        if (authentication != null) {
            for (GrantedAuthority auth : authentication.getAuthorities()) {
                String authority = auth.getAuthority();
                if (authority.startsWith("ROLE_")) {
                    role = authority.substring(5);
                }
            }
        }
        model.addAttribute("currentUserRole", role);
        return "assessment_scores";
    }
}

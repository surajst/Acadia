package com.concept.curriculum.web;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * Serves the admin curriculum-dashboard Thymeleaf view (ADR 0001 interface
 * layer). Data is fetched client-side via {@link CurriculumApiController}.
 */
@Controller
@PreAuthorize("hasRole('ADMIN')")
public class AdminCurriculumPageController {

    @GetMapping("/web/admin/curriculum")
    public String getCurriculumDashboard() {
        return "curriculum_dashboard";
    }
}

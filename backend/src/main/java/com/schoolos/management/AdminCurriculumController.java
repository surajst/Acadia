package com.schoolos.management;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
@PreAuthorize("hasRole('ADMIN')")
public class AdminCurriculumController {

    @GetMapping("/web/admin/curriculum")
    public String getCurriculumDashboard() {
        return "curriculum_dashboard";
    }
}

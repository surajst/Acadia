package com.concept.student.web;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

/**
 * Interface layer: canonicalizes the legacy /web/student/dashboard URL onto the
 * single student portal page (ADR 0001).
 */
@Controller
@RequestMapping("/web/student")
public class StudentPortalRedirectController {

    @GetMapping("/dashboard")
    public String dashboard() {
        return "redirect:/web/student/portal";
    }
}

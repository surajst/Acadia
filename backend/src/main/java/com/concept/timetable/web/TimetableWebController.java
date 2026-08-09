package com.concept.timetable.web;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * Interface layer for the admin timetable-management page. Renders the view;
 * all data is loaded client-side via the admin timetable JSON API (ADR 0001).
 */
@Controller
@PreAuthorize("hasRole('ADMIN')")
public class TimetableWebController {

    @GetMapping("/web/admin/timetable")
    public String timetableManagement() {
        return "timetable_management";
    }
}

package com.schoolos.academics;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
@PreAuthorize("hasRole('ADMIN')")
public class AdminSubjectWebController {

    @GetMapping("/web/admin/subjects")
    public String getSubjectManagement() {
        return "subject_management";
    }
}

package com.concept.teacher.web;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * Serves the teacher tasks Thymeleaf view (ADR 0001 interface layer); data is
 * loaded client-side. Only the caller's role is surfaced to the template.
 */
@Controller
public class TeacherTasksWebController {

    @GetMapping("/web/teacher/tasks")
    public String viewTeacherTasks(Model model, Authentication authentication) {
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
        return "teacher_tasks";
    }
}

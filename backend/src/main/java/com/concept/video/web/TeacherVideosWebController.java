package com.concept.video.web;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * Serves the teacher Videos page (ADR 0001 interface layer). Data is loaded
 * client-side from the API, so only the caller's role is surfaced here -- the
 * same shape as the tasks page beside it.
 */
@Controller
public class TeacherVideosWebController {

    @GetMapping("/web/teacher/videos")
    public String viewTeacherVideos(Model model, Authentication authentication) {
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
        return "teacher_videos";
    }
}

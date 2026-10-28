package com.concept.gallery.web;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * The Photos page, one page for every role (ADR 0001 interface layer). Albums
 * and photos load client-side from /api/gallery, which decides what each person
 * may see and do; the page only needs to know whether to offer staff controls.
 */
@Controller
public class GalleryPageController {

    @GetMapping("/web/gallery")
    public String gallery(Model model, Authentication authentication) {
        boolean staff = false;
        if (authentication != null) {
            for (GrantedAuthority authority : authentication.getAuthorities()) {
                String a = authority.getAuthority();
                staff |= a.equals("ROLE_ADMIN") || a.equals("ROLE_PRINCIPAL") || a.equals("ROLE_TEACHER");
            }
        }
        model.addAttribute("galleryStaff", staff);
        return "gallery";
    }
}

package com.concept.recognition.web;

import com.concept.recognition.app.RecognitionService;
import com.concept.tenant.TenantContext;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.UUID;

/**
 * Recognising a child, from the student profile a teacher already has open.
 *
 * <p>Deliberately not a separate screen. The moment a teacher wants to record
 * "she shared without being asked" is while they are looking at that child, and
 * a flow that starts with finding the child again does not get used.
 */
@Controller
public class RecognitionController {

    private final RecognitionService recognitionService;
    private final TenantContext tenantContext;

    public RecognitionController(RecognitionService recognitionService, TenantContext tenantContext) {
        this.recognitionService = recognitionService;
        this.tenantContext = tenantContext;
    }

    /**
     * Teachers, principals and admins may recognise a child. Parents may not:
     * the parent-side equivalent is the existing quest and reward flow, and a
     * parent topping up their own child's school XP would make the number
     * meaningless to everyone else.
     */
    @PostMapping("/web/teacher/student/{id}/award")
    @PreAuthorize("hasAnyRole('TEACHER', 'ADMIN', 'PRINCIPAL')")
    public String award(@PathVariable("id") UUID id,
                        @RequestParam("badgeCode") String badgeCode,
                        @RequestParam(value = "reason", required = false) String reason,
                        Authentication authentication,
                        RedirectAttributes ra) {
        try {
            RecognitionService.AwardView view = recognitionService.award(
                    id, badgeCode, reason, tenantContext.getTenantId().orElse(null), authentication);
            ra.addFlashAttribute("profileMessage",
                    view.emoji() + " " + view.label() + " awarded — +" + view.points() + " XP.");
        } catch (IllegalArgumentException e) {
            ra.addFlashAttribute("errorMessage", e.getMessage());
        }
        return "redirect:/web/teacher/student/" + id;
    }
}

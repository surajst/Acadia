package com.concept.assignment.web;

import com.concept.assignment.app.AssignmentException;
import com.concept.assignment.app.AssignmentService;
import com.concept.assignment.app.AssignmentViews.AssignmentsPage;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.UUID;

/**
 * Interface layer for the admin assignments web page — renders the view from
 * flat records and binds the assign/remove form posts. All logic lives in
 * {@link AssignmentService}; no JPA entity reaches this layer (ADR 0001).
 */
@Controller
public class AdminAssignmentPageController {

    private final AssignmentService assignmentService;

    public AdminAssignmentPageController(AssignmentService assignmentService) {
        this.assignmentService = assignmentService;
    }

    @GetMapping("/web/admin/assignments")
    public String showAssignments(@RequestParam(required = false) UUID teacher,
                                  Model model, Authentication authentication) {
        AssignmentsPage page = assignmentService.assignmentsPage(teacher, authentication);
        model.addAttribute("currentUserRole", page.currentUserRole());
        model.addAttribute("teachers", page.teachers());
        model.addAttribute("sections", page.sections());
        model.addAttribute("assignments", page.assignments());
        model.addAttribute("selectedTeacherId", page.selectedTeacherId());
        return "assignments";
    }

    @PostMapping("/web/admin/assignments/assign")
    public String assign(@RequestParam UUID teacherId,
                         @RequestParam UUID classSectionId,
                         @RequestParam String subjectName,
                         @RequestParam(defaultValue = "false") boolean isHomeClass,
                         Authentication authentication,
                         RedirectAttributes redirectAttributes) {
        try {
            assignmentService.createAssignmentWeb(teacherId, classSectionId, subjectName, isHomeClass, authentication);
            redirectAttributes.addFlashAttribute("successMessage", "Assignment created successfully.");
        } catch (AssignmentException e) {
            String prefix = e.status() == 409 ? "Duplicate assignment: " : "Failed to create assignment: ";
            redirectAttributes.addFlashAttribute("errorMessage", prefix + e.getMessage());
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage", "Failed to create assignment: " + e.getMessage());
        }
        return "redirect:/web/admin/assignments?teacher=" + teacherId;
    }

    @PostMapping("/web/admin/assignments/remove/{assignmentId}")
    public String remove(@PathVariable UUID assignmentId,
                         @RequestParam(required = false) UUID teacherId,
                         Authentication authentication,
                         RedirectAttributes redirectAttributes) {
        try {
            assignmentService.removeAssignmentWeb(assignmentId, authentication);
            redirectAttributes.addFlashAttribute("successMessage", "Assignment removed.");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage", "Failed to remove assignment: " + e.getMessage());
        }
        return teacherId != null
                ? "redirect:/web/admin/assignments?teacher=" + teacherId
                : "redirect:/web/admin/assignments";
    }
}

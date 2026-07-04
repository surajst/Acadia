package com.schoolos.management;

import com.schoolos.user.CurrentUserService;
import com.schoolos.user.User;
import com.schoolos.user.UserRepository;
import com.schoolos.user.UserRole;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Controller
public class AdminAssignmentWebController {

    @Autowired private UserRepository userRepository;
    @Autowired private ClassSectionRepository classSectionRepository;
    @Autowired private SubjectAssignmentService assignmentService;
    @Autowired private SubjectAssignmentRepository assignmentRepository;
    @Autowired private CurrentUserService currentUserService;

    // ─── GET /web/admin/assignments ───────────────────────────────────────────

    @GetMapping("/web/admin/assignments")
    public String showAssignments(
            @RequestParam(required = false) UUID teacher,
            Model model,
            Authentication authentication) {

        model.addAttribute("currentUserRole", resolveRole(authentication));

        UUID tenantId = currentUserService.getCurrentTenantId(authentication).orElse(null);

        List<User> teachers = tenantId != null
                ? userRepository.findByTenantIdAndRoleIn(tenantId, List.of(UserRole.TEACHER))
                : List.of();
        model.addAttribute("teachers", teachers);
        model.addAttribute("sections", tenantId != null ? classSectionRepository.findByTenantId(tenantId) : List.of());

        // Resolve which teacher to display: ?teacher=uuid param (only if it's
        // one of this tenant's own teachers), else first teacher.
        User selectedTeacher = null;
        if (teacher != null) {
            final UUID requestedId = teacher;
            selectedTeacher = teachers.stream().filter(t -> t.getId().equals(requestedId)).findFirst().orElse(null);
        }
        if (selectedTeacher == null && !teachers.isEmpty()) {
            selectedTeacher = teachers.get(0);
        }

        List<SubjectAssignment> assignments = selectedTeacher != null
                ? assignmentRepository.findByTeacher(selectedTeacher)
                : List.of();

        model.addAttribute("assignments", assignments);
        model.addAttribute("selectedTeacherId",
                selectedTeacher != null ? selectedTeacher.getId() : null);

        return "assignments";
    }

    // ─── POST /web/admin/assignments/assign ───────────────────────────────────

    @PostMapping("/web/admin/assignments/assign")
    public String assign(
            @RequestParam UUID teacherId,
            @RequestParam UUID classSectionId,
            @RequestParam String subjectName,
            @RequestParam(defaultValue = "false") boolean isHomeClass,
            Authentication authentication,
            RedirectAttributes redirectAttributes) {
        try {
            UUID tenantId = currentUserService.getCurrentTenantId(authentication).orElse(null);
            assignmentService.assignSubject(teacherId, classSectionId, subjectName, isHomeClass, tenantId);
            redirectAttributes.addFlashAttribute("successMessage",
                    "Assignment created successfully.");
        } catch (IllegalStateException e) {
            redirectAttributes.addFlashAttribute("errorMessage",
                    "Duplicate assignment: " + e.getMessage());
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage",
                    "Failed to create assignment: " + e.getMessage());
        }
        return "redirect:/web/admin/assignments?teacher=" + teacherId;
    }

    // ─── POST /web/admin/assignments/remove/{id} ──────────────────────────────

    @PostMapping("/web/admin/assignments/remove/{assignmentId}")
    public String remove(
            @PathVariable UUID assignmentId,
            @RequestParam(required = false) UUID teacherId,
            Authentication authentication,
            RedirectAttributes redirectAttributes) {
        try {
            UUID tenantId = currentUserService.getCurrentTenantId(authentication).orElse(null);
            assignmentService.removeAssignment(assignmentId, tenantId);
            redirectAttributes.addFlashAttribute("successMessage", "Assignment removed.");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage",
                    "Failed to remove assignment: " + e.getMessage());
        }
        String redirect = teacherId != null
                ? "redirect:/web/admin/assignments?teacher=" + teacherId
                : "redirect:/web/admin/assignments";
        return redirect;
    }

    // ─── Helper ───────────────────────────────────────────────────────────────

    private String resolveRole(Authentication authentication) {
        if (authentication == null) return "ADMIN";
        return authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .filter(a -> a.startsWith("ROLE_"))
                .map(a -> a.substring(5))
                .findFirst()
                .orElse("ADMIN");
    }
}

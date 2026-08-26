package com.concept.roster.web;

import com.concept.roster.app.ChildDetails;
import com.concept.roster.app.StudentAdminService;
import com.concept.roster.app.StudentProfileNotFoundException;
import com.concept.tenant.TenantContext;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Interface layer for admin student/parent management. Binds requests, enforces
 * the ADMIN-only gate, resolves the tenant, and shapes redirects/flash + JSON
 * responses. All decisions and storage access live in the application layer —
 * no entities, no repositories here (ADR 0001).
 */
@Controller
public class StudentAdminController {

    private final StudentAdminService studentAdminService;
    private final TenantContext tenantContext;

    public StudentAdminController(StudentAdminService studentAdminService, TenantContext tenantContext) {
        this.studentAdminService = studentAdminService;
        this.tenantContext = tenantContext;
    }

    @PostMapping("/web/admin/student/add")
    public String addStudent(@RequestParam("firstName") String firstName,
                             @RequestParam("lastName") String lastName,
                             @RequestParam("rollNumber") String rollNumber,
                             @RequestParam("schoolClassId") UUID schoolClassId,
                             @RequestParam(value = "loginEmail", required = false) String loginEmail,
                             @RequestParam(value = "loginPassword", required = false) String loginPassword,
                             @RequestParam(value = "guardianFirstName", required = false) String guardianFirstName,
                             @RequestParam(value = "guardianLastName", required = false) String guardianLastName,
                             @RequestParam(value = "guardianPhone", required = false) String guardianPhone,
                             Authentication authentication,
                             RedirectAttributes redirectAttributes) {
        requireAdmin(authentication, "register students");
        String credentials = studentAdminService.addStudent(firstName, lastName, rollNumber, schoolClassId,
                loginEmail, loginPassword, guardianFirstName, guardianLastName, guardianPhone,
                tenantContext.getTenantId().orElse(null), tenantContext.getAcademicYearId().orElse(null),
                authentication);
        if (credentials != null) {
            // Flash attribute (server-side, not in the URL) so the one-time
            // credentials render without leaking into the address bar/history.
            redirectAttributes.addFlashAttribute("newCredentials", credentials);
        }
        return "redirect:/web/admin/management?success=student_added";
    }

    @PostMapping("/web/admin/parent/add")
    @ResponseBody
    public Object addParent(@RequestParam("firstName") String firstName,
                            @RequestParam("lastName") String lastName,
                            @RequestParam(value = "phoneNumber", required = false) String phoneNumber,
                            @RequestParam(value = "studentId", required = false) UUID studentId,
                            @RequestParam(value = "loginEmail", required = false) String loginEmail,
                            @RequestParam(value = "loginPassword", required = false) String loginPassword,
                            Authentication authentication) {
        try {
            UUID id = studentAdminService.addParent(firstName, lastName, phoneNumber, studentId,
                    loginEmail, loginPassword,
                    tenantContext.getTenantId().orElse(null), tenantContext.getAcademicYearId().orElse(null),
                    authentication);
            return Map.of("status", "created", "id", id);
        } catch (IllegalArgumentException e) {
            return Map.of("error", e.getMessage());
        }
    }

    @PostMapping("/web/admin/student/{id}/update")
    public String updateStudent(@PathVariable("id") UUID id,
                                @RequestParam("firstName") String firstName,
                                @RequestParam("lastName") String lastName,
                                @RequestParam("rollNumber") String rollNumber,
                                @RequestParam(value = "schoolClassId", required = false) UUID schoolClassId,
                                @RequestParam(value = "guardianFirstName", required = false) String guardianFirstName,
                                @RequestParam(value = "guardianLastName", required = false) String guardianLastName,
                                @RequestParam(value = "guardianPhone", required = false) String guardianPhone,
                                @RequestParam(value = "dateOfBirth", required = false)
                                @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE)
                                java.time.LocalDate dateOfBirth,
                                @RequestParam(value = "medicalNotes", required = false) String medicalNotes,
                                @RequestParam(value = "emergencyContactName", required = false) String emergencyContactName,
                                @RequestParam(value = "emergencyContactPhone", required = false) String emergencyContactPhone,
                                Authentication authentication,
                                RedirectAttributes ra) {
        try {
            Optional<String> credentials = studentAdminService.updateStudent(id,
                    tenantContext.getTenantId().orElse(null), tenantContext.getAcademicYearId().orElse(null),
                    firstName, lastName, rollNumber, schoolClassId,
                    guardianFirstName, guardianLastName, guardianPhone,
                    new ChildDetails(dateOfBirth, medicalNotes, emergencyContactName, emergencyContactPhone),
                    authentication);
            credentials.ifPresent(c -> ra.addFlashAttribute("newCredentials", c));
            ra.addFlashAttribute("profileMessage", "Student details updated.");
            return "redirect:/web/teacher/student/" + id;
        } catch (StudentProfileNotFoundException e) {
            ra.addFlashAttribute("errorMessage", "Student not found.");
            return "redirect:/web/admin/management";
        } catch (IllegalArgumentException e) {
            ra.addFlashAttribute("errorMessage", e.getMessage());
            return "redirect:/web/teacher/student/" + id;
        }
    }

    @PostMapping("/web/admin/student/{id}/reset-login")
    public String resetStudentLogin(@PathVariable("id") UUID id, Authentication authentication,
                                    RedirectAttributes ra) {
        try {
            String credentials = studentAdminService.resetStudentLogin(id,
                    tenantContext.getTenantId().orElse(null), authentication);
            ra.addFlashAttribute("newCredentials", credentials);
            return "redirect:/web/teacher/student/" + id;
        } catch (StudentProfileNotFoundException e) {
            ra.addFlashAttribute("errorMessage", "Student not found.");
            return "redirect:/web/admin/management";
        } catch (IllegalArgumentException e) {
            ra.addFlashAttribute("errorMessage", e.getMessage());
            return "redirect:/web/teacher/student/" + id;
        }
    }

    @PostMapping("/web/admin/parent/{parentId}/reset-login")
    public String resetParentLogin(@PathVariable("parentId") UUID parentId,
                                   @RequestParam("studentId") UUID studentId,
                                   Authentication authentication,
                                   RedirectAttributes ra) {
        try {
            String credentials = studentAdminService.resetParentLogin(parentId,
                    tenantContext.getTenantId().orElse(null), authentication);
            ra.addFlashAttribute("newCredentials", credentials);
        } catch (StudentProfileNotFoundException e) {
            ra.addFlashAttribute("errorMessage", "Guardian not found.");
        } catch (IllegalArgumentException e) {
            ra.addFlashAttribute("errorMessage", e.getMessage());
        }
        return "redirect:/web/teacher/student/" + studentId;
    }

    @PostMapping("/web/admin/student/{id}/remove")
    public String removeStudent(@PathVariable("id") UUID id, Authentication authentication,
                                RedirectAttributes ra) {
        requireAdmin(authentication, "remove students");
        try {
            studentAdminService.removeStudent(id, tenantContext.getTenantId().orElse(null), authentication);
        } catch (StudentProfileNotFoundException e) {
            ra.addFlashAttribute("errorMessage", "Student not found.");
            return "redirect:/web/admin/management";
        }
        return "redirect:/web/admin/management?success=student_removed";
    }

    /** Defense-in-depth: only ADMIN may register/remove students (SecurityConfig also gates the URL). */
    private void requireAdmin(Authentication authentication, String action) {
        if (authentication == null) return;
        for (GrantedAuthority auth : authentication.getAuthorities()) {
            if ("ROLE_ADMIN".equals(auth.getAuthority())) {
                return;
            }
        }
        throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only administrators can " + action);
    }
}

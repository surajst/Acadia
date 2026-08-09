package com.concept.roster.web;

import com.concept.common.AuditLogService;
import com.concept.roster.app.RosterImportService;
import com.concept.user.User;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

/**
 * Thin web layer over {@link RosterImportService}: handles auth/session, stashes
 * the pending preview between the two-step upload, maps results onto the model,
 * and writes the audit entries. All parsing/validation/persistence lives in the
 * service.
 */
@Controller
@RequestMapping("/web/management/upload")
public class UploadWebController {

    @Autowired
    private RosterImportService rosterImportService;

    @Autowired
    private AuditLogService auditLogService;

    @Autowired
    private com.concept.user.CurrentUserService currentUserService;

    @GetMapping
    public String showUploadPage(HttpSession session) {
        if (session.getAttribute("currentUser") == null) {
            return "redirect:/web/login";
        }
        return "upload";
    }

    private static final String PENDING_IMPORT_KEY = "pendingStudentImport";

    /**
     * Step 1 of the two-step import: run a dry-run preview (nothing is written),
     * stash the parsed rows in the session, and render the preview for the admin
     * to confirm.
     */
    @PostMapping("/process")
    public String previewUpload(@RequestParam("file") MultipartFile file,
                                HttpSession session,
                                Model model) {
        User currentUser = (User) session.getAttribute("currentUser");
        if (currentUser == null) return "redirect:/web/login";

        if (file.isEmpty()) {
            model.addAttribute("error", "Please select a valid CSV or Excel file.");
            return "upload";
        }

        RosterImportService.StudentPreview preview;
        try {
            preview = rosterImportService.previewStudents(file.getInputStream(), file.getOriginalFilename(), currentUser);
        } catch (Exception e) {
            model.addAttribute("error", "Could not read the uploaded file: " + e.getMessage());
            return "upload";
        }

        session.setAttribute(PENDING_IMPORT_KEY, preview.rows());
        model.addAttribute("previewRows", preview.outcomes());
        model.addAttribute("previewSummary", preview.summary());
        model.addAttribute("previewCanCommit", preview.canCommit());
        return "upload";
    }

    /** Step 2: commit the rows stashed by {@link #previewUpload}. */
    @PostMapping("/confirm")
    public String confirmUpload(HttpSession session,
                                Authentication authentication,
                                Model model) {
        User currentUser = (User) session.getAttribute("currentUser");
        if (currentUser == null) return "redirect:/web/login";

        @SuppressWarnings("unchecked")
        List<List<String>> allRows = (List<List<String>>) session.getAttribute(PENDING_IMPORT_KEY);
        session.removeAttribute(PENDING_IMPORT_KEY);
        if (allRows == null) {
            model.addAttribute("error", "Your import preview expired. Please upload the file again.");
            return "upload";
        }

        RosterImportService.ImportResult result = rosterImportService.commitStudents(allRows, currentUser);

        auditLogService.log(authentication, "ROSTER_BULK_IMPORT", "Student", null,
                "Bulk import: " + result.created() + " created, " + result.skipped() + " skipped, " + result.failed() + " failed");

        model.addAttribute("success", result.created() + " student" + (result.created() == 1 ? "" : "s") + " imported"
                + (result.skipped() > 0 ? ", " + result.skipped() + " skipped as duplicates" : "")
                + (result.failed() > 0 ? ", " + result.failed() + " row(s) failed" : "") + ".");
        model.addAttribute("rowResults", result.outcomes());
        return "upload";
    }

    /** Discard a pending import preview without committing anything. */
    @PostMapping("/cancel")
    public String cancelUpload(HttpSession session) {
        session.removeAttribute(PENDING_IMPORT_KEY);
        return "redirect:/web/management/upload";
    }

    @PostMapping("/staff/process")
    public String processStaffUpload(@RequestParam("file") MultipartFile file,
                                     HttpSession session,
                                     Authentication authentication,
                                     Model model) {
        User currentUser = (User) session.getAttribute("currentUser");
        if (currentUser == null) return "redirect:/web/login";

        if (file.isEmpty()) {
            model.addAttribute("staffError", "Please select a valid CSV or Excel file.");
            return "upload";
        }

        UUID tenantId = currentUserService.getCurrentTenantId(authentication).orElse(currentUser.getTenantId());
        UUID academicYearId = currentUserService.getCurrentAcademicYearId(authentication).orElse(currentUser.getAcademicYearId());

        RosterImportService.ImportResult result;
        try {
            result = rosterImportService.importStaff(file.getInputStream(), file.getOriginalFilename(), tenantId, academicYearId);
        } catch (Exception e) {
            model.addAttribute("staffError", "Could not read the uploaded file: " + e.getMessage());
            return "upload";
        }

        auditLogService.log(authentication, "STAFF_BULK_IMPORT", "User", null,
                "Bulk staff import: " + result.created() + " created, " + result.skipped() + " skipped, " + result.failed() + " failed");

        model.addAttribute("staffSuccess", result.created() + " staff member" + (result.created() == 1 ? "" : "s") + " invited"
                + (result.skipped() > 0 ? ", " + result.skipped() + " skipped as duplicates" : "")
                + (result.failed() > 0 ? ", " + result.failed() + " row(s) failed" : "") + ". All are pending approval before they can sign in.");
        model.addAttribute("staffRowResults", result.outcomes());
        return "upload";
    }
}

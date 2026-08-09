package com.concept.export.web;

import com.concept.export.app.ExportService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;

/**
 * Interface layer for admin data export — tenant-scoped CSV downloads for
 * students, staff, and fees. Thin binding over {@link ExportService}; the
 * tenant scoping lives in the service (ADR 0001). ADMIN only.
 */
@RestController
@RequestMapping("/web/admin/export")
@PreAuthorize("hasRole('ADMIN')")
public class ExportController {

    private final ExportService exportService;

    public ExportController(ExportService exportService) {
        this.exportService = exportService;
    }

    @GetMapping("/students.csv")
    public ResponseEntity<byte[]> exportStudents(Authentication authentication) {
        return csv("students", exportService.studentsCsv(authentication));
    }

    @GetMapping("/staff.csv")
    public ResponseEntity<byte[]> exportStaff(Authentication authentication) {
        return csv("staff", exportService.staffCsv(authentication));
    }

    @GetMapping("/fees.csv")
    public ResponseEntity<byte[]> exportFees(Authentication authentication) {
        return csv("fees", exportService.feesCsv(authentication));
    }

    private static ResponseEntity<byte[]> csv(String name, String body) {
        String filename = "acadia-" + name + "-" + LocalDate.now() + ".csv";
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(new MediaType("text", "csv", StandardCharsets.UTF_8));
        headers.setContentDispositionFormData("attachment", filename);
        return ResponseEntity.ok().headers(headers).body(bytes);
    }
}

package com.concept.assessment.web;

import com.concept.assessment.app.AssessmentException;
import com.concept.assessment.app.AssessmentService;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.UUID;

/**
 * Interface layer for report-card PDF downloads (teacher/parent/student). Thin
 * binding over {@link AssessmentService}; ownership checks and PDF generation
 * live in the service, the HTTP/PDF envelope stays here (ADR 0001).
 */
@RestController
public class ReportCardController {

    private final AssessmentService assessmentService;

    public ReportCardController(AssessmentService assessmentService) {
        this.assessmentService = assessmentService;
    }

    @GetMapping("/api/teacher/students/{studentId}/report-card")
    @PreAuthorize("hasAnyRole('TEACHER', 'ADMIN')")
    public ResponseEntity<?> teacherReportCard(@PathVariable UUID studentId, @RequestParam String term) {
        return pdfResponse(assessmentService.teacherReportCard(studentId, term), term);
    }

    @GetMapping("/api/mobile/parent/report-card")
    @PreAuthorize("hasRole('PARENT')")
    public ResponseEntity<?> parentReportCard(@RequestParam(required = false) UUID studentId,
                                              @RequestParam String term,
                                              Authentication authentication) {
        return pdfResponse(assessmentService.parentReportCard(studentId, term, authentication), term);
    }

    @GetMapping("/api/student/report-card")
    @PreAuthorize("hasRole('STUDENT')")
    public ResponseEntity<?> studentReportCard(@RequestParam String term, Authentication authentication) {
        return pdfResponse(assessmentService.studentReportCard(term, authentication), term);
    }

    private ResponseEntity<byte[]> pdfResponse(byte[] pdf, String term) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_PDF);
        headers.setContentDisposition(ContentDisposition.attachment()
                .filename("report_card_" + term + ".pdf")
                .build());
        return new ResponseEntity<>(pdf, headers, HttpStatus.OK);
    }

    @ExceptionHandler(AssessmentException.class)
    public ResponseEntity<?> handle(AssessmentException e) {
        return ResponseEntity.status(e.status()).body(Map.of("error", e.getMessage()));
    }
}

package com.concept.assessment.web;

import com.concept.assessment.app.AssessmentException;
import com.concept.assessment.app.AssessmentService;
import com.concept.assessment.app.BulkScoreEntryRequest;
import com.concept.assessment.app.CreateAssessmentRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.UUID;

/**
 * Interface layer for teacher assessment management. Thin binding over
 * {@link AssessmentService}; tenant/ownership logic and the audit trail live in
 * the service (ADR 0001).
 */
@RestController
@RequestMapping("/api/teacher/assessments")
public class TeacherAssessmentController {

    private final AssessmentService assessmentService;

    public TeacherAssessmentController(AssessmentService assessmentService) {
        this.assessmentService = assessmentService;
    }

    @PostMapping("/create")
    @PreAuthorize("hasAnyRole('TEACHER', 'ADMIN')")
    public ResponseEntity<?> createAssessment(@RequestBody CreateAssessmentRequest request, Authentication authentication) {
        return ResponseEntity.ok(assessmentService.createAssessment(request, authentication));
    }

    @GetMapping("/class/{classSectionId}")
    @PreAuthorize("hasAnyRole('TEACHER', 'ADMIN')")
    public ResponseEntity<?> getAssessmentsForClass(@PathVariable UUID classSectionId) {
        return ResponseEntity.ok(assessmentService.assessmentsForClass(classSectionId));
    }

    @GetMapping("/{assessmentId}")
    @PreAuthorize("hasAnyRole('TEACHER', 'ADMIN')")
    public ResponseEntity<?> getAssessmentDetail(@PathVariable UUID assessmentId) {
        return ResponseEntity.ok(assessmentService.assessmentDetail(assessmentId));
    }

    @PostMapping("/{assessmentId}/scores")
    @PreAuthorize("hasAnyRole('TEACHER', 'ADMIN')")
    public ResponseEntity<?> enterScores(@PathVariable UUID assessmentId,
                                         @RequestBody BulkScoreEntryRequest request,
                                         Authentication authentication) {
        return ResponseEntity.ok(assessmentService.enterScores(assessmentId, request, authentication));
    }

    @ExceptionHandler(AssessmentException.class)
    public ResponseEntity<?> handle(AssessmentException e) {
        return ResponseEntity.status(e.status()).body(Map.of("error", e.getMessage()));
    }
}

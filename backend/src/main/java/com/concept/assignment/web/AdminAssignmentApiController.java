package com.concept.assignment.web;

import com.concept.assignment.app.AssignmentException;
import com.concept.assignment.app.AssignmentService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
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
 * Interface layer for the assignment JSON API. Thin binding over
 * {@link AssignmentService}; tenant resolution, duplicate handling, and
 * entity→map shaping live in the service (ADR 0001).
 */
@RestController
@RequestMapping("/api/admin/assignments")
public class AdminAssignmentApiController {

    private final AssignmentService assignmentService;

    public AdminAssignmentApiController(AssignmentService assignmentService) {
        this.assignmentService = assignmentService;
    }

    @PostMapping("/assign")
    @PreAuthorize("hasAnyRole('ADMIN', 'TEACHER')")
    public ResponseEntity<?> assign(@RequestBody Map<String, Object> body, Authentication authentication) {
        return ResponseEntity.ok(assignmentService.assignFromBody(body, authentication));
    }

    @DeleteMapping("/{assignmentId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'TEACHER')")
    public ResponseEntity<?> remove(@PathVariable UUID assignmentId, Authentication authentication) {
        return ResponseEntity.ok(assignmentService.remove(assignmentId, authentication));
    }

    @GetMapping("/teacher/{teacherId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'TEACHER')")
    public ResponseEntity<?> byTeacher(@PathVariable UUID teacherId, Authentication authentication) {
        return ResponseEntity.ok(assignmentService.byTeacher(teacherId, authentication));
    }

    @GetMapping("/class/{classSectionId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'TEACHER')")
    public ResponseEntity<?> byClass(@PathVariable UUID classSectionId, Authentication authentication) {
        return ResponseEntity.ok(assignmentService.byClass(classSectionId, authentication));
    }

    @GetMapping("/all-teachers")
    @PreAuthorize("hasAnyRole('ADMIN', 'TEACHER')")
    public ResponseEntity<?> allTeachers(Authentication authentication) {
        return ResponseEntity.ok(assignmentService.allTeachers(authentication));
    }

    @PostMapping("/seed")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> seed() {
        return ResponseEntity.ok(assignmentService.seed());
    }

    @ExceptionHandler(AssignmentException.class)
    public ResponseEntity<?> handle(AssignmentException e) {
        return ResponseEntity.status(e.status()).body(Map.of("error", e.getMessage()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<?> handleUnexpected(Exception e) {
        return ResponseEntity.status(500).body(Map.of("error", String.valueOf(e.getMessage())));
    }
}

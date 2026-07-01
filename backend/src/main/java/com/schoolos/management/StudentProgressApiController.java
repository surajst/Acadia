package com.schoolos.management;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/student/progress")
@PreAuthorize("hasRole('STUDENT')")
public class StudentProgressApiController {

    private final StudentProgressService studentProgressService;
    private final StudentRepository studentRepository;

    public StudentProgressApiController(StudentProgressService studentProgressService,
                                         StudentRepository studentRepository) {
        this.studentProgressService = studentProgressService;
        this.studentRepository = studentRepository;
    }

    /**
     * GET /api/student/progress
     * Returns all curriculum topics grouped by subject with completion status.
     * Shape: { "SCIENCE": { "completed": 3, "total": 13, "topics": [...] }, ... }
     */
    @GetMapping
    public ResponseEntity<?> getProgress(Authentication authentication) {
        try {
            Student student = resolveStudent(authentication);
            Map<String, SubjectProgressDto> progress =
                    studentProgressService.getProgressByStudent(student.getId());
            return ResponseEntity.ok(progress);
        } catch (Exception e) {
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * POST /api/student/progress/complete
     * Body: { "curriculumId": "uuid-string" }
     * Marks a topic complete and awards XP.
     */
    @PostMapping("/complete")
    @Transactional
    public ResponseEntity<?> markComplete(@RequestBody Map<String, String> body,
                                           Authentication authentication) {
        try {
            String curriculumIdStr = body.get("curriculumId");
            if (curriculumIdStr == null || curriculumIdStr.isBlank()) {
                return ResponseEntity.badRequest()
                        .body(Map.of("error", "curriculumId is required"));
            }

            UUID curriculumId;
            try {
                curriculumId = UUID.fromString(curriculumIdStr);
            } catch (IllegalArgumentException e) {
                return ResponseEntity.badRequest()
                        .body(Map.of("error", "Invalid curriculumId format"));
            }

            Student student = resolveStudent(authentication);
            Map<String, SubjectProgressDto> updatedProgress =
                    studentProgressService.markTopicComplete(student.getId(), curriculumId);

            return ResponseEntity.ok(updatedProgress);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Resolves the currently authenticated student using the same pattern
     * as StudentPortalController.resolveStudent().
     */
    private Student resolveStudent(Authentication authentication) {
        String username = (authentication != null) ? authentication.getName() : null;
        if (username == null || username.isBlank()) {
            throw new IllegalArgumentException("Not authenticated");
        }
        if (username.contains("@")) {
            username = username.substring(0, username.indexOf("@"));
        }
        // Pilot student accounts use username format "student_N" mapped to roll number "Pilot-N"
        if (username.startsWith("student_")) {
            String suffix = username.substring(8);
            for (Student s : studentRepository.findAll()) {
                if (("Pilot-" + suffix).equals(s.getRollNumber())) {
                    return s;
                }
            }
        }
        final String finalUsername = username;
        return studentRepository.findByFirstNameIgnoreCase(finalUsername)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Student record not found for username: " + finalUsername));
    }
}

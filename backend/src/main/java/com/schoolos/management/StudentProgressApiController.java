package com.schoolos.management;

import com.schoolos.user.CurrentUserService;
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
    private final CurrentUserService currentUserService;

    public StudentProgressApiController(StudentProgressService studentProgressService,
                                         StudentRepository studentRepository,
                                         CurrentUserService currentUserService) {
        this.studentProgressService = studentProgressService;
        this.studentRepository = studentRepository;
        this.currentUserService = currentUserService;
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
     * Resolves the currently authenticated student via their userId FK —
     * the previous implementation matched by firstName/roll-number pattern
     * with no tenant scoping, so an authenticated student could be resolved
     * to a different, same-named student in another tenant entirely.
     */
    private Student resolveStudent(Authentication authentication) {
        return currentUserService.getCurrentStudent(authentication)
                .orElseThrow(() -> new IllegalArgumentException("Student record not found"));
    }
}

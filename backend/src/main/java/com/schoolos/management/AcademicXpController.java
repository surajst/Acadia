package com.schoolos.management;

import com.schoolos.user.CurrentUserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/academic")
public class AcademicXpController {

    @Autowired
    private AcademicSubmissionRepository submissionRepository;

    @Autowired
    private StudentRepository studentRepository; // Existing repository

    @Autowired
    private CurrentUserService currentUserService;

    // 1. Student hits "Practice" -> Create a pending submission row instead of granting instant XP
    @PostMapping("/submit-task")
    @PreAuthorize("hasRole('STUDENT')")
    public ResponseEntity<String> submitTask(
            @RequestParam UUID studentId,
            @RequestParam String skillName,
            @RequestParam Integer xpBounty,
            Authentication authentication) {

        // studentId must be the caller's own student record — never trust it blindly.
        UUID ownStudentId = currentUserService.getCurrentStudent(authentication).map(Student::getId).orElse(null);
        if (ownStudentId == null || !ownStudentId.equals(studentId)) {
            return ResponseEntity.status(403).body("Error: Not authorized for this student.");
        }

        // Save the pending task to the database queue
        AcademicSubmission submission = new AcademicSubmission(studentId, skillName, xpBounty);
        submissionRepository.save(submission);

        return ResponseEntity.ok("Task successfully queued for teacher validation.");
    }

    // 2. Teacher views the queue -> Returns all pending items
    @GetMapping("/teacher/pending")
    @PreAuthorize("hasAnyRole('TEACHER', 'ADMIN', 'PRINCIPAL')")
    public ResponseEntity<List<AcademicSubmission>> getPendingSubmissions() {
        List<AcademicSubmission> pending = submissionRepository.findByStatus("PENDING");
        return ResponseEntity.ok(pending);
    }

    // 3. Teacher Approves -> Status changes to APPROVED and Student Metrics table is updated
    @PostMapping("/teacher/approve-xp")
    @PreAuthorize("hasAnyRole('TEACHER', 'ADMIN', 'PRINCIPAL')")
    public ResponseEntity<String> approveXp(@RequestParam UUID submissionId) {
        return submissionRepository.findById(submissionId).map(submission -> {
            if (!"PENDING".equals(submission.getStatus())) {
                return ResponseEntity.badRequest().body("This task has already been processed.");
            }

            // Mark submission as officially approved
            submission.setStatus("APPROVED");
            submissionRepository.save(submission);

            // TODO: Update the student_metrics table setting school_xp = school_xp + submission.getXpBounty()

            return ResponseEntity.ok("XP approved and allocated successfully!");
        }).orElse(ResponseEntity.notFound().build());
    }
}

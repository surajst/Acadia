package com.schoolos.management;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
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

    // 1. Student hits "Practice" -> Create a pending submission row instead of granting instant XP
    @PostMapping("/submit-task")
    public ResponseEntity<String> submitTask(
            @RequestParam UUID studentId, 
            @RequestParam String skillName,
            @RequestParam Integer xpBounty) {
        
        // Ensure the student actually exists in the system baseline
        if (!studentRepository.existsById(studentId)) {
            return ResponseEntity.badRequest().body("Error: Student profile not found.");
        }

        // Save the pending task to the database queue
        AcademicSubmission submission = new AcademicSubmission(studentId, skillName, xpBounty);
        submissionRepository.save(submission);

        System.out.println("[DATABASE SECURED] Task '" + skillName + "' queued for Student ID: " + studentId);
        return ResponseEntity.ok("Task successfully queued for teacher validation.");
    }

    // 2. Teacher views the queue -> Returns all pending items
    @GetMapping("/teacher/pending")
    public ResponseEntity<List<AcademicSubmission>> getPendingSubmissions() {
        List<AcademicSubmission> pending = submissionRepository.findByStatus("PENDING");
        return ResponseEntity.ok(pending);
    }

    // 3. Teacher Approves -> Status changes to APPROVED and Student Metrics table is updated
    @PostMapping("/teacher/approve-xp")
    public ResponseEntity<String> approveXp(@RequestParam UUID submissionId) {
        return submissionRepository.findById(submissionId).map(submission -> {
            if (!"PENDING".equals(submission.getStatus())) {
                return ResponseEntity.badRequest().body("This task has already been processed.");
            }

            // Mark submission as officially approved
            submission.setStatus("APPROVED");
            submissionRepository.save(submission);

            // TODO: Update the student_metrics table setting school_xp = school_xp + submission.getXpBounty()
            
            System.out.println("[XP DISPATCHED] Approved +" + submission.getXpBounty() + " XP for skill: " + submission.getSkillName());
            return ResponseEntity.ok("XP approved and allocated successfully!");
        }).orElse(ResponseEntity.notFound().build());
    }
}

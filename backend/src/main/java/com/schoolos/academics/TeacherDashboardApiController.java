package com.schoolos.academics;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import jakarta.transaction.Transactional;
import java.time.LocalDateTime;
import java.util.UUID;

@RestController
@RequestMapping("/api/academic")
public class TeacherDashboardApiController {

    @Autowired
    private TeacherVerificationRepository submissionRepo; // Updated here

    @Autowired
    private StudentMetricRepository studentMetricRepository;

    @Autowired
    private MathSkillRepository mathSkillRepo;

    @PostMapping("/verify-task")
    @Transactional
    public ResponseEntity<?> verifyTask(
            @RequestParam UUID submissionId,
            @RequestParam String status) {

        AcademicSubmission submission = submissionRepo.findById(submissionId).orElse(null);
        if (submission == null) {
            return ResponseEntity.badRequest().body("Submission verification target not found.");
        }

        if (!"PENDING".equals(submission.getStatus())) {
            return ResponseEntity.badRequest().body("This assignment submission has already been evaluated.");
        }

        UUID currentTeacherId = UUID.fromString("11111111-1111-1111-1111-111111111111");
        submission.setEvaluatedBy(currentTeacherId);
        submission.setEvaluatedAt(LocalDateTime.now());

        if ("APPROVE".equals(status)) {
            submission.setStatus("APPROVED");

            Integer xpReward = mathSkillRepo.findById(submission.getSkillId())
                    .map(MathSkill::getMaxXpReward)
                    .orElse(100);

            studentMetricRepository.findById(submission.getStudentId()).ifPresent(metric -> {
                Integer currentSchoolXp = metric.getSchoolXp() != null ? metric.getSchoolXp() : 0;
                metric.setSchoolXp(currentSchoolXp + xpReward);
                studentMetricRepository.save(metric);
            });

            submissionRepo.save(submission);
            return ResponseEntity.ok("Submission approved successfully. Academic XP distributed.");
            
        } else if ("REJECT".equals(status)) {
            submission.setStatus("REJECTED");
            submissionRepo.save(submission);
            return ResponseEntity.ok("Submission rejected and archived. No XP issued.");
        }

        return ResponseEntity.badRequest().body("Invalid evaluation resolution action state flag.");
    }
}

package com.schoolos.management;

import com.schoolos.academics.StudentMetric;
import com.schoolos.academics.StudentMetricRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/teacher/milestone")
public class TeacherMilestoneApiController {

    @Autowired
    private AcademicSubmissionRepository academicSubmissionRepository;

    @Autowired
    private StudentMetricRepository studentMetricRepository;

    @Autowired
    private StudentRepository studentRepository;

    @Transactional
    @PostMapping("/approve")
    @PreAuthorize("hasAnyRole('TEACHER', 'ADMIN')")
    public ResponseEntity<?> approveMilestone(@RequestParam("submissionId") UUID submissionId) {
        try {
            AcademicSubmission submission = academicSubmissionRepository.findById(submissionId)
                    .orElseThrow(() -> new IllegalArgumentException("Invalid submission ID"));

            if ("APPROVED".equals(submission.getStatus())) {
                return ResponseEntity.badRequest().body(Map.of("error", "Submission is already approved"));
            }

            submission.setStatus("APPROVED");
            academicSubmissionRepository.saveAndFlush(submission);

            // Award XP
            Student student = studentRepository.findById(submission.getStudentId())
                    .orElseThrow(() -> new IllegalArgumentException("Student not found"));
            StudentMetric metric = studentMetricRepository.findByStudentId(student.getId()).orElse(null);
            
            if (metric == null) {
                metric = new StudentMetric();
                metric.setId(UUID.randomUUID());
                metric.setStudent(student);
                metric.setTenantId(student.getTenantId());
                metric.setAcademicYearId(student.getAcademicYearId());
                metric.setSchoolXp(0);
                metric.setParentXp(0);
                metric.setActiveStreak(0);
            }
            
            int currentXp = metric.getSchoolXp() != null ? metric.getSchoolXp() : 0;
            metric.setSchoolXp(currentXp + submission.getXpBounty());
            studentMetricRepository.saveAndFlush(metric);

            return ResponseEntity.ok(Map.of("message", "Milestone approved successfully"));

        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @Transactional
    @PostMapping("/reject")
    @PreAuthorize("hasAnyRole('TEACHER', 'ADMIN')")
    public ResponseEntity<?> rejectMilestone(@RequestParam("submissionId") UUID submissionId,
                                            @RequestParam(value = "reason", required = false) String reason) {
        try {
            AcademicSubmission submission = academicSubmissionRepository.findById(submissionId)
                    .orElseThrow(() -> new IllegalArgumentException("Invalid submission ID"));

            submission.setStatus("REJECTED");
            submission.setRejectionReason(reason);
            academicSubmissionRepository.saveAndFlush(submission);

            return ResponseEntity.ok(Map.of("message", "Milestone rejected"));

        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }
}

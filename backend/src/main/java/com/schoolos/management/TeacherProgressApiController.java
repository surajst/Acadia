package com.schoolos.management;

import com.schoolos.academics.StudentMetric;
import com.schoolos.academics.StudentMetricRepository;
import com.schoolos.common.NotificationDeliveryService;
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
@RequestMapping("/api/teacher/progress")
public class TeacherProgressApiController {

    @Autowired
    private StudentProgressRepository studentProgressRepository;

    @Autowired
    private CurriculumRepository curriculumRepository;

    @Autowired
    private StudentMetricRepository studentMetricRepository;

    @Autowired
    private StudentRepository studentRepository;

    @Autowired
    private NotificationDeliveryService notificationDeliveryService;

    @Transactional
    @PostMapping("/approve")
    @PreAuthorize("hasAnyRole('TEACHER', 'ADMIN', 'PRINCIPAL')")
    public ResponseEntity<?> approveProgress(@RequestParam("studentProgressId") UUID studentProgressId) {
        try {
            StudentProgress progress = studentProgressRepository.findById(studentProgressId)
                    .orElseThrow(() -> new IllegalArgumentException("Invalid student progress ID"));

            if ("APPROVED".equals(progress.getStatus())) {
                return ResponseEntity.badRequest().body(Map.of("error", "Progress is already approved"));
            }

            progress.setStatus("APPROVED");
            progress.setCompleted(true);
            progress.setCompletedAt(java.time.LocalDateTime.now());
            studentProgressRepository.saveAndFlush(progress);

            // Award XP
            Curriculum curriculum = progress.getCurriculum();
            Student student = progress.getStudent();

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
            metric.setSchoolXp(currentXp + curriculum.getXpReward());
            studentMetricRepository.saveAndFlush(metric);

            // Dispatch Mock WhatsApp Notification
            notificationDeliveryService.send(student.getFirstName() + " " + student.getLastName(),
                    "[ALERT WHATSAPP DISPATCH] Sending to Student " + student.getFirstName() + " " + student.getLastName() +
                            ": ✅ " + curriculum.getTopicName() + " verified! +" + curriculum.getXpReward() + " XP awarded.");

            return ResponseEntity.ok(Map.of("message", "Progress approved successfully", "xpAwarded", curriculum.getXpReward()));

        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @Transactional
    @PostMapping("/reject")
    @PreAuthorize("hasAnyRole('TEACHER', 'ADMIN', 'PRINCIPAL')")
    public ResponseEntity<?> rejectProgress(@RequestParam("studentProgressId") UUID studentProgressId,
                                            @RequestParam(value = "reason", required = false) String reason) {
        try {
            StudentProgress progress = studentProgressRepository.findById(studentProgressId)
                    .orElseThrow(() -> new IllegalArgumentException("Invalid student progress ID"));

            progress.setStatus("REJECTED");
            progress.setRejectionReason(reason);
            studentProgressRepository.saveAndFlush(progress);

            Curriculum curriculum = progress.getCurriculum();
            Student student = progress.getStudent();

            String displayReason = (reason != null && !reason.trim().isEmpty()) ? reason : "No reason provided";

            // Dispatch Mock WhatsApp Notification
            notificationDeliveryService.send(student.getFirstName() + " " + student.getLastName(),
                    "[ALERT WHATSAPP DISPATCH] Sending to Student " + student.getFirstName() + " " + student.getLastName() +
                            ": ❌ " + curriculum.getTopicName() + " needs review — " + displayReason + ".");

            return ResponseEntity.ok(Map.of("message", "Progress rejected"));

        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }
}

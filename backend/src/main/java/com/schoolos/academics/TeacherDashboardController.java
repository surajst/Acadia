package com.schoolos.academics;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Controller
public class TeacherDashboardController {

    @Autowired
    private TeacherVerificationRepository submissionRepo; // Updated here

    @Autowired
    private MathSkillRepository mathSkillRepo;

    @Autowired
    private StudentMetricRepository studentMetricRepository;

    // Commented out to avoid route collision with UnifiedDashboardWebController
    // @GetMapping("/web/teacher/dashboard")
    // public String viewTeacherDashboard(Model model) {
    //     UUID currentTenantId = UUID.fromString("00000000-0000-0000-0000-000000000000"); 
    // 
    //     List<AcademicSubmission> rawSubmissions = submissionRepo
    //             .findByTenantIdAndStatusOrderBySubmittedAtDesc(currentTenantId, "PENDING");
    // 
    //     List<SubmissionQueueDto> enrichedQueue = new ArrayList<>();
    // 
    //     for (AcademicSubmission sub : rawSubmissions) {
    //         String dynamicSkillName = mathSkillRepo.findById(sub.getSkillId())
    //                 .map(MathSkill::getSkillName)
    //                 .orElse("Unknown Math Assignment");
    // 
    //         Integer xpBounty = mathSkillRepo.findById(sub.getSkillId())
    //                 .map(MathSkill::getMaxXpReward)
    //                 .orElse(100);
    // 
    //         String dynamicStudentName = studentMetricRepository.findById(sub.getStudentId())
    //                 .map(metric -> {
    //                     if (metric.getStudent() != null) {
    //                         return metric.getStudent().getFirstName() + " " + metric.getStudent().getLastName();
    //                     }
    //                     return "Active Student";
    //                 })
    //                 .orElse("Active Student");
    // 
    //         enrichedQueue.add(new SubmissionQueueDto(
    //             sub.getId(),
    //             dynamicStudentName,
    //             dynamicSkillName,
    //             xpBounty,
    //             sub.getSubmittedAt()
    //         ));
    //     }
    // 
    //     model.addAttribute("pendingSubmissions", enrichedQueue);
    //     return "teacher_dashboard";
    // }
}

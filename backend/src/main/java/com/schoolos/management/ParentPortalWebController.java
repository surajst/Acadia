package com.schoolos.management;

import com.schoolos.announcement.Announcement;
import com.schoolos.announcement.AnnouncementRepository;
import com.schoolos.academics.StudentMetric;
import com.schoolos.academics.StudentMetricRepository;
import com.schoolos.user.CurrentUserService;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Controller
@RequestMapping("/web/parent")
public class ParentPortalWebController {

    private final ParentRepository parentRepository;
    private final ParentQuestRepository parentQuestRepository;
    private final ParentRewardRepository parentRewardRepository;
    private final StudentRepository studentRepository;
    private final AnnouncementRepository announcementRepository;
    private final StudentMetricRepository studentMetricRepository;
    private final CurrentUserService currentUserService;

    public ParentPortalWebController(ParentRepository parentRepository,
                                     ParentQuestRepository parentQuestRepository,
                                     ParentRewardRepository parentRewardRepository,
                                     StudentRepository studentRepository,
                                     AnnouncementRepository announcementRepository,
                                     StudentMetricRepository studentMetricRepository,
                                     CurrentUserService currentUserService) {
        this.parentRepository = parentRepository;
        this.parentQuestRepository = parentQuestRepository;
        this.parentRewardRepository = parentRewardRepository;
        this.studentRepository = studentRepository;
        this.announcementRepository = announcementRepository;
        this.studentMetricRepository = studentMetricRepository;
        this.currentUserService = currentUserService;
    }

    @GetMapping("/dashboard")
    public String dashboard(Model model, Authentication authentication) {
        Parent parent = currentUserService.getCurrentParent(authentication).orElse(null);

        if (parent == null) {
            model.addAttribute("errorMessage", "No parent data found. Please seed the database first.");
            model.addAttribute("awaitingQuests", List.of());
            model.addAttribute("awaitingRewards", List.of());
            model.addAttribute("announcements", List.of());
            model.addAttribute("students", List.of());
            model.addAttribute("studentMetrics", new HashMap<String, StudentMetric>());
            model.addAttribute("pendingQuestCounts", new HashMap<String, Long>());
            return "parent_dashboard";
        }

        List<ParentQuest> awaitingQuests = parentQuestRepository.findByParentIdAndStatus(parent.getId(), "COMPLETED_AWAITING_APPROVAL");
        List<ParentReward> awaitingRewards = parentRewardRepository.findByParentIdAndStatus(parent.getId(), "CLAIMED_AWAITING_DELIVERY");

        // Fetch announcements matching parent's student(s) grade(s) or "ALL"
        List<Student> students = studentRepository.findByParentsContaining(parent);
        List<String> targetGrades = new ArrayList<>();
        targetGrades.add("ALL");
        for (Student student : students) {
            if (student.getClassSection() != null && student.getClassSection().getGradeName() != null) {
                targetGrades.add(student.getClassSection().getGradeName());
            }
        }

        UUID tenantId = parent.getTenantId();
        UUID academicYearId = parent.getAcademicYearId();
        if ((tenantId == null || academicYearId == null) && !students.isEmpty()) {
            // Fall back to this parent's own linked child's tenant/year, never
            // to an arbitrary student from another family/tenant.
            Student first = students.get(0);
            tenantId = first.getTenantId();
            academicYearId = first.getAcademicYearId();
        }

        List<Announcement> announcements = announcementRepository.findByTenantIdAndAcademicYearIdAndTargetGradeIn(
                tenantId,
                academicYearId,
                targetGrades
        );

        // Sort descending by creation date (newest first)
        announcements.sort((a, b) -> b.getCreatedAt().compareTo(a.getCreatedAt()));

        model.addAttribute("parent", parent);
        model.addAttribute("awaitingQuests", awaitingQuests);
        model.addAttribute("awaitingRewards", awaitingRewards);
        model.addAttribute("announcements", announcements);

        List<Student> dropdownStudents = studentRepository.findByParentsContaining(parent);
        model.addAttribute("students", dropdownStudents);

        // Build a metrics map: studentId (String) -> StudentMetric for glanceable XP/streak tiles
        Map<String, StudentMetric> studentMetrics = new HashMap<>();
        Map<String, Long> pendingQuestCounts = new HashMap<>();

        for (Student s : dropdownStudents) {
            String studentIdStr = s.getId().toString();
            studentMetricRepository.findByStudentId(s.getId()).ifPresent(m ->
                studentMetrics.put(studentIdStr, m));
            
            long pendingCount = awaitingQuests.stream()
                .filter(q -> q.getStudent() != null && s.getId().equals(q.getStudent().getId()))
                .count();
            pendingQuestCounts.put(studentIdStr, pendingCount);
        }
        
        model.addAttribute("studentMetrics", studentMetrics);
        model.addAttribute("pendingQuestCounts", pendingQuestCounts);

        return "parent_dashboard";
    }
}

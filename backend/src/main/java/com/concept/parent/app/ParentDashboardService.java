package com.concept.parent.app;

import com.concept.academics.StudentMetric;
import com.concept.academics.StudentMetricRepository;
import com.concept.announcement.Announcement;
import com.concept.announcement.AnnouncementRepository;
import com.concept.management.Parent;
import com.concept.management.ParentQuest;
import com.concept.management.ParentQuestRepository;
import com.concept.management.ParentReward;
import com.concept.management.ParentRewardRepository;
import com.concept.management.Student;
import com.concept.management.StudentRepository;
import com.concept.user.CurrentUserService;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Application layer for the parent dashboard (ADR 0001). Runs the same
 * tenant-scoped queries the former god-controller did and flattens every JPA
 * entity into a view record, so the Thymeleaf template renders off flat data
 * and no persistence type reaches the interface layer.
 */
@Service
public class ParentDashboardService {

    private final ParentQuestRepository parentQuestRepository;
    private final ParentRewardRepository parentRewardRepository;
    private final StudentRepository studentRepository;
    private final AnnouncementRepository announcementRepository;
    private final StudentMetricRepository studentMetricRepository;
    private final CurrentUserService currentUserService;

    public ParentDashboardService(ParentQuestRepository parentQuestRepository,
                                  ParentRewardRepository parentRewardRepository,
                                  StudentRepository studentRepository,
                                  AnnouncementRepository announcementRepository,
                                  StudentMetricRepository studentMetricRepository,
                                  CurrentUserService currentUserService) {
        this.parentQuestRepository = parentQuestRepository;
        this.parentRewardRepository = parentRewardRepository;
        this.studentRepository = studentRepository;
        this.announcementRepository = announcementRepository;
        this.studentMetricRepository = studentMetricRepository;
        this.currentUserService = currentUserService;
    }

    public record ParentView(String firstName, String lastName) {}

    public record StudentView(UUID id, String firstName, String lastName, String className) {}

    public record MetricView(Integer schoolXp, Integer parentXp, Integer activeStreak) {}

    public record QuestView(UUID id, String studentFirstName, String taskDescription, Integer xpBounty) {}

    public record RewardView(UUID id, String studentFirstName, String rewardTitle) {}

    public record AnnouncementView(String title, String content, String targetGrade, LocalDateTime createdAt) {}

    /** Everything the parent_dashboard template needs, all flat. */
    public record ParentDashboardView(ParentView parent,
                                      List<QuestView> awaitingQuests,
                                      List<RewardView> awaitingRewards,
                                      List<AnnouncementView> announcements,
                                      List<StudentView> students,
                                      Map<String, MetricView> studentMetrics,
                                      Map<String, Long> pendingQuestCounts) {}

    /** Empty when the caller has no linked parent record. */
    public Optional<ParentDashboardView> dashboard(Authentication authentication) {
        Parent parent = currentUserService.getCurrentParent(authentication).orElse(null);
        if (parent == null) {
            return Optional.empty();
        }

        List<ParentQuest> awaitingQuests =
                parentQuestRepository.findByParentIdAndStatus(parent.getId(), "COMPLETED_AWAITING_APPROVAL");
        List<ParentReward> awaitingRewards =
                parentRewardRepository.findByParentIdAndStatus(parent.getId(), "CLAIMED_AWAITING_DELIVERY");

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
                tenantId, academicYearId, targetGrades);
        announcements.sort((a, b) -> b.getCreatedAt().compareTo(a.getCreatedAt()));

        Map<String, MetricView> studentMetrics = new LinkedHashMap<>();
        Map<String, Long> pendingQuestCounts = new LinkedHashMap<>();
        for (Student s : students) {
            String studentIdStr = s.getId().toString();
            studentMetricRepository.findByStudentId(s.getId())
                    .ifPresent(m -> studentMetrics.put(studentIdStr, toMetricView(m)));
            long pendingCount = awaitingQuests.stream()
                    .filter(q -> q.getStudent() != null && s.getId().equals(q.getStudent().getId()))
                    .count();
            pendingQuestCounts.put(studentIdStr, pendingCount);
        }

        ParentDashboardView view = new ParentDashboardView(
                new ParentView(parent.getFirstName(), parent.getLastName()),
                awaitingQuests.stream().map(this::toQuestView).toList(),
                awaitingRewards.stream().map(this::toRewardView).toList(),
                announcements.stream().map(this::toAnnouncementView).toList(),
                students.stream().map(ParentDashboardService::toStudentView).toList(),
                studentMetrics,
                pendingQuestCounts);
        return Optional.of(view);
    }

    private static StudentView toStudentView(Student s) {
        String className = (s.getClassSection() != null)
                ? s.getClassSection().getGradeName() + " — " + s.getClassSection().getSectionName()
                : "Class N/A";
        return new StudentView(s.getId(), s.getFirstName(), s.getLastName(), className);
    }

    private MetricView toMetricView(StudentMetric m) {
        return new MetricView(m.getSchoolXp(), m.getParentXp(), m.getActiveStreak());
    }

    private QuestView toQuestView(ParentQuest q) {
        String studentFirstName = q.getStudent() != null ? q.getStudent().getFirstName() : "";
        return new QuestView(q.getId(), studentFirstName, q.getTaskDescription(), q.getXpBounty());
    }

    private RewardView toRewardView(ParentReward r) {
        String studentFirstName = r.getStudent() != null ? r.getStudent().getFirstName() : "";
        return new RewardView(r.getId(), studentFirstName, r.getRewardTitle());
    }

    private AnnouncementView toAnnouncementView(Announcement a) {
        return new AnnouncementView(a.getTitle(), a.getContent(), a.getTargetGrade(), a.getCreatedAt());
    }
}

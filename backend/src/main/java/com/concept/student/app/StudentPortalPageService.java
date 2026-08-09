package com.concept.student.app;

import com.concept.management.AcademicSubmission;
import com.concept.management.ParentQuest;
import com.concept.management.ParentReward;
import com.concept.management.RewardItem;
import com.concept.management.Student;
import com.concept.management.StudentPortalService;
import com.concept.management.StudentRewardService;
import com.concept.user.CurrentUserService;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

/**
 * Application layer for the student portal page (ADR 0001). Resolves the
 * authenticated student, delegates the read/write work to the existing
 * management services, and flattens the entity-backed dashboard into view
 * records so the Thymeleaf template renders off flat data and no persistence
 * type reaches the interface layer.
 */
@Service
public class StudentPortalPageService {

    private final StudentPortalService studentPortalService;
    private final StudentRewardService studentRewardService;
    private final CurrentUserService currentUserService;

    public StudentPortalPageService(StudentPortalService studentPortalService,
                                    StudentRewardService studentRewardService,
                                    CurrentUserService currentUserService) {
        this.studentPortalService = studentPortalService;
        this.studentRewardService = studentRewardService;
        this.currentUserService = currentUserService;
    }

    public enum RedeemResult { SUCCESS, INSUFFICIENT_XP, NO_LINKED_PARENT }

    public record StudentView(String firstName, String lastName, String className) {}

    public record MetricView(Integer schoolXp, Integer parentXp, Integer activeStreak) {}

    public record SubmissionView(String skillName, java.time.LocalDateTime submittedAt, String status,
                                 String proofOfWorkNotes, String rejectionReason, Integer xpBounty) {}

    public record SkillView(String skillName, Integer maxXpReward) {}

    public record RewardItemView(UUID id, String title, String description, String displayEmoji, int xpCost) {}

    public record ParentQuestView(UUID id, String status, String taskDescription, Integer xpBounty) {}

    public record ParentRewardView(UUID id, String rewardTitle, Integer xpCost) {}

    /** Everything the student_portal template needs, all flat. */
    public record StudentPortalView(StudentView student,
                                    MetricView studentMetrics,
                                    int totalXp,
                                    int scholarLevel,
                                    int levelProgress,
                                    int xpToNextLevel,
                                    List<SubmissionView> submissions,
                                    List<SkillView> availableSkills,
                                    List<RewardItemView> rewardInventory,
                                    List<ParentRewardView> pendingRewards,
                                    List<String> pendingRewardTitles,
                                    List<ParentQuestView> parentQuests,
                                    List<ParentRewardView> availableParentRewards) {}

    public StudentPortalView dashboard(Authentication authentication) {
        Student student = requireStudent(authentication);
        StudentPortalService.DashboardView v = studentPortalService.buildDashboard(student);

        var m = v.metrics();
        return new StudentPortalView(
                toStudentView(v.student()),
                (m != null) ? new MetricView(m.getSchoolXp(), m.getParentXp(), m.getActiveStreak())
                            : new MetricView(0, 0, 0),
                v.totalXp(), v.scholarLevel(), v.levelProgress(), v.xpToNextLevel(),
                v.submissions().stream().map(StudentPortalPageService::toSubmissionView).toList(),
                v.availableSkills().stream().map(StudentPortalPageService::toSkillView).toList(),
                v.rewardInventory().stream().map(StudentPortalPageService::toRewardItemView).toList(),
                v.pendingRewards().stream().map(StudentPortalPageService::toParentRewardView).toList(),
                v.pendingRewardTitles(),
                v.parentQuests().stream().map(StudentPortalPageService::toParentQuestView).toList(),
                v.availableParentRewards().stream().map(StudentPortalPageService::toParentRewardView).toList());
    }

    public void submitMilestone(String skillName, String proofOfWorkNotes,
                                String answer1, String answer2, String answer3,
                                UUID teacherTaskId, Authentication authentication) {
        Student student = requireStudent(authentication);
        UUID tenantId = currentUserService.getCurrentTenantId(authentication).orElse(null);
        studentPortalService.submitMilestone(skillName, proofOfWorkNotes, answer1, answer2, answer3,
                teacherTaskId, student, tenantId);
    }

    public RedeemResult redeemReward(UUID rewardId, Authentication authentication) {
        return map(studentRewardService.redeemReward(rewardId, requireStudent(authentication)));
    }

    public void claimQuest(UUID questId, Authentication authentication) {
        studentRewardService.claimQuest(questId, requireStudent(authentication));
    }

    public RedeemResult redeemParentReward(UUID rewardId, Authentication authentication) {
        return map(studentRewardService.redeemParentReward(rewardId, requireStudent(authentication)));
    }

    private Student requireStudent(Authentication authentication) {
        return currentUserService.getCurrentStudent(authentication)
                .orElseThrow(() -> new IllegalArgumentException("Student record not found"));
    }

    private static RedeemResult map(StudentRewardService.RedeemOutcome outcome) {
        return switch (outcome) {
            case INSUFFICIENT_XP -> RedeemResult.INSUFFICIENT_XP;
            case NO_LINKED_PARENT -> RedeemResult.NO_LINKED_PARENT;
            case REDEEMED -> RedeemResult.SUCCESS;
        };
    }

    private static StudentView toStudentView(Student s) {
        String className = (s.getClassSection() != null)
                ? s.getClassSection().getGradeName() + " - " + s.getClassSection().getSectionName()
                : "N/A";
        return new StudentView(s.getFirstName(), s.getLastName(), className);
    }

    private static SubmissionView toSubmissionView(AcademicSubmission a) {
        return new SubmissionView(a.getSkillName(), a.getSubmittedAt(), a.getStatus(),
                a.getProofOfWorkNotes(), a.getRejectionReason(), a.getXpBounty());
    }

    private static SkillView toSkillView(com.concept.academics.MathSkill s) {
        return new SkillView(s.getSkillName(), s.getMaxXpReward());
    }

    private static RewardItemView toRewardItemView(RewardItem r) {
        return new RewardItemView(r.getId(), r.getTitle(), r.getDescription(), r.getDisplayEmoji(), r.getXpCost());
    }

    private static ParentQuestView toParentQuestView(ParentQuest q) {
        return new ParentQuestView(q.getId(), q.getStatus(), q.getTaskDescription(), q.getXpBounty());
    }

    private static ParentRewardView toParentRewardView(ParentReward r) {
        return new ParentRewardView(r.getId(), r.getRewardTitle(), r.getXpCost());
    }
}

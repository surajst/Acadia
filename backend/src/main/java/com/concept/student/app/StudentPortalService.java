package com.concept.student.app;
import com.concept.student.data.AcademicSubmissionRepository;
import com.concept.student.data.AcademicSubmission;
import com.concept.parent.data.ParentRewardRepository;
import com.concept.parent.data.ParentReward;
import com.concept.parent.data.ParentQuestRepository;
import com.concept.parent.data.ParentQuest;
import com.concept.rewards.data.RewardItemRepository;
import com.concept.rewards.data.RewardItem;
import com.concept.tasks.data.TeacherTaskRepository;
import com.concept.tasks.data.TeacherTask;
import com.concept.shared.data.Student;

import com.concept.academics.MathSkill;
import com.concept.academics.MathSkillRepository;
import com.concept.academics.StudentMetric;
import com.concept.academics.StudentMetricRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Assembles the student portal dashboard view-model and handles milestone
 * submission. Extracted from StudentPortalController so the controller keeps
 * only request/response wiring and this read/write logic is testable on its own.
 */
@Service
public class StudentPortalService {

    private final StudentMetricRepository studentMetricRepository;
    private final AcademicSubmissionRepository academicSubmissionRepository;
    private final MathSkillRepository mathSkillRepository;
    private final RewardItemRepository rewardItemRepository;
    private final ParentRewardRepository parentRewardRepository;
    private final ParentQuestRepository parentQuestRepository;
    private final TeacherTaskRepository teacherTaskRepository;

    public StudentPortalService(StudentMetricRepository studentMetricRepository,
                                AcademicSubmissionRepository academicSubmissionRepository,
                                MathSkillRepository mathSkillRepository,
                                RewardItemRepository rewardItemRepository,
                                ParentRewardRepository parentRewardRepository,
                                ParentQuestRepository parentQuestRepository,
                                TeacherTaskRepository teacherTaskRepository) {
        this.studentMetricRepository = studentMetricRepository;
        this.academicSubmissionRepository = academicSubmissionRepository;
        this.mathSkillRepository = mathSkillRepository;
        this.rewardItemRepository = rewardItemRepository;
        this.parentRewardRepository = parentRewardRepository;
        this.parentQuestRepository = parentQuestRepository;
        this.teacherTaskRepository = teacherTaskRepository;
    }

    /** Everything the student_portal template needs, computed in one place. */
    public record DashboardView(Student student,
                                StudentMetric metrics,
                                int totalXp,
                                int scholarLevel,
                                int levelProgress,
                                int xpToNextLevel,
                                List<AcademicSubmission> submissions,
                                List<MathSkill> availableSkills,
                                List<RewardItem> rewardInventory,
                                List<ParentReward> pendingRewards,
                                List<String> pendingRewardTitles,
                                List<ParentQuest> parentQuests,
                                List<ParentReward> availableParentRewards) {}

    /** Build the dashboard view-model for a student, creating a zeroed metric row if none exists. */
    public DashboardView buildDashboard(Student student) {
        UUID studentId = student.getId();

        StudentMetric metrics = studentMetricRepository.findByStudentId(studentId).orElse(null);
        if (metrics == null) {
            metrics = new StudentMetric();
            metrics.setId(UUID.randomUUID());
            metrics.setStudent(student);
            metrics.setTenantId(student.getTenantId());
            metrics.setAcademicYearId(student.getAcademicYearId());
            metrics.setSchoolXp(0);
            metrics.setParentXp(0);
            metrics.setActiveStreak(0);
            studentMetricRepository.saveAndFlush(metrics);
        }

        int totalXp = metrics.getSchoolXp() != null ? metrics.getSchoolXp() : 0;
        int scholarLevel = (totalXp / 500) + 1;
        int levelProgress = (totalXp % 500) * 100 / 500;
        int xpToNextLevel = 500 - (totalXp % 500);

        List<AcademicSubmission> submissions = academicSubmissionRepository.findByStudentId(studentId);
        if (submissions == null) submissions = List.of();

        List<MathSkill> availableSkills = mathSkillRepository.findAll();
        if (availableSkills == null || availableSkills.isEmpty()) {
            MathSkill skill1 = new MathSkill();
            skill1.setId(UUID.randomUUID());
            skill1.setSkillName("6th Grade Fraction Mastery");
            skill1.setMaxXpReward(250);

            MathSkill skill2 = new MathSkill();
            skill2.setId(UUID.randomUUID());
            skill2.setSkillName("Basic Fractions");
            skill2.setMaxXpReward(250);

            availableSkills = List.of(skill1, skill2);
        }

        List<RewardItem> rewardInventory = rewardItemRepository.findAll();
        if (rewardInventory == null) rewardInventory = List.of();

        List<ParentReward> pendingRewards = parentRewardRepository.findByStudentIdAndStatus(studentId, "PENDING");
        if (pendingRewards == null) pendingRewards = List.of();
        List<String> pendingRewardTitles = new ArrayList<>();
        for (ParentReward pr : pendingRewards) {
            if (pr.getRewardTitle() != null) pendingRewardTitles.add(pr.getRewardTitle());
        }

        List<ParentQuest> parentQuests = parentQuestRepository.findByStudentId(studentId);
        if (parentQuests == null) parentQuests = List.of();

        List<ParentReward> availableParentRewards = parentRewardRepository.findByStudentIdAndStatus(studentId, "AVAILABLE");
        if (availableParentRewards == null) availableParentRewards = List.of();

        return new DashboardView(student, metrics, totalXp, scholarLevel, levelProgress, xpToNextLevel,
                submissions, availableSkills, rewardInventory, pendingRewards, pendingRewardTitles,
                parentQuests, availableParentRewards);
    }

    /**
     * Record an academic milestone/task submission for a student as PENDING review.
     * The XP bounty is resolved from the explicit teacher task, else a same-tenant
     * task whose title matches the skill name, else a matching MathSkill, else 250.
     */
    public void submitMilestone(String skillName, String proofOfWorkNotes,
                                String answer1, String answer2, String answer3,
                                UUID teacherTaskId, Student student, UUID tenantId) {
        int bounty = 250;
        if (teacherTaskId != null) {
            TeacherTask task = teacherTaskRepository.findById(teacherTaskId).orElse(null);
            if (task != null && task.getXpReward() != null) {
                bounty = task.getXpReward();
            }
        } else {
            // Fallback: match the skill name against a same-tenant teacher task title.
            List<TeacherTask> tenantTasks = tenantId != null ? teacherTaskRepository.findByTenantId(tenantId) : List.of();
            for (TeacherTask task : tenantTasks) {
                if (task.getTitle().equalsIgnoreCase(skillName)) {
                    bounty = task.getXpReward() != null ? task.getXpReward() : 50;
                    teacherTaskId = task.getId();
                    break;
                }
            }
            // Only fall back to a MathSkill bounty if nothing matched above.
            if (bounty == 250) {
                List<MathSkill> skills = mathSkillRepository.findAll();
                if (skills != null) {
                    for (MathSkill s : skills) {
                        if (s.getSkillName().equalsIgnoreCase(skillName)) {
                            bounty = s.getMaxXpReward() != null ? s.getMaxXpReward() : 250;
                            break;
                        }
                    }
                }
            }
        }

        AcademicSubmission submission = new AcademicSubmission();
        submission.setId(UUID.randomUUID());
        submission.setStudentId(student.getId());
        submission.setSkillName(skillName);
        submission.setXpBounty(bounty);
        submission.setStatus("PENDING");
        submission.setProofOfWorkNotes(proofOfWorkNotes);
        submission.setAnswer1(answer1);
        submission.setAnswer2(answer2);
        submission.setAnswer3(answer3);
        submission.setTeacherTaskId(teacherTaskId);
        submission.setSubmittedAt(LocalDateTime.now());
        academicSubmissionRepository.saveAndFlush(submission);
    }
}

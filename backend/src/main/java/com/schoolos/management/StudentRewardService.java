package com.schoolos.management;

import com.schoolos.academics.StudentMetric;
import com.schoolos.academics.StudentMetricRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Owns the student-facing XP mutations — claiming a parent quest, redeeming a
 * school-XP reward, and redeeming a parent reward — including the ownership
 * checks that guard them and the XP-balance arithmetic. Pulled out of the
 * 795-line StudentPortalController so this security-sensitive logic has a
 * single home and can be unit-tested without driving the web layer.
 */
@Service
public class StudentRewardService {

    private final RewardItemRepository rewardItemRepository;
    private final StudentMetricRepository studentMetricRepository;
    private final ParentRepository parentRepository;
    private final ParentRewardRepository parentRewardRepository;
    private final ParentQuestRepository parentQuestRepository;
    private final StudentRepository studentRepository;

    public StudentRewardService(RewardItemRepository rewardItemRepository,
                                StudentMetricRepository studentMetricRepository,
                                ParentRepository parentRepository,
                                ParentRewardRepository parentRewardRepository,
                                ParentQuestRepository parentQuestRepository,
                                StudentRepository studentRepository) {
        this.rewardItemRepository = rewardItemRepository;
        this.studentMetricRepository = studentMetricRepository;
        this.parentRepository = parentRepository;
        this.parentRewardRepository = parentRewardRepository;
        this.parentQuestRepository = parentQuestRepository;
        this.studentRepository = studentRepository;
    }

    /** Soft result of a redeem attempt — a shortfall is a normal user outcome, not an error. */
    public enum RedeemOutcome { REDEEMED, INSUFFICIENT_XP }

    /**
     * Mark a parent quest as completed-awaiting-approval. The caller must own the
     * quest (ownership check, IDOR guard).
     */
    @Transactional
    public void claimQuest(UUID questId, Student caller) {
        ParentQuest quest = parentQuestRepository.findById(questId)
                .orElseThrow(() -> new IllegalArgumentException("Invalid parent quest ID: " + questId));

        if (caller == null || quest.getStudent() == null || !quest.getStudent().getId().equals(caller.getId())) {
            throw new IllegalArgumentException("Not authorized for this quest");
        }

        quest.setStatus("COMPLETED_AWAITING_APPROVAL");
        parentQuestRepository.saveAndFlush(quest);
    }

    /**
     * Redeem a catalogue reward against the student's school XP: verify balance,
     * deduct the cost, and queue a PENDING {@link ParentReward} for the parent to
     * fulfil. Returns {@link RedeemOutcome#INSUFFICIENT_XP} without mutating when
     * the student can't afford it.
     */
    @Transactional
    public RedeemOutcome redeemReward(UUID rewardId, Student student) {
        RewardItem reward = rewardItemRepository.findById(rewardId)
                .orElseThrow(() -> new IllegalArgumentException("Invalid reward item ID: " + rewardId));

        UUID studentId = student.getId();

        StudentMetric metric = studentMetricRepository.findByStudentId(studentId).orElse(null);
        if (metric == null) {
            metric = new StudentMetric();
            metric.setId(UUID.randomUUID());
            metric.setStudent(student);
            metric.setTenantId(student.getTenantId());
            metric.setAcademicYearId(student.getAcademicYearId());
            metric.setSchoolXp(0);
            metric.setParentXp(0);
            metric.setActiveStreak(0);
            studentMetricRepository.saveAndFlush(metric);
        }

        int currentXp = metric.getSchoolXp() != null ? metric.getSchoolXp() : 0;
        int cost = reward.getXpCost();
        if (currentXp < cost) {
            return RedeemOutcome.INSUFFICIENT_XP;
        }

        metric.setSchoolXp(Math.max(0, currentXp - cost));
        studentMetricRepository.saveAndFlush(metric);

        // Resolve the parent to route the pending reward to: the student's own
        // parent if linked, otherwise a shared fallback parent record.
        Parent parent = student.getParents().isEmpty() ? null : student.getParents().iterator().next();
        if (parent == null) {
            parent = parentRepository.findById(UUID.fromString("99999999-9999-9999-9999-999999999991")).orElse(null);
        }
        if (parent == null) {
            parent = new Parent();
            parent.setId(UUID.fromString("99999999-9999-9999-9999-999999999991"));
            UUID tenantId = student.getTenantId();
            UUID academicYearId = student.getAcademicYearId();
            parent.setTenantId(tenantId);
            parent.setAcademicYearId(academicYearId);
            parent.setFirstName("Ramesh");
            parent.setLastName("Sharma");
            parent.setPhoneNumber("+91 99887 76655");
            parent.setEmail("ramesh.sharma@example.com");
            parentRepository.saveAndFlush(parent);
            student.getParents().add(parent);
            studentRepository.saveAndFlush(student);
        }

        ParentReward pendingReward = new ParentReward();
        pendingReward.setId(UUID.randomUUID());
        pendingReward.setTenantId(student.getTenantId());
        pendingReward.setAcademicYearId(student.getAcademicYearId());
        pendingReward.setParent(parent);
        pendingReward.setStudent(student);
        pendingReward.setRewardTitle(reward.getTitle());
        pendingReward.setXpCost(reward.getXpCost());
        pendingReward.setStatus("PENDING");
        parentRewardRepository.saveAndFlush(pendingReward);

        return RedeemOutcome.REDEEMED;
    }

    /**
     * Redeem a parent-issued reward: verify the student owns it (IDOR guard),
     * check the combined parent+school XP balance, deduct the cost (parent XP
     * first, then school XP), and mark the reward claimed-awaiting-delivery.
     */
    @Transactional
    public RedeemOutcome redeemParentReward(UUID rewardId, Student student) {
        ParentReward reward = parentRewardRepository.findById(rewardId)
                .orElseThrow(() -> new IllegalArgumentException("Invalid parent reward ID: " + rewardId));

        if (student == null) {
            throw new IllegalArgumentException("No student found");
        }
        if (reward.getStudent() == null || !reward.getStudent().getId().equals(student.getId())) {
            throw new IllegalArgumentException("Not authorized for this reward");
        }

        StudentMetric metric = studentMetricRepository.findByStudentId(student.getId())
                .orElseThrow(() -> new IllegalArgumentException("No student metrics found"));

        int schoolXp = metric.getSchoolXp() != null ? metric.getSchoolXp() : 0;
        int parentXp = metric.getParentXp() != null ? metric.getParentXp() : 0;
        int cost = reward.getXpCost();

        if (schoolXp + parentXp < cost) {
            return RedeemOutcome.INSUFFICIENT_XP;
        }

        // Deduct cost: parent XP first, then school XP.
        if (parentXp >= cost) {
            metric.setParentXp(parentXp - cost);
        } else {
            int remaining = cost - parentXp;
            metric.setParentXp(0);
            metric.setSchoolXp(Math.max(0, schoolXp - remaining));
        }
        studentMetricRepository.saveAndFlush(metric);

        reward.setStatus("CLAIMED_AWAITING_DELIVERY");
        parentRewardRepository.saveAndFlush(reward);

        return RedeemOutcome.REDEEMED;
    }
}

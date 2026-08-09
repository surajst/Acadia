package com.concept.management;
import com.concept.parent.data.ParentRewardRepository;
import com.concept.parent.data.ParentReward;
import com.concept.parent.data.ParentQuestRepository;
import com.concept.parent.data.ParentQuest;
import com.concept.rewards.data.RewardItemRepository;
import com.concept.rewards.data.RewardItem;
import com.concept.shared.data.Parent;
import com.concept.shared.data.Student;

import com.concept.academics.StudentMetric;
import com.concept.academics.StudentMetricRepository;
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
    private final ParentRewardRepository parentRewardRepository;
    private final ParentQuestRepository parentQuestRepository;

    public StudentRewardService(RewardItemRepository rewardItemRepository,
                                StudentMetricRepository studentMetricRepository,
                                ParentRewardRepository parentRewardRepository,
                                ParentQuestRepository parentQuestRepository) {
        this.rewardItemRepository = rewardItemRepository;
        this.studentMetricRepository = studentMetricRepository;
        this.parentRewardRepository = parentRewardRepository;
        this.parentQuestRepository = parentQuestRepository;
    }

    /**
     * Soft result of a redeem attempt — these are normal user outcomes, not errors:
     * a shortfall ({@code INSUFFICIENT_XP}) or a school-XP reward that can't be routed
     * because the student has no parent linked ({@code NO_LINKED_PARENT}).
     */
    public enum RedeemOutcome { REDEEMED, INSUFFICIENT_XP, NO_LINKED_PARENT }

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
     * the student can't afford it, or {@link RedeemOutcome#NO_LINKED_PARENT} when
     * the student has no parent to route the pending reward to.
     */
    @Transactional
    public RedeemOutcome redeemReward(UUID rewardId, Student student) {
        RewardItem reward = rewardItemRepository.findById(rewardId)
                .orElseThrow(() -> new IllegalArgumentException("Invalid reward item ID: " + rewardId));

        // A school-XP reward becomes a pending ParentReward that a linked parent
        // fulfils. With no parent linked there is nobody to route it to — reject
        // before touching XP rather than inventing a shared placeholder parent.
        if (student.getParents().isEmpty()) {
            return RedeemOutcome.NO_LINKED_PARENT;
        }

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

        // The student's own linked parent receives the pending reward. The
        // no-parent case was already rejected above.
        Parent parent = student.getParents().iterator().next();

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

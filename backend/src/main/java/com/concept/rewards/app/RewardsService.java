package com.concept.rewards.app;

import com.concept.common.AuditLogService;
import com.concept.rewards.data.RewardItem;
import com.concept.rewards.data.RewardCatalogRepository;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Application layer for the admin rewards catalog: list the tenant's reward
 * inventory, and create, amend or withdraw a reward. The web/console layers get
 * flat {@link RewardView} records — no entities cross the boundary (ADR 0001).
 *
 * <p>The cost bounds are enforced here rather than in the form. The create page
 * carried {@code min="1"} on the input, which the browser honours and an HTTP
 * client ignores; a reward saved at −50 XP was then redeemable, and because
 * redemption subtracts the cost it <em>granted</em> 50 XP to anyone who took
 * it, repeatedly. Until this class had a delete, there was also no way to
 * withdraw one.
 */
@Service
public class RewardsService {

    /** A reward has to cost something, or redeeming it is a way to print XP. */
    private static final int MIN_XP_COST = 1;

    /**
     * Above this, a typo (50000 for 500) reads as a catalogue error rather than
     * a prize, and no child can reach it anyway.
     */
    private static final int MAX_XP_COST = 100_000;

    private final RewardCatalogRepository rewardItemRepository;
    private final AuditLogService auditLogService;

    public RewardsService(RewardCatalogRepository rewardItemRepository,
                          AuditLogService auditLogService) {
        this.rewardItemRepository = rewardItemRepository;
        this.auditLogService = auditLogService;
    }

    @Transactional(readOnly = true)
    public List<RewardView> listInventory(UUID tenantId) {
        if (tenantId == null) {
            return Collections.emptyList();
        }
        return rewardItemRepository.findByTenantId(tenantId).stream()
                .map(r -> new RewardView(r.getId(), r.getDisplayEmoji(), r.getTitle(), r.getDescription(),
                        r.getXpCost(), r.getInventoryCount()))
                .collect(Collectors.toList());
    }

    @Transactional
    public UUID createReward(String title, String description, int xpCost, String displayEmoji,
                             int inventoryCount, UUID tenantId, UUID academicYearId,
                             Authentication authentication) {
        validate(title, xpCost, inventoryCount);

        RewardItem reward = new RewardItem(UUID.randomUUID(), title.trim(), description, xpCost,
                displayEmoji, inventoryCount);
        reward.setTenantId(tenantId);
        reward.setAcademicYearId(academicYearId);
        rewardItemRepository.save(reward);

        auditLogService.log(authentication, "REWARD_CREATED", "RewardItem", reward.getId(),
                "Added reward \"" + reward.getTitle() + "\" at " + xpCost + " XP, stock " + inventoryCount);
        return reward.getId();
    }

    /**
     * Amend a reward in place. Tenant-scoped, so an id from another school is a
     * miss rather than an edit.
     */
    @Transactional
    public void updateReward(UUID rewardId, String title, String description, int xpCost,
                             String displayEmoji, int inventoryCount, UUID tenantId,
                             Authentication authentication) {
        validate(title, xpCost, inventoryCount);

        RewardItem reward = rewardItemRepository.findByIdAndTenantId(rewardId, tenantId)
                .orElseThrow(() -> new IllegalArgumentException("Reward not found."));

        String before = reward.getTitle() + " at " + reward.getXpCost() + " XP";
        reward.setTitle(title.trim());
        reward.setDescription(description);
        reward.setXpCost(xpCost);
        reward.setDisplayEmoji(displayEmoji);
        reward.setInventoryCount(inventoryCount);
        rewardItemRepository.save(reward);

        auditLogService.log(authentication, "REWARD_UPDATED", "RewardItem", reward.getId(),
                "Changed reward from " + before + " to " + reward.getTitle() + " at " + xpCost
                        + " XP, stock " + inventoryCount);
    }

    /**
     * Withdraw a reward from the marketplace.
     *
     * <p>Rewards already redeemed are untouched: a ParentReward copies the
     * title at redemption rather than pointing back here, so a child does not
     * lose a prize they have earned because the catalogue entry was tidied up.
     */
    @Transactional
    public void deleteReward(UUID rewardId, UUID tenantId, Authentication authentication) {
        RewardItem reward = rewardItemRepository.findByIdAndTenantId(rewardId, tenantId)
                .orElseThrow(() -> new IllegalArgumentException("Reward not found."));

        String summary = reward.getTitle() + " (" + reward.getXpCost() + " XP)";
        rewardItemRepository.delete(reward);

        auditLogService.log(authentication, "REWARD_DELETED", "RewardItem", rewardId,
                "Withdrew reward " + summary);
    }

    private void validate(String title, int xpCost, int inventoryCount) {
        if (title == null || title.isBlank()) {
            throw new IllegalArgumentException("Give the reward a name.");
        }
        if (xpCost < MIN_XP_COST) {
            throw new IllegalArgumentException(
                    "A reward must cost at least " + MIN_XP_COST + " XP — at zero or below, redeeming it would add XP.");
        }
        if (xpCost > MAX_XP_COST) {
            throw new IllegalArgumentException("A reward cannot cost more than " + MAX_XP_COST + " XP.");
        }
        if (inventoryCount < 0) {
            throw new IllegalArgumentException("Stock cannot be negative.");
        }
    }
}

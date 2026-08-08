package com.concept.rewards.app;

import com.concept.management.RewardItem;
import com.concept.rewards.data.RewardCatalogRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Application layer for the admin rewards catalog: list the tenant's reward
 * inventory and create new rewards. The web/console layers get flat
 * {@link RewardView} records — no entities cross the boundary (ADR 0001).
 */
@Service
public class RewardsService {

    private final RewardCatalogRepository rewardItemRepository;

    public RewardsService(RewardCatalogRepository rewardItemRepository) {
        this.rewardItemRepository = rewardItemRepository;
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
    public void createReward(String title, String description, int xpCost, String displayEmoji,
                             int inventoryCount, UUID tenantId, UUID academicYearId) {
        RewardItem reward = new RewardItem(UUID.randomUUID(), title, description, xpCost, displayEmoji, inventoryCount);
        reward.setTenantId(tenantId);
        reward.setAcademicYearId(academicYearId);
        rewardItemRepository.save(reward);
    }
}

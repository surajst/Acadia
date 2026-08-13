package com.concept.rewards.data;

import org.springframework.data.jpa.repository.JpaRepository;
import com.concept.common.TenantScopedRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.UUID;

@Repository
public interface RewardItemRepository extends TenantScopedRepository<RewardItem, UUID> {
    List<RewardItem> findByTenantId(UUID tenantId);
}

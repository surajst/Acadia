package com.concept.rewards.data;

import com.concept.rewards.data.RewardItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

/** Reward inventory access for the admin rewards console (reads tenant-filtered). */
@Repository
public interface RewardCatalogRepository extends JpaRepository<RewardItem, UUID> {

    List<RewardItem> findByTenantId(UUID tenantId);
}

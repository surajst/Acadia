package com.concept.rewards.data;

import com.concept.common.TenantScopedRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

/**
 * Reward inventory access for the admin rewards console (reads tenant-filtered).
 *
 * <p>Extends {@link TenantScopedRepository} rather than JpaRepository so that
 * amending or withdrawing a reward has to name the tenant: these take an id
 * straight from a URL, and the inherited findById would happily return another
 * school's row.
 */
@Repository
public interface RewardCatalogRepository extends TenantScopedRepository<RewardItem, UUID> {

    List<RewardItem> findByTenantId(UUID tenantId);
}

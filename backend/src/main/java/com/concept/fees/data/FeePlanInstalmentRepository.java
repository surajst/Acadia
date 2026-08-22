package com.concept.fees.data;

import com.concept.common.TenantScopedRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface FeePlanInstalmentRepository extends TenantScopedRepository<FeePlanInstalment, UUID> {

    List<FeePlanInstalment> findByFeePlanIdAndTenantIdOrderBySequenceNumberAsc(UUID feePlanId, UUID tenantId);

    void deleteByFeePlanIdAndTenantId(UUID feePlanId, UUID tenantId);
}

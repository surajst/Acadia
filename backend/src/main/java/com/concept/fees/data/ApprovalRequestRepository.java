package com.concept.fees.data;

import com.concept.common.TenantScopedRepository;

import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface ApprovalRequestRepository extends TenantScopedRepository<ApprovalRequest, UUID> {

    // Tenant-scoped, like every listing here: a status is not an ownership key.
    List<ApprovalRequest> findByTenantIdAndStatusOrderByRequestedAtAsc(
            UUID tenantId, ApprovalRequest.Status status);
}

package com.concept.shared.data;

import com.concept.common.TenantScopedRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface PickupContactRepository extends TenantScopedRepository<PickupContact, UUID> {

    // Tenant-scoped even though studentId is already specific: an id arriving
    // in a request is not proof of ownership, and this list is a safety record.
    List<PickupContact> findByStudentIdAndTenantIdOrderByNameAsc(UUID studentId, UUID tenantId);
}

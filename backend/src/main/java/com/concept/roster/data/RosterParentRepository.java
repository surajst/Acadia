package com.concept.roster.data;

import com.concept.common.TenantScopedRepository;
import com.concept.management.Parent;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

/**
 * Tenant-scoped guardian access for admin student/parent management. Reads and
 * edits resolve the guardian via {@code findByIdAndTenantId}, so one school can
 * never touch another school's guardian record (see ADR 0001).
 */
@Repository
public interface RosterParentRepository extends TenantScopedRepository<Parent, UUID> {

    /**
     * Existing guardians on a phone within one tenant. List variant because some
     * tenants carry pre-dedup duplicates on a phone, which an Optional would
     * throw on.
     */
    List<Parent> findAllByTenantIdAndPhoneNumber(UUID tenantId, String phoneNumber);
}

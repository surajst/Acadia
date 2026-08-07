package com.concept.roster.data;

import com.concept.common.TenantScopedRepository;
import com.concept.management.ClassSection;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

/**
 * Tenant-scoped class-section access for admin class-structure management.
 * Edits and deletes resolve the section via {@code findByIdAndTenantId}, so one
 * school can never modify another school's class section.
 */
@Repository
public interface RosterClassSectionRepository extends TenantScopedRepository<ClassSection, UUID> {

    List<ClassSection> findByTenantId(UUID tenantId);
}

package com.concept.roster.data;

import com.concept.common.TenantScopedRepository;
import com.concept.shared.data.ClassSection;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Tenant-scoped class-section access for admin class-structure management.
 * Edits and deletes resolve the section via {@code findByIdAndTenantId}, so one
 * school can never modify another school's class section.
 */
@Repository
public interface RosterClassSectionRepository extends TenantScopedRepository<ClassSection, UUID> {

    List<ClassSection> findByTenantId(UUID tenantId);

    /** Resolve the section that mirrors a classroom's grade + section, within one tenant. */
    Optional<ClassSection> findByTenantIdAndGradeNameAndSectionName(UUID tenantId, String gradeName, String sectionName);
}

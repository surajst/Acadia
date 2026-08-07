package com.concept.roster.data;

import com.concept.common.TenantScopedRepository;
import com.concept.management.SchoolClass;
import org.springframework.stereotype.Repository;

import java.util.UUID;

/**
 * Tenant-scoped classroom (SchoolClass) access. Resolving a classroom during
 * student registration or a class move goes through {@code findByIdAndTenantId},
 * so a student can never be pointed at another school's classroom (ADR 0001).
 */
@Repository
public interface RosterSchoolClassRepository extends TenantScopedRepository<SchoolClass, UUID> {
}

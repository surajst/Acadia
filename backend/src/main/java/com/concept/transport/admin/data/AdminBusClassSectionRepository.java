package com.concept.transport.admin.data;

import com.concept.common.TenantScopedRepository;
import com.concept.shared.data.ClassSection;
import org.springframework.stereotype.Repository;

import java.util.UUID;

/**
 * Tenant-scoped class-section lookup for attaching a bus route to a section.
 * Scoped so a section from another school can never be touched (ADR 0001).
 */
@Repository
public interface AdminBusClassSectionRepository extends TenantScopedRepository<ClassSection, UUID> {
}

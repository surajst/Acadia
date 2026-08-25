package com.concept.console.data;

import com.concept.common.TenantScopedRepository;
import com.concept.shared.data.ClassSection;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

/** The classrooms the admin console lists, with their occupancy. */
@Repository
public interface ConsoleClassSectionRepository extends TenantScopedRepository<ClassSection, UUID> {

    List<ClassSection> findByTenantId(UUID tenantId);

    long countByTenantId(UUID tenantId);
}

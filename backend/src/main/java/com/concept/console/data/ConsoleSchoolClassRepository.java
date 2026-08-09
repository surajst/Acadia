package com.concept.console.data;

import com.concept.shared.data.SchoolClass;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

/** Classroom reads for the admin console hub (tenant-filtered). */
@Repository
public interface ConsoleSchoolClassRepository extends JpaRepository<SchoolClass, UUID> {

    List<SchoolClass> findByTenantId(UUID tenantId);

    long countByTenantId(UUID tenantId);
}

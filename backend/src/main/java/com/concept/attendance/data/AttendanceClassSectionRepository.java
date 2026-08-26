package com.concept.attendance.data;

import com.concept.common.TenantScopedRepository;
import com.concept.shared.data.ClassSection;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

/** The classes a teacher marks attendance for. */
@Repository
public interface AttendanceClassSectionRepository extends TenantScopedRepository<ClassSection, UUID> {

    List<ClassSection> findByTenantId(UUID tenantId);
}

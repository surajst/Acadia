package com.concept.dashboard.data;

import com.concept.shared.data.ClassSection;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

/** Class-section reads for the unified dashboard (tenant-scoped; never unscoped). */
@Repository
public interface DashboardClassSectionRepository extends JpaRepository<ClassSection, UUID> {

    List<ClassSection> findByTenantId(UUID tenantId);

    List<ClassSection> findByTeacherIdAndTenantId(UUID teacherId, UUID tenantId);
}

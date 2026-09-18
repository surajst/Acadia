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

    // There is deliberately no findByTeacherIdAndTenantId here. ClassSection
    // .teacherId is written by nothing outside the dev-mode seeders, so any
    // lookup through it silently returns nothing in production — which is how
    // the roster dashboard came to show every teacher the whole school.
    // SubjectAssignmentRepository.findByTeacher is the real teacher-to-class
    // link; use that.
}

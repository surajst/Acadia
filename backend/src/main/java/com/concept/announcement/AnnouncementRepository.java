package com.concept.announcement;

import org.springframework.data.jpa.repository.JpaRepository;
import com.concept.common.TenantScopedRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.UUID;

@Repository
public interface AnnouncementRepository extends TenantScopedRepository<Announcement, UUID> {
    List<Announcement> findByTenantIdAndAcademicYearIdAndTargetGradeIn(UUID tenantId, UUID academicYearId, List<String> targetGrades);
    List<Announcement> findByTenantId(UUID tenantId);
}

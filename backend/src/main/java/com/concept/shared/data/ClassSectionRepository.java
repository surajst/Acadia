package com.concept.shared.data;

import org.springframework.data.jpa.repository.JpaRepository;
import com.concept.common.TenantScopedRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ClassSectionRepository extends TenantScopedRepository<ClassSection, UUID> {
    List<ClassSection> findByTenantId(UUID tenantId);

    // No findByTeacherIdAndTenantId: ClassSection.teacherId is written only by
    // the dev-mode seeders, so in production it is always null and any query
    // through it quietly returns nothing. Both callers that used it shipped
    // bugs because of that. SubjectAssignmentRepository.findByTeacher is the
    // real teacher-to-class link.

    Optional<ClassSection> findByGradeNameAndSectionName(String gradeName, String sectionName);
    Optional<ClassSection> findByTenantIdAndGradeNameAndSectionName(UUID tenantId, String gradeName, String sectionName);
}

package com.schoolos.management;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ClassSectionRepository extends JpaRepository<ClassSection, UUID> {
    List<ClassSection> findByTenantId(UUID tenantId);
    List<ClassSection> findByTeacherIdAndTenantId(UUID teacherId, UUID tenantId);
    
    // 👈 Add this line back so UploadWebController stops complaining!
    Optional<ClassSection> findByGradeNameAndSectionName(String gradeName, String sectionName);
    Optional<ClassSection> findByTenantIdAndGradeNameAndSectionName(UUID tenantId, String gradeName, String sectionName);
}

package com.schoolos.management;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.UUID;

@Repository
public interface SchoolClassRepository extends JpaRepository<SchoolClass, UUID> {
    List<SchoolClass> findByGradeLevel(String gradeLevel);
    List<SchoolClass> findByTenantId(UUID tenantId);
    long countByTenantId(UUID tenantId);
}

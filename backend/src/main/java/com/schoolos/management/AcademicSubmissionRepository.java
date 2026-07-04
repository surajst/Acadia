package com.schoolos.management;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.util.UUID;

public interface AcademicSubmissionRepository extends JpaRepository<AcademicSubmission, UUID> {
    // Finds all tasks waiting for teacher validation
    List<AcademicSubmission> findByStatus(String status);

    List<AcademicSubmission> findByStudentId(UUID studentId);

    // AcademicSubmission has no direct tenantId column — scope via the
    // referenced student's own tenant instead.
    @Query("SELECT a FROM AcademicSubmission a WHERE a.status = :status " +
           "AND a.studentId IN (SELECT s.id FROM Student s WHERE s.tenantId = :tenantId)")
    List<AcademicSubmission> findByStatusAndStudentTenantId(@Param("status") String status, @Param("tenantId") UUID tenantId);
}

package com.concept.shared.data;
import com.concept.shared.data.Student;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AcademicSubmissionRepository extends JpaRepository<AcademicSubmission, UUID> {
    // No findByStatus(status) here on purpose. It existed, it looked harmless,
    // and it returned every school's pending submissions to whichever teacher
    // called /api/academic/teacher/pending. A status is not an ownership key --
    // use findByStatusAndStudentTenantId below.

    List<AcademicSubmission> findByStudentId(UUID studentId);

    // AcademicSubmission has no direct tenantId column — scope via the
    // referenced student's own tenant instead.
    @Query("SELECT a FROM AcademicSubmission a WHERE a.status = :status " +
           "AND a.studentId IN (SELECT s.id FROM Student s WHERE s.tenantId = :tenantId)")
    List<AcademicSubmission> findByStatusAndStudentTenantId(@Param("status") String status, @Param("tenantId") UUID tenantId);

    // Same reasoning as above, for a single row: loading by id alone returns a
    // submission from any school, so every id that arrives in a request has to
    // be resolved through the caller's own tenant.
    @Query("SELECT a FROM AcademicSubmission a WHERE a.id = :id " +
           "AND a.studentId IN (SELECT s.id FROM Student s WHERE s.tenantId = :tenantId)")
    Optional<AcademicSubmission> findByIdAndStudentTenantId(@Param("id") UUID id, @Param("tenantId") UUID tenantId);
}

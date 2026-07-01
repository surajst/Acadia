package com.schoolos.management;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

public interface AcademicSubmissionRepository extends JpaRepository<AcademicSubmission, UUID> {
    // Finds all tasks waiting for teacher validation
    List<AcademicSubmission> findByStatus(String status);

    List<AcademicSubmission> findByStudentId(UUID studentId);
}

package com.schoolos.academics;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.UUID;

// We explicitly name the bean here to avoid any hidden auto-naming collisions
@Repository("teacherVerificationRepository")
public interface TeacherVerificationRepository extends JpaRepository<AcademicSubmission, UUID> {
    
    List<AcademicSubmission> findByTenantIdAndStatusOrderBySubmittedAtDesc(UUID tenantId, String status);
}

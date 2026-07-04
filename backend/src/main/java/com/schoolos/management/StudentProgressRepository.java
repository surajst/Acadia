package com.schoolos.management;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.UUID;

@Repository
public interface StudentProgressRepository extends JpaRepository<StudentProgress, UUID> {
    List<StudentProgress> findByStudentId(UUID studentId);
    List<StudentProgress> findByStudentIdAndCompleted(UUID studentId, boolean completed);

    @Query("SELECT p FROM StudentProgress p WHERE p.student.tenantId = :tenantId AND p.status = :status")
    List<StudentProgress> findByStudentTenantIdAndStatus(@Param("tenantId") UUID tenantId, @Param("status") String status);
}

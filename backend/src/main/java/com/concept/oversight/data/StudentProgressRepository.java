package com.concept.oversight.data;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface StudentProgressRepository extends JpaRepository<StudentProgress, UUID> {
    List<StudentProgress> findByStudentId(UUID studentId);
    List<StudentProgress> findByStudentIdAndCompleted(UUID studentId, boolean completed);

    @Query("SELECT p FROM StudentProgress p WHERE p.student.tenantId = :tenantId AND p.status = :status")
    List<StudentProgress> findByStudentTenantIdAndStatus(@Param("tenantId") UUID tenantId, @Param("status") String status);

    // StudentProgress carries no tenant column of its own; the student it hangs
    // off does. A bare findById would happily return another school's row.
    @Query("SELECT p FROM StudentProgress p WHERE p.id = :id AND p.student.tenantId = :tenantId")
    Optional<StudentProgress> findByIdAndStudentTenantId(@Param("id") UUID id, @Param("tenantId") UUID tenantId);
}

package com.concept.tasks.data;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface TeacherTaskRepository extends JpaRepository<TeacherTask, UUID> {
    List<TeacherTask> findByCreatedByTeacherIdAndTenantId(UUID teacherId, UUID tenantId);
    List<TeacherTask> findByStandardAndAssignedToClassTrueAndTenantId(Integer standard, UUID tenantId);
    List<TeacherTask> findByStudentIdAndTenantId(UUID studentId, UUID tenantId);
    List<TeacherTask> findByTenantId(UUID tenantId);

    // TeacherTask keeps its tenant columns nullable (see the entity), so it
    // cannot extend BaseTenantEntity and the ArchUnit bare-findById rule does
    // not reach it. This is the scoped lookup callers must use regardless.
    Optional<TeacherTask> findByIdAndTenantId(UUID id, UUID tenantId);
}

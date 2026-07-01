package com.schoolos.management;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface TeacherTaskRepository extends JpaRepository<TeacherTask, UUID> {
    List<TeacherTask> findByCreatedByTeacherIdAndTenantId(UUID teacherId, UUID tenantId);
    List<TeacherTask> findByStandardAndAssignedToClassTrueAndTenantId(Integer standard, UUID tenantId);
    List<TeacherTask> findByStudentIdAndTenantId(UUID studentId, UUID tenantId);
}

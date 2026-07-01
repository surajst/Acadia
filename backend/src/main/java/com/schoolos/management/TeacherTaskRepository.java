package com.schoolos.management;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface TeacherTaskRepository extends JpaRepository<TeacherTask, UUID> {
    List<TeacherTask> findByCreatedByTeacherId(UUID teacherId);
    List<TeacherTask> findByStandardAndAssignedToClassTrue(Integer standard);
    List<TeacherTask> findByStudentId(UUID studentId);
}

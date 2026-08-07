package com.concept.attendance.data;

import com.concept.common.TenantScopedRepository;
import com.concept.management.Student;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

/**
 * Data layer for attendance. Extends {@link TenantScopedRepository} so marking
 * attendance can only resolve a student within the caller's tenant — a teacher
 * cannot write attendance onto another school's student by POSTing a foreign id.
 */
@Repository
public interface AttendanceStudentRepository extends TenantScopedRepository<Student, UUID> {

    List<Student> findByTenantId(UUID tenantId);

    List<Student> findBySchoolClassId(UUID schoolClassId);
}

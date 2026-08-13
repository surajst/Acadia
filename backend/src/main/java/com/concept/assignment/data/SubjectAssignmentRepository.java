package com.concept.assignment.data;
import com.concept.shared.data.ClassSection;

import com.concept.user.User;
import org.springframework.data.jpa.repository.JpaRepository;
import com.concept.common.TenantScopedRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface SubjectAssignmentRepository extends TenantScopedRepository<SubjectAssignment, UUID> {

    List<SubjectAssignment> findByTeacher(User teacher);

    List<SubjectAssignment> findByTeacherAndIsHomeClass(User teacher, boolean isHomeClass);

    List<SubjectAssignment> findByClassSection(ClassSection classSection);

    boolean existsByTeacherAndClassSection(User teacher, ClassSection classSection);
}

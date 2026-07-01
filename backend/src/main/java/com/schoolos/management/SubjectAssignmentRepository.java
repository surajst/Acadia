package com.schoolos.management;

import com.schoolos.user.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface SubjectAssignmentRepository extends JpaRepository<SubjectAssignment, UUID> {

    List<SubjectAssignment> findByTeacher(User teacher);

    List<SubjectAssignment> findByTeacherAndIsHomeClass(User teacher, boolean isHomeClass);

    List<SubjectAssignment> findByClassSection(ClassSection classSection);

    boolean existsByTeacherAndClassSection(User teacher, ClassSection classSection);
}

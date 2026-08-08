package com.concept.messaging.data;

import com.concept.management.ClassSection;
import com.concept.management.SubjectAssignment;
import com.concept.user.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

/**
 * Teaching-assignment access: gates who may start a conversation (a teacher must
 * be assigned to the student's class) and lists a parent's reachable teachers.
 */
@Repository
public interface MsgSubjectAssignmentRepository extends JpaRepository<SubjectAssignment, UUID> {

    boolean existsByTeacherAndClassSection(User teacher, ClassSection classSection);

    List<SubjectAssignment> findByTeacher(User teacher);

    List<SubjectAssignment> findByClassSection(ClassSection classSection);
}

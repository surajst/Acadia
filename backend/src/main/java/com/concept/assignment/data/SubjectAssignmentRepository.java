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

    /**
     * The real uniqueness key. A teacher takes a section for one subject at a
     * time, but nothing stops them taking it for two: the old check was on
     * (teacher, section) alone, so assigning Priya to 6-A for Mathematics made
     * 6-A Science impossible for her.
     */
    boolean existsByTeacherAndClassSectionAndSubjectName(User teacher, ClassSection classSection,
                                                        String subjectName);
}

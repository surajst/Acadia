package com.schoolos.management;

import com.schoolos.user.User;
import com.schoolos.user.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
public class SubjectAssignmentService {

    @Autowired
    private SubjectAssignmentRepository assignmentRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ClassSectionRepository classSectionRepository;

    /**
     * Creates a new subject assignment for a teacher on a class section.
     * Prevents exact duplicates (same teacher + same section).
     *
     * @param teacherId      UUID of the teacher (User)
     * @param classSectionId UUID of the ClassSection
     * @param subjectName    e.g. "Mathematics"
     * @param isHomeClass    true if this teacher is the class teacher for this section
     * @return the saved SubjectAssignment
     * @throws IllegalArgumentException if teacher or section not found
     * @throws IllegalStateException    if assignment already exists for this pair
     */
    public SubjectAssignment assignSubject(UUID teacherId,
                                           UUID classSectionId,
                                           String subjectName,
                                           boolean isHomeClass,
                                           UUID currentTenantId) {
        User teacher = userRepository.findById(teacherId)
                .orElseThrow(() -> new IllegalArgumentException("Teacher not found: " + teacherId));
        if (currentTenantId != null && !currentTenantId.equals(teacher.getTenantId())) {
            throw new IllegalArgumentException("Teacher not found: " + teacherId);
        }

        ClassSection section = classSectionRepository.findById(classSectionId)
                .orElseThrow(() -> new IllegalArgumentException("ClassSection not found: " + classSectionId));
        if (currentTenantId != null && !currentTenantId.equals(section.getTenantId())) {
            throw new IllegalArgumentException("ClassSection not found: " + classSectionId);
        }

        if (assignmentRepository.existsByTeacherAndClassSection(teacher, section)) {
            throw new IllegalStateException(
                    "Assignment already exists for teacher " + teacherId + " and section " + classSectionId);
        }

        SubjectAssignment assignment = new SubjectAssignment();
        assignment.setId(UUID.randomUUID());
        assignment.setTeacher(teacher);
        assignment.setClassSection(section);
        assignment.setSubjectName(subjectName);
        assignment.setHomeClass(isHomeClass);
        // Inherit tenant/academic-year from the teacher's own context
        assignment.setTenantId(teacher.getTenantId());
        assignment.setAcademicYearId(teacher.getAcademicYearId());

        return assignmentRepository.save(assignment);
    }

    /**
     * Removes a subject assignment by its UUID. Silently no-ops if not found.
     *
     * @param assignmentId UUID of the SubjectAssignment to remove
     */
    public void removeAssignment(UUID assignmentId, UUID currentTenantId) {
        SubjectAssignment assignment = assignmentRepository.findById(assignmentId).orElse(null);
        if (assignment == null) {
            return; // preserves prior silent no-op behavior for "not found"
        }
        if (currentTenantId != null && !currentTenantId.equals(assignment.getTenantId())) {
            // Same silent no-op as "not found" — don't reveal a cross-tenant ID exists.
            return;
        }
        assignmentRepository.deleteById(assignmentId);
    }

    /**
     * Returns all subject assignments for a given teacher.
     *
     * @param teacherId UUID of the teacher
     * @return list of assignments (may be empty)
     */
    public List<SubjectAssignment> getAssignmentsForTeacher(UUID teacherId, UUID currentTenantId) {
        User teacher = userRepository.findById(teacherId)
                .orElseThrow(() -> new IllegalArgumentException("Teacher not found: " + teacherId));
        if (currentTenantId != null && !currentTenantId.equals(teacher.getTenantId())) {
            throw new IllegalArgumentException("Teacher not found: " + teacherId);
        }
        return assignmentRepository.findByTeacher(teacher);
    }

    /**
     * Returns all subject assignments for a given class section.
     *
     * @param classSectionId UUID of the ClassSection
     * @return list of assignments (may be empty)
     */
    public List<SubjectAssignment> getAssignmentsForClass(UUID classSectionId, UUID currentTenantId) {
        ClassSection section = classSectionRepository.findById(classSectionId)
                .orElseThrow(() -> new IllegalArgumentException("ClassSection not found: " + classSectionId));
        if (currentTenantId != null && !currentTenantId.equals(section.getTenantId())) {
            throw new IllegalArgumentException("ClassSection not found: " + classSectionId);
        }
        return assignmentRepository.findByClassSection(section);
    }
}

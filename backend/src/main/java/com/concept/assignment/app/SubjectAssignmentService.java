package com.concept.assignment.app;
import com.concept.assignment.data.SubjectAssignmentRepository;
import com.concept.assignment.data.SubjectAssignment;
import com.concept.shared.data.ClassSectionRepository;
import com.concept.shared.data.ClassSection;

import com.concept.user.User;
import com.concept.user.UserRepository;
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
     * @throws IllegalStateException    if this teacher already has this section for
     *                                  this subject, or the section already has a
     *                                  different home class teacher
     */
    public SubjectAssignment assignSubject(UUID teacherId,
                                           UUID classSectionId,
                                           String subjectName,
                                           boolean isHomeClass,
                                           UUID currentTenantId) {
        User teacher = userRepository.findByIdAndTenantId(teacherId, currentTenantId)
                .orElseThrow(() -> new IllegalArgumentException("Teacher not found: " + teacherId));

        ClassSection section = classSectionRepository.findByIdAndTenantId(classSectionId, currentTenantId)
                .orElseThrow(() -> new IllegalArgumentException("ClassSection not found: " + classSectionId));

        String subject = subjectName == null ? "" : subjectName.trim();
        String sectionLabel = (section.getGradeName() + " " + section.getSectionName()).trim();

        // Keyed on the subject too. Without it, giving Priya 6-A Mathematics
        // made 6-A Science impossible for her -- a teacher who takes two
        // subjects for one class is ordinary, not a duplicate.
        if (assignmentRepository.existsByTeacherAndClassSectionAndSubjectName(teacher, section, subject)) {
            throw new IllegalStateException(
                    teacher.getFullName() + " is already assigned to " + sectionLabel + " for " + subject + ".");
        }

        // A section has one home class teacher. That is the part the old rule
        // was really protecting, and it was protecting it by accident.
        if (isHomeClass) {
            SubjectAssignment existingHome = assignmentRepository.findByClassSection(section).stream()
                    .filter(SubjectAssignment::isHomeClass)
                    .filter(a -> a.getTeacher() != null && !a.getTeacher().getId().equals(teacher.getId()))
                    .findFirst()
                    .orElse(null);
            if (existingHome != null) {
                throw new IllegalStateException(existingHome.getTeacher().getFullName()
                        + " is already the home class teacher for " + sectionLabel + ".");
            }
        }

        SubjectAssignment assignment = new SubjectAssignment();
        assignment.setId(UUID.randomUUID());
        assignment.setTeacher(teacher);
        assignment.setClassSection(section);
        assignment.setSubjectName(subject);
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
        SubjectAssignment assignment = assignmentRepository.findByIdAndTenantId(assignmentId, currentTenantId).orElse(null);
        if (assignment == null) {
            return; // preserves prior silent no-op behavior for "not found"
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
        User teacher = userRepository.findByIdAndTenantId(teacherId, currentTenantId)
                .orElseThrow(() -> new IllegalArgumentException("Teacher not found: " + teacherId));
        return assignmentRepository.findByTeacher(teacher);
    }

    /**
     * Returns all subject assignments for a given class section.
     *
     * @param classSectionId UUID of the ClassSection
     * @return list of assignments (may be empty)
     */
    public List<SubjectAssignment> getAssignmentsForClass(UUID classSectionId, UUID currentTenantId) {
        ClassSection section = classSectionRepository.findByIdAndTenantId(classSectionId, currentTenantId)
                .orElseThrow(() -> new IllegalArgumentException("ClassSection not found: " + classSectionId));
        return assignmentRepository.findByClassSection(section);
    }
}

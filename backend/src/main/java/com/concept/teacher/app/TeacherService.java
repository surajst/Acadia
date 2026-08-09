package com.concept.teacher.app;

import com.concept.management.AttendanceRepository;
import com.concept.management.ClassSection;
import com.concept.management.StudentRepository;
import com.concept.management.SubjectAssignment;
import com.concept.management.SubjectAssignmentRepository;
import com.concept.user.User;
import com.concept.user.UserRepository;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Application layer for the teacher class/attendance overview (ADR 0001).
 * Resolves the authenticated teacher and reads the shared management
 * repositories, returning flat maps so no JPA entity reaches the interface
 * layer. Serialized JSON is identical to the former god-controller.
 */
@Service
public class TeacherService {

    private final SubjectAssignmentRepository subjectAssignmentRepository;
    private final StudentRepository studentRepository;
    private final AttendanceRepository attendanceRepository;
    private final UserRepository userRepository;

    public TeacherService(SubjectAssignmentRepository subjectAssignmentRepository,
                          StudentRepository studentRepository,
                          AttendanceRepository attendanceRepository,
                          UserRepository userRepository) {
        this.subjectAssignmentRepository = subjectAssignmentRepository;
        this.studentRepository = studentRepository;
        this.attendanceRepository = attendanceRepository;
        this.userRepository = userRepository;
    }

    public Object teacherClasses(Authentication authentication) {
        try {
            User teacher = requireTeacher(authentication);
            List<SubjectAssignment> assignments = subjectAssignmentRepository.findByTeacher(teacher);

            if (assignments.isEmpty()) {
                Map<String, Object> errorResp = new LinkedHashMap<>();
                errorResp.put("error", "No classes assigned. Please contact admin.");
                errorResp.put("classes", Collections.emptyList());
                return errorResp;
            }

            return assignments.stream()
                    .map(assignment -> {
                        ClassSection section = assignment.getClassSection();
                        Map<String, Object> classData = new LinkedHashMap<>();
                        classData.put("id",           section.getId());
                        classData.put("className",    section.getGradeName() + " – " + section.getSectionName());
                        classData.put("subject",      assignment.getSubjectName());
                        classData.put("studentCount", studentRepository.countByClassSection(section));
                        classData.put("status",       "active");
                        classData.put("isHomeClass",  assignment.isHomeClass());
                        return classData;
                    })
                    .collect(Collectors.toList());
        } catch (Exception e) {
            throw TeacherException.serverError("Failed to load teacher classes");
        }
    }

    public Map<String, Object> attendanceSummary(Authentication authentication) {
        try {
            User teacher = requireTeacher(authentication);
            List<SubjectAssignment> assignments = subjectAssignmentRepository.findByTeacher(teacher);
            LocalDate today = LocalDate.now();

            // distinct() guards against a teacher teaching multiple subjects in the same section
            List<ClassSection> sections = assignments.stream()
                    .map(SubjectAssignment::getClassSection)
                    .distinct()
                    .collect(Collectors.toList());

            long markedCount = sections.stream()
                    .filter(section -> !attendanceRepository
                            .findByClassSectionAndAttendanceDate(section, today)
                            .isEmpty())
                    .count();

            Map<String, Object> summary = new LinkedHashMap<>();
            summary.put("totalClasses", sections.size());
            summary.put("markedToday",  markedCount);
            summary.put("pendingToday", sections.size() - markedCount);
            return summary;
        } catch (Exception e) {
            throw TeacherException.serverError("Failed to load attendance summary");
        }
    }

    private User requireTeacher(Authentication authentication) {
        return userRepository.findByEmail(authentication.getName())
                .orElseThrow(() -> new IllegalStateException("Authenticated user not found"));
    }
}

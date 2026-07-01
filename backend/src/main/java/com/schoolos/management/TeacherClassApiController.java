package com.schoolos.management;

import com.schoolos.user.User;
import com.schoolos.user.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api")
public class TeacherClassApiController {

    @Autowired
    private SubjectAssignmentRepository subjectAssignmentRepository;

    @Autowired
    private StudentRepository studentRepository;

    @Autowired
    private AttendanceRepository attendanceRepository;

    @Autowired
    private UserRepository userRepository;

    @GetMapping("/teacher/classes")
    @PreAuthorize("hasRole('TEACHER')")
    public ResponseEntity<?> getTeacherClasses(Authentication authentication) {
        try {
            User teacher = userRepository.findByEmail(authentication.getName())
                    .orElseThrow(() -> new IllegalStateException("Authenticated user not found"));

            List<SubjectAssignment> assignments =
                    subjectAssignmentRepository.findByTeacher(teacher);

            if (assignments.isEmpty()) {
                Map<String, Object> errorResp = new LinkedHashMap<>();
                errorResp.put("error", "No classes assigned. Please contact admin.");
                errorResp.put("classes", Collections.emptyList());
                return ResponseEntity.ok(errorResp);
            }

            List<Map<String, Object>> result = assignments.stream()
                    .map(assignment -> {
                        ClassSection section = assignment.getClassSection();
                        Map<String, Object> classData = new LinkedHashMap<>();
                        classData.put("id",           section.getId());
                        classData.put("className",    section.getGradeName() + " \u2013 " + section.getSectionName());
                        classData.put("subject",      assignment.getSubjectName());
                        classData.put("studentCount", studentRepository.countByClassSection(section));
                        classData.put("status",       "active");
                        classData.put("isHomeClass",  assignment.isHomeClass());
                        return classData;
                    })
                    .collect(Collectors.toList());

            return ResponseEntity.ok(result);
        } catch (Exception e) {
            return ResponseEntity.status(500).body(null);
        }
    }

    @GetMapping("/teacher/attendance/summary")
    @PreAuthorize("hasRole('TEACHER')")
    public ResponseEntity<Map<String, Object>> getAttendanceSummary(Authentication authentication) {
        try {
            User teacher = userRepository.findByEmail(authentication.getName())
                    .orElseThrow(() -> new IllegalStateException("Authenticated user not found"));

            List<SubjectAssignment> assignments =
                    subjectAssignmentRepository.findByTeacher(teacher);
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
            return ResponseEntity.ok(summary);
        } catch (Exception e) {
            return ResponseEntity.status(500).body(null);
        }
    }
}
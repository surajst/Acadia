package com.concept.student.web;

import com.concept.student.app.StudentException;
import com.concept.student.app.StudentService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Interface layer for the mobile student app. Thin binding over
 * {@link StudentService}; student resolution and all logic live in the
 * application layer (ADR 0001).
 */
@RestController
@RequestMapping("/api/mobile/student")
public class MobileStudentController {

    private final StudentService studentService;

    public MobileStudentController(StudentService studentService) {
        this.studentService = studentService;
    }

    @GetMapping("/dashboard")
    public ResponseEntity<?> dashboard(Authentication authentication) {
        return ResponseEntity.ok(studentService.mobileDashboard(authentication));
    }

    @GetMapping("/attendance")
    public ResponseEntity<?> attendance(Authentication authentication) {
        return ResponseEntity.ok(studentService.mobileAttendance(authentication));
    }

    @GetMapping("/tasks")
    @PreAuthorize("hasRole('STUDENT')")
    public ResponseEntity<?> tasks(Authentication authentication) {
        return ResponseEntity.ok(studentService.mobileTasks(authentication));
    }

    @GetMapping("/syllabus")
    public ResponseEntity<?> syllabus(Authentication authentication) {
        return ResponseEntity.ok(studentService.mobileSyllabus(authentication));
    }

    @ExceptionHandler(StudentException.class)
    public ResponseEntity<?> handle(StudentException e) {
        return ResponseEntity.status(e.status()).body(Map.of("error", e.getMessage()));
    }
}

package com.concept.teacher.web;

import com.concept.teacher.app.TeacherException;
import com.concept.teacher.app.TeacherService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Interface layer for the teacher's class list and attendance summary. Thin
 * binding over {@link TeacherService} (ADR 0001).
 */
@RestController
@RequestMapping("/api")
public class TeacherClassApiController {

    private final TeacherService teacherService;

    public TeacherClassApiController(TeacherService teacherService) {
        this.teacherService = teacherService;
    }

    @GetMapping("/teacher/classes")
    @PreAuthorize("hasRole('TEACHER')")
    public ResponseEntity<?> getTeacherClasses(Authentication authentication) {
        return ResponseEntity.ok(teacherService.teacherClasses(authentication));
    }

    @GetMapping("/teacher/attendance/summary")
    @PreAuthorize("hasRole('TEACHER')")
    public ResponseEntity<?> getAttendanceSummary(Authentication authentication) {
        return ResponseEntity.ok(teacherService.attendanceSummary(authentication));
    }

    @ExceptionHandler(TeacherException.class)
    public ResponseEntity<?> handle(TeacherException e) {
        // Preserve the former god-controller's behaviour: internal failures
        // returned a bare status with no body.
        return ResponseEntity.status(e.status()).build();
    }
}

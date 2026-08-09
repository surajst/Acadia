package com.concept.tasks.web;

import com.concept.tasks.app.AttendancePayload;
import com.concept.tasks.app.TasksException;
import com.concept.tasks.app.TasksService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.UUID;

/**
 * Interface layer for teacher daily attendance. Thin binding over
 * {@link TasksService}; section-ownership checks and absent-alert dispatch live
 * in the service (ADR 0001).
 */
@RestController
@RequestMapping("/api/teacher")
public class TeacherAttendanceController {

    private final TasksService tasksService;

    public TeacherAttendanceController(TasksService tasksService) {
        this.tasksService = tasksService;
    }

    @GetMapping("/attendance/today/{sectionId}")
    @PreAuthorize("hasRole('TEACHER')")
    public ResponseEntity<?> today(@PathVariable UUID sectionId, Authentication authentication) {
        return ResponseEntity.ok(tasksService.todayAttendance(sectionId, authentication));
    }

    @PostMapping("/attendance/submit")
    @PreAuthorize("hasRole('TEACHER')")
    public ResponseEntity<?> submit(@RequestBody AttendancePayload payload, Authentication authentication) {
        return ResponseEntity.ok(tasksService.submitAttendance(payload, authentication));
    }

    @ExceptionHandler(TasksException.class)
    public ResponseEntity<?> handle(TasksException e) {
        return ResponseEntity.status(e.status()).body(Map.of("error", e.getMessage()));
    }
}

package com.concept.tasks.web;

import com.concept.tasks.app.CreateTaskRequest;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.UUID;

/**
 * Interface layer for teacher task management and the student task/attendance
 * reads. Thin binding over {@link TasksService} (ADR 0001).
 */
@RestController
@RequestMapping("/api")
public class TaskApiController {

    private final TasksService tasksService;

    public TaskApiController(TasksService tasksService) {
        this.tasksService = tasksService;
    }

    @PostMapping("/teacher/tasks/create")
    @PreAuthorize("hasAnyRole('TEACHER', 'ADMIN')")
    public ResponseEntity<?> createTask(@RequestBody CreateTaskRequest request, Authentication authentication) {
        return ResponseEntity.ok(tasksService.createTask(request, authentication));
    }

    @GetMapping("/teacher/my-students")
    @PreAuthorize("hasRole('TEACHER')")
    public ResponseEntity<?> searchMyStudents(@RequestParam(value = "q", defaultValue = "") String query,
                                              Authentication authentication) {
        return ResponseEntity.ok(tasksService.searchMyStudents(query, authentication));
    }

    @GetMapping("/teacher/test-students")
    public ResponseEntity<?> testStudents() {
        return ResponseEntity.ok(tasksService.testStudents());
    }

    @GetMapping("/teacher/tasks/my-tasks")
    @PreAuthorize("hasAnyRole('TEACHER', 'ADMIN')")
    public ResponseEntity<?> myTasks(Authentication authentication) {
        return ResponseEntity.ok(tasksService.myTasks(authentication));
    }

    @GetMapping("/student/attendance")
    @PreAuthorize("hasRole('STUDENT')")
    public ResponseEntity<?> studentAttendance(Authentication authentication) {
        return ResponseEntity.ok(tasksService.studentAttendance(authentication));
    }

    @GetMapping("/student/tasks")
    @PreAuthorize("hasAnyRole('STUDENT')")
    public ResponseEntity<?> studentTasks(Authentication authentication) {
        return ResponseEntity.ok(tasksService.studentTasks(authentication));
    }

    @GetMapping("/student/tasks/{taskId}/questions")
    @PreAuthorize("hasAnyRole('STUDENT')")
    public ResponseEntity<?> taskQuestions(@PathVariable UUID taskId) {
        return ResponseEntity.ok(tasksService.taskQuestions(taskId));
    }

    @ExceptionHandler(TasksException.class)
    public ResponseEntity<?> handle(TasksException e) {
        return ResponseEntity.status(e.status()).body(Map.of("error", e.getMessage()));
    }
}

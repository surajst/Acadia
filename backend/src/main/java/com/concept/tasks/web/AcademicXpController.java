package com.concept.tasks.web;

import com.concept.tasks.app.TasksException;
import com.concept.tasks.app.TasksService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Interface layer for the academic-XP submission queue: a student queues a
 * practiced skill, a teacher/principal reviews and approves it. Thin binding
 * over {@link TasksService}; ownership and state checks live in the service
 * (ADR 0001). These endpoints return plain-string bodies, so the exception
 * mapping is done inline rather than as JSON.
 */
@RestController
@RequestMapping("/api/academic")
public class AcademicXpController {

    private final TasksService tasksService;

    public AcademicXpController(TasksService tasksService) {
        this.tasksService = tasksService;
    }

    @PostMapping("/submit-task")
    @PreAuthorize("hasRole('STUDENT')")
    public ResponseEntity<String> submitTask(@RequestParam UUID studentId,
                                             @RequestParam String skillName,
                                             @RequestParam Integer xpBounty,
                                             Authentication authentication) {
        try {
            tasksService.submitAcademicTask(studentId, skillName, xpBounty, authentication);
            return ResponseEntity.ok("Task successfully queued for teacher validation.");
        } catch (TasksException e) {
            return ResponseEntity.status(e.status()).body(e.getMessage());
        }
    }

    @GetMapping("/teacher/pending")
    @PreAuthorize("hasAnyRole('TEACHER', 'ADMIN', 'PRINCIPAL')")
    public ResponseEntity<?> pending() {
        return ResponseEntity.ok(tasksService.pendingSubmissions());
    }

    @PostMapping("/teacher/approve-xp")
    @PreAuthorize("hasAnyRole('TEACHER', 'ADMIN', 'PRINCIPAL')")
    public ResponseEntity<String> approveXp(@RequestParam UUID submissionId) {
        try {
            return ResponseEntity.ok(tasksService.approveXp(submissionId));
        } catch (TasksException e) {
            if (e.status() == 404) {
                return ResponseEntity.notFound().build();
            }
            return ResponseEntity.status(e.status()).body(e.getMessage());
        }
    }
}

package com.concept.tasks.web;

import com.concept.tasks.app.TasksException;
import com.concept.tasks.app.TasksService;
import com.concept.tenant.TenantContext;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
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
    private final TenantContext tenantContext;

    public AcademicXpController(TasksService tasksService, TenantContext tenantContext) {
        this.tasksService = tasksService;
        this.tenantContext = tenantContext;
    }

    /** What a pupil hands in: their own work, and nothing else. */
    public static class SubmitTaskRequest {
        public UUID taskId;
        public String notes;
        public List<String> answers;
    }

    /**
     * Hand in a task.
     *
     * <p>Takes a body rather than the previous query parameters, which included
     * the XP to award — the reward is read off the task server-side now, so a
     * pupil cannot name their own price. Nothing called the old shape.
     */
    @PostMapping("/submit-task")
    @PreAuthorize("hasRole('STUDENT')")
    public ResponseEntity<?> submitTask(@RequestBody SubmitTaskRequest request,
                                        Authentication authentication) {
        try {
            return ResponseEntity.ok(tasksService.submitTaskForCurrentStudent(
                    request.taskId, request.notes, request.answers, authentication));
        } catch (TasksException e) {
            return ResponseEntity.status(e.status()).body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/teacher/pending")
    @PreAuthorize("hasAnyRole('TEACHER', 'ADMIN', 'PRINCIPAL')")
    public ResponseEntity<?> pending() {
        return ResponseEntity.ok(tasksService.pendingSubmissions(tenantId()));
    }

    @PostMapping("/teacher/approve-xp")
    @PreAuthorize("hasAnyRole('TEACHER', 'ADMIN', 'PRINCIPAL')")
    public ResponseEntity<String> approveXp(@RequestParam UUID submissionId) {
        try {
            return ResponseEntity.ok(tasksService.approveXp(submissionId, tenantId()));
        } catch (TasksException e) {
            if (e.status() == 404) {
                return ResponseEntity.notFound().build();
            }
            return ResponseEntity.status(e.status()).body(e.getMessage());
        }
    }

    private UUID tenantId() {
        return tenantContext.getTenantId().orElse(null);
    }
}

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
    public ResponseEntity<?> createTask(@jakarta.validation.Valid @RequestBody CreateTaskRequest request,
                                       Authentication authentication) {
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

    /**
     * The grades this caller can actually set a task for. The form used to
     * offer a fixed Class 5-10 regardless of what the school runs.
     */
    @GetMapping("/teacher/grade-options")
    @PreAuthorize("hasAnyRole('TEACHER', 'ADMIN', 'PRINCIPAL')")
    public ResponseEntity<?> gradeOptions(Authentication authentication) {
        return ResponseEntity.ok(tasksService.gradeOptionsForCaller(authentication));
    }

    /**
     * The sections this caller can set a task for. The task form asks for a
     * section rather than a grade now: a grade covers every section in it, which
     * is how a task set for 6-A reached 6-B.
     */
    @GetMapping("/teacher/section-options")
    @PreAuthorize("hasAnyRole('TEACHER', 'ADMIN', 'PRINCIPAL')")
    public ResponseEntity<?> sectionOptions(Authentication authentication) {
        return ResponseEntity.ok(tasksService.sectionOptionsForCaller(authentication));
    }

    @GetMapping("/teacher/tasks/my-tasks")
    @PreAuthorize("hasAnyRole('TEACHER', 'ADMIN')")
    public ResponseEntity<?> myTasks(Authentication authentication) {
        return ResponseEntity.ok(tasksService.myTasks(authentication));
    }

    /**
     * Managing a task after it is set: edit, close, reopen, delete, and who has
     * handed it in. The list of tasks was read-only, so a typo lived for the life
     * of the task and a finished one stayed on every child's list.
     *
     * <p>ADMIN is on these alongside TEACHER for the same reason it is on
     * my-tasks: somebody has to be able to clear up after a teacher who has left.
     * The service checks ownership again -- a task id in a URL is not a
     * permission.
     */
    @PostMapping("/teacher/tasks/{taskId}/update")
    @PreAuthorize("hasAnyRole('TEACHER', 'ADMIN')")
    public ResponseEntity<?> updateTask(@PathVariable("taskId") UUID taskId,
                                        @RequestBody UpdateTaskBody body,
                                        Authentication authentication) {
        return ResponseEntity.ok(tasksService.updateTask(taskId, body.title(), body.description(),
                body.dueDate(), body.xpReward(), authentication));
    }

    /** What may be changed after a task is set. Who it is for is not on this list. */
    public record UpdateTaskBody(String title, String description,
                                 @com.fasterxml.jackson.annotation.JsonFormat(
                                         shape = com.fasterxml.jackson.annotation.JsonFormat.Shape.STRING,
                                         pattern = "yyyy-MM-dd")
                                 java.time.LocalDate dueDate,
                                 Integer xpReward) {}

    @PostMapping("/teacher/tasks/{taskId}/close")
    @PreAuthorize("hasAnyRole('TEACHER', 'ADMIN')")
    public ResponseEntity<?> closeTask(@PathVariable("taskId") UUID taskId, Authentication authentication) {
        return ResponseEntity.ok(tasksService.closeTask(taskId, authentication));
    }

    @PostMapping("/teacher/tasks/{taskId}/reopen")
    @PreAuthorize("hasAnyRole('TEACHER', 'ADMIN')")
    public ResponseEntity<?> reopenTask(@PathVariable("taskId") UUID taskId, Authentication authentication) {
        return ResponseEntity.ok(tasksService.reopenTask(taskId, authentication));
    }

    @PostMapping("/teacher/tasks/{taskId}/delete")
    @PreAuthorize("hasAnyRole('TEACHER', 'ADMIN')")
    public ResponseEntity<?> deleteTask(@PathVariable("taskId") UUID taskId, Authentication authentication) {
        return ResponseEntity.ok(tasksService.deleteTask(taskId, authentication));
    }

    @GetMapping("/teacher/tasks/{taskId}/submissions")
    @PreAuthorize("hasAnyRole('TEACHER', 'ADMIN')")
    public ResponseEntity<?> taskSubmissions(@PathVariable("taskId") UUID taskId, Authentication authentication) {
        return ResponseEntity.ok(tasksService.taskSubmissions(taskId, authentication));
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
    public ResponseEntity<?> taskQuestions(@PathVariable UUID taskId, Authentication authentication) {
        return ResponseEntity.ok(tasksService.taskQuestions(taskId, authentication));
    }

    @ExceptionHandler(TasksException.class)
    public ResponseEntity<?> handle(TasksException e) {
        return ResponseEntity.status(e.status()).body(Map.of("error", e.getMessage()));
    }
}

package com.concept.tasks.app;

/**
 * Signals a task/attendance request that cannot be served — validation (400),
 * cross-student/section access (403), or a missing entity (404). Carries the
 * HTTP status so the thin web layer maps it without holding any rules (ADR 0001).
 */
public class TasksException extends RuntimeException {

    private final int status;

    private TasksException(int status, String message) {
        super(message);
        this.status = status;
    }

    public int status() {
        return status;
    }

    public static TasksException badRequest(String message) {
        return new TasksException(400, message);
    }

    public static TasksException forbidden(String message) {
        return new TasksException(403, message);
    }

    public static TasksException notFound(String message) {
        return new TasksException(404, message);
    }
}

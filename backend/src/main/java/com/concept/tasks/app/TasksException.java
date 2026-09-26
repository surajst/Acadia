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

    /**
     * 409. The request was understood and allowed, and the thing it asks for has
     * already happened -- a second hand-in of the same task being the case this
     * was added for. A 400 would read as "you got the request wrong", which is
     * not what a pupil pressing a button they should not have been shown did.
     */
    public static TasksException conflict(String message) {
        return new TasksException(409, message);
    }

    public static TasksException notFound(String message) {
        return new TasksException(404, message);
    }
}

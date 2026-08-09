package com.concept.assignment.app;

/**
 * Signals an assignment request that cannot be served — a duplicate (409),
 * invalid input (400), or the dev-only seed guard (403). Carries the HTTP
 * status so the thin web layer can map it (JSON status or flash message)
 * without holding any rules (ADR 0001).
 */
public class AssignmentException extends RuntimeException {

    private final int status;

    private AssignmentException(int status, String message) {
        super(message);
        this.status = status;
    }

    public int status() {
        return status;
    }

    public static AssignmentException duplicate(String message) {
        return new AssignmentException(409, message);
    }

    public static AssignmentException badRequest(String message) {
        return new AssignmentException(400, message);
    }

    public static AssignmentException forbidden(String message) {
        return new AssignmentException(403, message);
    }
}

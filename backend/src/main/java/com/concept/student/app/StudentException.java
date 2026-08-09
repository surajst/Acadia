package com.concept.student.app;

/**
 * Signals a student-scoped request that cannot be served — missing entity (400)
 * or reaching for another student's quest/reward (403). Carries the HTTP status
 * so the thin web layer can map it without holding any rules (ADR 0001).
 */
public class StudentException extends RuntimeException {

    private final int status;

    private StudentException(int status, String message) {
        super(message);
        this.status = status;
    }

    public int status() {
        return status;
    }

    public static StudentException badRequest(String message) {
        return new StudentException(400, message);
    }

    public static StudentException forbidden(String message) {
        return new StudentException(403, message);
    }
}

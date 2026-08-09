package com.concept.timetable.app;

/**
 * Signals a timetable request that cannot be served — validation failures (400)
 * or the dev-only seed guard (403). Carries the HTTP status so the thin web
 * layer can map it without holding any of the rules (ADR 0001).
 */
public class TimetableException extends RuntimeException {

    private final int status;

    private TimetableException(int status, String message) {
        super(message);
        this.status = status;
    }

    public int status() {
        return status;
    }

    public static TimetableException badRequest(String message) {
        return new TimetableException(400, message);
    }

    public static TimetableException forbidden(String message) {
        return new TimetableException(403, message);
    }
}

package com.concept.teacher.app;

/**
 * Application-layer failure for the teacher slice, carrying the HTTP status the
 * interface layer should surface (ADR 0001).
 */
public class TeacherException extends RuntimeException {

    private final int status;

    private TeacherException(int status, String message) {
        super(message);
        this.status = status;
    }

    public int status() {
        return status;
    }

    public static TeacherException serverError(String message) {
        return new TeacherException(500, message);
    }
}

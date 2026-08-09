package com.concept.curriculum.app;

/**
 * Application-layer failure for the curriculum slice, carrying the HTTP status
 * the interface layer should surface (ADR 0001). Keeps web free of any
 * knowledge of persistence or domain enums.
 */
public class CurriculumException extends RuntimeException {

    private final int status;

    private CurriculumException(int status, String message) {
        super(message);
        this.status = status;
    }

    public int status() {
        return status;
    }

    public static CurriculumException badRequest(String message) {
        return new CurriculumException(400, message);
    }
}

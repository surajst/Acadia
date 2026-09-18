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

    /**
     * Used when a topic id is not in the caller's own school. Deliberately the
     * same answer as a genuinely missing row, so the endpoint cannot be used to
     * probe whether another school owns a given id.
     */
    public static CurriculumException notFound(String message) {
        return new CurriculumException(404, message);
    }
}

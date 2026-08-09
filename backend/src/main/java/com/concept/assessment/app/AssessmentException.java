package com.concept.assessment.app;

/**
 * Signals an assessment/report-card request that cannot be served — a missing
 * entity (400) or a parent reaching for another family's child (403). Carries
 * the HTTP status so the thin web layer maps it without holding rules (ADR 0001).
 */
public class AssessmentException extends RuntimeException {

    private final int status;

    private AssessmentException(int status, String message) {
        super(message);
        this.status = status;
    }

    public int status() {
        return status;
    }

    public static AssessmentException badRequest(String message) {
        return new AssessmentException(400, message);
    }

    public static AssessmentException forbidden(String message) {
        return new AssessmentException(403, message);
    }
}

package com.concept.parent.app;

/**
 * Signals a parent-scoped request that cannot be served — either the caller has
 * no parent profile / linked child (400) or is reaching for another family's
 * data (403). Carries the HTTP status so the thin web layer can map it without
 * knowing any of the access-control rules (ADR 0001).
 */
public class ParentException extends RuntimeException {

    private final int status;

    private ParentException(int status, String message) {
        super(message);
        this.status = status;
    }

    public int status() {
        return status;
    }

    public static ParentException badRequest(String message) {
        return new ParentException(400, message);
    }

    public static ParentException forbidden(String message) {
        return new ParentException(403, message);
    }
}

package com.concept.oversight.app;

/**
 * Signals an oversight/approval request that cannot be served (validation, or a
 * cross-tenant staff/invoice reach). Carries the HTTP status so the thin web
 * layer maps it without holding any rules (ADR 0001). Oversight failures are
 * uniformly 400 in the legacy controllers; the factory keeps that contract.
 */
public class OversightException extends RuntimeException {

    private final int status;

    private OversightException(int status, String message) {
        super(message);
        this.status = status;
    }

    public int status() {
        return status;
    }

    public static OversightException badRequest(String message) {
        return new OversightException(400, message);
    }
}

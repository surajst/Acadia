package com.concept.billing.app;

/**
 * Raised when a student cannot be billed because the school has not configured
 * a fee plan for that grade level and year.
 *
 * <p>This exists so the failure is loud. Invoicing used to fall back to a
 * hardcoded 15000 + 5000 whenever no fee structure was found, which made an
 * unconfigured school indistinguishable from a correctly configured one -- every
 * school on the platform billed a plausible-looking 20,000 per student and
 * nothing anywhere said the number was invented. A school only found out by
 * noticing the amount was wrong, and by then it had been sent to families.
 *
 * <p>The message is written to be shown to an admin, so it names the grade level
 * and where to go and fix it.
 */
public class FeePlanMissingException extends RuntimeException {

    public FeePlanMissingException(String message) {
        super(message);
    }
}

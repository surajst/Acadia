package com.concept.common;

/**
 * Seam for sending one email.
 *
 * <p>Deliberately separate from {@link NotificationDeliveryService} rather than
 * widening it. That one sends a single string to a phone number; email has a
 * subject and a body, and folding both shapes into one method is how a subject
 * line ends up prefixed to an SMS. Two seams, two provider beans.
 *
 * <p>The default implementation logs instead of sending, so an unconfigured
 * deployment behaves exactly as it did before email existed.
 */
public interface EmailDeliveryService {

    /**
     * @return the outcome, so callers can tell the user what actually happened.
     *         Implementations must not throw for delivery failure -- an invite
     *         whose email bounced still created the account, and pretending
     *         otherwise loses the account.
     */
    EmailResult send(String toAddress, String subject, String body);

    /**
     * @param delivered whether the provider accepted the message
     * @param detail    human-readable outcome, shown to an admin when it failed
     */
    record EmailResult(boolean delivered, String detail) {
        public static EmailResult sent() {
            return new EmailResult(true, "Sent");
        }

        public static EmailResult notConfigured() {
            return new EmailResult(false, "Email is not configured, so nothing was sent");
        }

        public static EmailResult failed(String detail) {
            return new EmailResult(false, detail);
        }
    }
}

package com.concept.roster.app;

/**
 * The one rule for what counts as a contact number a school can actually ring.
 *
 * <p>The bulk importer had this rule and the manual Register Student form did
 * not, so "abc123" was accepted through the form and rejected from a CSV --
 * the same school, the same field, two different answers. A guardian phone is
 * the channel absence alerts and fee reminders go out on, so a value nobody
 * can dial is a silent delivery failure rather than a cosmetic problem.
 *
 * <p>Deliberately permissive about shape: this is not an attempt to parse
 * international numbering plans. It accepts an optional leading +, then digits
 * with spaces, hyphens and brackets, and insists on enough digits to be a real
 * number. That is exactly the importer's long-standing rule, lifted rather
 * than reinvented so the two cannot drift again.
 */
public final class PhoneNumbers {

    /** The importer's rule, unchanged. */
    private static final String PATTERN = "^\\+?[0-9\\s\\-()]{7,}$";

    private PhoneNumbers() {}

    public static boolean isValid(String raw) {
        if (raw == null) {
            return false;
        }
        String trimmed = raw.trim();
        if (!trimmed.matches(PATTERN)) {
            return false;
        }
        // The character class alone would accept "((( )))-" -- seven
        // punctuation marks and no number at all.
        return trimmed.chars().filter(Character::isDigit).count() >= 7;
    }

    /**
     * @throws IllegalArgumentException with a message naming whose number it is,
     *         so an admin correcting a row knows which field to look at
     */
    public static void require(String raw, String whose) {
        if (!isValid(raw)) {
            throw new IllegalArgumentException(
                    "Enter a valid " + whose + " phone number — digits only, at least 7 of them.");
        }
    }
}

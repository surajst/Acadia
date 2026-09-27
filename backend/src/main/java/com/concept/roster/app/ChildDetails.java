package com.concept.roster.app;

import java.time.LocalDate;

/**
 * The things a school needs to know about a child that are not their name.
 *
 * <p>Grouped into a record rather than four more parameters on
 * {@code updateStudent}, which already carries nine. A signature long enough
 * that callers pass arguments by counting commas is a signature that will one
 * day get two of them the wrong way round.
 *
 * <p>Every field is optional. The roster predates all of them, and a required
 * field here would block editing every child already on file.
 */
public record ChildDetails(LocalDate dateOfBirth,
                           String medicalNotes,
                           String emergencyContactName,
                           String emergencyContactPhone,
                           /**
                            * Whether this child may appear in a photograph the
                            * school publishes.
                            *
                            * <p>Boxed, because null means "the caller is not
                            * editing this field" -- a different thing from false,
                            * and the difference matters: a caller that does not
                            * carry the checkbox must not silently withdraw
                            * permission to photograph a child.
                            */
                           Boolean photoConsent) {

    /** Nothing supplied -- used by callers that do not edit these fields. */
    public static ChildDetails none() {
        return new ChildDetails(null, null, null, null, null);
    }
}

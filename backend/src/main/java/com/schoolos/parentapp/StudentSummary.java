package com.schoolos.parentapp;

import java.util.UUID;

/**
 * SIS-agnostic view of a student, as consumed by parent-facing features.
 * Kept independent of the JPA {@code Student} entity so a future
 * {@code SisDataProvider} backed by an external SIS can populate it too.
 */
public record StudentSummary(
        UUID id,
        String firstName,
        String lastName,
        String gradeName,
        String sectionName
) {
}

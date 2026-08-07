package com.concept.attendance.app;

import java.util.List;
import java.util.UUID;

/** Flat, presentation-ready data for the attendance roll-call form. No entities. */
public record AttendanceFormView(
        UUID currentClassId,
        String currentClassLabel,
        List<ClassOption> classList,
        List<StudentRow> students
) {
    /** A selectable class in the roster-scope switcher. */
    public record ClassOption(UUID id, String label) {}

    /** One row in the roll-call table. */
    public record StudentRow(UUID id, String firstName, String lastName, String rollNumber) {}
}

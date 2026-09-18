package com.concept.tasks.app;

import com.concept.shared.data.AttendanceStatus;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Flat request payload for a teacher's daily attendance submission. Lives in
 * the application layer so the web controller binds it without depending on the
 * management package; the AttendanceStatus enum is referenced here, not in web
 * (ADR 0001).
 *
 * <p>{@code date} is optional and means today when absent, which is what every
 * existing client sends. It exists because the register could previously only
 * ever be written for {@code LocalDate.now()}: a teacher who missed Monday had
 * no way to mark it, and a school had no way to correct a day after the fact.
 */
public record AttendancePayload(LocalDate date, List<AttendanceEntry> attendance) {

    /** Today's register — the shape every client used before `date` existed. */
    public AttendancePayload(List<AttendanceEntry> attendance) {
        this(null, attendance);
    }

    public record AttendanceEntry(UUID studentId, AttendanceStatus status, String remarks) {}
}

package com.concept.tasks.app;

import com.concept.shared.data.AttendanceStatus;

import java.util.List;
import java.util.UUID;

/**
 * Flat request payload for a teacher's daily attendance submission. Lives in
 * the application layer so the web controller binds it without depending on the
 * management package; the AttendanceStatus enum is referenced here, not in web
 * (ADR 0001).
 */
public record AttendancePayload(List<AttendanceEntry> attendance) {

    public record AttendanceEntry(UUID studentId, AttendanceStatus status, String remarks) {}
}

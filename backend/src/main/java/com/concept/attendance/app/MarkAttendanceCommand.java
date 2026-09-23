package com.concept.attendance.app;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Request to mark attendance for a set of students on one date. Statuses are
 * plain strings (parsed in the application layer) so the interface layer stays
 * free of persistence types. The two lists are positional: statuses[i] applies
 * to studentIds[i].
 */
public record MarkAttendanceCommand(
        UUID tenantId,
        List<UUID> studentIds,
        List<String> statuses,
        /** The day being marked. Null means today, which is the ordinary case. */
        LocalDate attendanceDate
) {
    public MarkAttendanceCommand(UUID tenantId, List<UUID> studentIds, List<String> statuses) {
        this(tenantId, studentIds, statuses, null);
    }
}

package com.concept.attendance.app;

import java.util.List;
import java.util.UUID;

/**
 * Request to mark attendance for a set of students on today's date. Statuses are
 * plain strings (parsed in the application layer) so the interface layer stays
 * free of persistence types. The two lists are positional: statuses[i] applies
 * to studentIds[i].
 */
public record MarkAttendanceCommand(
        UUID tenantId,
        List<UUID> studentIds,
        List<String> statuses
) {}

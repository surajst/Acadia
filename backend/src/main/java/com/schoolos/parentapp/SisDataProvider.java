package com.schoolos.parentapp;

import com.schoolos.academics.SubjectPerformance;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Boundary between parent-facing features (ParentQuest/reward gamification,
 * attendance view, subject performance view) and however student/attendance/
 * performance data is actually sourced.
 *
 * The default implementation ({@link LocalJpaSisDataProvider}) reads from this
 * backend's own JPA repositories. A school that runs its own external SIS can
 * later be served by an alternate implementation (CSV import, REST connector,
 * etc.) without touching any parent-facing controller.
 */
public interface SisDataProvider {

    Optional<StudentSummary> getStudent(UUID studentId);

    List<AttendanceRecord> getAttendance(UUID studentId, DateRange range);

    List<SubjectPerformance> getSubjectPerformance(UUID studentId);
}

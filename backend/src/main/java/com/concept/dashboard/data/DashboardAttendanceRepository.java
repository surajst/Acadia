package com.concept.dashboard.data;

import com.concept.shared.data.Attendance;
import com.concept.shared.data.AttendanceStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.UUID;

/** Today's attendance counts for the dashboard stat (tenant-scoped). */
@Repository
public interface DashboardAttendanceRepository extends JpaRepository<Attendance, UUID> {

    long countByTenantIdAndAttendanceDateAndStatus(UUID tenantId, LocalDate attendanceDate, AttendanceStatus status);

    /**
     * How many children have a register entry today, of any status.
     *
     * <p>The headline stat used to divide absences by total enrolment, which
     * silently treats "not marked" as "present": before anyone had opened the
     * register the dashboard read 100%.
     */
    long countByTenantIdAndAttendanceDate(UUID tenantId, LocalDate attendanceDate);
}

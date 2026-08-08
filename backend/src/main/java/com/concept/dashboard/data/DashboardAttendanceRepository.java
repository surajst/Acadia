package com.concept.dashboard.data;

import com.concept.management.Attendance;
import com.concept.management.AttendanceStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.UUID;

/** Today's absence count for the dashboard attendance stat (tenant-scoped). */
@Repository
public interface DashboardAttendanceRepository extends JpaRepository<Attendance, UUID> {

    long countByTenantIdAndAttendanceDateAndStatus(UUID tenantId, LocalDate attendanceDate, AttendanceStatus status);
}

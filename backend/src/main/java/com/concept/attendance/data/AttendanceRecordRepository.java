package com.concept.attendance.data;

import com.concept.shared.data.Attendance;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

/** Writes attendance rows. */
@Repository
public interface AttendanceRecordRepository extends JpaRepository<Attendance, UUID> {

    /**
     * The existing entry for one child on one day, if the register was already
     * taken.
     *
     * <p>Without this, submitting a register twice inserted a second row rather
     * than correcting the first, and the day then held two contradictory
     * answers for the same child with nothing to say which was meant.
     */
    java.util.Optional<Attendance> findByStudent_IdAndAttendanceDateAndTenantId(
            UUID studentId, java.time.LocalDate attendanceDate, UUID tenantId);
}

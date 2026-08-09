package com.concept.attendance.data;

import com.concept.shared.data.Attendance;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

/** Writes attendance rows. */
@Repository
public interface AttendanceRecordRepository extends JpaRepository<Attendance, UUID> {
}

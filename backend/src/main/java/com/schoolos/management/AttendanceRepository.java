package com.schoolos.management;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Repository
public interface AttendanceRepository extends JpaRepository<Attendance, UUID> {
    long countByAttendanceDateAndStatus(LocalDate attendanceDate, AttendanceStatus status);
    long countByStudentIdAndStatus(UUID studentId, AttendanceStatus status);
    List<Attendance> findByClassSectionAndAttendanceDate(ClassSection classSection, LocalDate date);
    List<Attendance> findByStudentAndAttendanceDate(Student student, LocalDate date);
    List<Attendance> findByStudent(Student student);
    List<Attendance> findByStudentAndAttendanceDateBetween(Student student, LocalDate start, LocalDate end);
}
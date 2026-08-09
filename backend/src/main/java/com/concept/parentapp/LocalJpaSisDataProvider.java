package com.concept.parentapp;

import com.concept.academics.AssessmentService;
import com.concept.academics.SubjectPerformance;
import com.concept.shared.data.Attendance;
import com.concept.shared.data.AttendanceRepository;
import com.concept.shared.data.ClassSection;
import com.concept.shared.data.Student;
import com.concept.shared.data.StudentRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Default {@link SisDataProvider}: thin adapter over this backend's own
 * repositories. No new business logic — existing data, reshaped into
 * SIS-agnostic DTOs.
 */
@Service
public class LocalJpaSisDataProvider implements SisDataProvider {

    @Autowired
    private StudentRepository studentRepository;

    @Autowired
    private AttendanceRepository attendanceRepository;

    @Autowired
    private AssessmentService assessmentService;

    @Override
    public Optional<StudentSummary> getStudent(UUID studentId) {
        return studentRepository.findById(studentId).map(this::toSummary);
    }

    @Override
    public List<AttendanceRecord> getAttendance(UUID studentId, DateRange range) {
        return attendanceRepository
                .findByStudentIdAndAttendanceDateBetween(studentId, range.start(), range.end())
                .stream()
                .sorted(Comparator.comparing(Attendance::getAttendanceDate).reversed())
                .map(a -> new AttendanceRecord(a.getAttendanceDate(), a.getStatus().name()))
                .collect(Collectors.toList());
    }

    @Override
    public List<SubjectPerformance> getSubjectPerformance(UUID studentId) {
        return assessmentService.getSubjectPerformance(studentId);
    }

    private StudentSummary toSummary(Student student) {
        ClassSection section = student.getClassSection();
        return new StudentSummary(
                student.getId(),
                student.getFirstName(),
                student.getLastName(),
                section != null ? section.getGradeName() : null,
                section != null ? section.getSectionName() : null
        );
    }
}

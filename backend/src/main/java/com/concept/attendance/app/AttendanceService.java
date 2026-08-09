package com.concept.attendance.app;

import com.concept.attendance.data.AttendanceRecordRepository;
import com.concept.attendance.data.AttendanceStudentRepository;
import com.concept.common.NotificationDeliveryService;
import com.concept.shared.data.Attendance;
import com.concept.shared.data.AttendanceStatus;
import com.concept.shared.data.Parent;
import com.concept.shared.data.SchoolClass;
import com.concept.shared.data.SchoolClassRepository;
import com.concept.shared.data.Student;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Application layer for attendance. Owns the roll-call read model and the
 * mark-attendance decision, including the tenant checks. Knows nothing about
 * HTTP; returns/accepts flat view and command records only.
 */
@Service
public class AttendanceService {

    private final AttendanceStudentRepository studentRepository;
    private final AttendanceRecordRepository attendanceRepository;
    private final SchoolClassRepository schoolClassRepository;
    private final NotificationDeliveryService notificationDeliveryService;

    public AttendanceService(AttendanceStudentRepository studentRepository,
                             AttendanceRecordRepository attendanceRepository,
                             SchoolClassRepository schoolClassRepository,
                             NotificationDeliveryService notificationDeliveryService) {
        this.studentRepository = studentRepository;
        this.attendanceRepository = attendanceRepository;
        this.schoolClassRepository = schoolClassRepository;
        this.notificationDeliveryService = notificationDeliveryService;
    }

    /**
     * Build the roll-call form for a tenant, optionally scoped to one class.
     * A {@code classId} that does not belong to the tenant is ignored (the form
     * falls back to the whole tenant), so a foreign class id can never surface
     * another school's students.
     */
    @Transactional(readOnly = true)
    public AttendanceFormView buildForm(UUID tenantId, UUID classId) {
        List<SchoolClass> classes = tenantId != null
                ? schoolClassRepository.findByTenantId(tenantId) : Collections.emptyList();

        SchoolClass selected = null;
        if (classId != null) {
            selected = classes.stream().filter(c -> c.getId().equals(classId)).findFirst().orElse(null);
        } else if (!classes.isEmpty()) {
            selected = classes.get(0);
        }

        List<Student> students;
        if (selected != null) {
            students = studentRepository.findBySchoolClassId(selected.getId());
        } else {
            students = tenantId != null ? studentRepository.findByTenantId(tenantId) : Collections.emptyList();
        }

        List<AttendanceFormView.ClassOption> classList = classes.stream()
                .map(c -> new AttendanceFormView.ClassOption(c.getId(), label(c)))
                .collect(Collectors.toList());
        List<AttendanceFormView.StudentRow> rows = students.stream()
                .map(s -> new AttendanceFormView.StudentRow(
                        s.getId(), s.getFirstName(), s.getLastName(), s.getRollNumber()))
                .collect(Collectors.toList());

        return new AttendanceFormView(
                selected != null ? selected.getId() : null,
                selected != null ? label(selected) : null,
                classList,
                rows);
    }

    /**
     * Mark attendance for today. Each student is resolved tenant-scoped, so a
     * foreign student id is rejected outright rather than written into its tenant.
     */
    @Transactional
    public void mark(MarkAttendanceCommand command) {
        List<UUID> studentIds = command.studentIds();
        List<String> statuses = command.statuses();
        if (studentIds == null || statuses == null || studentIds.size() != statuses.size()) {
            throw new IllegalArgumentException("studentIds and statuses must be present and the same length");
        }

        LocalDate today = LocalDate.now();
        for (int i = 0; i < studentIds.size(); i++) {
            UUID studentId = studentIds.get(i);
            AttendanceStatus status = parseStatus(statuses.get(i));

            Student student = studentRepository.findByIdAndTenantId(studentId, command.tenantId())
                    .orElseThrow(() -> new IllegalArgumentException("Not authorized for student: " + studentId));

            Attendance attendance = new Attendance();
            attendance.setId(UUID.randomUUID());
            attendance.setTenantId(student.getTenantId());
            attendance.setAcademicYearId(student.getAcademicYearId());
            attendance.setStudent(student);
            attendance.setClassSection(student.getClassSection());
            attendance.setAttendanceDate(today);
            attendance.setStatus(status);
            attendanceRepository.saveAndFlush(attendance);

            if (status == AttendanceStatus.ABSENT) {
                for (Parent parent : student.getParents()) {
                    notificationDeliveryService.send(parent.getPhoneNumber(),
                            "[ALERT WHATSAPP DISPATCH] Sending to " + parent.getFirstName() + " " + parent.getLastName()
                                    + " (" + parent.getPhoneNumber() + "): Alert! Student " + student.getFirstName()
                                    + " was marked ABSENT today.");
                }
            }
        }
    }

    private AttendanceStatus parseStatus(String raw) {
        try {
            return AttendanceStatus.valueOf(raw.trim().toUpperCase());
        } catch (RuntimeException e) {
            throw new IllegalArgumentException("Unknown attendance status: " + raw);
        }
    }

    private String label(SchoolClass c) {
        return (c.getGradeLevel() + " - " + c.getSectionName()).trim();
    }
}

package com.concept.attendance.app;

import com.concept.attendance.data.AttendanceClassSectionRepository;
import com.concept.attendance.data.AttendanceRecordRepository;
import com.concept.attendance.data.AttendanceStudentRepository;
import com.concept.common.AuditLogService;
import com.concept.common.NotificationDeliveryService;
import com.concept.shared.data.Attendance;
import com.concept.shared.data.AttendanceStatus;
import com.concept.shared.data.Parent;
import com.concept.shared.data.ClassSection;
import com.concept.shared.data.Student;
import org.springframework.security.core.Authentication;
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
    private final AttendanceClassSectionRepository classSectionRepository;
    private final NotificationDeliveryService notificationDeliveryService;
    private final AuditLogService auditLogService;

    public AttendanceService(AttendanceStudentRepository studentRepository,
                             AttendanceRecordRepository attendanceRepository,
                             AttendanceClassSectionRepository classSectionRepository,
                             NotificationDeliveryService notificationDeliveryService,
                             AuditLogService auditLogService) {
        this.studentRepository = studentRepository;
        this.attendanceRepository = attendanceRepository;
        this.classSectionRepository = classSectionRepository;
        this.notificationDeliveryService = notificationDeliveryService;
        this.auditLogService = auditLogService;
    }

    /**
     * Build the roll-call form for a tenant, optionally scoped to one class.
     * A {@code classId} that does not belong to the tenant is ignored (the form
     * falls back to the whole tenant), so a foreign class id can never surface
     * another school's students.
     */
    @Transactional(readOnly = true)
    public AttendanceFormView buildForm(UUID tenantId, UUID classId) {
        List<ClassSection> classes = tenantId != null
                ? classSectionRepository.findByTenantId(tenantId) : Collections.emptyList();

        ClassSection selected = null;
        if (classId != null) {
            selected = classes.stream().filter(c -> c.getId().equals(classId)).findFirst().orElse(null);
        } else if (!classes.isEmpty()) {
            selected = classes.get(0);
        }

        List<Student> students;
        if (selected != null) {
            students = studentRepository.findByClassSectionId(selected.getId());
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
    public void mark(MarkAttendanceCommand command, Authentication authentication) {
        List<UUID> studentIds = command.studentIds();
        List<String> statuses = command.statuses();
        if (studentIds == null || statuses == null || studentIds.size() != statuses.size()) {
            throw new IllegalArgumentException("studentIds and statuses must be present and the same length");
        }

        LocalDate today = resolveDate(command.attendanceDate());
        int absent = 0;
        UUID sectionId = null;
        String sectionLabel = null;
        for (int i = 0; i < studentIds.size(); i++) {
            UUID studentId = studentIds.get(i);
            AttendanceStatus status = parseStatus(statuses.get(i));

            Student student = studentRepository.findByIdAndTenantId(studentId, command.tenantId())
                    .orElseThrow(() -> new IllegalArgumentException("Not authorized for student: " + studentId));

            if (sectionId == null && student.getClassSection() != null) {
                sectionId = student.getClassSection().getId();
                sectionLabel = label(student.getClassSection());
            }

            // Correct the existing entry rather than adding a second one. A
            // register submitted twice used to leave the day holding two
            // contradictory answers for one child, with nothing to say which
            // was meant -- and it inflated the "how many were marked" count
            // the dashboard now divides by.
            Attendance attendance = attendanceRepository
                    .findByStudent_IdAndAttendanceDateAndTenantId(student.getId(), today, command.tenantId())
                    .orElseGet(() -> {
                        Attendance fresh = new Attendance();
                        fresh.setId(UUID.randomUUID());
                        fresh.setTenantId(student.getTenantId());
                        fresh.setAcademicYearId(student.getAcademicYearId());
                        fresh.setStudent(student);
                        fresh.setAttendanceDate(today);
                        return fresh;
                    });
            attendance.setClassSection(student.getClassSection());
            attendance.setStatus(status);
            attendanceRepository.saveAndFlush(attendance);

            if (status == AttendanceStatus.ABSENT) {
                absent++;
                for (Parent parent : student.getParents()) {
                    // Never dispatch to something that is not a phone number.
                    // The manual form used to accept "abc123", and a provider
                    // handed that either errors or, worse, normalises it into
                    // somebody else's number.
                    if (!com.concept.roster.app.PhoneNumbers.isValid(parent.getPhoneNumber())) {
                        continue;
                    }
                    notificationDeliveryService.send(parent.getPhoneNumber(),
                            "[ALERT WHATSAPP DISPATCH] Sending to " + parent.getFirstName() + " " + parent.getLastName()
                                    + " (" + parent.getPhoneNumber() + "): Alert! Student " + student.getFirstName()
                                    + " was marked ABSENT today.");
                }
            }
        }

        // One row for the submission, not one per child: the register is taken
        // as a single act, and thirty rows a day would bury every other entry
        // in the log. Marking a child absent also messages their guardian, so
        // this is the record of why that message went out.
        auditLogService.log(authentication, "ATTENDANCE_SUBMITTED", "ClassSection", sectionId,
                "Marked " + studentIds.size() + " student" + (studentIds.size() == 1 ? "" : "s")
                        + " for " + today + (today.equals(LocalDate.now()) ? "" : " (backdated)")
                        + (sectionLabel == null ? "" : " in " + sectionLabel)
                        + " — " + absent + " absent");
    }

    /**
     * How far back a register may be taken or corrected.
     *
     * <p>Something has to bound it: a teacher who was off sick on Monday needs
     * Tuesday to fix it, and nobody needs to rewrite last term. Thirty days
     * covers a monthly reporting cycle.
     */
    /** Public so the form can bound its own date control to the same window. */
    public static final int BACKFILL_WINDOW_DAYS = 30;

    private LocalDate resolveDate(LocalDate requested) {
        LocalDate today = LocalDate.now();
        if (requested == null) {
            return today;
        }
        if (requested.isAfter(today)) {
            throw new IllegalArgumentException("Attendance cannot be taken for a day that has not happened yet.");
        }
        if (requested.isBefore(today.minusDays(BACKFILL_WINDOW_DAYS))) {
            throw new IllegalArgumentException(
                    "Attendance can only be recorded or corrected within the last "
                            + BACKFILL_WINDOW_DAYS + " days.");
        }
        return requested;
    }

    private AttendanceStatus parseStatus(String raw) {
        try {
            return AttendanceStatus.valueOf(raw.trim().toUpperCase());
        } catch (RuntimeException e) {
            throw new IllegalArgumentException("Unknown attendance status: " + raw);
        }
    }

    private String label(ClassSection c) {
        return (c.getGradeName() + " - " + c.getSectionName()).trim();
    }
}

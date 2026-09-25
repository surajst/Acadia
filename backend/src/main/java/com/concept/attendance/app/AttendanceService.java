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
    private final com.concept.assignment.data.SubjectAssignmentRepository subjectAssignmentRepository;
    private final com.concept.user.CurrentUserService currentUserService;

    public AttendanceService(AttendanceStudentRepository studentRepository,
                             AttendanceRecordRepository attendanceRepository,
                             AttendanceClassSectionRepository classSectionRepository,
                             NotificationDeliveryService notificationDeliveryService,
                             AuditLogService auditLogService,
                             com.concept.assignment.data.SubjectAssignmentRepository subjectAssignmentRepository,
                             com.concept.user.CurrentUserService currentUserService) {
        this.studentRepository = studentRepository;
        this.attendanceRepository = attendanceRepository;
        this.classSectionRepository = classSectionRepository;
        this.notificationDeliveryService = notificationDeliveryService;
        this.auditLogService = auditLogService;
        this.subjectAssignmentRepository = subjectAssignmentRepository;
        this.currentUserService = currentUserService;
    }

    /**
     * The sections this caller may take a register for, or null meaning "all".
     *
     * <p>Every teacher could take attendance for every section: the dropdown
     * listed the whole school and the submit accepted whatever came back. Priya,
     * assigned only to 6-A, could mark Neha's 6-B -- and marking a child absent
     * messages their guardian, so this was not only a data-integrity problem.
     *
     * <p>A teacher gets the sections they are assigned to, which covers both the
     * home-class register and any section they teach. ADMIN and PRINCIPAL get
     * all of them: an admin covering for an absent teacher is ordinary, and a
     * principal needs to be able to correct a register.
     *
     * <p>Null rather than a set of every id, so the caller can tell "no limit"
     * from "limited to nothing" -- a teacher with no assignments yet gets an
     * empty set and an honest empty dropdown, not the whole school.
     */
    private java.util.Set<UUID> permittedSectionIds(Authentication authentication, UUID tenantId) {
        com.concept.user.User caller =
                currentUserService.getCurrentUser(authentication).orElse(null);
        if (caller == null || caller.getRole() == null) {
            return java.util.Set.of();
        }
        if (caller.getRole() == com.concept.user.UserRole.ADMIN
                || caller.getRole() == com.concept.user.UserRole.PRINCIPAL) {
            return null;
        }
        return subjectAssignmentRepository.findByTeacher(caller).stream()
                .map(com.concept.assignment.data.SubjectAssignment::getClassSection)
                .filter(sec -> sec != null && tenantId != null && tenantId.equals(sec.getTenantId()))
                .map(ClassSection::getId)
                .collect(Collectors.toSet());
    }

    /**
     * Build the roll-call form for a tenant, scoped to what this caller may
     * mark, and optionally to one class.
     *
     * <p>A {@code classId} that does not belong to the tenant -- or to the
     * caller -- is ignored, so a foreign class id can never surface another
     * school's students or another teacher's section.
     */
    @Transactional(readOnly = true)
    public AttendanceFormView buildForm(UUID tenantId, UUID classId, Authentication authentication) {
        java.util.Set<UUID> permitted = permittedSectionIds(authentication, tenantId);
        List<ClassSection> classes = tenantId != null
                ? classSectionRepository.findByTenantId(tenantId) : Collections.<ClassSection>emptyList();
        // Filtered here as well as enforced on submit. Offering a teacher a
        // section they will then be refused is a worse experience than not
        // offering it, and the server rule below is what makes it a rule.
        if (permitted != null) {
            classes = classes.stream()
                    .filter(c -> permitted.contains(c.getId()))
                    .collect(Collectors.toList());
        }

        ClassSection selected = null;
        if (classId != null) {
            selected = classes.stream().filter(c -> c.getId().equals(classId)).findFirst().orElse(null);
        } else if (!classes.isEmpty()) {
            selected = classes.get(0);
        }

        List<Student> students;
        if (selected != null) {
            students = studentRepository.findByClassSectionId(selected.getId());
        } else if (permitted != null) {
            // A teacher with no permitted section sees nobody, rather than the
            // whole school -- which is what the old fallback did when the
            // requested classId was not one of theirs.
            students = Collections.emptyList();
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

        java.util.Set<UUID> permitted = permittedSectionIds(authentication, command.tenantId());
        LocalDate today = resolveDate(command.attendanceDate());
        int absent = 0;
        UUID sectionId = null;
        String sectionLabel = null;
        for (int i = 0; i < studentIds.size(); i++) {
            UUID studentId = studentIds.get(i);
            AttendanceStatus status = parseStatus(statuses.get(i));

            Student student = studentRepository.findByIdAndTenantId(studentId, command.tenantId())
                    .orElseThrow(() -> new IllegalArgumentException("Not authorized for student: " + studentId));

            // The rule, server-side. The dropdown is filtered above, but a
            // dropdown is not a permission: this endpoint took a list of
            // student ids and marked whoever was named.
            ClassSection theirSection = student.getClassSection();
            if (permitted != null
                    && (theirSection == null || !permitted.contains(theirSection.getId()))) {
                // Spring's own denial, not an IllegalArgumentException: the
                // controller catches those and redirects with a flash message,
                // which would turn a permission refusal into a tidy notice.
                // GlobalExceptionHandler rethrows this so the filter chain
                // answers 403, which is what a permission failure is.
                throw new org.springframework.security.access.AccessDeniedException(
                        "You are not assigned to " + (theirSection == null
                                ? "that class" : label(theirSection)) + ", so you cannot take its register.");
            }

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

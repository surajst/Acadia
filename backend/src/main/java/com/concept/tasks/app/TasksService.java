package com.concept.tasks.app;

import com.concept.common.GradeLevel;
import com.concept.common.NotificationDeliveryService;
import com.concept.shared.data.AcademicSubmission;
import com.concept.shared.data.AcademicSubmissionRepository;
import com.concept.shared.data.Attendance;
import com.concept.shared.data.AttendanceRepository;
import com.concept.shared.data.AttendanceStatus;
import com.concept.shared.data.ClassSection;
import com.concept.shared.data.ClassSectionRepository;
import com.concept.shared.data.Parent;
import com.concept.parent.data.ParentQuestRepository;
import com.concept.shared.data.Student;
import com.concept.shared.data.StudentRepository;
import com.concept.assignment.data.SubjectAssignment;
import com.concept.assignment.data.SubjectAssignmentRepository;
import com.concept.tasks.data.TaskType;
import com.concept.tasks.data.TeacherTask;
import com.concept.tasks.data.TeacherTaskRepository;
import com.concept.tasks.data.TeacherTaskRequest;
import com.concept.notification.app.NotificationPublisher;
import com.concept.tasks.app.TeacherTaskService;
import com.concept.user.CurrentUserService;
import com.concept.user.User;
import com.concept.user.UserRepository;
import com.concept.user.UserRole;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Application layer for teacher tasks, daily attendance, and the academic-XP
 * submission queue. Owns task creation, section-ownership checks for
 * attendance, absent-alert dispatch, and the student-facing task/attendance
 * reads — so the web controllers only bind and shape responses (ADR 0001).
 *
 * <p>Entity-shaped JSON responses (created task, task lists, pending
 * submissions) are returned as {@code Object}: the exact serialized shape is
 * preserved while the web layer keeps no static dependency on any entity.
 */
@Service
public class TasksService {

    private final TeacherTaskService teacherTaskService;
    private final StudentRepository studentRepository;
    private final ClassSectionRepository classSectionRepository;
    private final ParentQuestRepository parentQuestRepository;
    private final AttendanceRepository attendanceRepository;
    private final SubjectAssignmentRepository subjectAssignmentRepository;
    private final UserRepository userRepository;
    private final AcademicSubmissionRepository submissionRepository;
    private final TeacherTaskRepository teacherTaskRepository;
    private final NotificationPublisher notificationPublisher;
    private final NotificationDeliveryService notificationDeliveryService;
    private final CurrentUserService currentUserService;
    private final boolean devMode;

    public TasksService(TeacherTaskService teacherTaskService,
                        StudentRepository studentRepository,
                        ClassSectionRepository classSectionRepository,
                        ParentQuestRepository parentQuestRepository,
                        AttendanceRepository attendanceRepository,
                        SubjectAssignmentRepository subjectAssignmentRepository,
                        UserRepository userRepository,
                        AcademicSubmissionRepository submissionRepository,
                        TeacherTaskRepository teacherTaskRepository,
                        NotificationPublisher notificationPublisher,
                        NotificationDeliveryService notificationDeliveryService,
                        CurrentUserService currentUserService,
                        @Value("${app.dev-mode:false}") boolean devMode) {
        this.teacherTaskService = teacherTaskService;
        this.studentRepository = studentRepository;
        this.classSectionRepository = classSectionRepository;
        this.parentQuestRepository = parentQuestRepository;
        this.attendanceRepository = attendanceRepository;
        this.subjectAssignmentRepository = subjectAssignmentRepository;
        this.userRepository = userRepository;
        this.submissionRepository = submissionRepository;
        this.teacherTaskRepository = teacherTaskRepository;
        this.notificationPublisher = notificationPublisher;
        this.notificationDeliveryService = notificationDeliveryService;
        this.currentUserService = currentUserService;
        this.devMode = devMode;
    }

    // ─── Teacher tasks ──────────────────────────────────────────────────────

    public Object createTask(CreateTaskRequest request, Authentication authentication) {
        try {
            String username = authentication != null ? authentication.getName() : "teacher_1";
            UUID tenantId = currentUserService.getCurrentTenantId(authentication).orElse(null);
            UUID academicYearId = currentUserService.getCurrentAcademicYearId(authentication).orElse(null);
            TeacherTask created = teacherTaskService.createTask(
                    toManagementRequest(request), username, tenantId, academicYearId);
            notifyAssignedStudents(created, tenantId, academicYearId);
            return created;
        } catch (Exception e) {
            throw TasksException.badRequest(e.getMessage());
        }
    }

    /**
     * Tell the pupils a task was set for them.
     *
     * <p>Setting homework raised nothing before this, so the only way a child
     * found out was opening the app and looking. Resolved the same way
     * {@code getTasksForStudent} resolves the other direction: a class task goes
     * to everyone in that grade, a personal one to that pupil alone.
     */
    private void notifyAssignedStudents(TeacherTask task, UUID tenantId, UUID academicYearId) {
        if (task == null || tenantId == null) return;
        List<Student> recipients;
        if (Boolean.FALSE.equals(task.getAssignedToClass()) && task.getStudentId() != null) {
            recipients = studentRepository.findByIdAndTenantId(task.getStudentId(), tenantId)
                    .map(List::of).orElse(List.of());
        } else {
            recipients = studentRepository.findByTenantId(tenantId).stream()
                    .filter(s -> s.getClassSection() != null
                            && task.getStandard() != null
                            && task.getStandard() == GradeLevel.parse(s.getClassSection().getGradeName()))
                    .collect(Collectors.toList());
        }
        notificationPublisher.taskAssigned(recipients, tenantId, academicYearId,
                task.getId(), task.getTitle(), task.getSubjectCode());
    }

    public Object myTasks(Authentication authentication) {
        String username = authentication != null ? authentication.getName() : "teacher_1";
        UUID tenantId = currentUserService.getCurrentTenantId(authentication).orElse(null);
        return teacherTaskService.getTasksCreatedByTeacher(username, tenantId);
    }

    /**
     * The students a teacher can pick from when assigning a task, filtered by
     * a name fragment.
     *
     * <p>This used to invent a teacher id as
     * {@code UUID.nameUUIDFromBytes(email)} and look up
     * {@code ClassSection.teacherId} with it — a column nothing outside the
     * dev-mode seeders writes, and which that invented id could not have matched
     * regardless. The result was an empty list for every real teacher, so the
     * task-assignment autocomplete returned nothing at all. The subject
     * assignments below are the same link {@link #teacherOwnsSection} already
     * uses to gate the attendance register.
     */
    /**
     * The grades this caller can set a task for: the ones they are assigned to,
     * or every grade in the school for an admin or principal.
     *
     * <p>The form used to offer a fixed "Class 5" to "Class 10". A secondary
     * school running grades 6, 7, 11 and 12 was shown four classes it does not
     * have and missing two it does, and a task set against a grade with no
     * section reaches nobody.
     *
     * <p>The value stays the numeric standard the task is stored against; only
     * the list of them is now real. A grade whose name carries no number
     * (Nursery, LKG) cannot be expressed as a standard and is left out rather
     * than guessed at -- see the note on TeacherTask.standard.
     *
     * @return {value, label} pairs, ascending, never null
     */
    public List<Map<String, Object>> gradeOptionsForCaller(Authentication authentication) {
        UUID tenantId = currentUserService.getCurrentTenantId(authentication).orElse(null);
        if (tenantId == null) {
            return List.of();
        }
        User caller = currentUserService.getCurrentUser(authentication).orElse(null);
        if (caller == null) {
            return List.of();
        }

        boolean seesWholeSchool = caller.getRole() == UserRole.ADMIN || caller.getRole() == UserRole.PRINCIPAL;
        List<ClassSection> sections = seesWholeSchool
                ? classSectionRepository.findByTenantId(tenantId)
                : subjectAssignmentRepository.findByTeacher(caller).stream()
                        .map(SubjectAssignment::getClassSection)
                        .filter(sec -> sec != null && tenantId.equals(sec.getTenantId()))
                        .distinct()
                        .collect(Collectors.toList());

        Map<Integer, String> byStandard = new java.util.TreeMap<>();
        for (ClassSection section : sections) {
            Integer standard = standardOf(section.getGradeName());
            if (standard != null) {
                byStandard.putIfAbsent(standard, section.getGradeName().trim());
            }
        }
        return byStandard.entrySet().stream()
                .map(e -> Map.<String, Object>of("value", e.getKey(), "label", e.getValue()))
                .collect(Collectors.toList());
    }

    /** The digits in a grade name, or null when it has none. */
    private static Integer standardOf(String gradeName) {
        if (gradeName == null) {
            return null;
        }
        String digits = gradeName.replaceAll("[^0-9]", "");
        if (digits.isEmpty() || digits.length() > 2) {
            return null;
        }
        return Integer.valueOf(digits);
    }

    public List<Map<String, String>> searchMyStudents(String query, Authentication authentication) {
        String username = authentication != null ? authentication.getName() : null;
        User teacher = username == null ? null : userRepository.findByEmail(username).orElse(null);
        UUID tenantId = currentUserService.getCurrentTenantId(authentication).orElse(null);
        if (teacher == null || tenantId == null || !tenantId.equals(teacher.getTenantId())) {
            return List.of();
        }

        List<ClassSection> sections = subjectAssignmentRepository.findByTeacher(teacher).stream()
                .map(SubjectAssignment::getClassSection)
                .filter(s -> s != null && tenantId.equals(s.getTenantId()))
                .distinct()
                .collect(Collectors.toList());
        List<Student> students = sections.isEmpty()
                ? List.of()
                : studentRepository.findByClassSectionIn(sections);

        String lowerQuery = query.toLowerCase();
        return students.stream()
                .filter(s -> (s.getFirstName() + " " + s.getLastName()).toLowerCase().contains(lowerQuery))
                .map(s -> {
                    String className = s.getClassSection() != null
                            ? s.getClassSection().getGradeName() + " - " + s.getClassSection().getSectionName()
                            : "Unknown";
                    return Map.of(
                            "id", s.getId().toString(),
                            "name", s.getFirstName() + " " + s.getLastName(),
                            "className", className);
                })
                .collect(Collectors.toList());
    }

    /** Just the one section's roster, for the attendance sheet -- not the teacher's whole caseload. */
    public List<Map<String, String>> rosterForSection(UUID sectionId, Authentication authentication) {
        User teacher = userRepository.findByEmail(authentication.getName()).orElse(null);
        ClassSection section = teacher != null
                ? classSectionRepository.findByIdAndTenantId(sectionId, teacher.getTenantId()).orElse(null)
                : null;
        if (teacher == null || section == null || !teacherOwnsSection(teacher, section)) {
            throw TasksException.badRequest("Section not found");
        }
        String className = section.getGradeName() + " - " + section.getSectionName();
        return studentRepository.findByClassSectionIn(List.of(section)).stream()
                .map(s -> Map.of(
                        "id", s.getId().toString(),
                        "name", s.getFirstName() + " " + s.getLastName(),
                        "className", className))
                .collect(Collectors.toList());
    }

    public Object testStudents() {
        if (!devMode) {
            throw TasksException.forbidden("Disabled in production");
        }
        List<Map<String, Object>> sectionData = classSectionRepository.findAll().stream().map(s -> {
            Map<String, Object> m = new HashMap<>();
            m.put("id", s.getId());
            m.put("teacherId", s.getTeacherId());
            m.put("tenantId", s.getTenantId());
            return m;
        }).collect(Collectors.toList());

        List<Map<String, Object>> studentData = studentRepository.findAll().stream().map(s -> {
            Map<String, Object> m = new HashMap<>();
            m.put("id", s.getId());
            m.put("name", s.getFirstName());
            m.put("classId", s.getClassSection() != null ? s.getClassSection().getId() : null);
            return m;
        }).collect(Collectors.toList());

        List<Map<String, Object>> quests = parentQuestRepository.findAll().stream().map(q -> {
            Map<String, Object> m = new HashMap<>();
            m.put("id", q.getId());
            m.put("studentId", q.getStudent().getId());
            m.put("desc", q.getTaskDescription());
            return m;
        }).collect(Collectors.toList());

        return Map.of("sections", sectionData, "students", studentData, "quests", quests);
    }

    // ─── Student reads ──────────────────────────────────────────────────────

    public List<Map<String, Object>> studentAttendance(Authentication authentication) {
        Student student = requireStudent(authentication);
        LocalDate start = LocalDate.now().minusDays(60);
        LocalDate end = LocalDate.now();
        return attendanceRepository.findByStudentAndAttendanceDateBetween(student, start, end).stream()
                .sorted((a, b) -> b.getAttendanceDate().compareTo(a.getAttendanceDate()))
                .map(a -> Map.<String, Object>of(
                        "date", a.getAttendanceDate().toString(),
                        "status", a.getStatus().name(),
                        "dayOfWeek", a.getAttendanceDate().getDayOfWeek().toString()))
                .collect(Collectors.toList());
    }

    public Object studentTasks(Authentication authentication) {
        Student student = requireStudent(authentication);
        return teacherTaskService.getTasksForStudent(student.getId(), extractStandard(student), student.getTenantId());
    }

    public Object taskQuestions(UUID taskId, Authentication authentication) {
        Student student = requireStudent(authentication);
        return teacherTaskService.getQuestionsForTask(taskId, student.getTenantId());
    }

    // ─── Attendance (teacher) ───────────────────────────────────────────────

    public List<Map<String, Object>> todayAttendance(UUID sectionId, Authentication authentication) {
        User teacher = userRepository.findByEmail(authentication.getName()).orElse(null);
        ClassSection section = teacher != null
                ? classSectionRepository.findByIdAndTenantId(sectionId, teacher.getTenantId()).orElse(null)
                : null;
        if (teacher == null || section == null || !teacherOwnsSection(teacher, section)) {
            // Same message whether the section doesn't exist or isn't the caller's.
            throw TasksException.badRequest("Section not found");
        }
        List<Attendance> records = attendanceRepository.findByClassSectionAndAttendanceDate(section, LocalDate.now());
        return records.stream().map(a -> {
            Map<String, Object> m = new HashMap<>();
            m.put("studentId", a.getStudent().getId());
            m.put("studentName", a.getStudent().getFirstName() + " " + a.getStudent().getLastName());
            m.put("status", a.getStatus());
            m.put("remarks", a.getRemarks());
            return m;
        }).collect(Collectors.toList());
    }

    @Transactional
    public Map<String, Object> submitAttendance(AttendancePayload payload, Authentication authentication) {
        if (payload == null || payload.attendance() == null || payload.attendance().isEmpty()) {
            throw TasksException.badRequest("Attendance payload is required");
        }
        User teacher = userRepository.findByEmail(authentication.getName()).orElse(null);
        if (teacher == null) {
            throw TasksException.forbidden("Teacher not found");
        }
        LocalDate today = LocalDate.now();
        LocalDate date = attendanceDate(payload, today);
        int saved = 0;
        int skipped = 0;

        for (AttendancePayload.AttendanceEntry entry : payload.attendance()) {
            Student student = studentRepository.findByIdAndTenantId(entry.studentId(), teacher.getTenantId()).orElse(null);
            if (student == null) { skipped++; continue; }
            ClassSection section = student.getClassSection();
            if (section == null || !teacherOwnsSection(teacher, section)) { skipped++; continue; }

            List<Attendance> existing = attendanceRepository
                    .findByClassSectionAndAttendanceDate(section, date).stream()
                    .filter(a -> a.getStudent().getId().equals(entry.studentId()))
                    .collect(Collectors.toList());
            if (!existing.isEmpty()) {
                attendanceRepository.deleteAll(existing);
            }

            Attendance attendance = new Attendance();
            attendance.setId(UUID.randomUUID());
            attendance.setTenantId(student.getTenantId());
            attendance.setAcademicYearId(student.getAcademicYearId());
            attendance.setStudent(student);
            attendance.setClassSection(section);
            attendance.setAttendanceDate(date);
            attendance.setStatus(entry.status());
            attendance.setRemarks(entry.remarks() != null ? entry.remarks() : "");
            attendanceRepository.save(attendance);
            saved++;

            // Only today's absences raise an alert. Telling a parent their child
            // "was marked ABSENT today" while a teacher tidies up last week's
            // register would be a false alarm.
            if (entry.status() == AttendanceStatus.ABSENT && date.equals(today)) {
                for (Parent parent : student.getParents()) {
                    notificationDeliveryService.send(parent.getPhoneNumber(),
                            "[ALERT WHATSAPP DISPATCH] Sending to "
                                    + parent.getFirstName() + " " + parent.getLastName()
                                    + " (" + parent.getPhoneNumber() + "): Alert! Student "
                                    + student.getFirstName() + " was marked ABSENT today.");
                }
            }
        }
        return Map.of("status", "success", "saved", saved, "skipped", skipped,
                "date", date.toString());
    }

    /** How far back a teacher may correct the register. */
    private static final int BACKFILL_WINDOW_DAYS = 30;

    /**
     * The date to write, defaulting to today when the client sends none.
     *
     * <p>Backfill is bounded in both directions. The future is refused outright
     * — marking a child present for a day that has not happened is never a
     * correction. The past is capped at {@link #BACKFILL_WINDOW_DAYS} so a
     * mis-sent date cannot silently rewrite last term's register, which is the
     * record a school's attendance reporting is built on.
     */
    private LocalDate attendanceDate(AttendancePayload payload, LocalDate today) {
        LocalDate requested = payload.date();
        if (requested == null) {
            return today;
        }
        if (requested.isAfter(today)) {
            throw TasksException.badRequest("Attendance cannot be marked for a future date");
        }
        if (requested.isBefore(today.minusDays(BACKFILL_WINDOW_DAYS))) {
            throw TasksException.badRequest(
                    "Attendance can only be corrected within the last " + BACKFILL_WINDOW_DAYS + " days");
        }
        return requested;
    }

    // ─── Academic-XP submission queue ───────────────────────────────────────

    /**
     * A pupil hands in a task their teacher set.
     *
     * <p>Nothing called this before: the app could list tasks but had no way to
     * open or submit one, so a child saw homework they could not hand in. It
     * also took the XP to award as a request parameter, which would have let
     * the first caller award itself any number it liked — the reward is read
     * off the task here instead, along with the title, so the only thing the
     * pupil supplies is their own work.
     */
    @Transactional
    public Map<String, Object> submitTaskForCurrentStudent(UUID teacherTaskId, String notes,
                                                           List<String> answers,
                                                           Authentication authentication) {
        Student student = currentUserService.getCurrentStudent(authentication)
                .orElseThrow(() -> TasksException.forbidden("No student record for this account"));
        if (teacherTaskId == null) {
            throw TasksException.badRequest("A task is required");
        }
        TeacherTask task = teacherTaskRepository
                .findByIdAndTenantId(teacherTaskId, student.getTenantId())
                .orElseThrow(() -> TasksException.badRequest("Task not found"));

        // One pending hand-in per task: re-submitting replaces the previous
        // attempt rather than queueing a second copy for the teacher to review.
        submissionRepository
                .findByStudentIdAndTeacherTaskId(student.getId(), teacherTaskId).stream()
                .filter(s -> "PENDING".equals(s.getStatus()))
                .forEach(submissionRepository::delete);

        AcademicSubmission submission =
                new AcademicSubmission(student.getId(), task.getTitle(), task.getXpReward());
        submission.setTeacherTaskId(teacherTaskId);
        submission.setProofOfWorkNotes(notes);
        List<String> given = answers == null ? List.of() : answers;
        if (given.size() > 0) submission.setAnswer1(given.get(0));
        if (given.size() > 1) submission.setAnswer2(given.get(1));
        if (given.size() > 2) submission.setAnswer3(given.get(2));
        submissionRepository.save(submission);

        return Map.of("status", "submitted",
                "taskId", teacherTaskId,
                "xpAwaiting", task.getXpReward() == null ? 0 : task.getXpReward());
    }

    /**
     * The review queue for one school. Scoped by tenant because this is a
     * listing: an unscoped findByStatus returns every school's pending
     * submissions to whichever teacher happens to ask, with no id to guess.
     */
    public Object pendingSubmissions(UUID tenantId) {
        return submissionRepository.findByStatusAndStudentTenantId("PENDING", tenantId);
    }

    @Transactional
    public String approveXp(UUID submissionId, UUID tenantId) {
        // Resolved through the caller's tenant, so an id belonging to another
        // school reads as "not found" rather than being approved.
        AcademicSubmission submission = submissionRepository.findByIdAndStudentTenantId(submissionId, tenantId)
                .orElseThrow(() -> TasksException.notFound(""));
        if (!"PENDING".equals(submission.getStatus())) {
            throw TasksException.badRequest("This task has already been processed.");
        }
        submission.setStatus("APPROVED");
        submissionRepository.save(submission);
        return "XP approved and allocated successfully!";
    }

    // ─── helpers ────────────────────────────────────────────────────────────

    private boolean teacherOwnsSection(User teacher, ClassSection section) {
        return subjectAssignmentRepository.existsByTeacherAndClassSection(teacher, section);
    }

    private Student requireStudent(Authentication authentication) {
        return currentUserService.getCurrentStudent(authentication)
                .orElseThrow(() -> TasksException.badRequest("Student record not found"));
    }

    private int extractStandard(Student student) {
        if (student.getClassSection() == null) {
            return GradeLevel.UNKNOWN;
        }
        return GradeLevel.parse(student.getClassSection().getGradeName());
    }

    private TeacherTaskRequest toManagementRequest(CreateTaskRequest req) {
        TeacherTaskRequest tr = new TeacherTaskRequest();
        tr.setTitle(req.getTitle());
        tr.setDescription(req.getDescription());
        tr.setSubjectCode(req.getSubjectCode());
        if (req.getTaskType() != null && !req.getTaskType().isBlank()) {
            tr.setTaskType(TaskType.valueOf(req.getTaskType()));
        }
        tr.setStandard(req.getStandard());
        tr.setAssignedToClass(req.getAssignedToClass());
        tr.setStudentId(req.getStudentId());
        tr.setXpReward(req.getXpReward());
        tr.setDueDate(req.getDueDate());
        tr.setQuestion1(req.getQuestion1());
        tr.setQuestion2(req.getQuestion2());
        tr.setQuestion3(req.getQuestion3());
        return tr;
    }
}

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
        validateTaskTarget(request, authentication);
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
        } else if (task.getClassSectionId() != null) {
            // The section, so a 6-B family is not told about 6-A's homework.
            recipients = studentRepository.findByTenantId(tenantId).stream()
                    .filter(s -> s.getClassSection() != null
                            && task.getClassSectionId().equals(s.getClassSection().getId()))
                    .collect(Collectors.toList());
        } else {
            // No section recorded: a legacy grade-wide task, matching what
            // getTasksForStudent shows for the same row.
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

    /**
     * The sections this caller can set a task for.
     *
     * <p>Replaces the grade list on the task form. A grade is not narrow enough
     * to say who a task is for: a task set against "Grade 6" reached 6-A and
     * 6-B alike, and Priya, who teaches only 6-A, could set work that landed on
     * Neha's list. The standard the task is stored against is derived from the
     * section server-side, so the form no longer sends it at all.
     *
     * <p>Every section, including one whose grade name carries no number --
     * unlike the grade list, which has to drop those because a standard cannot
     * express "Nursery". That is the other reason to key on the section.
     *
     * @return {value, label} pairs sorted by label, never null
     */
    public List<Map<String, Object>> sectionOptionsForCaller(Authentication authentication) {
        UUID tenantId = currentUserService.getCurrentTenantId(authentication).orElse(null);
        User caller = tenantId == null ? null
                : currentUserService.getCurrentUser(authentication).orElse(null);
        if (caller == null || caller.getRole() == null) {
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

        return sections.stream()
                .map(sec -> Map.<String, Object>of(
                        "value", sec.getId(),
                        "label", (sec.getGradeName() + " - " + sec.getSectionName()).trim()))
                .sorted(java.util.Comparator.comparing(m -> String.valueOf(m.get("label"))))
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
        // The section, not just the grade: a class task set for 6-A used to
        // appear on every 6-B child's list, because the grade was all the
        // student's tasks were ever matched on.
        return teacherTaskService.getTasksForStudent(student.getId(), extractStandard(student),
                student.getClassSection() != null ? student.getClassSection().getId() : null,
                student.getTenantId());
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
                    if (!com.concept.roster.app.PhoneNumbers.isValid(parent.getPhoneNumber())) {
                        continue;
                    }
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

        // Closing a task has to stop hand-ins, not just hide it from the list.
        // A pupil who still had the sheet open could otherwise submit against a
        // task their teacher had retired, and the XP would sit in the queue.
        if ("CLOSED".equals(task.getTaskStatus())) {
            throw TasksException.badRequest("Your teacher has closed this task, so it can no longer be handed in.");
        }

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

    // --- Managing a task after it is set ------------------------------------

    /**
     * A task the caller is allowed to manage, or a refusal.
     *
     * <p>Owned by whoever set it, with ADMIN and PRINCIPAL able to reach any of
     * them -- somebody has to be able to clear up after a teacher who has left.
     * The task is resolved through the caller's own tenant first, so an id from
     * another school reads as "not found" rather than being edited.
     */
    private TeacherTask manageableTask(UUID taskId, Authentication authentication, UUID tenantId) {
        if (taskId == null) {
            throw TasksException.badRequest("A task is required");
        }
        TeacherTask task = teacherTaskRepository.findByIdAndTenantId(taskId, tenantId)
                .orElseThrow(() -> TasksException.notFound("That task was not found."));

        User caller = currentUserService.getCurrentUser(authentication).orElse(null);
        boolean unrestricted = caller != null && caller.getRole() != null
                && (caller.getRole() == UserRole.ADMIN || caller.getRole() == UserRole.PRINCIPAL);
        if (unrestricted) {
            return task;
        }

        String username = authentication != null ? authentication.getName() : null;
        UUID callerTaskId = teacherTaskService.resolveTeacherId(username);
        if (!callerTaskId.equals(task.getCreatedByTeacherId())) {
            throw new org.springframework.security.access.AccessDeniedException(
                    "That task was set by another teacher, so it is not yours to change.");
        }
        return task;
    }

    /**
     * Change what a task says.
     *
     * <p>The list of tasks was read-only: a typo in a title, a due date a
     * teacher wanted to move, or an XP reward set wrong stayed that way for the
     * life of the task, and children were working from it.
     *
     * <p>Who the task is for is deliberately not editable. Moving a task to
     * another section or another child after work has been handed in leaves
     * submissions attached to a task those pupils can no longer see; setting a
     * new task is the honest way to do that.
     */
    @Transactional
    public Object updateTask(UUID taskId, String title, String description,
                             java.time.LocalDate dueDate, Integer xpReward,
                             Authentication authentication) {
        UUID tenantId = currentUserService.getCurrentTenantId(authentication).orElse(null);
        TeacherTask task = manageableTask(taskId, authentication, tenantId);

        if (title == null || title.isBlank()) {
            throw TasksException.badRequest("Give the task a title.");
        }
        // The same bounds the create path enforces. A rule that holds only on
        // the way in is not a rule: -10 XP could simply be edited back in.
        if (xpReward == null || xpReward < 1) {
            throw TasksException.badRequest("A task has to be worth at least 1 XP.");
        }
        if (xpReward > CreateTaskRequest.MAX_XP_REWARD) {
            throw TasksException.badRequest(
                    "A task cannot be worth more than " + CreateTaskRequest.MAX_XP_REWARD + " XP.");
        }

        task.setTitle(title.trim());
        task.setDescription(description);
        task.setDueDate(dueDate);
        task.setXpReward(xpReward);
        teacherTaskRepository.save(task);

        return Map.of("status", "updated", "id", task.getId(), "title", task.getTitle());
    }

    /**
     * Retire a task without deleting it.
     *
     * <p>There was no way to do this, so a finished or mistaken task stayed on
     * every child's list indefinitely. A closed task drops off the student list
     * (which shows ACTIVE and OVERDUE only) and stops accepting hand-ins, while
     * the submissions already made stay on record.
     */
    @Transactional
    public Object closeTask(UUID taskId, Authentication authentication) {
        UUID tenantId = currentUserService.getCurrentTenantId(authentication).orElse(null);
        TeacherTask task = manageableTask(taskId, authentication, tenantId);
        if ("CLOSED".equals(task.getTaskStatus())) {
            throw TasksException.badRequest("That task is already closed.");
        }
        task.setTaskStatus("CLOSED");
        teacherTaskRepository.save(task);
        return Map.of("status", "closed", "id", task.getId());
    }

    /** Closing by mistake should not be one-way. */
    @Transactional
    public Object reopenTask(UUID taskId, Authentication authentication) {
        UUID tenantId = currentUserService.getCurrentTenantId(authentication).orElse(null);
        TeacherTask task = manageableTask(taskId, authentication, tenantId);
        if (!"CLOSED".equals(task.getTaskStatus())) {
            throw TasksException.badRequest("That task is not closed.");
        }
        // Back to ACTIVE, not OVERDUE: getTasksForStudent re-derives overdue from
        // the due date on the next read, so setting it here would be a guess the
        // read then corrects anyway.
        task.setTaskStatus("ACTIVE");
        teacherTaskRepository.save(task);
        return Map.of("status", "reopened", "id", task.getId());
    }

    /**
     * Remove a task entirely, but only while nothing has been handed in.
     *
     * <p>Deleting a task with submissions against it would leave those rows
     * pointing at a task that no longer exists -- and they carry XP a child has
     * been told they earned. That case is a close, and the refusal says so
     * rather than just failing.
     */
    @Transactional
    public Object deleteTask(UUID taskId, Authentication authentication) {
        UUID tenantId = currentUserService.getCurrentTenantId(authentication).orElse(null);
        TeacherTask task = manageableTask(taskId, authentication, tenantId);

        int handedIn = submissionRepository
                .findByTeacherTaskIdAndStudentTenantId(taskId, tenantId).size();
        if (handedIn > 0) {
            throw TasksException.badRequest(
                    handedIn + (handedIn == 1 ? " pupil has" : " pupils have")
                            + " already handed this in, so it cannot be deleted. Close it instead -- "
                            + "it comes off their lists and their work stays on record.");
        }

        teacherTaskRepository.delete(task);
        return Map.of("status", "deleted", "id", taskId);
    }

    /**
     * Who has handed this task in, and who has not.
     *
     * <p>A teacher could set work and then had no way to see who had done it --
     * the only review surface was one undifferentiated pending queue for the
     * whole school. This answers the question actually asked of a task: which of
     * my pupils is still outstanding.
     *
     * <p>The roster is the task's own section, so it lists the children the task
     * was set for rather than everybody in the grade.
     */
    public Object taskSubmissions(UUID taskId, Authentication authentication) {
        UUID tenantId = currentUserService.getCurrentTenantId(authentication).orElse(null);
        TeacherTask task = manageableTask(taskId, authentication, tenantId);

        List<AcademicSubmission> submissions =
                submissionRepository.findByTeacherTaskIdAndStudentTenantId(taskId, tenantId);
        java.util.Map<UUID, AcademicSubmission> byStudent = new java.util.LinkedHashMap<>();
        for (AcademicSubmission sub : submissions) {
            byStudent.put(sub.getStudentId(), sub);
        }

        List<Student> roster;
        if (Boolean.FALSE.equals(task.getAssignedToClass()) && task.getStudentId() != null) {
            roster = studentRepository.findByIdAndTenantId(task.getStudentId(), tenantId)
                    .map(List::of).orElse(List.of());
        } else if (task.getClassSectionId() != null) {
            roster = studentRepository.findByTenantId(tenantId).stream()
                    .filter(st -> st.getClassSection() != null
                            && task.getClassSectionId().equals(st.getClassSection().getId()))
                    .collect(Collectors.toList());
        } else {
            // A task raised before sections were recorded still reaches the
            // grade, so its roster is the grade -- the same rule the student list
            // applies, or these counts would not match what pupils see.
            roster = studentRepository.findByTenantId(tenantId).stream()
                    .filter(st -> st.getClassSection() != null && task.getStandard() != null
                            && task.getStandard() == GradeLevel.parse(st.getClassSection().getGradeName()))
                    .collect(Collectors.toList());
        }

        List<Map<String, Object>> rows = roster.stream()
                .map(st -> {
                    AcademicSubmission sub = byStudent.get(st.getId());
                    Map<String, Object> row = new java.util.LinkedHashMap<>();
                    row.put("studentName", (st.getFirstName() + " " + st.getLastName()).trim());
                    row.put("rollNumber", st.getRollNumber() == null ? "--" : st.getRollNumber());
                    row.put("handedIn", sub != null);
                    row.put("status", sub == null ? "NOT_SUBMITTED" : sub.getStatus());
                    row.put("notes", sub == null ? null : sub.getProofOfWorkNotes());
                    // The submission id, not the student's: the review action
                    // needs this one, and a student id on the page would be a
                    // UUID with nothing to do.
                    row.put("submissionId", sub == null ? null : sub.getId());
                    return row;
                })
                .collect(Collectors.toList());

        Map<String, Object> out = new java.util.LinkedHashMap<>();
        out.put("taskId", task.getId());
        out.put("title", task.getTitle());
        out.put("taskStatus", task.getTaskStatus());
        out.put("expected", rows.size());
        out.put("handedIn", rows.stream().filter(r -> Boolean.TRUE.equals(r.get("handedIn"))).count());
        out.put("rows", rows);
        return out;
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

    /**
     * Which class a task is being set for, checked before anything is written.
     *
     * <p>A task carried a numeric standard and nothing narrower, so one Priya set
     * for 6-A appeared on every 6-B child's list: they could hand in work their
     * teacher never set them, and Priya saw submissions from children she does
     * not teach. A section is now required on a class task, and it has to be one
     * the caller actually teaches -- an id a client can put in the body is not a
     * permission.
     *
     * <p>Thrown outside the try below on purpose. That block turns every
     * exception into TasksException.badRequest with the original message, which
     * would flatten a permission refusal into the same 400 as a typo.
     */
    private void validateTaskTarget(CreateTaskRequest request, Authentication authentication) {
        if (request == null) {
            throw TasksException.badRequest("No task was submitted.");
        }
        boolean forWholeClass = !Boolean.FALSE.equals(request.getAssignedToClass());
        if (!forWholeClass) {
            return; // a task for one named student is scoped by that student
        }

        UUID tenantId = currentUserService.getCurrentTenantId(authentication).orElse(null);
        if (request.getClassSectionId() == null) {
            throw TasksException.badRequest(
                    "Choose which class this task is for. Without a section it would go to every "
                            + "section of the grade.");
        }

        ClassSection section = tenantId == null ? null
                : classSectionRepository.findByIdAndTenantId(request.getClassSectionId(), tenantId).orElse(null);
        if (section == null) {
            throw TasksException.badRequest("That class was not found.");
        }

        User caller = currentUserService.getCurrentUser(authentication).orElse(null);
        boolean unrestricted = caller != null && caller.getRole() != null
                && (caller.getRole() == UserRole.ADMIN || caller.getRole() == UserRole.PRINCIPAL);
        if (!unrestricted && (caller == null || !teacherOwnsSection(caller, section))) {
            throw new org.springframework.security.access.AccessDeniedException(
                    "You are not assigned to " + section.getGradeName() + " - " + section.getSectionName()
                            + ", so you cannot set work for it.");
        }

        // The standard is what the task is stored against and what a student is
        // matched on, so it has to agree with the section or the task reaches
        // nobody. Derived rather than trusted: the client sends both.
        Integer fromSection = GradeLevel.parse(section.getGradeName());
        if (fromSection != null && fromSection != GradeLevel.UNKNOWN) {
            request.setStandard(fromSection);
        }
    }

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
        tr.setClassSectionId(req.getClassSectionId());
        tr.setStudentId(req.getStudentId());
        tr.setXpReward(req.getXpReward());
        tr.setDueDate(req.getDueDate());
        tr.setQuestion1(req.getQuestion1());
        tr.setQuestion2(req.getQuestion2());
        tr.setQuestion3(req.getQuestion3());
        return tr;
    }
}

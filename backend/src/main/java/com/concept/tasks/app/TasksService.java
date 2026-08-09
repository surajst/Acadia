package com.concept.tasks.app;

import com.concept.common.NotificationDeliveryService;
import com.concept.management.AcademicSubmission;
import com.concept.management.AcademicSubmissionRepository;
import com.concept.management.Attendance;
import com.concept.management.AttendanceRepository;
import com.concept.management.AttendanceStatus;
import com.concept.management.ClassSection;
import com.concept.management.ClassSectionRepository;
import com.concept.management.Parent;
import com.concept.management.ParentQuestRepository;
import com.concept.management.Student;
import com.concept.management.StudentRepository;
import com.concept.management.SubjectAssignmentRepository;
import com.concept.management.TaskType;
import com.concept.management.TeacherTaskRequest;
import com.concept.management.TeacherTaskService;
import com.concept.user.CurrentUserService;
import com.concept.user.User;
import com.concept.user.UserRepository;
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
            return teacherTaskService.createTask(toManagementRequest(request), username, tenantId, academicYearId);
        } catch (Exception e) {
            throw TasksException.badRequest(e.getMessage());
        }
    }

    public Object myTasks(Authentication authentication) {
        String username = authentication != null ? authentication.getName() : "teacher_1";
        UUID tenantId = currentUserService.getCurrentTenantId(authentication).orElse(null);
        return teacherTaskService.getTasksCreatedByTeacher(username, tenantId);
    }

    public List<Map<String, String>> searchMyStudents(String query, Authentication authentication) {
        String username = authentication != null ? authentication.getName() : "teacher_1";
        if ("teacher@greenwood.com".equalsIgnoreCase(username)) {
            username = "teacher_1";
        }
        UUID teacherId = UUID.nameUUIDFromBytes(username.getBytes());
        UUID tenantId = currentUserService.getCurrentTenantId(authentication).orElse(null);

        List<ClassSection> sections = (tenantId != null)
                ? classSectionRepository.findByTeacherIdAndTenantId(teacherId, tenantId)
                : List.of();
        List<Student> students = studentRepository.findByClassSectionIn(sections);

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

    public Object taskQuestions(UUID taskId) {
        return teacherTaskService.getQuestionsForTask(taskId);
    }

    // ─── Attendance (teacher) ───────────────────────────────────────────────

    public List<Map<String, Object>> todayAttendance(UUID sectionId, Authentication authentication) {
        User teacher = userRepository.findByEmail(authentication.getName()).orElse(null);
        ClassSection section = classSectionRepository.findById(sectionId).orElse(null);
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
        int saved = 0;
        int skipped = 0;

        for (AttendancePayload.AttendanceEntry entry : payload.attendance()) {
            Student student = studentRepository.findById(entry.studentId()).orElse(null);
            if (student == null) { skipped++; continue; }
            ClassSection section = student.getClassSection();
            if (section == null || !teacherOwnsSection(teacher, section)) { skipped++; continue; }

            List<Attendance> existing = attendanceRepository
                    .findByClassSectionAndAttendanceDate(section, today).stream()
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
            attendance.setAttendanceDate(today);
            attendance.setStatus(entry.status());
            attendance.setRemarks(entry.remarks() != null ? entry.remarks() : "");
            attendanceRepository.save(attendance);
            saved++;

            if (entry.status() == AttendanceStatus.ABSENT) {
                for (Parent parent : student.getParents()) {
                    notificationDeliveryService.send(parent.getPhoneNumber(),
                            "[ALERT WHATSAPP DISPATCH] Sending to "
                                    + parent.getFirstName() + " " + parent.getLastName()
                                    + " (" + parent.getPhoneNumber() + "): Alert! Student "
                                    + student.getFirstName() + " was marked ABSENT today.");
                }
            }
        }
        return Map.of("status", "success", "saved", saved, "skipped", skipped);
    }

    // ─── Academic-XP submission queue ───────────────────────────────────────

    @Transactional
    public void submitAcademicTask(UUID studentId, String skillName, Integer xpBounty, Authentication authentication) {
        UUID ownStudentId = currentUserService.getCurrentStudent(authentication).map(Student::getId).orElse(null);
        if (ownStudentId == null || !ownStudentId.equals(studentId)) {
            throw TasksException.forbidden("Error: Not authorized for this student.");
        }
        submissionRepository.save(new AcademicSubmission(studentId, skillName, xpBounty));
    }

    public Object pendingSubmissions() {
        return submissionRepository.findByStatus("PENDING");
    }

    @Transactional
    public String approveXp(UUID submissionId) {
        AcademicSubmission submission = submissionRepository.findById(submissionId)
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
        try {
            String grade = student.getClassSection().getGradeName();
            return Integer.parseInt(grade.replaceAll("[^0-9]", ""));
        } catch (Exception e) {
            return 6;
        }
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

package com.concept.tasks;

import com.concept.shared.data.AcademicSubmissionRepository;
import com.concept.assignment.app.SubjectAssignmentService;
import com.concept.shared.data.ClassSection;
import com.concept.shared.data.ClassSectionRepository;
import com.concept.shared.data.Student;
import com.concept.shared.data.StudentRepository;
import com.concept.tasks.app.CreateTaskRequest;
import com.concept.tasks.app.TasksService;
import com.concept.teacher.app.TeacherDashboardService;
import com.concept.tenant.AcademicYear;
import com.concept.tenant.AcademicYearRepository;
import com.concept.tenant.Tenant;
import com.concept.tenant.TenantRepository;
import com.concept.user.User;
import com.concept.user.UserRepository;
import com.concept.user.UserRole;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * "Hand in appears not to work."
 *
 * <p>The reported symptoms were both in the sheet rather than the server: the
 * header blanked to " · + XP" because the task was cleared while the modal was
 * still visible, and the confirmation was an Alert, which does nothing at all
 * on React Native Web -- so on the build being tested, a successful hand-in
 * looked exactly like nothing happening.
 *
 * <p>This pins the half the screen cannot: that the submission is recorded and
 * that it reaches the teacher's verification queue. If this passes and a child
 * still reports nothing happening, the fault is in the sheet, not the round
 * trip -- which is worth being able to tell apart.
 */
@SpringBootTest
@TestPropertySource(properties = "app.dev-mode=true")
@Transactional
class StudentHandInTest {

    @Autowired private TasksService tasksService;
    @Autowired private TeacherDashboardService teacherDashboardService;
    @Autowired private AcademicSubmissionRepository submissionRepository;
    @Autowired private SubjectAssignmentService assignmentService;
    @Autowired private StudentRepository studentRepository;
    @Autowired private ClassSectionRepository classSectionRepository;
    @Autowired private TenantRepository tenantRepository;
    @Autowired private AcademicYearRepository academicYearRepository;
    @Autowired private UserRepository userRepository;

    private UUID tenantId;
    private UUID yearId;
    /** A class task now names the section it is for, so the test has to keep it. */
    private UUID sixAId;
    private Student aarav;
    private Authentication teacher;
    private Authentication student;

    @BeforeEach
    void setup() {
        Tenant tenant = new Tenant();
        tenant.setId(UUID.randomUUID());
        tenant.setName("Demo SSC");
        tenant.setSubdomain("hand-" + UUID.randomUUID());
        tenant.setActive(true);
        tenant.setCreatedAt(Instant.now());
        tenantId = tenantRepository.saveAndFlush(tenant).getId();

        AcademicYear year = new AcademicYear();
        year.setId(UUID.randomUUID());
        year.setTenantId(tenantId);
        year.setName("2026-27");
        year.setStartDate(LocalDate.now().minusMonths(3));
        year.setEndDate(LocalDate.now().plusMonths(9));
        year.setCurrent(true);
        yearId = academicYearRepository.saveAndFlush(year).getId();

        ClassSection section = new ClassSection();
        section.setId(UUID.randomUUID());
        section.setTenantId(tenantId);
        section.setAcademicYearId(yearId);
        section.setGradeName("Grade 6");
        section.setSectionName("A");
        section = classSectionRepository.saveAndFlush(section);
        sixAId = section.getId();

        User priya = user("priya.hand@example.com", "Priya Demo", UserRole.TEACHER);
        assignmentService.assignSubject(priya.getId(), section.getId(), "Mathematics", true, tenantId);
        teacher = auth(priya, "TEACHER");

        User aaravUser = user("aarav6a-01@demossc", "Aarav Verma", UserRole.STUDENT);
        aarav = new Student();
        aarav.setId(UUID.randomUUID());
        aarav.setTenantId(tenantId);
        aarav.setAcademicYearId(yearId);
        aarav.setFirstName("Aarav");
        aarav.setLastName("Verma");
        aarav.setClassSection(section);
        aarav.setUserId(aaravUser.getId());
        aarav = studentRepository.saveAndFlush(aarav);
        student = auth(aaravUser, "STUDENT");
    }

    private User user(String email, String name, UserRole role) {
        User u = new User();
        u.setId(UUID.randomUUID());
        u.setTenantId(tenantId);
        u.setAcademicYearId(yearId);
        u.setEmail(email);
        u.setPasswordHash("irrelevant");
        u.setFullName(name);
        u.setRole(role);
        return userRepository.saveAndFlush(u);
    }

    private Authentication auth(User u, String role) {
        return new UsernamePasswordAuthenticationToken(u.getEmail(), null,
                List.of(new SimpleGrantedAuthority("ROLE_" + role)));
    }

    private UUID createTask() {
        CreateTaskRequest r = new CreateTaskRequest();
        r.setTitle("QA Fractions worksheet");
        r.setDescription("Practice sheet");
        r.setSubjectCode("MATH");
        r.setTaskType("HOMEWORK");
        r.setStandard(6);
        r.setAssignedToClass(true);
        // Which section it is for. Without this the task would reach every
        // section of Grade 6, which is what TaskSectionScopeTest covers.
        r.setClassSectionId(sixAId);
        r.setXpReward(10);
        r.setDueDate(LocalDate.now().plusDays(6));
        // createTask returns the TeacherTask entity, not a map.
        com.concept.tasks.data.TeacherTask created =
                (com.concept.tasks.data.TeacherTask) tasksService.createTask(r, teacher);
        assertNotNull(created.getId(), "the task must come back with an id");
        return created.getId();
    }

    @Test
    void handingInRecordsTheSubmission() {
        UUID taskId = createTask();

        Map<String, Object> result = tasksService.submitTaskForCurrentStudent(
                taskId, "Did questions 1 to 5", List.of("4/8", "1/2"), student);

        assertNotNull(result, "the hand-in must answer with something the sheet can show");
        assertFalse(submissionRepository.findByStudentId(aarav.getId()).isEmpty(),
                "the submission has to exist afterwards, or the task stays Active forever");
    }

    /** The other half: the teacher has to be able to see it. */
    @Test
    void theSubmissionReachesTheTeachersVerificationQueue() {
        UUID taskId = createTask();
        tasksService.submitTaskForCurrentStudent(taskId, "Done", List.of("4/8", "1/2"), student);

        TeacherDashboardService.VerificationQueues queues =
                teacherDashboardService.buildVerificationQueues(
                        "priya.hand@example.com", "TEACHER", tenantId);

        assertTrue(queues.pendingSubmissions().stream()
                        .anyMatch(sub -> sub.getStudentName() != null
                                && sub.getStudentName().contains("Aarav")),
                "a hand-in nobody can verify is the same as no hand-in; queue held "
                        + queues.pendingSubmissions().size() + " submission(s)");
    }

    /** Handing the same task in twice must not create a second pending row. */
    @Test
    void handingInTwiceDoesNotQueueItTwice() {
        UUID taskId = createTask();
        tasksService.submitTaskForCurrentStudent(taskId, "First go", List.of("4/8", "1/2"), student);
        tasksService.submitTaskForCurrentStudent(taskId, "Second go", List.of("1/2", "4/8"), student);

        assertEquals(1, submissionRepository.findByStudentId(aarav.getId()).size(),
                "a second attempt replaces the first rather than queueing another");
    }
}

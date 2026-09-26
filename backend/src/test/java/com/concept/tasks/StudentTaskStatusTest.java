package com.concept.tasks;

import com.concept.assignment.app.SubjectAssignmentService;
import com.concept.shared.data.AcademicSubmission;
import com.concept.shared.data.AcademicSubmissionRepository;
import com.concept.shared.data.ClassSection;
import com.concept.shared.data.ClassSectionRepository;
import com.concept.shared.data.Student;
import com.concept.shared.data.StudentRepository;
import com.concept.tasks.app.CreateTaskRequest;
import com.concept.tasks.app.TasksService;
import com.concept.tasks.data.TeacherTask;
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
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Aarav handed in "QA app task", pressed Done, and his Challenges card still read
 * "Tap to open and hand in" -- with an accessible name still saying "Open to hand
 * in" -- while his teacher could see the submission waiting in the queue. He had
 * no way to tell whether the work had gone.
 *
 * <p>The cause was that {@code /api/student/tasks} returned the task and nothing
 * about the pupil: no submission status, so the card could only ever render the
 * one state. And because Hand in stayed pressable, the same work could be sent
 * again, each time quietly replacing what the teacher was part-way through
 * reviewing.
 *
 * <p>The case most easily broken by fixing this is work that was <em>sent
 * back</em>: REJECTED is an invitation to try again, so that one submission must
 * still be allowed through.
 */
@SpringBootTest
@TestPropertySource(properties = "app.dev-mode=true")
@Transactional
class StudentTaskStatusTest {

    @Autowired private TasksService tasksService;
    @Autowired private AcademicSubmissionRepository submissionRepository;
    @Autowired private StudentRepository studentRepository;
    @Autowired private ClassSectionRepository classSectionRepository;
    @Autowired private SubjectAssignmentService assignmentService;
    @Autowired private TenantRepository tenantRepository;
    @Autowired private AcademicYearRepository academicYearRepository;
    @Autowired private UserRepository userRepository;

    private UUID tenantId;
    private UUID yearId;
    private ClassSection sixA;
    private Student aarav;
    private Authentication teacher;
    private Authentication student;

    @BeforeEach
    void setup() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);

        Tenant tenant = new Tenant();
        tenant.setId(UUID.randomUUID());
        tenant.setName("Demo SSC");
        tenant.setSubdomain("sts-" + suffix);
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

        sixA = new ClassSection();
        sixA.setId(UUID.randomUUID());
        sixA.setTenantId(tenantId);
        sixA.setAcademicYearId(yearId);
        sixA.setGradeName("Grade 6");
        sixA.setSectionName("A");
        sixA = classSectionRepository.saveAndFlush(sixA);

        User priya = person("priya-" + suffix + "@example.com", "Priya Sharma", UserRole.TEACHER);
        assignmentService.assignSubject(priya.getId(), sixA.getId(), "Mathematics", true, tenantId);
        teacher = auth(priya.getEmail(), "TEACHER");

        User aaravUser = person("aarav-" + suffix + "@example.com", "Aarav Verma", UserRole.STUDENT);
        aarav = new Student();
        aarav.setId(UUID.randomUUID());
        aarav.setTenantId(tenantId);
        aarav.setAcademicYearId(yearId);
        aarav.setFirstName("Aarav");
        aarav.setLastName("Verma");
        aarav.setClassSection(sixA);
        aarav.setUserId(aaravUser.getId());
        aarav = studentRepository.saveAndFlush(aarav);
        student = auth(aaravUser.getEmail(), "STUDENT");
    }

    private User person(String email, String name, UserRole role) {
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

    private Authentication auth(String email, String role) {
        return new UsernamePasswordAuthenticationToken(email, null,
                List.of(new SimpleGrantedAuthority("ROLE_" + role)));
    }

    private TeacherTask setTask(String title) {
        CreateTaskRequest r = new CreateTaskRequest();
        r.setTitle(title);
        r.setDescription("Questions 1 to 5");
        r.setSubjectCode("MATH");
        r.setTaskType("HOMEWORK");
        r.setAssignedToClass(true);
        r.setClassSectionId(sixA.getId());
        r.setXpReward(5);
        r.setDueDate(LocalDate.now().plusDays(6));
        return (TeacherTask) tasksService.createTask(r, teacher);
    }

    @SuppressWarnings("unchecked")
    private List<TasksService.StudentTaskView> aaravsTasks() {
        return (List<TasksService.StudentTaskView>) tasksService.studentTasks(student);
    }

    private TasksService.StudentTaskView taskNamed(String title) {
        return aaravsTasks().stream()
                .filter(t -> title.equals(t.title()))
                .findFirst().orElseThrow();
    }

    // ── What the card can now say ─────────────────────────────────────────────

    @Test
    void anUntouchedTaskReportsThatNothingHasBeenHandedIn() {
        setTask("QA app task");

        TasksService.StudentTaskView view = taskNamed("QA app task");

        assertEquals("NOT_SUBMITTED", view.submissionStatus());
        assertFalse(view.handedIn());
    }

    /** The reported case: after handing in, the task must not still invite it. */
    @Test
    void aHandedInTaskReportsPendingSoTheCardStopsSayingHandIn() {
        TeacherTask task = setTask("QA app task");
        tasksService.submitTaskForCurrentStudent(task.getId(), "Did questions 1 to 5", List.of(), student);

        TasksService.StudentTaskView view = taskNamed("QA app task");

        assertEquals("PENDING", view.submissionStatus(),
                "the teacher can see it waiting, so the pupil has to be able to as well");
        assertTrue(view.handedIn());
    }

    @Test
    void anApprovedTaskReportsApproved() {
        TeacherTask task = setTask("QA app task");
        tasksService.submitTaskForCurrentStudent(task.getId(), "Done", List.of(), student);
        AcademicSubmission submission = submissionRepository
                .findByStudentIdAndTeacherTaskId(aarav.getId(), task.getId()).get(0);
        tasksService.approveXp(submission.getId(), tenantId);

        assertEquals("APPROVED", taskNamed("QA app task").submissionStatus());
    }

    /** The task's own fields still travel, or the card has nothing to render. */
    @Test
    void theViewStillCarriesEverythingTheCardShows() {
        setTask("QA app task");

        TasksService.StudentTaskView view = taskNamed("QA app task");

        assertEquals("HOMEWORK", view.taskType());
        assertEquals(5, view.xpReward());
        assertEquals("MATH", view.subjectCode());
        assertEquals(LocalDate.now().plusDays(6), view.dueDate());
        assertEquals("ACTIVE", view.taskStatus());
        assertEquals("Questions 1 to 5", view.description());
    }

    // ── A second hand-in ──────────────────────────────────────────────────────

    /**
     * 409, not a silent replacement. The app left Hand in pressable because
     * nothing told it a submission existed, so the same work could be sent
     * repeatedly over what the teacher was reviewing.
     */
    @Test
    void aSecondHandInIsRefusedWithAConflict() {
        TeacherTask task = setTask("QA app task");
        tasksService.submitTaskForCurrentStudent(task.getId(), "First go", List.of(), student);

        com.concept.tasks.app.TasksException e = assertThrows(
                com.concept.tasks.app.TasksException.class,
                () -> tasksService.submitTaskForCurrentStudent(task.getId(), "Again", List.of(), student));

        assertEquals(409, e.status(),
                "already done is a conflict, not a malformed request");
        assertEquals(1, submissionRepository
                        .findByStudentIdAndTeacherTaskId(aarav.getId(), task.getId()).size(),
                "and the first attempt must survive untouched");
    }

    @Test
    void handingInAnApprovedTaskAgainIsAlsoRefused() {
        TeacherTask task = setTask("QA app task");
        tasksService.submitTaskForCurrentStudent(task.getId(), "First go", List.of(), student);
        AcademicSubmission submission = submissionRepository
                .findByStudentIdAndTeacherTaskId(aarav.getId(), task.getId()).get(0);
        tasksService.approveXp(submission.getId(), tenantId);

        assertThrows(com.concept.tasks.app.TasksException.class,
                () -> tasksService.submitTaskForCurrentStudent(task.getId(), "Again", List.of(), student));
    }

    /**
     * The half a careless guard breaks. Work sent back is an invitation to try
     * again; refusing that would leave a pupil told to redo something they cannot
     * resubmit.
     */
    @Test
    void workThatWasSentBackCanBeHandedInAgain() {
        TeacherTask task = setTask("QA app task");
        tasksService.submitTaskForCurrentStudent(task.getId(), "First go", List.of(), student);
        AcademicSubmission submission = submissionRepository
                .findByStudentIdAndTeacherTaskId(aarav.getId(), task.getId()).get(0);
        submission.setStatus("REJECTED");
        submissionRepository.saveAndFlush(submission);

        tasksService.submitTaskForCurrentStudent(task.getId(), "Second go", List.of(), student);

        assertEquals("PENDING", taskNamed("QA app task").submissionStatus(),
                "the new attempt is what the teacher should now see");
        assertEquals(1, submissionRepository
                        .findByStudentIdAndTeacherTaskId(aarav.getId(), task.getId()).size(),
                "the rejected row is replaced, not stacked beside the new one");
    }

    /**
     * An approval must never be hidden behind an older rejection. Without an
     * order over statuses, whichever row came back last would decide what the
     * pupil is shown.
     */
    @Test
    void anApprovalOutranksAnEarlierRejectionOnTheSameTask() {
        TeacherTask task = setTask("QA app task");
        tasksService.submitTaskForCurrentStudent(task.getId(), "First go", List.of(), student);
        AcademicSubmission first = submissionRepository
                .findByStudentIdAndTeacherTaskId(aarav.getId(), task.getId()).get(0);
        first.setStatus("REJECTED");
        submissionRepository.saveAndFlush(first);

        // A stale rejected row left behind alongside an approved one.
        AcademicSubmission stale = new AcademicSubmission(aarav.getId(), task.getTitle(), 5);
        stale.setTeacherTaskId(task.getId());
        stale.setStatus("APPROVED");
        submissionRepository.saveAndFlush(stale);

        assertEquals("APPROVED", taskNamed("QA app task").submissionStatus());
    }

    /** One pupil's hand-in is not another's. */
    @Test
    void anotherPupilsSubmissionDoesNotMarkThisOneHandedIn() {
        TeacherTask task = setTask("QA app task");

        Student kavya = new Student();
        kavya.setId(UUID.randomUUID());
        kavya.setTenantId(tenantId);
        kavya.setAcademicYearId(yearId);
        kavya.setFirstName("Kavya");
        kavya.setLastName("Rao");
        kavya.setClassSection(sixA);
        kavya = studentRepository.saveAndFlush(kavya);

        AcademicSubmission hers = new AcademicSubmission(kavya.getId(), task.getTitle(), 5);
        hers.setTeacherTaskId(task.getId());
        submissionRepository.saveAndFlush(hers);

        assertEquals("NOT_SUBMITTED", taskNamed("QA app task").submissionStatus());
        assertFalse(taskNamed("QA app task").handedIn());
    }
}

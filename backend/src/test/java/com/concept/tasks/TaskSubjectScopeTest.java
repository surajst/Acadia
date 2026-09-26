package com.concept.tasks;

import com.concept.academics.data.Subject;
import com.concept.academics.data.SubjectRepository;
import com.concept.assignment.app.SubjectAssignmentService;
import com.concept.shared.data.ClassSection;
import com.concept.shared.data.ClassSectionRepository;
import com.concept.shared.data.Student;
import com.concept.shared.data.StudentRepository;
import com.concept.tasks.app.CreateTaskRequest;
import com.concept.tasks.app.TasksException;
import com.concept.tasks.app.TasksService;
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

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A task has to be filed under a subject the caller actually teaches that class.
 *
 * <p>The section has been checked since R2-P1-2, but nothing ever looked at the
 * subject. So Priya, assigned to 6-A for Mathematics, could post English homework
 * to 6-A: it appeared on those children's lists under English, with 6-A's real
 * English teacher none the wiser and no record of how it got there. The web form
 * offering all five of the school's subjects is how it surfaced, and narrowing
 * that picker alone would have been a rule the form kept and the API did not --
 * which is why this test sends the request straight to the service.
 *
 * <p>The cases that matter most here are the ones where a refusal would be wrong.
 * A teacher who cannot set homework at all is worse off than the loose rule this
 * replaces, so the reconciliation between a task's catalogue <em>code</em> and an
 * assignment's subject <em>name</em> is pinned from both directions.
 */
@SpringBootTest
@TestPropertySource(properties = "app.dev-mode=true")
@Transactional
class TaskSubjectScopeTest {

    @Autowired private TasksService tasksService;
    @Autowired private SubjectRepository subjectRepository;
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
    private Authentication priya;

    @BeforeEach
    void setup() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);

        Tenant tenant = new Tenant();
        tenant.setId(UUID.randomUUID());
        tenant.setName("Demo SSC");
        tenant.setSubdomain("tss-" + suffix);
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

        // The catalogue this school offers. Both spellings matter below: the code
        // travels on the task, the display name on the assignment.
        catalogue("MATHEMATICS", "Mathematics", 1);
        catalogue("ENGLISH", "English", 2);
        catalogue("SOCIAL_SCIENCE", "Social Science", 3);

        User priyaUser = person("priya-" + suffix + "@example.com", "Priya Sharma", UserRole.TEACHER);
        assignmentService.assignSubject(priyaUser.getId(), sixA.getId(), "Mathematics", true, tenantId);
        priya = auth(priyaUser.getEmail(), "TEACHER");

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
    }

    private void catalogue(String code, String displayName, int order) {
        Subject subject = new Subject();
        subject.setId(UUID.randomUUID());
        subject.setTenantId(tenantId);
        subject.setAcademicYearId(yearId);
        subject.setCode(code);
        subject.setDisplayName(displayName);
        subject.setActive(true);
        subject.setSortOrder(order);
        subjectRepository.saveAndFlush(subject);
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

    private CreateTaskRequest classTask(String subjectCode) {
        CreateTaskRequest r = new CreateTaskRequest();
        r.setTitle("Worksheet 4");
        r.setDescription("Questions 1 to 5");
        r.setSubjectCode(subjectCode);
        r.setTaskType("HOMEWORK");
        r.setAssignedToClass(true);
        r.setClassSectionId(sixA.getId());
        r.setXpReward(5);
        r.setDueDate(LocalDate.now().plusDays(6));
        return r;
    }

    private CreateTaskRequest personalTask(String subjectCode) {
        CreateTaskRequest r = classTask(subjectCode);
        r.setAssignedToClass(false);
        r.setClassSectionId(null);
        r.setStudentId(aarav.getId());
        return r;
    }

    // ── The hole ─────────────────────────────────────────────────────────────

    @Test
    void aTeacherCannotFileATaskUnderASubjectTheyDoNotTeach() {
        TasksException refused = assertThrows(TasksException.class,
                () -> tasksService.createTask(classTask("ENGLISH"), priya));

        assertTrue(refused.getMessage().contains("not assigned to teach English"),
                "the refusal has to name the subject, or it is a puzzle: " + refused.getMessage());
    }

    /** The same hole, one pupil at a time. */
    @Test
    void norForOnePupil() {
        TasksException refused = assertThrows(TasksException.class,
                () -> tasksService.createTask(personalTask("ENGLISH"), priya));

        assertTrue(refused.getMessage().contains("not assigned to teach English"));
    }

    // ── And the work that must still go through ──────────────────────────────

    @Test
    void theirOwnSubjectIsFine() {
        assertDoesNotThrow(() -> tasksService.createTask(classTask("MATHEMATICS"), priya));
    }

    @Test
    void theirOwnSubjectIsFineForOnePupilToo() {
        assertDoesNotThrow(() -> tasksService.createTask(personalTask("MATHEMATICS"), priya));
    }

    /** Two subjects in one section is an ordinary arrangement. */
    @Test
    void aTeacherWhoTakesTwoSubjectsCanUseBoth() {
        User priyaUser = userRepository.findByEmail(priya.getName()).orElseThrow();
        assignmentService.assignSubject(priyaUser.getId(), sixA.getId(), "English", false, tenantId);

        assertDoesNotThrow(() -> tasksService.createTask(classTask("MATHEMATICS"), priya));
        assertDoesNotThrow(() -> tasksService.createTask(classTask("ENGLISH"), priya));
    }

    /**
     * The multi-word case, where a code and a name differ in shape rather than
     * just in case: "Social Science" against SOCIAL_SCIENCE.
     */
    @Test
    void aMultiWordSubjectMatchesItsCode() {
        User priyaUser = userRepository.findByEmail(priya.getName()).orElseThrow();
        assignmentService.assignSubject(priyaUser.getId(), sixA.getId(), "Social Science", false, tenantId);

        assertDoesNotThrow(() -> tasksService.createTask(classTask("SOCIAL_SCIENCE"), priya));
    }

    /**
     * The wrong-refusal guard that matters most. An assignment recorded with the
     * code in it rather than the display name is not in the catalogue's shape, and
     * a check that only consulted the catalogue would tell this teacher they do not
     * teach their own class.
     */
    @Test
    void anAssignmentHoldingTheCodeRatherThanTheNameStillCounts() {
        User odd = person("odd-" + UUID.randomUUID().toString().substring(0, 8) + "@example.com",
                "Odd Records", UserRole.TEACHER);
        // Not the home class: Priya already is, and a section has only one.
        assignmentService.assignSubject(odd.getId(), sixA.getId(), "SOCIAL_SCIENCE", false, tenantId);

        assertDoesNotThrow(() -> tasksService.createTask(
                classTask("SOCIAL_SCIENCE"), auth(odd.getEmail(), "TEACHER")));
    }

    /**
     * A subject this school does not offer is deliberately NOT this rule's business.
     *
     * <p>I wrote this the other way round first, and 46 existing tests said no: they
     * create tasks with the code "MATH" against an assignment recorded as
     * "Mathematics", and no abbreviation will ever normalise to its own long form.
     * That is worth taking seriously rather than editing away -- the rule was
     * refusing on a basis it could not verify, which is how it would stop a teacher
     * setting homework at all. Nobody's subject is being borrowed by a code the
     * school does not use.
     */
    @Test
    void aSubjectTheSchoolDoesNotOfferIsLeftAlone() {
        assertDoesNotThrow(() -> tasksService.createTask(classTask("ASTROPHYSICS"), priya));
    }

    /**
     * And the reason that leniency is narrow. If "is one of this school's subjects"
     * were matched on the code alone, the whole rule would be one spelling away from
     * nothing: English is the same claim as ENGLISH.
     */
    @Test
    void theDisplayNameIsTheSameClaimAsTheCode() {
        TasksException refused = assertThrows(TasksException.class,
                () -> tasksService.createTask(classTask("English"), priya));

        assertTrue(refused.getMessage().contains("not assigned to teach English"),
                refused.getMessage());
    }

    /** Likewise a shape a school might plausibly send. */
    @Test
    void aMultiWordSubjectsNameIsAlsoTheSameClaim() {
        TasksException refused = assertThrows(TasksException.class,
                () -> tasksService.createTask(classTask("Social Science"), priya));

        assertTrue(refused.getMessage().contains("not assigned to teach Social Science"),
                refused.getMessage());
    }

    @Test
    void aTaskWithNoSubjectIsRefused() {
        TasksException refused = assertThrows(TasksException.class,
                () -> tasksService.createTask(classTask(null), priya));

        assertTrue(refused.getMessage().toLowerCase().contains("subject"), refused.getMessage());
    }

    // ── Who the rule does not apply to ───────────────────────────────────────

    /**
     * Somebody has to be able to set work for a teacher who has left, and an admin
     * is assigned to nothing -- so the rule cannot apply to them without locking
     * the school out of its own classes. The same reasoning already governs the
     * section check beside it.
     */
    @Test
    void anAdminIsNotAssignedToAnythingAndIsNotHeldToThis() {
        User admin = person("admin-" + UUID.randomUUID().toString().substring(0, 8) + "@example.com",
                "School Admin", UserRole.ADMIN);

        assertDoesNotThrow(() -> tasksService.createTask(
                classTask("ENGLISH"), auth(admin.getEmail(), "ADMIN")));
    }

    @Test
    void aPrincipalLikewise() {
        User head = person("head-" + UUID.randomUUID().toString().substring(0, 8) + "@example.com",
                "Head Teacher", UserRole.PRINCIPAL);

        assertDoesNotThrow(() -> tasksService.createTask(
                classTask("ENGLISH"), auth(head.getEmail(), "PRINCIPAL")));
    }

    // ── Who this newly locks out: nobody who was not already ─────────────────

    /**
     * The question worth answering before shipping a rule like this: who loses
     * something they had?
     *
     * <p>A teacher with no subject assignments at all is refused -- but they were
     * already, and by a different rule. {@code teacherOwnsSection} has turned them
     * away from whole-class tasks since R2-P1-2, with an AccessDeniedException
     * rather than a bad request. This asserts the refusal still comes from there,
     * which is how you can tell the subject rule has not widened the net.
     */
    @Test
    void aTeacherWithNoAssignmentsWasAlreadyRefusedBySection() {
        User unassigned = person("none-" + UUID.randomUUID().toString().substring(0, 8) + "@example.com",
                "New Starter", UserRole.TEACHER);
        Authentication theirs = auth(unassigned.getEmail(), "TEACHER");

        // Not TasksException: the section rule gets there first, and says so in
        // its own words. If this ever becomes a TasksException, the subject rule
        // has started catching people the section rule used to.
        assertThrows(org.springframework.security.access.AccessDeniedException.class,
                () -> tasksService.createTask(classTask("MATHEMATICS"), theirs));
    }

    /**
     * And for one pupil, where there was no check at all before. This is the only
     * path the subject rule genuinely closes for an unassigned teacher -- reachable
     * through the API, though not through the app, whose student search returns
     * only children in the teacher's own sections.
     */
    @Test
    void anUnassignedTeacherCannotSlipATaskToOnePupilEither() {
        User unassigned = person("none2-" + UUID.randomUUID().toString().substring(0, 8) + "@example.com",
                "New Starter", UserRole.TEACHER);

        assertThrows(TasksException.class,
                () -> tasksService.createTask(personalTask("MATHEMATICS"),
                        auth(unassigned.getEmail(), "TEACHER")));
    }

    // ── Existing tasks are left alone ────────────────────────────────────────

    /**
     * The rule is on create. There is no edit path to guard -- updateTask takes the
     * title, description, due date and XP, and deliberately not what the task is
     * for -- so a task already filed under another subject stays editable. This
     * pins that, because adding the check to the wrong method would break it
     * silently for every task set before today.
     */
    @Test
    void aTaskAlreadyFiledElsewhereCanStillBeCorrected() {
        User admin = person("admin2-" + UUID.randomUUID().toString().substring(0, 8) + "@example.com",
                "School Admin", UserRole.ADMIN);
        Authentication adminAuth = auth(admin.getEmail(), "ADMIN");
        com.concept.tasks.data.TeacherTask english =
                (com.concept.tasks.data.TeacherTask) tasksService.createTask(classTask("ENGLISH"), adminAuth);

        assertDoesNotThrow(() -> tasksService.updateTask(english.getId(), "Corrected title",
                "Still English", LocalDate.now().plusDays(3), 10, adminAuth));
    }
}

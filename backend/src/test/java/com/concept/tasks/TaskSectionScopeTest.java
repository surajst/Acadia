package com.concept.tasks;

import com.concept.assignment.data.SubjectAssignment;
import com.concept.assignment.data.SubjectAssignmentRepository;
import com.concept.tasks.app.CreateTaskRequest;
import com.concept.tasks.app.TasksService;
import com.concept.tasks.app.TeacherTaskService;
import com.concept.tasks.data.TeacherTask;
import com.concept.tasks.data.TeacherTaskRepository;
import com.concept.shared.data.ClassSection;
import com.concept.shared.data.ClassSectionRepository;
import com.concept.shared.data.Student;
import com.concept.shared.data.StudentRepository;
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
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * Priya teaches 6-A. A task she set for her class appeared on every 6-B child's
 * list too, so Neha could hand in work her own teacher never set her, and Priya
 * saw submissions from children she does not teach.
 *
 * <p>It was not a logic bug. {@code teacher_tasks} carried a numeric standard and
 * nothing narrower, and a student fetched class tasks with
 * {@code findByStandardAndAssignedToClassTrue} -- there was no column to hold
 * the answer. V20 adds one.
 *
 * <p>The awkward half is the existing rows, which have no section and from which
 * none can be recovered: a teacher who takes two sections gives two candidates
 * and no tie-break. Null therefore keeps meaning grade-wide, so a task children
 * are already working from does not vanish off their list. That is asserted
 * here as deliberate behaviour rather than left as an accident -- what should
 * happen to those rows is the school's call, not a default.
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = "app.dev-mode=true")
@Transactional
class TaskSectionScopeTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private TasksService tasksService;
    @Autowired private TeacherTaskService teacherTaskService;
    @Autowired private TeacherTaskRepository teacherTaskRepository;
    @Autowired private StudentRepository studentRepository;
    @Autowired private ClassSectionRepository classSectionRepository;
    @Autowired private SubjectAssignmentRepository subjectAssignmentRepository;
    @Autowired private TenantRepository tenantRepository;
    @Autowired private AcademicYearRepository academicYearRepository;
    @Autowired private UserRepository userRepository;

    private UUID tenantId;
    private UUID yearId;
    private ClassSection sixA;
    private ClassSection sixB;
    private Student aarav;   // 6-A
    private Student neha;    // 6-B
    private String priyaEmail;
    private String adminEmail;
    private Authentication priya;
    private Authentication admin;

    @BeforeEach
    void setup() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);

        Tenant tenant = new Tenant();
        tenant.setId(UUID.randomUUID());
        tenant.setName("Demo SSC");
        tenant.setSubdomain("tasksec-" + suffix);
        tenant.setActive(true);
        tenant.setCreatedAt(Instant.now());
        tenantId = tenantRepository.saveAndFlush(tenant).getId();

        AcademicYear year = new AcademicYear();
        year.setId(UUID.randomUUID());
        year.setTenantId(tenantId);
        year.setName("2026-27");
        year.setStartDate(LocalDate.of(2026, 4, 1));
        year.setEndDate(LocalDate.of(2027, 3, 31));
        year.setCurrent(true);
        yearId = academicYearRepository.saveAndFlush(year).getId();

        sixA = section("Grade 6", "A");
        sixB = section("Grade 6", "B");
        aarav = student("Aarav", sixA);
        neha = student("Neha", sixB);

        priyaEmail = "priya-" + suffix + "@example.com";
        adminEmail = "admin-" + suffix + "@example.com";
        User priyaUser = person(priyaEmail, "Priya Sharma", UserRole.TEACHER);
        person(adminEmail, "Office Admin", UserRole.ADMIN);
        priya = new UsernamePasswordAuthenticationToken(priyaEmail, "n/a");
        admin = new UsernamePasswordAuthenticationToken(adminEmail, "n/a");

        assign(priyaUser, sixA);
    }

    private ClassSection section(String grade, String name) {
        ClassSection s = new ClassSection();
        s.setId(UUID.randomUUID());
        s.setTenantId(tenantId);
        s.setAcademicYearId(yearId);
        s.setGradeName(grade);
        s.setSectionName(name);
        return classSectionRepository.saveAndFlush(s);
    }

    private Student student(String firstName, ClassSection in) {
        Student s = new Student();
        s.setId(UUID.randomUUID());
        s.setTenantId(tenantId);
        s.setAcademicYearId(yearId);
        s.setFirstName(firstName);
        s.setLastName("Verma");
        s.setClassSection(in);
        return studentRepository.saveAndFlush(s);
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

    private void assign(User teacher, ClassSection section) {
        SubjectAssignment a = new SubjectAssignment();
        a.setId(UUID.randomUUID());
        a.setTenantId(tenantId);
        a.setAcademicYearId(yearId);
        a.setTeacher(teacher);
        a.setClassSection(section);
        a.setSubjectName("Mathematics");
        a.setHomeClass(true);
        subjectAssignmentRepository.saveAndFlush(a);
    }

    private CreateTaskRequest classTask(String title, ClassSection forSection) {
        CreateTaskRequest r = new CreateTaskRequest();
        r.setTitle(title);
        r.setDescription("Practice");
        r.setSubjectCode("MATH");
        r.setTaskType("HOMEWORK");
        r.setAssignedToClass(true);
        r.setClassSectionId(forSection == null ? null : forSection.getId());
        r.setXpReward(10);
        return r;
    }

    private List<TeacherTask> tasksVisibleTo(Student s) {
        return teacherTaskService.getTasksForStudent(s.getId(), 6,
                s.getClassSection() == null ? null : s.getClassSection().getId(), tenantId);
    }

    private boolean sees(Student s, String title) {
        return tasksVisibleTo(s).stream().anyMatch(t -> title.equals(t.getTitle()));
    }

    // ── The reported case ─────────────────────────────────────────────────────

    @Test
    void aTaskSetForOneSectionDoesNotReachTheOther() {
        tasksService.createTask(classTask("Fractions worksheet", sixA), priya);

        assertTrue(sees(aarav, "Fractions worksheet"), "6-A is who it was set for");
        assertFalse(sees(neha, "Fractions worksheet"),
                "Neha is in 6-B and was never set this work");
    }

    @Test
    void theSectionIsRecordedOnTheTask() {
        tasksService.createTask(classTask("Fractions worksheet", sixA), priya);

        TeacherTask saved = teacherTaskRepository.findByTenantId(tenantId).stream()
                .filter(t -> "Fractions worksheet".equals(t.getTitle())).findFirst().orElseThrow();
        assertEquals(sixA.getId(), saved.getClassSectionId());
        assertEquals(6, saved.getStandard(),
                "the standard is derived from the section, since that is what a student is matched on");
    }

    /** Each section's own work still arrives. */
    @Test
    void bothSectionsSeeTheirOwnWork() {
        tasksService.createTask(classTask("6A worksheet", sixA), priya);
        tasksService.createTask(classTask("6B worksheet", sixB), admin);

        assertTrue(sees(aarav, "6A worksheet"));
        assertFalse(sees(aarav, "6B worksheet"));
        assertTrue(sees(neha, "6B worksheet"));
        assertFalse(sees(neha, "6A worksheet"));
    }

    // ── The section has to be one the caller teaches ──────────────────────────

    @Test
    void priyaCannotSetWorkForASectionSheDoesNotTeach() {
        assertThrows(AccessDeniedException.class,
                () -> tasksService.createTask(classTask("Not hers", sixB), priya));
        assertFalse(sees(neha, "Not hers"));
    }

    @Test
    void aClassTaskWithNoSectionIsRefused() {
        Exception e = assertThrows(Exception.class,
                () -> tasksService.createTask(classTask("Unscoped", null), priya));
        assertTrue(String.valueOf(e.getMessage()).toLowerCase().contains("class"),
                "the message has to say what is missing, got: " + e.getMessage());
    }

    /** An admin covering for a teacher can set work for any section. */
    @Test
    void anAdminMaySetWorkForAnySection() {
        tasksService.createTask(classTask("Revision", sixB), admin);
        assertTrue(sees(neha, "Revision"));
    }

    /** A task for one named child does not need a section and is not scoped by one. */
    @Test
    void aTaskForOneStudentStillWorksWithoutASection() {
        CreateTaskRequest personal = classTask("Extra reading", null);
        personal.setAssignedToClass(false);
        personal.setStudentId(neha.getId());
        personal.setStandard(6);

        tasksService.createTask(personal, priya);

        assertTrue(sees(neha, "Extra reading"), "it was set for her by name");
        assertFalse(sees(aarav, "Extra reading"));
    }

    // ── The existing rows ─────────────────────────────────────────────────────

    /**
     * A task raised before the column existed has no section, and none can be
     * recovered. It keeps reaching the whole grade on purpose: narrowing it would
     * take work off the list of children already doing it, and widening the rule
     * to guess a section from the teacher's assignments has no answer for a
     * teacher who takes two.
     */
    @Test
    void aTaskWithNoSectionRecordedStillReachesTheWholeGrade() {
        TeacherTask legacy = new TeacherTask();
        legacy.setId(UUID.randomUUID());
        legacy.setTenantId(tenantId);
        legacy.setAcademicYearId(yearId);
        legacy.setTitle("Raised before sections were recorded");
        legacy.setSubjectCode("MATH");
        legacy.setTaskType(com.concept.tasks.data.TaskType.HOMEWORK);
        legacy.setStandard(6);
        legacy.setAssignedToClass(true);
        legacy.setClassSectionId(null);
        legacy.setCreatedByTeacherId(UUID.randomUUID());
        legacy.setXpReward(10);
        teacherTaskRepository.saveAndFlush(legacy);

        assertTrue(sees(aarav, "Raised before sections were recorded"));
        assertTrue(sees(neha, "Raised before sections were recorded"),
                "an existing task must not disappear off a child's list");
    }

    /**
     * The grade-wide fallback is defensive only: students.class_section_id is NOT
     * NULL, so no real student reaches it. Worth pinning, because it is the
     * reason narrowing the query is safe -- if a child could be in no section,
     * scoping class tasks by section would hide every one of them.
     *
     * <p>Asserted by trying to create one and being refused by the database,
     * rather than by reading the DDL, so it stays true if the column changes.
     */
    @Test
    void noStudentCanBeInNoSectionAtAll() {
        assertThrows(Exception.class, () -> {
            student("Unplaced", null);
            studentRepository.flush();
        }, "a student with no class section must not be storable");
    }

    /**
     * And if one somehow were, the grade-wide list is what they would get --
     * nothing can be narrowed for a child in no class.
     */
    @Test
    void withNoSectionToMatchOnTheWholeGradeIsTheAnswer() {
        tasksService.createTask(classTask("Fractions worksheet", sixA), priya);

        List<TeacherTask> visible = teacherTaskService.getTasksForStudent(
                UUID.randomUUID(), 6, null, tenantId);

        assertTrue(visible.stream().anyMatch(t -> "Fractions worksheet".equals(t.getTitle())),
                "with no section to match on, the grade is all there is to go on");
    }

    // ── Straight at the endpoint ──────────────────────────────────────────────

    private MvcResult postTask(String email, String role, UUID sectionId) throws Exception {
        String body = "{\"title\":\"API set task\",\"description\":\"Practice\","
                + "\"subjectCode\":\"MATH\",\"taskType\":\"HOMEWORK\",\"standard\":6,"
                + "\"assignedToClass\":true,\"xpReward\":10"
                + (sectionId == null ? "" : ",\"classSectionId\":\"" + sectionId + "\"")
                + "}";
        return mockMvc.perform(post("/api/teacher/tasks/create")
                        .with(user(email).roles(role))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andReturn();
    }

    /**
     * The form now sends a section, but a client can send whatever it likes. The
     * old payload -- a standard and no section -- must be refused rather than
     * quietly accepted as grade-wide, or the fix only holds for the browser.
     */
    @Test
    void theApiRefusesAClassTaskWithNoSection() throws Exception {
        MvcResult result = postTask(priyaEmail, "TEACHER", null);

        assertEquals(400, result.getResponse().getStatus(),
                "a class task with no section would go to every section of the grade");
        assertTrue(teacherTaskRepository.findByTenantId(tenantId).isEmpty(),
                "and nothing may be written");
    }

    @Test
    void theApiRefusesASectionTheCallerDoesNotTeach() throws Exception {
        MvcResult result = postTask(priyaEmail, "TEACHER", sixB.getId());

        assertEquals(403, result.getResponse().getStatus(),
                "an id in the request body is not a permission, got "
                        + result.getResponse().getStatus());
        assertTrue(teacherTaskRepository.findByTenantId(tenantId).isEmpty());
    }

    @Test
    void theApiAcceptsTheCallersOwnSection() throws Exception {
        MvcResult result = postTask(priyaEmail, "TEACHER", sixA.getId());

        assertTrue(result.getResponse().getStatus() < 400,
                "6-A is hers, got " + result.getResponse().getStatus());
        assertEquals(1, teacherTaskRepository.findByTenantId(tenantId).size());
    }

    /** Another school's section is not reachable by id. */
    @Test
    void theApiRefusesASectionFromAnotherSchool() throws Exception {
        MvcResult result = postTask(priyaEmail, "TEACHER", UUID.randomUUID());

        assertTrue(result.getResponse().getStatus() >= 400,
                "an unknown section must not be accepted, got " + result.getResponse().getStatus());
        assertTrue(teacherTaskRepository.findByTenantId(tenantId).isEmpty());
    }

    // ── The picker the form is filled from ────────────────────────────────────

    @Test
    void theSectionPickerOffersOnlyTheTeachersOwnSections() {
        List<java.util.Map<String, Object>> options = tasksService.sectionOptionsForCaller(priya);

        assertEquals(1, options.size(), "Priya takes one section");
        assertEquals(sixA.getId(), options.get(0).get("value"));
        assertEquals("Grade 6 - A", options.get(0).get("label"));
    }

    @Test
    void anAdminsPickerOffersTheWholeSchool() {
        List<java.util.Map<String, Object>> options = tasksService.sectionOptionsForCaller(admin);

        assertEquals(2, options.size());
        assertEquals("Grade 6 - A", options.get(0).get("label"), "sorted by label");
        assertEquals("Grade 6 - B", options.get(1).get("label"));
    }
}

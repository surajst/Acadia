package com.concept.tasks;

import com.concept.assignment.data.SubjectAssignment;
import com.concept.assignment.data.SubjectAssignmentRepository;
import com.concept.shared.data.AcademicSubmissionRepository;
import com.concept.shared.data.ClassSection;
import com.concept.shared.data.ClassSectionRepository;
import com.concept.shared.data.Student;
import com.concept.shared.data.StudentRepository;
import com.concept.tasks.app.CreateTaskRequest;
import com.concept.tasks.app.TasksService;
import com.concept.tasks.app.TeacherTaskService;
import com.concept.tasks.data.TeacherTask;
import com.concept.tasks.data.TeacherTaskRepository;
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
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * The teacher's task list was read-only. A typo in a title lived for the life of
 * the task, a finished task stayed on every child's list forever, and a teacher
 * who had set work had no way to see who had done it -- the only review surface
 * was one undifferentiated pending queue for the whole school.
 *
 * <p>The two rules worth their own tests are the ones that protect work already
 * done: a task with submissions against it cannot be deleted (those rows carry XP
 * a child has been told they earned), and closing a task has to stop hand-ins
 * rather than merely hide it, or a pupil with the sheet still open submits
 * against a retired task.
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = "app.dev-mode=true")
@Transactional
class TaskManagementTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private TasksService tasksService;
    @Autowired private TeacherTaskService teacherTaskService;
    @Autowired private TeacherTaskRepository teacherTaskRepository;
    @Autowired private AcademicSubmissionRepository submissionRepository;
    @Autowired private StudentRepository studentRepository;
    @Autowired private ClassSectionRepository classSectionRepository;
    @Autowired private SubjectAssignmentRepository subjectAssignmentRepository;
    @Autowired private TenantRepository tenantRepository;
    @Autowired private AcademicYearRepository academicYearRepository;
    @Autowired private UserRepository userRepository;

    private UUID tenantId;
    private UUID yearId;
    private ClassSection sixA;
    private Student aarav;
    private Student neha;
    private String priyaEmail;
    private String rahulEmail;
    private String adminEmail;
    private Authentication priya;
    private Authentication rahul;
    private Authentication admin;
    private Authentication aaravAuth;

    @BeforeEach
    void setup() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);

        Tenant tenant = new Tenant();
        tenant.setId(UUID.randomUUID());
        tenant.setName("Demo SSC");
        tenant.setSubdomain("taskmgmt-" + suffix);
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

        priyaEmail = "priya-" + suffix + "@example.com";
        rahulEmail = "rahul-" + suffix + "@example.com";
        adminEmail = "admin-" + suffix + "@example.com";
        User priyaUser = person(priyaEmail, "Priya Sharma", UserRole.TEACHER);
        User rahulUser = person(rahulEmail, "Rahul Iyer", UserRole.TEACHER);
        person(adminEmail, "Office Admin", UserRole.ADMIN);
        priya = auth(priyaEmail, "TEACHER");
        rahul = auth(rahulEmail, "TEACHER");
        admin = auth(adminEmail, "ADMIN");
        assign(priyaUser, sixA);
        assign(rahulUser, sixA);

        User aaravUser = person("aarav-" + suffix + "@example.com", "Aarav Verma", UserRole.STUDENT);
        aarav = student("Aarav", aaravUser.getId());
        neha = student("Neha", null);
        aaravAuth = auth(aaravUser.getEmail(), "STUDENT");
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

    private Student student(String firstName, UUID userId) {
        Student s = new Student();
        s.setId(UUID.randomUUID());
        s.setTenantId(tenantId);
        s.setAcademicYearId(yearId);
        s.setFirstName(firstName);
        s.setLastName("Verma");
        s.setRollNumber("6A-" + firstName);
        s.setClassSection(sixA);
        s.setUserId(userId);
        return studentRepository.saveAndFlush(s);
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

    private Authentication auth(String email, String role) {
        return new UsernamePasswordAuthenticationToken(email, null,
                List.of(new SimpleGrantedAuthority("ROLE_" + role)));
    }

    private TeacherTask aTask(Authentication setBy) {
        CreateTaskRequest r = new CreateTaskRequest();
        r.setTitle("Fractions worksheet");
        r.setDescription("Questions 1 to 5");
        r.setSubjectCode("MATH");
        r.setTaskType("HOMEWORK");
        r.setAssignedToClass(true);
        r.setClassSectionId(sixA.getId());
        r.setXpReward(10);
        r.setDueDate(LocalDate.now().plusDays(6));
        return (TeacherTask) tasksService.createTask(r, setBy);
    }

    private TeacherTask reload(UUID id) {
        return teacherTaskRepository.findByIdAndTenantId(id, tenantId).orElseThrow();
    }

    // ── Edit ──────────────────────────────────────────────────────────────────

    @Test
    void aTeacherCanCorrectATaskTheyHaveSet() {
        TeacherTask task = aTask(priya);

        tasksService.updateTask(task.getId(), "Fractions worksheet (corrected)",
                "Questions 1 to 8", LocalDate.now().plusDays(10), 20, priya);

        TeacherTask after = reload(task.getId());
        assertEquals("Fractions worksheet (corrected)", after.getTitle());
        assertEquals("Questions 1 to 8", after.getDescription());
        assertEquals(LocalDate.now().plusDays(10), after.getDueDate());
        assertEquals(20, after.getXpReward());
    }

    /**
     * The XP bounds have to hold on the way out as well as the way in. A rule
     * that only guards creation is not a rule: -10 XP could simply be edited
     * back in, which is the exact value this round already fixed once.
     */
    @Test
    void theXpBoundsHoldOnAnEditToo() {
        TeacherTask task = aTask(priya);

        assertThrows(Exception.class, () -> tasksService.updateTask(
                task.getId(), "Title", "d", null, -10, priya));
        assertThrows(Exception.class, () -> tasksService.updateTask(
                task.getId(), "Title", "d", null, 0, priya));
        assertThrows(Exception.class, () -> tasksService.updateTask(
                task.getId(), "Title", "d", null, 999999, priya));

        assertEquals(10, reload(task.getId()).getXpReward(), "none of those may have landed");
    }

    @Test
    void aBlankTitleIsRefusedOnAnEdit() {
        TeacherTask task = aTask(priya);

        assertThrows(Exception.class, () -> tasksService.updateTask(
                task.getId(), "   ", "d", null, 10, priya));
        assertEquals("Fractions worksheet", reload(task.getId()).getTitle());
    }

    // ── Ownership ─────────────────────────────────────────────────────────────

    @Test
    void anotherTeachersTaskIsNotTheirsToChange() {
        TeacherTask task = aTask(priya);

        assertThrows(AccessDeniedException.class,
                () -> tasksService.updateTask(task.getId(), "Hijacked", "d", null, 10, rahul));
        assertThrows(AccessDeniedException.class, () -> tasksService.closeTask(task.getId(), rahul));
        assertThrows(AccessDeniedException.class, () -> tasksService.deleteTask(task.getId(), rahul));
        assertEquals("Fractions worksheet", reload(task.getId()).getTitle());
    }

    /** Somebody has to be able to clear up after a teacher who has left. */
    @Test
    void anAdminCanManageAnyTask() {
        TeacherTask task = aTask(priya);

        tasksService.closeTask(task.getId(), admin);

        assertEquals("CLOSED", reload(task.getId()).getTaskStatus());
    }

    @Test
    void aTaskFromAnotherSchoolIsNotFound() {
        TeacherTask task = aTask(priya);
        // The caller resolves to no tenant at all, which is how a foreign id
        // arrives in practice.
        assertThrows(Exception.class,
                () -> tasksService.closeTask(task.getId(), auth("nobody@example.com", "TEACHER")));
        assertEquals("ACTIVE", reload(task.getId()).getTaskStatus());
    }

    // ── Close and reopen ──────────────────────────────────────────────────────

    @Test
    void aClosedTaskComesOffTheStudentsList() {
        TeacherTask task = aTask(priya);
        assertTrue(studentSees(task, "before closing it is on the list"));

        tasksService.closeTask(task.getId(), priya);

        assertFalse(studentSees(task, ""), "a closed task must leave the child's list");
    }

    /** Closing has to actually stop hand-ins, or it only hides the task. */
    @Test
    void aClosedTaskCannotBeHandedIn() {
        TeacherTask task = aTask(priya);
        tasksService.closeTask(task.getId(), priya);

        Exception e = assertThrows(Exception.class, () -> tasksService.submitTaskForCurrentStudent(
                task.getId(), "Done it", List.of(), aaravAuth));
        assertTrue(String.valueOf(e.getMessage()).toLowerCase().contains("closed"),
                "the pupil has to be told why, got: " + e.getMessage());
        assertTrue(submissionRepository.findByStudentIdAndTeacherTaskId(aarav.getId(), task.getId()).isEmpty());
    }

    @Test
    void closingTwiceIsRefused() {
        TeacherTask task = aTask(priya);
        tasksService.closeTask(task.getId(), priya);

        assertThrows(Exception.class, () -> tasksService.closeTask(task.getId(), priya));
    }

    @Test
    void aTaskClosedByMistakeCanBeReopened() {
        TeacherTask task = aTask(priya);
        tasksService.closeTask(task.getId(), priya);

        tasksService.reopenTask(task.getId(), priya);

        assertEquals("ACTIVE", reload(task.getId()).getTaskStatus());
        assertTrue(studentSees(task, "reopening has to put it back"));
    }

    @Test
    void reopeningATaskThatIsNotClosedIsRefused() {
        TeacherTask task = aTask(priya);
        assertThrows(Exception.class, () -> tasksService.reopenTask(task.getId(), priya));
    }

    // ── Delete ────────────────────────────────────────────────────────────────

    @Test
    void aTaskNobodyHasTouchedCanBeDeleted() {
        TeacherTask task = aTask(priya);

        tasksService.deleteTask(task.getId(), priya);

        assertTrue(teacherTaskRepository.findByIdAndTenantId(task.getId(), tenantId).isEmpty());
    }

    /**
     * The rule that protects work already done. Those submission rows carry XP a
     * child has been told they earned, and deleting the task would leave them
     * pointing at nothing.
     */
    @Test
    void aTaskWithHandInsCannotBeDeletedAndSaysWhatToDoInstead() {
        TeacherTask task = aTask(priya);
        tasksService.submitTaskForCurrentStudent(task.getId(), "Done it", List.of(), aaravAuth);

        Exception e = assertThrows(Exception.class, () -> tasksService.deleteTask(task.getId(), priya));

        assertTrue(String.valueOf(e.getMessage()).toLowerCase().contains("close"),
                "the refusal has to point at the action that does work, got: " + e.getMessage());
        assertFalse(teacherTaskRepository.findByIdAndTenantId(task.getId(), tenantId).isEmpty(),
                "and the task must survive");
        assertFalse(submissionRepository.findByStudentIdAndTeacherTaskId(aarav.getId(), task.getId()).isEmpty(),
                "as must the pupil's work");
    }

    // ── Who handed in ─────────────────────────────────────────────────────────

    @SuppressWarnings("unchecked")
    @Test
    void theSubmissionsViewListsTheWholeClassAndWhoHasHandedIn() {
        TeacherTask task = aTask(priya);
        tasksService.submitTaskForCurrentStudent(task.getId(), "Done it", List.of(), aaravAuth);

        Map<String, Object> view = (Map<String, Object>) tasksService.taskSubmissions(task.getId(), priya);

        assertEquals(2, view.get("expected"), "6-A has two pupils in it");
        assertEquals(1L, view.get("handedIn"));

        List<Map<String, Object>> rows = (List<Map<String, Object>>) view.get("rows");
        Map<String, Object> aaravRow = rows.stream()
                .filter(r -> "Aarav Verma".equals(r.get("studentName"))).findFirst().orElseThrow();
        Map<String, Object> nehaRow = rows.stream()
                .filter(r -> "Neha Verma".equals(r.get("studentName"))).findFirst().orElseThrow();

        assertEquals(Boolean.TRUE, aaravRow.get("handedIn"));
        assertEquals("PENDING", aaravRow.get("status"));
        assertEquals(Boolean.FALSE, nehaRow.get("handedIn"));
        assertEquals("NOT_SUBMITTED", nehaRow.get("status"),
                "a pupil who owes the work has to appear, or the view cannot answer who is outstanding");
    }

    /** It is the section's roster, not the grade's -- the point of P1-2. */
    @SuppressWarnings("unchecked")
    @Test
    void theRosterIsTheTasksOwnSection() {
        ClassSection sixB = new ClassSection();
        sixB.setId(UUID.randomUUID());
        sixB.setTenantId(tenantId);
        sixB.setAcademicYearId(yearId);
        sixB.setGradeName("Grade 6");
        sixB.setSectionName("B");
        sixB = classSectionRepository.saveAndFlush(sixB);
        Student inSixB = new Student();
        inSixB.setId(UUID.randomUUID());
        inSixB.setTenantId(tenantId);
        inSixB.setAcademicYearId(yearId);
        inSixB.setFirstName("Kabir");
        inSixB.setLastName("Rao");
        inSixB.setClassSection(sixB);
        studentRepository.saveAndFlush(inSixB);

        TeacherTask task = aTask(priya);
        Map<String, Object> view = (Map<String, Object>) tasksService.taskSubmissions(task.getId(), priya);

        List<Map<String, Object>> rows = (List<Map<String, Object>>) view.get("rows");
        assertEquals(2, rows.size(), "6-B is not who this was set for");
        assertTrue(rows.stream().noneMatch(r -> "Kabir Rao".equals(r.get("studentName"))));
    }

    @Test
    void anotherTeachersSubmissionsAreNotReadable() {
        TeacherTask task = aTask(priya);

        assertThrows(AccessDeniedException.class,
                () -> tasksService.taskSubmissions(task.getId(), rahul));
    }

    // ── Straight at the endpoints ─────────────────────────────────────────────

    private MvcResult call(String email, String role, String path, String body) throws Exception {
        var request = post(path).with(user(email).roles(role)).with(csrf());
        if (body != null) {
            request = request.contentType(MediaType.APPLICATION_JSON).content(body);
        }
        return mockMvc.perform(request).andReturn();
    }

    @Test
    void theCloseEndpointRefusesAnotherTeacher() throws Exception {
        TeacherTask task = aTask(priya);

        MvcResult result = call(rahulEmail, "TEACHER", "/api/teacher/tasks/" + task.getId() + "/close", null);

        assertEquals(403, result.getResponse().getStatus(),
                "a task id in a URL is not a permission");
        assertEquals("ACTIVE", reload(task.getId()).getTaskStatus());
    }

    @Test
    void theCloseEndpointWorksForTheOwner() throws Exception {
        TeacherTask task = aTask(priya);

        MvcResult result = call(priyaEmail, "TEACHER", "/api/teacher/tasks/" + task.getId() + "/close", null);

        assertTrue(result.getResponse().getStatus() < 400,
                "got " + result.getResponse().getStatus() + ": " + result.getResponse().getContentAsString());
        assertEquals("CLOSED", reload(task.getId()).getTaskStatus());
    }

    /**
     * ADMIN reaching these matters: /api/teacher/** is hasRole("TEACHER") and a
     * URL matcher beats every annotation, so without listing them an admin gets
     * a 403 that no annotation explains. That mismatch has been the real cause
     * of a reported bug three times now.
     */
    @Test
    void theEndpointsAreReachableByAnAdmin() throws Exception {
        TeacherTask task = aTask(priya);

        MvcResult result = call(adminEmail, "ADMIN", "/api/teacher/tasks/" + task.getId() + "/close", null);

        assertTrue(result.getResponse().getStatus() < 400,
                "an admin must not be refused by the URL rule, got " + result.getResponse().getStatus());
    }

    @Test
    void theUpdateEndpointEnforcesTheXpBound() throws Exception {
        TeacherTask task = aTask(priya);

        MvcResult result = call(priyaEmail, "TEACHER", "/api/teacher/tasks/" + task.getId() + "/update",
                "{\"title\":\"Edited\",\"description\":\"d\",\"xpReward\":-10}");

        assertTrue(result.getResponse().getStatus() >= 400,
                "the API must refuse -10 XP on an edit as well as on create, got "
                        + result.getResponse().getStatus());
        assertEquals(10, reload(task.getId()).getXpReward());
    }

    @Test
    void theSubmissionsEndpointAnswersForTheOwner() throws Exception {
        TeacherTask task = aTask(priya);

        MvcResult result = mockMvc.perform(
                        org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                                .get("/api/teacher/tasks/" + task.getId() + "/submissions")
                                .with(user(priyaEmail).roles("TEACHER")))
                .andReturn();

        assertEquals(200, result.getResponse().getStatus());
        assertTrue(result.getResponse().getContentAsString().contains("Aarav"),
                "the class roster has to come back: " + result.getResponse().getContentAsString());
    }

    private boolean studentSees(TeacherTask task, String why) {
        List<TeacherTask> visible = teacherTaskService.getTasksForStudent(
                aarav.getId(), 6, sixA.getId(), tenantId);
        return visible.stream().anyMatch(t -> t.getId().equals(task.getId()));
    }
}

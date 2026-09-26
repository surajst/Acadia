package com.concept.tasks;

import com.concept.assignment.app.SubjectAssignmentService;
import com.concept.shared.data.ClassSection;
import com.concept.shared.data.ClassSectionRepository;
import com.concept.tasks.app.CreateTaskRequest;
import com.concept.tasks.app.TasksService;
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
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.access.AccessDeniedException;
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
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Who set a task is a fact about a person, and it used to be stored as a hash of
 * their email address.
 *
 * <p>{@code teacher_tasks.created_by_teacher_id} held
 * {@code UUID.nameUUIDFromBytes(email)} with no foreign key, and both the
 * ownership check and the teacher's own task list keyed off it. So changing an
 * address silently handed every task that teacher had ever set to nobody: they
 * could no longer edit, close or delete their own work, and nothing anywhere said
 * why.
 *
 * <p>This was not hypothetical. V15 lowercased stored addresses, so every task set
 * before it by a user recorded in mixed case was already orphaned.
 *
 * <p>Capitalisation at sign-in was never the problem, which is worth pinning too:
 * {@code CustomUserDetailsService} normalises the typed address and then builds
 * the principal from {@code user.getEmail()}, the stored one. A test for it is
 * below, because that is a property of three collaborating classes and nothing
 * currently stops someone simplifying it away.
 */
@SpringBootTest
@TestPropertySource(properties = "app.dev-mode=true")
@Transactional
class TaskOwnershipSurvivesEmailChangeTest {

    @Autowired private TasksService tasksService;
    @Autowired private TeacherTaskRepository teacherTaskRepository;
    @Autowired private ClassSectionRepository classSectionRepository;
    @Autowired private SubjectAssignmentService assignmentService;
    @Autowired private TenantRepository tenantRepository;
    @Autowired private AcademicYearRepository academicYearRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private org.springframework.security.core.userdetails.UserDetailsService userDetailsService;

    private UUID tenantId;
    private UUID yearId;
    private ClassSection sixA;
    private User priya;

    @BeforeEach
    void setup() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);

        Tenant tenant = new Tenant();
        tenant.setId(UUID.randomUUID());
        tenant.setName("Demo SSC");
        tenant.setSubdomain("own-" + suffix);
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

        priya = person("priya-" + suffix + "@example.com", "Priya Sharma", UserRole.TEACHER);
        assignmentService.assignSubject(priya.getId(), sixA.getId(), "Mathematics", true, tenantId);
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

    /** What the JWT filter and the form login both end up producing. */
    private Authentication auth(String email, String role) {
        return new UsernamePasswordAuthenticationToken(email, null,
                List.of(new SimpleGrantedAuthority("ROLE_" + role)));
    }

    private TeacherTask setTask(Authentication who, String title) {
        CreateTaskRequest r = new CreateTaskRequest();
        r.setTitle(title);
        r.setDescription("Questions 1 to 5");
        r.setSubjectCode("MATHEMATICS");
        r.setTaskType("HOMEWORK");
        r.setAssignedToClass(true);
        r.setClassSectionId(sixA.getId());
        r.setXpReward(5);
        r.setDueDate(LocalDate.now().plusDays(6));
        return (TeacherTask) tasksService.createTask(r, who);
    }

    @SuppressWarnings("unchecked")
    private List<TeacherTask> myTasks(Authentication who) {
        return (List<TeacherTask>) tasksService.myTasks(who);
    }

    // ── The reported case ────────────────────────────────────────────────────

    @Test
    void aTeacherWhoseEmailChangesStillManagesHerTasks() {
        Authentication before = auth(priya.getEmail(), "TEACHER");
        TeacherTask task = setTask(before, "Fractions worksheet 4");
        // The precondition: she owns it now. Without this the assertions below
        // would pass just as well if createTask had failed to record an owner.
        assertDoesNotThrow(() -> tasksService.updateTask(task.getId(), "Worksheet 4a",
                "Still hers", LocalDate.now().plusDays(7), 6, before),
                "she must own her own task before an email change can be said to preserve it");

        // She marries, or the school moves domain.
        priya.setEmail("priya.sharma@newschool.example.com");
        userRepository.saveAndFlush(priya);
        Authentication after = auth(priya.getEmail(), "TEACHER");

        assertDoesNotThrow(() -> tasksService.updateTask(task.getId(), "Worksheet 4b",
                "Still hers", LocalDate.now().plusDays(8), 7, after),
                "changing an address must not hand a teacher's work to nobody");
        assertDoesNotThrow(() -> tasksService.closeTask(task.getId(), after));

        List<TeacherTask> hers = myTasks(after);
        assertFalse(hers.isEmpty(), "her own task list has to survive the change too");
        assertTrue(hers.stream().anyMatch(t -> t.getId().equals(task.getId())));
    }

    /**
     * The half that says the fix is about identity rather than about spelling: the
     * task must still belong to her, and not to anybody else who happens along.
     */
    @Test
    void anotherTeachersTaskIsStillNotHers() {
        User rahul = person("rahul-" + UUID.randomUUID().toString().substring(0, 8) + "@example.com",
                "Rahul Nair", UserRole.TEACHER);
        assignmentService.assignSubject(rahul.getId(), sixA.getId(), "Science", false, tenantId);

        TeacherTask hers = setTask(auth(priya.getEmail(), "TEACHER"), "Fractions worksheet 4");

        assertThrows(AccessDeniedException.class,
                () -> tasksService.updateTask(hers.getId(), "Mine now", "no",
                        LocalDate.now().plusDays(3), 5, auth(rahul.getEmail(), "TEACHER")));
    }

    // ── Capitalisation ───────────────────────────────────────────────────────

    /**
     * Signing in as PRIYA@... must reach the same person, and therefore the same
     * tasks, as priya@... The principal is built from the stored address rather
     * than the typed one, which is what makes this true -- and what this test is
     * really protecting.
     */
    @Test
    void aLoginWithDifferentCapitalisationOwnsTheSameTasks() {
        Authentication lower = auth(priya.getEmail(), "TEACHER");
        TeacherTask task = setTask(lower, "Fractions worksheet 4");

        // Through the real sign-in path, not by normalising the address here and
        // asserting on my own arithmetic. Both logins land in
        // CustomUserDetailsService, and the principal every later check sees is
        // whatever UserDetails.getUsername() returns -- so that is what this has to
        // use, or it proves nothing about how signing in actually behaves.
        String typedLoudly = priya.getEmail().toUpperCase(java.util.Locale.ROOT);
        String principal = userDetailsService.loadUserByUsername(typedLoudly).getUsername();
        assertEquals(priya.getEmail(), principal,
                "signing in as " + typedLoudly + " has to resolve to the stored address");

        Authentication shouting = auth(principal, "TEACHER");
        assertDoesNotThrow(() -> tasksService.updateTask(task.getId(), "Worksheet 4a",
                "Same person", LocalDate.now().plusDays(7), 6, shouting));

        List<TeacherTask> hers = myTasks(shouting);
        assertFalse(hers.isEmpty(), "the same person's task list, whatever they typed");
        assertTrue(hers.stream().anyMatch(t -> t.getId().equals(task.getId())));
    }

    // ── Rows the backfill could not place ────────────────────────────────────

    /**
     * A task whose owner cannot be recovered is left ownerless and reported, never
     * handed to a plausible teacher. Inventing a fact about who set a piece of work
     * is worse than admitting the fact is gone.
     *
     * <p>Standing in for what V24 leaves behind: a row whose hashed owner belongs
     * to an address no user has any more.
     */
    @Test
    void anUnmatchedRowIsNotQuietlyReassigned() {
        TeacherTask orphan = new TeacherTask();
        orphan.setId(UUID.randomUUID());
        orphan.setTenantId(tenantId);
        orphan.setAcademicYearId(yearId);
        orphan.setTitle("Set by someone who has left");
        orphan.setSubjectCode("MATHEMATICS");
        orphan.setTaskType(com.concept.tasks.data.TaskType.HOMEWORK);
        orphan.setStandard(6);
        orphan.setAssignedToClass(true);
        orphan.setClassSectionId(sixA.getId());
        orphan.setXpReward(5);
        orphan.setTaskStatus("ACTIVE");
        orphan.setCreatedAt(java.time.LocalDateTime.now());
        orphan.setCreatedByTeacherId(UUID.nameUUIDFromBytes("gone@example.com".getBytes()));
        orphan.setCreatedByUserId(null);
        teacherTaskRepository.saveAndFlush(orphan);

        assertNull(teacherTaskRepository.findById(orphan.getId()).orElseThrow().getCreatedByUserId(),
                "the row under test has to be ownerless, or it is testing nothing");

        // Not Priya's, even though she teaches the section it was set for.
        assertThrows(AccessDeniedException.class,
                () -> tasksService.updateTask(orphan.getId(), "Mine now", "no",
                        LocalDate.now().plusDays(3), 5, auth(priya.getEmail(), "TEACHER")));
        assertFalse(myTasks(auth(priya.getEmail(), "TEACHER")).stream()
                        .anyMatch(t -> t.getId().equals(orphan.getId())),
                "an ownerless task must not drift into somebody else's list");

        // But somebody can still clear it up.
        User admin = person("admin-" + UUID.randomUUID().toString().substring(0, 8) + "@example.com",
                "School Admin", UserRole.ADMIN);
        assertDoesNotThrow(() -> tasksService.closeTask(orphan.getId(),
                auth(admin.getEmail(), "ADMIN")),
                "an admin has to be able to deal with work nobody owns");
    }

    // ── The new column is actually populated ─────────────────────────────────

    @Test
    void settingATaskRecordsWhoSetIt() {
        TeacherTask task = setTask(auth(priya.getEmail(), "TEACHER"), "Fractions worksheet 4");

        TeacherTask stored = teacherTaskRepository.findById(task.getId()).orElseThrow();
        assertNotNull(stored.getCreatedByUserId(), "ownership has to be recorded to be checked");
        assertEquals(priya.getId(), stored.getCreatedByUserId());
        // And the old column stays populated for one release, so a rollback has
        // something to read.
        assertEquals(UUID.nameUUIDFromBytes(priya.getEmail().getBytes()),
                stored.getCreatedByTeacherId());
    }
}

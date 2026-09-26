package com.concept.tasks;

import com.concept.assignment.app.SubjectAssignmentService;
import com.concept.notification.app.NotificationService;
import com.concept.notification.data.Notification;
import com.concept.notification.data.NotificationRepository;
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
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * R3-P1-3. "Waiting for you" on the app's home screen listed homework that had
 * already been handed in, and tasks that had since been deleted -- and the bell
 * counted both.
 *
 * <p>The cause was that raising a notification was only ever half the story:
 * something created a TASK row when work was set, and nothing ever retired one.
 * The only way a row stopped being unread was a child tapping it, so a pupil who
 * had done everything asked of them still had a list of things to do, and could
 * only clear it by opening each item in turn.
 *
 * <p>Driven through {@link NotificationService}, which is what the app's
 * {@code /api/notifications} and {@code /unread-count} actually call: the list the
 * strip renders and the number on the bell come from two different queries, and a
 * fix that cleaned up one of them would leave a bell reading 3 over an empty
 * strip. Both are asserted every time for that reason.
 */
@SpringBootTest
@TestPropertySource(properties = "app.dev-mode=true")
@Transactional
class TaskNotificationRetirementTest {

    @Autowired private TasksService tasksService;
    @Autowired private NotificationService notificationService;
    @Autowired private NotificationRepository notificationRepository;
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
    private Student diya;
    private Authentication teacher;
    private Authentication aaravAuth;
    private Authentication diyaAuth;

    @BeforeEach
    void setup() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);

        Tenant tenant = new Tenant();
        tenant.setId(UUID.randomUUID());
        tenant.setName("Demo SSC");
        tenant.setSubdomain("tnr-" + suffix);
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
        aarav = pupil("Aarav", "Verma", aaravUser.getId());
        aaravAuth = auth(aaravUser.getEmail(), "STUDENT");

        // A second pupil in the same section, so "handed in" can be told apart
        // from "everybody's copy went away".
        User diyaUser = person("diya-" + suffix + "@example.com", "Diya Rao", UserRole.STUDENT);
        diya = pupil("Diya", "Rao", diyaUser.getId());
        diyaAuth = auth(diyaUser.getEmail(), "STUDENT");
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

    private Student pupil(String first, String last, UUID userId) {
        Student s = new Student();
        s.setId(UUID.randomUUID());
        s.setTenantId(tenantId);
        s.setAcademicYearId(yearId);
        s.setFirstName(first);
        s.setLastName(last);
        s.setClassSection(sixA);
        s.setUserId(userId);
        return studentRepository.saveAndFlush(s);
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

    /** What the strip would draw: the unread rows about this task, for this pupil. */
    private long unreadAbout(Authentication who, UUID taskId) {
        return notificationService.list(who).stream()
                .map(Notification.class::cast)
                .filter(n -> !n.isRead() && taskId.equals(n.getRelatedEntityId()))
                .count();
    }

    /** What the bell would draw. A different query, so it is asserted separately. */
    private long bell(Authentication who) {
        return ((Number) notificationService.unreadCount(who).get("count")).longValue();
    }

    /**
     * The precondition every test below needs: the row this is about exists and is
     * unread right now.
     *
     * <p>Without it, "afterwards there are none" passes just as happily when there
     * were none to begin with -- if taskAssigned stopped raising notifications, or
     * the section filter stopped matching this pupil, every retirement test here
     * would go green over an empty list. A test whose subject can silently be
     * absent is not testing anything.
     */
    private void assertWaiting(Authentication who, UUID taskId, String whose) {
        assertEquals(1, unreadAbout(who, taskId),
                whose + " should have exactly one unread row about this task before anything "
                        + "retires it -- with none, the assertions that follow prove nothing");
    }

    // ── The notification has to exist before any of this means anything ───────

    @Test
    void settingWorkTellsThePupilItIsWaiting() {
        TeacherTask task = setTask("Fractions worksheet 4");

        assertEquals(1, unreadAbout(aaravAuth, task.getId()),
                "without this row the rest of these tests would pass over an empty list");
        assertEquals(1, bell(aaravAuth));
    }

    // ── Handing in ───────────────────────────────────────────────────────────

    /** The reported case. */
    @Test
    void handingWorkInStopsTheHomeScreenSayingItIsWaiting() {
        TeacherTask task = setTask("Fractions worksheet 4");
        assertWaiting(aaravAuth, task.getId(), "Aarav");

        tasksService.submitTaskForCurrentStudent(task.getId(), "Did 1 to 5", List.of(), aaravAuth);

        assertEquals(0, unreadAbout(aaravAuth, task.getId()),
                "the work is with the teacher, so it is no longer waiting for Aarav");
        assertEquals(0, bell(aaravAuth), "the bell reads the same fact and must agree");
    }

    /**
     * The half that a careless fix breaks. Retiring by task rather than by pupil
     * would clear Diya's row too, and she has not handed anything in.
     */
    @Test
    void onePupilHandingInLeavesTheRestOfTheClassStillTold() {
        TeacherTask task = setTask("Fractions worksheet 4");
        assertWaiting(diyaAuth, task.getId(), "Diya");

        tasksService.submitTaskForCurrentStudent(task.getId(), "Did 1 to 5", List.of(), aaravAuth);

        assertEquals(1, unreadAbout(diyaAuth, task.getId()),
                "Diya still has this to do, and the only place that says so is her home screen");
        assertEquals(1, bell(diyaAuth));
    }

    // ── Closing and deleting ─────────────────────────────────────────────────

    @Test
    void closingATaskRetiresItForEveryoneItWasSetFor() {
        TeacherTask task = setTask("Fractions worksheet 4");
        assertWaiting(aaravAuth, task.getId(), "Aarav");
        assertWaiting(diyaAuth, task.getId(), "Diya");

        tasksService.closeTask(task.getId(), teacher);

        assertEquals(0, unreadAbout(aaravAuth, task.getId()));
        assertEquals(0, unreadAbout(diyaAuth, task.getId()),
                "a closed task refuses hand-ins, so it is waiting for nobody");
        assertEquals(0, bell(aaravAuth));
        assertEquals(0, bell(diyaAuth));
    }

    /** Here the row pointed at a task that no longer existed at all. */
    @Test
    void deletingATaskRetiresItForEveryoneItWasSetFor() {
        TeacherTask task = setTask("Fractions worksheet 4");
        assertWaiting(aaravAuth, task.getId(), "Aarav");
        assertWaiting(diyaAuth, task.getId(), "Diya");

        tasksService.deleteTask(task.getId(), teacher);

        assertEquals(0, unreadAbout(aaravAuth, task.getId()));
        assertEquals(0, unreadAbout(diyaAuth, task.getId()));
    }

    /**
     * Reopening does not announce the task a second time.
     *
     * <p>It would be easy to make closing and reopening symmetrical, but the
     * notification says "New task" -- and un-reading it would also un-read rows
     * pupils had genuinely dealt with before the task was ever closed.
     */
    @Test
    void reopeningDoesNotRaiseTheOldNotificationAgain() {
        TeacherTask task = setTask("Fractions worksheet 4");
        assertWaiting(aaravAuth, task.getId(), "Aarav");
        tasksService.closeTask(task.getId(), teacher);

        tasksService.reopenTask(task.getId(), teacher);

        assertEquals(0, unreadAbout(aaravAuth, task.getId()));
    }

    // ── Nothing else gets swept up ───────────────────────────────────────────

    /**
     * The failure mode of a fix like this is over-reach: clearing more than the
     * one thing that settled. An overdue task a child has not done is exactly
     * what "Waiting for you" is for.
     */
    @Test
    void otherTasksAreLeftWaiting() {
        TeacherTask done = setTask("Fractions worksheet 4");
        TeacherTask notDone = setTask("Reading log");
        assertWaiting(aaravAuth, done.getId(), "Aarav");
        assertWaiting(aaravAuth, notDone.getId(), "Aarav");

        tasksService.submitTaskForCurrentStudent(done.getId(), "Did 1 to 5", List.of(), aaravAuth);

        assertEquals(0, unreadAbout(aaravAuth, done.getId()));
        assertEquals(1, unreadAbout(aaravAuth, notDone.getId()),
                "only the task that was handed in should have been retired");
        assertEquals(1, bell(aaravAuth));
    }

    /** Retired means read, not gone: the pupil was told, and that stays on record. */
    @Test
    void aRetiredNotificationIsKeptAsHistoryRatherThanDeleted() {
        TeacherTask task = setTask("Fractions worksheet 4");
        assertWaiting(aaravAuth, task.getId(), "Aarav");
        tasksService.submitTaskForCurrentStudent(task.getId(), "Did 1 to 5", List.of(), aaravAuth);

        List<Notification> about = notificationRepository
                .findByRecipientIdOrderByCreatedAtDesc(aarav.getUserId()).stream()
                .filter(n -> task.getId().equals(n.getRelatedEntityId()))
                .toList();

        assertFalse(about.isEmpty(), "the row should still be there, just read");
        assertTrue(about.stream().allMatch(Notification::isRead));
        assertTrue(about.stream().anyMatch(n -> n.getTitle().contains("Fractions worksheet 4")));
    }
}

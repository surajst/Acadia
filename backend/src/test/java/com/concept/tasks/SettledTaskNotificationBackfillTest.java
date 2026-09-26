package com.concept.tasks;

import com.concept.tenant.AcademicYear;
import com.concept.tenant.AcademicYearRepository;
import com.concept.tenant.Tenant;
import com.concept.tenant.TenantRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * V22, run as the file that ships rather than as a copy of its logic.
 *
 * <p>{@link TaskNotificationRetirementTest} covers the code that retires a task
 * notification from here on. It cannot help the rows already sitting unread: the
 * screen QA reported is full of notifications about work handed in, and tasks
 * deleted, before any of that code existed. Those rows are only reachable by a
 * migration, and a migration that silently matches nothing looks exactly like one
 * that worked.
 *
 * <p>So each of the three reasons a row should be retired is set up here as data
 * the old code would have left behind, and each is required to be cleared -- and,
 * just as importantly, the rows that are genuinely still waiting are required to
 * survive. An overdue task a child has not done is what "Waiting for you" is for;
 * a backfill that swept those away would read as the notification system going
 * quiet.
 *
 * <p>The H2 file is the one executed. This migration is deliberately written to be
 * the same text in both folders -- no DO block, no UPDATE ... FROM, and the
 * updated table named rather than aliased, which H2 and Postgres read alike -- and
 * the Postgres copy is applied against a real Postgres by the postgres-migrations
 * CI job. A migration that fails to parse takes production down on the next
 * deploy, so neither vendor's file is left unrun.
 */
@SpringBootTest
@TestPropertySource(properties = "app.dev-mode=true")
@Transactional
class SettledTaskNotificationBackfillTest {

    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private TenantRepository tenantRepository;
    @Autowired private AcademicYearRepository academicYearRepository;

    private UUID tenantId;
    private UUID yearId;
    private UUID sectionId;

    @BeforeEach
    void setup() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);

        Tenant tenant = new Tenant();
        tenant.setId(UUID.randomUUID());
        tenant.setName("Demo SSC");
        tenant.setSubdomain("v22-" + suffix);
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

        sectionId = UUID.randomUUID();
        jdbcTemplate.update(
                "insert into class_sections (id, tenant_id, academic_year_id, grade_name,"
                        + " section_name, total_capacity) values (?, ?, ?, 'Grade 6', 'A', 40)",
                sectionId, tenantId, yearId);
    }

    private void runV22() throws IOException {
        String sql = new String(
                new ClassPathResource("db/migration/h2/V22__retire_settled_task_notifications.sql")
                        .getInputStream().readAllBytes(),
                StandardCharsets.UTF_8);
        jdbcTemplate.execute(sql);
    }

    /** A pupil with a login, since a notification is addressed to the user id. */
    private UUID pupil(String name) {
        UUID userId = UUID.randomUUID();
        jdbcTemplate.update(
                "insert into users (id, tenant_id, academic_year_id, email, password_hash,"
                        + " full_name, role, is_active) values (?, ?, ?, ?, 'x', ?, 'STUDENT', true)",
                userId, tenantId, yearId, name + "-" + userId + "@example.com", name);
        jdbcTemplate.update(
                "insert into students (id, tenant_id, academic_year_id, class_section_id,"
                        + " first_name, last_name, user_id) values (?, ?, ?, ?, ?, 'Test', ?)",
                UUID.randomUUID(), tenantId, yearId, sectionId, name, userId);
        return userId;
    }

    private UUID studentIdOf(UUID userId) {
        return jdbcTemplate.queryForObject(
                "select id from students where user_id = ?", UUID.class, userId);
    }

    private UUID task(String title, String status) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update(
                "insert into teacher_tasks (id, tenant_id, academic_year_id, class_section_id,"
                        + " created_by_teacher_id, assigned_to_class, standard, xp_reward,"
                        + " created_at, subject_type, task_status, task_type, title)"
                        + " values (?, ?, ?, ?, ?, true, 6, 5, current_timestamp, 'MATH', ?,"
                        + " 'HOMEWORK', ?)",
                id, tenantId, yearId, sectionId, UUID.randomUUID(), status, title);
        return id;
    }

    /** An unread "New task" row, exactly as NotificationPublisher would have left it. */
    private UUID notification(UUID recipientUserId, UUID taskId, String title) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update(
                "insert into notifications (id, tenant_id, academic_year_id, recipient_id,"
                        + " recipient_role, type, related_entity_id, title, body, read, created_at)"
                        + " values (?, ?, ?, ?, 'STUDENT', 'TASK', ?, ?, 'tap to open and hand it in',"
                        + " false, current_timestamp)",
                id, tenantId, yearId, recipientUserId, taskId, title);
        return id;
    }

    private void submission(UUID studentId, UUID taskId, String status) {
        jdbcTemplate.update(
                "insert into academic_submissions (id, student_id, teacher_task_id, skill_name,"
                        + " xp_bounty, status, submitted_at)"
                        + " values (?, ?, ?, 'Homework', 5, ?, current_timestamp)",
                UUID.randomUUID(), studentId, taskId, status);
    }

    private boolean isRead(UUID notificationId) {
        return Boolean.TRUE.equals(jdbcTemplate.queryForObject(
                "select read from notifications where id = ?", Boolean.class, notificationId));
    }

    // ── The three reasons a row is settled ───────────────────────────────────

    @Test
    void aNotificationForAHandedInTaskIsRetired() throws IOException {
        UUID aarav = pupil("Aarav");
        UUID taskId = task("Fractions worksheet 4", "ACTIVE");
        UUID row = notification(aarav, taskId, "New task: Fractions worksheet 4");
        submission(studentIdOf(aarav), taskId, "PENDING");

        runV22();

        assertTrue(isRead(row), "the work is with the teacher, so it is not waiting for Aarav");
    }

    @Test
    void aNotificationForAnApprovedTaskIsRetired() throws IOException {
        UUID aarav = pupil("Aarav");
        UUID taskId = task("Fractions worksheet 4", "ACTIVE");
        UUID row = notification(aarav, taskId, "New task: Fractions worksheet 4");
        submission(studentIdOf(aarav), taskId, "APPROVED");

        runV22();

        assertTrue(isRead(row));
    }

    @Test
    void aNotificationForAClosedTaskIsRetired() throws IOException {
        UUID aarav = pupil("Aarav");
        UUID taskId = task("Fractions worksheet 4", "CLOSED");
        UUID row = notification(aarav, taskId, "New task: Fractions worksheet 4");

        runV22();

        assertTrue(isRead(row), "a closed task refuses hand-ins, so it is waiting for nobody");
    }

    /** The one where the row pointed at nothing at all. */
    @Test
    void aNotificationForADeletedTaskIsRetired() throws IOException {
        UUID aarav = pupil("Aarav");
        UUID row = notification(aarav, UUID.randomUUID(), "New task: a task since deleted");

        runV22();

        assertTrue(isRead(row));
    }

    // ── And the rows that are genuinely still waiting ────────────────────────

    @Test
    void aNotificationForWorkStillToDoSurvives() throws IOException {
        UUID aarav = pupil("Aarav");
        UUID taskId = task("Reading log", "ACTIVE");
        UUID row = notification(aarav, taskId, "New task: Reading log");

        runV22();

        assertTrue(!isRead(row), "Aarav has not done this, and his home screen is where it says so");
    }

    /**
     * An overdue task is the one a child most needs to still see, so being past
     * its due date is not a reason to retire anything.
     */
    @Test
    void aNotificationForAnOverdueTaskSurvives() throws IOException {
        UUID aarav = pupil("Aarav");
        UUID taskId = task("Reading log", "OVERDUE");
        UUID row = notification(aarav, taskId, "New task: Reading log");

        runV22();

        assertTrue(!isRead(row));
    }

    /**
     * Work that was sent back is an invitation to try again, so that pupil's task
     * really is still waiting for them.
     */
    @Test
    void aNotificationForRejectedWorkSurvives() throws IOException {
        UUID aarav = pupil("Aarav");
        UUID taskId = task("Fractions worksheet 4", "ACTIVE");
        UUID row = notification(aarav, taskId, "New task: Fractions worksheet 4");
        submission(studentIdOf(aarav), taskId, "REJECTED");

        runV22();

        assertTrue(!isRead(row), "sent back means do it again, which means it is still waiting");
    }

    /**
     * The link runs recipient_id -> users.id -> students.user_id -> submissions,
     * and getting it wrong in either direction is easy. Diya has handed nothing
     * in; her row must not be cleared by Aarav's submission.
     */
    @Test
    void oneClassmatesSubmissionDoesNotRetireAnother() throws IOException {
        UUID aarav = pupil("Aarav");
        UUID diya = pupil("Diya");
        UUID taskId = task("Fractions worksheet 4", "ACTIVE");
        UUID aaravsRow = notification(aarav, taskId, "New task: Fractions worksheet 4");
        UUID diyasRow = notification(diya, taskId, "New task: Fractions worksheet 4");
        submission(studentIdOf(aarav), taskId, "PENDING");

        runV22();

        assertTrue(isRead(aaravsRow));
        assertTrue(!isRead(diyasRow), "Diya still has this to do");
    }

    /** Only TASK rows. A fee reminder is not settled by anything here. */
    @Test
    void otherKindsOfNotificationAreUntouched() throws IOException {
        UUID aarav = pupil("Aarav");
        UUID feeRow = UUID.randomUUID();
        jdbcTemplate.update(
                "insert into notifications (id, tenant_id, academic_year_id, recipient_id,"
                        + " recipient_role, type, related_entity_id, title, read, created_at)"
                        + " values (?, ?, ?, ?, 'PARENT', 'FEE', ?, 'Fees due: 450', false,"
                        + " current_timestamp)",
                feeRow, tenantId, yearId, aarav, UUID.randomUUID());

        runV22();

        assertTrue(!isRead(feeRow), "an unpaid invoice is not settled by a task being handed in");
    }

    /** A row somebody had already dealt with stays as it was. */
    @Test
    void anAlreadyReadRowIsLeftAlone() throws IOException {
        UUID aarav = pupil("Aarav");
        UUID taskId = task("Reading log", "ACTIVE");
        UUID row = notification(aarav, taskId, "New task: Reading log");
        jdbcTemplate.update("update notifications set read = true where id = ?", row);

        runV22();

        assertTrue(isRead(row));
        assertEquals(1, jdbcTemplate.queryForObject(
                "select count(*) from notifications where id = ?", Integer.class, row));
    }

    /** Production is Postgres; a version present in one folder only fails startup. */
    @Test
    void bothVendorFilesShip() {
        assertTrue(new ClassPathResource(
                "db/migration/h2/V22__retire_settled_task_notifications.sql").exists());
        assertTrue(new ClassPathResource(
                        "db/migration/postgresql/V22__retire_settled_task_notifications.sql").exists(),
                "production is Postgres; a missing file there is a failed deploy");
    }
}

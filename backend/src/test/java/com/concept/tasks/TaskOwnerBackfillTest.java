package com.concept.tasks;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import db.migration.common.V24__Backfill_task_owner;
import org.flywaydb.core.api.configuration.Configuration;
import org.flywaydb.core.api.migration.Context;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Transactional;

import javax.sql.DataSource;
import java.sql.Connection;
import java.time.LocalDate;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * V24, run as the class that ships rather than as a copy of its logic.
 *
 * <p>Flyway runs it once, on a database with no rows in it, so every real test of
 * what it does has to invoke it directly. The three cases it has to tell apart:
 *
 * <ol>
 *   <li>the old column holds {@code nameUUIDFromBytes(email)} -- what
 *       TeacherTaskService has always written;</li>
 *   <li>the old column holds a real {@code users.id} -- what ScreenContentSeeder
 *       writes, and why seeded demo tasks never appeared in a teacher's own list;</li>
 *   <li>the old column holds a hash of an address nobody has any more -- which V15
 *       created wholesale by lowercasing stored addresses.</li>
 * </ol>
 *
 * <p>The third is the one with teeth. It must be left alone <em>and</em> said out
 * loud: a migration that silently drops rows it cannot place is indistinguishable
 * from one that had nothing to do, and the rows it cannot place are exactly the
 * work some teacher has already lost.
 */
@SpringBootTest
@TestPropertySource(properties = "app.dev-mode=true")
@Transactional
class TaskOwnerBackfillTest {

    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private DataSource dataSource;

    private ListAppender<ILoggingEvent> logged;
    private ch.qos.logback.classic.Logger migrationLog;

    private UUID tenantId;
    private UUID yearId;

    @BeforeEach
    void setup() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        tenantId = UUID.randomUUID();
        yearId = UUID.randomUUID();

        jdbcTemplate.update(
                "insert into tenants (id, name, subdomain, is_active, created_at, onboarding_completed)"
                        + " values (?, 'Demo SSC', ?, true, current_timestamp, true)",
                tenantId, "v24-" + suffix);
        jdbcTemplate.update(
                "insert into academic_years (id, tenant_id, name, start_date, end_date, is_current)"
                        + " values (?, ?, '2026-27', ?, ?, true)",
                yearId, tenantId, LocalDate.now().minusMonths(3), LocalDate.now().plusMonths(9));

        migrationLog = (ch.qos.logback.classic.Logger)
                LoggerFactory.getLogger(V24__Backfill_task_owner.class);
        logged = new ListAppender<>();
        logged.start();
        migrationLog.addAppender(logged);
    }

    @AfterEach
    void tearDown() {
        if (migrationLog != null && logged != null) {
            migrationLog.detachAppender(logged);
        }
    }

    private UUID teacher(String email) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update(
                "insert into users (id, tenant_id, academic_year_id, email, password_hash,"
                        + " full_name, role, is_active) values (?, ?, ?, ?, 'x', ?, 'TEACHER', true)",
                id, tenantId, yearId, email, email);
        return id;
    }

    /** A task with the old column set to whatever a past writer put there. */
    private UUID task(String title, UUID legacyOwner) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update(
                "insert into teacher_tasks (id, tenant_id, academic_year_id, created_by_teacher_id,"
                        + " created_by_user_id, title, subject_type, task_type, standard, xp_reward,"
                        + " assigned_to_class, task_status, created_at)"
                        + " values (?, ?, ?, ?, null, ?, 'MATHEMATICS', 'HOMEWORK', 6, 5, true,"
                        + " 'ACTIVE', current_timestamp)",
                id, tenantId, yearId, legacyOwner, title);
        return id;
    }

    private UUID ownerOf(UUID taskId) {
        String raw = jdbcTemplate.queryForObject(
                "select cast(created_by_user_id as varchar) from teacher_tasks where id = ?",
                String.class, taskId);
        return raw == null ? null : UUID.fromString(raw);
    }

    /**
     * Invokes the shipped migration against this test's own connection.
     *
     * <p>DataSourceUtils, not dataSource.getConnection(): this test is
     * @Transactional and its rows are not committed, so a fresh connection sees an
     * empty database and the migration reports nothing to do -- which looks exactly
     * like a pass for three of the tests below. It did, the first time I ran them.
     */
    private void runV24() throws Exception {
        Connection connection = org.springframework.jdbc.datasource.DataSourceUtils
                .getConnection(dataSource);
        try {
            new V24__Backfill_task_owner().migrate(new Context() {
                @Override public Configuration getConfiguration() { return null; }
                @Override public Connection getConnection() { return connection; }
            });
        } finally {
            org.springframework.jdbc.datasource.DataSourceUtils
                    .releaseConnection(connection, dataSource);
        }
    }

    private String warnings() {
        StringBuilder out = new StringBuilder();
        for (ILoggingEvent event : logged.list) {
            if (event.getLevel() == Level.WARN) {
                out.append(event.getFormattedMessage()).append('\n');
            }
        }
        return out.toString();
    }

    // ── The two things it can match ──────────────────────────────────────────

    @Test
    void aTaskOwnedByTheHashOfALiveAddressIsMatched() throws Exception {
        UUID priya = teacher("priya@example.com");
        UUID taskId = task("Fractions worksheet 4",
                UUID.nameUUIDFromBytes("priya@example.com".getBytes()));
        assertNull(ownerOf(taskId), "the row has to start ownerless, or this proves nothing");

        runV24();

        assertEquals(priya, ownerOf(taskId));
    }

    /**
     * ScreenContentSeeder writes the real users.id into the same column, which is
     * why the seeded demo tasks were invisible in the teacher's own task list: one
     * writer stored a hash, the other an id, and the reader only knew about hashes.
     */
    @Test
    void aTaskAlreadyHoldingARealUserIdIsMatchedToo() throws Exception {
        UUID priya = teacher("priya@example.com");
        UUID taskId = task("Seeded demo task", priya);
        assertNull(ownerOf(taskId));

        runV24();

        assertEquals(priya, ownerOf(taskId));
    }

    // ── The one it must not ──────────────────────────────────────────────────

    @Test
    void aTaskWhoseOwnerHasNoLiveAddressIsReportedAndLeftAlone() throws Exception {
        UUID priya = teacher("priya@example.com");
        // What V15 left behind: the address this was hashed from was rewritten to
        // lower case, so nothing hashes to it any more.
        UUID taskId = task("Set before V15",
                UUID.nameUUIDFromBytes("Priya@Example.com".getBytes()));

        runV24();

        assertNull(ownerOf(taskId), "an owner that cannot be recovered must not be invented");

        String warned = warnings();
        assertTrue(warned.contains(taskId.toString()),
                "the row has to be named, not just counted -- somebody has to go and fix it.\n"
                        + "Logged instead:\n" + warned);
        assertTrue(warned.contains("Set before V15"),
                "and named in a way a person can recognise:\n" + warned);
        // Priya is right there, teaches the subject, and is not the answer.
        assertTrue(!priya.equals(ownerOf(taskId)));
    }

    @Test
    void aMatchedRunSaysHowManyItPlaced() throws Exception {
        teacher("priya@example.com");
        task("One", UUID.nameUUIDFromBytes("priya@example.com".getBytes()));
        task("Two", UUID.nameUUIDFromBytes("priya@example.com".getBytes()));
        task("Three", UUID.nameUUIDFromBytes("vanished@example.com".getBytes()));

        runV24();

        boolean counted = logged.list.stream()
                .anyMatch(e -> e.getFormattedMessage().contains("2 matched to a user")
                        && e.getFormattedMessage().contains("1 left ownerless"));
        assertTrue(counted, "the counts are how anybody knows it did anything. Logged:\n"
                + logged.list.stream().map(ILoggingEvent::getFormattedMessage)
                        .reduce("", (a, b) -> a + b + "\n"));
    }

    // ── And it does not trample what is already there ────────────────────────

    @Test
    void aRowThatAlreadyHasAnOwnerIsLeftUntouched() throws Exception {
        UUID priya = teacher("priya@example.com");
        UUID rahul = teacher("rahul@example.com");
        UUID taskId = task("Already placed",
                UUID.nameUUIDFromBytes("priya@example.com".getBytes()));
        jdbcTemplate.update("update teacher_tasks set created_by_user_id = ? where id = ?",
                rahul, taskId);

        runV24();

        assertEquals(rahul, ownerOf(taskId),
                "the backfill only fills gaps; re-running it must not rewrite decisions");
        assertTrue(!priya.equals(ownerOf(taskId)));
    }
}

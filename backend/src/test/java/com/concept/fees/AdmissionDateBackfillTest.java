package com.concept.fees;

import com.concept.shared.data.ClassSection;
import com.concept.shared.data.ClassSectionRepository;
import com.concept.shared.data.Student;
import com.concept.shared.data.StudentRepository;
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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * V21, run as the file that ships rather than as a copy of its logic.
 *
 * <p>Riya Singh was created on 23 September and her Term 1 still reads "overdue
 * 01 Jun 2026", while "Fix due dates" correctly reports that nothing needs
 * changing. Both are true: her admission_date is NULL, and billingStartFor falls
 * back to the academic year's start when it is, so her due dates really are
 * consistent with her billing start -- that start is just derived from a year
 * instead of from her.
 *
 * <p>The finding suspected V18 had written the year start into the column. It had
 * not; nothing ever writes that value. The distinction decides the fix: a
 * migration keyed on {@code admission_date = year start} would match no rows and
 * report the same "nothing changed". So NULL is the case that matters, and these
 * tests cover it first.
 *
 * <p>Every date here is written and read as <em>text</em>, never through a Java
 * date type. Two of these tests first passed locally and failed in CI with
 * one-day shifts (an admission date of 2026-07-15 read back as 07-14, and the
 * academic year's start reading a day early), because JPA's LocalDate conversion
 * depends on the JVM and session timezone and CI runs UTC where this machine runs
 * IST. The Postgres job was immune to exactly the same bug because it compares
 * with psql as text, which is the lesson taken here: assert what the database
 * holds, not what a date type round-trips to.
 *
 * <p>The SQL is read off the classpath and executed, so this tests the shipped
 * file. It is the <em>H2</em> file: the Postgres one uses a DO block,
 * GET DIAGNOSTICS and UPDATE ... FROM, none of which H2 has, and it is verified
 * separately against a real Postgres in the database-backup CI job. A migration
 * that fails to parse takes production down on the next deploy, so neither
 * vendor's file is left unrun.
 */
@SpringBootTest
@TestPropertySource(properties = "app.dev-mode=true")
@Transactional
class AdmissionDateBackfillTest {

    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private StudentRepository studentRepository;
    @Autowired private ClassSectionRepository classSectionRepository;
    @Autowired private TenantRepository tenantRepository;
    @Autowired private AcademicYearRepository academicYearRepository;

    /** As text, because that is how every comparison below is made. */
    private static final String YEAR_START = "2026-06-01";
    private static final String JOINED = "2026-09-23";

    private UUID tenantId;
    private UUID yearId;
    private ClassSection section;

    @BeforeEach
    void setup() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);

        Tenant tenant = new Tenant();
        tenant.setId(UUID.randomUUID());
        tenant.setName("Demo SSC");
        tenant.setSubdomain("adm-" + suffix);
        tenant.setActive(true);
        tenant.setCreatedAt(Instant.now());
        tenantId = tenantRepository.saveAndFlush(tenant).getId();

        AcademicYear year = new AcademicYear();
        year.setId(UUID.randomUUID());
        year.setTenantId(tenantId);
        year.setName("2026-27");
        // Placeholders: start_date is overwritten by the literal below, and
        // end_date is not read by this migration at all.
        year.setStartDate(LocalDate.of(2027, 3, 31));
        year.setEndDate(LocalDate.of(2027, 3, 31));
        year.setCurrent(true);
        yearId = academicYearRepository.saveAndFlush(year).getId();
        // Written as a literal afterwards, so the stored value is exactly
        // YEAR_START rather than whatever JPA's timezone handling makes of it.
        jdbcTemplate.update("update academic_years set start_date = cast(? as date) where id = ?",
                YEAR_START, yearId);

        section = new ClassSection();
        section.setId(UUID.randomUUID());
        section.setTenantId(tenantId);
        section.setAcademicYearId(yearId);
        section.setGradeName("Grade 6");
        section.setSectionName("B");
        section = classSectionRepository.saveAndFlush(section);
    }

    /** The migration as shipped, not a paraphrase of it. */
    private void runV21() throws IOException {
        String sql = new String(
                new ClassPathResource("db/migration/h2/V21__admission_date_from_first_trace.sql")
                        .getInputStream().readAllBytes(),
                StandardCharsets.UTF_8);
        jdbcTemplate.execute(sql);
    }

    /** @param admissionDate an ISO date as text, or null for the gap case */
    private Student student(String firstName, String admissionDate) {
        Student s = new Student();
        s.setId(UUID.randomUUID());
        s.setTenantId(tenantId);
        s.setAcademicYearId(yearId);
        s.setFirstName(firstName);
        s.setLastName("Singh");
        s.setClassSection(section);
        Student saved = studentRepository.saveAndFlush(s);
        // Set through SQL for the same reason as the year start above.
        jdbcTemplate.update("update students set admission_date = cast(? as date) where id = ?",
                admissionDate, saved.getId());
        return saved;
    }

    /**
     * A student-scoped audit row, which is the only evidence of when anyone
     * joined. Inserted with SQL so created_at is exactly the given date at
     * midday -- far enough from either midnight that no timezone offset could
     * move it onto an adjacent day.
     *
     * @param on an ISO date as text
     */
    private void trace(UUID studentId, String action, String on) {
        jdbcTemplate.update(
                "insert into audit_logs (id, tenant_id, academic_year_id, action, entity_type,"
                        + " entity_id, summary, created_at)"
                        + " values (?, ?, ?, ?, 'Student', ?, 'test', cast(? as timestamp))",
                UUID.randomUUID(), tenantId, yearId, action, studentId, on + " 12:00:00");
    }

    /** As text: a LocalDate here is what shifted by a day between IST and UTC. */
    private String admissionDateOf(Student s) {
        studentRepository.flush();
        return jdbcTemplate.queryForObject(
                "select cast(admission_date as varchar) from students where id = ?",
                String.class, s.getId());
    }

    // ── The reported case ─────────────────────────────────────────────────────

    /**
     * Riya: no admission date at all, and the only trace of her is a fee schedule
     * being raised -- not a STUDENT_ADDED row, which is exactly why V18 skipped
     * her.
     */
    @Test
    void aNullAdmissionDateIsFilledFromTheEarliestStudentScopedAuditRow() throws IOException {
        Student riya = student("Riya", null);
        trace(riya.getId(), "FEE_SCHEDULE_GENERATED", JOINED);

        runV21();

        assertEquals(JOINED, admissionDateOf(riya),
                "the only evidence of when she joined is her first audit row");
    }

    /** And the finding's own hypothesis, which costs nothing to also handle. */
    @Test
    void anAdmissionDateEqualToTheYearStartIsAlsoCorrected() throws IOException {
        Student riya = student("Riya", YEAR_START);
        trace(riya.getId(), "STUDENT_UPDATED", JOINED);

        runV21();

        assertEquals(JOINED, admissionDateOf(riya));
    }

    /**
     * The earliest trace wins, not the latest -- a student updated in December
     * did not join in December.
     */
    @Test
    void theEarliestTraceIsTheOneUsed() throws IOException {
        Student riya = student("Riya", null);
        trace(riya.getId(), "STUDENT_UPDATED", "2026-12-04");
        trace(riya.getId(), "FEE_SCHEDULE_GENERATED", JOINED);
        trace(riya.getId(), "XP_AWARDED", "2026-10-01");

        runV21();

        assertEquals(JOINED, admissionDateOf(riya));
    }

    // ── What it must not touch ────────────────────────────────────────────────

    /**
     * A date somebody set by hand is an answer, not a gap. Overwriting it with a
     * guess derived from an audit row would be worse than the bug.
     */
    @Test
    void aDeliberatelySetAdmissionDateIsLeftAlone() throws IOException {
        String stated = "2026-07-15";
        Student aarav = student("Aarav", stated);
        trace(aarav.getId(), "STUDENT_UPDATED", "2026-11-02");

        runV21();

        assertEquals(stated, admissionDateOf(aarav),
                "an admission date that is neither NULL nor the year start was chosen by a person");
    }

    /**
     * Never move the date earlier. An earlier admission date bills a family
     * sooner, which is the opposite of this bug and worse than it.
     */
    @Test
    void theDateIsOnlyEverMovedForward() throws IOException {
        Student aarav = student("Aarav", null);
        // A trace from before the year even opened -- a row imported from a
        // previous year, or a clock problem.
        trace(aarav.getId(), "ROSTER_BULK_IMPORT", "2026-03-01");

        runV21();

        assertNull(admissionDateOf(aarav),
                "a trace older than the year start is no better than the year start, so leave it");
    }

    /**
     * A student who joined with the year is not a mid-year joiner, and billing
     * from the year start is correct for them.
     */
    @Test
    void aStudentWhoJoinedOnTheFirstDayIsUnchanged() throws IOException {
        Student kabir = student("Kabir", null);
        trace(kabir.getId(), "STUDENT_ADDED", YEAR_START);

        runV21();

        assertNull(admissionDateOf(kabir),
                "their first trace is the year start, so there is nothing to correct");
    }

    /**
     * No trace at all means the database does not know when this student joined.
     * Inventing a date would be a guess presented as a fact, so the row keeps its
     * NULL -- and these are the students the new Register Student field exists
     * for.
     */
    @Test
    void aStudentWithNoTraceKeepsItsNullRatherThanBeingGuessedAt() throws IOException {
        Student unknown = student("Unknown", null);

        runV21();

        assertNull(admissionDateOf(unknown));
    }

    /** Another student's audit rows are not evidence about this one. */
    @Test
    void oneStudentsTraceDoesNotSetAnothersDate() throws IOException {
        Student riya = student("Riya", null);
        Student aarav = student("Aarav", null);
        trace(riya.getId(), "FEE_SCHEDULE_GENERATED", JOINED);

        runV21();

        assertEquals(JOINED, admissionDateOf(riya));
        assertNull(admissionDateOf(aarav), "nothing was ever logged about Aarav");
    }

    /** An audit row about something else entirely must not be read as a student's. */
    @Test
    void anAuditRowForADifferentEntityTypeIsIgnored() throws IOException {
        Student riya = student("Riya", null);
        // Deliberately her id in the entity_id column of a row about an invoice.
        jdbcTemplate.update(
                "insert into audit_logs (id, tenant_id, academic_year_id, action, entity_type,"
                        + " entity_id, summary, created_at)"
                        + " values (?, ?, ?, 'FEE_INVOICE_CANCELLED', 'FeeInvoice', ?, 'test',"
                        + " cast(? as timestamp))",
                UUID.randomUUID(), tenantId, yearId, riya.getId(), JOINED + " 12:00:00");

        runV21();

        assertNull(admissionDateOf(riya), "entity_type has to be part of the match, not just the id");
    }

    /** Running it twice changes nothing the second time. */
    @Test
    void theMigrationIsIdempotent() throws IOException {
        Student riya = student("Riya", null);
        trace(riya.getId(), "FEE_SCHEDULE_GENERATED", JOINED);

        runV21();
        String afterFirst = admissionDateOf(riya);
        runV21();

        assertEquals(afterFirst, admissionDateOf(riya));
        assertEquals(JOINED, afterFirst);
    }

    /**
     * Both vendor files have to exist, or the schema drifts between H2 and
     * Postgres and the application refuses to start on whichever is behind.
     */
    @Test
    void bothVendorsHaveTheMigration() {
        assertTrue(new ClassPathResource(
                        "db/migration/h2/V21__admission_date_from_first_trace.sql").exists());
        assertTrue(new ClassPathResource(
                        "db/migration/postgresql/V21__admission_date_from_first_trace.sql").exists(),
                "production is Postgres; a missing file there is a failed deploy");
    }
}

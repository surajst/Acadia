package com.concept.fees;

import com.concept.common.AuditLog;
import com.concept.common.AuditLogRepository;
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
import java.time.LocalDateTime;
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
    @Autowired private AuditLogRepository auditLogRepository;

    private static final LocalDate YEAR_START = LocalDate.of(2026, 6, 1);
    private static final LocalDate JOINED = LocalDate.of(2026, 9, 23);

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
        year.setStartDate(YEAR_START);
        year.setEndDate(YEAR_START.plusYears(1).minusDays(1));
        year.setCurrent(true);
        yearId = academicYearRepository.saveAndFlush(year).getId();

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

    private Student student(String firstName, LocalDate admissionDate) {
        Student s = new Student();
        s.setId(UUID.randomUUID());
        s.setTenantId(tenantId);
        s.setAcademicYearId(yearId);
        s.setFirstName(firstName);
        s.setLastName("Singh");
        s.setClassSection(section);
        s.setAdmissionDate(admissionDate);
        return studentRepository.saveAndFlush(s);
    }

    /** A student-scoped audit row, which is the only evidence of when anyone joined. */
    private void trace(UUID studentId, String action, LocalDate on) {
        AuditLog row = new AuditLog();
        row.setId(UUID.randomUUID());
        row.setAction(action);
        row.setEntityType("Student");
        row.setEntityId(studentId);
        row.setSummary("test");
        row.setCreatedAt(on.atTime(9, 30));
        // AuditLog extends BaseTenantEntity, so both of these are NOT NULL.
        row.setTenantId(tenantId);
        row.setAcademicYearId(yearId);
        auditLogRepository.saveAndFlush(row);
    }

    private LocalDate admissionDateOf(Student s) {
        studentRepository.flush();
        return jdbcTemplate.queryForObject(
                "select admission_date from students where id = ?", LocalDate.class, s.getId());
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
        trace(riya.getId(), "STUDENT_UPDATED", LocalDate.of(2026, 12, 4));
        trace(riya.getId(), "FEE_SCHEDULE_GENERATED", JOINED);
        trace(riya.getId(), "XP_AWARDED", LocalDate.of(2026, 10, 1));

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
        LocalDate stated = LocalDate.of(2026, 7, 15);
        Student aarav = student("Aarav", stated);
        trace(aarav.getId(), "STUDENT_UPDATED", LocalDate.of(2026, 11, 2));

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
        trace(aarav.getId(), "ROSTER_BULK_IMPORT", LocalDate.of(2026, 3, 1));

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
        AuditLog notAStudent = new AuditLog();
        notAStudent.setId(UUID.randomUUID());
        notAStudent.setAction("FEE_INVOICE_CANCELLED");
        notAStudent.setEntityType("FeeInvoice");
        // Deliberately her id in the entity_id column of a row about an invoice.
        notAStudent.setEntityId(riya.getId());
        notAStudent.setSummary("test");
        notAStudent.setCreatedAt(JOINED.atTime(9, 30));
        notAStudent.setTenantId(tenantId);
        notAStudent.setAcademicYearId(yearId);
        auditLogRepository.saveAndFlush(notAStudent);

        runV21();

        assertNull(admissionDateOf(riya), "entity_type has to be part of the match, not just the id");
    }

    /** Running it twice changes nothing the second time. */
    @Test
    void theMigrationIsIdempotent() throws IOException {
        Student riya = student("Riya", null);
        trace(riya.getId(), "FEE_SCHEDULE_GENERATED", JOINED);

        runV21();
        LocalDate afterFirst = admissionDateOf(riya);
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

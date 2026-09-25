package com.concept.fees;

import com.concept.fees.app.FeePlanService;
import com.concept.fees.app.InvoiceScheduleService;
import com.concept.fees.data.FeeInvoice;
import com.concept.fees.data.FeeInvoiceRepository;
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
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * V18 gave existing students the admission date nothing had ever recorded, but
 * it says in its own last line that invoices already raised keep the dates they
 * were given. Those are the rows a family is actually looking at: Riya Singh's
 * Term 1 reads "overdue 01 Jun 2026" because it was raised counting from the
 * academic year's start, months before she was admitted.
 *
 * <p>Re-raising the schedule is not available -- generateForStudent refuses a
 * student who already has one, and rightly, since that would double the bill --
 * so the dates have to be corrected in place.
 *
 * <p>The property that matters most here is idempotence, because this is the
 * kind of fix somebody runs a second time when they are not sure the first one
 * worked. A run that corrects nothing has to say so rather than look identical
 * to a run that corrected everything.
 */
@SpringBootTest
@TestPropertySource(properties = "app.dev-mode=true")
@Transactional
class DueDateRecalculationTest {

    @Autowired private FeePlanService feePlanService;
    @Autowired private InvoiceScheduleService invoiceScheduleService;
    @Autowired private FeeInvoiceRepository feeInvoiceRepository;
    @Autowired private StudentRepository studentRepository;
    @Autowired private ClassSectionRepository classSectionRepository;
    @Autowired private TenantRepository tenantRepository;
    @Autowired private AcademicYearRepository academicYearRepository;
    @Autowired private UserRepository userRepository;

    private static final String GRADE = "Grade 6";

    private UUID tenantId;
    private UUID yearId;
    private LocalDate yearStart;
    private ClassSection section;
    private Authentication admin;

    @BeforeEach
    void setup() {
        tenantId = UUID.randomUUID();
        yearId = UUID.randomUUID();
        yearStart = LocalDate.of(2026, 4, 1);

        Tenant tenant = new Tenant();
        tenant.setId(tenantId);
        tenant.setName("Recalc School");
        tenant.setSubdomain("recalc-" + tenantId.toString().substring(0, 8));
        tenant.setActive(true);
        tenant.setCreatedAt(Instant.now());
        tenantRepository.saveAndFlush(tenant);

        AcademicYear year = new AcademicYear();
        year.setId(yearId);
        year.setTenantId(tenantId);
        year.setName("2026-27");
        year.setStartDate(yearStart);
        year.setEndDate(yearStart.plusYears(1).minusDays(1));
        year.setCurrent(true);
        academicYearRepository.saveAndFlush(year);

        section = new ClassSection();
        section.setId(UUID.randomUUID());
        section.setTenantId(tenantId);
        section.setAcademicYearId(yearId);
        section.setGradeName(GRADE);
        section.setSectionName("A");
        classSectionRepository.saveAndFlush(section);

        User head = new User();
        head.setId(UUID.randomUUID());
        head.setTenantId(tenantId);
        head.setAcademicYearId(yearId);
        head.setEmail("admin-" + UUID.randomUUID() + "@example.com");
        head.setPasswordHash("irrelevant");
        head.setFullName("Office Admin");
        head.setRole(UserRole.ADMIN);
        head = userRepository.saveAndFlush(head);
        admin = new UsernamePasswordAuthenticationToken(head.getEmail(), "n/a");

        feePlanService.savePlanApproved(GRADE, List.of(
                new FeePlanService.InstalmentSpec("Term 1", new BigDecimal("12000.00"), 0),
                new FeePlanService.InstalmentSpec("Term 2", new BigDecimal("9000.00"), 120),
                new FeePlanService.InstalmentSpec("Term 3", new BigDecimal("9000.00"), 240)
        ), tenantId, yearId, null);
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

    /**
     * Reproduce the shape of a pre-V18 row: the schedule was raised while the
     * student had no admission date, so every date counts from the year start.
     * Setting the admission date afterwards is what V18 did.
     */
    private Student billedBeforeAdmissionDatesExisted(String firstName, LocalDate realAdmission) {
        Student s = student(firstName, null);
        invoiceScheduleService.generateForStudent(s.getId(), tenantId, null, null, admin);
        s.setAdmissionDate(realAdmission);
        return studentRepository.saveAndFlush(s);
    }

    private List<FeeInvoice> invoicesFor(Student s) {
        return feeInvoiceRepository.findByTenantId(tenantId).stream()
                .filter(inv -> s.getId().equals(inv.getStudentId()))
                .sorted(Comparator.comparing(FeeInvoice::getDueDate))
                .toList();
    }

    // ── The reported case ─────────────────────────────────────────────────────

    /** Riya, admitted in September, billed from April. */
    @Test
    void datesRaisedFromTheYearStartAreRecountedFromTheStudentsOwnStartDate() {
        LocalDate admitted = LocalDate.of(2026, 9, 14);
        Student riya = billedBeforeAdmissionDatesExisted("Riya", admitted);

        // Before: Term 1 falls due on the day the year opened.
        assertEquals(yearStart, invoicesFor(riya).get(0).getDueDate(),
                "this is the state V18 left behind");

        var outcome = invoiceScheduleService.recalculateDueDates(tenantId, admin);

        assertEquals(3, outcome.invoicesCorrected());
        assertEquals(1, outcome.studentsAffected());
        List<FeeInvoice> after = invoicesFor(riya);
        assertEquals(admitted, after.get(0).getDueDate(), "Term 1 is due on her start date");
        assertEquals(admitted.plusDays(120), after.get(1).getDueDate());
        assertEquals(admitted.plusDays(240), after.get(2).getDueDate());
    }

    /** And she stops being overdue on a bill that had not been raised yet. */
    @Test
    void theCorrectedInvoiceIsNoLongerOverdueOnHerFirstDay() {
        LocalDate admitted = LocalDate.of(2026, 9, 14);
        Student riya = billedBeforeAdmissionDatesExisted("Riya", admitted);

        assertTrue(invoicesFor(riya).get(0).isOverdue(admitted),
                "the bug: overdue on the day she joined");

        invoiceScheduleService.recalculateDueDates(tenantId, admin);

        assertFalse(invoicesFor(riya).get(0).isOverdue(admitted),
                "her first instalment is due on her first day, not before it");
    }

    /** Amounts are not the subject of this fix and must not move. */
    @Test
    void onlyTheDatesMove() {
        Student riya = billedBeforeAdmissionDatesExisted("Riya", LocalDate.of(2026, 9, 14));
        List<BigDecimal> before = invoicesFor(riya).stream().map(FeeInvoice::getTotalAmount).toList();

        invoiceScheduleService.recalculateDueDates(tenantId, admin);

        assertEquals(before, invoicesFor(riya).stream().map(FeeInvoice::getTotalAmount).toList());
    }

    // ── Idempotence ───────────────────────────────────────────────────────────

    @Test
    void asecondRunCorrectsNothingAndSaysSo() {
        billedBeforeAdmissionDatesExisted("Riya", LocalDate.of(2026, 9, 14));

        var first = invoiceScheduleService.recalculateDueDates(tenantId, admin);
        var second = invoiceScheduleService.recalculateDueDates(tenantId, admin);

        assertEquals(3, first.invoicesCorrected());
        assertEquals(0, second.invoicesCorrected(),
                "running it twice must not look the same as running it once");
        assertEquals(0, second.studentsAffected());
        assertEquals(first.invoicesExamined(), second.invoicesExamined(),
                "it still looked at the same invoices");
    }

    /** A school whose dates were always right is told nothing changed. */
    @Test
    void aScheduleThatWasAlreadyCorrectIsLeftAlone() {
        Student ananya = student("Ananya", LocalDate.of(2026, 7, 1));
        invoiceScheduleService.generateForStudent(ananya.getId(), tenantId, null, null, admin);
        List<LocalDate> before = invoicesFor(ananya).stream().map(FeeInvoice::getDueDate).toList();

        var outcome = invoiceScheduleService.recalculateDueDates(tenantId, admin);

        assertEquals(0, outcome.invoicesCorrected());
        assertEquals(3, outcome.invoicesExamined());
        assertEquals(before, invoicesFor(ananya).stream().map(FeeInvoice::getDueDate).toList());
    }

    /**
     * A student admitted before the year opened still bills from the year, so
     * their dates were never wrong -- the same rule generateForStudent applies.
     * A recalculation that "corrected" these would move dates families have
     * already been given.
     */
    @Test
    void anAdmissionBeforeTheYearOpenedStillBillsFromTheYear() {
        Student early = billedBeforeAdmissionDatesExisted("Kabir", yearStart.minusMonths(2));

        var outcome = invoiceScheduleService.recalculateDueDates(tenantId, admin);

        assertEquals(0, outcome.invoicesCorrected());
        assertEquals(yearStart, invoicesFor(early).get(0).getDueDate());
    }

    // ── Scope ─────────────────────────────────────────────────────────────────

    /** Two students, one wrong: the run must not touch the other. */
    @Test
    void onlyTheStudentsWhoseDatesAreWrongAreTouched() {
        Student riya = billedBeforeAdmissionDatesExisted("Riya", LocalDate.of(2026, 9, 14));
        Student ananya = student("Ananya", null);
        invoiceScheduleService.generateForStudent(ananya.getId(), tenantId, null, null, admin);
        List<LocalDate> ananyaBefore = invoicesFor(ananya).stream().map(FeeInvoice::getDueDate).toList();

        var outcome = invoiceScheduleService.recalculateDueDates(tenantId, admin);

        assertEquals(1, outcome.studentsAffected());
        assertEquals(3, outcome.invoicesCorrected());
        assertEquals(6, outcome.invoicesExamined());
        assertEquals(ananyaBefore, invoicesFor(ananya).stream().map(FeeInvoice::getDueDate).toList(),
                "a student with no admission date recorded bills from the year, unchanged");
        assertEquals(LocalDate.of(2026, 9, 14), invoicesFor(riya).get(0).getDueDate());
    }

    /** Another school's invoices are not this school's to correct. */
    @Test
    void anotherTenantIsNotTouched() {
        Student riya = billedBeforeAdmissionDatesExisted("Riya", LocalDate.of(2026, 9, 14));

        var outcome = invoiceScheduleService.recalculateDueDates(UUID.randomUUID(), admin);

        assertEquals(0, outcome.invoicesExamined());
        assertEquals(yearStart, invoicesFor(riya).get(0).getDueDate(),
                "a run against another tenant must leave these alone");
    }

    /** A custom invoice has no instalment behind it, so there is no offset to recount. */
    @Test
    void aCustomInvoiceWithNoInstalmentIsIgnored() {
        Student riya = student("Riya", LocalDate.of(2026, 9, 14));
        FeeInvoice oneOff = new FeeInvoice();
        oneOff.setId(UUID.randomUUID());
        oneOff.setTenantId(tenantId);
        oneOff.setAcademicYearId(yearId);
        oneOff.setStudentId(riya.getId());
        oneOff.setSource("CUSTOM");
        oneOff.setInstalmentLabel("Annual trip");
        oneOff.setDueDate(LocalDate.of(2026, 5, 1));
        oneOff.setTotalAmount(new BigDecimal("1500.00"));
        oneOff.setAmountPaid(BigDecimal.ZERO);
        oneOff.updateBalances();
        feeInvoiceRepository.saveAndFlush(oneOff);

        var outcome = invoiceScheduleService.recalculateDueDates(tenantId, admin);

        assertEquals(0, outcome.invoicesExamined(), "a one-off charge is not part of any schedule");
        assertEquals(LocalDate.of(2026, 5, 1),
                feeInvoiceRepository.findByIdAndTenantId(oneOff.getId(), tenantId).orElseThrow().getDueDate());
    }
}

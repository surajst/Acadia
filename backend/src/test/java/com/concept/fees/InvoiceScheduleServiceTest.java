package com.concept.fees;

import com.concept.fees.app.FeePlanMissingException;
import com.concept.fees.app.FeePlanService;
import com.concept.fees.app.InvoiceScheduleService;
import com.concept.fees.data.FeeInvoice;
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
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Nobody pays three to five lakh at once. These pin the behaviour that makes
 * termly billing expressible: a plan carries a schedule, invoices carry due
 * dates, and the dates are resolved per student rather than read off a
 * hardcoded calendar.
 */
@SpringBootTest
@TestPropertySource(properties = "app.dev-mode=true")
@Transactional
public class InvoiceScheduleServiceTest {

    @Autowired private FeePlanService feePlanService;
    @Autowired private InvoiceScheduleService invoiceScheduleService;
    @Autowired private StudentRepository studentRepository;
    @Autowired private ClassSectionRepository classSectionRepository;
    @Autowired private TenantRepository tenantRepository;
    @Autowired private AcademicYearRepository academicYearRepository;

    private UUID tenantId;
    private UUID yearId;
    private LocalDate yearStart;
    private ClassSection section;

    private static final String GRADE = "Grade 6";

    @BeforeEach
    public void setup() {
        tenantId = UUID.randomUUID();
        yearId = UUID.randomUUID();
        yearStart = LocalDate.of(2026, 4, 1);

        Tenant tenant = new Tenant();
        tenant.setId(tenantId);
        tenant.setName("Plan School");
        tenant.setSubdomain("plan-" + tenantId.toString().substring(0, 8));
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
    }

    private Student student(LocalDate admissionDate) {
        Student s = new Student();
        s.setId(UUID.randomUUID());
        s.setTenantId(tenantId);
        s.setAcademicYearId(yearId);
        s.setFirstName("Test");
        s.setLastName("Student");
        s.setClassSection(section);
        s.setAdmissionDate(admissionDate);
        return studentRepository.saveAndFlush(s);
    }

    private void threeTermPlan() {
        feePlanService.savePlanApproved(GRADE, List.of(
                new FeePlanService.InstalmentSpec("Term 1", new BigDecimal("120000.00"), 0),
                new FeePlanService.InstalmentSpec("Term 2", new BigDecimal("90000.00"), 120),
                new FeePlanService.InstalmentSpec("Term 3", new BigDecimal("90000.00"), 240)
        ), tenantId, yearId, null);
    }

    @Test
    public void generate_raisesOneInvoicePerInstalment_withItsOwnDueDate() {
        threeTermPlan();
        Student s = student(null);

        List<FeeInvoice> invoices = invoiceScheduleService.generateForStudent(s.getId(), tenantId, null, null, null);

        assertEquals(3, invoices.size(), "a year is billed in parts, not as one lump");
        assertEquals(new BigDecimal("120000.00"), invoices.get(0).getTotalAmount());
        assertEquals(yearStart, invoices.get(0).getDueDate());
        assertEquals(yearStart.plusDays(120), invoices.get(1).getDueDate());
        assertEquals(yearStart.plusDays(240), invoices.get(2).getDueDate());
        assertEquals("Term 1", invoices.get(0).getInstalmentLabel());

        BigDecimal billed = invoices.stream().map(FeeInvoice::getTotalAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        assertEquals(new BigDecimal("300000.00"), billed, "the parts must add up to the year");
    }

    /**
     * The case a hardcoded calendar cannot express. A student admitted in
     * September must not owe an instalment that fell due in April -- billing a
     * family for a term their child was not enrolled in is the kind of error a
     * school hears about from a parent.
     */
    @Test
    public void generate_forAMidYearAdmission_billsFromTheAdmissionDate() {
        threeTermPlan();
        LocalDate admitted = LocalDate.of(2026, 9, 15);
        Student s = student(admitted);

        List<FeeInvoice> invoices = invoiceScheduleService.generateForStudent(s.getId(), tenantId, null, null, null);

        assertEquals(admitted, invoices.get(0).getDueDate(),
                "the first instalment falls due when they joined, not in April");
        assertEquals(admitted.plusDays(120), invoices.get(1).getDueDate());
        assertTrue(invoices.get(0).getDueDate().isAfter(yearStart));
    }

    /** An admission recorded before the year opened still bills from the year. */
    @Test
    public void generate_forAnEarlyAdmission_billsFromTheYearStart() {
        threeTermPlan();
        Student s = student(yearStart.minusMonths(2));

        List<FeeInvoice> invoices = invoiceScheduleService.generateForStudent(s.getId(), tenantId, null, null, null);

        assertEquals(yearStart, invoices.get(0).getDueDate());
    }

    @Test
    public void generate_withoutAPlan_refusesInsteadOfGuessing() {
        Student s = student(null);

        FeePlanMissingException e = assertThrows(FeePlanMissingException.class, () ->
                invoiceScheduleService.generateForStudent(s.getId(), tenantId, null, null, null));
        assertTrue(e.getMessage().contains(GRADE), e.getMessage());
    }

    /** Running it twice would double every family's bill. */
    @Test
    public void generate_twice_isRefused() {
        threeTermPlan();
        Student s = student(null);
        invoiceScheduleService.generateForStudent(s.getId(), tenantId, null, null, null);

        assertThrows(IllegalArgumentException.class, () ->
                invoiceScheduleService.generateForStudent(s.getId(), tenantId, null, null, null));
    }

    /** A concession scales the whole schedule, and the parts still sum exactly. */
    @Test
    public void generate_withOverride_scalesEveryInstalmentAndStillSumsExactly() {
        threeTermPlan();
        Student s = student(null);

        List<FeeInvoice> invoices = invoiceScheduleService.generateForStudent(
                s.getId(), tenantId, new BigDecimal("200000.00"), "Sibling discount", null);

        BigDecimal billed = invoices.stream().map(FeeInvoice::getTotalAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        assertEquals(new BigDecimal("200000.00"), billed,
                "a scaled schedule must still add up to what the family was told");
        assertTrue(invoices.get(0).isOverridden());
        assertEquals("Sibling discount", invoices.get(0).getOverrideReason());
    }

    @Test
    public void generate_withOverrideButNoReason_isRefused() {
        threeTermPlan();
        Student s = student(null);

        assertThrows(IllegalArgumentException.class, () ->
                invoiceScheduleService.generateForStudent(
                        s.getId(), tenantId, new BigDecimal("200000.00"), "  ", null));
    }

    /** A plan whose parts do not sum to its total would silently mis-bill all year. */
    @Test
    public void savePlan_setsTheAnnualTotalFromTheInstalments() {
        threeTermPlan();
        assertEquals(new BigDecimal("300000.00"),
                feePlanService.listPlans(tenantId, yearId).get(0).getAnnualAmount());
    }

    @Test
    public void savePlan_withNoInstalments_isRefused() {
        assertThrows(IllegalArgumentException.class, () ->
                feePlanService.savePlanApproved(GRADE, List.of(), tenantId, yearId, null));
    }

    /** Editing a plan replaces its schedule rather than accumulating rows. */
    @Test
    public void savePlan_replacesTheScheduleInsteadOfAppending() {
        threeTermPlan();
        feePlanService.savePlanApproved(GRADE, List.of(
                new FeePlanService.InstalmentSpec("Full year", new BigDecimal("280000.00"), 0)
        ), tenantId, yearId, null);

        UUID planId = feePlanService.listPlans(tenantId, yearId).get(0).getId();
        assertEquals(1, feePlanService.instalmentsOf(planId, tenantId).size());
        assertEquals(new BigDecimal("280000.00"),
                feePlanService.listPlans(tenantId, yearId).get(0).getAnnualAmount());
    }

    /** Two schools must be able to price the same grade differently. */
    @Test
    public void twoSchoolsCanPlanTheSameGradeDifferently() {
        threeTermPlan();

        UUID otherTenant = UUID.randomUUID();
        Tenant other = new Tenant();
        other.setId(otherTenant);
        other.setName("Other School");
        other.setSubdomain("other-" + otherTenant.toString().substring(0, 8));
        other.setActive(true);
        other.setCreatedAt(Instant.now());
        tenantRepository.saveAndFlush(other);

        UUID otherYear = UUID.randomUUID();
        AcademicYear y = new AcademicYear();
        y.setId(otherYear);
        y.setTenantId(otherTenant);
        y.setName("2026-27");
        y.setStartDate(yearStart);
        y.setEndDate(yearStart.plusYears(1));
        y.setCurrent(true);
        academicYearRepository.saveAndFlush(y);

        feePlanService.savePlanApproved(GRADE, List.of(
                new FeePlanService.InstalmentSpec("Annual", new BigDecimal("50000.00"), 0)
        ), otherTenant, otherYear, null);

        assertEquals(new BigDecimal("300000.00"),
                feePlanService.listPlans(tenantId, yearId).get(0).getAnnualAmount());
        assertEquals(new BigDecimal("50000.00"),
                feePlanService.listPlans(otherTenant, otherYear).get(0).getAnnualAmount());
    }

    @Test
    public void overdue_isFalseBeforeTheDueDateAndTrueAfter() {
        threeTermPlan();
        Student s = student(null);
        FeeInvoice first = invoiceScheduleService
                .generateForStudent(s.getId(), tenantId, null, null, null).get(0);

        assertFalse(first.isOverdue(yearStart.minusDays(1)));
        assertTrue(first.isOverdue(yearStart.plusDays(1)),
                "nothing could ask this before invoices carried a due date");
    }
}

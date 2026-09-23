package com.concept.fees;

import com.concept.fees.app.FeePlanService;
import com.concept.fees.app.InvoiceScheduleService;
import com.concept.fees.data.FeeInvoice;
import com.concept.fees.data.FeeInvoiceRepository;
import com.concept.roster.app.StudentAdminService;
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A student added today was overdue today.
 *
 * <p>students.admission_date has existed since V5 and nothing ever wrote it,
 * so InvoiceScheduleService always fell through to the academic year's start.
 * A child admitted in September was billed from June and landed on the
 * Defaulters list on their first day -- for instalments that fell due before
 * they were enrolled, and in direct contradiction of the Fee plans page, which
 * promises offsets counted "from each student's start date".
 */
@SpringBootTest
@TestPropertySource(properties = "app.dev-mode=true")
@Transactional
class AdmissionDateBillingTest {

    @Autowired private StudentAdminService studentAdminService;
    @Autowired private InvoiceScheduleService invoiceScheduleService;
    @Autowired private FeePlanService feePlanService;
    @Autowired private FeeInvoiceRepository feeInvoiceRepository;
    @Autowired private StudentRepository studentRepository;
    @Autowired private ClassSectionRepository classSectionRepository;
    @Autowired private TenantRepository tenantRepository;
    @Autowired private AcademicYearRepository academicYearRepository;

    private UUID tenantId;
    private UUID yearId;
    private ClassSection section;

    /** Far enough back that "today" is unambiguously mid-year. */
    private static final LocalDate YEAR_START = LocalDate.now().minusMonths(4);

    @BeforeEach
    void setup() {
        Tenant tenant = new Tenant();
        tenant.setId(UUID.randomUUID());
        tenant.setName("Demo SSC");
        tenant.setSubdomain("adm-" + UUID.randomUUID());
        tenant.setActive(true);
        tenant.setCreatedAt(Instant.now());
        tenantId = tenantRepository.saveAndFlush(tenant).getId();

        AcademicYear year = new AcademicYear();
        year.setId(UUID.randomUUID());
        year.setTenantId(tenantId);
        year.setName("2026-27");
        year.setStartDate(YEAR_START);
        year.setEndDate(YEAR_START.plusYears(1));
        year.setCurrent(true);
        yearId = academicYearRepository.saveAndFlush(year).getId();

        section = new ClassSection();
        section.setId(UUID.randomUUID());
        section.setTenantId(tenantId);
        section.setAcademicYearId(yearId);
        section.setGradeName("Grade 6");
        section.setSectionName("A");
        section = classSectionRepository.saveAndFlush(section);

        // Term 1 on day zero, Term 2 after 120 days -- the report's own plan.
        feePlanService.savePlanApproved("Grade 6", List.of(
                        new FeePlanService.InstalmentSpec("Term 1", new BigDecimal("8000"), 0),
                        new FeePlanService.InstalmentSpec("Term 2", new BigDecimal("8000"), 120)),
                tenantId, yearId, null);
    }

    private Student register(String first) {
        studentAdminService.addStudent(first, "Singh", "6A-" + UUID.randomUUID().toString().substring(0, 4),
                section.getId(), null, null, null, null, null, tenantId, yearId, null);
        return studentRepository.findByTenantId(tenantId).stream()
                .filter(s -> first.equals(s.getFirstName()))
                .findFirst().orElseThrow();
    }

    @Test
    void aStudentRegisteredTodayHasAnAdmissionDate() {
        Student riya = register("Riya");
        assertNotNull(riya.getAdmissionDate(),
                "nothing wrote this column, which is why every schedule counted from the year");
        assertEquals(LocalDate.now(), riya.getAdmissionDate());
    }

    /** The reported case: her first instalment must not already be overdue. */
    @Test
    void aMidYearAdmissionIsNotOverdueOnTheirFirstDay() {
        Student riya = register("Riya");
        invoiceScheduleService.generateForStudent(riya.getId(), tenantId, null, null, null);

        List<FeeInvoice> invoices = feeInvoiceRepository.findByStudentId(riya.getId());
        assertFalse(invoices.isEmpty(), "the plan should have raised instalments");

        LocalDate today = LocalDate.now();
        assertTrue(invoices.stream().noneMatch(i -> i.getDueDate().isBefore(today)),
                "no instalment may fall due before the child was admitted; got "
                        + invoices.stream().map(FeeInvoice::getDueDate).toList());
    }

    /** And the offsets still count, from the right date. */
    @Test
    void theInstalmentOffsetsRunFromTheAdmissionDate() {
        Student riya = register("Riya");
        invoiceScheduleService.generateForStudent(riya.getId(), tenantId, null, null, null);

        List<LocalDate> due = feeInvoiceRepository.findByStudentId(riya.getId()).stream()
                .map(FeeInvoice::getDueDate).sorted().toList();

        assertEquals(LocalDate.now(), due.get(0), "Term 1 is offset zero from admission");
        assertEquals(LocalDate.now().plusDays(120), due.get(1), "Term 2 is 120 days after that");
    }
}

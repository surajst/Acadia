package com.concept.fees;
import com.concept.fees.app.FeeManagementService;
import com.concept.fees.app.FeeStructureMissingException;
import com.concept.fees.data.FeeStructureRepository;
import com.concept.fees.data.FeeStructure;
import com.concept.fees.data.FeeInvoice;
import com.concept.shared.data.ClassSectionRepository;
import com.concept.shared.data.ClassSection;
import com.concept.shared.data.StudentRepository;
import com.concept.shared.data.Student;

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
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

// Covers the "missing production entity-creation path" fix: real schools
// previously had no way to bill a real student. Also covers the
// cross-tenant IDOR guard on createInvoiceForStudent.
@SpringBootTest
@TestPropertySource(properties = "app.dev-mode=true")
@Transactional
public class FeeInvoiceCreationTenantTest {

    @Autowired
    private FeeManagementService feeManagementService;

    @Autowired
    private StudentRepository studentRepository;

    @Autowired
    private ClassSectionRepository classSectionRepository;

    @Autowired
    private FeeStructureRepository feeStructureRepository;

    @Autowired
    private TenantRepository tenantRepository;

    @Autowired
    private AcademicYearRepository academicYearRepository;

    private UUID tenantA;
    private UUID tenantB;
    private UUID academicYearIdA;
    private Student studentA;

    private Tenant makeTenant() {
        Tenant tenant = new Tenant();
        tenant.setId(UUID.randomUUID());
        tenant.setName("Test Tenant " + tenant.getId());
        tenant.setSubdomain("test-" + tenant.getId());
        tenant.setActive(true);
        tenant.setCreatedAt(Instant.now());
        return tenantRepository.saveAndFlush(tenant);
    }

    private UUID makeAcademicYear(UUID tenantId) {
        AcademicYear year = new AcademicYear();
        year.setId(UUID.randomUUID());
        year.setTenantId(tenantId);
        year.setName("2026");
        year.setStartDate(LocalDate.of(2026, 1, 1));
        year.setEndDate(LocalDate.of(2026, 12, 31));
        year.setCurrent(true);
        return academicYearRepository.saveAndFlush(year).getId();
    }

    @BeforeEach
    public void setup() {
        tenantA = makeTenant().getId();
        tenantB = makeTenant().getId();
        UUID academicYearId = makeAcademicYear(tenantA);
        academicYearIdA = academicYearId;

        ClassSection classSection = new ClassSection();
        classSection.setId(UUID.randomUUID());
        classSection.setTenantId(tenantA);
        classSection.setAcademicYearId(academicYearId);
        classSection.setGradeName("Grade 1");
        classSection.setSectionName("A");
        classSectionRepository.saveAndFlush(classSection);

        studentA = new Student();
        studentA.setId(UUID.randomUUID());
        studentA.setTenantId(tenantA);
        studentA.setAcademicYearId(academicYearId);
        studentA.setFirstName("Test");
        studentA.setLastName("Student");
        studentA.setClassSection(classSection);
        studentRepository.saveAndFlush(studentA);
    }

    @Test
    public void createInvoiceForStudent_sameTenant_succeeds() {
        FeeStructure structure = new FeeStructure();
        structure.setId(UUID.randomUUID());
        structure.setTenantId(tenantA);
        structure.setAcademicYearId(studentA.getAcademicYearId());
        structure.setGradeLevel(studentA.getClassSection().getGradeName());
        structure.setTuitionFee(new BigDecimal("15000.00"));
        structure.setTermFee(new BigDecimal("5000.00"));
        feeStructureRepository.saveAndFlush(structure);

        FeeInvoice invoice = feeManagementService.createInvoiceForStudent(studentA.getId(), tenantA, null);

        assertNotNull(invoice.getId());
        assertEquals(tenantA, invoice.getTenantId());
        assertEquals(new BigDecimal("20000.00"), invoice.getTotalAmount());
        assertEquals(0, BigDecimal.ZERO.compareTo(invoice.getAmountPaid()));
    }

    /**
     * The behaviour this change is really about. Invoicing used to fall back to
     * a hardcoded 15000 + 5000 whenever no fee structure matched, so an
     * unconfigured school silently billed every family 20,000 and looked
     * correct doing it. Refusing is the honest outcome.
     */
    @Test
    public void createInvoiceForStudent_withoutFeeStructure_refusesInsteadOfGuessing() {
        FeeStructureMissingException e = assertThrows(FeeStructureMissingException.class, () ->
                feeManagementService.createInvoiceForStudent(studentA.getId(), tenantA, null));

        // The admin has to be able to act on this, so it names the grade.
        assertTrue(e.getMessage().contains(studentA.getClassSection().getGradeName()),
                "message should name the grade level, was: " + e.getMessage());
    }

    /**
     * grade_level used to be UNIQUE on its own, which on a multi-tenant table
     * meant the first school to configure "Grade 6" claimed it platform-wide
     * and the next school's insert failed. Two schools must be able to price
     * the same grade differently.
     */
    @Test
    public void twoSchoolsCanPriceTheSameGradeDifferently() {
        String sharedGrade = "Grade 6";

        studentA.getClassSection().setGradeName(sharedGrade);
        classSectionRepository.saveAndFlush(studentA.getClassSection());

        FeeStructure forA = new FeeStructure();
        forA.setId(UUID.randomUUID());
        forA.setTenantId(tenantA);
        forA.setAcademicYearId(studentA.getAcademicYearId());
        forA.setGradeLevel(sharedGrade);
        forA.setTuitionFee(new BigDecimal("12000.00"));
        forA.setTermFee(new BigDecimal("3000.00"));
        feeStructureRepository.saveAndFlush(forA);

        FeeStructure forB = new FeeStructure();
        forB.setId(UUID.randomUUID());
        forB.setTenantId(tenantB);
        forB.setAcademicYearId(UUID.randomUUID());
        forB.setGradeLevel(sharedGrade);
        forB.setTuitionFee(new BigDecimal("40000.00"));
        forB.setTermFee(new BigDecimal("8000.00"));
        // Before the composite key this line threw a constraint violation.
        feeStructureRepository.saveAndFlush(forB);

        FeeInvoice invoice = feeManagementService.createInvoiceForStudent(studentA.getId(), tenantA, null);

        assertEquals(new BigDecimal("15000.00"), invoice.getTotalAmount(),
                "school A must be billed its own fees, not whichever school configured the grade first");
    }

    @Test
    public void createInvoiceForStudent_usesFeeStructure_whenPresent() {
        String gradeLevel = "Grade-1-" + UUID.randomUUID();
        studentA.getClassSection().setGradeName(gradeLevel);
        classSectionRepository.saveAndFlush(studentA.getClassSection());

        FeeStructure structure = new FeeStructure();
        structure.setId(UUID.randomUUID());
        structure.setTenantId(tenantA);
        structure.setAcademicYearId(academicYearIdA);
        structure.setGradeLevel(gradeLevel);
        structure.setTuitionFee(new BigDecimal("30000.00"));
        structure.setTermFee(new BigDecimal("2000.00"));
        feeStructureRepository.saveAndFlush(structure);

        FeeInvoice invoice = feeManagementService.createInvoiceForStudent(studentA.getId(), tenantA, null);

        assertEquals(new BigDecimal("32000.00"), invoice.getTotalAmount());
    }

    @Test
    public void createInvoiceForStudent_crossTenant_throws() {
        assertThrows(IllegalArgumentException.class, () ->
                feeManagementService.createInvoiceForStudent(studentA.getId(), tenantB, null));
    }

    @Test
    public void createInvoiceForStudent_unknownStudent_throws() {
        assertThrows(IllegalArgumentException.class, () ->
                feeManagementService.createInvoiceForStudent(UUID.randomUUID(), tenantA, null));
    }
}

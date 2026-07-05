package com.schoolos.management;

import com.schoolos.tenant.AcademicYear;
import com.schoolos.tenant.AcademicYearRepository;
import com.schoolos.tenant.Tenant;
import com.schoolos.tenant.TenantRepository;
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
        FeeInvoice invoice = feeManagementService.createInvoiceForStudent(studentA.getId(), tenantA, null);

        assertNotNull(invoice.getId());
        assertEquals(tenantA, invoice.getTenantId());
        assertEquals(new BigDecimal("20000.00"), invoice.getTotalAmount());
        assertEquals(0, BigDecimal.ZERO.compareTo(invoice.getAmountPaid()));
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

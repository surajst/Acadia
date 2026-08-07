package com.concept.management;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

// app.dev-mode=true lets the context boot with the insecure default JWT
// secret/demo password, same as how CI runs the packaged jar for e2e tests.
@SpringBootTest
@TestPropertySource(properties = "app.dev-mode=true")
@Transactional
public class FeeManagementServiceTest {

    @Autowired
    private FeeManagementService feeManagementService;

    @Autowired
    private FeeInvoiceRepository feeInvoiceRepository;

    @Autowired
    private FeeTransactionRepository feeTransactionRepository;

    @Autowired
    private StudentRepository studentRepository;

    @Autowired
    private ClassSectionRepository classSectionRepository;

    @Autowired
    private com.concept.tenant.TenantRepository tenantRepository;

    @Autowired
    private com.concept.tenant.AcademicYearRepository academicYearRepository;

    private UUID testInvoiceId;

    @BeforeEach
    public void setup() {
        UUID tenantId = UUID.fromString("00000000-0000-0000-0000-000000000000");
        UUID academicYearId = UUID.fromString("00000000-0000-0000-0000-111111111111");

        ClassSection classSection = new ClassSection();
        classSection.setId(UUID.randomUUID());
        classSection.setTenantId(tenantId);
        classSection.setAcademicYearId(academicYearId);
        classSection.setGradeName("Grade 1");
        classSection.setSectionName("A");
        classSectionRepository.saveAndFlush(classSection);

        Student student = new Student();
        student.setId(UUID.randomUUID());
        student.setTenantId(tenantId);
        student.setAcademicYearId(academicYearId);
        student.setFirstName("Test");
        student.setLastName("Student");
        student.setClassSection(classSection);
        studentRepository.saveAndFlush(student);

        // Create a test invoice
        FeeInvoice invoice = new FeeInvoice();
        invoice.setId(UUID.randomUUID());
        invoice.setStudentId(student.getId());
        invoice.setTotalAmount(new BigDecimal("20000.00"));
        invoice.setAmountPaid(BigDecimal.ZERO);

        invoice.setTenantId(tenantId);
        invoice.setAcademicYearId(academicYearId);

        invoice.updateBalances();
        FeeInvoice saved = feeInvoiceRepository.saveAndFlush(invoice);
        testInvoiceId = saved.getId();
    }

    @Test
    public void testRecordPayment_UnpaidToPartiallyPaid() {
        // Record a partial payment of 5000 INR
        feeManagementService.recordPayment(testInvoiceId, new BigDecimal("5000.00"), "ONLINE", null, null);

        FeeInvoice updated = feeInvoiceRepository.findById(testInvoiceId).orElseThrow();
        assertEquals(new BigDecimal("5000.00"), updated.getAmountPaid());
        assertEquals(new BigDecimal("15000.00"), updated.getAmountDue());
        assertEquals(FeeInvoice.FeeStatus.PARTIALLY_PAID, updated.getStatus());

        List<FeeTransaction> txns = feeTransactionRepository.findByInvoiceId(testInvoiceId);
        assertEquals(1, txns.size());
        assertEquals(new BigDecimal("5000.00"), txns.get(0).getAmountPaid());
        assertEquals("ONLINE", txns.get(0).getPaymentMode());
    }

    @Test
    public void testRecordPayment_FullyPaid() {
        // Record a payment of 20000 INR
        feeManagementService.recordPayment(testInvoiceId, new BigDecimal("20000.00"), "CASH", null, null);

        FeeInvoice updated = feeInvoiceRepository.findById(testInvoiceId).orElseThrow();
        assertEquals(new BigDecimal("20000.00"), updated.getAmountPaid());
        assertEquals(BigDecimal.ZERO.setScale(2), updated.getAmountDue());
        assertEquals(FeeInvoice.FeeStatus.PAID, updated.getStatus());

        List<FeeTransaction> txns = feeTransactionRepository.findByInvoiceId(testInvoiceId);
        assertEquals(1, txns.size());
        assertEquals(new BigDecimal("20000.00"), txns.get(0).getAmountPaid());
        assertEquals("CASH", txns.get(0).getPaymentMode());
    }

    @Test
    public void testRecordPayment_NegativeAmountThrows() {
        assertThrows(IllegalArgumentException.class, () -> {
            feeManagementService.recordPayment(testInvoiceId, new BigDecimal("-100.00"), "CHECK", null, null);
        });
    }

    @Test
    public void testRecordPayment_InvalidInvoiceThrows() {
        assertThrows(IllegalArgumentException.class, () -> {
            feeManagementService.recordPayment(UUID.randomUUID(), new BigDecimal("100.00"), "CASH", null, null);
        });
    }

    @Test
    public void getFeeSummary_aggregatesExpectedCollectedAndOutstanding() {
        // Isolated tenant so the totals are deterministic (no seeded invoices leak in).
        UUID tenantId = UUID.randomUUID();
        UUID academicYearId = UUID.randomUUID();

        com.concept.tenant.Tenant tenant = new com.concept.tenant.Tenant();
        tenant.setId(tenantId);
        tenant.setName("Fee Summary Tenant");
        tenant.setSubdomain("fs-" + tenantId.toString().substring(0, 8));
        tenant.setActive(true);
        tenant.setCreatedAt(java.time.Instant.now());
        tenantRepository.saveAndFlush(tenant);

        com.concept.tenant.AcademicYear year = new com.concept.tenant.AcademicYear();
        year.setId(academicYearId);
        year.setTenantId(tenantId);
        year.setName("2026-27");
        year.setStartDate(java.time.LocalDate.of(2026, 4, 1));
        year.setEndDate(java.time.LocalDate.of(2027, 3, 31));
        year.setCurrent(true);
        academicYearRepository.saveAndFlush(year);

        ClassSection classSection = new ClassSection();
        classSection.setId(UUID.randomUUID());
        classSection.setTenantId(tenantId);
        classSection.setAcademicYearId(academicYearId);
        classSection.setGradeName("Grade 1");
        classSection.setSectionName("A");
        classSectionRepository.saveAndFlush(classSection);

        // Two invoices: 10000 (fully paid) and 20000 (4000 paid) => expected 30000, collected 14000, outstanding 16000.
        saveInvoice(tenantId, academicYearId, newStudent(tenantId, academicYearId, classSection), "10000.00", "10000.00");
        saveInvoice(tenantId, academicYearId, newStudent(tenantId, academicYearId, classSection), "20000.00", "4000.00");

        FeeManagementService.FeeSummary s = feeManagementService.getFeeSummary(tenantId);

        assertEquals(2, s.totalInvoices());
        assertEquals(0, new BigDecimal("30000.00").compareTo(s.totalExpected()));
        assertEquals(0, new BigDecimal("14000.00").compareTo(s.totalCollected()));
        assertEquals(0, new BigDecimal("16000.00").compareTo(s.totalOutstanding()));
        assertEquals(47, s.collectionPercent()); // 14000/30000 = 46.67, HALF_UP -> 47
        assertEquals(1, s.outstandingInvoiceCount()); // the partially-paid one
    }

    private UUID newStudent(UUID tenantId, UUID academicYearId, ClassSection classSection) {
        Student student = new Student();
        student.setId(UUID.randomUUID());
        student.setTenantId(tenantId);
        student.setAcademicYearId(academicYearId);
        student.setFirstName("Fee");
        student.setLastName("Student");
        student.setClassSection(classSection);
        studentRepository.saveAndFlush(student);
        return student.getId();
    }

    private void saveInvoice(UUID tenantId, UUID academicYearId, UUID studentId, String total, String paid) {
        FeeInvoice invoice = new FeeInvoice();
        invoice.setId(UUID.randomUUID());
        invoice.setStudentId(studentId);
        invoice.setTenantId(tenantId);
        invoice.setAcademicYearId(academicYearId);
        invoice.setTotalAmount(new BigDecimal(total));
        invoice.setAmountPaid(new BigDecimal(paid));
        invoice.updateBalances();
        feeInvoiceRepository.saveAndFlush(invoice);
    }
}

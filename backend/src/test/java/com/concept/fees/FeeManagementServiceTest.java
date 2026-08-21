package com.concept.fees;
import com.concept.fees.app.FeeManagementService;
import com.concept.fees.data.FeeTransactionRepository;
import com.concept.fees.data.FeeTransaction;
import com.concept.fees.data.FeeInvoiceRepository;
import com.concept.fees.data.FeeInvoice;
import com.concept.shared.data.ClassSectionRepository;
import com.concept.shared.data.ClassSection;
import com.concept.shared.data.StudentRepository;
import com.concept.shared.data.Student;

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
    private UUID testTenantId;

    @BeforeEach
    public void setup() {
        UUID tenantId = UUID.fromString("00000000-0000-0000-0000-000000000000");
        this.testTenantId = tenantId;
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
        feeManagementService.recordPayment(testInvoiceId, new BigDecimal("5000.00"), "ONLINE", testTenantId, null);

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
        feeManagementService.recordPayment(testInvoiceId, new BigDecimal("20000.00"), "CASH", testTenantId, null);

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

    /**
     * The form set max=remainingDue in the browser, which is not validation. A
     * direct POST used to be accepted, and because updateBalances clamps
     * amountDue at zero the invoice then looked settled while amountPaid held
     * money the school could not account for.
     */
    @Test
    public void recordPayment_moreThanOutstanding_isRefused() {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () ->
                feeManagementService.recordPayment(testInvoiceId, new BigDecimal("50000.00"),
                        "CASH", testTenantId, null));
        assertTrue(e.getMessage().contains("more than"), e.getMessage());

        FeeInvoice untouched = feeInvoiceRepository.findById(testInvoiceId).orElseThrow();
        assertEquals(0, BigDecimal.ZERO.compareTo(untouched.getAmountPaid()),
                "a refused payment must not be partially applied");
        assertTrue(feeTransactionRepository.findByInvoiceId(testInvoiceId).isEmpty(),
                "a refused payment must leave no transaction behind");
    }

    @Test
    public void recordPayment_exactlyTheOutstandingAmount_isAllowed() {
        feeManagementService.recordPayment(testInvoiceId, new BigDecimal("20000.00"), "CASH", testTenantId, null);
        FeeInvoice paid = feeInvoiceRepository.findById(testInvoiceId).orElseThrow();
        assertEquals(FeeInvoice.FeeStatus.PAID, paid.getStatus());
    }

    /** Two partial payments must still be capped by what remains, not by the total. */
    @Test
    public void recordPayment_secondPaymentCannotExceedRemainingBalance() {
        feeManagementService.recordPayment(testInvoiceId, new BigDecimal("15000.00"), "CASH", testTenantId, null);

        assertThrows(IllegalArgumentException.class, () ->
                feeManagementService.recordPayment(testInvoiceId, new BigDecimal("6000.00"),
                        "CASH", testTenantId, null));

        FeeInvoice invoice = feeInvoiceRepository.findById(testInvoiceId).orElseThrow();
        assertEquals(new BigDecimal("15000.00"), invoice.getAmountPaid());
    }

    /**
     * A correction is a new entry, never an edit. Both the mistake and its
     * reversal have to stay on the invoice -- "we took 200,000 and gave it
     * back" is a different fact from "we took nothing", and a family querying
     * their receipt needs the first one to still exist.
     */
    @Test
    public void reversePayment_recordsTheOppositeAndKeepsTheOriginal() {
        feeManagementService.recordPayment(testInvoiceId, new BigDecimal("5000.00"), "CASH", testTenantId, null);
        FeeTransaction original = feeTransactionRepository.findByInvoiceId(testInvoiceId).get(0);

        feeManagementService.reversePayment(original.getId(), "Cheque bounced", testTenantId, null);

        FeeInvoice invoice = feeInvoiceRepository.findById(testInvoiceId).orElseThrow();
        assertEquals(0, BigDecimal.ZERO.compareTo(invoice.getAmountPaid()), "the payment is undone");
        assertEquals(FeeInvoice.FeeStatus.UNPAID, invoice.getStatus());

        List<FeeTransaction> ledger = feeTransactionRepository.findByInvoiceId(testInvoiceId);
        assertEquals(2, ledger.size(), "the original entry must survive alongside the reversal");
        FeeTransaction reversal = ledger.stream().filter(FeeTransaction::isReversal).findFirst().orElseThrow();
        assertEquals(new BigDecimal("5000.00").negate(), reversal.getAmountPaid());
        assertEquals("Cheque bounced", reversal.getNote());
        assertEquals(original.getId(), reversal.getReversesTransactionId());
    }

    @Test
    public void reversePayment_withoutAReason_isRefused() {
        feeManagementService.recordPayment(testInvoiceId, new BigDecimal("5000.00"), "CASH", testTenantId, null);
        FeeTransaction original = feeTransactionRepository.findByInvoiceId(testInvoiceId).get(0);

        assertThrows(IllegalArgumentException.class, () ->
                feeManagementService.reversePayment(original.getId(), "   ", testTenantId, null));
    }

    @Test
    public void reversePayment_cannotBeAppliedTwice() {
        feeManagementService.recordPayment(testInvoiceId, new BigDecimal("5000.00"), "CASH", testTenantId, null);
        FeeTransaction original = feeTransactionRepository.findByInvoiceId(testInvoiceId).get(0);

        feeManagementService.reversePayment(original.getId(), "Mistyped", testTenantId, null);
        assertThrows(IllegalArgumentException.class, () ->
                feeManagementService.reversePayment(original.getId(), "Again", testTenantId, null));
    }

    /** Reversing frees the balance up again, so the correct amount can be taken. */
    @Test
    public void reversePayment_thenRecordingTheCorrectAmount_works() {
        feeManagementService.recordPayment(testInvoiceId, new BigDecimal("20000.00"), "CASH", testTenantId, null);
        FeeTransaction wrong = feeTransactionRepository.findByInvoiceId(testInvoiceId).get(0);

        feeManagementService.reversePayment(wrong.getId(), "Wrong invoice", testTenantId, null);
        feeManagementService.recordPayment(testInvoiceId, new BigDecimal("2000.00"), "CASH", testTenantId, null);

        FeeInvoice invoice = feeInvoiceRepository.findById(testInvoiceId).orElseThrow();
        assertEquals(new BigDecimal("2000.00"), invoice.getAmountPaid());
        assertEquals(new BigDecimal("18000.00"), invoice.getAmountDue());
        assertEquals(FeeInvoice.FeeStatus.PARTIALLY_PAID, invoice.getStatus());
    }

    /** A reversal must not be usable to reach into another school's ledger. */
    @Test
    public void reversePayment_crossTenant_isRefused() {
        feeManagementService.recordPayment(testInvoiceId, new BigDecimal("5000.00"), "CASH", testTenantId, null);
        FeeTransaction original = feeTransactionRepository.findByInvoiceId(testInvoiceId).get(0);

        assertThrows(IllegalArgumentException.class, () ->
                feeManagementService.reversePayment(original.getId(), "Not mine", UUID.randomUUID(), null));
    }
}

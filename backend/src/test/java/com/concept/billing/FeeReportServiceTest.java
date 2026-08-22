package com.concept.billing;

import com.concept.billing.app.FeeReportService;
import com.concept.fees.app.FeeManagementService;
import com.concept.fees.data.FeeInvoice;
import com.concept.fees.data.FeeInvoiceRepository;
import com.concept.fees.data.FeeTransaction;
import com.concept.fees.data.FeeTransactionRepository;
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
 * Due dates made "overdue" computable and receipt numbers made a payment mean
 * something at a counter, but neither was useful until something showed them.
 * These pin the two reports that do: worst-overdue-first for arrears, and a
 * day-book that keeps a reversed receipt visible rather than deleting it.
 */
@SpringBootTest
@TestPropertySource(properties = "app.dev-mode=true")
@Transactional
public class FeeReportServiceTest {

    @Autowired private FeeReportService feeReportService;
    @Autowired private FeeManagementService feeManagementService;
    @Autowired private FeeInvoiceRepository feeInvoiceRepository;
    @Autowired private FeeTransactionRepository feeTransactionRepository;
    @Autowired private StudentRepository studentRepository;
    @Autowired private ClassSectionRepository classSectionRepository;
    @Autowired private TenantRepository tenantRepository;
    @Autowired private AcademicYearRepository academicYearRepository;

    private UUID tenantId;
    private UUID yearId;
    private ClassSection section;

    @BeforeEach
    public void setup() {
        tenantId = UUID.randomUUID();
        yearId = UUID.randomUUID();

        Tenant tenant = new Tenant();
        tenant.setId(tenantId);
        tenant.setName("Reports School");
        tenant.setSubdomain("rpt-" + tenantId.toString().substring(0, 8));
        tenant.setActive(true);
        tenant.setCreatedAt(Instant.now());
        tenantRepository.saveAndFlush(tenant);

        AcademicYear year = new AcademicYear();
        year.setId(yearId);
        year.setTenantId(tenantId);
        year.setName("2026-27");
        year.setStartDate(LocalDate.of(2026, 4, 1));
        year.setEndDate(LocalDate.of(2027, 3, 31));
        year.setCurrent(true);
        academicYearRepository.saveAndFlush(year);

        section = new ClassSection();
        section.setId(UUID.randomUUID());
        section.setTenantId(tenantId);
        section.setAcademicYearId(yearId);
        section.setGradeName("Grade 6");
        section.setSectionName("A");
        classSectionRepository.saveAndFlush(section);
    }

    private Student student(String firstName, String roll) {
        Student s = new Student();
        s.setId(UUID.randomUUID());
        s.setTenantId(tenantId);
        s.setAcademicYearId(yearId);
        s.setFirstName(firstName);
        s.setLastName("Test");
        s.setRollNumber(roll);
        s.setClassSection(section);
        return studentRepository.saveAndFlush(s);
    }

    private FeeInvoice invoice(Student s, String total, String paid, LocalDate dueDate, String label) {
        FeeInvoice inv = new FeeInvoice();
        inv.setId(UUID.randomUUID());
        inv.setStudentId(s.getId());
        inv.setTenantId(tenantId);
        inv.setAcademicYearId(yearId);
        inv.setTotalAmount(new BigDecimal(total));
        inv.setAmountPaid(new BigDecimal(paid));
        inv.setDueDate(dueDate);
        inv.setInstalmentLabel(label);
        inv.updateBalances();
        return feeInvoiceRepository.saveAndFlush(inv);
    }

    // ---------- defaulters ----------

    @Test
    public void defaulters_onlyIncludesOverdueUnpaidInvoices() {
        LocalDate today = LocalDate.now();
        Student paidOnTime = student("Paid", "R1");
        invoice(paidOnTime, "10000", "10000", today.minusDays(30), "Term 1"); // PAID, must not appear

        Student notYetDue = student("Future", "R2");
        invoice(notYetDue, "10000", "0", today.plusDays(10), "Term 1"); // due date in the future

        Student overdue = student("Overdue", "R3");
        invoice(overdue, "10000", "0", today.minusDays(5), "Term 1"); // genuinely overdue

        List<FeeReportService.DefaulterRow> rows = feeReportService.defaulters(tenantId);

        assertEquals(1, rows.size());
        assertEquals("Overdue Test", rows.get(0).studentName());
        assertEquals(5, rows.get(0).daysOverdue());
    }

    @Test
    public void defaulters_worstOverdueFirst() {
        LocalDate today = LocalDate.now();
        Student a = student("A", "RA");
        invoice(a, "10000", "0", today.minusDays(3), "Term 1");
        Student b = student("B", "RB");
        invoice(b, "10000", "0", today.minusDays(90), "Term 1");
        Student c = student("C", "RC");
        invoice(c, "10000", "0", today.minusDays(20), "Term 1");

        List<FeeReportService.DefaulterRow> rows = feeReportService.defaulters(tenantId);

        assertEquals(3, rows.size());
        assertEquals(90, rows.get(0).daysOverdue());
        assertEquals(20, rows.get(1).daysOverdue());
        assertEquals(3, rows.get(2).daysOverdue());
    }

    @Test
    public void defaulters_partiallyPaidInvoiceStillCounts() {
        LocalDate today = LocalDate.now();
        Student s = student("Partial", "RP");
        invoice(s, "10000", "4000", today.minusDays(15), "Term 2");

        List<FeeReportService.DefaulterRow> rows = feeReportService.defaulters(tenantId);

        assertEquals(1, rows.size());
        assertEquals(0, new BigDecimal("6000.00").compareTo(rows.get(0).amountDue()));
    }

    /** An invoice from a prior year, never settled, is still an arrear. */
    @Test
    public void defaulters_spansEveryYearBilled_notOnlyTheCurrentOne() {
        UUID lastYearId = UUID.randomUUID();
        AcademicYear lastYear = new AcademicYear();
        lastYear.setId(lastYearId);
        lastYear.setTenantId(tenantId);
        lastYear.setName("2025-26");
        lastYear.setStartDate(LocalDate.of(2025, 4, 1));
        lastYear.setEndDate(LocalDate.of(2026, 3, 31));
        lastYear.setCurrent(false);
        academicYearRepository.saveAndFlush(lastYear);

        Student s = new Student();
        s.setId(UUID.randomUUID());
        s.setTenantId(tenantId);
        s.setAcademicYearId(lastYearId);
        s.setFirstName("Carried");
        s.setLastName("Over");
        s.setRollNumber("RCO");
        s.setClassSection(section);
        studentRepository.saveAndFlush(s);

        invoice(s, "10000", "0", LocalDate.of(2025, 12, 1), "Term 3");

        List<FeeReportService.DefaulterRow> rows = feeReportService.defaulters(tenantId);
        assertEquals(1, rows.size());
        assertEquals("Carried Over", rows.get(0).studentName());
    }

    @Test
    public void defaulters_emptyTenant_returnsEmptyList() {
        assertTrue(feeReportService.defaulters(tenantId).isEmpty());
    }

    // ---------- collection report ----------

    @Test
    public void collectionReport_showsReceiptNumberInIssueOrder() {
        Student s = student("Payer", "RX");
        FeeInvoice inv = invoice(s, "10000", "0", LocalDate.now().plusDays(30), "Term 1");

        feeManagementService.recordPayment(inv.getId(), new BigDecimal("3000"), "CASH", tenantId, null);
        feeManagementService.recordPayment(inv.getId(), new BigDecimal("2000"), "ONLINE", tenantId, null);

        List<FeeReportService.ReceiptRow> receipts = feeReportService.collectionReport(tenantId, yearId);

        assertEquals(2, receipts.size());
        assertEquals(1, receipts.get(0).receiptNumber());
        assertEquals(new BigDecimal("3000"), receipts.get(0).amount());
        assertEquals("CASH", receipts.get(0).paymentMode());
        assertFalse(receipts.get(0).reversed());
        assertEquals(2, receipts.get(1).receiptNumber());
        assertEquals("ONLINE", receipts.get(1).paymentMode());
    }

    /**
     * A reversed payment stays on the day-book rather than vanishing -- the
     * receipt was issued, and the record of that has to survive even after
     * the money is given back. Its reversal (which carries no receipt number
     * of its own) must not appear as a phantom extra row.
     */
    @Test
    public void collectionReport_reversedPaymentStaysVisibleAndFlagged() {
        Student s = student("Reversed", "RY");
        FeeInvoice inv = invoice(s, "10000", "0", LocalDate.now().plusDays(30), "Term 1");

        feeManagementService.recordPayment(inv.getId(), new BigDecimal("5000"), "CASH", tenantId, null);
        FeeTransaction original = feeTransactionRepository.findByInvoiceId(inv.getId()).get(0);
        feeManagementService.reversePayment(original.getId(), "Cheque bounced", tenantId, null);

        List<FeeReportService.ReceiptRow> receipts = feeReportService.collectionReport(tenantId, yearId);

        assertEquals(1, receipts.size(), "the reversal itself must not appear as a second receipt row");
        assertTrue(receipts.get(0).reversed());
        assertEquals(1, receipts.get(0).receiptNumber());
    }

    @Test
    public void collectionReport_receiptNumbersDoNotSkip_evenAfterAReversal() {
        Student s = student("Sequence", "RS");
        FeeInvoice inv = invoice(s, "10000", "0", LocalDate.now().plusDays(30), "Term 1");

        feeManagementService.recordPayment(inv.getId(), new BigDecimal("1000"), "CASH", tenantId, null);
        FeeTransaction first = feeTransactionRepository.findByInvoiceId(inv.getId()).get(0);
        feeManagementService.reversePayment(first.getId(), "Mistyped", tenantId, null);
        feeManagementService.recordPayment(inv.getId(), new BigDecimal("2000"), "CASH", tenantId, null);

        List<FeeReportService.ReceiptRow> receipts = feeReportService.collectionReport(tenantId, yearId);

        assertEquals(2, receipts.size());
        assertEquals(1, receipts.get(0).receiptNumber());
        assertTrue(receipts.get(0).reversed());
        assertEquals(2, receipts.get(1).receiptNumber());
        assertFalse(receipts.get(1).reversed());
    }

    @Test
    public void collectionReport_emptyYear_returnsEmptyList() {
        assertTrue(feeReportService.collectionReport(tenantId, yearId).isEmpty());
    }

    @Test
    public void recordPayment_returnsTheReceiptNumberItIssued() {
        Student s = student("Receipted", "RR");
        FeeInvoice inv = invoice(s, "10000", "0", LocalDate.now().plusDays(30), "Term 1");

        Integer receiptNumber = feeManagementService.recordPayment(
                inv.getId(), new BigDecimal("4000"), "CASH", tenantId, null);

        assertEquals(1, receiptNumber);
    }
}

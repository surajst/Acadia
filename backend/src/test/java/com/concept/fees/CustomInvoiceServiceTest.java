package com.concept.fees;

import com.concept.fees.app.CustomInvoiceService;
import com.concept.fees.data.InvoiceLine;
import com.concept.fees.data.InvoiceLineRepository;
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
 * A trip, a textbook, a bus fare -- charges that fit no fee plan. Before this,
 * the only way to bill one was to override a tuition invoice, which made the
 * total wrong in a way nobody could unpick later. These pin that a custom
 * invoice says what it is for, refuses an unexplained line, and can bill a
 * whole class in one action without silently invoicing nobody.
 */
@SpringBootTest
@TestPropertySource(properties = "app.dev-mode=true")
@Transactional
public class CustomInvoiceServiceTest {

    @Autowired private CustomInvoiceService customInvoiceService;
    @Autowired private FeeInvoiceRepository feeInvoiceRepository;
    @Autowired private InvoiceLineRepository invoiceLineRepository;
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
        tenant.setName("Custom Invoice School");
        tenant.setSubdomain("ci-" + tenantId.toString().substring(0, 8));
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

    private Student student(String firstName) {
        Student s = new Student();
        s.setId(UUID.randomUUID());
        s.setTenantId(tenantId);
        s.setAcademicYearId(yearId);
        s.setFirstName(firstName);
        s.setLastName("Test");
        s.setClassSection(section);
        return studentRepository.saveAndFlush(s);
    }

    private List<CustomInvoiceService.LineSpec> lines(Object... pairs) {
        List<CustomInvoiceService.LineSpec> out = new java.util.ArrayList<>();
        for (int i = 0; i < pairs.length; i += 2) {
            out.add(new CustomInvoiceService.LineSpec((String) pairs[i], new BigDecimal((String) pairs[i + 1])));
        }
        return out;
    }

    @Test
    public void raise_oneStudentTwoLines_createsOneInvoiceWithBothLinesAndTheirSum() {
        Student s = student("Aarav");
        List<FeeInvoice> raised = customInvoiceService.raise(
                List.of(s.getId()),
                lines("Annual school trip", "1500.00", "Activity kit", "250.00"),
                LocalDate.of(2026, 9, 15), tenantId, null);

        assertEquals(1, raised.size());
        FeeInvoice invoice = raised.get(0);
        assertEquals(new BigDecimal("1750.00"), invoice.getTotalAmount());
        assertEquals(LocalDate.of(2026, 9, 15), invoice.getDueDate());
        assertTrue(invoice.isCustom());
        // The label is the first line's description, not a generic word --
        // "Annual school trip" reads on the ledger, "Custom invoice" does not.
        assertEquals("Annual school trip", invoice.getInstalmentLabel());

        List<InvoiceLine> savedLines = invoiceLineRepository
                .findByInvoiceIdAndTenantIdOrderBySequenceNumberAsc(invoice.getId(), tenantId);
        assertEquals(2, savedLines.size());
        assertEquals("Annual school trip", savedLines.get(0).getDescription());
        assertEquals(new BigDecimal("1500.00"), savedLines.get(0).getAmount());
        assertEquals("Activity kit", savedLines.get(1).getDescription());
        assertEquals(new BigDecimal("250.00"), savedLines.get(1).getAmount());
    }

    /**
     * Billing a whole class for a trip is one action to an admin. Making them
     * repeat it forty times is how half a class ends up uninvoiced, so raising
     * for several students creates one identical invoice per student, not one
     * invoice split between them.
     */
    @Test
    public void raise_threeStudents_createsThreeIdenticalInvoices() {
        Student a = student("Aarav");
        Student b = student("Bhavya");
        Student c = student("Chetan");

        List<FeeInvoice> raised = customInvoiceService.raise(
                List.of(a.getId(), b.getId(), c.getId()),
                lines("Sports day fee", "300.00"),
                LocalDate.of(2026, 10, 1), tenantId, null);

        assertEquals(3, raised.size());
        for (FeeInvoice invoice : raised) {
            assertEquals(new BigDecimal("300.00"), invoice.getTotalAmount());
        }
        assertEquals(3, raised.stream().map(FeeInvoice::getStudentId).distinct().count());

        // Each student's own line row, not one row shared across three invoices.
        for (FeeInvoice invoice : raised) {
            assertEquals(1, invoiceLineRepository
                    .findByInvoiceIdAndTenantIdOrderBySequenceNumberAsc(invoice.getId(), tenantId).size());
        }
    }

    @Test
    public void raise_noStudents_isRefused() {
        assertThrows(IllegalArgumentException.class, () ->
                customInvoiceService.raise(List.of(), lines("Trip", "100.00"),
                        LocalDate.of(2026, 10, 1), tenantId, null));
    }

    @Test
    public void raise_noDueDate_isRefused() {
        Student s = student("Aarav");
        assertThrows(IllegalArgumentException.class, () ->
                customInvoiceService.raise(List.of(s.getId()), lines("Trip", "100.00"), null, tenantId, null));
    }

    @Test
    public void raise_noLines_isRefused() {
        Student s = student("Aarav");
        assertThrows(IllegalArgumentException.class, () ->
                customInvoiceService.raise(List.of(s.getId()), List.of(),
                        LocalDate.of(2026, 10, 1), tenantId, null));
    }

    /**
     * A line with an amount and no description is exactly the unexplained
     * charge this feature exists to prevent -- refusing is the whole point.
     */
    @Test
    public void raise_blankDescription_isRefused() {
        Student s = student("Aarav");
        assertThrows(IllegalArgumentException.class, () ->
                customInvoiceService.raise(List.of(s.getId()), lines("   ", "100.00"),
                        LocalDate.of(2026, 10, 1), tenantId, null));
    }

    @Test
    public void raise_negativeLineAmount_isRefused() {
        Student s = student("Aarav");
        assertThrows(IllegalArgumentException.class, () ->
                customInvoiceService.raise(List.of(s.getId()), lines("Refund adjustment", "-50.00"),
                        LocalDate.of(2026, 10, 1), tenantId, null));
    }

    @Test
    public void raise_zeroTotal_isRefused() {
        Student s = student("Aarav");
        assertThrows(IllegalArgumentException.class, () ->
                customInvoiceService.raise(List.of(s.getId()), lines("Free item", "0.00"),
                        LocalDate.of(2026, 10, 1), tenantId, null));
    }

    @Test
    public void raise_unknownStudent_isRefused() {
        assertThrows(IllegalArgumentException.class, () ->
                customInvoiceService.raise(List.of(UUID.randomUUID()), lines("Trip", "100.00"),
                        LocalDate.of(2026, 10, 1), tenantId, null));
    }

    /** A custom invoice must not be raisable against a different school's student. */
    @Test
    public void raise_crossTenantStudent_isRefused() {
        Student s = student("Aarav");
        assertThrows(IllegalArgumentException.class, () ->
                customInvoiceService.raise(List.of(s.getId()), lines("Trip", "100.00"),
                        LocalDate.of(2026, 10, 1), UUID.randomUUID(), null));
    }

    /**
     * invoice_lines carries a real foreign key to fee_invoices, unlike its
     * sibling fee_transactions which has none. Deleting the invoice must
     * therefore cascade rather than fail -- this is what a dev-mode reset and
     * RosterStudentPurger both do directly against fee_invoices, and neither
     * should have to know invoice_lines exists.
     */
    @Test
    public void deletingAnInvoice_cascadesToItsLines() {
        Student s = student("Aarav");
        FeeInvoice invoice = customInvoiceService.raise(
                List.of(s.getId()), lines("Trip", "100.00"),
                LocalDate.of(2026, 10, 1), tenantId, null).get(0);
        assertFalse(invoiceLineRepository
                .findByInvoiceIdAndTenantIdOrderBySequenceNumberAsc(invoice.getId(), tenantId).isEmpty());

        feeInvoiceRepository.deleteById(invoice.getId());
        feeInvoiceRepository.flush();

        assertTrue(invoiceLineRepository
                .findByInvoiceIdAndTenantIdOrderBySequenceNumberAsc(invoice.getId(), tenantId).isEmpty());
    }
}

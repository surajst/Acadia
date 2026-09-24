package com.concept.fees;

import com.concept.fees.app.FeeManagementService;
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
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * An invoice raised in error had no way out. Record a payment, request a waiver,
 * reverse a payment -- that was the whole list, so a bill that should never have
 * existed sat on the family's ledger and in the school's outstanding total for
 * good. A waiver is the wrong instrument: it says the school forgave a debt it
 * was owed, which is a different fact from the debt never being owed, and it
 * leaves the original amount in the expected total either way.
 *
 * <p>Two rules matter more than the happy path. Nothing may be cancelled once
 * money has changed hands -- the invoice is then evidence of a receipt. And a
 * cancelled invoice must stay cancelled: {@code updateBalances()} is reached
 * from every amount setter and recomputes the status, so without a guard,
 * touching the total would quietly un-cancel it.
 *
 * <p>The role and paid-amount rules are posted straight at the endpoint as well
 * as called on the service, because a button that is merely hidden is not a
 * rule.
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = "app.dev-mode=true")
@Transactional
class InvoiceCancellationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private FeeManagementService feeManagementService;
    @Autowired private FeeInvoiceRepository feeInvoiceRepository;
    @Autowired private StudentRepository studentRepository;
    @Autowired private ClassSectionRepository classSectionRepository;
    @Autowired private TenantRepository tenantRepository;
    @Autowired private AcademicYearRepository academicYearRepository;
    @Autowired private UserRepository userRepository;

    private UUID tenantId;
    private UUID yearId;
    private Student riya;
    private String adminEmail;
    private String teacherEmail;
    private Authentication admin;
    private Authentication teacher;

    @BeforeEach
    void setup() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);

        Tenant tenant = new Tenant();
        tenant.setId(UUID.randomUUID());
        tenant.setName("Demo SSC");
        tenant.setSubdomain("cancel-" + suffix);
        tenant.setActive(true);
        tenant.setCreatedAt(Instant.now());
        tenantId = tenantRepository.saveAndFlush(tenant).getId();

        AcademicYear year = new AcademicYear();
        year.setId(UUID.randomUUID());
        year.setTenantId(tenantId);
        year.setName("2026-27");
        year.setStartDate(LocalDate.of(2026, 4, 1));
        year.setEndDate(LocalDate.of(2027, 3, 31));
        year.setCurrent(true);
        yearId = academicYearRepository.saveAndFlush(year).getId();

        ClassSection section = new ClassSection();
        section.setId(UUID.randomUUID());
        section.setTenantId(tenantId);
        section.setAcademicYearId(yearId);
        section.setGradeName("Grade 6");
        section.setSectionName("A");
        section = classSectionRepository.saveAndFlush(section);

        riya = new Student();
        riya.setId(UUID.randomUUID());
        riya.setTenantId(tenantId);
        riya.setAcademicYearId(yearId);
        riya.setFirstName("Riya");
        riya.setLastName("Singh");
        riya.setRollNumber("6A07");
        riya.setClassSection(section);
        riya = studentRepository.saveAndFlush(riya);

        adminEmail = "admin-" + suffix + "@example.com";
        teacherEmail = "teacher-" + suffix + "@example.com";
        admin = authFor(person(adminEmail, "Office Admin", UserRole.ADMIN));
        teacher = authFor(person(teacherEmail, "Priya Sharma", UserRole.TEACHER));
    }

    private User person(String email, String name, UserRole role) {
        User u = new User();
        u.setId(UUID.randomUUID());
        u.setTenantId(tenantId);
        u.setAcademicYearId(yearId);
        u.setEmail(email);
        u.setPasswordHash("irrelevant");
        u.setFullName(name);
        u.setRole(role);
        return userRepository.saveAndFlush(u);
    }

    private Authentication authFor(User u) {
        return new UsernamePasswordAuthenticationToken(u.getEmail(), "n/a");
    }

    private FeeInvoice invoice(BigDecimal total, BigDecimal paid) {
        FeeInvoice inv = new FeeInvoice();
        inv.setId(UUID.randomUUID());
        inv.setTenantId(tenantId);
        inv.setAcademicYearId(yearId);
        inv.setStudentId(riya.getId());
        inv.setInstalmentLabel("Term 1");
        inv.setDueDate(LocalDate.of(2026, 6, 1));
        inv.setTotalAmount(total);
        inv.setAmountPaid(paid);
        inv.updateBalances();
        return feeInvoiceRepository.saveAndFlush(inv);
    }

    // ── The action itself ─────────────────────────────────────────────────────

    @Test
    void anUnpaidInvoiceCanBeCancelledWithAReason() {
        FeeInvoice raised = invoice(new BigDecimal("8000.00"), BigDecimal.ZERO);

        feeManagementService.cancelInvoice(raised.getId(), "Raised for the wrong child", tenantId, admin);

        FeeInvoice after = feeInvoiceRepository.findByIdAndTenantId(raised.getId(), tenantId).orElseThrow();
        assertTrue(after.isCancelled(), "the invoice should read as cancelled");
        assertEquals(0, after.getAmountDue().compareTo(BigDecimal.ZERO),
                "a withdrawn bill cannot still be owed");
        assertEquals("Raised for the wrong child", after.getCancellationReason());
        assertEquals(adminEmail, after.getCancelledBy(), "the decision needs a name against it");
        assertNotNull(after.getCancelledAt());
    }

    /** The record has to survive: a cancellation with no reason is not a trail. */
    @Test
    void aCancellationWithoutAReasonIsRefused() {
        FeeInvoice raised = invoice(new BigDecimal("8000.00"), BigDecimal.ZERO);

        assertThrows(IllegalArgumentException.class,
                () -> feeManagementService.cancelInvoice(raised.getId(), "   ", tenantId, admin));
        assertFalse(feeInvoiceRepository.findByIdAndTenantId(raised.getId(), tenantId)
                .orElseThrow().isCancelled());
    }

    /**
     * Riya's own case. Term 1 has 2,000 paid against it, so this is exactly the
     * invoice a cancel button must refuse -- the money would be left attached
     * to a bill that no longer exists.
     */
    @Test
    void anInvoiceWithMoneyPaidAgainstItCannotBeCancelled() {
        FeeInvoice partPaid = invoice(new BigDecimal("8000.00"), new BigDecimal("2000.00"));

        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> feeManagementService.cancelInvoice(partPaid.getId(), "Raised in error", tenantId, admin));

        assertTrue(e.getMessage().contains("2000"),
                "the refusal has to say how much was paid, got: " + e.getMessage());
        assertTrue(e.getMessage().toLowerCase().contains("revers"),
                "and where to go instead, got: " + e.getMessage());
        assertFalse(feeInvoiceRepository.findByIdAndTenantId(partPaid.getId(), tenantId)
                .orElseThrow().isCancelled());
    }

    @Test
    void cancellingTwiceIsRefusedRatherThanSilentlyRepeated() {
        FeeInvoice raised = invoice(new BigDecimal("8000.00"), BigDecimal.ZERO);
        feeManagementService.cancelInvoice(raised.getId(), "Duplicate of Term 1", tenantId, admin);

        assertThrows(IllegalArgumentException.class,
                () -> feeManagementService.cancelInvoice(raised.getId(), "Again", tenantId, admin));
    }

    @Test
    void aTeacherCannotCancelAnInvoice() {
        FeeInvoice raised = invoice(new BigDecimal("8000.00"), BigDecimal.ZERO);

        assertThrows(AccessDeniedException.class,
                () -> feeManagementService.cancelInvoice(raised.getId(), "Raised in error", tenantId, teacher));
        assertFalse(feeInvoiceRepository.findByIdAndTenantId(raised.getId(), tenantId)
                .orElseThrow().isCancelled());
    }

    /** Another school's invoice is not found, rather than cancelled. */
    @Test
    void anInvoiceFromAnotherTenantCannotBeCancelled() {
        FeeInvoice raised = invoice(new BigDecimal("8000.00"), BigDecimal.ZERO);

        assertThrows(IllegalArgumentException.class,
                () -> feeManagementService.cancelInvoice(raised.getId(), "Raised in error",
                        UUID.randomUUID(), admin));
        assertFalse(feeInvoiceRepository.findByIdAndTenantId(raised.getId(), tenantId)
                .orElseThrow().isCancelled());
    }

    // ── The guard that keeps it cancelled ─────────────────────────────────────

    /**
     * {@code updateBalances()} runs from every amount setter and recomputes the
     * status from the amounts. Without the guard, writing the total back would
     * turn a cancelled invoice into an UNPAID one with the full amount owed
     * again -- and nothing in the UI would say it had happened.
     */
    @Test
    void touchingTheAmountsDoesNotUnCancelAnInvoice() {
        FeeInvoice raised = invoice(new BigDecimal("8000.00"), BigDecimal.ZERO);
        feeManagementService.cancelInvoice(raised.getId(), "Raised in error", tenantId, admin);

        FeeInvoice cancelled = feeInvoiceRepository.findByIdAndTenantId(raised.getId(), tenantId).orElseThrow();
        cancelled.setTotalAmount(new BigDecimal("9000.00"));
        cancelled.updateBalances();

        assertTrue(cancelled.isCancelled(), "recomputing the balances must not revive a cancelled invoice");
        assertEquals(0, cancelled.getAmountDue().compareTo(BigDecimal.ZERO));
    }

    /** An overdue badge on a withdrawn bill is a support call waiting to happen. */
    @Test
    void aCancelledInvoiceIsNeverOverdue() {
        FeeInvoice raised = invoice(new BigDecimal("8000.00"), BigDecimal.ZERO);
        feeManagementService.cancelInvoice(raised.getId(), "Raised in error", tenantId, admin);

        FeeInvoice cancelled = feeInvoiceRepository.findByIdAndTenantId(raised.getId(), tenantId).orElseThrow();
        assertFalse(cancelled.isOverdue(LocalDate.of(2027, 1, 1)),
                "its due date is long past, but nothing is owed on it");
    }

    // ── It has to leave the school's totals ───────────────────────────────────

    /**
     * The reason a cancellation exists rather than a waiver. If the amount stays
     * in the expected total, the collection percentage still counts a bill the
     * school has withdrawn, and the ledger reads as though it failed to collect
     * money it never asked for.
     */
    @Test
    void aCancelledInvoiceLeavesTheSchoolsExpectedAndOutstandingTotals() {
        invoice(new BigDecimal("5000.00"), BigDecimal.ZERO);          // stands
        FeeInvoice mistake = invoice(new BigDecimal("8000.00"), BigDecimal.ZERO);

        FeeManagementService.FeeSummary before = feeManagementService.getFeeSummary(tenantId);
        assertEquals(0, before.totalExpected().compareTo(new BigDecimal("13000.00")),
                "both invoices count before the cancellation");

        feeManagementService.cancelInvoice(mistake.getId(), "Raised in error", tenantId, admin);

        FeeManagementService.FeeSummary after = feeManagementService.getFeeSummary(tenantId);
        assertEquals(0, after.totalExpected().compareTo(new BigDecimal("5000.00")),
                "the withdrawn 8,000 must come out of what the school expects");
        assertEquals(0, after.totalOutstanding().compareTo(new BigDecimal("5000.00")),
                "and out of what it is owed");
        assertEquals(1, after.totalInvoices(), "and out of the invoice count");
        assertEquals(1, after.outstandingInvoiceCount(),
                "one invoice is still unsettled, not two");
    }

    // ── Straight at the endpoint ──────────────────────────────────────────────

    private MvcResult postCancel(String email, String role, UUID invoiceId, String reason) throws Exception {
        return mockMvc.perform(post("/web/admin/fees/invoice/" + invoiceId + "/cancel")
                        .with(user(email).roles(role))
                        .with(csrf())
                        .param("reason", reason))
                .andReturn();
    }

    @Test
    void theEndpointRefusesARoleThatMayNotCancel() throws Exception {
        FeeInvoice raised = invoice(new BigDecimal("8000.00"), BigDecimal.ZERO);

        MvcResult result = postCancel(teacherEmail, "TEACHER", raised.getId(), "Raised in error");

        assertEquals(403, result.getResponse().getStatus(),
                "a teacher posting straight at the endpoint must be refused");
        assertFalse(feeInvoiceRepository.findByIdAndTenantId(raised.getId(), tenantId)
                .orElseThrow().isCancelled());
    }

    @Test
    void theEndpointCancelsForAnAdmin() throws Exception {
        FeeInvoice raised = invoice(new BigDecimal("8000.00"), BigDecimal.ZERO);

        MvcResult result = postCancel(adminEmail, "ADMIN", raised.getId(), "Raised for the wrong child");

        assertTrue(result.getResponse().getStatus() < 400,
                "an admin must be able to cancel, got " + result.getResponse().getStatus());
        assertTrue(feeInvoiceRepository.findByIdAndTenantId(raised.getId(), tenantId)
                .orElseThrow().isCancelled());
    }

    /** The principal has to be able to do this too, not just the office. */
    @Test
    void theEndpointCancelsForThePrincipal() throws Exception {
        String headEmail = "head-" + UUID.randomUUID().toString().substring(0, 8) + "@example.com";
        person(headEmail, "Head Teacher", UserRole.PRINCIPAL);
        FeeInvoice raised = invoice(new BigDecimal("8000.00"), BigDecimal.ZERO);

        MvcResult result = postCancel(headEmail, "PRINCIPAL", raised.getId(), "Raised in error");

        assertTrue(result.getResponse().getStatus() < 400,
                "the principal must be able to cancel, got " + result.getResponse().getStatus());
        assertTrue(feeInvoiceRepository.findByIdAndTenantId(raised.getId(), tenantId)
                .orElseThrow().isCancelled());
    }

    /** A blank reason sent past the browser's required attribute. */
    @Test
    void theEndpointRefusesABlankReason() throws Exception {
        FeeInvoice raised = invoice(new BigDecimal("8000.00"), BigDecimal.ZERO);

        postCancel(adminEmail, "ADMIN", raised.getId(), "   ");

        assertFalse(feeInvoiceRepository.findByIdAndTenantId(raised.getId(), tenantId)
                .orElseThrow().isCancelled(),
                "the form's required attribute is not the rule");
    }
}

package com.concept.fees;

import com.concept.fees.app.ApprovalService;
import com.concept.fees.app.FeeDashboardService;
import com.concept.fees.app.FeeManagementService;
import com.concept.fees.app.FeePlanChangeRequestService;
import com.concept.fees.app.FeePlanService;
import com.concept.fees.data.ApprovalRequest;
import com.concept.fees.data.ApprovalRequestRepository;
import com.concept.fees.data.FeeInvoice;
import com.concept.fees.data.FeeInvoiceRepository;
import com.concept.fees.data.FeePlanRepository;
import com.concept.fees.data.FeeTransactionRepository;
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
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Reversing a payment un-records cash the school already receipted; changing a
 * fee plan re-prices a whole grade. Both were single-admin actions, audited
 * afterwards and gated by nothing. Neither has a value threshold -- the rule is
 * that they always need a principal, so there is no amount to argue about.
 *
 * <p>What matters most here is not that approval works, but that <em>asking</em>
 * changes nothing. A request that quietly took effect would be worse than no
 * gate at all, because the queue would imply a control that was not there.
 */
@SpringBootTest
@TestPropertySource(properties = "app.dev-mode=true")
@Transactional
public class ApprovalGateTest {

    @Autowired private FeeDashboardService feeDashboardService;
    @Autowired private FeeManagementService feeManagementService;
    @Autowired private FeePlanChangeRequestService changeRequestService;
    @Autowired private FeePlanService feePlanService;
    @Autowired private ApprovalService approvalService;
    @Autowired private ApprovalRequestRepository approvalRequestRepository;
    @Autowired private FeeInvoiceRepository feeInvoiceRepository;
    @Autowired private FeeTransactionRepository feeTransactionRepository;
    @Autowired private FeePlanRepository feePlanRepository;
    @Autowired private StudentRepository studentRepository;
    @Autowired private ClassSectionRepository classSectionRepository;
    @Autowired private TenantRepository tenantRepository;
    @Autowired private AcademicYearRepository academicYearRepository;
    @Autowired private UserRepository userRepository;

    private UUID tenantId;
    private UUID yearId;
    private FeeInvoice invoice;
    private Integer receiptNumber;
    private UUID paymentId;
    private Authentication admin;
    private Authentication principal;

    private Authentication makeUser(String email, UserRole role) {
        User user = new User();
        user.setId(UUID.randomUUID());
        user.setEmail(email);
        user.setPasswordHash("irrelevant");
        user.setFullName(email);
        user.setRole(role);
        user.setTenantId(tenantId);
        user.setAcademicYearId(yearId);
        userRepository.saveAndFlush(user);
        return new UsernamePasswordAuthenticationToken(email, null,
                List.of(new SimpleGrantedAuthority("ROLE_" + role.name())));
    }

    @BeforeEach
    public void setup() {
        Tenant tenant = new Tenant();
        tenant.setId(UUID.randomUUID());
        tenant.setName("Approval School");
        tenant.setSubdomain("appr-" + UUID.randomUUID());
        tenant.setActive(true);
        tenant.setCreatedAt(Instant.now());
        tenantId = tenantRepository.saveAndFlush(tenant).getId();

        AcademicYear year = new AcademicYear();
        year.setId(UUID.randomUUID());
        year.setTenantId(tenantId);
        year.setName("2026");
        year.setStartDate(LocalDate.of(2026, 1, 1));
        year.setEndDate(LocalDate.of(2026, 12, 31));
        year.setCurrent(true);
        yearId = academicYearRepository.saveAndFlush(year).getId();

        ClassSection section = new ClassSection();
        section.setId(UUID.randomUUID());
        section.setTenantId(tenantId);
        section.setAcademicYearId(yearId);
        section.setGradeName("Grade 6");
        section.setSectionName("A");
        classSectionRepository.saveAndFlush(section);

        Student student = new Student();
        student.setId(UUID.randomUUID());
        student.setTenantId(tenantId);
        student.setAcademicYearId(yearId);
        student.setFirstName("Fee");
        student.setLastName("Payer");
        student.setClassSection(section);
        studentRepository.saveAndFlush(student);

        invoice = new FeeInvoice();
        invoice.setId(UUID.randomUUID());
        invoice.setTenantId(tenantId);
        invoice.setAcademicYearId(yearId);
        invoice.setStudentId(student.getId());
        invoice.setTotalAmount(new BigDecimal("10000.00"));
        invoice.setAmountPaid(BigDecimal.ZERO);
        invoice.updateBalances();
        feeInvoiceRepository.saveAndFlush(invoice);

        admin = makeUser("admin@approval.test", UserRole.ADMIN);
        principal = makeUser("head@approval.test", UserRole.PRINCIPAL);

        receiptNumber = feeManagementService.recordPayment(
                invoice.getId(), new BigDecimal("4000.00"), "CASH", tenantId, admin);
        paymentId = feeTransactionRepository.findByInvoiceId(invoice.getId()).get(0).getId();
    }

    // ─── Payment reversal ───────────────────────────────────────────────────

    @Test
    public void askingToReverseAPaymentChangesNothingYet() {
        feeDashboardService.requestPaymentReversal(paymentId, "Cheque bounced", tenantId, admin);

        FeeInvoice unchanged = feeInvoiceRepository.findById(invoice.getId()).orElseThrow();
        assertEquals(0, new BigDecimal("4000.00").compareTo(unchanged.getAmountPaid()),
                "the payment must still stand while the request is pending");
        assertEquals(1, feeTransactionRepository.findByInvoiceId(invoice.getId()).size(),
                "no reversal entry may exist before approval");
        assertEquals(1, approvalService.pending(tenantId).size());
    }

    @Test
    public void thePrincipalApprovingActuallyReversesIt() {
        feeDashboardService.requestPaymentReversal(paymentId, "Cheque bounced", tenantId, admin);
        UUID requestId = approvalService.pending(tenantId).get(0).getId();

        approvalService.approve(requestId, tenantId, principal);

        FeeInvoice reversed = feeInvoiceRepository.findById(invoice.getId()).orElseThrow();
        assertEquals(0, BigDecimal.ZERO.compareTo(reversed.getAmountPaid()));
        assertEquals(2, feeTransactionRepository.findByInvoiceId(invoice.getId()).size(),
                "the original payment is never deleted -- the correction is its opposite");
        assertTrue(approvalService.pending(tenantId).isEmpty());
    }

    @Test
    public void rejectingLeavesThePaymentStanding() {
        feeDashboardService.requestPaymentReversal(paymentId, "Raised by mistake", tenantId, admin);
        UUID requestId = approvalService.pending(tenantId).get(0).getId();

        approvalService.reject(requestId, "Payment cleared fine", tenantId, principal);

        assertEquals(0, new BigDecimal("4000.00").compareTo(
                feeInvoiceRepository.findById(invoice.getId()).orElseThrow().getAmountPaid()));
        assertEquals(ApprovalRequest.Status.REJECTED,
                approvalRequestRepository.findById(requestId).orElseThrow().getStatus());
    }

    @Test
    public void theRequesterCannotApproveTheirOwnRequest() {
        feeDashboardService.requestPaymentReversal(paymentId, "Cheque bounced", tenantId, admin);
        UUID requestId = approvalService.pending(tenantId).get(0).getId();

        IllegalArgumentException refused = assertThrows(IllegalArgumentException.class,
                () -> approvalService.approve(requestId, tenantId, admin));
        assertTrue(refused.getMessage().contains("someone else"), refused.getMessage());

        assertEquals(0, new BigDecimal("4000.00").compareTo(
                feeInvoiceRepository.findById(invoice.getId()).orElseThrow().getAmountPaid()));
    }

    @Test
    public void aHopelessRequestIsRefusedWhenItIsMadeNotWhenItIsApproved() {
        // No reason given. Catching this at request time means the admin can fix
        // it, rather than the principal finding a broken request in the queue.
        assertThrows(IllegalArgumentException.class,
                () -> feeDashboardService.requestPaymentReversal(paymentId, "  ", tenantId, admin));
        assertTrue(approvalService.pending(tenantId).isEmpty(),
                "a refused request must not be queued");
    }

    @Test
    public void aRequestCannotBeDecidedTwice() {
        feeDashboardService.requestPaymentReversal(paymentId, "Cheque bounced", tenantId, admin);
        UUID requestId = approvalService.pending(tenantId).get(0).getId();
        approvalService.approve(requestId, tenantId, principal);

        assertThrows(IllegalArgumentException.class,
                () -> approvalService.approve(requestId, tenantId, principal));
    }

    @Test
    public void anotherSchoolsRequestIsInvisibleAndUndecidable() {
        feeDashboardService.requestPaymentReversal(paymentId, "Cheque bounced", tenantId, admin);
        UUID requestId = approvalService.pending(tenantId).get(0).getId();

        UUID otherTenant = UUID.randomUUID();
        assertTrue(approvalService.pending(otherTenant).isEmpty());
        assertThrows(IllegalArgumentException.class,
                () -> approvalService.approve(requestId, otherTenant, principal));
    }

    // ─── Fee plan ───────────────────────────────────────────────────────────

    @Test
    public void askingToSetAFeePlanChangesNothingYet() {
        changeRequestService.requestPlanSave("Grade 6",
                List.of(new FeePlanService.InstalmentSpec("Term 1", new BigDecimal("5000.00"), 0)),
                tenantId, yearId, admin);

        assertTrue(feePlanRepository.findByTenantIdAndAcademicYearIdAndGradeLevel(tenantId, yearId, "Grade 6").isEmpty(),
                "no plan may exist while the request is pending");
        assertEquals(1, approvalService.pending(tenantId).size());
    }

    @Test
    public void thePrincipalApprovingWritesThePlan() {
        changeRequestService.requestPlanSave("Grade 6",
                List.of(new FeePlanService.InstalmentSpec("Term 1", new BigDecimal("5000.00"), 0),
                        new FeePlanService.InstalmentSpec("Term 2", new BigDecimal("3000.00"), 120)),
                tenantId, yearId, admin);
        UUID requestId = approvalService.pending(tenantId).get(0).getId();

        approvalService.approve(requestId, tenantId, principal);

        var plan = feePlanRepository.findByTenantIdAndAcademicYearIdAndGradeLevel(tenantId, yearId, "Grade 6");
        assertTrue(plan.isPresent(), "the plan must exist once approved");
        assertEquals(0, new BigDecimal("8000.00").compareTo(plan.get().getAnnualAmount()));
        assertEquals(2, feePlanService.instalmentsOf(plan.get().getId(), tenantId).size());
    }

    @Test
    public void anInvalidPlanIsRefusedAtRequestTime() {
        assertThrows(IllegalArgumentException.class,
                () -> changeRequestService.requestPlanSave("Grade 6", List.of(), tenantId, yearId, admin));
        assertTrue(approvalService.pending(tenantId).isEmpty());
    }
}

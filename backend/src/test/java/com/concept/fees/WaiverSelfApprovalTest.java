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
 * A waiver writes money off a family's bill, so it is the one fee action with
 * a request/approve split. That split decided nothing: the request endpoint is
 * ADMIN-only and the approve endpoint is ADMIN or PRINCIPAL, so the requester
 * was always an eligible approver. Only the audit log knew who asked, and
 * nothing read it back.
 */
@SpringBootTest
@TestPropertySource(properties = "app.dev-mode=true")
@Transactional
public class WaiverSelfApprovalTest {

    @Autowired private FeeManagementService feeManagementService;
    @Autowired private FeeInvoiceRepository feeInvoiceRepository;
    @Autowired private StudentRepository studentRepository;
    @Autowired private ClassSectionRepository classSectionRepository;
    @Autowired private TenantRepository tenantRepository;
    @Autowired private AcademicYearRepository academicYearRepository;
    @Autowired private UserRepository userRepository;

    private UUID tenantId;
    private FeeInvoice invoice;
    private Authentication adminOne;
    private Authentication adminTwo;

    private Authentication makeAdmin(String email, UUID tenantId, UUID yearId) {
        User user = new User();
        user.setId(UUID.randomUUID());
        user.setEmail(email);
        user.setPasswordHash("irrelevant");
        user.setFullName(email);
        user.setRole(UserRole.ADMIN);
        user.setTenantId(tenantId);
        user.setAcademicYearId(yearId);
        userRepository.saveAndFlush(user);
        return new UsernamePasswordAuthenticationToken(email, null,
                List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));
    }

    @BeforeEach
    public void setup() {
        Tenant tenant = new Tenant();
        tenant.setId(UUID.randomUUID());
        tenant.setName("Waiver School");
        tenant.setSubdomain("waiver-" + UUID.randomUUID());
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
        UUID yearId = academicYearRepository.saveAndFlush(year).getId();

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

        adminOne = makeAdmin("admin.one@waiver.test", tenantId, yearId);
        adminTwo = makeAdmin("admin.two@waiver.test", tenantId, yearId);
    }

    @Test
    public void theRequesterCannotApproveTheirOwnWaiver() {
        feeManagementService.requestWaiver(invoice.getId(), new BigDecimal("2000.00"),
                "Sibling concession", tenantId, adminOne);

        IllegalArgumentException refused = assertThrows(IllegalArgumentException.class,
                () -> feeManagementService.decideWaiver(invoice.getId(), true, tenantId, adminOne));
        assertTrue(refused.getMessage().contains("different admin or the principal"),
                "the refusal should say who can approve instead, got: " + refused.getMessage());

        assertEquals(FeeInvoice.FeeWaiverStatus.PENDING,
                feeInvoiceRepository.findById(invoice.getId()).orElseThrow().getWaiverStatus(),
                "a refused self-approval must leave the request pending, not consume it");
    }

    @Test
    public void aDifferentAdminCanApproveIt() {
        feeManagementService.requestWaiver(invoice.getId(), new BigDecimal("2000.00"),
                "Sibling concession", tenantId, adminOne);

        feeManagementService.decideWaiver(invoice.getId(), true, tenantId, adminTwo);

        FeeInvoice settled = feeInvoiceRepository.findById(invoice.getId()).orElseThrow();
        assertEquals(FeeInvoice.FeeWaiverStatus.APPROVED, settled.getWaiverStatus());
        assertEquals(0, new BigDecimal("8000.00").compareTo(settled.getAmountDue()),
                "an approved waiver must come off the outstanding balance");
    }

    @Test
    public void theRequesterMayStillWithdrawTheirOwnRequest() {
        // Rejecting your own request costs the school nothing, and blocking it
        // would strand a request its author regrets.
        feeManagementService.requestWaiver(invoice.getId(), new BigDecimal("2000.00"),
                "Raised by mistake", tenantId, adminOne);

        feeManagementService.decideWaiver(invoice.getId(), false, tenantId, adminOne);

        assertEquals(FeeInvoice.FeeWaiverStatus.REJECTED,
                feeInvoiceRepository.findById(invoice.getId()).orElseThrow().getWaiverStatus());
    }

    @Test
    public void theRequesterIsRecordedOnTheInvoiceNotOnlyInTheAuditLog() {
        feeManagementService.requestWaiver(invoice.getId(), new BigDecimal("500.00"),
                "Hardship", tenantId, adminOne);

        UUID recorded = feeInvoiceRepository.findById(invoice.getId()).orElseThrow()
                .getWaiverRequestedByUserId();
        assertEquals(userRepository.findByEmail("admin.one@waiver.test").orElseThrow().getId(), recorded,
                "decideWaiver compares against this, so it has to be on the invoice");
    }
}

package com.concept.fees;

import com.concept.fees.app.FeePlanChangeRequestService;
import com.concept.fees.app.FeePlanService;
import com.concept.fees.app.ApprovalService;
import com.concept.fees.data.ApprovalRequest;
import com.concept.fees.data.ApprovalRequestRepository;
import com.concept.fees.data.FeePlanRepository;
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * P0-4. A school that signs itself up gets one admin and no principal, and
 * every fee plan it tried to save was refused with "this school does not have
 * one yet" — so it could not price a single grade, and could not begin
 * charging fees at all. Meanwhile "Custom Invoice", which bills a real family
 * a real amount, went through ungated: the control was not protecting the
 * money, it was only blocking the ordinary path to it.
 *
 * <p>These pin the resolution in both directions. Without a principal the
 * change takes effect and says so in the record; with one, the gate is exactly
 * as it was — which is the half that would be easy to lose.
 */
@SpringBootTest
@TestPropertySource(properties = "app.dev-mode=true")
@Transactional
class ApprovalWithoutPrincipalTest {

    @Autowired private FeePlanChangeRequestService changeRequestService;
    @Autowired private ApprovalService approvalService;
    @Autowired private ApprovalRequestRepository approvalRequestRepository;
    @Autowired private FeePlanRepository feePlanRepository;
    @Autowired private TenantRepository tenantRepository;
    @Autowired private AcademicYearRepository academicYearRepository;
    @Autowired private UserRepository userRepository;

    private UUID tenantId;
    private UUID yearId;
    private Authentication admin;

    @BeforeEach
    void setup() {
        Tenant tenant = new Tenant();
        tenant.setId(UUID.randomUUID());
        tenant.setName("Demo SSC");
        tenant.setSubdomain("nopr-" + UUID.randomUUID());
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

        // One admin, no principal — exactly what self-signup produces.
        admin = makeUser("suraj10@gmail.com", UserRole.ADMIN);
    }

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

    private List<FeePlanService.InstalmentSpec> twoTerms() {
        return List.of(
                new FeePlanService.InstalmentSpec("Term 1", new BigDecimal("8000"), 0),
                new FeePlanService.InstalmentSpec("Term 2", new BigDecimal("8000"), 120));
    }

    @Test
    void aSchoolWithNoPrincipalCanSaveAFeePlan() {
        FeePlanChangeRequestService.Outcome outcome =
                changeRequestService.requestPlanSave("Grade 6", twoTerms(), tenantId, yearId, admin);

        assertTrue(outcome.applied(), "the admin must be told the plan is in force, not pending");
        assertFalse(feePlanRepository.findByTenantIdAndAcademicYearIdOrderByGradeLevelAsc(tenantId, yearId).isEmpty(),
                "the plan must actually exist afterwards — this is the blocker");
    }

    /**
     * The weaker control has to be legible in the record. An empty queue and an
     * APPROVED row would imply a decision somebody made; nobody did.
     */
    @Test
    void theMissingSecondApproverIsRecorded() {
        changeRequestService.requestPlanSave("Grade 6", twoTerms(), tenantId, yearId, admin);

        List<ApprovalRequest> rows = approvalRequestRepository.findByTenantIdAndStatusOrderByRequestedAtAsc(
                tenantId, ApprovalRequest.Status.AUTO_APPROVED);
        assertEquals(1, rows.size(), "the request must be kept, marked as auto-approved");
        assertTrue(rows.get(0).getDecisionReason() != null
                        && rows.get(0).getDecisionReason().toLowerCase().contains("no principal"),
                "the reason must say why there was no second approver, got: "
                        + rows.get(0).getDecisionReason());
        assertTrue(approvalService.pending(tenantId).isEmpty(),
                "nothing may be left sitting in a queue nobody can decide");
    }

    /**
     * The half that matters most: appointing a principal restores the gate,
     * with no other change. If this ever goes green the other way round, the
     * control has quietly become optional.
     */
    @Test
    void appointingAPrincipalRestoresTheGate() {
        makeUser("head@demossc.test", UserRole.PRINCIPAL);

        FeePlanChangeRequestService.Outcome outcome =
                changeRequestService.requestPlanSave("Grade 7", twoTerms(), tenantId, yearId, admin);

        assertFalse(outcome.applied(), "with a principal present the change must wait");
        assertTrue(feePlanRepository.findByTenantIdAndAcademicYearIdOrderByGradeLevelAsc(tenantId, yearId).isEmpty(),
                "nothing may be written while the request is pending");
        assertEquals(1, approvalService.pending(tenantId).size());
    }
}

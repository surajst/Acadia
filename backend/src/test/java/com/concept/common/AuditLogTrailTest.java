package com.concept.common;

import com.concept.tenant.SchoolType;
import com.concept.tenant.TenantOnboardingService;
import com.concept.user.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * Every page in the admin console carries an "Audited session" badge, and the
 * Audit Log read back empty after a working day's worth of changes. The write
 * calls were all in place, so this drives the real path — an authenticated
 * admin session, a POST that is supposed to be audited, then the same data
 * endpoint the page fetches — rather than the service in isolation, which is
 * the layer that already looked correct.
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = "app.dev-mode=true")
@Transactional
class AuditLogTrailTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private TenantOnboardingService onboardingService;
    @Autowired private AuditLogRepository auditLogRepository;

    private String adminEmail;
    private UUID tenantId;

    @BeforeEach
    void setup() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        adminEmail = "principal-" + suffix + "@example.com";
        TenantOnboardingService.NewSchool school = onboardingService.createSchool(
                "Demo SSC " + suffix, "demossc" + suffix, adminEmail,
                "AdminPass123!", "Suraj Demo", SchoolType.SECONDARY);
        tenantId = school.tenant.getId();
    }

    private List<AuditLog> rowsForThisSchool() {
        return auditLogRepository
                .findByTenantIdOrderByCreatedAtDesc(tenantId, PageRequest.of(0, 50))
                .getContent();
    }

    /**
     * The narrowest statement of the bug: an audited write by a signed-in
     * admin has to leave a row behind.
     */
    @Test
    void addingAClassSectionIsRecorded() throws Exception {
        mockMvc.perform(post("/web/admin/class-sections/add")
                .with(user(adminEmail).roles("ADMIN"))
                .param("gradeName", "Grade 6")
                .param("sectionName", "A")
                .param("roomNumber", "101"));

        assertTrue(rowsForThisSchool().stream()
                        .anyMatch(r -> "CLASS_SECTION_ADDED".equals(r.getAction())),
                "adding a class section must leave an audit row");
    }

    /**
     * And the page has to be able to read it back. A row that is written but
     * filtered out on the way to the screen looks exactly like no row at all,
     * which is what the report saw.
     */
    @Test
    void theAuditPageReadsBackWhatWasWritten() throws Exception {
        mockMvc.perform(post("/web/admin/class-sections/add")
                .with(user(adminEmail).roles("ADMIN"))
                .param("gradeName", "Grade 7")
                .param("sectionName", "B")
                .param("roomNumber", "201"));

        MvcResult result = mockMvc.perform(get("/web/admin/audit-log/data")
                        .with(user(adminEmail).roles("ADMIN")))
                .andReturn();

        String body = result.getResponse().getContentAsString();
        assertTrue(body.contains("CLASS_SECTION_ADDED"),
                "the audit page must show the row it just wrote, got: " + body);
        assertTrue(body.contains(adminEmail),
                "the row must name who did it, got: " + body);
    }

    /**
     * Signing up already writes a row, before any Authentication exists. It is
     * the one caller of logDirect, and it is the evidence that the write side
     * works at all — so if this passes while the two above fail, the fault is
     * in resolving the actor, not in the repository.
     */
    @Test
    void creatingTheSchoolIsRecorded() {
        assertFalse(rowsForThisSchool().isEmpty(),
                "onboarding writes its own audit row via logDirect");
    }

    /** An unauthenticated caller must not be able to read another school's trail. */
    @Test
    void theAuditFeedIsNotPublic() throws Exception {
        int status = mockMvc.perform(get("/web/admin/audit-log/data"))
                .andReturn().getResponse().getStatus();
        assertTrue(status == 401 || status == 403 || status == 302,
                "expected the audit feed to refuse an anonymous caller, got " + status);
    }

    /**
     * The report listed attendance, XP awards and reward creation among the
     * things the log must carry. Those three were the genuine gap: every other
     * write it named was already recorded. Reward creation is the one reachable
     * from a service call with no fixtures, so it stands for the group here;
     * the attendance and XP emitters are exercised by their own suites.
     */
    @Test
    void creatingARewardIsRecorded() throws Exception {
        mockMvc.perform(post("/web/admin/rewards/create")
                .with(user(adminEmail).roles("ADMIN"))
                .param("title", "Extra library time")
                .param("description", "Twenty minutes")
                .param("xpCost", "40")
                .param("displayEmoji", "B")
                .param("inventoryCount", "5"));

        assertTrue(rowsForThisSchool().stream()
                        .anyMatch(r -> "REWARD_CREATED".equals(r.getAction())),
                "adding a reward must leave an audit row");
    }

    /**
     * And a refused reward must leave nothing behind -- an audit row for a
     * write that did not happen is worse than none, because it is evidence of
     * something untrue.
     */
    @Test
    void aRefusedRewardIsNotRecorded() throws Exception {
        mockMvc.perform(post("/web/admin/rewards/create")
                .with(user(adminEmail).roles("ADMIN"))
                .param("title", "Free XP")
                .param("description", "")
                .param("xpCost", "-50")
                .param("displayEmoji", "B")
                .param("inventoryCount", "5"));

        assertTrue(rowsForThisSchool().stream()
                        .noneMatch(r -> "REWARD_CREATED".equals(r.getAction())),
                "a rejected reward must not be audited as created");
    }
}

package com.concept.fees;

import com.concept.tenant.SchoolType;
import com.concept.tenant.TenantOnboardingService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * The fee plan page now refills the form from the last refused submission,
 * which means Thymeleaf JavaScript inlining in its script block. That fails at
 * render time, not build time, so it needs a test that actually renders the
 * page — in both branches, since the restore path only runs after an error.
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = "app.dev-mode=true")
@Transactional
class FeeSettingsPageTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private TenantOnboardingService onboardingService;

    private String adminEmail;

    @BeforeEach
    void setup() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        adminEmail = "admin-" + suffix + "@example.com";
        onboardingService.createSchool("Demo SSC " + suffix, "feesettings" + suffix, adminEmail,
                "AdminPass123!", "Suraj Demo", SchoolType.SECONDARY);
    }

    @Test
    void thePageRendersWithNothingSubmitted() throws Exception {
        MvcResult result = mockMvc.perform(get("/web/admin/fees/settings")
                .with(user(adminEmail).roles("ADMIN"))).andReturn();

        assertEquals(200, result.getResponse().getStatus());
        assertTrue(result.getResponse().getContentAsString().contains("addInstalment"),
                "the instalment script must render");
    }

    /**
     * A refused plan has to come back with the figures still in it. The report
     * called out losing them: a plan is a dozen typed numbers, and clearing the
     * form on one bad value means typing all of them again.
     */
    @Test
    void aRefusedPlanComesBackWithItsFiguresIntact() throws Exception {
        // Mismatched array lengths are refused before anything is written.
        // This endpoint is not on SecurityConfig's CSRF ignore list, so the real
        // form carries a token and the test has to as well.
        MvcResult refused = mockMvc.perform(post("/web/admin/fees/settings/save")
                .with(user(adminEmail).roles("ADMIN"))
                .with(csrf())
                .param("gradeLevel", "Grade 6")
                .param("label", "Term 1", "Term 2")
                .param("amount", "8000")
                .param("dueOffsetDays", "0", "120"))
                .andReturn();

        assertEquals(302, refused.getResponse().getStatus(),
                "the save must redirect; got " + refused.getResponse().getStatus()
                        + " " + refused.getResponse().getErrorMessage());
        assertTrue(refused.getFlashMap() != null && refused.getFlashMap().containsKey("errorMessage"),
                "the mismatched submission must be refused, or this proves nothing; flash="
                        + refused.getFlashMap());

        // MockMvc does not carry a FlashMap between two perform() calls the way
        // a browser carries it across a redirect, so it is handed over
        // explicitly -- otherwise the page renders the no-error branch and the
        // assertions below pass without the restore path ever running.
        MvcResult page = mockMvc.perform(get("/web/admin/fees/settings")
                .with(user(adminEmail).roles("ADMIN"))
                .flashAttrs(refused.getFlashMap())).andReturn();

        String html = page.getResponse().getContentAsString();
        assertEquals(200, page.getResponse().getStatus(),
                "the restore branch must render rather than blowing up in Thymeleaf");
        // Proves the error branch is the one that rendered -- without this the
        // test would pass just as happily if the flash attributes never arrived.
        assertTrue(html.contains("restoreSubmitted"),
                "the page must take the restore branch after a refused submission");
        assertTrue(html.contains("\"Term 1\"") || html.contains("'Term 1'"),
                "the typed instalment names must come back with it");
    }
}

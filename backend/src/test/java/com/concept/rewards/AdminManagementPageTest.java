package com.concept.rewards;

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
 * The rewards table grew per-row Edit and Remove controls, which are Thymeleaf
 * th:attr expressions and a CSRF meta tag — all of which fail at render time
 * rather than build time. This renders the page with a reward actually in it,
 * because an empty table exercises none of that.
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = "app.dev-mode=true")
@Transactional
class AdminManagementPageTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private TenantOnboardingService onboardingService;

    private String adminEmail;

    @BeforeEach
    void setup() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        adminEmail = "admin-" + suffix + "@example.com";
        onboardingService.createSchool("Demo SSC " + suffix, "mgmt" + suffix, adminEmail,
                "AdminPass123!", "Suraj Demo", SchoolType.SECONDARY);
    }

    @Test
    void theRewardsTableRendersItsRowControls() throws Exception {
        mockMvc.perform(post("/web/admin/rewards/create")
                .with(user(adminEmail).roles("ADMIN"))
                .with(csrf())
                .param("title", "Library pass")
                .param("description", "Twenty minutes")
                .param("xpCost", "40")
                .param("displayEmoji", "B")
                .param("inventoryCount", "5"));

        MvcResult page = mockMvc.perform(get("/web/admin/management")
                .with(user(adminEmail).roles("ADMIN"))).andReturn();

        assertEquals(200, page.getResponse().getStatus());
        String html = page.getResponse().getContentAsString();
        assertTrue(html.contains("Library pass"), "the reward must be listed");
        assertTrue(html.contains("data-reward-edit"), "each row needs its Edit control");
        assertTrue(html.contains("data-reward-delete"), "each row needs its Remove control");
        assertTrue(html.contains("name=\"_csrf\""),
                "the page must carry a token, or Edit and Remove are refused");
    }
}

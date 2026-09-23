package com.concept.common;

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

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

/**
 * A Thymeleaf mistake is a runtime error, not a build error: an expression that
 * does not parse, or a model attribute that is not there, throws when somebody
 * opens the page. The QA pass touched most of these templates, so this opens
 * each one and checks it comes back whole.
 *
 * <p>This is a smoke test and says so -- it asserts the page renders and
 * carries one string that proves the right template ran. What each page
 * <em>says</em> belongs in the test for that behaviour, not here.
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = "app.dev-mode=true")
@Transactional
class PageRenderSmokeTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private TenantOnboardingService onboardingService;

    private String adminEmail;

    @BeforeEach
    void setup() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        adminEmail = "admin-" + suffix + "@example.com";
        onboardingService.createSchool("Demo SSC " + suffix, "render" + suffix, adminEmail,
                "AdminPass123!", "Suraj Demo", SchoolType.SECONDARY);
    }

    private String renderAsAdmin(String path) throws Exception {
        MvcResult result = mockMvc.perform(get(path).with(user(adminEmail).roles("ADMIN"))).andReturn();
        assertEquals(200, result.getResponse().getStatus(),
                path + " did not render: " + result.getResponse().getStatus()
                        + " " + result.getResponse().getErrorMessage());
        return result.getResponse().getContentAsString();
    }

    @Test
    void theAdminDashboardRenders() throws Exception {
        String html = renderAsAdmin("/web/admin/dashboard");
        // The setup checklist replaces the empty state on a school with no
        // students, which this one has none of.
        assertTrue(html.contains("data-setup-checklist"),
                "a school with no students should be offered the checklist");
    }

    @Test
    void theManagementPageRenders() throws Exception {
        assertTrue(renderAsAdmin("/web/admin/management").contains("School Management"));
    }

    @Test
    void theOnboardingWizardRenders() throws Exception {
        String html = renderAsAdmin("/web/onboard/setup");
        assertTrue(html.contains("data-step-panel=\"5\""), "the wizard gained a Students step");
        assertTrue(html.contains("totalCapacity"), "sections are asked for their seat count here");
    }

    @Test
    void theTimetablePageRenders() throws Exception {
        assertTrue(renderAsAdmin("/web/admin/timetable").contains("roomBySection"));
    }

    @Test
    void theAssignmentsPageRenders() throws Exception {
        String html = renderAsAdmin("/web/admin/assignments");
        assertTrue(html.contains("subjectOptions"), "the subject field is bound to the school's list");
        // P2-1: the escape used to reach the browser as six literal characters.
        assertTrue(!html.contains("\\u2013"), "no unrendered unicode escape may survive");
    }

    @Test
    void theAttendancePageRenders() throws Exception {
        String html = renderAsAdmin("/web/teacher/attendance");
        assertTrue(html.contains("attendanceDatePicker"), "the register is dated now");
    }

    @Test
    void theFeePagesRender() throws Exception {
        assertTrue(renderAsAdmin("/web/admin/fees").length() > 0);
        assertTrue(renderAsAdmin("/web/admin/fees/settings").length() > 0);
        assertTrue(renderAsAdmin("/web/admin/fees/collections").contains("receipt"));
    }

    @Test
    void theAuditLogRenders() throws Exception {
        assertTrue(renderAsAdmin("/web/admin/audit-log").length() > 0);
    }

    /**
     * Both of these broke in CI and neither was in this test, which is exactly
     * why they reached CI. The upload page threw on every request because a
     * JavaScript array-of-arrays opens with "[[", which Thymeleaf reads as the
     * start of an inline expression, so it tried to evaluate the CSV header row.
     */
    @Test
    void theImportAndTeacherTaskPagesRender() throws Exception {
        assertTrue(renderAsAdmin("/web/management/upload").contains("downloadCredentials"));
        assertTrue(renderAsAdmin("/web/teacher/tasks").contains("loadGradeOptions"));
    }

    /**
     * No inline script may open a Thymeleaf expression by accident.
     *
     * <p>Thymeleaf treats "[[" as the start of an inline expression wherever it
     * appears in a script body, so an ordinary nested array literal takes the
     * whole page down at render time -- as one did. The deliberate form is
     * {@code /*[[${...}]]*}{@code /}, which this allows. It is a mechanical
     * rule, so it is checked mechanically rather than left to whoever writes
     * the next one.
     */
    @Test
    void noTemplateScriptOpensAThymeleafInlineExpressionByAccident() throws IOException {
        Path templates = Path.of("src/main/resources/templates");
        if (!Files.isDirectory(templates)) {
            return; // not running from the module root
        }

        Pattern accidental = Pattern.compile("\\[\\[(?!\\$\\{)");
        List<String> offenders = new ArrayList<>();

        try (Stream<Path> files = Files.walk(templates)) {
            for (Path file : files.filter(f -> f.toString().endsWith(".html")).toList()) {
                String html = Files.readString(file);
                int from = 0;
                while (true) {
                    int open = html.indexOf("<script", from);
                    if (open < 0) {
                        break;
                    }
                    int close = html.indexOf("</script>", open);
                    if (close < 0) {
                        break;
                    }
                    String body = html.substring(open, close);
                    Matcher m = accidental.matcher(body);
                    if (m.find()) {
                        int start = Math.max(0, m.start() - 40);
                        int end = Math.min(body.length(), m.start() + 40);
                        offenders.add(file.getFileName() + " -> "
                                + body.substring(start, end).replaceAll("\\s+", " "));
                    }
                    from = close + 1;
                }
            }
        }

        assertTrue(offenders.isEmpty(),
                "an inline script contains [[, which Thymeleaf evaluates as an expression: " + offenders);
    }
}

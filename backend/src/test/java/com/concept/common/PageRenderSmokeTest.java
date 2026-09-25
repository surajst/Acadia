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
import java.util.Set;
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
        // The picker asks for a section now, not a grade: a task set against
        // "Grade 6" reached 6-A and 6-B alike.
        assertTrue(renderAsAdmin("/web/teacher/tasks").contains("loadSectionOptions"));
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

    /**
     * No inline script may leave a quoted string open at a line break.
     *
     * <p>A JavaScript string in single or double quotes cannot span lines, so one
     * that does is a syntax error -- and a syntax error anywhere in a script
     * block takes down <em>every</em> inline handler on the page. That is how one
     * mistyped prompt() broke Record Payment, Reverse last payment and the
     * billing override toggle at once, none of which it touched: four Playwright
     * tests failed ten minutes into CI and none of them named the real fault.
     *
     * <p>It happens when a {@code \n} meant for the string is interpreted before
     * it reaches the file. That is a tooling accident rather than a thinking
     * error, which is exactly the kind worth catching mechanically instead of
     * carefully. It has now happened twice.
     *
     * <p>Template literals are excluded because backticks legitimately span
     * lines, and comments are stripped first because an apostrophe in prose
     * ("don't") is not an open string.
     */
    @Test
    void noInlineScriptLeavesAQuotedStringOpenAtALineBreak() throws IOException {
        Path templates = Path.of("src/main/resources/templates");
        if (!Files.isDirectory(templates)) {
            return; // not running from the module root
        }

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
                    int bodyStart = html.indexOf('>', open);
                    int close = html.indexOf("</script>", open);
                    if (bodyStart < 0 || close < 0) {
                        break;
                    }
                    scanScript(html.substring(bodyStart + 1, close), file.getFileName().toString(), offenders);
                    from = close + 1;
                }
            }
        }

        assertTrue(offenders.isEmpty(),
                "a quoted JavaScript string is left open at a line break, which makes the whole "
                        + "script block a syntax error and silently disables every inline handler "
                        + "on the page: " + offenders);
    }

    /**
     * Walks one script body tracking block comments and template literals, and
     * records any line where a quote opens and does not close.
     */
    private static void scanScript(String body, String fileName, List<String> offenders) {
        boolean inBlockComment = false;
        boolean inTemplate = false;
        int lineNumber = 0;

        for (String rawLine : body.split("\n", -1)) {
            lineNumber++;
            String line = rawLine;

            // Strip what is not code, in the order the parser would see it.
            if (inBlockComment) {
                int end = line.indexOf("*/");
                if (end < 0) {
                    continue;
                }
                line = line.substring(end + 2);
                inBlockComment = false;
            }

            StringBuilder code = new StringBuilder();
            char quote = 0;
            for (int i = 0; i < line.length(); i++) {
                char c = line.charAt(i);

                if (inTemplate) {
                    // Backticks may span lines, so they are not this test's
                    // business -- only note where one ends.
                    if (c == '`' && !isEscaped(line, i)) {
                        inTemplate = false;
                    }
                    continue;
                }
                if (quote != 0) {
                    code.append(c);
                    if (c == quote && !isEscaped(line, i)) {
                        quote = 0;
                    }
                    continue;
                }
                if (c == '/' && i + 1 < line.length() && line.charAt(i + 1) == '/') {
                    break; // line comment: the rest is prose
                }
                if (c == '/' && i + 1 < line.length() && line.charAt(i + 1) == '*') {
                    inBlockComment = true;
                    int end = line.indexOf("*/", i + 2);
                    if (end < 0) {
                        break;
                    }
                    inBlockComment = false;
                    i = end + 1;
                    continue;
                }
                if (c == '`') {
                    inTemplate = true;
                    continue;
                }
                // A regex literal can hold a quote character -- .replace(/"/g,
                // ...) is all over these templates -- so it has to be skipped or
                // every one reads as an unterminated string. A slash starts a
                // regex only where a value is expected, which is what the
                // previous significant character tells us; anywhere else it is
                // division. Regex literals cannot span lines, so a run to the
                // end of the line means this was division after all.
                if (c == '/' && startsRegex(code)) {
                    int end = -1;
                    for (int j = i + 1; j < line.length(); j++) {
                        if (line.charAt(j) == '/' && !isEscaped(line, j)) {
                            end = j;
                            break;
                        }
                    }
                    if (end > 0) {
                        i = end;
                        code.append('/');
                        continue;
                    }
                }
                if (c == '\'' || c == '"') {
                    quote = c;
                    code.append(c);
                    continue;
                }
                code.append(c);
            }

            if (quote != 0) {
                offenders.add(fileName + ":" + lineNumber + " -> "
                        + line.strip().substring(0, Math.min(70, line.strip().length())));
            }
        }
    }

    /**
     * Whether a slash here opens a regex literal rather than dividing.
     *
     * <p>Decided by the last significant character of the code seen so far: after
     * an operator, an opening bracket, a comma or nothing at all, a value is
     * expected and a slash begins a regex. After an identifier, a number or a
     * closing bracket, it is division. Good enough for template scripts, and it
     * only has to be right about {@code .replace(/"/g, ...)} and its relatives.
     */
    private static boolean startsRegex(CharSequence codeSoFar) {
        for (int i = codeSoFar.length() - 1; i >= 0; i--) {
            char c = codeSoFar.charAt(i);
            if (Character.isWhitespace(c)) {
                continue;
            }
            return "(,=:[!&|?{};+-*%~^<>".indexOf(c) >= 0;
        }
        return true; // nothing before it: a value is expected
    }

    /** Whether the character at {@code index} is preceded by an odd run of backslashes. */
    private static boolean isEscaped(String line, int index) {
        int backslashes = 0;
        for (int i = index - 1; i >= 0 && line.charAt(i) == '\\'; i--) {
            backslashes++;
        }
        return backslashes % 2 == 1;
    }

    /**
     * Standalone pages scroll with the window; portal pages do not.
     *
     * <p>Two classes of page, two different rules, and getting them the wrong
     * way round is invisible until somebody opens the page on a short window.
     * A signed-in portal page fills the screen and scrolls only its main
     * content, so the sidebar and header stay put. A standalone page -- login,
     * signup, the setup wizard, an error page -- has no sidebar to keep still,
     * and locking it puts a scrollbar inside the form card: at 200% zoom, or
     * on a 360x640 screen with the keyboard open, the last field ends up
     * behind it. That is what hid the password field on signup.
     *
     * <p>Checked mechanically because it is a mechanical rule, and because the
     * symptom only appears at a window size nobody tests at by default.
     */
    @Test
    void standalonePagesScrollWithTheWindowAndPortalPagesDoNot() throws IOException {
        Path templates = Path.of("src/main/resources/templates");
        if (!Files.isDirectory(templates)) {
            return; // not running from the module root
        }

        Set<String> standalone = Set.of(
                "login.html", "onboard_signup.html", "onboard_setup.html", "error.html");

        List<String> problems = new ArrayList<>();
        try (Stream<Path> files = Files.list(templates)) {
            for (Path file : files.filter(f -> f.toString().endsWith(".html")).toList()) {
                String html = Files.readString(file);
                // Only the opening html/body tags and the first style block
                // decide this; an overflow:hidden on some logo crop does not.
                int bodyOpen = html.indexOf("<body");
                String frame = bodyOpen < 0 ? html : html.substring(0, Math.min(html.length(), bodyOpen + 400));
                String squashed = frame.replaceAll("\\s+", " ");
                boolean locksViewport = squashed.contains("height:100vh; overflow:hidden")
                        || squashed.contains("height: 100%; overflow: hidden");

                if (standalone.contains(file.getFileName().toString())) {
                    if (locksViewport) {
                        problems.add(file.getFileName() + " is a standalone page and must scroll "
                                + "with the window, but locks html/body");
                    }
                } else if (html.contains("height:100vh") && !html.contains("height:100dvh")) {
                    problems.add(file.getFileName() + " locks the viewport with 100vh but has no "
                            + "100dvh upgrade, so a mobile URL bar crops it");
                }
            }
        }

        assertTrue(problems.isEmpty(), String.join(" | ", problems));
    }
}

package com.concept.roster;

import com.concept.shared.data.ClassSection;
import com.concept.shared.data.ClassSectionRepository;
import com.concept.shared.data.Student;
import com.concept.shared.data.StudentRepository;
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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * Register Student had no catch at all, so a duplicate roll number or an
 * unusable guardian phone threw past the controller and rendered the plain
 * error page -- taking the modal and all nine fields with it. Re-typing
 * everything to find out which one was wrong is how a roster gets abandoned
 * half-entered.
 *
 * <p>Two halves, asserted separately. The error has to come back as a message on
 * the page rather than an error page, and what was typed has to come back with
 * it. A test that only checks the redirect would pass on a fix that still lost
 * the form.
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = "app.dev-mode=true")
@Transactional
class RegisterStudentErrorTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private TenantOnboardingService onboardingService;
    @Autowired private ClassSectionRepository classSectionRepository;
    @Autowired private StudentRepository studentRepository;

    private String adminEmail;
    private UUID tenantId;
    private UUID yearId;
    private UUID sectionId;

    @BeforeEach
    void setup() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        adminEmail = "admin-" + suffix + "@example.com";
        var school = onboardingService.createSchool("Demo SSC " + suffix, "reg" + suffix,
                adminEmail, "AdminPass123!", "Suraj Demo", SchoolType.SECONDARY);
        tenantId = school.tenant.getId();
        yearId = school.academicYear.getId();

        ClassSection section = new ClassSection();
        section.setId(UUID.randomUUID());
        section.setTenantId(tenantId);
        section.setAcademicYearId(yearId);
        section.setGradeName("Grade 6");
        section.setSectionName("A");
        sectionId = classSectionRepository.saveAndFlush(section).getId();
    }

    private MvcResult register(String firstName, String rollNumber, String guardianPhone) throws Exception {
        return mockMvc.perform(post("/web/admin/student/add")
                        .with(user(adminEmail).roles("ADMIN"))
                        .with(csrf())
                        .param("firstName", firstName)
                        .param("lastName", "Verma")
                        .param("rollNumber", rollNumber)
                        .param("schoolClassId", sectionId.toString())
                        .param("guardianFirstName", "Ramesh")
                        .param("guardianLastName", "Verma")
                        .param("guardianPhone", guardianPhone))
                .andReturn();
    }

    /** The register itself still has to work. */
    @Test
    void anOrdinaryRegistrationSucceeds() throws Exception {
        MvcResult result = register("Aarav", "6A-41", "+91 98765 43210");

        assertEquals("/web/admin/management?success=student_added",
                result.getResponse().getRedirectedUrl());
        assertTrue(studentRepository.findByTenantId(tenantId).stream()
                .anyMatch(s -> "Aarav".equals(s.getFirstName())));
    }

    // ── The refusal ───────────────────────────────────────────────────────────

    /**
     * An unusable guardian phone is the reported trigger. It must come back to
     * the management page with a message, not to the error page.
     */
    @Test
    void aRefusedRegistrationRedirectsBackToTheFormRatherThanAnErrorPage() throws Exception {
        MvcResult result = register("Aarav", "6A-41", "abc123");

        assertEquals("/web/admin/management", result.getResponse().getRedirectedUrl(),
                "the admin has to land back on the page they were working on");
        assertNotNull(result.getFlashMap().get("errorMessage"), "and be told what was wrong");
        // Whether the student row is rolled back cannot be asserted from inside a
        // @Transactional test: the service joins the test's own transaction,
        // which never commits, so the flushed row stays visible either way.
        // RegisterStudentRollbackTest checks that separately.
    }

    /** The point of the fix: nine fields do not have to be typed again. */
    @Test
    void whatWasTypedComesBackWithTheError() throws Exception {
        MvcResult result = register("Aarav", "6A-41", "abc123");

        Object form = result.getFlashMap().get("registerStudentForm");
        assertNotNull(form, "the form's values have to survive the redirect");
        String asText = String.valueOf(form);
        assertTrue(asText.contains("Aarav"), "the name was typed and should still be there: " + asText);
        assertTrue(asText.contains("6A-41"), "and the roll number: " + asText);
        assertTrue(asText.contains("Ramesh"), "and the guardian: " + asText);
        assertTrue(asText.contains(sectionId.toString()), "and the class that was chosen: " + asText);
    }

    /** A password is never handed back into a rendered page, even the admin's own. */
    @Test
    void noSubmittedPasswordIsEchoedBack() throws Exception {
        MvcResult result = mockMvc.perform(post("/web/admin/student/add")
                        .with(user(adminEmail).roles("ADMIN"))
                        .with(csrf())
                        .param("firstName", "Aarav")
                        .param("lastName", "Verma")
                        .param("rollNumber", "6A-41")
                        .param("schoolClassId", sectionId.toString())
                        .param("loginPassword", "SuperSecret123!")
                        .param("guardianPhone", "abc123"))
                .andReturn();

        String asText = String.valueOf(result.getFlashMap().get("registerStudentForm"));
        assertFalse(asText.contains("SuperSecret123!"),
                "a submitted password must not be carried back into the page: " + asText);
    }

    /**
     * A class from another school is the other way in. Asserted separately
     * because it fails before anything is created, where the phone case fails
     * part-way through -- two paths to the same lost form.
     *
     * <p>Note for anyone extending this: a duplicate roll number is <em>not</em>
     * refused. The roll number only seeds a username, and a taken one yields no
     * login rather than an error. Whether a school wants roll numbers to be
     * unique is a separate question from this finding, so nothing here invents
     * that rule.
     */
    @Test
    void aClassFromAnotherSchoolAlsoKeepsTheForm() throws Exception {
        MvcResult result = mockMvc.perform(post("/web/admin/student/add")
                        .with(user(adminEmail).roles("ADMIN"))
                        .with(csrf())
                        .param("firstName", "Neha")
                        .param("lastName", "Verma")
                        .param("rollNumber", "6A-42")
                        .param("schoolClassId", UUID.randomUUID().toString())
                        .param("guardianPhone", "+91 98765 43211"))
                .andReturn();

        assertEquals("/web/admin/management", result.getResponse().getRedirectedUrl());
        assertNotNull(result.getFlashMap().get("errorMessage"));
        String asText = String.valueOf(result.getFlashMap().get("registerStudentForm"));
        assertTrue(asText.contains("Neha"), "the typing must survive this path too: " + asText);
        assertTrue(studentRepository.findByTenantId(tenantId).isEmpty(),
                "this one fails before anything is created");
    }

    // ── What the page then renders ────────────────────────────────────────────

    /**
     * The flash map is not the deliverable -- the rendered page is. This carries
     * the flash attributes into the GET the way the redirect does, so the
     * assertion is on what the admin actually sees.
     *
     * <p>Worth doing explicitly: MockMvc does not carry a FlashMap across
     * perform() calls, so a test that skips this passes while the refill branch
     * never runs at all.
     */
    @Test
    void theRefusedFormIsRenderedBackWithItsValuesAndReopened() throws Exception {
        MvcResult refused = register("Aarav", "6A-41", "abc123");

        String html = mockMvc.perform(get("/web/admin/management")
                        .with(user(adminEmail).roles("ADMIN"))
                        .flashAttrs(refused.getFlashMap()))
                .andReturn().getResponse().getContentAsString();

        assertTrue(bodyTagOf(html).contains("data-register-student-refused"),
                "the modal has to reopen, or the banner reads as an unrelated failure");
        assertTrue(html.contains("value=\"Aarav\""), "the first name has to be back in its field");
        assertTrue(html.contains("value=\"6A-41\""), "and the roll number");
        assertTrue(html.contains("value=\"Ramesh\""), "and the guardian's name");
    }

    /** And an ordinary visit must not reopen the modal or prefill anything. */
    @Test
    void anOrdinaryVisitLeavesTheFormClosedAndEmpty() throws Exception {
        String html = mockMvc.perform(get("/web/admin/management")
                        .with(user(adminEmail).roles("ADMIN")))
                .andReturn().getResponse().getContentAsString();

        assertFalse(bodyTagOf(html).contains("data-register-student-refused"),
                "nothing was refused, so nothing should reopen");
        assertFalse(html.contains("value=\"Aarav\""));
    }

    /**
     * Just the opening body tag. The marker's name also appears in the script
     * that reads it, so searching the whole page finds it either way -- an
     * assertion that would have passed against no fix at all.
     */
    private static String bodyTagOf(String html) {
        int open = html.indexOf("<body");
        if (open < 0) {
            return "";
        }
        int close = html.indexOf('>', open);
        return close < 0 ? html.substring(open) : html.substring(open, close + 1);
    }
}

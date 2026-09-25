package com.concept.tasks;

import com.concept.assignment.app.SubjectAssignmentService;
import com.concept.shared.data.ClassSection;
import com.concept.shared.data.ClassSectionRepository;
import com.concept.tenant.SchoolType;
import com.concept.tenant.TenantOnboardingService;
import com.concept.user.User;
import com.concept.user.UserRepository;
import com.concept.user.UserRole;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * A task worth -10 XP reached a child's Challenges list as "+-10 XP".
 *
 * <p>Both clients looked safe: the portal form carried {@code min="1"} and the
 * app's New task screen stripped the minus sign. A browser honours a min
 * attribute and an HTTP client ignores it, so neither was a rule.
 *
 * <p>These post the bad value <em>straight at the API</em>, which is the only
 * way to test the thing that was actually missing. Twice now a client-side
 * guard has existed while the server accepted the value behind it.
 *
 * <p>Worth stating why this test can exist at all: the annotations were
 * unenforceable until spring-boot-starter-validation was added to the build.
 * It is not transitive through starter-web, and an {@code @Valid} with no
 * validator on the classpath is silently a no-op -- which would have looked
 * exactly like a fix.
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = "app.dev-mode=true")
@Transactional
class TaskXpValidationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private TenantOnboardingService onboardingService;
    @Autowired private ClassSectionRepository classSectionRepository;
    @Autowired private SubjectAssignmentService assignmentService;
    @Autowired private UserRepository userRepository;

    private String teacherEmail;
    /**
     * A class task names the section it is for now. The teacher has to be a real
     * row assigned to a real section, or every post here would be refused for
     * that reason instead -- and the XP assertions below would pass whether or
     * not the XP rule existed at all.
     */
    private UUID sectionId;

    @BeforeEach
    void setup() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        teacherEmail = "teacher-" + suffix + "@example.com";
        var school = onboardingService.createSchool("Demo SSC " + suffix, "xp" + suffix,
                "admin-" + suffix + "@example.com", "AdminPass123!", "Suraj Demo", SchoolType.SECONDARY);

        UUID tenantId = school.tenant.getId();
        UUID yearId = school.academicYear.getId();

        ClassSection section = new ClassSection();
        section.setId(UUID.randomUUID());
        section.setTenantId(tenantId);
        section.setAcademicYearId(yearId);
        section.setGradeName("Grade 6");
        section.setSectionName("A");
        sectionId = classSectionRepository.saveAndFlush(section).getId();

        User priya = new User();
        priya.setId(UUID.randomUUID());
        priya.setTenantId(tenantId);
        priya.setAcademicYearId(yearId);
        priya.setEmail(teacherEmail);
        priya.setPasswordHash("irrelevant");
        priya.setFullName("Priya Demo");
        priya.setRole(UserRole.TEACHER);
        priya = userRepository.saveAndFlush(priya);
        assignmentService.assignSubject(priya.getId(), sectionId, "Mathematics", true, tenantId);
    }

    private MvcResult postTask(String xpReward) throws Exception {
        String body = """
                {"title":"QA Fractions worksheet","description":"Practice",
                 "subjectCode":"MATH","taskType":"HOMEWORK","standard":6,
                 "assignedToClass":true,"classSectionId":"%s","xpReward":%s}
                """.formatted(sectionId, xpReward);
        return mockMvc.perform(post("/api/teacher/tasks/create")
                        .with(user(teacherEmail).roles("TEACHER"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andReturn();
    }

    /** The reported value. */
    @Test
    void negativeXpIsRefusedByTheApi() throws Exception {
        MvcResult result = postTask("-10");

        assertEquals(400, result.getResponse().getStatus(),
                "the API accepted -10 and the student app rendered it as \"+-10 XP\"");
        String body = result.getResponse().getContentAsString();
        assertTrue(body.contains("error"), "clients read the error field, got: " + body);
        assertTrue(body.toLowerCase().contains("at least 1"),
                "the message has to say what the rule is, got: " + body);
    }

    /** Zero is the same fault with a smaller number. */
    @Test
    void zeroXpIsRefused() throws Exception {
        assertEquals(400, postTask("0").getResponse().getStatus());
    }

    /** And a typo of 1000 for 100 should not become a reward nobody can match. */
    @Test
    void anAbsurdlyLargeRewardIsRefused() throws Exception {
        assertEquals(400, postTask("999999").getResponse().getStatus());
    }

    /** The bound must not refuse an ordinary task. */
    @Test
    void anOrdinaryRewardIsAccepted() throws Exception {
        int status = postTask("10").getResponse().getStatus();
        assertTrue(status < 400, "a 10 XP homework task is the ordinary case, got " + status);
    }

    /** A blank title was accepted too, and renders as an unnamed row. */
    @Test
    void aTaskWithNoTitleIsRefused() throws Exception {
        String body = """
                {"title":"  ","description":"Practice","subjectCode":"MATH",
                 "taskType":"HOMEWORK","standard":6,"assignedToClass":true,
                 "classSectionId":"%s","xpReward":10}
                """.formatted(sectionId);
        MvcResult result = mockMvc.perform(post("/api/teacher/tasks/create")
                        .with(user(teacherEmail).roles("TEACHER"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andReturn();
        assertEquals(400, result.getResponse().getStatus());
    }
}

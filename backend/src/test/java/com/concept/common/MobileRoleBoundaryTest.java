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
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

/**
 * A parent typing /quests into the app opened student screens. Guarding the
 * routes in Expo Router fixes the screens; it does nothing about the data,
 * because the app is not what decides who may read a student's dashboard.
 *
 * <p>Only /api/mobile/driver had a URL rule. The student and parent trees
 * relied on @PreAuthorize per method, applied unevenly --
 * /api/mobile/student/dashboard, /attendance and /syllabus carried none at
 * all, so a parent's token fetched a student's dashboard and was answered with
 * data. These assert the boundary at the API, which is where it has to hold
 * whatever any client does.
 *
 * <p>403 or 401 both count as refused; which one depends on how the request is
 * authenticated. What must never happen is 200.
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = "app.dev-mode=true")
@Transactional
class MobileRoleBoundaryTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private TenantOnboardingService onboardingService;

    private String someone;

    @BeforeEach
    void setup() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        someone = "person-" + suffix + "@example.com";
        onboardingService.createSchool("Demo SSC " + suffix, "role" + suffix,
                "admin-" + suffix + "@example.com", "AdminPass123!", "Suraj Demo", SchoolType.SECONDARY);
    }

    private int statusFor(String path, String role) throws Exception {
        return mockMvc.perform(get(path).with(user(someone).roles(role)))
                .andReturn().getResponse().getStatus();
    }

    private void assertRefused(String path, String role) throws Exception {
        int status = statusFor(path, role);
        assertTrue(status == 403 || status == 401,
                role + " must not be served " + path + ", got " + status);
    }

    /** The reported case, at the layer that actually decides it. */
    @Test
    void aParentIsRefusedTheStudentTree() throws Exception {
        assertRefused("/api/mobile/student/dashboard", "PARENT");
        assertRefused("/api/mobile/student/attendance", "PARENT");
        assertRefused("/api/mobile/student/syllabus", "PARENT");
        assertRefused("/api/mobile/student/tasks", "PARENT");
        assertRefused("/api/mobile/student/timetable", "PARENT");
    }

    /**
     * The three with no annotation are called out separately: they are the ones
     * that answered with data, and a rule that only covers the annotated ones
     * would look like a fix while leaving these open.
     */
    @Test
    void theEndpointsThatHadNoAnnotationAreAlsoRefused() throws Exception {
        assertRefused("/api/mobile/student/dashboard", "TEACHER");
        assertRefused("/api/mobile/student/attendance", "ADMIN");
        assertRefused("/api/mobile/student/syllabus", "DRIVER");
    }

    @Test
    void aStudentIsRefusedTheParentTree() throws Exception {
        assertRefused("/api/mobile/parent/dashboard", "STUDENT");
    }

    @Test
    void aStudentIsRefusedTheTeacherTree() throws Exception {
        assertRefused("/api/mobile/teacher/badges", "STUDENT");
        assertRefused("/api/mobile/teacher/badges", "PARENT");
    }

    /**
     * And the guard must not lock out the roles that belong there -- a rule
     * that refuses everyone passes the tests above and breaks the product.
     */
    @Test
    void theRightRoleIsStillLetThrough() throws Exception {
        assertEquals(200, statusFor("/api/mobile/teacher/badges", "TEACHER"),
                "a teacher must still be able to read the badge catalogue");
    }

    /** Own profile: any signed-in role has one, so this must not be role-gated. */
    @Test
    void everySignedInRoleCanReachTheirOwnProfileRoute() throws Exception {
        for (String role : new String[] {"STUDENT", "PARENT", "TEACHER", "ADMIN"}) {
            int status = statusFor("/api/mobile/user/profile", role);
            assertTrue(status != 403,
                    role + " must not be forbidden from their own profile, got " + status);
        }
    }

    /** Anonymous callers get nothing from any of these trees. */
    @Test
    void anAnonymousCallerIsRefused() throws Exception {
        for (String path : new String[] {
                "/api/mobile/student/dashboard",
                "/api/mobile/parent/dashboard",
                "/api/mobile/teacher/badges"}) {
            int status = mockMvc.perform(get(path)).andReturn().getResponse().getStatus();
            assertTrue(status == 401 || status == 403 || status == 302,
                    path + " must refuse an anonymous caller, got " + status);
        }
    }
}

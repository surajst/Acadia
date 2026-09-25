package com.concept.common;

import com.concept.tenant.SchoolType;
import com.concept.tenant.TenantOnboardingService;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

/**
 * A parent's token lasts 24 hours. Rakesh's expired overnight, and the next
 * morning the app showed him "No fees raised yet. The school has not billed
 * anything for Your child" over 450 rupees outstanding, and a home screen reading
 * "Your child, Level 1, School 0".
 *
 * <p>None of that was the app inventing data. The API refused every request with
 * <b>403</b>, which is the same answer it gives a signed-in parent who reaches a
 * teacher's endpoint -- so the app could not tell "your session ended, sign in
 * again" from "this is not yours to see", and treated both as "there is nothing
 * here".
 *
 * <p>The cause was an absence: the {@code /api/**} chain had no
 * AuthenticationEntryPoint, so Spring Security fell back to
 * Http403ForbiddenEntryPoint. These tests pin the distinction that fixes it --
 * 401 with a reason for "not signed in", 403 for "signed in and not allowed" --
 * because it is the kind of thing a later refactor restores to a default without
 * noticing.
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = "app.dev-mode=true")
@Transactional
class ExpiredTokenTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private TenantOnboardingService onboardingService;

    @Value("${app.jwt-secret}")
    private String jwtSecret;

    private String parentEmail;

    @BeforeEach
    void setup() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        parentEmail = "rakesh-" + suffix + "@example.com";
        onboardingService.createSchool("Demo SSC " + suffix, "exp" + suffix,
                "admin-" + suffix + "@example.com", "AdminPass123!", "Suraj Demo", SchoolType.SECONDARY);
    }

    /**
     * A token that was valid and has run out, signed with the real key so it is
     * genuinely expired rather than merely wrong.
     */
    private String expiredTokenFor(String subject) {
        SecretKey key = Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8));
        long twoDaysAgo = System.currentTimeMillis() - (2L * 24 * 60 * 60 * 1000);
        // jjwt 0.11.5: setSubject/setIssuedAt/setExpiration, not the 0.12 names.
        return Jwts.builder()
                .setSubject(subject)
                .setIssuedAt(new Date(twoDaysAgo))
                .setExpiration(new Date(twoDaysAgo + 1000))
                .signWith(key)
                .compact();
    }

    private MvcResult callWith(String bearer) throws Exception {
        var request = get("/api/mobile/parent/dashboard");
        if (bearer != null) {
            request = request.header("Authorization", "Bearer " + bearer);
        }
        return mockMvc.perform(request).andReturn();
    }

    // ── The reported case ─────────────────────────────────────────────────────

    /** The whole point: an expired token is 401, not 403. */
    @Test
    void anExpiredTokenIsUnauthorisedNotForbidden() throws Exception {
        MvcResult result = callWith(expiredTokenFor(parentEmail));

        assertEquals(401, result.getResponse().getStatus(),
                "an expired session must be 401, or the app cannot tell it from a permission refusal");
    }

    /**
     * And it says which, because that is what lets the app clear the stored login
     * rather than treat the refusal as "no data".
     */
    @Test
    void theRefusalSaysTheSessionExpired() throws Exception {
        String body = callWith(expiredTokenFor(parentEmail)).getResponse().getContentAsString();

        assertTrue(body.contains("\"reason\":\"expired\""),
                "the app keys its sign-out on this, got: " + body);
        assertTrue(body.toLowerCase().contains("sign in"),
                "and it has to be readable if the app shows it, got: " + body);
    }

    // ── The other three ways in ───────────────────────────────────────────────

    @Test
    void noTokenAtAllIsAlsoUnauthorised() throws Exception {
        MvcResult result = callWith(null);

        assertEquals(401, result.getResponse().getStatus());
        assertTrue(result.getResponse().getContentAsString().contains("\"reason\":\"missing\""),
                "nothing was sent, which is not the same as something that expired");
    }

    /** A token signed with the wrong key never was valid, and says so differently. */
    @Test
    void aForgedTokenIsUnauthorisedAndReportedAsInvalid() throws Exception {
        SecretKey wrongKey = Keys.hmacShaKeyFor(
                "this-is-not-the-real-signing-key-and-is-long-enough".getBytes(StandardCharsets.UTF_8));
        String forged = Jwts.builder()
                .setSubject(parentEmail)
                .setIssuedAt(new Date())
                .setExpiration(new Date(System.currentTimeMillis() + 60_000))
                .signWith(wrongKey)
                .compact();

        MvcResult result = callWith(forged);

        assertEquals(401, result.getResponse().getStatus());
        assertTrue(result.getResponse().getContentAsString().contains("\"reason\":\"invalid\""),
                "a forged token never expired; calling it expired would be misleading");
    }

    @Test
    void aBearerHeaderHoldingNonsenseIsUnauthorised() throws Exception {
        MvcResult result = callWith("not-a-jwt-at-all");

        assertEquals(401, result.getResponse().getStatus());
    }

    // ── 403 has to keep meaning something different ───────────────────────────

    /**
     * The half that a careless fix breaks. If everything became 401, the app would
     * sign a parent out for tapping through to a teacher's screen -- losing their
     * session over a wrong turn.
     */
    @Test
    void aSignedInUserReachingSomethingElsesEndpointIsStillForbidden() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/mobile/teacher/badges")
                        .with(user(parentEmail).roles("PARENT")))
                .andReturn();

        assertEquals(403, result.getResponse().getStatus(),
                "a signed-in parent is authenticated; they are simply not allowed here");
    }

    @Test
    void theForbiddenBodyNeverClaimsTheSessionExpired() throws Exception {
        String body = mockMvc.perform(get("/api/mobile/teacher/badges")
                        .with(user(parentEmail).roles("PARENT")))
                .andReturn().getResponse().getContentAsString();

        assertTrue(body.contains("\"reason\":\"forbidden\""), "got: " + body);
        assertTrue(!body.contains("expired"),
                "a client that signs out on \"expired\" must not be told that here: " + body);
    }

    /** And a role that IS allowed still gets through, so none of this over-refuses. */
    @Test
    void theRightRoleIsUnaffected() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/mobile/teacher/badges")
                        .with(user("teacher-" + UUID.randomUUID() + "@example.com").roles("TEACHER")))
                .andReturn();

        assertEquals(200, result.getResponse().getStatus(),
                "a teacher reading the badge catalogue must be unaffected by any of this");
    }
}

package com.concept.tenant;

import com.concept.common.RateLimiter;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

/**
 * {@code /api/onboard/subdomain-available} has to be unauthenticated -- nobody
 * has an account at signup -- and it answers whether a given address belongs to
 * a school on ACADIA. Left open it is a directory anyone can walk: a few
 * thousand requests enumerate the customer list off a signup form.
 *
 * <p>Two things have to hold at once, and the second is the one that gets broken
 * by a careless limit: scraping has to become not worth doing, and someone
 * typing a school name -- which checks the field as they type -- must never hit
 * it.
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = "app.dev-mode=true")
@Transactional
class SubdomainRateLimitTest {

    private static final int LIMIT = TenantOnboardingApiController.SUBDOMAIN_CHECKS_PER_MINUTE;

    @Autowired private MockMvc mockMvc;
    @Autowired private RateLimiter rateLimiter;

    @BeforeEach
    void clearCounts() {
        // The limiter is a singleton and counts across tests, so each one starts
        // from zero or the order of tests decides the result.
        rateLimiter.reset();
    }

    private int check(String from, String subdomain) throws Exception {
        MvcResult result = mockMvc.perform(get("/api/onboard/subdomain-available")
                        .param("subdomain", subdomain)
                        .header("X-Forwarded-For", from))
                .andReturn();
        return result.getResponse().getStatus();
    }

    /** Typing a name must never trip it. */
    @Test
    void anOrdinarySignupIsNotRateLimited() throws Exception {
        for (int i = 0; i < 12; i++) {
            assertEquals(200, check("203.0.113.10", "silverbrook".substring(0, 3 + (i % 8))),
                    "checking the field while typing must not be refused (attempt " + (i + 1) + ")");
        }
    }

    /** And bulk scraping from one place stops. */
    @Test
    void enumeratingFromOneAddressIsCutOff() throws Exception {
        for (int i = 0; i < LIMIT; i++) {
            assertEquals(200, check("198.51.100.7", "school" + i),
                    "the first " + LIMIT + " have to be served (attempt " + (i + 1) + ")");
        }

        assertEquals(429, check("198.51.100.7", "school-over-the-limit"),
                "past the limit the endpoint has to refuse rather than keep answering");
    }

    /** The refusal says something a person can act on, not just a status. */
    @Test
    void theRefusalCarriesAReadableMessage() throws Exception {
        for (int i = 0; i < LIMIT; i++) {
            check("198.51.100.8", "school" + i);
        }

        MvcResult result = mockMvc.perform(get("/api/onboard/subdomain-available")
                        .param("subdomain", "anything")
                        .header("X-Forwarded-For", "198.51.100.8"))
                .andReturn();
        String body = result.getResponse().getContentAsString();
        assertEquals(429, result.getResponse().getStatus());
        assertTrue(body.toLowerCase().contains("too many"), "got: " + body);
    }

    /**
     * One noisy caller must not lock out everybody else. Worth its own test
     * because keying on {@code getRemoteAddr()} behind Render's proxy would put
     * every request in one bucket, and thirty checks would take signup down for
     * the whole internet.
     */
    @Test
    void oneAddressBeingCutOffDoesNotAffectAnother() throws Exception {
        for (int i = 0; i < LIMIT + 5; i++) {
            check("198.51.100.9", "school" + i);
        }
        assertEquals(429, check("198.51.100.9", "still-blocked"));

        assertEquals(200, check("203.0.113.55", "a-different-school"),
                "a different client has its own count");
    }

    /** A request with no forwarded address still has to work. */
    @Test
    void aRequestWithNoForwardedAddressIsStillServed() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/onboard/subdomain-available")
                        .param("subdomain", "silverbrook"))
                .andReturn();

        assertEquals(200, result.getResponse().getStatus(),
                "stripping the header must not break signup for anyone behind that proxy");
    }

    /** The limiter itself, away from HTTP: the window is per key and counts inclusively. */
    @Test
    void theLimiterAllowsExactlyTheLimit() {
        String key = "unit-" + UUID.randomUUID();
        for (int i = 1; i <= LIMIT; i++) {
            assertTrue(rateLimiter.tryAcquire(key, LIMIT), "request " + i + " is within the limit");
        }
        assertTrue(!rateLimiter.tryAcquire(key, LIMIT), "one past the limit is refused");
    }

    /** A null key cannot be counted, and must not be treated as a refusal. */
    @Test
    void anUnkeyableRequestIsAllowedThrough() {
        assertTrue(rateLimiter.tryAcquire(null, 1));
        assertTrue(rateLimiter.tryAcquire("   ", 1));
    }
}

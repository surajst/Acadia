package com.concept.config;

import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * The filter is disabled whenever app.dev-mode=true, which is how every other
 * test and the CI end-to-end job start the app — so it is only ever exercised
 * by constructing it directly with devMode=false, as these tests do.
 */
class RateLimitFilterTest {

    private static final int SIGNUP_LIMIT = 3;
    private static final int LOGIN_LIMIT = 5;

    private RateLimitFilter enabledFilter() {
        return new RateLimitFilter(false, true, SIGNUP_LIMIT, LOGIN_LIMIT);
    }

    private MockHttpServletRequest post(String path, String forwardedFor) {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", path);
        request.setServletPath(path);
        request.setRemoteAddr("10.0.0.1");
        if (forwardedFor != null) {
            request.addHeader("X-Forwarded-For", forwardedFor);
        }
        return request;
    }

    @Test
    void allowsRequestsUpToTheSignupLimitThenReturns429() throws Exception {
        RateLimitFilter filter = enabledFilter();
        FilterChain chain = mock(FilterChain.class);

        for (int i = 0; i < SIGNUP_LIMIT; i++) {
            MockHttpServletResponse response = new MockHttpServletResponse();
            filter.doFilter(post("/api/onboard/create-school", "203.0.113.7"), response, chain);
            assertEquals(200, response.getStatus(), "request " + (i + 1) + " should pass");
        }

        MockHttpServletResponse blocked = new MockHttpServletResponse();
        filter.doFilter(post("/api/onboard/create-school", "203.0.113.7"), blocked, chain);

        assertEquals(429, blocked.getStatus());
        assertNotNull(blocked.getHeader("Retry-After"));
        verify(chain, times(SIGNUP_LIMIT)).doFilter(org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any());
    }

    @Test
    void countsSignupAndLoginAgainstSeparateQuotas() throws Exception {
        RateLimitFilter filter = enabledFilter();
        FilterChain chain = mock(FilterChain.class);

        for (int i = 0; i < SIGNUP_LIMIT + 1; i++) {
            filter.doFilter(post("/api/onboard/create-school", "203.0.113.7"),
                    new MockHttpServletResponse(), chain);
        }

        MockHttpServletResponse login = new MockHttpServletResponse();
        filter.doFilter(post("/login", "203.0.113.7"), login, chain);
        assertEquals(200, login.getStatus(), "login has its own quota, unaffected by signup");
    }

    @Test
    void quotaIsPerClientIpNotShared() throws Exception {
        RateLimitFilter filter = enabledFilter();
        FilterChain chain = mock(FilterChain.class);

        for (int i = 0; i < SIGNUP_LIMIT + 1; i++) {
            filter.doFilter(post("/api/onboard/create-school", "203.0.113.7"),
                    new MockHttpServletResponse(), chain);
        }

        MockHttpServletResponse other = new MockHttpServletResponse();
        filter.doFilter(post("/api/onboard/create-school", "198.51.100.4"), other, chain);
        assertEquals(200, other.getStatus(), "a different client IP gets its own quota");
    }

    /**
     * The bypass this filter has to survive: a caller sends their own
     * X-Forwarded-For and the proxy appends the real IP, so only the trailing
     * entry is trustworthy. Spoofing the leading entry must not mint a new quota.
     */
    @Test
    void spoofedLeadingForwardedForEntriesDoNotBypassTheLimit() throws Exception {
        RateLimitFilter filter = enabledFilter();
        FilterChain chain = mock(FilterChain.class);

        for (int i = 0; i < SIGNUP_LIMIT; i++) {
            MockHttpServletResponse response = new MockHttpServletResponse();
            filter.doFilter(post("/api/onboard/create-school", "1.2.3." + i + ", 203.0.113.7"), response, chain);
            assertEquals(200, response.getStatus());
        }

        MockHttpServletResponse blocked = new MockHttpServletResponse();
        filter.doFilter(post("/api/onboard/create-school", "9.9.9.9, 203.0.113.7"), blocked, chain);
        assertEquals(429, blocked.getStatus(), "rotating the spoofable leading hop must not reset the quota");
    }

    @Test
    void ignoresUnthrottledPathsAndNonPostMethods() throws Exception {
        RateLimitFilter filter = enabledFilter();
        FilterChain chain = mock(FilterChain.class);

        for (int i = 0; i < SIGNUP_LIMIT + 5; i++) {
            MockHttpServletResponse response = new MockHttpServletResponse();
            filter.doFilter(post("/web/admin/dashboard", "203.0.113.7"), response, chain);
            assertEquals(200, response.getStatus());
        }

        for (int i = 0; i < LOGIN_LIMIT + 5; i++) {
            // A fresh request each time: OncePerRequestFilter marks the request
            // it has already seen, so reusing one instance would not re-enter.
            MockHttpServletRequest get = new MockHttpServletRequest("GET", "/login");
            get.setServletPath("/login");
            get.addHeader("X-Forwarded-For", "203.0.113.7");
            MockHttpServletResponse response = new MockHttpServletResponse();
            filter.doFilter(get, response, chain);
            assertEquals(200, response.getStatus(), "GET /login is the login page, not an attempt");
        }
    }

    @Test
    void isInertWhenDevModeIsOn() throws Exception {
        RateLimitFilter filter = new RateLimitFilter(true, true, SIGNUP_LIMIT, LOGIN_LIMIT);
        FilterChain chain = mock(FilterChain.class);

        for (int i = 0; i < SIGNUP_LIMIT + 10; i++) {
            MockHttpServletResponse response = new MockHttpServletResponse();
            filter.doFilter(post("/api/onboard/create-school", "203.0.113.7"), response, chain);
            assertEquals(200, response.getStatus());
        }
    }
}

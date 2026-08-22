package com.concept.config;

import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The correlation id has to be present during the request, echoed to the
 * caller, and gone afterwards -- a stale MDC value on a pooled Tomcat thread
 * would mislabel the next unrelated request, which is worse than no id at all.
 */
class RequestIdFilterTest {

    private final RequestIdFilter filter = new RequestIdFilter();

    /** Captures what the MDC held while the rest of the chain was running. */
    private AtomicReference<String> runChain(MockHttpServletRequest request, MockHttpServletResponse response)
            throws Exception {
        AtomicReference<String> seen = new AtomicReference<>();
        FilterChain chain = (req, res) -> seen.set(MDC.get(RequestIdFilter.MDC_KEY));
        filter.doFilter(request, response, chain);
        return seen;
    }

    @Test
    void generatesAnIdWhenTheCallerSendsNone() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        String seen = runChain(new MockHttpServletRequest(), response).get();

        assertNotNull(seen, "the chain must run with an id in the MDC");
        assertEquals(seen, response.getHeader(RequestIdFilter.HEADER),
                "the echoed header must match what was logged");
        assertDoesNotThrow(() -> UUID.fromString(seen));
    }

    @Test
    void reusesACleanInboundId() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(RequestIdFilter.HEADER, "abc-123_XYZ");
        MockHttpServletResponse response = new MockHttpServletResponse();

        assertEquals("abc-123_XYZ", runChain(request, response).get());
        assertEquals("abc-123_XYZ", response.getHeader(RequestIdFilter.HEADER));
    }

    @Test
    void rejectsAnIdThatCouldForgeLogLines() throws Exception {
        // A newline in the id would let a caller write what looks like its own
        // log entry. Anything outside [A-Za-z0-9_-] is dropped, not escaped.
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(RequestIdFilter.HEADER, "evil\nINFO Login succeeded for admin");
        MockHttpServletResponse response = new MockHttpServletResponse();

        String seen = runChain(request, response).get();
        assertFalse(seen.contains("\n"));
        assertDoesNotThrow(() -> UUID.fromString(seen), "a rejected id falls back to a generated one");
    }

    @Test
    void rejectsAnOverlongId() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(RequestIdFilter.HEADER, "a".repeat(65));
        MockHttpServletResponse response = new MockHttpServletResponse();

        assertDoesNotThrow(() -> UUID.fromString(runChain(request, response).get()));
    }

    @Test
    void clearsTheMdcSoAPooledThreadDoesNotInheritIt() throws Exception {
        filter.doFilter(new MockHttpServletRequest(), new MockHttpServletResponse(), (req, res) -> { });
        assertNull(MDC.get(RequestIdFilter.MDC_KEY));
    }

    @Test
    void clearsTheMdcEvenWhenTheRequestFails() throws Exception {
        FilterChain boom = (req, res) -> { throw new IllegalStateException("handler blew up"); };
        assertThrows(IllegalStateException.class,
                () -> filter.doFilter(new MockHttpServletRequest(), new MockHttpServletResponse(), boom));
        assertNull(MDC.get(RequestIdFilter.MDC_KEY),
                "an exception must not leave a stale id on the thread");
    }
}

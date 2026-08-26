package com.concept.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

/**
 * Tags every log line of one request with the same id.
 *
 * <p>Without this, a report of "it failed around 3pm" means reading
 * interleaved lines from every concurrent request and guessing which belong
 * together. With it, one id from the response header pulls the whole request
 * out of the log.
 *
 * <p>Runs before {@link RateLimitFilter} so that a 429 is correlated too --
 * throttling complaints are exactly the kind that arrive without much detail.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestIdFilter extends OncePerRequestFilter {

    static final String MDC_KEY = "requestId";
    static final String HEADER = "X-Request-Id";
    private static final int MAX_LENGTH = 64;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String requestId = sanitised(request.getHeader(HEADER));
        if (requestId == null) {
            requestId = UUID.randomUUID().toString();
        }
        MDC.put(MDC_KEY, requestId);
        response.setHeader(HEADER, requestId);
        try {
            chain.doFilter(request, response);
        } finally {
            // Tomcat reuses threads, so a stale id would be inherited by the
            // next unrelated request on this thread and quietly mislabel it.
            MDC.remove(MDC_KEY);
        }
    }

    /**
     * An inbound id is echoed so a caller can correlate across services, but
     * it lands in log lines, so it is not taken on trust: anything not plain
     * alphanumeric/dash/underscore, or over {@value #MAX_LENGTH} characters,
     * is discarded in favour of a generated one. That blocks newline injection
     * (forging log lines) and unbounded values.
     */
    private static String sanitised(String candidate) {
        if (candidate == null || candidate.isBlank() || candidate.length() > MAX_LENGTH) {
            return null;
        }
        for (int i = 0; i < candidate.length(); i++) {
            char c = candidate.charAt(i);
            boolean allowed = (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z')
                    || (c >= '0' && c <= '9') || c == '-' || c == '_';
            if (!allowed) {
                return null;
            }
        }
        return candidate;
    }
}

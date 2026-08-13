package com.concept.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Per-IP throttling for the handful of endpoints an unauthenticated caller can
 * reach. Without this, public school signup can be driven in a loop to create
 * unbounded Tenants, and the login endpoints can be brute-forced for free.
 *
 * <p>Counters are held in memory, which is correct for the current
 * single-instance deployment and is the reason this is a filter rather than a
 * dependency on Redis. If the backend is ever scaled past one instance, each
 * instance will enforce its own quota and the effective limit multiplies by the
 * instance count — move the counters to a shared store at that point.
 *
 * <p>Disabled whenever {@code app.dev-mode=true}, which is how local runs and
 * the CI end-to-end job start the server; those drive many logins in seconds
 * and would otherwise throttle themselves.
 */
// Must run BEFORE Spring Security's FilterChainProxy (registered at order -100).
// Form login for /login is handled inside that chain, so a filter left at the
// default LOWEST_PRECEDENCE would never see the request it is meant to throttle.
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class RateLimitFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(RateLimitFilter.class);

    /**
     * A quota: how many requests one IP may make to one rule within one window.
     */
    private record Rule(String name, List<String> paths, int limit, Duration window) {
        boolean matches(String path) {
            return paths.contains(path);
        }
    }

    /** Fixed-window counter. Reset happens lazily on the first request after expiry. */
    private static final class Window {
        final AtomicInteger count = new AtomicInteger();
        volatile Instant resetAt;

        Window(Instant resetAt) {
            this.resetAt = resetAt;
        }
    }

    private final List<Rule> rules;
    private final boolean enabled;
    private final Map<String, Window> windows = new ConcurrentHashMap<>();

    public RateLimitFilter(
            @Value("${app.dev-mode:false}") boolean devMode,
            @Value("${app.rate-limit.enabled:true}") boolean rateLimitEnabled,
            @Value("${app.rate-limit.signup-per-hour:5}") int signupPerHour,
            @Value("${app.rate-limit.login-per-15min:20}") int loginPer15Min) {
        this.enabled = rateLimitEnabled && !devMode;
        this.rules = List.of(
                new Rule("signup", List.of("/api/onboard/create-school"),
                        signupPerHour, Duration.ofHours(1)),
                new Rule("login", List.of("/login", "/web/auth/login", "/api/mobile/auth/login"),
                        loginPer15Min, Duration.ofMinutes(15)));
        if (!this.enabled) {
            log.info("Rate limiting disabled (dev-mode={}, app.rate-limit.enabled={})", devMode, rateLimitEnabled);
        }
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if (!enabled || !"POST".equalsIgnoreCase(request.getMethod())) {
            chain.doFilter(request, response);
            return;
        }

        Rule rule = ruleFor(request.getServletPath());
        if (rule == null) {
            chain.doFilter(request, response);
            return;
        }

        String key = rule.name() + "|" + clientIp(request);
        Instant now = Instant.now();
        Window window = windows.compute(key, (k, existing) -> {
            if (existing == null || now.isAfter(existing.resetAt)) {
                return new Window(now.plus(rule.window()));
            }
            return existing;
        });

        if (window.count.incrementAndGet() > rule.limit()) {
            long retryAfter = Math.max(1, Duration.between(now, window.resetAt).getSeconds());
            log.warn("Rate limit exceeded: rule={} ip={} path={}", rule.name(), clientIp(request),
                    request.getServletPath());
            response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
            response.setHeader("Retry-After", String.valueOf(retryAfter));
            response.setContentType("application/json");
            response.getWriter().write(
                    "{\"error\":\"Too many requests. Please try again in " + retryAfter + " seconds.\"}");
            return;
        }

        evictExpired(now);
        chain.doFilter(request, response);
    }

    private Rule ruleFor(String path) {
        for (Rule rule : rules) {
            if (rule.matches(path)) {
                return rule;
            }
        }
        return null;
    }

    /**
     * Render terminates TLS at its own proxy, so {@code getRemoteAddr()} is the
     * proxy's address for every caller. The real client is in X-Forwarded-For.
     *
     * <p>Deliberately takes the <em>last</em> entry, not the first: a caller can
     * send their own X-Forwarded-For header and the proxy appends to it, so the
     * leading entries are attacker-controlled and only the trailing one was
     * written by infrastructure we trust. Taking the first entry — which is what
     * Spring's ForwardedHeaderFilter does — would let one attacker rotate a
     * fake header value per request and bypass this filter entirely.
     */
    private String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            String[] hops = forwarded.split(",");
            return hops[hops.length - 1].trim();
        }
        return request.getRemoteAddr();
    }

    /** Keeps the map from growing without bound under a spray of distinct IPs. */
    private void evictExpired(Instant now) {
        if (windows.size() > 10_000) {
            windows.entrySet().removeIf(e -> now.isAfter(e.getValue().resetAt));
        }
    }
}

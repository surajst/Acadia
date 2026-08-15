package com.concept.config;

import com.concept.tenant.Tenant;
import com.concept.tenant.TenantRepository;
import com.concept.user.CurrentUserService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Optional;
import java.util.UUID;

/**
 * Refuses every authenticated request belonging to a deactivated school.
 *
 * <p>{@code Tenant.isActive} existed for a long time and nothing read it, which
 * is worse than not having the field at all: it looks like an off switch, so a
 * deactivated school appears handled while its users keep logging in and working
 * normally. This filter makes the flag mean something.
 *
 * <p>It runs after Spring Security has populated the security context — a bean
 * filter at default precedence sits behind the FilterChainProxy — so it can read
 * the authenticated user and resolve their tenant. Unauthenticated traffic
 * (login page, public signup, health checks) never reaches the check, so a
 * school can still be created and nobody is locked out of the front door.
 *
 * <p>The tenant is loaded per request rather than cached. That is one indexed
 * primary-key read against a database that now sits in the same region, and the
 * alternative is a window in which a school that was just switched off keeps
 * serving. For a guardrail, being immediate is worth more than the read.
 *
 * <p>Deactivation is not the same as deletion, and this is deliberately only the
 * enforcement half. Real offboarding still needs an export and an authorisation
 * story before anything in production is allowed to flip this flag.
 */
@Component
public class TenantActiveFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(TenantActiveFilter.class);

    private final CurrentUserService currentUserService;
    private final TenantRepository tenantRepository;

    public TenantActiveFilter(CurrentUserService currentUserService, TenantRepository tenantRepository) {
        this.currentUserService = currentUserService;
        this.tenantRepository = tenantRepository;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || auth instanceof AnonymousAuthenticationToken) {
            chain.doFilter(request, response);
            return;
        }

        Optional<UUID> tenantId = currentUserService.getCurrentTenantId(auth);
        if (tenantId.isEmpty()) {
            // No tenant on the principal at all: not this filter's call to make.
            chain.doFilter(request, response);
            return;
        }

        boolean active = tenantRepository.findById(tenantId.get())
                .map(Tenant::isActive)
                // A missing tenant row means the school was deleted underneath a
                // live session. Fail closed: that is not a reason to serve.
                .orElse(false);

        if (active) {
            chain.doFilter(request, response);
            return;
        }

        log.warn("Blocked request for deactivated tenant {}: {} {}",
                tenantId.get(), request.getMethod(), request.getRequestURI());
        reject(request, response);
    }

    /**
     * API callers get a status they can act on; browser sessions are ended and
     * sent back to the login page, so a deactivated school does not sit inside a
     * half-working UI issuing requests that all fail.
     */
    private void reject(HttpServletRequest request, HttpServletResponse response) throws IOException {
        HttpSession session = request.getSession(false);
        if (session != null) {
            session.invalidate();
        }
        SecurityContextHolder.clearContext();

        String path = request.getRequestURI();
        if (path.startsWith("/api/")) {
            response.setStatus(HttpStatus.FORBIDDEN.value());
            response.setContentType("application/json");
            response.getWriter().write("{\"error\":\"This school is no longer active.\"}");
            return;
        }
        response.sendRedirect(request.getContextPath() + "/login?error=inactive");
    }
}

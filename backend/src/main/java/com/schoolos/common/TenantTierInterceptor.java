package com.schoolos.common;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.context.support.WebApplicationContextUtils;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.Optional;
import java.util.UUID;

/**
 * Blocks admin/fee/curriculum-management surfaces (/web/admin/**, /api/admin/**)
 * for tenants provisioned at the PARENT_APP_ONLY tier — schools that only bought
 * the standalone parent-engagement app, not the full SMS.
 *
 * Fails open: if the current user, their tenant, or the tenant's tier can't be
 * resolved, the request is allowed through. Only a tenant explicitly marked
 * PARENT_APP_ONLY is blocked, so existing FULL_SMS tenants (the default) and
 * ad-hoc/demo users are unaffected.
 */
public class TenantTierInterceptor implements HandlerInterceptor {

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        String path = request.getRequestURI();
        if (!(path.startsWith("/web/admin") || path.startsWith("/api/admin"))) {
            return true;
        }

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || "anonymousUser".equals(auth.getName())) {
            return true;
        }

        try {
            com.schoolos.user.UserRepository userRepository = WebApplicationContextUtils
                    .getRequiredWebApplicationContext(request.getServletContext())
                    .getBean(com.schoolos.user.UserRepository.class);
            com.schoolos.tenant.TenantRepository tenantRepository = WebApplicationContextUtils
                    .getRequiredWebApplicationContext(request.getServletContext())
                    .getBean(com.schoolos.tenant.TenantRepository.class);

            String username = auth.getName();
            Optional<com.schoolos.user.User> userOpt = userRepository.findByEmail(username);
            if (userOpt.isEmpty()) {
                userOpt = userRepository.findByEmail(username + "@greenwood.com");
            }
            if (userOpt.isEmpty()) {
                return true;
            }

            UUID tenantId = userOpt.get().getTenantId();
            if (tenantId == null) {
                return true;
            }

            com.schoolos.tenant.Tenant tenant = tenantRepository.findById(tenantId).orElse(null);
            if (tenant == null || tenant.getEffectiveTier() != com.schoolos.tenant.TenantTier.PARENT_APP_ONLY) {
                return true;
            }

            response.sendError(HttpServletResponse.SC_FORBIDDEN, "This feature is not available on your plan.");
            return false;
        } catch (Exception e) {
            // Resolution failure — fail open rather than lock out existing flows.
            return true;
        }
    }
}

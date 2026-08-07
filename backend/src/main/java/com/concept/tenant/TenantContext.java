package com.concept.tenant;

import com.concept.user.CurrentUserService;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;

/**
 * Single source of truth for "which tenant is this request acting as".
 *
 * <p>The application layer reads the tenant id from here and passes it down to
 * tenant-scoped repositories; controllers should not re-derive it and business
 * services should not accept a caller-supplied tenant id. Resolving it in one
 * place is what lets tenant isolation be enforced structurally (see ADR 0001).
 *
 * <p>Reads the current {@link Authentication} from the security context, so it
 * works for both session (web) and JWT (mobile) requests without the caller
 * having to thread {@code Authentication} through every method.
 */
@Component
public class TenantContext {

    private final CurrentUserService currentUserService;

    public TenantContext(CurrentUserService currentUserService) {
        this.currentUserService = currentUserService;
    }

    /** The acting tenant, or empty when the request is unauthenticated. */
    public Optional<UUID> getTenantId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            return Optional.empty();
        }
        return currentUserService.getCurrentTenantId(auth);
    }

    /**
     * The acting tenant, or an exception when there is none. Use in application
     * code that must never run without a tenant — a missing tenant here is a
     * programming/auth error, not a user-facing condition.
     */
    public UUID requireTenantId() {
        return getTenantId().orElseThrow(
                () -> new IllegalStateException("No tenant in the current security context"));
    }
}

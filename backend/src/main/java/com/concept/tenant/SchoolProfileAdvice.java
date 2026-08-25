package com.concept.tenant;

import com.concept.user.CurrentUserService;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

import java.util.UUID;

/**
 * Puts the school's own vocabulary and module switches on every rendered page.
 *
 * <p>A ControllerAdvice rather than a parameter threaded through each
 * controller: the alternative is touching every handler that renders a
 * template, and any one that got missed would show "Class" on a page where the
 * rest of the app says "Level". Half-applied vocabulary reads as a bug, so the
 * mechanism has to cover everything by default.
 *
 * <p>Templates use it as {@code ${school.levelSingular}} and
 * {@code ${school.hasSyllabus}}. It is never null -- a request with no
 * resolvable tenant gets the conventional-school defaults, so a page renders
 * sensible words rather than blanks.
 */
@ControllerAdvice
public class SchoolProfileAdvice {

    private final TenantRepository tenantRepository;
    private final CurrentUserService currentUserService;

    public SchoolProfileAdvice(TenantRepository tenantRepository, CurrentUserService currentUserService) {
        this.tenantRepository = tenantRepository;
        this.currentUserService = currentUserService;
    }

    @ModelAttribute("school")
    public SchoolType schoolType(Authentication authentication) {
        if (authentication == null) {
            return SchoolType.SECONDARY;
        }
        UUID tenantId = currentUserService.getCurrentUser(authentication)
                .map(user -> user.getTenantId())
                .orElse(null);
        if (tenantId == null) {
            return SchoolType.SECONDARY;
        }
        return tenantRepository.findById(tenantId)
                .map(Tenant::getSchoolType)
                .orElse(SchoolType.SECONDARY);
    }
}

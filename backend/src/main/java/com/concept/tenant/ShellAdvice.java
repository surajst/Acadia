package com.concept.tenant;

import com.concept.user.CurrentUserService;
import com.concept.user.User;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

import java.util.UUID;

/**
 * What the app shell needs on every page: whose school this is, who is signed
 * in, and where they are.
 *
 * <p>A ControllerAdvice for the same reason as {@link SchoolProfileAdvice} --
 * the sidebar renders on every template, and a handler that forgot to supply
 * the school name would render a page with a blank brand block. Covering
 * everything by default is the only version of this that stays correct.
 *
 * <p>Never null. A request with no resolvable user still renders a usable
 * shell rather than a page of empty strings.
 */
@ControllerAdvice
public class ShellAdvice {

    private final TenantRepository tenantRepository;
    private final CurrentUserService currentUserService;

    public ShellAdvice(TenantRepository tenantRepository, CurrentUserService currentUserService) {
        this.tenantRepository = tenantRepository;
        this.currentUserService = currentUserService;
    }

    /**
     * @param initials at most two letters, for the sidebar avatar tile
     * @param path     the current request path, so a nav item can mark itself active
     *                 server-side rather than by matching strings in JavaScript
     */
    public record Shell(String schoolName, String userName, String initials, String role, String path) {

        /**
         * True when the given nav href is the section being viewed.
         *
         * <p>Prefix match, not equality: {@code /web/admin/fees/collections} has
         * to light up "Fees & Billing" too, or the sidebar tells you that you
         * are nowhere the moment you open a sub-page.
         */
        public boolean on(String href) {
            return href != null && !href.isBlank() && path != null && path.startsWith(href);
        }
    }

    @ModelAttribute("shell")
    public Shell shell(org.springframework.security.core.Authentication authentication,
                       HttpServletRequest request) {
        String path = request != null ? request.getRequestURI() : "";
        User user = authentication == null
                ? null
                : currentUserService.getCurrentUser(authentication).orElse(null);

        if (user == null) {
            return new Shell("", "", "", "", path);
        }

        UUID tenantId = user.getTenantId();
        String schoolName = tenantId == null ? "" : tenantRepository.findById(tenantId)
                .map(Tenant::getName)
                .orElse("");

        String name = user.getFullName() == null ? "" : user.getFullName().trim();
        String role = user.getRole() == null ? "" : user.getRole().name();
        return new Shell(schoolName, name, initialsOf(name), role, path);
    }

    /** First letters of the first and last words -- "Priya Nair" becomes "PN". */
    private static String initialsOf(String name) {
        if (name == null || name.isBlank()) {
            return "";
        }
        String[] parts = name.trim().split("\\s+");
        String first = parts[0].substring(0, 1);
        if (parts.length == 1) {
            return first.toUpperCase();
        }
        String last = parts[parts.length - 1].substring(0, 1);
        return (first + last).toUpperCase();
    }
}

package com.concept.config;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * What the API answers when nobody is signed in: 401, never 403.
 *
 * <p>Without this, the {@code /api/**} chain had no entry point at all, so Spring
 * Security fell back to {@link org.springframework.security.web.authentication.Http403ForbiddenEntryPoint}
 * and an expired token came back as 403 -- the same status as a signed-in parent
 * reaching a teacher's endpoint. Those are different problems with different
 * fixes, and a client cannot tell them apart from one status code.
 *
 * <p>The cost of conflating them was not abstract. A parent's 24-hour token
 * expired overnight; the app read the refusals as "no data" and rendered "No fees
 * raised yet. The school has not billed anything for Your child" over 450 rupees
 * outstanding, and a home screen reading "Your child, Level 1, School 0". The app
 * had no way to know it needed to sign in again, because nothing in the response
 * said so.
 *
 * <p>So the body carries a {@code reason} as well: {@code expired} when the token
 * was valid once, {@code invalid} when it never was, {@code missing} when none was
 * sent. The app clears a stored session on {@code expired} and {@code invalid} and
 * sends the person to sign in; it should not do that merely because a request
 * failed.
 *
 * @see JwtErrorAttribute for where the reason is recorded
 */
@Component
public class ApiAuthenticationEntryPoint implements AuthenticationEntryPoint {

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
                         AuthenticationException authException) throws IOException {
        String reason = JwtErrorAttribute.reasonFrom(request);

        response.setStatus(HttpStatus.UNAUTHORIZED.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        // Written by hand rather than through Jackson: this runs in the filter
        // chain, outside any @ControllerAdvice, and an ObjectMapper failure here
        // would replace a clear 401 with a 500.
        response.getWriter().write(
                "{\"error\":\"" + messageFor(reason) + "\",\"reason\":\"" + reason + "\"}");
    }

    /**
     * Wording a person could be shown, not just a client parsed. The app decides
     * what to display, but a bare "Unauthorized" leaves it nothing to fall back
     * on.
     */
    private static String messageFor(String reason) {
        return switch (reason) {
            case JwtErrorAttribute.EXPIRED -> "Your session has expired. Please sign in again.";
            case JwtErrorAttribute.INVALID -> "That sign-in is no longer valid. Please sign in again.";
            default -> "Please sign in to continue.";
        };
    }
}

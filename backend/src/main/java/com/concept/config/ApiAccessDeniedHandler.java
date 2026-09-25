package com.concept.config;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * What the API answers when somebody <em>is</em> signed in and still may not do
 * this: 403, with a body shaped like the 401's so a client can read both the same
 * way.
 *
 * <p>Declared explicitly alongside {@link ApiAuthenticationEntryPoint} because the
 * whole point of that change is that these two cases stop sharing a status code.
 * Leaving this to the default would keep the 403 correct by accident while the
 * 401 was deliberate, and the next person reading the chain could not tell that
 * the pairing was intended.
 *
 * <p>The {@code reason} is {@code forbidden} and never {@code expired}: a client
 * must not clear a working session because a parent tapped through to a teacher's
 * screen.
 */
@Component
public class ApiAccessDeniedHandler implements AccessDeniedHandler {

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response,
                       AccessDeniedException accessDeniedException) throws IOException {
        response.setStatus(HttpStatus.FORBIDDEN.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write(
                "{\"error\":\"You do not have access to this.\",\"reason\":\"forbidden\"}");
    }
}

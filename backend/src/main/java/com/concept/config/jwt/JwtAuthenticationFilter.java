package com.concept.config.jwt;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtUtils jwtUtils;
    private final UserDetailsService userDetailsService;

    public JwtAuthenticationFilter(JwtUtils jwtUtils, UserDetailsService userDetailsService) {
        this.jwtUtils = jwtUtils;
        this.userDetailsService = userDetailsService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        final String authHeader = request.getHeader("Authorization");
        final String jwt;
        final String username;

        // Skip filter if no Authorization header or doesn't start with Bearer
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            com.concept.config.JwtErrorAttribute.record(
                    request, com.concept.config.JwtErrorAttribute.MISSING);
            filterChain.doFilter(request, response);
            return;
        }

        jwt = authHeader.substring(7);
        try {
            username = jwtUtils.extractUsername(jwt);
            
            if (username != null && SecurityContextHolder.getContext().getAuthentication() == null) {
                UserDetails userDetails = this.userDetailsService.loadUserByUsername(username);

                if (!jwtUtils.validateToken(jwt, userDetails)) {
                    // Parsed, but does not belong to this user. No exception is
                    // thrown for that, so record it here or the entry point would
                    // report it as though no token had been sent.
                    com.concept.config.JwtErrorAttribute.record(
                            request, com.concept.config.JwtErrorAttribute.INVALID);
                }
                if (jwtUtils.validateToken(jwt, userDetails)) {
                    UsernamePasswordAuthenticationToken authToken = new UsernamePasswordAuthenticationToken(
                            userDetails,
                            null,
                            userDetails.getAuthorities()
                    );
                    authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                    SecurityContextHolder.getContext().setAuthentication(authToken);
                }
            }
        } catch (io.jsonwebtoken.ExpiredJwtException e) {
            // The ordinary case, not an error: tokens last 24 hours and a parent
            // opening the app the next morning has one that has run out. Logged at
            // debug because at error level this was noise that buried real faults.
            //
            // Recorded so ApiAuthenticationEntryPoint can answer 401 with
            // reason=expired -- which is what lets the app clear the stored login
            // and send the person to sign in, rather than reading the refusal as
            // "this family has no data" and rendering an empty state over money
            // they owe.
            com.concept.config.JwtErrorAttribute.record(
                    request, com.concept.config.JwtErrorAttribute.EXPIRED);
            logger.debug("Expired JWT presented: " + e.getMessage());
        } catch (Exception e) {
            // Never valid: wrong signature, malformed, or a user that no longer
            // exists. Worth a warning, unlike an expiry.
            com.concept.config.JwtErrorAttribute.record(
                    request, com.concept.config.JwtErrorAttribute.INVALID);
            logger.warn("Rejected JWT: " + e.getMessage());
        }

        filterChain.doFilter(request, response);
    }
}

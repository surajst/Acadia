package com.concept.config;

import io.sentry.Sentry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/**
 * App-wide error handling.
 *
 * <p>History: this used to catch every {@link Exception} and return the "error"
 * view as a String — which Spring renders with HTTP 200. That silently rewrote
 * the status of every failure (deliberate 403/404 {@link ResponseStatusException}s
 * included) to 200, hiding real errors from clients, monitoring, and tests.
 *
 * <p>Now: deliberate status exceptions keep their status, and only genuinely
 * unexpected exceptions fall through to the friendly page — with a real 500.
 */
@ControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /** Honour the intended status (e.g. the /test/reset 403 guard) instead of masking it as 200. */
    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<String> handleStatusException(ResponseStatusException ex) {
        return ResponseEntity.status(ex.getStatusCode())
                .body(ex.getReason() != null ? ex.getReason() : ex.getMessage());
    }

    /**
     * An unknown URL is a 404, not a server error.
     *
     * <p>Spring raises {@link NoResourceFoundException} when no handler and no
     * static resource match. The catch-all below was swallowing it and
     * reporting 500, so every typo and every bot probing for /wp-admin looked
     * like the application had crashed. Left alone this would also be the
     * single largest source of noise in Sentry, drowning out real errors.
     */
    @ExceptionHandler(NoResourceFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public String handleNotFound(NoResourceFoundException ex, Model model) {
        model.addAttribute("errorMessage", "Page not found");
        return "error";
    }

    /**
     * Truly unexpected failures render the friendly page but with a real 500, not a 200.
     *
     * <p>The stack trace is logged here on purpose. Handling an exception with
     * {@code @ExceptionHandler} counts as "resolved", so nothing else in Spring
     * logs it at INFO — before this, an unexpected 500 in production left no
     * trace anywhere and was undebuggable from the logs alone.
     *
     * <p>The Sentry capture is explicit for the same reason. Sentry's automatic
     * reporting only sees exceptions that escape the dispatcher, and this
     * handler catches every one of them first — verified against a running app
     * pointed at a local collector: without this call, nothing was sent.
     */
    /**
     * A refused permission is not a server fault.
     *
     * <p>Without this, the catch-all below swallowed AccessDeniedException and
     * rendered a 500 -- so every method-level {@code @PreAuthorize} denial
     * looked like a crash, logged a stack trace, and was reported to Sentry as
     * a defect. Rethrowing lets Spring Security's ExceptionTranslationFilter do
     * its job: 403 for a signed-in user, a redirect to the login page for an
     * anonymous one.
     */
    @ExceptionHandler(AccessDeniedException.class)
    public void handleAccessDenied(AccessDeniedException ex) {
        throw ex;
    }

    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public String handleGlobalException(Exception ex, Model model) {
        log.error("Unhandled exception rendering error page", ex);
        Sentry.captureException(ex);
        model.addAttribute("errorMessage", ex.getMessage());
        return "error";
    }
}

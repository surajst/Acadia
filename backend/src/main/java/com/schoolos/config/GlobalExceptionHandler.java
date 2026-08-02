package com.schoolos.config;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.server.ResponseStatusException;

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

    /** Honour the intended status (e.g. the /test/reset 403 guard) instead of masking it as 200. */
    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<String> handleStatusException(ResponseStatusException ex) {
        return ResponseEntity.status(ex.getStatusCode())
                .body(ex.getReason() != null ? ex.getReason() : ex.getMessage());
    }

    /** Truly unexpected failures render the friendly page but with a real 500, not a 200. */
    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public String handleGlobalException(Exception ex, Model model) {
        model.addAttribute("errorMessage", ex.getMessage());
        return "error";
    }
}

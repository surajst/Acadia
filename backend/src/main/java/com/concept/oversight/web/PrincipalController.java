package com.concept.oversight.web;

import com.concept.oversight.app.OversightException;
import com.concept.oversight.app.OversightService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.UUID;

/**
 * Interface layer for the PRINCIPAL/ADMIN oversight + approval surface. Thin
 * binding over {@link OversightService}; tenant resolution, ownership checks,
 * and the audit trail live in the service (ADR 0001).
 */
@RestController
@RequestMapping("/api/principal")
@PreAuthorize("hasAnyRole('ADMIN', 'PRINCIPAL')")
public class PrincipalController {

    private final OversightService oversightService;

    public PrincipalController(OversightService oversightService) {
        this.oversightService = oversightService;
    }

    @GetMapping("/progress/school")
    public ResponseEntity<?> schoolProgress(Authentication authentication) {
        return ResponseEntity.ok(oversightService.schoolProgress(authentication));
    }

    @GetMapping("/progress/class")
    public ResponseEntity<?> classProgress(@RequestParam int standard, Authentication authentication) {
        return ResponseEntity.ok(oversightService.classProgress(standard, authentication));
    }

    @GetMapping("/fee-summary")
    public ResponseEntity<?> feeSummary(Authentication authentication) {
        return ResponseEntity.ok(oversightService.feeSummary(authentication));
    }

    @GetMapping("/attendance-summary")
    public ResponseEntity<?> attendanceSummary() {
        return ResponseEntity.ok(oversightService.attendanceSummary());
    }

    @GetMapping("/fees/waivers/pending")
    public ResponseEntity<?> pendingWaivers(Authentication authentication) {
        return ResponseEntity.ok(oversightService.pendingWaivers(authentication));
    }

    @PostMapping("/fees/{invoiceId}/waiver/approve")
    public ResponseEntity<?> approveWaiver(@PathVariable UUID invoiceId, Authentication authentication) {
        return ResponseEntity.ok(oversightService.approveWaiver(invoiceId, authentication));
    }

    @PostMapping("/fees/{invoiceId}/waiver/reject")
    public ResponseEntity<?> rejectWaiver(@PathVariable UUID invoiceId, Authentication authentication) {
        return ResponseEntity.ok(oversightService.rejectWaiver(invoiceId, authentication));
    }

    @GetMapping("/staff/pending")
    public ResponseEntity<?> pendingStaff(Authentication authentication) {
        return ResponseEntity.ok(oversightService.pendingStaff(authentication));
    }

    @PostMapping("/staff/{userId}/approve")
    public ResponseEntity<?> approveStaff(@PathVariable UUID userId, Authentication authentication) {
        return ResponseEntity.ok(oversightService.decideStaff(userId, true, authentication));
    }

    @PostMapping("/staff/{userId}/reject")
    public ResponseEntity<?> rejectStaff(@PathVariable UUID userId, Authentication authentication) {
        return ResponseEntity.ok(oversightService.decideStaff(userId, false, authentication));
    }

    @ExceptionHandler(OversightException.class)
    public ResponseEntity<?> handle(OversightException e) {
        return ResponseEntity.status(e.status()).body(Map.of("error", e.getMessage()));
    }
}

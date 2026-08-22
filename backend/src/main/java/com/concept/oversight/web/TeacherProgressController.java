package com.concept.oversight.web;

import com.concept.oversight.app.OversightException;
import com.concept.oversight.app.OversightService;
import com.concept.tenant.TenantContext;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.UUID;

/**
 * Interface layer for teacher curriculum-progress approvals (with XP award +
 * notification). Thin binding over {@link OversightService} (ADR 0001).
 */
@RestController
@RequestMapping("/api/teacher/progress")
public class TeacherProgressController {

    private final OversightService oversightService;
    private final TenantContext tenantContext;

    public TeacherProgressController(OversightService oversightService, TenantContext tenantContext) {
        this.oversightService = oversightService;
        this.tenantContext = tenantContext;
    }

    @PostMapping("/approve")
    @PreAuthorize("hasAnyRole('TEACHER', 'ADMIN', 'PRINCIPAL')")
    public ResponseEntity<?> approve(@RequestParam("studentProgressId") UUID studentProgressId) {
        return ResponseEntity.ok(oversightService.approveProgress(studentProgressId, tenantId()));
    }

    @PostMapping("/reject")
    @PreAuthorize("hasAnyRole('TEACHER', 'ADMIN', 'PRINCIPAL')")
    public ResponseEntity<?> reject(@RequestParam("studentProgressId") UUID studentProgressId,
                                    @RequestParam(value = "reason", required = false) String reason) {
        return ResponseEntity.ok(oversightService.rejectProgress(studentProgressId, reason, tenantId()));
    }

    @ExceptionHandler(OversightException.class)
    public ResponseEntity<?> handle(OversightException e) {
        return ResponseEntity.status(e.status()).body(Map.of("error", e.getMessage()));
    }

    private UUID tenantId() {
        return tenantContext.getTenantId().orElse(null);
    }
}

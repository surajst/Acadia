package com.concept.oversight.web;

import com.concept.oversight.app.OversightService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Interface layer for the ADMIN progress view. Thin binding over
 * {@link OversightService} (ADR 0001).
 */
@RestController
@RequestMapping("/api/admin/progress")
@PreAuthorize("hasRole('ADMIN')")
public class AdminProgressController {

    private final OversightService oversightService;

    public AdminProgressController(OversightService oversightService) {
        this.oversightService = oversightService;
    }

    @GetMapping("/school")
    public ResponseEntity<?> school(Authentication authentication) {
        return ResponseEntity.ok(oversightService.schoolProgress(authentication));
    }

    @GetMapping("/class")
    public ResponseEntity<?> classProgress(@RequestParam("standard") int standard, Authentication authentication) {
        return ResponseEntity.ok(oversightService.classProgress(standard, authentication));
    }
}

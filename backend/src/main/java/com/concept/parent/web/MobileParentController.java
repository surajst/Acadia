package com.concept.parent.web;

import com.concept.parent.app.ParentException;
import com.concept.parent.app.ParentService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.UUID;

/**
 * Interface layer for the mobile parent app. Binds requests and delegates to
 * {@link ParentService}; all parent resolution, child-ownership checks, and
 * XP/translation logic live in the application layer (ADR 0001).
 */
@RestController
@RequestMapping("/api/mobile/parent")
public class MobileParentController {

    private final ParentService parentService;

    public MobileParentController(ParentService parentService) {
        this.parentService = parentService;
    }

    @GetMapping("/dashboard")
    public ResponseEntity<?> dashboard(@RequestParam(value = "studentId", required = false) UUID studentId,
                                       Authentication authentication) {
        return ResponseEntity.ok(parentService.mobileDashboard(studentId, authentication));
    }

    @GetMapping("/subject-performance")
    public ResponseEntity<?> subjectPerformance(@RequestParam(value = "studentId", required = false) UUID studentId,
                                                Authentication authentication) {
        return ResponseEntity.ok(parentService.subjectPerformance(studentId, authentication));
    }

    @GetMapping("/attendance")
    public ResponseEntity<?> attendance(@RequestParam(value = "studentId", required = false) UUID studentId,
                                        Authentication authentication) {
        return ResponseEntity.ok(parentService.attendanceLog(studentId, authentication));
    }

    @GetMapping("/bus-location")
    public ResponseEntity<?> busLocation(@RequestParam(value = "studentId", required = false) UUID studentId,
                                         Authentication authentication) {
        return ResponseEntity.ok(parentService.busLocation(studentId, authentication));
    }

    @GetMapping("/announcements")
    public ResponseEntity<?> announcements(Authentication authentication) {
        return ResponseEntity.ok(parentService.announcements(authentication));
    }

    @GetMapping("/announcements/{id}/localized")
    public ResponseEntity<?> announcementLocalized(@PathVariable UUID id, @RequestParam String lang,
                                                   Authentication authentication) {
        return ResponseEntity.ok(parentService.announcementLocalized(id, lang, authentication));
    }

    @GetMapping("/announcements/{id}/speech")
    public ResponseEntity<?> announcementSpeech(@PathVariable UUID id, @RequestParam String lang,
                                                Authentication authentication) {
        return ResponseEntity.ok(parentService.announcementSpeech(id, lang, authentication));
    }

    @PutMapping("/language")
    public ResponseEntity<?> setLanguage(@RequestBody Map<String, String> body, Authentication authentication) {
        return ResponseEntity.ok(parentService.setPreferredLanguage(body.get("language"), authentication));
    }

    /** Ask the school to reduce one instalment. A principal decides. */
    @PostMapping("/fees/{invoiceId}/waiver-request")
    public ResponseEntity<?> requestWaiver(@PathVariable("invoiceId") UUID invoiceId,
                                           @RequestBody Map<String, Object> body,
                                           Authentication authentication) {
        java.math.BigDecimal amount = body.get("amount") == null
                ? null : new java.math.BigDecimal(String.valueOf(body.get("amount")));
        String reason = body.get("reason") == null ? null : String.valueOf(body.get("reason"));
        return ResponseEntity.ok(parentService.requestWaiver(invoiceId, amount, reason, authentication));
    }

    @ExceptionHandler(ParentException.class)
    public ResponseEntity<?> handle(ParentException e) {
        return ResponseEntity.status(e.status()).body(Map.of("error", e.getMessage()));
    }
}

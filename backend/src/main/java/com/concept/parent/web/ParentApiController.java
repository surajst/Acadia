package com.concept.parent.web;

import com.concept.parent.app.AssignQuestRequest;
import com.concept.parent.app.ParentException;
import com.concept.parent.app.ParentService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.UUID;

/**
 * Interface layer for the parent JSON API. Thin binding over
 * {@link ParentService}; every child-ownership check lives in the service
 * (ADR 0001).
 */
@RestController
@RequestMapping("/api/parent")
public class ParentApiController {

    private final ParentService parentService;

    public ParentApiController(ParentService parentService) {
        this.parentService = parentService;
    }

    @PostMapping("/approve-quest/{id}")
    @PreAuthorize("hasRole('PARENT')")
    public ResponseEntity<?> approveQuest(@PathVariable UUID id, Authentication authentication) {
        return ResponseEntity.ok(parentService.approveQuestApi(id, authentication));
    }

    @PostMapping("/assign-quest")
    @PreAuthorize("hasRole('PARENT')")
    public ResponseEntity<?> assignQuest(@RequestBody AssignQuestRequest dto, Authentication authentication) {
        return ResponseEntity.ok(parentService.assignQuestApi(dto, authentication));
    }

    @GetMapping("/child-attendance")
    @PreAuthorize("hasRole('PARENT')")
    public ResponseEntity<?> childAttendance(Authentication authentication) {
        return ResponseEntity.ok(parentService.childAttendance(authentication));
    }

    @GetMapping("/child-syllabus")
    @PreAuthorize("hasRole('PARENT')")
    public ResponseEntity<?> childSyllabus(Authentication authentication) {
        return ResponseEntity.ok(parentService.childSyllabus(authentication));
    }

    @GetMapping("/child-progress")
    @PreAuthorize("hasRole('PARENT')")
    public ResponseEntity<?> childProgress(@RequestParam("studentId") UUID studentId, Authentication authentication) {
        return ResponseEntity.ok(parentService.childProgress(studentId, authentication));
    }

    @ExceptionHandler(ParentException.class)
    public ResponseEntity<?> handle(ParentException e) {
        return ResponseEntity.status(e.status()).body(Map.of("error", e.getMessage()));
    }
}

package com.concept.timetable.web;

import com.concept.timetable.app.TimetableEntryRequest;
import com.concept.timetable.app.TimetableException;
import com.concept.timetable.app.TimetableService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.UUID;

/**
 * Interface layer for admin timetable CRUD. Thin binding over
 * {@link TimetableService}; validation and audit live in the service (ADR 0001).
 */
@RestController
@RequestMapping("/api/admin/timetable")
@PreAuthorize("hasRole('ADMIN')")
public class AdminTimetableController {

    private final TimetableService timetableService;

    public AdminTimetableController(TimetableService timetableService) {
        this.timetableService = timetableService;
    }

    @GetMapping
    public ResponseEntity<?> list(@RequestParam(required = false) UUID classSectionId, Authentication authentication) {
        return ResponseEntity.ok(timetableService.adminList(classSectionId, authentication));
    }

    @PostMapping
    public ResponseEntity<?> create(@RequestBody TimetableEntryRequest request, Authentication authentication) {
        return ResponseEntity.ok(timetableService.adminCreate(request, authentication));
    }

    @PutMapping("/{id}")
    public ResponseEntity<?> update(@PathVariable UUID id, @RequestBody TimetableEntryRequest request,
                                    Authentication authentication) {
        return ResponseEntity.ok(timetableService.adminUpdate(id, request, authentication));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> delete(@PathVariable UUID id, Authentication authentication) {
        return ResponseEntity.ok(timetableService.adminDelete(id, authentication));
    }

    @ExceptionHandler(TimetableException.class)
    public ResponseEntity<?> handle(TimetableException e) {
        return ResponseEntity.status(e.status()).body(Map.of("error", e.getMessage()));
    }
}

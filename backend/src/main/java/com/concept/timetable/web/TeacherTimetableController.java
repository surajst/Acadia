package com.concept.timetable.web;

import com.concept.timetable.app.TimetableException;
import com.concept.timetable.app.TimetableService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Interface layer for the teacher's timetable views + the dev-only seed. Thin
 * binding over {@link TimetableService} (ADR 0001). The read endpoints preserve
 * the prior "500 on unexpected error" contract; the seed guard maps through
 * {@link TimetableException}.
 */
@RestController
@RequestMapping("/api/teacher")
public class TeacherTimetableController {

    private final TimetableService timetableService;

    public TeacherTimetableController(TimetableService timetableService) {
        this.timetableService = timetableService;
    }

    @GetMapping("/timetable/today")
    @PreAuthorize("hasRole('TEACHER')")
    public ResponseEntity<?> today(Authentication authentication) {
        try {
            return ResponseEntity.ok(timetableService.todayTimetable(authentication));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(null);
        }
    }

    @GetMapping("/timetable/week")
    @PreAuthorize("hasRole('TEACHER')")
    public ResponseEntity<?> week(Authentication authentication) {
        try {
            return ResponseEntity.ok(timetableService.weekTimetable(authentication));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(null);
        }
    }

    @PostMapping("/timetable/seed")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> seed() {
        try {
            return ResponseEntity.ok(timetableService.seedTimetable());
        } catch (TimetableException e) {
            return ResponseEntity.status(e.status()).body(java.util.Map.of("error", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(java.util.Map.of("status", "error", "message", e.getMessage()));
        }
    }
}

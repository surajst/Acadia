package com.concept.video.web;

import com.concept.video.app.LearningVideoService;
import com.concept.video.app.VideoException;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

/**
 * Learning videos, for all three audiences.
 *
 * <p>Thin over {@link LearningVideoService} (ADR 0001). Every rule -- who may post
 * to a section, who may see a list, who may remove -- is decided in the service,
 * because a rule the controller keeps is a rule the next controller forgets.
 *
 * <p>@PreAuthorize is a coarse first gate only. SecurityConfig's URL rules are
 * evaluated before method annotations, so these paths sit under the API chain's
 * authenticated-by-default rule and the annotations narrow from there.
 */
@RestController
public class LearningVideoApiController {

    private final LearningVideoService videoService;

    public LearningVideoApiController(LearningVideoService videoService) {
        this.videoService = videoService;
    }

    public static class PostVideoRequest {
        public String url;
        public UUID sectionId;
        public String subjectCode;
        public String note;
    }

    // ── Teacher and admin ────────────────────────────────────────────────────

    @PostMapping("/api/teacher/videos")
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN','PRINCIPAL')")
    public ResponseEntity<?> post(@RequestBody PostVideoRequest body, Authentication authentication) {
        return ResponseEntity.ok(videoService.post(
                body.url, body.sectionId, body.subjectCode, body.note, authentication));
    }

    @GetMapping("/api/teacher/videos")
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN','PRINCIPAL')")
    public ResponseEntity<?> mine(Authentication authentication) {
        return ResponseEntity.ok(videoService.mine(authentication));
    }

    @DeleteMapping("/api/teacher/videos/{videoId}")
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN','PRINCIPAL')")
    public ResponseEntity<?> remove(@PathVariable UUID videoId, Authentication authentication) {
        return ResponseEntity.ok(videoService.remove(videoId, authentication));
    }

    // ── Pupil ────────────────────────────────────────────────────────────────

    @GetMapping("/api/mobile/student/videos")
    @PreAuthorize("hasRole('STUDENT')")
    public ResponseEntity<?> forStudent(@RequestParam(value = "subject", required = false) String subject,
                                        Authentication authentication) {
        return ResponseEntity.ok(videoService.forStudent(subject, authentication));
    }

    @PostMapping("/api/mobile/student/videos/{videoId}/watched")
    @PreAuthorize("hasRole('STUDENT')")
    public ResponseEntity<?> watched(@PathVariable UUID videoId, Authentication authentication) {
        return ResponseEntity.ok(videoService.markWatched(videoId, authentication));
    }

    // ── Parent ───────────────────────────────────────────────────────────────

    @GetMapping("/api/mobile/parent/videos")
    @PreAuthorize("hasRole('PARENT')")
    public ResponseEntity<?> forParent(@RequestParam(value = "studentId", required = false) UUID studentId,
                                       @RequestParam(value = "subject", required = false) String subject,
                                       Authentication authentication) {
        return ResponseEntity.ok(videoService.forParent(studentId, subject, authentication));
    }

    @ExceptionHandler(VideoException.class)
    public ResponseEntity<?> handle(VideoException e) {
        return ResponseEntity.status(e.status()).body(Map.of("error", e.getMessage()));
    }
}

package com.concept.gallery.web;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import jakarta.servlet.http.HttpServletRequest;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.concept.gallery.app.GalleryException;
import com.concept.gallery.app.GalleryService;
import com.concept.gallery.app.GalleryViews.CreateAlbum;
import com.concept.media.ImagePipeline;

/**
 * Photo albums, for staff, pupils and parents, from the web portal and the app.
 *
 * <p>Thin over {@link GalleryService} (ADR 0001): who may see, add or remove is
 * decided there. Paths sit under the API chain's authenticated-by-default rule,
 * which accepts both a portal session and an app token.
 */
@RestController
public class GalleryApiController {

    private final GalleryService gallery;

    public GalleryApiController(GalleryService gallery) {
        this.gallery = gallery;
    }

    public static class TagRequest {
        public List<UUID> studentIds;
    }

    @GetMapping("/api/gallery/status")
    public ResponseEntity<?> status() {
        return ResponseEntity.ok(gallery.status());
    }

    @GetMapping("/api/gallery/albums")
    public ResponseEntity<?> albums(Authentication authentication) {
        return ResponseEntity.ok(gallery.albumsFor(authentication));
    }

    @PostMapping("/api/gallery/albums")
    public ResponseEntity<?> create(@RequestBody CreateAlbum request, Authentication authentication) {
        return ResponseEntity.ok(gallery.createAlbum(request, authentication));
    }

    @GetMapping("/api/gallery/audiences")
    public ResponseEntity<?> audiences(Authentication authentication) {
        return ResponseEntity.ok(gallery.audiencesFor(authentication));
    }

    /** Pupils in an audience with no photo consent, shown before an album is created. */
    @GetMapping("/api/gallery/consent-check")
    public ResponseEntity<?> consentCheck(@RequestParam("audienceType") String audienceType,
                                          @RequestParam(value = "audienceRef", required = false) String audienceRef,
                                          Authentication authentication) {
        return ResponseEntity.ok(gallery.pupilsWithoutConsent(audienceType, audienceRef, authentication));
    }

    @GetMapping("/api/gallery/albums/{albumId}/photos")
    public ResponseEntity<?> photos(@PathVariable UUID albumId, Authentication authentication) {
        return ResponseEntity.ok(gallery.photosIn(albumId, authentication));
    }

    /**
     * One photo, as the raw request body (Content-Type image/jpeg or image/png).
     *
     * <p>Not multipart: the app-wide multipart limit is 2MB and stays that way
     * for bulk import, while a phone photo is often larger. The body is read
     * here with the pipeline's own cap, and a body over it is refused without
     * being read to the end.
     */
    @PostMapping("/api/gallery/albums/{albumId}/photos")
    public ResponseEntity<?> upload(@PathVariable UUID albumId, HttpServletRequest request,
                                    Authentication authentication) throws IOException {
        return ResponseEntity.ok(gallery.addPhoto(albumId, readCapped(request), authentication));
    }

    @DeleteMapping("/api/gallery/albums/{albumId}")
    public ResponseEntity<?> removeAlbum(@PathVariable UUID albumId, Authentication authentication) {
        gallery.removeAlbum(albumId, authentication);
        return ResponseEntity.ok(Map.of("status", "removed"));
    }

    @DeleteMapping("/api/gallery/photos/{photoId}")
    public ResponseEntity<?> removePhoto(@PathVariable UUID photoId, Authentication authentication) {
        gallery.removePhoto(photoId, authentication);
        return ResponseEntity.ok(Map.of("status", "removed"));
    }

    @GetMapping("/api/gallery/albums/{albumId}/pupils")
    public ResponseEntity<?> taggable(@PathVariable UUID albumId, Authentication authentication) {
        return ResponseEntity.ok(gallery.taggablePupils(albumId, authentication));
    }

    @PostMapping("/api/gallery/photos/{photoId}/pupils")
    public ResponseEntity<?> tag(@PathVariable UUID photoId, @RequestBody TagRequest body,
                                 Authentication authentication) {
        gallery.tagPupils(photoId, body == null ? List.of() : body.studentIds, authentication);
        return ResponseEntity.ok(Map.of("status", "tagged"));
    }

    @GetMapping("/api/gallery/pupils/{studentId}/photos")
    public ResponseEntity<?> photosOfPupil(@PathVariable UUID studentId, Authentication authentication) {
        return ResponseEntity.ok(gallery.photosOfPupil(studentId, authentication));
    }

    @ExceptionHandler(GalleryException.class)
    public ResponseEntity<?> handle(GalleryException e) {
        return ResponseEntity.status(e.status()).body(Map.of("error", e.getMessage()));
    }

    /** Reads at most one byte past the cap, so an oversized body is refused, not buffered. */
    private static byte[] readCapped(HttpServletRequest request) throws IOException {
        if (request.getContentLengthLong() > ImagePipeline.MAX_UPLOAD_BYTES) {
            throw GalleryService.uploadTooLarge();
        }
        try (InputStream in = request.getInputStream()) {
            byte[] body = in.readNBytes(ImagePipeline.MAX_UPLOAD_BYTES + 1);
            if (body.length > ImagePipeline.MAX_UPLOAD_BYTES) {
                throw GalleryService.uploadTooLarge();
            }
            return body;
        }
    }
}

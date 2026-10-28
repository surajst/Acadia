package com.concept.media;

import java.io.IOException;

import jakarta.servlet.http.HttpServletRequest;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Serves locally stored images on a developer's machine and in CI, standing in
 * for the bucket.
 *
 * <p>Only in dev mode, and only for links {@link LocalMediaStore} signed and
 * that have not expired: the gallery decides who gets a link, exactly as it
 * does with presigned S3 links, and this endpoint checks nothing else. Without
 * the signature it would be an open read of every stored photo. When the store
 * is S3 (a bucket is set even in dev mode) there is nothing to serve here.
 */
@RestController
@ConditionalOnProperty(name = "app.dev-mode", havingValue = "true")
public class DevMediaController {

    private final MediaStore store;

    public DevMediaController(MediaStore store) {
        this.store = store;
    }

    @GetMapping("/dev-media/**")
    public ResponseEntity<byte[]> serve(HttpServletRequest request,
                                        @RequestParam(value = "expires", required = false) Long expires,
                                        @RequestParam(value = "sig", required = false) String sig) {
        if (!(store instanceof LocalMediaStore local)) {
            return ResponseEntity.notFound().build();
        }
        String key = request.getRequestURI().substring(request.getContextPath().length() + "/dev-media/".length());
        if (expires == null || !local.verify(key, expires, sig)) {
            return ResponseEntity.status(403).build();
        }
        try {
            return ResponseEntity.ok()
                    .contentType(MediaType.IMAGE_JPEG)
                    .cacheControl(CacheControl.noStore())
                    .body(local.read(key));
        } catch (IOException | IllegalArgumentException e) {
            // Missing, or a key that escapes the media root.
            return ResponseEntity.notFound().build();
        }
    }
}

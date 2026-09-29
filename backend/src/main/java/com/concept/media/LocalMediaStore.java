package com.concept.media;

import org.springframework.web.util.UriComponentsBuilder;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;

/**
 * The one used on a developer's machine and in CI.
 *
 * <p>Files go under a directory and are read back through the application. No
 * credentials, no bucket, no container -- which matters because the alternative
 * on offer was MinIO, and a suite that needs Docker to run is a suite people
 * stop running.
 *
 * <p>What this does NOT do is prove the S3 adapter works. The two share this
 * interface and nothing else: signing, permissions, CORS and the bucket policy
 * are exactly the parts a filesystem cannot exercise. Those need one real
 * upload against the actual bucket before anybody's photograph depends on them.
 */
public class LocalMediaStore implements MediaStore {

    private final Path root;
    private final String publicBase;

    public LocalMediaStore(Path root, String publicBase) {
        this.root = root;
        this.publicBase = publicBase;
    }

    @Override
    public void put(String key, byte[] bytes, String contentType) {
        try {
            Path target = resolve(key);
            Files.createDirectories(target.getParent());
            Files.write(target, bytes);
        } catch (IOException e) {
            throw new UncheckedIOException("could not write " + key, e);
        }
    }

    @Override
    public URI readUrl(String key, Duration validFor) {
        // No signature and no expiry, because there is nothing here worth
        // signing. Anything relying on the expiry being enforced would pass
        // locally and fail in production, so this deliberately does not pretend.
        return UriComponentsBuilder.fromUriString(publicBase)
                .pathSegment("dev-media")
                .path("/" + key)
                .build()
                .toUri();
    }

    @Override
    public void delete(String key) {
        try {
            Files.deleteIfExists(resolve(key));
        } catch (IOException e) {
            throw new UncheckedIOException("could not delete " + key, e);
        }
    }

    /** Read bytes back, for the dev-only endpoint that serves them. */
    public byte[] read(String key) throws IOException {
        return Files.readAllBytes(resolve(key));
    }

    /**
     * Keys are built by us, not supplied by a caller -- but this is the one
     * place a key becomes a filesystem path, so it checks anyway. A key that
     * escapes the root would let a traversal read any file the process can.
     */
    private Path resolve(String key) {
        Path target = root.resolve(key).normalize();
        if (!target.startsWith(root.normalize())) {
            throw new IllegalArgumentException("key escapes the media root: " + key);
        }
        return target;
    }
}

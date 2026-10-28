package com.concept.media;

import org.springframework.web.util.UriComponentsBuilder;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Clock;
import java.util.Base64;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
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
    private final byte[] signingKey;
    private final Clock clock;

    public LocalMediaStore(Path root, String publicBase) {
        this(root, publicBase, randomKey(), Clock.systemUTC());
    }

    LocalMediaStore(Path root, String publicBase, byte[] signingKey, Clock clock) {
        this.root = root;
        this.publicBase = publicBase;
        this.signingKey = signingKey.clone();
        this.clock = clock;
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

    /**
     * A link to the dev-media endpoint, signed and expiring like a presigned S3
     * GET.
     *
     * <p>It used to be a bare path, which made the endpoint behind it an
     * unauthenticated read of every stored photo. Signing it means the endpoint
     * serves only what the gallery chose to hand out, for as long as S3 would --
     * so code that leans on links expiring behaves the same here as deployed.
     * The key is random per process: links do not survive a restart, which is
     * fine for a developer's machine.
     */
    @Override
    public URI readUrl(String key, Duration validFor) {
        long expires = clock.instant().plus(validFor).getEpochSecond();
        return UriComponentsBuilder.fromUriString(publicBase)
                .pathSegment("dev-media")
                .path("/" + key)
                .queryParam("expires", expires)
                .queryParam("sig", sign(key, expires))
                .build()
                .toUri();
    }

    /** Whether a link's signature is ours and it has not expired. */
    public boolean verify(String key, long expires, String signature) {
        if (signature == null || clock.instant().getEpochSecond() > expires) {
            return false;
        }
        return MessageDigest.isEqual(sign(key, expires).getBytes(StandardCharsets.US_ASCII),
                signature.getBytes(StandardCharsets.US_ASCII));
    }

    private String sign(String key, long expires) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(signingKey, "HmacSHA256"));
            byte[] digest = mac.doFinal((key + "\n" + expires).getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(digest);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("HmacSHA256 is unavailable", e);
        }
    }

    private static byte[] randomKey() {
        byte[] key = new byte[32];
        new SecureRandom().nextBytes(key);
        return key;
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

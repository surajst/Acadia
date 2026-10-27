package com.concept.media;

import java.net.URI;
import java.time.Duration;

/**
 * Somewhere to keep files that are too big for the database.
 *
 * <p>Two implementations: {@link S3MediaStore} for anything deployed, and
 * {@link LocalMediaStore} for a developer's machine and CI, so neither needs
 * credentials or a container to run the suite.
 *
 * <h2>Why bytes are written from here and not from the browser</h2>
 *
 * <p>There is no presigned PUT on this interface, and that is deliberate. A
 * photograph off a phone carries EXIF, and EXIF carries the GPS coordinates
 * where it was taken. For pictures of children that is the single most
 * sensitive thing in the file, and stripping it is the whole reason the
 * original is never kept.
 *
 * <p>If the browser uploaded straight to the bucket, the original would land
 * there with its coordinates intact and we would be deleting it after the
 * fact -- which means it existed, was backed up, and was replicated, before
 * anybody stripped anything. So uploads come to the application, are stripped
 * and resized in memory, and only the two derivatives are ever stored.
 *
 * <p>Reads do use presigned GETs: no bytes go through the application, the
 * link expires, and the bucket stays private.
 */
public interface MediaStore {

    /** Store bytes under a key, replacing whatever was there. */
    void put(String key, byte[] bytes, String contentType);

    /**
     * A link that lets the holder read this one object for a while.
     *
     * <p>Short-lived by design: it is handed to a browser, and anything a
     * browser holds ends up in history, logs and shared screenshots.
     */
    URI readUrl(String key, Duration validFor);

    /** Remove an object. Succeeds when the object was already gone. */
    void delete(String key);
}

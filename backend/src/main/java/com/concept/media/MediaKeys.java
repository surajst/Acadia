package com.concept.media;

import java.util.UUID;

/**
 * Where things live in the bucket.
 *
 * <p>Every key starts with the tenant, which is what makes a mistake
 * survivable: a prefix is something you can grant, revoke, audit and delete
 * per school. Flat keys would mean one school's photograph is one guessed
 * identifier away from another's, and no way to hand a school its own data or
 * to prove it was removed.
 *
 * <p>The identifiers in a key are random UUIDs, so a key is not guessable --
 * but that is a second line, not the first. Objects are never public; reads go
 * through a short-lived presigned URL.
 */
public final class MediaKeys {

    private MediaKeys() {
    }

    /** The small one, for a grid. */
    public static String albumThumb(UUID tenantId, UUID albumId, UUID photoId) {
        return album(tenantId, albumId) + photoId + "-thumb.jpg";
    }

    /** The one shown on its own, big enough for a phone screen and no bigger. */
    public static String albumDisplay(UUID tenantId, UUID albumId, UUID photoId) {
        return album(tenantId, albumId) + photoId + "-display.jpg";
    }

    private static String album(UUID tenantId, UUID albumId) {
        return "tenant/" + tenantId + "/albums/" + albumId + "/";
    }
}

package com.concept.media;

import java.net.URI;
import java.time.Duration;

/**
 * What a deployed server uses when no bucket has been set up.
 *
 * <p>Not the filesystem: on Render the container's disk is wiped on every
 * deploy, so photographs written there would vanish without anybody noticing
 * until a parent asked where they went. Not a refusal to start either: the rest
 * of the school's app has nothing to do with photos and should keep running.
 * So the gallery says plainly that photos are not switched on, and stores
 * nothing.
 */
public class UnavailableMediaStore implements MediaStore {

    /** What a person is told. */
    public static final String NOT_SET_UP =
            "Photo storage is not set up for this school yet, so photos cannot be added.";

    @Override
    public void put(String key, byte[] bytes, String contentType) {
        throw new IllegalStateException(NOT_SET_UP);
    }

    @Override
    public URI readUrl(String key, Duration validFor) {
        throw new IllegalStateException(NOT_SET_UP);
    }

    /** Nothing was ever stored here, so there is nothing to delete. */
    @Override
    public void delete(String key) {
    }

    @Override
    public boolean available() {
        return false;
    }
}

package com.concept.video.app;

/**
 * Asks YouTube whether a video exists and may be played inside the app.
 *
 * <p>An interface because this is the one part of adding a video that leaves the
 * building. Tests supply their own -- CI has no business making a request to
 * youtube.com, and a suite that does is a suite that fails when a runner has no
 * egress or when somebody's video is deleted.
 */
public interface VideoLookup {

    /** Raised when the video cannot be shown, with wording a teacher can act on. */
    class Unplayable extends RuntimeException {
        public Unplayable(String message) {
            super(message);
        }
    }

    /**
     * @throws Unplayable when the video does not exist, is private, or its owner
     *                    has turned off embedding
     */
    VideoDetails describe(String youtubeId);
}

package com.concept.gallery.app;

/**
 * Refusals a person reads, carrying the status a client should see. Shaped
 * like {@code VideoException}, so a future controller maps both the same way.
 */
public class GalleryException extends RuntimeException {

    private final int status;

    private GalleryException(int status, String message) {
        super(message);
        this.status = status;
    }

    public int status() {
        return status;
    }

    public static GalleryException badRequest(String message) {
        return new GalleryException(400, message);
    }

    public static GalleryException forbidden(String message) {
        return new GalleryException(403, message);
    }

    public static GalleryException notFound(String message) {
        return new GalleryException(404, message);
    }
}

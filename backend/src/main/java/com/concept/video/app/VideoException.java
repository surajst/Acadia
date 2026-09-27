package com.concept.video.app;

/**
 * Refusals a person reads, carrying the status the client should see.
 *
 * <p>Shaped like {@code TasksException} on purpose: the controller maps it with an
 * {@code @ExceptionHandler}, so the two features answer a client the same way and
 * neither leaks a stack trace where a sentence belongs.
 */
public class VideoException extends RuntimeException {

    private final int status;

    private VideoException(int status, String message) {
        super(message);
        this.status = status;
    }

    public int status() {
        return status;
    }

    public static VideoException badRequest(String message) {
        return new VideoException(400, message);
    }

    public static VideoException forbidden(String message) {
        return new VideoException(403, message);
    }

    public static VideoException notFound(String message) {
        return new VideoException(404, message);
    }

    public static VideoException conflict(String message) {
        return new VideoException(409, message);
    }
}

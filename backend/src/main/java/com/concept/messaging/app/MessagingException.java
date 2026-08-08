package com.concept.messaging.app;

/**
 * A messaging failure that carries the HTTP status the web layer should return
 * (400 bad request, 403 forbidden, 502 upstream). Keeps status mapping out of
 * the application logic while letting the controller stay a thin translator.
 */
public class MessagingException extends RuntimeException {
    private final int status;

    public MessagingException(int status, String message) {
        super(message);
        this.status = status;
    }

    public int status() { return status; }

    public static MessagingException badRequest(String m) { return new MessagingException(400, m); }
    public static MessagingException forbidden(String m) { return new MessagingException(403, m); }
    public static MessagingException upstream(String m) { return new MessagingException(502, m); }
}

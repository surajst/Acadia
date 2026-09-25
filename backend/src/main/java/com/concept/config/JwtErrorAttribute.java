package com.concept.config;

import jakarta.servlet.http.HttpServletRequest;

/**
 * Carries <em>why</em> a token failed from the JWT filter to the entry point.
 *
 * <p>The filter is the only thing that sees the exception, and the entry point is
 * the only thing that writes the response, and Spring Security does not pass
 * anything between them -- a failed token simply leaves the security context
 * empty, which is indistinguishable from no token at all. A request attribute is
 * the seam that survives the gap; it lives and dies with the one request.
 *
 * <p>The distinction earns its keep on the client: an expired session should clear
 * the stored login and send the person to sign in, and a request that merely
 * failed should not.
 */
public final class JwtErrorAttribute {

    /** A token that was valid once and has passed its expiry. The ordinary case. */
    public static final String EXPIRED = "expired";

    /** A token that never was valid: wrong signature, malformed, unknown user. */
    public static final String INVALID = "invalid";

    /** No Authorization header, or one that is not a Bearer token. */
    public static final String MISSING = "missing";

    private static final String KEY = JwtErrorAttribute.class.getName();

    private JwtErrorAttribute() {
    }

    public static void record(HttpServletRequest request, String reason) {
        request.setAttribute(KEY, reason);
    }

    /** The recorded reason, or {@link #MISSING} when the filter recorded nothing. */
    public static String reasonFrom(HttpServletRequest request) {
        Object recorded = request.getAttribute(KEY);
        return recorded instanceof String reason ? reason : MISSING;
    }
}

package com.concept.common;

import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * A fixed-window request counter, keyed by whatever the caller chooses.
 *
 * <p>Exists for one job: {@code /api/onboard/subdomain-available} is
 * unauthenticated by necessity (nobody has an account yet) and answers whether a
 * given address belongs to a school on ACADIA. Left open it is a directory
 * anyone can walk -- a few thousand requests enumerate the customer list, which
 * is not a secret the product gets to leak on a signup form.
 *
 * <p>In memory on purpose. The alternative is Redis, and a new piece of
 * infrastructure for one public endpoint is a worse trade than a limit that
 * resets when the process does. Two consequences are worth stating rather than
 * discovering: the count is per instance, so N instances allow N times the limit
 * (there is one today); and it does not survive a restart. Both are acceptable
 * for slowing down enumeration, and neither would be for anything that has to be
 * exact, like billing.
 *
 * <p>Fixed window rather than sliding: a caller can land twice the limit across a
 * window boundary. A sliding window means keeping every timestamp, and for
 * "make scraping slow" the simpler structure is worth more than the precision.
 */
@Component
public class RateLimiter {

    /** Long enough that a normal signup never notices, short enough to bound memory. */
    static final Duration WINDOW = Duration.ofMinutes(1);

    /**
     * Above this the map itself is the problem -- an attacker rotating source
     * addresses would otherwise grow it without limit. Reaching it clears the
     * map, which resets everyone's count: worse than perfect, better than
     * running out of heap, and a legitimate signup is unaffected either way.
     */
    static final int MAX_TRACKED_KEYS = 50_000;

    private final Map<String, Window> windows = new ConcurrentHashMap<>();

    private static final class Window {
        final Instant startedAt;
        final AtomicInteger count = new AtomicInteger();

        Window(Instant startedAt) {
            this.startedAt = startedAt;
        }
    }

    /**
     * Record one request and say whether it is within the limit.
     *
     * @param key   what to count by -- for a public endpoint, the client address
     * @param limit requests allowed per {@link #WINDOW}
     * @return true when the request may proceed, false when it is over the limit
     */
    public boolean tryAcquire(String key, int limit) {
        if (key == null || key.isBlank()) {
            // No key means nothing to count by. Refusing every such request would
            // take the endpoint down for anyone behind a proxy that strips the
            // address; allowing them is the safer failure for a limiter whose job
            // is to slow scraping, not to authorise.
            return true;
        }
        if (windows.size() > MAX_TRACKED_KEYS) {
            windows.clear();
        }

        Instant now = Instant.now();
        Window window = windows.compute(key, (k, existing) ->
                existing == null || existing.startedAt.plus(WINDOW).isBefore(now)
                        ? new Window(now)
                        : existing);

        return window.count.incrementAndGet() <= limit;
    }

    /** Test seam: forget everything counted so far. */
    public void reset() {
        windows.clear();
    }
}

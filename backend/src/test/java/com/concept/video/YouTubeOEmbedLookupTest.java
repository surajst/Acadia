package com.concept.video;

import com.concept.video.app.VideoDetails;
import com.concept.video.app.VideoLookup;
import com.concept.video.app.YouTubeOEmbedLookup;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The real lookup, against a server that behaves the way YouTube does -- run
 * locally, so none of this depends on youtube.com being up, on a CI runner having
 * egress, or on somebody not deleting the video a test was written against.
 *
 * <p>The timeout is the case worth the machinery. This call runs inside the
 * request that saves a video, with a teacher watching a form: a slow answer is
 * worse than no answer, because the browser gives up somewhere the server cannot
 * see, the teacher presses the button again, and a video that did save quietly
 * becomes two. Proving a five second bound against youtube.com would be both slow
 * and a test of somebody else's uptime, so the base URL is a property and this
 * points it at a handler that never replies.
 */
class YouTubeOEmbedLookupTest {

    private static final String VIDEO_ID = "dQw4w9WgXcQ";

    private HttpServer server;
    private String base;
    private final AtomicReference<Responder> responder = new AtomicReference<>();

    private interface Responder {
        void respond(com.sun.net.httpserver.HttpExchange exchange) throws IOException;
    }

    @BeforeEach
    void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/oembed", exchange -> {
            Responder r = responder.get();
            if (r == null) {
                exchange.sendResponseHeaders(500, -1);
                exchange.close();
                return;
            }
            r.respond(exchange);
        });
        server.start();
        base = "http://127.0.0.1:" + server.getAddress().getPort() + "/oembed";
    }

    @AfterEach
    void stopServer() {
        if (server != null) {
            server.stop(0);
        }
    }

    private void replyWith(int status, String body) {
        responder.set(exchange -> {
            byte[] bytes = body == null ? new byte[0] : body.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(status, bytes.length == 0 ? -1 : bytes.length);
            if (bytes.length > 0) {
                try (OutputStream out = exchange.getResponseBody()) {
                    out.write(bytes);
                }
            }
            exchange.close();
        });
    }

    private VideoLookup lookup() {
        return new YouTubeOEmbedLookup(base);
    }

    // ── The happy answer ─────────────────────────────────────────────────────

    @Test
    void aPlayableVideoComesBackWithItsRealTitle() {
        replyWith(200, "{\"title\":\"Photosynthesis in 5 minutes\","
                + "\"thumbnail_url\":\"https://i.ytimg.com/vi/" + VIDEO_ID + "/hqdefault.jpg\"}");

        VideoDetails details = lookup().describe(VIDEO_ID);

        assertEquals("Photosynthesis in 5 minutes", details.title());
        // The thumbnail oEmbed offers is deliberately dropped: it is derived from
        // the id instead, so a stored address cannot go stale.
        assertNull(details.thumbnailUrl());
    }

    // ── The timeout ──────────────────────────────────────────────────────────

    /**
     * A server that accepts the connection and then says nothing, which is the
     * shape of the failure that actually hurts -- a refused connection is instant,
     * a hanging one is what leaves a teacher looking at a spinner.
     */
    @Test
    void aServerThatNeverRepliesIsGivenUpOnAndSaysSo() {
        responder.set(exchange -> {
            try {
                Thread.sleep(Duration.ofSeconds(30).toMillis());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });

        Instant started = Instant.now();
        VideoLookup.Unplayable refused =
                assertThrows(VideoLookup.Unplayable.class, () -> lookup().describe(VIDEO_ID));
        Duration waited = Duration.between(started, Instant.now());

        assertEquals("Couldn't reach YouTube, try again.", refused.getMessage());
        assertTrue(waited.compareTo(Duration.ofSeconds(10)) < 0,
                "a teacher should not be left on a form for " + waited.toSeconds()
                        + "s; the bound is five");
        // And not the other message. "That video is no good" would send a teacher
        // off to edit a link that was fine all along.
        assertTrue(!refused.getMessage().toLowerCase().contains("embedding"));
    }

    @Test
    void anUnreachableHostSaysTheSameThing() {
        // Port 1 on loopback: nothing listens, so the connection is refused rather
        // than hanging. Same message, because the teacher's next move is the same.
        VideoLookup unreachable = new YouTubeOEmbedLookup("http://127.0.0.1:1/oembed");

        VideoLookup.Unplayable refused =
                assertThrows(VideoLookup.Unplayable.class, () -> unreachable.describe(VIDEO_ID));

        assertEquals("Couldn't reach YouTube, try again.", refused.getMessage());
    }

    // ── What YouTube says about videos you cannot embed ──────────────────────

    @Test
    void embeddingTurnedOffIsItsOwnMessage() {
        replyWith(401, null);

        VideoLookup.Unplayable refused =
                assertThrows(VideoLookup.Unplayable.class, () -> lookup().describe(VIDEO_ID));

        assertTrue(refused.getMessage().contains("turned off embedding"), refused.getMessage());
    }

    @Test
    void aDeletedOrPrivateVideoIsItsOwnMessageToo() {
        replyWith(404, null);

        VideoLookup.Unplayable refused =
                assertThrows(VideoLookup.Unplayable.class, () -> lookup().describe(VIDEO_ID));

        assertTrue(refused.getMessage().toLowerCase().contains("does not exist"), refused.getMessage());
    }

    /**
     * Anything else is a YouTube problem, not a video problem, and the wording has
     * to keep the two apart -- the teacher's next move is different.
     */
    @Test
    void aServerErrorIsNotBlamedOnTheVideo() {
        replyWith(503, null);

        VideoLookup.Unplayable refused =
                assertThrows(VideoLookup.Unplayable.class, () -> lookup().describe(VIDEO_ID));

        assertEquals("Couldn't reach YouTube, try again.", refused.getMessage());
    }

    @Test
    void anAnswerThatIsNotJsonIsNotBlamedOnTheVideoEither() {
        replyWith(200, "<html>we are down</html>");

        assertEquals("Couldn't reach YouTube, try again.",
                assertThrows(VideoLookup.Unplayable.class, () -> lookup().describe(VIDEO_ID))
                        .getMessage());
    }

    @Test
    void anAnswerWithNoTitleIsTreatedAsUnplayable() {
        replyWith(200, "{\"author_name\":\"Somebody\"}");

        assertTrue(assertThrows(VideoLookup.Unplayable.class, () -> lookup().describe(VIDEO_ID))
                .getMessage().contains("turned off embedding"));
    }
}

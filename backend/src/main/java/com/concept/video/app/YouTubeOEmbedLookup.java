package com.concept.video.app;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * The real lookup: YouTube's oEmbed endpoint.
 *
 * <p>No API key, no quota, and it answers the question actually being asked. A
 * video that is deleted, private, or whose owner has turned off embedding comes
 * back 401 or 404 -- so one call proves the video exists <em>and</em> that it will
 * play inside the app, which is the pair of facts a teacher needs before a class
 * is told to watch something.
 *
 * <h2>Five seconds, and then say so</h2>
 *
 * <p>This runs inside the request that saves the video, with a teacher watching a
 * form. A slow answer is worse than no answer: the browser gives up somewhere the
 * server cannot see, the teacher presses the button again, and a video that did
 * save quietly becomes two. So the wait is bounded and the refusal is honest about
 * whose fault it is -- "couldn't reach YouTube" is something to retry, where "that
 * video is no good" sends a teacher off to edit a link that was fine.
 *
 * <p>The base URL is a property so the timeout can be tested against a server that
 * deliberately hangs. Making CI wait on youtube.com to prove a timeout works would
 * be both slow and a test of somebody else's uptime.
 */
@Service
public class YouTubeOEmbedLookup implements VideoLookup {

    private static final Logger log = LoggerFactory.getLogger(YouTubeOEmbedLookup.class);

    private static final String CANNOT_EMBED =
            "This video can't be played inside the app (the owner has turned off embedding).";
    static final String UNREACHABLE = "Couldn't reach YouTube, try again.";

    /** Five seconds end to end, connect included. */
    static final Duration TIMEOUT = Duration.ofSeconds(5);

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(TIMEOUT)
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final String oEmbedBase;

    public YouTubeOEmbedLookup(
            @Value("${app.youtube.oembed-base:https://www.youtube.com/oembed}") String oEmbedBase) {
        this.oEmbedBase = oEmbedBase;
    }

    @Override
    public VideoDetails describe(String youtubeId) {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(oEmbedBase
                        + "?url=https%3A%2F%2Fwww.youtube.com%2Fwatch%3Fv%3D" + youtubeId
                        + "&format=json"))
                .timeout(TIMEOUT)
                .header("Accept", "application/json")
                .GET()
                .build();

        HttpResponse<String> response;
        try {
            response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new Unplayable(UNREACHABLE);
        } catch (Exception e) {
            // Deliberately not "the video is bad": we do not know that. Saying so
            // would have a teacher editing a link that was fine all along.
            log.warn("oEmbed lookup failed for {}", youtubeId, e);
            throw new Unplayable(UNREACHABLE);
        }

        if (response.statusCode() == 401 || response.statusCode() == 403) {
            throw new Unplayable(CANNOT_EMBED);
        }
        if (response.statusCode() == 404) {
            throw new Unplayable("That video does not exist, or it is private.");
        }
        if (response.statusCode() != 200) {
            log.warn("oEmbed returned {} for {}", response.statusCode(), youtubeId);
            throw new Unplayable(UNREACHABLE);
        }

        try {
            JsonNode body = objectMapper.readTree(response.body());
            String title = body.path("title").asText(null);
            if (title == null || title.isBlank()) {
                throw new Unplayable(CANNOT_EMBED);
            }
            // The thumbnail oEmbed offers is deliberately ignored: it is derived
            // from the id instead, so a stored address cannot go stale. See
            // YouTubeUrls.thumbnailUrl.
            return new VideoDetails(title.trim(), null);
        } catch (Unplayable e) {
            throw e;
        } catch (Exception e) {
            log.warn("oEmbed gave something unreadable for {}", youtubeId, e);
            throw new Unplayable(UNREACHABLE);
        }
    }
}

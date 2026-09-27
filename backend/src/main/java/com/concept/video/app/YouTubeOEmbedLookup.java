package com.concept.video.app;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
 * <p>Short timeouts on purpose. This runs inside the request that saves the video,
 * and a teacher waiting on a form is a worse failure than a refusal they can
 * retry.
 */
@Service
public class YouTubeOEmbedLookup implements VideoLookup {

    private static final Logger log = LoggerFactory.getLogger(YouTubeOEmbedLookup.class);

    private static final String CANNOT_EMBED =
            "This video can't be played inside the app (the owner has turned off embedding).";

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public VideoDetails describe(String youtubeId) {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(YouTubeUrls.oEmbedUrl(youtubeId)))
                .timeout(Duration.ofSeconds(8))
                .header("Accept", "application/json")
                .GET()
                .build();

        HttpResponse<String> response;
        try {
            response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new Unplayable("Could not reach YouTube to check that video. Please try again.");
        } catch (Exception e) {
            // Deliberately not "the video is bad": we do not know that. Saying so
            // would have a teacher editing a link that was fine all along.
            log.warn("oEmbed lookup failed for {}", youtubeId, e);
            throw new Unplayable("Could not reach YouTube to check that video. Please try again.");
        }

        if (response.statusCode() == 401 || response.statusCode() == 403) {
            throw new Unplayable(CANNOT_EMBED);
        }
        if (response.statusCode() == 404) {
            throw new Unplayable("That video does not exist, or it is private.");
        }
        if (response.statusCode() != 200) {
            log.warn("oEmbed returned {} for {}", response.statusCode(), youtubeId);
            throw new Unplayable("Could not reach YouTube to check that video. Please try again.");
        }

        try {
            JsonNode body = objectMapper.readTree(response.body());
            String title = body.path("title").asText(null);
            String thumbnail = body.path("thumbnail_url").asText(null);
            if (title == null || title.isBlank()) {
                throw new Unplayable(CANNOT_EMBED);
            }
            return new VideoDetails(title.trim(), thumbnail);
        } catch (Unplayable e) {
            throw e;
        } catch (Exception e) {
            log.warn("oEmbed gave something unreadable for {}", youtubeId, e);
            throw new Unplayable("Could not reach YouTube to check that video. Please try again.");
        }
    }
}

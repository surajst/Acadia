package com.concept.video.app;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Turns a link somebody pasted into the eleven characters worth keeping.
 *
 * <p>Only the id is stored. A pasted URL carries whatever was on it -- tracking
 * parameters, a playlist it happened to be playing from, a timestamp, an
 * autoplay flag -- and every one of those is a second source of truth that can
 * disagree with the first when the player URL is rebuilt. The id is the only
 * part that means the video.
 *
 * <p>Refusing a playlist or a channel is the point of this being strict rather
 * than "find something that looks like an id". {@code /watch?v=X&list=Y} is a
 * video and is accepted, with the list dropped. {@code /playlist?list=Y} has no
 * video in it at all, and a regex loose enough to pull an id out of one is loose
 * enough to pull eleven characters out of almost anything.
 */
public final class YouTubeUrls {

    /** YouTube's own id alphabet and fixed width. */
    private static final Pattern ID = Pattern.compile("[A-Za-z0-9_-]{11}");

    private static final Pattern WATCH =
            Pattern.compile("^https?://(?:www\\.|m\\.)?youtube\\.com/watch\\?(?:.*&)?v=([A-Za-z0-9_-]{11})(?:[&#].*)?$");
    private static final Pattern SHORT =
            Pattern.compile("^https?://youtu\\.be/([A-Za-z0-9_-]{11})(?:[?#].*)?$");
    private static final Pattern SHORTS =
            Pattern.compile("^https?://(?:www\\.|m\\.)?youtube\\.com/shorts/([A-Za-z0-9_-]{11})(?:[?#/].*)?$");
    private static final Pattern EMBED =
            Pattern.compile("^https?://(?:www\\.)?youtube(?:-nocookie)?\\.com/embed/([A-Za-z0-9_-]{11})(?:[?#/].*)?$");

    private static final Pattern[] ACCEPTED = {WATCH, SHORT, SHORTS, EMBED};

    private YouTubeUrls() {}

    /** Why a link was refused, in words a teacher can act on. */
    public static final class NotAVideo extends IllegalArgumentException {
        public NotAVideo(String message) {
            super(message);
        }
    }

    /**
     * The eleven-character id in this link.
     *
     * @throws NotAVideo when the link is not a single YouTube video
     */
    public static String videoId(String raw) {
        String url = raw == null ? "" : raw.trim();
        if (url.isEmpty()) {
            throw new NotAVideo("Paste a YouTube link.");
        }

        for (Pattern accepted : ACCEPTED) {
            Matcher m = accepted.matcher(url);
            if (m.matches()) {
                return m.group(1);
            }
        }

        // Past this point it is refused; the rest is only about saying why, since
        // "that link is not valid" sends a teacher to support rather than to the
        // address bar.
        String lower = url.toLowerCase(java.util.Locale.ROOT);
        if (lower.contains("/playlist?") || lower.contains("list=")) {
            throw new NotAVideo("That is a playlist. Open the one video you want and paste its link.");
        }
        if (lower.contains("/channel/") || lower.contains("/@") || lower.contains("/c/")
                || lower.contains("/user/")) {
            throw new NotAVideo("That is a channel. Open the one video you want and paste its link.");
        }
        if (!isYouTubeHost(url)) {
            // By host, not by substring. "notyoutube.com" contains "youtube.com",
            // and so does "www.youtube.com.evil.example" -- the second is the
            // reason the patterns above are anchored, and it would be odd for the
            // message below to be fooled by what the matching is not.
            throw new NotAVideo("Only YouTube links can be added.");
        }
        throw new NotAVideo("That does not look like a YouTube video link.");
    }

    /** Whether the link's host really is YouTube's, rather than merely containing it. */
    private static boolean isYouTubeHost(String url) {
        try {
            String host = java.net.URI.create(url).getHost();
            if (host == null) {
                return false;
            }
            host = host.toLowerCase(java.util.Locale.ROOT);
            return host.equals("youtu.be")
                    || host.equals("youtube.com") || host.endsWith(".youtube.com")
                    || host.equals("youtube-nocookie.com") || host.endsWith(".youtube-nocookie.com");
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    /** Whether a stored value is a plausible id, for reading rows back. */
    public static boolean isVideoId(String value) {
        return value != null && ID.matcher(value).matches();
    }

    /**
     * The player address for an id.
     *
     * <p>youtube-nocookie, so a child watching schoolwork is not handed the
     * tracking cookie the ordinary domain sets. rel=0 keeps end-screen suggestions
     * to the same channel -- it does not remove them, and nothing here tries to:
     * hiding YouTube's branding or its advertising would breach their terms.
     *
     * <p>enablejsapi=1 is what lets the player report that a video finished.
     * Without it YouTube posts no messages at all and "Watched" never happens,
     * silently, behind a player that otherwise looks perfectly fine.
     *
     * <p>The client appends {@code &origin=<its own origin>} to this, and must:
     * YouTube refuses to post to a page whose origin it was not told, and only the
     * page knows what that is. A server guessing would be wrong on the web build,
     * on localhost and in the native webview, for three different reasons. That one
     * parameter is the only part of the player address the client decides.
     */
    public static String embedUrl(String youtubeId) {
        return "https://www.youtube-nocookie.com/embed/" + youtubeId
                + "?rel=0&modestbranding=1&playsinline=1&enablejsapi=1";
    }

    /**
     * The thumbnail for an id, built rather than stored.
     *
     * <p>oEmbed returns a thumbnail URL and storing it looked like the obvious
     * thing. It is not: that URL is a fact about YouTube's CDN at the moment the
     * video was added, and it goes stale on its own schedule -- a stored one turns
     * into a broken image in a list, with no way to tell it has. The address is
     * derivable from the id, so there is no reason to keep a copy that can rot.
     *
     * <p>hqdefault rather than maxresdefault: every video has one. maxres exists
     * only for videos uploaded above a certain resolution, and its absence is a 404
     * image rather than a fallback.
     */
    public static String thumbnailUrl(String youtubeId) {
        return "https://i.ytimg.com/vi/" + youtubeId + "/hqdefault.jpg";
    }

    /** The oEmbed endpoint for an id. No API key, and it answers 401/404 when a video cannot be embedded. */
    public static String oEmbedUrl(String youtubeId) {
        return "https://www.youtube.com/oembed?url=https%3A%2F%2Fwww.youtube.com%2Fwatch%3Fv%3D"
                + youtubeId + "&format=json";
    }
}

package com.concept.video;

import com.concept.video.app.YouTubeUrls;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * What counts as a link to one YouTube video.
 *
 * <p>Only the eleven-character id is kept. A pasted URL carries whatever was on
 * it -- tracking parameters, a playlist it happened to be playing from, a start
 * time -- and each is a second source of truth that can disagree with the first
 * when the player address is rebuilt.
 *
 * <p>The refusals matter as much as the acceptances, and the playlist case is the
 * one that decides the shape of the whole parser. A regex loose enough to find an
 * id inside {@code /playlist?list=...} is loose enough to find eleven characters
 * in almost anything, so these patterns match the whole URL rather than search
 * inside it.
 */
class YouTubeUrlsTest {

    private static final String ID = "dQw4w9WgXcQ";

    // ── The four forms a teacher will actually paste ──────────────────────────

    @ParameterizedTest(name = "[{index}] {0}")
    @CsvSource({
            "https://www.youtube.com/watch?v=dQw4w9WgXcQ",
            "http://www.youtube.com/watch?v=dQw4w9WgXcQ",
            "https://youtube.com/watch?v=dQw4w9WgXcQ",
            "https://m.youtube.com/watch?v=dQw4w9WgXcQ",
            "https://youtu.be/dQw4w9WgXcQ",
            "https://www.youtube.com/shorts/dQw4w9WgXcQ",
            "https://www.youtube.com/embed/dQw4w9WgXcQ",
            "https://www.youtube-nocookie.com/embed/dQw4w9WgXcQ",
    })
    void theFormsWeAccept(String url) {
        assertEquals(ID, YouTubeUrls.videoId(url));
    }

    /**
     * The clutter a real paste carries. Every one of these is a single video, and
     * everything after the id is dropped -- including a `list`, because a video
     * playing from a playlist is still that video.
     */
    @ParameterizedTest(name = "[{index}] {0}")
    @ValueSource(strings = {
            "https://www.youtube.com/watch?v=dQw4w9WgXcQ&t=42s",
            "https://www.youtube.com/watch?v=dQw4w9WgXcQ&list=PLabc123&index=2",
            "https://www.youtube.com/watch?app=desktop&v=dQw4w9WgXcQ",
            "https://www.youtube.com/watch?v=dQw4w9WgXcQ#t=1m",
            "https://youtu.be/dQw4w9WgXcQ?t=42",
            "https://www.youtube.com/shorts/dQw4w9WgXcQ?feature=share",
            "  https://youtu.be/dQw4w9WgXcQ  ",
    })
    void theClutterARealPasteCarriesIsDropped(String url) {
        assertEquals(ID, YouTubeUrls.videoId(url));
    }

    // ── What is refused, and whether it says why ─────────────────────────────

    @Test
    void aPlaylistIsRefusedAndSaysWhatToDoInstead() {
        YouTubeUrls.NotAVideo refused = assertThrows(YouTubeUrls.NotAVideo.class,
                () -> YouTubeUrls.videoId("https://www.youtube.com/playlist?list=PLabc123def456"));

        assertTrue(refused.getMessage().toLowerCase().contains("playlist"), refused.getMessage());
        assertTrue(refused.getMessage().toLowerCase().contains("one video"),
                "a refusal that does not say what to do next sends a teacher to support: "
                        + refused.getMessage());
    }

    @ParameterizedTest(name = "[{index}] {0}")
    @ValueSource(strings = {
            "https://www.youtube.com/channel/UCabcdefghijklmnop",
            "https://www.youtube.com/@somechannel",
            "https://www.youtube.com/c/somechannel",
            "https://www.youtube.com/user/somechannel",
    })
    void aChannelIsRefused(String url) {
        YouTubeUrls.NotAVideo refused =
                assertThrows(YouTubeUrls.NotAVideo.class, () -> YouTubeUrls.videoId(url));
        assertTrue(refused.getMessage().toLowerCase().contains("channel"), refused.getMessage());
    }

    @ParameterizedTest(name = "[{index}] {0}")
    @ValueSource(strings = {
            "https://vimeo.com/123456789",
            "https://example.com/watch?v=dQw4w9WgXcQ",
            "https://notyoutube.com/watch?v=dQw4w9WgXcQ",
    })
    void somebodyElsesVideoIsRefused(String url) {
        YouTubeUrls.NotAVideo refused =
                assertThrows(YouTubeUrls.NotAVideo.class, () -> YouTubeUrls.videoId(url));
        assertTrue(refused.getMessage().toLowerCase().contains("only youtube"), refused.getMessage());
    }

    @ParameterizedTest(name = "[{index}] {0}")
    @ValueSource(strings = {
            "dQw4w9WgXcQ",
            "not a url at all",
            "https://www.youtube.com/watch?v=tooshort",
            "https://www.youtube.com/watch?v=waaaaaaaaytoolongforanid",
            "https://www.youtube.com/watch",
            "javascript:alert(1)",
            "https://www.youtube.com.evil.example/watch?v=dQw4w9WgXcQ",
    })
    void garbageIsRefused(String url) {
        assertThrows(YouTubeUrls.NotAVideo.class, () -> YouTubeUrls.videoId(url));
    }

    @Test
    void nothingAtAllIsRefused() {
        assertThrows(YouTubeUrls.NotAVideo.class, () -> YouTubeUrls.videoId(null));
        assertThrows(YouTubeUrls.NotAVideo.class, () -> YouTubeUrls.videoId("   "));
    }

    /**
     * A bare id is deliberately not accepted. It would be convenient, and it is
     * the loophole that makes every refusal above negotiable: anybody who wanted
     * to point at a playlist could take eleven characters out of it by hand.
     */
    @Test
    void aBareIdIsNotALink() {
        assertThrows(YouTubeUrls.NotAVideo.class, () -> YouTubeUrls.videoId(ID));
    }

    // ── What we build back out of the id ─────────────────────────────────────

    @Test
    void thePlayerAddressIsTheNoCookieDomain() {
        String embed = YouTubeUrls.embedUrl(ID);

        assertTrue(embed.startsWith("https://www.youtube-nocookie.com/embed/" + ID),
                "a child watching schoolwork should not collect the tracking cookie "
                        + "the ordinary domain sets: " + embed);
        assertTrue(embed.contains("rel=0"));
        assertTrue(embed.contains("playsinline=1"));
    }

    @Test
    void theOEmbedAddressAsksAboutThatVideo() {
        assertTrue(YouTubeUrls.oEmbedUrl(ID).contains(ID));
        assertTrue(YouTubeUrls.oEmbedUrl(ID).contains("format=json"));
    }

    @Test
    void anIdIsElevenCharactersOfYouTubesOwnAlphabet() {
        assertTrue(YouTubeUrls.isVideoId(ID));
        assertTrue(YouTubeUrls.isVideoId("_-Aa09_-Aa0"));
        assertTrue(!YouTubeUrls.isVideoId("tooshort"));
        assertTrue(!YouTubeUrls.isVideoId("has a space"));
        assertTrue(!YouTubeUrls.isVideoId(null));
    }
}

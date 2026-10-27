package com.concept.media;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.net.URI;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.web.util.UriComponentsBuilder;

class LocalMediaStoreTest {

    private static final byte[] KEY = "a-test-signing-key-of-32-bytes!!".getBytes();
    private static final Instant NOW = Instant.parse("2026-10-27T10:00:00Z");

    @TempDir
    Path root;

    private LocalMediaStore storeAt(Instant now) {
        return new LocalMediaStore(root, "http://localhost:8080", KEY, Clock.fixed(now, ZoneOffset.UTC));
    }

    private static Map<String, String> query(URI uri) {
        return UriComponentsBuilder.fromUri(uri).build().getQueryParams().toSingleValueMap()
                .entrySet().stream().collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }

    @Test
    void storesAndReadsBack() throws Exception {
        LocalMediaStore store = storeAt(NOW);
        store.put("tenant/t/albums/a/p-thumb.jpg", new byte[] {1, 2, 3}, "image/jpeg");
        assertThat(store.read("tenant/t/albums/a/p-thumb.jpg")).containsExactly(1, 2, 3);
        store.delete("tenant/t/albums/a/p-thumb.jpg");
        store.delete("tenant/t/albums/a/p-thumb.jpg");
    }

    @Test
    void aLinkItHandedOutVerifiesUntilItExpires() {
        String key = "tenant/t/albums/a/p-display.jpg";
        URI link = storeAt(NOW).readUrl(key, Duration.ofMinutes(10));
        Map<String, String> q = query(link);
        long expires = Long.parseLong(q.get("expires"));

        assertThat(link.getPath()).isEqualTo("/dev-media/" + key);
        assertThat(storeAt(NOW.plusSeconds(599)).verify(key, expires, q.get("sig"))).isTrue();
        assertThat(storeAt(NOW.plusSeconds(601)).verify(key, expires, q.get("sig"))).isFalse();
    }

    @Test
    void aSignatureIsGoodForItsOwnPhotoAndExpiryOnly() {
        String key = "tenant/t/albums/a/p-display.jpg";
        Map<String, String> q = query(storeAt(NOW).readUrl(key, Duration.ofMinutes(10)));
        long expires = Long.parseLong(q.get("expires"));
        LocalMediaStore store = storeAt(NOW);

        assertThat(store.verify("tenant/other/albums/a/p-display.jpg", expires, q.get("sig"))).isFalse();
        assertThat(store.verify(key, expires + 3600, q.get("sig"))).isFalse();
        assertThat(store.verify(key, expires, null)).isFalse();
        assertThat(store.verify(key, expires, "not-a-signature")).isFalse();
    }

    @Test
    void anotherProcessesLinksDoNotVerify() {
        String key = "tenant/t/albums/a/p-display.jpg";
        Map<String, String> q = query(storeAt(NOW).readUrl(key, Duration.ofMinutes(10)));
        LocalMediaStore restarted = new LocalMediaStore(root, "http://localhost:8080");
        assertThat(restarted.verify(key, Long.parseLong(q.get("expires")), q.get("sig"))).isFalse();
    }

    @Test
    void refusesAKeyThatEscapesTheRoot() {
        assertThatThrownBy(() -> storeAt(NOW).put("../outside.jpg", new byte[] {1}, "image/jpeg"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void anUnconfiguredStoreSaysSoAndStoresNothing() {
        UnavailableMediaStore store = new UnavailableMediaStore();
        assertThat(store.available()).isFalse();
        assertThatThrownBy(() -> store.put("k", new byte[] {1}, "image/jpeg"))
                .hasMessage(UnavailableMediaStore.NOT_SET_UP);
        store.delete("k");
    }
}

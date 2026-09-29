package com.concept.media;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

import java.time.Duration;

/**
 * What this parser does with files that are wrong.
 *
 * <p>Every input here is hostile or broken, and the bar is the same for all of
 * them: answer quickly, answer {@link ExifOrientation#NORMAL}, do not throw and
 * do not hang. This code runs on bytes a stranger uploaded, before anything has
 * decided the file is trustworthy, so it is the first thing an attacker reaches.
 *
 * <p>Each case is timed, not merely asserted. A parser that loops forever on a
 * self-referencing offset still "returns NORMAL" if you wait long enough -- the
 * test that only checks the value would pass while one request pinned a core.
 * On a 1GB single container that is the whole service.
 */
class ExifOrientationTest {

    /** Generous next to the microseconds this should take, tight next to a hang. */
    private static final Duration QUICKLY = Duration.ofSeconds(2);

    private static int readWithin(byte[] bytes) {
        return assertTimeoutPreemptively(QUICKLY, () -> ExifOrientation.read(bytes),
                "the parser must fail fast on a malformed file, not spin");
    }

    @Test
    void readsAnOrdinaryOrientation() throws IOException {
        assertThat(readWithin(app1(tiffWithOrientation(6)))).isEqualTo(6);
    }

    // ── Truncation ──────────────────────────────────────────────────────────

    /**
     * Every prefix of a valid file, as a dropped connection would leave it.
     *
     * <p>Not "all of them return NORMAL" -- a first version of this test asserted
     * that and was wrong. The APP1 segment ends before the file does, so a prefix
     * that happens to contain all of it legitimately yields 6; only the trailing
     * end-of-image marker is missing, and nothing needs that. Asserting NORMAL
     * everywhere would have been demanding a bug.
     *
     * <p>What must hold for every prefix: it returns quickly, it does not throw,
     * and the answer is a real orientation. Where the segment is incomplete the
     * answer has to be NORMAL, because a half-read tag is not evidence.
     */
    @Test
    void survivesAnApp1SegmentCutShort() throws IOException {
        byte[] whole = app1(tiffWithOrientation(6));
        int segmentEnd = 2 + 2 + (((whole[4] & 0xFF) << 8) | (whole[5] & 0xFF));

        for (int cut = 0; cut <= whole.length; cut++) {
            byte[] shortened = new byte[cut];
            System.arraycopy(whole, 0, shortened, 0, cut);
            int answer = readWithin(shortened);

            assertThat(answer)
                    .as("truncated at " + cut + " bytes: always a real orientation")
                    .isBetween(1, 8);
            if (cut < segmentEnd) {
                assertThat(answer)
                        .as("truncated at " + cut + ", inside the segment: a half-read tag "
                                + "is not evidence")
                        .isEqualTo(ExifOrientation.NORMAL);
            }
        }
    }

    @Test
    void survivesASegmentLengthLongerThanTheFile() throws IOException {
        byte[] file = app1(tiffWithOrientation(6));
        // Claim the APP1 segment runs well past the end of the data.
        file[4] = (byte) 0x7F;
        file[5] = (byte) 0xFF;
        assertThat(readWithin(file)).isEqualTo(ExifOrientation.NORMAL);
    }

    // ── Offsets that point nowhere ──────────────────────────────────────────

    @Test
    void survivesAnIfdOffsetPastTheEnd() throws IOException {
        byte[] tiff = tiffWithOrientation(6);
        // The IFD0 offset sits at bytes 4..7 of the TIFF header.
        tiff[4] = 0x7F; tiff[5] = (byte) 0xFF; tiff[6] = (byte) 0xFF; tiff[7] = (byte) 0xFF;
        assertThat(readWithin(app1(tiff))).isEqualTo(ExifOrientation.NORMAL);
    }

    @Test
    void survivesANegativeLookingOffset() throws IOException {
        byte[] tiff = tiffWithOrientation(6);
        // 0xFFFFFFFF is -1 as a signed int. Read as unsigned it is enormous;
        // read as signed it walks backwards out of the array. Either way it must
        // not reach an index.
        tiff[4] = (byte) 0xFF; tiff[5] = (byte) 0xFF;
        tiff[6] = (byte) 0xFF; tiff[7] = (byte) 0xFF;
        assertThat(readWithin(app1(tiff))).isEqualTo(ExifOrientation.NORMAL);
    }

    // ── Counts that lie ─────────────────────────────────────────────────────

    /**
     * An IFD claiming 65535 entries in a few bytes of data.
     *
     * <p>The orientation deliberately is NOT the first entry. A first version put
     * it first, so the parser found it and returned before the count mattered --
     * the test passed while asserting nothing about the count at all.
     */
    @Test
    void survivesAnEntryCountFarLargerThanTheData() throws IOException {
        ByteArrayOutputStream tiff = new ByteArrayOutputStream();
        tiff.write('M'); tiff.write('M');
        tiff.write(0); tiff.write(42);
        tiff.write(0); tiff.write(0); tiff.write(0); tiff.write(8);

        tiff.write(0xFF); tiff.write(0xFF);         // 65535 entries claimed
        tiff.write(0x01); tiff.write(0x00);         // one real entry, not orientation
        tiff.write(0); tiff.write(3);
        tiff.write(0); tiff.write(0); tiff.write(0); tiff.write(1);
        tiff.write(0); tiff.write(1); tiff.write(0); tiff.write(0);

        assertThat(readWithin(app1(tiff.toByteArray())))
                .as("the walk has to stop at the end of the data, not at the claimed count")
                .isEqualTo(ExifOrientation.NORMAL);
    }

    /**
     * The loop case.
     *
     * <p>An IFD whose "next IFD" offset points back at itself is the classic way
     * to hang a parser that follows the chain. This one does not follow it at all
     * -- it reads IFD0 and stops -- and this test is what keeps that true if
     * somebody later adds IFD1 support for thumbnails.
     */
    @Test
    void survivesAnIfdThatPointsAtItself() throws IOException {
        ByteArrayOutputStream tiff = new ByteArrayOutputStream();
        tiff.write('M'); tiff.write('M');
        tiff.write(0); tiff.write(42);
        tiff.write(0); tiff.write(0); tiff.write(0); tiff.write(8);   // IFD0 at +8

        tiff.write(0); tiff.write(1);                                  // one entry
        tiff.write(0x01); tiff.write(0x00);                            // NOT orientation
        tiff.write(0); tiff.write(3);
        tiff.write(0); tiff.write(0); tiff.write(0); tiff.write(1);
        tiff.write(0); tiff.write(1); tiff.write(0); tiff.write(0);
        tiff.write(0); tiff.write(0); tiff.write(0); tiff.write(8);    // next IFD = itself

        assertThat(readWithin(app1(tiff.toByteArray())))
                .isEqualTo(ExifOrientation.NORMAL);
    }

    @Test
    void survivesAByteOrderMarkItDoesNotRecognise() throws IOException {
        byte[] tiff = tiffWithOrientation(6);
        tiff[0] = 'X';
        tiff[1] = 'Y';
        assertThat(readWithin(app1(tiff))).isEqualTo(ExifOrientation.NORMAL);
    }

    @Test
    void survivesRandomBytesAndEmptyInput() {
        assertThat(readWithin(null)).isEqualTo(ExifOrientation.NORMAL);
        assertThat(readWithin(new byte[0])).isEqualTo(ExifOrientation.NORMAL);
        byte[] noise = new byte[512];
        for (int i = 0; i < noise.length; i++) {
            noise[i] = (byte) (i * 37);
        }
        assertThat(readWithin(noise)).isEqualTo(ExifOrientation.NORMAL);
    }

    /**
     * A file that is all segment headers and no content. Each is well formed, so
     * a parser cannot bail on the first one -- it has to walk all of them and
     * still stop.
     */
    @Test
    void survivesAFileOfNothingButEmptySegments() throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(0xFF); out.write(0xD8);
        for (int i = 0; i < 2000; i++) {
            out.write(0xFF);
            out.write(0xE2);   // APP2: valid, and not the one being looked for
            out.write(0x00);
            out.write(0x02);   // length 2, meaning no payload
        }
        assertThat(readWithin(out.toByteArray())).isEqualTo(ExifOrientation.NORMAL);
    }

    // ── Fixtures ────────────────────────────────────────────────────────────

    /** A TIFF block, big-endian, one IFD0 entry carrying the orientation. */
    private static byte[] tiffWithOrientation(int orientation) throws IOException {
        ByteArrayOutputStream tiff = new ByteArrayOutputStream();
        tiff.write('M'); tiff.write('M');
        tiff.write(0); tiff.write(42);
        tiff.write(0); tiff.write(0); tiff.write(0); tiff.write(8);

        tiff.write(0); tiff.write(1);
        tiff.write(0x01); tiff.write(0x12);
        tiff.write(0); tiff.write(3);
        tiff.write(0); tiff.write(0); tiff.write(0); tiff.write(1);
        tiff.write((orientation >> 8) & 0xFF);
        tiff.write(orientation & 0xFF);
        tiff.write(0); tiff.write(0);
        tiff.write(0); tiff.write(0); tiff.write(0); tiff.write(0);
        return tiff.toByteArray();
    }

    /** Wrap a TIFF block in a JPEG carrying one APP1 segment. */
    private static byte[] app1(byte[] tiff) throws IOException {
        int length = 2 + 6 + tiff.length;
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(0xFF); out.write(0xD8);
        out.write(0xFF); out.write(0xE1);
        out.write((length >> 8) & 0xFF);
        out.write(length & 0xFF);
        out.write("Exif".getBytes(StandardCharsets.US_ASCII));
        out.write(0); out.write(0);
        out.write(tiff);
        out.write(0xFF); out.write(0xD9);  // EOI
        return out.toByteArray();
    }
}

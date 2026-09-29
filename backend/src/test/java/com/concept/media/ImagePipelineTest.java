package com.concept.media;

import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ImagePipelineTest {

    private static byte[] jpeg(int width, int height) throws IOException {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        g.setColor(Color.ORANGE);
        g.fillRect(0, 0, width, height);
        g.dispose();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(image, "jpg", out);
        return out.toByteArray();
    }

    // ── What the bytes are ──────────────────────────────────────────────────

    @Test
    void recognisesAJpeg() throws IOException {
        assertThat(ImagePipeline.sniff(jpeg(10, 10))).isEqualTo(ImagePipeline.Kind.JPEG);
    }

    @Test
    void recognisesAPng() throws IOException {
        BufferedImage image = new BufferedImage(4, 4, BufferedImage.TYPE_INT_ARGB);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(image, "png", out);
        assertThat(ImagePipeline.sniff(out.toByteArray())).isEqualTo(ImagePipeline.Kind.PNG);
    }

    /**
     * The one that matters. A caller controls the filename and the Content-Type,
     * so neither is evidence about the contents.
     */
    @Test
    void refusesSomethingThatIsNotAnImageHoweverItIsLabelled() {
        byte[] zipPretendingOtherwise = new byte[] {
            0x50, 0x4B, 0x03, 0x04, 0x14, 0x00, 0x00, 0x00, 0x08, 0x00,
        };
        assertThat(ImagePipeline.sniff(zipPretendingOtherwise)).isNull();

        assertThat(ImagePipeline.sniff("<svg onload=alert(1)>".getBytes(StandardCharsets.UTF_8)))
                .as("an SVG is a script container, not a photograph")
                .isNull();
        assertThat(ImagePipeline.sniff(new byte[0])).isNull();
        assertThat(ImagePipeline.sniff(null)).isNull();
    }

    @Test
    void doesNotReadPastTheEndOfAShortFile() {
        // Three bytes of a PNG signature and nothing more. The check reads eight,
        // so the bounds matter: this used to be where an index blew up.
        assertThat(ImagePipeline.sniff(new byte[] {(byte) 0x89, 0x50, 0x4E})).isNull();
    }

    // ── What comes out ──────────────────────────────────────────────────────

    @Test
    void scalesDownToTheLongestEdgeAndKeepsTheShape() throws IOException {
        ImagePipeline.Rendered thumb =
                ImagePipeline.render(jpeg(2000, 1000), ImagePipeline.THUMB_EDGE);
        assertThat(thumb.width()).isEqualTo(ImagePipeline.THUMB_EDGE);
        assertThat(thumb.height()).isEqualTo(ImagePipeline.THUMB_EDGE / 2);
    }

    @Test
    void doesNotEnlargeSomethingAlreadySmaller() throws IOException {
        ImagePipeline.Rendered rendered = ImagePipeline.render(jpeg(120, 90), 1600);
        assertThat(rendered.width()).isEqualTo(120);
        assertThat(rendered.height()).isEqualTo(90);
    }

    /**
     * The point of the whole class.
     *
     * <p>Asserted on the bytes rather than by trusting that ImageIO does not copy
     * metadata. A JPEG's EXIF lives in an APP1 segment introduced by FF E1 and
     * carrying the string "Exif"; if either survives re-encoding, a photograph's
     * coordinates survive with it.
     */
    @Test
    void keepsNoMetadataFromTheOriginal() throws IOException {
        byte[] withExif = jpegCarryingFakeExif();
        assertThat(indexOf(withExif, "Exif".getBytes(StandardCharsets.US_ASCII)))
                .as("the fixture has to actually contain EXIF, or this proves nothing")
                .isGreaterThan(0);

        byte[] rendered = ImagePipeline.render(withExif, 1600).bytes();

        assertThat(indexOf(rendered, "Exif".getBytes(StandardCharsets.US_ASCII)))
                .as("an EXIF block in the stored image means a child's location in the bucket")
                .isEqualTo(-1);
        assertThat(indexOf(rendered, "51.5074,-0.1278".getBytes(StandardCharsets.US_ASCII)))
                .as("nor may the payload survive under another name")
                .isEqualTo(-1);
    }

    // ── Which way up ────────────────────────────────────────────────────────

    /**
     * The case that matters, and the one a phone actually produces.
     *
     * <p>A phone photographed in portrait stores the sensor's LANDSCAPE frame
     * plus orientation 6, meaning "turn this a quarter clockwise to view". A
     * pipeline that keeps only the pixels would store it on its side.
     *
     * <p>So: a wide source, tagged 6, must come out tall. Dimensions alone are
     * checked first because they are unambiguous, and then the corner pixels,
     * because an image can have the right shape and still be rotated the wrong
     * way round -- which is the mistake this code most easily makes.
     */
    @Test
    void turnsAPortraitPhonePhotographUpright() throws IOException {
        // 120 wide, 60 tall, with a red mark in the TOP-LEFT of the stored frame.
        byte[] stored = markedJpeg(120, 60);
        byte[] asPhoneWroteIt = withOrientation(stored, 6);

        assertThat(ExifOrientation.read(asPhoneWroteIt))
                .as("the fixture has to actually declare orientation 6")
                .isEqualTo(6);

        ImagePipeline.Rendered rendered = ImagePipeline.render(asPhoneWroteIt, 1600);

        assertThat(rendered.width()).as("a quarter turn swaps the sides").isEqualTo(60);
        assertThat(rendered.height()).isEqualTo(120);

        // A quarter turn clockwise sends the top-left corner to the top-right.
        BufferedImage out = ImageIO.read(new java.io.ByteArrayInputStream(rendered.bytes()));
        assertThat(isRed(out.getRGB(out.getWidth() - 1, 0)))
                .as("turned the wrong way: the mark should be top-RIGHT after a clockwise turn")
                .isTrue();
        assertThat(isRed(out.getRGB(0, 0)))
                .as("if the mark is still top-left, nothing was turned at all")
                .isFalse();
    }

    @Test
    void leavesAnUprightPhotographAlone() throws IOException {
        byte[] upright = withOrientation(markedJpeg(120, 60), 1);
        ImagePipeline.Rendered rendered = ImagePipeline.render(upright, 1600);
        assertThat(rendered.width()).isEqualTo(120);
        assertThat(rendered.height()).isEqualTo(60);
    }

    /** All eight, so a wrong case cannot hide behind the one we test by hand. */
    @Test
    void everyOrientationProducesTheExpectedShape() {
        BufferedImage wide = new BufferedImage(8, 4, BufferedImage.TYPE_INT_RGB);
        for (int o = 1; o <= 4; o++) {
            BufferedImage out = ImagePipeline.turnUpright(wide, o);
            assertThat(out.getWidth()).as("orientation " + o + " is not a quarter turn").isEqualTo(8);
            assertThat(out.getHeight()).isEqualTo(4);
        }
        for (int o = 5; o <= 8; o++) {
            BufferedImage out = ImagePipeline.turnUpright(wide, o);
            assertThat(out.getWidth()).as("orientation " + o + " is a quarter turn").isEqualTo(4);
            assertThat(out.getHeight()).isEqualTo(8);
        }
    }

    @Test
    void treatsAMissingOrNonsenseTagAsUpright() throws IOException {
        assertThat(ExifOrientation.read(jpeg(4, 4))).isEqualTo(ExifOrientation.NORMAL);
        assertThat(ExifOrientation.read(withOrientation(markedJpeg(8, 4), 99)))
                .as("a value outside 1..8 means nothing; leaving the pixels alone is the safe read")
                .isEqualTo(ExifOrientation.NORMAL);
        assertThat(ExifOrientation.read(new byte[] {(byte) 0xFF, (byte) 0xD8, 0x00}))
                .as("a truncated file must not throw")
                .isEqualTo(ExifOrientation.NORMAL);
    }

    // ── Size ────────────────────────────────────────────────────────────────

    /**
     * The cap exists because decoding is where a small file becomes a large
     * object: four bytes a pixel, whatever it compressed to. Refusing before
     * decode is the point, so this passes bytes that are never a valid image --
     * if the check ran after ImageIO, the message would be about decoding.
     */
    @Test
    void refusesAFileTooLargeToDecodeSafely() {
        byte[] huge = new byte[ImagePipeline.MAX_UPLOAD_BYTES + 1];
        assertThatThrownBy(() -> ImagePipeline.render(huge, 400))
                .isInstanceOf(IOException.class)
                .hasMessageContaining("too large");
    }

    @Test
    void refusesBytesThatCannotBeDecoded() {
        assertThatThrownBy(() -> ImagePipeline.render(new byte[] {1, 2, 3, 4}, 400))
                .isInstanceOf(IOException.class)
                .hasMessageContaining("not an image");
    }

    /**
     * A real JPEG with an APP1 segment spliced in after SOI, carrying the "Exif"
     * marker and a recognisable payload standing in for coordinates. Built here
     * rather than committed as a binary so what it contains is readable.
     */
    private static byte[] jpegCarryingFakeExif() throws IOException {
        byte[] base = jpeg(50, 50);
        byte[] payload = ("Exif\u0000\u0000" + "51.5074,-0.1278")
                .getBytes(StandardCharsets.US_ASCII);
        int length = payload.length + 2; // the segment length includes itself

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(base, 0, 2); // FF D8, the start-of-image marker
        out.write(0xFF);
        out.write(0xE1); // APP1
        out.write((length >> 8) & 0xFF);
        out.write(length & 0xFF);
        out.write(payload);
        out.write(base, 2, base.length - 2);
        return out.toByteArray();
    }

    /** A JPEG with a red mark in its top-left corner, so a turn is visible. */
    private static byte[] markedJpeg(int width, int height) throws IOException {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, width, height);
        g.setColor(Color.RED);
        // A block, not a pixel: JPEG is lossy and a single pixel would be
        // averaged away with its neighbours before anything could be asserted.
        g.fillRect(0, 0, Math.max(2, width / 4), Math.max(2, height / 4));
        g.dispose();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(image, "jpg", out);
        return out.toByteArray();
    }

    /** Lossy compression moves colours around, so "red" is a neighbourhood. */
    private static boolean isRed(int rgb) {
        int r = (rgb >> 16) & 0xFF;
        int g = (rgb >> 8) & 0xFF;
        int b = rgb & 0xFF;
        return r > 120 && g < 110 && b < 110;
    }

    /**
     * Splice a real EXIF APP1 segment declaring an orientation into a JPEG.
     *
     * <p>Built properly -- byte order mark, the 42, an IFD0 offset, one entry --
     * rather than stubbing the reader, because the parser walking this structure
     * correctly IS the thing under test. The offsets inside are relative to the
     * TIFF header, not to the file, which is the detail such a parser usually
     * gets wrong.
     */
    private static byte[] withOrientation(byte[] jpeg, int orientation) throws IOException {
        ByteArrayOutputStream tiff = new ByteArrayOutputStream();
        tiff.write('M');                     // big-endian
        tiff.write('M');
        tiff.write(0); tiff.write(42);       // the magic 42
        tiff.write(0); tiff.write(0); tiff.write(0); tiff.write(8);  // IFD0 at +8

        tiff.write(0); tiff.write(1);        // one entry
        tiff.write(0x01); tiff.write(0x12);  // tag 0x0112, Orientation
        tiff.write(0); tiff.write(3);        // type SHORT
        tiff.write(0); tiff.write(0); tiff.write(0); tiff.write(1);  // count 1
        tiff.write((orientation >> 8) & 0xFF);
        tiff.write(orientation & 0xFF);
        tiff.write(0); tiff.write(0);        // padding of the 4-byte value field
        tiff.write(0); tiff.write(0); tiff.write(0); tiff.write(0);  // no next IFD

        byte[] payload = tiff.toByteArray();
        int length = 2 + 6 + payload.length; // length field + "Exif  " + TIFF

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(jpeg[0]);                  // FF
        out.write(jpeg[1]);                  // D8
        out.write(0xFF);
        out.write(0xE1);                     // APP1
        out.write((length >> 8) & 0xFF);
        out.write(length & 0xFF);
        out.write("Exif".getBytes(StandardCharsets.US_ASCII));
        out.write(0);
        out.write(0);
        out.write(payload);
        out.write(jpeg, 2, jpeg.length - 2);
        return out.toByteArray();
    }

    private static int indexOf(byte[] haystack, byte[] needle) {
        outer:
        for (int i = 0; i <= haystack.length - needle.length; i++) {
            for (int j = 0; j < needle.length; j++) {
                if (haystack[i + j] != needle[j]) {
                    continue outer;
                }
            }
            return i;
        }
        return -1;
    }
}

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

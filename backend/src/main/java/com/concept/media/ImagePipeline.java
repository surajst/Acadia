package com.concept.media;

import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

/**
 * Turns an uploaded file into the two images we are willing to keep.
 *
 * <h2>Why the original is never stored</h2>
 *
 * <p>A photograph off a phone carries EXIF, and EXIF carries the coordinates it
 * was taken at. On a picture of a child in a classroom, that is the location of
 * the child, and it is the single most sensitive field in the file. Decoding to
 * pixels and re-encoding drops every metadata block with it -- there is no
 * copy-EXIF step in ImageIO's JPEG writer, which is exactly the behaviour
 * wanted here. So the pipeline is not "strip EXIF"; it is "keep only pixels",
 * which is a stronger statement and harder to get subtly wrong.
 *
 * <p>Orientation is the one casualty worth naming. EXIF also carries which way
 * up the camera was, and dropping it means a photo taken in portrait on some
 * phones is stored rotated. That is a visible bug and a fair trade for not
 * holding the coordinates; fixing it properly means reading the orientation tag
 * and rotating the pixels before discarding the metadata, which is worth doing
 * and is not done here yet.
 */
public final class ImagePipeline {

    /** Longest edge of the version shown on its own. */
    public static final int DISPLAY_EDGE = 1600;

    /** Longest edge of the version shown in a grid. */
    public static final int THUMB_EDGE = 400;

    /** What a caller may upload. Sniffed from the bytes, never from the request. */
    public enum Kind { JPEG, PNG }

    private ImagePipeline() {
    }

    /** A decoded, resized, metadata-free JPEG. */
    public record Rendered(byte[] bytes, int width, int height) {
    }

    /**
     * What these bytes actually are.
     *
     * <p>From the first few bytes, not from the Content-Type header and not from
     * the filename. Both of those are supplied by whoever is uploading, so
     * neither is evidence: a caller who says `image/png` and sends a zip has told
     * us nothing, and believing them is how something that is not an image ends
     * up stored as one.
     *
     * @return the kind, or null when the bytes are not an image we accept
     */
    public static Kind sniff(byte[] bytes) {
        if (bytes == null || bytes.length < 8) {
            return null;
        }
        // FF D8 FF -- every JPEG begins this way.
        if ((bytes[0] & 0xFF) == 0xFF && (bytes[1] & 0xFF) == 0xD8 && (bytes[2] & 0xFF) == 0xFF) {
            return Kind.JPEG;
        }
        // The 8-byte PNG signature. The 0D 0A and 1A bytes are there to catch
        // transfers that mangle line endings, so checking all eight is worth it.
        int[] png = {0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A};
        for (int i = 0; i < png.length; i++) {
            if ((bytes[i] & 0xFF) != png[i]) {
                return null;
            }
        }
        return Kind.PNG;
    }

    /**
     * Decode, scale so the longest edge is at most {@code maxEdge}, re-encode.
     *
     * <p>Always re-encoded, even when the image is already small enough, because
     * re-encoding is what discards the metadata. Returning the input untouched
     * for a small file would mean a small file keeps its coordinates -- the exact
     * case a phone produces after a crop.
     *
     * @throws IOException when the bytes do not decode, which {@link #sniff} will
     *                     usually have caught first
     */
    public static Rendered render(byte[] bytes, int maxEdge) throws IOException {
        BufferedImage source = ImageIO.read(new ByteArrayInputStream(bytes));
        if (source == null) {
            throw new IOException("those bytes are not an image this server can read");
        }

        int longest = Math.max(source.getWidth(), source.getHeight());
        double scale = longest > maxEdge ? (double) maxEdge / longest : 1.0;
        int width = Math.max(1, (int) Math.round(source.getWidth() * scale));
        int height = Math.max(1, (int) Math.round(source.getHeight() * scale));

        // TYPE_INT_RGB, not ARGB: the output is JPEG, which has no alpha channel.
        // Drawing a transparent PNG straight onto an ARGB canvas and encoding it
        // as JPEG turns the transparent parts black; filling white first is what
        // makes a logo with a clear background look the way it did.
        BufferedImage target = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = target.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                    RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g.setRenderingHint(RenderingHints.KEY_RENDERING,
                    RenderingHints.VALUE_RENDER_QUALITY);
            g.setColor(java.awt.Color.WHITE);
            g.fillRect(0, 0, width, height);
            g.drawImage(source, 0, 0, width, height, null);
        } finally {
            g.dispose();
        }

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        if (!ImageIO.write(target, "jpg", out)) {
            throw new IOException("no JPEG writer available");
        }
        return new Rendered(out.toByteArray(), width, height);
    }
}

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
 * <p>Orientation is the exception, and it has to be handled rather than
 * accepted. A phone does not rotate the pixels when you turn it: it stores the
 * sensor's landscape frame and writes a tag saying which way up it was. Keeping
 * only the pixels would therefore store every portrait photograph on its side.
 * So the tag is read, the pixels are turned to match, and only then is the
 * metadata dropped -- in that order, because after re-encoding there is nothing
 * left to read. See {@link ExifOrientation}.
 *
 * <h2>Memory</h2>
 *
 * <p>Sizing is the other reason the caller matters. A decoded image costs four
 * bytes a pixel whatever the file compressed to, so a 12-megapixel photograph is
 * ~48MB of heap before anything is resized, and a rotation holds a second copy.
 * The container this runs in has 1GB. Uploads are therefore capped
 * ({@link #MAX_UPLOAD_BYTES}) and handled one at a time; a batch endpoint that
 * decoded several at once would be a heap exhaustion waiting for a school with
 * a good camera.
 */
public final class ImagePipeline {

    /** Longest edge of the version shown on its own. */
    public static final int DISPLAY_EDGE = 1600;

    /** Longest edge of the version shown in a grid. */
    public static final int THUMB_EDGE = 400;

    /** What a caller may upload. Sniffed from the bytes, never from the request. */
    public enum Kind { JPEG, PNG }

    /**
     * The largest file this will read.
     *
     * <p>Not a policy about photograph quality -- it is a memory bound.
     */
    public static final int MAX_UPLOAD_BYTES = 12 * 1024 * 1024;

    /**
     * The largest image this will decode, in pixels.
     *
     * <p>{@link #MAX_UPLOAD_BYTES} alone does not bound memory, and believing it
     * did was a real hole here. Compression ratio is attacker-controlled: PNG
     * deflates a single flat colour to almost nothing, so a few kilobytes can
     * declare 20000x20000 in its header and cost 400 million pixels -- 1.6GB at
     * four bytes each -- the instant anything decodes it. The container has 1GB,
     * so that is not a slow request, it is the process dying.
     *
     * <p>40 megapixels is comfortably above any phone or DSLR a school will use
     * and far below what it takes to hurt us.
     */
    public static final long MAX_PIXELS = 40_000_000L;

    /** What a person is told when they send something that is not a photo we take. */
    public static final String NOT_A_PHOTO = "Please upload a JPEG or PNG photo";

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
     * Is this an iPhone photograph in Apple's format?
     *
     * <p>Worth recognising rather than lumping in with "unreadable", because it
     * is the commonest thing a parent will send and the answer they need is
     * different. The web picker's accept is `image/&#42;`, which on a Mac or an
     * iPhone offers .heic, and nothing converts it: expo-image-picker hands back
     * the file as picked, and the browser's own decoder cannot read it either, so
     * the client-side downscale passes the original through untouched. It arrives
     * here intact.
     *
     * <p>ISO base media format: a `ftyp` box at offset 4, then a brand. heic and
     * heix are stills; mif1 and msf1 are the generic HEIF brands Apple also
     * writes.
     */
    public static boolean isHeic(byte[] bytes) {
        if (bytes == null || bytes.length < 12) {
            return false;
        }
        if (bytes[4] != 'f' || bytes[5] != 't' || bytes[6] != 'y' || bytes[7] != 'p') {
            return false;
        }
        String brand = new String(bytes, 8, 4, java.nio.charset.StandardCharsets.US_ASCII);
        return brand.equals("heic") || brand.equals("heix")
                || brand.equals("mif1") || brand.equals("msf1")
                || brand.equals("hevc") || brand.equals("heim");
    }

    /**
     * The dimensions this file DECLARES, without decoding it.
     *
     * <p>The whole point: a reader parses the header and stops. Asking
     * ImageIO.read first and checking afterwards is the bug -- by then the
     * allocation has already happened, which is the thing being defended against.
     *
     * @return width and height, or null when no reader recognises the bytes
     */
    static long[] declaredSize(byte[] bytes) throws IOException {
        try (javax.imageio.stream.ImageInputStream in =
                     ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))) {
            if (in == null) {
                return null;
            }
            java.util.Iterator<javax.imageio.ImageReader> readers = ImageIO.getImageReaders(in);
            if (!readers.hasNext()) {
                return null;
            }
            javax.imageio.ImageReader reader = readers.next();
            try {
                reader.setInput(in);
                return new long[] {reader.getWidth(0), reader.getHeight(0)};
            } finally {
                reader.dispose();
            }
        }
    }

    /**
     * Turn the pixels to match what the EXIF tag said.
     *
     * <p>Written as a direct mapping of source pixel to destination pixel rather
     * than as an AffineTransform, because the eight cases are the entire content
     * of this method and a reader can check each one against the spec by eye. The
     * transform version is two lines shorter and impossible to review.
     *
     * <p>Orientations 5 to 8 swap width and height: those are the quarter turns,
     * and a portrait photograph from a phone is almost always 6.
     *
     * <p>Bulk array access rather than per-pixel getRGB: a 12-megapixel image is
     * twelve million calls otherwise, which turns a rotation into a visible pause
     * on the one request the user is waiting for.
     */
    static BufferedImage turnUpright(BufferedImage src, int orientation) {
        if (orientation <= ExifOrientation.NORMAL || orientation > 8) {
            return src;
        }
        int w = src.getWidth();
        int h = src.getHeight();
        boolean quarterTurn = orientation >= 5;
        int nw = quarterTurn ? h : w;
        int nh = quarterTurn ? w : h;

        int[] in = src.getRGB(0, 0, w, h, null, 0, w);
        int[] out = new int[nw * nh];

        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int dx;
                int dy;
                switch (orientation) {
                    case 2 -> { dx = w - 1 - x; dy = y; }              // mirrored
                    case 3 -> { dx = w - 1 - x; dy = h - 1 - y; }      // upside down
                    case 4 -> { dx = x;         dy = h - 1 - y; }      // mirrored, upside down
                    case 5 -> { dx = y;         dy = x; }              // transposed
                    case 6 -> { dx = h - 1 - y; dy = x; }              // quarter turn clockwise
                    case 7 -> { dx = h - 1 - y; dy = w - 1 - x; }      // transverse
                    case 8 -> { dx = y;         dy = w - 1 - x; }      // quarter turn anticlockwise
                    default -> { dx = x;        dy = y; }
                }
                out[dy * nw + dx] = in[y * w + x];
            }
        }

        BufferedImage dst = new BufferedImage(nw, nh, BufferedImage.TYPE_INT_RGB);
        dst.setRGB(0, 0, nw, nh, out, 0, nw);
        return dst;
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
        if (bytes != null && bytes.length > MAX_UPLOAD_BYTES) {
            throw new IOException("that picture is too large to process");
        }
        // An iPhone's own format, and the commonest thing that is not a JPEG. It
        // gets its own answer because "we cannot read that" tells a parent
        // nothing they can act on.
        if (isHeic(bytes)) {
            throw new IOException(NOT_A_PHOTO);
        }
        if (sniff(bytes) == null) {
            throw new IOException(NOT_A_PHOTO);
        }

        // Before decoding, which is the only moment this check is worth anything.
        long[] size = declaredSize(bytes);
        if (size == null) {
            throw new IOException(NOT_A_PHOTO);
        }
        if (size[0] <= 0 || size[1] <= 0 || size[0] * size[1] > MAX_PIXELS) {
            throw new IOException("that picture has too many pixels to process");
        }

        BufferedImage decoded = ImageIO.read(new ByteArrayInputStream(bytes));
        if (decoded == null) {
            throw new IOException("those bytes are not an image this server can read");
        }

        // Before anything else, and before the metadata is discarded.
        BufferedImage source = turnUpright(decoded, ExifOrientation.read(bytes));

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

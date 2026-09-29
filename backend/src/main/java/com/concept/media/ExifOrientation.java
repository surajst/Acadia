package com.concept.media;

/**
 * Reads the one EXIF tag we cannot afford to discard.
 *
 * <p>Phones do not rotate the pixels when you turn the camera. They store the
 * sensor's own landscape frame and write a tag saying which way up it was. Every
 * viewer is then expected to honour that tag. So a portrait photograph is, in
 * the file, a landscape image plus a note -- and a pipeline that keeps only the
 * pixels keeps the image and throws away the note, which is how an upright
 * photograph of a child ends up on its side in an album.
 *
 * <p>Hence this: read the tag, turn the pixels to match, and only then discard
 * the metadata. The rotation has to happen first, because after re-encoding
 * there is nothing left to read.
 *
 * <h2>Why this is hand-written</h2>
 *
 * <p>It is about eighty lines against a dependency, and the eighty lines are
 * confined to one tag in the first IFD. A library would be more thorough and
 * would also be a new supply-chain entry in the path that handles photographs of
 * children, for a tag whose format has not changed since 1995.
 *
 * <p>Anything unexpected returns {@link #NORMAL} rather than throwing. A file we
 * cannot parse the orientation of is still a file we can store; guessing would
 * be worse than leaving it alone, and an exception here would refuse an upload
 * over a metadata block nobody asked about.
 */
public final class ExifOrientation {

    /** Upright already, or nothing said. */
    public static final int NORMAL = 1;

    private static final int APP1 = 0xE1;
    private static final int ORIENTATION_TAG = 0x0112;

    private ExifOrientation() {
    }

    /**
     * The orientation these bytes declare, 1 to 8, or {@link #NORMAL}.
     *
     * <p>Only JPEG carries this. PNG has no EXIF, so a PNG is always 1.
     */
    public static int read(byte[] bytes) {
        if (bytes == null || bytes.length < 4) {
            return NORMAL;
        }
        // SOI. Anything else is not a JPEG and has no EXIF to find.
        if ((bytes[0] & 0xFF) != 0xFF || (bytes[1] & 0xFF) != 0xD8) {
            return NORMAL;
        }

        int i = 2;
        while (i + 4 <= bytes.length) {
            if ((bytes[i] & 0xFF) != 0xFF) {
                return NORMAL; // out of step with the segment structure
            }
            int marker = bytes[i + 1] & 0xFF;
            // Standalone markers carry no length; SOS means the entropy-coded
            // image data starts and there are no more headers to walk.
            if (marker == 0xD8 || (marker >= 0xD0 && marker <= 0xD9)) {
                i += 2;
                continue;
            }
            if (marker == 0xDA) {
                return NORMAL;
            }
            int length = ((bytes[i + 2] & 0xFF) << 8) | (bytes[i + 3] & 0xFF);
            if (length < 2 || i + 2 + length > bytes.length) {
                return NORMAL;
            }
            if (marker == APP1 && length >= 8 && startsWithExif(bytes, i + 4)) {
                return fromTiff(bytes, i + 10, i + 2 + length);
            }
            i += 2 + length;
        }
        return NORMAL;
    }

    private static boolean startsWithExif(byte[] b, int at) {
        return at + 6 <= b.length
                && b[at] == 'E' && b[at + 1] == 'x' && b[at + 2] == 'i' && b[at + 3] == 'f'
                && b[at + 4] == 0 && b[at + 5] == 0;
    }

    /**
     * Walk IFD0 looking for the orientation tag.
     *
     * @param origin where the TIFF header starts; every offset inside is
     *               relative to this, which is the detail that makes a
     *               hand-written parser go wrong if you use the file start
     */
    private static int fromTiff(byte[] b, int origin, int end) {
        if (origin + 8 > end) {
            return NORMAL;
        }
        boolean big;
        if (b[origin] == 'I' && b[origin + 1] == 'I') {
            big = false;
        } else if (b[origin] == 'M' && b[origin + 1] == 'M') {
            big = true;
        } else {
            return NORMAL;
        }
        if (u16(b, origin + 2, big) != 42) {
            return NORMAL;
        }

        long ifd = u32(b, origin + 4, big);
        long start = origin + ifd;
        if (start < origin || start + 2 > end) {
            return NORMAL;
        }
        int entries = u16(b, (int) start, big);
        for (int n = 0; n < entries; n++) {
            long entry = start + 2 + (12L * n);
            if (entry + 12 > end) {
                return NORMAL;
            }
            if (u16(b, (int) entry, big) == ORIENTATION_TAG) {
                // Type SHORT, count 1: the value sits in the first two bytes of
                // the value field rather than at an offset elsewhere.
                int value = u16(b, (int) entry + 8, big);
                return value >= 1 && value <= 8 ? value : NORMAL;
            }
        }
        return NORMAL;
    }

    private static int u16(byte[] b, int at, boolean big) {
        if (at + 2 > b.length) {
            return 0;
        }
        int hi = b[at] & 0xFF;
        int lo = b[at + 1] & 0xFF;
        return big ? (hi << 8) | lo : (lo << 8) | hi;
    }

    private static long u32(byte[] b, int at, boolean big) {
        if (at + 4 > b.length) {
            return 0;
        }
        long a = b[at] & 0xFFL;
        long c = b[at + 1] & 0xFFL;
        long d = b[at + 2] & 0xFFL;
        long e = b[at + 3] & 0xFFL;
        return big ? (a << 24) | (c << 16) | (d << 8) | e
                   : (e << 24) | (d << 16) | (c << 8) | a;
    }
}

/**
 * Shrink a picked image in the browser before it is uploaded.
 *
 * <p>On a device, `expo-image-picker` does this for us: `allowsEditing` crops to a
 * square and `quality` re-encodes, so what arrives is tens of kilobytes. On web it
 * does neither -- both options are ignored and the picker hands back the file
 * exactly as it sits on disk. A photograph off a phone or a camera is several
 * megabytes, the server refuses anything over 1.5MB, and the refusal is correct
 * but useless: there is nothing the user can do about it from the picker.
 *
 * <p>So the browser does the same job the phone does. A 512px edge is generous for
 * an avatar drawn at 92dp -- three times over even on a 3x screen -- and takes a
 * 6MB photograph to something in the tens of kilobytes.
 *
 * <p>An image that is already small is returned untouched rather than re-encoded.
 * Re-encoding a small PNG to reach the same place would lose quality for nothing,
 * and it would change bytes the caller may be entitled to expect back unaltered.
 */

/** Longest edge, in pixels, of an uploaded avatar. */
const MAX_EDGE = 512;

/** Above this, shrink even if the picture's dimensions look reasonable. */
const MAX_BYTES = 900_000;

export async function downscaleImage(blob: Blob, name: string): Promise<Blob> {
  // SVGs have no pixels to resample and canvas would rasterise them; anything
  // that is not a bitmap goes through as it is and the server decides.
  if (!blob.type.startsWith('image/') || blob.type === 'image/svg+xml') {
    return blob;
  }

  let bitmap: ImageBitmap;
  try {
    bitmap = await createImageBitmap(blob);
  } catch {
    // A file the browser cannot decode is not one we can shrink. Let it go and
    // let the server give the real answer, which will be about the content type.
    return blob;
  }

  const longest = Math.max(bitmap.width, bitmap.height);
  if (longest <= MAX_EDGE && blob.size <= MAX_BYTES) {
    bitmap.close?.();
    return blob;
  }

  const scale = Math.min(1, MAX_EDGE / longest);
  const width = Math.max(1, Math.round(bitmap.width * scale));
  const height = Math.max(1, Math.round(bitmap.height * scale));

  const canvas = document.createElement('canvas');
  canvas.width = width;
  canvas.height = height;
  const context = canvas.getContext('2d');
  if (!context) {
    bitmap.close?.();
    return blob;
  }
  context.drawImage(bitmap, 0, 0, width, height);
  bitmap.close?.();

  // The type is preserved rather than normalised to JPEG: a PNG avatar with a
  // transparent corner would come back with a black one.
  const type = blob.type === 'image/png' ? 'image/png' : 'image/jpeg';
  const shrunk = await new Promise<Blob | null>((resolve) => {
    canvas.toBlob(resolve, type, 0.82);
  });

  // Larger than what we started with is possible for a small PNG, and taking the
  // smaller of the two is the only version of this that cannot make things worse.
  if (!shrunk || shrunk.size >= blob.size) {
    return blob;
  }
  return new File([shrunk], name, { type });
}

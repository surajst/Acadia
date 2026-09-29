const zlib = require('zlib');

const { test, expect } = require('./fixtures');

/**
 * Setting a profile photo on the Expo web build.
 *
 * <h2>What went wrong, and why nothing caught it</h2>
 *
 * The upload had never been tested on web, only on a device, and the two do not
 * share a code path. Three separate web-only faults stacked up, and the visible
 * symptom of all three together was nothing at all happening:
 *
 *  1. `FormData.append('photo', { uri, name, type })` is React Native's idiom.
 *     A browser stringifies that object, so the request carried a text field
 *     reading "[object Object]" and no file. The CORS preflight passed, which is
 *     why the network tab looked healthy.
 *  2. Setting `Content-Type: multipart/form-data` by hand strips the boundary the
 *     browser would have generated, and a multipart body without a boundary
 *     cannot be parsed.
 *  3. `Alert.alert` is a no-op on React Native Web, so when the request did fail,
 *     the failure was caught, reported, and shown to nobody.
 *
 * <h2>What this asserts</h2>
 *
 * Not that a request was sent -- a request was always sent. That the photo is
 * still there after a reload, which is the thing QA found missing and the only
 * claim that requires all three faults to be fixed. The server is asked directly
 * as well as through the UI, because an avatar can show a `blob:` uri from local
 * state and look perfectly correct while nothing has been stored.
 */
test.describe('A profile photo', () => {
  test.use({ baseURL: 'http://localhost:8081' });

  const API = 'http://localhost:8080';

  // A real 1x1 PNG. Small enough to be well inside the 2MB multipart limit, and a
  // genuine image so the server's content-type check has something true to find.
  const PNG = Buffer.from(
    'iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8DwHwAFAAH/q842iQAAAABJRU5ErkJggg==',
    'base64',
  );

  /**
   * A real, valid PNG of `size` x `size` pixels, built here rather than committed.
   *
   * Noise, deliberately: a flat colour compresses to almost nothing, and the point
   * of this file is to be genuinely too large. 2000px comes out around 1.65MB --
   * over the server's own 1.5MB refusal and under the 2MB the container will
   * accept, so what gets exercised is the application's rule rather than Tomcat's.
   */
  function bigPng(size) {
    const chunk = (type, data) => {
      const length = Buffer.alloc(4);
      length.writeUInt32BE(data.length);
      const body = Buffer.concat([Buffer.from(type, 'ascii'), data]);
      const crc = Buffer.alloc(4);
      crc.writeUInt32BE(zlib.crc32(body));
      return Buffer.concat([length, body, crc]);
    };

    const ihdr = Buffer.alloc(13);
    ihdr.writeUInt32BE(size, 0);
    ihdr.writeUInt32BE(size, 4);
    ihdr[8] = 8;   // bit depth
    ihdr[9] = 2;   // truecolour RGB

    const rows = [];
    for (let y = 0; y < size; y += 1) {
      const row = Buffer.alloc(1 + size * 3);
      for (let x = 0; x < size; x += 1) {
        row[1 + x * 3] = (x * 7 + y * 13) & 255;
        row[2 + x * 3] = (x * 31 + y * 17) & 255;
        row[3 + x * 3] = (x * 11 + y * 29) & 255;
      }
      rows.push(row);
    }

    return Buffer.concat([
      Buffer.from([0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a]),
      chunk('IHDR', ihdr),
      chunk('IDAT', zlib.deflateSync(Buffer.concat(rows), { level: 1 })),
      chunk('IEND', Buffer.alloc(0)),
    ]);
  }

  /** Width from a PNG's IHDR, which is the first chunk and always at this offset. */
  function pngWidth(buffer) {
    return buffer.readUInt32BE(16);
  }

  async function login(page, username, password) {
    await page.goto('/');
    await page.getByPlaceholder('Email / Username').fill(username);
    await page.getByPlaceholder('Password').fill(password);
    await page.getByText('Log In').click();
    await page.waitForLoadState('networkidle');
    await expect(page.getByPlaceholder('Password')).toHaveCount(0, { timeout: 30000 });
  }

  /**
   * Drives expo-image-picker's web implementation, which appends a hidden
   * `input[type=file]` to the body and clicks it. Playwright's file chooser sees
   * that click, so the picked file arrives as a real `blob:` uri from
   * URL.createObjectURL -- exactly the input that broke.
   */
  async function pick(page, trigger) {
    const chooser = page.waitForEvent('filechooser', { timeout: 30000 });
    await trigger();
    const fileChooser = await chooser;
    await fileChooser.setFiles({ name: 'avatar.png', mimeType: 'image/png', buffer: PNG });
  }

  test('survives a reload, which means it reached the server', async ({ page }) => {
    await page.goto(`${API}/test/reset`, { waitUntil: 'load' });
    await login(page, 'arjun@gmail.com', 'PilotLaunchSecure2026!');

    await page.goto('/profile');
    await page.waitForLoadState('networkidle');

    // Nothing to start with, so a pass later cannot be a photo left behind by
    // another test or a previous run.
    await expect(page.getByText('Add a photo')).toBeVisible({ timeout: 30000 });

    const upload = page.waitForResponse(
      (r) => r.url().includes('/api/mobile/user/photo') && r.request().method() === 'POST',
      { timeout: 30000 },
    );
    await pick(page, () => page.getByText('Add a photo').click());

    // The status is asserted rather than merely awaited: a 4xx is a response too,
    // and the original bug produced one.
    const response = await upload;
    expect(response.status(), await response.text()).toBe(200);

    // The claim. A reload throws away every trace of the picked file, so the
    // avatar can only come back from the server.
    await page.reload();
    await page.waitForLoadState('networkidle');
    await expect(page.getByText('Remove photo')).toBeVisible({ timeout: 30000 });
    await expect(page.getByText('Add a photo')).toHaveCount(0);
  });

  test('is served back as the image that was uploaded', async ({ page, request }) => {
    await page.goto(`${API}/test/reset`, { waitUntil: 'load' });
    await login(page, 'arjun@gmail.com', 'PilotLaunchSecure2026!');

    await page.goto('/profile');
    await page.waitForLoadState('networkidle');
    await expect(page.getByText('Add a photo')).toBeVisible({ timeout: 30000 });

    const upload = page.waitForResponse(
      (r) => r.url().includes('/api/mobile/user/photo') && r.request().method() === 'POST',
      { timeout: 30000 },
    );
    await pick(page, () => page.getByText('Add a photo').click());
    expect((await upload).status()).toBe(200);

    // Fetched straight from the API with the app's own token, so this is about
    // the bytes and not about how react-native-web happens to render an Image.
    const token = await page.evaluate(() => window.localStorage.getItem('userToken'));
    expect(token, 'the app stores its token under userToken').toBeTruthy();

    const profile = await request.get(`${API}/api/mobile/user/profile`, {
      headers: { Authorization: `Bearer ${token}` },
    });
    expect(profile.ok()).toBeTruthy();
    const body = await profile.json();
    expect(body.hasPhoto, 'the profile must report the photo it just stored').toBe(true);

    const photo = await request.get(`${API}/api/mobile/user/photo/${body.userId}`, {
      headers: { Authorization: `Bearer ${token}` },
    });
    expect(photo.ok()).toBeTruthy();
    const bytes = await photo.body();

    // Byte-for-byte. A body of "[object Object]" is 15 bytes of text and would
    // have passed a mere "something came back" assertion.
    expect(bytes.length, 'the stored photo must be the file that was picked')
      .toBe(PNG.length);
    expect(bytes.equals(PNG)).toBe(true);
    expect(photo.headers()['content-type']).toContain('image/png');
  });

  /**
   * That it is actually ON SCREEN, which is not what the test above proves.
   *
   * <p>The test above fetches the photo from the API with an Authorization header
   * and checks the bytes. It passed while every user saw a blank grey circle,
   * because the screen does not get to send headers: react-native-web renders
   * `<Image>` as `<img src>`, the endpoint is Bearer-authenticated, and the browser
   * asked for it with no token and got a 401. Bytes in the database are not a
   * photograph a parent can see.
   *
   * <p>So this asserts the rendered image: that an <img> exists, that it is showing
   * something the app fetched rather than a URL the browser would have to
   * authenticate, and that the browser actually decoded it -- naturalWidth is 0 for
   * an image that failed to load, which is exactly what a 401 produces and what no
   * amount of checking the src would have caught.
   */
  test('the avatar renders the photo, not a blank circle', async ({ page }) => {
    await page.goto(`${API}/test/reset`, { waitUntil: 'load' });
    await login(page, 'arjun@gmail.com', 'PilotLaunchSecure2026!');
    await page.goto('/profile');
    await page.waitForLoadState('networkidle');
    await expect(page.getByText('Add a photo')).toBeVisible({ timeout: 30000 });

    const upload = page.waitForResponse(
      (r) => r.url().includes('/api/mobile/user/photo') && r.request().method() === 'POST',
      { timeout: 30000 },
    );
    await pick(page, () => page.getByText('Add a photo').click());
    expect((await upload).status()).toBe(200);

    // Reloaded, so what is on screen came from the server and not from the
    // picker's own blob still sitting in memory.
    await page.reload();
    await page.waitForLoadState('networkidle');
    await expect(page.getByText('Remove photo')).toBeVisible({ timeout: 30000 });

    // Asserted over every image source on the page rather than by locating the
    // avatar element.
    //
    // Three attempts went into react-native-web's DOM before this: it hides the
    // <img> on purpose for screen readers (so toBeVisible fails on a working
    // avatar), `img` first() picked a navigation back-icon, and the accessible
    // label lands on more than one nested node. None of that is what the bug was
    // about, and coupling to it made the test fragile in ways that hid the point.
    //
    // The requirement is simply: nothing asks the browser to load the
    // token-protected endpoint, and the photo the app fetched is on the page and
    // decoded. Both are answerable from the image sources alone.
    const sources = await page.evaluate(() => {
      const out = [];
      document.querySelectorAll('img').forEach((el) => out.push({
        src: el.getAttribute('src') || '',
        width: el.complete ? el.naturalWidth : 0,
      }));
      Array.from(document.querySelectorAll('*')).forEach((el) => {
        const bg = getComputedStyle(el).backgroundImage;
        if (bg && bg !== 'none') out.push({ src: bg, width: null });
      });
      return out;
    });

    // The bug: an <img src> pointed straight at a Bearer-authenticated endpoint,
    // so the browser asked with no token, got a 401, and drew nothing.
    const authenticated = sources.filter((s) => s.src.includes('/api/mobile/user/photo/'));
    expect(authenticated,
      'nothing may ask the browser to load the token-protected photo endpoint')
      .toEqual([]);

    // And the photo the app fetched is on the page, and the browser decoded it --
    // naturalWidth stays 0 for an image that failed to load.
    const decoded = sources.filter((s) => s.src.startsWith('data:image/') && s.width > 0);
    expect(decoded.length,
      `the avatar must show fetched bytes that actually decoded; saw ${JSON.stringify(sources.map((s) => s.src.slice(0, 40)))}`)
      .toBeGreaterThan(0);
  });

  /**
   * The other half of the web/native split, and the one that would have been found
   * next.
   *
   * On a device the picker honours `allowsEditing` and `quality`, so what reaches
   * the upload is already a small square. On web it honours neither -- it returns
   * the file exactly as picked -- so an ordinary photograph arrives at several
   * megabytes against a 1.5MB ceiling and is refused. Fixing the multipart body
   * alone would have moved the symptom from "nothing happens" to "every real
   * photograph is too large", which is not much of an improvement.
   */
  test('a photograph too large to store is shrunk rather than refused', async ({ page, request }) => {
    const huge = bigPng(2000);
    // Stated rather than assumed: if a future change to the generator brought this
    // under the server's limit, the test would pass without exercising anything.
    expect(huge.length, 'the fixture has to be genuinely over the 1.5MB limit')
      .toBeGreaterThan(1_500_000);

    await page.goto(`${API}/test/reset`, { waitUntil: 'load' });
    await login(page, 'arjun@gmail.com', 'PilotLaunchSecure2026!');
    await page.goto('/profile');
    await page.waitForLoadState('networkidle');
    await expect(page.getByText('Add a photo')).toBeVisible({ timeout: 30000 });

    const upload = page.waitForResponse(
      (r) => r.url().includes('/api/mobile/user/photo') && r.request().method() === 'POST',
      { timeout: 60000 },
    );
    const chooser = page.waitForEvent('filechooser', { timeout: 30000 });
    await page.getByText('Add a photo').click();
    await (await chooser).setFiles({
      name: 'holiday.png', mimeType: 'image/png', buffer: huge,
    });

    const response = await upload;
    expect(response.status(), await response.text()).toBe(200);

    const token = await page.evaluate(() => window.localStorage.getItem('userToken'));
    const profile = await (await request.get(`${API}/api/mobile/user/profile`, {
      headers: { Authorization: `Bearer ${token}` },
    })).json();
    const stored = await (await request.get(`${API}/api/mobile/user/photo/${profile.userId}`, {
      headers: { Authorization: `Bearer ${token}` },
    })).body();

    // Smaller, and specifically small enough that the server would have taken it.
    expect(stored.length, 'the picture must be shrunk before it is sent')
      .toBeLessThan(huge.length);
    expect(stored.length).toBeLessThan(1_500_000);
    // And shrunk by resampling, not by re-compressing a 2000px image harder --
    // which would still cost the phone the memory of decoding it.
    expect(pngWidth(stored), 'the stored avatar should be at most 512px wide')
      .toBeLessThanOrEqual(512);
  });
});

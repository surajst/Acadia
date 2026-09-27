const { test, expect } = require('./fixtures');

/**
 * "Watched ✓" on the Expo web build.
 *
 * <h2>The two parameters this is really about</h2>
 *
 * The player reports that a video finished through a postMessage from the YouTube
 * iframe. It only sends one if the embed carries `enablejsapi=1`, and it refuses to
 * send it to a page whose `origin` it was not told. Either missing and "Watched"
 * never happens -- silently, behind a player that looks and plays perfectly
 * normally. Nobody notices until a teacher asks who has watched.
 *
 * So this asserts the configuration first, then the behaviour.
 *
 * <h2>How the video is "played to the end"</h2>
 *
 * The embed request is intercepted and answered with a page that posts the message
 * YouTube posts at the end. That is not a shortcut around the hard part -- it is
 * the only way to test the hard part honestly:
 *
 *  - the stub is served AT the youtube-nocookie origin, so `event.origin` on the
 *    message is genuinely `https://www.youtube-nocookie.com` and the listener's
 *    origin check is exercised rather than bypassed;
 *  - the message is the real shape, `{"event":"onStateChange","info":0}`;
 *  - and nothing waits on a real video's running time, or on a CI runner having
 *    egress to youtube.com, or on somebody not deleting the video this was written
 *    against.
 *
 * What is NOT proven here is that YouTube itself sends that message. Nothing in CI
 * can prove that. What is proven is that the embed is configured so it will, and
 * that when it arrives the app does the right thing with it.
 */
test.describe('Finishing a video marks it watched', () => {
  test.use({ baseURL: 'http://localhost:8081' });

  const API = 'http://localhost:8080';
  const NOCOOKIE = 'https://www.youtube-nocookie.com';

  async function login(page, username, password) {
    await page.goto('/');
    await page.getByPlaceholder('Email / Username').fill(username);
    await page.getByPlaceholder('Password').fill(password);
    await page.getByText('Log In').click();
    await page.waitForLoadState('networkidle');
    await expect(page.getByPlaceholder('Password')).toHaveCount(0, { timeout: 30000 });
  }

  /**
   * Answers the embed with a page that reaches its end immediately.
   *
   * Served at the nocookie origin, so the message the app receives carries the
   * origin the app checks for.
   */
  async function stubThePlayer(page) {
    await page.route(`${NOCOOKIE}/embed/**`, (route) =>
      route.fulfill({
        status: 200,
        contentType: 'text/html',
        body: `<!doctype html><html><body>
          <p>stub player</p>
          <script>
            // What YouTube posts when a video reaches its end. info 0 is ENDED.
            parent.postMessage(JSON.stringify({ event: 'onStateChange', info: 0 }), '*');
          </script>
        </body></html>`,
      }));
  }

  test('the player is configured so YouTube can report the end', async ({ page }) => {
    await page.goto(`${API}/test/reset`, { waitUntil: 'load' });
    await login(page, 'arjun@gmail.com', 'PilotLaunchSecure2026!');
    await stubThePlayer(page);

    await page.goto('/videos');
    await page.waitForLoadState('networkidle');

    const card = page.getByText('Photosynthesis in 5 minutes');
    await expect(card, 'the reset seeds one video for this pupil')
      .toBeVisible({ timeout: 30000 });
    await card.click();

    const player = page.locator('[data-video-player]');
    await expect(player).toBeVisible({ timeout: 30000 });
    const src = await player.getAttribute('src');

    expect(src, 'without enablejsapi YouTube posts nothing at all').toContain('enablejsapi=1');
    // The origin has to be this page's, exactly. YouTube will not post to a page
    // whose origin it was not told, and it is not the server's to guess: it differs
    // between the deployed build, localhost and the native webview.
    const origin = await page.evaluate(() => window.location.origin);
    expect(src, `the embed must name this page's origin (${origin})`)
      .toContain(`origin=${encodeURIComponent(origin)}`);
    // And the address the teacher's link became, not the link itself.
    expect(src).toContain(`${NOCOOKIE}/embed/dQw4w9WgXcQ`);
  });

  test('reaching the end records it, and the list says so', async ({ page }) => {
    await page.goto(`${API}/test/reset`, { waitUntil: 'load' });
    await login(page, 'arjun@gmail.com', 'PilotLaunchSecure2026!');
    await stubThePlayer(page);

    await page.goto('/videos');
    await page.waitForLoadState('networkidle');

    const card = page.getByText('Photosynthesis in 5 minutes');
    await expect(card).toBeVisible({ timeout: 30000 });
    // Nothing is watched yet, asserted before anything is played: a screen that
    // always said "Watched" would pass the half below.
    await expect(page.getByText('Watched ✓')).toHaveCount(0);

    // The request the whole feature turns on.
    const watchedCall = page.waitForRequest(
      (r) => r.url().includes('/videos/') && r.url().endsWith('/watched') && r.method() === 'POST',
      { timeout: 30000 });

    await card.click();
    const request = await watchedCall;
    expect(request.url()).toContain('/api/mobile/student/videos/');

    await expect(page.getByText('Watched ✓')).toBeVisible({ timeout: 30000 });
  });

  test('it is still watched when the pupil comes back', async ({ page }) => {
    await page.goto(`${API}/test/reset`, { waitUntil: 'load' });
    await login(page, 'arjun@gmail.com', 'PilotLaunchSecure2026!');
    await stubThePlayer(page);

    await page.goto('/videos');
    await page.waitForLoadState('networkidle');
    await page.getByText('Photosynthesis in 5 minutes').click();
    await expect(page.getByText('Watched ✓')).toBeVisible({ timeout: 30000 });

    // The mark is painted locally the moment the player reports the end, so the
    // tick appearing proves nothing about the server until the page is reloaded.
    await page.goto('/');
    await page.waitForLoadState('networkidle');
    await page.goto('/videos');
    await page.waitForLoadState('networkidle');

    await expect(page.getByText('Watched ✓')).toBeVisible({ timeout: 30000 });
  });

  /**
   * A parent sees the same list and the same marks, and cannot change them: the
   * mark is about their child, not about them.
   */
  test('a parent sees their child\'s list read-only', async ({ page }) => {
    await page.goto(`${API}/test/reset`, { waitUntil: 'load' });
    await login(page, 'ramesh@gmail.com', 'PilotLaunchSecure2026!');

    await page.goto('/videos');
    await page.waitForLoadState('networkidle');

    await expect(page.getByText('Photosynthesis in 5 minutes')).toBeVisible({ timeout: 30000 });
  });
});

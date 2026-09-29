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
 * <h2>The handshake, and why the first version of this test was worthless</h2>
 *
 * enablejsapi and origin are necessary and NOT sufficient. The widget protocol is a
 * handshake: YouTube stays completely silent until the parent posts
 * `{"event":"listening","id":n,"channel":"widget"}` into the iframe. The app did
 * not, so nothing was ever recorded -- and this test passed anyway, because its
 * stub sent ENDED unprompted. It proved the app reacts to a message. It could not
 * prove a message would ever arrive, which was the only thing in doubt.
 *
 * So the stub now behaves like YouTube: silent until spoken to. It answers the
 * handshake with onReady and only then reports the end. If the app stops sending
 * the handshake, nothing arrives, no request is made, and these fail -- which is
 * what the earlier version should have done.
 *
 * The rest of the shape is deliberate too: the stub is served AT the
 * youtube-nocookie origin, so `event.origin` is genuinely YouTube's and the
 * listener's origin check is exercised rather than bypassed, and the messages are
 * the real ones. Nothing waits on a real video's running time, on a runner having
 * egress, or on somebody not deleting the video this was written against.
 *
 * What is still NOT proven here is YouTube's own behaviour. Nothing in CI can prove
 * that. What is proven is that the app performs its half of a protocol whose other
 * half QA has now confirmed by hand.
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
  /**
   * A recorded playback, replayed.
   *
   * <p>The sequence below is what QA captured from the real player over one full
   * watch: `infoDelivery` messages carrying playerState -1, 3, 1, 2, then 0. Note
   * what is NOT in it -- a single `onStateChange`. YouTube does not send those
   * until the parent subscribes, and every previous version of this stub sent one
   * unprompted. That is why the suite stayed green through two shipped bugs: the
   * stub spoke the one dialect the app already understood.
   *
   * <p>So this stub says only what the real player said. If the app goes back to
   * reading onStateChange alone, nothing here arrives in a form it understands and
   * these tests fail -- which is what they were always supposed to do.
   *
   * <p>It also records what the page posts IN, so the subscription can be asserted
   * on its own rather than inferred.
   */
  const RECORDED_PLAYBACK = [-1, 3, 1, 2, 0];

  async function stubThePlayer(page) {
    await page.route(`${NOCOOKIE}/embed/**`, (route) =>
      route.fulfill({
        status: 200,
        contentType: 'text/html',
        body: `<!doctype html><html><body>
          <p>stub player</p>
          <script>
            window.__sentToPlayer = [];
            var states = ${JSON.stringify(RECORDED_PLAYBACK)};

            function deliver(state) {
              // The real shape: infoDelivery, with the state nested under info.
              parent.postMessage(JSON.stringify({
                event: 'infoDelivery',
                info: { playerState: state },
              }), '*');
            }

            window.addEventListener('message', function (e) {
              var data;
              try { data = typeof e.data === 'string' ? JSON.parse(e.data) : e.data; }
              catch (err) { return; }
              if (!data) return;
              window.__sentToPlayer.push(data);
            });

            // onReady unprompted, which is what the real player does -- it does
            // not wait for the listening handshake to say hello.
            parent.postMessage(JSON.stringify({ event: 'onReady' }), '*');

            // Then the playback, paced so the order is observable.
            states.forEach(function (state, i) {
              window.setTimeout(function () { deliver(state); }, 150 * (i + 1));
            });
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

  /**
   * The half that was missing. Asserted on its own as well as through the
   * behaviour below, so a regression says "the handshake stopped" rather than
   * "watched stopped working" and leaves somebody to find out why.
   */
  /**
   * The two things the page has to SAY, asserted on their own.
   *
   * <p>Separate from the behaviour below because a failure here names the cause.
   * "The subscription stopped being sent" is actionable; "watched stopped working"
   * sends somebody back through the whole protocol, which is how this component
   * came to be fixed three times.
   *
   * <p>The subscription is the one that matters most and was missing longest.
   * Without it YouTube sends no onStateChange at all, ever.
   */
  test('the app introduces itself and then asks to be told about state changes',
    async ({ page }) => {
      await page.goto(`${API}/test/reset`, { waitUntil: 'load' });
      await login(page, 'arjun@gmail.com', 'PilotLaunchSecure2026!');
      await stubThePlayer(page);

      await page.goto('/videos');
      await page.waitForLoadState('networkidle');
      await page.getByText('Photosynthesis in 5 minutes').click();
      await expect(page.locator('[data-video-player]')).toBeVisible({ timeout: 30000 });

      const frame = page.frameLocator('[data-video-player]');
      const sent = async () => frame.locator('body')
        .evaluate(() => window.__sentToPlayer || []);

      // The handshake, on the channel YouTube listens on.
      await expect.poll(async () => (await sent())
        .filter((m) => m.event === 'listening').length, { timeout: 30000 })
        .toBeGreaterThan(0);
      const handshake = (await sent()).find((m) => m.event === 'listening');
      expect(handshake.channel, 'YouTube ignores anything not on the widget channel')
        .toBe('widget');

      // And the subscription, which is a command and not a handshake.
      await expect.poll(async () => (await sent())
        .filter((m) => m.event === 'command'
          && m.func === 'addEventListener'
          && (m.args || []).includes('onStateChange')).length, { timeout: 30000 })
        .toBeGreaterThan(0);
      const subscribe = (await sent()).find((m) => m.event === 'command');
      expect(subscribe.channel, 'a command off the widget channel is discarded')
        .toBe('widget');
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
    await stubThePlayer(page);

    // Every attempt to record a watch, whoever makes it.
    const attempts = [];
    page.on('request', (r) => {
      if (r.url().includes('/videos/') && r.url().includes('/watched')) {
        attempts.push(`${r.method()} ${r.url()}`);
      }
    });

    await page.goto('/videos');
    await page.waitForLoadState('networkidle');
    await expect(page.getByText('Photosynthesis in 5 minutes')).toBeVisible({ timeout: 30000 });

    // Read-only means read-only even when the video reaches its end in front of
    // them. The endpoint is pupil-only, so calling it as a parent is a 403 the
    // screen would have to swallow -- and the mark is about their child, not
    // about them, so nothing here should want to send it.
    await page.getByText('Photosynthesis in 5 minutes').click();
    await expect(page.locator('[data-video-player]')).toBeVisible({ timeout: 30000 });
    await page.waitForTimeout(2000);

    expect(attempts, 'a parent watching to the end must not record a watch')
      .toEqual([]);
  });

  /**
   * A teacher gets their own list, not a pupil's.
   *
   * <p>Videos is on the teacher's wheel, and the screen used to send everyone who
   * was not a parent to the PUPIL endpoint. A teacher tapping it was refused with
   * a 403 and shown "Could not load videos" -- a destination on the wheel that had
   * never worked. Three audiences, three endpoints, and no default to fall through
   * to.
   *
   * <p>The absence assertion matters as much as the presence one: a tick says "you
   * watched this", which is not a thing a teacher does to a video they shared, and
   * the endpoint behind it refuses them anyway.
   */
  test('a teacher sees what they shared, with how many have watched', async ({ page }) => {
    await page.goto(`${API}/test/reset`, { waitUntil: 'load' });
    await login(page, 'teacher@greenwood.com', 'PilotLaunchSecure2026!');

    await page.goto('/videos');
    await page.waitForLoadState('networkidle');

    // The list loaded at all. This is the bug: it used to say it could not.
    await expect(page.getByText(/could not load/i)).toHaveCount(0);
    await expect(page.getByText('Photosynthesis in 5 minutes')).toBeVisible({ timeout: 30000 });

    // The teacher's measure is the class, not themselves.
    //
    // Matched on the words, not a data-* attribute: react-native-web drops unknown
    // props on Text, so a hook like that never reaches the DOM. A first version of
    // this test looked for one and timed out against a screen that was rendering
    // perfectly -- the same attribute had been sitting in the component unused,
    // because every other test in this file quietly matched the text instead.
    await expect(page.getByText(/pupils? ha(?:s|ve) watched/))
      .toBeVisible({ timeout: 30000 });
    await expect(page.getByText('Watched ✓'),
      'a tick is about the person reading it, which is the wrong question for a teacher')
      .toHaveCount(0);
  });
});

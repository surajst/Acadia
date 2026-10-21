const { test, expect } = require('./fixtures');

/**
 * Messages the user can actually see on the web build.
 *
 * <h2>What went wrong for weeks</h2>
 *
 * <p>`Alert.alert` is a no-op on React Native Web. It does not throw and does not
 * warn: it returns, and nothing appears. Twenty-seven call sites reported
 * failures through it, so on the build QA and the pilot parents use, those
 * failures were caught, formatted, and shown to nobody. A profile photo upload
 * failed silently. An assessment failed to be created and the gradebook carried
 * on as though it had, which is still confusing a test.
 *
 * <p>Nothing caught it because a test that does not look for a message passes
 * whether or not one appears. So these look.
 *
 * <h2>Assessment creation, specifically</h2>
 *
 * <p>The first one converted, because it is the one with a real bug behind it:
 * the gradebook's "Could not create assessment" went to an Alert, so a teacher
 * whose assessment failed saw the panel sitting on a different, pre-seeded
 * assessment and no explanation at all.
 */
test.describe('Messages reach the user on the web build', () => {
  test.use({ baseURL: 'http://localhost:8081' });

  const API = 'http://localhost:8080';

  async function login(page, username, password) {
    await page.goto('/');
    await page.getByPlaceholder('Email / Username').fill(username);
    await page.getByPlaceholder('Password').fill(password);
    await page.getByText('Log In').click();
    await page.waitForLoadState('networkidle');
    await expect(page.getByPlaceholder('Password')).toHaveCount(0, { timeout: 30000 });
  }

  /**
   * Put one item in the teacher's verification queue.
   *
   * <p>Signed in as the pupil over the API rather than driven through the UI:
   * the point of the test below is the dialog, and walking a pupil through the
   * syllabus screen to get there would make it fail for reasons that have
   * nothing to do with dialogs.
   */
  async function createSomethingToVerify(request) {
    const login = await request.post(`${API}/api/mobile/auth/login`, {
      data: { email: 'arjun@gmail.com', password: process.env.DEMO_ADMIN_PASSWORD },
    });
    expect(login.ok(), 'the pupil has to be able to sign in').toBeTruthy();
    const token = (await login.json()).token;
    const auth = { Authorization: `Bearer ${token}` };

    const syllabus = await request.get(`${API}/api/mobile/student/syllabus`, { headers: auth });
    expect(syllabus.ok()).toBeTruthy();
    const body = await syllabus.json();

    // The shape varies by subject grouping, so find the first thing carrying an
    // id rather than assuming a path through it.
    const ids = [];
    const walk = (node) => {
      if (Array.isArray(node)) return node.forEach(walk);
      if (node && typeof node === 'object') {
        if (typeof node.id === 'string' && node.topicName) ids.push(node.id);
        return Object.values(node).forEach(walk);
      }
      return undefined;
    };
    walk(body);
    expect(ids.length, 'the syllabus needs at least one topic to mark complete')
      .toBeGreaterThan(0);

    const marked = await request.post(`${API}/api/student/progress/complete`, {
      headers: auth, data: { curriculumId: ids[0] },
    });
    expect(marked.ok(), await marked.text()).toBeTruthy();
  }

  test('a failed assessment creation says so, instead of failing silently',
    async ({ page }) => {
      await page.goto(`${API}/test/reset`, { waitUntil: 'load' });
      await login(page, 'teacher@greenwood.com', process.env.DEMO_ADMIN_PASSWORD);

      // Refuse the create. Anything the server could answer with -- this stands
      // in for the 4xx a real failure produces.
      await page.route('**/api/teacher/assessments/create', (route) => {
        if (route.request().method() === 'POST') {
          return route.fulfill({
            status: 400,
            contentType: 'application/json',
            body: JSON.stringify({ error: 'refused for the purposes of this test' }),
          });
        }
        return route.continue();
      });

      await page.goto('/gradebook');
      await page.waitForLoadState('networkidle');

      const title = page.getByLabel('New assessment title');
      await expect(title).toBeVisible({ timeout: 30000 });
      await title.fill(`PW Notice ${Date.now()}`);
      await page.getByRole('button', { name: 'Create assessment' }).click();

      // The claim: the failure is on the screen, beside the button that was
      // pressed, and carries the server's reason. Before this change the request
      // failed, the catch ran, Alert.alert did nothing, and the teacher was
      // given no reason at all.
      await page.waitForLoadState('networkidle');
      await expect(page.getByRole('alert')
        .filter({ hasText: /could not create assessment: refused for the purposes of this test/i }))
        .toBeVisible({ timeout: 30000 });
    });

  /**
   * An error stays put.
   *
   * <p>Not a detail: a message that fades before it is read is the same failure
   * as one that never appeared, and the reader here may be a parent on a phone
   * browser reading at their own pace.
   *
   * <p>Driven through the verification queue's "Could not load", which goes
   * through notify() -- the gradebook's create failure is shown in the page
   * now, beside its button, so it no longer exercises the notice host.
   */
  test('an error notice waits to be dismissed, and then goes', async ({ page }) => {
    // It waits six seconds on purpose, on top of a reset and a sign-in.
    test.setTimeout(60000);
    await page.goto(`${API}/test/reset`, { waitUntil: 'load' });
    await login(page, 'teacher@greenwood.com', process.env.DEMO_ADMIN_PASSWORD);

    // 400, not 500: on the dev server a 5xx also wakes Expo's log overlay,
    // whose own requests keep the network from ever going idle.
    await page.route('**/api/teacher/queue', (route) => route.fulfill({
      status: 400,
      contentType: 'application/json',
      body: JSON.stringify({ error: 'queue refused for the purposes of this test' }),
    }));

    // Waits on the refused response rather than on networkidle: with this
    // route in place the dashboard behind the stack keeps requests open and the
    // network never goes idle. This is a page load, not a form submission.
    const refused = page.waitForResponse((r) => r.url().endsWith('/api/teacher/queue')
      && r.request().method() === 'GET');
    await page.goto('/verification');
    await refused;

    const notice = page.getByText('queue refused for the purposes of this test');
    await expect(notice).toBeVisible({ timeout: 30000 });
    await expect(page.getByText('Could not load')).toBeVisible();

    // Well past the fade used for successes.
    await page.waitForTimeout(6000);
    await expect(notice, 'an error must not disappear on its own').toBeVisible();

    await page.getByRole('button', { name: 'Dismiss: Could not load' }).click();
    await expect(notice).toHaveCount(0);
  });

  /**
   * The question dialog, from the keyboard alone.
   *
   * <p>It blocks, so it has to be answerable without a mouse -- and Escape has to
   * mean no. These are used for irreversible things: sending a child's work back
   * as needing more work is not something an accidental dismissal may do.
   */
  test('a question can be cancelled with Escape, and Escape means no',
    async ({ page, request }) => {
      await page.goto(`${API}/test/reset`, { waitUntil: 'load' });

      // Make the thing to verify, rather than hoping the seed has one.
      //
      // /test/reset empties both queues, so this test used to skip -- and a
      // skipped test is a test that proves nothing, which is the whole failure
      // mode this file exists to close. A pupil marking a topic complete is
      // exactly what puts a row in front of the teacher.
      await createSomethingToVerify(request);

      await login(page, 'teacher@greenwood.com', process.env.DEMO_ADMIN_PASSWORD);

      // Watch for the decision ever being sent.
      const decisions = [];
      page.on('request', (r) => {
        if (/\/(progress|milestone)\/.*\/(decide|decision)/i.test(r.url())
            || (r.method() === 'POST' && /verif|decide/i.test(r.url()))) {
          decisions.push(`${r.method()} ${r.url()}`);
        }
      });

      await page.goto('/verification');
      await page.waitForLoadState('networkidle');

      // "Decline" is the row's button; "Send back" is the dialog's confirm
      // label. They are deliberately different words and it is easy to reach for
      // the wrong one here.
      //
      // By role and exact name: getByText('Decline') also matches the header's
      // "Award or decline before it sits any longer", which comes first -- so
      // the click landed on a subtitle, nothing opened, and that is what was
      // recorded as "ask() does not open on web".
      const decline = page.getByRole('button', { name: 'Decline', exact: true }).first();
      await expect(decline,
        'the queue must have something in it, or this test asserts nothing')
        .toBeVisible({ timeout: 30000 });
      await decline.click();

      const dialog = page.getByText('Send this back?');
      await expect(dialog).toBeVisible({ timeout: 30000 });

      await page.keyboard.press('Escape');
      await expect(dialog, 'Escape has to close it').toHaveCount(0);
      expect(decisions, 'dismissing a question must never be read as consent')
        .toEqual([]);
    });

  /**
   * And the other answer: confirming does what it says.
   *
   * <p>Escape-means-no alone would pass for a dialog whose confirm button did
   * nothing at all. ask() has to hand back the user's choice, both ways.
   */
  test('confirming the question sends the work back', async ({ page, request }) => {
    await page.goto(`${API}/test/reset`, { waitUntil: 'load' });
    await createSomethingToVerify(request);
    await login(page, 'teacher@greenwood.com', process.env.DEMO_ADMIN_PASSWORD);

    await page.goto('/verification');
    await page.waitForLoadState('networkidle');

    const decline = page.getByRole('button', { name: 'Decline', exact: true }).first();
    await expect(decline).toBeVisible({ timeout: 30000 });
    await decline.click();
    await expect(page.getByText('Send this back?')).toBeVisible({ timeout: 30000 });

    const decided = page.waitForRequest((r) => r.method() === 'POST'
      && /\/api\/teacher\/(progress|milestone)\/reject/.test(r.url()));
    await page.getByRole('button', { name: 'Send back', exact: true }).click();
    const sent = await decided;
    expect(sent.url()).toMatch(/reject/);

    await page.waitForLoadState('networkidle');
    await expect(page.getByText('Send this back?')).toHaveCount(0);
    // The only item was sent back, so nothing is left to decline.
    await expect(page.getByRole('button', { name: 'Decline', exact: true })).toHaveCount(0);
  });
});

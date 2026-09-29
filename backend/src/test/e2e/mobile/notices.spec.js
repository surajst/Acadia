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
      data: { email: 'arjun@gmail.com', password: 'PilotLaunchSecure2026!' },
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
      await login(page, 'teacher@greenwood.com', 'PilotLaunchSecure2026!');

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

      // The claim: the failure is on the screen. Before this change the request
      // failed, the catch ran, Alert.alert did nothing, and the teacher was
      // given no reason at all.
      await expect(page.getByText(/could not create assessment/i))
        .toBeVisible({ timeout: 30000 });
    });

  /**
   * An error stays put.
   *
   * <p>Not a detail: a message that fades before it is read is the same failure
   * as one that never appeared, and the reader here may be a parent on a phone
   * browser reading at their own pace.
   */
  test('an error notice waits to be dismissed, and then goes', async ({ page }) => {
    await page.goto(`${API}/test/reset`, { waitUntil: 'load' });
    await login(page, 'teacher@greenwood.com', 'PilotLaunchSecure2026!');

    await page.route('**/api/teacher/assessments/create', (route) => (
      route.request().method() === 'POST'
        ? route.fulfill({ status: 400, contentType: 'application/json', body: '{}' })
        : route.continue()));

    await page.goto('/gradebook');
    await page.waitForLoadState('networkidle');
    await page.getByLabel('New assessment title').fill(`PW Stay ${Date.now()}`);
    await page.getByRole('button', { name: 'Create assessment' }).click();

    const notice = page.getByText(/could not create assessment/i);
    await expect(notice).toBeVisible({ timeout: 30000 });

    // Well past the fade used for successes.
    await page.waitForTimeout(6000);
    await expect(notice, 'an error must not disappear on its own').toBeVisible();

    await page.getByRole('button', { name: /^Dismiss:/ }).first().click();
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

      await login(page, 'teacher@greenwood.com', 'PilotLaunchSecure2026!');

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
      const decline = page.getByText('Decline').first();
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
});

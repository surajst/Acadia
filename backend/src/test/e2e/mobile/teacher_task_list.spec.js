const { test, expect } = require('./fixtures');

/**
 * R3-P2-1. A closed task looked exactly like a live one in the teacher's own
 * task list on the phone.
 *
 * That list is the last place a closed task is visible at all -- it comes off
 * every pupil's list and refuses hand-ins -- so it is the only screen that can
 * answer "did that close work?" and "which one did I close by mistake?". It
 * answered neither: every row drew the same type badge, the same title, and a
 * dot coloured by task *type*.
 *
 * The cause was a field name. The screen declared `status`, and the endpoint
 * returns TeacherTask rows straight out of JPA, where the column is `taskStatus`
 * -- so the value was always undefined and the closed branch could never be
 * reached. Worth noting because it is invisible in TypeScript: the field was
 * optional, so reading a name the server never sends type-checks perfectly.
 */
test.describe("A teacher's task list on the phone", () => {
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
   * Closes a task through the API, because the phone has no close control -- that
   * lives in the web console. The absolute host matters: a relative URL would
   * resolve against Expo on :8081 and quietly do nothing.
   */
  async function closeFirstTask(page) {
    return page.evaluate(async (api) => {
      const token = window.localStorage.getItem('userToken');
      const headers = { Authorization: `Bearer ${token}`, 'Content-Type': 'application/json' };
      const listed = await fetch(`${api}/api/teacher/tasks/my-tasks`, { headers });
      const tasks = await listed.json();
      const rows = Array.isArray(tasks) ? tasks : (tasks.value ?? []);
      const open = rows.find((t) => t.taskStatus !== 'CLOSED');
      if (!open) return null;
      const closed = await fetch(`${api}/api/teacher/tasks/${open.id}/close`, {
        method: 'POST', headers,
      });
      return closed.ok ? open.title : null;
    }, API);
  }

  test('a closed task is marked closed, and an open one is not', async ({ page }) => {
    await page.goto(`${API}/test/reset`, { waitUntil: 'load' });
    await login(page, 'teacher@greenwood.com', 'PilotLaunchSecure2026!');

    await page.goto('/tasks');
    await page.waitForLoadState('networkidle');

    // Nothing is closed yet, so the badge must be absent. Asserted before
    // closing anything: a screen that always drew CLOSED would pass the half
    // below and be just as wrong.
    await expect(page.getByText('CLOSED', { exact: true })).toHaveCount(0);

    const title = await closeFirstTask(page);
    test.skip(!title, 'no task is set for this teacher in the seed, so none can be closed');

    await page.goto('/tasks');
    await page.waitForLoadState('networkidle');

    await expect(page.getByText('CLOSED', { exact: true }).first())
      .toBeVisible({ timeout: 30000 });
    // And the task it belongs to is still legible -- half of why a teacher is on
    // this screen is to find the one they closed by mistake.
    await expect(page.getByText(title, { exact: false }).first()).toBeVisible();
  });

  test('closing one task does not mark the others closed', async ({ page }) => {
    await page.goto(`${API}/test/reset`, { waitUntil: 'load' });
    await login(page, 'teacher@greenwood.com', 'PilotLaunchSecure2026!');

    await page.goto('/tasks');
    await page.waitForLoadState('networkidle');
    const before = await page.getByText(/^(HOMEWORK|PRACTICE|PROJECT|READING)$/).count();
    test.skip(before < 2, 'needs at least two tasks in the seed to tell them apart');

    const title = await closeFirstTask(page);
    test.skip(!title, 'nothing could be closed');

    await page.goto('/tasks');
    await page.waitForLoadState('networkidle');

    // Exactly one badge, not one per row.
    await expect(page.getByText('CLOSED', { exact: true })).toHaveCount(1);
    // And no row went missing: this list is every task the teacher has set,
    // whatever its state.
    expect(await page.getByText(/^(HOMEWORK|PRACTICE|PROJECT|READING)$/).count()).toBe(before);
  });
});

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
 *
 * <h2>Why these tests set up their own tasks</h2>
 *
 * The first version read whatever /test/reset had seeded. The anchor added later --
 * requiring a task row on screen before asserting anything about the rows -- failed
 * immediately: this list is empty after a reset, because the seeded tasks do not
 * reach it.
 *
 * That is exactly the vacuity the anchor was added to catch. Without it, "no CLOSED
 * badge is showing" was passing on an empty screen and the second test was quietly
 * skipping, so the whole file was green and testing nothing.
 *
 * Each test now creates what it needs through the API -- which is also the only way
 * to close a task from here, since the phone has no close control. That lives in
 * the web console.
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
   * Sets a task for the teacher's own first class, the way the app does.
   *
   * <p>subjectCode carries the assignment's subject *name* ("Mathematics"), which is
   * what app/task-new.tsx sends when it cannot match the catalogue, and which the
   * server accepts -- it compares the shape of a code against a name.
   *
   * <p>Returns the title on success and the server's own words on failure, so a
   * refusal shows up in the assertion rather than as a mystery empty list.
   */
  async function setTask(page, title) {
    return page.evaluate(async ({ api, taskTitle }) => {
      const token = window.localStorage.getItem('userToken');
      const headers = { Authorization: `Bearer ${token}`, 'Content-Type': 'application/json' };
      const classes = await (await fetch(`${api}/api/teacher/classes`, { headers })).json();
      const mine = Array.isArray(classes) ? classes[0] : null;
      if (!mine) return 'no class is assigned to this teacher';
      const due = new Date(Date.now() + 6 * 86400000).toISOString().slice(0, 10);
      const resp = await fetch(`${api}/api/teacher/tasks/create`, {
        method: 'POST',
        headers,
        body: JSON.stringify({
          title: taskTitle,
          description: 'Set by Playwright',
          subjectCode: mine.subject,
          taskType: 'HOMEWORK',
          assignedToClass: true,
          classSectionId: mine.id,
          standard: mine.standard,
          xpReward: 20,
          dueDate: due,
        }),
      });
      return resp.ok ? taskTitle : `refused ${resp.status}: ${await resp.text()}`;
    }, { api: API, taskTitle: title });
  }

  /** Closes a named task through the API, since the phone has no control for it. */
  async function closeTaskNamed(page, title) {
    return page.evaluate(async ({ api, taskTitle }) => {
      const token = window.localStorage.getItem('userToken');
      const headers = { Authorization: `Bearer ${token}`, 'Content-Type': 'application/json' };
      const listed = await (await fetch(`${api}/api/teacher/tasks/my-tasks`, { headers })).json();
      const rows = Array.isArray(listed) ? listed : (listed.value ?? []);
      const target = rows.find((t) => t.title === taskTitle);
      if (!target) return 'that task is not in my-tasks';
      const closed = await fetch(`${api}/api/teacher/tasks/${target.id}/close`, {
        method: 'POST', headers,
      });
      return closed.ok ? 'closed' : `refused ${closed.status}`;
    }, { api: API, taskTitle: title });
  }

  const typeBadges = (page) => page.getByText(/^(HOMEWORK|PRACTICE|PROJECT|READING)$/);

  test('a closed task is marked closed, and an open one is not', async ({ page }) => {
    await page.goto(`${API}/test/reset`, { waitUntil: 'load' });
    await login(page, 'teacher@greenwood.com', 'PilotLaunchSecure2026!');

    expect(await setTask(page, 'QA P18 Close Me'),
      'the rest of this test is about a task, so one has to exist')
      .toBe('QA P18 Close Me');

    await page.goto('/tasks');
    await page.waitForLoadState('networkidle');

    // A list has to be on screen before its contents mean anything.
    await expect(typeBadges(page).first()).toBeVisible({ timeout: 30000 });
    await expect(page.getByText('QA P18 Close Me')).toBeVisible();

    // Nothing is closed yet, so the badge must be absent. Asserted before closing
    // anything: a screen that always drew CLOSED would pass the half below and be
    // just as wrong.
    await expect(page.getByText('CLOSED', { exact: true })).toHaveCount(0);

    expect(await closeTaskNamed(page, 'QA P18 Close Me')).toBe('closed');

    await page.goto('/tasks');
    await page.waitForLoadState('networkidle');

    await expect(page.getByText('CLOSED', { exact: true }).first())
      .toBeVisible({ timeout: 30000 });
    // And the task it belongs to is still legible -- half of why a teacher is on
    // this screen is to find the one they closed by mistake.
    await expect(page.getByText('QA P18 Close Me')).toBeVisible();
  });

  test('closing one task does not mark the others closed', async ({ page }) => {
    await page.goto(`${API}/test/reset`, { waitUntil: 'load' });
    await login(page, 'teacher@greenwood.com', 'PilotLaunchSecure2026!');

    expect(await setTask(page, 'QA P18 One')).toBe('QA P18 One');
    expect(await setTask(page, 'QA P18 Two')).toBe('QA P18 Two');

    await page.goto('/tasks');
    await page.waitForLoadState('networkidle');
    await expect(typeBadges(page).first()).toBeVisible({ timeout: 30000 });
    const before = await typeBadges(page).count();
    expect(before, 'both tasks should be listed').toBeGreaterThanOrEqual(2);

    expect(await closeTaskNamed(page, 'QA P18 One')).toBe('closed');

    await page.goto('/tasks');
    await page.waitForLoadState('networkidle');
    await expect(typeBadges(page).first()).toBeVisible({ timeout: 30000 });

    // Exactly one badge, not one per row.
    await expect(page.getByText('CLOSED', { exact: true })).toHaveCount(1);
    // And no row went missing: this list is every task the teacher has set,
    // whatever its state.
    expect(await typeBadges(page).count()).toBe(before);
    await expect(page.getByText('QA P18 Two')).toBeVisible();
  });
});

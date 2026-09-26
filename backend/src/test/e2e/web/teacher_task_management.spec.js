const { test, expect } = require('@playwright/test');

// The teacher's task list was read-only: a typo lived for the life of the task,
// a finished task stayed on every child's list, and a teacher who set work had
// no way to see who had done it. TaskManagementTest owns the rules (ownership,
// the XP bounds on an edit, refusing to delete work already handed in); this
// covers the five actions as a teacher actually reaches them, per the
// feature-coverage rule, plus one assertion against each new endpoint.

async function loginAsTeacher(page) {
  await page.goto('/login');
  await page.fill('#username', 'teacher@greenwood.com');
  await page.fill('#password', 'PilotLaunchSecure2026!');
  await page.click('button[type="submit"]');
  await page.waitForURL(url => url.pathname.includes('/web/') && !url.pathname.includes('/login'),
    { timeout: 90000 });
}

async function createTask(page, title) {
  await page.goto('/web/teacher/tasks');
  await page.waitForLoadState('networkidle');
  await page.fill('#taskTitle', title);
  // By code, not by index. The picker now shows only the subjects this teacher
  // is assigned to teach for the chosen class, so index 1 was Science when the
  // whole catalogue was listed and does not exist now -- and the server would
  // refuse it either way.
  await page.selectOption('#subjectType', 'MATHEMATICS');
  await page.selectOption('#taskType', 'HOMEWORK');
  await page.selectOption('#classSectionId', { label: 'Grade 6 - A' });
  await page.locator('input[name="assignedToClass"][value="true"]').check();
  await page.fill('#xpReward', '30');
  await page.click('button:has-text("Assign Task")');
  const row = page.locator('#tasksTableBody tr', { hasText: title });
  await expect(row).toBeVisible({ timeout: 30000 });
  return row;
}

test.describe('Managing a task after it is set', () => {
  test.setTimeout(180000);

  test('a teacher can correct a task they have set', async ({ page }) => {
    await page.goto('/test/reset');
    await loginAsTeacher(page);
    const row = await createTask(page, 'QA P15 Edit Me');

    await row.locator('button[data-task-edit]').click();
    await expect(page.locator('#editTaskModal')).toBeVisible();
    // The modal opens with what the task says, not empty.
    await expect(page.locator('#editTitle')).toHaveValue('QA P15 Edit Me');

    await page.fill('#editTitle', 'QA P15 Corrected');
    await page.fill('#editXpReward', '45');
    await page.click('button[data-save-task]');

    await expect(page.locator('#editTaskModal')).not.toBeVisible({ timeout: 30000 });
    await expect(page.locator('[data-task-notice]')).toContainText(/updated/i);
    await expect(page.locator('#tasksTableBody tr', { hasText: 'QA P15 Corrected' })).toBeVisible();
  });

  test('closing a task marks it closed and reopening puts it back', async ({ page }) => {
    await page.goto('/test/reset');
    await loginAsTeacher(page);
    const row = await createTask(page, 'QA P15 Close Me');

    // Close asks now (R3-P2-6). Playwright dismisses dialogs unless told
    // otherwise, so without this the task would simply stay open.
    page.once('dialog', dialog => dialog.accept());
    await row.locator('button[data-task-close]').click();
    const closed = page.locator('#tasksTableBody tr', { hasText: 'QA P15 Close Me' });
    await expect(closed).toContainText('CLOSED', { timeout: 30000 });

    // Reopen is offered in place of Close, not alongside it.
    await expect(closed.locator('button[data-task-close]')).toHaveCount(0);
    // And reopening asks nothing. It is the undo, and putting a confirmation in
    // front of the recovery from a confirmed action is how people stop reading
    // them. No dialog handler here, so an unexpected prompt fails this.
    await closed.locator('button[data-task-reopen]').click();
    await expect(page.locator('#tasksTableBody tr', { hasText: 'QA P15 Close Me' }))
      .toContainText('ACTIVE', { timeout: 30000 });
  });

  /**
   * R3-P2-6. Close had no confirmation while Delete did -- and Close is the one
   * a teacher reaches for far more often, from a small button wedged between
   * "Who handed in" and "Delete". It takes the task off every pupil's list and
   * stops them handing it in.
   */
  test('backing out of the close confirmation leaves the task open', async ({ page }) => {
    await page.goto('/test/reset');
    await loginAsTeacher(page);
    const row = await createTask(page, 'QA P16 Close Cancelled');

    page.once('dialog', dialog => dialog.dismiss());
    await row.locator('button[data-task-close]').click();
    await page.waitForLoadState('networkidle');

    await expect(page.locator('#tasksTableBody tr', { hasText: 'QA P16 Close Cancelled' }))
      .toContainText('ACTIVE');
  });

  test('the close confirmation says what happens and what can be undone', async ({ page }) => {
    await page.goto('/test/reset');
    await loginAsTeacher(page);
    const row = await createTask(page, 'QA P16 Close Wording');

    let asked = null;
    page.once('dialog', dialog => { asked = dialog.message(); dialog.dismiss(); });
    await row.locator('button[data-task-close]').click();
    await page.waitForLoadState('networkidle');

    expect(asked, 'closing a task must ask first').toBeTruthy();
    expect(asked).toContain('QA P16 Close Wording');
    // The two things somebody about to press it actually wants to know.
    expect(asked).toMatch(/will not be able to hand it in/i);
    expect(asked).toMatch(/reopen/i);
  });

  /**
   * R3-P2-2. The Class column read "Class 6" -- the grade -- for every task,
   * including one set for a single section, with "Whole Class" beside it. So a
   * task for 6-A was listed exactly like one for the whole of Grade 6. True
   * before tasks carried a section; this list was the last place still saying it.
   */
  test('the list names the section a task was set for, not the whole grade', async ({ page }) => {
    await page.goto('/test/reset');
    await loginAsTeacher(page);
    const row = await createTask(page, 'QA P16 Section Label');

    // createTask picks Grade 6 - A from the section list, so that is what the
    // row has to say.
    await expect(row).toContainText('Grade 6 - A', { timeout: 30000 });
    // The claim that was not true.
    await expect(row).not.toContainText('Class 6');
    await expect(row).not.toContainText('all sections');
  });

  /**
   * R3-P2-3, finished. The Subject picker listed all five of the school's subjects,
   * so a teacher who takes Mathematics in 6-A was invited to file English homework
   * for them.
   *
   * The first version of this grouped rather than filtered, deliberately: nothing
   * on the server restricted subjectCode, so a short list would have been a rule
   * the form kept and the API did not. The server enforces it now
   * (TaskSubjectScopeTest), which inverts that argument -- leaving the other four
   * on offer means offering four guaranteed refusals.
   *
   * The fallback is the part worth protecting, and the admin case below is it.
   */
  test('the subject picker offers only what this teacher teaches that class', async ({ page }) => {
    await page.goto('/test/reset');
    await loginAsTeacher(page);
    await page.goto('/web/teacher/tasks');
    await page.waitForLoadState('networkidle');

    const select = page.locator('#subjectType');
    await expect(select.locator('option').first()).toHaveText(/Mathematics/, { timeout: 30000 });

    const offered = await select.locator('option').evaluateAll(opts => opts.map(o => o.value));
    expect(offered, 'this teacher takes Mathematics in 6-A and nothing else')
      .toEqual(['MATHEMATICS']);

    // And the catalogue is still what it is drawn from -- the list is narrowed,
    // not hardcoded, which is what `populated from /api/subjects` is about.
    const apiCodes = await page.evaluate(() =>
      fetch('/api/subjects').then(r => r.json()).then(list => list.map(s => s.code)));
    expect(apiCodes.length).toBeGreaterThan(offered.length);
    for (const code of offered) {
      expect(apiCodes).toContain(code);
    }
  });

  /**
   * The fallback: when the form cannot tell what the caller teaches, it offers
   * everything.
   *
   * <p>This is the half that keeps the narrowing safe, and it is worth a test of its
   * own because nothing else would notice it breaking -- the teacher test above
   * passes whether the fallback works or not.
   *
   * <p>Driven by failing the request rather than by signing in as an admin. That was
   * the first version and it was the wrong surface: /web/teacher/tasks does not
   * properly support an admin -- the template has a branch reading "This page is for
   * teachers. An admin account cannot list tasks here." -- and the test timed out
   * waiting for a page that never settles for them. The admin exemption is a server
   * rule and is pinned where it lives, in
   * TaskSubjectScopeTest.anAdminIsNotAssignedToAnythingAndIsNotHeldToThis.
   *
   * <p>What matters here is the branch, not who takes it: an admin, a teacher whose
   * assignment cannot be matched to a catalogue entry, and a request that simply
   * failed all arrive at the same place, and none of them should be left unable to
   * choose a subject at all.
   */
  test('when the form cannot tell what the caller teaches, it offers every subject',
    async ({ page }) => {
      await page.goto('/test/reset');
      await loginAsTeacher(page);

      // Exactly what an admin gets from this endpoint, and what a network failure
      // gets anybody.
      await page.route('**/api/teacher/classes', route =>
        route.fulfill({ status: 403, contentType: 'application/json', body: '{"error":"no"}' }));

      await page.goto('/web/teacher/tasks');
      await page.waitForLoadState('networkidle');

      const offered = await page.locator('#subjectType option').evaluateAll(opts => opts.map(o => o.value));
      const apiCodes = await page.evaluate(() =>
        fetch('/api/subjects').then(r => r.json()).then(list => list.map(s => s.code)));

      expect(offered.length,
        'with nothing to narrow by, every subject has to stay available -- the '
        + 'alternative is a picker with nothing in it')
        .toBe(apiCodes.length);
      expect(apiCodes.length, 'the catalogue should have more than one subject for this to mean anything')
        .toBeGreaterThan(1);
    });

  /**
   * The rule as the teacher meets it. The picker keeps them out of this by default;
   * this drives the refusal itself, because the picker is a courtesy and the server
   * is the rule -- and a teacher who gets here needs to be told why, not just
   * stopped.
   */
  test("a subject the teacher does not teach is refused, and says so", async ({ page }) => {
    await page.goto('/test/reset');
    await loginAsTeacher(page);
    await page.goto('/web/teacher/tasks');
    await page.waitForLoadState('networkidle');

    await page.fill('#taskTitle', 'QA P17 Wrong Subject');
    await page.selectOption('#taskType', 'HOMEWORK');
    await page.selectOption('#classSectionId', { label: 'Grade 6 - A' });
    await page.locator('input[name="assignedToClass"][value="true"]').check();
    await page.fill('#xpReward', '30');

    // Past the narrowed list, the way anything that is not this form arrives.
    await page.locator('#subjectType').evaluate((select) => {
      const option = document.createElement('option');
      option.value = 'ENGLISH';
      option.textContent = 'English';
      select.appendChild(option);
      select.value = 'ENGLISH';
    });

    await page.click('button:has-text("Assign Task")');
    await page.waitForLoadState('networkidle');

    await expect(page.locator('[data-task-notice]'))
      .toContainText(/not assigned to teach English/i, { timeout: 30000 });
    await expect(page.locator('#tasksTableBody tr', { hasText: 'QA P17 Wrong Subject' }))
      .toHaveCount(0);
  });

  test('a task nobody has touched can be deleted', async ({ page }) => {
    await page.goto('/test/reset');
    await loginAsTeacher(page);
    const row = await createTask(page, 'QA P15 Delete Me');

    page.once('dialog', dialog => dialog.accept());
    await row.locator('button[data-task-delete]').click();

    await expect(page.locator('[data-task-notice]')).toContainText(/deleted/i, { timeout: 30000 });
    await expect(page.locator('#tasksTableBody tr', { hasText: 'QA P15 Delete Me' })).toHaveCount(0);
  });

  test('backing out of the delete confirmation keeps the task', async ({ page }) => {
    await page.goto('/test/reset');
    await loginAsTeacher(page);
    const row = await createTask(page, 'QA P15 Keep Me');

    page.once('dialog', dialog => dialog.dismiss());
    await row.locator('button[data-task-delete]').click();
    await page.waitForLoadState('networkidle');

    await expect(page.locator('#tasksTableBody tr', { hasText: 'QA P15 Keep Me' })).toBeVisible();
  });

  test('the submissions panel lists the class and who is outstanding', async ({ page }) => {
    await page.goto('/test/reset');
    await loginAsTeacher(page);
    const row = await createTask(page, 'QA P15 Who Handed In');

    await row.locator('button[data-task-submissions]').click();
    await expect(page.locator('#submissionsModal')).toBeVisible();
    await expect(page.locator('#submissionsTitle')).toContainText('QA P15 Who Handed In');
    // Nobody has handed in yet, so the count says so and the roster still lists
    // the pupils who owe it -- that is the question the panel exists to answer.
    await expect(page.locator('#submissionsCount')).toContainText(/handed in/i, { timeout: 30000 });
    await expect(page.locator('#submissionsBody tr').first()).toContainText(/Not handed in/i);

    await page.click('#submissionsModal button[aria-label="Close"]');
    await expect(page.locator('#submissionsModal')).not.toBeVisible();
  });
});

test.describe('Endpoints for managing a task', () => {
  test.setTimeout(180000);

  /**
   * One assertion per new endpoint, as the testing rules require. Driven through
   * the page's own session rather than a JWT, since that is how the buttons call
   * them -- and it is the path the CSRF token travels on.
   */
  test('close, reopen, update, submissions and delete all answer for the owner', async ({ page }) => {
    await page.goto('/test/reset');
    await loginAsTeacher(page);
    await createTask(page, 'QA P15 API Task');

    const taskId = await page.locator('#tasksTableBody tr', { hasText: 'QA P15 API Task' })
      .locator('button[data-task-close]').getAttribute('data-id');
    expect(taskId).toMatch(/^[0-9a-fA-F-]{36}$/);

    const results = await page.evaluate(async (id) => {
      const token = document.getElementById('csrfToken');
      const headers = { 'Content-Type': 'application/json' };
      if (token) headers['X-CSRF-TOKEN'] = token.value;
      const out = {};

      out.close = (await fetch('/api/teacher/tasks/' + id + '/close', { method: 'POST', headers })).status;
      out.reopen = (await fetch('/api/teacher/tasks/' + id + '/reopen', { method: 'POST', headers })).status;

      const update = await fetch('/api/teacher/tasks/' + id + '/update', {
        method: 'POST', headers,
        body: JSON.stringify({ title: 'QA P15 API Task edited', description: 'd', xpReward: 40 })
      });
      out.update = update.status;

      const subs = await fetch('/api/teacher/tasks/' + id + '/submissions');
      out.submissions = subs.status;
      out.submissionsBody = subs.ok ? await subs.json() : null;

      // -10 XP has to be refused on an edit as well as on create.
      out.badXp = (await fetch('/api/teacher/tasks/' + id + '/update', {
        method: 'POST', headers,
        body: JSON.stringify({ title: 'QA P15 API Task edited', description: 'd', xpReward: -10 })
      })).status;

      out.delete = (await fetch('/api/teacher/tasks/' + id + '/delete', { method: 'POST', headers })).status;
      return out;
    }, taskId);

    expect(results.close).toBe(200);
    expect(results.reopen).toBe(200);
    expect(results.update).toBe(200);
    expect(results.submissions).toBe(200);
    expect(results.badXp, 'an edit must not be a way back in for -10 XP').toBeGreaterThanOrEqual(400);
    expect(results.delete).toBe(200);

    // The submissions payload has to carry the counts the panel reads.
    expect(results.submissionsBody).toHaveProperty('expected');
    expect(results.submissionsBody).toHaveProperty('handedIn');
    expect(Array.isArray(results.submissionsBody.rows)).toBe(true);
  });
});

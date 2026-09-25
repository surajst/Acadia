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
  await page.selectOption('#subjectType', { index: 1 });
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

    await row.locator('button[data-task-close]').click();
    const closed = page.locator('#tasksTableBody tr', { hasText: 'QA P15 Close Me' });
    await expect(closed).toContainText('CLOSED', { timeout: 30000 });

    // Reopen is offered in place of Close, not alongside it.
    await expect(closed.locator('button[data-task-close]')).toHaveCount(0);
    await closed.locator('button[data-task-reopen]').click();
    await expect(page.locator('#tasksTableBody tr', { hasText: 'QA P15 Close Me' }))
      .toContainText('ACTIVE', { timeout: 30000 });
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

const { test, expect } = require('@playwright/test');

async function login(page, username, password) {
  await page.goto('/login');
  await page.fill('#username', username);
  await page.fill('#password', password);
  await page.click('button[type="submit"]');
  await page.waitForURL(url => url.pathname.includes('/web/') && !url.pathname.includes('/login'), { timeout: 90000 });
}

// A quest's XP used to be paid once per approval request, and the dashboard
// button only disabled itself after the server answered -- so a double-click
// paid the child twice.
test.describe('Quest approval pays once', () => {

  test('A double-click sends one approval, and a repeat is refused', async ({ page, request }) => {
    await page.goto('/test/reset');

    // Ramesh sets a quest for Arjun.
    await login(page, 'ramesh@gmail.com', process.env.DEMO_ADMIN_PASSWORD);
    await page.goto('/web/parent/dashboard');
    await page.locator('[data-tab="profile"]').first().click();
    const title = 'Double Approval Task: ' + Math.random().toString(36).substring(7);
    const child = page.locator('#profile-assignStudentId option', { hasText: 'Arjun' }).first();
    await page.selectOption('#profile-assignStudentId', await child.getAttribute('value'));
    await page.fill('#profile-taskTitle', title);
    await page.fill('#profile-xpBounty', '120');
    await page.locator('#assignTaskForm-profile button[type="submit"]').click();
    await page.waitForURL(url => url.pathname.includes('/web/parent/dashboard'), { timeout: 90000 });
    await page.waitForLoadState('networkidle');
    await expect(page.locator('#toast')).toContainText('Home task assigned successfully!');

    // Arjun marks it done on the phone.
    const loginRes = await request.post('/api/mobile/auth/login', {
      data: { email: 'arjun@gmail.com', password: process.env.DEMO_ADMIN_PASSWORD },
    });
    expect(loginRes.ok()).toBeTruthy();
    const auth = { Authorization: `Bearer ${(await loginRes.json()).token}` };
    const quest = (await (await request.get('/api/mobile/student/dashboard', { headers: auth })).json())
      .parentQuests.find(q => q.taskDescription === title);
    expect(quest).toBeDefined();
    expect((await request.post(`/api/student/claim-quest/${quest.id}`, { headers: auth })).ok()).toBeTruthy();

    // Ramesh double-clicks Approve.
    await page.goto('/web/parent/dashboard');
    await page.waitForLoadState('networkidle');
    await page.locator('[data-tab="quests"]').first().click();
    // By quest id: the button swaps its classes once approved.
    const button = page.locator(`button[data-quest-id="${quest.id}"]`);
    await expect(button).toBeVisible({ timeout: 90000 });
    const approvals = [];
    page.on('request', r => {
      if (r.url().includes('/api/parent/approve-quest/') && r.method() === 'POST') approvals.push(r);
    });
    const firstResponse = page.waitForResponse(r => r.url().includes('/api/parent/approve-quest/'));
    await button.dblclick();
    expect((await firstResponse).ok()).toBeTruthy();
    await expect(button).toHaveText('✓ Verified');
    await page.waitForLoadState('networkidle');
    expect(approvals.length, 'one click-pair, one approval').toBe(1);

    // A second approval from anywhere else (another tab, the API) is refused.
    const again = await page.evaluate(async (id) => {
      const res = await fetch('/api/parent/approve-quest/' + id, { method: 'POST' });
      return res.status;
    }, quest.id);
    expect(again).toBe(409);
  });

});

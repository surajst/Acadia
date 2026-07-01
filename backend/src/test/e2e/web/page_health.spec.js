const { test, expect } = require('@playwright/test');

async function login(page, username, password) {
  await page.goto('/login');
  await page.fill('#username', username);
  await page.fill('#password', password);
  await page.click('button[type="submit"]');
  await page.waitForLoadState('load');
  await page.waitForURL(url => url.pathname.includes('/web/') && !url.pathname.includes('/login'), { timeout: 90000 });
}

async function assertPageHealth(page, path) {
  await page.goto(path);
  await page.waitForLoadState('load');
  
  await expect(page.locator('text=Something went wrong')).not.toBeVisible();
  await expect(page.locator('text=Whitelabel Error Page')).not.toBeVisible();
  await expect(page.locator('text=Application Error')).not.toBeVisible();
}

test.describe('Admin Page Health', () => {
  test.beforeEach(async ({ page }) => {
    await page.goto('/test/reset');
    await login(page, 'admin@greenwood.com', 'PilotLaunchSecure2026!');
  });

  test('Admin Dashboard', async ({ page }) => {
    await assertPageHealth(page, '/web/admin/dashboard');
  });

  test('Admin Management', async ({ page }) => {
    await assertPageHealth(page, '/web/admin/management');
  });

  test('Admin Fees', async ({ page }) => {
    await assertPageHealth(page, '/web/admin/fees');
  });

  test('Admin Curriculum', async ({ page }) => {
    await assertPageHealth(page, '/web/admin/curriculum');
    await expect(page.locator('text=Curriculum Progress')).toBeVisible();
  });

  test('Admin Assignments', async ({ page }) => {
    await assertPageHealth(page, '/web/admin/assignments');
    await expect(page.locator('h2:has-text("Teacher Assignments")')).toBeVisible();
  });
});

test.describe('Teacher Page Health', () => {
  test.beforeEach(async ({ page }) => {
    await page.goto('/test/reset');
    await login(page, 'teacher@greenwood.com', 'PilotLaunchSecure2026!');
  });

  test('Teacher Dashboard', async ({ page }) => {
    await assertPageHealth(page, '/web/teacher/dashboard');
  });

  test('Teacher Attendance', async ({ page }) => {
    await assertPageHealth(page, '/web/teacher/attendance');
  });

  test('Teacher Tasks', async ({ page }) => {
    await assertPageHealth(page, '/web/teacher/tasks');
  });
});

test.describe('Parent Page Health', () => {
  test.beforeEach(async ({ page }) => {
    await page.goto('/test/reset');
    await login(page, 'ramesh@gmail.com', 'PilotLaunchSecure2026!');
  });

  test('Parent Dashboard', async ({ page }) => {
    await assertPageHealth(page, '/web/parent/portal');
  });
});

test.describe('Student Page Health', () => {
  test.beforeEach(async ({ page }) => {
    await page.goto('/test/reset');
    await login(page, 'arjun@gmail.com', 'PilotLaunchSecure2026!');
  });

  test('Student Dashboard', async ({ page }) => {
    await assertPageHealth(page, '/web/student/portal');
  });
});

test.describe('Mobile API Health', () => {
  test('Mobile Auth & Student Dashboard API', async ({ request }) => {
    const loginRes = await request.post('/api/mobile/auth/login', {
      data: { email: 'arjun@gmail.com', password: 'PilotLaunchSecure2026!' }
    });
    expect(loginRes.ok()).toBeTruthy();
    const data = await loginRes.json();
    expect(data.token).toBeDefined();
    
    const dashRes = await request.get('/api/mobile/student/dashboard', {
      headers: { Authorization: `Bearer ${data.token}` }
    });
    expect(dashRes.ok()).toBeTruthy();
    const dashData = await dashRes.json();
    expect(dashData.student).toBeDefined();
  });

  test('Mobile Auth & Parent Dashboard API', async ({ request }) => {
    const loginRes = await request.post('/api/mobile/auth/login', {
      data: { email: 'ramesh@gmail.com', password: 'PilotLaunchSecure2026!' }
    });
    expect(loginRes.ok()).toBeTruthy();
    const data = await loginRes.json();
    expect(data.token).toBeDefined();
    
    const dashRes = await request.get('/api/mobile/parent/dashboard', {
      headers: { Authorization: `Bearer ${data.token}` }
    });
    expect(dashRes.ok()).toBeTruthy();
    const dashData = await dashRes.json();
    expect(dashData.parent).toBeDefined();
  });

  test('Mobile Parent Attendance API', async ({ request }) => {
    const loginRes = await request.post('/api/mobile/auth/login', {
      data: { email: 'ramesh@gmail.com', password: 'PilotLaunchSecure2026!' }
    });
    expect(loginRes.ok()).toBeTruthy();
    const data = await loginRes.json();
    expect(data.token).toBeDefined();
    
    const attRes = await request.get('/api/mobile/parent/attendance', {
      headers: { Authorization: `Bearer ${data.token}` }
    });
    expect(attRes.ok()).toBeTruthy();
    const attData = await attRes.json();
    expect(Array.isArray(attData)).toBeTruthy();
  });

  test('Mobile Student Attendance API', async ({ request }) => {
    const loginRes = await request.post('/api/mobile/auth/login', {
      data: { email: 'arjun@gmail.com', password: 'PilotLaunchSecure2026!' }
    });
    expect(loginRes.ok()).toBeTruthy();
    const data = await loginRes.json();
    expect(data.token).toBeDefined();
    
    const attRes = await request.get('/api/mobile/student/attendance', {
      headers: { Authorization: `Bearer ${data.token}` }
    });
    expect(attRes.ok()).toBeTruthy();
    const attData = await attRes.json();
    expect(Array.isArray(attData)).toBeTruthy();
  });

  test('Mobile Student Tasks API', async ({ request }) => {
    const loginRes = await request.post('/api/mobile/auth/login', {
      data: { email: 'arjun@gmail.com', password: 'PilotLaunchSecure2026!' }
    });
    expect(loginRes.ok()).toBeTruthy();
    const data = await loginRes.json();
    expect(data.token).toBeDefined();
    
    const tasksRes = await request.get('/api/mobile/student/tasks', {
      headers: { Authorization: `Bearer ${data.token}` }
    });
    expect(tasksRes.ok()).toBeTruthy();
    const tasksData = await tasksRes.json();
    expect(Array.isArray(tasksData)).toBeTruthy();
  });

  test('Mobile Teacher Portal Tab Health', async ({ page }) => {
    // This test requires the Expo dev server running on port 8081.
    // Skip gracefully when not running in a mobile-capable environment.
    test.skip(true, 'Expo dev server not available in this environment — skipping mobile UI test');
  });
});

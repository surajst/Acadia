const { test, expect } = require('./fixtures');

/**
 * R3-P2-4. Every browser tab showed the same title whatever screen was open.
 *
 * The app's only titles were `options.title` on a Stack.Screen, which names the
 * navigator's own header and does not touch `document.title` -- so a parent with
 * Fees and Attendance open side by side could not tell the two tabs apart, and a
 * link shared from either arrived describing nothing.
 *
 * The distinctness assertions are the ones that matter. A single title applied
 * everywhere would satisfy "the tab has a title" perfectly well, and that is
 * exactly the state being fixed.
 */
test.describe('Browser tabs are named after the screen', () => {
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

  test("a parent's screens are each named, and named differently", async ({ page }) => {
    await page.goto(`${API}/test/reset`, { waitUntil: 'load' });
    await login(page, 'ramesh@gmail.com', 'PilotLaunchSecure2026!');

    const seen = {};
    for (const [route, expected] of [
      ['/fees', 'Fees'],
      ['/attendance', 'Attendance'],
      ['/performance', 'Performance'],
      ['/announcements', 'News'],
    ]) {
      await page.goto(route);
      await page.waitForLoadState('networkidle');
      // "Screen · ACADIA", which is the convention the web portal has used for
      // every page all along.
      await expect
        .poll(() => page.title(), { timeout: 30000 })
        .toMatch(new RegExp(`^${expected} · ACADIA$`));
      seen[route] = await page.title();
    }

    // The finding itself: four screens, four different tabs.
    const titles = Object.values(seen);
    expect(new Set(titles).size, `each screen needs its own tab title, got ${titles}`)
      .toBe(titles.length);
  });

  test('a teacher gets their own screens named too', async ({ page }) => {
    await login(page, 'teacher@greenwood.com', 'PilotLaunchSecure2026!');

    await page.goto('/gradebook');
    await page.waitForLoadState('networkidle');
    await expect.poll(() => page.title(), { timeout: 30000 }).toBe('Gradebook · ACADIA');

    await page.goto('/teacher');
    await page.waitForLoadState('networkidle');
    await expect.poll(() => page.title(), { timeout: 30000 }).toBe('My Classes · ACADIA');
  });

  /**
   * Home is the one screen whose own name would be a tautology, so it carries the
   * brand alone -- and an unlisted screen falls back to the same thing rather than
   * keeping whatever the screen before it was called.
   */
  test('the home screen carries the app name', async ({ page }) => {
    await login(page, 'arjun@gmail.com', 'PilotLaunchSecure2026!');

    await page.goto('/challenges');
    await page.waitForLoadState('networkidle');
    await expect.poll(() => page.title(), { timeout: 30000 }).toBe('Challenges · ACADIA');

    await page.goto('/');
    await page.waitForLoadState('networkidle');
    await expect.poll(() => page.title(), { timeout: 30000 }).toBe('ACADIA');
  });
});

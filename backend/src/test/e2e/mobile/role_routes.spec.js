const { test, expect } = require('@playwright/test');

/**
 * The API was already locked down -- a parent's token gets 403 on every teacher
 * endpoint -- but the app let a parent reach the screens by address. /gradebook
 * opened and offered "Create assessments and enter scores" above "No classes
 * assigned"; /challenges and /marketplace opened too.
 *
 * Nothing leaked, because no data ever arrived. What leaked was the impression
 * that a parent is supposed to be there, and a screen full of controls that
 * cannot work. R3-P1-5.
 *
 * Driven per role, because the map in constants/routeAccess.ts is only right if
 * it both refuses the wrong role and admits the right one -- a guard that refuses
 * everybody passes half of these and breaks the app.
 */
test.describe('Screens belong to roles', () => {
  test.use({ baseURL: 'http://localhost:8081' });

  async function login(page, username, password) {
    await page.goto('/');
    await page.getByPlaceholder('Email / Username').fill(username);
    await page.getByPlaceholder('Password').fill(password);
    await page.getByText('Log In').click();
    await page.waitForLoadState('networkidle');
    // Signed in when the password field is gone.
    await expect(page.getByPlaceholder('Password')).toHaveCount(0, { timeout: 30000 });
  }

  /** Where the app actually ended up, ignoring any query string. */
  async function landedOn(page) {
    return page.evaluate(() => window.location.pathname);
  }

  test('a parent is sent home from the teacher gradebook', async ({ page }) => {
    await page.goto('/test/reset', { waitUntil: 'load' }).catch(() => {});
    await login(page, 'ramesh@gmail.com', 'PilotLaunchSecure2026!');

    await page.goto('/gradebook');
    await page.waitForLoadState('networkidle');

    expect(await landedOn(page), 'a parent has no gradebook').toBe('/');
    // The specific words QA saw on a screen that was not theirs.
    await expect(page.getByText(/Create assessments and enter scores/i)).toHaveCount(0);
    await expect(page.getByText(/No classes assigned/i)).toHaveCount(0);
  });

  test('a parent is sent home from the pupil screens', async ({ page }) => {
    await login(page, 'ramesh@gmail.com', 'PilotLaunchSecure2026!');

    for (const route of ['/challenges', '/marketplace', '/student-results']) {
      await page.goto(route);
      await page.waitForLoadState('networkidle');
      expect(await landedOn(page), `${route} is not a parent's`).toBe('/');
    }
  });

  test('a pupil is sent home from teacher screens', async ({ page }) => {
    await login(page, 'arjun@gmail.com', 'PilotLaunchSecure2026!');

    for (const route of ['/gradebook', '/verification', '/task-new']) {
      await page.goto(route);
      await page.waitForLoadState('networkidle');
      expect(await landedOn(page), `${route} is not a pupil's`).toBe('/');
    }
  });

  /**
   * The half a careless guard breaks. A guard that redirects everybody would pass
   * every test above and leave the app unusable, so each role has to be shown
   * reaching its own work.
   */
  test('a teacher still reaches their own screens', async ({ page }) => {
    await login(page, 'teacher@greenwood.com', 'PilotLaunchSecure2026!');

    for (const route of ['/gradebook', '/teacher', '/tasks']) {
      await page.goto(route);
      await page.waitForLoadState('networkidle');
      expect(await landedOn(page), `${route} is a teacher's own screen`).toBe(route);
    }
  });

  test('a pupil still reaches their own screens', async ({ page }) => {
    await login(page, 'arjun@gmail.com', 'PilotLaunchSecure2026!');

    for (const route of ['/challenges', '/marketplace']) {
      await page.goto(route);
      await page.waitForLoadState('networkidle');
      expect(await landedOn(page), `${route} is a pupil's own screen`).toBe(route);
    }
  });

  /** And the screens everyone has stay reachable for everyone. */
  test('profile and settings are open to every role', async ({ page }) => {
    for (const who of ['ramesh@gmail.com', 'arjun@gmail.com', 'teacher@greenwood.com']) {
      await page.goto('/');
      await login(page, who, 'PilotLaunchSecure2026!');
      for (const route of ['/profile', '/settings']) {
        await page.goto(route);
        await page.waitForLoadState('networkidle');
        expect(await landedOn(page), `${route} belongs to everyone (${who})`).toBe(route);
      }
      // Sign out between roles so the next login starts clean.
      await page.evaluate(() => window.localStorage.clear());
    }
  });
});

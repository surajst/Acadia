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

  /**
   * The case the first version of constants/routeAccess.ts got wrong: it read
   * /attendance as the teacher's register and refused parents, when
   * (tabs)/attendance.tsx is the parent's own attendance calendar. Two
   * long-standing tests caught it, which is why the map is now derived from
   * constants/wheel.ts rather than written out a second time.
   */
  test('a parent still reaches their own screens', async ({ page }) => {
    await login(page, 'ramesh@gmail.com', 'PilotLaunchSecure2026!');

    for (const route of ['/attendance', '/fees', '/performance', '/announcements', '/recognition']) {
      await page.goto(route);
      await page.waitForLoadState('networkidle');
      expect(await landedOn(page), `${route} is a parent's own screen`).toBe(route);
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

  /**
   * The strongest form of the admission half, and the one that closes the hole
   * the first map fell into.
   *
   * <p>Naming routes in a test is another hand-written list, and a list can be
   * wrong the same way the map was. This reads the destinations out of the app
   * itself -- every spoke drawn on the role's wheel -- and requires each one to
   * open. If the guard and the navigation ever disagree about a role, whichever
   * of the two is wrong, this fails.
   */
  for (const [who, role] of [
    ['ramesh@gmail.com', 'parent'],
    ['arjun@gmail.com', 'pupil'],
    ['teacher@greenwood.com', 'teacher'],
  ]) {
    test(`every spoke on a ${role}'s wheel opens`, async ({ page }) => {
      // One login plus a page load per spoke; eight spokes is the widest wheel.
      test.setTimeout(120000);
      await login(page, who, 'PilotLaunchSecure2026!');

      // UserWheel labels each spoke "{label}, {i + 1} of {n}".
      const spokeName = /, \d+ of \d+$/;
      const count = await page.getByRole('button', { name: spokeName }).count();
      expect(count, `a ${role} should be offered somewhere to go`).toBeGreaterThan(0);

      for (let i = 0; i < count; i++) {
        // Back to the wheel by address rather than history: a full load also
        // clears any LogBox toast a visited screen left behind, which would
        // otherwise swallow the next spoke's tap.
        await page.goto('/');
        await page.waitForLoadState('networkidle');
        const spoke = page.getByRole('button', { name: spokeName }).nth(i);
        const label = await spoke.getAttribute('aria-label');
        await spoke.click();
        await page.waitForLoadState('networkidle');
        expect(await landedOn(page), `"${label}" is on a ${role}'s wheel, so it must open`)
          .not.toBe('/');
      }
    });
  }

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

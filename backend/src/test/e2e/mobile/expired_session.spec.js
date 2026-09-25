const { test, expect } = require('@playwright/test');

/**
 * A parent's token lasts 24 hours. Rakesh's expired overnight and the app did not
 * notice: it had no response interceptor at all, so every refused request read as
 * "no data" and the Fees screen said "No fees raised yet. The school has not
 * billed anything for Your child" over 450 rupees outstanding.
 *
 * ExpiredTokenTest pins the backend half -- 401 with reason=expired for a dead
 * token, 403 kept for "signed in but not allowed". This covers what the app does
 * with it, which is the half that was missing and the half a person experiences.
 */
test.describe('An ended session sends you back to sign-in', () => {
  test.use({ baseURL: 'http://localhost:8081' });

  async function login(page, username, password) {
    await page.goto('/');
    await page.getByPlaceholder('Email / Username').fill(username);
    await page.getByPlaceholder('Password').fill(password);
    await page.getByText('Log In').click();
    await page.waitForLoadState('networkidle');
  }

  function isSignInScreen(page) {
    // The gate in app/_layout.tsx renders sign-in whenever userToken is null, so
    // the password field being back is the observable outcome.
    return page.getByPlaceholder('Password').isVisible();
  }

  test('a parent whose token has expired is signed out rather than shown empty data', async ({ page }) => {
    await page.goto('/test/reset', { waitUntil: 'load' }).catch(() => {});
    await login(page, 'ramesh@gmail.com', 'PilotLaunchSecure2026!');
    await expect(page.getByPlaceholder('Password')).toHaveCount(0, { timeout: 30000 });

    // Replace the stored token with one that is structurally a JWT and long
    // expired. Signed with the wrong key, so the server reports it as invalid --
    // which takes the same path as expired: 401, session cleared. Forging a
    // genuinely-expired token would need the server's signing key, which a test
    // has no business holding.
    await page.evaluate(() => {
      const dead = [
        'eyJhbGciOiJIUzI1NiJ9',
        'eyJzdWIiOiJyYW1lc2hAZ21haWwuY29tIiwiaWF0IjoxNTE2MjM5MDIyLCJleHAiOjE1MTYyMzkwMjN9',
        'ZmFrZS1zaWduYXR1cmU',
      ].join('.');
      window.localStorage.setItem('userToken', dead);
    });

    // Any authenticated screen will do; Fees is the one that told the lie.
    await page.goto('/fees');
    await page.waitForLoadState('networkidle');

    // The assertion that matters: signed out, not reassured.
    await expect(page.getByPlaceholder('Password')).toBeVisible({ timeout: 30000 });
    await expect(page.getByText(/No fees raised yet/i)).toHaveCount(0);
    await expect(page.getByText(/has not billed anything/i)).toHaveCount(0);
  });

  test('the stored token is cleared, so a reload does not resurrect the dead session', async ({ page }) => {
    await login(page, 'ramesh@gmail.com', 'PilotLaunchSecure2026!');
    await expect(page.getByPlaceholder('Password')).toHaveCount(0, { timeout: 30000 });

    await page.evaluate(() => {
      window.localStorage.setItem('userToken', 'eyJhbGciOiJIUzI1NiJ9.e30.not-a-real-signature');
    });
    await page.goto('/fees');
    await page.waitForLoadState('networkidle');
    await expect(page.getByPlaceholder('Password')).toBeVisible({ timeout: 30000 });

    // If the token were still in storage, the gate would let the app back in on
    // the next load and the same false empty state would return.
    const stored = await page.evaluate(() => window.localStorage.getItem('userToken'));
    expect(stored, 'a dead token must not survive in storage').toBeFalsy();
  });

  /**
   * The half a careless fix breaks: signing a parent out for reaching a screen
   * that is simply not theirs would lose their session over a wrong turn.
   *
   * Driven by opening a teacher-only screen as a parent, so the refusals travel
   * through the app's own API client and hit the interceptor that decides whether
   * to end the session. An earlier version of this test called fetch() from the
   * page instead -- which both resolved against the Expo dev server on :8081
   * rather than the API on :8080, and bypassed the very interceptor it was meant
   * to exercise. It asserted nothing about the app.
   *
   * That a parent can reach this screen at all is R3-P1-5, which is a separate
   * fix; here it is useful, because it is the simplest way to make the app take
   * a 403 in its normal flow.
   */
  test('a 403 does not sign anyone out', async ({ page }) => {
    await login(page, 'ramesh@gmail.com', 'PilotLaunchSecure2026!');
    await expect(page.getByPlaceholder('Password')).toHaveCount(0, { timeout: 30000 });

    const before = await page.evaluate(() => window.localStorage.getItem('userToken'));
    expect(before, 'the parent should be signed in at this point').toBeTruthy();

    // Every teacher call this screen makes is forbidden for a parent.
    await page.goto('/gradebook');
    await page.waitForLoadState('networkidle');

    const after = await page.evaluate(() => window.localStorage.getItem('userToken'));
    expect(after, 'a permission refusal must not end the session').toBe(before);
    await expect(page.getByPlaceholder('Password')).toHaveCount(0);
  });
});

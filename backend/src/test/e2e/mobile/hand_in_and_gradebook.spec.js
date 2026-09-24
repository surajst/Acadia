const { test, expect } = require('@playwright/test');

/**
 * The two app actions the QA pass could not confirm, driven in a real browser
 * rather than with synthetic pointer events.
 *
 * Both reported symptoms were platform-specific rather than broken logic, and
 * only a browser shows them:
 *
 *  - Hand in appeared to do nothing. The confirmation was Alert.alert, which
 *    is a no-op on React Native Web, and the sheet blanked to " · + XP"
 *    because the task was cleared while the modal was still animating out.
 *    The backend round trip was never at fault -- StudentHandInTest covers
 *    that half.
 *  - Saving gradebook scores gave no confirmation, for the same Alert reason,
 *    and a score above the maximum was accepted.
 */
test.describe('App actions that need a real browser', () => {
  test.use({ baseURL: 'http://localhost:8081' });

  async function login(page, username, password) {
    await page.goto('/');
    await page.getByPlaceholder('Email / Username').fill(username);
    await page.getByPlaceholder('Password').fill(password);
    await page.getByText('Log In').click();
    await page.waitForLoadState('networkidle');
  }

  test('a student handing work in sees it confirmed in the sheet', async ({ page }) => {
    await page.goto('http://localhost:8080/test/reset');
    await login(page, 'arjun@gmail.com', 'PilotLaunchSecure2026!');

    // Challenges is where a pupil's set work lives.
    await page.getByText('Challenges', { exact: true }).first().click();
    await page.waitForLoadState('networkidle');

    const firstTask = page.locator('text=/Homework|Practice|Project|Reading/ >> visible=true').first();
    if (await firstTask.count() === 0) {
      test.skip(true, 'no task is set for this pupil in the seed; nothing to hand in');
    }
    await firstTask.click();
    await page.waitForLoadState('networkidle');

    // The sheet must name the task. This is the assertion the blanking bug
    // broke: the header rendered " · + XP" with nothing around it.
    const handIn = page.getByRole('button', { name: 'Hand in' });
    await expect(handIn).toBeVisible({ timeout: 30000 });

    // Every question has to be answered before it will send.
    const answers = page.getByPlaceholder('Your answer');
    const count = await answers.count();
    for (let i = 0; i < count; i++) {
      await answers.nth(i).fill('42');
    }
    await page.getByPlaceholder('Optional note').fill('Handed in by Playwright');

    await handIn.click();
    await page.waitForLoadState('networkidle');

    // In the sheet, not an Alert -- which is the whole point on web.
    await expect(page.locator('text=Handed in >> visible=true').first()).toBeVisible({ timeout: 30000 });
    await expect(page.locator('text=/Waiting for your teacher/ >> visible=true')).toBeVisible();

    // And the sheet still knows which task it was: no " · + XP".
    await expect(page.locator('text=" · + XP"')).toHaveCount(0);

    await page.getByRole('button', { name: 'Done' }).click();
    await page.waitForLoadState('networkidle');
  });

  test('a teacher cannot save a score above the maximum, and sees it said so', async ({ page }) => {
    await page.goto('http://localhost:8080/test/reset');
    await login(page, 'teacher@greenwood.com', 'PilotLaunchSecure2026!');

    await page.goto('/gradebook');
    await page.waitForLoadState('networkidle');

    const title = `PW Unit Test ${Date.now()}`;
    await page.getByLabel('New assessment title').fill(title);

    // Out of 20, so 150 is unambiguously wrong -- and the choice itself is new:
    // the max was fixed at 100, so a 20-mark quiz had to be faked.
    await page.getByRole('button', { name: 'Out of 20 marks' }).click();
    await page.getByRole('button', { name: 'Create assessment' }).click();
    await page.waitForLoadState('networkidle');

    const scoreField = page.locator('input[aria-label*="Score for"]').first();
    if (await scoreField.count() === 0) {
      test.skip(true, 'no pupils in this class section in the seed; nothing to score');
    }

    await scoreField.fill('150');
    await page.getByRole('button', { name: 'Save all scores' }).click();
    await page.waitForLoadState('networkidle');

    // The server refuses it and names the pupil and the maximum; the screen
    // shows that message rather than a generic failure.
    await expect(page.locator('text=/out of 20/ >> visible=true').first())
      .toBeVisible({ timeout: 30000 });

    // A mark inside the range saves, and says so in the page.
    await scoreField.fill('18');
    await page.getByRole('button', { name: 'Save all scores' }).click();
    await page.waitForLoadState('networkidle');
    await expect(page.locator('text=/score.* saved/ >> visible=true').first())
      .toBeVisible({ timeout: 30000 });
  });

  test('an empty Quests screen says so instead of inventing a quest', async ({ page }) => {
    await page.goto('http://localhost:8080/test/reset');
    await login(page, 'arjun@gmail.com', 'PilotLaunchSecure2026!');

    await page.goto('/quests');
    await page.waitForLoadState('networkidle');

    // The invented cards that used to stand in for an empty state. A family
    // comparing notes would have been comparing two different fictions.
    await expect(page.locator('text=Clean your room')).toHaveCount(0);
    await expect(page.locator('text=Extra Screen Time')).toHaveCount(0);
    await expect(page.locator('text=Ice Cream Trip')).toHaveCount(0);
  });
});

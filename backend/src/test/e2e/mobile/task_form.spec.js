const { test, expect } = require('./fixtures');

/**
 * R3-P1-4. Setting work from the phone told the teacher the wrong thing about
 * who would get it.
 *
 * The "ASSIGN TO" control read "Whole of Grade 6", and underneath it: "Every
 * section of Grade 6, not just Grade 6 - A." Both were true once, when a task was
 * keyed by grade and one set for 6-A really did land on every 6-B and 6-C child's
 * list. That was fixed earlier -- the section is required now and the form has
 * been sending classSectionId ever since -- but the words stayed behind. So a
 * teacher setting homework for their own class was being told, twice, that two
 * other classes would receive it.
 *
 * The confirmation after saving had the same fault ("went to everyone in Grade
 * 6"), which matters more: it is the sentence a teacher reads to decide whether
 * they need to undo something.
 *
 * Where the task actually lands is pinned server-side by TaskSectionScopeTest.
 * What this covers is what the teacher is told, which is the whole of the finding.
 */
test.describe('Setting work from the phone says who will get it', () => {
  test.use({ baseURL: 'http://localhost:8081' });

  async function login(page, username, password) {
    await page.goto('/');
    await page.getByPlaceholder('Email / Username').fill(username);
    await page.getByPlaceholder('Password').fill(password);
    await page.getByText('Log In').click();
    await page.waitForLoadState('networkidle');
  }

  test('the form names the class, not the whole grade', async ({ page }) => {
    await page.goto('http://localhost:8080/test/reset');
    await login(page, 'teacher@greenwood.com', 'PilotLaunchSecure2026!');

    await page.goto('/task-new');
    await page.waitForLoadState('networkidle');

    // The seed gives this teacher Grade 6 - A. Matched without the dash, which is
    // an en dash in the class name and easy to get wrong in a locator.
    await expect(page.getByText(/Whole class \(Grade 6/)).toBeVisible({ timeout: 30000 });
    await expect(page.getByText(/Everyone in Grade 6/)).toBeVisible();

    // The two claims that were not true. Asserted by absence because that is
    // exactly the finding: the words were left behind by an earlier fix.
    await expect(page.getByText(/Whole of Grade 6/)).toHaveCount(0);
    await expect(page.getByText(/Every section of/)).toHaveCount(0);
    await expect(page.getByText(/not just Grade 6/)).toHaveCount(0);
  });

  test('the confirmation after saving names the class too', async ({ page }) => {
    await page.goto('http://localhost:8080/test/reset');
    await login(page, 'teacher@greenwood.com', 'PilotLaunchSecure2026!');

    await page.goto('/task-new');
    await page.waitForLoadState('networkidle');

    await page.getByPlaceholder('Fractions worksheet 4').fill('Wording check');
    await page.getByPlaceholder('What should they do?').fill('Set by Playwright');

    await page.getByRole('button', { name: 'Assign task' }).click();
    await page.waitForLoadState('networkidle');

    // In the screen, not an Alert: Alert.alert is a no-op on React Native Web,
    // which is why this form appeared to do nothing while creating the task every
    // time. The sentence is the point here -- it is what a teacher reads to
    // decide whether they have just mailed two other classes' worth of homework.
    // The section has to be in there, not just the grade -- which is the whole
    // difference between "your class" and "three classes". The dash in the class
    // name is an en dash, so the gap between grade and section is matched loosely
    // rather than spelled out.
    await expect(page.getByText(/went to everyone in Grade 6.{1,4}A\./))
      .toBeVisible({ timeout: 30000 });
  });
});

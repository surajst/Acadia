const { test, expect } = require('@playwright/test');

test.describe('ACADIA Mastery Task System', () => {
  test.beforeEach(async ({ page }) => {
    await page.goto('/test/reset');
  });

  test('Full mastery task loop end-to-end', async ({ page }) => {
    test.setTimeout(180000);

    // Login as arjun@gmail.com
    await page.goto('/login');
    await page.fill('input[name="username"]', 'arjun@gmail.com');
    await page.fill('input[name="password"]', 'PilotLaunchSecure2026!');
    await page.click('button[type="submit"]');

    // wait for student dashboard
    await page.waitForURL('**/web/student/**', { timeout: 90000 });

    // Navigate to challenges tab first
    await page.locator('[data-tab="challenges"]').first().click();
    await page.waitForFunction(() => {
      const c = document.getElementById('scholastic-tasks-container');
      return c && !c.innerHTML.includes('Loading tasks...');
    }, { timeout: 30000 });

    // Assert Scholastic Challenges section is visible
    await expect(page.locator('text=Scholastic Challenges').first()).toBeVisible();

    // Assert at least one mastery task card visible with XP bounty shown
    await expect(page.locator('text=XP').first()).toBeVisible();

    // Record initial XP from home tab
    await page.goto('/web/student/portal');
    await page.locator('[data-tab="home"]').first().click();
    await page.waitForLoadState('domcontentloaded');
    const initialXpText = await page.locator('span#portal-total-xp').first().textContent();
    const initialXp = parseInt(initialXpText.trim(), 10);

    // Go to student portal to submit the milestone
    await page.goto('/web/student/portal');
    await page.locator('[data-tab="challenges"]').first().click();
    await page.waitForFunction(() => {
      const c = document.getElementById('scholastic-tasks-container');
      return c && !c.innerHTML.includes('Loading tasks...');
    }, { timeout: 30000 });

    // Fill #proofOfWorkNotes
    await page.locator('#skillName').selectOption({ index: 1 });
    await page.fill('#proofOfWorkNotes', 'I completed this by solving all practice problems');
    await page.click('button:has-text("Submit Milestone Evidence")');

    // Assert success confirmation appears
    await expect(page.locator('text=Milestone submitted successfully!')).toBeVisible();

    // Wait for post-submit navigation to settle
    await page.waitForLoadState('domcontentloaded');

    // Assert submission status shows PENDING (in History tab)
    await page.goto('/web/student/portal');
    await page.locator('[data-tab="profile"]').first().click();
    await page.waitForLoadState('domcontentloaded');
    await expect(page.locator('#tab-profile').locator('text=PENDING').first()).toBeVisible();

    // Logout via cookie clear (student portal logout button is sr-only)
    await page.context().clearCookies();

    // Login as teacher@greenwood.com
    await page.goto('/login');
    await page.fill('input[name="username"]', 'teacher@greenwood.com');
    await page.fill('input[name="password"]', 'PilotLaunchSecure2026!');
    await page.click('button[type="submit"]');

    // wait for teacher dashboard
    await page.waitForURL('**/web/teacher/dashboard', { timeout: 90000 });

    // Assert arjun@gmail.com submission appears in Verification Queue
    await expect(page.locator('text=Arjun Sharma').first()).toBeVisible();

    // Assert proof of work notes text is visible
    await expect(page.locator('text=I completed this by solving all practice problems')).toBeVisible();

    // Click "Approve Task", confirm dialog
    page.once('dialog', dialog => dialog.accept());
    await page.locator('div').filter({ hasText: 'I completed this by solving all practice problems' }).locator('button:has-text("Approve Task")').first().click();

    // Wait for the page to reload after approval (redirect + page load)
    await page.waitForLoadState('domcontentloaded');
    await page.waitForURL('**/web/teacher/dashboard*', { timeout: 30000 }).catch(() => {});

    // Wait for the queue item to disappear
    await expect(page.locator('text=I completed this by solving all practice problems')).toBeHidden({ timeout: 15000 });

    // Logout via cookie clear
    await page.context().clearCookies();

    // Login as arjun@gmail.com
    await page.goto('/login');
    await page.fill('input[name="username"]', 'arjun@gmail.com');
    await page.fill('input[name="password"]', 'PilotLaunchSecure2026!');
    await page.click('button[type="submit"]');
    await page.waitForURL('**/web/student/*', { timeout: 90000 });

    // Go to history tab
    await page.goto('/web/student/portal');
    await page.locator('[data-tab="profile"]').first().click();
    await page.waitForLoadState('domcontentloaded');
    await expect(page.locator('#tab-profile').locator('text=APPROVED').first()).toBeVisible();

    // Assert School XP increased (expecting non-zero XP since it was reset to 0 and now we earned bounty)
    await page.goto('/web/student/portal');
    await page.locator('[data-tab="home"]').first().click();
    await page.waitForLoadState('domcontentloaded');
    // Assert XP increased by 250
    await expect(page.locator('span#portal-total-xp').first()).toHaveText(String(initialXp + 250));

    // Logout via cookie clear
    await page.context().clearCookies();

    // Reset again
    await page.goto('/test/reset');

    // Login as arjun@gmail.com, submit same task again
    await page.goto('/login');
    await page.fill('input[name="username"]', 'arjun@gmail.com');
    await page.fill('input[name="password"]', 'PilotLaunchSecure2026!');
    await page.click('button[type="submit"]');
    await page.waitForURL('**/web/student/**', { timeout: 90000 });
    
    await page.goto('/web/student/portal');
    await page.locator('[data-tab="challenges"]').first().click();
    await page.waitForFunction(() => {
      const c = document.getElementById('scholastic-tasks-container');
      return c && !c.innerHTML.includes('Loading tasks...');
    }, { timeout: 30000 });
    await page.locator('#skillName').selectOption({ index: 1 });
    await page.fill('#proofOfWorkNotes', 'I completed this by solving all practice problems');
    await page.click('button:has-text("Submit Milestone Evidence")');
    await page.waitForLoadState('domcontentloaded');

    // Logout via cookie clear
    await page.context().clearCookies();

    // Login as teacher@greenwood.com, reject with reason "Insufficient proof"
    await page.goto('/login');
    await page.fill('input[name="username"]', 'teacher@greenwood.com');
    await page.fill('input[name="password"]', 'PilotLaunchSecure2026!');
    await page.click('button[type="submit"]');
    await page.waitForURL('**/web/teacher/dashboard', { timeout: 90000 });

    // Reject the task and enter reason
    page.once('dialog', dialog => dialog.accept('Insufficient proof'));
    await page.locator('div').filter({ hasText: 'I completed this by solving all practice problems' }).locator('button:has-text("Reject")').first().click();

    // Wait for page reload after rejection
    await page.waitForLoadState('domcontentloaded');
    await page.waitForURL('**/web/teacher/dashboard*', { timeout: 30000 }).catch(() => {});

    // Assert item disappears from queue
    await expect(page.locator('text=I completed this by solving all practice problems')).toBeHidden({ timeout: 15000 });

    // Logout via cookie clear
    await page.context().clearCookies();

    // Login as arjun@gmail.com, assert rejection reason "Insufficient proof" visible
    await page.goto('/login');
    await page.fill('input[name="username"]', 'arjun@gmail.com');
    await page.fill('input[name="password"]', 'PilotLaunchSecure2026!');
    await page.click('button[type="submit"]');
    await page.waitForURL('**/web/student/*', { timeout: 90000 });
    
    await page.goto('/web/student/portal');
    await page.locator('[data-tab="profile"]').first().click();
    await page.waitForLoadState('domcontentloaded');
    await expect(page.locator('#tab-profile').locator('text=Insufficient proof')).toBeVisible();

    // Logout via cookie clear
    await page.context().clearCookies();
  });
});

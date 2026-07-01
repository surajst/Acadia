const { test, expect } = require('@playwright/test');

test.describe('ACADIA Syllabus Verification System', () => {
  test.beforeEach(async ({ request }) => {
    // Reset via /test/reset
    await request.get('/test/reset');
  });

  test('Full teacher syllabus verification loop', async ({ page, request }) => {
    test.setTimeout(180000);

    // Helper: login as student and navigate to syllabus portal
    async function loginAsStudent() {
      await page.goto('/login');
      await page.fill('input[name="username"]', 'arjun@gmail.com');
      await page.fill('input[name="password"]', 'PilotLaunchSecure2026!');
      await Promise.all([
        page.waitForURL('**/web/student/**', { timeout: 90000 }),
        page.click('button:has-text("Sign in")')
      ]);
      // Navigate to the syllabus section of the student portal
      await page.goto('/web/student/portal');
      await page.waitForLoadState('domcontentloaded');
      await page.locator('[data-tab="syllabus"]').first().click();
      // Wait for syllabus JS fetch to complete and skeleton to hide
      await page.waitForFunction(() => {
        const skeleton = document.getElementById('syllabus-skeleton');
        return skeleton && skeleton.classList.contains('hidden');
      }, { timeout: 30000 });
    }

    // Helper: login as teacher
    async function loginAsTeacher() {
      await page.goto('/login');
      await page.fill('input[name="username"]', 'teacher@greenwood.com');
      await page.fill('input[name="password"]', 'PilotLaunchSecure2026!');
      await Promise.all([
        page.waitForURL('**/web/teacher/dashboard*', { timeout: 90000 }),
        page.click('button:has-text("Sign in")')
      ]);
      await page.waitForLoadState('domcontentloaded');
      await page.waitForURL('**/web/teacher/dashboard*', { timeout: 90000 });
    }

    // Helper: logout via form submit
    async function logout() {
      await Promise.all([
        page.waitForURL('**/login?logout*', { timeout: 90000 }),
        page.click('button:has-text("Logout")')
      ]);
    }

    // Step 1: Student marks Chapter 1 of Science complete
    await loginAsStudent();

    const scienceCard = page.locator('div[id="subject-card-SCIENCE"]');
    await expect(scienceCard).toBeVisible({ timeout: 15000 });
    await scienceCard.locator('button:has-text("Show / Hide Chapters")').click();
    
    // Click first Mark Complete button
    const markCompleteBtn = scienceCard.locator('button:has-text("Mark Complete")').first();
    await expect(markCompleteBtn).toBeVisible({ timeout: 10000 });
    await markCompleteBtn.click();

    // Assert button changes to Pending
    await expect(scienceCard.locator('text=Pending').first()).toBeVisible({ timeout: 10000 });
    // XP unchanged (still 0 since not yet approved)
    const xpText = await page.locator('#syllabus-total-xp').textContent();
    expect(xpText.trim()).toBe('0 XP');

    // Logout
    await page.goto('/web/student/portal');
    await logout();

    // Step 2: Teacher approves
    await loginAsTeacher();

    await expect(page.locator('h2:has-text("Syllabus Verification Queue")')).toBeVisible({ timeout: 10000 });

    const progressRow = page.locator('[id^="progress-row-"]').first();
    const approveBtn = progressRow.locator('button:has-text("Approve")');
    await expect(approveBtn).toBeVisible({ timeout: 10000 });

    page.once('dialog', dialog => dialog.accept());
    await approveBtn.click();
    await expect(approveBtn).not.toBeVisible({ timeout: 10000 });

    // Logout
    await logout();

    // Step 3: Student verifies approved status and XP increased
    await loginAsStudent();

    const scienceCard2 = page.locator('div[id="subject-card-SCIENCE"]');
    await expect(scienceCard2).toBeVisible({ timeout: 15000 });
    await scienceCard2.locator('button:has-text("Show / Hide Chapters")').click();
    // At least one chapter shows the checkmark (approved icon ✓)
    await expect(scienceCard2.locator('span:has-text("✓")').first()).toBeVisible({ timeout: 10000 });
    const newXpText = await page.locator('#syllabus-total-xp').textContent();
    expect(newXpText.trim()).not.toBe('0 XP');

    // Logout
    await page.goto('/web/student/portal');
    await logout();

    // Step 4: Reset, then test rejection flow
    await request.get('/test/reset');

    await loginAsStudent();

    const scienceCard3 = page.locator('div[id="subject-card-SCIENCE"]');
    await expect(scienceCard3).toBeVisible({ timeout: 15000 });
    await scienceCard3.locator('button:has-text("Show / Hide Chapters")').click();
    const markCompleteBtn2 = scienceCard3.locator('button:has-text("Mark Complete")').first();
    await expect(markCompleteBtn2).toBeVisible({ timeout: 10000 });
    await markCompleteBtn2.click();
    await expect(scienceCard3.locator('text=Pending').first()).toBeVisible({ timeout: 10000 });

    // Logout
    await page.goto('/web/student/portal');
    await logout();

    // Step 5: Teacher rejects with reason
    await loginAsTeacher();

    const progressRow2 = page.locator('[id^="progress-row-"]').first();
    const rejectBtn = progressRow2.locator('button:has-text("Reject")');
    await expect(rejectBtn).toBeVisible({ timeout: 10000 });
    
    page.once('dialog', dialog => {
      dialog.accept('Needs more detail');
    });
    await rejectBtn.click();
    await expect(rejectBtn).not.toBeVisible({ timeout: 10000 });

    // Logout
    await logout();

    // Step 6: Student sees rejection with reason
    await loginAsStudent();

    const scienceCard4 = page.locator('div[id="subject-card-SCIENCE"]');
    await expect(scienceCard4).toBeVisible({ timeout: 15000 });
    await scienceCard4.locator('button:has-text("Show / Hide Chapters")').click();
    // Rejected chapter shows ❌ icon
    await expect(scienceCard4.locator('span:has-text("❌")').first()).toBeVisible({ timeout: 10000 });
    await expect(scienceCard4.locator('text=Needs more detail').first()).toBeVisible({ timeout: 10000 });
  });
});

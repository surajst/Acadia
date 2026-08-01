const { test, expect } = require('@playwright/test');

async function login(page, username, password) {
  await page.goto('/login');
  await page.fill('#username', username);
  await page.fill('#password', password);
  await page.click('button[type="submit"]');
  await page.waitForURL(url => url.pathname.includes('/web/') && !url.pathname.includes('/login'), { timeout: 90000 });
}

test.describe('ACADIA End-to-End Simulation & Regression Sweep', () => {

  test('Test 1: Admin Statistics & Classroom Progress Check', async ({ page }) => {
    // 1. Login as Admin
    await login(page, 'admin@greenwood.com', 'PilotLaunchSecure2026!');

    // 2. Navigate to Admin Dashboard
    await page.goto('/web/admin/dashboard');

    // 3. Verify statistics cards
    // Admin dashboard shows attendance percentage and absences / total students
    const attendanceEl = page.locator('h3:has-text("%")').first();
    await expect(attendanceEl).toBeVisible();
    
    const absencesEl = page.locator('h3:has-text("/")').first();
    await expect(absencesEl).toBeVisible();
  });

  test('Test 2: Teacher Daily Attendance Toggles & Ledger Saving', async ({ page }) => {
    // 1. Login as Teacher
    await login(page, 'teacher@greenwood.com', 'PilotLaunchSecure2026!');

    // 2. Navigate to Daily Attendance Log page
    await page.goto('/web/teacher/attendance');

    // 3. Select the "Absent" chip/button for the first student
    const firstAbsentButton = page.locator('button[id^="btn_absent_"]').first();
    await firstAbsentButton.click();

    // 4. Submit the daily attendance log
    const saveButton = page.locator('button:has-text("Save Daily Attendance Log")');
    await saveButton.click();

    // 5. Click the "Save & Dispatch" confirm button inside the modal dialog
    const confirmButton = page.locator('button:has-text("Save & Dispatch")');
    await confirmButton.click();

    // 6. Assert success toast matches "Attendance Ledger Saved Successfully."
    const successToast = page.locator('#toast, .toast-enter');
    await expect(successToast).toBeVisible();
    await expect(successToast).toContainText('Attendance Ledger Saved Successfully.');
  });

  test('Test 3: Parent-Student Quest Bounty & Dual-Deduction Math Sweep', async ({ page }) => {
    // Reset DB state for Arjun to guarantee idempotency across persistent H2 runs
    await page.goto('/test/reset');

    // Log out to clear session
    await page.context().clearCookies();

    // ----------------------------------------------------
    // STEP 3.1: Log in as Ramesh (Parent) and Assign Quest
    // ----------------------------------------------------
    await login(page, 'ramesh@gmail.com', 'PilotLaunchSecure2026!');
    await page.goto('/web/parent/dashboard');
    await page.locator('[data-tab="profile"]').first().click();
    const questChild = page.locator('#profile-assignStudentId option', { hasText: 'Arjun' }).first();
    await page.selectOption('#profile-assignStudentId', await questChild.getAttribute('value'));
    await page.fill('#profile-taskTitle', 'Clean Room');
    await page.fill('#profile-xpBounty', '200');
    await page.locator('#assignTaskForm-profile button[type="submit"]').click();

    // Verify assignment redirect/toast success indicator
    await page.waitForURL(url => url.pathname.includes('/web/parent/dashboard'), { timeout: 90000 });
    const parentToast = page.locator('#toast');
    await expect(parentToast).toBeVisible();
    await expect(parentToast).toContainText('Home task assigned successfully!');

    // Log out to clear session
    await page.context().clearCookies();

    // ----------------------------------------------------
    // STEP 3.2: Log in as Arjun (Student) and Claim Quest
    // ----------------------------------------------------
    await login(page, 'arjun@gmail.com', 'PilotLaunchSecure2026!');
    await page.goto('/web/student/portal');
    await page.waitForLoadState('networkidle');
    // Wait, the claim button is in the challenges tab.
    await page.locator('[data-tab="challenges"]').first().click();
    await page.waitForLoadState('networkidle');
    const questCard = page.locator('div.rounded-2xl', { hasText: 'Clean Room' })
      .filter({ has: page.locator('button:has-text("Mark Done")') });
    await expect(questCard).toBeVisible();
    await questCard.locator('button:has-text("Mark Done")').click();

    // Verify claim redirect/toast success indicator
    await page.waitForLoadState('networkidle');
    await page.waitForURL(/.*\/web\/student\/portal.*/, { timeout: 90000 });
    const studentToast = page.locator('#toast');
    await expect(studentToast).toBeVisible();
    await expect(studentToast).toContainText('Home quest marked as completed!');

    // Log out
    await page.context().clearCookies();

    // ----------------------------------------------------
    // STEP 3.3: Log in as Ramesh (Parent) and Approve Quest
    // ----------------------------------------------------
    await login(page, 'ramesh@gmail.com', 'PilotLaunchSecure2026!');
    await page.goto('/web/parent/dashboard');
    await page.waitForLoadState('domcontentloaded');
    // The Approval Queue lives in the hidden Quests tab; approval is an AJAX POST.
    await page.locator('[data-tab="quests"]').first().click();
    const approveBtn = page.locator('.approve-quest-btn').first();
    await expect(approveBtn).toBeVisible();
    const [approveResp] = await Promise.all([
      page.waitForResponse(r => r.url().includes('/api/parent/approve-quest/') && r.request().method() === 'POST'),
      approveBtn.click(),
    ]);
    expect(approveResp.ok()).toBeTruthy();

    // ----------------------------------------------------
    // STEP 3.4: Add Custom Reward as Ramesh
    // ----------------------------------------------------
    // The custom reward form is on the main page
    await page.goto('/web/parent/dashboard');
    await page.locator('[data-tab="profile"]').first().click();
    const rewardChild = page.locator('#rewardStudentId option', { hasText: 'Arjun' }).first();
    await page.selectOption('#rewardStudentId', await rewardChild.getAttribute('value'));
    await page.fill('#rewardTitle', 'Pizza Party');
    await page.fill('#xpCost', '350');
    await page.click('button:has-text("Create Reward")');

    // Verify reward creation redirect/toast success indicator
    await page.waitForURL(url => url.pathname.includes('/web/parent/dashboard'), { timeout: 90000 });
    const rewardToast = page.locator('#toast');
    await expect(rewardToast).toBeVisible();
    await expect(rewardToast).toContainText('Custom reward added successfully!');

    // Log out
    await page.context().clearCookies();

    // ----------------------------------------------------
    // STEP 3.5: Log in as Arjun (Student) and Redeem Custom Reward
    // ----------------------------------------------------
    await login(page, 'arjun@gmail.com', 'PilotLaunchSecure2026!');
    
    // Go to rewards tab in student portal
    await page.goto('/web/student/portal');
    await page.locator('[data-tab="rewards"]').first().click();
    await page.waitForLoadState('networkidle');

    // Assert that the custom parent reward "Pizza Party" is present
    const rewardTitleEl = page.locator('h4:has-text("Pizza Party")').first();
    await expect(rewardTitleEl).toBeVisible();

    // Click the "Redeem Reward" button for "Pizza Party"
    const redeemParentRewardLink = page.locator('form[action*="/web/student/reward/"][action$="/redeem"] button').first();
    await redeemParentRewardLink.click();

    // Verify redemption redirect/toast success indicator
    await page.waitForURL(url => url.pathname.includes('/web/student/portal'), { timeout: 90000 });
    const redeemToast = page.locator('#toast');
    await expect(redeemToast).toBeVisible();
    await expect(redeemToast).toContainText('Custom reward redeemed successfully!');

    // ----------------------------------------------------
    // STEP 3.6: Verify Dual-Deduction XP Balance Mathematics
    // ----------------------------------------------------
    // Arjun started with schoolXp = 300 and parentXp = 100.
    // Quest gave Arjun +200 parentXp -> parentXp = 300.
    // Custom reward cost is 350.
    // Dual-deduction rules: consume 300 parentXp, then remaining 50 from schoolXp -> schoolXp = 250.
    // Total schoolXp displayed in dashboard (metrics card) must drop from 300 to 250 exactly.
    // The metric display shows <span th:text="${totalXp}"> inside metrics card.
    await page.goto('/web/student/portal');
    await page.waitForLoadState('networkidle');
    const totalXpEl = page.locator('span:near(span:has-text("Total XP"))').first();
    await expect(totalXpEl).toHaveText('250');

    // Log out
    await page.context().clearCookies();

    // ----------------------------------------------------
    // STEP 3.7: Log in as Ramesh and Release Reward
    // ----------------------------------------------------
    await login(page, 'ramesh@gmail.com', 'PilotLaunchSecure2026!');
    await page.goto('/web/parent/dashboard');
    await page.waitForLoadState('networkidle');
    // The Deliveries Pending queue lives in the hidden Quests tab.
    await page.locator('[data-tab="quests"]').first().click();
    const releaseRewardLink = page.locator('button:has-text("Release & Mark Delivered")').first();
    await expect(releaseRewardLink).toBeVisible();
    await releaseRewardLink.click();

    // Verify release redirect/toast success indicator
    await page.waitForURL(url => url.pathname.includes('/web/parent/dashboard'), { timeout: 90000 });
    const releaseToast = page.locator('#toast');
    await expect(releaseToast).toBeVisible();
    await expect(releaseToast).toContainText('Reward successfully delivered/released!');
  });

});

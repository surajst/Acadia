const { test, expect } = require('@playwright/test');

async function login(page, username, password) {
  await page.goto('/login');
  await page.fill('#username', username);
  await page.fill('#password', password);
  await page.click('button[type="submit"]');
  await page.waitForURL(url => url.pathname.includes('/web/') && !url.pathname.includes('/login'), { timeout: 90000 });
}

test.describe('ACADIA Custom Parent Rewards Delivery Flow', () => {

  test.beforeEach(async ({ page }) => {
    // Reset database state to guarantee baseline roster and XP balances
    await page.goto('/test/reset');
  });

  test('TC-15/16/17/18/19: Create custom reward, redeem, verify dual-deduction math, and release delivery loop', async ({ page }) => {
    // 1. Login as Parent (Ramesh)
    await login(page, 'ramesh@gmail.com', 'PilotLaunchSecure2026!');

    // 2. Navigate to the Parent Dashboard (the /portal route now redirects here)
    await page.goto('/web/parent/dashboard');
    await page.waitForLoadState('networkidle');

    // 3. The "Create Custom Reward" form lives in the hidden Profile tab panel.
    //    Click the Profile tab to reveal it before interacting.
    await page.locator('[data-tab="profile"]').first().click();

    // 4. Fill out the "Create Custom Reward" form (child selection is now required)
    const uniqueRewardTitle = 'Weekend Campfire Night: ' + Math.random().toString(36).substring(7);
    const rewardChild = page.locator('#rewardStudentId option', { hasText: 'Arjun' }).first();
    await page.selectOption('#rewardStudentId', await rewardChild.getAttribute('value'));
    await page.fill('#rewardTitle', uniqueRewardTitle);
    await page.fill('#xpCost', '150');

    // 5. Submit form
    await page.click('button:has-text("Create Reward")');

    // 6. Verify success redirect and toast message
    await page.waitForLoadState('networkidle');
    await page.waitForURL(url => url.pathname.includes('/web/parent/dashboard'), { timeout: 90000 });
    await expect(page.locator('#toast')).toContainText('Custom reward added successfully!');

    // 6. Logout
    await page.context().clearCookies();

    // 7. Login as Student (Arjun)
    await login(page, 'arjun@gmail.com', 'PilotLaunchSecure2026!');

    // 8. Navigate to Student Portal (Rewards Tab)
    await page.goto('/web/student/portal');
    await page.locator('[data-tab="rewards"]').first().click();
    await page.waitForLoadState('networkidle');

    // 9. Assert parent-defined reward is visible and redeemable
    const customRewardTitle = page.locator(`h4:has-text("${uniqueRewardTitle}")`).first();
    await expect(customRewardTitle).toBeVisible();

    // The parent-defined reward redeem control is now a POST form button (reskin).
    const redeemBtn = page.locator(`form[action*="/web/student/reward/"][action$="/redeem"] button`).first();
    await expect(redeemBtn).toBeVisible();
    await redeemBtn.click();

    // 10. Verify redemption success toast and status change
    await page.waitForLoadState('networkidle');
    await page.waitForURL(url => url.pathname.includes('/web/student/portal'), { timeout: 90000 });
    await expect(page.locator('#toast')).toContainText('Custom reward redeemed successfully!');

    // Verify Dual-Deduction XP Balance Mathematics
    // Arjun started with schoolXp = 300 and parentXp = 100.
    // Custom reward cost is 150.
    // Dual-deduction rules: consume 100 parentXp, then remaining 50 from schoolXp -> schoolXp = 250.
    // Total schoolXp displayed in dashboard (metrics card) must drop from 300 to 250 exactly.
    await page.goto('/web/student/portal');
    // Note: total XP is on the default home tab!
    await page.waitForLoadState('networkidle');
    const totalXpEl = page.locator('span:near(span:has-text("Total XP"))').first();
    await expect(totalXpEl).toHaveText('250');

    // 11. Logout
    await page.context().clearCookies();

    // 12. Login as Parent (Ramesh)
    await login(page, 'ramesh@gmail.com', 'PilotLaunchSecure2026!');

    // 13. Navigate to the Parent Dashboard
    await page.goto('/web/parent/dashboard');
    await page.waitForLoadState('networkidle');

    // 14. The "Deliveries Pending" queue lives in the hidden Quests tab panel.
    //     Click the Quests tab to reveal it, then Release & Mark Delivered.
    await page.locator('[data-tab="quests"]').first().click();
    const releaseBtn = page.locator('button:has-text("Release & Mark Delivered")').first();
    await expect(releaseBtn).toBeVisible();
    await releaseBtn.click();

    // 15. Verify release success toast
    await page.waitForLoadState('networkidle');
    await page.waitForURL(url => url.pathname.includes('/web/parent/dashboard'), { timeout: 90000 });
    await expect(page.locator('#toast')).toContainText('Reward successfully delivered/released!');
  });

});

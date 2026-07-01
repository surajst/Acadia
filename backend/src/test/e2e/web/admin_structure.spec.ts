import { test, expect } from '@playwright/test';

// Helper function to log in securely
async function login(page: any, username: string, password: string) {
  await page.goto('/login');
  await page.fill('#username', username);
  await page.fill('#password', password);
  await page.click('button[type="submit"]');
  // Wait until logged in (redirected to dashboard/web context) and not the login page
  await page.waitForURL((url: URL) => url.pathname.includes('/web/') && !url.pathname.includes('/login'));
}

test.describe('ACADIA Student Lifecycle Operations Management Specs', () => {

  test('Test: Register and Delete Student Workflow', async ({ page }) => {
    // 1. Reset database state to guarantee baseline student scale (500+ students)
    await page.goto('/test/reset');

    // 2. Login as Administrator
    await login(page, 'admin@greenwood.com', 'PilotLaunchSecure2026!');

    // 3. Navigate to Admin Configuration Dashboard
    await page.goto('/web/admin/management');

    // 4. Verify baseline aggregate count of active students is at the baseline scale (500+)
    const initialCountText = await page.locator('h3', { hasText: /^\d+$/ }).nth(0).innerText();
    const initialCount = parseInt(initialCountText, 10);
    expect(initialCount).toBeGreaterThanOrEqual(500);

    // 5. Open the Sleek Glassmorphic Registration Modal
    await page.click('button:has-text("Register New Student")');
    await expect(page.locator('#registerStudentModal')).toBeVisible();

    // 6. Fill out and submit student registration parameters
    await page.fill('#firstName', 'Simran');
    await page.fill('#lastName', 'Kaur');
    await page.fill('#rollNumber', '6A-41');
    await page.selectOption('#schoolClassId', { label: 'Grade 6 - A' });
    await page.click('button:has-text("Register Student")');

    // 7. Verify redirect and assert student count instantly increments by 1
    await page.waitForURL((url: URL) => url.pathname.includes('/web/admin/management'));
    const incrementedCountText = await page.locator('h3', { hasText: /^\d+$/ }).nth(0).innerText();
    const incrementedCount = parseInt(incrementedCountText, 10);
    expect(incrementedCount).toBe(initialCount + 1);

    // 8. Navigate to Master Roster Hub for Grade 6 - A to trigger removal
    await page.goto('/web/admin/dashboard?classId=11111111-2222-3333-4444-555555555551');

    // 9. Find the row for 'Simran Kaur' and click the quick-action delete trigger (🗑️)
    await page.fill('#rosterSearchInput', 'Simran');
    const studentRow = page.locator('tr:has-text("Simran Kaur")').first();
    await expect(studentRow).toBeVisible();
    const deleteTrigger = studentRow.locator('button:has-text("🗑️")');
    await deleteTrigger.click();

    // 10. Verify confirmation modal appears with warning details
    await expect(page.locator('#deleteModal')).toBeVisible();

    // 11. Click the confirmation trigger to execute deletion
    await page.click('#deleteForm button[type="submit"]');

    // 12. Verify redirect back to Admin Dashboard and check count returns to baseline
    await page.waitForURL((url: URL) => url.pathname.includes('/web/admin/management'));
    const finalCountText = await page.locator('h3', { hasText: /^\d+$/ }).nth(0).innerText();
    const finalCount = parseInt(finalCountText, 10);
    expect(finalCount).toBe(initialCount);
  });

});

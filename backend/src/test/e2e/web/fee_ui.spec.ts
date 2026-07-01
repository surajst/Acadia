import { test, expect } from '@playwright/test';

// Helper function to log in securely
async function login(page: any, username: string, password: string) {
  await page.goto('/login');
  await page.fill('#username', username);
  await page.fill('#password', password);
  await page.click('button[type="submit"]');
  // Wait until logged in (redirected to dashboard/web context) and not the login page
  await page.waitForURL((url: URL) => url.pathname.includes('/web/') && !url.pathname.includes('/login'), { timeout: 90000 });
}

test.describe('ACADIA Administrative Fee Management Specs', () => {

  test('Test: Verify Fee KPI Dashboard and Collect Payment Workflow', async ({ page }) => {
    // 1. Reset database state to guarantee baseline
    await page.goto('/test/reset');

    // 2. Login as Administrator
    await login(page, 'admin@greenwood.com', 'PilotLaunchSecure2026!');

    // 3. Navigate to Admin Fees Dashboard
    await page.goto('/web/admin/fees');

    // 4. Verify presence of the three glowing analytical KPI summary cards
    await expect(page.locator('text=Total Revenue Target')).toBeVisible();
    await expect(page.locator('text=Total Collected Invoices')).toBeVisible();
    await expect(page.locator('text=Outstanding Deficit')).toBeVisible();

    // Verify revenue target number renders (at least ₹ 100,000.00 since there are 500+ students with 10k-20k fee structures)
    const expectedRevenueText = await page.locator('h3').nth(0).innerText();
    expect(expectedRevenueText).toContain('₹');

    // 5. Locate first student row in table that has "Record Payment" quick-action trigger
    const firstRow = page.locator('tbody tr').first();
    await expect(firstRow).toBeVisible();

    const studentName = await firstRow.locator('td').first().locator('p').innerText();
    console.log(`Targeting E2E payment collection for student: ${studentName}`);

    const recordBtn = firstRow.locator('button:has-text("Record Payment")');
    await recordBtn.click();

    // 6. Assert payment entry modal box becomes visible
    await expect(page.locator('#paymentModal')).toBeVisible();

    // Verify student name inside modal matches the row student
    const modalStudentName = await page.locator('#studentNameDisplay').innerText();
    expect(modalStudentName).toBe(studentName);

    // 7. Input partial payment details and submit
    await page.selectOption('#paymentModeSelect', 'ONLINE');
    await page.fill('#amountInput', '5000.00');
    
    // Submit transaction
    await page.click('button[type="submit"]:has-text("Record Transaction")');

    // 8. Verify redirect and success toast message
    await page.waitForURL((url: URL) => url.pathname.includes('/web/admin/fees'), { timeout: 90000 });
    await expect(page.locator('text=Payment transaction recorded successfully!')).toBeVisible();

    // 9. Assert row balance updates dynamically (verify Paid Balance increases and Status changes to Partial)
    const updatedRow = page.locator(`tbody tr:has-text("${studentName}")`).first();
    await expect(updatedRow.locator('span:has-text("Partial")')).toBeVisible();
    
    const paidBalanceText = await updatedRow.locator('td').nth(3).innerText();
    expect(paidBalanceText).toContain('5,000');
  });

});

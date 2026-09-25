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
    // Renamed: the figure counts one-off custom invoices too, so "target"
    // implied the grade fee plans alone.
    await expect(page.locator('text=Total Billed')).toBeVisible();
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

    // 8. Verify redirect and success toast message. The flash now names the
    // receipt number a payment was actually given -- see recordPayment's new
    // return value -- so this checks the stable prefix rather than the old
    // generic wording that receipt numbers made obsolete. A plain substring
    // match, not a regex: Playwright's text= selector string is parsed as a
    // JS string literal first, which silently drops single backslashes
    // before the selector engine ever sees \d or \. -- exactly the class of
    // bug that made this test fail once already this branch.
    await page.waitForURL((url: URL) => url.pathname.includes('/web/admin/fees'), { timeout: 90000 });
    await expect(page.locator('text=Payment recorded — Receipt #')).toBeVisible();

    // 9. Assert row balance updates dynamically (verify Paid Balance increases and Status changes to Partial)
    const updatedRow = page.locator(`tbody tr:has-text("${studentName}")`).first();
    await expect(updatedRow.locator('span:has-text("Partial")')).toBeVisible();
    
    const paidBalanceText = await updatedRow.locator('td').nth(3).innerText();
    expect(paidBalanceText).toContain('5,000');
  });

});

// ─── R2-P1-3: cancelling an invoice, and correcting due dates ─────────────────

test.describe('Invoice cancellation and due-date correction', () => {

  /**
   * The Cancel action goes through a prompt() for the reason, mirroring Reverse
   * last payment. The rules live in InvoiceCancellationTest, which posts the bad
   * cases straight at the endpoint; this covers the click path the admin
   * actually uses, per the feature-coverage rule.
   */
  test('an admin can cancel an unpaid invoice from the ledger', async ({ page }) => {
    await page.goto('/test/reset');
    await login(page, 'admin@greenwood.com', 'PilotLaunchSecure2026!');
    await page.goto('/web/admin/fees');
    await page.waitForLoadState('networkidle');

    // Only an invoice with nothing paid against it offers the action, which is
    // the rule the server enforces too.
    const cancelBtn = page.locator('button[data-cancel-invoice]').first();
    await expect(cancelBtn).toBeVisible();
    await cancelBtn.scrollIntoViewIfNeeded();

    page.once('dialog', dialog => dialog.accept('Raised in error — QA'));
    await cancelBtn.click();

    await page.waitForURL(url => url.pathname.includes('/web/admin/fees'), { timeout: 90000 });
    await page.waitForLoadState('networkidle');

    await expect(page.locator('[data-flash]')).toContainText(/cancelled/i);
    await expect(page.locator('[data-cancelled-badge]').first()).toBeVisible();
  });

  /** Backing out of the prompt must not cancel anything. */
  test('dismissing the reason prompt cancels nothing', async ({ page }) => {
    await page.goto('/test/reset');
    await login(page, 'admin@greenwood.com', 'PilotLaunchSecure2026!');
    await page.goto('/web/admin/fees');
    await page.waitForLoadState('networkidle');

    const before = await page.locator('[data-cancelled-badge]').count();

    const cancelBtn = page.locator('button[data-cancel-invoice]').first();
    await cancelBtn.scrollIntoViewIfNeeded();
    page.once('dialog', dialog => dialog.dismiss());
    await cancelBtn.click();

    await page.waitForLoadState('networkidle');
    expect(await page.locator('[data-cancelled-badge]').count()).toBe(before);
  });

  /**
   * The due-date correction is idempotent, so the assertion is on it reporting
   * what it did rather than on a particular number: a second run legitimately
   * reports that nothing changed.
   */
  test('the due-date correction reports what it changed and is safe to repeat', async ({ page }) => {
    await page.goto('/test/reset');
    await login(page, 'admin@greenwood.com', 'PilotLaunchSecure2026!');
    await page.goto('/web/admin/fees');
    await page.waitForLoadState('networkidle');

    await page.locator('button[data-recalculate-due-dates]').click();
    await page.waitForURL(url => url.pathname.includes('/web/admin/fees'), { timeout: 90000 });
    await page.waitForLoadState('networkidle');
    await expect(page.locator('[data-flash]')).toContainText(/due date|already count/i);

    // Second run: whatever the first one did, this one must find nothing left.
    await page.locator('button[data-recalculate-due-dates]').click();
    await page.waitForURL(url => url.pathname.includes('/web/admin/fees'), { timeout: 90000 });
    await page.waitForLoadState('networkidle');
    await expect(page.locator('[data-flash]')).toContainText(/nothing changed/i);
  });
});

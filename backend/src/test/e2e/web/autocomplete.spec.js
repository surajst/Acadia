const { test, expect } = require('@playwright/test');

test('Teacher Tasks Autocomplete Isolate Class 6', async ({ page }) => {
    // 1. Reset and Login
    await page.goto('/test/reset');
    await page.goto('/login');
    await page.fill('#username', 'teacher@greenwood.com');
    await page.fill('#password', 'PilotLaunchSecure2026!');
    await page.click('button[type="submit"]');
    await page.waitForLoadState('domcontentloaded');
    await page.waitForURL(url => url.pathname.includes('/web/') && !url.pathname.includes('/login'), { timeout: 90000 });

    // 2. Go to My Tasks
    await page.goto('/web/teacher/tasks');
    await page.waitForLoadState('domcontentloaded');
    await expect(page.locator('#studentNameSearch')).toBeVisible({ timeout: 30000 });

    // 3. Select Individual Student (click the radio input directly to fire 'change' event)
    await page.locator('input[name="assignedToClass"][value="false"]').click();
    await expect(page.locator('#studentSearchSection')).toBeVisible({ timeout: 10000 });

    // 4. Test Autocomplete
    await page.fill('#studentNameSearch', 'a');
    await page.waitForTimeout(1000); // Wait for debounce — should be hidden (query < 2 chars)
    await expect(page.locator('#studentSearchResults')).toBeHidden();

    await page.fill('#studentNameSearch', 'ar');
    await page.waitForTimeout(1000); // Wait for debounce and fetch

    const results = page.locator('#studentSearchList li');
    const count = await results.count();
    expect(count).toBeGreaterThan(0);

    for (let i = 0; i < count; i++) {
        const text = await results.nth(i).textContent();
        expect(text).not.toContain('Grade'); // Confirm no class label is shown
        expect(text.toLowerCase()).toContain('ar'); // Confirm student name matched the query
    }

    // 5. Select a student
    await results.nth(0).click();
    const hiddenVal = await page.locator('#studentId').inputValue();
    expect(hiddenVal.length).toBeGreaterThan(10); // Ensure UUID is populated
    await expect(page.locator('#studentSearchResults')).toBeHidden();

    // 6. Test Back to Dashboard
    await page.click('text=← Back to Dashboard');
    await page.waitForLoadState('domcontentloaded');
    await expect(page).toHaveURL(/\/web\/teacher\/dashboard/);
    await expect(page.locator('text=Syllabus Verification Queue')).toBeVisible();
});

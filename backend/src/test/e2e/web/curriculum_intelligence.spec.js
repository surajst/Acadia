const { test, expect } = require('@playwright/test');

test.describe('Admin Curriculum Intelligence Dashboard', () => {

  test('Full admin curriculum dashboard test', async ({ page }) => {
    test.setTimeout(90000);

    // Login as admin@greenwood.com
    await page.goto('/login');
    await page.fill('input[name="username"]', 'admin@greenwood.com');
    await page.fill('input[name="password"]', 'PilotLaunchSecure2026!');
    await Promise.all([
      page.waitForURL('**/web/admin/*', { timeout: 90000 }),
      page.click('button:has-text("Sign in")')
    ]);
    await page.waitForLoadState('domcontentloaded');
    await page.waitForURL('**/web/admin/*', { timeout: 90000 });

    // Navigate to /web/admin/curriculum
    await page.goto('/web/admin/curriculum');
    await page.waitForLoadState('domcontentloaded');

    // Assert page title or header contains "Curriculum"
    await expect(page.locator('h1:has-text("Curriculum Progress")')).toBeVisible({ timeout: 30000 });

    // Assert all 4 KPI cards are visible
    const totalStudentsKpi = page.locator('#kpiTotalStudents');
    await expect(totalStudentsKpi).toBeVisible();
    
    // Wait for KPI data to load (fetched via JS after page load)
    await expect(totalStudentsKpi).not.toHaveText('--', { timeout: 20000 });
    const studentsText = await totalStudentsKpi.innerText();
    expect(parseInt(studentsText)).toBeGreaterThan(0);

    await expect(page.locator('p:has-text("Overall Completion")')).toBeVisible();
    await expect(page.locator('#kpiCompletion')).toBeVisible();
    
    await expect(page.locator('p:has-text("Chapters Awaiting")')).toBeVisible();
    await expect(page.locator('#kpiPending')).toBeVisible();
    
    await expect(page.locator('p:has-text("Total XP Awarded")')).toBeVisible();
    await expect(page.locator('#kpiTotalXp')).toBeVisible();

    // Assert Class Completion section is visible
    await expect(page.locator('#barsPanelTitle')).toBeVisible();
    const barsContainer = page.locator('#barsContainer');
    
    // Assert bars exist for Class 5, Class 6, Class 7, Class 8, Class 9, Class 10
    // Assert each bar shows student count in parentheses
    for (const classNum of ['5', '6', '7', '8', '9', '10']) {
        const classTitleLocator = barsContainer.locator(`span:has-text("Class ${classNum}")`);
        await expect(classTitleLocator).toBeVisible();
        await expect(classTitleLocator.locator('span', { hasText: /students/ })).toBeVisible();
    }

    // Assert Needs Attention panel is visible
    await expect(page.locator('h3:has-text("Needs Attention")')).toBeVisible();
    await expect(page.locator('#needsAttentionContainer')).toBeVisible();

    // Assert Top Scholars panel is visible
    await expect(page.locator('h3:has-text("Top Scholars")')).toBeVisible();
    await expect(page.locator('#topScholarsContainer')).toBeVisible();

    // Find the class filter dropdown
    const classFilter = page.locator('#classFilter');
    
    // Select "Class 6" from the dropdown
    await classFilter.selectOption('6');
    // Wait for the bars to update
    await expect(page.locator('#barsPanelTitle:has-text("Subject Completion - Class 6")')).toBeVisible({ timeout: 30000 });

    // Assert class detail view renders with Class 6 data
    await expect(page.locator('#barsPanelTitle:has-text("Subject Completion - Class 6")')).toBeVisible();

    // Select "All Classes" to reset
    await classFilter.selectOption('ALL');
    // Wait for the school-wide view to return
    await expect(page.locator('#barsPanelTitle:has-text("Class Completion Progress")')).toBeVisible({ timeout: 30000 });

    // Assert school-wide view returns
    await expect(page.locator('#barsPanelTitle:has-text("Class Completion Progress")')).toBeVisible();

    // Logout
    await page.goto('/web/admin/dashboard');
    await Promise.all([
        page.waitForURL('**/login?logout*', { timeout: 90000 }),
        page.locator('form[action="/logout"] button').click()
    ]);
  });
});

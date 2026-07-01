const { test, expect } = require('@playwright/test');
const path = require('path');

test('Capture Screenshots', async ({ page, context }) => {
  test.setTimeout(120000);
  const outDir = 'C:\\Users\\st\\.gemini\\antigravity-ide\\brain\\e86492c5-aca5-454d-b0a6-fbc6a0b5d611';

  // 1. Seed demo data
  await page.goto('/test/reset');
  await page.waitForTimeout(2000);

  // 2. Student Portal Screenshots
  await page.goto('/login');
  await page.fill('#username', 'arjun@gmail.com');
  await page.fill('#password', 'PilotLaunchSecure2026!');
  await Promise.all([
    page.waitForURL('**/web/student/**', { timeout: 10000 }),
    page.click('button[type="submit"]')
  ]);

  await page.goto('/web/student/portal');
  await page.waitForLoadState('networkidle');

  // Click Challenges tab
  await page.evaluate(() => switchTab('challenges'));
  await page.waitForTimeout(2000); // wait for render
  await page.screenshot({ path: path.join(outDir, 'student_portal_challenges_final.png') });

  // Click Attendance tab
  await page.evaluate(() => switchTab('attendance'));
  await page.waitForTimeout(2000);
  await page.screenshot({ path: path.join(outDir, 'student_portal_attendance_final.png') });

  // Logout
  await context.clearCookies();

  // 3. Parent Portal Screenshots
  await page.goto('/login');
  await page.fill('#username', 'ramesh@gmail.com');
  await page.fill('#password', 'PilotLaunchSecure2026!');
  await Promise.all([
    page.waitForURL('**/web/parent/**', { timeout: 10000 }),
    page.click('button[type="submit"]')
  ]);

  await page.goto('/web/parent/portal');
  await page.waitForLoadState('networkidle');
  
  // Scroll down to make new sections visible
  await page.evaluate(() => window.scrollBy(0, 500));
  await page.waitForTimeout(500);

  // Open Attendance Section
  await page.evaluate(() => toggleParentSection('attendance'));
  await page.waitForTimeout(2000);
  await page.screenshot({ path: path.join(outDir, 'parent_portal_attendance_final.png') });

  // Close Attendance, Open Syllabus Section
  await page.evaluate(() => toggleParentSection('attendance')); // close
  await page.evaluate(() => toggleParentSection('syllabus')); // open
  await page.waitForTimeout(2000);
  await page.screenshot({ path: path.join(outDir, 'parent_portal_syllabus_final.png') });
});

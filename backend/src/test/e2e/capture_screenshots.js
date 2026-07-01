const { chromium } = require('playwright');
const path = require('path');

async function login(page, username, password) {
  await page.goto('http://localhost:8080/login');
  await page.fill('#username', username);
  await page.fill('#password', password);
  await page.click('button[type="submit"]');
  await page.waitForURL(url => url.pathname.includes('/web/') && !url.pathname.includes('/login'));
}

(async () => {
  const browser = await chromium.launch();
  const context = await browser.newContext();
  const page = await context.newPage();

  const outDir = 'C:\\Users\\st\\.gemini\\antigravity\\brain\\a258ac0b-8c79-496a-9010-9e4b949a70b3\\';

  try {
    // 1. Student Mobile Home
    await page.setViewportSize({ width: 390, height: 844 });
    await login(page, 'student_1', 'PilotLaunchSecure2026!');
    await page.goto('http://localhost:8080/web/student/portal?tab=home');
    await page.waitForLoadState('networkidle');
    await page.screenshot({ path: path.join(outDir, 'student_mobile_home.png') });
    console.log('Saved student_mobile_home.png');

    // 2. Student Mobile Syllabus
    await page.click('#mobile-nav-syllabus'); // specifically click mobile nav
    await page.waitForTimeout(2000);
    await page.screenshot({ path: path.join(outDir, 'student_mobile_syllabus.png') });
    console.log('Saved student_mobile_syllabus.png');

    // 3. Student Desktop Sidebar
    await page.setViewportSize({ width: 1440, height: 900 });
    await page.goto('http://localhost:8080/web/student/portal?tab=home');
    await page.waitForLoadState('networkidle');
    await page.screenshot({ path: path.join(outDir, 'student_desktop_sidebar.png') });
    console.log('Saved student_desktop_sidebar.png');

    // 4. Parent Mobile Home
    const context2 = await browser.newContext();
    const page2 = await context2.newPage();
    
    await page2.setViewportSize({ width: 390, height: 844 });
    await login(page2, 'parent_1', 'PilotLaunchSecure2026!');
    await page2.goto('http://localhost:8080/web/parent/dashboard?tab=home');
    await page2.waitForLoadState('networkidle');
    await page2.screenshot({ path: path.join(outDir, 'parent_mobile_home.png') });
    console.log('Saved parent_mobile_home.png');

  } catch (error) {
    console.error('Error taking screenshots:', error);
  } finally {
    await browser.close();
  }
})();

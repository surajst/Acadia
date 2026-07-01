const { chromium } = require('playwright');
(async () => {
  const browser = await chromium.launch();
  const page = await browser.newPage({ viewport: { width: 1440, height: 900 } });
  await page.goto('http://localhost:8080/login');
  await page.fill('input[name="username"]', 'teacher@greenwood.com');
  await page.fill('input[name="password"]', 'PilotLaunchSecure2026!');
  await page.click('button[type="submit"]');
  await page.waitForURL('**/web/**');
  await page.waitForLoadState('load');
  await page.waitForTimeout(800);
  await page.screenshot({ path: 'd:\\Development\\parent-trust-os\\backend\\src\\test\\e2e\\teacher_dashboard_3classes.png', fullPage: true });
  const url = page.url();
  console.log('Dashboard URL:', url);
  // Also check if there's a classes list
  const classCards = await page.locator('[class*="class"], [class*="card"]').count();
  console.log('Class-related elements:', classCards);
  const bodyText = await page.locator('body').innerText();
  const hasGrade7 = bodyText.includes('Grade 7') || bodyText.includes('grade 7');
  const hasGrade8 = bodyText.includes('Grade 8') || bodyText.includes('grade 8');
  console.log('Has Grade 7:', hasGrade7);
  console.log('Has Grade 8:', hasGrade8);
  await browser.close();
})();

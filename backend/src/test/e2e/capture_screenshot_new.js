const { chromium } = require('playwright');
(async () => {
  const browser = await chromium.launch();
  const page = await browser.newPage();
  await page.setViewportSize({ width: 1280, height: 800 });
  await page.goto('http://localhost:8080/login');
  await page.waitForTimeout(1000); // Wait for animations
  await page.screenshot({ path: 'C:/Users/st/.gemini/antigravity/brain/a258ac0b-8c79-496a-9010-9e4b949a70b3/artifacts/login_screenshot_single.png' });
  await browser.close();
})();

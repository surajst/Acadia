const { chromium } = require('playwright');

(async () => {
  const browser = await chromium.launch();
  const page = await browser.newPage();
  
  // Test teacher_1
  await page.goto('http://localhost:8080/test/reset');
  await page.goto('http://localhost:8080/login');
  await page.fill('#username', 'teacher_1');
  await page.fill('#password', 'PilotLaunchSecure2026!');
  await page.click('button[type="submit"]');
  await page.waitForLoadState('networkidle');
  
  const response1 = await page.evaluate(async () => {
    const res = await fetch('/api/teacher/my-students?q=');
    return res.json();
  });
  console.log('--- Teacher 1 Students (Expected: Class 6) ---');
  console.log(response1);
  
  // Test teacher_2
  await page.goto('http://localhost:8080/test/reset');
  await page.goto('http://localhost:8080/login');
  await page.fill('#username', 'teacher_2');
  await page.fill('#password', 'PilotLaunchSecure2026!');
  await page.click('button[type="submit"]');
  await page.waitForLoadState('networkidle');
  
  const response2 = await page.evaluate(async () => {
    const res = await fetch('/api/teacher/my-students?q=');
    return res.json();
  });
  console.log('\n--- Teacher 2 Students (Expected: Class 7) ---');
  console.log(response2);
  
  await browser.close();
})();

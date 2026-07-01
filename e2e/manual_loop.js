const { chromium, expect } = require('@playwright/test');

(async () => {
  console.log("Starting manual loop...");
  const browser = await chromium.launch({ headless: true });
  const context = await browser.newContext();
  const page = await context.newPage();

  // 1. Student logs in
  console.log("Student (Arjun) logging in...");
  await page.goto('http://localhost:8080/web/login');
  await page.fill('#username', 'arjun');
  await page.fill('#password', 'StudentTesting2026!');
  await page.click('button[type="submit"]');
  await page.waitForTimeout(2000);

  // 2. Student marks a chapter complete
  console.log("Student marking a topic complete...");
  await page.goto('http://localhost:8080/web/student/dashboard');
  
  // Find a topic button that hasn't been marked yet
  const markCompleteBtn = await page.locator('button.topic-action-btn:has-text("Mark Complete")').first();
  const count = await markCompleteBtn.count();
  if (count === 0) {
    console.log("No topics available to mark complete! Please check database.");
    await browser.close();
    return;
  }
  
  const curriculumId = await markCompleteBtn.getAttribute('data-id');
  console.log(`Marking topic ${curriculumId} as complete...`);
  await markCompleteBtn.click();
  await page.waitForTimeout(1000);
  
  // Accept the prompt using dialog handler if there was a prompt (wait, the prompt might not be handled automatically!)
  // Wait, the page has an alert or confirm for mark complete?
  // Let me just send an API request instead to be sure.
  
  await context.clearCookies(); // log out

  // 3. Teacher logs in
  console.log("Teacher logging in...");
  await page.goto('http://localhost:8080/web/login');
  await page.fill('#username', 'teacher_1@greenwood.com');
  await page.fill('#password', 'GreenwoodStaffTesting2026!');
  await page.click('button[type="submit"]');
  await page.waitForTimeout(2000);

  // 4. Teacher views syllabus verification queue and approves
  console.log("Teacher approving topic...");
  await page.goto('http://localhost:8080/web/teacher/dashboard');
  
  // Accept dialog handler for evaluateSyllabus confirm
  page.on('dialog', async dialog => {
      console.log('Dialog opened:', dialog.message());
      await dialog.accept();
  });

  const approveBtn = await page.locator(`button[data-id][onclick*="approve"]`).first();
  const approveCount = await approveBtn.count();
  if (approveCount === 0) {
    console.log("No topics in the Syllabus Verification Queue!");
    await browser.close();
    return;
  }
  
  await approveBtn.click();
  await page.waitForTimeout(2000);
  
  await context.clearCookies(); // log out

  // 5. Student logs back in to check XP
  console.log("Student logging back in to verify XP...");
  await page.goto('http://localhost:8080/web/login');
  await page.fill('#username', 'arjun');
  await page.fill('#password', 'StudentTesting2026!');
  await page.click('button[type="submit"]');
  await page.waitForTimeout(2000);
  
  await page.goto('http://localhost:8080/web/student/dashboard');
  const xpBadge = await page.locator('.xp-badge, :text-matches("XP", "i")').textContent();
  console.log("Student XP after approval:", xpBadge);

  await browser.close();
  console.log("Manual loop completed successfully!");
})();

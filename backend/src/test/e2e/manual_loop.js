const { chromium, expect } = require('@playwright/test');

(async () => {
  console.log("Starting manual loop...");
  const browser = await chromium.launch({ headless: true });
  const context = await browser.newContext();
  
  // Set up dialog handler for the entire context
  context.on('dialog', async dialog => {
      console.log('Dialog opened:', dialog.message());
      await dialog.accept();
  });
  
  const page = await context.newPage();

  console.log("Resetting test environment...");
  await page.goto('http://localhost:8080/test/reset');
  await page.waitForTimeout(2000);

  // 1. Student logs in
  console.log("Student (arjun) logging in...");
  await page.goto('http://localhost:8080/web/login');
  await page.fill('#username', 'arjun');
  await page.fill('#password', 'GreenwoodStaffTesting2026!');
  await page.click('button[type="submit"]');
  await page.waitForTimeout(2000);
  
  // Get initial XP
  const xpBadgeLoc = page.locator('#school-xp-text').first();
  const xpBadgeInit = await xpBadgeLoc.textContent();
  console.log("Initial Student XP:", xpBadgeInit.trim());

  // 2. Student marks a chapter complete
  console.log("Student marking a topic complete...");
  
  const markCompleteBtn = await page.locator('button.topic-action-btn:has-text("Mark Complete")').first();
  const count = await markCompleteBtn.count();
  if (count === 0) {
    console.log("No topics available to mark complete! Checking if there are 'Pending Verification' buttons...");
    const pendingBtn = await page.locator('button:has-text("Pending Verification")').first();
    if (await pendingBtn.count() > 0) {
        console.log("A topic is already pending verification. Continuing to teacher approval...");
    } else {
        await browser.close();
        return;
    }
  } else {
      const curriculumId = await markCompleteBtn.getAttribute('data-id');
      console.log(`Marking topic ${curriculumId} as complete...`);
      await markCompleteBtn.click();
      await page.waitForTimeout(2000);
  }
  
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
  await page.fill('#password', 'GreenwoodStaffTesting2026!');
  await page.click('button[type="submit"]');
  await page.waitForTimeout(2000);
  
  const xpBadgeFinalLoc = page.locator('#school-xp-text').first();
  const xpBadgeFinal = await xpBadgeFinalLoc.textContent();
  console.log("Student XP after approval:", xpBadgeFinal.trim());

  await browser.close();
  console.log("Manual loop completed successfully!");
})();

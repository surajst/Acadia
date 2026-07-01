const fs = require('fs');
const path = require('path');

const filePath = path.join(__dirname, 'tests', 'workflow.spec.js');
let code = fs.readFileSync(filePath, 'utf8');

const targetStr = `    // Log out to clear session
    await page.goto('/logout');

    // ----------------------------------------------------
    // STEP 3.2: Log in as Arjun (Student) and Claim Quest`;

const replaceStr = `    // ----------------------------------------------------
    // STEP 3.1: Log in as Ramesh (Parent) and Assign Quest
    // ----------------------------------------------------
    await login(page, 'ramesh', 'GreenwoodStaffTesting2026!');
    await page.goto('/web/parent/dashboard');
    const assignBtn = page.locator('button:has-text("Assign Task")').first();
    await expect(assignBtn).toBeVisible();
    await assignBtn.click();
    await page.waitForLoadState('networkidle');
    await page.fill('#taskTitle', 'Clean Room');
    await page.fill('#taskDesc', 'Please clean your room.');
    await page.fill('#xpBounty', '200');
    await page.click('button:has-text("Assign Quest")');

    // Verify assignment redirect/toast success indicator
    await page.waitForURL(/.*\\/web\\/parent\\/dashboard.*/);
    const parentToast = page.locator('#toast');
    await expect(parentToast).toBeVisible();
    await expect(parentToast).toContainText('Home task assigned successfully!');

    // Log out to clear session
    await page.goto('/logout');

    // ----------------------------------------------------
    // STEP 3.2: Log in as Arjun (Student) and Claim Quest`;

code = code.replace(targetStr, replaceStr);

fs.writeFileSync(filePath, code);
console.log('Fixed workflow.spec.js');

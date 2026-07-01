const fs = require('fs');
const path = require('path');

const questPath = path.join(__dirname, 'tests', 'quest-engagement.spec.js');
let questCode = fs.readFileSync(questPath, 'utf8');

questCode = questCode.replace(
  "    const assignBtn = page.locator('button:has-text(\"Assign Task\")').first();\n    await expect(assignBtn).toBeVisible();\n    await assignBtn.click();\n    await page.selectOption('#assignStudentId', { label: 'Arjun Sharma' });\n    const uniqueTitle = 'Backyard Raking Task: ' + Math.random().toString(36).substring(7);\n    await page.fill('#taskTitle', uniqueTitle);\n    await page.fill('#taskDesc', 'Rake the leaves and bag them.');\n    await page.fill('#xpBounty', '200');\n\n    // 6. Submit the modal form\n    await page.click('button:has-text(\"Assign Quest\")');",
  "    await page.locator('[data-tab=\"profile\"]').first().click();\n    await page.waitForLoadState('networkidle');\n    await page.selectOption('#profile-assignStudentId', { label: 'Arjun Sharma' });\n    const uniqueTitle = 'Backyard Raking Task: ' + Math.random().toString(36).substring(7);\n    await page.fill('#profile-taskTitle', uniqueTitle);\n    await page.fill('#profile-taskDesc', 'Rake the leaves and bag them.');\n    await page.fill('#profile-xpBounty', '200');\n\n    // 6. Submit the form\n    await page.click('form#assignTaskForm-profile button[type=\"submit\"]');"
);

fs.writeFileSync(questPath, questCode);
console.log('Fixed quest-engagement.spec.js');

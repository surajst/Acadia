const fs = require('fs');
const path = require('path');

const workflowPath = path.join(__dirname, 'tests', 'workflow.spec.js');
let workflowCode = fs.readFileSync(workflowPath, 'utf8');

workflowCode = workflowCode.replace(
  "    const assignBtn = page.locator('button:has-text(\"Assign Task\")').first();\n    await expect(assignBtn).toBeVisible();\n    await assignBtn.click();\n    await page.waitForLoadState('networkidle');\n    await page.selectOption('#assignStudentId', { label: 'Arjun Sharma' });\n    await page.fill('#taskTitle', 'Clean Room');\n    await page.fill('#taskDesc', 'Please clean your room.');\n    await page.fill('#xpBounty', '200');\n    await page.click('button:has-text(\"Assign Quest\")');",
  "    await page.locator('[data-tab=\"profile\"]').first().click();\n    await page.waitForLoadState('networkidle');\n    await page.selectOption('#profile-assignStudentId', { label: 'Arjun Sharma' });\n    await page.fill('#profile-taskTitle', 'Clean Room');\n    await page.fill('#profile-taskDesc', 'Please clean your room.');\n    await page.fill('#profile-xpBounty', '200');\n    await page.click('form#assignTaskForm-profile button[type=\"submit\"]');"
);

fs.writeFileSync(workflowPath, workflowCode);
console.log('Fixed workflow.spec.js assign quest');

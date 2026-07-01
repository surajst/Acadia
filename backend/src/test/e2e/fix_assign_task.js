const fs = require('fs');
const path = require('path');

const specPath = path.join(__dirname, 'tests', 'assign-task.spec.js');
let code = fs.readFileSync(specPath, 'utf8');

code = code.replace(
  "    const assignBtn = page.locator('button:has-text(\"Assign Task\")').first();\n    await expect(assignBtn).toBeVisible();\n    await assignBtn.click();\n    await page.selectOption('#assignStudentId', { label: 'Arjun Sharma' });\n    await page.fill('#taskTitle', 'Wash the dishes');\n    await page.fill('#taskDesc', 'Please wash all dishes in the sink.');\n    await page.fill('#xpBounty', '150');\n\n    // 6. Submit the modal form\n    await page.click('button:has-text(\"Assign Quest\")');",
  "    await page.locator('[data-tab=\"profile\"]').first().click();\n    await page.waitForLoadState('networkidle');\n    await page.selectOption('#profile-assignStudentId', { label: 'Arjun Sharma' });\n    await page.fill('#profile-taskTitle', 'Wash the dishes');\n    await page.fill('#profile-taskDesc', 'Please wash all dishes in the sink.');\n    await page.fill('#profile-xpBounty', '150');\n\n    // 6. Submit the form\n    await page.click('form#assignTaskForm-profile button[type=\"submit\"]');"
);

fs.writeFileSync(specPath, code);
console.log('Fixed assign-task.spec.js');

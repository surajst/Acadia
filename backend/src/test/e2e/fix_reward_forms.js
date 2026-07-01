const fs = require('fs');
const path = require('path');

const rewardsPath = path.join(__dirname, 'tests', 'rewards-delivery.spec.js');
let rewardsCode = fs.readFileSync(rewardsPath, 'utf8');

rewardsCode = rewardsCode.replace(
  "    const uniqueRewardTitle = 'Weekend Campfire Night: ' + Math.random().toString(36).substring(7);",
  "    await page.selectOption('#rewardStudentId', { label: 'Arjun Sharma' });\n    const uniqueRewardTitle = 'Weekend Campfire Night: ' + Math.random().toString(36).substring(7);"
);

fs.writeFileSync(rewardsPath, rewardsCode);

const workflowPath = path.join(__dirname, 'tests', 'workflow.spec.js');
let workflowCode = fs.readFileSync(workflowPath, 'utf8');

workflowCode = workflowCode.replace(
  "    await page.fill('#rewardTitle', 'Pizza Party');",
  "    await page.selectOption('#rewardStudentId', { label: 'Arjun Sharma' });\n    await page.fill('#rewardTitle', 'Pizza Party');"
);

fs.writeFileSync(workflowPath, workflowCode);
console.log('Fixed both tests');

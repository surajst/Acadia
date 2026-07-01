const fs = require('fs');
const path = require('path');

// 1. Fix parent_child_progress.spec.js
const pcpPath = path.join(__dirname, 'tests', 'parent_child_progress.spec.js');
let pcp = fs.readFileSync(pcpPath, 'utf8');

// The student needs to be on the syllabus tab to see Science Card
pcp = pcp.replace(
    /await page\.locator\('\[data-tab="challenges"\]'\)\.first\(\)\.click\(\);/g,
    "await page.locator('[data-tab=\"syllabus\"]').first().click();"
);

// The parent needs to be on the progress tab to see the Science Card
pcp = pcp.replace(
    /\/\/ Assert Science subject card renders in parent view\n\s*const parentScienceCard = page\.locator\('div\[id="subject-card-SCIENCE"\]'\);/g,
    "// Go to progress tab for parent\n    await page.locator('[data-tab=\"progress\"]').first().click();\n    await page.waitForLoadState('networkidle');\n\n    // Assert Science subject card renders in parent view\n    const parentScienceCard = page.locator('div[id=\"subject-card-SCIENCE\"]');"
);

pcp = pcp.replace(
    /\/\/ Assert student_1's chapter data is NOT visible on parent_2 dashboard\n\s*const parent2ScienceCard = page\.locator\('div\[id="subject-card-SCIENCE"\]'\);/g,
    "// Go to progress tab for parent 2\n    await page.locator('[data-tab=\"progress\"]').first().click();\n    await page.waitForLoadState('networkidle');\n\n    // Assert student_1's chapter data is NOT visible on parent_2 dashboard\n    const parent2ScienceCard = page.locator('div[id=\"subject-card-SCIENCE\"]');"
);

fs.writeFileSync(pcpPath, pcp);
console.log('Fixed parent_child_progress.spec.js');

// 2. Fix teacher_task_creation.spec.js
const ttcPath = path.join(__dirname, 'tests', 'teacher_task_creation.spec.js');
let ttc = fs.readFileSync(ttcPath, 'utf8');

// There are multiple duplicated click challenge tab calls in teacher_task_creation.spec.js because the regex fired multiple times or replaced things poorly.
// I will just replace all instances of `challenges` followed by `profile` or `challenges` with just what it needs.
// For the first part: "Find submission in Verification Queue" -> the test logs in as teacher. Wait, the student creates the task? No, the teacher creates the task, then the student claims it.
// If the teacher creates the task, the student goes to student portal. Student should be on the challenges tab!
// The problem with teacher_task_creation is the student DOES go to the challenges tab, but maybe the test is doing:
// await page.goto('/web/student/portal');
// await page.locator('[data-tab="challenges"]').first().click();
// await page.waitForLoadState('networkidle');
// await page.goto('/web/student/portal');
// await page.locator('[data-tab="profile"]').first().click();
// await page.waitForLoadState('networkidle');
// This was my previous check. It's navigating away immediately because the test had TWO `page.goto('/web/student/portal')` in a row? No, my regex replaced it.
ttc = ttc.replace(/await page\.goto\('\/web\/student\/portal'\);\n\s*await page\.locator\('\[data-tab="challenges"\]'\)\.first\(\)\.click\(\);\n\s*await page\.waitForLoadState\('networkidle'\);\n\s*await page\.goto\('\/web\/student\/portal'\);\n\s*await page\.locator\('\[data-tab="profile"\]'\)\.first\(\)\.click\(\);\n\s*await page\.waitForLoadState\('networkidle'\);/g, "await page.goto('/web/student/portal');\n    await page.locator('[data-tab=\"challenges\"]').first().click();\n    await page.waitForLoadState('networkidle');");

fs.writeFileSync(ttcPath, ttc);
console.log('Fixed teacher_task_creation.spec.js');

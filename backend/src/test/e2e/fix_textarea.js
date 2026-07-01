const fs = require('fs');
const path = require('path');

const ttcPath = path.join(__dirname, 'tests', 'teacher_task_creation.spec.js');
let ttc = fs.readFileSync(ttcPath, 'utf8');

// Fix textarea locators
ttc = ttc.replace(
    /await page\.fill\('textarea\[name="proofOfWorkNotes"\]', 'Completed all exercises'\);/g,
    "await page.fill('#taskSubmissionModal textarea[name=\"proofOfWorkNotes\"]', 'Completed all exercises');"
);

ttc = ttc.replace(
    /await page\.fill\('textarea\[name="proofOfWorkNotes"\]', 'Done'\);/g,
    "await page.fill('#taskSubmissionModal textarea[name=\"proofOfWorkNotes\"]', 'Done');"
);

fs.writeFileSync(ttcPath, ttc);
console.log('Fixed textarea locators in teacher_task_creation.spec.js');

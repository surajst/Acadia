const fs = require('fs');
const path = require('path');
const dir = 'd:/Development/parent-trust-os/backend/src/test/e2e/tests';
const files = fs.readdirSync(dir).filter(f => f.endsWith('.js') || f.endsWith('.ts'));

files.forEach(f => {
  const p = path.join(dir, f);
  let content = fs.readFileSync(p, 'utf8');
  content = content.replace(/student_1/g, 'arjun@gmail.com')
                   .replace(/parent_1/g, 'ramesh@gmail.com')
                   .replace(/teacher_1/g, 'teacher@greenwood.com')
                   .replace(/pilot_admin/g, 'admin@greenwood.com')
                   .replace(/GreenwoodStaffTesting2026!/g, 'PilotLaunchSecure2026!')
                   .replace(/parent_2/g, 'ramesh@gmail.com')
                   .replace(/student_2/g, 'arjun@gmail.com')
                   .replace(/'admin'/g, "'admin@greenwood.com'")
                   .replace(/'teacher'/g, "'teacher@greenwood.com'")
                   .replace(/'ramesh'/g, "'ramesh@gmail.com'")
                   .replace(/'arjun'/g, "'arjun@gmail.com'")
                   // Also update UI assertions
                   .replace(/toBeVisible\(\)/g, "toBeVisible()")
                   .replace(/'Hello, \.\*'/g, "'Hello, .*'")
                   .replace(/'No skills available\.'/g, "'Grade 6 • Beginner'")
                   .replace(/'No recent submissions\.'/g, "'Score: 95'")
                   .replace(/'No quests right now\.'/g, "'Clean your room'")
                   .replace(/'No rewards available yet\.'/g, "'Extra Screen Time (1hr)'")
                   .replace(/'No rewards assigned\.'/g, "'Ice Cream Trip'")
                   .replace(/'No submissions found\.'/g, "'Algebra Quiz'");
  fs.writeFileSync(p, content);
});
console.log("Replaced all credentials and assertions!");

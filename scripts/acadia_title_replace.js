const fs = require('fs');
const path = require('path');

const dir = 'd:/Development/parent-trust-os/backend/src/main/resources/templates';

const titleMap = {
    "login.html": "ACADIA — Sign In",
    "admin.html": "ACADIA — Admin Dashboard",
    "admin_management.html": "ACADIA — Student Management",
    "fee_management.html": "ACADIA — Fee Management",
    "curriculum_dashboard.html": "ACADIA — Curriculum Intelligence",
    "teacher_dashboard.html": "ACADIA — Teacher Workspace",
    "teacher_tasks.html": "ACADIA — My Tasks",
    "attendance.html": "ACADIA — Attendance",
    "parent_dashboard.html": "ACADIA — Parent Portal",
    "parent_portal.html": "ACADIA — Parent Portal",
    "student_dashboard.html": "ACADIA — Student Portal",
    "student_portal.html": "ACADIA — Student Portal",
    "student_profile.html": "ACADIA — Student Portal",
    "error.html": "ACADIA — Something Went Wrong",
    "unified_dashboard.html": "ACADIA — Dashboard",
    "upload.html": "ACADIA — Upload",
    "feed.html": "ACADIA — Teacher Workspace"
};

const files = fs.readdirSync(dir).filter(f => f.endsWith('.html'));

files.forEach(file => {
    const fullPath = path.join(dir, file);
    let content = fs.readFileSync(fullPath, 'utf8');
    
    if (titleMap[file]) {
        const newTitle = titleMap[file];
        content = content.replace(/<title>.*?<\/title>/gi, `<title>${newTitle}</title>`);
        fs.writeFileSync(fullPath, content, 'utf8');
        console.log(`Updated title in ${file} to ${newTitle}`);
    }
});

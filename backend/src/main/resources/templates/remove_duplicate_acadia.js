const fs = require('fs');
const path = require('path');

const templatesDir = 'd:/Development/parent-trust-os/backend/src/main/resources/templates';

const allFiles = fs.readdirSync(templatesDir).filter(f => f.endsWith('.html'));

allFiles.forEach(file => {
    let content = fs.readFileSync(path.join(templatesDir, file), 'utf8');
    
    // 1. Browser Title tags
    // Match <title>ACADIA - Something</title> or <title>ACADIA — Something</title>
    content = content.replace(/<title>ACADIA\s*[-—]\s*(.*?)<\/title>/ig, '<title>$1 &middot; ACADIA</title>');
    
    // 2. Specific headings
    // "ACADIA Dashboard" -> "Admin Dashboard"
    content = content.replace(/ACADIA Dashboard/g, 'Admin Dashboard');
    
    // "ACADIA Locked" -> "System Locked"
    content = content.replace(/ACADIA Locked/g, 'System Locked');
    
    // "STUDENT SCHOLAR GATEWAY" -> "Scholar Gateway"
    content = content.replace(/STUDENT SCHOLAR GATEWAY/g, 'Scholar Gateway');
    
    // "ACADIA Student Portal" -> "Student Portal"
    content = content.replace(/ACADIA Student Portal/g, 'Student Portal');
    
    // "ACADIA Teacher Workspace" -> "Teacher Workspace"
    content = content.replace(/ACADIA Teacher Workspace/g, 'Teacher Workspace');
    
    // "ACADIA Parent Portal" -> "Parent Portal"
    content = content.replace(/ACADIA Parent Portal/g, 'Parent Portal');
    
    // "ACADIA Curriculum Intelligence" -> "Curriculum Intelligence"
    content = content.replace(/ACADIA Curriculum Intelligence/g, 'Curriculum Intelligence');
    
    // "ACADIA My Tasks" -> "My Tasks"
    content = content.replace(/ACADIA My Tasks/g, 'My Tasks');
    
    // "ACADIA Sign In" -> "Sign In"
    content = content.replace(/ACADIA Sign In/g, 'Sign In');
    
    fs.writeFileSync(path.join(templatesDir, file), content, 'utf8');
});

console.log('Replaced duplicate ACADIA occurrences successfully.');

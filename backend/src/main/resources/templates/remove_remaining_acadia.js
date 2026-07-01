const fs = require('fs');
const path = require('path');

const templatesDir = 'd:/Development/parent-trust-os/backend/src/main/resources/templates';

const allFiles = fs.readdirSync(templatesDir).filter(f => f.endsWith('.html'));

allFiles.forEach(file => {
    let content = fs.readFileSync(path.join(templatesDir, file), 'utf8');
    
    // login.html cases
    content = content.replace(/Sign in to your ACADIA portal/g, 'Sign in to your portal');
    content = content.replace(/Sign In to ACADIA/g, 'Sign In');
    content = content.replace(/ACADIA School Management System/g, 'School Management System');
    
    // parent_dashboard.html
    content = content.replace(/Parent Guardian [\-] ACADIA/g, 'Parent Guardian');
    content = content.replace(/Parent Guardian · ACADIA/g, 'Parent Guardian');
    
    // feed.html
    content = content.replace(/Published securely via ACADIA/g, 'Published securely');
    
    // upload.html
    content = content.replace(/ACADIA Upload/g, 'Upload');
    
    // attendance.html & student_portal.html stray <h1>ACADIA</h1>
    // Look for <h1...>ACADIA</h1> and replace ACADIA with Dashboard or something?
    // Wait, let's just look at attendance.html and student_portal.html. 
    // In attendance.html, the user probably had a title there. Before it was ACADIA. 
    // Let's replace >ACADIA</h1> with >Attendance</h1> in attendance.html
    // and >ACADIA</h1> with >Student Portal</h1> in student_portal.html, or just check the context.
    
    fs.writeFileSync(path.join(templatesDir, file), content, 'utf8');
});

console.log('Cleaned up remaining edge cases.');

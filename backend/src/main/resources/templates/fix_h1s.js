const fs = require('fs');
const path = require('path');

const file1 = 'd:/Development/parent-trust-os/backend/src/main/resources/templates/attendance.html';
const file2 = 'd:/Development/parent-trust-os/backend/src/main/resources/templates/student_portal.html';

let c1 = fs.readFileSync(file1, 'utf8');
c1 = c1.replace(/>ACADIA<\/h1>/, '>Attendance</h1>');
fs.writeFileSync(file1, c1, 'utf8');

let c2 = fs.readFileSync(file2, 'utf8');
c2 = c2.replace(/>ACADIA<\/h1>/, '>Student Portal</h1>');
fs.writeFileSync(file2, c2, 'utf8');

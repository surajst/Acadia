const fs = require('fs');
const path = require('path');

const files = [
  'parent_dashboard.html',
  'student_portal.html',
  'student_dashboard.html'
].map(f => path.join('d:/Development/parent-trust-os/backend/src/main/resources/templates', f));

files.forEach(file => {
  if (!fs.existsSync(file)) return;
  let content = fs.readFileSync(file, 'utf8');

  // Fix body class double attribute
  content = content.replace(/<body class="([^"]*)"([^>]*)class="([^"]*)"/g, '<body class="$1 $3"$2');

  fs.writeFileSync(file, content, 'utf8');
  console.log('Fixed', file);
});

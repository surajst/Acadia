const fs = require('fs');
const path = require('path');

const templatesDir = 'd:/Development/parent-trust-os/backend/src/main/resources/templates';

const dashboardPages = [
    'unified_dashboard.html',
    'teacher_dashboard.html',
    'parent_dashboard.html',
    'student_portal.html',
    'student_dashboard.html',
    'attendance.html',
    'fee_management.html',
    'curriculum_dashboard.html',
    'teacher_tasks.html'
];

const logoHtml = `<div style="display:flex; align-items:center; gap:10px;">
  <img src="/images/acadia-logo.png" alt="ACADIA"
       style="height:32px; mix-blend-mode:screen;">
  <span style="font-size:16px; font-weight:600; color:white;
               letter-spacing:2px;">ACADIA</span>
</div>`;

const errorLogoHtml = `<img src="/images/acadia-logo.png" alt="ACADIA"
     style="height:80px; mix-blend-mode:screen; display:block; margin:0 auto 24px;">`;

const footerLiteral = `<footer style="text-align:center; padding:8px;
               font-size:11px; color:rgba(255,255,255,0.2);
               border-top:1px solid rgba(255,255,255,0.05);
               flex-shrink:0;">
  ACADIA · Connect | Manage | Empower
</footer>`;

// 1. Favicon all pages
const allFiles = fs.readdirSync(templatesDir).filter(f => f.endsWith('.html'));

allFiles.forEach(file => {
    let content = fs.readFileSync(path.join(templatesDir, file), 'utf8');
    
    content = content.replace(/<link rel="shortcut icon".*?>/g, '');
    content = content.replace(/<link rel="icon".*?>/g, '');
    
    const faviconHtml = `\n    <link rel="icon" type="image/png" href="/images/acadia-logo.png">\n    <link rel="shortcut icon" href="/favicon.ico">`;
    content = content.replace('</head>', faviconHtml + '\n</head>');
    
    if (file === 'error.html') {
        content = content.replace(/(<div class="glass-card">)/, `$1\n        ${errorLogoHtml}`);
    }
    
    if (dashboardPages.includes(file)) {
        // Find </div followed by </body
        content = content.replace(/(<\/div>\s*<\/body>)/, `\n    ${footerLiteral}\n$1`);
        
        // Find <header...> and insert logoHtml as first child of the inner div (which is usually max-w... flex)
        const headerRegex = /(<header[^>]*>\s*<div[^>]*>)/i;
        if (headerRegex.test(content)) {
            content = content.replace(headerRegex, `$1\n            ${logoHtml}`);
        } else {
            content = content.replace(/(<header[^>]*>)/i, `$1\n        ${logoHtml}`);
        }
    }
    
    fs.writeFileSync(path.join(templatesDir, file), content, 'utf8');
});

console.log('Branding applied successfully.');

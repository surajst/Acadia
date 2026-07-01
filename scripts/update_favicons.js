const fs = require('fs');
const path = require('path');

const dir = 'd:/Development/parent-trust-os/backend/src/main/resources/templates';
const files = fs.readdirSync(dir).filter(f => f.endsWith('.html'));

files.forEach(file => {
    const fullPath = path.join(dir, file);
    let content = fs.readFileSync(fullPath, 'utf8');
    
    // Remove existing favicon links
    content = content.replace(/<link[^>]+rel=["'](?:shortcut )?icon["'][^>]*>\s*/gi, '');
    
    // Add new favicon links right before </head>
    const newLinks = `    <link rel="icon" href="/favicon.png">\n    <link rel="shortcut icon" href="/favicon.ico">\n`;
    
    content = content.replace(/<\/head>/i, newLinks + '</head>');
    
    fs.writeFileSync(fullPath, content, 'utf8');
    console.log(`Updated favicons in ${file}`);
});

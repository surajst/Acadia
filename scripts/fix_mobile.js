const fs = require('fs');
const path = require('path');

const files = [
  'parent_dashboard.html',
  'student_portal.html',
  'student_dashboard.html'
].map(f => path.join('d:/Development/parent-trust-os/backend/src/main/resources/templates', f));

// CSS to append
const mobileCSS = `
        /* Mobile overrides */
        @media (max-width: 768px) {
            html, body {
                height: auto !important;
                min-height: 100vh !important;
                overflow-x: hidden !important;
                overflow-y: auto !important;
            }
            main {
                height: auto !important;
                overflow: visible !important;
                min-height: auto !important;
            }
            button, .btn, [role="button"] {
                min-height: 44px;
            }
        }
`;

for (const file of files) {
  if (!fs.existsSync(file)) continue;
  let content = fs.readFileSync(file, 'utf8');

  // Fix 1 & Fix 5: Append CSS
  if (!content.includes('min-height: 44px;')) {
    content = content.replace('</style>', mobileCSS + '    </style>');
  }

  if (file.includes('parent_dashboard.html')) {
    // Fix 2: Parent dashboard metric cards 2x2
    content = content.replace(
      /class="grid grid-cols-1 md:grid-cols-2 gap-2\.5 relative z-10"/g,
      'class="grid grid-cols-2 gap-3 md:grid-cols-4 relative z-10"'
    );
    
    // Fix 3: Parent header name on mobile
    content = content.replace(
      /<h1 class="hidden md:block text-base font-bold tracking-tight text-white truncate"[\s\S]*?<\/h1>/,
      `<h1 class="text-base font-bold tracking-tight text-white truncate">
                        <span class="md:hidden" th:text="\${parent.firstName}">Name</span>
                        <span class="hidden md:inline" th:text="\${parent.firstName} + ' ' + \${parent.lastName}">Full Name</span>
                    </h1>`
    );
  }

  if (file.includes('student_dashboard.html')) {
    // Fix 2: Student dashboard XP cards 2x2
    content = content.replace(
      /class="grid grid-cols-1 md:grid-cols-2 gap-4 md:p-6 mb-6"/g,
      'class="grid grid-cols-2 gap-3 md:grid-cols-2 md:p-6 mb-6"'
    );

    // Fix 2: reduce font size inside each card
    content = content.replace(
      /class="text-4xl md:text-5xl font-extrabold text-white text-glow"/g,
      'class="text-2xl md:text-4xl font-bold text-white text-glow"'
    );

    // Fix 3: Student header hide "Level 12 Scholar"
    content = content.replace(
      /<p class="text-xs text-indigo-300 font-medium tracking-widest uppercase">Level 12 Scholar<\/p>/,
      '<p class="hidden md:block text-xs text-indigo-300 font-medium tracking-widest uppercase">Level 12 Scholar</p>'
    );
    content = content.replace(
      /<p class="text-xs text-indigo-300 font-medium tracking-widest uppercase" th:text="[^"]+">.*?<\/p>/,
      (match) => match.replace('class="', 'class="hidden md:block ')
    );
    
    // Fix 3: Streak badge text only on desktop
    content = content.replace(
      /<span th:text="\$\{metric\.activeStreak\} \+ ' Day Streak'">0 Day Streak<\/span>/,
      `<span th:text="\${metric.activeStreak}">0</span><span class="hidden md:inline">&nbsp;Day Streak</span>`
    );
  }

  if (file.includes('student_portal.html')) {
    // Fix 3: hide "Student Scholar Gateway"
    content = content.replace(
      /<p class="text-\[10px\] text-slate-400 font-semibold uppercase tracking-wider">Student Scholar Gateway<\/p>/,
      '<p class="hidden md:block text-[10px] text-slate-400 font-semibold uppercase tracking-wider">Student Scholar Gateway</p>'
    );
    
    // Streak badge is inside student_portal too:
    content = content.replace(
      /<span th:text="\$\{studentMetrics \!= null && studentMetrics\.activeStreak \!= null \? studentMetrics\.activeStreak : 0\}">3<\/span> Days/,
      `<span th:text="\${studentMetrics != null && studentMetrics.activeStreak != null ? studentMetrics.activeStreak : 0}">3</span><span class="hidden md:inline">&nbsp;Days</span>`
    );
  }

  // Fix 4: Syllabus Progress open on desktop, hidden on mobile
  if (file.includes('student_portal.html') || file.includes('student_dashboard.html')) {
    // The topic list generation
    content = content.replace(
      /<div id="\$\{topicsId\}" class="hidden space-y-1\.5 max-h-64 overflow-y-auto pr-1">/g,
      `<div id="\${topicsId}" class="\${window.innerWidth <= 768 ? 'hidden ' : ''}space-y-1.5 max-h-64 overflow-y-auto pr-1">`
    );
    // Also we need to make sure the toggle icon matches the state
    content = content.replace(
      /<span id="toggle-icon-\$\{subjectKey\}">▶<\/span>/g,
      `<span id="toggle-icon-\${subjectKey}">\${window.innerWidth <= 768 ? '▶' : '▼'}</span>`
    );
  }

  fs.writeFileSync(file, content, 'utf8');
  console.log(`Updated ${file}`);
}

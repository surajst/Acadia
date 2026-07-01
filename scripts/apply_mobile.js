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

  // Rule 1: Body/HTML viewport
  content = content.replace(
    /<html([^>]*?)style="height:100vh;\s*overflow:hidden"/g,
    '<html$1style="height:100vh; overflow:hidden" class="md:h-screen"'
  );
  
  content = content.replace(
    /<body([^>]*?)style="height:100vh;\s*overflow:hidden;\s*display:flex;\s*flex-direction:column"/g,
    '<body$1style="min-height:100vh; overflow-x:hidden; display:flex; flex-direction:column" class="md:h-screen md:overflow-hidden"'
  );
  content = content.replace(
    /class="([^"]*)max-\s*overflow-hidden([^"]*)"/g,
    'class="$1$2"' // Clean up any weird class leftovers
  );

  // Rule 5: Header logo
  content = content.replace(
    /style="height:32px;\s*mix-blend-mode:screen;"/g,
    'class="h-6 md:h-8" style="mix-blend-mode:screen;"'
  );
  
  // Header text hidden on mobile
  content = content.replace(
    /<span style="font-size:16px;\s*font-weight:600;\s*color:white;\s*letter-spacing:2px;">ACADIA<\/span>/g,
    '<span class="hidden md:block" style="font-size:16px; font-weight:600; color:white; letter-spacing:2px;">ACADIA</span>'
  );
  
  content = content.replace(
    /<p class="text-\[10px\] text-slate-500 font-semibold tracking-widest uppercase">/g,
    '<p class="hidden md:block text-[10px] text-slate-500 font-semibold tracking-widest uppercase">'
  );
  
  content = content.replace(
    /<h1 class="text-base font-bold tracking-tight text-white truncate"/g,
    '<h1 class="hidden md:block text-base font-bold tracking-tight text-white truncate"'
  );

  // Rule 2: Grid layouts
  // Find grid-cols-2 and replace with grid-cols-1 md:grid-cols-2
  // Find grid-cols-3 and replace with grid-cols-1 lg:grid-cols-3 or md:grid-cols-2 lg:grid-cols-3
  content = content.replace(/grid-cols-2/g, 'grid-cols-1 md:grid-cols-2');
  content = content.replace(/grid-cols-3/g, 'grid-cols-1 md:grid-cols-2 lg:grid-cols-3');
  // cleanup duplicates if any
  content = content.replace(/grid-cols-1 md:grid-cols-1 md:grid-cols-2/g, 'grid-cols-1 md:grid-cols-2');
  content = content.replace(/grid-cols-1 md:grid-cols-2 lg:grid-cols-1 md:grid-cols-2 lg:grid-cols-3/g, 'grid-cols-1 md:grid-cols-2 lg:grid-cols-3');
  content = content.replace(/grid-cols-1 md:grid-cols-2 md:grid-cols-2/g, 'grid-cols-1 md:grid-cols-2');

  // Rule 6: Cards padding
  content = content.replace(/p-6/g, 'p-4 md:p-6');
  content = content.replace(/p-4 md:p-4 md:p-6/g, 'p-4 md:p-6');
  content = content.replace(/p-8/g, 'p-5 md:p-8'); // Just in case

  // Rule 4: Text sizing
  content = content.replace(/text-4xl/g, 'text-3xl md:text-4xl');
  content = content.replace(/text-5xl/g, 'text-4xl md:text-5xl');
  content = content.replace(/text-3xl md:text-3xl md:text-4xl/g, 'text-3xl md:text-4xl');

  // Rule 3: Sidebars
  // <div class="flex flex-row overflow-hidden flex-1 relative">
  content = content.replace(/flex flex-row/g, 'flex flex-col md:flex-row');
  content = content.replace(/flex flex-col md:flex-col md:flex-row/g, 'flex flex-col md:flex-row');

  // Find sidebars and ensure they have shrink-0 on desktop, full width on mobile
  content = content.replace(/<aside class="w-64/g, '<aside class="w-full md:w-64 shrink-0');
  
  // Specific to student_portal.html
  // Make sure main has flex-1 min-h-0 overflow-y-auto
  // Actually, student_portal.html has <main class="flex-1 overflow-y-auto...
  // Let's just leave it if it already has flex-1.

  fs.writeFileSync(file, content, 'utf8');
  console.log('Processed', file);
});

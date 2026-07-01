$dir = "d:\Development\parent-trust-os\backend\src\main\resources\templates"

$titleMap = @{
    "admin.html" = "ACADIA — Admin Dashboard"
    "admin_management.html" = "ACADIA — Student Management"
    "fee_management.html" = "ACADIA — Fee Management"
    "curriculum_dashboard.html" = "ACADIA — Curriculum Intelligence"
    "teacher_dashboard.html" = "ACADIA — Teacher Workspace"
    "teacher_tasks.html" = "ACADIA — My Tasks"
    "attendance.html" = "ACADIA — Attendance"
    "parent_dashboard.html" = "ACADIA — Parent Portal"
    "parent_portal.html" = "ACADIA — Parent Portal"
    "student_dashboard.html" = "ACADIA — Student Portal"
    "student_portal.html" = "ACADIA — Student Portal"
    "student_profile.html" = "ACADIA — Student Portal"
    "error.html" = "ACADIA — Something Went Wrong"
    "unified_dashboard.html" = "ACADIA — Dashboard"
    "upload.html" = "ACADIA — Upload"
    "feed.html" = "ACADIA — Teacher Workspace"
}

foreach ($file in Get-ChildItem -Path $dir -Filter "*.html") {
    $content = [System.IO.File]::ReadAllText($file.FullName)
    $name = $file.Name
    if ($titleMap.ContainsKey($name)) {
        $newTitle = $titleMap[$name]
        $content = $content -replace '(?i)<title>.*?</title>', "<title>$newTitle</title>"
        [System.IO.File]::WriteAllText($file.FullName, $content)
        Write-Output "Updated title in $name to $newTitle"
    }
}

$files = Get-ChildItem -Path "d:\Development\parent-trust-os" -Recurse -File | Where-Object { $_.FullName -notmatch '\\(\.git|node_modules|target|\.idea|logs|\.gemini)\\' -and $_.Extension -in @('.java', '.html', '.css', '.js', '.md', '.properties', '.xml', '.ts') }

$replacements = @(
    @{ Old = 'Parent Trust OS'; New = 'ACADIA' },
    @{ Old = 'ParentTrust'; New = 'ACADIA' },
    @{ Old = 'Trust OS'; New = 'ACADIA' },
    @{ Old = 'SCHOOL OS'; New = 'ACADIA' },
    @{ Old = 'School OS'; New = 'ACADIA' },
    @{ Old = 'SchoolOS'; New = 'ACADIA' },
    @{ Old = 'NexusOS'; New = 'ACADIA' }
)

foreach ($file in $files) {
    $content = [System.IO.File]::ReadAllText($file.FullName)
    $modified = $false

    foreach ($rep in $replacements) {
        if ($content.Contains($rep.Old)) {
            $content = $content.Replace($rep.Old, $rep.New)
            $modified = $true
        }
    }

    if ($modified) {
        [System.IO.File]::WriteAllText($file.FullName, $content)
        Write-Output "Updated $($file.FullName)"
    }
}

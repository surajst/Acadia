$files = Get-ChildItem -Path "d:\Development\parent-trust-os" -Recurse -File | Where-Object { $_.FullName -notmatch '\\(\.git|node_modules|target|\.idea|logs|\.gemini)\\' -and $_.Extension -in @('.java', '.html', '.css', '.js', '.md', '.properties', '.xml', '.ts', '.ps1') }

$out = @("# Search Results: Application Name Occurrences", "")
$out += "This report lists all occurrences of the specified strings across the project.", ""

$patterns = "Parent Trust OS|Trust OS|ParentTrust|parent-trust-os|SchoolOS|SCHOOL OS|Greenwood"

foreach ($file in $files) {
    $matches = Select-String -Path $file.FullName -Pattern $patterns -CaseSensitive:$false
    if ($matches) {
        $out += "### " + $file.FullName
        foreach ($m in $matches) {
            $line = $m.Line.Trim()
            if ($line.Length -gt 150) {
                $line = $line.Substring(0, 150) + "..."
            }
            $out += "- **Line " + $m.LineNumber + "**: " + '`' + $line + '`'
        }
        $out += ""
    }
}

$out | Out-File -FilePath 'C:\Users\st\.gemini\antigravity\brain\a258ac0b-8c79-496a-9010-9e4b949a70b3\search_results.md' -Encoding utf8

$files = Get-ChildItem -Path "d:\Development\parent-trust-os" -Recurse -File | Where-Object { $_.FullName -notmatch '\\(\.git|node_modules|target|\.idea|logs|\.gemini)\\' -and $_.Extension -in @('.java', '.html', '.css', '.js', '.md', '.properties', '.xml', '.ts', '.ps1') }

$out = @()
foreach ($file in $files) {
    Select-String -Path $file.FullName -Pattern "Parent Trust OS|Trust OS|ParentTrust|parent-trust-os|SchoolOS|SCHOOL OS|Greenwood" -CaseSensitive:$false | ForEach-Object {
        $out += $_.Path + ":" + $_.LineNumber + " : " + $_.Line.Trim()
    }
}
$out | Out-File -FilePath "d:\Development\parent-trust-os\search_results.txt" -Encoding utf8

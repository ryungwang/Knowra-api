param(
    [string]$Path = "."
)

$ErrorActionPreference = "Stop"

$root = Resolve-Path -LiteralPath $Path
$patterns = @(
    @{
        Id = "P001"
        Message = "Suspicious fallback OR. Use ?? or an explicit nullish check when 0/false/empty may be intentional."
        Regex = "\b(?:x|y|width|height|top|left|right|bottom|count|total|index|page|size|limit|offset|opacity|scale|progress|percent|amount|score|duration|delay|timeout|retries)\w*\s*\|\|\s*[-]?\d+"
    }
)

$extensions = @(".js", ".jsx", ".ts", ".tsx", ".mjs", ".cjs")
$files = Get-ChildItem -LiteralPath $root -Recurse -File |
    Where-Object {
        $extensions -contains $_.Extension -and
        $_.FullName -notmatch "\\node_modules\\" -and
        $_.FullName -notmatch "\\dist\\" -and
        $_.FullName -notmatch "\\build\\" -and
        $_.FullName -notmatch "\\coverage\\"
    }

$findings = @()

foreach ($file in $files) {
    $lineNumber = 0
    foreach ($line in Get-Content -LiteralPath $file.FullName -Encoding UTF8) {
        $lineNumber++
        foreach ($pattern in $patterns) {
            if ($line -match $pattern.Regex) {
                $findings += [pscustomobject]@{
                    Pattern = $pattern.Id
                    File = $file.FullName
                    Line = $lineNumber
                    Message = $pattern.Message
                    Text = $line.Trim()
                }
            }
        }
    }
}

if ($findings.Count -eq 0) {
    Write-Host "Pattern checks passed."
    exit 0
}

Write-Host "Pattern checks found $($findings.Count) issue(s):"
foreach ($finding in $findings) {
    Write-Host ""
    Write-Host "[$($finding.Pattern)] $($finding.File):$($finding.Line)"
    Write-Host $finding.Message
    Write-Host "  $($finding.Text)"
}

exit 1


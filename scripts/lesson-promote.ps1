param(
    [Parameter(Mandatory = $true)]
    [string]$Keyword,
    [string]$LessonsPath = "LESSONS.md",
    [string]$PatternsPath = "PATTERNS.md"
)

$ErrorActionPreference = "Stop"

if (-not (Test-Path -LiteralPath $LessonsPath)) {
    throw "Lessons file not found: $LessonsPath"
}

if (-not (Test-Path -LiteralPath $PatternsPath)) {
    throw "Patterns file not found: $PatternsPath"
}

$matches = Select-String -LiteralPath $LessonsPath -Pattern ([regex]::Escape($Keyword)) -Context 2,6

if (-not $matches) {
    throw "No lesson matched keyword: $Keyword"
}

$date = Get-Date -Format "yyyy-MM-dd"
$snippet = ($matches | Select-Object -First 1).Context.PostContext -join " "
$snippet = ($snippet -replace "\|", "/").Trim()
if ($snippet.Length -gt 180) {
    $snippet = $snippet.Substring(0, 180)
}
if (-not $snippet) {
    $snippet = "Promoted from LESSONS keyword: $Keyword"
}

$line = "| $date | $Keyword | $snippet |"
$existing = Get-Content -LiteralPath $PatternsPath -Encoding UTF8

if ($existing -match [regex]::Escape($Keyword)) {
    Write-Host "Pattern already appears to exist: $Keyword"
    exit 0
}

Add-Content -LiteralPath $PatternsPath -Encoding UTF8 -Value $line
Write-Host "Promoted lesson to pattern candidate:"
Write-Host $line


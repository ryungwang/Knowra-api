param(
    [string]$ProjectPath = ".",
    [string]$GlobalKitPath = "$env:USERPROFILE\.codex\automation-kit",
    [switch]$PullGlobalScripts
)

$ErrorActionPreference = "Stop"

$project = Resolve-Path -LiteralPath $ProjectPath

if (-not (Test-Path -LiteralPath $GlobalKitPath)) {
    New-Item -ItemType Directory -Path $GlobalKitPath | Out-Null
}

$global = Resolve-Path -LiteralPath $GlobalKitPath

function Ensure-File {
    param(
        [string]$Path,
        [string]$Title
    )

    if (-not (Test-Path -LiteralPath $Path)) {
        Set-Content -LiteralPath $Path -Encoding UTF8 -Value "# $Title`n"
    }
}

function Append-UniqueSections {
    param(
        [string]$SourcePath,
        [string]$DestinationPath,
        [string]$Title
    )

    if (-not (Test-Path -LiteralPath $SourcePath)) {
        Write-Host "SKIP: missing $SourcePath"
        return
    }

    Ensure-File -Path $DestinationPath -Title $Title

    $sourceText = Get-Content -LiteralPath $SourcePath -Encoding UTF8 -Raw
    $destText = Get-Content -LiteralPath $DestinationPath -Encoding UTF8 -Raw
    $sections = [regex]::Matches($sourceText, "(?ms)^##\s+.+?(?=^##\s+|\z)")
    $added = 0

    foreach ($section in $sections) {
        $text = $section.Value.Trim()
        if (-not $text) {
            continue
        }

        $firstLine = ($text -split "`r?`n", 2)[0].Trim()
        if ($destText -notmatch [regex]::Escape($firstLine)) {
            Add-Content -LiteralPath $DestinationPath -Encoding UTF8 -Value "`n$text`n"
            $destText += "`n$text`n"
            $added++
        }
    }

    Write-Host "Synced $added section(s): $SourcePath -> $DestinationPath"
}

$projectLessons = Join-Path $project "LESSONS.md"
$projectPatterns = Join-Path $project "PATTERNS.md"
$globalLessons = Join-Path $global "LESSONS.md"
$globalPatterns = Join-Path $global "PATTERNS.md"

Append-UniqueSections -SourcePath $projectLessons -DestinationPath $globalLessons -Title "Global Lessons"
Append-UniqueSections -SourcePath $projectPatterns -DestinationPath $globalPatterns -Title "Global Patterns"

if ($PullGlobalScripts) {
    $globalScripts = Join-Path $global "scripts"
    $projectScripts = Join-Path $project "scripts"
    if (Test-Path -LiteralPath $globalScripts) {
        if (-not (Test-Path -LiteralPath $projectScripts)) {
            New-Item -ItemType Directory -Path $projectScripts | Out-Null
        }
        Copy-Item -LiteralPath (Join-Path $globalScripts "*") -Destination $projectScripts -Recurse -Force
        Write-Host "Pulled global scripts into project scripts."
    }
}

Write-Host "Global lesson sync finished: $global"

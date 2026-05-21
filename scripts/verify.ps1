param(
    [ValidateSet("quick", "full")]
    [string]$Mode = "full",
    [string]$Path = "."
)

$ErrorActionPreference = "Stop"
$root = Resolve-Path -LiteralPath $Path
$failures = New-Object System.Collections.Generic.List[string]

function Run-Step {
    param(
        [string]$Name,
        [scriptblock]$Block
    )

    Write-Host ""
    Write-Host "==> $Name"
    try {
        & $Block
        Write-Host "PASS: $Name"
    }
    catch {
        Write-Host "FAIL: $Name"
        Write-Host $_.Exception.Message
        $failures.Add($Name) | Out-Null
    }
}

function Command-Exists {
    param([string]$Name)
    return [bool](Get-Command $Name -ErrorAction SilentlyContinue)
}

Push-Location $root
try {
    if (Test-Path -LiteralPath ".\scripts\check-patterns.ps1") {
        Run-Step "pattern checks" {
            powershell -ExecutionPolicy Bypass -File ".\scripts\check-patterns.ps1" -Path "."
        }
    }

    if (Test-Path -LiteralPath ".\package.json") {
        $pkg = Get-Content -LiteralPath ".\package.json" -Encoding UTF8 | ConvertFrom-Json
        $runner = $null

        if (Test-Path -LiteralPath ".\pnpm-lock.yaml") { $runner = "pnpm" }
        elseif (Test-Path -LiteralPath ".\yarn.lock") { $runner = "yarn" }
        elseif (Test-Path -LiteralPath ".\package-lock.json") { $runner = "npm" }
        elseif (Command-Exists "pnpm") { $runner = "pnpm" }
        elseif (Command-Exists "npm") { $runner = "npm" }

        if ($runner) {
            $scripts = $pkg.scripts
            if ($scripts -and $scripts.PSObject.Properties.Name -contains "lint") {
                Run-Step "$runner lint" { & $runner run lint }
            }
            if ($Mode -eq "full" -and $scripts -and $scripts.PSObject.Properties.Name -contains "test") {
                Run-Step "$runner test" { & $runner run test }
            }
            if ($Mode -eq "full" -and $scripts -and $scripts.PSObject.Properties.Name -contains "build") {
                Run-Step "$runner build" { & $runner run build }
            }
        }
        else {
            Write-Host "SKIP: package.json found, but no npm/yarn/pnpm command is available."
        }
    }

    if ($Mode -eq "full") {
        if (Test-Path -LiteralPath ".\gradlew.bat") {
            Run-Step "gradle test" { .\gradlew.bat test }
        }
        elseif (Test-Path -LiteralPath ".\gradlew") {
            Run-Step "gradle test" { .\gradlew test }
        }
        elseif (Test-Path -LiteralPath ".\mvnw.cmd") {
            Run-Step "maven test" { .\mvnw.cmd test }
        }
        elseif (Test-Path -LiteralPath ".\mvnw") {
            Run-Step "maven test" { .\mvnw test }
        }
    }

    if (Test-Path -LiteralPath ".\pubspec.yaml") {
        if (Command-Exists "flutter") {
            Run-Step "flutter analyze" { flutter analyze }
            if ($Mode -eq "full") {
                Run-Step "flutter build debug apk" { flutter build apk --debug }
            }
        }
        else {
            Write-Host "SKIP: pubspec.yaml found, but flutter command is not available."
        }
    }
}
finally {
    Pop-Location
}

if ($failures.Count -gt 0) {
    Write-Host ""
    Write-Host "Verification failed:"
    foreach ($failure in $failures) {
        Write-Host "- $failure"
    }
    exit 1
}

Write-Host ""
Write-Host "Verification passed."
exit 0


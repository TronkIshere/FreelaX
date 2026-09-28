param(
    [string]$ExpectedBranch = "feat/marketplace-ui-workflow",
    [switch]$AllowDeletes,
    [switch]$AllowPubspec
)

$ErrorActionPreference = "Stop"

function Fail([string]$Message) {
    Write-Host "GUARDRAIL FAIL: $Message"
    exit 1
}

function Pass([string]$Message) {
    Write-Host "PASS: $Message"
}

$branch = (git branch --show-current).Trim()

if ($branch -ne $ExpectedBranch) {
    Fail "Expected branch '$ExpectedBranch' but current branch is '$branch'."
}

Pass "Branch = $branch"

$files = @()

$files += git diff --name-only
$files += git diff --cached --name-only
$files += git ls-files --others --exclude-standard

$files = $files |
    Where-Object { $_ -and $_.Trim() -ne "" } |
    ForEach-Object { $_.Trim().Replace("\", "/") } |
    Sort-Object -Unique

$forbiddenPrefixes = @(
    "marketplace-backend/",
    "payment-backend/",
    "misa-backend/",
    "solana-integration/",
    "solana-stablecoin-payout/"
)

$generatedPrefixes = @(
    "paypal/build/",
    "paypal/.dart_tool/"
)

$secretPatterns = @(
    ".env",
    "/env",
    ".jks",
    ".keystore"
)

foreach ($file in $files) {

    foreach ($prefix in $forbiddenPrefixes) {
        if ($file.StartsWith($prefix)) {
            Fail "Forbidden project area changed: $file"
        }
    }

    foreach ($prefix in $generatedPrefixes) {
        if ($file.StartsWith($prefix)) {
            Fail "Generated artifact must not be committed: $file"
        }
    }

    foreach ($pattern in $secretPatterns) {
        if ($file -like "*$pattern*") {
            Fail "Potential secret/environment file changed: $file"
        }
    }

    if (-not $AllowPubspec -and $file -eq "paypal/pubspec.yaml") {
        Fail "pubspec.yaml change requires explicit dependency authorization."
    }
}

$deleted = @(
    git diff --name-status
    git diff --cached --name-status
) | Where-Object { $_ -match "^D\s" }

if ($deleted.Count -gt 0 -and -not $AllowDeletes) {
    Write-Host ($deleted -join "`n")
    Fail "File deletion detected. Deletes are denied by default."
}

if ($files.Count -eq 0) {
    Pass "No working-tree changes detected."
}
else {
    Write-Host ""
    Write-Host "Changed files:"
    $files | ForEach-Object { Write-Host "  $_" }
}

Write-Host ""
Write-Host "HEAD:"
git rev-parse HEAD

Write-Host ""
Write-Host "Working tree:"
git status --short

Write-Host ""
Write-Host "GUARDRAIL RESULT: PASS"
exit 0

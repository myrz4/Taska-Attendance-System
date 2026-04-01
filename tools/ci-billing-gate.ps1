param(
  [switch]$SkipNpmInstall
)

$ErrorActionPreference = 'Stop'

$repoRoot = Split-Path -Parent $PSScriptRoot
$compileScript = Join-Path $PSScriptRoot 'compile-javafx.ps1'

Write-Host "Running JavaFX compile gate..." -ForegroundColor Cyan
& powershell -NoProfile -ExecutionPolicy Bypass -File $compileScript -ProjectRoot $repoRoot
if ($LASTEXITCODE -ne 0) {
  throw "JavaFX compile gate failed with exit code $LASTEXITCODE"
}

Push-Location $repoRoot
try {
  if (-not $SkipNpmInstall) {
    Write-Host "Running npm install for root tooling..." -ForegroundColor Cyan
    npm install
    if ($LASTEXITCODE -ne 0) {
      throw "Root npm install failed with exit code $LASTEXITCODE"
    }
  }

  Write-Host "Running billing emulator regression gate..." -ForegroundColor Cyan
  npm run smoke:dummy-billing
  if ($LASTEXITCODE -ne 0) {
    throw "Billing emulator regression gate failed with exit code $LASTEXITCODE"
  }
}
finally {
  Pop-Location
}

Write-Host "Billing CI gate completed successfully." -ForegroundColor Green
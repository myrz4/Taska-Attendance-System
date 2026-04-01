param(
  [switch]$SkipJavaBuild,
  [switch]$SkipAssetCopy,
  [switch]$SkipDeploy,
  [switch]$SkipNpmInstall,
  [switch]$SkipPreDeployCheck,
  [switch]$SkipPostDeploySmoke,
  [string]$FunctionsDir = "teacher_app_taskazurah/functions"
)

$ErrorActionPreference = 'Stop'

$repoRoot = Split-Path -Parent $PSScriptRoot
$compileScript = Join-Path $PSScriptRoot 'compile-javafx.ps1'
$copyAssetsScript = Join-Path $PSScriptRoot 'copy-assets.ps1'
$deployScript = Join-Path $PSScriptRoot 'deploy-billing-functions.ps1'

if (-not $SkipJavaBuild) {
  Write-Host "Running JavaFX compile..." -ForegroundColor Cyan
  & powershell -NoProfile -ExecutionPolicy Bypass -File $compileScript -ProjectRoot $repoRoot
  if ($LASTEXITCODE -ne 0) {
    throw "JavaFX compile failed with exit code $LASTEXITCODE"
  }
}

if (-not $SkipAssetCopy) {
  Write-Host "Copying JavaFX assets..." -ForegroundColor Cyan
  Push-Location $repoRoot
  try {
    & powershell -NoProfile -ExecutionPolicy Bypass -File $copyAssetsScript
    if ($LASTEXITCODE -ne 0) {
      throw "JavaFX asset copy failed with exit code $LASTEXITCODE"
    }
  }
  finally {
    Pop-Location
  }
}

if ($SkipDeploy) {
  Write-Host "Skipping billing deploy. Java build and local prep completed." -ForegroundColor Yellow
  exit 0
}

$deployArgs = @(
  '-NoProfile',
  '-ExecutionPolicy', 'Bypass',
  '-File', $deployScript,
  '-FunctionsDir', $FunctionsDir
)

if ($SkipNpmInstall) {
  $deployArgs += '-SkipNpmInstall'
}
if ($SkipPreDeployCheck) {
  $deployArgs += '-SkipPreDeployCheck'
}
if ($SkipPostDeploySmoke) {
  $deployArgs += '-SkipPostDeploySmoke'
}

Write-Host "Running billing functions deploy flow..." -ForegroundColor Cyan
& powershell @deployArgs
if ($LASTEXITCODE -ne 0) {
  throw "Billing release flow failed with exit code $LASTEXITCODE"
}

Write-Host "Billing release flow completed successfully." -ForegroundColor Green
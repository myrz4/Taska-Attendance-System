param(
  [string]$FunctionsDir = "teacher_app_taskazurah/functions",
  [switch]$SetSecrets,
  [switch]$SkipNpmInstall,
  [switch]$SkipPreDeployCheck,
  [switch]$SkipPostDeploySmoke
)

$ErrorActionPreference = 'Stop'

$repoRoot = Split-Path -Parent $PSScriptRoot
$targetDir = Join-Path $repoRoot $FunctionsDir
$preDeployCheckScript = Join-Path $repoRoot "tools/check-billing-predeploy.js"
$postDeploySmokeScript = Join-Path $repoRoot "tools/smoke-billing-postdeploy.js"

if (-not (Test-Path $targetDir)) {
  throw "Functions directory not found: $targetDir"
}

Push-Location $targetDir
try {
  if (-not $SkipNpmInstall) {
    npm install
  }

  if ($SetSecrets) {
    firebase functions:secrets:set BILLPLZ_API_KEY
    firebase functions:secrets:set BILLPLZ_X_SIGNATURE_KEY
  }

  if (-not $SkipPreDeployCheck) {
    if (-not (Test-Path $preDeployCheckScript)) {
      throw "Pre-deploy billing check script not found: $preDeployCheckScript"
    }

    Write-Host "Running pre-deploy billing catalog check..." -ForegroundColor Cyan
    node $preDeployCheckScript
    if ($LASTEXITCODE -ne 0) {
      throw "Pre-deploy billing check failed with exit code $LASTEXITCODE"
    }
  }

  firebase deploy --only functions

  if (-not $SkipPostDeploySmoke) {
    if (-not (Test-Path $postDeploySmokeScript)) {
      throw "Post-deploy billing smoke script not found: $postDeploySmokeScript"
    }

    Write-Host "Running post-deploy billing smoke..." -ForegroundColor Cyan
    node $postDeploySmokeScript
    if ($LASTEXITCODE -ne 0) {
      throw "Post-deploy billing smoke failed with exit code $LASTEXITCODE"
    }
  }
}
finally {
  Pop-Location
}
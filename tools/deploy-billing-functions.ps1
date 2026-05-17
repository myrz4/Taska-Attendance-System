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

$firebaseInvoker = $null
$firebaseArgsPrefix = @()
if (Get-Command firebase -ErrorAction SilentlyContinue) {
  $firebaseInvoker = (Get-Command firebase -ErrorAction Stop).Source
}
elseif (Get-Command 'npx.cmd' -ErrorAction SilentlyContinue) {
  $firebaseInvoker = (Get-Command 'npx.cmd' -ErrorAction Stop).Source
  $firebaseArgsPrefix = @('firebase-tools')
}
elseif (Get-Command npx -ErrorAction SilentlyContinue) {
  $firebaseInvoker = (Get-Command npx -ErrorAction Stop).Source
  $firebaseArgsPrefix = @('firebase-tools')
}
else {
  throw "Neither 'firebase' nor 'npx' is available in PATH."
}

function Invoke-FirebaseCli {
  param(
    [Parameter(Mandatory = $true)]
    [string[]]$Arguments
  )

  $commandArgs = @()
  if ($firebaseArgsPrefix.Count -gt 0) {
    $commandArgs += $firebaseArgsPrefix
  }
  $commandArgs += $Arguments

  $process = Start-Process -FilePath $firebaseInvoker -ArgumentList $commandArgs -NoNewWindow -Wait -PassThru
  if ($process.ExitCode -ne 0) {
    throw "Firebase CLI command failed with exit code $($process.ExitCode): $($Arguments -join ' ')"
  }
}

if (-not (Test-Path $targetDir)) {
  throw "Functions directory not found: $targetDir"
}

Push-Location $targetDir
try {
  if (-not $SkipNpmInstall) {
    npm install
    if ($LASTEXITCODE -ne 0) {
      throw "npm install failed with exit code $LASTEXITCODE"
    }
  }

  if ($SetSecrets) {
    Invoke-FirebaseCli -Arguments @('functions:secrets:set', 'BILLPLZ_API_KEY')
    Invoke-FirebaseCli -Arguments @('functions:secrets:set', 'BILLPLZ_X_SIGNATURE_KEY')
    Invoke-FirebaseCli -Arguments @('functions:secrets:set', 'STRIPE_SECRET_KEY')
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

  Invoke-FirebaseCli -Arguments @('deploy', '--only', 'functions')

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
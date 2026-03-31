param(
  [string]$FunctionsDir = "teacher_app_taskazurah/functions",
  [switch]$SetSecrets,
  [switch]$SkipNpmInstall
)

$ErrorActionPreference = 'Stop'

$repoRoot = Split-Path -Parent $PSScriptRoot
$targetDir = Join-Path $repoRoot $FunctionsDir

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

  firebase deploy --only functions
}
finally {
  Pop-Location
}
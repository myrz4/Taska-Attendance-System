param(
  [string]$ProjectRoot = $PSScriptRoot,
  [string]$Port = '',
  [switch]$SkipBuild,
  [switch]$PrintOnly,
  [switch]$StopArduinoProcesses,
  [int]$Retries = 3
)

$scriptPath = Join-Path $PSScriptRoot 'tools\upload-esp32-manual-boot.ps1'
if (-not (Test-Path $scriptPath)) {
  throw "Missing upload script: $scriptPath"
}

& $scriptPath -ProjectRoot $ProjectRoot -Port $Port -SkipBuild:$SkipBuild -PrintOnly:$PrintOnly -StopArduinoProcesses:$StopArduinoProcesses -Retries $Retries
exit $LASTEXITCODE
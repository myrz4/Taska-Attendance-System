<#
  Copies non-.java assets from src/nfc into bin/nfc so ImageLoader.getResource("/nfc/<file>") works.

  Usage:
    powershell -NoProfile -ExecutionPolicy Bypass -File tools\copy-assets.ps1
#>

$ErrorActionPreference = 'SilentlyContinue'

$root = (Get-Location).Path
$srcDir = Join-Path $root 'src\nfc'
$dstDir = Join-Path $root 'bin\nfc'

if (-not (Test-Path -LiteralPath $srcDir)) {
  Write-Host "src/nfc not found: $srcDir"
  exit 1
}

New-Item -ItemType Directory -Force -Path $dstDir | Out-Null

$allowedExt = @(
  '.png', '.jpg', '.jpeg', '.gif', '.bmp',
  '.css',
  '.xml',
  '.html', '.htm',
  '.ico',
  '.csv',
  '.yml', '.yaml',
  '.properties'
)

$files = Get-ChildItem -LiteralPath $srcDir -Recurse -File -Force |
  Where-Object {
    $ext = $_.Extension
    if ([string]::IsNullOrWhiteSpace($ext)) { return $false }
    $allowedExt -contains $ext.ToLowerInvariant()
  }

# Safety: remove any previously-copied service account key from bin
$dstKey = Join-Path $dstDir 'serviceAccountKey.json'
if (Test-Path -LiteralPath $dstKey) {
  Remove-Item -LiteralPath $dstKey -Force | Out-Null
}

foreach ($f in $files) {
  $rel = $f.FullName.Substring($srcDir.Length).TrimStart('\\','/')
  $target = Join-Path $dstDir $rel
  $targetDir = Split-Path -Parent $target
  New-Item -ItemType Directory -Force -Path $targetDir | Out-Null
  Copy-Item -LiteralPath $f.FullName -Destination $target -Force
}

Write-Host ("Copied {0} asset files to {1}" -f $files.Count, $dstDir)

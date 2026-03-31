<#
  Clean generated artifacts from this workspace to reduce size.
  - Defaults to DryRun (prints what would be deleted).
  - Only targets build outputs/caches that can be regenerated.
  - Does NOT delete source code, jar_files, or javafx-sdk unless explicitly requested.

  Usage:
    pwsh -File tools/clean.ps1                # dry-run
    pwsh -File tools/clean.ps1 -DryRun:$false # actually delete

  Optional:
    pwsh -File tools/clean.ps1 -DryRun:$false -IncludeFirestoreExports
    pwsh -File tools/clean.ps1 -DryRun:$false -RemoveJavafxSdk
#>

[CmdletBinding()]
param(
  [bool]$DryRun = $true,
  [bool]$IncludeFirestoreExports = $false,
  [bool]$RemoveJavafxSdk = $false
)

$ErrorActionPreference = 'SilentlyContinue'

function Write-Info([string]$msg) { Write-Host $msg }

function Remove-DirIfExists([string]$path) {
  if (-not (Test-Path -LiteralPath $path)) { return }

  if ($DryRun) {
    Write-Info ("[DRY-RUN] Would delete: {0}" -f $path)
    return
  }

  try {
    # Clear read-only attributes recursively (common on Windows)
    attrib -R "$path\*" /S /D | Out-Null
  } catch { }

  Write-Info ("Deleting: {0}" -f $path)
  Remove-Item -LiteralPath $path -Recurse -Force
}

function Remove-GlobDirs([string]$root, [string[]]$names) {
  foreach ($name in $names) {
    Get-ChildItem -LiteralPath $root -Directory -Recurse -Force -Filter $name |
      Where-Object {
        $_.FullName -notlike (Join-Path $root '_inspect_appimage*')
      } |
      ForEach-Object { Remove-DirIfExists $_.FullName }
  }
}

$root = (Get-Location).Path

Write-Info "Workspace: $root"
if ($DryRun) {
  Write-Info "Mode: DRY-RUN"
} else {
  Write-Info "Mode: DELETE"
}

# 1) High-confidence huge artifacts
Remove-DirIfExists (Join-Path $root '_inspect_appimage')

# 2) Java build outputs
Remove-DirIfExists (Join-Path $root 'bin')
Remove-DirIfExists (Join-Path $root 'target')
Remove-DirIfExists (Join-Path $root 'src\bin')
Remove-DirIfExists (Join-Path $root 'src\target')
Remove-DirIfExists (Join-Path $root 'src\src\bin')
Remove-DirIfExists (Join-Path $root 'src\src\target')

# 3) Flutter build/cache outputs (parent + teacher apps)
$flutterApps = @(
  (Join-Path $root 'parent_app_taskazurah'),
  (Join-Path $root 'teacher_app_taskazurah')
)

foreach ($app in $flutterApps) {
  if (-not (Test-Path -LiteralPath $app)) { continue }

  Remove-DirIfExists (Join-Path $app 'build')
  Remove-DirIfExists (Join-Path $app '.dart_tool')

  # Android Gradle caches/build
  Remove-DirIfExists (Join-Path $app 'android\.gradle')
  Remove-DirIfExists (Join-Path $app 'android\build')
  Remove-DirIfExists (Join-Path $app 'android\app\build')

  # iOS CocoaPods (optional but usually safe to regen)
  Remove-DirIfExists (Join-Path $app 'ios\Pods')
  Remove-DirIfExists (Join-Path $app 'ios\.symlinks')

  # Desktop ephemeral
  Remove-DirIfExists (Join-Path $app 'windows\flutter\ephemeral')
  Remove-DirIfExists (Join-Path $app 'linux\flutter\ephemeral')
  Remove-DirIfExists (Join-Path $app 'macos\Flutter\ephemeral')
}

# 4) Node build outputs (if present)
Remove-GlobDirs $root @('node_modules', '.parcel-cache', '.next', '.nuxt', '.vite', 'dist', 'out')

# 5) Firestore export output (keep backups unless explicitly requested)
if ($IncludeFirestoreExports) {
  Remove-DirIfExists (Join-Path $root 'firestore-export\exports')
  Remove-DirIfExists (Join-Path $root 'firestore-export\fix\node_modules')
}

# 6) JavaFX SDK (only if you want it removed and you can reference it elsewhere)
if ($RemoveJavafxSdk) {
  Get-ChildItem -LiteralPath $root -Directory -Force -Filter 'javafx-sdk-*' |
    ForEach-Object { Remove-DirIfExists $_.FullName }
}

Write-Info "Done."

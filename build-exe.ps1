# 🍋 Taska Zuhrah Attendance System - EXE Build Script
# Requires: JDK 17+ with jpackage, JavaFX SDK 21.0.9, and your existing structure

$appName = "Taska Attendance System"
$appVersion = "1.0.0"
$outputDir = Join-Path (Get-Location).Path (Join-Path "dist" (Get-Date -Format "yyyyMMdd-HHmmss"))
$installerPath = Join-Path $outputDir "$appName-$appVersion.exe"
$appImageDir = Join-Path $outputDir $appName
$launcherCfgPath = Join-Path $appImageDir "app\$appName.cfg"

function Invoke-JPackage {
  param(
    [string[]]$Arguments
  )

  & jpackage @Arguments
  if ($LASTEXITCODE -ne 0) {
    throw "jpackage failed with exit code $LASTEXITCODE"
  }
}

# 🧹 Clean old compiled files
Write-Host "Cleaning old build..." -ForegroundColor Yellow
Remove-Item -Recurse -Force bin -ErrorAction Ignore
New-Item -ItemType Directory -Force -Path "bin\nfc" | Out-Null

# 🧠 Compile all .java source files recursively
Write-Host "Compiling source..." -ForegroundColor Cyan
javac --module-path "javafx-sdk-21.0.9\lib" `
      --add-modules javafx.controls,javafx.fxml `
      -cp "jar_files/*" `
      -d bin `
      (Get-ChildItem -Path src -Recurse -Filter *.java).FullName `
      --release 17

# 📦 Copy static resources
Write-Host "Copying runtime assets..." -ForegroundColor Cyan
powershell -NoProfile -ExecutionPolicy Bypass -File tools\copy-assets.ps1

# 🧩 Package compiled classes into a runnable JAR
Write-Host "Creating runnable JAR..." -ForegroundColor Cyan
jar --create --file "TaskaAttendanceSystem.jar" -C bin .

# 📦 Stage a clean jpackage input folder (prevents accidentally shipping repo files like serviceAccountKey.json)
Write-Host "Staging jpackage input..." -ForegroundColor Cyan
$stage = Join-Path (Get-Location).Path "dist-input"
Remove-Item -Recurse -Force $stage -ErrorAction Ignore
New-Item -ItemType Directory -Force -Path $stage | Out-Null
New-Item -ItemType Directory -Force -Path $outputDir | Out-Null

# Main runnable jar
Copy-Item -LiteralPath "TaskaAttendanceSystem.jar" -Destination $stage -Force

# Dependency jars + runtime properties expected by the desktop app.
Copy-Item -LiteralPath "jar_files" -Destination (Join-Path $stage "jar_files") -Recurse -Force

# The packaged app needs the full JavaFX SDK layout at runtime so the lib
# directory keeps its sibling bin folder with the native JavaFX renderer DLLs.
Copy-Item -LiteralPath "javafx-sdk-21.0.9" -Destination (Join-Path $stage "javafx-sdk-21.0.9") -Recurse -Force

# Build an app-image first so we can patch the launcher config to match the
# known-good local JavaFX startup: main jar on the classpath, dependencies and
# JavaFX jars under jar_files, and JavaFX modules added explicitly at launch.
Write-Host "Building app image..." -ForegroundColor Green
Invoke-JPackage -Arguments @(
  '--dest', $outputDir,
  '--type', 'app-image',
  '--input', $stage,
  '--main-jar', 'TaskaAttendanceSystem.jar',
  '--main-class', 'nfc.LoginView',
  '--name', $appName,
  '--icon', 'src/nfc/logo.ico',
  '--app-version', $appVersion,
  '--vendor', 'mirza dev',
  '--description', 'A nursery attendance system using NFC and Firestore integration.'
)

if (-not (Test-Path -LiteralPath $launcherCfgPath)) {
  throw "jpackage app launcher config not found: $launcherCfgPath"
}

$launcherCfg = @"
[Application]
app.classpath=`$APPDIR\TaskaAttendanceSystem.jar;`$APPDIR\jar_files\*
app.mainclass=nfc.LoginView

[JavaOptions]
java-options=--module-path
java-options=`$APPDIR\javafx-sdk-21.0.9\lib
java-options=--add-modules
java-options=javafx.controls,javafx.fxml,javafx.graphics,javafx.base
java-options=-Djpackage.app-version=$appVersion
"@
Set-Content -LiteralPath $launcherCfgPath -Value $launcherCfg -Encoding ascii

Write-Host "Packaging installer..." -ForegroundColor Green
Invoke-JPackage -Arguments @(
  '--dest', $outputDir,
  '--type', 'exe',
  '--app-image', $appImageDir,
  '--name', $appName,
  '--app-version', $appVersion,
  '--vendor', 'mirza dev',
  '--description', 'A nursery attendance system using NFC and Firestore integration.'
)

Write-Host "✅ Build complete! EXE created at $installerPath" -ForegroundColor Green
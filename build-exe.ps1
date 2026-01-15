# 🍋 Taska Zuhrah Attendance System - EXE Build Script
# Requires: JDK 17+ with jpackage, JavaFX SDK 21.0.9, and your existing structure

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
Copy-Item "src/nfc/*.png" -Destination "bin/nfc" -Force -ErrorAction SilentlyContinue
Copy-Item "src/nfc/*.css" -Destination "bin/nfc" -Force -ErrorAction SilentlyContinue

# 🧩 Package compiled classes into a runnable JAR
Write-Host "Creating runnable JAR..." -ForegroundColor Cyan
jar --create --file "TaskaAttendanceSystem.jar" -C bin .

# 🏗️ Build .exe using jpackage
Write-Host "Packaging into EXE..." -ForegroundColor Green
jpackage `
  --type exe `
  --input . `
  --main-jar "TaskaAttendanceSystem.jar" `
  --main-class nfc.LoginView `
  --name "Taska Attendance System" `
  --icon "src/nfc/logo.ico" `
  --module-path "javafx-sdk-21.0.9\lib" `
  --add-modules javafx.controls,javafx.fxml `
  --win-console `
  --app-version "1.0.0" `
  --vendor "Bee Caliph Nursery" `
  --description "A nursery attendance system using NFC and Firestore integration."

Write-Host "✅ Build complete! EXE created in /Taska Attendance System directory." -ForegroundColor Green
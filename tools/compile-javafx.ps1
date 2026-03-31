param(
  [string]$ProjectRoot = (Get-Location).Path,
  [string]$JavaRelease = '17'
)

$ErrorActionPreference = 'Stop'

Set-Location $ProjectRoot

$javaFxLib = Join-Path $ProjectRoot 'javafx-sdk-21.0.9\lib'
$jarPath = Join-Path $ProjectRoot 'jar_files'

if (-not (Test-Path $javaFxLib)) {
  throw "JavaFX SDK lib folder not found: $javaFxLib"
}

if (-not (Test-Path $jarPath)) {
  throw "Dependency folder not found: $jarPath"
}

$classpath = "$jarPath/*"

$sources = Get-ChildItem -Path 'src/nfc' -Filter '*.java' | ForEach-Object { $_.FullName }
if (-not $sources -or $sources.Count -eq 0) {
  throw 'No Java source files found under src/nfc'
}

& javac --module-path $javaFxLib --add-modules javafx.controls,javafx.fxml,javafx.graphics,javafx.base -cp $classpath -d 'bin' --release $JavaRelease $sources

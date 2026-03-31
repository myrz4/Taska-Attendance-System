param(
  [string]$ProjectRoot = (Get-Location).Path,
  [string]$MainClass = 'nfc.LoginView',
  [string]$NfcPort = ''
)

$ErrorActionPreference = 'Stop'

function Resolve-TaskaProjectRoot {
  param(
    [string]$InputPath
  )

  if (-not $InputPath -or -not $InputPath.Trim()) {
    return (Get-Location).Path
  }

  $resolved = $null
  if (Test-Path $InputPath -PathType Leaf) {
    $resolved = Split-Path -Path (Resolve-Path $InputPath) -Parent
  } elseif (Test-Path $InputPath -PathType Container) {
    $resolved = (Resolve-Path $InputPath).Path
  } else {
    return $InputPath
  }

  $current = $resolved
  while ($current) {
    $hasJavaFx = Test-Path (Join-Path $current 'javafx-sdk-21.0.9\lib')
    $hasJarFiles = Test-Path (Join-Path $current 'jar_files')
    $hasBin = Test-Path (Join-Path $current 'bin')
    if ($hasJavaFx -and $hasJarFiles -and $hasBin) {
      return $current
    }

    $parent = Split-Path -Path $current -Parent
    if (-not $parent -or $parent -eq $current) {
      break
    }
    $current = $parent
  }

  return $resolved
}

$ProjectRoot = Resolve-TaskaProjectRoot -InputPath $ProjectRoot

Set-Location $ProjectRoot

$javaFxLib = Join-Path $ProjectRoot 'javafx-sdk-21.0.9\lib'
$jarPath = Join-Path $ProjectRoot 'jar_files'
$binPath = Join-Path $ProjectRoot 'bin'

if (-not (Test-Path $javaFxLib)) {
  throw "JavaFX SDK lib folder not found: $javaFxLib"
}

if (-not (Test-Path $jarPath)) {
  throw "Dependency folder not found: $jarPath"
}

if (-not (Test-Path $binPath)) {
  throw "Compiled output folder not found: $binPath. Run the build task first."
}

$classpath = "$binPath;$jarPath/*"

$javaArgs = @(
  '--module-path', $javaFxLib,
  '--add-modules', 'javafx.controls,javafx.fxml,javafx.graphics,javafx.base'
)

if ($NfcPort -and $NfcPort.Trim()) {
  $javaArgs += "-Dtaska.nfc.port=$($NfcPort.Trim())"
}

& java @javaArgs -cp $classpath $MainClass
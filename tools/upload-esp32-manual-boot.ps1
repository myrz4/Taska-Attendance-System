param(
  [string]$ProjectRoot = (Split-Path -Path $PSScriptRoot -Parent),
  [string]$Port = '',
  [switch]$SkipBuild,
  [switch]$PrintOnly,
  [switch]$StopArduinoProcesses,
  [int]$Retries = 3
)

$ErrorActionPreference = 'Stop'

function Resolve-ArduinoCliPath {
  $candidates = @(
    "$env:LOCALAPPDATA\Programs\Arduino IDE\resources\app\lib\backend\resources\arduino-cli.exe",
    "$env:LOCALAPPDATA\Arduino15\arduino-cli.exe"
  )

  foreach ($candidate in $candidates) {
    if ($candidate -and (Test-Path $candidate)) {
      return $candidate
    }
  }

  throw 'arduino-cli.exe not found. Install Arduino IDE 2.x or Arduino CLI first.'
}

function Resolve-TaskaProjectRoot {
  param([string]$InputPath)

  if (-not $InputPath -or -not $InputPath.Trim()) {
    return (Get-Location).Path
  }

  if (Test-Path $InputPath -PathType Container) {
    return (Resolve-Path $InputPath).Path
  }

  $resolved = Resolve-Path $InputPath -ErrorAction SilentlyContinue
  if ($resolved) {
    return Split-Path -Path $resolved.Path -Parent
  }

  return $InputPath
}

function Get-SerialPorts {
  $ports = Get-CimInstance Win32_SerialPort -ErrorAction SilentlyContinue |
    Where-Object { $_.DeviceID -match '^COM\d+$' } |
    Select-Object DeviceID, Description

  if (-not $ports) {
    return @()
  }

  return @($ports)
}

function Get-LikelyPortHolderProcesses {
  $candidates = Get-CimInstance Win32_Process -ErrorAction SilentlyContinue |
    Where-Object {
      $_.Name -in @('Arduino IDE.exe', 'arduino-cli.exe', 'arduino-language-server.exe')
    } |
    Select-Object ProcessId, Name, CommandLine

  if (-not $candidates) {
    return @()
  }

  return @($candidates)
}

function Stop-LikelyPortHolders {
  $processes = Get-LikelyPortHolderProcesses
  if (-not $processes.Count) {
    Write-Host 'No Arduino IDE background processes found to stop.'
    return
  }

  Write-Host 'Stopping likely COM-port holder processes:'
  foreach ($proc in $processes) {
    Write-Host ("  - {0} [{1}]" -f $proc.Name, $proc.ProcessId)
    try {
      Stop-Process -Id $proc.ProcessId -Force -ErrorAction Stop
    } catch {
      Write-Host ("    Failed to stop process {0}: {1}" -f $proc.ProcessId, $_.Exception.Message)
    }
  }

  Start-Sleep -Milliseconds 800
}

function Resolve-UploadPort {
  param([string]$RequestedPort)

  if ($RequestedPort -and $RequestedPort.Trim()) {
    return $RequestedPort.Trim().ToUpperInvariant()
  }

  $ports = Get-SerialPorts
  if (-not $ports.Count) {
    throw 'No serial COM ports detected.'
  }

  $preferred = $ports | Where-Object {
    ($_.Description -match 'USB') -or
    ($_.Description -match 'CP210') -or
    ($_.Description -match 'CH340') -or
    ($_.Description -match 'Silicon') -or
    ($_.Description -match 'UART') -or
    ($_.Description -match 'ESP32')
  } | Select-Object -First 1

  if ($preferred) {
    return $preferred.DeviceID
  }

  return ($ports | Select-Object -First 1).DeviceID
}

$cli = Resolve-ArduinoCliPath
$ProjectRoot = Resolve-TaskaProjectRoot -InputPath $ProjectRoot
$sketchPath = Join-Path $ProjectRoot 'TaskaNFCAttendance'
$buildPath = Join-Path $ProjectRoot '.arduino_build\esp32'
$fqbn = 'esp32:esp32:esp32'
$boardOptions = 'UploadSpeed=115200,PartitionScheme=huge_app'
$Port = Resolve-UploadPort -RequestedPort $Port

if (-not (Test-Path $sketchPath)) {
  throw "Sketch folder not found: $sketchPath"
}

$compileArgs = @(
  'compile',
  '--fqbn', $fqbn,
  '--board-options', $boardOptions,
  '--build-path', $buildPath,
  $sketchPath
)

$uploadArgs = @(
  'upload',
  '--fqbn', $fqbn,
  '--board-options', $boardOptions,
  '--build-path', $buildPath,
  '--port', $Port,
  '--verbose',
  $sketchPath
)

Write-Host "Arduino CLI: $cli"
Write-Host "Project root: $ProjectRoot"
Write-Host "Sketch: $sketchPath"
Write-Host "Port: $Port"
Write-Host "Board: $fqbn [$boardOptions]"

$availablePorts = Get-SerialPorts
if ($availablePorts.Count) {
  Write-Host 'Detected serial ports:'
  foreach ($entry in $availablePorts) {
    Write-Host ("  - {0} : {1}" -f $entry.DeviceID, $entry.Description)
  }
}

$likelyPortHolders = Get-LikelyPortHolderProcesses
if ($likelyPortHolders.Count) {
  Write-Host 'Likely port-holder processes detected:'
  foreach ($proc in $likelyPortHolders) {
    Write-Host ("  - {0} [{1}]" -f $proc.Name, $proc.ProcessId)
  }
}

if ($PrintOnly) {
  Write-Host ''
  Write-Host 'Compile command:'
  Write-Host ((@($cli) + $compileArgs) -join ' ')
  Write-Host ''
  Write-Host 'Upload command:'
  Write-Host ((@($cli) + $uploadArgs) -join ' ')
  return
}

if ($StopArduinoProcesses) {
  Stop-LikelyPortHolders
}

if (-not $SkipBuild) {
  New-Item -ItemType Directory -Force -Path $buildPath | Out-Null
  & $cli @compileArgs
  if ($LASTEXITCODE -ne 0) {
    throw "Compile failed with exit code $LASTEXITCODE"
  }
}

for ($attempt = 1; $attempt -le $Retries; $attempt++) {
  Write-Host ''
  Write-Host ("Upload attempt {0}/{1}" -f $attempt, $Retries)
  Write-Host 'Manual boot steps:'
  Write-Host ("1. Make sure nothing else is using {0} (Arduino Serial Monitor, VS Code monitor, other tools)." -f $Port)
  Write-Host '2. Hold the BOOT button on the ESP32.'
  Write-Host '3. Tap EN or RESET once while still holding BOOT.'
  Write-Host '4. Keep holding BOOT and press Enter here to start upload.'
  Write-Host '5. Release BOOT only after the upload starts writing.'
  [void](Read-Host 'Press Enter when ready')

  & $cli @uploadArgs
  if ($LASTEXITCODE -eq 0) {
    Write-Host ''
    Write-Host 'ESP32 upload completed successfully.'
    exit 0
  }

  Write-Host ''
  Write-Host ("Upload failed on attempt {0}." -f $attempt)
}

throw "ESP32 upload failed after $Retries attempts on $Port."
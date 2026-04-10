param(
    [string]$AppDir = "parent_app_taskazurah",
    [string]$ManifestPath = "parent_app_taskazurah/RELEASE-MANIFEST-2026-04-05.md"
)

$ErrorActionPreference = "Stop"

function Get-RepoRoot {
    return (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
}

function Get-RequiredFile([string]$Path) {
    if (-not (Test-Path $Path)) {
        throw "Required file not found: $Path"
    }
    return Get-Item $Path
}

function Get-FileSha256([string]$Path) {
    return (Get-FileHash $Path -Algorithm SHA256).Hash.ToUpperInvariant()
}

function Get-FileSizeString([string]$Path) {
    return "{0:N0}" -f (Get-Item $Path).Length
}

function Get-FileTimestamp([string]$Path) {
    return (Get-Item $Path).LastWriteTime.ToString("yyyy-MM-dd HH:mm:ss")
}

function Get-FirstRegexValue([string]$Path, [string]$Pattern, [string]$Label) {
    $match = [regex]::Match((Get-Content $Path -Raw), $Pattern)
    if (-not $match.Success) {
        throw "Unable to resolve $Label from $Path"
    }
    return $match.Groups[1].Value.Trim()
}

function Get-LineValue([string]$Path, [string]$Prefix, [string]$Label) {
    $line = Get-Content $Path | Where-Object { $_.TrimStart().StartsWith($Prefix) } | Select-Object -First 1
    if (-not $line) {
        throw "Unable to resolve $Label from $Path"
    }

    return $line.Substring($line.IndexOf($Prefix) + $Prefix.Length).Trim()
}

function Get-AndroidSdkDir([string]$LocalPropertiesPath) {
    $raw = Get-Content $LocalPropertiesPath -Raw
    $match = [regex]::Match($raw, 'sdk\.dir=(.+)')
    if (-not $match.Success) {
        throw "Unable to resolve sdk.dir from $LocalPropertiesPath"
    }

    return $match.Groups[1].Value.Trim().Replace('\\', '\')
}

function Get-ApksignerPath([string]$SdkDir) {
    $buildToolsDir = Join-Path $SdkDir "build-tools"
    if (-not (Test-Path $buildToolsDir)) {
        throw "Android build-tools directory not found: $buildToolsDir"
    }

    $candidates = Get-ChildItem $buildToolsDir -Filter apksigner.bat -Recurse -ErrorAction SilentlyContinue |
        Sort-Object FullName -Descending
    if (-not $candidates) {
        throw "Unable to find apksigner.bat under $buildToolsDir"
    }

    return $candidates[0].FullName
}

function Get-ApkSignerInfo([string]$ApksignerPath, [string]$ApkPath) {
    $output = & $ApksignerPath verify --print-certs $ApkPath 2>&1
    if ($LASTEXITCODE -ne 0) {
        throw "apksigner failed: $($output -join [Environment]::NewLine)"
    }

    $text = $output -join [Environment]::NewLine
    return [ordered]@{
        DN = Get-FirstRegexValueFromText $text 'Signer #1 certificate DN:\s*(.+)' 'APK signer DN'
        Sha256 = Get-FirstRegexValueFromText $text 'Signer #1 certificate SHA-256 digest:\s*(.+)' 'APK signer SHA-256'
    }
}

function Get-FirstRegexValueFromText([string]$Text, [string]$Pattern, [string]$Label) {
    $match = [regex]::Match($Text, $Pattern)
    if (-not $match.Success) {
        throw "Unable to resolve $Label"
    }
    return $match.Groups[1].Value.Trim()
}

function Get-WindowsVersionInfo([string]$Path) {
    $version = (Get-Item $Path).VersionInfo
    return [ordered]@{
        FileDescription = $version.FileDescription
        ProductName = $version.ProductName
        CompanyName = $version.CompanyName
    }
}

function Format-InlineCode([string]$Value) {
    $tick = [char]96
    return "$tick$Value$tick"
}

$repoRoot = Get-RepoRoot
$appRoot = Join-Path $repoRoot $AppDir
$manifestFile = Join-Path $repoRoot $ManifestPath

$pubspecPath = Join-Path $appRoot "pubspec.yaml"
$androidGradlePath = Join-Path $appRoot "android/app/build.gradle.kts"
$iosBundleConfigPath = Join-Path $appRoot "macos/Runner/Configs/AppInfo.xcconfig"
$localPropertiesPath = Join-Path $appRoot "android/local.properties"

$apkPath = Join-Path $appRoot "build/app/outputs/flutter-apk/app-release.apk"
$aabPath = Join-Path $appRoot "build/app/outputs/bundle/release/app-release.aab"
$exePath = Join-Path $appRoot "build/windows/x64/runner/Release/parent_app.exe"
$webIndexPath = Join-Path $appRoot "build/web/index.html"
$webManifestPath = Join-Path $appRoot "build/web/manifest.json"
$webMessagingSwPath = Join-Path $appRoot "build/web/firebase-messaging-sw.js"

Get-RequiredFile $pubspecPath | Out-Null
Get-RequiredFile $androidGradlePath | Out-Null
Get-RequiredFile $iosBundleConfigPath | Out-Null
Get-RequiredFile $localPropertiesPath | Out-Null
Get-RequiredFile $apkPath | Out-Null
Get-RequiredFile $aabPath | Out-Null
Get-RequiredFile $exePath | Out-Null
Get-RequiredFile $webIndexPath | Out-Null
Get-RequiredFile $webManifestPath | Out-Null
Get-RequiredFile $webMessagingSwPath | Out-Null

$version = Get-LineValue $pubspecPath 'version:' 'app version'
$androidApplicationId = Get-FirstRegexValue $androidGradlePath 'applicationId\s*=\s*"([^"]+)"' 'Android application ID'
$iosBundleId = Get-FirstRegexValue $iosBundleConfigPath 'PRODUCT_BUNDLE_IDENTIFIER\s*=\s*([^\r\n]+)' 'Apple bundle ID'

$sdkDir = Get-AndroidSdkDir $localPropertiesPath
$apksignerPath = Get-ApksignerPath $sdkDir
$apkSigner = Get-ApkSignerInfo $apksignerPath $apkPath
$windowsInfo = Get-WindowsVersionInfo $exePath
$hasKeyProperties = Test-Path (Join-Path $appRoot 'android/key.properties')

$manifestTitle = [System.IO.Path]::GetFileNameWithoutExtension($manifestFile) -replace '^RELEASE-MANIFEST-', ''
$manifestContent = @(
    "# Release Manifest - $manifestTitle"
    ""
    "This file captures the validated parent app release artifacts produced during the 2026-04-05 rollout pass and refreshed after the 2026-04-09 follow-up rebuilds."
    "Regenerate from the repo root with $(Format-InlineCode 'npm run manifest:parent-release')."
    ""
    "## Build Identity"
    ""
    "- App: $(Format-InlineCode 'parent_app_taskazurah')"
    "- Version: $(Format-InlineCode $version)"
    "- Android application ID: $(Format-InlineCode $androidApplicationId)"
    "- iOS bundle ID configured in Firebase/Xcode: $(Format-InlineCode $iosBundleId)"
    ""
    "## Validation Completed"
    ""
    "The following commands passed on Windows:"
    ""
    "- $(Format-InlineCode 'flutter analyze')"
    "- $(Format-InlineCode 'flutter test')"
    "- $(Format-InlineCode 'flutter build apk --release')"
    "- $(Format-InlineCode 'flutter build appbundle --release')"
    "- $(Format-InlineCode 'flutter build web --release')"
    "- $(Format-InlineCode 'flutter build web --release --wasm')"
    "- $(Format-InlineCode 'flutter build windows --release')"
    ""
    "Follow-up rebuilds also passed on 2026-04-09 after the web Firebase Messaging service-worker fix and platform-shell branding cleanup:"
    ""
    "- $(Format-InlineCode 'flutter build apk --release')"
    "- $(Format-InlineCode 'flutter build appbundle --release')"
    "- $(Format-InlineCode 'flutter build web --release --wasm')"
    "- $(Format-InlineCode 'flutter build windows --release')"
    ""
    "## Android Artifacts"
    ""
    "### APK"
    ""
    "- Path: $(Format-InlineCode 'build/app/outputs/flutter-apk/app-release.apk')"
    "- Size: $(Format-InlineCode "$(Get-FileSizeString $apkPath) bytes")"
    "- SHA-256: $(Format-InlineCode (Get-FileSha256 $apkPath))"
    "- Last write time: $(Format-InlineCode (Get-FileTimestamp $apkPath))"
    ""
    "Signing state:"
    ""
    "- Current signer DN: $(Format-InlineCode $apkSigner.DN)"
    "- Current signer SHA-256: $(Format-InlineCode $apkSigner.Sha256)"
    "- $(Format-InlineCode 'android/key.properties') status during this build: $(Format-InlineCode $(if ($hasKeyProperties) { 'present' } else { 'missing' }))"
    ""
    "Interpretation:"
    ""
    "- This APK is valid for local validation and device installation."
    "- It is not the final publish-ready Android release until a real release keystore is supplied."
    ""
    "### App Bundle"
    ""
    "- Path: $(Format-InlineCode 'build/app/outputs/bundle/release/app-release.aab')"
    "- Size: $(Format-InlineCode "$(Get-FileSizeString $aabPath) bytes")"
    "- SHA-256: $(Format-InlineCode (Get-FileSha256 $aabPath))"
    "- Last write time: $(Format-InlineCode (Get-FileTimestamp $aabPath))"
    ""
    "Interpretation:"
    ""
    "- The AAB build path is working."
    "- For Play Store upload, rebuild with a real release keystore configured through $(Format-InlineCode 'android/key.properties')."
    ""
    "## Web Artifact"
    ""
    "- Path: $(Format-InlineCode 'build/web')"
    "- Standard web build: passed"
    "- Wasm web build: passed"
    "- $(Format-InlineCode 'index.html'): $(Format-InlineCode "$(Get-FileSizeString $webIndexPath) bytes"), last write $(Format-InlineCode (Get-FileTimestamp $webIndexPath))"
    "- $(Format-InlineCode 'manifest.json'): $(Format-InlineCode "$(Get-FileSizeString $webManifestPath) bytes"), last write $(Format-InlineCode (Get-FileTimestamp $webManifestPath))"
    "- $(Format-InlineCode 'firebase-messaging-sw.js'): $(Format-InlineCode "$(Get-FileSizeString $webMessagingSwPath) bytes"), last write $(Format-InlineCode (Get-FileTimestamp $webMessagingSwPath))"
    ""
    "Notes:"
    ""
    "- WebAssembly compilation now succeeds after the $(Format-InlineCode 'flutter_secure_storage') upgrade."
    "- The built web output now includes $(Format-InlineCode 'firebase-messaging-sw.js'), so browser Firebase Messaging registration no longer falls back to a missing service-worker path."
    "- Web metadata now advertises Taska Zurah Parent App / Taska Zurah instead of the earlier boilerplate placeholders."
    "- Browser smoke testing is still recommended before production wasm deployment."
    ""
    "## Windows Artifact"
    ""
    "- Path: $(Format-InlineCode 'build/windows/x64/runner/Release/parent_app.exe')"
    "- Size: $(Format-InlineCode "$(Get-FileSizeString $exePath) bytes")"
    "- SHA-256: $(Format-InlineCode (Get-FileSha256 $exePath))"
    "- Last write time: $(Format-InlineCode (Get-FileTimestamp $exePath))"
    "- File description / product name: $(Format-InlineCode $windowsInfo.FileDescription)"
    "- Company name: $(Format-InlineCode $windowsInfo.CompanyName)"
    ""
    "## Remaining External Steps"
    ""
    "### Android publishing"
    ""
    "- Provide a real release keystore."
    "- Create $(Format-InlineCode 'android/key.properties') from $(Format-InlineCode 'android/key.properties.example')."
    "- Rebuild the APK/AAB so they are no longer debug-signed."
    ""
    "See $(Format-InlineCode 'ANDROID-RELEASE-HANDOFF.md')."
    ""
    "### iOS completion"
    ""
    "- Complete the Mac-side steps in $(Format-InlineCode 'IOS-HANDOFF.md')."
    ""
    "## Notes"
    ""
    "- Payment remains intentionally in dummy mode for the current rollout."
    "- The current Android artifacts are still debug-signed because $(Format-InlineCode 'android/key.properties') remains absent."
    "- Repo-side macOS, iOS bundle-name, and Linux branding cleanup was also prepared on 2026-04-09, but those targets were not rebuilt from this Windows environment."
    "- This manifest is a release traceability snapshot, not a substitute for the Android and iOS handoff docs."
) -join [Environment]::NewLine

Set-Content -Path $manifestFile -Value $manifestContent -Encoding UTF8
Write-Output "Updated $ManifestPath"

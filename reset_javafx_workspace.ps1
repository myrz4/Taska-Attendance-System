Write-Host "🧹 Cleaning Java Language Server cache..."

# Close VS Code if open
Get-Process Code -ErrorAction SilentlyContinue | ForEach-Object { Stop-Process $_ -Force }

# Paths
$javaDir = "$env:APPDATA\Code\User\workspaceStorage"
$factoryPath = ".factorypath"

# Delete caches safely
if (Test-Path $javaDir) {
    Remove-Item -Recurse -Force $javaDir
    Write-Host "✅ Deleted workspaceStorage cache"
} else {
    Write-Host "ℹ️ No workspaceStorage cache found"
}

if (Test-Path $factoryPath) {
    Remove-Item -Force $factoryPath
    Write-Host "✅ Deleted .factorypath"
} else {
    Write-Host "ℹ️ No .factorypath file found"
}

# Reopen VS Code
Write-Host "🚀 Reopening VS Code project..."
Start-Process "code" -ArgumentList "C:\Users\zafri\Downloads\Taska Attendance System"

Write-Host "`n✅ Done. Wait until the status bar says 'Java: Ready'. Then reopen LoginView.java."
Pause
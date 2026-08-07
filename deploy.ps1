# Deploy fuck-sable jar to test-server mods directory
# Usage: .\deploy.ps1

$ErrorActionPreference = "Stop"

$projectRoot = "g:\sable (1)\sablecat"
$testServerMods = "g:\sable (1)\test-server\mods"

# Build the project
Write-Host "[deploy] Building fuck-sable..." -ForegroundColor Cyan
Set-Location $projectRoot
& .\gradlew.bat build -x test --no-daemon --console=plain 2>&1 | Select-String "BUILD|FAIL|error" | ForEach-Object { Write-Host $_.Line }

# Find the built jar
$jar = Get-ChildItem "$projectRoot\build\libs" -Filter "fuck-sable-*.jar" |
    Sort-Object LastWriteTime -Descending |
    Select-Object -First 1

if (-not $jar) {
    Write-Host "[deploy] ERROR: No fuck-sable-*.jar found in build/libs/" -ForegroundColor Red
    exit 1
}

# Remove old fuck-sable jars from mods dir
Get-ChildItem $testServerMods -Filter "fuck-sable-*.jar" | ForEach-Object {
    Write-Host "[deploy] Removing old: $($_.Name)" -ForegroundColor Yellow
    Remove-Item $_.FullName -Force
}

# Deploy
Copy-Item $jar.FullName $testServerMods -Force
Write-Host "[deploy] Deployed: $($jar.Name) -> $testServerMods" -ForegroundColor Green
Write-Host "[deploy] Done." -ForegroundColor Cyan

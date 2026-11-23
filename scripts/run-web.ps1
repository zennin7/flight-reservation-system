$ErrorActionPreference = "Stop"
. (Join-Path $PSScriptRoot "pg-common.ps1")
Import-SkyRoyalPath

$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
Set-Location $repoRoot

$null = Resolve-SkyRoyalJava
$mvnCmd = Resolve-SkyRoyalMaven -RepoRoot $repoRoot

$appJsonPath = Join-Path $repoRoot "core/src/main/resources/com/group2/flightres/app.json"
$hasEnvPass = (-not [string]::IsNullOrEmpty($env:SKY_ROYAL_DB_PASSWORD)) -or (-not [string]::IsNullOrEmpty($env:LAUNCH_DB_PASSWORD))
if (-not (Test-Path $appJsonPath) -and -not $hasEnvPass) {
    throw "Missing local database configuration (core/src/main/resources/com/group2/flightres/app.json). Run .\scripts\run-setup.cmd first."
}

$cfg = Read-SkyRoyalAppJson -RepoRoot $repoRoot
$dbHost = if (-not [string]::IsNullOrWhiteSpace($env:SKY_ROYAL_DB_HOST)) {
    $env:SKY_ROYAL_DB_HOST.Trim()
} elseif (-not [string]::IsNullOrWhiteSpace($env:LAUNCH_DB_HOST)) {
    $env:LAUNCH_DB_HOST.Trim()
} elseif ($cfg -and -not [string]::IsNullOrWhiteSpace($cfg.dbHost)) {
    $cfg.dbHost.Trim()
} else {
    "127.0.0.1"
}
$detectedPort = Find-SkyRoyalPostgresPort -HostName $dbHost -RepoRoot $repoRoot
Export-SkyRoyalDbEnv -RepoRoot $repoRoot -DetectedPort $detectedPort

if ([string]::IsNullOrWhiteSpace($env:SKY_ROYAL_WEB_PORT)) {
    if (-not [string]::IsNullOrWhiteSpace($env:PORT)) {
        $env:SKY_ROYAL_WEB_PORT = $env:PORT.Trim()
    } else {
        $env:SKY_ROYAL_WEB_PORT = "8080"
    }
}

Write-Host "[BUILD] Preparing core module..."
& $mvnCmd -pl core -am -DskipTests install -q
if ($LASTEXITCODE -ne 0) {
    throw "Failed to build core module (exit code $LASTEXITCODE)."
}

$lanIp = Get-NetIPAddress -AddressFamily IPv4 -ErrorAction SilentlyContinue |
    Where-Object { $_.IPAddress -ne "127.0.0.1" -and $_.IPAddress -notlike "169.254.*" -and $_.AddressState -eq "Preferred" } |
    Select-Object -First 1 -ExpandProperty IPAddress

Write-Host "Starting Sky Royal Website (preferred port $($env:SKY_ROYAL_WEB_PORT); will switch automatically if busy)..."
if ($lanIp) {
    Write-Host "LAN IP detected: $lanIp (replace localhost with this IP to open on the same Wi-Fi)."
}

& $mvnCmd -pl web compile exec:java
if ($LASTEXITCODE -ne 0) {
    throw "Sky Royal website exited with code $LASTEXITCODE."
}

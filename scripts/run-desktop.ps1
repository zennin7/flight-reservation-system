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

Write-Host "[BUILD] Preparing core module..."
& $mvnCmd -pl core -am -DskipTests install -q
if ($LASTEXITCODE -ne 0) {
    throw "Failed to build core module (exit code $LASTEXITCODE)."
}

Write-Host "Starting Sky Royal Desktop App (database ${env:SKY_ROYAL_DB_HOST}:${env:SKY_ROYAL_DB_PORT}/${env:SKY_ROYAL_DB_NAME})..."
& $mvnCmd -pl desktop javafx:run
if ($LASTEXITCODE -ne 0) {
    throw "Sky Royal desktop app exited with code $LASTEXITCODE."
}

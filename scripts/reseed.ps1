$ErrorActionPreference = "Stop"
. (Join-Path $PSScriptRoot "pg-common.ps1")
Import-SkyRoyalPath

$null = Resolve-SkyRoyalJava
$psqlExe = Resolve-SkyRoyalPsql

$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
Set-Location $repoRoot

$cfg = Read-SkyRoyalAppJson -RepoRoot $repoRoot
$hasEnvPass = (-not [string]::IsNullOrEmpty($env:SKY_ROYAL_DB_PASSWORD)) -or (-not [string]::IsNullOrEmpty($env:LAUNCH_DB_PASSWORD))
if (-not $cfg -and -not $hasEnvPass) {
    throw "Missing app.json and database password environment variable. Run .\scripts\run-setup.cmd first."
}

$dbHost = if (-not [string]::IsNullOrWhiteSpace($env:SKY_ROYAL_DB_HOST)) { $env:SKY_ROYAL_DB_HOST.Trim() } elseif ($cfg) { $cfg.dbHost } else { "127.0.0.1" }
$dbPort = Find-SkyRoyalPostgresPort -HostName $dbHost -RepoRoot $repoRoot
$dbName = if (-not [string]::IsNullOrWhiteSpace($env:SKY_ROYAL_DB_NAME)) { $env:SKY_ROYAL_DB_NAME.Trim() } elseif ($cfg) { $cfg.dbName } else { "flight_reservation" }
$dbUser = if (-not [string]::IsNullOrWhiteSpace($env:SKY_ROYAL_DB_USER)) { $env:SKY_ROYAL_DB_USER.Trim() } elseif ($cfg) { $cfg.dbUsername } else { "postgres" }
$dbPass = if (-not [string]::IsNullOrEmpty($env:SKY_ROYAL_DB_PASSWORD)) { $env:SKY_ROYAL_DB_PASSWORD } elseif (-not [string]::IsNullOrEmpty($env:LAUNCH_DB_PASSWORD)) { $env:LAUNCH_DB_PASSWORD } else { $cfg.dbPassword }

$env:PGPASSWORD = $dbPass
try {
    foreach ($script in @(
        "core/sql/migration_users_email_ci.sql",
        "core/sql/migration_p2_holds_payments_refund.sql",
        "core/sql/migration_flight_prefix_sr.sql",
        "core/sql/seed_auth_data.sql",
        "core/sql/seed_flights_relative.sql",
        "core/sql/seed_destinations_relative.sql"
    )) {
        Write-Host "[RESEED] Running $script on ${dbHost}:${dbPort}/${dbName}..."
        [void](Invoke-PsqlSafe -PsqlExe $psqlExe -Arguments @("-w", "-h", $dbHost, "-p", "$dbPort", "-U", $dbUser, "-d", $dbName, "-v", "ON_ERROR_STOP=1", "-f", (Join-Path $repoRoot $script)))
    }
    Write-Host "[OK] Rolling 14-day flight schedule refreshed."
}
finally {
    Remove-Item Env:PGPASSWORD -ErrorAction SilentlyContinue
}

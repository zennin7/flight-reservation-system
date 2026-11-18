$ErrorActionPreference = "Stop"
$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
Set-Location $repoRoot

$appJsonPath = Join-Path $repoRoot "core/src/main/resources/com/group2/flightres/app.json"
if (-not (Test-Path $appJsonPath)) {
    Write-Error "Missing app.json. Run .\scripts\setup.ps1 first."
}
$cfg = Get-Content $appJsonPath -Raw | ConvertFrom-Json

$psqlExe = (Get-Command psql -ErrorAction SilentlyContinue).Source
if (-not $psqlExe) {
    $psqlExe = Get-ChildItem "C:\Program Files\PostgreSQL\*\bin\psql.exe" -ErrorAction SilentlyContinue |
               Sort-Object FullName -Descending | Select-Object -First 1 -ExpandProperty FullName
}
if (-not $psqlExe -or -not (Test-Path $psqlExe)) {
    Write-Error "psql.exe not found on PATH or under C:\Program Files\PostgreSQL."
}

$env:PGPASSWORD = $cfg.dbPassword
try {
    foreach ($script in @("core/sql/seed_flights_relative.sql", "core/sql/seed_destinations_relative.sql")) {
        Write-Host "[RESEED] Running $script on $($cfg.dbHost):$($cfg.dbPort)/$($cfg.dbName)..."
        & $psqlExe -w -h $cfg.dbHost -p $cfg.dbPort -U $cfg.dbUsername -d $cfg.dbName -v ON_ERROR_STOP=1 -f (Join-Path $repoRoot $script) | Out-Null
    }
    Write-Host "[OK] Rolling 14-day flight schedule refreshed."
}
finally {
    Remove-Item Env:PGPASSWORD -ErrorAction SilentlyContinue
}

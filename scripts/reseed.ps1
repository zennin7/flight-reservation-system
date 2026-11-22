$ErrorActionPreference = "Stop"
$env:Path = [Environment]::GetEnvironmentVariable("Path", "Machine") + ";" + [Environment]::GetEnvironmentVariable("Path", "User")
if (-not $env:JAVA_HOME -or -not (Test-Path (Join-Path $env:JAVA_HOME "bin\javac.exe"))) {
    $resolvedJdk = $null
    $javaCmdInfo = Get-Command java -ErrorAction SilentlyContinue
    if ($javaCmdInfo -and $javaCmdInfo.Source) {
        $javaItem = Get-Item $javaCmdInfo.Source -ErrorAction SilentlyContinue
        $realJava = if ($javaItem -and $javaItem.Target) { ($javaItem.Target | Select-Object -First 1) } else { $javaCmdInfo.Source }
        $candHome = Split-Path (Split-Path $realJava -Parent) -Parent
        if ($candHome -and (Test-Path (Join-Path $candHome "bin\javac.exe"))) {
            $verLine = (& cmd /c "`"$(Join-Path $candHome 'bin\java.exe')`" -version 2>&1") -join " "
            if ($verLine -match '"(\d+)' -and [int]$Matches[1] -ge 17) {
                $resolvedJdk = $candHome
            }
        }
    }
    if (-not $resolvedJdk) {
        $jdkDirs = Get-ChildItem @(
            "C:\Program Files\Eclipse Adoptium\jdk-17*",
            "C:\Program Files\Java\jdk-17*",
            "C:\Program Files\Microsoft\jdk-17*"
        ) -Directory -ErrorAction SilentlyContinue | Sort-Object Name -Descending
        foreach ($d in $jdkDirs) {
            if (Test-Path (Join-Path $d.FullName "bin\javac.exe")) {
                $resolvedJdk = $d.FullName
                break
            }
        }
    }
    if (-not $resolvedJdk) {
        throw "No JDK 17 found. Install with: winget install EclipseAdoptium.Temurin.17.JDK, then reopen PowerShell."
    }
    $env:JAVA_HOME = $resolvedJdk
    $jdkBin = Join-Path $resolvedJdk "bin"
    if ($env:Path -notlike "*$jdkBin*") {
        $env:Path = "$jdkBin;$env:Path"
    }
    Write-Host "[FIX] JAVA_HOME set to $resolvedJdk for this run"
}
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
    foreach ($script in @("core/sql/migration_users_email_ci.sql", "core/sql/migration_p2_holds_payments_refund.sql", "core/sql/migration_flight_prefix_sr.sql", "core/sql/seed_auth_data.sql", "core/sql/seed_flights_relative.sql", "core/sql/seed_destinations_relative.sql")) {
        Write-Host "[RESEED] Running $script on $($cfg.dbHost):$($cfg.dbPort)/$($cfg.dbName)..."
        & $psqlExe -w -h $cfg.dbHost -p $cfg.dbPort -U $cfg.dbUsername -d $cfg.dbName -v ON_ERROR_STOP=1 -f (Join-Path $repoRoot $script) | Out-Null
    }
    Write-Host "[OK] Rolling 14-day flight schedule refreshed."
}
finally {
    Remove-Item Env:PGPASSWORD -ErrorAction SilentlyContinue
}

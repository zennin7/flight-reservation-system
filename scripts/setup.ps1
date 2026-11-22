param(
    [switch]$NonInteractive
)

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

Write-Host "=== Sky Royal Windows Setup ==="

# 1. Check Java 17+
$javaCmd = (Get-Command java -ErrorAction SilentlyContinue).Source
if (-not $javaCmd -and $env:JAVA_HOME) {
    $cand = Join-Path $env:JAVA_HOME "bin\java.exe"
    if (Test-Path $cand) { $javaCmd = $cand }
}
if (-not $javaCmd) {
    Write-Error "Java 17+ not found. Install with: winget install EclipseAdoptium.Temurin.17.JDK"
}
$verOut = (& cmd /c "`"$javaCmd`" -version 2>&1") -join " "
if ($verOut -match '"(\d+)') {
    $major = [int]$Matches[1]
    if ($major -lt 17) {
        Write-Error "Java $major found, but Java 17+ is required. Install with: winget install EclipseAdoptium.Temurin.17.JDK"
    }
    Write-Host "[OK] Java $major detected ($javaCmd)"
}

# 2. Check Maven
$mvnCmd = (Get-Command mvn -ErrorAction SilentlyContinue).Source
if (-not $mvnCmd) {
    Write-Error "Maven (mvn) not found on PATH. Install with: winget install Apache.Maven"
}
Write-Host "[OK] Maven detected ($mvnCmd)"

# 3. Check psql
$psqlExe = (Get-Command psql -ErrorAction SilentlyContinue).Source
if (-not $psqlExe) {
    $psqlExe = Get-ChildItem "C:\Program Files\PostgreSQL\*\bin\psql.exe" -ErrorAction SilentlyContinue |
               Sort-Object FullName -Descending | Select-Object -First 1 -ExpandProperty FullName
}
if (-not $psqlExe -or -not (Test-Path $psqlExe)) {
    Write-Error "psql.exe not found on PATH or under C:\Program Files\PostgreSQL. Install with: winget install PostgreSQL.PostgreSQL.18"
}
Write-Host "[OK] psql detected ($psqlExe)"

# 4. Collect DB connection settings
$detectedPorts = @(Get-NetTCPConnection -State Listen -ErrorAction SilentlyContinue |
    Where-Object { $_.LocalPort -in 5432, 5433 } |
    Select-Object -ExpandProperty LocalPort -Unique |
    Sort-Object)
$defaultPort = if ($detectedPorts.Count -gt 0) { [int]$detectedPorts[0] } else { 5432 }
if ($detectedPorts.Count -gt 0) {
    Write-Host "[INFO] Detected listening PostgreSQL port(s): $($detectedPorts -join ', ') (defaulting to $defaultPort)"
}

if ($NonInteractive) {
    $dbHost = if ($env:LAUNCH_DB_HOST) { $env:LAUNCH_DB_HOST.Trim() } else { "127.0.0.1" }
    if ($dbHost -match '://|/') { Write-Error "Invalid PostgreSQL host '$dbHost': enter a hostname or IP (e.g. 127.0.0.1), not a URL." }
    $dbPort = if ($env:LAUNCH_DB_PORT) { [int]$env:LAUNCH_DB_PORT } else { $defaultPort }
    $dbName = if ($env:LAUNCH_DB_NAME) { $env:LAUNCH_DB_NAME.Trim() } else { "flight_reservation" }
    $dbUser = if ($env:LAUNCH_DB_USER) { $env:LAUNCH_DB_USER.Trim() } else { "postgres" }
    $dbPass = $env:LAUNCH_DB_PASSWORD
    if (-not $dbPass) { Write-Error "LAUNCH_DB_PASSWORD must be set in -NonInteractive mode." }
} else {
    do {
        $hIn = Read-Host "PostgreSQL host [127.0.0.1]"
        $dbHost = if ([string]::IsNullOrWhiteSpace($hIn)) { "127.0.0.1" } else { $hIn.Trim() }
        if ($dbHost -match '://|/') {
            Write-Warning "Host must be a hostname or IP (for example 127.0.0.1), not a URL containing '://' or '/'."
        }
    } while ($dbHost -match '://|/')
    $pIn = Read-Host "PostgreSQL port [$defaultPort]"
    $dbPort = if ([string]::IsNullOrWhiteSpace($pIn)) { $defaultPort } else { [int]$pIn.Trim() }
    $nIn = Read-Host "Database name [flight_reservation]"
    $dbName = if ([string]::IsNullOrWhiteSpace($nIn)) { "flight_reservation" } else { $nIn.Trim() }
    $uIn = Read-Host "PostgreSQL user [postgres]"
    $dbUser = if ([string]::IsNullOrWhiteSpace($uIn)) { "postgres" } else { $uIn.Trim() }
    $secPass = Read-Host "PostgreSQL password" -AsSecureString
    $dbPass = [System.Net.NetworkCredential]::new("", $secPass).Password
}

$env:PGPASSWORD = $dbPass
try {
    # Quick connectivity & auth probe
    $probeErr = (& $psqlExe -w -h $dbHost -p $dbPort -U $dbUser -d postgres -tAc "SELECT 1;" 2>&1) | Out-String
    if ($LASTEXITCODE -ne 0) {
        if ($probeErr -match 'password authentication failed|no password supplied') {
            throw "PostgreSQL connection failed: wrong password for user '$dbUser', or wrong port ($dbPort)."
        }
        throw "PostgreSQL connection failed on ${dbHost}:${dbPort} for user '$dbUser' (wrong port, host, or PostgreSQL service stopped). Details: $($probeErr.Trim())"
    }

    function Get-PgScalar([string]$targetDb, [string]$sqlText) {
        $raw = & $psqlExe -w -h $dbHost -p $dbPort -U $dbUser -d $targetDb -tAc $sqlText 2>$null
        if ($null -eq $raw) { return "" }
        return ($raw | Out-String).Trim()
    }

    # 5. Create database if missing
    $dbExists = Get-PgScalar "postgres" "SELECT 1 FROM pg_database WHERE datname = '$dbName';"
    if ($dbExists -ne "1") {
        Write-Host "[CREATE] Creating database '$dbName' on ${dbHost}:${dbPort}..."
        & $psqlExe -w -h $dbHost -p $dbPort -U $dbUser -d postgres -v ON_ERROR_STOP=1 -c "CREATE DATABASE `"$dbName`";" | Out-Null
    } else {
        Write-Host "[SKIP] Database '$dbName' already exists."
    }

    function Invoke-SqlFile([string]$relPath) {
        $full = Join-Path $repoRoot $relPath
        Write-Host "[SQL] Running $relPath..."
        & $psqlExe -w -h $dbHost -p $dbPort -U $dbUser -d $dbName -v ON_ERROR_STOP=1 -f $full | Out-Null
    }

    # 6. Schema (only if airports table does not exist)
    $hasSchema = Get-PgScalar $dbName "SELECT CASE WHEN to_regclass('public.airports') IS NULL THEN 0 ELSE 1 END;"
    if ($hasSchema -eq "0") {
        Invoke-SqlFile "core/sql/schema.sql"
    } else {
        Write-Host "[SKIP] core/sql/schema.sql (schema already initialized)."
    }

    # 7. Migrations
    $hasTz = Get-PgScalar $dbName "SELECT COUNT(*) FROM information_schema.columns WHERE table_schema='public' AND table_name='airports' AND column_name='timezone';"
    if ($hasTz -eq "0") {
        Invoke-SqlFile "core/sql/migration_airports_timezone.sql"
    } else {
        Write-Host "[SKIP] core/sql/migration_airports_timezone.sql (airports.timezone already present)."
    }
    Invoke-SqlFile "core/sql/migration_payments_card_meta.sql"
    Invoke-SqlFile "core/sql/migration_users_email_ci.sql"
    Invoke-SqlFile "core/sql/migration_p2_holds_payments_refund.sql"
    Invoke-SqlFile "core/sql/migration_flight_prefix_sr.sql"

    # 8. Auth seed (idempotent upsert)
    Invoke-SqlFile "core/sql/seed_auth_data.sql"

    # 9. Flight & destination seeds (idempotent, in dependency order)
    $hasAircraft = Get-PgScalar $dbName "SELECT COUNT(*) FROM aircraft_types;"
    if ($hasAircraft -eq "0") {
        Invoke-SqlFile "core/sql/seed_flights_data.sql"
    } else {
        Write-Host "[SKIP] core/sql/seed_flights_data.sql (aircraft_types already populated)."
    }
    Invoke-SqlFile "core/sql/seed_flights_relative.sql"
    Invoke-SqlFile "core/sql/seed_destinations_relative.sql"
}
finally {
    Remove-Item Env:PGPASSWORD -ErrorAction SilentlyContinue
}

# 10. Write ignored app.json from app.json.example
$examplePath = Join-Path $repoRoot "core/src/main/resources/com/group2/flightres/app.json.example"
$appJsonPath = Join-Path $repoRoot "core/src/main/resources/com/group2/flightres/app.json"
$cfgObj = Get-Content $examplePath -Raw | ConvertFrom-Json
$cfgObj.dbHost = $dbHost
$cfgObj.dbPort = $dbPort
$cfgObj.dbName = $dbName
$cfgObj.dbUsername = $dbUser
$cfgObj.dbPassword = $dbPass
$jsonOut = $cfgObj | ConvertTo-Json -Depth 4
[System.IO.File]::WriteAllText($appJsonPath, $jsonOut, [System.Text.UTF8Encoding]::new($false))
Write-Host "[OK] Wrote local config: core/src/main/resources/com/group2/flightres/app.json (gitignored)."
Write-Host "[BUILD] Installing Maven artifacts (mvn -DskipTests install)..."
mvn -DskipTests install -q
if ($LASTEXITCODE -ne 0) { throw "Maven install failed." }
Write-Host "=== Setup complete. Next: .\scripts\run-web.cmd or .\scripts\run-desktop.cmd ==="

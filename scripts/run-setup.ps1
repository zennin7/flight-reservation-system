param(
    [switch]$NonInteractive
)

$ErrorActionPreference = "Stop"
. (Join-Path $PSScriptRoot "pg-common.ps1")
Import-SkyRoyalPath

$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
Set-Location $repoRoot

Write-Host "=== Sky Royal Setup ==="

Write-Host "[1/6] Checking Java 17+..."
$javaExe = Resolve-SkyRoyalJava
$javaMajor = Get-JavaMajorVersion -JavaExePath $javaExe
Write-Host "[OK] Java $javaMajor detected ($javaExe)"

Write-Host "[2/6] Checking Maven..."
$mvnCmd = Resolve-SkyRoyalMaven -RepoRoot $repoRoot
Write-Host "[OK] Maven command: $mvnCmd"

Write-Host "[3/6] Checking PostgreSQL client & server..."
$psqlExe = Resolve-SkyRoyalPsql
Write-Host "[OK] psql detected ($psqlExe)"

if ($NonInteractive) {
    $dbHost = if (-not [string]::IsNullOrWhiteSpace($env:SKY_ROYAL_DB_HOST)) {
        $env:SKY_ROYAL_DB_HOST.Trim()
    } elseif (-not [string]::IsNullOrWhiteSpace($env:LAUNCH_DB_HOST)) {
        $env:LAUNCH_DB_HOST.Trim()
    } else {
        "127.0.0.1"
    }
    if ($dbHost -match '://|/') {
        throw "Invalid PostgreSQL host '$dbHost': enter a hostname or IP (for example 127.0.0.1), not a URL."
    }
    $dbPort = Find-SkyRoyalPostgresPort -HostName $dbHost -RepoRoot $repoRoot
    $dbName = if (-not [string]::IsNullOrWhiteSpace($env:SKY_ROYAL_DB_NAME)) {
        $env:SKY_ROYAL_DB_NAME.Trim()
    } elseif (-not [string]::IsNullOrWhiteSpace($env:LAUNCH_DB_NAME)) {
        $env:LAUNCH_DB_NAME.Trim()
    } else {
        "flight_reservation"
    }
    $dbUser = if (-not [string]::IsNullOrWhiteSpace($env:SKY_ROYAL_DB_USER)) {
        $env:SKY_ROYAL_DB_USER.Trim()
    } elseif (-not [string]::IsNullOrWhiteSpace($env:LAUNCH_DB_USER)) {
        $env:LAUNCH_DB_USER.Trim()
    } else {
        "postgres"
    }
    $dbPass = if (-not [string]::IsNullOrEmpty($env:SKY_ROYAL_DB_PASSWORD)) {
        $env:SKY_ROYAL_DB_PASSWORD
    } else {
        $env:LAUNCH_DB_PASSWORD
    }
    if ([string]::IsNullOrEmpty($dbPass)) {
        throw "SKY_ROYAL_DB_PASSWORD (or LAUNCH_DB_PASSWORD) must be set when using -NonInteractive."
    }
} else {
    do {
        $hIn = Read-Host "PostgreSQL host [127.0.0.1]"
        $dbHost = if ([string]::IsNullOrWhiteSpace($hIn)) { "127.0.0.1" } else { $hIn.Trim() }
        if ($dbHost -match '://|/') {
            Write-Warning "Host must be a hostname or IP (for example 127.0.0.1), not a URL containing '://' or '/'."
        }
    } while ($dbHost -match '://|/')

    $defaultPort = Find-SkyRoyalPostgresPort -HostName $dbHost -RepoRoot $repoRoot
    $pIn = Read-Host "PostgreSQL port [$defaultPort]"
    $dbPort = if ([string]::IsNullOrWhiteSpace($pIn)) { $defaultPort } else { [int]$pIn.Trim() }
    if ($dbPort -ne $defaultPort -and -not (Test-TcpPortOpen -TargetHost $dbHost -Port $dbPort)) {
        throw "PostgreSQL did not answer on ${dbHost}:${dbPort}. Check the port or start PostgreSQL in Windows Services."
    }

    $nIn = Read-Host "Database name [flight_reservation]"
    $dbName = if ([string]::IsNullOrWhiteSpace($nIn)) { "flight_reservation" } else { $nIn.Trim() }

    $uIn = Read-Host "PostgreSQL user [postgres]"
    $dbUser = if ([string]::IsNullOrWhiteSpace($uIn)) { "postgres" } else { $uIn.Trim() }

    $secPass = Read-Host "PostgreSQL password" -AsSecureString
    $dbPass = [System.Net.NetworkCredential]::new("", $secPass).Password
    if ([string]::IsNullOrEmpty($dbPass)) {
        throw "PostgreSQL password cannot be empty."
    }
}

if ($dbName -notmatch '^[A-Za-z_][A-Za-z0-9_]*$') {
    throw "Invalid database name '$dbName'. Use only letters, numbers, and underscores (starting with a letter or underscore)."
}

Write-Host "[4/6] Connecting to ${dbHost}:${dbPort} and preparing database '$dbName'..."
$createdNewDb = $false
$env:PGPASSWORD = $dbPass
try {
    [void](Invoke-PsqlSafe -PsqlExe $psqlExe -Arguments @("-w", "-h", $dbHost, "-p", "$dbPort", "-U", $dbUser, "-d", "postgres", "-tAc", "SELECT 1;"))

    function Get-PgScalar([string]$targetDb, [string]$sqlText) {
        $raw = Invoke-PsqlSafe -PsqlExe $psqlExe -Arguments @("-w", "-h", $dbHost, "-p", "$dbPort", "-U", $dbUser, "-d", $targetDb, "-tAc", $sqlText)
        return $raw.Trim()
    }

    function Invoke-SqlFile([string]$relPath) {
        $full = Join-Path $repoRoot $relPath
        Write-Host "[SQL] Running $relPath..."
        [void](Invoke-PsqlSafe -PsqlExe $psqlExe -Arguments @("-w", "-h", $dbHost, "-p", "$dbPort", "-U", $dbUser, "-d", $dbName, "-v", "ON_ERROR_STOP=1", "-f", $full))
    }

    $dbExists = Get-PgScalar "postgres" "SELECT 1 FROM pg_database WHERE datname = '$dbName';"
    if ($dbExists -ne "1") {
        Write-Host "[CREATE] Creating database '$dbName'..."
        [void](Invoke-PsqlSafe -PsqlExe $psqlExe -Arguments @("-w", "-h", $dbHost, "-p", "$dbPort", "-U", $dbUser, "-d", "postgres", "-v", "ON_ERROR_STOP=1", "-c", "CREATE DATABASE `"$dbName`";"))
        $createdNewDb = $true
    } else {
        Write-Host "[SKIP] Database '$dbName' already exists."
    }

    try {
        Write-Host "[5/6] Applying schema, migrations, and seed data..."
        $hasSchema = Get-PgScalar $dbName "SELECT CASE WHEN to_regclass('public.airports') IS NULL THEN 0 ELSE 1 END;"
        if ($hasSchema -eq "0") {
            Invoke-SqlFile "core/sql/schema.sql"
        } else {
            Write-Host "[SKIP] core/sql/schema.sql (schema already initialized)."
        }

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

        Invoke-SqlFile "core/sql/seed_auth_data.sql"

        $hasAircraft = Get-PgScalar $dbName "SELECT COUNT(*) FROM aircraft_types;"
        if ($hasAircraft -eq "0") {
            Invoke-SqlFile "core/sql/seed_flights_data.sql"
        } else {
            Write-Host "[SKIP] core/sql/seed_flights_data.sql (aircraft_types already populated)."
        }
        Invoke-SqlFile "core/sql/seed_flights_relative.sql"
        Invoke-SqlFile "core/sql/seed_destinations_relative.sql"
    } catch {
        if ($createdNewDb) {
            Write-Warning "Setup failed partway; removing newly created half-initialized database '$dbName'..."
            try {
                [void](Invoke-PsqlSafe -PsqlExe $psqlExe -Arguments @("-w", "-h", $dbHost, "-p", "$dbPort", "-U", $dbUser, "-d", "postgres", "-c", "SELECT pg_terminate_backend(pid) FROM pg_stat_activity WHERE datname = '$dbName' AND pid <> pg_backend_pid();"))
                [void](Invoke-PsqlSafe -PsqlExe $psqlExe -Arguments @("-w", "-h", $dbHost, "-p", "$dbPort", "-U", $dbUser, "-d", "postgres", "-c", "DROP DATABASE IF EXISTS `"$dbName`";"))
            } catch {
                Write-Warning "Cleanup could not drop '$dbName': $($_.Exception.Message)"
            }
        }
        throw
    }
}
finally {
    Remove-Item Env:PGPASSWORD -ErrorAction SilentlyContinue
}

Write-Host "[6/6] Writing local app.json and compiling Maven artifacts..."
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

& $mvnCmd -DskipTests install -q
if ($LASTEXITCODE -ne 0) {
    throw "Maven build failed (exit code $LASTEXITCODE)."
}

Write-Host ""
Write-Host "Setup OK"
Write-Host "Database: ${dbHost}:${dbPort}/${dbName} (user: ${dbUser})"
Write-Host "Demo logins (website /login and desktop app):"
Write-Host "  Admin:    testadmin  / Passw0rd!  (email: testadmin@example.com)"
Write-Host "  Customer: customer   / Passw0rd!  (email: customer@example.com)"
Write-Host "Simulated payment test cards (no real money moves):"
Write-Host "  Approve:  4242 4242 4242 4242 (any future expiry + 3-digit CVV)"
Write-Host "  Decline:  4000 0000 0000 0002"
Write-Host "Note: Welcome emails are written locally to the outbox\ folder unless SMTP is configured."
Write-Host "Next steps:"
Write-Host "  .\scripts\run-web.cmd      (start the website)"
Write-Host "  .\scripts\run-desktop.cmd  (start the JavaFX desktop app)"

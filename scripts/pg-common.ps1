function Import-SkyRoyalPath {
    $machinePath = [Environment]::GetEnvironmentVariable("Path", "Machine")
    $userPath = [Environment]::GetEnvironmentVariable("Path", "User")
    $parts = @()
    foreach ($chunk in @($env:Path, $machinePath, $userPath)) {
        if (-not [string]::IsNullOrWhiteSpace($chunk)) {
            foreach ($p in ($chunk -split ';')) {
                $trimmed = $p.Trim()
                if (-not [string]::IsNullOrWhiteSpace($trimmed) -and ($parts -notcontains $trimmed)) {
                    $parts += $trimmed
                }
            }
        }
    }
    $env:Path = ($parts -join ';')
}

function Get-JavaMajorVersion {
    param([string]$JavaExePath)
    if ([string]::IsNullOrWhiteSpace($JavaExePath) -or -not (Test-Path $JavaExePath)) {
        return $null
    }
    $prev = $ErrorActionPreference
    $ErrorActionPreference = 'Continue'
    try {
        $verOut = (& cmd /c "`"$JavaExePath`" -version 2>&1") -join " "
    } finally {
        $ErrorActionPreference = $prev
    }
    if ($verOut -match '"(\d+)(?:\.\d+)?') {
        $first = [int]$Matches[1]
        if ($first -eq 1 -and $verOut -match '"1\.(\d+)') {
            return [int]$Matches[1]
        }
        return $first
    }
    return $null
}

function Resolve-SkyRoyalJava {
    # Optional test hook for verifying unsupported-Java error handling safely
    if (-not [string]::IsNullOrWhiteSpace($env:SKY_ROYAL_TEST_JAVA_EXE)) {
        $hookPath = $env:SKY_ROYAL_TEST_JAVA_EXE.Trim()
        if (-not (Test-Path $hookPath)) {
            throw "Java 17 or newer was not found (test Java executable '$hookPath' does not exist). Install Temurin JDK 17+ from https://adoptium.net (or run: winget install EclipseAdoptium.Temurin.17.JDK), reopen your terminal, and try again."
        }
        $major = Get-JavaMajorVersion -JavaExePath $hookPath
        if ($null -eq $major -or $major -lt 17) {
            $foundLabel = if ($null -ne $major) { "Java $major" } else { "an unsupported Java version" }
            throw "$foundLabel was found at '$hookPath', but Java 17 or newer is required. Install Temurin JDK 17+ from https://adoptium.net (or run: winget install EclipseAdoptium.Temurin.17.JDK), reopen your terminal, and try again."
        }
        return $hookPath
    }

    $tooOldFound = $null
    if (-not [string]::IsNullOrWhiteSpace($env:JAVA_HOME)) {
        $jhJava = Join-Path $env:JAVA_HOME "bin\java.exe"
        $jhJavac = Join-Path $env:JAVA_HOME "bin\javac.exe"
        $major = Get-JavaMajorVersion -JavaExePath $jhJava
        if ($null -ne $major) {
            if ($major -ge 17 -and (Test-Path $jhJavac)) {
                return $jhJava
            }
            if ($major -lt 17) {
                $tooOldFound = "Java $major"
            }
        }
    }

    $javaCmdInfo = Get-Command java -ErrorAction SilentlyContinue
    if ($javaCmdInfo -and $javaCmdInfo.Source) {
        $javaItem = Get-Item $javaCmdInfo.Source -ErrorAction SilentlyContinue
        $realJava = if ($javaItem -and $javaItem.Target) { ($javaItem.Target | Select-Object -First 1) } else { $javaCmdInfo.Source }
        $candHome = Split-Path (Split-Path $realJava -Parent) -Parent
        $candJavac = if ($candHome) { Join-Path $candHome "bin\javac.exe" } else { "" }
        $major = Get-JavaMajorVersion -JavaExePath $realJava
        if ($null -ne $major) {
            if ($major -ge 17 -and (Test-Path $candJavac)) {
                $env:JAVA_HOME = $candHome
                $jdkBin = Join-Path $candHome "bin"
                if ($env:Path -notlike "*$jdkBin*") { $env:Path = "$jdkBin;$env:Path" }
                return $realJava
            }
            if ($major -lt 17 -and -not $tooOldFound) {
                $tooOldFound = "Java $major"
            }
        }
    }

    $jdkDirs = Get-ChildItem @(
        "C:\Program Files\Eclipse Adoptium\jdk-*",
        "C:\Program Files\Java\jdk-*",
        "C:\Program Files\Microsoft\jdk-*",
        "C:\Program Files\Zulu\zulu-*"
    ) -Directory -ErrorAction SilentlyContinue | Sort-Object Name -Descending
    foreach ($d in $jdkDirs) {
        $candJava = Join-Path $d.FullName "bin\java.exe"
        $candJavac = Join-Path $d.FullName "bin\javac.exe"
        if ((Test-Path $candJava) -and (Test-Path $candJavac)) {
            $major = Get-JavaMajorVersion -JavaExePath $candJava
            if ($null -ne $major -and $major -ge 17) {
                $env:JAVA_HOME = $d.FullName
                $jdkBin = Join-Path $d.FullName "bin"
                if ($env:Path -notlike "*$jdkBin*") { $env:Path = "$jdkBin;$env:Path" }
                Write-Host "[FIX] JAVA_HOME set to $($d.FullName) for this run"
                return $candJava
            }
            if ($null -ne $major -and $major -lt 17 -and -not $tooOldFound) {
                $tooOldFound = "Java $major"
            }
        }
    }

    if ($tooOldFound) {
        throw "$tooOldFound was found, but Java 17 or newer is required. Download Temurin JDK 17+ from https://adoptium.net (or run: winget install EclipseAdoptium.Temurin.17.JDK), reopen your terminal, and run the script again."
    }
    throw "Java 17 or newer (JDK) was not found on this PC. Download Temurin JDK 17+ from https://adoptium.net (or run: winget install EclipseAdoptium.Temurin.17.JDK), reopen your terminal, and run the script again."
}

function Resolve-SkyRoyalMaven {
    param([string]$RepoRoot)
    if (-not [string]::IsNullOrWhiteSpace($RepoRoot)) {
        $mvnw = Join-Path $RepoRoot "mvnw.cmd"
        if (Test-Path $mvnw) {
            return $mvnw
        }
    }
    $mvnCmd = (Get-Command mvn -ErrorAction SilentlyContinue).Source
    if ($mvnCmd) {
        return $mvnCmd
    }
    throw "Neither mvnw.cmd nor Maven (mvn) was found. Re-clone the full repository (including mvnw.cmd and .mvn/), or install Maven with: winget install Apache.Maven"
}

function Resolve-SkyRoyalPsql {
    $psqlExe = (Get-Command psql -ErrorAction SilentlyContinue).Source
    if (-not $psqlExe) {
        $psqlExe = Get-ChildItem "C:\Program Files\PostgreSQL\*\bin\psql.exe" -ErrorAction SilentlyContinue |
            Sort-Object {
                if ($_.FullName -match '\\PostgreSQL\\(\d+)\\') { [int]$Matches[1] } else { 0 }
            } -Descending |
            Select-Object -First 1 -ExpandProperty FullName
    }
    if (-not $psqlExe -or -not (Test-Path $psqlExe)) {
        throw "PostgreSQL (psql.exe) was not found on this PC. Install PostgreSQL from https://www.postgresql.org/download/windows/ (or run: winget install PostgreSQL.PostgreSQL.18), remember the 'postgres' password you set during installation, reopen your terminal, and run scripts\run-setup.cmd again."
    }
    return $psqlExe
}

function Read-SkyRoyalAppJson {
    param([string]$RepoRoot)
    if ([string]::IsNullOrWhiteSpace($RepoRoot)) { return $null }
    $path = Join-Path $RepoRoot "core/src/main/resources/com/group2/flightres/app.json"
    if (-not (Test-Path $path)) { return $null }
    try {
        return (Get-Content $path -Raw | ConvertFrom-Json)
    } catch {
        return $null
    }
}

function Test-TcpPortOpen {
    param(
        [string]$TargetHost = "127.0.0.1",
        [int]$Port,
        [int]$TimeoutMs = 1500
    )
    if ($Port -lt 1 -or $Port -gt 65535) { return $false }
    $client = New-Object System.Net.Sockets.TcpClient
    try {
        $async = $client.BeginConnect($TargetHost, $Port, $null, $null)
        if (-not $async.AsyncWaitHandle.WaitOne($TimeoutMs, $false)) {
            return $false
        }
        $client.EndConnect($async)
        return $true
    } catch {
        return $false
    } finally {
        $client.Close()
    }
}

function Find-SkyRoyalPostgresPort {
    param(
        [string]$HostName = "127.0.0.1",
        [string]$RepoRoot = ""
    )

    $explicitRaw = if (-not [string]::IsNullOrWhiteSpace($env:SKY_ROYAL_DB_PORT)) {
        $env:SKY_ROYAL_DB_PORT.Trim()
    } elseif (-not [string]::IsNullOrWhiteSpace($env:LAUNCH_DB_PORT)) {
        $env:LAUNCH_DB_PORT.Trim()
    } else {
        $null
    }

    if ($explicitRaw) {
        $parsedPort = 0
        if (-not [int]::TryParse($explicitRaw, [ref]$parsedPort) -or $parsedPort -lt 1 -or $parsedPort -gt 65535) {
            throw "The configured PostgreSQL port '$explicitRaw' is not valid. Use a number between 1 and 65535 (standard PostgreSQL uses 5432; some setups use 5433)."
        }
        if (Test-TcpPortOpen -TargetHost $HostName -Port $parsedPort) {
            Write-Host "[OK] PostgreSQL answered on configured port ${HostName}:${parsedPort}"
            return $parsedPort
        }
        throw "PostgreSQL did not answer on configured address ${HostName}:${parsedPort}. Make sure the PostgreSQL service is running in Windows Services (for example postgresql-x64-18 or postgresql-x64-17), or clear SKY_ROYAL_DB_PORT to auto-detect ports 5432 and 5433. If PostgreSQL is not installed yet, download it from https://www.postgresql.org/download/windows/ (or run: winget install PostgreSQL.PostgreSQL.18)."
    }

    $candidates = New-Object System.Collections.Generic.List[int]
    $cfg = Read-SkyRoyalAppJson -RepoRoot $RepoRoot
    if ($cfg -and $cfg.dbPort) {
        $cfgPort = [int]$cfg.dbPort
        if ($cfgPort -ge 1 -and $cfgPort -le 65535 -and -not $candidates.Contains($cfgPort)) {
            $candidates.Add($cfgPort)
        }
    }
    foreach ($p in @(5432, 5433)) {
        if (-not $candidates.Contains($p)) {
            $candidates.Add($p)
        }
    }

    Write-Host "[INFO] Probing PostgreSQL on ${HostName} (ports: $($candidates -join ', '))..."
    foreach ($p in $candidates) {
        if (Test-TcpPortOpen -TargetHost $HostName -Port $p) {
            Write-Host "[OK] PostgreSQL answered on ${HostName}:${p}"
            return $p
        }
    }

    throw "PostgreSQL did not answer on ${HostName} ports $($candidates -join ' or '). Please start PostgreSQL (open Windows Services -> start 'postgresql-x64-*'), or install PostgreSQL from https://www.postgresql.org/download/windows/ (winget install PostgreSQL.PostgreSQL.18) and remember the 'postgres' password."
}

function Invoke-PsqlSafe {
    param(
        [string]$PsqlExe,
        [string[]]$Arguments
    )
    $prev = $ErrorActionPreference
    $ErrorActionPreference = 'Continue'
    try {
        $output = & $PsqlExe @Arguments 2>&1
        $code = $LASTEXITCODE
    } finally {
        $ErrorActionPreference = $prev
    }
    $text = ($output | ForEach-Object { $_.ToString() }) -join "`n"
    if ($code -ne 0) {
        if ($text -match 'password authentication failed|no password supplied') {
            throw "PostgreSQL rejected the password (wrong password for user, or wrong port). Run scripts\run-setup.cmd again and enter the exact password you chose when installing PostgreSQL."
        }
        throw "PostgreSQL command failed: $($text.Trim())"
    }
    return $text
}

function Export-SkyRoyalDbEnv {
    param(
        [string]$RepoRoot,
        [int]$DetectedPort = 0
    )
    $cfg = Read-SkyRoyalAppJson -RepoRoot $RepoRoot
    if ([string]::IsNullOrWhiteSpace($env:SKY_ROYAL_DB_HOST)) {
        if (-not [string]::IsNullOrWhiteSpace($env:LAUNCH_DB_HOST)) {
            $env:SKY_ROYAL_DB_HOST = $env:LAUNCH_DB_HOST.Trim()
        } elseif ($cfg -and -not [string]::IsNullOrWhiteSpace($cfg.dbHost)) {
            $env:SKY_ROYAL_DB_HOST = $cfg.dbHost.Trim()
        } else {
            $env:SKY_ROYAL_DB_HOST = "127.0.0.1"
        }
    }
    if ([string]::IsNullOrWhiteSpace($env:SKY_ROYAL_DB_PORT)) {
        if (-not [string]::IsNullOrWhiteSpace($env:LAUNCH_DB_PORT)) {
            $env:SKY_ROYAL_DB_PORT = $env:LAUNCH_DB_PORT.Trim()
        } elseif ($DetectedPort -gt 0) {
            $env:SKY_ROYAL_DB_PORT = "$DetectedPort"
        } elseif ($cfg -and $cfg.dbPort) {
            $env:SKY_ROYAL_DB_PORT = "$($cfg.dbPort)"
        } else {
            $env:SKY_ROYAL_DB_PORT = "5432"
        }
    }
    if ([string]::IsNullOrWhiteSpace($env:SKY_ROYAL_DB_NAME)) {
        if (-not [string]::IsNullOrWhiteSpace($env:LAUNCH_DB_NAME)) {
            $env:SKY_ROYAL_DB_NAME = $env:LAUNCH_DB_NAME.Trim()
        } elseif ($cfg -and -not [string]::IsNullOrWhiteSpace($cfg.dbName)) {
            $env:SKY_ROYAL_DB_NAME = $cfg.dbName.Trim()
        } else {
            $env:SKY_ROYAL_DB_NAME = "flight_reservation"
        }
    }
    if ([string]::IsNullOrWhiteSpace($env:SKY_ROYAL_DB_USER)) {
        if (-not [string]::IsNullOrWhiteSpace($env:LAUNCH_DB_USER)) {
            $env:SKY_ROYAL_DB_USER = $env:LAUNCH_DB_USER.Trim()
        } elseif ($cfg -and -not [string]::IsNullOrWhiteSpace($cfg.dbUsername)) {
            $env:SKY_ROYAL_DB_USER = $cfg.dbUsername.Trim()
        } else {
            $env:SKY_ROYAL_DB_USER = "postgres"
        }
    }
    if ([string]::IsNullOrEmpty($env:SKY_ROYAL_DB_PASSWORD)) {
        if (-not [string]::IsNullOrEmpty($env:LAUNCH_DB_PASSWORD)) {
            $env:SKY_ROYAL_DB_PASSWORD = $env:LAUNCH_DB_PASSWORD
        } elseif ($cfg -and -not [string]::IsNullOrEmpty($cfg.dbPassword)) {
            $env:SKY_ROYAL_DB_PASSWORD = $cfg.dbPassword
        }
    }
}

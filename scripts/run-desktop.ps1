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
    Write-Error "Missing core/src/main/resources/com/group2/flightres/app.json. Run .\scripts\setup.ps1 first."
}

Write-Host "Installing core and starting Sky Royal Desktop App (com.group2.flightres.App via javafx:run)..."
mvn -pl core -am -DskipTests install -q
if ($LASTEXITCODE -ne 0) { throw "Failed to build core module." }
mvn -pl desktop javafx:run

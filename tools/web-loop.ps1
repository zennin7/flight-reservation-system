param(
    [int]$Port = 7070,
    [string]$OutDir = "docs/screenshots"
)

$ErrorActionPreference = "Stop"
$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
Set-Location $repoRoot

New-Item -ItemType Directory -Force -Path $OutDir | Out-Null
$desktopPng = Join-Path (Resolve-Path $OutDir).Path "web-home-1440.png"
$mobilePng  = Join-Path (Resolve-Path $OutDir).Path "web-home-390.png"

$edgeCandidates = @(
    "C:\Program Files (x86)\Microsoft\Edge\Application\msedge.exe",
    "C:\Program Files\Microsoft\Edge\Application\msedge.exe"
)
$edge = $edgeCandidates | Where-Object { Test-Path $_ } | Select-Object -First 1
if (-not $edge) {
    throw "Microsoft Edge (msedge.exe) not found."
}

Write-Host "Building core + web..."
cmd /c "mvn -pl web -am -DskipTests compile dependency:build-classpath -Dmdep.outputFile=target/cp.txt > web-loop-build.log 2>&1"
if ($LASTEXITCODE -ne 0) {
    Get-Content web-loop-build.log -Tail 40
    throw "Maven build failed with exit code $LASTEXITCODE"
}

$cp = (Get-Content "web/target/cp.txt" -Raw).Trim()
$fullCp = "web/target/classes;core/target/classes;$cp"

$javaExe = (Get-Command java -ErrorAction SilentlyContinue).Source
if (-not $javaExe -and $env:JAVA_HOME) {
    $javaExe = Join-Path $env:JAVA_HOME "bin\java.exe"
}
if (-not $javaExe -or -not (Test-Path $javaExe)) {
    $javaExe = (Get-ChildItem "C:\Program Files\Java\*\bin\java.exe","C:\Program Files\Eclipse Adoptium\*\bin\java.exe" -ErrorAction SilentlyContinue | Select-Object -First 1).FullName
}
if (-not $javaExe) {
    throw "java.exe not found."
}

Write-Host "Starting WebApplication on port $Port using $javaExe..."
$env:PORT = "$Port"
$proc = Start-Process -FilePath $javaExe -ArgumentList @("-cp", $fullCp, "com.group2.flightres.web.WebApplication") -PassThru -NoNewWindow -RedirectStandardOutput "web-loop-server.log" -RedirectStandardError "web-loop-server-err.log"

try {
    $url = "http://localhost:$Port/"
    $ready = $false
    for ($i = 0; $i -lt 40; $i++) {
        Start-Sleep -Milliseconds 500
        try {
            $resp = Invoke-WebRequest -Uri $url -UseBasicParsing -TimeoutSec 2
            if ($resp.StatusCode -eq 200) {
                $ready = $true
                break
            }
        } catch {
            # keep polling
        }
    }
    if (-not $ready) {
        Get-Content "web-loop-server.log" -Tail 30 -ErrorAction SilentlyContinue
        Get-Content "web-loop-server-err.log" -Tail 30 -ErrorAction SilentlyContinue
        throw "WebApplication did not become ready on $url"
    }

    Write-Host "Capturing 1440x3800 screenshot..."
    & $edge --headless=new --disable-gpu --screenshot="$desktopPng" --window-size=1440,3800 "$url" | Out-Null
    Write-Host "Capturing 390x3800 screenshot..."
    & $edge --headless=new --disable-gpu --screenshot="$mobilePng" --window-size=390,3800 "$url" | Out-Null
}
finally {
    if ($proc -and -not $proc.HasExited) {
        Stop-Process -Id $proc.Id -Force
        Write-Host "Stopped WebApplication (PID $($proc.Id))."
    }
}

foreach ($png in @($desktopPng, $mobilePng)) {
    $item = Get-Item $png
    $hash = (Get-FileHash -Algorithm SHA256 $png).Hash
    Write-Output ("{0} | LastWriteTime={1} | SHA256={2}" -f $item.Name, $item.LastWriteTime, $hash)
}

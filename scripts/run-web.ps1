$ErrorActionPreference = "Stop"
$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
Set-Location $repoRoot

$appJsonPath = Join-Path $repoRoot "core/src/main/resources/com/group2/flightres/app.json"
if (-not (Test-Path $appJsonPath)) {
    Write-Error "Missing core/src/main/resources/com/group2/flightres/app.json. Run .\scripts\setup.ps1 first."
}

if (-not $env:PORT) {
    $env:PORT = "8080"
}

$lanIp = Get-NetIPAddress -AddressFamily IPv4 -ErrorAction SilentlyContinue |
    Where-Object { $_.IPAddress -ne "127.0.0.1" -and $_.IPAddress -notlike "169.254.*" -and $_.AddressState -eq "Preferred" } |
    Select-Object -First 1 -ExpandProperty IPAddress

Write-Host "Starting Sky Royal Website (com.group2.flightres.web.WebApplication)..."
Write-Host "Local URL: http://localhost:$($env:PORT)"
if ($lanIp) {
    Write-Host "LAN URL:   http://${lanIp}:$($env:PORT)"
}

mvn -pl core -DskipTests install -q
if ($LASTEXITCODE -ne 0) { throw "Failed to build core module." }
mvn -pl web compile exec:java

$ErrorActionPreference = "Stop"
$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
Set-Location $repoRoot

$appJsonPath = Join-Path $repoRoot "core/src/main/resources/com/group2/flightres/app.json"
if (-not (Test-Path $appJsonPath)) {
    Write-Error "Missing core/src/main/resources/com/group2/flightres/app.json. Run .\scripts\setup.ps1 first."
}

Write-Host "Installing core and starting Sky Royal Desktop App (com.group2.flightres.App via javafx:run)..."
mvn -pl core -DskipTests install -q
if ($LASTEXITCODE -ne 0) { throw "Failed to build core module." }
mvn -pl desktop javafx:run

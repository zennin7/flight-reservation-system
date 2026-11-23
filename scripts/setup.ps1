param(
    [switch]$NonInteractive
)

& (Join-Path $PSScriptRoot "run-setup.ps1") @PSBoundParameters

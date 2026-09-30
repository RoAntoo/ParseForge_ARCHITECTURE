[CmdletBinding()]
param([Parameter(Mandatory)][string]$EngineRoot, [switch]$Conversions)
$ErrorActionPreference = 'Stop'
& (Join-Path $PSScriptRoot 'invoke-autonomous-marker.ps1') -EngineRoot $EngineRoot -Mode health
if ($Conversions) {
    foreach ($mode in @('digital', 'ocr', 'cancel')) {
        & (Join-Path $PSScriptRoot 'invoke-autonomous-marker.ps1') -EngineRoot $EngineRoot -Mode $mode
    }
}
& (Join-Path $EngineRoot 'runtime\python\python.exe') -I -X utf8 (Join-Path $PSScriptRoot 'audit-autonomous-marker.py') $EngineRoot
if ($LASTEXITCODE -ne 0) { throw 'Runtime audit failed' }

# Stop only server trees owned by this spike; never match the old Marker tree.
[CmdletBinding()]
param()
$ErrorActionPreference = 'Stop'
$spikeRoot = Join-Path $env:LOCALAPPDATA 'ParseForge\engines\marker'
$python = Join-Path $spikeRoot 'runtime\Scripts\python.exe'
function Resolve-SpikePhysicalPath([string]$Path) {
    $physicalPath = & $python -I -B -c "from pathlib import Path; import sys; print(Path(sys.argv[1]).resolve(strict=True))" $Path
    if ($LASTEXITCODE -ne 0) { throw "Could not resolve physical path: $Path" }
    return $physicalPath
}
$runtimePrefix = (Resolve-SpikePhysicalPath (Join-Path $spikeRoot 'runtime')).TrimEnd('\') + '\'
$servers = @(Get-CimInstance Win32_Process | Where-Object {
    if (!$_.ExecutablePath -or !($_.Name -eq 'llama-server.exe' -or
        ($_.Name -eq 'python.exe' -and $_.CommandLine -match '-m surya\.(fast_layout|ocr_error|detection)\.server'))) {
        return $false
    }
    try {
        $resolvedPath = Resolve-SpikePhysicalPath $_.ExecutablePath
    } catch {
        return $false
    }
    $resolvedPath -and $resolvedPath.StartsWith($runtimePrefix, [StringComparison]::OrdinalIgnoreCase)
})
foreach ($server in $servers) {
    Write-Output "Stopping isolated server tree PID $($server.ProcessId): $($server.ExecutablePath)"
    & taskkill.exe /PID $server.ProcessId /T /F
    if ($LASTEXITCODE -ne 0) { throw "Could not stop server $($server.ProcessId)" }
}

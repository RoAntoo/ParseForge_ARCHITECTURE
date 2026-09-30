# Stop only server trees owned by this spike; never match the old Marker tree.
[CmdletBinding()]
param()
$ErrorActionPreference = 'Stop'
$spikeRoot = Join-Path $env:LOCALAPPDATA 'ParseForge\engines\marker'
$runtimePrefix = [IO.Path]::GetFullPath((Join-Path $spikeRoot 'runtime')) + '\'
$servers = @(Get-CimInstance Win32_Process | Where-Object {
    $_.ExecutablePath -and
    $_.ExecutablePath.StartsWith($runtimePrefix, [StringComparison]::OrdinalIgnoreCase) -and
    ($_.Name -eq 'llama-server.exe' -or
     ($_.Name -eq 'python.exe' -and $_.CommandLine -match '-m surya\.(fast_layout|ocr_error|detection)\.server'))
})
foreach ($server in $servers) {
    Write-Output "Stopping isolated server tree PID $($server.ProcessId): $($server.ExecutablePath)"
    & taskkill.exe /PID $server.ProcessId /T /F
    if ($LASTEXITCODE -ne 0) { throw "Could not stop server $($server.ProcessId)" }
}

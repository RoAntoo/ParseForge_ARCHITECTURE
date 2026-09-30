[CmdletBinding()]
param([Parameter(Mandatory)][string]$EngineRoot,
      [ValidateSet('health','digital','ocr','cancel')][string]$Mode = 'health')
$ErrorActionPreference = 'Stop'
$repo = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..\..'))
$classpathFile = Join-Path $repo 'target\spike-classpath.txt'
if (!(Test-Path -LiteralPath $classpathFile)) { throw 'Run mvn compile dependency:build-classpath -Dmdep.outputFile=target/spike-classpath.txt first' }
$classpath = (Join-Path $repo 'target\classes') + ';' + (Get-Content -LiteralPath $classpathFile -Raw).Trim()
$java = (Get-Command java -ErrorAction Stop).Source
$EngineRoot = [IO.Path]::GetFullPath($EngineRoot)
# ProcessHandle.Info does not expose command lines on this Windows JDK.
# Supplement Java's ownership/termination evidence with read-only CIM snapshots.
$monitor = Start-Job -ArgumentList $EngineRoot -ScriptBlock {
    param($root)
    $seen = @{}
    while ($true) {
        Get-CimInstance Win32_Process | Where-Object {
            $_.ExecutablePath -and $_.ExecutablePath.StartsWith($root + '\', [StringComparison]::OrdinalIgnoreCase)
        } | ForEach-Object {
            $identity = "$($_.ProcessId):$($_.CreationDate)"
            if (!$seen.ContainsKey($identity)) {
                $seen[$identity] = $true
                $_ | Select-Object ProcessId,ParentProcessId,ExecutablePath,CommandLine,CreationDate
            }
        }
        Start-Sleep -Milliseconds 500
    }
}
try {
    & $java -cp $classpath dev.parseforge.infrastructure.engine.marker.spike.MarkerRuntimeSpike $EngineRoot $Mode
    $spikeExit = $LASTEXITCODE
} finally {
    Stop-Job $monitor
    @(Receive-Job $monitor) | Select-Object ProcessId,ParentProcessId,ExecutablePath,CommandLine,CreationDate |
        ConvertTo-Json -Depth 5 | Set-Content -LiteralPath (Join-Path $EngineRoot "logs\$Mode-windows-processes.json")
    Remove-Job $monitor
}
if ($spikeExit -ne 0) { throw "Java spike failed: $Mode (exit $spikeExit)" }

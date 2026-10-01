param([string]$AppDirectory, [switch]$Ui)
. "$PSScriptRoot/release-common.ps1"
if (-not $AppDirectory) { $AppDirectory = Join-Path $script:BuildRoot 'app-image/ParseForge' }
$AppDirectory = [IO.Path]::GetFullPath($AppDirectory)
foreach ($file in @('ParseForge.exe','runtime/bin/server/jvm.dll','build-info.json')) {
    if (-not (Test-Path -LiteralPath (Join-Path $AppDirectory $file))) { throw "Missing packaged file: $file" }
}
$reportDir = Join-Path $script:BuildRoot ('smoke/' + [Guid]::NewGuid().ToString())
New-Item -ItemType Directory -Path $reportDir -Force | Out-Null
$report = Join-Path $reportDir 'smoke.json'
$mode = if ($Ui) { '--smoke-ui' } else { '--smoke-test' }
$oldPath = $env:PATH; $oldJava = $env:JAVA_HOME; $oldJavaOpts = $env:JAVA_TOOL_OPTIONS; $oldJdkOpts = $env:JDK_JAVA_OPTIONS
try {
    $env:PATH = "$env:SystemRoot\System32;$env:SystemRoot"; $env:JAVA_HOME = $null
    $env:JDK_JAVA_OPTIONS = $null
    # Isolated roots ensure the smoke never alters the user's settings/engines.
    $env:JAVA_TOOL_OPTIONS = '-Dparseforge.dataDir="' + (Join-Path $reportDir 'data') + '"'
    $process = Start-Process -FilePath (Join-Path $AppDirectory 'ParseForge.exe') -ArgumentList @($mode,('"' + $report + '"')) -WorkingDirectory $env:TEMP -WindowStyle Hidden -PassThru
    if (-not $process.WaitForExit(60000)) { $process.Kill(); throw 'Packaged smoke timed out' }
    if ($process.ExitCode -ne 0 -or -not (Test-Path -LiteralPath $report)) { throw "Packaged smoke failed: $($process.ExitCode)" }
    $result = Get-Content -LiteralPath $report -Raw | ConvertFrom-Json
    if (-not ([IO.Path]::GetFullPath($result.javaHome)).StartsWith($AppDirectory + '\', [StringComparison]::OrdinalIgnoreCase)) { throw 'External Java runtime used' }
    if (-not $result.windowsJob -or -not $result.settingsReadable -or -not $result.settingsPersisted) { throw 'Startup checks failed' }
    if ($Ui -and -not $result.uiOpened) { throw 'Window did not open' }
    $result | ConvertTo-Json
    Write-Host "Packaged smoke passed: $report"
} finally { $env:PATH=$oldPath; $env:JAVA_HOME=$oldJava; $env:JAVA_TOOL_OPTIONS=$oldJavaOpts; $env:JDK_JAVA_OPTIONS=$oldJdkOpts }

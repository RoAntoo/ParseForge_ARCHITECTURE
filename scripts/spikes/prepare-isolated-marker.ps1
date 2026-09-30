# Experimental bootstrap only. Refuses to overwrite an existing engine.
[CmdletBinding()]
param(
    [Parameter(Mandatory)][string]$BootstrapPython,
    [Parameter(Mandatory)][string]$LlamaDirectory
)
$ErrorActionPreference = 'Stop'
$spikeRoot = Join-Path $env:LOCALAPPDATA 'ParseForge\engines\marker'
if (Test-Path -LiteralPath $spikeRoot) { throw "Destination already exists: $spikeRoot" }
$BootstrapPython = (Resolve-Path -LiteralPath $BootstrapPython).Path
$LlamaDirectory = (Resolve-Path -LiteralPath $LlamaDirectory).Path
if (!(Test-Path -LiteralPath (Join-Path $LlamaDirectory 'llama-server.exe'))) {
    throw 'LlamaDirectory must contain llama-server.exe and its DLLs'
}
foreach ($folder in @('runtime', 'models', 'cache', 'temp', 'spike-info', 'cache\home')) {
    New-Item -ItemType Directory -Path (Join-Path $spikeRoot $folder) -Force | Out-Null
}
$saved = @{}
$settings = @{
    PIP_CACHE_DIR = (Join-Path $spikeRoot 'cache\pip')
    TEMP = (Join-Path $spikeRoot 'temp')
    TMP = (Join-Path $spikeRoot 'temp')
    PYTHONNOUSERSITE = '1'
    PYTHONDONTWRITEBYTECODE = '1'
    PYTHONPATH = $null
    PYTHONHOME = $null
}
try {
    foreach ($name in $settings.Keys) {
        $saved[$name] = [Environment]::GetEnvironmentVariable($name, 'Process')
        if ($null -eq $settings[$name]) {
            Remove-Item -LiteralPath "Env:$name" -ErrorAction SilentlyContinue
        } else {
            [Environment]::SetEnvironmentVariable($name, $settings[$name], 'Process')
        }
    }
    & $BootstrapPython -m venv (Join-Path $spikeRoot 'runtime')
    if ($LASTEXITCODE -ne 0) { throw 'venv creation failed' }
    $python = Join-Path $spikeRoot 'runtime\Scripts\python.exe'
    & $python -m pip install --disable-pip-version-check -r (Join-Path $PSScriptRoot 'marker-requirements.txt') *> (Join-Path $spikeRoot 'spike-info\pip-install.log')
    if ($LASTEXITCODE -ne 0) { throw 'Installation failed; see spike-info/pip-install.log' }
    & $python -m pip check
    if ($LASTEXITCODE -ne 0) { throw 'Dependency verification failed' }
    Copy-Item -LiteralPath $LlamaDirectory -Destination (Join-Path $spikeRoot 'runtime\llamacpp') -Recurse
    & $python -B (Join-Path $PSScriptRoot 'new-marker-fixture.py') (Join-Path $spikeRoot 'temp\stage-3a.pdf')
    if ($LASTEXITCODE -ne 0) { throw 'Fixture creation failed' }
} finally {
    foreach ($name in $saved.Keys) {
        if ($null -eq $saved[$name]) {
            Remove-Item -LiteralPath "Env:$name" -ErrorAction SilentlyContinue
        } else {
            [Environment]::SetEnvironmentVariable($name, $saved[$name], 'Process')
        }
    }
}

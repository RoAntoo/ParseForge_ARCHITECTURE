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
    $cpuPackages = @(Get-Content -LiteralPath (Join-Path $PSScriptRoot 'marker-requirements.txt') |
        Where-Object { $_ -match '^(torch|torchvision)==' })
    if ($cpuPackages.Count -ne 2) { throw 'Expected pinned torch and torchvision requirements' }
    & $python -m pip install --disable-pip-version-check --no-deps --index-url https://download.pytorch.org/whl/cpu @cpuPackages *> (Join-Path $spikeRoot 'spike-info\pip-install-cpu.log')
    if ($LASTEXITCODE -ne 0) { throw 'CPU wheels installation failed; see spike-info/pip-install-cpu.log' }
    & $python -m pip install --disable-pip-version-check -r (Join-Path $PSScriptRoot 'marker-requirements.txt') *> (Join-Path $spikeRoot 'spike-info\pip-install.log')
    if ($LASTEXITCODE -ne 0) { throw 'Installation failed; see spike-info/pip-install.log' }
    & $python -B -c "import sys,torch,torchvision; print('torch:',torch.__version__,'torchvision:',torchvision.__version__,'CUDA:',torch.version.cuda,'HIP:',torch.version.hip); sys.exit(0 if '+cpu' in torch.__version__ and '+cpu' in torchvision.__version__ and torch.version.cuda is None and torch.version.hip is None and not torch.backends.cuda.is_built() else 'Expected CPU-only PyTorch and torchvision builds')" *> (Join-Path $spikeRoot 'spike-info\cpu-build-check.log')
    if ($LASTEXITCODE -ne 0) { throw 'CPU build verification failed; see spike-info/cpu-build-check.log' }
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

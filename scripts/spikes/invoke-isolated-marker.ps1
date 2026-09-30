# Experimental Stage 3A launcher. Does not change ParseForge's persisted settings.
[CmdletBinding()]
param(
    [string]$Pdf,
    [switch]$ForceOcr
)
$ErrorActionPreference = 'Stop'
$spikeRoot = Join-Path $env:LOCALAPPDATA 'ParseForge\engines\marker'
$python = Join-Path $spikeRoot 'runtime\Scripts\python.exe'
$entrypoint = Join-Path $spikeRoot 'runtime\Scripts\marker_single.exe'
if (!(Test-Path -LiteralPath $entrypoint)) { throw "Missing isolated entrypoint: $entrypoint" }
if ($Pdf) { $Pdf = (Resolve-Path -LiteralPath $Pdf).Path }
$settings = @{
    PYTHONDONTWRITEBYTECODE = '1'
    PYTHONNOUSERSITE = '1'
    PYTHONPATH = $null
    PYTHONHOME = $null
    USERPROFILE = (Join-Path $spikeRoot 'cache\home')
    HOME = (Join-Path $spikeRoot 'cache\home')
    HF_HOME = (Join-Path $spikeRoot 'models\huggingface')
    HF_HUB_CACHE = (Join-Path $spikeRoot 'models\huggingface\hub')
    XDG_CACHE_HOME = (Join-Path $spikeRoot 'cache')
    TORCH_HOME = (Join-Path $spikeRoot 'cache\torch')
    MODEL_CACHE_DIR = (Join-Path $spikeRoot 'models\datalab')
    PIP_CACHE_DIR = (Join-Path $spikeRoot 'cache\pip')
    TEMP = (Join-Path $spikeRoot 'temp')
    TMP = (Join-Path $spikeRoot 'temp')
    TORCH_DEVICE = 'cpu'
    SURYA_INFERENCE_BACKEND = 'llamacpp'
    SURYA_INFERENCE_URL = $null
    SURYA_INFERENCE_PORT = $null
    # Avoid upstream atexit process termination on Windows; stop explicitly.
    SURYA_INFERENCE_KEEP_ALIVE = 'true'
    SURYA_GGUF_LOCAL_MODEL_PATH = $null
    SURYA_GGUF_LOCAL_MMPROJ_PATH = $null
    LLAMA_CPP_BINARY = (Join-Path $spikeRoot 'runtime\llamacpp\llama-server.exe')
    FAST_LAYOUT_SERVER_URL = $null
    FAST_LAYOUT_SERVER_PORT = $null
    OCR_ERROR_SERVER_URL = $null
    OCR_ERROR_SERVER_PORT = $null
    DETECTOR_SERVER_URL = $null
    DETECTOR_SERVER_PORT = $null
    HF_HUB_DISABLE_SYMLINKS_WARNING = '1'
}
$saved = @{}
try {
    foreach ($name in $settings.Keys) {
        $saved[$name] = [Environment]::GetEnvironmentVariable($name, 'Process')
        if ($null -eq $settings[$name]) {
            Remove-Item -LiteralPath "Env:$name" -ErrorAction SilentlyContinue
        } else {
            [Environment]::SetEnvironmentVariable($name, $settings[$name], 'Process')
        }
    }
    Push-Location -LiteralPath $spikeRoot
    try {
        & $python -B -c "import sys,os,marker,surya; print('PYTHON:',sys.executable); print('PREFIX:',sys.prefix); print('BASE:',sys.base_prefix); print('MARKER:',list(marker.__path__)); print('SURYA:',surya.__file__); print('SERVER_CACHE:',os.path.expanduser('~/.cache/datalab/surya'))"
        if ($LASTEXITCODE -ne 0) { throw 'Runtime verification failed' }
        Write-Output "ENTRYPOINT: $entrypoint"
        if (!$Pdf) {
            & $entrypoint --help
        } else {
            $markerArgs = @($Pdf, '--output_dir', (Join-Path $spikeRoot 'temp\output'), '--output_format', 'markdown')
            if ($ForceOcr) { $markerArgs += '--force_ocr' }
            & $entrypoint @markerArgs
        }
        if ($LASTEXITCODE -ne 0) { throw "Marker exited with code $LASTEXITCODE" }
    } finally { Pop-Location }
} finally {
    foreach ($name in $saved.Keys) {
        if ($null -eq $saved[$name]) {
            Remove-Item -LiteralPath "Env:$name" -ErrorAction SilentlyContinue
        } else {
            [Environment]::SetEnvironmentVariable($name, $saved[$name], 'Process')
        }
    }
}

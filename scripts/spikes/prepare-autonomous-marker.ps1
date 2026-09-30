# Stage 3B only. All artifacts stay outside Git. Never bootstraps with global Python.
[CmdletBinding()]
param([Parameter(Mandatory)][string]$EngineRoot)
$ErrorActionPreference = 'Stop'
$EngineRoot = [IO.Path]::GetFullPath($EngineRoot)
if (Test-Path -LiteralPath (Join-Path $EngineRoot 'runtime')) { throw 'Runtime already exists; choose a fresh destination.' }
if ($EngineRoot.StartsWith([IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..\..')), [StringComparison]::OrdinalIgnoreCase)) { throw 'Engine must be outside the repository' }
$artifacts = Get-Content (Join-Path $PSScriptRoot 'runtime-artifacts.json') -Raw | ConvertFrom-Json
foreach ($folder in @('downloads', 'runtime\python\Lib\site-packages', 'runtime\llamacpp', 'models', 'cache\home', 'temp', 'logs')) {
    New-Item -ItemType Directory -Path (Join-Path $EngineRoot $folder) -Force | Out-Null
}
function Get-VerifiedArtifact($artifact, $filename) {
    $destination = Join-Path $EngineRoot "downloads\$filename"
    if (!(Test-Path -LiteralPath $destination)) { Invoke-WebRequest $artifact.url -OutFile $destination }
    if ((Get-FileHash -LiteralPath $destination -Algorithm SHA256).Hash -ne $artifact.sha256) { throw "Hash mismatch: $filename" }
    return $destination
}
$pythonZip = Get-VerifiedArtifact $artifacts.python 'python.zip'
$llamaZip = Get-VerifiedArtifact $artifacts.llamaCpp 'llama.zip'
$pipWheel = Get-VerifiedArtifact $artifacts.pip 'pip-25.0.1-py3-none-any.whl'
$pythonDir = Join-Path $EngineRoot 'runtime\python'
Expand-Archive -LiteralPath $pythonZip -DestinationPath $pythonDir -Force
# Materialize the bundled compiled stdlib under a conventional Lib directory.
Expand-Archive -LiteralPath (Join-Path $pythonDir 'python312.zip') -DestinationPath (Join-Path $pythonDir 'Lib') -Force
[IO.File]::WriteAllText((Join-Path $pythonDir 'python312._pth'), "Lib`n.`nLib/site-packages`nimport site`n", [Text.Encoding]::ASCII)
Add-Type -AssemblyName System.IO.Compression.FileSystem
[IO.Compression.ZipFile]::ExtractToDirectory($pipWheel, (Join-Path $pythonDir 'Lib\site-packages'))
Expand-Archive -LiteralPath $llamaZip -DestinationPath (Join-Path $EngineRoot 'runtime\llamacpp') -Force
$llama = @(Get-ChildItem -LiteralPath (Join-Path $EngineRoot 'runtime\llamacpp') -Filter llama-server.exe -Recurse)
if ($llama.Count -ne 1) { throw 'Expected exactly one llama-server.exe' }
$python = Join-Path $pythonDir 'python.exe'
$wheels = Join-Path $EngineRoot 'downloads\wheels'
New-Item -ItemType Directory -Path $wheels -Force | Out-Null
$requirements = Join-Path $PSScriptRoot 'marker-requirements.txt'
$cpuPins = @(Get-Content $requirements | Where-Object { $_ -match '^(torch|torchvision)==' })
& $python -I -m pip --isolated --cache-dir (Join-Path $EngineRoot 'cache\pip') download --only-binary=:all: --no-deps --index-url https://download.pytorch.org/whl/cpu --dest $wheels @cpuPins *> (Join-Path $EngineRoot 'logs\download-cpu.log')
if ($LASTEXITCODE -ne 0) { throw 'CPU wheel download failed' }
& $python -I -m pip --isolated --cache-dir (Join-Path $EngineRoot 'cache\pip') download --only-binary=:all: --find-links $wheels --dest $wheels -r $requirements *> (Join-Path $EngineRoot 'logs\download-wheels.log')
if ($LASTEXITCODE -ne 0) { throw 'Wheel download failed' }
# Hash every wheel before installation; lock file is portable across fresh destinations.
& $python -I (Join-Path $PSScriptRoot 'seal-marker-runtime.py') wheels $EngineRoot
if ($LASTEXITCODE -ne 0) { throw 'Wheel lock failed' }
$expectedLock = Join-Path $PSScriptRoot 'marker-autonomous-requirements.lock'
if ((Get-Content -LiteralPath $expectedLock -Raw).Replace("`r`n", "`n") -ne (Get-Content -LiteralPath (Join-Path $EngineRoot 'downloads\requirements.lock') -Raw).Replace("`r`n", "`n")) {
    throw 'Downloaded wheels differ from the reviewed Stage 3B hash lock'
}
& $python -I -m pip --isolated --cache-dir (Join-Path $EngineRoot 'cache\pip') install --no-index --find-links $wheels --require-hashes -r (Join-Path $EngineRoot 'downloads\requirements.lock') *> (Join-Path $EngineRoot 'logs\install.log')
if ($LASTEXITCODE -ne 0) { throw 'Wheel installation failed' }
& $python -I -m pip check *> (Join-Path $EngineRoot 'logs\pip-check.log')
if ($LASTEXITCODE -ne 0) { throw 'Dependency verification failed' }
& $python -I (Join-Path $PSScriptRoot 'seal-marker-runtime.py') manifest $EngineRoot (Join-Path $PSScriptRoot 'runtime-artifacts.json')
if ($LASTEXITCODE -ne 0) { throw 'Manifest creation failed' }
& $python -I (Join-Path $PSScriptRoot 'new-marker-fixture.py') (Join-Path $EngineRoot 'temp\digital.pdf')
if ($LASTEXITCODE -ne 0) { throw 'PDF fixture failed' }
Copy-Item -LiteralPath (Join-Path $EngineRoot 'temp\digital.pdf') -Destination (Join-Path $EngineRoot 'temp\ocr.pdf')
Write-Output "Prepared autonomous runtime: $EngineRoot"

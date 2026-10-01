$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest
$script:ProjectRoot = Split-Path $PSScriptRoot -Parent
$script:BuildRoot = Join-Path $script:ProjectRoot 'build'
$script:Tools = Get-Content (Join-Path $PSScriptRoot 'release-tools.json') -Raw | ConvertFrom-Json

function Get-ReleaseVersion {
    [xml]$pom = Get-Content (Join-Path $script:ProjectRoot 'pom.xml') -Raw
    $version = [string]$pom.project.version
    if ($version -notmatch '^\d+\.\d+\.\d+$') { throw "Release requires stable SemVer: $version" }
    return $version
}
function Reset-BuildDirectory([string]$Path) {
    $absolute = [IO.Path]::GetFullPath($Path)
    $allowed = [IO.Path]::GetFullPath($script:BuildRoot) + [IO.Path]::DirectorySeparatorChar
    if (-not $absolute.StartsWith($allowed, [StringComparison]::OrdinalIgnoreCase)) { throw "Unsafe build deletion: $absolute" }
    if (Test-Path -LiteralPath $absolute) { Remove-Item -LiteralPath $absolute -Recurse -Force }
    New-Item -ItemType Directory -Path $absolute -Force | Out-Null
}
function Invoke-Checked([string]$Executable, [string[]]$Arguments) {
    & $Executable @Arguments
    if ($LASTEXITCODE -ne 0) { throw "$Executable failed: exit $LASTEXITCODE" }
}
function Get-VerifiedTool($Artifact, [string]$Path) {
    New-Item -ItemType Directory -Path (Split-Path $Path -Parent) -Force | Out-Null
    if (-not (Test-Path -LiteralPath $Path)) {
        Write-Host "Downloading pinned tool: $($Artifact.version)"
        Invoke-WebRequest -Uri $Artifact.url -OutFile "$Path.part"
        if ((Get-FileHash -LiteralPath "$Path.part" -Algorithm SHA256).Hash.ToLowerInvariant() -ne $Artifact.sha256) {
            Remove-Item -LiteralPath "$Path.part" -Force
            throw 'Tool SHA-256 mismatch'
        }
        Move-Item -LiteralPath "$Path.part" -Destination $Path
    }
    if ((Get-FileHash -LiteralPath $Path -Algorithm SHA256).Hash.ToLowerInvariant() -ne $Artifact.sha256) { throw "Tool SHA-256 mismatch: $Path" }
}
function Get-ReleaseJdk {
    $directory = Join-Path $script:BuildRoot ('tools/' + $script:Tools.jdk.directory)
    $archive = Join-Path $script:BuildRoot 'tools/temurin.zip'
    Get-VerifiedTool $script:Tools.jdk $archive
    if (-not (Test-Path -LiteralPath (Join-Path $directory 'bin/jpackage.exe'))) {
        Expand-Archive -LiteralPath $archive -DestinationPath (Join-Path $script:BuildRoot 'tools') -Force
    }
    return $directory
}
function Get-InnoCompiler {
    $directory = Join-Path $script:BuildRoot 'tools/inno'
    $installer = Join-Path $script:BuildRoot 'tools/inno-setup.exe'
    Get-VerifiedTool $script:Tools.inno $installer
    $compiler = Join-Path $directory 'ISCC.exe'
    if (-not (Test-Path -LiteralPath $compiler)) {
        $process = Start-Process -FilePath $installer -ArgumentList @('/VERYSILENT', '/SUPPRESSMSGBOXES', '/NORESTART', '/CURRENTUSER', ('/DIR="' + $directory + '"')) -WindowStyle Hidden -PassThru -Wait
        if ($process.ExitCode -ne 0) { throw "Private Inno compiler install failed: $($process.ExitCode)" }
    }
    if (-not (Test-Path -LiteralPath $compiler)) { throw "Inno compiler missing: $compiler" }
    return $compiler
}

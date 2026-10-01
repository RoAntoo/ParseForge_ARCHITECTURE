param()
. "$PSScriptRoot/release-common.ps1"
& "$PSScriptRoot/build.ps1"
& "$PSScriptRoot/package.ps1" -SkipBuild
$version = Get-ReleaseVersion
$compiler = Get-InnoCompiler
Reset-BuildDirectory (Join-Path $script:BuildRoot 'installer')
Reset-BuildDirectory (Join-Path $script:BuildRoot 'release')
Invoke-Checked $compiler @("/DAppVersion=$version", ('/DProjectRoot=' + $script:ProjectRoot), (Join-Path $script:ProjectRoot 'packaging/ParseForge.iss'))
$release = Join-Path $script:BuildRoot 'release'
Copy-Item -LiteralPath (Join-Path $script:BuildRoot "installer/ParseForge-Setup-$version.exe") -Destination $release
Compress-Archive -LiteralPath (Join-Path $script:BuildRoot 'app-image/ParseForge') -DestinationPath (Join-Path $release "ParseForge-$version-win-x64.zip") -CompressionLevel Optimal
$hashes = Get-ChildItem -LiteralPath $release -File | Sort-Object Name | ForEach-Object {
    (Get-FileHash -LiteralPath $_.FullName -Algorithm SHA256).Hash.ToLowerInvariant() + '  ' + $_.Name
}
$hashes | Set-Content -LiteralPath (Join-Path $release 'SHA256SUMS.txt') -Encoding ASCII
Copy-Item -LiteralPath (Join-Path $script:ProjectRoot 'docs/release/release-notes.md') -Destination $release
Write-Host "PACKAGE BUILT - VM VALIDATION PENDING: $release"

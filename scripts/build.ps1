param()
. "$PSScriptRoot/release-common.ps1"
$jdk = Get-ReleaseJdk
$oldJava = $env:JAVA_HOME
$oldPath = $env:PATH
try {
    $env:JAVA_HOME = $jdk; $env:PATH = "$jdk\bin;$oldPath"
    Push-Location $script:ProjectRoot
    try { Invoke-Checked 'mvn.cmd' @('-B', 'clean', 'verify', 'dependency:copy-dependencies', '-DincludeScope=runtime', '-DoutputDirectory=target/package-input') }
    finally { Pop-Location }
    $version = Get-ReleaseVersion
    Copy-Item -LiteralPath (Join-Path $script:ProjectRoot "target/parseforge-$version.jar") -Destination (Join-Path $script:ProjectRoot 'target/package-input')
} finally { $env:JAVA_HOME = $oldJava; $env:PATH = $oldPath }

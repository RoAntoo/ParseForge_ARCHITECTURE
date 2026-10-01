param([switch]$SkipBuild)
. "$PSScriptRoot/release-common.ps1"
if (-not $SkipBuild) { & "$PSScriptRoot/build.ps1" }
$version = Get-ReleaseVersion
$jdk = Get-ReleaseJdk
$input = Join-Path $script:ProjectRoot 'target/package-input'
if (-not (Test-Path -LiteralPath (Join-Path $input "parseforge-$version.jar"))) { throw 'Run build.ps1 first' }
$runtime = Join-Path $script:BuildRoot 'jlink/runtime'
Reset-BuildDirectory (Join-Path $script:BuildRoot 'jlink')
Reset-BuildDirectory (Join-Path $script:BuildRoot 'app-image')
# JavaFX and third-party jars are classpath dependencies; jlink trims the JDK.
# jdeps analyzes all runtime jars; crypto.ec is also required as an HTTPS provider.
$jars = @(Get-ChildItem -LiteralPath $input -File -Filter '*.jar' | Select-Object -ExpandProperty FullName)
$analysis = & (Join-Path $jdk 'bin/jdeps.exe') --ignore-missing-deps --multi-release 21 --print-module-deps @jars
if ($LASTEXITCODE -ne 0) { throw 'jdeps module analysis failed' }
$modules = ((($analysis -join '').Split(',') + @('jdk.crypto.ec')) | Sort-Object -Unique) -join ','
if ($modules -notmatch '^([a-z][a-z0-9.]*,)*[a-z][a-z0-9.]*$') { throw "Invalid module analysis: $modules" }
$modules | Set-Content -LiteralPath (Join-Path $script:BuildRoot 'jlink/modules.txt') -Encoding ASCII
Invoke-Checked (Join-Path $jdk 'bin/jlink.exe') @('--module-path', (Join-Path $jdk 'jmods'), '--add-modules', $modules, '--strip-debug', '--no-header-files', '--no-man-pages', '--compress=2', '--output', $runtime)
$icon = Join-Path $script:ProjectRoot 'src/main/resources/icons/ParseForge.ico'
Invoke-Checked (Join-Path $jdk 'bin/jpackage.exe') @('--type', 'app-image', '--name', 'ParseForge', '--app-version', $version, '--vendor', 'ParseForge contributors', '--description', 'Local document conversion', '--copyright', 'Copyright ParseForge contributors', '--icon', $icon, '--input', $input, '--main-jar', "parseforge-$version.jar", '--main-class', 'dev.parseforge.presentation.javafx.Launcher', '--runtime-image', $runtime, '--dest', (Join-Path $script:BuildRoot 'app-image'), '--java-options', '-Dfile.encoding=UTF-8')
$app = Join-Path $script:BuildRoot 'app-image/ParseForge'
Copy-Item -LiteralPath (Join-Path $script:ProjectRoot 'LICENSE'), (Join-Path $script:ProjectRoot 'THIRD_PARTY_NOTICES.md') -Destination $app
& "$PSScriptRoot/collect-notices.ps1" -AppDirectory $app
@{version=$version; jdk=$script:Tools.jdk.version; modules=$modules; inno=$script:Tools.inno.version} | ConvertTo-Json | Set-Content (Join-Path $app 'build-info.json') -Encoding UTF8
Write-Host "App image: $app"

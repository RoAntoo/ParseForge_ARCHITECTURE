# Opt-in host install/reinstall/uninstall in build; never a clean-VM substitute.
param()
. "$PSScriptRoot/release-common.ps1"
$key = 'HKCU:\Software\Microsoft\Windows\CurrentVersion\Uninstall\{FB01D31C-0D7A-4A0B-9765-063ED02299E4}_is1'
if (Test-Path -LiteralPath $key) { throw 'An existing ParseForge registration exists; use a clean test account instead' }
$desktop = Join-Path ([Environment]::GetFolderPath('DesktopDirectory')) 'ParseForge.lnk'
$start = Join-Path ([Environment]::GetFolderPath('Programs')) 'ParseForge.lnk'
if ((Test-Path -LiteralPath $desktop) -or (Test-Path -LiteralPath $start)) { throw 'Existing ParseForge shortcuts found; use a clean test account' }
$version = Get-ReleaseVersion
$setup = Join-Path $script:BuildRoot "release/ParseForge-Setup-$version.exe"
$destination = Join-Path $script:BuildRoot 'installed-test'
if (Test-Path -LiteralPath $destination) { throw "Test destination already exists: $destination" }
$logRoot = Join-Path $script:BuildRoot 'installer-test'
New-Item -ItemType Directory -Path $logRoot -Force | Out-Null
$installed = $false
function Run-Setup([string]$Name) {
    $args = @('/VERYSILENT','/SUPPRESSMSGBOXES','/NORESTART','/LANG=spanish',
        '/TASKS="desktopicon,startmenuicon"', ('/DIR="' + $destination + '"'), ('/LOG="' + (Join-Path $logRoot ($Name + '.log')) + '"'))
    $process = Start-Process -FilePath $setup -ArgumentList $args -WindowStyle Hidden -Wait -PassThru
    if ($process.ExitCode -ne 0) { throw "Setup failed: $($process.ExitCode)" }
}
try {
    Run-Setup 'install'; $installed = $true
    foreach($path in @((Join-Path $destination 'ParseForge.exe'),$desktop,$start)) {
        if (-not (Test-Path -LiteralPath $path)) { throw "Installed file/shortcut missing: $path" }
    }
    & "$PSScriptRoot/smoke-installed.ps1" -AppDirectory $destination -Ui
    # A user-created document in the app folder must also survive uninstall.
    $document = Join-Path $destination 'user-document.md'
    Set-Content -LiteralPath $document -Value 'Preserve this user document' -Encoding UTF8
    Run-Setup 'reinstall'
    if ((Get-Content -LiteralPath $document -Raw).Trim() -ne 'Preserve this user document') { throw 'Reinstall changed user document' }
    & "$PSScriptRoot/smoke-installed.ps1" -AppDirectory $destination
} finally {
    if ($installed) {
        $absolute = [IO.Path]::GetFullPath($destination)
        if (-not $absolute.StartsWith([IO.Path]::GetFullPath($script:BuildRoot) + '\', [StringComparison]::OrdinalIgnoreCase)) { throw 'Unsafe uninstall path' }
        $uninstaller = Join-Path $absolute 'unins000.exe'
        if (Test-Path -LiteralPath $uninstaller) {
            $process = Start-Process -FilePath $uninstaller -ArgumentList @('/VERYSILENT','/SUPPRESSMSGBOXES','/NORESTART',('/LOG="' + (Join-Path $logRoot 'uninstall.log') + '"')) -WindowStyle Hidden -Wait -PassThru
            if ($process.ExitCode -ne 0) { throw "Uninstall failed: $($process.ExitCode)" }
        }
    }
}
foreach($path in @((Join-Path $destination 'ParseForge.exe'),$desktop,$start,$key)) {
    if (Test-Path -LiteralPath $path) { throw "Uninstall retained app-owned item: $path" }
}
if (-not (Test-Path -LiteralPath $document)) { throw 'Uninstall deleted user document' }
Write-Host "Host install/reinstall/uninstall passed; user document retained. Logs: $logRoot"

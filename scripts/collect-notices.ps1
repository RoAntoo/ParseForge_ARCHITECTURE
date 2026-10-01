param([Parameter(Mandatory)][string]$AppDirectory)
. "$PSScriptRoot/release-common.ps1"
Add-Type -AssemblyName System.IO.Compression.FileSystem
$destination = Join-Path $AppDirectory 'third-party-licenses'
New-Item -ItemType Directory -Path $destination -Force | Out-Null
Copy-Item -LiteralPath (Join-Path $script:ProjectRoot 'packaging/licenses/SOURCES.md') -Destination $destination
Get-ChildItem -LiteralPath (Join-Path $script:ProjectRoot 'packaging/licenses') -Directory | ForEach-Object {
    Copy-Item -LiteralPath $_.FullName -Destination $destination -Recurse -Force
}
$inventory = @()
foreach ($jar in (Get-ChildItem -LiteralPath (Join-Path $AppDirectory 'app') -File -Filter '*.jar')) {
    $inventory += @{file=$jar.Name; sha256=(Get-FileHash -LiteralPath $jar.FullName -Algorithm SHA256).Hash.ToLowerInvariant()}
    $zip = [IO.Compression.ZipFile]::OpenRead($jar.FullName)
    try {
        foreach($entry in $zip.Entries) {
            if ($entry.FullName -match '(^|/)(LICENSE|LICENCE|NOTICE|COPYING|COPYRIGHT)([^/]*)$') {
                $directory = Join-Path $destination $jar.BaseName
                New-Item -ItemType Directory -Path $directory -Force | Out-Null
                $name = $entry.FullName.Replace('/','_').Replace('\','_')
                [IO.Compression.ZipFileExtensions]::ExtractToFile($entry, (Join-Path $directory $name), $true)
            }
        }
    } finally { $zip.Dispose() }
}
$inventory | ConvertTo-Json | Set-Content -LiteralPath (Join-Path $AppDirectory 'dependency-inventory.json') -Encoding UTF8

[CmdletBinding()]
param(
    [string] $ReleaseDirectory,
    [string] $OutputDirectory
)

$ErrorActionPreference = 'Stop'
$scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
if (!$ReleaseDirectory) { $ReleaseDirectory = Join-Path $scriptDir '..\..\build\releases\0.1.0' }
if (!$OutputDirectory) { $OutputDirectory = Join-Path $scriptDir '..\..\build\chocolatey' }
$releaseDir = (Resolve-Path -LiteralPath $ReleaseDirectory).Path
$outputDir = [IO.Path]::GetFullPath($OutputDirectory)
$manifestPath = Join-Path $releaseDir 'release-manifest.json'
$manifest = Get-Content -LiteralPath $manifestPath -Raw | ConvertFrom-Json
$version = $manifest.version
if ($version -notmatch '^\d+\.\d+\.\d+$' -or $manifest.java -ne 25) {
    throw 'This package requires a release manifest with a numeric x.y.z version and Java 25.'
}

$archiveName = "mcfpp-$version.zip"
$sourceName = "mcfpp-$version-source.tar.xz"
$hashes = @{}
foreach ($name in @($archiveName, $sourceName)) {
    $expected = $manifest.sha256.$name
    $actual = (Get-FileHash -LiteralPath (Join-Path $releaseDir $name) -Algorithm SHA256).Hash.ToLowerInvariant()
    if (!$expected -or $actual -ne $expected) {
        throw "Release checksum mismatch: $name"
    }
    $hashes[$name] = $actual
}

# A new staging directory prevents files from a previous package entering this one.
$stage = Join-Path $outputDir ("staging-" + [guid]::NewGuid().ToString('N'))
$toolsDir = Join-Path $stage 'tools'
New-Item -ItemType Directory -Path $toolsDir -Force | Out-Null
$utf8 = New-Object System.Text.UTF8Encoding($false)
foreach ($relative in @('mcfpp.nuspec', 'tools\chocolateyinstall.ps1', 'tools\chocolateyuninstall.ps1')) {
    $template = [IO.File]::ReadAllText((Join-Path $scriptDir $relative))
    $content = $template.Replace('@VERSION@', $version).Replace('@SHA256@', $hashes[$archiveName])
    [IO.File]::WriteAllText((Join-Path $stage $relative), $content, $utf8)
}
foreach ($name in @($archiveName, $sourceName, 'release-manifest.json', 'SHA256SUMS')) {
    Copy-Item -LiteralPath (Join-Path $releaseDir $name) -Destination $toolsDir
}

# Use the license shipped with the frozen release, not the working tree.
Add-Type -AssemblyName System.IO.Compression.FileSystem
$zip = [IO.Compression.ZipFile]::OpenRead((Join-Path $releaseDir $archiveName))
try {
    $license = $zip.GetEntry("mcfpp-$version/LICENSE")
    if (!$license) { throw 'The release ZIP does not contain LICENSE.' }
    [IO.Compression.ZipFileExtensions]::ExtractToFile($license, (Join-Path $toolsDir 'LICENSE.txt'))
} finally {
    $zip.Dispose()
}

$verification = @"
MCFPP $version is packaged by the upstream project maintainer.
Project: https://github.com/MinecraftFunctionPlusPlus/MCFPP

The embedded distribution and frozen project sources are produced by the
upstream Gradle release task (gradlew.bat release, JDK 25). The source archive
contains the build scripts and project sources for this release. Extract it
with tar -xf $sourceName and follow docs/cli.md for the Mcfpm SDK prerequisite.
Third-party runtime license notices are retained inside the distribution JAR.

Verify the embedded release files with PowerShell Get-FileHash -Algorithm SHA256:
$($hashes[$archiveName])  $archiveName
$($hashes[$sourceName])  $sourceName

The original release-manifest.json and SHA256SUMS are included for provenance.
Only the Windows distribution and source archive from that manifest are embedded.
The installer validates the ZIP checksum, extracts it under the package tools
directory, and creates the mcfpp command with Chocolatey's Install-BinFile.
Uninstallation removes that command and Chocolatey removes the package directory.
"@
[IO.File]::WriteAllText((Join-Path $toolsDir 'VERIFICATION.txt'), $verification + "`n", $utf8)

& choco pack (Join-Path $stage 'mcfpp.nuspec') "--output-directory=$outputDir" --no-progress
if ($LASTEXITCODE -ne 0) { throw "choco pack failed with exit code $LASTEXITCODE" }
$package = Join-Path $outputDir "mcfpp.$version.nupkg"
Get-FileHash -LiteralPath $package -Algorithm SHA256

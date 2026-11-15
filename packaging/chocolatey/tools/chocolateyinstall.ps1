$ErrorActionPreference = 'Stop'
$toolsDir = Split-Path -Parent $MyInvocation.MyCommand.Definition
$archive = Join-Path $toolsDir 'mcfpp-@VERSION@.zip'

Get-ChecksumValid -File $archive -Checksum '@SHA256@' -ChecksumType 'sha256'
Get-ChocolateyUnzip -FileFullPath $archive -Destination $toolsDir -PackageName 'mcfpp'

$launcher = Join-Path $toolsDir 'mcfpp-@VERSION@\bin\mcfpp.bat'
if (!(Test-Path -LiteralPath $launcher -PathType Leaf)) {
    throw 'The MCFPP distribution does not contain its Windows launcher.'
}
Install-BinFile -Name 'mcfpp' -Path $launcher

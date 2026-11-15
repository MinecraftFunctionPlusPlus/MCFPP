$ErrorActionPreference = 'Stop'
$toolsDir = Split-Path -Parent $MyInvocation.MyCommand.Definition
$launcher = Join-Path $toolsDir 'mcfpp-@VERSION@\bin\mcfpp.bat'
Uninstall-BinFile -Name 'mcfpp' -Path $launcher

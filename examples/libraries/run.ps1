param(
    [ValidateSet('all', 'producer', 'install', 'consumer', 'verify')][string]$Step = 'all',
    [Parameter(Mandatory = $true)][string]$CompilerJar,
    [string]$McfpmCommand = 'mcfpm',
    [string]$JavaHome21,
    [string]$WorkDirectory = (Join-Path $PSScriptRoot 'work')
)
$ErrorActionPreference = 'Stop'
$CompilerJar = [IO.Path]::GetFullPath($CompilerJar)
$workRoot = [IO.Path]::GetFullPath($WorkDirectory)
$producerRoot = Join-Path $workRoot 'producer'
$consumerRoot = Join-Path $workRoot 'consumer'
$installHome = Join-Path $workRoot 'host-home'
$repositoryRoot = Join-Path $workRoot 'repository'
$javaExecutable = if ($JavaHome21) { Join-Path $JavaHome21 'bin/java.exe' } else { 'java' }
if (!(Test-Path -LiteralPath $CompilerJar -PathType Leaf)) { throw "Compiler jar not found: $CompilerJar" }
if ($McfpmCommand -ne 'mcfpm') { $McfpmCommand = [IO.Path]::GetFullPath($McfpmCommand) }

function Invoke-Compiler([string]$Config) {
    & $javaExecutable "-Duser.home=$installHome" '-jar' $CompilerJar build --project $Config
    if ($LASTEXITCODE -ne 0) { throw "MCFPP exited with $LASTEXITCODE" }
}
function Invoke-ExampleStep([string]$CurrentStep) {
    switch ($CurrentStep) {
        'producer' {
            foreach ($name in @('producer', 'consumer')) {
                $destination = Join-Path $workRoot $name
                if (Test-Path -LiteralPath $destination) { throw "Use a new WorkDirectory; already exists: $destination" }
            }
            New-Item -ItemType Directory -Path $workRoot, $installHome -Force | Out-Null
            New-Item -ItemType Directory -Path $producerRoot -Force | Out-Null
            foreach ($inputName in @('mcfpp.toml', 'mcfpp-packages.json', 'src', 'data')) {
                Copy-Item -LiteralPath (Join-Path $PSScriptRoot "producer/$inputName") -Destination (Join-Path $producerRoot $inputName) -Recurse
            }
            Copy-Item -LiteralPath (Join-Path $PSScriptRoot 'consumer') -Destination $consumerRoot -Recurse
            Invoke-Compiler (Join-Path $producerRoot 'mcfpp.toml')
            if (!(Test-Path -LiteralPath (Join-Path $producerRoot 'out/bin.mclib'))) { throw 'Missing published v1 library' }
            Copy-Item -LiteralPath (Join-Path $PSScriptRoot 'producer/mcfpm.toml') -Destination (Join-Path $producerRoot 'mcfpm.toml')
        }
        'install' {
            $versionRoot = Join-Path $repositoryRoot 'fixture/semantic-lib/1.0.0'
            New-Item -ItemType Directory -Path $versionRoot -Force | Out-Null
            Push-Location -LiteralPath $producerRoot
            try {
                & $McfpmCommand pack --input (Join-Path $producerRoot 'out') --type compiler.mcfpp.library --classifier mcfpp --output (Join-Path $versionRoot 'semantic-lib-1.0.0-mcfpp.zip') --register
                if ($LASTEXITCODE -ne 0) { throw 'Mcfpm pack failed' }
                $manifestText = (& $McfpmCommand manifest show --format json) -join "`n"
                if ($LASTEXITCODE -ne 0) { throw 'Mcfpm canonical manifest failed' }
                [IO.File]::WriteAllText((Join-Path $versionRoot 'semantic-lib-1.0.0.mcfpkg'), $manifestText.TrimEnd([char[]]"`r`n"), [Text.UTF8Encoding]::new($false))
            } finally { Pop-Location }
            $repositoryUri = [uri]::new($repositoryRoot.Replace('\', '/') + '/').AbsoluteUri
            $repositoryConfig = "`n[mcfpm]`ndefault-repository = `"fixture`"`n[mcfpm.repositories]`nfixture = `"$repositoryUri`"`n[mcfpm.bindings]`nfixture = `"fixture`"`n"
            [IO.File]::AppendAllText((Join-Path $consumerRoot 'mcfpp.toml'), $repositoryConfig)
            & $javaExecutable "-Duser.home=$installHome" '-jar' $CompilerJar add --project $consumerRoot fixture:semantic-lib@1.0.0
            if ($LASTEXITCODE -ne 0) { throw 'MCFPP dependency installation failed' }
            Copy-Item -LiteralPath (Join-Path $consumerRoot 'mcfpp.lock') -Destination (Join-Path $workRoot 'installed.lock')
            $archiveRoot = Join-Path $workRoot 'source-archive'
            New-Item -ItemType Directory -Path $archiveRoot -Force | Out-Null
            $sourcePath = [IO.Path]::GetFullPath((Join-Path $producerRoot 'src/library.mcfpp'))
            $archivePath = [IO.Path]::GetFullPath((Join-Path $archiveRoot 'library.mcfpp'))
            $workPrefix = $workRoot.TrimEnd([char[]]'\/') + [IO.Path]::DirectorySeparatorChar
            if (!$sourcePath.StartsWith($workPrefix, [StringComparison]::OrdinalIgnoreCase) -or
                !$archivePath.StartsWith($workPrefix, [StringComparison]::OrdinalIgnoreCase)) { throw 'Source archive must remain within WorkDirectory' }
            Move-Item -LiteralPath $sourcePath -Destination $archivePath
        }
        'consumer' {
            if (Test-Path -LiteralPath (Join-Path $producerRoot 'src/library.mcfpp')) { throw 'Archive producer source before consuming the installed artifact' }
            Invoke-Compiler (Join-Path $consumerRoot 'mcfpp.toml')
        }
        'verify' {
            $lock = Join-Path $consumerRoot 'mcfpp.lock'
            if ((Get-FileHash -LiteralPath $lock).Hash -ne (Get-FileHash -LiteralPath (Join-Path $workRoot 'installed.lock')).Hash) { throw 'Compiler changed the lock' }
            $output = Join-Path $consumerRoot 'out'
            $bodies = @(Get-ChildItem -LiteralPath $output -File -Recurse -Filter '*.mcfunction' | ForEach-Object { [IO.File]::ReadAllText($_.FullName) }) -join "`n"
            if (!$bodies.Contains('say installed-private-body')) { throw 'Imported private implementation is absent from output' }
            $resource = Join-Path $output 'Imports/data/fixture.package/tags/function/extra.json'
            if (!(Test-Path -LiteralPath $resource)) { throw 'Installed static resource is absent from output' }
            Write-Output 'Local install and compilation succeeded; producer source archived, private implementation and static resource present, lock unchanged.'
        }
    }
}
$savedJavaHome = $env:JAVA_HOME
try {
    if ($JavaHome21) { $env:JAVA_HOME = [IO.Path]::GetFullPath($JavaHome21) }
    $steps = if ($Step -eq 'all') { @('producer', 'install', 'consumer', 'verify') } else { @($Step) }
    foreach ($currentStep in $steps) { Invoke-ExampleStep $currentStep }
} finally { $env:JAVA_HOME = $savedJavaHome }

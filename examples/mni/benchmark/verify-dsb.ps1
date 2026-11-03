param(
    [string]$DsbJar = 'F:/Project_MCFPP/mcfpp-language-support/intellij-plugin/src/main/resources/dps/datapack-sandbox-cli.jar',
    [string]$JavaExe = 'F:/Java/jdk-25.0.4/bin/java.exe',
    [string]$Adapter = 'examples/mni/benchmark/DsbFunctionalCheck.java',
    [string]$CliEvidenceRoot = 'build/inheritance-mni/actual-examples-cli-check/attempt1',
    [ValidatePattern('^[A-Za-z0-9_-]+$')][string]$Attempt = 'attempt2',
    [string[]]$Cases = @()
)

$ErrorActionPreference = 'Stop'
$PSNativeCommandUseErrorActionPreference = $false
$repoRoot = (Resolve-Path -LiteralPath (Join-Path $PSScriptRoot '../../..')).Path

function Resolve-InputPath([string]$Path, [string]$Base) {
    if ([string]::IsNullOrWhiteSpace($Path)) { throw 'Empty path is not allowed.' }
    if (-not [IO.Path]::IsPathRooted($Path)) { $Path = Join-Path $Base $Path }
    return (Resolve-Path -LiteralPath $Path).Path
}

function Write-Utf8NoBom([string]$Path, [string]$Text) {
    $encoding = New-Object System.Text.UTF8Encoding($false)
    [IO.File]::WriteAllText($Path, $Text, $encoding)
}

function Get-FileSha256([string]$Path) {
    return (Get-FileHash -LiteralPath $Path -Algorithm SHA256).Hash.ToLowerInvariant()
}

function Get-TextSha256([string]$Text) {
    $sha = [Security.Cryptography.SHA256]::Create()
    try {
        $bytes = $sha.ComputeHash([Text.Encoding]::UTF8.GetBytes($Text))
        return (($bytes | ForEach-Object { $_.ToString('x2') }) -join '')
    } finally {
        $sha.Dispose()
    }
}

function Get-DirectoryAggregateHash([string]$Root) {
    $files = @(Get-ChildItem -LiteralPath $Root -Recurse -File | Sort-Object FullName)
    $text = New-Object System.Text.StringBuilder
    foreach ($file in $files) {
        $relative = $file.FullName.Substring($Root.Length).TrimStart([char[]]@('\', '/')).Replace('\', '/')
        [void]$text.Append((Get-FileSha256 $file.FullName))
        [void]$text.Append('  ')
        [void]$text.Append($relative)
        [void]$text.Append("`n")
    }
    return Get-TextSha256 $text.ToString()
}

function Get-FunctionPath([string]$PackRoot, [string]$FunctionId) {
    $colon = $FunctionId.IndexOf(':')
    if ($colon -le 0) { throw "Function id has no namespace: $FunctionId" }
    $namespace = $FunctionId.Substring(0, $colon)
    $path = $FunctionId.Substring($colon + 1)
    $functionRoot = Join-Path (Join-Path (Join-Path $PackRoot 'data') $namespace) 'function'
    return Join-Path $functionRoot ($path + '.mcfunction')
}

function Get-LineEnding([string]$Text) {
    if ($Text.Contains("`r`n")) { return "`r`n" }
    if ($Text.Contains("`n")) { return "`n" }
    return "`n"
}

function New-ProbeDiff([string]$RelativePath, [int]$InsertBefore, [string[]]$Added, [string]$Context) {
    $lines = [System.Collections.Generic.List[string]]::new()
    $lines.Add("--- a/$RelativePath")
    $lines.Add("+++ b/$RelativePath")
    $lines.Add("@@ -$InsertBefore,1 +$InsertBefore,$($Added.Count + 1) @@")
    foreach ($line in $Added) { $lines.Add("+$line") }
    $lines.Add(" $Context")
    return (($lines -join "`n") + "`n")
}

function Add-ObservationToTerminal {
    param(
        [string]$PackRoot,
        [string]$Namespace,
        [string]$FunctionId,
        [string[]]$ProbeCommands,
        [hashtable]$Visited,
        [System.Collections.Generic.List[object]]$ProbeRecords
    )

    if ($Visited.ContainsKey($FunctionId)) { return }
    $Visited[$FunctionId] = $true

    $path = Get-FunctionPath -PackRoot $PackRoot -FunctionId $FunctionId
    if (-not (Test-Path -LiteralPath $path)) { throw "Function file is missing: $path" }

    $text = [IO.File]::ReadAllText($path)
    $newline = Get-LineEnding $text
    $lines = [regex]::Split($text, "\r\n|\n|\r")

    $last = -1
    for ($index = $lines.Length - 1; $index -ge 0; $index--) {
        $candidate = $lines[$index].Trim()
        if ($candidate -ne '' -and -not $candidate.StartsWith('#')) {
            $last = $index
            break
        }
    }
    if ($last -lt 0) { throw "Function has no executable line: $FunctionId" }

    # The generated call graph ends in caller-frame pops, but score-mapping's main
    # continues through tail return helpers. Instrument the true terminal helpers,
    # never the non-final pop in main.
    $tailTargets = [System.Collections.Generic.List[string]]::new()
    for ($index = $last; $index -ge 0; $index--) {
        $candidate = $lines[$index].Trim()
        if ($candidate -eq '' -or $candidate.StartsWith('#')) { continue }
        if ($candidate -notmatch 'return\s+run\s+function') { break }
        $matches = [regex]::Matches($candidate, 'return\s+run\s+function\s+([A-Za-z0-9_.:/-]+)')
        if ($matches.Count -eq 0) { throw "Malformed tail return in $FunctionId at line $($index + 1)" }
        foreach ($match in $matches) { $tailTargets.Add($match.Groups[1].Value) }
    }

    if ($tailTargets.Count -gt 0) {
        foreach ($target in $tailTargets) {
            if (-not $target.StartsWith($Namespace + ':', [StringComparison]::Ordinal)) {
                throw "Tail continuation outside $Namespace in ${FunctionId}: $target"
            }
            Add-ObservationToTerminal -PackRoot $PackRoot -Namespace $Namespace `
                -FunctionId $target -ProbeCommands $ProbeCommands -Visited $Visited `
                -ProbeRecords $ProbeRecords
        }
        return
    }

    $popLine = 'data remove storage mcfpp:system stack_frame[0]'
    $pop = -1
    for ($index = $lines.Length - 1; $index -ge 0; $index--) {
        if ($lines[$index].Trim() -eq $popLine) {
            $pop = $index
            break
        }
    }
    if ($pop -lt 0) { throw "No genuine final caller-frame pop in $FunctionId" }

    $added = [System.Collections.Generic.List[string]]::new()
    $added.Add('# DSB OBSERVATION (probe-only): read the caller frame before its genuine final cleanup')
    foreach ($command in $ProbeCommands) { $added.Add($command) }

    $originalHash = Get-FileSha256 $path
    $rewritten = [System.Collections.Generic.List[string]]::new()
    for ($index = 0; $index -lt $lines.Length; $index++) {
        if ($index -eq $pop) {
            foreach ($line in $added) { $rewritten.Add($line) }
        }
        $rewritten.Add($lines[$index])
    }
    Write-Utf8NoBom -Path $path -Text ($rewritten -join $newline)
    $stagedHash = Get-FileSha256 $path

    $relative = $path.Substring($PackRoot.Length).TrimStart([char[]]@('\', '/')).Replace('\', '/')
    $ProbeRecords.Add([ordered]@{
        function = $FunctionId
        file = $relative
        originalSha256 = $originalHash
        stagedSha256 = $stagedHash
        insertBeforeLine = $pop + 1
        addedCommands = @($added)
        diff = (New-ProbeDiff -RelativePath $relative -InsertBefore ($pop + 1) `
            -Added @($added) -Context $lines[$pop])
    })
}

$DsbJar = Resolve-InputPath $DsbJar $repoRoot
$JavaExe = Resolve-InputPath $JavaExe $repoRoot
$Adapter = Resolve-InputPath $Adapter $repoRoot
$CliEvidenceRoot = Resolve-InputPath $CliEvidenceRoot $repoRoot
$cliResultPath = Join-Path $CliEvidenceRoot 'result.json'
if (-not (Test-Path -LiteralPath $cliResultPath)) { throw "CLI evidence result.json is missing: $cliResultPath" }

$evidence = Get-Content -LiteralPath $cliResultPath -Raw | ConvertFrom-Json
$cliCases = @($evidence.cases)
if ($cliCases.Count -ne 10) { throw "Expected 10 CLI evidence cases, found $($cliCases.Count)" }

$casesById = @{}
foreach ($cliCase in $cliCases) {
    $caseId = "$($cliCase.example)-$($cliCase.language)"
    if ($casesById.ContainsKey($caseId)) { throw "Duplicate CLI evidence case: $caseId" }
    $casesById[$caseId] = $cliCase
}

# Expected values are the literal values asserted by ObserveExample.java against
# the real emitted command files. Ray pose probes are extra observations added
# only to the staged copy; they do not implement or replace the ray algorithm.
$caseSpecs = @(
    [ordered]@{ Example = 'damage'; Expected = [ordered]@{ health = 70; low = 0; alias = 0 }; Ray = $false; Score = $false },
    [ordered]@{ Example = 'repeat'; Expected = [ordered]@{ total = 10; guarded = 8; tail = 9; nested = 22 }; Ray = $false; Score = $false },
    [ordered]@{ Example = 'nbt-reference'; Expected = [ordered]@{ normal = 70; aliased = 10; untouched = 10; indexed = 15 }; Ray = $false; Score = $false },
    [ordered]@{ Example = 'raycast'; Expected = [ordered]@{ continueHits = 4; continueLast = 3; afterContinue = 5; stopHits = 1; afterStop = 2 }; Ray = $true; Score = $false },
    [ordered]@{ Example = 'score-mapping'; Expected = [ordered]@{ loadedHealth = 7; loadedAlive = 1 }; Ray = $false; Score = $true }
)

# -Cases selects known case ids only. Validation happens before any output
# directory, copier, or manifest is created so a typo cannot leave partial evidence.
$allCaseIds = [System.Collections.Generic.List[string]]::new()
foreach ($spec in $caseSpecs) {
    foreach ($language in @('Kotlin', 'Java')) {
        $allCaseIds.Add("$($spec.Example)-$language")
    }
}
$requestedCases = @($Cases | Where-Object { -not [string]::IsNullOrWhiteSpace($_) })
$selectedCaseIds = [System.Collections.Generic.List[string]]::new()
if ($requestedCases.Count -eq 0) {
    foreach ($caseId in $allCaseIds) { $selectedCaseIds.Add($caseId) }
} else {
    foreach ($requested in $requestedCases) {
        $requested = $requested.Trim()
        $matched = @($allCaseIds | Where-Object { $_ -ieq $requested })
        if ($matched.Count -eq 0) {
            throw "Unknown -Cases value '$requested'. Valid cases: $($allCaseIds -join ', ')"
        }
        if (@($selectedCaseIds | Where-Object { $_ -ieq $matched[0] }).Count -gt 0) {
            throw "Duplicate -Cases value '$requested'."
        }
        $selectedCaseIds.Add([string]$matched[0])
    }
}
foreach ($caseId in $casesById.Keys) {
    if ($allCaseIds -notcontains $caseId) { throw "CLI evidence has unhandled case: $caseId" }
}
foreach ($caseId in $allCaseIds) {
    if (-not $casesById.ContainsKey($caseId)) { throw "CLI evidence is missing case: $caseId" }
}

$outputRoot = Join-Path $repoRoot (Join-Path 'build/inheritance-mni/dsb-check' $Attempt)
if (Test-Path -LiteralPath $outputRoot) {
    throw "Refusing to reuse existing DSB evidence directory: $outputRoot"
}
$packsRoot = Join-Path $outputRoot 'packs'
$manifestRoot = Join-Path $outputRoot 'manifests'
$reportRoot = Join-Path $outputRoot 'reports'
$diffRoot = Join-Path $outputRoot 'probe-diffs'
New-Item -ItemType Directory -Force -Path $packsRoot, $manifestRoot, $reportRoot, $diffRoot | Out-Null

$objectives = @('mcfpp_default', 'mcfpp_temp', 'mcfpp_boolean', 'health_hp', 'state_alive')
$objectiveCommands = @($objectives | ForEach-Object { "scoreboard objectives add $_ dummy" })
$startedUtc = [DateTime]::UtcNow.ToString('o')
$caseRecords = [System.Collections.Generic.List[object]]::new()
$manifestPaths = [System.Collections.Generic.List[string]]::new()

foreach ($spec in $caseSpecs) {
    foreach ($language in @('Kotlin', 'Java')) {
        $caseId = "$($spec.Example)-$language"
        if ($selectedCaseIds -notcontains $caseId) { continue }
        if (-not $casesById.ContainsKey($caseId)) { throw "CLI evidence is missing case: $caseId" }
        $cliCase = $casesById[$caseId]
        $namespace = [string]$cliCase.namespace

        $dataRoot = [string]$cliCase.dataRoot
        if (-not (Test-Path -LiteralPath $dataRoot)) {
            $windowsPrefix = 'F:/Project_MCFPP/MCFPP/'
            if ($dataRoot.StartsWith($windowsPrefix, [StringComparison]::OrdinalIgnoreCase)) {
                $dataRoot = Join-Path $repoRoot $dataRoot.Substring($windowsPrefix.Length)
            }
        }
        $dataRoot = (Resolve-Path -LiteralPath $dataRoot).Path
        $sourcePackRoot = Split-Path -Parent $dataRoot
        $sourceMcmeta = Join-Path $sourcePackRoot 'pack.mcmeta'
        if (-not (Test-Path -LiteralPath $sourceMcmeta)) {
            throw "Source pack is missing pack.mcmeta: $sourcePackRoot"
        }

        $sourceImportsRoot = Join-Path (Split-Path -Parent $sourcePackRoot) 'Imports'
        if (-not (Test-Path -LiteralPath $sourceImportsRoot)) {
            throw "Source Imports pack is missing: $sourceImportsRoot"
        }
        $sourceImportsMcmeta = Join-Path $sourceImportsRoot 'pack.mcmeta'
        if (-not (Test-Path -LiteralPath $sourceImportsMcmeta)) {
            throw "Source Imports pack is missing pack.mcmeta: $sourceImportsRoot"
        }

        $sourceAggregate = Get-DirectoryAggregateHash $dataRoot
        $sourceMcmetaHash = Get-FileSha256 $sourceMcmeta
        $sourceImportsAggregate = Get-DirectoryAggregateHash (Join-Path $sourceImportsRoot 'data')
        $sourceImportsMcmetaHash = Get-FileSha256 $sourceImportsMcmeta

        $casePackRoot = Join-Path (Join-Path $packsRoot $caseId) 'pack'
        $caseImportsRoot = Join-Path (Join-Path $packsRoot $caseId) 'Imports'
        New-Item -ItemType Directory -Force -Path (Split-Path -Parent $casePackRoot) | Out-Null
        Copy-Item -LiteralPath $sourcePackRoot -Destination $casePackRoot -Recurse -Force
        Copy-Item -LiteralPath $sourceImportsRoot -Destination $caseImportsRoot -Recurse -Force

        $probeCommands = [System.Collections.Generic.List[string]]::new()
        foreach ($key in $spec.Expected.Keys) {
            if ($spec.Example -eq 'damage') {
                $holder = @{ health = '#normal'; low = '#low'; alias = '#alias' }[$key]
                $probeCommands.Add("execute store result storage verification:dsb $key int 1 run scoreboard players get $holder hp")
            } elseif ($spec.Example -eq 'nbt-reference') {
                $nbtPath = @{ normal = 'normal.hp'; aliased = 'alias.hp'; untouched = 'values[0]'; indexed = 'values[1]' }[$key]
                $probeCommands.Add("data modify storage verification:dsb $key set from storage example:health $nbtPath")
            } else {
                $probeCommands.Add("data modify storage verification:dsb $key set from storage mcfpp:system stack_frame[0].$key")
            }
        }
        if ($spec.Ray) {
            $probeCommands.Add('execute if block ~ ~ ~ minecraft:stone run data modify storage verification:dsb poseOriginStone set value 1b')
            $probeCommands.Add('execute positioned ^ ^ ^0.5 if block ~ ~ ~ minecraft:stone run data modify storage verification:dsb poseHalfStone set value 1b')
            $probeCommands.Add('execute positioned ^ ^ ^1.5 if block ~ ~ ~ minecraft:stone run data modify storage verification:dsb poseOneHalfStone set value 1b')
            $probeCommands.Add('execute positioned ^ ^ ^2 if block ~ ~ ~ minecraft:air run data modify storage verification:dsb poseTwoAir set value 1b')
        }

        $probeRecords = [System.Collections.Generic.List[object]]::new()
        Add-ObservationToTerminal -PackRoot $casePackRoot -Namespace $namespace `
            -FunctionId ($namespace + ':main') -ProbeCommands @($probeCommands) `
            -Visited @{} -ProbeRecords $probeRecords
        if ($probeRecords.Count -eq 0) { throw "No terminal caller-frame cleanup instrumented for $caseId" }

        $probeDiffPath = Join-Path $diffRoot ($caseId + '.diff')
        $probeDiffText = (($probeRecords | ForEach-Object { $_.diff }) -join '')
        Write-Utf8NoBom -Path $probeDiffPath -Text $probeDiffText
        $stagedAggregate = Get-DirectoryAggregateHash (Join-Path $casePackRoot 'data')
        $stagedImportsAggregate = Get-DirectoryAggregateHash (Join-Path $caseImportsRoot 'data')

        $invocation = "execute in minecraft:overworld positioned 0.5 64.0 0.5 rotated 90 0 run function $($namespace):main"
        $assertions = [System.Collections.Generic.List[object]]::new()
        foreach ($key in $spec.Expected.Keys) {
            $assertions.Add([ordered]@{ storage = [ordered]@{ id = 'verification:dsb'; path = $key; equals = $spec.Expected[$key] } })
        }
        if ($spec.Ray) {
            foreach ($key in @('poseOriginStone', 'poseHalfStone', 'poseOneHalfStone', 'poseTwoAir')) {
                $assertions.Add([ordered]@{ storage = [ordered]@{ id = 'verification:dsb'; path = $key; equals = 1 } })
            }
            $assertions.Add([ordered]@{ block = [ordered]@{ pos = @(0, 64, 0); id = 'minecraft:stone' } })
            $assertions.Add([ordered]@{ block = [ordered]@{ pos = @(-1, 64, 0); id = 'minecraft:stone' } })
        }
        if ($spec.Score) {
            $assertions.Add([ordered]@{ score = [ordered]@{ target = '#scores'; objective = 'health_hp'; equals = 7 } })
            $assertions.Add([ordered]@{ score = [ordered]@{ target = '#scores'; objective = 'state_alive'; equals = 1 } })
        }
        $assertions.Add([ordered]@{ storage = [ordered]@{ id = 'mcfpp:system'; path = 'stack_frame'; equals = @() } })

        $world = [ordered]@{}
        if ($spec.Ray) {
            $world.blocks = @(
                [ordered]@{ pos = @(0, 64, 0); id = 'minecraft:stone' },
                [ordered]@{ pos = @(-1, 64, 0); id = 'minecraft:stone' }
            )
        }
        $manifest = [ordered]@{
            version = '26.3'
            unsupported = 'error'
            failOnMissingResources = $true
            packs = @("../packs/$caseId/pack", "../packs/$caseId/Imports")
            world = $world
            steps = @(
                [ordered]@{ commands = $objectiveCommands },
                [ordered]@{ load = $true },
                [ordered]@{ command = $invocation }
            )
            assertions = @($assertions)
        }

        $manifestPath = Join-Path $manifestRoot ($caseId + '.dps.json')
        Write-Utf8NoBom -Path $manifestPath -Text (($manifest | ConvertTo-Json -Depth 20) + "`n")
        $manifestPaths.Add($manifestPath)

        $caseRecords.Add([ordered]@{
            case = $caseId
            example = $spec.Example
            language = $language
            namespace = $namespace
            entry = ($namespace + ':main')
            invocation = $invocation
            sourceDataRoot = $dataRoot
            sourcePackRoot = $sourcePackRoot
            sourcePackMcmetaSha256 = $sourceMcmetaHash
            sourceImportsRoot = $sourceImportsRoot
            sourceImportsAggregateSha256 = $sourceImportsAggregate
            sourceImportsMcmetaSha256 = $sourceImportsMcmetaHash
            sourceAggregateSha256 = $sourceAggregate
            stagedAggregateSha256 = $stagedAggregate
            stagedImportsAggregateSha256 = $stagedImportsAggregate
            stagedPack = $casePackRoot
            stagedImports = $caseImportsRoot
            manifest = $manifestPath
            probeDiff = $probeDiffPath
            probeFiles = @($probeRecords | ForEach-Object {
                [ordered]@{
                    function = $_.function
                    file = $_.file
                    originalSha256 = $_.originalSha256
                    stagedSha256 = $_.stagedSha256
                    insertBeforeLine = $_.insertBeforeLine
                    addedCommands = @($_.addedCommands)
                }
            })
            expected = $spec.Expected
        })
    }
}

if ($manifestPaths.Count -ne $selectedCaseIds.Count -or $caseRecords.Count -ne $selectedCaseIds.Count) {
    throw "Selected cases were not all handled: selected $($selectedCaseIds.Count), manifests $($manifestPaths.Count), records $($caseRecords.Count)"
}

$allReportPath = Join-Path $reportRoot 'all-report.json'
$dsbLogPath = Join-Path $outputRoot 'dsb-check.log'
$javaArgs = @('-cp', $DsbJar, $Adapter, '--output', $allReportPath) + @($manifestPaths)
$commandText = (($javaArgs | ForEach-Object { if ($_ -match '\s') { '"' + $_ + '"' } else { $_ } }) -join ' ')
$exitCode = 1
try {
    & $JavaExe @javaArgs *> $dsbLogPath
    $exitCode = $LASTEXITCODE
} catch {
    $exitCode = 1
    Write-Utf8NoBom -Path $dsbLogPath -Text ($_.Exception.ToString() + "`n")
}

$reportEntries = @()
if (Test-Path -LiteralPath $allReportPath) {
    $reportEntries = @(Get-Content -LiteralPath $allReportPath -Raw | ConvertFrom-Json)
}

function Find-ReportEntry([object[]]$Entries, [string]$ManifestPath) {
    $full = [IO.Path]::GetFullPath($ManifestPath)
    foreach ($entry in $Entries) {
        $candidate = [string]$entry.path
        try { $candidateFull = [IO.Path]::GetFullPath($candidate) } catch { $candidateFull = $candidate }
        if ($candidateFull -ieq $full) { return $entry }
    }
    $leaf = Split-Path -Leaf $ManifestPath
    foreach ($entry in $Entries) {
        if ((Split-Path -Leaf ([string]$entry.path)) -ieq $leaf) { return $entry }
    }
    return $null
}

$allPassed = $true
foreach ($record in $caseRecords) {
    $entry = Find-ReportEntry -Entries $reportEntries -ManifestPath $record.manifest
    $messages = @()
    $diagnostics = @()
    $traceCount = 0
    $traceOff = ($null -ne $entry) -and ([string]$entry.commandTraceMode -eq 'OFF')
    if ($null -ne $entry) {
        $messages = @($entry.messages | Where-Object { $null -ne $_ })
        $diagnostics = @($entry.diagnostics | Where-Object { $null -ne $_ })
        if ($null -ne $entry.attempts) {
            foreach ($manifestAttempt in @($entry.attempts)) {
                $diagnostics += @($manifestAttempt.diagnostics | Where-Object { $null -ne $_ })
                if ($null -ne $manifestAttempt.traceCount) {
                    $traceCount += [int]$manifestAttempt.traceCount
                    if ([int]$manifestAttempt.traceCount -ne 0) { $traceOff = $false }
                }
            }
        }
    }
    $passed = ($null -ne $entry) -and [bool]$entry.passed -and $messages.Count -eq 0 -and `
        $diagnostics.Count -eq 0 -and $traceOff
    if (-not $passed) { $allPassed = $false }

    $caseReportPath = Join-Path $reportRoot ($record.case + '.report.json')
    if ($null -ne $entry) {
        Write-Utf8NoBom -Path $caseReportPath -Text ((ConvertTo-Json -InputObject $entry -Depth 40) + "`n")
    }
    $record['passed'] = $passed
    $record['messages'] = @($messages)
    $record['diagnostics'] = @($diagnostics)
    $record['results'] = if ($null -ne $entry) { $entry.results } else { $null }
    $record['snapshot'] = if ($null -ne $entry) { $entry.snapshot } else { $null }
    $record['resourceSummary'] = if ($null -ne $entry) { $entry.resourceSummary } else { $null }
    $record['traceMode'] = if ($null -ne $entry) { [string]$entry.commandTraceMode } else { $null }
    $record['traceCount'] = $traceCount
    $record['report'] = $caseReportPath
}

if ($exitCode -ne 0) { $allPassed = $false }

$resultPath = Join-Path $outputRoot 'result.json'
$result = [ordered]@{
    schema = 'mcfpp-dsb-functional-check/1'
    attempt = $Attempt
    startedUtc = $startedUtc
    endedUtc = [DateTime]::UtcNow.ToString('o')
    dsbJar = $DsbJar
    dsbJarSha256 = (Get-FileSha256 $DsbJar)
    adapter = $Adapter
    adapterSha256 = (Get-FileSha256 $Adapter)
    commandTraceMode = 'OFF'
    javaExe = $JavaExe
    cliEvidenceRoot = $CliEvidenceRoot
    cliResult = $cliResultPath
    cliResultSha256 = (Get-FileSha256 $cliResultPath)
    requestedCases = @($requestedCases)
    selectedCases = @($selectedCaseIds)
    command = $commandText
    exitCode = $exitCode
    report = $allReportPath
    log = $dsbLogPath
    allPassed = $allPassed
    cases = @($caseRecords)
}
Write-Utf8NoBom -Path $resultPath -Text (($result | ConvertTo-Json -Depth 40) + "`n")

if (-not $allPassed) {
    $failed = @($caseRecords | Where-Object { -not $_.passed } | ForEach-Object { $_.case })
    throw "DSB functional check failed (exit $exitCode); failed cases: $($failed -join ', '). Report: $allReportPath"
}

Write-Output "DSB functional check passed: $($caseRecords.Count) cases. Report: $allReportPath"
Write-Output "Evidence: $resultPath"

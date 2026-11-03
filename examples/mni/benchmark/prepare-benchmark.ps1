<#
.SYNOPSIS
    Wrap an already generated Minecraft 26.3 data pack with a fixed-load
    benchmark harness.

.DESCRIPTION
    This script does not compile MCFPP, run a server, or measure anything.
    It copies every source file unchanged, then merges the existing
    #minecraft:load and #minecraft:tick registrations by appending to their
    JSON value lists, and writes an isolated mcfpp_benchmark namespace.

    The entry function is supplied by the caller and is never generated or
    edited. The same wrapper configuration (load, warmup ticks, measured
    ticks, fixed position/rotation/dimension) must be used for every variant
    that is compared.

    Minecraft 26.3 uses data pack format 121.0. A source pack whose
    pack.mcmeta does not target 121.x is rejected instead of being silently
    wrapped for a different game version.

.PARAMETER SourcePack
    Directory that directly contains pack.mcmeta and data/. This is the
    generated pack directory, not its parent targetPath (which may also
    contain Imports/).

.PARAMETER EntryFunction
    Full resource id of the function to execute, for example
    example.comparison:raycontinuecase. The function must exist at
    data/<namespace>/function/<path>.mcfunction in -SourcePack.

.PARAMETER Output
    New directory that will contain the wrapped pack. If it already exists
    and is non-empty, -Force is required and the directory must carry the
    mcfpp-benchmark.generated.json marker written by this script.

.PARAMETER Kernel
    Fixed scenario metadata: damage, repeat, raycontinue, raystop, or score.
    It selects the reset/world function and does not alter the entry function.

.PARAMETER Repetitions
    Number of independent measured runs expected for this configuration.
    The pack counts completed runs and reports the target; the operator
    starts one run at a time so that each profile can be saved separately.

.PARAMETER WarmupTicks
    Warmup ticks executed before the harness arms the measured phase.
    Warmup ticks are not part of the measured counters.

.PARAMETER MeasuredTicks
    Measured ticks per run. The harness stops itself after exactly this
    many measured ticks. The default 100 is nominal 5 s at 20 TPS. This is
    not a profile length guarantee: Minecraft 26.3 stops a /perf profile
    after 10 wall-clock seconds, so a lagging 100-tick run can still be
    capped. Read the actual tick span from the profile CSV.

.PARAMETER Load
    Entry-function invocations per measured tick. This is the fixed per-tick
    load, not the number of measurement runs.

.PARAMETER ManualProfile
    Do not emit /perf start and /perf stop commands. Use this when the
    server cannot run /perf from functions (function-permission-level < 4).
    The operator then starts and stops /perf manually around measure/finish.

.PARAMETER Force
    Replace an existing non-empty output only when it contains the marker
    written by this script. Unmarked directories are never deleted.

.EXAMPLE
    powershell -NoProfile -File examples/mni/benchmark/prepare-benchmark.ps1 `
      -SourcePack "examples/mni/comparison/build/<actual-pack>" `
      -EntryFunction "example.comparison:raycontinuecase" `
      -Output "build/game-benchmark/packs/mni-raycontinue" `
      -Kernel raycontinue -Repetitions 3 -WarmupTicks 200 -MeasuredTicks 100 -Load 1
#>
[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)][string]$SourcePack,
    [Parameter(Mandatory = $true)][string]$EntryFunction,
    [Parameter(Mandatory = $true)][string]$Output,
    [Parameter(Mandatory = $true)]
    [ValidateSet('damage', 'repeat', 'raycontinue', 'raystop', 'score')]
    [string]$Kernel,
    [ValidateRange(1, 1000)][int]$Repetitions = 3,
    [ValidateRange(0, 1000000)][int]$WarmupTicks = 200,
    [ValidateRange(1, 1000000)][int]$MeasuredTicks = 100,
    [ValidateRange(1, 1000)][int]$Load = 1,
    [switch]$ManualProfile,
    [switch]$Force
)

$ErrorActionPreference = 'Stop'
$Kernel = $Kernel.ToLowerInvariant()

function Resolve-FullPath {
    param([string]$Path)
    return $ExecutionContext.SessionState.Path.GetUnresolvedProviderPathFromPSPath($Path)
}

function Remove-TrailingSeparator {
    param([string]$Path)
    while ($Path.Length -gt 1 -and ($Path.EndsWith('\') -or $Path.EndsWith('/'))) {
        $Path = $Path.Substring(0, $Path.Length - 1)
    }
    return $Path
}

function Join-PathParts {
    param([string[]]$Parts)
    return [System.IO.Path]::Combine($Parts)
}

function Read-JsonFile {
    param([string]$Path)
    $raw = [System.IO.File]::ReadAllText($Path, [System.Text.Encoding]::UTF8)
    if ([string]::IsNullOrWhiteSpace($raw)) {
        throw "JSON file is empty: $Path"
    }
    try {
        return $raw | ConvertFrom-Json
    }
    catch {
        throw "Cannot parse JSON file $Path : $($_.Exception.Message)"
    }
}

function Write-Utf8NoBom {
    param([string]$Path, [string]$Content)
    $parent = [System.IO.Path]::GetDirectoryName($Path)
    if (-not [string]::IsNullOrWhiteSpace($parent) -and -not (Test-Path -LiteralPath $parent -PathType Container)) {
        New-Item -ItemType Directory -Path $parent -Force | Out-Null
    }
    if (-not $Content.EndsWith("`n")) {
        $Content += "`n"
    }
    $encoding = New-Object System.Text.UTF8Encoding($false)
    [System.IO.File]::WriteAllText($Path, $Content, $encoding)
}

function Get-FormatMajor {
    param($Value)
    if ($null -eq $Value) { return $null }
    if ($Value -is [System.Array]) {
        if ($Value.Length -lt 1) { throw 'pack.mcmeta format array is empty' }
        return [int]$Value[0]
    }
    return [int]$Value
}

function Get-PackFormat {
    param($Meta)
    if ($null -eq $Meta) { throw 'pack.mcmeta has no root object' }
    $packProp = $Meta.PSObject.Properties['pack']
    if ($null -eq $packProp -or $null -eq $packProp.Value) {
        throw 'pack.mcmeta has no "pack" object'
    }
    $pack = $packProp.Value

    $minProp = $pack.PSObject.Properties['min_format']
    $maxProp = $pack.PSObject.Properties['max_format']
    $plainProp = $pack.PSObject.Properties['pack_format']

    if ($null -ne $minProp -or $null -ne $maxProp) {
        if ($null -eq $minProp -or $null -eq $maxProp) {
            throw 'pack.mcmeta must contain both min_format and max_format'
        }
        $min = Get-FormatMajor $minProp.Value
        $max = Get-FormatMajor $maxProp.Value
        $text = "min_format=$min, max_format=$max"
        if ($min -le 121 -and $max -ge 121) {
            return [pscustomobject]@{ Major = 121; Text = $text }
        }
        return [pscustomobject]@{ Major = $min; Text = $text }
    }

    if ($null -ne $plainProp) {
        $plain = Get-FormatMajor $plainProp.Value
        return [pscustomobject]@{ Major = $plain; Text = "pack_format=$plain" }
    }

    throw 'pack.mcmeta has neither min_format/max_format nor pack_format'
}

function Test-ContainsTagValue {
    param($Values, [string]$Id)
    foreach ($value in @($Values)) {
        if ($value -is [string]) {
            if ([string]::Equals($value, $Id, [System.StringComparison]::Ordinal)) {
                return $true
            }
        }
        elseif ($null -ne $value) {
            $idProp = $value.PSObject.Properties['id']
            if ($null -ne $idProp -and [string]::Equals([string]$idProp.Value, $Id, [System.StringComparison]::Ordinal)) {
                return $true
            }
        }
    }
    return $false
}

function Merge-FunctionTag {
    param([string]$TagPath, [string]$FunctionId)
    $values = @()
    $ordered = [ordered]@{}

    if (Test-Path -LiteralPath $TagPath -PathType Leaf) {
        $tag = Read-JsonFile $TagPath
        if ($null -eq $tag) { throw "Tag JSON is empty: $TagPath" }
        if ($tag -is [System.Array] -or $tag -is [string]) {
            throw "Function tag JSON root must be an object: $TagPath"
        }
        $valuesProp = $tag.PSObject.Properties['values']
        if ($null -ne $valuesProp) {
            $values = @($valuesProp.Value | Where-Object { $null -ne $_ })
        }
        foreach ($property in $tag.PSObject.Properties) {
            $ordered[$property.Name] = $property.Value
        }
    }

    if (-not (Test-ContainsTagValue $values $FunctionId)) {
        $values = @($values + $FunctionId)
    }
    $ordered['values'] = @($values)

    $json = $ordered | ConvertTo-Json -Depth 32
    Write-Utf8NoBom $TagPath $json
}

try {
    $sourceFull = Remove-TrailingSeparator (Resolve-FullPath $SourcePack)
    $outputFull = Remove-TrailingSeparator (Resolve-FullPath $Output)
    $currentDir = Remove-TrailingSeparator (Resolve-FullPath (Get-Location).Path)

    if (-not (Test-Path -LiteralPath $sourceFull -PathType Container)) {
        throw "SourcePack does not exist or is not a directory: $sourceFull"
    }

    $sourceMeta = Join-PathParts @($sourceFull, 'pack.mcmeta')
    $sourceData = Join-PathParts @($sourceFull, 'data')
    if (-not (Test-Path -LiteralPath $sourceMeta -PathType Leaf)) {
        throw "SourcePack is not a data pack root (missing pack.mcmeta): $sourceFull"
    }
    if (-not (Test-Path -LiteralPath $sourceData -PathType Container)) {
        throw "SourcePack is not a data pack root (missing data/): $sourceFull"
    }

    $packFormat = Get-PackFormat (Read-JsonFile $sourceMeta)
    if ($packFormat.Major -ne 121) {
        throw "Source pack targets data format '$($packFormat.Text)', but Minecraft 26.3 requires data format 121.0. Rebuild the source pack with version 26.3."
    }

    if ($EntryFunction -cnotmatch '^[a-z0-9_.-]+:[a-z0-9_./-]+$') {
        throw "EntryFunction is not a full lower-case resource id: $EntryFunction"
    }
    $entryParts = $EntryFunction.Split(':', 2)
    $entryNamespace = $entryParts[0]
    $entryPath = $entryParts[1]
    if ($entryNamespace -eq '.' -or $entryNamespace -eq '..') {
        throw "EntryFunction namespace may not be '.' or '..': $EntryFunction"
    }
    if ($entryPath.StartsWith('/') -or $entryPath.EndsWith('/') -or $entryPath.Contains('//')) {
        throw "EntryFunction path must not start/end with '/' or contain '//': $EntryFunction"
    }
    if (($entryPath.Split('/') -contains '..') -or ($entryPath.Split('/') -contains '.')) {
        throw "EntryFunction path may not contain '.' or '..' path segments: $EntryFunction"
    }
    $entryFile = Join-PathParts @($sourceData, $entryNamespace, 'function', ($entryPath + '.mcfunction'))
    if (-not (Test-Path -LiteralPath $entryFile -PathType Leaf)) {
        throw "EntryFunction '$EntryFunction' is not present in SourcePack. Expected file: $entryFile"
    }

    $sourceBenchmarkDir = Join-PathParts @($sourceData, 'mcfpp_benchmark')
    if (Test-Path -LiteralPath $sourceBenchmarkDir) {
        throw "SourcePack already contains data/mcfpp_benchmark. Refusing to overwrite it; wrap the unwrapped source pack instead."
    }

    $root = [System.IO.Path]::GetPathRoot($outputFull)
    if ([string]::IsNullOrWhiteSpace($root) -or $outputFull -eq (Remove-TrailingSeparator $root)) {
        throw "Refusing to use a filesystem root as Output: $outputFull"
    }
    if ($outputFull -eq $currentDir) {
        throw "Refusing to use the current working directory as Output: $outputFull"
    }
    if ($outputFull -eq $sourceFull) {
        throw 'Output and SourcePack must be different directories'
    }
    if ($outputFull.StartsWith($sourceFull + [System.IO.Path]::DirectorySeparatorChar, [System.StringComparison]::OrdinalIgnoreCase)) {
        throw 'Output must not be inside SourcePack'
    }
    if ($sourceFull.StartsWith($outputFull + [System.IO.Path]::DirectorySeparatorChar, [System.StringComparison]::OrdinalIgnoreCase)) {
        throw 'SourcePack must not be inside Output'
    }

    if (Test-Path -LiteralPath $outputFull) {
        $outputItem = Get-Item -LiteralPath $outputFull -Force
        if (-not $outputItem.PSIsContainer) {
            throw "Output exists and is not a directory: $outputFull"
        }
        if (($outputItem.Attributes -band [System.IO.FileAttributes]::ReparsePoint) -ne 0) {
            throw "Output is a reparse point/symlink; refusing to write through it: $outputFull"
        }
        $existing = @(Get-ChildItem -LiteralPath $outputFull -Force)
        if ($existing.Count -gt 0) {
            if (-not $Force) {
                throw "Output already exists and is not empty: $outputFull. Pass -Force only for an output previously created by this script."
            }
            $markerPath = Join-PathParts @($outputFull, 'mcfpp-benchmark.generated.json')
            if (-not (Test-Path -LiteralPath $markerPath -PathType Leaf)) {
                throw "Refusing to delete unmarked Output: $outputFull. Delete it manually if it is really disposable."
            }
            $oldMarker = Read-JsonFile $markerPath
            if ($oldMarker.generatedBy -ne 'prepare-benchmark.ps1') {
                throw "Refusing to delete Output without the prepare-benchmark.ps1 marker: $outputFull"
            }
            Remove-Item -LiteralPath $outputFull -Recurse -Force
        }
    }
    if (-not (Test-Path -LiteralPath $outputFull -PathType Container)) {
        New-Item -ItemType Directory -Path $outputFull -Force | Out-Null
    }

    Get-ChildItem -LiteralPath $sourceFull -Force | ForEach-Object {
        Copy-Item -LiteralPath $_.FullName -Destination $outputFull -Recurse -Force
    }

    $outputData = Join-PathParts @($outputFull, 'data')
    $benchmarkFunctionDir = Join-PathParts @($outputData, 'mcfpp_benchmark', 'function')
    $minecraftTagDir = Join-PathParts @($outputData, 'minecraft', 'tags', 'function')
    New-Item -ItemType Directory -Path $benchmarkFunctionDir -Force | Out-Null
    New-Item -ItemType Directory -Path $minecraftTagDir -Force | Out-Null

    $runEntryLines = @()
    for ($i = 0; $i -lt $Load; $i++) {
        $runEntryLines += "execute in minecraft:overworld positioned 0.5 64.0 0.5 rotated 90 0 run function $EntryFunction"
    }

    $worldLines = switch ($Kernel) {
        'raycontinue' {
            @(
                '# Dedicated benchmark world only: clear the sampled volume and place the two stones.',
                'execute in minecraft:overworld run forceload add 0 0',
                'execute in minecraft:overworld run forceload add -1 0',
                'execute in minecraft:overworld run fill -2 64 -1 1 64 1 minecraft:air',
                'execute in minecraft:overworld run setblock 0 64 0 minecraft:stone',
                'execute in minecraft:overworld run setblock -1 64 0 minecraft:stone'
            )
            break
        }
        'raystop' {
            @(
                '# Dedicated benchmark world only: clear the sampled volume and place the two stones.',
                'execute in minecraft:overworld run forceload add 0 0',
                'execute in minecraft:overworld run forceload add -1 0',
                'execute in minecraft:overworld run fill -2 64 -1 1 64 1 minecraft:air',
                'execute in minecraft:overworld run setblock 0 64 0 minecraft:stone',
                'execute in minecraft:overworld run setblock -1 64 0 minecraft:stone'
            )
            break
        }
        'score' {
            @(
                '# ScoreMapping example state: fixed holder #scores, objectives health_hp/state_alive.',
                'scoreboard objectives add health_hp dummy',
                'scoreboard objectives add state_alive dummy',
                'scoreboard players set #scores health_hp 0',
                'scoreboard players set #scores state_alive 0'
            )
            break
        }
        default {
            @('# This kernel requires no external world or scoreboard state.')
        }
    }
    $worldLines = @($worldLines)

    $manualStartComment = '# Manual profile mode: operator starts /perf after warmup, immediately before /function mcfpp_benchmark:measure (never during warmup).'
    $manualStopComment = '# Manual profile mode: run /perf stop after the completion message (or keep the zip if /perf already stopped at the 10 s cap).'
    $perfStartLine = if ($ManualProfile) { $manualStartComment } else { 'perf start' }
    $perfStopLine = if ($ManualProfile) { $manualStopComment } else { 'perf stop' }
    $perfStopGuard = if ($ManualProfile) {
        @($manualStopComment)
    }
    else {
        @(
            'execute if score #phase mcfpp_benchmark matches 2 run perf stop',
            'execute if score #phase mcfpp_benchmark matches 4 run perf stop'
        )
    }
    $measureHint = if ($ManualProfile) {
        'start /perf start now, then immediately run /function mcfpp_benchmark:measure (do not profile warmup)'
    }
    else {
        'run /function mcfpp_benchmark:measure (it starts /perf automatically)'
    }
    $finishHint = if ($ManualProfile) {
        'run /perf stop, then /function mcfpp_benchmark:report; if /perf already stopped at the 10 s cap, preserve that zip'
    }
    else {
        'run /function mcfpp_benchmark:report'
    }
    $baselineHint = if ($ManualProfile) {
        'run /perf stop now; if /perf already stopped at the 10 s cap, preserve that zip.'
    }
    else {
        'Use the new /perf zip as the no-workload reference.'
    }

    $loadContent = @'
# Called from #minecraft:load. Initializes the objective and leaves every run stopped.
function mcfpp_benchmark:setup
'@

    $setupContent = @'
# Creates/resets the benchmark objective and stops any active run.
# On a second /reload the objective add logs "already exists"; the counters are still reset.
scoreboard objectives add mcfpp_benchmark dummy
scoreboard players set #phase mcfpp_benchmark 0
scoreboard players set #accepted mcfpp_benchmark 0
scoreboard players set #warmup_ticks mcfpp_benchmark __WARMUP__
scoreboard players set #measured_ticks mcfpp_benchmark __MEASURED__
scoreboard players set #load mcfpp_benchmark __LOAD__
scoreboard players set #runs_target mcfpp_benchmark __REPS__
scoreboard players set #runs_done mcfpp_benchmark 0
scoreboard players set #remaining mcfpp_benchmark 0
scoreboard players set #ticks mcfpp_benchmark 0
scoreboard players set #invocations mcfpp_benchmark 0
scoreboard players set #last_ticks mcfpp_benchmark 0
scoreboard players set #last_invocations mcfpp_benchmark 0
scoreboard players set #done mcfpp_benchmark 0
scoreboard players set #gametime_start mcfpp_benchmark 0
scoreboard players set #gametime_end mcfpp_benchmark 0
scoreboard players set #gametime_span mcfpp_benchmark 0
'@

    $startContent = @'
# Starts one run the first time; refuses to overlap an active run.
scoreboard players set #accepted mcfpp_benchmark 0
execute if score #phase mcfpp_benchmark matches 0 run function mcfpp_benchmark:start_impl
execute if score #accepted mcfpp_benchmark matches 0 run tellraw @a [{"text":"[MCFPP benchmark] ","color":"gold"},{"text":"start ignored: a run is already active, or the pack is not initialized.","color":"red"}]
'@

    $startImplContent = @'
scoreboard players set #accepted mcfpp_benchmark 1
scoreboard players set #ticks mcfpp_benchmark 0
scoreboard players set #invocations mcfpp_benchmark 0
scoreboard players set #last_ticks mcfpp_benchmark 0
scoreboard players set #last_invocations mcfpp_benchmark 0
scoreboard players set #done mcfpp_benchmark 0
scoreboard players set #gametime_start mcfpp_benchmark 0
scoreboard players set #gametime_end mcfpp_benchmark 0
scoreboard players set #gametime_span mcfpp_benchmark 0
function mcfpp_benchmark:reset_kernel
scoreboard players operation #remaining mcfpp_benchmark = #warmup_ticks mcfpp_benchmark
scoreboard players set #phase mcfpp_benchmark 1
tellraw @a [{"text":"[MCFPP benchmark] ","color":"gold"},{"text":"kernel=__KERNEL__ load=__LOAD__ warmup=__WARMUP__ ticks; measured=__MEASURED__ ticks per run."}]
execute if score #warmup_ticks mcfpp_benchmark matches 0 run function mcfpp_benchmark:arm
'@

    $armContent = @'
# Warmup is complete. The measured phase waits for an explicit trigger so that
# /perf can be started (automatically or manually) without counting a partial tick.
scoreboard players set #phase mcfpp_benchmark 3
scoreboard players set #remaining mcfpp_benchmark 0
tellraw @a [{"text":"[MCFPP benchmark] ","color":"gold"},{"text":"warmup complete; __MEASURE_HINT__."}]
say MCFPP benchmark: warmup complete; run /function mcfpp_benchmark:measure
'@

    $measureContent = @'
# Starts the measured phase only when the harness is armed.
scoreboard players set #accepted mcfpp_benchmark 0
execute if score #phase mcfpp_benchmark matches 3 run function mcfpp_benchmark:measure_impl
execute if score #accepted mcfpp_benchmark matches 0 run tellraw @a [{"text":"[MCFPP benchmark] ","color":"gold"},{"text":"measure ignored: the pack is not armed at the end of warmup.","color":"red"}]
'@

    $measureImplContent = @'
scoreboard players set #accepted mcfpp_benchmark 1
scoreboard players set #ticks mcfpp_benchmark 0
scoreboard players set #invocations mcfpp_benchmark 0
scoreboard players set #done mcfpp_benchmark 0
scoreboard players operation #remaining mcfpp_benchmark = #measured_ticks mcfpp_benchmark
execute store result score #gametime_start mcfpp_benchmark run time query gametime
__PERF_START__
scoreboard players set #phase mcfpp_benchmark 2
tellraw @a [{"text":"[MCFPP benchmark] ","color":"gold"},{"text":"measured phase started: __MEASURED__ ticks at load __LOAD__."}]
say MCFPP benchmark: measured phase started.
'@

    $stopContent = @'
# Stops an active warmup/armed/measured/baseline phase. In auto-profile mode a
# running /perf is stopped first; completed runs are not incremented.
__PERF_STOP_GUARD__
scoreboard players set #phase mcfpp_benchmark 0
scoreboard players set #remaining mcfpp_benchmark 0
scoreboard players set #done mcfpp_benchmark 0
tellraw @a [{"text":"[MCFPP benchmark] ","color":"gold"},{"text":"stopped. completed runs="},{"score":{"name":"#runs_done","objective":"mcfpp_benchmark"}},{"text":"/"},{"score":{"name":"#runs_target","objective":"mcfpp_benchmark"}}]
'@

    $tickContent = @'
execute if score #phase mcfpp_benchmark matches 1 run function mcfpp_benchmark:warmup_tick
execute if score #phase mcfpp_benchmark matches 2 run function mcfpp_benchmark:measured_tick
execute if score #phase mcfpp_benchmark matches 4 run function mcfpp_benchmark:baseline_tick
'@

    $warmupTickContent = @'
function mcfpp_benchmark:run_entry
scoreboard players remove #remaining mcfpp_benchmark 1
execute if score #remaining mcfpp_benchmark matches ..0 run function mcfpp_benchmark:arm
'@

    $measuredTickContent = @'
function mcfpp_benchmark:run_entry
scoreboard players add #ticks mcfpp_benchmark 1
scoreboard players operation #invocations mcfpp_benchmark += #load mcfpp_benchmark
scoreboard players remove #remaining mcfpp_benchmark 1
execute if score #remaining mcfpp_benchmark matches ..0 run function mcfpp_benchmark:finish
'@

    $finishContent = @'
scoreboard players operation #last_ticks mcfpp_benchmark = #ticks mcfpp_benchmark
scoreboard players operation #last_invocations mcfpp_benchmark = #invocations mcfpp_benchmark
scoreboard players set #done mcfpp_benchmark 1
scoreboard players set #phase mcfpp_benchmark 0
scoreboard players set #remaining mcfpp_benchmark 0
execute store result score #gametime_end mcfpp_benchmark run time query gametime
scoreboard players operation #gametime_span mcfpp_benchmark = #gametime_end mcfpp_benchmark
scoreboard players operation #gametime_span mcfpp_benchmark -= #gametime_start mcfpp_benchmark
__PERF_STOP__
scoreboard players add #runs_done mcfpp_benchmark 1
tellraw @a [{"text":"[MCFPP benchmark] ","color":"gold"},{"text":"measured run complete. ticks="},{"score":{"name":"#last_ticks","objective":"mcfpp_benchmark"}},{"text":" invocations="},{"score":{"name":"#last_invocations","objective":"mcfpp_benchmark"}},{"text":" run "},{"score":{"name":"#runs_done","objective":"mcfpp_benchmark"}},{"text":"/"},{"score":{"name":"#runs_target","objective":"mcfpp_benchmark"}}]
say MCFPP benchmark: measured run complete; __FINISH_HINT__
execute if score #runs_done mcfpp_benchmark >= #runs_target mcfpp_benchmark run tellraw @a [{"text":"[MCFPP benchmark] ","color":"gold"},{"text":"configured repetition target reached.","color":"green"}]
'@

    $baselineContent = @'
# Idle baseline: the same measured tick budget with no entry-function calls.
scoreboard players set #accepted mcfpp_benchmark 0
execute if score #phase mcfpp_benchmark matches 0 run function mcfpp_benchmark:baseline_impl
execute if score #accepted mcfpp_benchmark matches 0 run tellraw @a [{"text":"[MCFPP benchmark] ","color":"gold"},{"text":"baseline ignored: another phase is active.","color":"red"}]
'@

    $baselineImplContent = @'
scoreboard players set #accepted mcfpp_benchmark 1
scoreboard players set #done mcfpp_benchmark 0
scoreboard players operation #remaining mcfpp_benchmark = #measured_ticks mcfpp_benchmark
execute store result score #gametime_start mcfpp_benchmark run time query gametime
__PERF_START__
scoreboard players set #phase mcfpp_benchmark 4
tellraw @a [{"text":"[MCFPP benchmark] ","color":"gold"},{"text":"idle baseline started: __MEASURED__ ticks, no entry-function calls."}]
say MCFPP benchmark: idle baseline started.
'@

    $baselineTickContent = @'
scoreboard players remove #remaining mcfpp_benchmark 1
execute if score #remaining mcfpp_benchmark matches ..0 run function mcfpp_benchmark:baseline_finish
'@

    $baselineFinishContent = @'
scoreboard players set #phase mcfpp_benchmark 0
scoreboard players set #remaining mcfpp_benchmark 0
execute store result score #gametime_end mcfpp_benchmark run time query gametime
scoreboard players operation #gametime_span mcfpp_benchmark = #gametime_end mcfpp_benchmark
scoreboard players operation #gametime_span mcfpp_benchmark -= #gametime_start mcfpp_benchmark
__PERF_STOP__
tellraw @a [{"text":"[MCFPP benchmark] ","color":"gold"},{"text":"idle baseline complete. __BASELINE_HINT__"}]
say MCFPP benchmark: idle baseline complete.
'@

    $reportContent = @'
tellraw @a [{"text":"[MCFPP benchmark] ","color":"gold"},{"text":"phase="},{"score":{"name":"#phase","objective":"mcfpp_benchmark"}},{"text":" runs="},{"score":{"name":"#runs_done","objective":"mcfpp_benchmark"}},{"text":"/"},{"score":{"name":"#runs_target","objective":"mcfpp_benchmark"}},{"text":" last_ticks="},{"score":{"name":"#last_ticks","objective":"mcfpp_benchmark"}},{"text":" last_invocations="},{"score":{"name":"#last_invocations","objective":"mcfpp_benchmark"}},{"text":" load="},{"score":{"name":"#load","objective":"mcfpp_benchmark"}},{"text":" gametime_span="},{"score":{"name":"#gametime_span","objective":"mcfpp_benchmark"}}]
tellraw @a [{"text":"[MCFPP benchmark] ","color":"gray"},{"text":"ticks are scheduled tick counts, not wall-clock milliseconds. Use the /perf zip (metrics/ticking.csv ticktime) for elapsed time and MSPT.","color":"gray"}]
tellraw @a [{"text":"[MCFPP benchmark] ","color":"gray"},{"text":"/perf stops after 10 s; a capped zip covers only part of the measured interval. Read its actual @tick span; repeat shorter matched runs if capped.","color":"gray"}]
'@

    $worldContent = @'
# Reset/world preparation for this kernel. Runs before warmup, never inside measured ticks.
__WORLD_LINES__
'@

    $resetKernelContent = @'
# Called once per run, before warmup.
function mcfpp_benchmark:world
'@

    $runEntryContent = @'
# Fixed execution context for every variant: overworld, position 0.5 64.0 0.5,
# yaw 90, pitch 0. The entry function is invoked __LOAD__ time(s) per tick.
__RUN_LINES__
'@

    $replacements = @{
        '__WARMUP__' = [string]$WarmupTicks
        '__MEASURED__' = [string]$MeasuredTicks
        '__LOAD__' = [string]$Load
        '__REPS__' = [string]$Repetitions
        '__KERNEL__' = $Kernel
        '__MEASURE_HINT__' = $measureHint
        '__FINISH_HINT__' = $finishHint
        '__BASELINE_HINT__' = $baselineHint
        '__PERF_START__' = $perfStartLine
        '__PERF_STOP__' = $perfStopLine
        '__PERF_STOP_GUARD__' = ($perfStopGuard -join "`n")
        '__WORLD_LINES__' = ($worldLines -join "`n")
        '__RUN_LINES__' = ($runEntryLines -join "`n")
    }

    $files = [ordered]@{
        'load.mcfunction' = $loadContent
        'setup.mcfunction' = $setupContent
        'start.mcfunction' = $startContent
        'start_impl.mcfunction' = $startImplContent
        'arm.mcfunction' = $armContent
        'measure.mcfunction' = $measureContent
        'measure_impl.mcfunction' = $measureImplContent
        'stop.mcfunction' = $stopContent
        'tick.mcfunction' = $tickContent
        'warmup_tick.mcfunction' = $warmupTickContent
        'measured_tick.mcfunction' = $measuredTickContent
        'finish.mcfunction' = $finishContent
        'baseline.mcfunction' = $baselineContent
        'baseline_impl.mcfunction' = $baselineImplContent
        'baseline_tick.mcfunction' = $baselineTickContent
        'baseline_finish.mcfunction' = $baselineFinishContent
        'report.mcfunction' = $reportContent
        'world.mcfunction' = $worldContent
        'reset_kernel.mcfunction' = $resetKernelContent
        'run_entry.mcfunction' = $runEntryContent
    }

    foreach ($name in $files.Keys) {
        $content = $files[$name]
        foreach ($token in $replacements.Keys) {
            $content = $content.Replace($token, $replacements[$token])
        }
        Write-Utf8NoBom (Join-PathParts @($benchmarkFunctionDir, $name)) $content
    }

    Merge-FunctionTag (Join-PathParts @($minecraftTagDir, 'load.json')) 'mcfpp_benchmark:load'
    Merge-FunctionTag (Join-PathParts @($minecraftTagDir, 'tick.json')) 'mcfpp_benchmark:tick'

    $marker = [ordered]@{
        generatedBy = 'prepare-benchmark.ps1'
        format = 1
        minecraftVersion = '26.3'
        packFormat = '121.0'
        sourcePack = $sourceFull
        sourcePackFormat = $packFormat.Text
        entryFunction = $EntryFunction
        kernel = $Kernel
        loadPerTick = $Load
        repetitions = $Repetitions
        warmupTicks = $WarmupTicks
        measuredTicks = $MeasuredTicks
        manualProfile = [bool]$ManualProfile
        generatedAtUtc = [DateTime]::UtcNow.ToString('o')
    }
    Write-Utf8NoBom (Join-PathParts @($outputFull, 'mcfpp-benchmark.generated.json')) ($marker | ConvertTo-Json -Depth 8)

    Write-Host ''
    Write-Host 'Benchmark data pack written:' -ForegroundColor Green
    Write-Host "  Output       : $outputFull"
    Write-Host "  SourcePack   : $sourceFull"
    Write-Host "  EntryFunction: $EntryFunction"
    Write-Host "  Kernel       : $Kernel"
    Write-Host "  Load/tick    : $Load"
    Write-Host "  Warmup ticks : $WarmupTicks"
    Write-Host "  Measured ticks: $MeasuredTicks"
    Write-Host "  Repetitions  : $Repetitions"
    Write-Host "  Profile mode : $(if ($ManualProfile) { 'manual /perf' } else { 'automatic /perf from functions' })"
    Write-Host ''
    Write-Host 'Next: install Output (and the compiler Imports/ pack, if present) into the dedicated server, then follow examples/mni/benchmark/README.md.'
}
catch {
    Write-Host ("ERROR: " + $_.Exception.Message) -ForegroundColor Red
    exit 1
}

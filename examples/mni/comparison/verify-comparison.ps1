param(
    [Parameter(Mandatory = $true)][string]$RuntimeJar,
    [Parameter(Mandatory = $true)][string]$ProviderJar,
    [ValidatePattern('^[A-Za-z0-9_-]+$')][string]$Attempt = 'attempt1',
    [string]$ReportPath = '',
    [int]$CommandBudget = 20000
)

$ErrorActionPreference = 'Stop'
$PSNativeCommandUseErrorActionPreference = $false
$repoRoot = (Resolve-Path -LiteralPath (Join-Path $PSScriptRoot '../../..')).Path
if (-not [IO.Path]::IsPathRooted($RuntimeJar)) { $RuntimeJar = Join-Path $repoRoot $RuntimeJar }
if (-not [IO.Path]::IsPathRooted($ProviderJar)) { $ProviderJar = Join-Path $repoRoot $ProviderJar }
$RuntimeJar = (Resolve-Path -LiteralPath $RuntimeJar).Path
$ProviderJar = (Resolve-Path -LiteralPath $ProviderJar).Path
if ([string]::IsNullOrWhiteSpace($ReportPath)) {
    $ReportPath = Join-Path $repoRoot 'docs/mni/generated-code-report.md'
} elseif (-not [IO.Path]::IsPathRooted($ReportPath)) {
    $ReportPath = Join-Path $repoRoot $ReportPath
}
$ReportPath = [IO.Path]::GetFullPath($ReportPath)

$separator = [IO.Path]::PathSeparator
$evidenceRoot = Join-Path $repoRoot 'build/inheritance-mni/comparison-check'
$evidence = Join-Path $evidenceRoot $Attempt
if (Test-Path -LiteralPath $evidence) {
    throw "Existing evidence directory: $evidence"
}
New-Item -ItemType Directory -Force -Path $evidence | Out-Null
$resultPath = Join-Path $evidence 'result.json'

$javaHome = if ($env:JAVA_HOME) { $env:JAVA_HOME } else { 'F:/Java/ms-21.0.12.1' }
$java = Join-Path $javaHome 'bin/java.exe'
$javac = Join-Path $javaHome 'bin/javac.exe'
$jarTool = Join-Path $javaHome 'bin/jar.exe'
if (-not (Test-Path -LiteralPath $java)) {
    $java = 'java'
    $javac = 'javac'
    $jarTool = 'jar'
}
$env:JAVA_TOOL_OPTIONS = "-Djdk.net.unixdomain.tmpdir=" + (Join-Path $repoRoot 'build')
$env:JDK_JAVA_OPTIONS = '-XX:TieredStopAtLevel=1'

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

function Write-Utf8NoBom([string]$Path, [string]$Text) {
    $encoding = [Text.UTF8Encoding]::new($false)
    [IO.File]::WriteAllText($Path, $Text, $encoding)
}

function Write-BaselinePacks {
    param(
        [string]$BaselinesPath,
        [string]$Root,
        [string[]]$Objectives
    )

    $baselines = Get-Content -LiteralPath $BaselinesPath -Raw | ConvertFrom-Json
    $records = [System.Collections.Generic.List[object]]::new()
    foreach ($scenario in $baselines.scenarios) {
        foreach ($variantName in $scenario.variants.PSObject.Properties.Name) {
            $variant = $scenario.variants.$variantName
            $entry = [string]$variant.entry
            $entryColon = $entry.IndexOf(':')
            if ($entryColon -le 0) { throw "Baseline entry has no namespace: $entry" }
            $namespace = $entry.Substring(0, $entryColon)
            $packRoot = Join-Path (Join-Path $Root $scenario.id) $variantName
            $dataRoot = Join-Path $packRoot 'data'
            New-Item -ItemType Directory -Force -Path $dataRoot | Out-Null

            $packMeta = [ordered]@{
                pack = [ordered]@{
                    description = "MNI comparison baseline $($scenario.id)/$variantName"
                    min_format = 121
                    max_format = 121
                }
            }
            Write-Utf8NoBom (Join-Path $packRoot 'pack.mcmeta') (($packMeta | ConvertTo-Json -Depth 4) + "`n")

            $functionNames = [System.Collections.Generic.List[string]]::new()
            foreach ($property in $variant.functions.PSObject.Properties) {
                $qualified = [string]$property.Name
                $colon = $qualified.IndexOf(':')
                if ($colon -le 0) { throw "Baseline function has no namespace: $qualified" }
                $functionNamespace = $qualified.Substring(0, $colon)
                $functionPath = $qualified.Substring($colon + 1)
                $target = Join-Path (Join-Path (Join-Path $dataRoot $functionNamespace) 'function') ($functionPath + '.mcfunction')
                New-Item -ItemType Directory -Force -Path (Split-Path -Parent $target) | Out-Null
                $lines = @($property.Value)
                $text = if ($lines.Count -eq 0) { '' } else { ($lines -join "`n") + "`n" }
                Write-Utf8NoBom $target $text
                $functionNames.Add($qualified)
            }

            $loadBody = [System.Collections.Generic.List[string]]::new()
            foreach ($objective in $Objectives) {
                $loadBody.Add("scoreboard objectives add $objective dummy")
            }
            if ($loadBody.Count -eq 0) { throw 'At least one baseline objective is required' }
            $loadFunction = "${namespace}:load"
            $loadTarget = Join-Path (Join-Path (Join-Path $dataRoot $namespace) 'function') 'load.mcfunction'
            New-Item -ItemType Directory -Force -Path (Split-Path -Parent $loadTarget) | Out-Null
            Write-Utf8NoBom $loadTarget (($loadBody -join "`n") + "`n")

            $tagRoot = Join-Path (Join-Path $dataRoot 'minecraft') 'tags/function'
            New-Item -ItemType Directory -Force -Path $tagRoot | Out-Null
            $loadTag = [ordered]@{ values = @($loadFunction) }
            Write-Utf8NoBom (Join-Path $tagRoot 'load.json') (($loadTag | ConvertTo-Json -Depth 3) + "`n")

            $records.Add([ordered]@{
                scenario = $scenario.id
                variant = $variantName
                entry = $entry
                pack = $packRoot
                dataRoot = $dataRoot
                packMcmeta = (Join-Path $packRoot 'pack.mcmeta')
                loadFunction = $loadFunction
                loadTag = 'minecraft:load'
                functions = @($functionNames)
            })
        }
    }
    return $records
}

function Write-MeasuredReport {
    param([string]$ResultPath, [string]$OutputPath)

    $e = Get-Content -LiteralPath $ResultPath -Raw | ConvertFrom-Json
    $md = [System.Collections.Generic.List[string]]::new()
    $md.Add('# MNI 生成代码成本对照（实际执行）')
    $md.Add('')
    $md.Add('**状态：实际测量成功。** 本报告由 verify-comparison.ps1 成功执行后仅根据 result.json 生成。')
    $md.Add('')
    $md.Add("生成时间（UTC）：$($e.endedUtc)。")
    $md.Add('')
    $md.Add('## 实际执行范围')
    $md.Add('')
    $md.Add("- 当前运行时 JAR：$($e.runtimeJar)（SHA-256 $($e.runtimeJarSha256)）。")
    $md.Add("- 目标 Minecraft 版本：$($e.mcVersion)。")
    $md.Add("- 字面 provider JAR：$($e.providerJar)（SHA-256 $($e.providerJarSha256)）。")
    $md.Add("- 实际 CLI 命令：$($e.compile.command)；工作目录：$($e.compile.workingDirectory)。")
    $md.Add("- 真实 CLI 只执行一次：exit $($e.compile.exitCode)，零错误零警告 $($e.compile.zeroErrorsWarnings)；工程级墙钟耗时 **$($e.compile.elapsedMs) ms**（包含 JVM 启动，覆盖全部五个 kernel 和共享产物，不是每 kernel 的编译耗时）。")
    $md.Add("- MNI 生成产物共 **$($e.emitted.functionFiles)** 个 function 文件、**$($e.emitted.functionBytes)** UTF-8 字节（整个工程，含共享 stdlib；每 kernel 可达统计见下表），聚合 SHA-256 $($e.emitted.aggregateSha256)。")
    $md.Add("- 其中 example.comparison 命名空间：$($e.emitted.namespaceFiles) 个 function 文件、$($e.emitted.namespaceBytes) UTF-8 字节，聚合 SHA-256 $($e.emitted.namespaceAggregateSha256)；共享工程总量与每 kernel 可达量分开记录。")
    $md.Add("- 生成命令由 ContextCommandExecutor（严格测试解释器）实际执行；行为等价性检查在生成指标前通过。这不是 Minecraft 游戏内执行。")
    $md.Add('')
    $md.Add('## 数据包素材')
    $md.Add('')
    $md.Add("- MNI 源数据包：$($e.emitted.dataRoot)（由真实 CLI 在 stage/project/build/mcfpp 下生成，命名空间 $($e.emitted.generatedNamespace)）。")
    $md.Add('- 手写与运行时基线数据包由 baselines.json 中的字面函数映射逐个物化；每个包都有 pack.mcmeta（min_format/max_format 121）、load 函数和 minecraft:load 标签。')
    foreach ($pack in $e.baselinePacks) {
        $md.Add("- $($pack.scenario)/$($pack.variant)：$($pack.pack)；entry=$($pack.entry)；load=$($pack.loadFunction)；tag=$($pack.loadTag)。")
    }
    $md.Add('')
    $md.Add('## 五场景三方成本表')
    $md.Add('')
    $md.Add('| 领域 | 场景 | 变体 | 命令数 | 跨帧搬运 | 存储复制 | NBT 命令 | 栈 push/pop | caller-beyond 帧（含普通成员调用） | 可达文件 | 可达字节 | 观测值 | block 测试 |')
    $md.Add('| --- | --- | --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | --- | ---: |')
    foreach ($scenario in $e.measurement.scenarios) {
        foreach ($variant in $scenario.variants) {
            $observedParts = @()
            foreach ($property in $variant.observed.PSObject.Properties) {
                $observedParts += "$($property.Name)=$($property.Value)"
            }
            $observedText = if ($observedParts.Count -eq 0) { '-' } else { $observedParts -join ', ' }
            $m = $variant.metrics
            $md.Add("| $($scenario.domain) | $($scenario.id) | $($variant.variant) | $($m.commandLines) | $($m.parameterMoves) | $($m.storageCopies) | $($m.nbtCommands) | $($m.stackPushes)/$($m.stackPops) | $($m.extraAdapterFrames) | $($m.reachableFiles) | $($m.reachableBytes) | $observedText | $($variant.blockTests) |")
        }
    }
    $md.Add('')
    $md.Add('## 场景期望与等价性')
    $md.Add('')
    $md.Add('| 场景 | kernel | 期望观测 | 期望 block 测试 | 期望 score |')
    $md.Add('| --- | --- | --- | ---: | --- |')
    foreach ($scenario in $e.measurement.scenarios) {
        $expectedParts = @()
        foreach ($property in $scenario.expected.PSObject.Properties) {
            $expectedParts += "$($property.Name)=$($property.Value)"
        }
        $expectedText = if ($expectedParts.Count -eq 0) { '-' } else { $expectedParts -join ', ' }
        $scoreParts = @()
        foreach ($property in $scenario.expectedScores.PSObject.Properties) {
            $scoreParts += "$($property.Name)=$($property.Value)"
        }
        $scoreText = if ($scoreParts.Count -eq 0) { '-' } else { $scoreParts -join ', ' }
        $blockExpected = if ($null -eq $scenario.expectedBlockTests) { '-' } else { $scenario.expectedBlockTests }
        $md.Add("| $($scenario.id) | $($scenario.kernel) | $expectedText | $blockExpected | $scoreText |")
    }
    $md.Add('')
    $md.Add('所有三种变体都由同一个严格解释器执行；观测值、射线 block 测试次数和 score objective 值必须与期望完全一致，否则脚本不会生成此报告。观测 probe 只在内存副本中插入到真实调用者栈帧弹出之前，计数时统一排除，已生成文件从不修改。')
    $md.Add('')
    $md.Add('## 指标定义')
    $md.Add('')
    foreach ($property in $e.measurement.definitions.PSObject.Properties) {
        $md.Add("- **$($property.Name)**：$($property.Value)")
    }
    $md.Add('')
    $md.Add('## 源文件哈希')
    $md.Add('')
    foreach ($property in $e.sourceHashes.PSObject.Properties) {
        $md.Add("- $($property.Name)：$($property.Value)")
    }
    $md.Add('')
    $md.Add('## 限制')
    $md.Add('')
    $md.Add('- 命令条数不是速度，也不代表游戏内 tick 成本；本报告不预设任何加速比例。')
    $md.Add('- 执行器是仓库内严格测试解释器，不是 Minecraft 服务器；没有测量游戏内运行耗时、tick 或负载。')
    $md.Add('- 编译耗时只有一次工程级 CLI 墙钟值（含 JVM 启动），不能拆成每 kernel 的编译耗时。')
    $md.Add('- 手写与运行时基线是 baselines.json 中实际执行的命令映射，不是编译器生成产物；MNI 变体来自真实 CLI 输出。')
    $md.Add('- 未执行数据包 load/init/tick 或 objective 注册；只执行五个 kernel 入口和三方基线函数。')
    $md.Add('- 可达文件/字节按字面 function 调用静态遍历，包含未走到的回调/备用分派 helper；共享工程总量与每 kernel 可达量分开记录。')
    $md.Add('')

    $parent = Split-Path -Parent $OutputPath
    if ($parent) { New-Item -ItemType Directory -Force -Path $parent | Out-Null }
    $md | Set-Content -LiteralPath $OutputPath -Encoding utf8
}

$result = [ordered]@{
    schema = 'mcfpp-comparison-evidence/1'
    startedUtc = [DateTime]::UtcNow.ToString('o')
    endedUtc = $null
    runtimeJar = $RuntimeJar
    runtimeJarSha256 = $null
    providerJar = $ProviderJar
    providerJarSha256 = $null
    mcVersion = '26.3'
    commandBudget = $CommandBudget
    sourceHashes = $null
    compile = $null
    emitted = $null
    baselinePacks = $null
    measurement = $null
}

try {
    $result.runtimeJarSha256 = Get-FileSha256 $RuntimeJar
    $result.providerJarSha256 = Get-FileSha256 $ProviderJar

    $sourceHashes = [ordered]@{}
    $sourceFiles = @(
        'examples/mni/comparison/ComparisonProvider.java',
        'examples/mni/comparison/kernels.mcfpp',
        'examples/mni/comparison/baselines.json',
        'examples/mni/comparison/MeasureComparison.java',
        'examples/mni/comparison/verify-comparison.ps1',
        'src/test/kotlin/top/mcfpp/test/util/ContextCommandExecutor.kt'
    )
    foreach ($relative in $sourceFiles) {
        $sourceHashes[$relative] = Get-FileSha256 (Join-Path $repoRoot $relative)
    }
    $result.sourceHashes = $sourceHashes

    $bootstrapDir = Join-Path $evidence 'bootstrap'
    $bootstrapClasses = Join-Path $bootstrapDir 'classes'
    New-Item -ItemType Directory -Force -Path $bootstrapClasses | Out-Null
    $bootstrapLog = Join-Path $evidence 'bootstrap-compile.log'
    $bootstrapClasspath = $RuntimeJar + $separator + $ProviderJar
    & $javac -encoding UTF-8 -cp $bootstrapClasspath -d $bootstrapClasses (Join-Path $repoRoot 'examples/mni/comparison/ComparisonProvider.java') *> $bootstrapLog
    if ($LASTEXITCODE -ne 0) {
        throw "ComparisonProvider bootstrap compile failed (exit $LASTEXITCODE); see $bootstrapLog"
    }
    $bootstrapJar = Join-Path $bootstrapDir 'comparison-bootstrap.jar'
    $bootstrapJarLog = Join-Path $evidence 'bootstrap-jar.log'
    & $jarTool --create --file $bootstrapJar -C $bootstrapClasses . *> $bootstrapJarLog
    if ($LASTEXITCODE -ne 0) {
        throw "ComparisonProvider bootstrap jar failed (exit $LASTEXITCODE); see $bootstrapJarLog"
    }
    $result['bootstrapJar'] = $bootstrapJar
    $result['bootstrapJarSha256'] = Get-FileSha256 $bootstrapJar

    $stage = Join-Path $evidence 'stage'
    $stageSource = Join-Path $stage 'project/src'
    New-Item -ItemType Directory -Force -Path $stageSource | Out-Null
    Copy-Item -LiteralPath (Join-Path $repoRoot 'examples/mni/comparison/kernels.mcfpp') -Destination (Join-Path $stageSource 'kernels.mcfpp')
    Set-Content -LiteralPath (Join-Path $stageSource '.mcfpp') -Value '//> injectedBy example.mnicomparison.ComparisonProvider' -Encoding ascii
    $config = [ordered]@{
        sourcePath = 'project/src'
        targetPath = 'project/build'
        namespace = 'example.comparison'
        version = '26.3'
        description = 'MNI three-way comparison measurement project'
        jars = @($ProviderJar, $bootstrapJar)
    }
    $configText = @('schema = 1', 'name = "mcfpp"') + @($config.GetEnumerator() | ForEach-Object { "$($_.Key) = $(ConvertTo-Json -InputObject $_.Value -Compress -Depth 5)" })
    $configText | Set-Content -LiteralPath (Join-Path $stage 'mcfpp.toml') -Encoding utf8
    $stagedBaselines = Join-Path $stage 'baselines.json'
    Copy-Item -LiteralPath (Join-Path $repoRoot 'examples/mni/comparison/baselines.json') -Destination $stagedBaselines
    $result.baselinePacks = @(Write-BaselinePacks -BaselinesPath $stagedBaselines `
        -Root (Join-Path $evidence 'baseline-packs') `
        -Objectives @('comparison', 'mcfpp_default', 'health_hp', 'state_alive'))

    $compileLog = Join-Path $evidence 'compiler.log'
    $compileWatch = [Diagnostics.Stopwatch]::StartNew()
    Push-Location $stage
    try {
        & $java -jar $RuntimeJar build --project mcfpp.toml *> $compileLog
        $compileExit = $LASTEXITCODE
    } finally {
        Pop-Location
    }
    $compileWatch.Stop()
    $compilerLog = Get-Content -LiteralPath $compileLog -Raw
    $zeroErrorsWarnings = ($compileExit -eq 0) -and ($compilerLog -match '\[0\]\s+error and\s+\[0\]\s+warning')
    $result.compile = [ordered]@{
        projectWide = $true
        command = "java -jar $RuntimeJar build --project mcfpp.toml"
        workingDirectory = $stage
        exitCode = $compileExit
        elapsedMs = [math]::Round($compileWatch.Elapsed.TotalMilliseconds, 3)
        zeroErrorsWarnings = $zeroErrorsWarnings
        logTail = @(Get-Content -LiteralPath $compileLog -Tail 40)
    }
    if (-not $zeroErrorsWarnings) {
        throw "Real CLI compile failed or was not clean (exit $compileExit); see $compileLog"
    }

    $dataRoot = Join-Path $stage 'project/build/mcfpp/data'
    if (-not (Test-Path -LiteralPath $dataRoot)) {
        throw "Generated data root does not exist: $dataRoot"
    }
    $functionFiles = @(Get-ChildItem -LiteralPath $dataRoot -Recurse -File -Filter '*.mcfunction' | Sort-Object FullName)
    if ($functionFiles.Count -eq 0) {
        throw "No generated .mcfunction files under $dataRoot"
    }
    $fileRecords = [System.Collections.Generic.List[object]]::new()
    [int64]$totalBytes = 0
    foreach ($file in $functionFiles) {
        $relative = $file.FullName.Substring($dataRoot.Length).TrimStart([char[]]@('\', '/')).Replace('\', '/')
        $bytes = [int64]$file.Length
        $totalBytes += $bytes
        $fileRecords.Add([ordered]@{
            path = $relative
            bytes = $bytes
            sha256 = Get-FileSha256 $file.FullName
        })
    }
    $aggregateText = (($fileRecords | ForEach-Object { "$($_['sha256'])  $($_['path'])" }) -join "`n") + "`n"
    $namespaceName = 'example.comparison'
    $namespaceRecords = @($fileRecords | Where-Object { $_['path'].StartsWith("$namespaceName/") })
    [int64]$namespaceBytes = 0
    foreach ($record in $namespaceRecords) { $namespaceBytes += [int64]$record.bytes }
    $namespaceAggregateText = (($namespaceRecords | ForEach-Object { "$($_['sha256'])  $($_['path'])" }) -join "`n") + "`n"
    $result.emitted = [ordered]@{
        dataRoot = $dataRoot
        functionFiles = $functionFiles.Count
        functionBytes = $totalBytes
        aggregateSha256 = Get-TextSha256 $aggregateText
        generatedNamespace = $namespaceName
        namespaceFiles = $namespaceRecords.Count
        namespaceBytes = $namespaceBytes
        namespaceAggregateSha256 = Get-TextSha256 $namespaceAggregateText
        files = $fileRecords
    }

    $testClasses = Join-Path $repoRoot 'build/classes/kotlin/test'
    if (-not (Test-Path -LiteralPath $testClasses)) {
        throw "Missing test utility classes: $testClasses. Compile the test source set before running this measurement."
    }
    $driverClasses = Join-Path $evidence 'driver-classes'
    New-Item -ItemType Directory -Force -Path $driverClasses | Out-Null
    $driverCompileLog = Join-Path $evidence 'driver-compile.log'
    $driverClasspath = $RuntimeJar + $separator + $testClasses
    & $javac -encoding UTF-8 -cp $driverClasspath -d $driverClasses (Join-Path $repoRoot 'examples/mni/comparison/MeasureComparison.java') *> $driverCompileLog
    if ($LASTEXITCODE -ne 0) {
        throw "MeasureComparison compile failed (exit $LASTEXITCODE); see $driverCompileLog"
    }

    $measurementJson = Join-Path $evidence 'measurement.json'
    $measurementLog = Join-Path $evidence 'measurement.log'
    $runClasspath = $driverClasses + $separator + $RuntimeJar + $separator + $testClasses
    & $java -cp $runClasspath MeasureComparison --data-root $dataRoot --baselines $stagedBaselines --out $measurementJson --command-budget $CommandBudget *> $measurementLog
    if ($LASTEXITCODE -ne 0) {
        throw "MeasureComparison execution failed (exit $LASTEXITCODE); see $measurementLog"
    }
    if (-not (Test-Path -LiteralPath $measurementJson)) {
        throw "MeasureComparison did not write $measurementJson; see $measurementLog"
    }
    $result.measurement = Get-Content -LiteralPath $measurementJson -Raw | ConvertFrom-Json

    Write-Output "Compared $($result.measurement.scenarios.Count) scenarios x 3 variants."
    Write-Output "Project-wide CLI compile: $($result.compile.elapsedMs) ms, exit $($result.compile.exitCode), zero-errors-warnings=$zeroErrorsWarnings."
    Write-Output "Evidence: $resultPath"
} catch {
    $result['error'] = $_.Exception.Message
    throw
} finally {
    $result.endedUtc = [DateTime]::UtcNow.ToString('o')
    $result | ConvertTo-Json -Depth 40 | Set-Content -LiteralPath $resultPath -Encoding utf8
}

Write-MeasuredReport -ResultPath $resultPath -OutputPath $ReportPath
Write-Output "Report: $ReportPath"

# MNI 示例 DSB 功能验收（Minecraft 26.3）

本页是 MNI 示例数据包的**主验收说明**。入口是
[`examples/mni/benchmark/verify-dsb.ps1`](../../examples/mni/benchmark/verify-dsb.ps1)，
它通过 [`DsbFunctionalCheck.java`](../../examples/mni/benchmark/DsbFunctionalCheck.java)
调用 Datapack Sandbox（DSB）26.3 的公共运行时 API，加载当前实际生成的
Java/Kotlin 示例产物，逐例断言真实结果。

> **性能测试和真实游戏均已按用户要求取消，不是交付门槛。** 本页不再要求专用
> 服务器、`/perf`、预热、测量 tick、重复轮次、固定负载、基线或 MSPT 统计。DSB
> 是确定性解释器，它的运行时间不是游戏耗时，也不能写成性能/加速结论。
>
> 当前证据状态：10 个 Java/Kotlin 示例 case 在 DSB 26.3 下全部功能 PASS。其中
> 8 例复用 `dsb-check/attempt2` 的实际通过证据，`repeat-Kotlin`/`repeat-Java`
> 2 例由 `dsb-check/attempt3` 新执行通过（16MB 功能快照上限，断言 `10/8/9/22`，
> 最终 `stack_frame=[]`）。逐例汇总见
> [`functional-summary.json`](../../build/inheritance-mni/dsb-check/functional-summary.json)。
> 这不是一次全新的 10 例同轮执行，也不存在等待真实游戏或性能测试的交付门槛。

## 1. 验收范围

验收对象固定为
`build/inheritance-mni/actual-examples-cli-check/attempt1/result.json` 中记录的
10 个实际产物 case：5 个示例 × Kotlin/Java。默认运行全部 10 个；`-Cases` 可只选
其中已知 case（如 `@('repeat-Kotlin','repeat-Java')`）复跑，未选 case 不产生证据。

| 示例 | 数量 | 入口 | 功能断言 |
| --- | ---: | --- | --- |
| `damage` | 2 | `<namespace>:main` | 血量扣减与下限：`70`、`0`、`0` |
| `repeat` | 2 | `<namespace>:main` | 同步回调累加：`10`、`8`、`9`、`22` |
| `nbt-reference` | 2 | `<namespace>:main` | 原位置/别名/索引引用：`70`、`10`、`10`、`15` |
| `raycast` | 2 | `<namespace>:main` | continue/stop 的采样命中、最后下标、回调次数 |
| `score-mapping` | 2 | `<namespace>:main` | score 保存/加载：`loadedHealth=7`、`loadedAlive=1` |

入口只读取 `dataRoot`（`project/build/mcfpp/data`）及其相邻的
`project/build/Imports`，不会编译 MCFPP，也不会改写这些实际产物。

## 2. 运行

在仓库根目录执行：

```powershell
powershell -NoProfile -ExecutionPolicy Bypass `
  -File examples\mni\benchmark\verify-dsb.ps1 `
  -DsbJar "F:\Project_MCFPP\mcfpp-language-support\intellij-plugin\src\main\resources\dps\datapack-sandbox-cli.jar" `
  -JavaExe "F:\Java\jdk-25.0.4\bin\java.exe" `
  -CliEvidenceRoot "build\inheritance-mni\actual-examples-cli-check\attempt1" `
  -Attempt attempt4
```

只跑已知的两个 case（不复制、不生成清单、不执行其余 8 个；`-Cases` 接
PowerShell 数组）：

```powershell
& ./examples/mni/benchmark/verify-dsb.ps1 -Cases @('repeat-Kotlin','repeat-Java') -Attempt attempt3
```

第二个命令就是 `attempt3` 实际执行的历史复跑；该目录已存在，再次复跑需换新的
`-Attempt`。全量命令里的 `attempt4` 只是下次新运行示例，当前 10/10 并非一次
全新执行的结果，而是 8 例复用 + 2 例新执行的汇总。

参数：

- `-DsbJar`：实际 DSB CLI JAR；默认指向上面的本地副本。
- `-JavaExe`：Java 25 可执行文件；默认 `F:\Java\jdk-25.0.4\bin\java.exe`。
- `-Adapter`：`DsbFunctionalCheck.java`；默认 `examples/mni/benchmark/DsbFunctionalCheck.java`。
- `-CliEvidenceRoot`：含 `result.json` 的示例产物根。
- `-Attempt`：`build/inheritance-mni/dsb-check/<Attempt>` 输出名；已存在时拒绝
  复用，避免把旧报告当成本次证据。脚本内默认 `attempt2` 已存在，实际运行请显式
  传新名。
- `-Cases`：可选的 case id 数组（如 `@('repeat-Kotlin','repeat-Java')`）；默认全部
  10 个。未知或重复值会在创建输出目录、复制数据包和生成清单之前失败。

CLI 证据 `result.json` 必须仍然正好包含这 10 个 case；`-Cases` 只选择本次执行
哪些，不改变证据文件。已有的 `dsb-check/attempt1` 是失败诊断证据，不应覆盖；
`attempt2` 记录了 8 例通过，`attempt3` 只新执行 repeat 两例。新运行使用新的
`-Attempt`，且其 `allPassed` 只代表本次所选 case。

## 3. DSB 26.3 协议事实

| 项目 | 值 | 依据 |
| --- | --- | --- |
| DSB profile | `26.3` | CLI `version` 输出；26.3 为默认 profile |
| 数据包格式 | `121.0` | 26.3 profile 的 `pack_format=121` |
| 目标 Java | 25 | DSB 26.3 profile 与本地运行环境 |
| 清单 schema | 随 CLI 打包的 26.3 schema | `ManifestSchemaValidator.validateFileTree` |
| 严格模式 | `UnsupportedFeatureMode.ERROR` + `failOnMissingResources=true` | 未支持命令、缺失资源或诊断都失败 |
| 命令追踪 | `CommandTraceMode.OFF` | 适配器在创建 sandbox 前设置 |

每个 case 的清单使用 `version=26.3`、实际 `pack` 与 `Imports` 两个数据包。适配器
使用 `SandboxKt.createSandbox(...)` 真正加载这两个包，再用
`ManifestRunner.INSTANCE.runInExistingSandbox(...)` 在同一个 sandbox 上执行清单的
world/steps/assertions；不会重新加载包，也不会把清单里的预期值当成结果。

## 4. 固定场景与上下文

每个入口都从同一个固定上下文调用：

```mcfunction
execute in minecraft:overworld positioned 0.5 64.0 0.5 rotated 90 0 run function <namespace>:main
```

`raycast` 世界 fixture 显式设置石块：

- `(0,64,0)`：`minecraft:stone`
- `(-1,64,0)`：`minecraft:stone`

并在副本中加入仅观测用的局部探针：

- `~ ~ ~` 命中 `(0,64,0)` 石块；
- `^ ^ ^0.5` 命中 `(0,64,0)` 石块；
- `^ ^ ^1.5` 命中 `(-1,64,0)` 石块；
- `^ ^ ^2` 位于第 5 个采样点的空气位置。

这组探针固定了 `(0.5,64,0.5)`、yaw 90、pitch 0 的语义；实际算法仍由原命令
执行，探针不替代采样、命中或回调逻辑。continue 的 `4` 是 5 个采样点中的命中
次数、`2` 是不同方块数；stop 只执行第 1 个采样点。它们都不是耗时或性能。

## 5. 加载、探针和调用帧

### 5.1 实际加载

每个清单先执行数据包自己的 `#minecraft:load`（即真实 `<namespace>:load` 与
`init`），因此会执行生成器注册和 `math` 初始化。入口另外只补给 reachable 代码
需要的 objective：`mcfpp_default`、`mcfpp_temp`、`mcfpp_boolean`，以及
score-mapping 的 `health_hp`/`state_alive`。后者不是生成器 `load` 会注册的
objective，必须由验收入口提供；它们的值由实际 `save`/`load` 命令写入和读回。

### 5.2 探针位置

脚本把原包复制到 `dsb-check/<Attempt>/packs/<case>/`，只在副本中插入：

```mcfunction
# DSB OBSERVATION (probe-only): read the caller frame before its genuine final cleanup
data modify storage verification:dsb <probe> set from storage mcfpp:system stack_frame[0].<probe>
```

- 8 个 case 的 `main` 末尾就是真实调用者帧的最终 pop，探针插在它之前。
- `score-mapping` 的 `main` 以
  `return run function if_branch_53/else_branch_57` 结束；`main` 中间的 pop 不是
  最终 pop。脚本递归进入两个 terminal helper，在各自的最终 pop 前插桩，再断言
  `loadedHealth`/`loadedAlive`。
- 探针不改变原算法；源数据包从不写入。每个被插桩函数的原/副本 SHA-256 和
  `probe-diffs/<case>.diff` 都保留在 `result.json`。

### 5.3 调用帧

所有 case 在入口调用和真实 `load`/`init` 完成后断言：

```json
{"storage":{"id":"mcfpp:system","path":"stack_frame","equals":[]}}
```

因此栈帧为空是实际执行结果，不是从命令条数推断。清单断言只使用
`storage`/`block`/`score`，没有 trace 断言；关闭命令追踪不会改变这些断言。

## 6. 断言值和结果表

10 个 Java/Kotlin 示例 case 在 DSB 26.3 下全部功能 PASS。按
[`functional-summary.json`](../../build/inheritance-mni/dsb-check/functional-summary.json)
的划分：

- **复用（8 例）**：`damage`、`nbt-reference`、`raycast`、`score-mapping`
  的 Kotlin/Java case 复用 `attempt2` 的实际原生通过报告，未重新执行，也未改写
  原报告、断言值或空栈帧结果。
- **新执行（2 例）**：`repeat-Kotlin`、`repeat-Java` 由 `attempt3` 在 16MB 功能
  快照上限下实际执行通过；`attempt3` 只选择这两例，未重跑其余 8 例。

| Case | 实际断言结果 | 证据 |
| --- | --- | --- |
| `damage-Kotlin` / `damage-Java` | `health=70`、`low=0`、`alias=0` | 复用 `attempt2` |
| `repeat-Kotlin` / `repeat-Java` | `total=10`、`guarded=8`、`tail=9`、`nested=22` | 新执行 `attempt3` |
| `nbt-reference-Kotlin` / `nbt-reference-Java` | `normal=70`、`aliased=10`、`untouched=10`、`indexed=15` | 复用 `attempt2` |
| `raycast-Kotlin` / `raycast-Java` | `continueHits=4`、`continueLast=3`、`afterContinue=5`、`stopHits=1`、`afterStop=2`；固定位置/旋转探针与石块 fixture | 复用 `attempt2` |
| `score-mapping-Kotlin` / `score-mapping-Java` | `loadedHealth=7`、`loadedAlive=1`；`#scores health_hp=7`、`#scores state_alive=1` | 复用 `attempt2` |

这些断言值来自 `ObserveExample.java` 对同一批实际 CLI 产物的字面结果，并由 DSB
入口变成清单断言。所有 10 个 case 都断言执行后 `mcfpp:system.stack_frame=[]`。
`raycast` 的 4 次 continue 命中中最后命中下标为 3，continue 后计数为 5；stop 只
执行第 1 个采样点，stop 后计数为 2，并断言源位置 `(0.5,64,0.5)`、yaw 90、pitch 0
的局部探针命中 `(0,64,0)`、`(-1,64,0)` 石块且第 5 个采样点为空气。

只有本次所选 case 的实际报告全部 `passed` 且无 diagnostics 时，`result.json`
的 `allPassed` 才为 `true`；该字段只覆盖本次运行。10 例总体结论由
`functional-summary.json` 汇总上述 8 例复用 + 2 例新执行得到，不代表存在一次
全新的 10 例同轮执行。

## 7. 证据位置

```text
build/inheritance-mni/dsb-check/<Attempt>/
├── manifests/<case>.dps.json          # 所选实际清单（默认 10 个）
├── packs/<case>/pack/                 # 实际生成包副本
├── packs/<case>/Imports/              # 实际依赖包副本
├── reports/all-report.json            # 适配器原始报告（所选 case）
├── reports/<case>.report.json         # 单 case 报告
├── probe-diffs/<case>.diff            # 仅观测命令的插入 diff
├── dsb-check.log
└── result.json                        # 命令、退出码、哈希、探针、诊断、allPassed
```

逐例状态汇总在
[`build/inheritance-mni/dsb-check/functional-summary.json`](../../build/inheritance-mni/dsb-check/functional-summary.json)，
它引用 `attempt1` 的 10 例清单、`attempt2`/`attempt3` 的报告和
`attempt2`/`attempt3` 的 `input-guards.json`。执行前后对全部 10 例原
`pack`/`Imports` 内容与 `pack.mcmeta`、CLI `result.json`、Java 25 运行时
（`java.exe`、`jvm.dll`、`modules`、`release`）及 DSB JAR 做了 SHA-256 守卫，
before/after 全部一致，源产物未被改写。报告以 UTF-8
写出并转义代理项，非 ASCII 名称可无损解析。

入口实际执行的命令为：

```text
java -cp <DsbJar> examples\mni\benchmark\DsbFunctionalCheck.java `
  --output <Attempt>/reports/all-report.json `
  <selected manifests>
```

适配器对每例先执行 `ManifestSchemaValidator.validateFileTree`，再读取实际
`version`/`packs`，创建 `SandboxWorld`、设置 `CommandTraceMode.OFF`，然后调用
`SandboxKt.createSandbox` 和 `ManifestRunner.INSTANCE.runInExistingSandbox`。
它不会调用 CLI 的 `check` 报告路径，也不会保留完整命令 trace；每例结果仍包含
最终 snapshot、resource summary、messages、schema/命令/断言诊断。`result.json`
额外记录 `traceMode=OFF` 和 `traceCount=0`，追踪未关闭即判失败。

失败处理：

- 适配器或脚本退出非零 → 脚本保留报告/日志/诊断并以非零退出；
- 任一 case `passed=false` → 同样失败，不把失败写成通过；
- 报告里出现 diagnostics、schema 失败或追踪未关闭 → 该 case 失败，原始诊断保留；
- 报告缺失或无法解析 → 失败，不生成“通过”结论。

## 8. 与旧游戏内基准的关系

- 本页原来的专用服务器、`/perf`、MSPT、预热和固定负载流程已被本 DSB 功能验收
  取代。按用户要求，不再需要性能数据。
- [`prepare-benchmark.ps1`](../../examples/mni/benchmark/prepare-benchmark.ps1)
  的服务器包装流程保留为可选历史工具；它不属于本验收，也不应被用来声称性能。
- 任何未实际执行 DSB 的 case 都保持未通过；未运行游戏不构成功能失败，但也不能
  产生游戏内 ticks/MSPT/耗时结论。

## 9. 限制

- DSB 不是 Minecraft 服务端；它验证数据包可见状态、函数/回调控制流、执行上下文
  和栈帧，不验证客户端、网络、实体 AI、物理或真实服务器线程。
- 本入口不测游戏性能，不生成 MSPT/耗时/方差，也不把 DSB 墙钟时间称作游戏性能。
- 全量 `#minecraft:load` 会执行真实生成器的注册和 `math` 初始化；如果这些命令在
  DSB 下出现缺失资源、unsupported 或命令错误，入口会如实失败并保留报告，不会
  静默跳过整个 load 或伪造算法结果。
- 本结论只覆盖这 10 个示例 case 的功能验收。编译器导出字符串后端已由
  [`runtime-string-dsb/attempt2/result.json`](../../build/inheritance-mni/runtime-string-dsb/attempt2/result.json)
  的实际 DSB 通过记录覆盖（0 diagnostics）；全量与定向功能回归的合并结果见
  [`regression-results.md`](regression-results.md)。
- 16MB 上限只用于容纳功能证据所需的最终快照，不是性能目标、吞吐量或任何游戏
  性能指标；命令数、递归深度、tick 和输出上限未因此放宽。

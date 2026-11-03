# MNI 示例 DSB 功能验收

本目录的 [`verify-dsb.ps1`](verify-dsb.ps1) 使用
[`DsbFunctionalCheck.java`](DsbFunctionalCheck.java) 调用 Datapack Sandbox（DSB）
26.3 的公共运行时 API，默认加载
`build/inheritance-mni/actual-examples-cli-check/attempt1/result.json`
中记录的 10 个**实际** Java/Kotlin 示例产物，逐例断言功能结果；也可用
`-Cases` 只重跑指定 case。它是功能验收，**不是性能基准**。

当前 10 个 Java/Kotlin 示例 case 在 DSB 26.3 下全部功能 PASS：其中 8 例复用
`attempt2` 的既有通过证据，`repeat-Kotlin`/`repeat-Java` 2 例由 `attempt3` 新执行
通过。逐例结果汇总见
[`functional-summary.json`](../../../build/inheritance-mni/dsb-check/functional-summary.json)。

> **性能测试和真实游戏均已按用户要求取消，不是交付门槛。** 本入口不运行游戏、
> 不测 ticks/MSPT/耗时，也不生成预热、重复轮次、固定负载或基线数据。DSB 是
> 确定性解释器，其运行时间不能称为游戏性能。

## 运行

在仓库根目录执行（默认值已指向当前可用环境）：

```powershell
powershell -NoProfile -ExecutionPolicy Bypass `
  -File examples\mni\benchmark\verify-dsb.ps1 `
  -DsbJar "F:\Project_MCFPP\mcfpp-language-support\intellij-plugin\src\main\resources\dps\datapack-sandbox-cli.jar" `
  -JavaExe "F:\Java\jdk-25.0.4\bin\java.exe" `
  -CliEvidenceRoot "build\inheritance-mni\actual-examples-cli-check\attempt1" `
  -Attempt attempt4
```

只跑指定的两个 case（其余 8 个不复制、不生成清单、不执行；`-Cases` 接
PowerShell 数组）：

```powershell
& ./examples/mni/benchmark/verify-dsb.ps1 -Cases @('repeat-Kotlin','repeat-Java') -Attempt attempt3
```

第二个代码块就是 `attempt3` 实际执行的命令；`attempt3` 目录已存在，再次复跑请换新的
`-Attempt`。全量命令使用 `attempt4` 也不代表存在一次全新的 10/10 执行：当前
10/10 是 8 例复用 + 2 例新执行并汇总的结果。

| 参数 | 默认值 | 含义 |
| --- | --- | --- |
| `-DsbJar` | 上面的本地 DSB CLI JAR | 实际使用的 DSB 26.3 CLI/JAR |
| `-JavaExe` | `F:\Java\jdk-25.0.4\bin\java.exe` | 启动 DSB 的 Java 25 |
| `-Adapter` | `examples/mni/benchmark/DsbFunctionalCheck.java` | 关闭命令追踪的 DSB 公共 API 适配器 |
| `-CliEvidenceRoot` | `build/inheritance-mni/actual-examples-cli-check/attempt1` | 含 `result.json` 的示例产物证据根 |
| `-Attempt` | `attempt2`（脚本内默认；已存在） | 输出目录名；已存在时拒绝复用，实际运行请显式传新名 |
| `-Cases` | 全部 10 个 case | 可选 case id 数组，如 `@('repeat-Kotlin','repeat-Java')`；未知或重复值在复制前失败 |

CLI 证据 `result.json` 必须仍然正好包含 10 个 case（5 个示例 × Kotlin/Java），且
`dataRoot` 指向真实生成的 `project/build/mcfpp/data`。默认 `-Cases` 运行全部；
指定后只对所列 case 复制、生成清单和执行，所有原有断言保持不变。已有
`dsb-check/attempt1` 是失败诊断证据，不应覆盖；`attempt2` 记录了 8 例通过，
`attempt3` 只新执行 repeat 两例。新运行请使用新的 `-Attempt`。

## 当前证据状态

10 个 case 在 DSB 26.3 下全部功能 PASS，分为两部分：

- **复用证据（8 例）**：`damage`、`nbt-reference`、`raycast`、`score-mapping`
  的 Kotlin/Java 共 8 例沿用 `dsb-check/attempt2` 的实际原生通过报告；探针、
  断言值和空调用帧栈均未改写。
- **新执行（2 例）**：`repeat-Kotlin`、`repeat-Java` 由 `dsb-check/attempt3`
  使用 16MB 功能快照上限实际复跑通过，断言 `10/8/9/22`，最终
  `stack_frame=[]`。`attempt3` 只选择这两例，未重跑其余 8 例。

`attempt2` 中 repeat 两例分别因 10594860、10562150 字节的最终快照超过旧 10MB
上限而失败；适配器只把该上限提高到 16MB，`MAX_COMMANDS`、函数递归深度、tick、
输出上限和全部断言均未改变。16MB 只是功能证据快照的捕获容量，不是性能阈值。
逐例汇总和证据路径见
[`functional-summary.json`](../../../build/inheritance-mni/dsb-check/functional-summary.json)。
执行前后对全部 10 例原数据包/`Imports` 内容与 `pack.mcmeta`、CLI `result.json`、
Java 25 运行时（`java.exe`、`jvm.dll`、`modules`、`release`）和 DSB JAR 做了
SHA-256 守卫；`attempt2` 与 `attempt3` 的 `input-guards.json` before/after 全部
一致，源产物未被改写。

## 检查内容

对每个 case，脚本把原数据包和它旁边的 `Imports` 复制到
`build/inheritance-mni/dsb-check/<Attempt>`，然后：

1. 用实际 `dataRoot` 的父目录作为数据包根，连同实际 `Imports` 一起加载。
2. 通过数据包自己的 `#minecraft:load` 执行真实 `<namespace>:load` / `init`；
   另外供给入口函数实际使用的 objective：
   `mcfpp_default`、`mcfpp_temp`、`mcfpp_boolean`、`health_hp`、`state_alive`。
   `health_hp`/`state_alive` 是 score-mapping 需要的、生成器 `load` 不会注册的外部
   objective。
3. 在固定上下文执行实际入口：

   ```mcfunction
   execute in minecraft:overworld positioned 0.5 64.0 0.5 rotated 90 0 run function <namespace>:main
   ```

4. 只在**副本**中、每个真实调用者帧最终 pop 之前插入带
   `# DSB OBSERVATION (probe-only)` 标记的观测命令，读取结果后仍由原命令 pop。
   `score-mapping` 的 `main` 以 `return run function if_branch_53/else_branch_57`
   尾调用结束，脚本递归进入两个真正的 terminal helper，而**不是**在 `main` 中
   非最终的 pop 前插桩。其他 8 个 case 的 `main` 末尾就是最终 pop。
5. 断言 `mcfpp:system.stack_frame` 为空、实际结果值、ray 的固定位置/旋转探针和
   石块 fixture；源数据包从不修改。

## 断言值

断言值来自 [`ObserveExample.java`](../../../build/inheritance-mni/actual-examples-cli-check/ObserveExample.java)
对同一批实际产物的字面结果：

| 示例 | 实际断言 |
| --- | --- |
| `damage` | `health=70`、`low=0`、`alias=0` |
| `repeat` | `total=10`、`guarded=8`、`tail=9`、`nested=22` |
| `nbt-reference` | `normal=70`、`aliased=10`、`untouched=10`、`indexed=15` |
| `raycast` | `continueHits=4`、`continueLast=3`、`afterContinue=5`、`stopHits=1`、`afterStop=2` |
| `score-mapping` | `loadedHealth=7`、`loadedAlive=1`，以及 `#scores health_hp=7`、`#scores state_alive=1` |

`raycast` 额外断言：源位置 `(0.5,64,0.5)`、yaw 90、pitch 0 的局部探针命中
`(0,64,0)` 和 `(-1,64,0)` 石块，并在第 5 个采样点读到空气；世界 fixture 也显式
设置这两个石块。所有 case 都断言执行后调用帧栈为空。

## DSB 调用和证据

脚本用 Java 25 source-file mode 启动适配器，一次性执行所选清单（默认 10 个）：

```text
java -cp <DsbJar> examples\mni\benchmark\DsbFunctionalCheck.java `
  --output <Attempt>/reports/all-report.json `
  <selected manifests>
```

适配器按序对每个清单：

1. 用 DSB 的 `ManifestSchemaValidator.validateFileTree` 做 schema 校验；
2. 读取实际 `version` 和相对 `packs` 路径；
3. 新建 `SandboxWorld` 并设置 **`CommandTraceMode.OFF`**；
4. 用 `SandboxKt.createSandbox(version, packs, world, null,
   UnsupportedFeatureMode.ERROR, limits)` 真正加载数据包；
5. 用 `ManifestRunner.INSTANCE.runInExistingSandbox(...)` 在同一个 sandbox 上
   执行原清单的 world/steps/assertions，`failOnMissingResources=true`、strict
   unsupported error 保持不变。

因此执行的是 DSB 原生的 world 状态机、函数/`return`/回调与断言求值，不是脚本
模拟，也不捕获完整命令追踪。`result.json` 中每例记录 `traceMode=OFF` 和
`traceCount=0`；若追踪未关闭或报告有 diagnostics，该例判失败。报告以 UTF-8
写出并转义代理项，非 ASCII 名称可无损解析。

输出结构：

```text
build/inheritance-mni/dsb-check/<Attempt>/
├── manifests/<case>.dps.json
├── packs/<case>/pack/                 # 实际产物副本
├── packs/<case>/Imports/              # 实际依赖包副本
├── reports/all-report.json            # 适配器原始报告（所选 case，默认 10 个）
├── reports/<case>.report.json         # 从 all-report 拆出的单例报告
├── probe-diffs/<case>.diff            # 仅观测命令的插入 diff
├── dsb-check.log
└── result.json                        # 哈希、探针、实际 snapshot/resource/诊断
```

适配器或脚本非零、任一 case `passed=false`、追踪未关闭，或报告出现 diagnostics
时，脚本保留原始报告/日志/诊断并以非零退出。失败 case 不会被记成通过。
`result.json` 的 `allPassed` 只针对本次所选 case；10 例总体功能结论由
[`functional-summary.json`](../../../build/inheritance-mni/dsb-check/functional-summary.json)
汇总 8 例复用 + 2 例新执行得到，不代表存在一次全量重跑。

## 边界

- DSB 不是 Minecraft 服务端；本检查只证明数据包在 DSB 26.3 profile 下的命令语义、
  调用帧和固定场景结果。它不证明游戏内表现、MSPT 或任何性能差异。
- 全量 `#minecraft:load` 会执行生成器注册和 `math` 初始化；如果真实产物在 DSB
  下出现命令/资源诊断，脚本会如实失败并保留报告，不会跳过或伪造算法。
- 本任务不要求真实游戏环境。原 `prepare-benchmark.ps1` 的服务器包装流程保留为
  可选的历史工具，不属于本验收入口，也不再被要求运行。
- 本结论只覆盖这 10 个示例 case 的功能验收。编译器导出字符串后端已由
  [`runtime-string-dsb/attempt2/result.json`](../../../build/inheritance-mni/runtime-string-dsb/attempt2/result.json)
  的实际 DSB 通过记录覆盖（0 diagnostics）；全量与定向功能回归的合并结果见
  [`regression-results.md`](../../../docs/mni/regression-results.md)，本页不声称整个项目已完成。

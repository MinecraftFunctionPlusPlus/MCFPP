# MNI 生成代码成本对照报告

本文保留 **lambda 迁移前的历史范围与证据**，没有新执行的测量结果。下述捕获累加器算法、脚本入口、基线和 CLI/DSB 记录只适用于当时产物，不证明当前语法或累加器重载已通过；迁移后真实编译/DSB 由协调者另行记录。

当前源码改用只读局部值快照和显式返回值累积：`repeat(times: int, initial: int, body: INLINE (int,int)->int): int` 每次以 `(accumulator,index)` 返回下一累加器；`raycast(samples: CONST int, initial: int, step: INLINE (int,int)->int, keepGoing: INLINE (int)->bool): int` 每次实际 stone 命中先更新累加器，再决定后续采样。原 void repeat 和 bool 回调 raycast 重载仍保留。当前示例的预期值为 repeat `10/8/9/22`，raycast 继续命中/最后下标 `4/3`、停止 `1/0`、调用者尾部 `2`；它们不是本报告新测量数据。射线两项结果来自独立遍历，不能沿用历史单次遍历命令计数。

## 状态：性能验收已取消

用户已于 2026-10-18 明确不再要求性能指标，功能优先。因此本报告**不是验收门槛，也不阻塞任何示例**；此前“待真实测量（pending）”的要求作废。本文没有记录任何实际命令数、文件字节数、栈操作数或编译耗时，也不据此声称任何性能结论。

生成代码测量脚本保留为可选、未测量的历史工具。它们可以帮助比较实现，但运行或不运行都不改变功能验收；本文不把未运行的结果写成通过。

功能验收入口是 Datapack Sandbox（DSB）检查，而不是性能基准：[`examples/mni/benchmark/README.md`](../../examples/mni/benchmark/README.md)、[`verify-dsb.ps1`](../../examples/mni/benchmark/verify-dsb.ps1)。保存的 [`functional-summary.json`](../../build/inheritance-mni/dsb-check/functional-summary.json) 记录 10 个 Java/Kotlin 示例 case 在 DSB 26.3 下全部功能 PASS：8 例复用 `attempt2`，`repeat` 2 例由 `attempt3` 新执行；原产物、CLI 证据和运行时/JAR 哈希未变。本文不重复逐例计数；统一回归与最终验收见 [`docs/mni/regression-results.md`](regression-results.md)、[`docs/implementation-review/acceptance.md`](../implementation-review/acceptance.md)。

**性能与真实游戏验收已按用户要求取消。** DSB 只是确定性功能解释器，不测 ticks/MSPT/耗时，也不生成预热、固定负载或基线；其运行时间不能写成游戏性能。全量功能运行的 6 项失败已由受影响定向运行修复，编译器导出字符串的 DSB 也已实际通过；合并证据与复用边界见 [regression-results.md](regression-results.md)，本文不重复统计。

## 原测量范围

四个领域、五个场景、三种实现：

| 领域 | 场景 | 行为等价目标 |
| --- | --- | --- |
| damage | `damagecase` | `100 - 30 == 70`，REF 写回调用者位置 |
| repeat | `repeatcase` | 历史可变捕获累加器，`0+1+2+3+4 == 10`；当前改用显式返回累积 |
| raycast | `raycontinuecase` | 5 个 0.5 步长采样点，continue 命中 4 次、最后下标 3、5 次 block 测试 |
| raycast | `raystopcase` | 同一算法 stop 首次命中后停止，命中 1 次、最后下标 0、1 次 block 测试 |
| score-mapping | `scorecase` | `save` 后 `load`：int `7` 往返，bool `true` 存 `1` 读回真 |

三种实现：

1. **handwritten**：手写专用命令映射，固定 holder、展开计数/采样和调用点已知 block 位置。
2. **runtime**：常规运行时库加参数适配；调用者/库之间真实 copy-in/copy-out，库压入适配帧，算法可复用，repeat/ray 从复制进来的调用者值初始化状态，damage 使用通用下界 clamp，repeat/ray 通过 callback 选择器或策略配置分派。
3. **mni**：真实 CLI 使用四个已交付字面 Kotlin provider 生成 `kernels.mcfpp` 的命令；`ComparisonProvider.java` 只注入 provider，不重写算法。

## 原计划指标（保留口径，不再作为验收要求）

如果以后有人可选地运行测量，脚本仍按以下口径记录：

- 实际执行命令数（非注释函数体行；`execute` 包装与内部命令不重复计数）。
- 跨调用者帧的参数搬运、存储间复制、直接 NBT 命令与 `execute store ... storage` 写入；跨帧数据搬运包括普通成员调用适配器，不只包含宏参数适配。
- 实际栈 push/pop，以及普通 caller frame 之外的 caller-beyond 帧（包括普通生成的 load/save 成员调用帧，不能全部视为宏适配开销）。
- 每个 kernel 的可达 function 文件数与 UTF-8 字节数；共享工程总量单独记录。
- 一次真实 CLI 工程级编译墙钟耗时（包含 JVM 启动；不虚构每 kernel 耗时）。
- 源文件与生成文件的 SHA-256。
- 五种场景的三方观测值、射线 block 测试次数和 score objective 值。

## 可选测量入口

以下为历史命令（非验收要求）；脚本/基线未在本次文档编辑中迁移或验证，不能据此声称适配当前 lambda 语义：

```powershell
pwsh -NoProfile -File examples/mni/comparison/verify-comparison.ps1 `
  -RuntimeJar build/libs/MCFPP-1.0.2-SNAPSHOT.jar `
  -ProviderJar build/inheritance-mni/actual-examples-cli-check/attempt1/provider-build/libs/mni-example-providers.jar
```

脚本会用 `-RuntimeJar` / `-ProviderJar` 编译 `ComparisonProvider`，运行一次真实 CLI 生成五个 kernel，再用严格 `ContextCommandExecutor` 执行实际 MNI 命令和 `baselines.json` 中的 handwritten/runtime 命令。观测 probe 只存在于内存副本中，插入到真实 caller frame pop 之前，计数时统一排除；生成文件不修改。本次文档修订没有运行该脚本。

可选的证据路径（`<Attempt>` 默认为 `attempt1`）：

```text
build/inheritance-mni/comparison-check/<Attempt>/stage/project/build/mcfpp/data/example.comparison/function   # MNI 源数据包
build/inheritance-mni/comparison-check/<Attempt>/baseline-packs/<scenario.id>/<variant>/data                    # 基线数据包
```

基线数据包由 `baselines.json` 的字面函数映射物化，包含 `pack.mcmeta`（`min_format`/`max_format` 121）、UTF-8 无 BOM 且带结尾换行的 `.mcfunction` 文件、初始化 `comparison`、`mcfpp_default`、`health_hp`、`state_alive` 的 `load` 函数和 `minecraft:load` 标签；实际包路径、entry、load 函数和标签记录在 `result.json` 的 `baselinePacks` 字段。

## 口径与限制

- 命令条数不是速度，也不是游戏内 tick 成本；不预设加速比例。
- `ContextCommandExecutor` 是严格测试解释器，不是 Minecraft 实机；不声称游戏内运行结果。
- 编译耗时只有工程级一次 CLI 墙钟值，包含 JVM 启动和共享 stdlib 生成，不能解释为每 kernel 编译时间。
- handwritten/runtime 基线是实际执行的命令映射，不是编译器生成产物；MNI 变体来自真实 CLI 输出。
- 脚本物化含 `load`/标签的基线数据包，但测量本身不执行数据包 `load`/`init`/`tick` 或 objective 注册；只执行五个 kernel 入口和三方基线函数。
- 可达文件/字节按字面 `function` 调用静态遍历，包含未走到的回调/备用分派 helper；probe 不计入。
- 性能验收取消后，本报告的旧指标只保留为可选的实现对照口径，不代表功能正确性、游戏内表现或验收完成。

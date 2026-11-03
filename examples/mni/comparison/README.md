# MNI 三方命令成本对照

本目录提供四个领域、五个场景的三方真实命令成本对照入口：

| 领域 | 场景（kernel） | 行为等价观测 |
| --- | --- | --- |
| damage | `damagecase` | `100 - 30 == 70`，写回调用者位置 |
| repeat | `repeatcase` | 带捕获的同步循环，`0+1+2+3+4 == 10` |
| raycast | `raycontinuecase` | 5 个 0.5 步长采样点，continue 策略命中 4 次、最后下标 3、5 次 block 测试 |
| raycast | `raystopcase` | 同一算法 stop 策略首次命中后停止，命中 1 次、最后下标 0、1 次 block 测试 |
| score-mapping | `scorecase` | `save` 后 `load`：int `7` 往返，bool `true` 保存为 `1` 并读回真 |

三方变体：

1. **handwritten**：`baselines.json` 中手写的专用命令映射。直接使用固定 holder、展开的计数/采样和调用点已知的 block 位置；damage 在减伤后执行真实的下界 clamp。
2. **runtime**：`baselines.json` 中的常规运行时库。调用者把参数写入自己的栈帧，库函数压入自己的适配帧，执行真正的 copy-in / 通用算法 / copy-out；damage 通过通用条件 helper 执行下界 clamp，repeat/ray 从复制进来的调用者值初始化算法状态，repeat 通过 callback 选择器调用回调，ray 通过策略配置选择 stop/continue 回调。没有把最终结果硬编码成命令。
3. **mni**：`kernels.mcfpp` 由真实 CLI 使用四个已交付的字面 Kotlin provider 生成。`ComparisonProvider.java` 只做 `injectedBy` 引导，不复制或重写任何宏算法；score provider 注册其原有声明处理器。

## 文件

```text
examples/mni/comparison/
├── ComparisonProvider.java   # 极小的 provider 注入引导
├── kernels.mcfpp             # 五个小 kernel + ScoreMapping Player
├── baselines.json            # handwritten/runtime 的实际命令映射
├── MeasureComparison.java    # 严格执行、等价性检查和指标采集
├── verify-comparison.ps1     # 一次 CLI 编译 + 实际命令执行 + 报告生成
└── README.md
```

`verify-comparison.ps1` 接受显式 `-RuntimeJar` 和 `-ProviderJar`，并在仓库根目录解析以下实际输入：

```text
build/libs/MCFPP-1.0.2-SNAPSHOT.jar
build/inheritance-mni/actual-examples-cli-check/attempt1/provider-build/libs/mni-example-providers.jar
build/classes/kotlin/test                              # 本次 ContextCommandExecutor 编译结果
examples/mni/comparison/baselines.json                 # handwritten/runtime 字面函数映射
```

脚本不假定这些输入存在；缺失时在对应步骤明确失败，不会用旧解释器或旧产物伪造通过。MNI 源数据包由真实 CLI 在本次证据目录下生成：

```text
build/inheritance-mni/comparison-check/<Attempt>/stage/project/build/mcfpp/data/example.comparison/function
```

基线数据包同样物化到本次证据目录，而不是只复制 JSON：

```text
build/inheritance-mni/comparison-check/<Attempt>/baseline-packs/<scenario.id>/<variant>/data
```

每个基线包包含 `pack.mcmeta`（`min_format`/`max_format` 121）、按 `comparison.handwritten` 或 `comparison.runtime` 命名空间写出的 `.mcfunction` 文件（UTF-8 无 BOM、结尾换行），以及初始化 `comparison`、`mcfpp_default`、`health_hp`、`state_alive` objective 的 `load` 函数和 `minecraft:load` 标签。实际包路径、entry、load 函数和标签记录在 `result.json` 的 `baselinePacks` 中。

## 生成真实证据的唯一根命令

在仓库根目录执行（PowerShell 7）：

```powershell
pwsh -NoProfile -File examples/mni/comparison/verify-comparison.ps1 `
  -RuntimeJar build/libs/MCFPP-1.0.2-SNAPSHOT.jar `
  -ProviderJar build/inheritance-mni/actual-examples-cli-check/attempt1/provider-build/libs/mni-example-providers.jar
```

只有 Windows PowerShell 时可用 `powershell -NoProfile -ExecutionPolicy Bypass -File ...` 替代 `pwsh -NoProfile -File ...`。

可选参数：`-Attempt <name>` 选择独立的证据目录（默认 `attempt1`，目录已存在会拒绝覆盖）；`-ReportPath <path>` 覆盖报告路径（默认 `docs/mni/generated-code-report.md`）；`-CommandBudget <n>` 覆盖严格执行器的命令预算（默认 `20000`）。

脚本要求 `build/classes/kotlin/test` 已包含本次 `ContextCommandExecutor.executedFunctionLines` 的编译结果；若不存在会明确失败，不会用旧解释器伪造通过。

脚本实际执行：

1. 用 `javac` 针对运行时 JAR 和字面 provider JAR 编译 `ComparisonProvider.java`，只把它自己的 class 打进 bootstrap JAR；不复制库。
2. 在 `build/inheritance-mni/comparison-check/<attempt>/stage` 下暂存一个 `example.comparison` 工程，写入 `kernels.mcfpp`、`injectedBy` 指令和同时包含 provider/bootstrap 的 `mcfpp.toml`。
3. 读取本次复制的 `baselines.json`，把 handwritten/runtime 的字面函数映射逐个物化为 `baseline-packs/<scenario.id>/<variant>` 数据包，并记录实际路径与 entry。
4. 用 `java -jar <RuntimeJar> build --project mcfpp.toml` 只执行一次真实 CLI，捕获包含 JVM 启动的工程级墙钟耗时、exit code、错误/警告数和日志。
5. 统计实际生成的 `.mcfunction` 文件数、UTF-8 字节数、源文件哈希和生成文件哈希。
6. 针对运行时 JAR 和 `build/classes/kotlin/test` 编译 `MeasureComparison.java`，再用同一套 classpath 运行它。
7. `MeasureComparison` 读取实际生成的 function 文件，并在每个场景中分别执行 MNI、handwritten、runtime 三方命令。所有执行都通过严格的 `ContextCommandExecutor`；观测 probe 只插入到内存副本中真实 caller frame pop 之前，报告前会检查结果等价性。
8. 成功后才写 `measurement.json` 并生成 Markdown 报告；`result.json` 无论成功或失败都会保留（失败时包含 `error`），但失败不会生成成功报告。

失败时脚本以非零状态退出，不会生成成功报告；`result.json` 仍保留失败信息。当前 `docs/mni/generated-code-report.md` 在真实测量执行前保持 pending。

## 指标定义

- **命令行 / commandLines**：`ContextCommandExecutor.executedFunctionLines` 中实际执行的非注释函数体行数。每个 `execute` 包装行只计一次，绝不与其内部命令重复计数；根部的合成 `function <entry>` 调用已剔除。
- **跨帧搬运 / parameterMoves**：实际执行的、跨 `stack_frame[1]` 的 `data modify ... from storage` copy-in/copy-out 行。跨帧数据搬运包含普通成员调用适配器，不只包含宏参数适配；它是次数，不是时间。
- **存储复制 / storageCopies**：实际执行的所有 `data modify ... from storage` 存储间复制，包括同一帧内的局部搬运。
- **NBT 命令 / nbtCommands**：实际执行的直接 `data ...` 命令，加上 `execute store ... storage` 写入；表示真实 NBT 读写/搬运次数，不是时间。
- **栈 push/pop**：实际执行的普通 caller frame 压栈/出栈行。每种变体都有 1 个普通 caller frame；`extraAdapterFrames = stackPushes - 1` 表示 caller-beyond 帧（包括普通生成的 load/save 成员调用帧），不隐藏调用者成本，也不能解释为全部都是宏适配开销。
- **可达文件/字节**：从 entry 出发静态遍历字面 `function` 调用，包含未走到的 callback / 备用分派 helper。生成文件按磁盘 UTF-8 大小计，baseline 按带一个结尾换行的命令映射序列化大小计；probe 不计入。
- **编译耗时**：只有一次真实 CLI 工程级墙钟值（含 JVM 启动），覆盖五个 kernel 和共享 stdlib；不拆成虚构的每 kernel 耗时。

## 限制

- 命令条数不是速度，也不是游戏内 tick 成本；本目录不预设加速比例，不添加性能阈值或分数。
- `ContextCommandExecutor` 是仓库内的严格测试解释器，不是 Minecraft 实机。本对照不声称游戏内运行结论。
- 手写和运行时基线是 `baselines.json` 中实际执行的命令映射，不是编译器产物；只有 MNI 变体来自真实 CLI 输出。
- 脚本物化含 `load`/标签的基线数据包，但测量本身不执行数据包 `load`/`init`/`tick` 或 objective 注册；只执行五个 kernel 入口和三方基线函数。
- 本页在源码 authoring 阶段不运行 Gradle、编译器、CLI 或测试，也不提供任何未经实际脚本记录的命令数、字节数或耗时。

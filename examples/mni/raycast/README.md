# MNI `raycast` 示例

`raycast(samples, body)` 演示一个固定步长的方块射线：同一个 provider 算法分别接入“命中后停止”和“命中后继续”两种源码 INLINE 回调。

```text
raycast(samples: CONST int, body: (int)->bool)
raycast(samples: CONST int, initial: int, step: (int,int)->int, keepGoing: (int)->bool): int
```

- `samples` 是 `CONST int`。宿主在构造展开时读取这个闭合值，并按采样点展开；没有运行时循环计数，也没有把采样次数解释成不同方块数量。负数在宿主构造期被拒绝。
- 步长固定为 **0.5 方块**（`SAMPLE_STEP = 0.5`），沿当前执行上下文的本地前向轴前进。位置完全由公开句柄构造：`currentPosition()`、`currentRotation()`、`localOffset(...)`、`blockAt(...)`、`blockMatches(...)`。没有可变位置句柄，也没有动态坐标 API；`localOffset` 的偏移量是宿主侧字面量。
- 每个采样点的顺序是：先检查 provider 自己的 `running` 局部量，再进入 `at(samplePoint)`，在当前采样点真实执行一次 `if block ~ ~ ~ minecraft:stone`。只有真实命中才把源码回调作为 INLINE 插入，并把当前采样下标作为 VALUE 实参传入。
- 回调返回 `bool`，provider 把它写回自己的 `running` 局部量。`true` 继续后续采样，`false` 结束本次射线的后续采样。回调里的 `return` 只退出当前这次内联区域，不会退出 ray 宏调用者；ray 调用之后的调用者语句仍会执行。
- 采样点语义是“点”，不是“去重后的方块”：同一方块内的连续两个采样点会被测试两次，也可以命中回调两次。射线的命中次数不等于不同方块数量。
- 不生成运行时 `raycast` 宏函数，不生成参数区，也不按回调编号分派。`at`/`if` 等正常 lowering 仍会生成 `mni/...` 控制流 helper，这是源码控制流，不是宏运行时栈。

本示例使用 `currentRotation()`：lowering 采用 `positioned ^ ^ ^`，不额外发出 `rotated`，因此函数入口的 ambient rotation 被保留，即使入口没有实体执行者、不能假设 `@s`。这不是 `localOffset` 的普遍规则：显式绝对 rotation 会折算成世界轴偏移；实体 rotation 形式可能先 `rotated as @s`，只在父 rotation 可忠实恢复时补回，无法恢复的形状会在提交前拒绝。

## 目录

```text
examples/mni/raycast/
├── build --project mcfpp.toml                         # 从 build/raycast-macro.jar 加载 provider
├── project/src/.mcfpp                 # injectedBy 指令（默认启用 Kotlin provider）
├── project/src/main.mcfpp             # 同一宏的停止/继续两种源码回调
├── provider/java/.../JavaRaycastMacro.java
└── provider/kotlin/.../KotlinRaycastMacro.kt
```

`main.mcfpp` 使用显式累积重载：实际命中后先计算 `step(accumulator, index)`，再计算 `keepGoing(index)`；false 停止后续采样。局部捕获为创建时只读快照，状态由返回值传递：

- 继续策略：`raycast(5, 0, { hits, i -> hits + 1 }, { true })` 返回命中数；独立返回值遍历 `raycast(5, -1, { last, i -> i }, { true })` 返回最后采样下标。
- 停止策略使用 `{ false }`，首次命中更新累积值后跳过后续采样；`afterStop = stopHits + 1` 仍执行。

在测试世界（起点 `(0.5, 64.0, 0.5)`、yaw 90、0.5 步长，`x=0` 与 `x=-1` 两个石块）中，五个采样点为 `x = 0.5, 0.0, -0.5, -1.0, -1.5`：

- 继续策略：5 次 block 测试，4 次命中；命中落在 2 个不同方块上（每个方块被连续两个采样点命中）。`continueHits = 4`，`continueLast = 3`，`afterContinue = 5`。
- 停止策略：只执行第 1 次 block 测试并命中；`stopHits = 1`，`afterStop = 2`。

真实游戏内结果取决于实际世界和入口上下文；上面数字是 `MniRaycastTest` 的确定性测试世界结果，不是未执行过的实机结论。

## 构建 provider

在仓库根目录先构建编译器（JDK 21）：

```bash
./gradlew shadowJar
```

下面的命令使用 `build/libs/MCFPP-1.0.2-SNAPSHOT.jar`。

Java：

```bash
javac -cp build/libs/MCFPP-1.0.2-SNAPSHOT.jar \
  -d examples/mni/raycast/build/classes \
  examples/mni/raycast/provider/java/example/mniraycast/JavaRaycastMacro.java

jar --create --file examples/mni/raycast/build/raycast-macro.jar \
  -C examples/mni/raycast/build/classes .
```

Kotlin：

```bash
kotlinc examples/mni/raycast/provider/kotlin \
  -cp build/libs/MCFPP-1.0.2-SNAPSHOT.jar \
  -d examples/mni/raycast/build/classes

jar --create --file examples/mni/raycast/build/raycast-macro.jar \
  -C examples/mni/raycast/build/classes .
```

`project/src/.mcfpp` 默认注入 Kotlin provider。使用 Java provider 时，把第一行替换为：

```text
//> injectedBy example.mniraycast.JavaRaycastMacro
```

同一个命名空间内不能同时注册两个同名 `raycast` 宏。`injectedBy` 指令必须位于文件开头。

## 运行

`mcfpp.toml` 的 `sourcePath`、`targetPath` 和 `jars` 都相对项目配置文件所在目录。可在示例目录内运行：

```bash
cd examples/mni/raycast
java -jar ../../../build/libs/MCFPP-1.0.2-SNAPSHOT.jar build --project mcfpp.toml
```

产物写入 `examples/mni/raycast/project/build`。

`main.mcfpp` 使用函数入口的实际位置/旋转。要在固定坐标验证，可用类似命令建立入口上下文后再调用：

```mcfunction
execute positioned 0.5 64.0 0.5 rotated 90 0 run function example.raycast:main
```

具体世界中的石头位置由数据包环境决定；示例源码不硬编码世界方块，也不把测试器中的方块表写进 provider。

## 测试

`src/test/kotlin/top/mcfpp/test/MniRaycastTest.kt` 使用真实 `@MNIMacro` provider 和真实 MCFPP 源编译，不手工构造 Code/Plan。测试通过本批次交付的严格生成命令执行器 `src/test/kotlin/top/mcfpp/test/util/ContextCommandExecutor.kt` 执行实际注册的 mcfunctions，覆盖：

- 同一 provider 算法接“继续”和“停止”两种源码回调。
- 真实 `if block ~ ~ ~ minecraft:stone` 的命中/未命中，以及同一方块被连续采样点多次命中。
- 回调收到当前累积值和采样下标；局部捕获采用只读值快照，累积通过返回值完成。
- 停止策略在首次命中后跳过剩余采样，但 ray 调用之后的调用者尾部仍执行。
- `return true` 提前返回只退出一次内联插入，外层采样继续。
- 采样点位置、环境旋转（yaw 90）和“无实体执行者”上下文正确；执行命令中没有重新发出 `rotated`。
- 没有运行时 `raycast` 函数、没有宏编号分派，生成的上下文 helper 不接收回调/参数区。

## 验证状态

以下已交付条目是迁移前实现的历史证据，不能视作新重载/捕获语义的运行结论。当前真实源码检查入口为 `MniLambdaMigrationTest`，报告写入 `build/lambda/`：

- `attempt1` 的 Java/Kotlin provider 编译和真实 CLI 编译均通过，两个 case 都是 `cliExitCode=0`、`zeroErrorsWarnings=true`。
- 同一批观察对两个 case 都返回 `observationExitCode=0`：继续策略 5 次 block 测试、4 次命中、最后下标 3，停止策略 1 次命中、`afterStop=2`。
- `approved-generated-context-batch14` 中 `MniRaycastTest` 3/3 通过，覆盖两种源码回调、固定位置/环境旋转（yaw 90）和“无实体执行者”上下文。
- **DSB 功能验收**：`raycast-Kotlin`/`raycast-Java` 复用 `attempt2` 的原生 DSB 通过报告，断言 `continueHits=4`、`continueLast=3`、`afterContinue=5`、`stopHits=1`、`afterStop=2`，并验证固定位置/旋转探针和石块 fixture；执行后 `mcfpp:system.stack_frame=[]`。逐例汇总见 [`functional-summary.json`](../../../build/inheritance-mni/dsb-check/functional-summary.json)。
- 全量入口是 [`verify-dsb.ps1`](../benchmark/verify-dsb.ps1)，使用说明见 [`benchmark/README.md`](../benchmark/README.md)。复跑必须显式传新的 `-Attempt`，已有 `attempt1`–`attempt3` 均保留、不覆盖；例如：

```powershell
& ./examples/mni/benchmark/verify-dsb.ps1 -Cases @('raycast-Kotlin','raycast-Java') -Attempt attempt4
```

DSB 不是 Minecraft 实机，也不测性能；性能与真实游戏验收已按用户要求取消，不是本示例的完成门槛。统一回归与最终验收见 [`regression-results.md`](../../../docs/mni/regression-results.md)、[`acceptance.md`](../../../docs/implementation-review/acceptance.md)。

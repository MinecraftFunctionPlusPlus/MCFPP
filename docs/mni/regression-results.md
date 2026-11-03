# MNI 功能回归结果（2026-10-18）

本文发布 MNI 最终功能回归的统一说明。证据来自已保存的协调者运行记录；本次文档修订**没有重新运行**任何 Gradle、JUnit、CLI 或 DSB 命令。本文只复用下列实际产物，不以“新跑了一次全量测试”表述。

## 1. 结论

| 证据 | 实际结果 | 状态 |
|---|---|---|
| 全量功能运行 `full-functional-check/attempt2` | 204 个测试类、2007 项测试：2001 通过、6 失败、0 error、0 skipped | 之前的全量运行；6 项失败已由定向运行解释并修复 |
| 定向修复运行 `functional-repair-check/attempt1` | 10 个测试类、136 项测试：0 失败、0 error、0 skipped | 新执行 |
| 复用既有通过测试 | 1873 项 | 在复核 8 个源文件增量后复用，未重跑 |
| 合并证据 | 2009 项 = 136 项新执行 + 1873 项之前通过 | **合并证据，不是一次新的 2009 项全量运行** |
| 十例 Java/Kotlin 示例原生 DSB | 10/10 功能 PASS：8 例复用、2 例新执行 | 见 [`functional-summary.json`](../../build/inheritance-mni/dsb-check/functional-summary.json) |
| 编译器导出字符串 DSB | 1 个 case PASS，0 diagnostics | `runtime-string-dsb/attempt2` |

全量运行的 2007 项包含 6 项失败；定向运行的 136 项包含全部 6 项失败所在测试类，并额外包含 2 项新的字符串字面量测试。合并计数因此比旧全量多 2 项，但这不是“重新执行全部 2009 项”的同轮结果。统一事实文件为 [`integrated-functional-summary.json`](../../build/inheritance-mni/integrated-functional-summary.json)。

## 2. 复跑命令

以下命令均以仓库根目录为工作目录，使用 PowerShell 7 的 `pwsh`（已保存运行记录的实际解释器）。带 `-Attempt` 的脚本必须传入尚不存在的目录名，已保存的目录不会被覆盖。

**未来可复现的全量命令（本次文档修订未执行，也不是新的 2009 项同轮运行）**：不使用旧 `full-functional-check.ps1`，因为它固定使用 Gradle 默认的 512m 测试堆，曾导致 NBT 测试出现 `Java heap space`。下面的命令直接调用同一个 Gradle `check shadowJar`，启用 `functional-repair.init.gradle` 把测试 JVM 上限设为 `1536m`，并保留已保存定向运行使用的 JDK 21、离线、无守护进程和 in-process 设置；不带 `--tests` 过滤。

```powershell
$env:JAVA_HOME = 'F:/Java/ms-21.0.12.1'
$env:GRADLE_USER_HOME = 'F:/DevCache/.gradle'
$env:JAVA_TOOL_OPTIONS = '-Djdk.net.unixdomain.tmpdir=F:\Project_MCFPP\MCFPP\build'
$env:JDK_JAVA_OPTIONS = '-XX:TieredStopAtLevel=1'
$env:Path = "$env:JAVA_HOME/bin;$env:Path"
.\gradlew.bat --offline --no-daemon `
  "-Dorg.gradle.jvmargs=-Xmx3g -XX:MaxMetaspaceSize=1g" `
  -Pkotlin.compiler.execution.strategy=in-process `
  -I build/inheritance-mni/functional-repair.init.gradle `
  check shadowJar --continue --console=plain --stacktrace
```

定向修复运行分为“冻结输入”和“执行”两步，使用同一个新名称：

```powershell
$repairAttempt = 'review-20261018-1'
pwsh -NoProfile -ExecutionPolicy Bypass `
  -File build\inheritance-mni\functional-repair-check.ps1 `
  -Attempt $repairAttempt -CaptureInputs

pwsh -NoProfile -ExecutionPolicy Bypass `
  -File build\inheritance-mni\functional-repair-check.ps1 `
  -Attempt $repairAttempt
```

保存证据对应 `full-functional-check/attempt2`、`functional-repair-check/attempt1-inputs.json` 与 `functional-repair-check/attempt1`。`-CaptureInputs` 先写出冻结清单；执行时若源码/配置已变化会拒绝运行，并在结束后复核清单。

编译器导出字符串 DSB：

```powershell
$stringDsbAttempt = 'review-20261018-1'
pwsh -NoProfile -ExecutionPolicy Bypass `
  -File build\inheritance-mni\run-runtime-string-dsb.ps1 `
  -Attempt $stringDsbAttempt
```

十例示例 DSB 的命令、探针和历史说明保留在 [`examples/mni/benchmark/README.md`](../../examples/mni/benchmark/README.md)。示例 DSB 的 `dsb-check/attempt2` 是 8 例复用证据、`dsb-check/attempt3` 只新执行 repeat 两例，不能把两者说成同一轮十例执行。

## 3. 六个失败的解决说明

全量运行中的 6 项失败均已在本轮定向运行中通过；失败记录保留为历史证据，不再是当前缺口。

| 失败测试 | 全量运行中的错误 | 当前处理与证据 |
|---|---|---|
| `LibFieldAccessTest.frozenGenericInterfaceTypeArgumentsRestoreBoundSignaturesAcrossLibraryRoundTrip()` | `expected: <0> but was: <1>` | 恢复 canonical implicit interface root，并在源模型和 fresh 模型中重新断言；`LibFieldAccessTest` 69/69 通过。 |
| `MniNbtReferenceTest.realSourceNbtFieldReferenceWritesTheOriginalPositionAndAliasReadsIt()` | `expected: <0> but was: <3>` | 全量运行注释记录为 Java heap space，不是断言语义回归；同一编译产物在 fresh JVM 中通过，4 项 NBT 测试在 `1536m` 定向 JVM 中全部通过。 |
| `MniNbtReferenceTest.realSourceNbtListIndexIsFrozenOnceBeforeTheAmountMutatesItsSource()` | `expected: <0> but was: <1>` | 同上；两个 NBT 失败均由堆空间不足解释，fresh JVM 与定向运行均通过。 |
| `MniRuntimeStringTest.runtimeStringAddKeepsExactQuotesBackslashesControlsAndUnicode()` | `Unsupported command`：测试执行器拒绝了含原始换行的 `data modify ... set value` 命令 | `ScoreCommandExecutor` 的 set-value matcher 现包含原始换行；7 项字符串测试与独立原生 DSB 均通过。 |
| `TemplateInitializationTest.restoredTemplateMethodsKeepOwnersAndIndependentReceivers()` | `expected: <0> but was: <1>` | fixture 改为提供合法的空参数父构造器；receiver 结果 `927/12` 保持断言，`TemplateInitializationTest` 8/8 通过。 |
| `TypeVariableDeclarationTest.templateReadonlyTypesCannotBeCopiedIntoOrdinaryFields()` | `java.util.NoSuchElementException: Collection is empty.` | 拒绝路径现断言事务回滚、未发布特化、无非法字段或 holder，符合既有事务契约；`TypeVariableDeclarationTest` 10/10 通过。 |

## 4. 复用边界、源码增量与哈希

全量运行后只改动了以下 8 个源文件，没有成员关系（membership）变更：

```text
src/main/kotlin/top/mcfpp/mni/expand/CompilerBridge.kt
src/main/kotlin/top/mcfpp/mni/expand/CompilerPlanValues.kt
src/main/kotlin/top/mcfpp/model/compound/DataTemplate.kt
src/test/kotlin/top/mcfpp/test/LibFieldAccessTest.kt
src/test/kotlin/top/mcfpp/test/MniRuntimeStringTest.kt
src/test/kotlin/top/mcfpp/test/TemplateInitializationTest.kt
src/test/kotlin/top/mcfpp/test/TypeVariableDeclarationTest.kt
src/test/kotlin/top/mcfpp/test/util/ScoreCommandExecutor.kt
```

增量影响限定于：显式字面量命令路径、恢复 canonical implicit interface root、测试执行器对原始换行的匹配，以及上述 4 个测试类。其他测试源码、依赖、Gradle 配置和打包标准库字节保持全量运行时的状态。因此 1873 项此前通过的其他测试在 8 文件增量复核后复用，没有重跑；定向运行只执行受影响的 10 个类。

- 标准库：复用之前的 schema 80 再生结果，`src/main/resources/datapack/bin.mclib` 的 SHA-256 为 `91B49110607EC35F2DEF2ACB6B298F511F40578E8A9E360D1F4167C66D731732`；schema/序列化 payload 未改变，本轮未重新生成。全量与定向运行的 before/after 均为同一哈希。
- 公开运行时 JAR：[`build/libs/MCFPP-1.0.2-SNAPSHOT.jar`](../../build/libs/MCFPP-1.0.2-SNAPSHOT.jar)，SHA-256 为 `8B679AE4639DA7619EB7AD993F60ABBC3573F5BF5C31EB5070B99A1F1A6B9A0D`。
- 定向运行的冻结输入、源哈希和产物哈希见 [`functional-repair-check/attempt1/result.json`](../../build/inheritance-mni/functional-repair-check/attempt1/result.json) 与 [`input-guards.json`](../../build/inheritance-mni/functional-repair-check/attempt1/input-guards.json)；全量运行的输入守卫见 [`full-functional-check/attempt2/input-guards.json`](../../build/inheritance-mni/full-functional-check/attempt2/input-guards.json)。

## 5. 示例与字符串后端

十例 Java/Kotlin 示例在 DSB 26.3 下全部功能 PASS：`damage`、`nbt-reference`、`raycast`、`score-mapping` 共 8 例复用 `dsb-check/attempt2` 的原生通过报告；`repeat-Kotlin`/`repeat-Java` 2 例由 `dsb-check/attempt3` 新执行通过（断言 `10/8/9/22`，最终 `stack_frame=[]`）。逐例结果见 [`dsb-check/functional-summary.json`](../../build/inheritance-mni/dsb-check/functional-summary.json)。

编译器导出字符串 DSB 的 [`attempt2/result.json`](../../build/inheritance-mni/runtime-string-dsb/attempt2/result.json) 记录 `passed=true`、`exitCode=0`；[`report.json`](../../build/inheritance-mni/runtime-string-dsb/attempt2/report.json) 的实际输出覆盖：

- 动态字符串的精确引号、反斜杠、LF、CR、tab、backspace、form feed、中文和 emoji；
- 运行期值与字面量 `$(left)` 的拼接；
- 折叠字面量中的 `$(...)`；
- 三类输出全部符合预期，最终 `stack_frame=[]`，0 diagnostics。

该运行使用 `build/inheritance-mni/dsb-runtime/datapack-sandbox-cli.jar` 和 Java 25；编译器导出产物、适配器和运行时在前后哈希守卫中一致，详见 [`input-guards.json`](../../build/inheritance-mni/runtime-string-dsb/attempt2/input-guards.json) 与 [`command.json`](../../build/inheritance-mni/runtime-string-dsb/attempt2/command.json)。没有为文档重新执行这十例或字符串 DSB。

## 6. 边界

- 本文的合并结论是“136 项新执行 + 1873 项复用”的证据合成，**不是**一次新的 2009 项全量运行；旧全量 2007 项中的 6 个失败记录保留为历史，不再列为当前失败。
- 性能指标和真实 Minecraft 实机/服务端验收已由用户明确取消，不是剩余门槛；DSB 是确定性功能执行器，不是 Minecraft 服务端。
- 公共 API 仍为 `0.1.0-experimental`，见 [`api-versioning.md`](api-versioning.md)。独立最终验收记录由 [`acceptance.md`](../implementation-review/acceptance.md) 发布。
- 示例仍以本仓库已交付的生成产物和既有命令为证据；本文没有新增功能、测试或评分卡。

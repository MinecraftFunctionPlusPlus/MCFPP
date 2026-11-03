# MNI `repeat` 示例

`repeat(times, body)` 保留无返回值的静态 INLINE 接口；示例累积使用显式返回值重载 `repeat(times, initial, body): int`：

- `times` 是 `VALUE int`。调用者侧先按源码顺序求值一次，宏体只使用这个逻辑值；provider 再把它绑定到自己的生成局部 `limit`。
- `body` 是 `(int)->void` 的 `INLINE` 回调。provider 用 `whileLoop` 生成运行时循环，用独立生成局部 `i` 计数，并在每次迭代用 `inlineVoid` 把当前 `i` 作为 `VALUE` 实参插入调用点。
- 局部捕获在创建回调时按既有值复制契约快照，捕获绑定只读。全局声明保持调用时读写；累积状态由 `body(accumulator, index): int` 显式返回，不能写外层局部变量。
- 回调中的 `return` 只结束这一次内联插入，不会退出 repeat 循环，也不会退出调用者；循环计数自增和宏调用之后的调用者语句继续执行。
- 每次展开都有自己的 `i`、`limit` 和回调局部，不共享符号。嵌套 `repeat` 也不会串用外层计数。
- 不生成运行时 `repeat` 宏函数，也不按回调编号分派。普通 `while`/`if` lowering 仍会生成 `mniu2f`/`mni/...` 控制流 helper，这是正常的源码 lower；不是宏调用帧，也不是编号回调分派。

Java 与 Kotlin provider 使用同一公共 builder API、同一宏签名和同一算法；只有一份源码回调示例工程。

## 目录

```text
examples/mni/repeat/
├── build --project mcfpp.toml                         # 示例工程配置，从 build/repeat-macro.jar 加载 provider
├── project/src/.mcfpp                 # injectedBy 指令（默认启用 Kotlin provider）
├── project/src/main.mcfpp             # 两种源码回调 + 嵌套展开示例
├── provider/java/.../JavaRepeatMacro.java
└── provider/kotlin/.../KotlinRepeatMacro.kt
```

`main.mcfpp` 的三组结果：

```text
策略 1：repeat(5, 0, { accumulator, i -> accumulator + i })
  total = 0 + 1 + 2 + 3 + 4 = 10

策略 2：repeat(5, 0, { accumulator, i -> if (i == 2) { return accumulator; } accumulator + i })
  guarded = 0 + 1 + 3 + 4 = 8
  tail = guarded + 1 = 9

嵌套展开：外层每项调用 repeat(2, 0, { subtotal, j -> subtotal + i * 10 + j })，将结果加到外层返回值。
  nested = 0 + 1 + 10 + 11 = 22
```

## 构建 provider

在仓库根目录先构建编译器（JDK 21）：

```bash
./gradlew shadowJar
```

下面的命令使用 `build/libs/MCFPP-1.0.2-SNAPSHOT.jar`。

选择其中一个 provider。Java：

```bash
javac -cp build/libs/MCFPP-1.0.2-SNAPSHOT.jar \
  -d examples/mni/repeat/build/classes \
  examples/mni/repeat/provider/java/example/mnirepeat/JavaRepeatMacro.java

jar --create --file examples/mni/repeat/build/repeat-macro.jar \
  -C examples/mni/repeat/build/classes .
```

Kotlin：

```bash
kotlinc examples/mni/repeat/provider/kotlin \
  -cp build/libs/MCFPP-1.0.2-SNAPSHOT.jar \
  -d examples/mni/repeat/build/classes

jar --create --file examples/mni/repeat/build/repeat-macro.jar \
  -C examples/mni/repeat/build/classes .
```

`project/src/.mcfpp` 默认注入 Kotlin provider。使用 Java provider 时，把该文件的第一行（Kotlin `injectedBy` 指令）替换为：

```text
//> injectedBy example.mnirepeat.JavaRepeatMacro
```

同一个命名空间内不能同时注入两个同名 `repeat` 宏。`injectedBy` 指令必须位于文件开头，普通注释出现在第一条非指令行后就会停止读取后续指令。切换 Java/Kotlin 时只需用 `injectedBy` 选择其中一个 provider；jar 中同时包含两份入口也受支持，因为实际只注入被选中的那个类。

## 运行

`mcfpp.toml` 的 `sourcePath`、`targetPath` 和 `jars` 都相对项目配置文件所在目录。可在示例目录内运行：

```bash
cd examples/mni/repeat
java -jar ../../../build/libs/MCFPP-1.0.2-SNAPSHOT.jar build --project mcfpp.toml
```

产物写入 `examples/mni/repeat/project/build`。

## 测试

`src/test/kotlin/top/mcfpp/test/MniRepeatTest.kt` 使用真实 `@MNIMacro` provider 和真实 MCFPP 源编译，不手工构造 Code/Plan。测试通过 `ScoreCommandExecutor` 执行实际生成的命令，覆盖：

- 同一累积算法接两种源码回调；两种回调都显式返回下一累积值。
- 策略 2 的 `return` 只跳过 `i == 2` 的那次插入，循环继续，调用者尾部 `tail = total + 1` 仍执行。
- `repeat(effect(3), ...)` 中 VALUE 计数只求值一次，回调收到 `0, 1, 2`。
- 嵌套 `repeat` 的计数和回调局部相互独立，内层按值捕获外层迭代。
- 生成命令中没有运行时 `repeat` 宏函数或命令宏编号分派；生成 helper 不接收回调/参数区。

## 验证状态

以下 `attempt1`–`attempt3` 是迁移前实现的历史证据，不能当作新签名/捕获语义的运行结论。当前源码迁移检查入口是 `MniLambdaMigrationTest`，实际报告由该检查产出到 `build/lambda/`：

- `attempt1` 的 Java/Kotlin provider 编译和真实 CLI 编译均通过，两个 case 都是 `cliExitCode=0`、`zeroErrorsWarnings=true`。
- 同一批观察对两个 case 都返回 `observationExitCode=0`，得到 `total=10`、`guarded=8`、`tail=9`、`nested=22`。
- **DSB 功能验收**：`repeat-Kotlin`/`repeat-Java` 由 `attempt3` 在 16MB 功能快照上限下实际新执行通过，断言 `total=10`、`guarded=8`、`tail=9`、`nested=22`，最终 `mcfpp:system.stack_frame=[]`。执行前后原数据包/`Imports`、CLI 证据、Java 25 运行时和 DSB JAR 的 SHA-256 守卫一致。逐例汇总见 [`functional-summary.json`](../../../build/inheritance-mni/dsb-check/functional-summary.json)。
- 全量入口是 [`verify-dsb.ps1`](../benchmark/verify-dsb.ps1)，使用说明见 [`benchmark/README.md`](../benchmark/README.md)。复跑必须显式传新的 `-Attempt`，已有 `attempt1`–`attempt3` 均保留、不覆盖；例如：

```powershell
& ./examples/mni/benchmark/verify-dsb.ps1 -Cases @('repeat-Kotlin','repeat-Java') -Attempt attempt4
```

`MniRepeatTest` 不在 `approved-generated-context-batch14` 报告中；本页不把更早批次当作新运行。repeat 的当前功能证据是上述 `attempt3` DSB 执行，不是 Minecraft 实机。

DSB 只覆盖数据包功能语义，不测性能，也不替代游戏。性能与真实游戏验收已按用户要求取消，不是本示例的完成门槛；统一回归与最终验收见 [`regression-results.md`](../../../docs/mni/regression-results.md)、[`acceptance.md`](../../../docs/implementation-review/acceptance.md)。

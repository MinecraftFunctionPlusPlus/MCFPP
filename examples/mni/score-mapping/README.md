# MNI `score-mapping` 示例

这个示例演示真正的 annotation 派生声明处理器：

- `@ScoreMapping("#scores")` 是处理器注解，参数来自原始源码 annotation，不经过宿主 getter。
- `@Score("objective")` 是字段注解；编译器把字符串参数投影成不可变 `AnnotationView` 的位置参数
  `"0"`、`"1"`……
- 处理器在发命令前校验全部字段：只支持 `int`/`bool`，重复目标（holder + objective）直接诊断。
- `load`/`save` 是真实生成在 `Player` 上的成员方法，源码直接调用。
- 生成方法使用固定 holder（`ScoreHolder.named`），不依赖实体/世界上下文后端。
- `int` 原样读写；`bool` 保存严格写 `0`/`1`，读取任意非零分数为 `true`；缺失分数读
  `0`（每次分数读取先清零临时值，不依赖失败 `get` 的旧内容）。
- 处理器通过公共 `Declarations.metadata` 显式发布内置非重复零参数标记
  `mni:GeneratedMembers`。该标记只作为元数据，不会递归触发处理器。

## 目录

```text
examples/mni/score-mapping/
├── build --project mcfpp.toml
├── project/src/.mcfpp
├── project/src/main.mcfpp
└── provider/
    └── kotlin/example/mniscore/KotlinScoreMappingProvider.kt
```

`provider` 通过 `.mcfpp` 的 `injectedBy` 调用现有静态 import 钩子，在编译器安装当前命名空间时
注册 Kotlin 处理器和字段注解，生成方法使用 Kotlin DSL 构造。

## 构建 provider

在仓库根目录先构建编译器（JDK 25）：

```bash
./gradlew shadowJar mniExamplesJar
```

`mniExamplesJar` 生成 `build/libs/mni-example-providers.jar`，示例配置直接加载它。
`project/src/.mcfpp` 注入 `example.mniscore.KotlinScoreMappingProvider`。

示例配置使用 `noMcLib = true` 生成可执行数据包。生成成员方法的库导出仍受库格式支持范围约束。

## 运行

`mcfpp.toml` 中的相对路径以项目配置文件所在目录为基准。可在示例目录内运行：

```bash
cd examples/mni/score-mapping
java -jar ../../../build/libs/MCFPP-0.1.0.jar build --project mcfpp.toml
```

`main.mcfpp` 先 `save`（`health=7`、`alive=true` 即 1），再清零字段并 `load`，因此最终内存字段为
`health=7`、`alive=true`。生成的 `load`/`save` 命令可以由现有 `ScoreCommandExecutor` 执行：
int 往返、bool 的 0/1 保存、非零读真、缺失读零均为可观测行为。

固定 holder 和 objective 必须存在于数据包中；本示例只负责生成正确的存取命令，不生成 objective
声明。测试执行器不要求 objective 预先注册。

## 验证状态

已交付证据（复用协调者记录，本次文档修订未重跑）：

- `attempt1` 的 Java/Kotlin provider 编译和真实 CLI 编译均通过，两个 case 都是 `cliExitCode=0`、`zeroErrorsWarnings=true`。
- **DSB 功能验收**：`score-mapping-Kotlin`/`score-mapping-Java` 复用 `attempt2` 的原生 DSB 通过报告，断言 `loadedHealth=7`、`loadedAlive=1`、`#scores health_hp=7`、`#scores state_alive=1`，执行后 `mcfpp:system.stack_frame=[]`。执行前后原数据包/`Imports`、CLI 证据、Java 25 运行时和 DSB JAR 的 SHA-256 守卫一致。逐例汇总见 [`functional-summary.json`](../../../build/inheritance-mni/dsb-check/functional-summary.json)。
- 历史解释器证据保留：`approved-generated-context-batch14` 中 `MniScoreMappingTest` 5 项有 4 项通过；其中运行期断言（`MniScoreMappingTest.kt:383-428`）在同一个执行器里覆盖：写入 `12`/`3` 后 int 读 `12`、非零 bool 读 `true`，`save` 精确写 `9`/`1`；随后 `reset` 真实 key、用同一个生成 `main` 再调用，缺失 int 读 `0`、缺失 bool 读 `false`，第二次 `save` 仍写 `9`/`1`，且没有失败的 score 操作。`ScoreCommandExecutor` 的缺失条件按 false 处理、`reset` 删除真实 key 也由专门测试覆盖。
- 历史失败保留但不列为当前缺口：同一测试类最后的结构断言统计 `load` 可达命令中清零临时值的行数，因 helper 可达路径计数失败；`attempt1` 的两个 score 观察 case 返回 `observationExitCode=1`，原因是探针在调用帧 pop 之后读取 `stack_frame[0].loadedHealth`，不是生成命令结果不符。两者都是历史观察/测试问题，其功能结论已由上述 `attempt2` DSB 双例通过覆盖；本页不把它们重新表述为当前未完成项。
- 全量入口是 [`verify-dsb.ps1`](../benchmark/verify-dsb.ps1)，使用说明见 [`benchmark/README.md`](../benchmark/README.md)。复跑必须显式传新的 `-Attempt`，已有 `attempt1`–`attempt3` 均保留、不覆盖；例如：

```powershell
& ./examples/mni/benchmark/verify-dsb.ps1 -Cases @('score-mapping-Kotlin','score-mapping-Java') -Attempt attempt4
```

DSB 只覆盖数据包功能语义，不是 Minecraft 实机，也不测性能；性能与真实游戏验收已按用户要求取消，不是本示例的完成门槛。完整功能 JUnit 与统一验收见 [`regression-results.md`](../../../docs/mni/regression-results.md)、[`acceptance.md`](../../../docs/implementation-review/acceptance.md)。

# data 继承与 MNI 首期交付验收

日期：2026-10-18。范围：当前工作区中两份计划的已实现功能。本文复用已保存证据，没有新增测试或重新运行构建；实现与证据此前经根代理独立核对，但这份最终文件尚待独立阅读，不把本文当作独立评审结论。

## 1. 交付结论

- 两份计划的首期功能范围均已交付，并各自有当前源码接点、正式测试和已保存运行结果。按已接受的证据，没有必需的剩余功能缺口。
- `data` 侧对照原审查的 12 个具名探针与 1 个字段共享问题逐项核对；13 项均有修复实现和行为测试，旧探针本身只作为旧缺陷复现材料。
- MNI 侧完成第 10 节六个阶段与第 12 节九条首期完成标准；公共 API 仍为实验性 `0.1.0-experimental`。
- 用户明确取消的性能、真实 Minecraft 客户端/服务器验收不属于未完成项；本文也不作稳定 API 承诺。

## 2. `data` 继承：原审查 13 项逐项结果

源码接点、正式测试类和每次运行对应的 XML/`result.json` 已逐项列在 [继承验收记录](../inheritance/verification.md)；当前语义以 [实现契约](../inheritance/implementation-contract.md) 为准。原 [审查 README](../../build/inheritance-review/README.md) 的 characterization 探针不再作为“修复已通过”的证据。

| 原问题 | 当前实际结果（正式测试） |
|---|---|
| `incompatibleParentsAreAcceptedAsBothSupertypes` | 同名字段类型/契约冲突在声明阶段诊断，交换父顺序结果相同（`DataInheritanceFieldsTest`）。 |
| `localInitializerIsDiscardedWithLocalDeclaration` | 父字段初值 1、子本地初值 9 时最终读到 9，本地声明及其初值保留（`DataInheritanceFieldsTest`/`ConstructionTest`）。 |
| `unrelatedMethodConflictSilentlySelectsFirstParent` | 无关父的同签名实现报歧义，不再静默取第一父；兼容的本地 override 可明确实现（`MethodTest`/`MethodEntryTest`）。 |
| `diamondLosesMoreSpecificOverride` | 菱形在两种父顺序下都选择更具体的 `R.f`，结果为 2；共享祖先去重（`MethodTest`/`MethodEntryTest`/`LibraryTest`）。 |
| `overloadSelectionStopsBeforeExactMatchInLaterParent` | 父方法收齐后统一排序，`f(int)` 精确匹配胜过另一父的 `f(object)`，与父顺序无关（`MethodTest`/`MethodEntryTest`/`SuperTest`）。 |
| `inheritedAbstractObligationIsNotChecked` | 未实例化的具体类型缺少抽象实现也会诊断并阻止构造；抽象中间类型可保留义务（`ContractPhaseTest`/`ContractTest`）。 |
| `overrideAcceptsIncompatibleReturnAndNarrowedAccess` | 不兼容返回、收窄访问和 `void` 不匹配被拒绝；合法协变返回与扩大访问通过（`ContractTest`/`LibraryTest`）。 |
| `parentConstructorBodyDoesNotExecute` | 父字段初值和父构造体在同一 receiver 上按父优先顺序执行，共享祖先只执行一次；父体得 7、子初值得 9 两例均通过（`ConstructionTest`/`ConstructorPlanTest`）。 |
| `declarationOrderChangesMissingOverrideDiagnostic` | 父先、子先和跨文件前向父均在声明完成后统一检查，诊断不随源码顺序改变（`ContractPhaseTest`/`CompletionTest`）。 |
| `indirectInheritanceCycleCausesStackOverflow` | 直接、间接、未使用及泛型循环在声明阶段给出循环路径，不再依赖实例化或触发 `StackOverflowError`（`GraphTest`/`CompletionTest`）。 |
| `inferenceSelectsDifferentOverloadThanInvocation` | 推导与实际调用共同选择 `B.f(int):bool`；推导不执行初始化表达式，实际构造/调用只执行一次（`MethodTest`/`SuperTest`）。 |
| `superHasNoRuntimeBinding` | 单父 `super` 读写实际 receiver；无关父保留歧义诊断；唯一更具体的菱形父实现可调用（`SuperTest`/`ConstructionTest`）。 |
| 字段共享、注解污染、`const` 丢失（第 13 项） | 父子字段和注解容器隔离，子注解不回写父/兄弟；同 `const` 覆盖保持 `const`，`var`→`const` 报错，实例读写遵守有效契约（`LayoutRestoreTest`/`LibraryTest`/`LibraryMetadataTest`）。 |

泛型与 fresh `.mclib` 路径也已覆盖：同一绑定祖先去重，不同泛型绑定保持不同 origin 和构造阶段；从磁盘新载入的 schema 80 库复现字段冲突、方法选择、父构造一次/子初值覆盖、注解隔离和 `const`/访问检查。真实 Minecraft 行为未验证，已按用户要求取消该项验收。

## 3. MNI：六个实施阶段与用户入口

| 阶段 | 已交付且可用的功能 | 证据/入口 |
|---|---|---|
| 一、Java/Kotlin 兼容 | 旧 `@MNIFunction` 路径保留；Java 与 Kotlin 均可编写/发布库，支持顶层 facade、`object`/companion `@JvmStatic`、`void`/返回值、caller、只读参数及 `NativeCallContext`。 | [Kotlin 兼容说明](../mni/kotlin-compatibility.md)、[CLI 退出码 0](../../build/inheritance-mni/kotlin-example-results/attempt3/exit-code.txt)。 |
| 二、类型化模型与构造 API | 不可变 `Code`/`Expr`/`Stmt`/`Block`、Java builder、Kotlin DSL、受控展开会话和原子提交；真实 lowering 后端可用，运行期字符串 `+` 精确保留操作数。 | [实现契约](../mni/implementation-contract.md)、[字符串 DSB 结果](../../build/inheritance-mni/runtime-string-dsb/attempt2/result.json)。 |
| 三、宏入口与参数模式 | `@MNIMacro` 接普通调用、`VALUE`/`REF`/`CONST`；`VALUE` 从左到右只求值一次，`REF` 绑定原位置且支持别名，`CONST` 只接受闭合常量，旧 MNI 在 lowering 阶段执行。 | [教程参数规则](../mni/tutorial.md)、[`damage`](../../examples/mni/damage/README.md)、[`raycast`](../../examples/mni/raycast/README.md)。 |
| 四、内联回调与执行上下文 | `INLINE` 延迟绑定源码 lambda/静态函数，捕获调用者原位置；回调 `return` 不退出调用者，嵌套展开局部状态隔离；`executeAs`/`at`/`rotated` 等结构化上下文可用。 | [教程执行上下文](../mni/tutorial.md)、[`repeat`](../../examples/mni/repeat/README.md)、[`raycast`](../../examples/mni/raycast/README.md)。 |
| 五、声明生成与 NBT | annotation 处理器在全声明屏障后新增方法/内部函数/白名单元数据，失败不入半成品；`score-mapping` 提供真实 `load`/`save`；`REF` 可指向确定 NBT 字段或列表元素并原位读写。 | [教程声明生成](../mni/tutorial.md)、[`score-mapping`](../../examples/mni/score-mapping/README.md)、[`nbt-reference`](../../examples/mni/nbt-reference/README.md)。 |
| 六、文档、版本与验证 | Java/Kotlin 对照教程、参数求值规则、展开预算/`dump`、独立 API 版本、DSB 功能入口和示例命令均已交付。 | [教程](../mni/tutorial.md)、[API 版本策略](../mni/api-versioning.md)、[MNI 文档入口](../mni/README.md)、[回归结果](../mni/regression-results.md)。 |

## 4. MNI 第 12 节首期完成标准逐条核对

| 完成标准 | 当前交付状态 |
|---|---|
| Java 和 Kotlin 都能编写、发布 MNI 库 | 阶段一已完成；两种 JVM 入口均有说明和实际 CLI 记录。 |
| 不新增编译期执行语言或解释器 | 没有 `comptime`/`quote`/`splice` 或任意 MCFPP 函数编译期解释器；宏只构造不可变代码树。 |
| 使用者通过普通调用、`ref` 和 lambda 定制库 | 阶段三/四已覆盖；`damage`、`repeat`、`raycast` 可运行。 |
| 作者使用结构化 API，不接触解析器节点和全局当前函数 | 公共入口为 `top.mcfpp.mni.api`，示例 provider 不引用 ANTLR 或 `Function.currFunction`。 |
| 编译器保证类型、作用域、求值次数和执行上下文 | 阶段二至四的契约与正式测试覆盖；受影响定向运行 136 项全部通过。 |
| 同一示例库适配不同数据位置和用户行为而无需复制源码 | `nbt-reference` 复用 `damage` provider，仅改变 REF 传入位置；`raycast` 同一算法接停止/继续两种回调。 |
| 常见专用场景消除可证明的数据适配与回调分派 | `REF` 直接读写原位置，`VALUE` 一次求值；生成结果没有运行时宏函数、参数区复制或回调编号分派。 |
| 错误指向用户调用/声明并展示必要的展开链 | 诊断保留源位置并在根调用处补齐宏/内联链；预算超限和 `dump` 规则见教程。 |
| 旧 MNI 兼容，已有工作区修改保留 | 旧 `@MNIFunction` 路径继续可用；本轮保留工作区已有改动，未做 git commit/push。 |

## 5. 实际运行证据与边界

- 继承 13 项映射到具体类 XML 及 fresh schema 80 往返结果；旧的全量测试、旧探针和旧库哈希均明确标为历史证据，不冒充当前结果。
- 全量功能运行 `full-functional-check/attempt2`：204 个测试类、2007 项，2001 通过、6 失败、0 error、0 skip。6 项失败所在测试类全部由 `functional-repair-check/attempt1` 的 10 类、136 项新执行覆盖，结果为 0 failure/error/skip。旧 6 项均不是当前缺口。
- 组合证据为 **136 项新执行 + 1873 项复用 = 2009 项**；这不是一次新的 2009 项同轮全量运行。统一事实见 [integrated-functional-summary.json](../../build/inheritance-mni/integrated-functional-summary.json) 与 [回归结果](../mni/regression-results.md)。
- 真实 CLI：5 个示例 × Java/Kotlin = 10 个 case，全部 `cliExitCode=0`、零错误/警告；见 [actual-examples-cli-check/attempt1/result.json](../../build/inheritance-mni/actual-examples-cli-check/attempt1/result.json)。
- DSB 功能执行：10/10 个 Java/Kotlin case 通过；8 例复用 `attempt2`，`repeat-Kotlin`/`repeat-Java` 2 例由 `attempt3` 新执行。见 [functional-summary.json](../../build/inheritance-mni/dsb-check/functional-summary.json)。
- 编译器导出字符串 DSB `attempt2` 通过 3 种输出形态（动态控制字符/中文/emoji、运行期值与字面量 `$(left)`、折叠字面量 `$(...)`），0 diagnostics，结束时调用帧为空。
- 当前运行时 JAR：[MCFPP-1.0.2-SNAPSHOT.jar](../../build/libs/MCFPP-1.0.2-SNAPSHOT.jar)，SHA-256 `8B679AE4639DA7619EB7AD993F60ABBC3573F5BF5C31EB5070B99A1F1A6B9A0D`。标准库保留之前的 schema 80 再生结果，`bin.mclib` SHA-256 `91B49110607EC35F2DEF2ACB6B298F511F40578E8A9E360D1F4167C66D731732`，本轮未重新生成且序列化 payload 未变。

## 6. 使用入口与复现边界

- 从仓库根构建运行时：`./gradlew shadowJar`。示例在其自身目录运行：`java -jar ../../../build/libs/MCFPP-1.0.2-SNAPSHOT.jar mcfpp.json`；各示例的 provider 构建、`injectedBy` 选择与预期结果见对应 README。
- 文档入口：[MNI README](../mni/README.md)、[Java/Kotlin 教程](../mni/tutorial.md)、[API 版本](../mni/api-versioning.md)、[继承验收记录](../inheritance/verification.md)。
- 未验证/不作出的声明：真实 Minecraft 客户端或服务器行为、性能/MSPT/游戏内耗时、稳定公共 API。DSB、`ScoreCommandExecutor`/`ContextCommandExecutor` 都是确定性功能执行器，不是 Minecraft，也不构成性能数据。
- 剩余边界：上述性能与实机验收已由用户取消；API 仍为 `0.1.0-experimental`，没有稳定兼容承诺。按已接受的证据，没有必需的功能缺口；未来可选目标不扩展本次首期范围。

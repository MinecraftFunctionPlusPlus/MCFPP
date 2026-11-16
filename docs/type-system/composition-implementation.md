# 类型组合与工厂实施记录

本轮依据完整保存在仓库的 [原始计划](./type-composition-and-factory-plan.md)，从提交 `a00ddbff` 在分支
`codex/type-composition-factories` 开始。该提交保存开始实施前的工作区；本地运行日志未纳入提交。
本轮是独立任务，不恢复已有的 `.codex-workflow-graph` 工作流。

## 当前决定：类型命名空间中的普通工厂

用户在前一轮提交后修正了工厂设计。`new` 定义在所属类型命名空间中，没有 `this`，
按普通实参规则调用并返回独立、完整的值。复用父初始化只写 `Parent.new(args...)`，
再从返回值显式组装子值；普通调用不要求调用方具有名义祖先关系。
已撤销父限定接收者语法、隐式插入接收者实参及相关名义父查找要求。
普通实例方法和扩展方法继续拥有各自的 `this`。

本次修正已完成，验证见本文末尾；下述交付结果属于上一轮实现，
其中旧父限定测试的历史记录不再定义当前语言规则。

## 前一轮交付状态（提交306275f3）

原始计划阶段1—8已实现并完成迁移文档：开放对象形状、字面量类型、never、规范化交叉/联合、
中央结构子类型、扁平字段继承、普通函数工厂、对象shorthand、真实存储写约束，
以及类型/工厂库往返和缓存失效。对象展开继续按批准决定推迟。库格式为4，开发缓存版本为88。

验证结果（JDK25.0.4，Gradle9.2.1，离线本地SDK）：
- 完整 `test` 执行316类/2553项，2548通过、3失败、2跳过，无executor错误或OOM。
  随后只更新两个旧测试夹具，主实现不变；对相关四类定向复测46项全部通过。
  因而当前实现的适用证据覆盖2551项通过、2项跳过，**不是一次零失败的全量运行**。
  完整及复测日志分别为 `build/type-composition-full-3.log`、`build/type-composition-focused-17.log`。
- 两项跳过分别为缺少DSB_JAR的真实sandbox集成，以及用户此前推迟的嵌套lambda REF转发；
  未将其记作通过，也未扩大本轮范围。
- 46个标准库目标全部重建成功；发行包冒烟通过。复用同一主实现/JAR/资源的已执行证据，
  未因后续测试夹具或文档修改重复运行。
- 独立发行JAR编译 `examples/type-composition` 为0错误/0警告；导出命令模拟得到
  `[3,7,11,4,9], spawn`、Marker `{active:1b,name:"spawn",x:3,y:4}`，最终栈深0。
  此处验证了实际导出命令，未运行真实Minecraft。

以下检查点按实施时间保留，标为“进行中”或“未通过”的内容均为当时状态；最终结果以本节和末尾记录为准。

## 已确认的设计决定

- 工厂只接受声明中的普通参数，没有隐式 `this` 或特殊首参数规则。
  复用父初始化时显式调用 `Parent.new(args...)`，使用独立返回值构造完整子值。
- 对象字面量的重复字段报错，并指出字段名。
- 空模板生成返回 `{}` 的默认工厂。
- 显式工厂每条可达成功路径必须显式 `return`，不采用尾表达式返回。
- 可选 `never` 字段只能缺失。
- 共享视图写入同时满足视图和真实存储的约束；联合字段写入满足所有分支。
- `new` 位于所属模板体内，使用正文规定的模板词法环境与完整 `Self`。
- 对象展开仍属后续工作，不在本轮实现范围。

## 当前实现地图

以下路径均相对仓库根目录。

| 领域 | 实现位置 | 开始实施时的行为 | 现有检查 |
| --- | --- | --- | --- |
| 类型身份 | `type/TypeId.kt`、`type/MCFPPType.kt` | 按 TypeId 比较；无对象形状、字面量类型、never | `TypeKernelTest` |
| 类型关系与组合 | `type/TypeRelations.kt`、`MCFPPUnionType.kt`、`TypeSyntaxResolver.kt`、`model/compound/UnionDataTemplate.kt` | 名义子类型；联合展开去重；交叉采用合成继承模板 | `TypeKernelTest`、`IntersectionTemplateCompletionTest` |
| 字段、常量与存在性 | `analysis/MemberDescriptor.kt`、`ConstantBinding.kt`、`CompilerValue.kt`、`TemplateLiteralSupport.kt` | 不可变常量快照、可选字段与存在性事实已具备；const 类型仍为基本类型 | `DataConstantTest`、`DataConstantCallTest`、`DataPresenceTest` |
| 继承与布局 | `model/compound/DataTemplate.kt`、`model/scope/CompoundDataScope.kt`、`analysis/InheritanceContractValidator.kt` | 已扁平化并保留来源；独立同名字段仍可能歧义；有方法继承检查 | `DataInheritanceFieldsTest`、`DataInheritanceGraphTest`、`DataInheritanceContractTest` |
| 视图与写入 | `analysis/ValueModel.kt`、`StorageAccess.kt`、`type/ReinterpretationCompatibility.kt` | as 共享 Place；普通复制独立；const 与重叠失效已具备；需补真实可变字段类型约束 | `StorageViewTest`、常量调用测试 |
| 语法与声明 | `antlr/mcfppParser.g4`、`MCFPPFieldVisitor.kt`、`MCFPPGenericDataTemplateFieldVisitor.kt` | constructor 声明与隐式默认构造器；new 当前是普通标识符 | `ConstructorResolutionTest`、`TemplateFieldInferenceTest` |
| 字面量与推断 | `antlr/MCFPPExprVisitor.kt`、`analysis/DeclarationBinding.kt` | 无上下文推断 dict；模板上下文禁止多余字段并应用默认值；尚无 shorthand | `DataTemplateLiteralTest` |
| 函数、返回与构造 | `model/function/Function.kt`、`DataTemplateConstructor.kt`、`ConstructorPlan.kt`、`antlr/MCFPPImVisitor.kt` | 构造先分配 this，再执行父链与初始化；普通函数检查显式返回 | `ConstructorExecutionTest`、`DataInheritanceConstructorPlanTest` |
| 库与缓存 | `io/TypeIdentitySerializer.kt`、`TypeDescriptorSerializer.kt`、`library/compiler/`、`library/model/`、`library/format/`、`library/syntax/` | 保存有限类型、声明 AST、构造器与字段初值 | `LibraryTypeTableTest`、`LibraryCompoundTest`、`LibrarySyntaxCodecTest` |

实现文件位于 `src/main/kotlin/top/mcfpp/`，语法位于 `src/main/antlr/`；测试位于
`src/test/kotlin/top/mcfpp/test/`，库测试位于其 `library/` 子目录。

## 基线验证

- 命令：`java -version`；结果：JDK 25.0.4。
- 命令：`gradlew.bat test --tests top.mcfpp.test.TypeKernelTest --tests top.mcfpp.test.DataTemplateLiteralTest --tests top.mcfpp.test.ConstructorResolutionTest --console=plain`。
- 结果：测试未运行，`:prepareBuiltinImportPlan` 的 runtimeClasspath 缺少本地
  `moe.afox.mcfpm:{core,mcfpp-adapter,repository-maven}:0.1.0-SNAPSHOT`。
  Kotlin/Java 编译任务为 UP-TO-DATE，不能据此声称本轮完成新编译。
- 按 `docs/cli.md` 从相邻 `Mcfpm` 工程发布所需 SDK 后，再执行相同基线检查。

## 实施顺序

1. 不可变对象字段合同、字面量类型、never、交叉与联合规范化、中央结构子类型。
2. 使用同一组合规则合并继承字段；保留名义祖先与声明来源。
3. 对象字面量与词法 shorthand；普通函数工厂声明、调用和隐式工厂。
4. 共享位置写入、联合共同字段与常量类型接入。
5. 库保存恢复、迁移说明、聚焦回归及最终全量检查。

各步结果与尚未通过的检查将在实际执行后记录。既有 constructor、父初始化和方法覆盖测试中，
与新合同直接冲突的案例须迁移，不能将旧断言失败记作新行为通过。

## 历史继续工作检查点（当时进行中）

用户授权完整实施、先提交现有代码再建分支，以及按子任务调整 agent 模型与推理强度。
原始计划第 15 节中的所有决定已落实在本文“已确认的设计决定”中；对象展开继续推迟。

该检查点时各实现仍在集成，尚不能声称计划完成或测试通过。协调者负责最终构建、测试与记录。

| agent | 文件所有权与任务 |
| --- | --- |
| `/root/type_map` | 类型包（协调者持有 MCFPPType/TypeSyntaxResolver 除外）、规范化与中央关系测试 |
| `/root/factory_map` | grammar、FieldVisitor/GenericFieldVisitor、TemplateFactory、DataTemplate 工厂登记、标准库构造迁移 |
| `/root/inheritance` | CompoundDataScope、InheritanceContractValidator、DataTemplate.flatExtends、PreparedTemplateHeaderReader、继承测试 |
| `/root/storage_safety` | StorageAccess、ConstantBinding、UnionTypeVar、Var/MCInt/NumericConversions、写约束与常量测试 |
| `/root/serialization` | io 类型编码与 library 类型/工厂/语法保存恢复 |
| `/root/object_binding` | DeclarationBinding、TemplateLiteralSupport、PrimitiveCompiler 字面量绑定与字面量测试迁移 |
| `/root/toolchain` | 本地 SDK 设置与独立 worktree 的原始基线验证 |
| `/root` | ExprVisitor、ImVisitor、Function、MCFPPType、TypeSyntaxResolver，集成和最终验证 |

核心共享 API：`ObjectFieldContract(name,type,optional,kind,origins)`、
`ObjectMemberKind.VAR/CONST`、`MCFPPObjectType.fields/fieldsByName`、
`MCFPPLiteralType(primitive,value)`、`MCFPPNeverType`、
`TypeCompositions.objectShape/intersection/union/widen/literalOrNull/restore`、
`TypeRelations.isNominalSubtype`。结构身份复用保留键的 TypeId.Applied/Specialized，
对象类型复用合成模板的运行时布局；纯形状关系直接使用不可变字段合同。

工厂 API：`TemplateFactory(template,ctx,isImplicit)`，静态普通函数、无隐式 this；
`DataTemplate.factories`、`resolveFactory(args)`。调用必须使用普通函数返回值，不预分配 receiver。
语法 shorthand 的 `nbtKeyValuePair.expression()` 可为空，公共解析入口是
`TemplateLiteralSupport.compoundEntries(compound)`；重复键通过它统一诊断。

SDK 已用 JDK 25.0.4 从相邻 Mcfpm 成功发布到 `F:/DevCache/.m2/repository`；
默认 Maven 用户目录是失效符号链接，验证命令须带
`-Dmaven.repo.local=F:/DevCache/.m2/repository`。独立基线 worktree：
`F:/DevCache/mcfpp-type-composition-baseline`，固定在 `a00ddbff`，运行最初三个测试类。
该 worktree 的结果不覆盖主工作区的后续改动。

### 集成验证进展

- 原始计划及本记录已提交为 `0352c0ee`，实施代码仍在集成。
- 第一次主工程 `compileKotlin` 失败：工厂给只读的 `Member.isStatic` 赋值。
  已改用 `Function.receiverless`，复制、调用及 checkpoint 都保留该合同。
- 第二次主工程 Kotlin/Java 编译成功；随后全目标标准库重建在首个目标失败，
  内置实体 summon 仍带旧 override 标志。已移除该标志。
- 下一次 Kotlin/Java 编译成功；标准库错误缩小到七处外部调用私有
  `SlotWithWildcard.new`。已改为显式、带目标类型的对象字面量，保留私有工厂。
- 当前重建命令：`gradlew.bat -Dmaven.repo.local=F:/DevCache/.m2/repository rebuildStdlib -PstdlibTargets=1.21.8,26.3 --offline --console=plain`。
  完整输出保存于 `build/type-composition-stdlib.log`；尚未记录通过。
- 隔离基线两次各执行 30 项测试、17 项失败，均在加载旧标准库资源时触发摘要不符。
  恢复 Git 原始资源字节后，失败由浮点函数推进到已提交的混合换行 JSON。
  该基线不能声称通过，已停止重跑；当前实现需要使用重建后的标准库验证。
- 新增聚焦检查：`TypeCompositionTest`、`TypeCompositionInheritanceTest`、
  `FactoryConstructionTest`、`CompositionStorageTest`、`TypeCompositionLibraryTest`，
  以及现有 MNI 声明/命名空间 journal 回滚用例。尚未执行这些新测试。

额外已发现并处理的集成接点：父接收者参与重载排序；普通函数调用拒绝抽象实现；
工厂调用检查访问权限；构造工厂不接受隐式 this；发布库格式升级为 4、开发缓存为 88。
对象字段的读写访问能力保存在合同元数据中，联合规范化不能因数据形状相同而抹除
限制；库中相同语义身份的不同访问合同保留各自描述行。

后续仍须完成：标准库重建、聚焦行为及导入导出运行检查、修复实际失败，
再执行计划要求的全量测试与工程验证。object/entity/type wrapper 的内部构造器兼容路径
目前仍保留；普通 data 源码 constructor 已拒绝，不能笼统宣称全部内部构造器类已删除。

### 最新运行检查点

- `1.21.8`、`26.3` 的标准库重建已成功，两个源码编译均为 0 错误、0 警告；
  Gradle `BUILD SUCCESSFUL in 40s`。此前导出失败是 const 字段类型收窄后，
  将源语法中的 `float` 错当成外部声明；现在基本类型按内置表绑定。
- 已新增 `.gitattributes`：数据包资源使用 `-text`，防止 Git 转换字节后破坏库摘要。
  `git check-attr` 已确认函数资源和 bin.mclib 的 text 属性均为 unset。
- 首次测试源码编译失败于新测试的 Test 注解歧义及列表断言泛型推断，已修复。
- 首轮五组聚焦测试已结束，日志 `build/type-composition-focused.log`：
  TypeCompositionTest、TypeCompositionInheritanceTest、FactoryConstructionTest、
  CompositionStorageTest、library.TypeCompositionLibraryTest。共 57 项，39 通过、18 失败。
  存储与若干源码用例的共同失败来自导入标准库 const 字段被收窄后，纯绑定丢失
  已知常量；已交由绑定 agent 修复。泛型工厂用例编译成功，执行器遗漏泛型实例函数，
  已修正执行器。库身份用例使用了未归一化的不可能交集，已改为有效规范化夹具。
- 新增 `examples/type-composition` 与 README/文档示例迁移完成，尚待实际 CLI 验证；
  示例预期输出顺序为 `3, 7, 11, 4, 9, spawn`。
- 源码 `Box<{x:int}>` 的 readonly 实参现按形式参数的 Type 上下文绑定对象类型，
  空 `{}` 只在已知 Type 上下文解释为类型。普通表达式语法保持原顺序；修复待运行。
- `/root/toolchain` 已完成已知旧构造器与常量字段字面量测试迁移；其隔离基线任务已结束。

### 后续集成检查点

- 第二轮聚焦检查：58 项，57 通过、1 失败；日志 `build/type-composition-focused-2.log`。
  剩余失败为泛型普通函数签名 `{x:T}` 未进入类型解析器，已修复 `MCFPPType.parseFromString`。
- 标准库 `Rotation`、`CommandResult` 的旧 const 零值字段迁移为带初值的只读 var 属性，
  避免将 MNI 返回的任意运行时值约束成零值字面量类型。
- 全目标标准库重建已成功（`BUILD SUCCESSFUL in 10m 21s`），日志
  `build/type-composition-stdlib-all.log`。46 个既有支持目标均成功，源码编译均为 0 错误、0 警告。
- 重建启动后又接入以下更改，尚须在下一次主工程编译验证：已知隐藏字段向可选目标
  字段复制时检查实际类型；泛型普通签名的对象类型解析；泛型间接名义祖先的源名称
  查找与歧义检查。`ParentQualifiedFactoryTest` 覆盖新增祖先调用接点。
- `/root/toolchain` 已验证从当前 Gradle worker classpath 直接启动 CLI `--version`；
  等待稳定的主类/资源后编译 `examples/type-composition`。其 CLI 与生成物检查仍待执行。

仍须完成：全目标标准库重建结果、上述更改的聚焦验证、完整项目测试、
`verifyDistribution`、实际 CLI 示例编译，以及本地实现提交。现有通过证据不可标记为新跑结果。

当前完整验证命令为 `gradlew.bat -Dmaven.repo.local=F:/DevCache/.m2/repository test verifyDistribution --offline --console=plain`，
会话 `19593`，日志 `build/type-composition-full.log`。最新主 Kotlin 编译与内置导入计划生成已成功；
完整测试与发行 JAR 结果尚未返回。`TypeCompositionTest` 新增 any 变量/参数/返回值运行用例，
验证对象字面量按既有隐式装箱规则使用；该接点同样在此次完整验证中检查。

示例交付证据：`examples/type-composition` 已经实际 CLI 编译成功（退出 0、0 错误、0 警告），
日志 `build/type-composition-example.log`。通过现有 `ScoreCommandExecutor` 模拟生成命令，
得到 `3, 7, 11, 4, 9, spawn`、扁平返回值 `{active:1b,name:"spawn",x:3,y:4}`，
最终栈深度 0；日志 `build/type-composition-example-command-check.log`。未在 Minecraft 游戏中执行。

完整测试尚在运行，已出现 Any 参数结构视图字段被误记为未初始化，以及既有集合/擦除形状
测试失败。已分派 storage/type/binding 代理读取具体诊断；不得将当前完整测试标记为通过。

### 首轮全量检查中的修复地图（尚待重新编译）

会话 `19593` 的测试 JVM 使用启动时的旧编译产物。下列编辑在其运行期间完成，
因此该全量结果不能证明这些编辑通过。协调者统一运行 Gradle；代理用忽略目录下的
Java 反射探针读取具体失败，避免等待整套 XML 写入。通用探针为
`build/TypeCompositionTestProbe.java`，使用 JDK 25 和
`@F:/DevCache/.gradle/.tmp/gradle-worker-classpath18203841291229794857txt`，参数为类名、方法名。

- 类型/视图：StorageAccess 对未知 any 的必需字段读取使用临时初始化证明，
  不伪造实际类型、事实或源声明；可信确切字面量事实恢复值；类型包装器写入按 typeAs 检查。
- 复制：捕获使用真实生产者的原始快照；集合元素类型递归 widening，保留 CONST；
  原生普通参数副本采用已转换的形式参数类型，防止标量 const 的 singleton 类型锁死参数副本。
- 绑定：字面量类型的一元/二元运算按基本类型计算；对象字面量在任何字段执行前纯检查，
  拒绝 void/null，失败字面量不泄漏调用命令。
- 字典：显式 `literal as dict<T>` 和嵌套 dict 元素传播上下文；复合实参选择可接受且更具体的
  上下文（dict 优于 nbt），保留真正歧义；Map 的 keys/keyValueSet 纯绑定签名与运行时一致。
- 数值：NBT byte/short/long/double 负字面量保持位宽，转换仍由原后端执行。
- 接收者：数据 Var 与 DataTemplateObject 都传接收者参加排名；原生集合转发真实类型，
  以 NativeFunction.caller 排序；结构等价时按较少默认参数选择，默认数相同仍歧义。
- 效果：SourceEffects 使用声明接收者合同查找方法，实际 Place 用于效果；识别泛型伴生静态调用。
  实际编译期对象调用保留编译期全局载荷，运行时可变全局仍失效。
- 库：合并字段允许多个一致来源并确定规范来源；泛型工厂恢复返回实际所属实例的 Self。
- 内建 API：Display 的向量/quaternion Setter 使用中央结构兼容检查。
- 文档：只组合父字段文档，函数保留原所属声明；源码/库工厂文档均保留 new 签名。
- 旧测试迁移仅针对明确变化：常量声明即初始化、普通对象改为默认结构类型、子工厂无继承
  初始化器、无 override、交集不再是带方法的 UnionDataTemplate、历史库格式拒绝。
  FloatProvider 导出与 sameNamespace 声明用例单独探针通过，暂保留完整套件中的失败供后续复查。

### 全量结果及下一轮验证

- 首轮完整命令已结束：2160 项测试，467 失败、3 跳过；49 分 43 秒。除行为失败外，
  测试进程在 TextStandardLibraryTest 的声明快照处堆内存耗尽。`verifyDistribution` 因前序
  test 失败未完成。此结果属于上述运行期编辑之前的编译产物。
- 失败详情另存于忽略目录 `build/type-composition-full-failures.json`，原日志仍为
  `build/type-composition-full.log`。后续须保留实际结果，不能将其改称通过。
- 已接入声明完成屏障：源码、导入、继承合并和泛型替换延迟交集/联合归一化，完成后
  重建字段适配器、来源、函数参数及返回类型；新增继承与嵌套签名的源码/库往返回归。
- MNI 声明域快照补入 TemplateFactory 及工厂元数据，解决大量共同入口拒绝。
- 空/单字段花括号按上下文解释为对象或 lambda；导入对象 `{it}` 读取真正外层词法变量，
  callback 的隐式 it 仍保留。新增导入泛型 Self 和 `{it}` 工厂运行回归。
- 整数字面量统一先读符号再读幅值，支持 Int/Long 等有符号最小值并报告越界；
  PrimitiveCompiler、DeclarationBinding、ExprVisitor 使用同一入口。
- 最新聚焦命令覆盖类型/继承/工厂/存储/库、父限定调用、交集完成、NBT 数组、数值转换及
  MNI 声明 journal。首次编译发现 ExprVisitor 缺少导入和诊断 API 参数错误，已修复并重启，
  日志 `build/type-composition-focused-3.log`，尚无测试结果。

代理正在按完整失败详情检查尚未覆盖的具体原因；协调者统一执行 Gradle，防止并发构建
污染产物。完成聚焦修复后仍须执行完整项目测试、发行验证和本地实现提交。

### 第三轮聚焦结果

- 新 Kotlin/Java 与测试源码均已编译成功；121 项测试，112 通过、9 失败，7 分 1 秒。
  详情另存 `build/type-composition-focused-3-failures.json`。
- 两个继承签名失败分别为新夹具联合缺括号及导入时提前比较暂存交集与最终形状，已修复待编译。
- 工厂相关失败找到共同语法原因：显式 `{value: source.value + amount}` 被优先当作 lambda
  中的带命名空间表达式。正在为显式键冒号加对象优先解析，空/单名称仍按上下文选择。
- 有符号最小值源码编译已成功；库导出同样需要先读取完整有符号整数，修复已接入待编译。
- 已确认当前产物的泛型菱形继承库往返及 MNI header reader 代表用例探针通过；这两项
  只作为具体失败定位证据，不代替全套 Gradle 结果。
- 另已处理真实发现：Selector/BoundExecutor 接收者类型转发、编译器 ProjectionSource token
  快照支持、常量读取避免分配临时名、lambda 返回上下文、被优化移除的测试观测局部值，
  以及字符串测试助手清理已删除目录路径。测试 JVM 每 25 个类重启，保持串行与原有堆大小。

### 第四轮聚焦结果（仍在修复）

- Kotlin/Java 及测试源码编译成功。263 项测试，40 失败、1 跳过；8 分 24 秒。
  测试进程仍在 TextStandardLibraryTest 的库导入 journal 处内存耗尽。
  详情 `build/type-composition-focused-4-failures.json`，日志 `build/type-composition-focused-4.log`。
- 当前 512 MB 独立 Text 工厂探针三个目标全部成功，证明单用例并非必然超出堆容量；
  正在检查前序测试留下的图与缓存，不继续盲目增加分叉或堆大小。
- 核心工厂解析与有符号最小值的源码/库导出失败已通过。新库工厂消费回归仅剩新增测试
  import 书写错误（`.` 改为 `:`），已经修正。联合回归调整为读取共有字段，保留两个分支
  在继承完成后均未被提前抹除的检查。
- 新待编译修复：结构对象/显式 map 表示的 IR 构造；unchecked map 复制保留原始布局证据；
  深层可选字段的存在性证明保留读布局；空且无本地初值的隐式工厂可绑定为闭合空记录；
  const 初值按源码顺序完成；UUID 的精确快照准入；本地输出优先于同名导入函数。
- 静态编译期全局值不再按运行时可变值失效，避免 main 编译时先清掉 PositionState 的 pos3；
  运行时全局仍失效。相关原回归继续保留。
- LambdaLibrary 固定输出目录在独立探针通过；本轮 AccessDenied 可能与并行诊断探针
  写入同一路径有关。后续完整 Gradle 检查期间禁止运行写固定输出目录的探针，避免互扰。

所有运行期后续编辑须在下次集中编译验证；未完成完整测试与发行验证，尚不提交最终实现。

### 第五轮聚焦结果

- Kotlin/Java 与测试源码编译成功；207 项，189 通过、18 失败，无跳过，无进程内存错误；
  10 分 17 秒。日志 `build/type-composition-focused-5.log`，详情
  `build/type-composition-focused-5-failures.json`。本轮按测试类隔离进程，保持 512 MB 与串行。
- 已通过本轮的核心类型、结构继承、存储、新工厂及父限定工厂、库工厂往返检查；
  signed-range 最小整数、const 按源码排序、selector 重复注册和本地同名入口覆盖修复也已通过。
- 两个默认 const 失败已从声明阶段推进至生成工厂的局部 const：纯绑定现已证明空工厂常量，
  实际调用后尚缺相同返回值证明。TemplateFactory 现正常执行后，仅为空且无初值的隐式工厂
  补充精确空记录事实，待编译。
- 联合函数返回的共有必填字段被误判未初始化，确认需补生产代码的读布局，保持原回归不变。
- 字典 merge 上下文被继承的宽泛 NBT.merge 候选干扰；IR 修改入口现使用正确字典 API
  及接收者排名，待编译。其余 Map 具体失败继续检查实际诊断。
- ReferenceMni 已推进至 EntityRef 的 generic 计算属性：现在快照读取真实 templateArgument
  后备字段，保留严格准入；MNI 常量替换夹具补建真实声明符号，待编译。
- 当前还在检查 ReferenceSource 借用执行器、BlockState 的嵌套 NBT 常量事实、DataPresence
  的旧 const 夹具。禁止并行探针写同一固定输出目录。

下一步：完成上述具体修复并重新编译验证，然后执行完整 test/verifyDistribution、
Kotlin MNI 子工程工作流和最终本地提交。原始完整计划仍为本目录独立文件，不依赖上下文保留。

### 第六轮验证启动检查点

所有已定位的第五轮失败均已有对应源修复，尚不能记为通过。新增接点：
列表字面量把期望元素类型传给每个元素；原始 NBT 复制保留真实物理值证据；
闭包复制恢复全部后代引用元数据；联合函数返回的共有必填字段保留读布局；
存在性谓词在写入/返回时物化，查询仍绕过 getter。

当前命令为 `shadowJar test`，集中选择上轮受影响领域及 KotlinMniWorkflowTest、
MniDependencyWorkflowTest（合计 17 个测试类），会话 `55071`，
日志 `build/type-composition-focused-6.log`。所有代理已停止构建；固定目录探针禁止与 Gradle 并行。
本轮仍须在实际结果返回后记录，并继续完整项目验证与最终提交。

第六轮原会话在 turn 中断后已不存在，系统中也无对应 wrapper/test 进程。日志证明主源码、
测试源码和 shadowJar 已构建成功，但测试尚未完成，XML 为空，不能按整轮结果统计。
已重启同一组检查，日志 `build/type-composition-focused-6-resume.log`；原日志保留。
中断前两个错误已分派读取：同名命名空间执行帧，以及无初值 const 的错误计数断言。

### 第六轮完成与第七轮验证检查点

- 重启后的第六轮完成：134 项，125 通过、9 失败，无内存耗尽；7 分 55 秒。
  日志 `build/type-composition-focused-6-resume.log`，详情
  `build/type-composition-focused-6-failures.json`。MNI 依赖工作流、标准库运行、原始 NBT
  多次复制和联合共有字段源码用例均已通过。
- 同名入口的间歇失败定位到优化器：无序清单把本地与导入同 ID 函数的命令混合回写。
  已改为保留发现顺序、本地优先选择、仅回写选中的函数，并新增专门回归；待第七轮编译。
- 三个 Map 失败定位到 fresh 对象字段的 IR 上下文转换：`any` 字段需要声明擦除后的合同，
  同时保留具体载荷事实。修复已完成，现有 unchecked 视图不改变；待编译。
- MNI 两项和 Kotlin 工作流同源于严格快照遗漏精确的内建 SummonFunction，已补准入，
  没有放宽任意宿主类型。联合底层测试补建真实输出地址，保留生产约束；待编译。
- ReferenceSource 仍缺借用执行器分支；既有两个元数据修复未恢复可观察输出。暂停盲目复制，
  准备在闭包复制入口/出口记录引用事实，以首次丢失位置作为下一次修改依据。

下一轮仅重编译与重测上述受影响类和优化器回归，再继续完整 `test verifyDistribution`。
禁止把尚未执行的新修复记作通过。

### 第七轮结果

- `shadowJar test` 新编译完成；54 项，49 通过、5 失败；4 分 59 秒，无内存耗尽。
  日志 `build/type-composition-focused-7.log`，详情 `build/type-composition-focused-7-failures.json`。
- 同名入口原失败及专门优化器回归均通过；CompositionStorage 全部 17 项通过；
  MapIR、MapProjectionIR 全通过。MapMember 剩余一个源码编译失败，正在读取具体诊断。
- 严格 MNI 快照已通过 SummonFunction 准入，三项失败推进至声明域有限预算耗尽；
  先查图遍历与重复捕获原因，不盲目放大预算。ReferenceSource 的输出缺失仍待仪器定位。
- 已确认旧 data constructor 入口被拒绝，泛型仍经过同一入口；普通 A() 只走工厂。
  内部兼容构造器按 owner 限定；字段继承不复制函数声明。
- 标准库之前的全目标重建早于工厂文档导出修复；最终须刷新全部目标 bin.mclib，
  `verifyDistribution` 自身不会重建这些产物。

### 第七轮失败的具体修复依据

- ReferenceSource 的运行期探针在 const 初始化首次丢失引用：`ReferenceValues.create` 与
  `StorageAccess.write` 输入仍有 BoundExecutor/borrow，ConstantBinding 结果变为无引用。
  `ConstantBinding.bindDeclaration` 现保留已通过 write 作用域检查的引用事实；待编译。
- Map 剩余错误来自可变嵌套 union 未递归 widening，仍把 2、3 当作只能写回自身的类型。
  已递归放宽并规范化 union；夹具的结构对象读取改为 `.entries[0].value`，副本独立断言保留。
- MNI 预算的实际分布：65,536 个记录中约 20,000 个是没有后备字段的 final
  SimpleAccessor/SimpleMutator 空包装器，来自标准库扁平实体字段。正在将这两个精确类型
  作为身份叶节点，Property 中的引用替换检测继续保留，预算不增加。

实测使用身份叶节点后完整图为 49,482 项、队列为空，原 65,536 上限保持不变。
这两个 final 类型确实无后备字段；Property 仍保存其身份，因此替换可被检测。
当前第八轮命令为 `shadowJar test`，选择 MapMember、ReferenceMni、ReferenceSource、
KotlinMniWorkflow 及声明/命名空间两个 journal 类，日志
`build/type-composition-focused-8.log`；会话 `15587`。仍未完成最终全量验证与提交。

### 第八轮结果

- 新主源码、测试和发行 JAR 均编译成功；66 项，62 通过、4 失败；2 分 54 秒。
  详情 `build/type-composition-focused-8-failures.json`。
- ReferenceSource 三项全部通过，执行器输出恢复；声明和命名空间 journal 共 46 项通过。
  MNI 已越过原预算错误，下一处诊断为 Source fields require a completed concrete template declaration。
- MapMember 剩余用例出现 4 个编译诊断，正在查看具体原因，不能按递归 widening 修复完成记账。

后续诊断：Map 的 fresh 字面量需在生成前得到已 widening 的类型上下文，不能在生成后
把不变列表改成另一种元素合同；ImVisitor 接线已修复，已有变量和 unchecked cast 不做重构。
MNI 的具体拒绝类型为开放 `GameRule` 泛型原型，来自 nativeDirectory 的环境类型种子。
环境扫描现跳过 raw owner 为 GenericDataTemplate 的原型；实际使用处和闭合实例的严格检查保留。
第九轮选择三个 Map 类、ReferenceMni、KotlinMniWorkflow、MniGenericHeaderPreparation，
命令 `shadowJar test`，日志 `build/type-composition-focused-9.log`；未执行的结果不能标记通过。

### 第九轮结果

- 新编译成功；48 项，43 通过、5 失败；2 分 56 秒。详情
  `build/type-composition-focused-9-failures.json`。MapIR 与 MapProjectionIR 各 4 项全部通过。
- MapMember 的编译错误由 4 条减到 1 条，继续针对剩余诊断定位。
- 三项 MNI 已越过开放泛型原型，推进到库字段的 `<retained-library-type>` 占位符被当作
  源码类型解析；正在检查实际元数据来源。
- 新纳入的 MniGenericHeaderPreparation 23 项中 22 通过，剩余第一应用 header 用例已交
  header reader 所有者读取具体拒绝原因，不把之前未覆盖的用例声称为新回归。

第十轮前的三处源修复：

- fresh 结构对象运行期生成保留已确定的可变字段合同，精确载荷事实另行保留；不把 int
  存储再收窄为 literal 2，unchecked 视图保持原约束。
- 纯 header reader 新增 FACTORY：new、静态普通参数、完整实例 Self，无隐式 this；
  保留不执行主体、不实例化原型的检查。
- LibraryImportSession 的默认常量仅携带实际引用的常量/类型闭包，不再带整份库目录；
  具体占位符来自无关 Generic<T>。新增导入整数默认值仅有 Int 描述符的回归。

第十轮集中检查上述六类及 TypeCompositionLibrary，日志
`build/type-composition-focused-10.log`，命令 `shadowJar test`。通过后刷新全目标标准库再全量验证。

### 第十轮结果与最终验证启动

- 新编译成功；55 项，54 通过、1 失败；3 分 51 秒。
  Map 三组、ReferenceMni、KotlinMniWorkflow、TypeCompositionLibrary 全通过，
  默认常量类型闭包新增回归也通过。
- 唯一失败已推进至 header 旧顺序断言：实际规范顺序为 base/items/value。
  已保持精确排序断言，并按名称检查字段来源、类型及可选性；仅测试编辑，待最终全量检查。
- 正在执行全部 46 目标 `rebuildStdlib`，日志 `build/type-composition-stdlib-final.log`。
  该次产物会补齐工厂文档元数据，完成后执行完整 `test verifyDistribution`。

全 46 目标标准库刷新已成功：6 分 30 秒，各目标 0 错误、0 警告，日志
`build/type-composition-stdlib-final.log`。生成覆盖全部目标和默认副本。
当前最终全量命令为 `gradlew.bat -Dmaven.repo.local=F:/DevCache/.m2/repository test verifyDistribution --offline --console=plain`，
日志 `build/type-composition-full-final.log`。测试类独立进程、串行；临时 init script 只打印每类完成结果。
所有代理暂停独立探针，防止固定输出目录互扰。须等实际全量与发行结果后再提交最终实现。

当前全量会话 `2998`；源实现已暂存（252 文件、6540 插入、3084 删除），未提交，
`git diff --cached --check` 已通过。本地 logs/、build/ 不纳入提交。
截至 DataConstant 阶段已出现 indirectInstanceWritesPreserveTheFixedSibling 与
parentAndErasedViewsReadTheActualFixedFieldValue 两项失败，正在检查具体诊断；完整全量仍须继续。

全量运行期间的编辑不属于本次启动编译产物，须后续集中编译检查：

- Parent/Child 常量视图生成 read_Parent 静态返回 1，实际对象 schema 为 8，最终运行值 18。
  正在给不可信运行时数据参数复用 actual-layout body：保留实际类型/布局与不可信标记，
  不捕获普通实参常量；递归只复用代码布局。
- 三项常量编译失败已定位 capture 处的 `'5' with '5'` 合同拒绝，正在检查真实生产者证据，
  不以显示名或仅公开 type 相等放行。
- DataInheritanceLibrary 两项 const/访问用例、DataTemplateParentIdentityMetadata 的嵌套泛型
  交集用例也已失败，分别交原模块所有者读取具体诊断；尚不能认作通过。

上述修复现已源就绪：Function 仅不可信运行时数据参数采用无普通值常量的布局绑定，
保留递归代码复用；新增递归转发实际 schema=8 的回归。
StorageAccess.constantFor 仅在可信且已初始化、真实基本类型精确匹配、完整常量载荷与
所需字面量值相等时恢复字面量快照，不可信早退保持。两项继承库 const 失败已确认同源。
ParentIdentity 用例修正旧 producer/新导入会话名义身份直接相等断言，改查加载后 Box 身份、
精确参数和会话隔离，结构合同检查保留。均须在全量结束后重新编译验证。

后续全量收集（运行仍未结束）：

- GenericDeclarationContract 的 Shared.setter→getter 流程缺初始化证明。SourceEffects 现仅对
  全体语句可确证为直线静态默认赋值的函数提供初始化集合，成功调用后只恢复 INITIALIZED、
  保持值 Unknown；分支、循环、间接调用等不给证明。原正例保留，新增条件 setter 负例。
- LambdaSyntax 的旧 AST 计数迁移为两处空花括号共享节点和三处显式 compound；解析实际零错误，
  控制块和普通调用检查保留。
- LibFieldAccess 一组 readonly 负例抛 PreparationFactFailure，现由 ImVisitor 精确转诊断，
  readonly 仍拒绝运行时值；CountMatchPart 无 count 初值，旧空构造夹具改完整对象字面量。
- LibFieldAccess 的 World/Worldborder/XP 正例实际命令具备多步独立快照，旧断言仅接受直接复制。
  类型代理正在按有序复制链更新这些断言，保留一次执行与快照独立性要求。
- 该库字段类最终 47 项、18 失败、1 跳过；其它 native 类别仍由序列化代理读取实际诊断，
  不能将全部 18 项都视为已解决。当前全量经过 Map 三组，均通过，无内存耗尽。

### 全量后半段修复检查点（仍未完成验证）

下列源编辑均晚于会话 2998 的编译，不能引用本次旧产物运行证明其通过。

- LibFieldAccess 18 项已全部归因：7 个必需字段旧空构造夹具、override、StdCommands 134 个
  合法去重签名、4 处复制链、readonly 异常转换、TypeDataTemplate 委派转换、文本 extra 与
  编码打印、ItemPredicate 隐式工厂的声明类型上下文。各所有者已编辑，待编译。
- TemplateFactory 现生成带原字段显式类型的局部（含 generic list<T>/optional dict<T>），
  新增 FactoryConstruction 集合回归；`<prepared>` 预留壳不合成工厂。
  库保存生成后的 AST，因此该修复必须再次重导出全部 46 目标元数据，无需格式升级。
- MNI completion 两项由上述 prepared 壳 guard 覆盖；generated inherited-method conflict
  旧负例迁移为父子独立声明正例，另保留局部重复签名及原子发布拒绝。
- FileScope/NamespaceScope 的纯类型查询循环已修，命名空间仅扫描文件本地声明；
  两个 MniHeaderMaterialization 递归模型回归待验证。
- ExprVisitor/DeclarationBinding 优先显式 MNI macro，再用标准 repeat/run fallback；
  SourceCodeBinder.inlineSyntax 支持透明表达式包装，保留 unary/cast 屏障及 callback 检查。
  MniLambdaMigration、Raycast、Repeat、SourceBodyBinding、ProjectionInline 等同源失败待复测。
- SourceCodeBinder 的 int Unary 先按共享 signed 解析；prepared 越界诊断恢复
  UNSUPPORTED_EXPRESSION。ReadonlyPreparationFacts/SourceBinding 两数值回归待验证。
- CompilerSourceIndex 的结构对象从不可变字段合同投影，名义模板完成守卫保持；
  MniNbtReference 回归待验证。
- 测试迁移：InternalReferenceCalls 透明 lambda；Readonly record catalog 使用真实对象字段
  schema 并保留不匹配拒绝；MacroLoading 父关系测试改非空合同保留早/新快照隔离；
  TypeSyntaxResolution 用不同字段的对象交集验证别名、来源及 canonical 身份。
- 仍在诊断：MniSourceEnvironment 两个 readonly 环境用例的 prepared operator 错误；
  TemplateProcessor 的 eager Sequence hash erased-payload 准入、literal typed fact 与 ref 名称旧断言。

全量当前已到 ParentQualifiedFactory，累计日志摘要 1963 项/63 失败/3 跳过，尚未结束。
完整 XML/失败 JSON须等本次执行结束后保存；之后统一编译所有上述编辑，按失败/重要影响范围复测，
刷新标准库，再完成全量和独立发行验证，最后本地提交。不得把已暂存解释为已验证。

只读环境失败现已定位为 parser 将相邻 `copy<int>(...)` 的右尖括号当作关系运算符，
吞入下一调用；修复仅在只读实参顶层、右尖括号后为左括号时结束实参，保留括号内比较。
Sequence/Record 现先验证擦除载荷再计算缓存 hash；journal 测试保留未知对象零调用断言。
整数字段 fact 和 `ref` 名称测试也已按实际合同迁移。以上均为源就绪，等待集中编译。

全量后段补充（同样尚未重编译）：

- ValueModel 的共享映射 join 快路径现在也要求读取布局证明处于 INITIALIZED 且类型精确匹配；
  否则使用正常 join 清理无效证明，保留原回归。
- 不可信参数的递归 body 复用不再携带列表长度或字段存在性值事实，避免负索引被首个实参长度固化；
  只保留实际类型、布局和信任规则，旧 callback 路径保持，新增递归负索引回归。
- 静态初始化证明不再把同名参数遮蔽的 `State.x` 当作全局对象字段；新增真实 marker 初始化与
  未初始化 x 同时存在的负例。
- UnifiedValue 正例改用声明即闭合的 const、独立可变集合副本和完整 Child 工厂，保持输出与库往返；
  旧 const 行为的拒绝已有专门回归。Readonly visitor 精确转换 PreparationFactFailure 为拒绝诊断。
- ContextCallback 的直接 inline 参数采用共享透明 AST 解包，保留 unary/cast 屏障。
- TypeSuffix 的必需字段旧空构造夹具与实体示例/测试的旧初始化、运行期 const 正在迁移；
  泛型调用组合解析和上下文执行命令断言正在基于实际输出归因，不能视作已通过。

### 本次全量实际结果及下一轮

会话 2998 已结束，耗时 1 小时 7 分 32 秒；日志报 2521 测试、97 失败、3 跳过。
XML 另计 1 个 executor 错误，共 317 suites/2522 entries/98 failures。
`LibFieldAccessTest` 的 executor 179 在 `nativeTimeMethodsUseDeclaredReturnTypesAcrossLibraryRoundTrip`
导入 checkpoint 的 DeclarationStateJournal.capture.walk 内堆耗尽；该类源有 69 个测试，日志只完成 47 项，
因此不能把本次称作覆盖所有用例的全量完成。`verifyDistribution` 未执行。
失败详情已保存 `build/type-composition-full-final-failures.json`。

后半段实体旧夹具、内联命令断言、库格式版本 1→4、JSON 空格断言均已按实际诊断迁移，
保留运行结果、上下文、导出和原子性要求。DataTemplate 访问检查另有真实问题：
结构兼容使无继承关系的私有类型获得 PROTECTED，现改回绑定声明身份与名义祖先策略。
Library capture 补直接负浮点字面量以保留负零 bits。均待集中编译。

下一轮先编译/重建全部 46 目标标准库，再按本次失败与重要影响范围检查。
同时排查大测试类的内存占用；未明确原因前不把增加 heap 当作已解决。

第三次全目标重建命令为 `rebuildStdlib`，日志
`build/type-composition-stdlib-typed-factories.log`。新 parser、Kotlin、Java 主代码已编译成功；
此次导出包含隐式工厂局部变量的原声明类型上下文。下述 journal 修复晚于这次主代码编译，
不影响库 wire/工厂 AST，须在后续测试前重新编译，但不要求再次刷新库元数据。

OOM 已找到一个确定的跨代增长路径：DataTemplate checkpoint 将同一物理 scope 的完整快照
反复追加，并把历史 JournalSnapshot 包装对象纳入后代根。现改为按 CompoundDataScope
身份保留一次快照，只发布真实成员根，仍保存/还原脱离当前声明的 scope。
新增回归检查重复捕获的根集合不增长、替换前后作用域均可回滚/恢复。
尚不能据此声称 OOM 完全解决，须检查 LibFieldAccess 全部 69 项。

集中第十一轮选择器保存在 `build/type-composition-focused-11-selectors.json`，临时 init script
避免 Windows 命令行长度限制。包含此前失败方法、重命名后的等价方法、重要新工厂/常量/初始化回归，
以及受 snapshot 影响的 journal 类和完整 LibFieldAccess 类；正式全量仍须随后通过。

上述全部 46 目标标准库重建成功，耗时 7 分 4 秒，各目标 0 错误/0 警告。
现执行 `shadowJar verifyDistribution test` 加第十一轮选择器与进度 init script，
日志 `build/type-composition-focused-11.log`，会话 5827。新 journal 源已再次成功编译；
prepareBuiltinImportPlan 已刷新，测试结果尚未产生。

第十一轮耗时 11 分 48 秒，263 项/14 失败/1 跳过；另有 executor 12 OOM，失败 JSON：
`build/type-composition-focused-11-failures.json`。发行包冒烟先执行并通过：独立 JAR 零错误/警告，
成功导出泛型 NBT 引用数据包。

- FactoryConstruction 9 项全通过，含集合泛型上下文；MNI 宏/inline/readonly 环境及数值、
  UnifiedValue 4 项、ValueModel 14 项、上下文/传送相关先前失败用例均通过。
- Declaration journal 31、CompilerObject 20、CompilerState 24、CompletionDomain 14、Namespace 16
  全通过，新的快照去重与脱离作用域恢复回归通过。
- MniCompoundScopeJournal 一项污染输入夹具现被 CompilerValue 的新准入提前拒绝，
  正按 TemplateProcessorJournal 的相同办法保留边界零未知对象调用验证。
- LibFieldAccess 本次只完成 13/69；OOM 位于 Function.checkpointFunctionState map261/290，
  经 FunctionStateJournal/CompilerObjectJournal/DeclarationStateJournal 到 import commit。
  去重修复正确但并非 OOM 唯一原因，正检查跨项目函数图保留；不得记为69项通过。
- 常量视图的 trust 标记在独立实参/变量复制时被重置，现统一在 writePayload 新根复制边界保留，
  目标仍为新 Place；原88断言保持，补保存视图后复制读取8。
- DataOnly 字段只保留真实 producer 结构中的 literal singleton，而无完整常量载荷；
  现仅对可信、初始化、精确基本类型、Unknown值的分支从真实父结构字段合同恢复证明。
- Companion 源运行已通过，fresh import 的绑定指向普通模板，静态初始化摘要现接受其精确配对对象，
  同名局部和条件写入负例保持；复制链单项断言亦按真实多级快照迁移。
- 实体构造纯预检漏识别 isEntityTemplate，及导出扫描误把再生 summon intrinsic 当外包函数，
  正在窄修；Display 另一个运行期 const 改 var，未降低UUID/目标断言。

上述修复中晚于会话5827编译的编辑仍待集中验证。没有运行新全量；旧全量结果不能覆盖它们。

第十二轮命令 `shadowJar test`，选择剩余失败、DataConstant/Generic/EntityTemplate 全类和
MniGeneratedDeclaration 全类；日志 `build/type-composition-focused-12.log`，会话3848已结束。
2 分 45 秒，29项/4失败/1跳过，另 executor63 OOM；详情
`build/type-composition-focused-12-failures.json`。

- DataConstant 8 项全部通过（88、复制视图8、递归8和79），GenericDeclarationContract 4 项全通过。
  复制链、CompoundScope 污染拒绝、Display UUID、LibraryExport 均通过。
- EntityTemplate 到7项，1失败1跳过：普通实体构造及导出 ownership 多项已通过；
  summonsCopy 编译仍2错误，正在读实际诊断。nbtMerge 用例遭 OOM 未完成。
- 新的 MniGeneratedDeclaration 正例在 fresh prepare 被父/子同签名候选误判歧义，
  已交类型/投影所有者修真实 lookup；局部重复拒绝通过，不能删除正例隐藏问题。
- DataInheritance 两项已越过原字面量赋值合同错误，正例现因运行期 literal 视图捕获失败，
  负例已正确拒写但仅报告 shared property 无 setter、缺 constant 分类；正在依据新实际路径检查。
- 另一次 OOM仍在 DeclarationStateJournal.capture→import commit，同样说明下一轮需先修项目图生命周期。

已用三个独立 Project 编译实证相同 builtin String.toText 身份跨 init 的 parent数量1→2→3、
可达函数数3→5→7；旧调用者图被保留且 journal会沿其 parent遍历。
拟在 Project.init 清旧 namespace 前，以现有 FunctionInventory.all 收集并释放旧调用图，
新增本项目关系保留/下一项目不留旧调用者的隔离回归；不引入 global ledger 或强制完成懒声明。

第十二轮剩余源码已进一步定位并修复，待新编译：

- producer singleton 证明确实已成功，后续 captureClosed 因 literalId/primitiveId 不同丢弃已证明载荷；
  现仅在 canonical literal payload 且精确 primitiveId匹配时重编码该已知值用于还原，
  其它视图继续要求真实证明。负例继续精确断言该字段不可写且无 setter。
- 纯 SourceFunction 投影现保留声明接收者，pure binder 将接收者转换加入重载排序，
  与运行期选择保持一致；父/子函数本体不复制，本地重复仍拒。
- 最后实体编译错为 `consumedCount` 的 EntityRef<StoneDisplay> 被按裸 StoneDisplay排名；
  ParameterMatcher 对真正实体方法使用 EntityRef<Owner>，纯投影同步相同合同。
- Project.init 通过现有 FunctionInventory（不请求未绑定library functions）清旧图，
  Function.recordCall 按对象身份去重；closeCompilation未改变，当前项目仍可导出与模拟。
  新增两项 ProjectIsolation 检查连续项目、同名不同函数、当前图与journal恢复。

下一集中验证将包含完整 LibFieldAccess69、ProjectIsolation与调用图受影响journal类，
并检查实体/继承字面量/生成方法剩余入口。通过前不启动耗时全量。

第十三轮已启动，命令 `shadowJar verifyDistribution test`；选择完整 LibFieldAccess、
ProjectIsolation、Function/Namespace/CompilerObject/CompilerState journal、EntityTemplate、
MniGeneratedDeclaration，以及两项 DataInheritance 剩余失败。
日志 `build/type-composition-focused-13.log`，会话44072。
纯投影额外保存实体接收者候选，在实际 EntityRef 时使用，普通 DTO 仍使用原Owner合同。

第十三轮实际结果：3分38秒，123项/1失败/2跳过，另executor69/70均OOM；详情
`build/type-composition-focused-13-failures.json`。发行包冒烟再次通过。
DataInheritance2、MniGenerated4、Function journal29、Namespace16、CompilerObject20、CompilerState24
全部通过；EntityTemplate已执行部分均通过，但nbtMerge仍OOM未完成，LibFieldAccess仍仅13/69。

唯一普通失败是新ProjectIsolation回归的builtin.parent应清空断言（93行）；
说明从namespace/当前child图收集不足，优化后caller可不再从namespace可达，仍由共享builtin反向持有。
下一修复仅补现有共享基本类型/内置root的清理收集，不重复旧3project探针，不盲加heap。
在该回归及完整大类通过前仍不启动全量。

第十四轮会话84092，日志 `build/type-composition-focused-14.log`，命令
`shadowJar verifyDistribution test`，覆盖 LibFieldAccess、ProjectIsolation、EntityTemplate，
以及 CompilerState/Function/Namespace journal。新增 `io/InitializedBuiltinData.kt` 仅读取固定
compiler-owned catalog/表的已初始化 lazy slot，cleanup再纳入其raw函数及反向旧调用者；
不调用未初始化provider。新文件已暂存，仍不代表验证完成。

第十四轮实际结果：4分41秒，154项/11失败/0跳过，**无executor错误或OOM**。
完整LibFieldAccess69项已执行（58通过/11失败）；ProjectIsolation8、EntityTemplate8、
CompilerState24、Function29、Namespace16全部通过。发行包冒烟通过。
失败 JSON `build/type-composition-focused-14-failures.json`。

剩余11项均在此前因OOM未完整覆盖的LibFieldAccess类：效果给予、属性写入、进度/队伍/消息/tag
命令的直接复制断言正迁为有序独立快照链；AttributeModifier/TemplatePool必需id改完整typed值；
collection工厂改显式集合类型局部后return，避免先构造shape再unchecked view所保留的实际shape写约束；
打印夹具用external typed nbt覆盖运行期NBT编码（普通闭合对象现会优化为text），原命令/参数/载荷断言保持。
Random命令缺失predicate项仍在读实际输出，尚不认定已解决。下一轮仅复测这11方法，随后全量。

第十五轮仅上述11方法，日志 `build/type-composition-focused-15.log`，51秒，8通过/3失败/0跳过，
无OOM；详情 `build/type-composition-focused-15-failures.json`。打印、Random和多组快照链已通过。
Attribute/TemplatePool的缺必需字段编译已修，现推进到其旧直接复制断言；正在用同一严格复制链检查迁移。
collection的dict/ImmutableList局部已合法，map不能直接使用对象字面量上下文（既有API没有该转换），
正改用已有Map测试通过的raw NBT→map视图模式；避免为测试新增语言功能或放松真实shape写约束。

第十六轮仅剩余3方法，日志 `build/type-composition-focused-16.log`，28秒，3项全部通过。
主实现自第十四轮后未改变；第十四轮58个LibField成功方法、第十五轮8个、第十六轮3个共同覆盖69方法，
这是分轮证据，不称为单次69全通过。后续正式全量须完整执行并通过。

最新独立发行JAR运行 `examples/type-composition` 成功（compiler 0 errors/0 warnings），日志
`build/type-composition-example-current.log`。随后对新导出mcfunction运行现有命令模拟器，得到
`[3,7,11,4,9], spawn`，Marker `{active:1b,name:"spawn",x:3,y:4}`，最终栈深0；
日志 `build/type-composition-example-current-command-check.log`。这是生成命令模拟，未运行真实Minecraft。

即将执行第三次完整 `test`，只加载进度输出init script。发行冒烟复用第十四轮通过证据，
期间仅LibFieldAccess夹具编辑，JAR/主实现/标准库资源未变，无需再次重复同一冒烟。

第三次全量已启动，会话79571，日志 `build/type-composition-full-3.log`。
启动时主代码、生成parser、测试编译和资源任务均UP-TO-DATE，使用上述已聚焦检查的编译产物。
当前实现已暂存288文件/7687插入/3319删除，cached diff检查通过；该数字不是完成声明。
除本实施记录外暂停源码编辑和代理探针，等待真实全量结果。

第三次全量中途记录：LambdaDeliveryIntegrationTest 在调用库往返案例时报告
AccessDeniedException（line16），同轮随后独立 LambdaLibraryTest 2/2 通过。
只读检查未发现 build/lambda/library 持续的ReadOnly/ReparsePoint属性或ACL拒绝；
后续通过案例已经重写同一路径，尚不能据当前目录推定失败瞬间的路径/锁原因。
等待完整XML堆栈后再作窄范围处理；全量继续，未修改测试或主实现。

第三次完整test实际结束：1小时1分17秒，316 suites，2553项，2548通过/3失败/2跳过，
XML errors=0，无executor错误或OOM；日志 `build/type-composition-full-3.log`，
失败详情 `build/type-composition-full-3-failures.json`。LibFieldAccess69、EntityTemplate8、
ProjectIsolation8、TypeComposition27、TypeCompositionInheritance8、TypeCompositionLibrary7均完整通过。

仅剩失败为：
- LambdaDeliveryIntegrationTest：PackAssembly.publish line106将新stage/pack移到imported/pack时
  Windows AccessDenied；同轮稍后独立LambdaLibrary2/2通过。尚不假定锁原因。
- MniDeclarationPrivateState：非法Sequence现在在构造时已被拒绝，旧夹具未到达checkpoint。
  正改为合法建key/cache后仅反射污染，保留checkpoint拒绝且宿主hash全过程零调用。
- StorageViewTest数值视图初始化：旧null-view断言与复制后保留实际Int/不信任常量合同冲突。
  正改为目标自身Place/独立StoredData/实际Int/public Float/trust=false，保留原无转换与复制命令断言。

这些修复只影响相应证据范围；其余全量通过结果与发行/标准库/示例证据继续适用。

最终第十七轮定向检查（会话64311）：1分14秒，46项全部通过，0失败/0跳过，
日志 `build/type-composition-focused-17.log`。MniDeclarationPrivateState22、StorageView21、
LambdaDeliveryIntegration1、LambdaLibrary2全部通过；仅前两类的旧夹具已修改，
主实现没有改变。Windows目录move异常未复现；未发现持续权限/句柄缺陷，未引入无依据的生产改动。

完整轮的每类统计另存 `build/type-composition-full-3-summary.json`，失败详情保留，
其开始时暂存树为 `af94487fc441b42a3ddb9b0d68af23c8e68b38d0`。
当前只更新上述两个测试夹具和本实施记录，未使其余通过的主实现/资源证据失效。
所有计划规则均有通过的适用检查；保留两项明确跳过及真实Minecraft未执行的边界。

## 2026-11-16 工厂语义修正（已完成）

用户撤销先前父工厂接收者绑定决定，采用上述普通类型命名空间工厂。已完成：
1. 删除执行/纯类型绑定/副作用分析中的特殊父查找和隐式实参；保留普通方法接收者。
2. 将父限定测试迁移为普通父工厂调用、返回独立值、子工厂逐字段合成与类型检查，
   并拒绝废弃接收者语法及实例上的工厂调用。
3. 同步修改计划、规范、迁移说明与可运行示例；仅运行受影响工厂、方法、存储、库往返检查。

前一轮全量结果是先前证据，不称为本次新执行验证。后续记录实际命令及结果。

已删除执行、纯绑定和effects中的父限定分支及接收者参数插入，清除仅供该语法使用的
nominalAncestorsNamed查找；工厂通过实例调用时报告类型命名空间诊断。
普通this解析、方法接收者传递和TemplateFactory.receiverless保持各自既有合同。
现有库往返案例增加Derived工厂调用Value.new并显式组合字段，以及导入后total()读取this。
示例Marker同样采用Position.new返回值组合。

本次首次检查会话61451，日志 `build/factory-namespace-focused.log`：
`shadowJar verifyDistribution test`，选择FactoryConstruction、ParentFactoryComposition、StorageView、
TypeCompositionLibrary、DataInheritanceMethod、DataConstantCall、EffectAnalysis、MniGeneratedDeclaration、
TypeCompositionInheritance九类。

实际结果：3分50秒，73项全部通过，0失败/0跳过；重新编译main、测试和发行JAR，
发行包冒烟通过。FactoryConstruction10、ParentFactoryComposition5覆盖显式实参、独立值、
泛型组合、普通参数/返回类型错误、无隐式this、旧绑定语法和实例工厂调用拒绝。
DataInheritanceMethod10、MniGeneratedDeclaration4和库往返中的total()继续验证普通方法this。
TypeCompositionLibrary7包含导出/全新导入后的Derived.new字段11/4与方法结果15。

使用新发行JAR在examples/type-composition执行 `build --offline` 成功，compiler为0错误/0警告，
日志 `build/factory-namespace-example.log`。对新生成mcfunction执行原命令模拟器成功：
`[3,7,11,4,9], spawn`、Marker `{active:1b,name:"spawn",x:3,y:4}`，最终栈深0；
日志 `build/factory-namespace-example-command-check.log`。未运行真实Minecraft。
本次按影响范围执行上述检查，没有重新执行此前2553项全量；旧全量结果仍明确标为前一轮证据。

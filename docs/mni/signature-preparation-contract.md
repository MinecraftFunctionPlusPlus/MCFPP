# 纯签名准备与首次泛型声明完成契约

本契约细化 `source-projection-contract.md` 的 E01。交付边界是实际 compiler 声明到完整、同域的 `BindingEnvironment`；函数体生成、实参执行、宏展开和后端提交继续由其既有叶负责。本文是设计交付，不表示下列产品接口已经存在。

## 1. 已核实的入口和必须替换的路径

| 现有接点 | 实际行为 | 准备阶段的处理 |
| --- | --- | --- |
| `SpecializationPolicy.resolveDeclaredSignature` | 调用 `Function.restoreDeclarationEnvironment`；后者改 `scope.parent`，可能恢复文件及 imports | 增加显式准备入口，消费冻结 lexical view，不调用该恢复方法 |
| `DeclarationBinding.declaredType/type` | 已有 AST 类型绑定；应用模板调用 `GenericDataTemplate.compile`，兜底 `MCFPPType.parseFromContext` 也可进入 eager readonly visitor | 提取共同类型 AST walker，由具体 legacy/preparation context 决定名字与应用类型的解析 |
| `MCFPPType.resolveTypeId(Specialized)` | `prepareHeader`、恢复 readonly Var、`prototype.compile` | 准备阶段通过自己的已登记 canonical 域查询；缺失进入准备队列，禁止从该方法补找 |
| `GenericDataTemplate.compileInDeclarationEnvironment` | 创建 model，恢复环境，运行字段和注解 visitor，改 index/cache，登记合同 | 拆成 header 准备与声明 materialization；保留同一 canonical 实例身份 |
| `Project.collectingTemplateSignatures` / `completePendingTemplateContracts` | 在收集边界完成继承、字段推导、注解和合同；会产生更多具体类型 | 外部声明完成协议的实际生产执行者；纯 prepare 不隐式触发它 |
| `SourceDefaultBinder` / `SourceConstantFacts` | 已实现 formal 身份、前序约束、闭合 readonly、数值与转换规则 | 继续作为规范纯事实逻辑；禁止新增语义不同的默认求值器 |
| `SnapshotSourceCodeBinder` | 只读完整具体候选并选择；已保留 normal/default/effect/order | E01 最终消费者；准备器不挑 outer-call winner |
| `MacroLoadingSnapshot.prepare` | 唯一宏元数据 parser，预收集 callback，整域冻结后仅 lookup | 每轮使用同一完整类型域重投影普通和 Java/Kotlin 宏 |

当前源码的 `parameter` 只接受 `= value`，`FunctionParam.defaultContext` 和 `FunctionParamInfo.defaultContext` 是 `ValueContext?`。本设计保留该语言边界，不新增任意 expression default，不改变 parser、ParamInfo 或库 schema。`b = a + 1`、`b = effect(a)` 等一般表达式示意表示既有 BoundExpr 模板能力，不能作为当前参数声明源码示例；Kotlin 默认参数也不自动映射到 MCFPP。source-projection-contract.md 中同类示意按这个边界解释。

合法 value 包括 scalar、NBT list/compound/array、coordinate、selector、null 和字符串；list/compound 的元素及多行插值仍可嵌现有 expression。它们必须逐形状处理，不能因为最外层叫 value 就认定闭合。真实源码正例首先使用现有 int/float/bool/string 默认、显式 readonly 类型实参及模板类型替换；type-valued default 若只来自已准备 metadata/模型，测试必须这样标注，不能伪称 `T:type=int` 已被 parameter grammar 接受。

准备中禁止 `Function/NativeFunction.invoke`、`GenericFunction.compile`、`Function.compileBody`、`MCFPPExprVisitor`、`PrimitiveCompiler`、`Property.getter`、readonly Var 恢复、`buildUnConcrete`、宿主注解构造或 `Annotation.on`。内部读取现有 `Var.storageBinding` 的常量证明可以；不得先创建新 adapter 再读取它来选类型。

## 2. 数据、身份和结果

新增生产协调器 `analysis/SignaturePreparation.kt`，实际实现词法视图、候选准备、类型请求队列和 staging 生命周期。其输入来自真实 Function/GenericDataTemplate/Namespace/FileScope；没有作者提供的 resolver service，也没有只在测试中实现的 factory。

```kotlin
internal class SignaturePreparationSession(
    declarations: CompilerDeclarationUniverse,
    limits: PreparationLimits
) {
    fun prepare(request: SignaturePreparationRequest): SignaturePreparationResult
    fun discard()
}

internal data class SignaturePreparationRequest(
    val site: SourceSpan,
    val lexical: DeclarationLexicalSnapshot,
    val candidates: List<CallableDeclarationId>,
    val explicitReadonly: List<PreparedExpressionFact>
)

internal sealed interface SignaturePreparationResult {
    class Ready(val declarations: PreparedDeclarations) : SignaturePreparationResult
    class NeedsCompletion(val requests: List<DeclarationCompletionRequest>,
                          val staged: PreparedHeaderBatch) : SignaturePreparationResult
    class Rejected(val diagnostics: List<PreparationDiagnostic>) : SignaturePreparationResult
}
```

所有集合防御复制。`PreparedExpressionFact` 为实际 compiler 类型身份、不可变 CompilerValue 证明、来源及声明依赖；不持普通运行时 Var。类型常量的全部可达 TypeId 都必须在本轮域内有描述，不调用全局 `resolveTypeId` 推测。

`PreparedCompilerSignature` 至少保存：原 `CallableDeclarationId`、原真实 Function 受控关联、owner 的 `inheritanceIdentity`、完整 readonly 形参和闭合实参、具体 normal/return 类型、全部 formal identity、原 default syntax、来源以及声明版本。这个内部准备结果允许持 compiler 对象；公开 Code 与 detached plan 仍不持这些对象。

原声明身份与具体签名身份分开：同一 `GenericFunction` 的历史 specialization 不作为额外重载；一次 request 对每个原声明最多交付一个具体签名。函数 key 可以包含只读闭合值对应的稳定 specialization identity，不能用模式、返回类型或物理 TypeKey 计数制造重载。

`CallableDeclarationId` 对现有 Function 绑定其 declaration identity；header 中尚未 materialize 的成员使用 `(canonical owner TypeId, declaration source unit, member ordinal)`。这些成员只有在完成阶段建立到真实 Function 的一一映射后才可进入生产调用白名单。不能构造占位 Function 来绕过此要求。

`PreparedDeclarations` 含完整域事实、所有候选结果和完成版本；它不含 winner。`Rejected` 不携可使用的半成品 BindingEnvironment。候选不适配 readonly 的结果以 `Inapplicable` 保存原因，不能让一个本来不匹配的候选阻断另一合法重载；语法、权限、域或声明内部错误仍是明确诊断。

## 3. 共同类型解析与 lexical view

新增 `type/TypeSyntaxResolver.kt`，把 `MCFPPType.parseFromContext` 及 `DeclarationBinding.type` 的现有类型 AST 分支收敛到一个 walker。仍使用现有 ANTLR parser，沿用 primitive/container/union/vector/selector 规则；不写字符串分割解析器。

```kotlin
internal interface TypeSyntaxContext {
    fun named(namespace: String?, name: String, site: SourceSpan): MCFPPType
    fun readonly(expression: mcfppParser.ExpressionContext): PreparedExpressionFact
    fun apply(head: GenericDataTemplate, arguments: List<PreparedExpressionFact>,
              site: SourceSpan): MCFPPType
}
```

这只是共享 walker 的内部 context 边界。H01 同时提供实际 legacy 实现，保留当前普通编译行为；H02/H03 的 `PreparationTypeContext` 由上述真实 session 构造，并在 H05 接通真实 header factory。最终生产准备没有“缺 factory 就 fallback legacy”的路径。阶段性尚未启用的应用类型必须带 span 拒绝，后继启用后删除该临时门。

primitive 及容器构造沿用现有类型实现和 TypeRelations；`MCFPPTypeWithGeneric.replaceGenericParam` 的分支也必须通过同一申请/登记点报告新类型。不能把一个有副作用的 override 当纯工厂任意调用；首期支持已知 compiler 类型，未知实现明确拒绝。

`DeclarationLexicalSnapshot` 放在 `analysis/DeclarationLexicalSnapshot.kt`。它从原声明的 `declarationFile` 或持久 `DeclarationEnvironmentInfo` 读取 namespace/import 映射，并从已登记 namespaces/file scopes 复制关系；不得调用 `resolveImports`、`restoreDeclarationEnvironment` 或修改 `currFile/currNamespace`。对未准备的 alias 返回完成请求，不能调用可构造泛型的 `resolveAlias`。

查找顺序沿用 FileScope 的真实可见规则及本地/库/std 同 namespace 优先级；qualified name 只访问该文件合法导入的 namespace。函数候选保留整个可见集合，模板参数绑定先于声明外部同名类型。owner/access 检查使用原声明 owner 与调用者能力；不能借 caller 的同名 T、变量或 imports 绑定声明 default。

`DeclarationBinding` 增加显式 preparation context，支持没有临时 Function 的 header 字段推导。当前 Function 构造器入口仍包装 legacy context。纯 context 的 lookup 直接读取 snapshot/formal facts，不调用 `FunctionScope.getVar`（它会写 stackIndex），不进入 receiver getter。super、成员或构造表达式若当前纯事实不足，产生结构化请求/诊断；不能走 legacy bind 分支补全。

## 4. readonly 与 defaults 的准备算法

1. 纯绑定 outer call 的每个显式 readonly 表达式一次，保留完整证明及 span。运行时函数调用不能提供 readonly 常量。对每个候选独立使用同一显式事实序列，按声明顺序处理 readonly 形参，完成其类型替换和默认值；此阶段没有 normal 参数求值，也没有 winner。
2. readonly default 只读原声明自由常量和前序 readonly。所有 formal 名字先建立遮蔽表：后向、自引用、normal formal 和循环均明确拒绝，不能退回声明或 caller 同名自由变量。值为深不可变 typed 常量；类型常量递归保留域内描述。数值折叠共用 SourceConstantFacts/PrimitiveEvaluation 的 int32、float 位模式、混合比较、短路及转换规则。
3. 完成 readonly 后通过共同类型 walker 解析具体 normal/return。normal default 此时只绑定为表达式模板，引用 readonly、前序 normal 和原声明自由身份；任意普通/native 调用保留为 Call，不解释其返回值。模板类型可推导，effects 和顺序不被消除。这覆盖已准备/生成模板；真实 source 只从现有 ValueContext 取得默认来源。

`SourceDefaultBinder` 增加内部 compiler-fact 适配入口，使 `DeclarationBinding` 的准备模式与现有 BoundExpr closure 使用同一纯运算/转换实现。禁止保留一份旧整数-only 折叠，再为 header 单写 float 或 bool 分支。该入口不要求先伪造具有未解析 normal 签名的 SourceFunction。

现有 source value 的适配复用 DeclarationBinding 的 literal/nbtLiteral AST 分支和冻结 CompilerValue。已闭合的递归值投为带准确类型/schema 的 BoundExpr Literal，不经过 SourceCodeBinder 当前的 scalar-only literal 门，不把 payload 转成字符串再解析。非闭合 list/compound/插值必须保留每个子表达式及 effect；不能用 null proof 冒充闭合，也不能调用旧 ExprVisitor 求值。H02 对全部合法 value 做分类；V01 负责目前模型缺失的非闭合形状，父环境验收保留该门。

具体 compiler 签名齐备并完成域冻结后，E01 才使用现有 `SourceDefaultBinder.bind` 生成最终 BoundExpr 模板，映射到同一原声明的 readonlySymbols/normalSymbols。模板不带 call-specific 值；`SourcePreparedReadonly` 单独记录该次准备的完整闭合实参及原声明 FunctionKey。

准备依赖 readonly 的 normal 类型不等于 normal 实参兼容性判断。E01 不根据 normal 类型、参数模式或当前缓存选择 winner；最终 SnapshotSourceCodeBinder 对同域完整候选执行既有排名、defaults 计分和选中后的模式验证。

字段推导或 default 内部的普通子调用若必须推导结果类型，先准备其全部候选，再调用 `ParameterMatcher` 新增的 prepared-facts 入口，复用现有 `best`/conversion/default 评分；该入口不再自行 resolve 声明。它只负责该子表达式的静态类型，不提交或提前选择 outer macro call。禁止第三份排名实现。

## 5. 首次 Box<int>：canonical header 与完整声明阶段

`GenericDataTemplate` 新增私有 `preparedHeaders: Map<SpecializationKey, PreparedHeaderEntry>`；原 `compiledTemplates` 只暴露已完整完成的实例。二者的 key 使用现有 SpecializationKey/GenerationOptions 和闭合 readonly payload，不能以类型显示名或 args 的 Var identity 作 key。

```kotlin
internal enum class HeaderPhase { RESERVED, HEADER_READY, COMPLETING, COMPLETE }
internal class PreparedTemplateHeader(
    val identity: TypeId.Specialized,
    val model: CompiledGenericDataTemplate,
    val readonlyFacts: List<PreparedExpressionFact>,
    val parents: List<MCFPPType>,
    val fields: List<PreparedFieldHeader>,
    val members: List<PreparedCallableHeader>,
    val stamp: DeclarationStamp
)
```

phase 是私有 entry 的可变状态；发布的 header record 不可变。`PreparedFieldHeader` 保存原声明 identity、准确类型、nullable/const/static/access、property 种类、initializer syntax、span；`PreparedCallableHeader` 保存成员签名/default 来源。它们不是 Var/Property/Function 适配器。

`PreparedTemplateHeaderReader` 放在 `analysis/PreparedTemplateHeaderReader.kt`，直接读取原 `GenericDataTemplate.ctx` 中的成员结构，复用 DeclarationSyntax、共同类型 walker 和准备模式 DeclarationBinding。读取成员结构不建立第二套语言 parser；类型和表达式算法不在 reader 中再实现一次。它完成：

- 按 readonly 事实替换声明的父表达式，准备所有 parent headers，保留不同实际绑定的 inheritanceIdentity。
- 先登记显式字段和方法头，再用既有纯绑定规则推导未标类型字段。initializer 中的函数、getter/native 不执行；无法收敛或当前无纯 schema 的表达式返回带来源诊断。
- 以现有继承合同的 canonical origin、最具体 owner 和同源菱形规则形成字段视图；不得 first-parent 取值。下表 H05B 提取现有字段兼容规则的纯入口，不能复制一份比较规则。

factory 在 batch 私有 staging 中先登记 RESERVED 的真实 `CompiledGenericDataTemplate` shell，再迭代填 header；不用 `args` getter，不向 scope 塞 readonly Var，不调用 `.initialize()` 的未知 override。已知 DataTemplate/GenericObject 分支必须有明确实现；未知子类先拒绝。同 key 重入复用同 shell，类型引用环允许，继承环仍由已有 graph contract 拒绝。

shell 的 TypeId 与后续实际编译一致；parents 只能在 staging 中构造。真实字段仍在 record 中，空 scope 不能冒充“没有字段”。只允许 header-aware preparation 查询这个 cache，普通 getType/Namespace/Datapack 导出不能得到一个被当成 complete 的空 model。

`compileInDeclarationEnvironment` 必须先查 COMPLETE cache，再查 preparedHeaders 并取同一 shell；把旧成员构建逻辑应用到这个尚未 materialize 的 scope，不能另造 CompiledGenericDataTemplate，也不能直接因为存在 header 就 early return。完整阶段才创建 readonly adapter、Function/Property/Var 和构造函数。成员声明与 header origin 建立一一关联，后续真实目标表以这个关联消费。

命名沿用既有 index 规则，但仅在受控 publish 时预留一次。staging shell 的身份来自原 prototype 和完整 typed specialization key；其未发布名字不能进入 catalog、目标表或导出。发布先确认没有已登记的同 key 实例，再分配最终名字并冻结 header，最后登记 canonical entry。纯 staging 失败不消耗全局/index 名称；不能以 session TypeKey/arena 计数命名。已有 COMPLETE 实例保持原名字和身份。

完整阶段完成继承、字段推导、待执行声明效果和合同后，才设置 COMPLETE 并加入既有 compiledTemplates。Project 的完成 barrier 负责报告成功，不能在 registerTemplateContract 之前就标 COMPLETE。失败回到原 checkpoint；正在 COMPLETING 的实例只允许该完成队列内部解环，不能被普通调用、导出或另一次请求当成完成。

## 6. 外部声明完成与注解重试协议

纯 header 不能预测宿主注解生成的字段、权限或方法。合法首次带注解泛型必须有实际完成路径，不能永久拒绝后宣布 E01 已完整实现。

```kotlin
internal class CompilerDeclarationPreparation {
    fun complete(batch: PreparedHeaderBatch,
                 requests: List<DeclarationCompletionRequest>): DeclarationCompletionResult
}
internal sealed interface DeclarationCompletionResult {
    class Completed(val revision: DeclarationRevision) : DeclarationCompletionResult
    class Rejected(val diagnostics: List<PreparationDiagnostic>) : DeclarationCompletionResult
}
```

实现放 `expand/CompilerDeclarationPreparation.kt`，由受控声明准备阶段调用；它接通 `Project` 的现有声明收集/完成 barrier 和 phase-aware GenericDataTemplate materialization。纯 `SignaturePreparationSession.prepare` 不调用它。

batch 显式携带该次 staging 中已保留的 canonical shells。完成器接管它们的发布权，不能仅凭 requests 重新 new 一批 model。纯 NeedsCompletion 不预先发布全局cache；Ready发布或外部completion接管时做一次原子登记。两个请求若在登记前碰到同key，不允许各自发布：当前compiler准备队列串行消费batch；过期batch必须丢弃并从已登记canonicalentry重准备，不能把自身shell替入旧graph。未消费batch可直接discard，不留RESERVED条目。

完成请求至少区分 `MATERIALIZE_HEADER`、`PENDING_DECLARATION_EFFECTS`、`UNRESOLVED_ALIAS`、`REQUIRES_DECLARATION_LAYOUT`。请求含原声明身份、闭合 readonly facts、span、被检查的 stamp，不含闭包。即使未带注解，首次 header 若真实成员/backend layout 尚未完成，也须请求 materialization 后才能发布生产环境。

完成器在全局声明 checkpoint 内执行：保存 Project/namespace/scope/function/object/storage/声明深状态与名称/diagnostic 关系，再运行现有声明流程；不进入 Function body 编译、对象 initializer 执行或宏 host。必要的 Annotation.build/on 是这个外部阶段的明确行为。全部成功后保留新声明并返回 revision；任一错误回滚全部状态，诊断以事务结果交付。失败后的重试不能因残留 applied marker 跳过注解，也不能重复累计生成成员。

这些 checkpoint 必须消费当前图中最终 `M_GLOBAL_STATE` 组合能力及 DeclarationStateJournal；仅调用三个 private hooks 不足以声称深回滚。若组合能力尚未交付，本完成叶依赖它而等待，不能放一个 no-op journal。header cache/phase/记录、失联 canonical shell 都必须纳入 GenericDataTemplate 私有 checkpoint roots。

外部阶段按实际需求应用注解一次：全局候选中不适配 readonly 的声明不要求执行其效果；所有已准备且适配的候选需要的类型都进入完成集合，仍不按 normal 实参选 winner。已完成实例不得重跑 annotation visitor。至少用计数注解证明纯 prepare 为 0、一次完成为 1、再次 prepare/complete 仍为 1。

E01 在 completion 成功后丢弃本轮全部 SourceFunction/BoundExpr/TypeRef/MacroFunction 投影，重新采集完成事实、调用 MacroLoadingSnapshot.prepare、重新绑定原 call syntax。只保留逻辑声明/来源 identity，不在旧 TypeRef 上追加 fields/parents。

## 7. 域版本、循环与失败原子性

`DeclarationStamp` 是冻结的完整声明事实版本：原声明和 readonly 绑定、parents、全部字段槽/可见性/property 种类、成员 normal/readonly/return/default 来源、alias/import 目标及完成效果状态。域 stamp 汇合所有可达声明。不能只比较类型名字/count、同一 MCFPPType 对象、reverse source() 的 typeId 或 cache size。

旧 SourceTypeCatalog.graph 始终保持独立不可变；header 后续完成也不得改变旧图中的 facts。生产 `PreparedDeclarations` 带完成域版本，E02 和 CompilerBridge 必须核对当前受控声明 revision 与该版本；不接受旧 BoundCall 配新目标表。SourceTypeCatalog 的 compiler reverse handle 只用于该被核对版本的 lookup，不能证明环境仍然有效。

同一 completion barrier 下事实只单调加入/从 RESERVED 到 COMPLETE；新 completion revision 后进行全量重准备。单轮 work queue 以 `(declaration identity, typed readonly payload, generation options)` 去重，有明确 maxNodes/maxSpecializations/maxDepth/maxRounds，诊断携完整请求路径和 span。`Node<T>.next: Node<T>` 与 A→B→A 类型字段环复用 RESERVED；`Grow<T>.next: Grow<list<T>>` 无限增长耗尽预算后明确失败；default 值依赖环、字段推导环及父继承环分别诊断。

纯 prepare 仅修改 session staging。Rejected 不发布 preparedHeaders、compiledTemplates、index、关系或实际输出；一次成功 batch 才原子登记不可变 header entries。completion 有自己的深事务。重复 retry 若域 stamp、待处理请求及进度均未变化，应立即报告无进展，不能不断执行同一注解或放宽为 Any。

header cache 使用的 key、record 与容器需要和现有 private checkpoint 一样先检查准确形状和每个语义角色；严禁在验证前 rehash 未知 payload。新状态的关系恢复与 DeclarationStateJournal 的深恢复必须有组合回归。

## 8. E01 的具体生产消费链

`CompilerSourceIndex`（原三路径叶）实际实现：

1. 从真实当前 Function、其声明文件/imports、可见 Namespace/FileScope、原 owner 读取 symbols/fields/candidate declarations；构造 DeclarationLexicalSnapshot。普通、legacy Native 和 Java/Kotlin 宏进入同一候选视图，模式不作为重载条件。
2. 使用 SignaturePreparationSession 准备每个原声明。NeedsCompletion 的 staged batch 和 requests 一同交显式外层 CompilerDeclarationPreparation，成功后整体重试；Rejected 不调用 source binder。收集完整 types/fields/default 依赖/callback 域，用现 MacroLoadingSnapshot.prepare 一次冻结，建立 FunctionKey→原真实目标的受控表。
3. 用 SourceDefaultBinder 完成各声明模板及 formal 身份；建立同域 BindingEnvironment，再交 SnapshotSourceCodeBinder。E02 后续才把源符号映射为 session Ref、调用白名单和 layout/context capabilities。Index 不建立 Ref、不执行参数、不注册生成函数。

`CompilerBindingEnvironment.kt` 应公开一个内部的准备结果入口，供后续 pipeline 在 eager visitor 之前调用。NeedCompletion 不是“宏未匹配”，不得 fallback 到旧 eager visitor。完成重试属于声明阶段；当前宏/实参仍是未执行语法。

## 9. 必须细化的有界实施图

原 `R_HEADER_PREP_IMPL` 四路径占位不能直接承载全部功能。以下 slice 每项不超过四写路径、三步骤；测试路径均在 `src/test/kotlin/top/mcfpp/test/`，产品省略 `src/main/kotlin/top/mcfpp/` 前缀。

| slice / 依赖 | 唯一写路径 | 最多三步骤与实际验收 |
| --- | --- | --- |
| H01 共享类型 walker | type/TypeSyntaxResolver.kt；type/MCFPPType.kt；analysis/DeclarationBinding.kt；MniTypeSyntaxResolutionTest.kt | ①提取 AST walker/提供真实 legacy context ②所有旧入口委托 ③primitive/container/union/qualified/alias/generic 旧行为一致，未启用纯分支 source 拒绝 |
| H02 纯 lexical/default facts / H01 | analysis/DeclarationLexicalSnapshot.kt；analysis/DeclarationBinding.kt；mni/expand/SourceDefaultBinder.kt；MniReadonlyPreparationFactsTest.kt | ①真实声明/import/formal 快照且无 scope getter mutation ②准备 context 与 readonly 运算复用现纯逻辑、分类现有value ③真实合法scalar/闭合复合default，模型模板的前序/后向/自由名字、float/mixed compare/int32证明 |
| H03 全候选具体签名 / H02 | analysis/SignaturePreparation.kt；model/function/SpecializationPolicy.kt；analysis/DeclarationBinding.kt；MniSignaturePreparationTest.kt | ①准备结果/staging/完整 readonly 与签名替换 ②现 policy 增准备入口，legacy不暗入纯路径 ③所有原候选、无 winner/历史实例不成重载；应用模板暂时 source 拒绝直到H05 |
| H04 子表达式共同选择 / H03 | model/function/ParameterMatcher.kt；analysis/DeclarationBinding.kt；MniPreparedSelectionTest.kt | ①prepared-facts入口复用既有best ②纯子调用只查询准备结果 ③readonly/default/exact/conversion/歧义与当前选择一致，零宿主执行 |
| H05A 首次 header 生命周期 / H03,H04 | model/compound/GenericDataTemplate.kt；analysis/PreparedTemplateHeaderReader.kt；analysis/SignaturePreparation.kt；MniGenericHeaderPreparationTest.kt | ①canonical shell/cache/phase/私有checkpoint ②原AST字段/父/成员头的真实准备和旧compile同shell promotion ③循环字段、不同绑定、失败cache不污染、同实例复用；不能复制继承规则 |
| H05B 纯继承字段视图 / H05A 的稳定record API | model/compound/DataTemplate.kt；analysis/PreparedTemplateHeaderReader.kt；model/scope/CompoundDataScope.kt；MniPreparedHeaderInheritanceTest.kt | ①提取现有field槽兼容/来源最具体归并的纯入口 ②flatExtends及header reader共用 ③菱形/无关冲突/不同绑定/const-nullable-type不兼容，保留I_FIELD全部既有合同 |
| H06 声明完成/重试 / H05A,H05B,最终M_GLOBAL_STATE与声明深journal | Project.kt；model/compound/GenericDataTemplate.kt；mni/expand/CompilerDeclarationPreparation.kt；MniDeclarationPreparationCompletionTest.kt | ①明确完成请求接现有barrier、完整阶段回报 ②事务和annotation-once/版本推进 ③首次注解实例真实生成字段、失败回滚与重试、同shell完整compile无body |
| R_ENV_INDEX_IMPL / H04,H05B,H06,R_ENTRY,P02,P07 | mni/expand/CompilerBindingEnvironment.kt；mni/expand/CompilerSourceIndex.kt；MniSourceEnvironmentTest.kt | ①实际多文件/权限/default/candidate采集 ②完成→全域宏重投影→全语法重绑有限循环 ③真实Java/Kotlin宏和ordinary/native共同候选，同域和零执行；尚缺value形状不得fallback |

H05A 先交显式局部字段/父记录和 lifecycle，不以未经共同归并的继承视图发布 Ready；H05B 在原父验收内补齐全部字段归并后才能接 H06/E01。这使两叶各自可编译，也避免在四路径里塞第二套继承实现。

V01 是当前合法 value 的明确能力边界与有界叶候选，不增加语言语法。SourceCodeBinder 目前只处理 closed scalar，BoundExpr/Code 目前也没有动态 collection/interpolation 构建节点。H02 直接适配闭合 CompilerValue→typed Literal；非闭合合法 value 则须保留来源并明确拒绝，不能提前声称动态表达式均已支持。全计划验收应按用户原计划要求的现有源码表达式范围核定这些形状的真实 E01 覆盖；该核定不能用新增默认语法代替。

需要补齐的形状可按现 source-projection-contract 的 sealed-consumer 分期拆为模型→Arena→Validator→Printer→Plan→真实 value 投影，每叶≤4路径、旧未知门 fail-closed。模型/API设计叶候选为 BoundSource.kt、api/Code.kt、expand/LoweringPlan.kt、MniSourceValueModelTest.kt；各消费者叶候选沿 P03–P06 的现有产品文件加独立专项测试；最终投影叶候选为 SourceCodeBinder.kt、SourceDefaultBinder.kt、SourceCodeProjector.kt、MniSourceValueProjectionTest.kt。节点具体形状和消费者分期需在派发前独立审查，本文不把它们当作已实现接口。不得把该组塞入原 R_ENV_INDEX 三路径；未补齐时 E01 的报告必须列明闭合值子集和被拒形状。

先运行原继承/默认/ABI 回归再声明共享walker可用；各 phase 叶也有自己的正式测试。父验收必须汇合 H01–H06、真实 R_ENV_INDEX_IMPL，并按全计划核定的现有源码范围检查合法 value 覆盖；不能因为中间显式拒绝门通过就削减首次泛型/注解/default 原验收。后继 E02/P08/R_PIPE 的无 eager 前置和真实后端验收仍保留。没有默认语法扩展、ParamInfo变更或stdlib格式升级前置。

## 10. 真实测试与静态自检

| 案例 | 必须观测 |
| --- | --- |
| 首次 `Box<int>`，fields 含 T、list<T>、`Base<T>` | 无历史cache时 header准确；同绑定同shell、bool绑定独立；parents/fields完整 |
| `Node<T>.next:Node<T>`、互相引用、增长型递归 | 有限环保持identity；增长耗尽预算带path/span；无半条cache |
| 两文件分别声明/import同名类型，caller另有T/a | 类型来自原声明；真实合法value默认采集准确；模型模板引用原formal/free identity，caller不污染 |
| 真实float/bool/int/string默认；模型的前序-a与混合比较 | 实际FloatBits/Bool与现defaults规范一致，明确source与模型来源，零eager执行 |
| 显式effectful参数；生成/模型normal default调用 | 真实显式参数保留一次执行合同；模型BoundExpr Call及原effects/order保留，不冒充参数声明grammar支持 |
| exact/conversion/default/ordinary与macro候选 | 准备前后全原候选存在；准备不选winner；现selector结果一致；模式不改排名 |
| 带宿主注解的首次合法泛型 | 纯prepare返回NeedsCompletion且计数0；实际外部完成计数1、生成字段存在；整域重建后Ready；重复请求不重跑 |
| 注解在修改父/成员/名字后失败 | 全局/声明/Scope/Function/Storage/diagnostic事务恢复；再试不重复成员或丢失一次效果 |
| header准备后真正GenericDataTemplate.compile | 同一canonical model，成员materialize一次；新函数body与constructor body仍未编译；后续真实调用正常 |
| 同对象parents/field类型/alias变化但type数量不变 | 新域/版本更新且全候选重绑；旧graph事实不变；E02拒混域/过期 |
| 现有合法默认值的mclib往返 | 从真实源码解析写库，再fresh接收模型绑定；准确CompilerValue/defaultContext/defaultTypes；无session图持久化，Kotlin默认不自动映射 |

所有纯阶段测试初始化真实 Function.currFunction，设置命令、resource、scope、callgraph、flow、effect/storage、TempPool/index 哨兵；比较 before/after，包括 stackIndex，不能靠未初始化全局提前失败。类型/header 准备允许的新声明仅在约定 publish/外部完成点出现。宏provider、native、getter和annotation计数分别记录，禁止把“没有生成命令”当作“没有宿主执行”。

本设计自检：闭合首次泛型与完整compile、readonly/default身份、共同解析与排名、注解外部完成/重试、域版本、循环预算、失败原子性、库格式和所有实际消费者；所有切片≤4写路径/≤3步。本轮不改产品、不生成库、不运行Gradle，待非作者独立审查。

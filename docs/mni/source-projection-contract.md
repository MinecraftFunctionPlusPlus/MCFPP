# 纯源码投影与参数序言实施契约

本文件补充 `implementation-contract.md` §6、§7、§9。交付目标是把已经纯绑定的 `BoundExpr / SourceCall / BoundLocation` 无损转换成现有会话拥有的代码树，再转换成完全脱离编译器对象的 `LoweringPlan`。本文件是实施设计，以下新增签名尚未实现；不能据此宣称源码宏调用已可执行。

本文主体保留原投影实施设计与当时接点。当前 lambda 迁移契约以下述 §5 的补充为准；历史阶段计划不代表当前实现状态，也不替代迁移后的编译与运行证据。

公开引用迁移后，下文 REF、BoundLocation 与地址序言均属于编译器内部位置能力。源码 `ref expression` 仅保留用于定位迁移诊断的语法节点，不能成功编译；公开源码通过显式 Minecraft 地址构造 `ScoreRef` / `NBTRef<T>` 并作为 VALUE 传递。

## 1. 已核对的生产接点与边界

| 当前接点 | 已有事实 | 必须补齐的消费者 |
| --- | --- | --- |
| `expand/SourceCodeBinder.kt:406–499` | 完整候选集合先比较实际类型、readonly/default，再校验模式；`SourceCall.target` 已唯一选定 | 投影与宏解析只消费该结果，禁止另做候选排名 |
| `expand/BoundSource.kt` | 保存节点、常量证明、效果、位置、span、显式调用顺序；默认表达式另在 `SourceFunction` 中 | 默认补全、声明形参符号替换、规范字段关联 |
| `expand/BindingEnvironment.kt:180–206` | 当前 catalog 的 `TypeRef` 没有字段；泛型元素/关系等源码事实已冻结 | 一次构造的冻结类型图及字段/索引 schema；保持旧 snapshot 独立 |
| `api/Code.kt:85–115` | 没有值索引节点、地址捕获声明；Call 仅有 normal arguments | 新节点、readonly 调用包及所有树消费者 |
| `expand/CodeArena.kt:117–153,263–445` | 创建、预算、依赖扫描、导入重命名均显式遍历节点 | 新节点必须同时参与创建、扫描、复制和身份重命名 |
| `expand/CodeValidator.kt:160–188,271–289` | 索引只能靠整条外部 binding 绕过；转换能力与 source catalog 不完全一致 | 逐步解析索引 schema、地址支配、规范转换证明；不能扩登记外部 binding 来绕过验证 |
| `expand/CodeLowerer.kt:94–181` | Read/REF 每次使用时 CaptureLocation；Call 丢失 readonly；已有 ExprBlock 与局部声明可复用 | 序言处持久地址捕获、值索引、readonly、默认值已补全标记 |
| `expand/LoweringPlan.kt:55` | Literal 仍存 `ConstantValue`；其 TypeValue 可含 `TypeRef` | 新 `PlanConstant` 必须递归脱离 TypeRef，包括列表、记录与 readonly |
| `model/function/Function.kt:1059–1113` | 现有默认值可能进入 `MCFPPExprVisitor`；NativeFunction.argPass 也会补默认值 | 新管线传完整实参并走明确的“已补全”接入，禁止在投影阶段调用这些入口 |
| `analysis/StorageAccess.kt:114–168` | 已有 Location、captured index、capturedLocations；动态地址不能依靠重新读取原索引变量 | 后端桥接可复用其位置数据，但必须消费冻结的值/位置；不能把保存 ValueRef.Read 当作已经固定值 |

当前仓库只有 detached plan 的生产器 `CodeLowerer`，还没有 `expand/CompilerBridge.kt`。本组的树/plan 验收与后续真实命令后端验收分别设门，最终组验收必须两者都完成。

## 2. 唯一数据域、冻结图与 canonical 字段

全部内部类型放在 `top.mcfpp.mni.api` 或 `expand` 对应文件中；公共 API 不暴露 compiler type、Var、ANTLR、Project 或 expand 类型。Java 作者入口保持不变。

扩展 `SourceTypeCatalog.snapshot`，接受**已经准备完成**的声明域：

```kotlin
// expand/BindingEnvironment.kt；此处列出目标签名。
internal data class SourceFieldDeclaration(
    val declaration: SourceFieldId, val owner: TypeId, val name: String,
    val valueType: TypeId, val writable: Boolean, val source: SourceSpan
)
internal data class SourceFieldId(val owner: TypeId, val declarationKey: String)
internal data class SourceFieldView(val receiver: TypeId, val declaration: SourceFieldId)

SourceTypeCatalog.snapshot(
    types: Collection<MCFPPType>,
    fields: Collection<SourceFieldDeclaration>,
    fieldViews: Collection<SourceFieldView>
): SourceTypeCatalog
SourceTypeCatalog.field(id: SourceFieldId): FieldRef<*>
SourceTypeCatalog.field(owner: TypeRef<*>, name: String): FieldRef<*>?
SourceTypeCatalog.indexSchema(type: TypeRef<*>): IndexSchema?
```

保留旧 `snapshot(types)` 重载。新重载在一个受控声明准备边界内冻结整个域，每次 prepare 都重建；不能按类型名称/count 缓存，也不能原地更新旧 TypeRef 的字段、父关系、元素类型或转换表。完整域包括可达字段类型、父类型、容器元素、callback 参数/结果及默认值所引用的类型。

`SourceField` 增加稳定 declaration identity；已有测试构造可保留便利重载，但进入生产 `BindingEnvironment` 时必须从本 catalog 取得唯一 `FieldRef`。继承字段保存**原声明 owner**和 identity；SourceFieldView 显式记录每个 receiver 的已解析字段视图，子类型引用同一规范字段，override/hide 后只暴露已验收的可见选择。不能从 subtype 关系重新猜覆盖/私有字段规则，不能用 `(字段名, 字段类型)` 重新猜 identity，也不能为每次访问分配 FieldKey。泛型 owner identity 包含实际类型参数。

冻结图目标形状如下，具体私有构造组织可沿用现类：

```kotlin
// api/Types.kt；均 internal，不引入 api → expand 依赖。
internal data class IndexSchema(val element: TypeKey, val writable: Boolean)
internal sealed interface ConstantSchema {
    // 精确标量种类，NULL 独立于可空能力；不得靠 OTHER 猜测。
    data class Scalar(val shape: ConstantShape) : ConstantSchema
    data class Sequence(val element: TypeKey) : ConstantSchema
    data class Record(val fields: Map<String, TypeKey>) : ConstantSchema
}
internal enum class ConstantShape { INT32, BOOL, FLOAT32, FLOAT64, STRING, NBT, TYPE, NULL }
internal class FrozenTypeRecord(
    val key: TypeKey, val name: String, val stableSourceId: String,
    val kind: TypeKind, acceptedSources: Set<TypeKey>, fields: List<FieldKey>,
    val index: IndexSchema?, val constant: ConstantSchema?
) // 集合防御复制；stableSourceId 是完整 TypeId 的确定性编码，非 typeName。
internal data class FrozenConversionRecord(
    val from: TypeKey, val to: TypeKey, val kind: ConversionKind
)
internal data class FrozenFieldRecord(
    val key: FieldKey, val name: String, val type: TypeKey,
    val writable: Boolean, val source: SourceSpan
)
// 记录只用 key 连接；关系、转换、常量 schema 也在同一冻结图内。
internal class FrozenTypeGraph private constructor(/* immutable records */) {
    companion object {
        fun freeze(types: Collection<FrozenTypeRecord>, fields: Collection<FrozenFieldRecord>,
                   conversions: Collection<FrozenConversionRecord>): FrozenTypeGraph
    }
    fun type(key: TypeKey): ImmutableTypeRef<*>
    fun fields(key: TypeKey): List<FieldRef<*>>
    fun index(key: TypeKey): IndexSchema?
    fun permits(from: TypeKey, to: TypeKey, kind: ConversionKind): Boolean
    fun constantSchema(key: TypeKey): ConstantSchema?
}
```

先验证/冻结 key records，再分配所有 handle，最后发布 catalog。`ImmutableTypeRef` 增加默认空的 internal graph 接点；graph handle 的 kind/name/关系来自对应冻结记录，不能再提供互相冲突的独立 fields。handle 构造不得遍历其字段；`fields()` 通过冻结记录取同图 canonical handle。因此 `Node.next: Node`、A→B→A 和泛型自引用均不需要修改已发布 TypeRef。初始化中的 graph/handle 不得进入环境、registry 或作者代码。validator 对 graph handle 检查 `graph.type(key) === handle`；不能接受同 key 伪造物。现 `ImmutableTypeRef` 的独立构造可保留给原有 builtins/tests；生产 catalog handle 必须携带同一个冻结图身份。

索引 schema 第一阶段对应源码 binder 已支持的 int 索引序列/数组；不可变列表 `writable=false`，bool 索引拒绝。键/predicate/selector 等未来语法必须有独立 schema 后才可开放，不能用 `OTHER` 或 Any 假定可索引。

转换亦由同图冻结：精确、nominal、Object/Any、int→float、已批准显式 reinterpret。源码 binder、投影、validator 使用同一事实。`TypeKind` 只用于受支持运算矩阵，不能替代类型身份或强制转换证明。

ConstantSchema 的集合也必须防御复制；其 key 回到同一图。type 常量的外层 schema 是 TYPE，payload 指向被描述类型，二者不可混淆。Null 只在准备阶段明确授予的 null/nullable/Any schema 下合法；纯 NBT 解析可用于检查 Nbt payload，不能为验证常量调用编译器 visitor。源阶段目前只接受的标量应全部可投影，其余已有闭合 proof 使用 schema 检查，缺 schema 明确拒绝。

## 3. Code 的最小扩展

以下都是内部不可变 payload，沿用 defensive copy、origin、session 和 arena 预算。

```kotlin
// api/Code.kt
internal data class AddressId(val session: SessionId, val value: Long)

// 保留现有 root/steps/context/writable 参数；末尾追加默认空字段。
// root 永远保存原符号 provenance，capturedBase 非空时寻址从已捕获地址开始。
NodeLocation(..., val capturedBase: AddressId? = null)

internal data class TypedConstant(
    val type: TypeRef<*>, val value: ConstantValue, val source: SourceSpan
)

// ExprNode 增加：
data class Index(val receiver: Expr<*>, val index: Expr<*>) : ExprNode

// Call 在现有两参数后追加默认空参数，兼容 builder 和旧测试。
class Call(
    val function: FunctionRef<*>, arguments: List<CallArgument>,
    readonlyArguments: List<TypedConstant> = emptyList()
) : ExprNode

// StmtNode 增加：冻结地址而不是声明保存内容的 Local。
data class CaptureAddress(
    val address: AddressId, val location: NodeLocation, val type: TypeRef<*>
) : StmtNode
```

`ImmutableFunctionRef` 末尾追加 `readonlyParameters: List<ParameterView> = emptyList()`，防御复制，表示 canonical 目标签名的编译期参数。`FunctionRef.parameters()` 继续仅返回 normal 参数；不得把 readonly 拼进 normal 或 helper 参数。macro/helper 的 readonly 为空。函数 key 和投影环境的调用白名单同时校验完整签名，不能只凭相同 key 接受伪造描述符。

`NodeLocation.capturedBase` 是地址 capability：同 session，不能从作者 API 创建。此字段存在时 root 只供来源、别名保守分析和跨会话外部映射使用；后端禁止再从 root 重新解析动态基址。steps 是**相对捕获地址的剩余路径**，不是原完整路径。equals/hashCode 包含 capturedBase、steps、context、root；两个不同捕获 ID 不意味着物理不相交。

CodeArena 增加 `freshAddress()`，在创建 CaptureAddress 时登记所属 scope；创建 Ref 不得自行把捕获地址当作外部 binding。导入树时预扫描 CaptureAddress 声明并分配新 AddressId，随后重写其使用；树外捕获地址不得克隆成未声明新地址。child import 必须提供明确地址映射或诊断，不能重算原位置。capturedBase 和 CapturedIndex 的支配均由 validator 逐控制路径验证。

`CodePrinter` 必须输出按遍历编号的 `capture a0 = ...`、`address a0...`、index 和 typed readonly；禁止输出物理计数作稳定名称。

## 4. R_SOURCE_ENV 的真实交付边界

环境采集读取已经完成继承/字段/权限和声明特化准备的 compiler 数据，只把快照交给投影。生产实现文件为 `expand/CompilerBindingEnvironment.kt`；不以作者传入的空 service 代替。

它必须交付以下一套共同数据：

```kotlin
internal class SourceProjectionEnvironment(
    val binding: BindingEnvironment,
    val session: ExpansionSession,
    roots: Map<SymbolId, Ref<*>>,
    functions: Map<FunctionKey, FunctionRef<*>>
)
```

- `binding.types` 与 MacroLoadingSnapshot 共用同一 prepare 返回的 catalog。snapshot 的 symbols/functions/fields/defaults/callback 描述必须全图 ownership 合法。
- `roots` key 是 BoundSource 源身份，value 是本 session 中代表**同一物理位置**的 Ref。源身份可属于 snapshot 的 session 0；投影不得通过改 Long 值假装同 session。环境采集用 source→session 身份映射登记外部绑定、执行上下文及未来 backend layout。
- 函数表保留普通/旧 native 的目标种类、实际签名、readonly 描述、声明 identity；backend 另持 FunctionKey→真实 Function/NativeFunction 的受控表。树和 plan 不持真实对象。
- 对普通变量只采集声明与已证明值，不调用 Property.getter、NativeFunction.invoke、Function.invoke、PrimitiveCompiler、MCFPPExprVisitor、buildUnConcrete 或生成体编译。
- SourceField 只代表已授权的物理字段；普通 getter/setter 属性不在 REF 字段表中。若合法值读取需要 accessor，应在纯源绑定时表示明确 Call，不能由投影把 Field 隐式改成宿主 getter。
- 默认表达式由声明环境纯绑定为 `BoundExpr` 模板，并附 readonly/normal **形参 SourceSymbol** 列表；引用靠身份，不靠调用者同名变量。只有 default flag 而没有表达式/闭合值的声明不得进入可调用生产快照。
- 参数/返回类型依赖 readonly 的声明，在受控声明准备中进行纯签名替换，返回 canonical 类型和稳定的已准备目标 identity；不编译函数体。先完成 readonly 常量，再查询此签名准备结果。若产生新类型，需要重新 prepare 完整域及宏投影，并重绑原语法；旧 SourceCall 不能与新 catalog 混用。
- 环境采集负责完整 namespace/import/权限及 generic owner identity，包含所有普通/宏共同候选。仍由现 `SnapshotSourceCodeBinder` 选择；若需给 candidate 增加纯签名实例化接点，修改其既有选择函数，禁止在宏 resolver 写第二套排名。

具体准备入口由 `CompilerSourceIndex.prepareCallDeclarations(syntax, preparedDomain)` 实现：只纯绑定显式 readonly，按候选声明 identity 准备签名及 default 模板，收集新出现的类型/字段，必要时整体重建 domain/catalog/宏引用，然后对原语法重新执行 `SnapshotSourceCodeBinder.call`。此入口不决定 winner。每个 generic 声明在该调用的候选视图中只出现一次，使用该调用 readonly 闭合值准备的签名；不能把该声明历史上所有具体实例一齐作为相同 readonly 参数类型的重载。依赖 readonly 的 candidate 在现 selectTarget 的 match 中只读取该次准备的具体签名；不得调用 GenericFunction.compile/Function.compileBody。声明准备的扩张必须单调且受声明预算约束，重复同一声明+readonly 闭合值不得无限重建；旧 domain 的 BoundExpr/SourceCall 一律失效，不按类型名修补。

冻结类型图的物理字段 schema 不替代调用者权限。生产环境另提供该调用者可用的 FieldKey 能力集合，E02 通过 `ExpansionSession.registerFieldCapability(field)` 登记；`ValidationEnvironment` 增加显式可选字段白名单，生产管线必须启用并在 Field/位置解析时检查。原独立模型测试可保留默认空缺的兼容模式，不能让生产环境以空缺白名单获得全字段权限。直接从 TypeRef.fields() 取得描述符也不能绕过此门。

默认模板所需的 `SourceFunction.readonlySymbols/normalSymbols` 在 `BindingEnvironment.kt` 增加；长度、类型、唯一身份、默认表达式可引用的前序形参由构造验证。函数声明环境的自由变量提前解析成源身份，投影不能用调用者名字补找。readonly defaults 只能依赖前序 readonly 的闭合值；normal defaults 可依赖已绑定 readonly、前序 normal 及显式可见声明绑定。自引用、后向引用及循环带声明 span 拒绝。

## 5. 实际投影类和调用 API

生产实现为 `expand/SourceCodeProjector.kt`，直接遍历 BoundSource，使用 CodeArena；没有 `SourceArgumentProjection` 注入回调。所有结果只创建树，不执行命令或宿主函数。

```kotlin
internal class SourceCodeProjector(val environment: SourceProjectionEnvironment) {
    fun expression(value: BoundExpr, scope: ScopeId): Expr<*>
    fun captureLocation(value: BoundLocation, scope: ScopeId): ProjectedLocation
    fun arguments(value: SourceCall, scope: ScopeId): ProjectedCallArguments
    fun call(value: SourceCall, scope: ScopeId): Code // 非 void Expr；void Block
}
internal class ProjectedLocation(
    statements: List<Stmt>, val reference: Ref<*>
) // statements 防御复制；与 reference 同 scope/session
internal class ProjectedCallArguments(
    val orderedPrelude: Block, arguments: List<CallArgument>,
    readonlyArguments: List<TypedConstant>, val scope: ScopeId
) // 所有集合防御复制；arguments 已完成转换/捕获，与 prelude 不可拆开导入
```

`expression` 返回的必要副作用在 ExprBlock 内，不能抽到调用者外部；`captureLocation` 则给出在对应 REF 参数位置插入的完整有序 statements。其私有内部方法可以持有 append-only 语句列表和 formal→已绑定句柄替换表；不得保存在最终树里。

每个生成节点使用本 session 的 scope/context，`Origin.callSite` 保持外层用户宏调用，`generatedFrom` 指向当前 BoundExpr/默认声明的 span，expansionFrames 继承会话栈。内层表达式诊断不能退化为 unknown span。projector 的依赖/类型/模式预检在分配节点前完成；任何中途失败由会话失败路径撤销整个暂存结果，不留下可再用的半套参数句柄。

| BoundExprNode | 具体 Code 映射与要求 |
| --- | --- |
| Literal | Literal；保留实际 canonical type。验证 null/TypeValue/集合等 schema；不因 kind=OTHER 拒绝全部，也不把任意 payload 当常量 |
| Read | 从 roots 映射取得原位置，按字段/索引规则投影；有前置捕获时用 ExprBlock + Yield(Read) |
| Unary | 同 operator 的 Unary，保留原 operand；不提前求运行时结果 |
| Binary | Binary；numeric mixed int/float 在对应 operand 位置插入已批准 int→float。比较结果为 bool 不能拿 bool 当 operand 类型。AND/OR 的右侧 ExprBlock 仍受短路控制；string ADD 需在 validator/plan/backend 同步支持 |
| Convert | 显式 Convert，保留 target 与 explicit 语义；即使同类型也不能在 REF 路径剥掉它。转换关系必须来自同图冻结证明 |
| Field | catalog.field(declaration identity) 得到 canonical FieldRef；Field(receiver, field)，不运行 getter |
| Index | Index(receiver,index)，严格 receiver→index 各一次。结果类型、可写性来自 index schema；不调用容器 getter 取实际元素来猜类型 |
| Call | 按以下已补全调用规则投影，保留 Native/Ordinary/Macro 区别及调用 span |
| Unsupported | SourceBindingFailure，保留原 span；禁止退回 eager visitor 或 raw command |

当前 source binder 接受、而 validator 尚不支持的 string ADD、int→float 隐式转换、null/TypeValue 等闭合常量必须在本组增加支持与回归；不能把它们作为“暂时 unsupported”让投影的完整性假通过。显式 reinterpret 的后台 representation 检查仍由对应 lowering 接点严格执行，不得到命令阶段才盲转。

### 已选择的普通与旧 native 调用

1. 验证 `SourceCall.target` 就是 BindingEnvironment 中的 canonical 目标、catalog 版本一致；不重查同名函数。检查原 `evaluationOrder` 与 readonly/normal 实参身份和次序一致，防止手造 SourceCall 省略或重复副作用。
2. 按 readonly 声明次序补全闭合常量，必要的纯常量转换也在此完成。按 `SourceFunction` 的 readonly 参数 schema 检查 TypedConstant；运行时函数不能证明常量。保留 TypeValue 的规范身份。
3. 原有显式 normal 实参按原顺序各产生一次 LocalDecl，包括常量/无用参数；转换在各参数绑定位置完成。再按声明序补缺失的 normal defaults，模板中的前序 formal Read 替换为前面临时的读取；不能复制前序 initializer，不能让 default 再读当前已变化的调用者变量。
4. 已补全 normal 参数全部用临时读取形成 CallArgument.value；Call 携带完整 typed readonly，包在 ExprBlock 中 yield 非 void 结果。void 调用用 StmtNode.Eval(Call) 放在 block 中，不创建 void Yield。源码 value 位置出现 void 则诊断。调用 effect 仍属于 Call 节点。

这里的列表是算法步骤，不是执行图叶的数量。后端收到 normal 参数数必须已等于签名 arity，不再求默认值。readonly 与 normal 分区独立保留；NativeFunction 的旧协议仍由最终桥接生成 readonly + normal + 非 void caller + 非 void ValueWrapper，NativeCallContext ABI 仍保留。纯投影不执行任一 ABI。

上述补全与顺序绑定实际由 `SourceCodeProjector.arguments` 统一实现，`call` 只组装 prelude 与目标节点。目标为宏时同样按已选 mode 创建稳定 VALUE 临时、CONST literal capability 和已捕获 REF，最终放 MacroCall；宏没有 readonly。`MacroArgumentBinder.bind(SourceCall)` 也调用此方法，不复制一套源码绑定规则。`expression(BoundExprNode.Call)` 只接受 call 返回的 Expr；void Block 出现在值位置须带原调用 span 拒绝。

依赖 readonly 的 generic 目标只接收受控准备产生的具体签名/目标 identity；编译函数体/提交 specialization 是后续后台事务行为。源码 binder 的候选资格和 default 排名不能在这一步改变。

### 嵌套宏与 INLINE 边界

当前源码函数类型使用 `(int,int)->int`、`()->void`，无 `func` 前缀；普通具名 `func` 声明不变。lambda 为 `{ i -> ... }`、显式类型 `{ i: int -> ... }`，单参数目标允许隐式 `it`，零参数体为 `{ ... }`。迁移反例 `(i) => { ... }` 必须给出迁移诊断，不维持旧写法或可变捕获兼容。

自由局部绑定在 lambda 创建点按值快照并保持只读，捕获来源用声明身份追踪，赋值、复合赋值和引用写入须在绑定/投影阶段拒绝。类型复制沿用 `LambdaCaptureRules` / `StorageAccess` 的既有契约，不引入通用深复制。全局也按声明身份识别，在实际使用点读取当前值并保留合法写入，不能把同名局部当作全局。快照创建不能拖到每次 INLINE 插入时重新读取来源；嵌套 lambda 在其自身创建点快照外层参数。

INLINE 只投影静态 lambda 字面体或普通源码函数引用；动态函数值即使类型匹配也须明确拒绝，不能以普通运行时调用替代。非 void 回调尾表达式产生结果，尾随分号丢弃值，void 目标丢弃尾值；显式 return 只退出本次回调，break/continue 不能跨回调边界。沿用现有 lambda、StorageAccess 和回收体系，不建立第二套闭包/存储/GC 系统。

累积状态通过显式返回值传递：`repeat(times: int, initial: int, body: INLINE (int,int)->int): int` 在 `(accumulator,index)` 返回下一累加器；`raycast(samples: CONST int, initial: int, step: INLINE (int,int)->int, keepGoing: INLINE (int)->bool): int` 仅在实际 stone 命中时先保存 step(accumulator,index) 的结果，再执行 keepGoing(index)。原 void repeat/bool raycast 重载保留，0.5 步长和 ambient 上下文保持原含义，不引入 Result 或共享可写盒。

宏 Call 保留 MacroCall 节点，不能提前执行宿主入口。参数中的嵌套宏位于对应 initializer/索引表达式的原位置，递归展开后仍受 ExprBlock、短路、循环约束。树结果回到相同 position 后才允许 final 验证。

Callback 的语法/源体由 R_SOURCE_BODY 与 Inline 叶绑定；本组不提前编译 body、不以空 InlineFn 或普通函数调用替代。`MacroArgumentBinder` 的目标签名含 INLINE 时，将 callback 描述交给已完成的 Inline binder；该依赖未完成前，入口报告明确“INLINE binder unavailable”并附 span，不能声称该调用绑定成功。VALUE/CONST/REF 路径可独立验收；最终 INLINE 组门必须解除该诊断并跑真实 callback 用例。

## 6. REF：基址、路径和上下文在原参数位置冻结

不得用 `let copied = ref.load()` 代替捕获地址。如下源序列的期望为：

```text
use(effectA(), ref records[indexCall()].items[nextIndex()], effectB())
A → freeze records base → indexCall → freeze selected element
  → select items / freeze base → nextIndex → freeze final address → B
```

实现算法：从环境 roots 取原 Ref，先发 CaptureAddress 固定根存储/frame/receiver/context；每个字段用 canonical FieldRef 组合当前捕获地址；遇到索引，先把**索引之前的整个前缀**固定，再投影索引 initializer 并 LocalDecl 固定其值，使用 CapturedIndex 组合路径，再 CaptureAddress 固定该次选择。不能延迟到所有索引都执行后才解析前缀，因为后一个索引可能修改前一个基址/嵌套集合。纯常量索引可以使用 Index(int)，但它的原表达式若含已保留副作用，不能凭 constantIndex 跳过。

输出 Ref 指向最后一个 AddressId，剩余 steps 为空，actual type 精确等于参数类型。重复 `Ref.load` 或宏树中的多次 assign 不再计算根、索引或 receiver；fieldRef 派生位置是相对该地址的新增路径。executionContext 固定，不能换 execute/entity/frame 后继续使用。

两个 `ref health` 分别捕获的地址可不同 ID，但后端解析为同一原始地址，顺序写入彼此可见。不得以 ID 不同做不相交优化，不复制数据、不结束回写。动态 index VALUE 与其他参数按原 `evaluationOrder` 交错；一次宏 host 构造不能执行任何这些运行时动作。

validator 为 CaptureAddress 建立独立地址支配表，条目包含解析后的实际 type、writable、context、scope、来源 root。先验证源位置及捕获索引局部的支配，再添加结果 AddressId。条件分支中创建的地址不能支配另一分支/后续汇合；循环每次进入执行相应 capture，不能跨迭代复用未重捕获状态。拒绝重复声明、未定义、自引用、跨 session/context、扩大可写能力、类型不符。索引逐段使用冻结 index schema 检查，不登记“整条派生外部 binding”规避检查。

## 7. detached plan 与后台消费者

保留现 PlanOperation.CaptureLocation / PlanLocationId；新的 Code CaptureAddress 降低成**在该语句位置**的 CaptureLocation，并在 lowerer 的 AddressId→PlanLocationId 表登记。后续只有地址且 steps 为空的 Read/REF 直接复用该 PlanLocationId；有相对 steps 时从保存地址继续派生。

```kotlin
// expand/LoweringPlan.kt
PlanLocation(..., val capturedBase: PlanLocationId? = null)
// capturedBase 非空时 root 只保留 provenance；基址从地址表取得，不再重读 root。
data class Index(val result: PlanTemp, val receiver: PlanTemp,
                 val index: PlanTemp, override val site: PlanSite) : PlanOperation

internal sealed interface PlanConstant {
    data class Integral(val value: Long) : PlanConstant
    data class Bool(val value: Boolean) : PlanConstant
    data class Text(val value: String) : PlanConstant
    data class FloatBits(val bits: Int) : PlanConstant
    data class DoubleBits(val bits: Long) : PlanConstant
    data class Nbt(val snbt: String) : PlanConstant
    data class TypeValue(val type: PlanType) : PlanConstant
    object NullValue : PlanConstant
    // 普通不可变类，构造时递归防御复制；不能直接 data class 持可变集合。
    class Sequence(elements: List<PlanConstant>) : PlanConstant
    class Record(fields: Map<String, PlanConstant>) : PlanConstant
}
internal data class PlanReadonlyArgument(
    val type: PlanType, val value: PlanConstant, val source: SourceSpan
)
// PlanFunction 追加 readonlyParameters；PlanOperation.Call 追加 readonlyArguments。
// 两者默认空，normal PlanArgument 顺序不变。
```

PlanType 的数据域必须足以验证/定位实际类型：TypeKey、稳定源 TypeId 的 detached 编码、kind、必要 representation 标签；不得存 MCFPPType/TypeRef 或闭包。TypeValue 的源 TypeId 编码含泛型/nominal identity；不能只存 displayName。Literal、readonly、列表/记录中的所有类型值递归转为 PlanConstant。plan 不持 Code、SourceFunction、BoundExpr、parser、Method、Function 或 arena；关闭 session 后仍可遍历、验证、后台消费。

plan 同时携带不可变 domain stamp，对应准备完成的类型/layout/目标表版本。CompilerBridge 只消费该版本的白名单和冻结布局，不能拿旧 TypeKey 在新 domain 按名称重找。跨 session 共用同一 declaration domain 时可独立降低，所得 plan 不依赖任一 arena；跨新旧 domain 的树/句柄混用必须拒绝。资源稳定名使用稳定声明/源类型编码，绝不直接使用 domain stamp、TypeKey 的物理计数或 session ID。

`CompilerBridge` 是后继生产消费者，明确实现而不是测试注入服务：

```kotlin
internal class CompilerBridge(/* explicit compiler target + frozen layouts + journals */) {
    fun prepare(plan: LoweringPlan): CompilerJournalParticipant
}
```

prepare 在独立 staging target 上遍历基本块，不向调用者发布。实现必须：

- CaptureLocation 保存实际 storage/frame/receiver/context 及已求值路径组件；capturedBase 必须从地址表取得。可以复用 analysis.Location/StorageBinding，但其 captured 值必须来自专用稳定临时或已捕获地址，禁止晚读原索引 Var。
- PlanOperation.Index 以已算出的 receiver/index 读取元素；不能为类型选择再执行 getter，也不能再次调用 index 函数。
- Call 根据 plan key 查白名单内真实目标；readonly 由 typed PlanConstant 适配，normal 由已算值适配，完整 arity 后进入旧普通/native 调用协议。禁用“缺实参再 completeDefaultValue”路径。native 两 ABI 都测试；带 receiver 的合法目标必须有已准备的显式 receiver 位置，缺失时在 prepare 前诊断，不能默默传 null。
- 真实 legacy invoke 只允许发生在后台 staging + Storage/Function/scope/global journals 已覆盖的范围；失败撤销已暂存命令、辅助函数、资源、scope、callgraph、flow、名称和诊断事务状态。保留原调用位置和展开栈。
- 对无法稳定表示的动态地址，在提交前附 span 明确拒绝；前端树/plan 层仍完整保留该地址。不得把不支持的位置改成 VALUE，不能先写真实输出再还原树。

后台接入门必须用真实源码宏/普通/native 调用的命令执行结果验证。plan 测试机只验本组执行顺序，不能替代 CompilerBridge 生产接入。

## 8. R_ARG_SEQUENCE 的稳定 API

原 R_ARG_BIND 的 3 路径保持：MacroCallResolver.kt、MacroArgumentBinder.kt、MniArgumentBindingTest.kt。

```kotlin
internal sealed interface ResolvedCall {
    class Ordinary(val source: SourceCall) : ResolvedCall
    class Macro(val source: SourceCall, val declaration: MacroFunction) : ResolvedCall
}
internal class MacroCallResolver {
    fun resolve(call: SourceCall, environment: BindingEnvironment,
                macros: Collection<MacroFunction>): ResolvedCall
}
internal class BoundMacroArguments(
    val orderedPrelude: Block, arguments: List<MacroInput>,
    val resultType: TypeRef<*>, val scope: ScopeId
)
internal class MacroArgumentBinder(val projector: SourceCodeProjector) {
    fun bind(call: ResolvedCall.Macro, scope: ScopeId): BoundMacroArguments
    fun bindGenerated(function: MacroFunction, arguments: List<CallArgument>,
                      scope: ScopeId, source: SourceSpan): BoundMacroArguments
}
```

resolver 按完整 canonical 目标签名/key 关联已选 SourceFunction 和 registry MacroFunction，不再次按名称筛选、模式尝试或失败回退普通调用。普通/宏候选冲突在声明准备时拒绝；同类型不同 mode 不构成重载。

VALUE 每个 initializer（含转换）各声明一个 Local，arena.value(local.load()) 的缓存读取作为作者输入；未使用也保留 LocalDecl。CONST 仅使用已经验证的闭合值及纯常量转换生成 Literal/Const；含 CALL/WRITE/UNKNOWN 效果与常量证明冲突时拒绝，不能执行表达式补证明。REF 只接受原 SourceCall 的显式 Reference，writable 且 sameType，无转换，使用 captureLocation 的语句。所有顺序取自已验证的 evaluationOrder，默认补全规则同 §5。

源码入口 bind 取 projector.arguments 的结果，把 Value payload 中的稳定临时读取包装为 Value、保留 Ref/Const capability，不再执行原表达式。递归 expander 消费 Code.MacroCall 时调用 bindGenerated：按该节点既定签名检查模式/类型，对原始生成 VALUE Expr 各临时化一次，对生成 REF 捕获地址后保留同一位置；不能从代码树重新拼 SourceCall 或重查重载。对源码投影已经稳定的临时/地址，再作纯读取/地址别名绑定允许产生额外本地句柄，原 initializer/索引/根地址捕获不得复制或重新计算。宿主生成调用的实参列表顺序就是其求值顺序，源码调用的原顺序已由外层 prelude 保证。

orderedPrelude 和 arguments 是同一 scope 的一个不可分割绑定单元：**不能** builder.append(prelude) 后把参数句柄留在外面，因为 append 会 hygienic clone 局部。MacroExpander 必须把原 prelude statements 与 host 返回树组装在同一块/ExprBlock 中，再整体 import/clone；若确实需要独立导入，必须一次导入绑定单元并同时重写所有 argument 句柄。

非 void 最终树为 ExprBlock(prelude + 已递归展开的结果 Yield)，void 为 Block(prelude + 展开结果 statements)。不创建引用兄弟 scope 临时的返回 Expr。宿主调用前可做 session/类型/mode 检查，但序言作为树的一部分在最终验证时检查完整支配；不能把 VALUE 临时登记为全局 external binding 来掩盖缺少 prelude。

## 9. 有序实施图与每叶写入边界

以下是需根协调者 refine 到图中的完整切片；`api/`、`expand/` 均相对 `src/main/kotlin/top/mcfpp/mni/`，`test/` 相对 `src/test/kotlin/top/mcfpp/test/`；`io/`、`model/`、`antlr/` 相对 `src/main/kotlin/top/mcfpp/`。依赖为明确偏序；同文件切片串行。每行最多 3 步、4 个写文件。新增文件名为确定建议，不代表已有实现。

| slice / 依赖 | 唯一写路径（≤4） | 步骤（≤3）与验收 |
| --- | --- | --- |
| P00A 消费者兼容 | expand/CodeArena.kt；expand/CodeValidator.kt；expand/CodeLowerer.kt；expand/CodePrinter.kt | ①给现 sealed when 增加带 span 的未知节点拒绝分支 ②现有创建/导入/验证/plan 回归；此叶不开放新语法 |
| P00B plan 测试兼容 | test/MniCodeLoweringPlanTest.kt | ①测试机未知 operation 明确失败 ②原 plan 行为回归；给新 sealed operation 留编译接点 |
| P01 模型 / P00A,B | api/Code.kt；api/Types.kt；expand/LoweringPlan.kt；test/MniProjectionModelTest.kt | ①增加 Index/CaptureAddress/typed readonly 与兼容默认参数 ②增加冻结图/index schema/PlanConstant 模型 ③深不可变和旧构造兼容；未接消费者的新节点必须明确拒绝。此时只新增 PlanConstant，尚不改变 Literal.value 类型，避免破坏现测试机编译 |
| P02 canonical 图 / P01 | expand/BindingEnvironment.kt；expand/BoundSource.kt；test/MniSourceTypeProjectionTest.kt；io/info/MacroFunctionInfo.kt | ①catalog 图/字段与完整 snapshot 重建 ②SourceField/形参/default 模板身份和 metadata prepare 新重载 ③自循环、继承、泛型、旧 snapshot 独立；库 metadata 不持图 |
| P03 arena / P02 | expand/CodeArena.kt；test/MniProjectionArenaTest.kt | ①新节点预算/依赖和地址 ID ②导入声明扫描及重命名 ③重复插入、自由绑定、跨会话/地址支配前置检查 |
| P04 validator / P03 | expand/CodeValidator.kt；test/MniProjectionValidationTest.kt | ①索引/字段/schema/转换/常量完整验证 ②地址控制路径支配与完整 readonly 签名 ③非法地址/伪造 descriptor/模式/上下文负例 |
| P05 printer / P03 | expand/CodePrinter.kt；test/MniProjectionPrinterTest.kt | ①输出新节点/readonly/地址 ②重复 prepare/session 下打印稳定，不露物理 ID |
| P06 detached lowering / P04 | expand/CodeLowerer.kt；expand/LoweringPlan.kt；test/MniCodeLoweringPlanTest.kt；test/MniProjectionPlanTest.kt | ①Index、地址表和 typed readonly 降低 ②Literal.value 与 readonly 同步切为递归 PlanConstant，更新测试机 ③独立机运行顺序/别名；跨 session、旧 snapshot、关闭 arena 后深遍历无 compiler 句柄 |
| P07 声明默认纯绑定 / P02 | expand/BindingEnvironment.kt；expand/SourceCodeBinder.kt；expand/SourceDefaultBinder.kt；test/MniSourceDefaultBindingTest.kt | ①纯模板/前序 formal 引用绑定 ②在现 common selector 接入签名实例化事实，保持唯一排名 ③readonly/default 正负和无副作用；不使用旧 completeDefaultValue |
| E01 R_SOURCE_ENV 索引 / P02,P07,R_ENTRY | expand/CompilerBindingEnvironment.kt；expand/CompilerSourceIndex.kt；test/MniSourceEnvironmentTest.kt | ①真实 namespace/import/权限/声明及 default 索引 ②共同候选与 catalog ownership ③Java/Kotlin 加载宏、普通候选同域；不编译函数体 |
| E02 R_SOURCE_ENV session 桥 / E01,P03,M_SESSION | expand/CompilerBindingEnvironment.kt；expand/CompilerSourceBindings.kt；expand/ExpansionSession.kt；test/MniSourceEnvironmentBindingTest.kt | ①源符号→session 外部 Ref/context/layout ②目标白名单与只读事实 ③预设当前函数/命令与资源/scope/callgraph/flow 哨兵，采集完全无求值 |
| P08 真实投影 / P06,P07,E02 | expand/SourceCodeProjector.kt；expand/SourceProjectionEnvironment.kt；test/MniSourceProjectionTest.kt；test/MniSourceCallProjectionTest.kt | ①实现全部 BoundExpr 映射及位置捕获 ②普通/native readonly/default ExprBlock 与 formal 替换 ③源码解析→纯绑定→Code→final 验证→plan 行为测试；无注入空投影 service |
| A01 R_ARG_SEQUENCE / P05,P06,P08 | expand/MacroCallResolver.kt；expand/MacroArgumentBinder.kt；test/MniArgumentBindingTest.kt | ①唯一目标关联 ②VALUE/CONST/REF 有序绑定 ③未用副作用、混排动态索引、别名及非法模式回归；INLINE 单独门 |
| B01 M_BACKEND_EMIT 值/索引 / P06,E02,M_STORAGE_JOURNAL,M_FUNCTION_JOURNAL,M_GLOBAL_STATE | expand/CompilerBridge.kt；expand/CompilerPlanValues.kt；expand/CompilerPlanLocations.kt；test/MniSourceProjectionBackendTest.kt | ①计划基本块/值/Index 和稳定地址消费 ②staging/journal 接入 ③真实命令与Score执行结果、失败回滚；不可只运行 plan 机 |
| B02 普通/native 完整调用 / B01,P08 | expand/CompilerBridge.kt；expand/CompilerPlanCalls.kt；model/function/Function.kt；test/MniProjectedOrdinaryCallTest.kt | ①增加明确完整实参普通调用接点 ②readonly 目标映射/默认不再执行 ③真实普通/generic 调用及默认一次执行；更广 compiler body 生成仍受既有 M_LOWER 事务门约束 |
| B03 native 双协议 / B02 | expand/CompilerPlanCalls.kt；model/function/NativeFunction.kt；test/MniProjectedNativeCallTest.kt | ①完整 typed readonly+normal 转换入现双 ABI ②receiver/result 包装且不重新补默认 ③context/legacy 两种 readonly/default/错误回滚真实测试 |
| I01 INLINE 合流 / A01,R_EXPAND,R_SOURCE_BODY,C_INLINE_EXPAND | expand/MacroArgumentBinder.kt；expand/SourceCodeProjector.kt；test/MniArgumentBindingTest.kt；test/MniSourceProjectionInlineTest.kt | ①使用真实 callback binder ②闭合 MacroCall 嵌套展开位置 ③callback body 不在参数投影执行、每次 inline 卫生、返回区域回归 |
| C01 R_PIPE_INTEGRATE / A01,R_EXPAND,I01,B03,M_PRODUCTION_COMMIT,M_BRIDGE_ACCEPT,I_CONSTRUCT | antlr/MCFPPExprVisitor.kt；expand/MacroExpander.kt；test/MniMacroCallPipelineTest.kt；test/MniNbtReferenceTest.kt | ①eager spill 前纯判定并走新管线 ②prelude+host 结果整体递归展开/验证/暂存提交 ③真实源码含宏/普通/旧MNI/REF、失败不回退；全部原验收在组门汇合 |

P00 的临时未知分支只用于可编译分期，必须抛出有来源的异常，禁止 Unit/空集合/继续遍历跳过未知节点。CodeArena 的 use、prepare 扫描和 copy 三处分别处理，不能只改创建入口。P03/P04/P05/P06 后，新增节点必须各有真实分支与测试。DefaultCodeBuilder 的已有保守 control-flow 分支无需解释新节点的 effect，但导入时仍必须经过完整 arena 扫描/验证。最终组门扫描所有 ExprNode/StmtNode/PlanOperation 消费者，确认没有新模型落到临时拒绝分支；新的消费者出现需纳入分片边界。不得为赶进度让最终测试只覆盖旧节点。

R_SOURCE_PROJECT_IMPL 占位应拆为 P00–P08 及 E01/E02 的明确依赖，不能继续作为三文件大叶。R_ARG_SEQUENCE 依赖 P08/P06，不能让其作者修改 SourceExpr 既有四文件。B01–B03 并入 M_BACKEND_EMIT（原 M_LOWER/D_NBT 的真实生产接入）组；R_PIPE_INTEGRATE 必须同时依赖 source projection/参数绑定和 M_BACKEND_EMIT，不能只依赖两者之一。本文不操作任务图，根协调者需记录这些必需后继；不能以“未来可扩展”省略。

### 9.1 必须同时替换旧依赖，避免父组等待自己的后继

本表的新增边不能直接叠加到旧粗依赖上。依赖箭头统一表示“前置 → 消费者”；组是验收汇合点，其子叶不得依赖包含自己的组或该组的下游合流。落图须同时执行以下替换：

当前基线采用协调者确认的图 v395：R_BIND 已仅依赖 R_SYNTAX/M_TYPES；C_INLINE 已依赖 R_SOURCE_EXPR/M_BUILDERS/R_SYNTAX；R_SOURCE_ENV 已仅依赖 R_SOURCE_EXPR/R_ENTRY/R_SYNTAX/M_TYPES。这些已修正边保持。实际仍需修改的是 C_INLINE_EXPAND 对 R_BIND 的粗依赖，以及 R_CALL_PIPE 对 R_EXPAND/M_LOWER/I_CONSTRUCT 的父组依赖。仓库 `inheritance-mni-plan.json` 是历史初图，不能据它声称已删除的边仍存在。

- **移除 `C_INLINE_EXPAND.deps` 中的 `R_BIND`**，将其直接 deps 改为 `R_SOURCE_BODY`、`R_ARG_SEQUENCE`（A01）、`R_EXPAND`、`R_SOURCE_EXPR`、`M_BUILDERS`、`R_SYNTAX`、`M_TYPES`、`M_VALIDATE`、`M_SESSION`。展开所需的投影/地址能力由 A01 的前置传递；不得再等待包含最终调用接入的整个 R_BIND。
- C_INLINE 父组保持 v395 的 `R_SOURCE_EXPR`、`M_BUILDERS`、`R_SYNTAX`，将展开依赖留给具体 C_INLINE_EXPAND。历史初图曾有 C_INLINE 父组依赖 R_BIND；当前已修正，不再执行一次“删除”。落图仍检查祖先继承边，避免重新引入相同等待环。
- `R_EXPAND` 依赖 A01 的已稳定 VALUE/CONST/REF 句柄和 generated-call 绑定接口，以及 M_SESSION/M_VALIDATE；不能依赖 `R_BIND`、`R_CALL_PIPE`、`R_PIPE_INTEGRATE`、I01 或 C_INLINE_EXPAND。其基本宏递归能力可在 INLINE 不可用时独立实现/验证，INLINE 的最终完成在 I01 验收。
- **I01 归最终 pipeline 合流**，直接在 A01、R_EXPAND、R_SOURCE_BODY、C_INLINE_EXPAND 之后；不放进 R_ARG_BIND 组。R_ARG_BIND 只汇合投影与 A01 的基础实参绑定。否则 R_EXPAND 等待含 I01 的 R_ARG_BIND、I01 等待 C_INLINE_EXPAND、后者又等待 R_EXPAND，会形成环。
- E01/E02 仍属于 R_SOURCE_ENV 的准备子叶；保留原父组归属，不得承袭 `R_EXPAND`、`M_LOWER`、`R_PIPE_INTEGRATE` 前置。`R_CALL_PIPE` 父组从 v395 的 R_EXPAND/M_LOWER/I_CONSTRUCT/R_SYNTAX/M_TYPES 改为仅 `R_SYNTAX`、`M_TYPES`；R_SOURCE_EXPR/R_ENTRY 等具体准备依赖留在 R_SOURCE_ENV/E01。不能通过父组让 E01/E02 等待下游展开/后端。继承声明的只读完成事实由索引前置提供，原 I_CONSTRUCT 的整体接入验收仍保留在 C01。
- 更外层 R_BIND 保持 v395 的 `R_SYNTAX`、`M_TYPES`，不能重新添加历史初图的 M_LOWER/I_CONSTRUCT 粗依赖，也不能让它依赖自己内部的 R_SOURCE_EXPR、R_EXPAND 或 C01。R_SOURCE_ENV 保持 `R_SOURCE_EXPR`、`R_ENTRY`、`R_SYNTAX`、`M_TYPES` 的准备前沿，E01/E02 再使用表中细化依赖。否则 B01 等待 E02、E02 经祖先 R_BIND 等待整个 M_LOWER，而 M_LOWER 又包含 B01，仍会形成继承依赖环。
- B01 是 M_BACKEND_EMIT / M_LOWER 下的生产子叶，**不得依赖整个 M_LOWER 或 M_BACKEND_EMIT**。只直接依赖 P06 的 detached plan、E02 的实际符号/目标/layout 表，以及 M_STORAGE_JOURNAL、M_FUNCTION_JOURNAL、M_GLOBAL_STATE 的已验收 checkpoint。完整全局 journal 若继续细分，依赖其最终组合叶，不能回退到依赖包含 B01 的祖先组。
- M_COMPILER_BRIDGE 父组保持当前 `M_LOWER_PLAN`、`M_VALIDATE`、`M_SESSION` 的具体基础依赖；M_LOWER_PLAN 是已存在的纯 plan 叶，不是包含 backend 的整个 M_LOWER。细分 M_BACKEND_EMIT 时，把当前 M_GLOBAL_JOURNAL 组依赖落实为其最终组合叶 M_GLOBAL_STATE，并加入 P06/E02；storage/function/global 的全部原验收仍由实际组合链和 M_BRIDGE_ACCEPT 汇合。
- R_PIPE_INTEGRATE（C01）保留实际构造、展开、backend 与原子提交的末端门：显式依赖 A01、R_EXPAND、I01、B03、M_PRODUCTION_COMMIT、M_BRIDGE_ACCEPT、I_CONSTRUCT。B03 是 B01–B03 细分后的 backend 末端；它明确替代“等待整个 M_LOWER”的粗依赖。若节点仍名为 M_BACKEND_EMIT，则以该组的独立末端验收门关联，组内不能包含 C01。

落图用的直接依赖清单如下。已有模型/注册/语法节点是外部准备前沿，不增加到本文的 18 个实施切片数量中；journal 与展开节点沿用现节点，只修依赖，原各自验收不删减。

| 节点 | 直接 deps（逗号分隔；空为该局部图的已存在前沿） |
| --- | --- |
| P00A | M_TYPES, M_VALIDATE, M_LOWER_PLAN |
| P00B | M_LOWER_PLAN |
| P01 | P00A, P00B |
| P02 | P01, R_SOURCE_EXPR |
| P03 | P02 |
| P04 | P03 |
| P05 | P03 |
| P06 | P04 |
| P07 | P02 |
| E01 | P02, P07, R_ENTRY, R_SYNTAX |
| E02 | E01, P03, M_SESSION |
| P08 | P06, P07, E02 |
| A01 | P05, P06, P08 |
| R_EXPAND | A01, M_SESSION, M_VALIDATE |
| R_SOURCE_BODY | R_SOURCE_EXPR, M_TYPES, R_SYNTAX |
| C_INLINE_EXPAND | R_SOURCE_BODY, A01, R_EXPAND, R_SOURCE_EXPR, M_BUILDERS, R_SYNTAX, M_TYPES, M_VALIDATE, M_SESSION |
| I01 | A01, R_EXPAND, R_SOURCE_BODY, C_INLINE_EXPAND |
| M_STORAGE_JOURNAL | |
| M_FUNCTION_JOURNAL | M_STORAGE_JOURNAL |
| M_COMPOUND_SCOPE_JOURNAL | |
| M_OBJECT_STATE | M_STORAGE_JOURNAL, M_FUNCTION_JOURNAL, M_COMPOUND_SCOPE_JOURNAL |
| M_GLOBAL_STATE | M_STORAGE_JOURNAL, M_FUNCTION_JOURNAL, M_OBJECT_STATE |
| B01 | P06, E02, M_STORAGE_JOURNAL, M_FUNCTION_JOURNAL, M_GLOBAL_STATE |
| B02 | B01, P08 |
| B03 | B02 |
| M_PRODUCTION_COMMIT | B03, M_GLOBAL_STATE, M_SESSION |
| M_BRIDGE_ACCEPT | B03, M_PRODUCTION_COMMIT |
| C01 | A01, R_EXPAND, I01, B03, M_PRODUCTION_COMMIT, M_BRIDGE_ACCEPT, I_CONSTRUCT |

可复核的拓扑路径为：`P00 → P01 → P02 → P03 → P04 → P06` 与 `P02 → P07 → E01 → E02` 汇入 P08，再到 A01→R_EXPAND→C_INLINE_EXPAND→I01；独立 journal 前沿与 P06/E02 汇入 B01→B02→B03→M_PRODUCTION_COMMIT→M_BRIDGE_ACCEPT；两支最后汇入 C01。P05 在 A01 前，R_SOURCE_BODY 在 C_INLINE_EXPAND 前，I_CONSTRUCT 在 C01 前。没有从 C01/I01/后端回到 E01/E02、A01 或其父准备组的边。

此清单用于检查新增子图无环；根协调者落图时仍须检查完整现图，包括所有父组的继承依赖。原 R_BIND 的一次求值/别名/失败无发布、C_INLINE 的卫生与返回区、M_LOWER 的真实命令/旧 MNI/回滚、R_CALL_PIPE 的 eager 前置识别，以及继承构造兼容验收均保留；局部叶提前完成不代表上述汇合门完成。

## 10. 必须可观察的验收案例

| 场景 | 行为证据 |
| --- | --- |
| `unused(effectA(), effectB())` | host 不读任何 Value；最终执行仍记录 A→B，各一次；宏构造/纯投影期间记录为空 |
| 同一 Value.expr 两次 | initializer 中 effectA 只一次，两次读取同一临时值；单独两次普通 Expr 使用则允许两次执行 |
| `m(a(), ref xs[index()], b())` | 记录 a→base→index→address→b；后续宏反复读写 ref 不再运行 index |
| `ref rows[first()].items[second()]` | second 改变 rows/first 索引来源时，ref 仍用先前固定的中间基址；不能只验证 index 调用计数 |
| 两个 `ref health` | 先加 1 再读/加 2，原 health 增加 3；没有参数内容复制或末尾回写；不同 AddressId 仍别名 |
| 值调用 `getRecord().values[index()]` 的已绑定结构 | getRecord→字段→index 各一次，返回元素类型来自 schema；不可写临时可读，显式 REF 拒绝 |
| bool index、immutable-list REF、getter REF、cast REF | 都有正确 span 的 SourceBindingFailure/CodeModelException，命令/资源/flow 哨兵未变；同类型 cast 也拒绝 |
| `call<T>(x, default(x))` | readonly T 的类型常量保留至 plan；默认读取先前参数临时，effectful x 一次；caller 同名变量不能污染模板 |
| required readonly、readonly default、normal default | 分别覆盖普通 GenericFunction 与两 Native ABI；完整参数只补一次，缺 default payload 明确拒绝 |
| int/float 重载、CONST 动态值、REF 转换 | exact 胜 conversion；选中 CONST 后动态值报模式错误，不退回 VALUE；REF 不转换；共同候选含普通函数 |
| `false && effect()` / 循环内宏参数 | 短路无 effect；每次循环执行一次参数序言，不能提升到循环外 |
| cyclic Node 字段及 `Node<int>/Node<bool>` | 同域字段 handle 身份一致，自循环可遍历有限层；两个泛型实际类型不同；重 prepare 不改变旧字段/父/元素事实 |
| 地址支配、scope、context、导入 | 分支内地址逃逸拒绝；两次整体插入地址/Local 身份分别重命名，外部原位置保留；跨 context/session 不静默重绑定 |
| detached 常量与关闭 session | type 常量嵌列表/record/readonly 全部只余 PlanConstant/PlanType；关闭 arena 后无 TypeRef/Code/Function/Method/ANTLR/闭包可达 |
| 真正调用接入 | 从实际源码与配置 JAR 入口跑宏，再调用普通/legacy MNI；检查 ScoreCommandExecutor 结果、callgraph/flow、失败无部分输出。plan 测试机不能替代该项 |

测试必须设置真实 `Function.currFunction` 和命令哨兵，并快照 command/resource/scope/callgraph/flow/当前函数状态；不能靠未初始化全局状态使 eager 路径提前失败。计数、顺序与运行结果同时验证，避免只匹配节点字段。严格区分纯阶段“零执行”、plan 机“执行语义”、真实 backend “生成命令并运行”三类证据。

## 11. 交付自检与独立验收入口

本轮只写本文档，不改产品/测试、不运行 Gradle。静态自检覆盖：每个 BoundExpr 变体、normal/readonly/default、规范字段循环、REF 前缀捕获、短路/嵌套宏位置、全部树消费者、PlanConstant 深分离、真实环境与 backend 责任、每叶 ≤4 文件/≤3 步。当前产品缺口仍由 §9 的各叶交付，不算本文档已经修复。

独立 reviewer 应重点检查：地址捕获是否保存原物理位置而非内容、default formal identity 是否闭合、是否仍存在按 mode 第二次排名、canonical 图构造是否可自引用且旧 snapshot 不变、plan 是否还残留 TypeRef、以及分期最终是否把临时 unknown 分支全部替换成真实实现。

本轮自评（均为设计文档，未取得产品实现证据）：

| 维度 | 分数 | 证据与改进点 |
| --- | --- | --- |
| 准确性 | 4/5 | §1 对照实际节点、调用和默认值入口；新冻结图/地址 API 尚需独立实现审查验证构造细节 |
| 完整性 | 4/5 | §5 覆盖全部 BoundExpr，§7 覆盖 plan 深分离，§9 明确后台/visitor/INLINE 合流；真实后台具体命令仍由列明后继叶落实 |
| 清晰度 | 4/5 | 映射表和责任边界可直接查阅；实施表较长，根落图后宜按叶提供局部链接 |
| 可执行性 | 4/5 | 给出具体签名、生产类、顺序、文件边界和行为断言；每叶还需独立编译及实际回归，不能把本文当运行证明 |
| 简洁性 | 4/5 | 关键语义集中到一处，避免每叶各自解释；签名与实施图共同阅读仍有成本 |

均分 4.0/5。后续优先完成 P01/P02 的循环 canonical 图构造验证、P06 的跨 session 深分离回归，再以 B01–C01 的真实调用验证时序。自检结论应可由独立 reviewer 依据上述具体接点复核；当前结论是“可交付审查的实施设计”，不是产品完成。

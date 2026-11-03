# MNI 首期实施契约

本契约对照 601 行 `MNI-METAPROGRAMMING-PLAN.md`、35 节点任务图 `.codex-workflow-graph/inheritance-mni-plan.json` 和当前工作区源码制定。它是实现接口与验收约定，不表示这些 API 已实现。公共包使用 `top.mcfpp.mni.api`，编译器内部包使用 `top.mcfpp.mni.expand`。本任务只交付本文；下述路径及任务图调整由协调者落实后交给对应 worker。

## 1. 保持的边界与实际接入点

运行时程序继续由 MCFPP 表达，Java/Kotlin MNI 生成代码。只新增调用处 `ref` 和具有目标类型的 lambda。保留现有常量、特化、历史编译期函数及旧 MNI 行为；不引入 MCFPP quote/comptime/splice、运行时闭包、回调编号分派或任意旧函数体改写。

本次核对的实际源码接点如下，行号为编写时位置；并行继承修复可能移动行号。

| 接点 | 当前事实 | 新路径职责 |
|---|---|---|
| `antlr/MCFPPExprVisitor.kt:503–555` | 调用开始就 spill、写注释，然后访问并 capture 实参，最后选择函数 | 必须在 504 行动作之前识别可解析的调用候选；只有确定非宏后才进入旧路径。禁止宏失败后再按普通函数重试 |
| `antlr/MCFPPImVisitor.kt:215–232` | 先尝试 `PrimitiveCompiler.tryCompile`，再进入普通 visitor | 优化器的纯 prepare 阶段识别宏并路由，或无副作用返回 unsupported，交新宏路径处理；不得先输出再 fallback |
| `analysis/DeclarationBinding.kt:25–44,270–317` | 使用声明签名查询类型与常量，但 `Bound` 没有保留表达式、位置或符号身份；泛型模板分支还会 compile 声明 | 可复用类型关系、声明候选选择和闭合常量规则，不能直接把 `Bound` 当作新宏的源代码树；建立纯源绑定器 |
| `analysis/ValueModel.kt:9–62` | 已有 `SymbolId`、`Symbol`、不可变 `Place` 和捕获索引的 `Location` | 内部桥接保留原物理位置和符号；公共作者不接触这些内部对象 |
| `analysis/TypedIR.kt:6–64` | 已有显式读写、调用、基本块和控制流，但含内部布局与 RawCommand | 可作为降低目标；公共代码树保持结构化，不能以 RawCommand 代替缺失语义 |
| `type/MCFPPType.kt:48–58,122–124` | 类型由 `typeId` 标识；类型对象及成员 scope 可变 | `TypeRef` 是只读投影，类型相等使用规范身份，不能把 `MCFPPType` 暴露给宏 |
| `model/function/Function.kt:1264–1271` | `runInFunction` 用 try/finally 恢复全局当前函数 | 仅内部 `CompilerBridge` 可以借用此机制；不能将当前 Function 传给新宏作者 |
| `model/Namespace.kt:153–223` 与 `model/compound/CompoundData.kt:325–394` | 两条旧 MNI 注册路径检查不一致 | `N_VALIDATE` 共享旧协议验证，`R_ENTRY` 只为 namespace 新增独立宏注册器 |
| `model/function/NativeFunction.kt:70–129`、`mni/NativeCallContext.kt` | 编写时快照中调用只接受单个 NativeCallContext，并通过 resultAdapter 返回；N_ABI 正在恢复双协议 | 新宏不复用 NativeFunction 的 eager 实参调用；生成树对旧 MNI 的调用留到降低阶段 |
| `Project.kt:345–353`、`model/Namespace.kt` 的类注册入口 | 配置 JAR 加入 Project.classLoader，类进入 namespace 注册 | 新宏与处理器使用同一 classloader 和现有类发现链；不另做 Kotlin 执行器或无条件扫描整 JAR |
| `io/MCFPPFile.kt:182–188`、`antlr/MCFPPAnnotationVisitor.kt:77–93` | annotation 阶段记录/执行旧注解 | 在已有次序中收集新处理器请求，不能绕过所有文件的声明完成屏障 |
| `Project.kt:616–657,730–733`、`model/compound/DataTemplate.kt:105–132` | 字段推断和 pending annotations 在 completeTemplateDeclarations 内完成，再检查函数体 | 新声明处理器在完整字段视图可用后运行、提交生成签名，然后检查源和生成体；与继承完成节点共同协调 |
| `Project.kt:58–88` | 错误位置依赖 ANTLR context 栈，withCompilationContext 对未捕获异常会退出进程 | 新会话自行转为结构化诊断；调用边界捕获宏/验证/降低异常，保留 SourceSpan 和展开链 |

旧协议差异必须记录：原计划 8.2 描述的历史位置参数数量为 readonly + normal + 非 void caller + 非 void 返回 wrapper；编写时快照已迁移到 `NativeCallContext`，当时不存在 `ValueWrapper` 源文件。协调者已新增 `N_ABI`，恢复历史 `ValueWrapper` 与双协议 invoke 的工作正在进行；分别用真实 fixture 验证位置 ABI 与 context ABI，`N_VALIDATE` 仅统一两种入口的注册校验。上述缺失是编写时事实，不是后续实现必须保持的限制。不要为新宏回退或覆盖当前未提交的 context 迁移。

## 2. 模块依赖、交付所有者和 Java ABI

依赖方向固定为：公共声明与不可变节点 → builder 实现 → 会话组合 → 注册/源绑定/验证 → 递归展开 → 后端降低。`api` 禁止 import `expand`、Var、ANTLR、Function、Project；`expand` 可以依赖内部类型与后端。

为解决 `MacroContext → Quote → ExpansionSession` 环，采用以下明确归属：

1. `M_TYPES` 一次交付 `Code.kt`、`Types.kt`、`Inputs.kt` 和 **接口骨架** `Quote.kt`、`CodeBuilder.kt`、`MacroContext.kt`。相互引用的都是接口，源码同批编译，不需要会话实现。它也交付内部 `CodeArena.kt`：会话身份、作用域/符号工厂、不可变节点工厂、节点计数 hook。
2. `M_BUILDERS` 交付 `expand/DefaultQuote.kt`、`expand/DefaultCodeBuilder.kt`、`api/KotlinDsl.kt`，构造函数只接受 CodeArena 和只读服务接口。禁止引用 ExpansionSession；节点分配调用 arena 的 hook。
3. `M_SESSION` 实现 ExpansionSession、Budget 和 context/service 实现，组合 DefaultQuote 与事务。图中应增加 `M_SESSION → M_BUILDERS` 依赖。所有服务接口在 M_TYPES 的 MacroContext.kt 中先定义，具体实现归 M_SESSION。
4. validator 接受树与只读环境，lowerer 接受验证证明及显式目标。二者都不成为公共 Quote 的依赖。

公共 API 使用真实 JVM 接口/普通类、命名方法与 `java.util.function.Consumer/Function/BiConsumer`。不用 Kotlin `Function1`、`Unit` 返回的 SAM、value class、带必填默认参数的方法作为唯一 Java 入口；使用 `void` SAM，提供显式重载。公共方法签名以本文 Java 视图为准。Kotlin 属性/扩展只是便利包装；Java 必须能写 `ctx.quote().block(b -> { ...; })`。公共类不可由作者注入任意内部节点；工厂创建句柄，验证器仍拒绝无法识别的实现。

下列 `T` 是 MCFPP 类型的 phantom marker，例如 `IntT`、`BoolT`、`FloatT`、`StringT`、`VoidT`、`EntityT`、`PositionT`、`BlockT`；并非 JVM 数字类型。marker、方法重载与 Java 泛型不能替代实际 MCFPP 类型验证。

## 3. 公共代码和参数句柄（M_TYPES）

以下为可实现的 Java 可见签名；实体以不可变 final 实现/内部构造器实现，接口仅用于表达其视图。

```java
interface Code { SourceSpan source(); }
interface MacroInput extends Code {}
interface Expr<T> extends Code { TypeRef<T> type(); }
interface Stmt extends Code {}
interface Block extends Code { List<Stmt> statements(); }
interface Ref<T> extends MacroInput { TypeRef<T> type(); Expr<T> load(); }
interface Local<T> extends Ref<T> { String hint(); }
interface Value<T> extends MacroInput { TypeRef<T> type(); Expr<T> expr(); }
interface Const<T> extends Value<T> { ConstantValue value(); int intValue(); boolean boolValue(); }
interface TypeRef<T> {
    String name();
    boolean sameType(TypeRef<?> other);
    boolean isAssignableFrom(TypeRef<?> other);
    List<FieldRef<?>> fields();
}
interface InlineFn<S> extends MacroInput { InlineSignature<?> signature(); }
interface FunctionRef<R> { String name(); List<ParameterView> parameters(); TypeRef<R> returnType(); }
interface FieldRef<T> { String name(); TypeRef<T> type(); boolean writable(); SourceSpan source(); }
interface InlineSignature<R> { List<ParameterView> parameters(); TypeRef<R> returnType(); }
interface FunctionSignature<R> extends InlineSignature<R> {}
interface ParameterView { String name(); TypeRef<?> type(); ParamMode mode(); }
```

`SourceSpan` 包含文件逻辑路径、start/end offset、start/end 行列，使用公开构造器/静态工厂；`unknown()` 仅用于没有源文件的宿主生成点。源文件 offset 采用半开区间，行列对外从 1 开始。生成节点默认定位到调用点；另外保留内部 `Origin(callSite, generatedFrom, expansionFrame)`，避免把宿主代码文件行号作为用户错误位置。

`TypeRef` 内部保存规范类型身份和只读 descriptor，不允许从 name 重新猜类型。新声明的类型在当前编译图内规范化；声明/资源版本会话变化后不得借名称复用过时句柄。`TypeService` 提供 `intType()/boolType()/floatType()/stringType()/voidType()`、`lookup(String)`、`function(String, List<TypeRef<?>>)`；没有宏通用类型参数注册或开放式类型构造器。

`ConstantValue` 是公共不可变值 ADT：整数、浮点位模式、bool、string、NBT 文本、序列、record、type/null。容器防御性复制并使用不可修改视图，不能暴露可变 NBT Tag、Var 或任意宿主对象。`Const.intValue()/boolValue()` 在类型不匹配时报定位诊断，不能静默强制转换；内部可投影自现有 CompilerValue。`Const.expr()` 生成相同值的字面量，永远不解释任意函数。

每个代码/输入句柄的内部元数据包含 `SessionId`、`ScopeId`、`Origin`、实际 TypeKey（适用时）、不可变 payload。Local 额外包含符号身份；Ref 额外包含位置与绑定时的 ExecutionContextId。不得把 SessionId、内部 SymbolId 等 JVM value class 放进公共方法签名。所有集合防御性复制；树不能保存 builder、ParserRuleContext、可变 Var 或宿主 lambda。宿主 lambda 只在构造阶段立即消费。

内部节点至少包含：Literal、Read、Unary、Binary、Convert、Call、MacroCall、ExprBlock、InlineCall；LocalDecl、Assign、Eval、If、While、ScopeBlock、CallbackReturn、Yield、Break、Continue；上下文节点由 C_CONTEXT 增补。表达式块显式携带结果类型及出口。节点 enum/密封层级可以内部实现，但禁止以未定义字符串操作代替节点。

## 4. Quote、builder 与 Kotlin DSL（M_BUILDERS）

```java
interface Quote {
    Block block(Consumer<CodeBuilder> body);
    <T> Expr<T> expression(TypeRef<T> type, Consumer<CodeBuilder> body);
}
interface CodeBuilder {
    <T> Local<T> local(TypeRef<T> type, Expr<T> initialValue);
    <T> Local<T> local(String hint, TypeRef<T> type, Expr<T> initialValue);
    <T> void assign(Ref<T> destination, Expr<T> expression);
    void evaluate(Expr<?> expression);
    void append(Block block);
    void ifThen(Expr<BoolT> condition, Consumer<CodeBuilder> thenBody);
    void ifElse(Expr<BoolT> condition, Consumer<CodeBuilder> thenBody, Consumer<CodeBuilder> elseBody);
    void whileLoop(Function<CodeBuilder, Expr<BoolT>> condition, Consumer<CodeBuilder> body);
    <R> Expr<R> call(FunctionRef<R> function, List<? extends CallArgument> arguments);
    void callVoid(FunctionRef<VoidT> function, List<? extends CallArgument> arguments);
    <R> Expr<R> inline(InlineFn<?> callback, List<? extends InlineArgument> arguments);
    void inlineVoid(InlineFn<?> callback, List<? extends InlineArgument> arguments);
    <T> void yield(Expr<T> expression);
    Expr<IntT> intLiteral(int value);
    Expr<BoolT> boolLiteral(boolean value);
    Expr<FloatT> floatLiteral(float value);
    Expr<StringT> stringLiteral(String value);
    <T> Expr<T> add(Expr<T> left, Expr<T> right);
    <T> Expr<T> sub(Expr<T> left, Expr<T> right);
    <T> Expr<BoolT> lt(Expr<T> left, Expr<T> right);
    <T> Expr<BoolT> eq(Expr<T> left, Expr<T> right);
    Expr<BoolT> not(Expr<BoolT> value);
    <T> Expr<T> field(Expr<?> receiver, FieldRef<T> field);
    <T> Ref<T> fieldRef(Ref<?> receiver, FieldRef<T> field);
}
```

算术、比较其余运算采用相同参数模式，并明确 numeric/bool 支持矩阵；即使通过 raw Java 泛型绕过编译，validator 也必须拒绝 bool 加法、非 bool 条件等。`call`/`inline` 只创建表达式，使用点执行；忽略非 void 表达式不会隐式发出调用，需 `evaluate`。void 操作使用 `callVoid`/`inlineVoid`，直接追加语句。`InlineArgument.value(expr)/ref(ref)/constant(constValue)` 提供 Java 静态工厂，按回调签名检查模式与类型，不使用 `List<Object>`。

`CallArgument` 是不可变的带模式实参联合，归 M_TYPES 的 Inputs.kt；Java 静态工厂签名为 `<T> CallArgument value(Expr<T>)`、`<T> CallArgument ref(Ref<T>)`、`<T> CallArgument constant(Const<T>)`、`CallArgument inline(InlineFn<?>)`。工厂仅保留原句柄，不能通过 `ref.load()` 实现 ref。M_BUILDERS 创建调用节点，M_VALIDATE 根据 FunctionRef 的实际目标种类、parameters 的 mode/类型及作用域逐项验证：普通源函数/旧 MNI 不因这些工厂新增 REF ABI；CONST/INLINE 仅用于支持这些模式的宏目标；内部 helper 首期支持 VALUE/REF，其他模式在定义时拒绝。

M_LOWER 对 VALUE 按从左至右一次求值，对 REF 在对应参数位置固定基址/索引并传递原 BoundLocation；两形参别名必须保持。内部 REF helper 按位置和执行上下文特化，将形参位置替换为原位置，可生成稳定命名的专用内部辅助体或在调用处展开；它不扩展普通源函数 ABI。不得复制引用内容进参数区再回写，也不得把不支持的位置默默当 VALUE；后端无法稳定表示的跨上下文/动态位置应在提交前拒绝。该特化的代码、名称、资源和预算均归当前事务。

```java
// 预先提供的签名为 (REF int, VALUE int) -> void；helper 体由 GeneratedBody 构造。
static Block invokeHelper(MacroContext ctx, FunctionRef<VoidT> helper, Ref<IntT> health) {
    return ctx.quote().block(b -> b.callVoid(helper,
        List.of(CallArgument.ref(health), CallArgument.value(b.intLiteral(1)))));
}
```

`whileLoop` 的 condition 宿主函数只在构造时执行一次，它构造的条件区域在运行时每次迭代执行。builder 关闭后任何操作报错；子 builder 创建词法作用域，不能在子 scope 中借外层 builder 写入节点。`yield` 仅在表达式区域合法；所有可达正常出口必须产生匹配结果，重复/缺失 yield 有诊断。回调 return 与 yield 属于不同区域，不可互换。

卫生规则：

- `append` 及 callback 插入使用 capture-avoiding 克隆；只重命名插入树内部声明及其绑定引用，保留输入 Value、Ref、自由变量、外部 FunctionRef 的绑定。
- 重复 append 同一块得到不同局部身份；不能仅更改显示名称。循环体在树上克隆一次，不是每次运行重新编译。
- scope 以父链验证支配与可见性；不能把 child Local 返回成外层 Ref，也不能从 sibling scope 引用它。合法表达式块可通过 yield 传出值，不传出局部存储句柄。
- 一般 Expr 重复插入可以重复执行；需要一次求值必须 local。Value.expr 则是 binder 创建的稳定参数符号读取。
- 同一根会话内的子宏会话可重映射导出树后组合；已经关闭/失败会话的句柄以及另一根调用的句柄一律拒绝。不能允许作者静态缓存代码再用于下一调用。

Java/Kotlin 共用上述实现。Kotlin DSL 用 `@DslMarker` 和接收者扩展，命名 `ctx.quote.block { ... }` 可以是 `getQuote`/扩展属性的包装，但 `ctx.quote()` 必须仍是有效 Java 方法。语义检查不能依赖 DSL marker。

并排教程必须展示以下区别：

```java
// Java if/for 决定构造多少节点，在编译期执行。
return ctx.quote().block(b -> {
    for (int i = 0; i < count.intValue(); i++) b.assign(health, b.sub(health.load(), amount.expr()));
});
// builder.ifThen 决定运行时是否执行赋值。
return ctx.quote().block(b -> b.ifThen(b.lt(health.load(), b.intLiteral(0)),
    branch -> branch.assign(health, branch.intLiteral(0))));
```

## 5. 宏 context、服务与会话（M_SESSION）

```java
interface MacroContext {
    Quote quote();
    TypeService types();
    DiagnosticService diagnostics();
    HelperService helpers();
    ResourceService resources();
    SourceSpan callSite();
}
interface DiagnosticService {
    void warning(SourceSpan site, String message);
    void error(SourceSpan site, String message);
}
interface HelperService {
    <R> FunctionRef<R> define(String hint, FunctionSignature<R> signature, GeneratedBody<R> body);
}
interface ResourceService {
    ResourceRef reserve(ResourceKind kind, String hint, ResourceData immutableData);
}
@FunctionalInterface interface GeneratedBody<R> {
    void build(CodeBuilder builder, List<? extends MacroInput> parameters);
}
```

`GeneratedBody` 是一次消费的代码构造 SAM，接受当前辅助函数的 `CodeBuilder` 与参数 Value/Ref 只读列表；void 体构成 block，非 void 体用 yield 产生结果。它在注册辅助体时立即消费，不保存在不可变树里。ResourceKind 首期仅显式支持 objective、storage 及内部函数文件等后端确实实现的类别。ResourceData 只能是不可变受支持模型；拒绝任意全局回调。辅助函数的参数符号由服务分配，不允许把外层 Local/上下文 self 暗中捕获进未来调用；显式全局资源与参数可以使用。

内部签名和所有者：

```kotlin
// M_TYPES: 不依赖 ExpansionSession，不发布编译器状态。
internal class CodeArena(
    val sessionId: SessionId,
    val rootScope: ScopeId,
    val accountNodes: (Int, Origin) -> Unit
) // freshSymbol / childScope / factory / importTree，均使用会话局部分配序列

// M_SESSION: 一个根调用事务；嵌套宏为保存点，预算属于编译单元。
internal class ExpansionSession(
    val site: ExpansionSite,
    val budget: ExpansionBudget,
    val environment: ExpansionEnvironment,
    val transaction: CompilerTransaction
) : AutoCloseable {
    fun context(): MacroContext
    fun child(site: ExpansionSite): ExpansionSession
    fun stage(code: Code): StagedExpansion
    fun abort(diagnostic: ExpansionDiagnostic): Nothing
    override fun close() // 未完成则回滚；成功关闭后句柄失效
}
internal class ExpansionBudget(val maxDepth: Int = 64, val maxNodes: Int = 100_000) {
    fun enter(frame: ExpansionFrame): BudgetFrame
    fun allocate(count: Int, origin: Origin)
}
```

根调用执行顺序：纯绑定 → 暂存参数序言 → 执行宏构造 → 结构/类型/作用域预验证 → 递归展开所有 MacroCall/InlineCall → 完整验证（无待展开节点）→ 暂存降低 → 验证提交计划 → 一次提交命令、内部函数、资源、声明相关编辑。禁止对外公开 `commit()`，宏作者不能提前发布变更。

事务必须覆盖调用目标命令、辅助函数及 scope 注册、资源/objective/storage 表、调用图边、临时命名分配、相关 StorageAccess/flow facts。用 detached lowering state 和变更集实现；仅复制当前命令列表再还原远远不够。桥接旧 MNI 时它可能改变 scope/flow/global registry，同样要重定向到 staging state 或 journal，失败全部丢弃。`Function.runInFunction` 只恢复当前函数，不能冒充完整回滚。外部文件/网络操作不在事务内。

稳定名称由逻辑文件路径、调用 span、宏规范签名、同调用中的结构序号和构建配置生成；不得使用 JVM hashCode、进程全局 SymbolId.fresh 值或系统临时时间。调试打印使用符号的树内首次出现序号。不同调用实例的可变资源总是隔离；不自动合并辅助函数的可变状态。

预算按编译单元建立一次：默认深度 64（根调用深度为 1），第 65 层拒绝；每个新分配或卫生克隆的节点计一次，第 100001 个拒绝；原始源节点不计为新节点，展开生成的序言/桥接/出口节点计入。只读共享节点引用不重复分配则不重复计数。失败分支已消耗预算不退还，防止反复失败绕过限制。所有路径必须 try/finally 弹出展开帧；不支持的递归内联链在进入已活动 callback 身份时立即拒绝，普通递归宏仍受深度/节点预算约束。错误包含用户 span 和完整必要展开链，不返回部分结果。

`R_CONFIG` 的配置名固定为 `macroMaxDepth=64`、`macroMaxNodes=100000`、`dumpMacroExpansion=false`；配置读取位于 Project.kt，CLI 位于 MCFPP.kt，ProjectConfig 仅存字段。无效的非正限制在编译前诊断，不能悄悄恢复默认值。

## 6. 注册与纯源绑定（R_ENTRY、R_SYNTAX、R_BIND）

新增 Java 注解协议：

```java
@Retention(RetentionPolicy.RUNTIME) @Target(ElementType.METHOD)
@interface MNIMacro {
    String identifier() default "";
    MacroParam[] params() default {};
    String returnType() default "void";
}
@Retention(RetentionPolicy.RUNTIME) @Target({})
@interface MacroParam { String name(); String type(); ParamMode mode() default ParamMode.VALUE; }
enum ParamMode { VALUE, REF, CONST, INLINE }
```

ParamMode/签名描述类型公开在 api，Java 注解在 annotations。INLINE 的 type 采用明确函数签名描述，例如 `(int)->void`；源码函数类型同样采用 `(int,int)->int`、`()->void`，不带 `func` 前缀，普通具名 `func` 声明保持原形式。首期只支持具名 namespace 宏，receiver 为显式参数，不注册成员宏、不添加宏通用类型参数。元数据 type 字符串通过已有声明类型解析投影 TypeRef，不能注册未解析类型。

JVM 入口必须 `public static`，形如 `(MacroContext, Ref, Value, Const, InlineFn) -> Block/Expr`。首参数精确 MacroContext，后续 erased wrapper 按 mode 精确匹配，void 宏返回 Block，非 void 返回 Expr；检查数目、可见性、static、重复有效入口，跳过无关 bridge/synthetic。非空返回值仍需在展开后检查实际 TypeRef，不能相信反射泛型。suspend、额外默认参数 mask、未经明确映射的扩展 receiver/value class 不能作为入口。Kotlin 顶层、object @JvmStatic、外部类的 companion @JvmStatic 均走同一反射链，默认参数不转 MCFPP 默认实参。

重载候选统一按 MCFPP 名称、参数类型和现有类型关系选择，不能靠模式区分同签名；宏之间及宏/普通函数之间的相同签名冲突在注册/声明完成时拒绝。先纯解析结构与类型并确定唯一候选，再检查 `ref`/CONST/INLINE。动态值给 CONST 应报告模式错误，不能落回同名 VALUE 宏。

普通参数保留 expressionList / callArgumentList 与 lambda 结构；`REF expression` 仅保留诊断节点，解析时无条件报告迁移错误。公开地址值使用 `ScoreRef` / `NBTRef<T>` 普通 VALUE 参数；readonlyArgs 保持 expressionList。lambda 使用 `{ i -> ... }`，允许 `{ i: int -> ... }`；单参数目标可隐式使用 `it`，零参数用 `{ ... }`。迁移反例 `(i) => { ... }` 已拒绝并给出迁移诊断。调用模式检查不扩展旧 REF ABI；源码 lambda 的既有函数类型路径与 MNI 的静态体要求分别检查。ANTLR 生成代码由语法生成任务产生，不手改生成文件。

内部签名：

```kotlin
// 新增纯源绑定叶任务；只有此处接触 ANTLR。
internal interface SourceCodeBinder {
    fun call(syntax: FunctionCallContext, env: BindingEnvironment): SourceCall
    fun expression(syntax: ExpressionContext, env: BindingEnvironment): BoundExpr
    fun location(syntax: ExpressionContext, env: BindingEnvironment): BoundLocation
    fun callback(syntax: CallbackSyntax, signature: InlineSignature<*>, env: BindingEnvironment): BoundCallback
}
internal class MacroCallResolver {
    fun resolve(call: SourceCall, env: BindingEnvironment): ResolvedCall
}
internal class MacroArgumentBinder {
    fun bind(call: ResolvedMacroCall, session: ExpansionSession): BoundMacroArguments
}
```

BindingEnvironment 显式持有声明/导入快照、作用域、类型表、符号、已知常量和执行上下文；SourceCall 保留每个实参的语法种类、span、只读参数及完整顺序，BoundExpr 保留不可变节点、实际类型、效应及符号引用。BoundLocation 保留原根符号、路径、基址/索引绑定表达式、writable 和 execution context。解析不执行旧 visitor、不调用 NativeFunction.invoke、不写命令、不触发后端 getter、构造器或普通函数。需泛型声明特化时走受控声明服务并纳入事务，不能借用会发命令的 compile 接口。

参数绑定输出 `BoundMacroArguments(orderedPrelude: Block, arguments: List<MacroInput>, resultType)`：

- VALUE：每个实参从左至右在序言中 `let temp = sourceExpr`，即使没有使用也保留副作用。expr() 返回 temp 的读取。纯字面量可以消除 temp，但不得改变可观察执行顺序。类型转换发生在该参数绑定位置一次。
- 内部 REF：由编译器 API 提供位置句柄，源码 `ref expression` 已拒绝；原位置可写且无需隐式转换；不接受临时值、readonly、算术或普通 getter/setter。基址与动态索引在它的参数序号位置各确定一次，随后读写同一逻辑/物理位置。别名合法，不复制到参数区，不结束回写。
- CONST：只接受现有规则可证明的完整不可变值；纯分析不得为了证明而执行宿主旧 MNI 或任意 MCFPP 函数。仍检查实参类型与模式。
- INLINE：仅静态 lambda 字面体或普通源码函数引用；以声明身份区分创建时快照的自由局部和使用时实时访问的全局，不 eager compile/execute 回调体。动态函数值、native/macro 目标与逃逸明确拒绝。

嵌套宏参数必须同样保留为节点，并在对应求值位置展开；不能把实参里的内层宏提升到整个外层调用之前。序言属于最终树，和宏结果一起验证与提交。返回 Expr 宏在原表达式位置生成 ExprBlock；void 宏返回 Block，只能用于语句位置。

## 7. 验证、递归展开与降低（M_VALIDATE、M_LOWER）

```kotlin
internal class CodeValidator {
    fun validate(code: Code, environment: ValidationEnvironment, phase: ValidationPhase): ValidationResult
}
internal class MacroExpander {
    fun expand(code: PrevalidatedCode, session: ExpansionSession): ExpandedCode
}
internal class CodeLowerer {
    fun lower(code: ValidatedCode, target: LoweringTarget, transaction: CompilerTransaction): LoweringPlan
}
internal class CompilerBridge {
    fun bindLocation(location: BoundLocation, target: LoweringTarget): BackendLocation
    fun lowerCall(call: BoundCall, target: LoweringTarget, sink: StagingSink): BackendValue
    fun commit(plan: LoweringPlan, transaction: CompilerTransaction): CommitResult
}
internal class CodePrinter { fun print(code: Code): String }
```

只有 validator 能构造 ValidatedCode，只有完整递归展开后的树可被 final 验证；初验可含已知签名的 MacroCall/InlineCall，终验禁止残留。验证类型赋值/参数/返回/条件、可写位置、词法支配、结果完整性、break/continue/return 区域、scope/context/session 生命周期。对于树的未支持节点直接诊断，不退回原源码或拼接命令。

CodePrinter 固定节点顺序、局部编号和资源逻辑键；不打印宿主对象地址或全局临时计数，供 R_CONFIG dump 与测试共享。调试结果不是可跨会话重新插入的持久缓存。

CodeLowerer 输出既有 IR/命令模型的暂存计划；局部映射到明确 layout，分支/循环使用内部辅助函数和真实控制流，必要时使用现有 NoStackFunction 机制。backend 生成使用已有 command/type/storage 操作，不要求重写整个 PrimitiveCompiler。旧 MNI 调用在此边界转换参数并调用其兼容协议；新宏构造期间绝不执行它。lowering target 显式包含目标函数、上下文、flow/storage 和诊断 sink；桥接可以在内部临时切换全局值，但必须 finally 恢复，并保证所有输出只到 staging。

测试提交失败时应同时比较命令、命名空间成员/辅助函数、资源、调用图和相关 flow facts，而非只比较 commandList 长度。第三方旧 MNI 主动外部 IO 不属于编译器回滚合同。

## 8. 内联回调与局部返回（C_INLINE、C_RETURN）

InlineSignature 的每个参数包含名称、MCFPP 类型和 mode；回调的目标类型推断在入口选择后进行。`InlineFn<S>` 的 S 仅辅助宿主泛型，实际 signature 为唯一语义依据。InlineBinder 输出不可变 BoundCallback（参数、捕获符号映射、源 body、独立 ReturnRegionId）。INLINE 的普通函数引用必须可解析到其静态源码体；NativeFunction、macro 或动态函数值不能假冒可内联函数。

`InlineExpander.expand(callback, arguments, session): InlineRegion` 按参数模式顺序绑定新实参，为参数与 callback 所有局部分配新身份。自由局部绑定在 lambda 创建时按值快照且只读，拒绝赋值、复合赋值和引用写入；复制遵循已有 `LambdaCaptureRules` / `StorageAccess` 类型契约，不承诺通用深复制。全局按声明身份识别，保持使用时实时读取及合法写入；同名局部不能冒充全局。每次插入保留该创建点快照，不能重新读取局部来源；嵌套 lambda 在自身创建时快照外层参数。每次插入独立命名，不能通过复制原 Var 对象实现卫生。复用现有 lambda/存储/回收体系，不另建闭包、存储或 GC 系统。

非 void 回调以尾表达式产生结果，尾随分号丢弃该值，void 目标也丢弃尾值。显式 return 与尾值都必须按实际结果类型检查，不能借尾分号静默补出结果。

冻结示例签名为 `repeat(times: int, body: INLINE (int)->void): void` 与 `repeat(times: int, initial: int, body: INLINE (int,int)->int): int`。后者每次调用 body(accumulator,index)，返回值作为下一累加器；局部 return 结束当前项并继续后续迭代。射线保留 `raycast(samples: CONST int, body: INLINE (int)->bool): void`，新增 `raycast(samples: CONST int, initial: int, step: INLINE (int,int)->int, keepGoing: INLINE (int)->bool): int`：每个实际 stone 命中先更新 step(accumulator,index) 的结果，再执行 keepGoing(index)，false 停止后续采样。0.5 步长与调用点 ambient 上下文保持原语义，不增加 Result 类型或共享盒。

CallbackReturn 包含所属 ReturnRegionId 和可选结果；C_RETURN 的 `InlineReturnLowerer.lower(region): ExpandedCode` 将它变为该回调区域出口，并为非 void 回调建立结果槽。早返后不会执行该区域余下语句，也不会退出调用者/外层回调。条件和循环中的早返必须经过传播出口状态或等价控制流处理；不能仅设置当前 Function 的全局 isReturned。break/continue 查找当前回调区域内的 loop，禁止越过区域边界。外围停止条件通过 bool/枚举等显式结果表达。

活动链以静态 callback 身份（函数声明身份或 lambda 源身份）检测递归；克隆的新局部身份不能绕过检测。回调不得被存储为运行时值、返回为闭包、提交到 helper 或资源服务后延迟使用。

## 9. MC 上下文与 NBT 位置（C_CONTEXT、D_NBT）

MinecraftBuilder 作为 CodeBuilder 的结构化扩展，基础接口类型在 M_TYPES 中预声明，具体操作由 C_CONTEXT 添加并保持 Java SAM：

```java
interface MinecraftBuilder extends CodeBuilder {
    void executeAs(EntitySet targets, BiConsumer<MinecraftBuilder, EntityHandle> body);
    void at(PositionHandle position, Consumer<MinecraftBuilder> body);
    void rotated(RotationHandle rotation, Consumer<MinecraftBuilder> body);
    Ref<IntT> score(EntityHandle entity, String objective);
    Ref<IntT> score(ScoreHolder holder, String objective); // 全局/固定 holder
    PositionHandle position(double x, double y, double z);
    PositionHandle offset(PositionHandle base, double x, double y, double z);
    BlockHandle blockAt(PositionHandle position);
    Expr<BoolT> blockMatches(BlockHandle block, BlockPredicate predicate);
}
```

结构化 EntitySet/EntityHandle/PositionHandle/RotationHandle/BlockHandle/ScoreHolder/BlockPredicate 使用不可变模型与安全工厂，不能让 arbitrary raw command 成为上下文 API。额外方法按射线示例需要补充，必须有 validator 和 lowering 支持。MC 服务可经 `CodeBuilder.minecraft()` 返回当前 scope 的适配视图；此方法的接口骨架归 M_TYPES，实际实现归 C_CONTEXT。不可直接将基础 builder 强制 cast 成 MinecraftBuilder。

ExecutionContextId 分别描述 executor、position、rotation 的绑定。executeAs 只改变 executor；at/rotated 分别显式改变位置/旋转。运行时集合必须进入 executeAs 遍历，不能用 EntitySet 当单实体。self/实体位置句柄只在所属 scope 合法；ref 记录创建时的 executor，外部 ref 在嵌套 as 中不能按新 @s 重新解释。

全局/fixed holder 和同 executor 内位置可降低；跨 as 的实体引用若没有稳定身份表示必须报错，而不是默默固定到不正确实体。未提供通用实体身份捕获作为首期功能。ContextValidator 检查 scope/context 兼容，ContextLowerer 才生成 execute 子句，且不隐式增添 at。射线按显式位置逐步采样，可连续命中同一方块。

`NbtReferenceBinder.bind(location, environment): BoundLocation` 支持确定根与路径、已经固定的基址/索引；动态索引即使运行时确定，也必须在参数序言捕获一次并可由后端稳定表示，否则拒绝。NBT Ref 仍写原位置，不借临时内容再整体回写。普通属性只有显式位置访问器契约才可 ref；getter/setter 存在本身不构成契约。

## 10. 声明生成与 annotation 屏障（D_PROTOCOL、D_INTEGRATE）

```java
interface TemplateProcessor {
    DeclarationEdits expand(DeclarationContext context, TemplateView template);
}
interface TemplateView {
    TypeRef<?> type();
    List<FieldRef<?>> fields();
    List<MethodView> methods();
    List<AnnotationView> annotations();
    SourceSpan source();
}
interface DeclarationContext {
    Quote quote(); TypeService types(); DiagnosticService diagnostics();
    HelperService helpers(); ResourceService resources();
    DeclarationBuilder declarations();
}
interface DeclarationBuilder {
    <O, R> MethodEdit method(TypeRef<O> owner, String name, FunctionSignature<R> signature,
                             GeneratedMethodBody<O, R> body);
    DeclarationEdits edits(List<? extends DeclarationEdit> edits);
}
@FunctionalInterface interface GeneratedMethodBody<O, R> {
    void build(MethodBodyContext<O> context);
}
interface MethodBodyContext<O> {
    CodeBuilder builder();
    TypeRef<O> owner();
    Ref<O> self();
    List<? extends MacroInput> parameters();
}
```

GeneratedMethodBody/MethodBodyContext 归 D_PROTOCOL 的 api/Declarations.kt，M_TYPES 不依赖它们；D_INTEGRATE 创建实际方法 owner 和接收者绑定，M_VALIDATE/M_LOWER 消费生成体。`method` 首期生成实例成员；owner 必须与当前 TemplateView.type() 的规范类型身份一致，不能向任意类型添加成员。signature 仅描述显式参数，不把 self 算入 parameters。每个方法构造时创建独立方法根作用域，self 的类型为 O，绑定到未来调用者的原实例位置，字段引用通过 `builder.fieldRef(context.self(), field)` 建立；验证 field 属于该 owner/合法继承成员且可访问、可写。self/参数仅在该方法根作用域及子作用域有效，不能跨方法复用、作为 Ref 逃逸或捕获到 helper。非 void 方法体用 yield 产生方法结果，D_INTEGRATE 将它归属该方法出口；不借用 annotation 调用者的返回区域。GeneratedMethodBody 立即消费，树不保存宿主闭包。

```java
// owner 可由 template.type() 推断为捕获的 O；hp 是已验证的该模板 int 字段。
static <O> MethodEdit resetHealth(DeclarationContext ctx, TypeRef<O> owner,
                                FieldRef<IntT> hp, FunctionSignature<VoidT> noArgsVoid) {
    return ctx.declarations().method(owner, "resetHealth", noArgsVoid, body -> {
        CodeBuilder b = body.builder();
        b.assign(b.fieldRef(body.self(), hp), b.intLiteral(0));
    });
}
```

视图仅投影已完成字段和成员，不暴露可修改 scope 或原 Function；方法生成体使用上述结构化代码和显式 self 句柄，不能要求作者构造 ANTLR。DeclarationEdits 首期只允许新增方法、必要的内部声明、白名单元数据。禁止替换/删除既有 body、任意重排继承关系。所有新增成员先在 detached declaration scope 中检查名称、签名、访问和继承约束，再整体提交；多个处理器的已提交编辑对后续处理器可见，单次失败不留下半个 edit。

TemplateProcessorRunner 的步骤固定：索引所有源声明 → 完成父图/字段类型和旧 pending annotations → 按源文件/声明/输入 annotation 次序运行新处理器一次 → 验证并提交生成签名 → 刷新生成成员的声明契约/可调用索引 → 检查源方法和生成方法体 → 降低。不能在遍历第一个文件 annotation 时就检查函数体；必须等所有文件签名完成。生成 annotation 仅作为元数据，不递归触发新处理器。

新处理器通过现有 JAR 类加载及显式处理器映射注册（annotation 规范名 → public 无参 TemplateProcessor 类）；可用实现 TemplateProcessor 的旧 DataTemplateAnnotation 作为兼容 adapter，但新 `expand` 的输入仍是只读视图。具体映射入口由 D_INTEGRATE 在 namespace 类注册中提供，并与旧 Annotation.build/forDataTemplate 的执行去重：一个输入 annotation 的新处理协议执行一次，不同时两次应用。

score 映射示例：int 对应整数分数，bool 写 0/1，读取非零为真；缺失分数读零（不能依赖失败 get 留下旧临时值）。重复目标映射或不支持字段类型在发命令前诊断；生成 load/save 本身的数据读取/写入计入报告成本。处理器失败时资源、helper、声明均回滚。

## 11. 任务图的必要调整和交付切分

下列是明确的协调请求；本文没有修改任务图或源码。

| 原节点 | 调整 | 理由 |
|---|---|---|
| M_TYPES | 增加 Quote.kt、CodeBuilder.kt、MacroContext.kt 接口骨架与 expand/CodeArena.kt；公共函数/签名/源位置/服务类型归对应基础文件 | 后续 worker 不必重复声明同名类，且 Java API 可先独立编译 |
| M_BUILDERS | 公共接口已在 M_TYPES；增加 expand/DefaultQuote.kt、DefaultCodeBuilder.kt | builder 实现不 import 会话，解决依赖环 |
| M_SESSION | 依赖增加 M_BUILDERS；增加 CompilerTransaction.kt、默认 context/services 实现路径 | 事务不能塞进 API 接口或只恢复命令列表 |
| R_ENTRY | 增加 api/ParamMode（可位于 Inputs.kt）、expand/MacroFunction.kt 或等价独立描述符，以及 JAR/库加载注册的实际接点 | 新宏不可冒充 eager NativeFunction；序列化/加载时也要能找到入口 |
| R_SYNTAX | 生成代码目录由构建生成；将所有 normalArgs 消费点列入检查范围，必要时更新旧 visitor 与 DeclarationBinding/PrimitiveCompiler 的参数适配 | normalArgs 结构变更影响远多于单个宏 visitor；旧语法和普通调用不能失效 |
| R_BIND | 拆为 `R_SOURCE_EXPR`（SourceCodeBinder/BindingEnvironment/源位置与纯表达式/位置绑定）与 `R_CALL_BIND`（候选+模式序言+事务调用集成），原 R_BIND 为汇合验收 | 当前任务把无副作用源前端与整条执行链混在一个小叶；两者有可独立测试的强接口边界 |
| M_LOWER | 可拆基础树到后端计划与 CompilerTransaction/legacy bridge 两叶；后者需 StorageAccess、Function、namespace staging 的窄接点 | 当前全局可变状态使“无部分提交”显著超出一个 lowerer 文件；不能靠拷贝 commandList 宣称实现 |
| R_CONFIG | 增加 Project.kt 配置读取路径与独立 MacroExpander.kt（宜单独 `R_EXPAND` 叶） | ProjectConfig 无法自己读取配置；递归展开不是 diagnostics 的职责 |
| C_INLINE | 增加 `R_SOURCE_BODY` 叶负责 lambda/静态函数 body 的纯语句绑定；依赖 R_SOURCE_EXPR；C_INLINE 只消费 BoundCallback | 原图没有实际承接源 callback body→不可变树的实现任务 |
| C_CONTEXT | 增加 API 上下文句柄定义和 builder 适配实现路径；可分上下文模型/验证与降低两个叶 | EntitySet、self、位置与射线 block 查询不能仅有方法外壳 |
| D_INTEGRATE | 增加 MCFPPAnnotationVisitor.kt、Project.kt、io/MCFPPFile.kt、Namespace.kt/显式处理器映射接点；图依赖共同声明屏障 | 当前 writePaths 不包含真正 annotation 调度/多文件屏障，继承 worker 也会改这些点，需串行集成 |
| N_VALIDATE / 新增 N_ABI | N_VALIDATE 统一注册校验；协调者已新增 N_ABI 恢复 ValueWrapper 与 NativeFunction 双协议 invoke | 编写时 ValueWrapper 缺失；恢复正在进行，不能由简单数量公式覆盖原计划兼容目标 |

拆分时保留现有 35 节点的验收 ID，新增叶依赖边后由原节点汇合验收；不要通过删减原目标使“小任务”表面完成。协调者为重叠文件顺序安排接入，所有 worker 保留已有未提交修改。

## 12. 原计划与全部 35 节点的验收映射

原计划 1–12 节均有明确归属：

| 原计划节 | 本契约位置 | 实施/验收节点 |
|---|---|---|
| 1 目标与边界 | §1 | DOC_API、V_FINAL；贯穿全部实现 |
| 2 当前依据 | §1、§11 | N_VALIDATE、M_CONTRACT、R_BIND、D_INTEGRATE |
| 3 对外 API | §2、§3、§5、§6 | M_TYPES、M_SESSION、R_ENTRY |
| 4 最小语法 | §6、§8 | R_SYNTAX、R_BIND、C_INLINE、C_RETURN |
| 5 Quote/组合 | §3、§4、§6、§8 | M_BUILDERS、M_VALIDATE、R_BIND、C_INLINE |
| 6 上下文/资源 | §5、§9 | M_SESSION、C_CONTEXT、D_NBT |
| 7 annotation | §10 | D_PROTOCOL、D_INTEGRATE、D_SCORE |
| 8 Kotlin | §2、§4、§6、§1 的兼容差异 | N_VALIDATE、N_KOTLIN、N_EXAMPLE、M_BUILDERS |
| 9 编译流水线 | §1、§5–§7 | M_LOWER、R_BIND、R_CONFIG；建议新增纯绑定/展开叶 |
| 10 分阶段交付 | §11、下表 | 图中全部实现与示例节点 |
| 11 正确性/性能 | §13 | V_REGRESS、V_COMPARE、V_GAME |
| 12 完成标准 | §13、全部节点证据 | DOC_API、V_FINAL |

| 图中 ID | 具体交付/本契约关系 | 必须保留的验收接点 |
|---|---|---|
| I_CONTRACT | 独立继承契约，作为类型/字段只读视图前提 | 不在本任务重定义继承规则 |
| N_VALIDATE | 旧协议共享验证，§1/§6 | namespace/compound 一致；Java 与当前 ABI 保持 |
| M_CONTRACT | 本文 | 公共签名、纯绑定、会话/验证/降低边界和35节点映射 |
| I_GRAPH | 父图声明完成 | 新类型绑定不得绕过循环/顺序检查 |
| I_FIELDS | 稳定独立字段及 annotation | TypeRef/TemplateView 投影规范字段，不能共享可变 annotation |
| I_METHODS | 共同候选/重载 | MacroCallResolver 与 source binder 使用统一类型选择规则 |
| I_OBLIGATIONS | 完整声明的 override/abstract 校验 | 生成方法提交也经过相同契约 |
| I_CONSTRUCT | 构造和 super | R_BIND 依赖其完成；纯绑定不能执行构造器 |
| I_LIBRARY | fresh .mclib 继承往返 | 新声明视图的跨库身份稳定且兼容 |
| N_KOTLIN | Java/顶层/object/companion fixture | 相同元数据和实际调用结果 |
| N_EXAMPLE | Kotlin JAR 示例工程 | 固定兼容工具链，实际通过现有配置加载 |
| M_TYPES | §2/§3 基础句柄与接口骨架 | Java 可调用、不可变、类型/源位置/身份 |
| M_BUILDERS | §4 builder/DSL/卫生 | Java/Kotlin 同树、重复 append 局部隔离/自由绑定保持 |
| M_VALIDATE | §7 预验/终验/规范打印 | 类型错误、越界、会话缓存/句柄逃逸均拒绝 |
| M_SESSION | §5 服务/事务/预算 | 资源/辅助函数回滚、稳定命名、64/100000 |
| M_LOWER | §7 lowering plan 和后端 bridge | 真正命令与模拟执行；失败无部分提交 |
| R_ENTRY | §6 新注册协议 | public static、wrapper/返回、模式同签名冲突 |
| R_SYNTAX | §6 显式调用实参结构 | ref/lambda 正反语法；旧消费点回归 |
| R_BIND | §6 纯绑定与 orderedPrelude | 副作用左至右一次，未用值保留，ref 别名/CONST 错误 |
| R_CONFIG | §5/§7 配置与 dump | 参数实际读取、递归/预算链、重复编译输出稳定 |
| R_DAMAGE | damage 示例 | 同库适配变量/score、下限正确、无参数区复制回写/宏调用栈 |
| C_INLINE | §8 目标签名/捕获/卫生插入 | 两次 callback 局部隔离、捕获可见、递归拒绝 |
| C_RETURN | §8 区域出口 | 条件/循环早返、返回值、嵌套与 break/continue 边界 |
| C_CONTEXT | §9 结构化 MC | as/位置独立、全局外部 ref、非法跨 as/句柄逃逸 |
| C_REPEAT | repeat 的 void/显式累加器重载 | 只读局部快照、显式返回累积和局部早返真实运行，生成无编号分派 |
| C_RAY | 固定步长显式位置射线 | 停止/继续两策略；同方块重复命中按采样解释 |
| D_PROTOCOL | §10 只读声明/新增编辑 | 冲突/非法编辑、失败资源/声明一起回滚 |
| D_INTEGRATE | §10 声明屏障与处理器调度 | 多文件可调用、源码/annotation 次序、输入一次、生成注解不递归 |
| D_NBT | §9 确定位置模型 | 原位置读写、索引副作用一次、拒绝不稳定上下文/普通属性 |
| D_SCORE | §10 score load/save 示例 | bool0/1、非零读真、缺失零、不支持类型/重复映射报错 |
| V_REGRESS | 全量检查和真实回归 | MNI/常量/函数/float/继承，必要的标准库重建；报告真实失败/跳过 |
| V_COMPARE | 四案例三种实现对照 | 实际命令/搬运/NBT/栈/文件/体积/编译耗时 |
| V_GAME | 固定版本/负载的可复现实验 | 游戏运行只在可用环境实际测量，不虚构结果 |
| DOC_API | Java/Kotlin 教程和 API 独立实验版本 | 生成期/运行时、参数求值、context、展开查看与兼容规则 |
| V_FINAL | 原计划最终标准和继承审查逐项证据 | 现有修改保留；缺口如实记录及五轴自评 |

协调者在原35节点之外新增的 `N_ABI` 承担历史 ValueWrapper 与双协议 invoke；它与 N_VALIDATE 分开验收后汇入 N_KOTLIN。其实际调用 fixture 必须覆盖 readonly/normal/caller/return 的零与非零组合。

## 13. 测试接点与完成证据

实现 worker 使用图中对应正式测试文件；本契约不新增镜像式测试。以下测试须同时观察规范树/绑定结果与真实命令或模拟执行结果，不能只断言“编译无异常”。

1. **纯绑定**：在会执行副作用的实参、嵌套宏、索引、getter 和旧 MNI 名称上进行 source bind，命令/资源/scope/call graph/flow facts 无变化；BoundExpr 保存调用目标和源 span。预先设定命令哨兵并检查快照，不靠“没有当前函数”掩盖副作用。
2. **Java ABI**：真实 Java fixture 通过公共 API 构造 local/branch/while/call/inline，验证无需 Kotlin FunctionN/Unit、ANTLR、Var 或当前函数；Kotlin 同逻辑生成规范树相同。Java raw 泛型注入错误仍由 validator 拒绝。
3. **求值/别名**：`unused(effectA(), effectB())` 保留 A→B 各一次；同一 Value.expr 多次读取只执行原参数一次；两 REF 指向同位置观察顺序写；动态索引与 VALUE 混排在正确位置捕获；CONST 动态和 ref 隐式转换拒绝。
4. **卫生/生命周期**：同块 append 两次只克隆内部声明；局部捕获保持创建点值且拒绝写入，全局按声明身份实时访问并允许合法写入；sibling/child local、跨根会话及关闭 builder 使用拒绝。循环条件运行时重复求值而构造期宿主条件函数仅调用一次。
5. **递归/预算**：深度64合法、65失败，节点100000合法、100001失败；宏→宏、实参内宏、重复 append 节点计费；递归 callback 身份链不能靠克隆逃过；错误定位用户调用且没有部分结果。
6. **事务**：宏抛错、返回错类型、嵌套失败、validator失败、lowerer失败、旧 MNI 降低失败与声明冲突分别测试；命令、辅助函数、资源、scope、调用图、相关 flow facts 均无泄漏。失败后再编译同输入名称与干净执行一致。
7. **回调/context**：循环和分支中提前 return 仅退出当前 callback；嵌套返回值正确；跨 callback 的 break/continue 诊断；as 不改变位置，外部 fixed ref 保持，不能稳定表示的实体 ref 跨 as 报错。
8. **声明/NBT**：完整继承字段视图、输入顺序一次执行、生成签名可被其他源文件调用、生成注解不递归；失败编辑不残留；NBT 原位/别名/索引一次；score缺失后不能沿用之前非零临时值。
9. **兼容与结果**：Java/Kotlin 旧入口、现有常量/函数/浮点/编译期函数保持；fresh .mclib 与重复编译稳定；damage/repeat/ray/score 四案例有生成命令和执行断言。

性能报告将手写专用、运行时适配、新 MNI 三版分别实际编译，记录命令数、参数搬运、NBT、额外栈、文件数量/体积和编译时间；命令数不等同于速度。游戏耗时必须标注固定 Minecraft 版本、负载、预热、重复次数和真实环境；无可用运行环境时交付可复现说明并明确尚未测量。射线分别记录采样次数和不同方块数。

首期 API 验收后以独立实验版本号公开；DOC_API 定义版本边界，V_FINAL 对原计划12节逐项提供当前源码、实际测试与生成报告证据。本文仅定义这些可检查的合同，不宣称任何实现或性能验收已经通过。

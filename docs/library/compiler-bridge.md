# 发布库的编译器桥接与 Mcfpm 会话接口

本契约配套 [format-v1.md](format-v1.md)，落实已批准的预编译库提案 revision 3。当前生产入口使用正式 v1 库和 JDK Map 会话 ABI；下文的内部接口示意用于说明设计边界，实际类与方法以生产源码为准。发布格式保存中立 AST，不保存 Kryo 对象图或 ANTLR context。

## 使用入口与当前验证范围

完整操作见 [本地 Maven 作者/消费者样例](../../examples/libraries/README.md)。库作者在 `mcfpp.toml` 配置 `[library]`，导出 `bin.mclib`、`module.json` 和模块资源，再用 Mcfpm pack/register 发布。消费者通过 MCFPP CLI、`mcfpp-packages.json` 和 `mcfpp.lock` 管理依赖。CLI 在进入编译会话前准备依赖；编译会话只消费已准备的锁图和缓存。

MCFPP 使用 JDK 21。当前构建以 `runtimeOnly` 装配 `moe.afox.mcfpm:core` 与 `mcfpp-adapter`，其传递依赖含 `model`；本地开发先发布这三个 SDK 模块到 Maven local。SDK 构建 toolchain 为 JDK 25，发布字节码目标仍为 Java 17；它不编译依赖 MCFPP jar。不同 Kotlin 版本通过 Java 外观和 JDK 类型交互。

本地 Maven 样例脚本已实际完成 SDK install、移走作者源码和当前 fat JAR 消费，检查了私有方法生成、静态资源和 lock 不变。标准库也已通过实际源码生成，以及显式目录和内嵌 `%std` 消费验证；这些结果覆盖已执行场景，不代表未来 reader 版本已经验收。

预编译保留可分析正文，消费点已知值仍按当前编译策略优化。发布后的兼容方向为新 reader 读取已发布旧格式，旧 reader 对新格式/必需语义明确拒绝；未发布 Kryo 缓存重新导出，不提供历史对象图兼容入口。内建 `DataObject` 使用明确的 builtin 身份；同名 data/object companion 依靠声明 ownerPath 和 companion 关系区分，不能仅按显示名称合并。类型与选择器的 wire 编号及内部表示规则以 format-v1 为准。

`NativeSelectorKind` 的 wire 值 6 保存 reference helper 中的 `NEAREST_ENTITY`（内部 `@n`）；它与源码表达式的 `SelectorKind` 表分开，后者当前只有值 1–5，不能据此前者宣称新增源码选择器语法。

### 按 Minecraft 目标使用标准库

项目配置的 `version` 决定标准库的 Minecraft 目标。默认 `stdLib` 为 `["%std"]`；例如：

```toml
schema = 1
version = "26.3"
stdLib = ["%std"]
```

`%std` 优先读取发行资源 `datapack/targets/<目标版本>/` 下实际编译的 `bin.mclib`、`module.json` 和 `datapack/<module>/data/...`。各受支持目标使用各自从标准库源码编译的 bundle；根目录 bundle 仅作为目标完全匹配的回退。读取器核对库内目标，不能通过改目录名或 manifest 把另一个目标的产物当作当前标准库。目标 bundle 缺失时，按诊断为该目标重新构建标准库或配置自定义 bundle。

`stdLib` 也接受相对项目根目录的自定义库目录、ZIP/JAR 或 `bin.mclib` 路径，例如 `"stdLib":["libraries/stdlib-26.3"]`；目录应同时包含 `bin.mclib` 和 `module.json`。`"stdLib":[]` 不加载标准库，`-ignoreStdLib` 跳过整个列表。上面的作者/消费者例子显式跳过标准库，便于独立复现库安装流程。

库资源按所属模块的逻辑路径登记；选择内嵌目标 bundle 时只读取该 bundle 的资源，不混入其他目标。普通函数及可分析正文在消费会话中生成；静态资源随模块复制，冲突应报告而不是靠复制顺序覆盖。导出自己的库时保留自己的初始化和资源归属，依赖库以引用和依赖身份保留，不把整个依赖的实现再次归为本库。

读取库字节不会执行语言初始化正文。导入后，初始化记录按库依赖关系排序并编译为加载函数，在同一会话中排在库的 LOAD 回调之前；重复准备不会重复登记这些成员。消费方无需手工调用库初始化函数，也不要通过额外复制加载标签来重复调用它们。发布格式保留中立正文及这些初始化关系；新 reader 维持读取已发布旧格式，旧编译器对不支持的新格式或必需语义明确拒绝。未发布的 Kryo/ANTLR 快照需重新导出，没有兼容承诺。

## 1. 当前入口与必须保留的能力

当前 `Function.ast`、`GenericFunction`、`GenericExtensionFunction`、`InlineFunction`、`CompileTimeFunction` 和构造器依赖 `CurlBlockContext`；`GenericDataTemplate.context` 依赖 `TemplateBodyContext`；字段 `preInit` 保存 `ExpressionContext`；`FunctionParam.defaultContext` 保存 `ValueContext`。`MCFPPImVisitor`、`MCFPPExprVisitor`、`DeclarationBinding`、`TypeSyntaxResolver`、lambda binder、context/entity/ref callback 和 MNI SourceBodyBinder 还有大量直接 context 消费者。TypedIR 仅覆盖其中部分语义，不能作为库正文全集。

还有两条容易漏掉的路径：`CompilerSourceIndex.sourceSlice()` 从原始 `inputStream` 截取静态函数正文；`SourceBodyBinder.parseCallbackBlock()` 会重新解析静态 callback 的正文文本，部分 lambda 用 `FrozenCallbackSyntax` 恢复 context。仅替换 FunctionInfo.context 会令导入函数作为 MNI callback 时失败，或暗中退回源码解析。新桥接必须同时接管这些入口。

## 2. 中立模型与会话 handle

在 `top.mcfpp.library.model` 定义格式无关的不可变 DTO（LibraryModel、DeclarationRecord、TypeRecord、ConstantRecord、EnvironmentRecord、SemanticNode、BodyRecord）。它们不引用 ANTLR、Kryo、Var、MCFPPType、Scope、Class、Method 或 ClassLoader。v1 codec 只依赖这些 DTO 和 JDK IO；未来解码器输出同一逻辑模型，必要时先保留历史 operation 语义再转换。

在 `top.mcfpp.library.bridge` 定义编译会话对象：

```kotlin
data class BodyHandle(val library: SessionLibraryId, val body: BodyId)
data class NodeHandle(val body: BodyHandle, val node: NodeId)

interface BodyRepository {
    fun body(handle: BodyHandle): BoundBody
    fun node(handle: NodeHandle): BoundNode
    fun callback(handle: BodyHandle): StructuredCallbackBody
}

interface DeclarationBodyAccess {
    fun function(function: Function): BodyHandle?
    fun default(parameter: FunctionParam): BodyHandle?
    fun initializer(owner: DataTemplate, field: DeclarationId): BodyHandle?
    fun template(owner: GenericDataTemplate): NodeHandle?
}
```

BoundBody 是原始不可变正文加绑定后的会话 declaration/type/environment 索引；不会把变量的值写回共享树。源文件也通过 `SourceTreeCapture` 产生相同语义树/handle；迁移期源文件仍可保留真实 parser context，且可按需捕获。所有 BodyHandle/NodeHandle 只在所属 CompilationSession 内有效，session close 后拒绝使用。

声明创建先分配 shell，后填充签名与绑定：`(PackageKey, local D) → 唯一 session declaration`，泛型参数是 `(owner, role, index)`；导入不同库的 private/anonymous 参数必须分配不同会话身份。已有 `TypeId.Opaque` 仍可作为实现细节，但从不恢复发布字节中的某个全局计数器。引用依赖通过完整 package/key 定位后桥接到现有模型；语言命名空间合并仍按现有访问/重载规则，不擅自允许多版本同包共存。

## 3. 可逐步落地的 context 投影

首版提供手写的 `CurrentSyntaxProjection`，从语义节点直接构造**当前编译器生成的** context。它是会话适配器，不是发布协议，不把 ANTLR 的结构或数字反写到库。这样可复用现有语义分析和生成逻辑，逐段把消费者改成直接读中立树。

```kotlin
interface CurrentSyntaxProjection {
    fun block(body: BodyHandle): mcfppParser.CurlBlockContext
    fun expression(node: NodeHandle): mcfppParser.ExpressionContext
    fun defaultValue(body: BodyHandle): mcfppParser.ValueContext
    fun type(node: NodeHandle): mcfppParser.TypeContext
    fun template(node: NodeHandle): mcfppParser.TemplateBodyContext
    fun lambda(node: NodeHandle): mcfppParser.InlineLambdaContext
    fun origin(context: ParserRuleContext): NodeHandle?
}
```

投影按节点表显式分派：new 对应的生成 context；设置 parent、children、start/stop；创建 TerminalNode；使用本次生成器的 `mcfppLexer.ADD` 等常量。禁止 `Class.forName`、反射 class 构造器、记录任意 ruleIndex 或“把孩子列表照搬回来”。Binary 要按当前 grammar 的优先级层次包裹并在必要处生成 Group，不重新关联原树；Identifier/member keyword、类型参数、lambda 尾表达式保持语义分类。

必须填充生成 accessor 以外的显式字段：各二元表达式的 `op` 列表、`range.num1/num2`、`nbtKeyValuePair.key`、annotation 的 `id` 等。只 addChild 不填这些字段会让 visitor 得到不完整结果。语句块、执行上下文、模板成员的访问修饰符/override/abstract、getter/setter 三种形式都按当前 grammar 映射。`LEGACY_ACCESSOR_SHIM`、旧 `ref expression` 和 `(args) =>` 从不生成。

默认值当前 grammar 是 `value`，不是任意 expression；投影 `defaultValue` 必须检查持久化根确实能表示当前已支持的 value（含带表达式的 list/compound/multiline string），不能随便把 expression cast 成 ValueContext。若未来语言扩大默认值语法，应一起更新捕获、投影和格式版本判断。模板字段 initializer 保持完整 ExpressionContext。

每次实际调用/实例化/回调绑定获得新的 context/token 树，避免 parent、token 或 scope 的可变状态被不同调用共享；可缓存不可变 DTO 和会话绑定索引。投影 context 关联 side table：`context → NodeHandle + bound names + semantic constant + diagnostic origin`。Name/TypeName 中已绑定的声明应由类型/名字查找入口优先读取 side table；否则同名 dependency namespace 或消费方 imports 会改变作者绑定。需要实参后才决定的 Lookup 沿用保存的候选集正常匹配，不能绑定到消费方新增的同名声明。

### 文本和位置的兼容处理

现有 `.text`、`startIndex`、source substring 以及 API SourceSpan 仍可工作，但语义不依赖原源码。投影器从树生成会话内 canonical display buffer，并在构造 token 时明确赋予 text、UTF-16 start/stop、line/column、tokenIndex、TokenSource/CharStream；不运行 lexer 或 parser。关键字和标识符之间保留显示分隔符，命令/多行字符串使用保存的实际 TextPart 值，不能由 `.text` 连接猜测原始空白。这个 buffer 仅用于显示/切片兼容；不是发布必须项，删掉原 Debug/源码文件后仍可编译。

若某个常量没有合法源码字面量（例如计算后的特殊 IEEE bits），由 side table 提供 ConstantRecord，表达式入口直接恢复已验证闭合值；不能用不等价的显示文本重建其值。原源码诊断位置由 Debug 或 package/key/node 单独提供，不混用 canonical display offset 和真实源文件 offset。所有按 substring 验证的 callback 片段必须使用同一 projection buffer 坐标系。

投影是兼容工具。禁止“打印语义树成 MCFPP 字符串→Parser→context”的中间步骤；禁止 FrozenCallbackSyntax 所用的 `Class<ParserRuleContext>`、token type 数字进入发布格式。

## 4. 正文消费接入点

增加一个 `Function.bodyHandle`，保留 `ast` 作为源文件/投影的会话属性。`compileBody` 先通过 `DeclarationBodyAccess` 获取绑定正文：已有源码 ast 可以直接走旧 visitor，库正文使用 projection.block(handle)；两者在现有 `restoreDeclarationEnvironment`、EffectAnalysis、参数准备和 visitor 调用的同一位置进入。函数拷贝、specialization、bindActualCall、构造器分阶段初始化必须复制 handle，不能仅复制 ast 后丢原始所属库。

| 消费路径 | 首版接入行为 |
|---|---|
| 普通函数与 PrimitiveCompiler | 无论预生成实现是否存在均保留 BodyHandle；正常 runtime body、returnKnowledge/TypedIR 分析读相同正文，已知/未知参数沿用当前策略 |
| readonly/generic/extension | 签名的 dependent type 和默认值通过 type/default projection 解析；`SpecializationPolicy` 复制 bodyHandle；owner/environment 不从调用方继承 |
| inline/compileTime | 保存明确 execution 类别，创建相应 Function 子类；调用同一 BodyRepository，不能恢复成普通 Function |
| constructor / typeAs | 用户正文和 intrinsic 分开；父/子初始化顺序复用现有分阶段实现；同一 initializer 不因 metadata + template body 出现两次而重复执行 |
| generic/anonymous template | template projection 从有序 MemberDeclaration + D/B 构建 TemplateBodyContext；仅有参数绑定后才能确定的头/父表达式继续通过树解析；没有 `parentID` 文本再解析回退 |
| default / initializer | FunctionParam、preInit 加 handle；闭合 snapshot 是可选优化，缺 snapshot 时仍可用正文按声明环境计算；先前参数与 this 的绑定顺序沿用现有实现 |
| lambda / named-function callback | Lambda 节点保存参数 D、implicit-it 区分、body、tail、捕获 D；现有 LambdaBinding/LambdaLowering 接受新投影和其 side table；静态函数 callback 复用目标 BodyHandle |
| context / entity / ref read callbacks | ContextCallbacks、EntityReadCallbacks、ReferenceReadCallbacks 由 CallbackBodyProvider 取得树/投影；借用和执行上下文在调用会话创建，不能从库反序列化 |
| property / operator / annotation | getter/setter 绑定独立 target D，原生描述与普通函数分开；运行既有访问/运算符语义；已完成声明注解效果先还原后校验，pending 部分正常阶段执行 |
| MNI SourceCodeBinder/SourceBodyBinder/SourceDefaultBinder | 完整声明目录准备后创建 MNI key/catalog，正文与回调读取 structured provider；closed defaults 转 immutable literal facts；不在库类型树上重新解析字符串 |

参数求值、副作用、StorageAccess、alias/ref、ContextBorrowing 的事实仍由现有 binder/visitor 在实际调用建立。持久化树表达原操作和顺序，不把优化证明当成语义事实。普通常量实参不自动成为泛型 specialization key；现场优化承诺是在相同现有策略下源码和库具有同样可分析正文与结果。

### MNI 的结构正文入口是必需项

在编译器内部给 `SourceFunctionBody`/`SourceCallbackSyntax` 对应的准备快照关联一个 opaque session body key，由 `StructuredCallbackBodyProvider` 映射到 BodyHandle 或 Lambda NodeHandle；公开 MNI API 可以继续暴露文本作为展示视图，不让 plugin 获得内部上下文对象。

```kotlin
interface StructuredCallbackBodyProvider {
    fun functionBody(functionKey: SessionFunctionKey): StructuredCallbackBody?
    fun lambdaBody(callbackKey: SessionCallbackKey): StructuredCallbackBody?
}
// StructuredCallbackBody = projected statements + distinct tail + origin;
// each access creates fresh contexts owned by the current binding.
```

`CompilerSourceIndex` 构造 sourceBody 时同时登记 key，获取文本可以用 projection buffer。`SourceBodyBinder.parseCallbackBlock` 必须先按 key 查询 provider：静态函数拿其 B，lambda 拿其 Lambda 节点；成功后直接构造 CallbackBody，不调用 parse。库来源缺 structured key/provider 是 `LIB_BODY_UNSUPPORTED`，不得走文本回退。源文件当前的旧 parse 回退可暂留，但新的 capture 应同样登记结构正文。嵌套 lambda 的 capture discovery、冻结/再次绑定和 collection callback 都必须传递子 NodeHandle；不能在最外层接好 provider 后又对嵌套正文调用 parser。

准备快照的 `sourceSpan` 和 `sourceText` 应来自同一 projection 坐标系；用户诊断通过 origin 再映射 Debug。`FrozenCallbackSyntax` 仅保留为会话内源码能力，导出时必须转换为中立 Lambda 节点。默认值、类型头和模板父表达式的旧 `parseFromString` 路径也要获得 type/node handle，优先 `TypeSyntaxResolver` 的 context/structured 入口；不得把这些字符串保存在库里作为唯一表示。

## 5. grammar 与 io/info 的覆盖分工

以下是当前 `mcfppParser.g4` 的全部语义规则族与持久化表示的对应关系。分隔符、注释、括号样式等不改变语义的 token 不逐个入库；文档进入可选 Debug。

| grammar 规则族 | 保存方式 |
|---|---|
| compilationUnit / namespace / import / topStatement / typealias | E、namespace/alias D、顶层初始化 B；保留 import alias/from/wildcard |
| declarationName / readOnlyParams / normalParams / parameter lists | 参数 D 的 owner/role/index/variance/default B；省略 readonly 类型等价 Type |
| function / inline / compileTime / native / extension declarations | execution、receiver、NativeBinding、独立 B；普通函数也有 B |
| data/object/compound/extend/templateBody | data D、Parent type/expression、ordered members、TemplateBody 节点 |
| templateMember / field / accessor / getter / setter / constructor / operation | D 的可见性/flags/initializer、独立访问器与运算符 target、用户/原生/intrinsic 分类 |
| enum / enumMember / annotation / annotationArgs / javaRefer | enum ordinal+NBT 常量、AnnotationRecipe、显式 JVM descriptor |
| expression / conditionExpression / all precedence / cast / unary | Binary/Unary/Group/Cast；条件不需要独立运行时操作，但投影生成正确 rule |
| primary / var / suffix / selector / propertyOperator | Name/This/Super/TypeValue/Index/Member/PropertyFilter；空 suffix/group 明确保留 |
| arguments / readonly args / mixed call args / trailing lambda | Call + 参数类别/顺序；map/set member keyword；legacy ref 诊断形式不导出 |
| inlineLambda / lambda params/body/tail | Lambda D/B、implicit-it 标记、tail、捕获绑定；legacy Lambda/shim 不导出 |
| statement / field modifiers / assignment / control / return | Local role/modifier、Assign、Expression、Break/Continue/Return/Empty |
| if/else-if/else / while/do / foreach / try-store / execute | 对应语句节点，保持单语句 block 和块体等价语义、execute 左侧路径 |
| orgCommand / interpolated orgCommand content | 有序 TextPart；保留纯文本和表达式边界 |
| typeBody / builtins / VecType / entity constraints | T 与 TypeName/Vector/Container/Entity 等类型语法节点 |
| className / type application / union / unionTemplate / anonymous data / function type | TypeApply / UnionType / IntersectionType / AnonymousDataType / FunctionType |
| value / nbt scalar / list / compound / typed arrays / range / coordinate / target selector | C 或对应表达式节点；list/compound 内表达式不提前当纯常量 |
| multiLineStringLiteral / text / quotes / interpolation | 实际解码文本与 Interpolation 的 TextPart 序列 |

旧 io/info 的职责迁移如下：FunctionInfo 不能只按 Native/Generic/else 丢失 inline/const 类别；ParamInfo 的 defaultContext/defaultTypes 迁到 default B、C、T；DataTemplateInfo 的 parentExpressions/context/preInit/embeddedParents 迁到 Parent/TemplateBody/initializer/私有 D；FieldInfo 的 properties/operators 按独立 D 引用还原；CompletedInheritedFieldInfo 与 DeclarationAnnotationsInfo 分别迁到完成效果和有 phase 的 A；EnumInfo/EnumMemberInfo、aliases、FunctionTagInfo、ReferenceHelperInfo、MacroFunctionInfo 均需显式 codec。GlobalFieldInfo 的全局 tags/scoreboards 不再直接打包，按 OwnershipIndex 过滤。`LibraryMetadata.reset`、type memo、project SourceIndex 和 helper registry 的生命周期归 CompilationSession。

## 6. 导入、导出与资源会话

以下为编译器内部接口；JVM adapter 外观见 §7。Result 必须能表达成功/失败，失败不能只有日志然后正常返回。

```kotlin
interface LibrarySource : AutoCloseable {
    val identity: ResolvedLibraryIdentity
    fun openBinary(): InputStream
    fun openResource(normalizedEntry: String): InputStream
    fun resourceEntries(): List<ResourceEntry>
}
interface LibraryImportSession : AutoCloseable {
    fun stage(source: LibrarySource): LibraryResult<StagedLibrary>
    fun bindAll(): LibraryResult<BoundLibraries>
    fun commit(): LibraryResult<ImportedLibraries>
}
interface LibraryExportSession : AutoCloseable {
    fun snapshot(selection: OwnedLibrarySelection): LibraryResult<LibraryModel>
    fun write(model: LibraryModel, sink: AtomicLibrarySink): LibraryResult<ExportedLibrary>
}
```

导入状态固定为 OPEN → STAGED → BOUND → COMMITTED → CLOSED；错误转 FAILED，再 close。stage 完成解码和局部校验，在私有 workspace 注册 shell；stage 可以调用多次。bindAll 在**全部依赖和本地 includes/jars/标准库都 stage 完成后**统一解析声明、类型、父类、环境、注解、MNI 和 helper；最后检查 namespace 冲突（包括 enum/alias）、资源和 target。commit 一次性发布整组库到编译会话。任一步失败撤回整组变更，禁止提交其中一个成功库后把失败当作 warning 继续编译。循环声明可用 shell；包依赖顺序仍由 Mcfpm 锁图确定，不在编译器里重做解析。

导出先完成当前声明索引，基于 OwnershipIndex 选择本包全部声明与私有实现闭包，再捕获树/类型/常量/资源。外库对象只能变成 dependency refs。生成的 actualCallBody、compiledFunctions 缓存、累积标签成员和他库 helpers 不能误选为本包所有。文件与归档通过 AtomicLibrarySink 同目录临时写入及成功后替换；写失败关闭句柄、删除临时内容并保留原有效产物。

LibrarySource 不把 openBinary 的流所有权交给调用方永久持有：每次返回新流，由读取者关闭；source 本身在 session close 时关闭归档资源。资源预检完成后才激活。ResourceRegistry 用规范化最终输出路径 + owner + digest 登记，负责屏蔽重新生成声明对应的预生成资源、合成 tag contribution、诊断不同来源冲突，不能靠最后一次 copy 覆盖解决。session close 幂等，清理 source/projection/metadata/MNI/type/helper 缓存、临时资源目录与 plugin loader；不删除 Mcfpm 已校验缓存。

## 7. 独立的 JVM adapter ABI

MCFPP 使用 Java 21；Mcfpm SDK 维持 Java 17 字节码，不能静态依赖编译器的 Kotlin implementation ABI。MCFPP 发布公共外观 `top.mcfpp.api.library.LibraryCompilerApi`，Mcfpm adapter 通过反射加载它；外观的字段/方法只用 JDK 17 可见类型。编译器发行包通过 runtimeOnly 携带 Mcfpm core/adapter 及其传递 model 依赖；SDK adapter 不依赖 MCFPP 生产 jar 编译。

外观是 public final Java 类，具有以下 **public static** 方法；实现可以委托 Kotlin，JVM 方法签名不能依赖 Kotlin object/Companion/default 参数：

```java
int apiVersion();                                      // 返回 1
Map<String,Object> capabilities();
AutoCloseable openSession(Map<String,Object> request);
Map<String,Object> compile(AutoCloseable session);
```

capabilities 返回 `apiVersion:Integer, formats:List<Integer>, semantics:List<String>, runtimeAbis:List<String>, minimumJava:Integer`。adapter 先用 ClassLoader 查找类和精确方法签名，再检查 apiVersion/minimumJava/capabilities；缺失或更高 ABI 返回明确 `MCFPP_INTEGRATION_FAILED`，不探测 Project 的成员作为回退。MCFPP API 可以在后续 ABI 提供新入口，ABI v1 同样保持旧调用者可用。`AutoCloseable` 是 JDK 共享类型；adapter 不 cast 成编译器自有 session 类。

所有 Map key 是 String；值只允许 String、Integer、Long、Boolean、byte[]、Path、List、Map、ClassLoader 或 AutoCloseable（后两者仅位于下文指定字段）。返回的集合为不可变快照，输入在 openSession 时复制；不暴露 Kotlin Result/Function、enum、JSONObject、ProjectConfig、Function、Module 或 payload 的 Mcfpm 类型。所有必需字段缺失/类型错误抛 `IllegalArgumentException`，资源失败抛带 library diagnostic 的异常；compile 的可预期失败用结果 DTO 表示。无成功结果可以掩盖错误计数。

request v1 的字段：

| key | JDK 类型与含义 |
|---|---|
| `apiVersion` | Integer=1 |
| `configuration` | Path，消费者 mcfpp.toml |
| `libraries` | List<Map>，按锁图依赖顺序，见下表 |
| `lockedPackages` | List<Map>，完整锁定坐标/版本/直接依赖列表，供核对，绝不触发求解 |
| `plugins` | List<Map>：`coordinate:String,version:String,classifier:String,path:Path,sha256:byte[32]`，已按 Mcfpm 信任流程校验 |
| `resourcePacks` | List<Map>：坐标/版本/classifier/path/sha256，保持 Mcfpm 配对资源模型 |
| `datapacks` | List<Map>：坐标/版本/classifier/path/sha256，独立数据包模型 |
| `hostClassLoader` | ClassLoader，可选；必须能看到当前 MCFPP 公共 API |

libraries 每项为 `coordinate:String, version:String, classifier:String, sha256:byte[32], binary:byte[], moduleJson:byte[], resources:Map<String,byte[]>`。这与当前 `McfppLibraryPayload` 可直接转换；避免把 JVM17 adapter 和 JVM21 编译器都绑定到某个新的 DTO jar。resources 的 key 已是 format-v1 逻辑根路径。编译器仍核对内部清单，外层包校验与路径验证不能代替库语义校验。大载荷将来如需流式 ABI 应另行版本化；v1 不增加依赖字节大小的性能目标。

lockedPackages 每项为 `coordinate:String,version:String,dependencies:List<Map>`，dependency 项为 coordinate/version。当前一次编译同一 coordinate 只能有一个版本；出现不一致时诊断，不能按顺序静默选择。未安装、锁图缺失或下载缓存缺损由 Mcfpm adapter 在 openSession 前报告，编译不更新 lock，也不自动联网选择版本。

compile 返回 `success:Boolean, loadedLibraries:Integer, loadedModules:Integer, compilerErrors:Integer, diagnostics:List<Map>, outputs:List<Map>`。diagnostic 字段为 `code:String,severity:String,message:String,coordinate:String?,version:String?,classifier:String?,entry:String?,section:Integer?,record:Long?,byteOffset:Long?,declaration:String?`；severity=`error|warning|info`。output 为 `kind:String,path:Path`。success 仅当所有导入绑定、源码编译及输出成功且 compilerErrors=0。不要把 loadedLibraries 的尝试次数当作成功数量。

openSession 只建立独占会话、复制请求和准备资源；compile 运行一次。外观负责 `readConfig → init → stage builtins/local/locked libraries → bindAll/commit → 编译消费方 → 输出` 的确定顺序；不会要求 adapter 注册 `READ_LIB` 结束 hook。即使暂时由 Project 单例实现，会话也必须独占该实例并在 finally 清理；并发 openSession 应明确拒绝忙碌，不做交错修改。session.close 幂等；在 compile 异常/失败时同样彻底清理。

Mcfpm `CurrentMcfppCompilerAdapter` 保留 compile(config,libraries) 的上层体验，内部只解析本外观、构造 request、try/finally close 并转 McfpmResult；移除 Project.readConfig 签名猜测、stage ordinal/hook、LibBinReader 无结果调用和 Module 可变字段反射。异常解包保留根因与库定位，不把所有错误都变成“零库已导入”的成功结果。

## 8. CLI、plugin 类型身份与本地入口

`mcfpp install/build` 通过通用 JDK 类型门面复用 Mcfpm 的解析、缓存与校验。`mcfpp build --project mcfpp.toml` 自动准备依赖；有效锁保持已选版本，`--locked` 禁止改锁，`--offline` 禁止联网。生成的 SDK 清单位于 `.mcfpp`，原生锁保存为 `mcfpp.lock`。发行物以 runtimeOnly 装配 SDK core、repository-maven 和 mcfpp-adapter，SDK 不编译依赖 MCFPP；实际 MCFPP 进程使用 Java 21。

依赖提供者把已校验 FetchedGraph 转成 §7 DTO，统一送交外观。本地 includes、jars 和内置标准库继续使用同一 LibrarySource。启用本地 Java MNI 后，CLI 先调用生成的 Gradle 工程，再以其实际运行 classpath 创建共享公共 API 的宿主加载器；加载器在编译后关闭。

JVM plugin 沿用 Mcfpm 的信任与校验决定。正式路径以实际编译器 API 的 ClassLoader 为 parent，由编译器会话为已校验插件路径创建受管 URLClassLoader；禁止 plugin 夹带另一份 `top.mcfpp.*`/MNI API。独立使用 `TrustedMcfppPluginLoader` 时同样传入实际 compiler API parent，并由调用者关闭其返回的 loader。先加载 plugin/provider，再绑定 NativeBinding/MacroBinding/annotation codec；全部库类型准备完成后建立 MNI catalog。成功、失败和异常均清理会话拥有的 source、临时资源、metadata 和 loader，close 幂等；不关闭宿主公共 loader，不删除 Mcfpm 已安装缓存。

发布时本地 includes/jars 若包含需要分发的依赖，必须通过现有 Mcfpm migration 校验转成依赖或载荷；编译器不把作者机器上的绝对路径写进 manifest。配对资源包和数据包继续沿用既有 Mcfpm 输出登记器，库导出目录可交给 `McfppOutputRegistrar.registerDirectory(MCFPP_LIBRARY, classifier, directory)` 生成正式载荷。

## 9. 实现接缝与首个可运行切片

容器/声明实现只写 `library/model`、v1 codec、身份映射与隔离 registry；正文实现只写 capture/projection/body repository 和其测试，先用模型 fixture 独立编译；adapter 实现只依赖 §7 JDK ABI 和 format-v1 归档根，不等编译器内部类重构。共同枚举/tag 必须以 format-v1 为单一来源，不能各自取 ordinal。Function、Project、SourceBodyBinder 等共享生产文件的集成按明确文件所有者串行执行。

首个切片是带普通函数的库：捕获 Block/Local/Binary/Call/Return → 编码 → 独立 ImportSession 注册/绑定 → consumer 用已知与未知实参调用 → 现有 generator 输出。此时保留全部语法模型与明确 unsupported 诊断；尚未实现的节点不能被导出为“已完成可发布的 v1”。随后按上述表闭合 default/initializer、lambda/MNI callback、template/member/ref 入口，再迁移标准库。普通切片通过不等于全语言发布就绪。

正式冻结前需补齐的实施项包括：全部实际 builtin key 的显式登记、当前 annotation provider 的 portable codec、`ReferenceHelperRecipe.operandTypes` 类型描述符闭包按稳定 T[] 导出/导入后重建会话 typeId map、SourceIndex 各级结构 callback key、历史 v1 固定样本和跨库绑定/资源实际闭环。这些是实现任务，不能用文档存在或当前缓存测试通过代替。构建与完整集成由协调者统一安排，避免多进程写同一 Gradle 输出。

## 10. 注解 codec 的实际迁移切片

声明的注解实例、父注解和可变容器当前通过 `Property.copyAnnotations`/Java serialization 保留别名；不能在新格式把所有扩展注解统一拒绝后就宣布能力迁移完成。实现 `PortableAnnotationCodec` 注册表，以 `(provider package, JVM class name, schema, version)` 查找。codec 提供 `capture(instance, arena)`、`allocateShell(context)`、`restore(shell, state, arena)`；capture 只读取 provider 明确声明的数据；allocateShell 不调用 forField/forDataTemplate/forFunction，不执行字段生成等动作；restore 分配全部 A/H shell 后填充，保持引用别名。provider 可提供恢复专用工厂/构造器，避免普通构造器中的可观察副作用；不得使用 Unsafe、Java 序列化对象图或通用字段反射代替 codec。

内置 codec 首批必须覆盖 `ConcreteOnly, DataOnly, Dynamic, Load, Tick, MCFPPEntity, NoInstance` 的共同 arguments/parents/phase，`From/To.className`，`Name.name/version`（version 可空）。目前 To 的动作自身未实现，codec 保留其状态和既有诊断，不承诺新增行为。`WritesReceiver`、`NoExternalWrites` 是 JVM 方法注解，其事实在 NativeBinding 装载验证中获取，不是 Annotation 子类 state；不能混在同一 object codec。Name/From 等已有私有/包可见字段可加明确的 provider 内部状态 accessor/factory，不能要求 reader 反射字段。

现有夹具给出的具体状态和迁移入口：

| 夹具 | 必须保存的内容与接入 |
|---|---|
| `DataInheritanceLibraryMetadataTest.MetadataMarker/GeneratingAnnotation` | values 的独立可变列表；applications 计数；完成的 generated field/property、initializer 17、isDataOnly 与 InheritedEffect。生成时创建的 initializer context 也经 SourceTreeCapture；导入不能再执行生成动作 |
| `GenericTemplateAnnotationTest.HeaderProbe/ParentProbe/BodyProbe` | applications、environments、shared、nested；HeaderProbe.shared、nested[0]、ParentProbe.shared 指向同一 H。ParentProbe 虽不在 namespace 注册，作为 codec 明确声明的父 provider 随本包 D 描述保留；泛型实例的克隆 arena 各自独立，pending header/body 正常执行并保留环境/顺序 |
| `TemplateFieldInferenceTest.MutableFieldAnnotation` | values 的深拷贝；NonSerializableFieldAnnotation.payload=Any 的原有拒绝诊断继续是负例，不能以此替代可支持的 MutableField 成功路径 |
| `DataInheritanceLayoutRestoreTest.Marker/GeneratingAnnotation`、`DataTemplateParentIdentityMetadataTest.ParentProbe`、`IntersectionTemplateCompletionTest.RestoreProbe` | 显式 counters/marker 状态；完成声明与父身份重建后核对，不因 annotation 数据重放动作或重复 initializer |
| `MniAnnotationDeclarationStateTest.Generate` | fieldName、applications、fail、labels、second 与 payload（仅当值属于 codec 声明的有限 portable 域）；labels/second 别名必须保留。computed getter/hashCode 不读取；静态 constructed/observed/callbacks 不入库；提供恢复工厂避免增加 constructed |
| `MniScoreMappingTest.ScoreFieldAnnotation` | positional objective 字符串与 compilerArguments 一致；恢复后的 declaration facts/MNI catalog 继续暴露同一 score 映射 |
| `MniDeclarationPreparationCompletionTest.GenerateField`、`MniSourceEnvironmentTest.GenerateField`、`MniEmbeddedParentCompletionTest.CountApplications` | 完成/待完成阶段、生成字段与计数按相同规则；声明目录准备不能隐式执行待执行动作 |

其余 preparation/journal 夹具中的 Probe/Marker 若仅在活跃会话检查，不强行把它们变成发布库 fixture；当其对象进入导出选择闭包，必须为其注册显式 test codec 或保持已有“无可持久化状态”负例。codec 注册可以位于测试/provider 同一包以访问私有数据；改变 fixture 是为接入明确的保存 API，不能移除原状态断言。codec 自身不调用声明动作，导入 pending/completed 决策仍由既有模板完成阶段控制。

这些夹具是源码调查得到的迁移依据，本任务未执行它们；现有缓存路径通过与否也不构成新格式已通过的证据。后续注解实现任务应复用最相关的成功/拒绝夹具，经正式 v1 编码和全新会话读取后核对别名、完成阶段与生成效果，再和函数/模板闭环集成。

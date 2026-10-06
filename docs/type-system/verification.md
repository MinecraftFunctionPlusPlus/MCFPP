# 当前阶段验证记录

最新验证日期：2026-10-06（Asia/Shanghai）。阶段64 MCFL15标准库重建Project语言错误/警告0/0，`bin.mclib` 286207 bytes；最终定向字段方法1项与LogicStatementTest6单次联合7项全通过。最近完整检查仍属于提交 `72dc557`，共346项；本轮未运行完整check或实际Minecraft服务端，整个迁移仍未完成。

## 最新必要检查：库字段与 Property 权限（阶段 64）

仅`PropertyInfo.accessModifier`新增持久化；Var权限原已由Kryo保存，运行时adapter复制声明access/owner，但binding仍为transient。权限判断复用恢复的声明owner，Function的`accessTo`通过`NoStack`/`Internal`沿词法caller检查。`StorageAccess.inFrame(binding, stackIndex)`创建指定帧偏移的地址view，共享data、Place及versions。旧visitor while body使用`NoStack`共享外层while frame，移除无匹配的body push并登记真实child，修复了物理帧结构。`FunctionConditionTest`按官方1.20.3 DataPack单ID、function-return及score短路条件的受限子集执行：[Minecraft Java Edition 1.20.3](https://www.minecraft.net/en-us/article/minecraft-java-edition-1-20-3)。

真实库往返验证 Base.private constructor/while 写入4后读5、Child.protected为6、object静态private为7，且frame0平衡；独立consumer对外部private/protected字段读取及Intruder读取基类private产生编译错误。对象仍需显式初始化，未覆盖入口继续跟进。

验证历程保留各轮边界：实现前红测2项producer分别有3/9个权限语言错误（XML `2026-10-06T01:08:01.481Z`）。首轮16项15通过，四份XML时间戳依次为`01:12:03.039Z`、`01:12:08.757Z`、`01:12:12.153Z`、`01:12:08.744Z`；唯一失败为执行器不支持嵌套function条件。补执行器后字段用例仍报缺失`stack_frame[0].this.hidden`（`01:17:19.814Z`），FunctionConditionTest补验1项通过（`01:17:28.966Z`）；frame偏移修复后字段方法仍有退出帧泄漏（`01:24:01.542Z`），当轮ConstructorExecution7通过（`01:24:05.182Z`）。while body共享外层帧后，最终仅复查该字段方法1项与LogicStatementTest6，共7项全过、0 failures/errors/skips，Gradle exit0；XML时间戳`2026-10-06T01:30:51.910Z`、`01:30:57.600Z`。15（首轮其余绿）+1（FunctionCondition补验）+7（ConstructorExecution补验）+7（最终字段/逻辑复查）=30个不同用例跨轮各自通过，非一次联合30项。三次标准库重建Project语言诊断均0/0；最终MCFL15/bin286207 bytes。日志：`mcfpp-library-field-access-red.log`、`mcfpp-library-field-access-stdlib.log`、`mcfpp-library-field-access-final.log`、`mcfpp-library-field-access-complete.log`、`mcfpp-library-field-access-stdlib-complete.log`、`mcfpp-library-field-access-runtime-complete.log`、`mcfpp-library-field-access-stdlib-final.log`、`mcfpp-library-field-access-runtime-final.log`。未运行fullcheck或服务器。

## 历史必要检查：库函数访问修饰符持久化（阶段 63）

普通、generic、native函数`accessModifier`已写入并从MCFL14库恢复；泛型特化保留权限。`FuncGetter`按声明owner执行权限检查；临时`NoStackFunction`只用于解包词法caller，不改运行时storage或原function owner。writer每次重建时清除普通/generic缓存写快照，reader仍读取canonical缓存；`FieldInfo.from`显式owner过滤并在恢复本地Var/Property的`declaredParentTemplate`，防止再写库时丢失字段。

首轮测试14项13通过/1失败（LibMemberAccess3 XML `2026-10-06T00:36:57.610Z`；TemplateInitialization8及LibCacheFormat3通过）。失败为model注入PRIVATE NativeFunction后再次`genIndex`复用了PUBLIC写快照，权限检查不是IR/clone绕过。首轮红测三项原本都暴露库权限丢失；之后`restoredMethodsUseLexicalOwnerForPrivateAndProtectedCalls` 的producer main加入真实合法private/protected调用，证明确为cache往返权限问题。两次标准库重建的语言诊断均为0 errors/0 warnings。

最终单次联合：LibMemberAccess3、TemplateInitialization8、LibCacheFormat3，共14项全过、0 failures/errors/skips、Gradle exit0。XML时间戳 `2026-10-06T00:49:26.509Z`、`00:49:32.487Z`、`00:49:32.476Z`。MCFL从13升至14，最终bin为285207 bytes（282676 bytes为首次中间快照，非最终产物）。真实库往返覆盖producer内部private/protected调用、外部拒绝、子类对基类private拒绝及model-injected private native双次写/读；无private-native源码语法。未运行fullcheck/服务器。日志 `mcfpp-library-member-access-red.log`、`mcfpp-library-member-access-stdlib.log`、`mcfpp-library-member-access-final.log`、`mcfpp-library-member-access-stdlib-complete.log`、`mcfpp-library-member-access-complete.log`。

## 历史必要检查：目录与归档模块资源复制（阶段 62）

`Project.readFromDIR/JAR/ZIP`保留来源`resourcePath`；归档module入口路径包含`datapack/`，`FileUtils.extractTo`剥除完整`sourceDir/`并去掉残余前导斜杠；缺少packages字段的base-only module继续导入。JAR/ZIP reader关闭archive handle，测试临时目录可清理。目录、ZIP、JAR三类真实库来源均精确复制base-only module的资源、函数及tag；`copyImport=false`时不导出Imports。

阶段62红测fresh XML `2026-10-06T00:17:38.651Z`，3项全部因缺 `function/marker.mcfunction` 失败。修复后唯一联合验证LibModuleCopyTest3 + TemplateInitializationTest8，共11项全过、0 failures/errors/skips、Gradle exit0/BUILD SUCCESSFUL in52s。fresh XML时间戳 `2026-10-06T00:22:51.552Z`、`00:22:53.265Z`。日志 `mcfpp-library-module-copy-red.log` 与 `mcfpp-library-module-copy-final.log`。MCFL13/bin282180未变，无stdlib重建、fullcheck或服务器验证。ZipFile关闭资源参考 [Java ZipFile.close](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/zip/ZipFile.html#close()) 与 [Kotlin use](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin.io/use.html)。

## 历史必要检查：恢复导入成员 owner 与模板 scope（阶段 61）

`FieldInfo`保留无参 `get()`，新增owner参数并复用restore；`DataTemplateInfo`普通/泛型模板通过`field.get(template)`恢复canonical field scope。修复的是本地方法owner与方法scope.parent中canonical field优先项，不是Var字段owner。三个`FunctionInfo`恢复已序列化的`isAbstract`。参数NBT/score/return/Symbol/Place无需重绑。

实现前红测 fresh XML `2026-10-06T00:02:12.468Z`，两个方法分别有5/2个语言错误。首轮三套件26项中24通过、2失败，TemplateInitialization XML `2026-10-06T00:04:27.892Z`；ConstructorExecution7和SpecializationPolicy11分别于`00:04:40.457Z`、`00:04:41.396Z`全过。普通模板方法fixture产生`Cannot get member add`及后续比较错误；修正grammar中 var 后函数调用优先级（bucket仍先）解决。object fixture复用已有main.scope生成ctor prologue，fieldStore读取尚未赋值的main.result，导致执行器报missing `default.test_func_main_result`；改用独立空scope临时Function生成prologue后通过，这是测试夹具初始化顺序修正，与未登记无关。只复查两失败方法后 fresh XML `2026-10-06T00:09:23.003Z`，2/0/0/0。合计26个不同用例跨轮各自通过，非最终联合26项。真实库往返检查保留继承owner及静态路径：实例927/generic12、object7，frame0。MCFL13/bin282180不变，无stdlib重建、fullcheck或服务器验证。日志 `mcfpp-library-member-owner-red.log`、`mcfpp-library-member-owner-final.log`、`mcfpp-library-member-owner-complete.log`。

## 历史必要检查：消费端库函数主体导出（阶段 60）

`DatapackCreator` 新增的库导出入口仅导出 `bodyCompiled` 的非 Native 函数，并按实际 namespace ID 写入；收集器遍历 `compiledFunctions`、`GenericDataTemplate.compiledTemplates`、模板接口/对象/companion，使用 identity visited 避免重复实例。库测试 helper 真实读取 consumer 目录生成的所有 `.mcfunction`，main 与 callee 均从磁盘执行；generic wrapper 有非空文件，generic prototype 不输出，没有从 Imports 或内存补漏。

实现前红测单方法失败：XML `2026-10-05T23:52:01.504Z`，`restoredGenericSpecializationsKeepTheDeclarationFileForRuntimeArguments` 在执行器报告缺失 `fixture.defaults:add_0_int`，定位为消费包未导出 wrapper/callee。collector修复后 `TemplateInitializationTest` 6项与 `ConstructorExecutionTest` 7项单次联合全过，0 failures/errors/skips，Gradle exit0；fresh XML 时间戳 `2026-10-05T23:53:57.493Z`、`23:54:08.422Z`。日志 `mcfpp-library-body-export-red.log` 与 `mcfpp-library-body-export-final.log`。MCFL13及bin 282180 bytes未变；未重建stdlib、未运行fullcheck或实际服务器。

## 历史必要检查：库声明词法环境恢复（阶段 59）

库元数据保存声明文件的namespace和unsolvedImports；全部includes读取后，分别恢复声明文件的FileScope，供调用图绑定与函数编译使用。普通函数、构造器及泛型特化共用声明上下文入口，并在结束后恢复调用方上下文。泛型模板和object实例继承其声明环境，不序列化整个FileScope或Project。

实现前三个定向红测均失败：构造RHS及自由函数预期44、实际99，泛型调用预期5、实际10，跨库导入预期0个语言错误、实际1个。fresh XML为 `2026-10-05T23:25:44.039Z`，3 tests/3 failures/0 errors/0 skipped。首次标准库重建虽完成Kotlin编译，却报告54个原生MNI类型解析错误、0警告；延迟annotation callback时缺少声明文件上下文，无法解析模板自身的类型。class/field callbacks恢复声明环境，并保存/恢复currTemplate后，第二次重建成功，Project报告0个语言错误、0个警告。

最终单次联合运行：TemplateInitialization6、ConstructorExecution7、IRCall17、LibCacheFormat3、SpecializationPolicy11、TemplateFieldInference6，共50项、0 failures/errors/skips，Gradle exit0。六份fresh XML时间戳分别为 `2026-10-05T23:38:42.679Z`、`23:38:52.714Z`、`23:38:53.474Z`、`23:38:54.764Z`、`23:38:54.770Z`、`23:38:55.104Z`。标准库MCFL由12升至13，bin由267356增至282180 bytes；格式测试及实际Kryo库往返均通过。

日志位于 `F:/DevCache/.codex/runtime/`：`mcfpp-library-declaration-scope-red.log`、`mcfpp-library-declaration-scope-stdlib.log`、`mcfpp-library-declaration-scope-stdlib-final.log`、`mcfpp-library-declaration-scope-final.log`。此阶段验证内存中命令的执行；消费端物理库函数主体导出、方法owner恢复及imported object自动load仍待解决。

## 历史必要检查：普通模板推断字段声明绑定（阶段 58）

普通模板 inferred fields 复用 `PrimitiveCompiler` 私有图的 Lowering、FlowAnalysis 与 ReturnTypeAnalysis 做声明/类型绑定；不发布 IR、不执行用户函数、不生成命令。普通构造参数和未绑定 `T!` 保持 Unknown；同字段在不同 ctor overload 下要求一致 `TypeId`。支持 `this` 单字段及此前字段读取。anonymous template 走同一声明队列；generic 类型实参绑定完成后再分析其实例，继承完成后再应用 annotations 并刷新模板参数/返回 adapter，保留既有 Symbol/Place。

声明绑定不会重复执行 RHS：pure AST binding 与 runtime AST 生成是两个阶段。运行时 receiver 按声明类型初始化可编码的默认字段，Unknown erased 值不伪造 snapshot；NBT codec 遇不可编码 child 返回整体 null。普通模板局部声明使用 `buildUnConcrete`，避免复制 defaultVar 图而向 DataOnly 空地址写命令。未接入路径继续用 legacy `extraFunction`，包括 native/generic/compiler-only/static、多级 `this`/member method 等语法；阶段范围仍有限。

验证历程：早期定向测试在 test compile 阶段因 `@Test` 名称解析失败；之后一次 worker 通信退出，没有 fresh XML。`--info` 单类诊断轮6项显示两项 discarded-probe assertion把标准库 Slot 初始化命令误当用户probe；仅检查本地 source function calls 后修正 helper。第三项 NBT codec NPE导致 worker exit。runtime修复后 TemplateFieldInference 6项 fresh XML 为5 pass/1 fail，失败是产品生成非法空地址NBT命令 `data modify storage mcfpp:system  set value 0`，严格执行器拒绝；ImVisitor修复后定向该方法1项通过。随后指定其余31项四套件全过。合计37个不同用例各自最终通过，未将分轮结果写成最终联合37项通过。日志：`mcfpp-template-field-inference.log`、`mcfpp-template-field-inference-final.log`、`mcfpp-template-field-inference-diagnostic.log`、`mcfpp-template-field-inference-runtime-fix.log`、`mcfpp-template-field-inference-dataonly-final.log`、`mcfpp-template-field-inference-regression.log`。

MCFL 12 schema 与 `bin.mclib` 267356 bytes 保持不变；本轮无 stdlib 重建、full check 或服务器测试。阶段57 const/runtime 初始化结果仍见后文历史段；导入构造 RHS lexical scope、import object 自动 load、未迁入的模板路径及旧消费警告仍是缺口。

## 历史必要检查：模板 const 字段运行时初始化（阶段 57）

`sharedProject.prepareObjectInitializers` 在 annotation、完整签名和继承信息 ready 后、用户函数 body 编译前编译完整本地 object constructor，使用已有 guard 避免重复。source inferred object 字段按声明顺序暂存上下文、访问和已解析 annotation；FieldVisitor 不试算 RHS，由实际 constructor `prepareBody` 单次求值并补齐 field/property/Symbol。typed const RHS 同样登记；普通 typed const 可对每个 receiver runtime 初始化，传入参数先绑定，再跑 RHS。两个不同 receiver（1、2）实测正确初始化。

const 仅限制重赋，不等同于 compiler-only：compiler-only const 保留 `ValueSnapshot` 完整值而不物化，`T!` 独立要求完整值，inferred mirrored 不继承该约束。字段 annotation 在 annotation visitor 转存后触发 helper 补 annotation stage；函数 annotation 按真实 AST 声明定位，无 fake args。错误 RHS 不写默认值；self/forward 引用明确诊断且不生成对应初始化写入。

首轮联合 28 项（TemplateConstInitialization 4、TemplateInitialization 3、ConstructorExecution 7、LibCacheFormat 3、SpecializationPolicy 11）为 27 pass/1 fail。唯一失败是负向测试字符串中两个顶层声明缺少换行，解析早退后 helper 找不到 object；仅调整测试字符串后，定向该方法 1 项通过。XML 新时间戳为 `2026-10-05T22:24:27.881Z`，0 failures/errors/skips；首轮的其他 27 项全过，未再联合运行。构建日志：`mcfpp-template-const-stdlib.log`、`mcfpp-template-const.log`、`mcfpp-template-const-order-final.log`。真实执行覆盖 load 中帧/global storage、constructor 和 main；测试 helper 隔离 objective、`math:_init` 与 marker summon，并非服务器验证。此次本地 source object 路径重编译含 20 个 Slot 推断字段，MCFL 12 descriptor schema 未变；bin 267356 bytes（增加 198）。

## 历史必要检查：模板初始化表达式库往返（阶段 56）

`DataTemplateInfo` 按声明顺序持久化字段初始化表达式，`DataTemplate.preInit` 使用 `LinkedHashMap`；`GenericDataTemplateInfo` 原已有 body AST，没有重复增加。源码构造器编译恢复声明文件与命名空间；`@Transient` 文件字段不会随库保存，导入构造器的 `file` 仍为 null 并依赖 caller，词法 scope 是已知缺口。Kryo 三个自定义 reader 先 reference 再 nested read，避免循环声明回读时以未完成对象算 hash；依据 [Kryo 5.6.2 Serializer References](https://github.com/EsotericSoftware/kryo/blob/kryo-parent-5.6.2/README.md#serializer-references)。生产和字符串测试共享 `MCFPPFile.resolveImports`。

TemplateInitializationTest 3、ConstructorExecutionTest 7、LibCacheFormatTest 3 的本轮 XML 均为新时间戳，合计 13 项、0 failures/errors/skips。测试覆盖声明顺序、库导入后的显式模板构造执行、Kryo 循环引用和 MCFL 12 格式。历史首轮 12 项有 9 fail/3 pass（reader 循环初始化的 caller 为 null）；随后 13 项尝试发生 worker 中断且结果 XML 过期；诊断轮 3 项中 1 fail/1 skipped，发现 restored object 的 Defaults RHS 使用了无法解析的 `Defaults.z` 和 Box 调用导入绑定问题。共享 resolveImports 与入口绑定修复后最终联合 13 项全通过。注意 object 测试只显式调用构造器，不证明库 object 自动 load；导入 RHS 的声明词法 scope 完整持久化仍待解决。最终日志：`F:/DevCache/.codex/runtime/mcfpp-template-initializers-complete.log`。MCFL 12；未运行完整 check 或实际 Minecraft 服务端。

## 历史必要检查：模板构造 receiver 与初始化帧（阶段 55）

普通模板构造器不再按普通常量实参做常量特化；T! 和 compiler-only 仍按 `SpecializationPolicy` 处理。receiver 使用固定 `frame0.this` 独立传递；参数只编码入帧，不污染 callee facts。调用返回通过 `FrameExit(function,index)` 统一 IR/旧路径出口；entry 路径插入 pop，caller 路径先写回 receiver 再 pop。`preInit` 每次运行，包括 AST-null 隐式默认构造；原/特化模板及 static object 构造器均导出。显式类型非 const 字段现登记 preInit；静态字段赋值通过 `replacedBy` 后物化，非 const object 字段动态化。receiver 写回保留 source type，Unknown effect barrier 仍可撤销知识。普通模板赋值/传参的复制规则不变。

ConstructorExecutionTest 最终 7 项真实执行/导出检查通过，0 failures/errors/skips；测试还检查 static object 导出命令实际产生 NBT 值 4。首轮 8 套件共 80 项有 4 项失败：两个 set 保留字样例、typed initializer 漏登记导致运行结果 90 而非 94、执行器缺 `unless score matches`；其余 76 项通过。中间复查 ConstructorExecution 7 + ConstructorResolution 4 共 11 项有 1 项失败（object constructor 文件为空），静态赋值物化修复后最终 7 项全过；ConstructorResolution 4 项此前已全过。MCFL 11 未变；无标准库重建、完整 check 或服务端测试。

## 历史必要检查：模板构造器重载解析（阶段 54）

ConstructorResolutionTest 最终 4 项通过，0 failures/errors/skips。覆盖精确候选不受声明顺序影响、T! 拒绝未知形参但接受完整常量、默认实参在 if/else-if 常量分支中的执行、歧义无构造副作用（只容许公共帧前言）。解析以 `ParameterMatcher.match`/`best` 选择候选，复用类型、完整值、默认参数及歧义判断；旧的字符串/类型顺序重载接口已删除，只有 `Selected` 才初始化对象，错误 `UnknownVar` 后续 visitor 访问不再重复诊断。常真 if 及静态 false→true else-if 主体内联时不再调用未注册函数。

首轮 ConstructorResolutionTest 4、TypeBindingTest 4、StorageViewTest 21 共 29 项有 3 项失败，TypeBinding/StorageView 全过；中间复查 ConstructorResolutionTest 4 + LogicStatementTest 6 共 10 项有 1 项失败，LogicStatement 6 全过，剩余失败是测试误禁公共 stack prepend 前言。最终仅复查 ConstructorResolutionTest 4 项通过。日志：`F:/DevCache/.codex/runtime/mcfpp-constructor-resolution.log`、`mcfpp-constructor-resolution-final.log`、`mcfpp-constructor-resolution-complete.log`。MCFL 11 未变；未重建标准库、未运行完整 check 或实际 Minecraft 服务端。模板构造 compile 的普通常量特化、`this`/`preInit` 帧尚未改动。

## 历史必要检查：宿主值对象身份（阶段 53）

VarIdentityTest 5、StorageViewTest 21、LegacyFloatIRTest 9，共 35 项最终复查通过，0 failures/errors/skips。首轮 VarIdentityTest 5、NBTAddressTest 7、StorageViewTest 21、ListMemberTest 19、SpecializationPolicyTest 11 共 63 项有 2 项失败；后四套件共 58 项全部通过。失败分别为跨递归调用的 legacy visitor 表达式结果错误（期望 sum 24、得到 49）和 spill 测试执行器未建立帧。括号子 visitor 现共享父级活跃值列表，同时保留各自结果字段；直接 spill 测试通过真实 stack prepend/remove 建立执行器帧。最终复查使用 `F:/DevCache/.codex/runtime/mcfpp-var-identity-final.log`，首轮日志为 `mcfpp-var-identity.log`。

删除 `Var`、`Pos3Var`、`Pos2Var`、`PosDimension` 共 8 个 equals/hashCode 覆盖后，宿主对象统一按对象身份比较；语言值仍由 `CompilerValue` 比较。表达式缓存只删除目标引用，spill 的 `distinct()` 只合并同一引用，避免同名但不同临时值混淆。MCFL 11 未变；未运行完整 check、未重建标准库或实际 Minecraft 服务端。

## 历史必要检查：NBT 地址等价与宏捕获（阶段 52）

NBTAddressTest 7、FloatProviderTest 10，共 17 项通过，0 failures/errors/skips。首轮四套件共 72 项仅 `FloatProviderTest.nonStorageAndDynamicIndexSourcesAreCopiedBeforeEvaluation` 失败（读取次数预期 2、实际 4），其余 71 项通过；`FloatProviders.preparePath` 原先预写动态 index，随后自动宏参数编码又读取一次。移除该重复预写后保留原 2 次读取断言，定向复查 17 项全部通过。日志：`F:/DevCache/.codex/runtime/mcfpp-nbt-address.log`、`mcfpp-nbt-address-final.log`。

`NBTAddressKey` 冻结地址 source 与 path segments；按快照比较路径段和长度，父子路径同时检查 source，修复 equals 自递归，避免不同来源或不同深度路径被判为同址。自动宏参数使用独立 arg 槽，值来自实际绑定或 scoreboard；FloatProviders 不再预先重复写入 index。前轮 CollectionStorageTest 39、MapMemberTest 16 均通过，故最终只复查直接受影响的两个套件。MCFL 11 未变；未运行完整 check、未重建标准库或实际 Minecraft 服务端。

## 历史必要检查：旧浮点 IR（阶段 51）

最终复查 LegacyFloatIRTest 9、LegacyFloatLayoutTest 5、LegacyFloatConversionTest 6，共 20 项通过，0 failures/errors/skips。日志：`F:/DevCache/.codex/runtime/mcfpp-legacy-float-ir-final.log`。首轮 51 项有 3 项失败；修复 identity/诊断后首次复查因 4 处智能转换导致编译失败并已修复。随后 22 项检查有 1 项失败、其中算术 13 项通过；修复 Concrete 目标的默认 0 覆写后，最终 20 项全部通过。相关历史日志：`mcfpp-legacy-float-ir.log`、`mcfpp-legacy-float-ir-interop.log`、`mcfpp-legacy-float-ir-interop-final.log`。MCFL 11 未变，无签名/缓存结构变化，未重建标准库；未运行完整 check 或实际 Minecraft 服务端。

旧浮点算术/比较、Promote/Convert 已接入 IR，四分量使用独立 NBT 帧，纯 `LegacyFloatCommands` 负责读写/调用，并保留旧 4 记分板 return ABI。普通、递归、static、旧与 IR 双向调用、早先参数、多实参、常量及连续返回已由真实库命令执行。旧算术/比较不做宿主浮点折叠或跨数值折叠；`16777217` 保留八位十进制表示。identity/toNBT 保留来源 codec。包含 FloatBits 端点的旧浮点范围，其静态顺序不再用宿主比较（整数/native 行为不变），未定义浮点迭代语义。

已知 int/bool/byte/short as legacyfloat 仍沿旧入口并在实际访问时诊断；未使用的视图不报错，unknown any 视图不做运行时 typecheck，命名 float 视图的来源随后被写成已知标量，再读取视图会诊断。阶段 50 乘除和阶段 49 加减的历史验证见下文。

## 历史必要检查：旧浮点乘除（阶段 50）

LegacyFloatMultiplyDivideTest 7、LegacyFloatArithmeticTest 6、LegacyFloatConversionTest 6、LegacyFloatLayoutTest 5、ConversionTest 12 联合共 36 项通过，0 failures/errors/skips。日志：`F:/DevCache/.codex/runtime/mcfpp-legacy-float-mul-div.log`。7 项中包括 executor 和入口断言，并非全为库函数执行。MCFL 11 未变，未改签名/缓存结构，未重建标准库；未运行完整 check 或实际 Minecraft 服务端。

任一符号为零的乘除（包括分母为零及 0/0）在算术前规范为四个零分量，不执行会失败的 `/=0` 或 `%=0`，不新增已知零编译诊断，也不定义 IEEE 特殊值。非零乘法维持精确截断；除法固定 7 次十进制长除，若 A<D 再做 1 次，精确商向零截断为 8 位有效数字。右分量只读且不依赖 return；`div_align` 因仍被 inverse/3vec 引用而保留。

## 历史必要检查：旧浮点加减（阶段 49）

LegacyFloatArithmeticTest 6、LegacyFloatConversionTest 6、LegacyFloatLayoutTest 5、ConversionTest 12、IRCallTest 17 联合共 46 项通过，0 failures/errors/skips。Gradle exit 0。日志：`F:/DevCache/.codex/runtime/mcfpp-legacy-float-add-sub.log`。新增 6 项覆盖实际库函数执行、executor 语义及真实 visitor 捕获顺序下的连续表达式，并非每项都执行 mcfunction。MCFL 11 未变，未改签名/缓存结构、未重建标准库；未运行完整 check 或实际 Minecraft 服务端。

旧 `_add`/`_rmv` 共用主体，零先分流；非零时正幅值对齐一个十进制保护位及 sticky 位，精确十进制加减后向零截断为 8 位有效数字。工作尾数 9/10 位时归一化正确；右实体分量只读且不依赖 return。executor 只扩展 single identity execute as、score 位置 `@s`、交换和 score 条件链。

## 历史必要检查：旧浮点转换（阶段 48）

LegacyFloatConversionTest 6、LegacyFloatLayoutTest 5、ConversionTest 12、FloatProviderTest 10 联合共 33 项通过，0 failures/errors/skips。日志：`F:/DevCache/.codex/runtime/mcfpp-legacy-float-conversions.log`。MCFL 保持 11；本阶段未改索引结构/签名，未重建 `bin.mclib`。未运行完整 check 或实际 Minecraft 服务端。

旧值为 `sign * (int0 * 10000 + int1) * 10^(exp-8)`；int→旧浮点按八位十进制有效数字截断，与常量 codec 的 nearest/ties-to-even 舍入不同；零规范为全零，Int.MIN_VALUE/MAX_VALUE 均丢失低位，±2147483648 附近为 ±2147483600。旧浮点→int 向零截断，未知运行时超范围饱和至 Int.MIN_VALUE/MAX_VALUE；已知非有限或超范围复用 NumericConversion.floatToIntError 编译诊断。旧转换尚未接入旧浮点 IR。

## 历史检查：旧浮点编码与比较基础（阶段 47）

```sh
./gradlew regenerateStdlib
./gradlew test --tests top.mcfpp.test.LegacyFloatLayoutTest --tests top.mcfpp.test.ConversionTest --tests top.mcfpp.test.FloatProviderTest --tests top.mcfpp.test.FloatIRTest --tests top.mcfpp.test.LibCacheFormatTest
git diff --check
```

五个套件共 36 项通过：LegacyFloatLayoutTest 5、ConversionTest 12、FloatProviderTest 10、FloatIRTest 6、LibCacheFormatTest 3。日志为 `F:/DevCache/.codex/runtime/mcfpp-legacy-float-layout-final.log`；此前四个套件的 33 项通过记录在 `mcfpp-legacy-float-layout.log`。标准库重建日志为 `mcfpp-legacy-float-stdlib.log`，0 错误/0 警告；`bin.mclib` 的格式头已核验为 MCFL 11。

编码覆盖极大/极小有限 Float、零和舍入边界；比较覆盖正负值、零、高低分量优先级及六种运算，检查命令不依赖旧比较库、不修改操作数，且不使用 return。非有限字面量、物化和 toNBT 有诊断断言。旧浮点算术仍使用模拟库，转换库 `_scoreto`/`_toscore` 尚待修复；未运行完整 check 或 Minecraft 服务端。

## 最新必要检查：显式转换 IR 与返回接口

```sh
./gradlew test --tests top.mcfpp.test.ConversionIRTest --tests top.mcfpp.test.ConversionTest --tests top.mcfpp.test.FloatIRTest --tests top.mcfpp.test.FloatProviderTest --tests top.mcfpp.test.IRCallTest -Dorg.gradle.jvmargs=-Xmx2g -Pkotlin.daemon.jvmargs=-Xmx2g
./gradlew test --tests top.mcfpp.test.ConversionIRTest --tests top.mcfpp.test.NbtArrayIRTest --tests top.mcfpp.test.IRCallTest -Dorg.gradle.jvmargs=-Xmx2g -Pkotlin.daemon.jvmargs=-Xmx2g
./gradlew test --tests top.mcfpp.test.ConversionIRTest -Dorg.gradle.jvmargs=-Xmx2g -Pkotlin.daemon.jvmargs=-Xmx2g
git diff --check
```

首轮 toNBT(short) 常量快照被重标为 IntTag；保留 nbt 外层身份和内部来源格式后，5 个套件、52 项通过。检查覆盖窄化/扩大、26.3 浮点转换与循环/递归、已知 any 重载、编码副本、直接 as、long/double 常量后端读、普通同名函数、非法组合和越界诊断，并保留旧模拟后端回归。转换及提升折叠开关均执行。

随后旧调用入口复现 short 返回的 NBT 地址为空。byte/short 返回改为保留既有记分板接口，IR 调用方按 byte/short 捕获；转换、数组和普通调用共 3 个套件、32 项通过。最后将返回用例扩为旧/IR 两种调用方和 byte/short 两种宽度，ConversionIRTest 的 8 项通过，0 失败/错误/跳过，当前 XML 为这个套件。

日志：`F:/DevCache/.codex/runtime/mcfpp-conversion-ir-final.log`、`mcfpp-conversion-ir-abi.log`、`mcfpp-conversion-ir-return-callers.log`。MCFL 保持 10，未运行完整 check 或重建标准库。未实现的数值组合仍诊断；旧浮点转换、DataObject 等来源和实际服务端舍入/异常对照仍待完成。

## 上一阶段必要检查：26.3 原生浮点 IR（7cd1a69）

```sh
./gradlew test --tests top.mcfpp.test.FloatIRTest --tests top.mcfpp.test.FloatProviderTest --tests top.mcfpp.test.IRCallTest --tests top.mcfpp.test.RangeIRTest --tests top.mcfpp.test.CollectionIRTest -Dorg.gradle.jvmargs=-Xmx2g -Pkotlin.daemon.jvmargs=-Xmx2g
./gradlew test --tests top.mcfpp.test.FloatIRTest --tests top.mcfpp.test.RangeIRTest -Dorg.gradle.jvmargs=-Xmx2g -Pkotlin.daemon.jvmargs=-Xmx2g
git diff --check
```

5 个套件、59 项通过。新增 FloatIRTest 的 6 项覆盖算术/比较和循环回边、递归返回与较早参数捕获、static 浮点写回、声明/返回/实参/默认参数/集合元素提升、动态下标跨 RHS 调用、共享视图、负零、精度边界、浮点范围载荷及非法输入诊断；折叠开关均执行。旧 FloatProviderTest 继续覆盖原生表达式和 26.2/1.21.8 的旧后端选择。

首轮旧常量测试依赖 MCFloatConcrete 适配对象强转失败，改为从 StorageAccess 查询不可变快照，仍检查值和不生成运行时计算命令；测试的旧扁平执行器跳过新增 IR 容器初始化。复核补齐已知浮点范围的左右端点顺序检查后，浮点和范围 2 个套件、14 项通过，0 失败/错误/跳过。日志：`F:/DevCache/.codex/runtime/mcfpp-float-ir-final.log`、`mcfpp-float-ir-range-bounds.log`。未运行完整 check 或重建标准库；该阶段旧模拟浮点 IR、显式转换 MNI、浮点/未知范围迭代和实际服务端对照仍未完成。

## 上一阶段必要检查：递归返回与 static 写回形状（eb83a60）

```sh
./gradlew test --tests top.mcfpp.test.CallShapeIRTest --tests top.mcfpp.test.RangeIRTest --tests top.mcfpp.test.IRCallTest --tests top.mcfpp.test.DynamicIndexIRTest -Dorg.gradle.jvmargs=-Xmx2g -Pkotlin.daemon.jvmargs=-Xmx2g
./gradlew compileTestKotlin -Dorg.gradle.jvmargs=-Xmx2g -Pkotlin.daemon.jvmargs=-Xmx2g
git diff --check
```

4 个套件、50 项，0 失败/错误/跳过。CallShapeIRTest 从 6 项扩为 12 项，新增递归擦除返回的嵌套类型/长度、相互递归范围端点、递归 static 整体替换、不同返回类型和变化的递归输入不得伪造证明、无返回递归不采用调用后返回值，以及递归嵌套输出收敛到共同有限形状。正向运行覆盖折叠开关和无宏 1.20.1；子值仍为 Unknown。

首轮一个新增反例因缺少块后分隔导致语法错误，修正后全部通过。最后整理测试导入/图收集后，单独 compileTestKotlin 通过。日志：`F:/DevCache/.codex/runtime/mcfpp-recursive-shape-final.log`、`mcfpp-recursive-shape-cleanup.log`。没有重复完整 check 或标准库重建。不同递归输入形状仍返回保守摘要，未知范围形参、浮点范围和通用迭代器仍未完成。

## 上一阶段必要检查：整数范围值与命名范围 IR（8aee6b7）

```sh
./gradlew regenerateStdlib -Dorg.gradle.jvmargs=-Xmx2g -Pkotlin.daemon.jvmargs=-Xmx2g
./gradlew test --tests top.mcfpp.test.RangeIRTest --tests top.mcfpp.test.LoopIRTest --tests top.mcfpp.test.CallShapeIRTest --tests top.mcfpp.test.IRCallTest --tests top.mcfpp.test.LibCacheFormatTest -Dorg.gradle.jvmargs=-Xmx2g -Pkotlin.daemon.jvmargs=-Xmx2g
./gradlew test --tests top.mcfpp.test.RangeIRTest --tests top.mcfpp.test.IRCallTest -Dorg.gradle.jvmargs=-Xmx2g -Pkotlin.daemon.jvmargs=-Xmx2g
git diff --check
```

5 个套件、40 项通过。新增 RangeIRTest 的 8 项覆盖命名/返回/擦除范围、超过 Float 精度的整数及 32 位极值、端点求值顺序、独立副本和共享视图、static 写回、分支/嵌套集合、缺失或不明端点诊断、旧入口精确编码及选择器格式。跨折叠开关与 26.3/1.20.2/1.20.1 执行。

检查修复了范围返回槽缺少 NBT 地址、as range 类型入口遗漏和探索阶段 any 范围提前回退。旧入口动态范围迭代仍有明确边界，用例分开验证动态范围物化和常量迭代；补齐旧入口返回载荷写出、返回快照及左端点跨右端点调用的保存后，2 个套件、25 项通过，0 失败/错误/跳过。

日志：`F:/DevCache/.codex/runtime/mcfpp-range-values-final.log`、`mcfpp-range-values-legacy-return.log`、`mcfpp-range-values-stdlib.log`。范围端点缓存结构及成员签名发生变化，库格式升级为 MCFL 10，已验证二进制格式头并重建标准库为 0 错误/0 警告；没有重复完整 check。浮点范围 IR、未知端点形参的迭代及通用运行时迭代器仍未完成。

## 上一阶段必要检查：调用返回与 static 写回的子形状（0c3e65f）

```sh
./gradlew test --tests top.mcfpp.test.CallShapeIRTest --tests top.mcfpp.test.CollectionIRTest --tests top.mcfpp.test.IRCallTest --tests top.mcfpp.test.DynamicIndexIRTest --tests top.mcfpp.test.NbtArrayIRTest -Dorg.gradle.jvmargs=-Xmx2g -Pkotlin.daemon.jvmargs=-Xmx2g
git diff --check
```

5 个套件、61 项，0 失败/错误/跳过。新增 CallShapeIRTest 的 6 项覆盖嵌套集合返回与透传、较早参数在后续 static 调用前的形状快照、static 整体替换及旧后代清理、不同返回分支的共同信息、数组返回长度，以及普通常量实参不能选择返回/写入分支。折叠开关均覆盖，包含 26.3 和无宏的 1.20.1；返回元素保留类型但值仍为 Unknown。

首轮两个新用例失败：无宏目标在形状绑定前提前回退；已编译被调函数返回的嵌套类型对象未登记到调用方。探索阶段推迟负索引回退，并从不可变 TypeId 恢复受支持的容器类型后通过。日志：`F:/DevCache/.codex/runtime/mcfpp-call-shape-ir-final.log`。没有重复全量构建或标准库重建，该轮 MCFL 保持 9。递归子形状仍保守处理；该轮尚未接入命名范围及通用迭代器。

## 上一阶段必要检查：IR 词法作用域（eef6d44）

```sh
./gradlew test --tests top.mcfpp.test.ScopeIRTest --tests top.mcfpp.test.LoopIRTest --tests top.mcfpp.test.CollectionIRTest --tests top.mcfpp.test.IRCallTest -Dorg.gradle.jvmargs=-Xmx2g -Pkotlin.daemon.jvmargs=-Xmx2g
./gradlew test --tests top.mcfpp.test.ScopeIRTest --tests top.mcfpp.test.LoopIRTest --tests top.mcfpp.test.IRCallTest -Dorg.gradle.jvmargs=-Xmx2g -Pkotlin.daemon.jvmargs=-Xmx2g
git diff --check
```

联合 4 个套件、46 项通过。新增 ScopeIRTest 的 5 项覆盖同级块同名声明、嵌套遮蔽及初始化读取外层、同名集合/共享视图、循环和递归局部存储、未读取 NBT 局部的父路径初始化，以及同域重复声明和越界引用诊断。

首次检查发现越界引用会退回旧入口，从而泄漏常量分支内的局部变量；现直接诊断。另一个测试变量名误用了保留字，已修正。声明 Symbol 保留源码名，存储名独立按声明位置分配；此调整后只复查 3 个相关套件、28 项，0 失败/错误/跳过。日志：`F:/DevCache/.codex/runtime/mcfpp-scope-ir-final.log` 和 `mcfpp-scope-ir-storage-names.log`。未重复全量构建或标准库重建，MCFL 保持 9。

## 上一阶段必要检查：do…while 与闭合整数区间循环 IR（711331e）

```sh
./gradlew test --tests top.mcfpp.test.LoopIRTest --tests top.mcfpp.test.ErasedFlowTest --tests top.mcfpp.test.IRCallTest --tests top.mcfpp.test.CollectionIRTest -Dorg.gradle.jvmargs=-Xmx2g -Pkotlin.daemon.jvmargs=-Xmx2g
git diff --check
```

4 个套件、50 项，0 失败、0 错误、0 跳过。新增 LoopIRTest 的 6 项覆盖 do…while 的先执行后判断、continue 转向条件、至少一次写入的类型证据，以及闭合整数区间的边界一次求值、迭代变量副本、嵌套同名循环变量、break/continue、32 位极值、动态空区间、返回和递归帧。跨折叠开关及 26.3/1.20.2/1.20.1 执行。

首轮一处正向样例因 any 声明语法写错而失败；修正两处同类样例后全部通过，并确认负向回边用例实际触发语义诊断而非语法错误。日志：`F:/DevCache/.codex/runtime/mcfpp-loop-ir-final.log`；该次为上述 4 个套件。未重复全量构建或标准库重建，MCFL 保持 9。命名 range 值、浮点范围和通用迭代器仍在旧边界，不由本次结果证明完成。

## 上一阶段必要检查：map 投影 IR（ab1f3cb）

```sh
./gradlew test --tests top.mcfpp.test.MapProjectionIRTest --tests top.mcfpp.test.MapIRTest --tests top.mcfpp.test.MapMemberTest --tests top.mcfpp.test.CollectionIRTest -Dorg.gradle.jvmargs=-Xmx2g -Pkotlin.daemon.jvmargs=-Xmx2g
./gradlew compileKotlin -Dorg.gradle.jvmargs=-Xmx2g -Pkotlin.daemon.jvmargs=-Xmx2g
git diff --check
```

4 个套件、45 项，0 失败、0 错误、0 跳过，首轮通过。新增 MapProjectionIRTest 的 5 项确认 keys/keyValueSet 及直接投影下标使用实际 IR，覆盖子字段实际类型、嵌套长度、独立副本、后续实参 static 调用前捕获、循环回边重新投影、键列表递归返回、无宏目标，以及未知键/空字段名/旧布局诊断。跨折叠开关及 26.3/1.20.2/1.20.1 执行。

日志：`F:/DevCache/.codex/runtime/mcfpp-map-projection-ir.log`；该次为上述 4 个套件。移除未用导入并消除新增参数遮蔽后，单独 compileKotlin 通过，日志为 `mcfpp-map-projection-ir-compile.log`。没有重复全量构建或标准库重建，MCFL 保持 9。

## 上一阶段必要检查：map 索引与成员 IR（9245a62）

```sh
./gradlew test --tests top.mcfpp.test.MapIRTest --tests top.mcfpp.test.MapMemberTest --tests top.mcfpp.test.DynamicIndexIRTest --tests top.mcfpp.test.ValueModelTest --tests top.mcfpp.test.EffectAnalysisTest -Dorg.gradle.jvmargs=-Xmx2g -Pkotlin.daemon.jvmargs=-Xmx2g
git diff --check
```

5 个套件、54 项，0 失败、0 错误、0 跳过。新增 MapIRTest 的 6 项确认实际 IR，并覆盖已知键的实际类型、普通副本、删除后共享视图按键定位、动态接收者与 RHS 前键捕获、浅覆盖合并和自合并、循环与递归 static 写回、查询纯效果、未知键共同值证据、旧布局/非法类型诊断。跨折叠开关及 26.3/1.20.2/1.20.1 执行；无宏目标的未知键写入、删除及查询继续使用普通命令循环。

首次编译修复了校验局部变量重名；随后检查发现遗漏 map<T> 类型语法入口，非法赋值退回旧 visitor 并触发已有属性替换异常。补齐入口后，54 项中剩余两项因执行器不支持合并条件的命令写法失败；恢复既有等价嵌套 execute 写法后全部通过。日志：`F:/DevCache/.codex/runtime/mcfpp-map-ir-final.log`；该次为上述 5 个套件。未重复全量构建或标准库重建，MCFL 仍为 9。

## 上一阶段必要检查：NBT 数组 IR（313886b）

```sh
./gradlew test --tests top.mcfpp.test.NbtArrayIRTest --tests top.mcfpp.test.NbtArrayTest --tests top.mcfpp.test.DynamicIndexIRTest --tests top.mcfpp.test.IRCallTest -Dorg.gradle.jvmargs=-Xmx2g -Pkotlin.daemon.jvmargs=-Xmx2g
git diff --check
```

4 个套件、48 项，0 失败、0 错误、0 跳过。新增 NbtArrayIRTest 的 7 项覆盖实际 IR 入口、数组共享视图与独立擦除副本、byte/long 跨帧精确编码（含 64 位边界）、空数组格式、静态写回前捕获索引、递归和循环，以及非法元素操作/旧目标混合数组编码。折叠开关均覆盖，涉及 26.3/1.20.2/1.20.1。

首轮 47 项中，数组视图一项发现主函数仍退回旧路径；补齐 as 入口后全部通过。随后保留无宏目标上未知长度的字面负索引旧路径，补充回归后 48 项通过。日志：`F:/DevCache/.codex/runtime/mcfpp-array-ir-final.log`；该次为上述 4 个套件。没有重复全量构建或标准库重建，MCFL 保持 9。

## 上一阶段必要检查：列表查询与按值删除 IR（ea7dca8）

```sh
./gradlew test --tests top.mcfpp.test.ListQueryIRTest --tests top.mcfpp.test.ListIRTest --tests top.mcfpp.test.ListMemberTest --tests top.mcfpp.test.ImmutableListTest --tests top.mcfpp.test.DynamicIndexIRTest --tests top.mcfpp.test.EffectAnalysisTest -Dorg.gradle.jvmargs=-Xmx2g -Pkotlin.daemon.jvmargs=-Xmx2g
./gradlew compileKotlin -Dorg.gradle.jvmargs=-Xmx2g -Pkotlin.daemon.jvmargs=-Xmx2g
git diff --check
```

6 个套件、53 项，0 失败、0 错误、0 跳过。新增 ListQueryIRTest 的 5 项覆盖查询纯效果与独立结果、来源不变、空列表、递归删除首项、未匹配保持数据、擦除 bool/int 身份、已知删除的子形状、动态接收者实参副作用、只读列表查询，以及未知类型诊断和显式视图。跨 26.3/1.20.2/1.20 与折叠开关执行；旧浮点及编译器专用查询通过既有成员套件保留。

日志：`F:/DevCache/.codex/runtime/mcfpp-list-query-ir.log`；该次为上述 6 个套件。移除旧后端不再使用的导入和辅助函数后，单独编译检查通过，日志为 `mcfpp-list-query-ir-compile.log`。没有重复全量构建或标准库重建，MCFL 保持 9。

## 上一阶段必要检查：列表变更 IR（21224b0）

```sh
./gradlew test --tests top.mcfpp.test.ListIRTest --tests top.mcfpp.test.ListMemberTest --tests top.mcfpp.test.DictionaryIRTest --tests top.mcfpp.test.DynamicIndexIRTest --tests top.mcfpp.test.CollectionIRTest --tests top.mcfpp.test.EffectAnalysisTest --tests top.mcfpp.test.ValueModelTest -Dorg.gradle.jvmargs=-Xmx2g -Pkotlin.daemon.jvmargs=-Xmx2g
./gradlew test --tests top.mcfpp.test.ListIRTest -Dorg.gradle.jvmargs=-Xmx2g -Pkotlin.daemon.jvmargs=-Xmx2g
git diff --check
```

联合 7 个套件、79 项通过。新增 ListIRTest 的 5 项覆盖三种目标/折叠开关下的七种列表变更、循环增长、嵌套子形状、自追加副本、动态索引参数求值顺序、static 递归接收者效果及非法写入诊断。首次联合检查发现未知长度后丢失共同元素类型；不同长度合流现保留范围类型证据，固定下标可读取共同范围事实，追加保留已有位置。混合类型范围仍拒绝未经视图的具体操作。

空批量操作补充为直接保留原事实，并只复查 ListIRTest 的 5 项，全部通过。日志分别为 `F:/DevCache/.codex/runtime/mcfpp-list-ir-final.log`、`mcfpp-list-ir-empty-batch.log`。测试中的循环块分隔符已修正。MCFL 保持 9，该轮没有全量构建或标准库重建。

## 上一阶段必要检查：字典成员 IR（4bed87f）

```sh
./gradlew test --tests top.mcfpp.test.DictionaryIRTest --tests top.mcfpp.test.DictionaryMemberTest --tests top.mcfpp.test.DynamicIndexIRTest --tests top.mcfpp.test.CollectionIRTest --tests top.mcfpp.test.EffectAnalysisTest -Dorg.gradle.jvmargs=-Xmx2g -Pkotlin.daemon.jvmargs=-Xmx2g
git diff --check
```

5 个套件、57 个测试，0 失败、0 错误、0 跳过。新增 DictionaryIRTest 的 5 项覆盖三个目标/折叠开关下的循环成员调用、递归合并与独立来源、static 精确字段效果、实参副作用前捕获接收者，以及签名和复杂键诊断。首次检查暴露两项旧字典回归：合并字面量缺少泛型上下文；复用声明处的字面量绑定后修复。测试自身的 Test 导入与函数间换行也已修正。

日志：`F:/DevCache/.codex/runtime/mcfpp-dictionary-ir-final.log`；该次共 5 个套件。本轮只增加瞬态 IR/分析，MCFL 保持 9，没有重复全量构建或重建标准库。

## 上一阶段必要检查：动态列表 IR（9f74d5e）

```sh
./gradlew test --tests top.mcfpp.test.DynamicIndexIRTest --tests top.mcfpp.test.CollectionIRTest --tests top.mcfpp.test.CollectionStorageTest --tests top.mcfpp.test.ValueModelTest --tests top.mcfpp.test.IRCallTest --tests top.mcfpp.test.EffectAnalysisTest -Dorg.gradle.jvmargs=-Xmx2g -Pkotlin.daemon.jvmargs=-Xmx2g
git diff --check
```

6 个套件、106 个测试，0 失败、0 错误、0 跳过。DynamicIndexIRTest 新增 13 项，覆盖动态/嵌套索引、RHS 前捕获与负数归一、循环范围类型、独立集合副本、共享视图、递归帧、static 地址写回，以及未知范围写入不伪造全局类型。CollectionStorageTest 的 39 项同时验证复杂键转义、已证明下标可用于无宏目标，以及真正未知的下标被拒绝。

日志：`F:/DevCache/.codex/runtime/mcfpp-dynamic-index-ir-final.log`；该次共 6 个必要套件。按用户最新要求没有重复全量构建；Location/IR 属于瞬态分析数据，未改变序列化签名，MCFL 保持 9，复用上一轮索引。

## 最近完整检查：72dc557

```sh
./gradlew regenerateStdlib -Dorg.gradle.jvmargs=-Xmx2g -Pkotlin.daemon.jvmargs=-Xmx2g
./gradlew check --rerun-tasks -Dorg.gradle.jvmargs=-Xmx2g -Pkotlin.daemon.jvmargs=-Xmx2g
```

- 标准库索引重建：成功，0 语言编译错误，0 语言编译警告。
- 完整 check：成功；346 个测试，0 失败，0 错误，0 跳过；8 个任务实际执行，没有缓存命中。XML 报告共 40 个测试套件。
- 类型内核：21 项；值模型：14 项；基本块端到端路径：12 项；库缓存格式：3 项。
- 转换 API：12 项；结构重解释查询：6 项；特化与调用结果：11 项。
- 已有浮点后端断言：10 项全部通过；算术语法回归全部通过。
- 新增 StorageViewTest：21 项通过；覆盖视图、擦除传递、模板副本、跨帧/递归临时值与副作用屏障。
- 新增 ErasedFlowTest：9 项通过；覆盖擦除 while 不动点绑定、跨目标/折叠一致性、break/continue、不可达过滤、嵌套复制、object 静态限制及动态条件返回知识。
- 新增 CollectionStorageTest：39 项通过；覆盖 list/dict 元素知识、递归独立复制、共享视图、未知范围、动态下标缓存与求值顺序、编译器专用集合的静态擦除通道与物化拒绝、复杂键、混合编码和显式目标能力。
- 新增 CompilerOnlyViewTest：15 项通过；覆盖静态 list/dict/type 的命名视图共享、嵌套擦除字段、普通副本、整体替换、新增字段、旧适配器读取新版本、原始命令屏障、原生 clear/add 回写，以及未知值/动态下标/dynamic 视图声明的诊断。
- 新增 DictionaryMemberTest：16 项通过；覆盖统一签名、静态深合并与独立复制、删除复杂键、部分已知接收者的字段知识、编译器专用字段写入拒绝、未知键诊断、泛型工厂/参数/返回、旧 visitor 帧参数，以及 map 字典投影读取当前数据并保持独立；补充整体合并的标量事实、未知运行时深合并、重叠来源及静态/运行时空键边界。
- 新增 ListMemberTest：19 项通过；覆盖共享签名、前置顺序和负数索引、静态视图更新及独立复制、未知原始值和动态宏索引、未知范围的统一类型、独立查找结果、来源不变、无宏/无 return 目标的按值删除、bool/byte 区分、自追加和清空，以及编译器专用值和混合编码的写前拒绝。
- 新增 MapMemberTest：16 项通过；覆盖共享签名、键覆盖去重、别名与普通副本、参数/返回、浅覆盖嵌套完整值、独立查询、无宏目标的动态键写入/删除、自合并、初始化只执行一次、复杂和空字符串键、投影独立、完整静态视图及额外根字段、运行时编译器值拒绝、旧布局诊断，以及未知键后保持共同值类型与字面量不覆盖同名参数。运行时空键的字典投影有明确诊断断言。
- 新增 ImmutableListTest：6 项通过；覆盖常量/运行时工厂与克隆的类型身份、统一成员表、观察可变来源写入、普通副本/参数/返回独立、无宏旧目标查询、编译器专用静态视图，以及视图/副本/返回值的元素赋值与变更成员拒绝。
- 新增 NbtArrayTest：11 项通过；覆盖三种数组的精确身份和元素值、普通复制与共享视图、参数/返回、byte/long 元素类型、负数索引与已知越界、RHS 调用前捕获索引、多下标独立读取、别名缓存失效、空数组快照和跨帧格式、嵌套及擦除复制，以及 bool/byte 区分和无宏目标诊断。
- 新增 IRCallTest：17 项通过；覆盖实际 Call 生成、前向/相互递归、跨三种目标和折叠开关的参数与返回隔离、static 精确写入与擦除输出类型、未知效果传播、默认参数/重载、循环回边绑定、可达返回检查，以及直接 any 返回的运算、条件、具体参数和 as 视图。
- 新增 EffectAnalysisTest：5 项通过；覆盖普通参数副本不外泄、static 写入跨包装函数映射、相互递归纯效果、未知效果传播、死分支过滤及递归字段路径扩大后收敛。
- 新增 TypeBindingTest：4 项通过；覆盖纯类型查询排序与不修改作用域、歧义阻止父域回退、擦除类型/必需参数限制，以及跨命名空间编译时声明存储前缀稳定。
- 新增 CollectionIRTest：18 项通过；覆盖 list/dict/ImmutableList 的实际 IR、分支/while 元素不动点、嵌套共享视图与独立副本、递归集合形参/返回、static 精确字段效果与类型更新、条件改写及未知效果撤销类型、按顺序捕获载荷/子形状、已知长度与负索引、整体替换清除旧后代、共享导出存储、空字典身份、上下文字面量与泛型不变、只读槽拒绝，以及 void 元素诊断。
- 值模型补充 2 项形状断言；覆盖 fork/复制不共享可变长度、可达路径合流、元素写入保留父长度、根替换和未知屏障撤销形状。
- 新增 ProjectIsolationTest：6 项通过；连续项目初始化清除旧标签、词法/语法缓存、元数据图缓存和编译上下文，同路径文件更新后重新解析。
- git diff --check：通过。

该完整检查在 Windows 使用工作区外的 Temurin 21 与 Gradle 8.14；标准库重建后单独运行 check --rerun-tasks，确保 processResources 使用新索引。
完整检查日志位于 `F:/DevCache/.codex/runtime/mcfpp-collection-ir-stdlib.log`、`mcfpp-collection-ir-check.log`；扩大的集合/调用/存储联合检查为 `mcfpp-collection-ir-expanded.log`，151 项全部通过，追加非法输入检查后的集合套件为 `mcfpp-collection-ir-final-boundary.log`，18 项通过。IR 调用、只读列表、数组、map 和字典阶段日志保留在同一目录；当前 XML 已由最新必要检查更新。

## 反向验证与本轮发现的回归

临时删除 StoredData.write 中的版本失效，单独运行模板共享视图测试，实际失败：预期 14，得到 4。
随后恢复失效代码并运行上述完整检查，所有测试通过。该断言确实能检测旧记分板缓存被重复使用，反向验证改动没有留在源码中。

全量回归修复了累计诊断被首个错误中断、static 浮点写回绑定未跟随调用帧、模板形参读取原型记分板、递归覆盖活跃擦除返回，以及临时参数复制丢失 byte/short 与集合泛型的问题。
模板上转断言改为验证不可变完整快照、额外数据编码及目标成员限制，不再依赖旧 Concrete 实现类。
新增原生宿主集合变更断言，验证未知 MNI 屏障前产生的集合变更被提交到实际 NBT，再撤销事实。
擦除控制流回归在修复前实际出现 9 项中的 8 项失败；完成不动点操作绑定并补充执行器对既有布尔 matches 命令的支持后，相关 53 项和最终完整 170 项通过。
集合元素首批 8 项回归在接入前均实际失败。不同动态下标的读缓存回归也实际失败：预期 11，得到 4；修复为每次未知位置读取分配独立寄存器，已加载适配对象仍按写版本复用，避免重复读取递归。
集合字面量中的调用暴露了旧语法允许相邻表达式而无需逗号的问题；收紧列表语法后，较早元素在后续调用修改来源时仍保留求值时的值。
全量检查中的旧 indexedAssignmentWritesBackThroughProperty 断言只匹配特定记分板写命令，现改为实际执行并断言元素更新为 4、相邻元素仍为 2，以及入口帧平衡。
集合编码的后续回归覆盖已知复杂字符串键、未知键的明确后端诊断、旧目标拒绝混合编码、新目标混合/嵌套/部分已知列表、bool/byte 共享编码和空键路径限制。混合列表先前会被 NBT 工具库的二进制包装送入 SNBT 解析并触发编译进程退出；NbtEncoding 现在编码可见载荷，Tag 副本使用深复制接口。
全套检查还触发同进程多次编译的内存耗尽：load/tick 的全局标签保留所有旧项目函数，静态父类型的 children 列表也保留历次子类型。ProjectIsolationTest 在修复前实际失败；初始化现在清除旧标签引用，并重新加入当前项目的函数。删除无人读取的反向列表，保留名义关系所需的 parent 图；序列化结构变化后将库索引升级为 MCFL 4 并重建，不扩大测试堆内存掩盖引用泄漏。
编译器专用嵌套集合的三项回归在修复前全部实际失败：字典副本共享列表，多层字典副本共享下层数据，直接 type 字典字段误用运行时构造器。普通字典赋值现在递归复制嵌套容器，type 字段直接保留已知编译期值并建立独立字段身份。
随后完整检查再次遇到连续编译的测试进程内存耗尽。Project.init 遗漏清除词法缓存，旧 MCFPPFile 键继续保留文件域和项目引用；同一路径改写后的文件也可能沿用旧词法流。旧当前文件上下文还使新加载的库函数以旧文件域为父级，串联历次项目。两项项目隔离回归在对应修复前分别失败；初始化现同步清除 tokens、trees 和当前文件/函数/强制作用域/模板上下文，未扩大测试堆内存。
清理当前文件上下文后，旧顶层测试暴露了测试入口在解析后才设置当前文件的问题。测试入口现于解析前绑定新文件，语法错误时停止后续阶段，与文件编译路径一致；顶层样例使用当前 var 语法，并断言零错误、常量初值与运行时变量类型。
编译器专用集合的擦除传递回归先复现动态声明生成空载荷、物化及特化判断只看声明类型的问题。校正五个使用尚未支持的括号后直接索引样例后，临时恢复 MCAny 的旧类型检查，静态通道、运行时屏障和嵌套擦除字段的断言再次实际失败，后续错误路径导致测试进程退出；临时检查已恢复。实际内容检查现集中在 StorageAccess，普通擦除副本递归复制编译器载荷；泛型参数端到端验证静态实参复用特化、type 返回与入口帧平衡。
进一步扩展连续编译后仍出现内存耗尽。工作区外的 Eclipse MAT 内存快照分析确认 DataTemplateInfo 类持有的缓存保留 499,629,920 字节，约占该次堆的 93.77%，主要引用来自 infoCache。普通/泛型模板及函数标签元数据的三项隔离回归在修复前实际失败；LibraryMetadata 现在在项目初始化时清除这些图缓存及构建上下文，保留当前项目内的复用和 DataObject 基类型映射。最终完整 216 项通过。测试堆内存设置没有改动；诊断工具、快照和报告均在工作区外。
静态命名 as 视图的写入回归在接入前实际失败：经字典视图将 type 值改为 bool，来源仍为 int；直接静态来源的视图还会被错误地拒绝。完整静态值现在使用 CompilerOnly 布局并共享 Place，已知写入以持久替换更新不可变祖先快照；新增值模型断言验证旧快照不变、兄弟事实保留及未知范围失效。
原生静态 clear/add 回归也实际失败：来源仍读取旧元素。接收者读取后得到的新适配对象曾被按 Var 值相等去重丢弃，宿主变更未提交；现按对象身份保存实际接收者，并将完整静态变更回写同一位置。普通静态擦除副本脱离来源绑定，dynamic 静态视图在调用运行时适配器前诊断。最终完整 232 项通过。
字典成员回归实际复现双成员表不一致，以及 remove/merge 后未改动字段的实际类型被未知原生屏障撤销。字典现共用签名，查询标注无外部写入、变更标注只写接收者并通过 StorageAccess 提交；完整静态深合并复制不可变载荷，复杂键删除及已知标量合并保留兄弟事实。
端到端函数回归随后复现字典参数被临时复制降成 nbt，以及运行时 int 参数已经写入 NBT、callee 却读取未初始化原型记分板。临时复制现在保留语言类型，字典工厂保留泛型，旧 visitor 运行时参数绑定输入帧；int/bool 及 dict<int> 参数/返回的实际执行通过。删除旧字典常量成员类后索引升为 MCFL 5，旧 map 宿主桥接行为由专门断言保留。最终完整 243 项通过。
列表首批 10 项回归在迁入前实际失败 8 项，包括双成员表、前置顺序、负数索引、动态插入及查找结果。修正一个不符合可变泛型不变规则的静态输入视图样例后，统一签名、不可变值更新及独立命令循环使联合 36 项通过；动态索引宏缺少参数值也由实际命令执行发现，索引现先编码到独立参数槽。
继续扩展时，动态插入到统一 int 的 list<any> 丢失实际类型，修复后保留未知范围的共同类型但撤销旧常量。旧浮点 `0.0` 与 `-0.0` 的后端布局相同，按原始位折叠却返回 -1，实际失败回归修复为保留浮点查找的命令后端。预防性检查测试直接比较写入前后的命令和事实，排除声明本身不合法或初始化命令造成的假阳性。相关 91 项及最终完整 262 项通过，标准库为 0 错误/0 警告，MCFL 6 拒绝旧列表成员元数据。
负数插入的 size + index + 1 归一以及批量前置的来源顺序，对照了工作区外已校验的官方 1.20.2 服务端 NBT 路径实现字节码。此对照不等同于运行实际服务端。
继续审查字典时，部分已知接收者合并已知空键会绕过路径能力限制，整体深合并又撤销了确定覆盖的标量字段类型，两项新增回归在修复前实际失败。空键的常量整体合并试验进一步发现当前 NBT 库无法解析含空键的 SNBT 字面量；这条路径现于写入前明确诊断，保持接收者命令和事实不变，完整静态字典仍可合并和查询空键。未知运行时来源使用整体路径深合并，不读取空键子路径；已知标量输入先冻结事实，避免重叠来源在前一字段写入后丢失后续类型。DictionaryMemberTest 的 16 项和最终完整 267 项通过，标准库为 0 错误/0 警告。
map 迁移删除双成员表及键/值双份存储，采用 `{entries:[{key,value}]}`。首批回归暴露旧布局与成员路径问题；迁入后补充擦除 map 回归先因类型事实丢失无法绑定 int 算术，保留共同值类型后实际执行仍得到 4 而非 14。生成命令显示字典字面量的 value 字段覆盖了同名输入参数：把 7 改为 2。字面量现在复用独立捕获位置，不再按字段名分配帧槽，最终该断言得到 14。相关 104 项及最新完整 283 项通过，MCFL 7 拒绝旧 map 成员元数据。
map 的未知键读取把完整 `{key:<字符串>}` compound 作为宏参数插入列表谓词，没有拼接原始字符串成员名。工作区外已校验的官方 1.20.2 CommandMacro.stringify 与 Tag.getAsString/StringTagVisitor 字节码确认 compound 宏参数使用 SNBT 格式；严格执行器补充对应谓词与 compound 插值。1.20.2 的空字符串、引号、点、方括号及反斜线键实际执行通过，1.20.1 的动态键写入、删除与 merge 不生成宏或 return。此对照不等于启动实际服务端。
只读列表首批 6 项回归在迁入前实际失败 5 项：常量工厂丢失 ImmutableList 身份，语言语法不识别该泛型类型。元素和成员写入拒绝测试最初也可能因类型无法解析而通过；现在正向解析/调用断言零错误，拒绝矩阵覆盖视图、普通副本和函数返回值。按语言类型选择成员表后，旧列表仍需显式连接 instanceData；相关 40 项及最终完整检查均通过。
NBT 数组首批 9 项回归在迁入前实际失败 8 项：字面量降为 nbt，下标读取使用默认零值且绕开位置/版本接口。迁入后相关 73 项通过；补充空数组跨帧格式及嵌套/擦除独立复制后，两个新套件的 17 项通过。空数组测试曾错误读取已经移除的函数局部位置，现于返回前将真实帧载荷写到可观察位置，再检查三种 Tag 格式。最终完整 300 项通过，标准库 0 错误/0 警告，MCFL 8 拒绝旧只读列表元数据。
实际 IR 调用首批 8 项中，7 个有效输入在接入前失败；相互递归样例最初缺少语法所需的块分隔，修正后纳入正向验证。接入调用后，循环回边的拒绝样例曾使绑定反复重试；工作区外线程快照确认测试进程正在 PrimitiveCompiler.prepare 循环。仅终止已确认的当前测试进程后，改为逐个嵌套语句收集诊断并保留余下循环体及回边，重复绑定收敛，负向断言正常结束。
调用图的私有准备、类型查询和递归外部写入不动点接入后，相关 80 项通过。后续 static any 输出类型与表达式视图求值顺序回归扩展为 85 项，直接返回视图、递归擦除返回和死分支重载扩展为 88 项。已证明类型的 any 调用结果直接运算，以及常量条件的可达返回检查，在对应修复前实际失败；测试语法修正后的日志为 `mcfpp-ir-call-expression-red-corrected.log`。细化调用结果时又触发两处 any 声明类型断言失败；现在声明签名和运算类型证据分别保存，any 声明、直接运算/条件/重载及显式具体声明均通过。
旧普通参数特化测试只计数某条参数记分板初始化命令，现改为验证三次实际 IR 调用、无普通值特化、执行结果 2/3/4，以及返回没有常量值知识。最后联合 91 项及完整 326 项通过；标准库 0 错误/0 警告，MCFL 9 拒绝旧函数存储元数据。测试堆内存设置未变，线程快照与日志均在工作区外。
集合 IR 的首批 7 项在迁入前均失败，初步接入后的联合 53 项通过。扩大到 135 项时暴露 IR 绕过旧目标混合编码检查，以及 dict<any> 字面量缺少上下文约束；IRCollectionValidation 现于后端生成前检查共同编码、写入前形状和空键能力。已知键直接赋值曾错误读取尚不存在的空键，现跳过最终左值读取；现代目标的混合常量仍保留可见 bool 编码。共享导出适配器的测试最初把手动写入命令附加在已移除的入口帧之后，现先执行真实函数，再单独验证适配器共享事实；该调整不算作产品帧修复。
补查类型及形状的 28 项中有 4 项失败（`mcfpp-collection-ir-boundary-red.log`）：static 元素改写沿用旧 int、条件改写借用输入类型、嵌套捕获丢失原形状，以及空字典推导为不可运行时的 wildcard。调用后的改写位置现先撤销旧类型，再接收无常量值的 callee 类型证据；条件未完全初始化的输出保持未知，未改动兄弟事实不受影响。Read 保存求值时子事实/长度，Construct 和 Write 从捕获快照复制，后续调用不能改变此前操作数的形状；空字典保持 dict<any>。普通 list<any> 返回元素仍须显式 as，相关样例没有假设普通函数会计算常量返回；一个原始命令样例也按语法要求改为独立一行。
非法 void 调用作为集合元素的追加检查曾使测试进程以 1 退出（`mcfpp-collection-ir-void-red.log`），没有生成可用的单项 XML 报告。集合类型查询现在转为逐语句收集的编译诊断，并断言不发布调用/构造命令。最终集合 18 项与完整 346 项通过；标准库 0 错误/0 警告。Function 的 IR/效果和 Var 的存储绑定属于瞬态字段，未改变序列化签名或布局，本轮保持 MCFL 9 并重建索引，测试堆内存设置未变。
动态列表 IR 的初始拒绝检查在进程中断前记录 10 项已执行、9 项失败和 1 项跳过（`mcfpp-dynamic-index-ir-red.log`），没有声称全部新增用例均已失败。迁入后修正了测试中的保留字函数名，并将执行器不支持的条件 store 形式简化为无条件读取长度；随后 66 项通过。未知范围 static 写入的补查使必要检查增至 67 项，确认一次赋值不等于整片范围已初始化。扩到 106 项发现两项复杂字典键路径遗漏转义；IR 地址现复用 StorageAccess.quotedKey。旧无宏测试把有已知值的 dynamic 变量当作未知下标，已同时断言已证明值可生成、普通函数返回的未知值会诊断。最终 106 项全部通过，没有修改执行器来容忍错误命令。
宏生成显式绑定当前帧的 IR 参数，补齐带绑定路径的宏函数 `$` 前缀，两个嵌套运行时下标实际执行通过。目标能力区分宏与 return run：1.20.2 删除 return run，1.20.3 重新加入，IR 在 1.20.2 使用原有条件栈后端。依据 [官方 1.20.2 发布说明](https://www.minecraft.net/en-us/article/minecraft-java-edition-1-20-2) 和 [官方 1.20.3 发布说明](https://www.minecraft.net/en-us/article/minecraft-java-edition-1-20-3)。

完整回归发现并修复了模板安全向上转型时处理额外字段的空指针。
后续回归修复了动态集合的 Java 调试视图被误送入运行时变量构造的问题；编译器对象载荷保留在原生调用边界。
两项旧分支测试要求将续接命令复制到每个分支；它们已迁移为实际执行生成的数据包文件，
验证三条分支路径的结果、续接命令只执行一次和入口栈帧平衡。

## 覆盖行为

- 命名空间、声明类别、别名链、循环别名、容器不变、联合类型与哈希一致性。
- object 与 any 的直线语义、未知 any 操作诊断、重载优先级、缺省参数及泛型绑定方向。
  type 载荷使用内部特化，动态擦除声明拒绝编译器专用载荷；原生 Java 调试视图不创建运行时变量或伪造常量快照。
- NBT 映射数值禁止普通算术和隐式 int/float 赋值；Byte.build 保留实参。
- 常量载荷、类型身份、位置路径和特化键不受来源集合修改影响。
- 分支同常量汇合、不同常量汇合、嵌套 else-if、循环不动点、break、continue 和提前返回。
- 原始命令前提交延迟值，命令后不沿用过期常量。
- int/bool/any/object IR 在分支和 while 循环中独立汇合类型和值知识；同类型保持 Exact，不同类型为 Candidates；操作绑定使用所有可达迭代的类型；折叠开关不改变类型分析。
- any/object 无标签载荷支持未知复制、普通参数/返回及 static 写回；普通返回只传播类型知识，不计算用户函数的常量返回值。
- 普通 int/bool/any/object 自由函数的 Call 实际接入 IR，私有调用图绑定完毕后发布；前向/相互递归效果使用 Pure/Writes/Unknown 不动点，static 写入映射实际 Place，普通参数副本写入不外泄，未证明的调用和原始命令保持未知屏障。
- static any 写回更新可证明的实际类型；any 调用返回可直接绑定运算、布尔条件、具体重载与 as，仍保留 any 声明和未知值知识。调用结果有独立位置，表达式视图按求值顺序读取；常量条件排除不可达缺失返回，dynamic 条件仍检查所有可能返回路径。
- list/dict/ImmutableList 的可编码构造、已知下标/键、复制和共享视图迁入真实 IR 的分支/while；子事实与嵌套长度随读取捕获，static 后续写回不改变较早操作数。元素改写保留兄弟事实和父长度，整体替换清除旧后代，不同长度合流后撤销长度；static 子位置更新/撤销实际类型，不把 ordinary 参数值送入编译期求值。
- 集合普通参数/返回的递归帧载荷独立；只读槽写入拒绝但嵌套可变元素仍可访问。空字典保留运行时身份，字面量上下文不改变普通可变泛型不变规则，void 元素以诊断结束；IR 编码能力检查在命令发布前执行。
- as 使用同一 Place，不调用转换/构造、不补字段、不做运行时类型检查；未使用视图不强制物化；旧浮点的已知标量来源访问明确诊断。
- 模板普通赋值/参数/返回复制载荷，as 共享位置；别名写入撤销重叠缓存并保留已知兄弟字段。
- 求值后保存的操作数及较早参数跨递归调用恢复，后续调用的写回仍保留；未知用户函数/原生函数产生事实和缓存屏障，纯 IR 函数保留事实。
- list/dict 的已知元素、部分已知字面量、擦除载荷和普通副本保留子事实；未知下标写入撤销重叠值并合并可能元素类型，正负已知索引归一到同一位置。
- 列表动态下标接入 IR；Location 记录各层独立捕获结果，负数在 RHS 改写容器前归一，static 写回与命名视图保留原地址。未知范围读取保留共同类型/形状但无常量值，未知写入合并可能类型、撤销重叠后代；普通副本独立。已知 any 下标采用实际 int/string 类型，byte 不因继承获得 int 下标签名；无宏目标拒绝未知下标，dynamic 布局不撤销已证明的下标值。
- 编译器专用列表拒绝 dynamic 物化，静态副本和已知下标仍可使用；混合字面量保留完整联合身份，含编译器专用分支的联合拒绝物化；空列表保持运行时表示。
- list/dict 的编译器专用内容经 any/object 保留内部静态载荷，普通副本递归复制嵌套擦除字段；动态声明和显式物化在载荷写入前诊断，运行时屏障不物化这些值。完整静态实参的特化键保持不可变，泛型调用复用相同静态载荷的函数体。
- 完整静态 list/dict/type 的 as 共享 CompilerOnly 位置和写版本，原始命令不撤销静态事实；已知子位置写入及整体替换更新所有视图的读值，祖先完整快照重新生成，旧快照和普通副本仍独立；原生 list clear/add 的宿主变更提交到同一位置。
- 字典常量/运行时共用四个签名及接收者写入接口，已知删除/标量合并保留未改动字段；完整静态深合并与输入副本独立。查询和删除使用统一键能力限制，运行时接收者拒绝编译器专用字段。
- 字典整体深合并保留可证明的标量输入类型和值，重叠来源先冻结事实；禁止空键路径的目标对已知空键输入写入前诊断，完整静态字典不受运行时编码限制，未知运行时输入继续整体深合并。路径限制依据 [官方 1.21.5 NBT 说明](https://www.minecraft.net/en-us/article/minecraft-java-edition-1-21-5)；当前库的空键 SNBT 拒绝由实际解析失败确认。
- list 的 11 个成员共用签名与接收者写入；完整常量及静态视图更新不可变快照，已知变更重排元素子事实和嵌套长度，未知变更撤销旧常量并保留统一类型。自追加先复制输入，普通查询不改写来源，结果不共享静态记分板；擦除元素按已证明类型过滤，不增加运行时标签。浮点查询保留实际后端，按值删除支持无宏/无 return 的旧目标。
- map 的 6 个成员共用值/位置后端；索引覆盖不重复追加键，merge 替换整个 value 并冻结重叠来源。普通 map 副本/参数/返回及 keys/keyValueSet 投影独立，as 和静态视图写入共享同一位置；静态 remove/clear 保留额外根字段。未知键写入撤销旧值并合并可能实际类型，运行时接收者在写前拒绝编译器专用值。
- ImmutableList 的只读成员与 list 查询共用后端；工厂和复制保留身份，元素槽和列表变更成员明确拒绝。as 共享来源位置并观察可变来源的新版本，普通副本/参数/返回独立，编译器专用内容保持静态载荷。泛型仍不变，嵌套元素自身的可变接口保持其类型约束。
- ByteArray/IntArray/LongArray 字面量、不可变快照及复制保留原数组格式和 byte/int/long 元素身份；负数索引归一、已知长度越界诊断、动态索引捕获、别名缓存失效与集中元素位置一致。空数组通过真实参数/返回载荷搬运验证，嵌套与擦除普通副本保持独立；无宏目标拒绝未知索引。
- 已知字典键中的点、空格、引号和反斜线作为字面名称访问；dynamic 字符串物化保留常量事实。未知字典键仍明确拒绝生成；map 字符串键以值比较并通过 compound 宏谓词访问，未知键的字典投影尚不可生成，运行时空键的字典投影明确诊断。
- 旧目标的列表构造和元素写入检查共同 NBT 编码，bool/byte 仍保持不同类型身份；1.21.5 起允许可见混合列表并拒绝空键路径。依据 [官方 1.21.5 NBT 说明](https://www.minecraft.net/en-us/article/minecraft-java-edition-1-21-5)。
- 普通 int/bool 形参、循环和返回接入同一基本块路径；普通参数值不增加特化数量，泛型缓存保留类型和值实参。
- 连续和嵌套调用的整数、布尔结果分别保存；动态分支的最后一个返回不能变成函数常量；遗漏返回路径产生诊断。
- 模板结构查询递归处理只读字段、可写不变、可选字段、私有字段及抽象能力，不改变普通子类型关系。
- 数值转换具有具体源重载；整数窄化在边界上比较常量与命令结果；long/double 到 int 保留后端读取操作。
- 旧浮点栈保存、恢复和 NBT 编码保留四个分量；工作寄存器不携带过期宿主常量；旧算术和比较保留模拟库调用。
- 库索引保存透明别名和接口标记；未解析模板在 Kryo 循环图读取中保留稳定身份；函数存储前缀不随当前调用方的编译命名空间改变。
- 擦除载荷结构变化后曾升级至 MCFL 3，移除旧反向子类型列表后升级为 MCFL 4；删除字典常量成员类后升级为 MCFL 5，统一列表成员后升级为 MCFL 6，统一 map 成员及布局后升级为 MCFL 7，只读列表成员与数组快照迁移后升级为 MCFL 8，IR 调用及声明存储前缀迁移后升级为 MCFL 9，格式拒绝测试包含上一版本索引。
- 开启和关闭基本块常量折叠时，有符号整数溢出结果及命令副作用顺序一致。
- 旧目标的基本块输出不使用不受支持的 return；递归分支条件栈和入口帧均平衡。
  原始命令嵌套调用另一个基本块函数时，不清空调用方的分支条件栈。
- 已知的 26.3 能力选择数值提供器，未知未来版本不会自动选择后端。

## 未完成验收

这些结果验证的是 migration.md 中已接入的范围，不能替代整份重构方案的验收。
其余集合成员、map/NBT 数组与编译器专用集合的 IR，未知长度上的负数字面下标、集合返回/static 整体替换的完整子形状，模板/浮点及泛型/T! 的调用与控制流、其余循环语法、未知字典字符串键的运行时后端、其余原生成员编码检查、全局/实体位置的递归效果及完整擦除返回类型不动点、模板方法/构造、其余布局和转换后端、MNI 全面迁移及旧体系移除仍列在迁移指南中。原始命令直接跨函数修改物理记分板与帧恢复的关系仍需核实。
as、擦除载荷和存储已经有实际贯通路径；不能把这些测试外推为全部语言类型和调用路径均已完成。
旧浮点测试验证分量搬运和命令结构，未证明模拟库的全部数值精度、舍入及异常行为。
旧测试仍有仅打印诊断的用例；测试通过不能证明其全部输入都符合新语义。
没有配置可运行的 Minecraft 目标服务端，实际服务端验证尚未完成。

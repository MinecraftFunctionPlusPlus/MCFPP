# 下一阶段：迁移 Datapack 与 Debug 接口（阶段111）

阶段87普通值位置type拒绝规则继续生效。阶段88–110完成受测集合、数值、文本转换/拼接、predicate、StdCommands void/命令结果、System print/诊断、delegated-int模板、Time路径及两个legacy分支入口的限定迁移；当前库格式MCFL39。whole17仍未完成。

阶段90已验证13个方法：Dictionary 4、Map 6、ImmutableList 3，复用既有context/API，不新增context入口或扩展operator。保持字典已知key限制，Map允许dynamic key，readonly list允许dynamic needle。fixture与旧/未知缓存格式回归跨轮各自通过，最终fresh fixture单项复查source/fresh磁盘结果及frame0。MCFL22重建成功，具体轮次见verification.md。

阶段93已迁移ConversionData的49个入口；阶段94完成toText入口限定验证；阶段95改为匹配显式Java方法名；阶段96完成text receiver赋值与拼接限定验证；阶段97统一String/NBT toText入口；阶段98恢复T!文本快照；阶段99迁移DataObjectData.toText；阶段100限定纯text JSON转义。分轮结果见verification.md。

### 阶段 96：text 与拼接运算（已限定验证）

接通text的runtime receiver赋值与三个构造路径；两项concat operator走单context，JsonTextConcreteData重复注入改为复用JsonTextData。MCFL26→27。fixture的source与fresh consumer磁盘断言覆盖12项text payload、original/copy、return7和frame0；不能据此宣称未知MNI屏障后的常量折叠分支、聊天渲染或跨帧寿命均已验证。final3仅单fixture复查，Cache在首轮独立通过。

### 阶段 97：String/NBT toText receiver 分流（已限定验证）

String与NBT的toText入口已按实际receiver状态合并到各自单一Java类；移除重复Concrete入口，不扩展到其他native/effects。MCFL27→28。指定fixture与Cache回归通过；两个toText输入是literal，但拼接结果仍由临时数组append形成，尚未证明AB/AS即时折叠。

### 阶段 98：T! text常量拼接与普通copy（已限定验证）

`StorageAccess.restore`恢复完整text ListTag快照为immutable encoded component，并保留声明类型。已复用单个fixture验证T! copy/concat的source与fresh磁盘结果A/A/AB/AS、return7/frame0；joined/suffixed由直接literal component list生成。MCFL28不变。

### 阶段 99：DataObjectData.toText显式context（已限定验证）

仅迁移 `DataObjectData.toText`，复用旧DTO SNBT/runtimeNBTChat逻辑；JavaVar与Any路径保持现状。普通Payload/Box fixture从source/fresh磁盘执行，验证字段7、返回7及NBT组件；普通data构造使用 `buildUnConcrete`，不代表Concrete覆盖。MCFL28→29，必要stdlib与Cache回归已通过。

### 阶段 100：PlainChatComponent JSON字符串转义（已限定验证）

PlainChatComponent纯文本使用Fastjson2 `JSON.toJSONString` 编码单个字符串。source/fresh fixture通过带引号、反斜线的 `toText`、T!副本/拼接与NBT字符串 `toText`。MCFL29不变；不扩展控制字符、style、decoder或聊天渲染。

### 阶段 101：LootTablePredicateData.pass/fail（已限定验证）

`LootTablePredicateData.pass/fail` 两个bool入口迁入context；`NativePredicateOperations`复用 `CommandBoolPart` 的 `if/unless predicate`，通过现有结果发布路径得到 `ScoreBool`，id读取使用 `PropertyVar.get`。不改静态 `of`/factory、effects或world模拟。source/fresh库往返检查生成命令，Cache回归通过；MCFL30。执行器不模拟world predicate，不据此声称世界侧求值或frame0。结果见verification.md。

### 阶段 102：StdCommands void 方法（已限定验证）

16个不受Java primitive-float签名冲突影响的void语言方法迁入显式context；`place`/playsound仍可有 `MCFloat` 参数。保留damage的4个primitive-float入口及其他107个 `CommandReturn` 返回入口，不触碰这些真实消费边界。单context Java方法使用唯一名称及原annotation identifier，保留语言identifier、参数默认值、effects和原命令词序。`ResourceID.toCommandPart`以 `@MNIFunction.override=true` 声明替换失效的源码覆盖，并在Java wrapper中读取 `id.toCommandPart`。

`NativeStdCommandOperations` 私有emit helper在 `context.withArguments` 中构造原命令，按 `buildMacroFunction()` 顺序添加准备命令，最后调用一次；非宏路径直接发出自身，不加return-run，也不经旧 `processMacroCommandReturn`。source/fresh fixture使用 `TemplatePool()`、设置 `pool.id`，在 `Box.observe(target as string)` 中调用 `place(pool, "fixture:constant", 2)` 与 `place(pool, target, 2)`；main传literal，但普通形参 `target` 在调用体内仍是未知值。两个place调用实际均生成一次macro，准备参数先于调用且宏体无return-run。stdlib2和失败fixture修复后单项通过；Cache只首轮通过。MCFL31；不据此声称非宏路径或world执行已覆盖。详见verification.md。

### 阶段 103：damage void 方法（已限定验证）

四个damage void接口已迁入显式context，处理旧Java primitive-float、`DamageType`和selector（实际为 `SelectorVar`）参数签名；DamageType为ResourceID data。保留语言标识及默认语义。浮点能力沿用 `FloatProviders.enabled`，未建新浮点引擎。

source/fresh fixture验证DamageType初始化为 `minecraft:generic`、damage宏的参数准备与调用顺序，以及旧目标下direct-context动态float拒绝；常量沿现有编码路径。标准库和Cache联合回归成功，MCFL32、三份资源同hash。没有验证world或float执行；详见verification.md。

### 阶段 104：System print 原生入口（已限定验证）

九种print语言入口已接入单context；`NativePrintOperations`按既有组件导出，合法 `list<*>`/`dict<*>` 使用native pattern桥接和闭合pattern codec。DTO输出TODO占位与无外部调用的 `printVar` 已删除。MCFL33；分轮验证及边界见verification.md。最终仅print fixture复查通过，Cache仅首轮通过，不代表最终joint2全绿。未验证macro/world执行、frame清理或tellraw渲染。

### 阶段 105：delegated-int 模板基础（已限定验证）

`data Seconds as int` delegated整数wrapper的普通参数初始化、默认构造与dispatch已接通；typed copy保持独立Place，`as int`视图共享原Place，immutable typed snapshot/restore、codec与ConstructorInfo恢复已接通。MCFL33→34。RED的构造器失败修复后，指定fixture与Cache回归共4项全过；source/fresh磁盘最终值494和frame0通过，4/9仅为中间读数。详见verification.md。不扩展Time或其他MNI。

### 阶段 106：Time 显式原生接口（已限定验证）

`TimeData`的12项运算及`TimeObjectData`的6个factory已迁到单context；context的`declaredReturnType`由真实callee给出，Time结果使用独立Place，六倍率factory移除GlobalScope静态缓存且倍率不变。FieldInfo恢复operators，DataTemplateInfo支持self-signature，MCFL35→36。指定fixture最终复查的source/fresh结果1027809与frame0通过；Cache仅MCFL35首轮通过，未复跑MCFL36。详细轮次和未验证边界见verification.md。

### 阶段 107：恢复动态分支中的局部值（已限定验证）

在legacy动态if的两个分支入口修复共享score名的局部值读取：score缓存owner使用实际Function对象identity；父函数在条件跳转前flush可见runtime bindings，使lazy初始化支配分支路径。单source/fresh fixture真实磁盘结果10/7和frame0通过。未扩至CFG/loop/全量facts；MCFL36不变，详见verification.md。

### 阶段 108：System诊断接口（已限定验证）

删除没有合法普通 `type` 返回路径的 `System.typeOf`；debug/info/warn/error四个void方法迁入显式context并标记 `NoExternalWrites`。编译期concrete内容和runtime宿主 `toString` 诊断语义保留，不生成runtime NBT。MCFL36→37；两个定向方法和标准库已通过，详见verification.md。

### 阶段 109：捕获 seed 的命令结果（已限定验证）

普通 `CommandResult` 公开只读 `result:int`/`success:bool`；seed使用单context与真实declaredReturnType建立未知事实，通过root `{}`及一次双 `execute store` 发布。source/fresh模型和磁盘命令合同限定验证通过；不模拟世界结果或声明frame0。MCFL37→38，标准库已重建；详细轮次见verification.md。

### 阶段 110：迁移 StdCommands 命令结果接口（已限定验证）

StdCommands剩余106个旧CommandReturn入口迁到单context；127个Java入口统一为单context签名，107个返回qualified `CommandResult`、20个void。参数默认值、语言标识、effects和文字顺序保留，宏实参通过真实withArguments捕获，未知float仍受既有guard限制。fixture只覆盖seed/help/say，不代表全部命令world验证。MCFL38→39；stdlib和两项必要测试通过，详见verification.md。

StdCommands以外另有14个Java类：91个CommandReturn返回注解和7个void旧wrapper，未纳入阶段110；whole17仍未完成。

### 阶段 111 计划：迁移 Datapack 与 Debug

限定Datapack九个方法及Debug.start/stop两个方法。`Op`、`Datapack`、`Debug` 三个静态类当前没有导出的语言对象（MinecraftData为空）；Datapack fixture使用真实 `@From` 对象 `Packs`，Debug两项沿已有显式native声明验证。FunctionVar/TODO及Op/Recipe的Player!参数合同留待后续。已发现 `Op.deop` 当前构造的是 `op` 命令，迁移时应修正并验证，但不把它描述为本阶段已解决。预计Java ABI变化将MCFL39→40并重建标准库；尚未实现或验证。

阶段88–110详细实施与分轮验证见verification.md；source/fresh consumer磁盘断言范围见各阶段记录。标准库和项目资源当前使用MCFL39。
先阅读 [最新续接记录](./session-continuation-2026-10-04.md) 与 [迁移状态](./migration.md)，原始用户约束保留在 [上一会话交接](./session-handoff-2026-10-04.md)。
上一会话的 140 项测试是此次基线；最新完整结果以 [验证记录](./verification.md) 为准。

## 已完成的纵向路径与优先剩余工作

- StorageAccess 连接声明、字段、视图、延迟 NBT 物化和记分板缓存，写入使用位置版本失效，兄弟字段事实可保留。
- any/object 共用无标签载荷，删除 lastVar；int/bool/any/object IR 的分支和普通返回保留独立类型知识。
- 语言 as 使用同一 Place 的 TypedView，普通来源复用纯结构检查；模板赋值仍复制，旧浮点标量布局拒绝实际访问。
- 擦除/模板形参返回、static 写回、表达式活跃临时值及早先参数已加入实际跨帧和递归测试。
- 受限 IR 调用图推导 Pure/Writes/Unknown 的递归不动点；static 形参写入映射实际参数位置，普通参数副本写入不外泄。未知用户调用与未标注 MNI 有提交/失效屏障，数值 MNI 明确标注无外部写入。
- 2026-10-05 继续接入擦除 while 循环：操作绑定先读取完整控制流的不动点事实，包含 break/continue/return 与不可达过滤；动态条件元数据同时用于普通返回类型分析。
- list/dict 的直线元素读写接入 StorageAccess，保留子元素实际类型、复制子事实与共享视图；动态下标按求值顺序捕获，未知位置的读寄存器彼此独立；编译器专用元素不进入运行时 NBT。普通集合不得因此承载 `type` 值。
- 已知字符串键的点、空格、引号和反斜线完整转义；未知字符串键明确拒绝生成。列表保留完整元素联合身份，集中编码使用可见载荷，旧目标对混合/未证明共同编码的列表构造与元素写入进行能力检查，空键路径由能力表控制。
- [旧假设待迁移] 编译器专用集合赋值递归复制嵌套列表/字典；此前“type 字典字段从完整值建立静态字段”的正向行为不构成语言规则，需改为拒绝普通字段/容器保存 `type` 值。
- list/dict 的实际内容决定能否进入运行时载荷；编译器专用擦除字段保留内部静态通道，普通副本递归复制，屏障和调用帧跳过不可物化的值，泛型按完整不可变静态实参特化。
- 完整静态 list/dict/type 的命名 as 视图使用 CompilerOnly 布局并共享 Place/写版本；已知写入重建不可变祖先快照，普通复制读取最新值并脱离来源绑定；静态 list 的 clear/add 变更回写事实，运行时屏障保留静态位置。
- 字典常量/运行时成员合为一份签名；clear/remove/merge 通过接收者位置写入，未改动字段保留事实，containsKey/remove 复用复杂键和未知键限制；MCFL 升至 5。旧 visitor 的运行时形参绑定输入帧，NBT 临时值及字典工厂保留语言类型和泛型。
- list 的 11 个成员统一到 NBTListData/ListOperations；变更重排已知元素事实、撤销未知范围常量并保留统一实际类型，复制来源后处理自追加。查找结果独立、按值删除支持无宏/无 return 目标；浮点查找保持后端编码，编译器专用值和旧目标混合编码在写入前检查，MCFL 升至 6。旧 Concrete 成员类暂供 map/ImmutableList 使用。
- 字典整体合并保留已知标量字段类型和值，重叠来源的事实先冻结再回写；已知空键输入在不支持该路径的运行时目标上明确诊断，完整静态字典可处理空键。现有 NBT 库拒绝含空键的 SNBT 字面量，不伪造可用的常量整块合并；未知运行时输入仍使用整体路径复制。
- map 的 6 个成员共用 NBTMapData/MapOperations，删除常量成员类；采用唯一 entry 列表，浅覆盖整个 value。已知键写入保持位置，普通副本与投影独立，完整静态 as 共享不可变祖先更新；未知键写入保留共同实际类型并撤销常量。键作为字符串值比较；读取未知键需要 compound 宏谓词，写入/查找/删除/merge 支持无宏目标。MCFL 升至 7，旧双份 map 数据不自动转换。
- 字典字面量字段复用独立捕获结果，不再用字段名分配帧位置；修复字段 value 覆盖函数同名参数的实际回归。map keys 为独立投影，keyValueSet 仅能投影已知键，运行时空键明确诊断。
- ImmutableList<T> 接入类型语法、统一只读成员和列表元素位置；工厂、普通复制、参数/返回保留只读身份，as 观察来源写入，普通副本独立，元素槽写入和列表变更成员明确拒绝。删除最后的 NBTListConcreteData；不新增泛型协变。
- ByteArray/IntArray/LongArray 字面量和不可变快照保留精确格式，含空数组；元素位置与版本、已知长度和负数索引、普通复制及运行时参数/返回接入 StorageAccess。byte/int/long 元素身份互不混淆，未知索引在右侧调用前捕获，无宏目标明确诊断；MCFL 升至 8。
- 普通自由函数的 int/bool/any/object 调用实际迁入 IR；前向与相互递归在私有图上完成类型/效果绑定后一次发布。纯类型查询复用重载排序及默认参数，具体调用根据可达路径和循环回边绑定，不根据首次迭代或临时候选猜测。
- 标量和擦除调用用独立帧槽保存操作数、早先参数与返回，static 写回保留其他位置的事实并更新擦除实际类型；直接调用返回值具有 Place，表达式 as 按求值顺序读取。已知 any 返回可直接绑定运算、条件与重载，保持 any 声明，普通返回的值知识仍未知。函数物理存储前缀按声明命名空间确定，MCFL 升至 9。
- list/dict/ImmutableList 的可编码字面量、已知下标/键、普通复制及命名共享视图接入实际 IR 的分支和 while；元素类型采用回边不动点，读取捕获独立子事实和嵌套长度，整体替换撤销旧后代。可编码集合的普通形参/返回和递归载荷保持独立，static 已知字段写入映射位置并传播类型证据，条件改写和未知效果撤销旧元素类型。
- IR 后端生成前检查旧目标的共同列表编码及空键能力；空字典保持 dict<any> 的运行时身份，上下文字面量不引入可变泛型协变。只读槽写入拒绝，嵌套可变元素仍可操作；void 调用作为集合元素产生诊断。
- 列表动态下标接入 IR；Location 区分逻辑范围与捕获地址，负数在 RHS 前归一，嵌套读、共享视图及 static 写回保持原索引。未知读取合并共同类型/形状，未知写入合并可能类型并撤销重叠后代，不把一次写入视为整个范围已赋值。宏直接使用当前帧的 IR 参数；1.20.2 的控制流不用 return run。
- 可编码字典的 clear/remove/merge/containsKey 接入 IR，类型绑定复用原生签名，字面量实参使用泛型上下文。查询只读，删除映射字段效果，清空与合并写入接收者；合并从冻结来源递归传播字段事实，动态接收者保留实参求值前的索引。
- 列表 add/prepend/addAll/prependAll/insert/removeAt/clear 接入 IR 与接收者效果；已知变更重排完整子树，批量输入先捕获，空批量保留事实。不同长度合流保留共同元素类型，追加保留原位置，未知位置变更撤销不再可靠的形状；旧目标编码、宏和已知越界在生成前检查。
- list/ImmutableList 的 indexOf/lastIndexOf/contains 和可变列表 remove 已进入 IR；查询纯读，删除只移除首个匹配项并具有接收者效果。IR 与旧入口共用 ListValues/ListSearch，擦除列表按已证明类型选择位置，未知类型要求视图；已知删除移动剩余子形状，运行时支持无宏目标及递归帧。

- ByteArray/IntArray/LongArray 与 byte/long 载荷接入 IR 的字面量、索引、共享视图、参数/返回、循环和递归；复用数组格式快照和捕获地址，64 位整数载荷保持精确。支持宏的目标可归一未知长度上的负数字面下标，无宏目标保留原入口。MCFL 保持 9。

- 可编码 map 的字符串下标、clear/remove/merge/containsKey/size/isEmpty 接入 IR；更新保持唯一 entries 布局，合并浅覆盖，普通副本独立，视图保存完整键谓词。MapFacts 保留已知键子形状和未知键共同类型，MapCommands 与旧入口共用运行时循环；static 变更记录 entries 效果，已验证循环/递归和旧无宏目标。
- keys/keyValueSet 接入纯读 MapProjection，结果独立拥有载荷与位置，子类型/常量/嵌套长度随快照复制；支持直接下标、成员查询、循环中重新投影及键列表递归返回。键列表的运行时循环与旧入口共用；字典投影继续要求已知且非空的运行时字段名，编译器专用投影仍走旧边界。

- do…while 接入先体后条件的 IR 回边，continue 检查条件，break/return 保留独立退出；直接闭合整数区间 for 使用普通基本块，边界按顺序只求值一次，迭代变量每轮复制计数器，嵌套同名变量不覆盖外层。包含上界且在递增前检查终止，避免最大整数溢出；不调用 Java 迭代器或在编译期逐项展开。命名 range 值、浮点范围和通用迭代器仍使用旧边界。

- IR 的局部声明按词法块检查重名，初始化先读取外层绑定；同级分支和嵌套块的同名变量具有不同声明 ID/存储槽。源码名称留在 Symbol，后台名称单独分配；foreach 变量归入循环体，越界引用不会退回旧入口泄漏变量，局部视图共享真实来源，循环/递归局部存储隔离。

- 普通调用的返回及 static 整体替换已传递子类型与嵌套长度，输入/返回快照清除常量值，早先参数保留求值时形状；不同返回分支只保留共同证据。调用方从 TypeId 登记嵌套容器类型，无宏负索引在形状绑定完成后决定是否回退；递归子形状仍保守。

- 整数范围值已接入 IR 构造、复制、视图、参数/返回、static 替换和命名/嵌套迭代；两端须有 int 证据，循环捕获后不受原范围改写影响。旧入口保留 int/float 身份，共用 compound 快照和存储接口，返回槽和跨调用捕获独立；iterator 签名统一，旧常量迭代惰性生成，未迁入的运行时迭代给诊断。MCFL 升至 10 并重建索引。

- 同一类型/形状输入的直接与相互递归已有返回/写回不动点，从无返回路径开始求解，保留共同子类型和长度并清除值知识。始终不返回的调用不会证明后续返回值；嵌套递归输出只保留共同有限形状。递归输入类型/形状变化时仍给保守摘要，不复用另一载荷的证明。

阶段 47–56 历史进展见 verification.md。阶段 56 已将有序字段 RHS 持久化在 `DataTemplateInfo`，`preInit` 使用 `LinkedHashMap`；`GenericDataTemplateInfo` 复用已有 body AST。源码构造器编译恢复声明文件与命名空间；导入构造器 transient file 仍为 null 并依赖 caller，词法 scope 尚未完整持久化。Kryo reader 先 reference 再 nested read；生产与字符串测试共享 `MCFPPFile.resolveImports`。MCFL 12 标准库重建成功，语言错误/警告为 0；阶段56最终13项通过。阶段57实现及验证详见 verification.md。consume 阶段此前记录的 9119 项 `flatExtends` 重复继承字段警告仍待清理；object 自动 load 未验证。没有运行完整 check 或实际服务器。

### 阶段 57：模板 const 字段初始化（已完成）

已实现：`sharedProject.prepareObjectInitializers` 在 annotation、完整签名和继承 ready 后、用户函数 body 编译前编译完整本地 object constructor，并由已有 body guard 防止重复。source inferred object 字段上下文/访问/annotation 按声明顺序暂存，FieldVisitor 不试算 RHS，真实 constructor `prepareBody` 单次求值并补 field/property/Symbol；typed const RHS 同样登记。constructor 先绑定 incoming 参数，再求 RHS，两个 receiver 1/2 已实际验证。

const 只限制重赋；compiler-only const 保留完整 `ValueSnapshot` 且不物化，`T!` 独立检查完整值，inferred mirrored 不继承约束。field annotation 在 annotation 转存后触发 helper 补 stage；函数 annotation 从真实 AST 声明取得。错误 RHS 不写默认值，self/forward 引用给清晰诊断。普通模板 typed const 允许每实例初始化。

阶段57历史：首轮28项27通过/1测试夹具失败；修正负向source换行后定向复查该方法1项通过，没有最终联合28项复跑。MCFL12 schema不变，stdlib含本地20个Slot inferred fields后为267356 bytes。记录见verification.md。导入RHS lexical scope、import object自动load仍待处理；phase56 consume记录的`flatExtends`重复继承警告未清理。

### 阶段 58：普通模板推断字段声明绑定（受限支持）

阶段58已接入：普通模板 inferred fields 复用 `PrimitiveCompiler` 私有图的 Lowering/FlowAnalysis/ReturnTypeAnalysis 做 pure declaration binding，不发布 IR、不执行用户函数、不生成命令。普通构造参数与未绑定 T! 保持 Unknown，同字段 across ctor overload 要求同一 TypeId；支持 this 单字段/此前字段。anonymous 统一队列，泛型类型实参绑定后的实例独立完成，继承后annotations与模板参数/返回 adapter刷新并保留Symbol/Place。Runtime receiver按声明类型初始化可编码默认字段；Unknown erased不伪造snapshot，NBT遇不可编码child返回整体null；普通模板局部声明buildUnConcrete防止DataOnly空地址写入。pure AST binding与runtime AST generation分离，不重复执行RHS。native/generic/compiler-only/static、多级this/member method等未迁入语法继续legacy `extraFunction`。阶段58验证37个不同用例跨轮各自通过，非一次联合37项。

### 阶段 59：导入构造 RHS 的声明词法环境

阶段59已实现导入声明环境恢复：仅持久化 `namespace` 与 `unsolvedImports`，不保存整个 `FileScope` 或 `Project`；读取全部 includes 后，在各自声明文件的 FileScope 恢复 `currFile` 与声明环境；普通函数、构造器和泛型特化均经统一 compile 入口在各自环境编译。MCFL 13 schema 已重建并通过必要回归。消费端仍缺物理库函数 body；方法 owner 恢复和 imported object 自动 load 也未解决，分别保留为后续边界。

### 阶段 60：导出消费端重编译的库函数主体

阶段60已受限实现并以磁盘consumer测试验证：`DatapackCreator`新增库导出入口仅导出 `bodyCompiled` 的非 Native 函数，按实际 namespace ID 写入；collector递归 `compiledFunctions`、`GenericDataTemplate.compiledTemplates`、模板接口/对象/companion，并用 identity visited 去重。generic wrapper写出非空函数体且prototype不导出。方法owner恢复与 imported object 自动 load仍为独立缺口。

### 阶段 61：恢复导入成员的 owner 与模板 scope

限定修改 `FieldInfo`、`DataTemplateInfo`、`FunctionInfo`：`FieldInfo`保留无参 `get()` 兼容，新增带 `DataTemplate` owner 的get并复用 `restore(owner?)`；先令目标模板 `owner.scope` 指向新 canonical field，再恢复字段。修复本地方法 owner，并将方法 `scope.parent` 以 `add(0, field)` 接到canonical scope首位；保留原 FileScope、types及null语义。普通/泛型模板通过 `field.get(template)`恢复。三个 `FunctionInfo` get恢复已保存的 `isAbstract`。参数NBT/score/return/Symbol/Place不含owner，不重建或重绑。无schema变化；imported object自动load仍独立待办。阶段61首轮26项24通过/2失败，修正grammar中var函数调用优先级与object测试fixture初始化后，仅复查两失败方法通过；26个不同用例跨轮全通过，非单次最终联合26项。

### 阶段 62：修复模块资源路径恢复

阶段62已保存目录/JAR/ZIP模块的真实`resourcePath`，统一归档资源的`datapack/`前缀，解压时剥除完整`sourceDir/`，保留没有packages字段的base-only module，并关闭归档读取句柄。三种真实库来源精确复制函数与tag文件，关闭copyImport后不导出Imports；与模板初始化联合11项通过。MCFL13/bin282180未变。imported object autoLoad所有权仍待独立界定。

### 阶段 63：持久化函数访问修饰符

持久化并恢复普通、generic、native函数的`accessModifier`；generic特化已有Function复制权限，不重复新增传递逻辑。成员检查须使用真实声明模板，表达式NoStackFunction须保留词法访问上下文，继承成员须以原声明owner判定权限。升级MCFL并重建stdlib，用真实库往返验证内部private/protected调用合法、外部及子类对基类private的访问被拒绝。final源码语义及metadata缺口独立保留，不增加无消费者的`isFinal`字段。imported object autoLoad所有权不纳入本阶段承诺。阶段63已以MCFL14真实库往返验证通过；首轮14项13通过/1失败，根因是model注入NativeFunction修改PRIVATE后第二次genIndex复用PUBLIC写快照。修正writer重写缓存后，最终LibMemberAccess3 + TemplateInitialization8 + LibCacheFormat3联合14项全过；两次stdlib Project语言诊断均0错误/0警告，最终bin285207 bytes。

### 阶段 66：当前实例未限定字段寻址（已验证）

仅当来源是`CompoundDataScope`中的普通实例字段声明，且当前scope原始`getVar("this")`取到真实`DataTemplateObject`，才调用`receiver.getMemberVar(key, caller)`。更近FunctionScope局部保持原始raw结果（含`fieldVarSet` null）；object/static、无owner readonly、无receiver与未解析名继续走原路径。producer/consumer真实库往返覆盖未限定constructor写、if→while写、第二实例及shadow/protected读取，结果5/9/8/6；LibFieldAccess4 + ConstructorExecution7单次11项通过。未改`Internal`全局lookup/putVar或frame布局，详见verification.md。

### 阶段 67：generic 类只读签名与源码特化（已验证）

限定路径已实现并通过真实库往返：prototype readonly签名和源码特化入口连接，generic kind/parent factory经`AbstractTemplateInfo`恢复，构造器恢复接受明确owner。不可变完整实参快照同时定义`SpecializationKey`与原型派生`TypeId`；readonly实参独立保留为CompilerOnly静态绑定，不进入运行时重定位、默认字段载荷或参数物化。完整值在`Var.assignedBy`的dynamic转换前冻结；已知完整动态局部n可传递，无法证明完整值的runtime parameter n即使调用处传3也拒绝。ordinary generic constructor body改由原lazy编译入口执行。producer/反向consumer身份、private owner和磁盘结果均按阶段验证记录通过；MCFL16/bin286243。41个不同用例跨轮各自通过，非单轮41项。T字段、generic object、qualified readonly/full Kryo identity等未覆盖。

### 阶段 68：未注解 generic 类类型绑定（已验证）

限定的三个入口已实现：prototype仍解析readonly签名/parents，成员body、default constructor和abstract检查延后到T绑定后；annotation visitor保留top-level逻辑并跳过prototype body；实例复制`isAbstract`、注册实际成员及运行原abstract检查。`flatExtends`后设`currTemplate`为实例，再由`MCFPPAnnotationVisitor.visitTemplateBody(ctx)`转存字段annotations，最后complete/apply/refresh。支持未注解普通generic class字段、构造器参数及返回类型。producer Int 4/9同一Compiled、Bool true不同Compiled；consumer反序恢复`scope.types[T]`、字段/constructor/read的Int/Bool类型、private Var/Property owner和TypeId；磁盘4/9、固定score `#generic_bool`观察值1、frame0。MCFL16/bin286243不变。17个不同用例跨轮各自通过，非单轮联合。generic object/interface、top-level/method annotations持久化、source abstract/final flags及显式Cell<int>类型注记未覆盖，详见verification.md。

### 阶段 69：泛型类型标注与声明准备（已限定验证）

显式generic类型参数与late declaration准备已在限定路径通过真实库往返：共享`prepareHeader`在原声明file上下文、bind参数前运行；`completeTemplateDeclarations` parent-first完成cached实例，再刷新签名，无INDEX_TYPE全局预扫描。形参、字段、构造器、返回签名共用canonical specialization；bucket顺序反转不改变稳定TypeId/owner，consumer从生成文件执行。MCFL17/bin289989；stdlib日志`mcfpp-generic-template-explicit-type-stdlib.log`；19个不同用例跨轮各自通过，分轮结果见verification.md。阶段69当时TypeValue仅保证builtin/formal types；绑定T/N表达式在阶段70限定解决，Declaration/Applied TypeValue仍待71。

### 阶段 70：泛型类型表达式的作用域（已限定验证）

`Cell<(T)>`、`Sized<(N+1)>`在声明scope和真实caller中解析，递归路径透传context；checked lookup保留raw null，纯type scope不回退caller.vars，无fake Function。六个生产文件和一个fixture覆盖影子T=bool/N=90、反序consumer、字段/constructor/method参数与返回canonical及private owner。七处binary索引修正；七处控制流转换跳过CompilerOnly/无runtime表示；两个结构检查仅排除static字段而保留真字段校验。

29项首轮绿、Logic6和最终新方法1分别通过，共36个不同用例跨轮各自通过；最后仅1项复查，不是联合36或最终7绿。实际磁盘4/6/8/bool1/frame0；MCFL17/bin289989未变，无stdlib/fullcheck/服务器。完整轮次见verification.md；阶段70提交记录见Git历史。

### 阶段 71：Declaration/Applied TypeValue恢复（已限定验证）

四个生产文件和一个fixture完成Concrete表达式出口类型值归一、有限TypeId恢复及顶层Typed-TypeValue注册；普通typed模板字段用unknown adapter避免晚声明空默认快照。source/fresh LeafAlias/Leaf/list<int> canonical字段/ctor/read/自由函数参数及磁盘4/9/7/frame0通过。首轮5绿+新71方法1共6个不同用例跨轮各自通过，最终仅必要2项复查；wire/layout/签名schema与MCFL17/bin289989未变，无stdlib/fullcheck/服务器。提交记录见Git历史；完整失败推进见verification.md。

### 阶段 72：完整静态类型集合（已限定验证）

六prod+fixture接通普通expr统一类型值出口、Meta selector静态view、Typed/TypeValue/Sequence/Record共享登记、CompilerOnly子facts seeding及Concrete完整已知索引。中间Property getter恢复DTO receiver，最终赋值property保持原路径。前置readBundle wire签名、named types/direct [Leaf] canonical、source/fresh身份及磁盘4/9/frame0已验证；仅实例顺序反转。首轮4绿+最终新方法1共5个不同用例跨轮各过，非最终联合5；MCFL17/bin289989及schema不变，无stdlib/fullcheck/服务器。完整轮次见verification.md，提交以Git历史为准。

### 阶段 73：冻结Specialized类型值（已限定验证）

两个prod与一个fixture共享resolveSpecialization(id)，复用snapshot restore/prototype.compile当前target/options并核对声明与FullID；tryResolve保留READ_LIB/currentcanonical路径。前置Holder<Cell<int>> wire、canonical成员签名、fresh独立模型、稳定FullID/T快照及磁盘4/9/frame0通过。最终必要联合2项（新73+旧72）全过，非全量；MCFL17/bin289989/schema未变，无stdlib/fullcheck/服务器。阶段72提交ee46fba877b9af3570ddeb1b8f65f8742d22c11d；阶段73提交以Git历史为准。

### 阶段 74：源码联合类型与冻结身份（已限定验证）

新增至少一个PIPE '|'的unionType，旧UNION '&'及unionTemplateType/UnionDataTemplate不变；表达式优先级不变。递归scope/caller解析invalid项诊断null，复用MCFPPUnionType及有限Union resolver完整ID检查。Scalar/ReorderedScalar仅经typealias将Union作为Box静态T；source/fresh规范化snapshot/canonical/fullID与consumer真实磁盘4/9/frame0通过。必要联合4项全过，旧DataTemplate.unionTest仅语法smoke；direct readonly union expression及Union runtime值/布局未验。MCFL17/bin289989/schema不变，无stdlib/fullcheck/服务器。

### 阶段 75：冻结向量TypeValue（已限定验证）

仅MCFPPType Applied分支恢复Builtin(vector)唯一数字维度并核对FullID，四种元素类型工厂不变，无新>0约束/registry/wire/Vector runtime。前置vec2/vec3 Box静态T签名与fresh反序的维度、snapshot/canonical/FullID及consumer磁盘4/9/frame0验证；最终必要联合2项（新75+旧71）全过。MCFL17/bin289989/schema不变，无stdlib/fullcheck/server；完整轮次见verification.md。

### 阶段 76：冻结SelectorTypeValue（已限定验证）

仅一行Selector resolver复用MCFPPEntityType并校验完整ID，原顺序/null/引号/flag保持。真实fixture覆盖Selection entity<2,"minecraft:pig","!minecraft:cow">及bare entity，两个前置签名、source/fresh canonical/不可变快照/FullID与consumer磁盘4/9/frame0通过；最终仅1项，非joint2。isName=true、empty source与世界/runtime仍未验。MCFL17/bin289989/schema不变，无stdlib/fullcheck/server；75提交d6dfb7f57b5844839c00a9df5261c61d9d1acd00，76提交以Git历史为准。

### 阶段 77：direct Union TypeValue表达式（已限定验证）

lazy Type.data及新增NoExternalWrites Type MNI operator '|'复用既有Native dispatch，type参数/返回及returnsConstWhenArgsConst；无grammar/visitor override、registry、runtime Union或用户函数求值。front Box<(int|string)>、ordinary named Meta表达式、direct重复输入及alias对照的source/fresh canonical/FullID/snapshot与consumer磁盘4/9/frame0通过，最终必要1项。内建scope不新增namespace持久Native签名，oldbin实际读取通过；MCFL17/bin289989/schema不变，无stdlib/fullcheck/server。

### 阶段 78：匿名模板alias冻结身份（已限定验证）

匿名创建入口在真实owner中转存annotation；缓存alias API不触发解析。Declaration完整ID匹配、tryResolve后按template===确认唯一canonical；匿名data-N与合法named data_N隔离。X@DataOnly/透明Y、前置Box<X>、source/fresh T/scope/ID/snapshot及磁盘4/9/frame0通过。四个不同用例跨轮各自通过，最终仅new1复查；MCFL17/bin289989不变，无stdlib/fullcheck/server。不实例化X，不验匿名method/ctor、重新parse稳定ID、全部跨库碰撞、无根匿名或Opaque；详细失败轮次见verification.md。

### 阶段 79：generic函数自身readonly依赖签名（已限定验证）

existing UnresolvedType保留自身readonly Identifier依赖，shared bound signature连接候选匹配和特化；freezeReadonly提供独立CompilerOnly完整快照绑定。relay<T>(Box<(T)>)->Box<(T)> source/fresh签名、恰好2个wrapper及consumer磁盘4/9/7/frame0通过，caller影子不污染；最终必要联合5全绿。MCFL17/bin289989不变，无stdlib/fullcheck/server。仅GenericFunction延期识别/static T/runtime int字段，default literal延期cast实现未有新增断言；native/extension新入口、任意typedef/重载等价、用户constexpr、local空prototype导出等边界保留。

### 阶段 80：generic object（已限定验证）

共享generic factory/TemplateBody注册、ObjectCompoundData/self companion及静态方法入口已限定接通；稳定object FullID和有限canonical恢复不新增wire/metadata。Settings<N> source/fresh模型、consumer反序9→4→4及磁盘4/9/4/frame0通过；最终仅新1，三用例跨轮各过。原无this断言纠正为canonical StaticMemberView，生产未绕过。MCFL17/bin289989不变；genericobject wrapper直接库fieldtype、静态字段强转、init/autoload及其他独立边界仍未验。

### 阶段 81：generic interface静态TypeValue（已限定验证）

Contract<T>及Box<(Contract<int/bool>)>前置签名使用显式type primary，interface origin/FullID/readonly快照、canonical Box、抽象exchange实际Int/Bool参数返回/noCtor和fresh独立模型已通过；consumer实际磁盘4/9/frame0。沿existing generic wrapper/已注册serializer及isInterface/kind，不新增wire/metadata，MCFL17/bin289989未变。shared成员声明入口按ABSTRACT保存flag；最终仅新1，三case跨轮各过。不构造/运行interface，不扩generic继承、runtime布局/shape转换/annotations或autoload，legacy interface wrapper未激活。

### 阶段 82：源码generic特化磁盘导出（已限定验证）

仅namespace导出加局部身份visited/compiledTemplates递归，marker复用静态输出、普通模板复用原输出，其余genFunction/helper行为不变。source-only Box1/relay1/Settings2实际磁盘4/9/4/9/frame0及init/read/static read目标文件通过，无内存fallback或库consumer。最终新82+旧80+普通ObjectMethods联合3全绿。MCFL17/bin289989/schema未变，无stdlib/fullcheck/server；autoload、接口runtime、empty prototype及一般语法完整性等边界保留。

### 阶段 83：internal generated名字隔离（已限定验证）

两个现有生成编号separator改'-'，Settings_int-0/relay-0与合法Settings_int_0/relay_0真实target和owner storage prefix隔离，source盘4/9/4/9/frame0通过；四case跨轮各过、最终仅新1。原成员Function.prefix不含owner，试joint夹具误比较后仅修OWNERprefix断言。FullID/key/options、MCFL17/bin289989/schema不变，无stdlib/fullcheck/server。不扩一般member prefix、casefold、跨库同名或命名系统。

### 阶段 87：仅泛型参数承载 `type` 值（已实现并限定验证）

`TypeUsage`统一判定接入源码声明入口、已绑定普通签名、IR/擦除值与集合、延迟字段；普通值位置拒绝保存 `TypeValue`。依赖普通 `type` 存储的旧30个正例已撤回；4个合法的直接泛型类型表达式库fixture保留。最终5项定向复查全绿，19个不同用例跨轮各自通过，非单次全套；MCFL19/bin292301未变，无stdlib/fullcheck/server。细节和先前红测见verification.md。

### 阶段 88：list.clear 显式调用上下文（已限定验证）

36行 `NativeCallContext` 暴露Function、receiver的ValueRef/Place、immutable CompilerValue快照与通用writeReceiver；内部private Var桥执行function.runInFunction和StorageAccess恢复/写回。Java clear为单context，NativeFunction采用实际invocationArgs，CompoundData按精确签名识别，其他native ABI不变。真实实例owner模板 `reset` 的clear/add源码库往返执行结果7，frame0；保留一个IR clear和cache拒旧检查。MCFL19→20，stdlib Project0/0，三个bin产物292301 bytes且SHA一致。限定测试与资源记录见verification.md；其他native列表成员尚未迁移。

### 旧浮点乘除（阶段 50 已实现）

阶段 49 加减实现提交 `82d955b`；阶段 50 完成乘除。LegacyFloatMultiplyDivideTest 7 项与加减、旧转换、旧布局及 Conversion 套件联合 36 项通过，0 failures/errors/skips。MCFL 11 未变，无签名/缓存结构变化、未重建标准库；未运行完整 check 或实际服务器验证。

任一符号为零的乘除（包括分母为零及 0/0）在算术前规范为四个零分量，不执行会失败的 `/=0` 或 `%=0`，不新增已知零编译诊断，也不引入 IEEE 特殊值。非零乘法保留精确向零截断；除法固定 7 次十进制长除，若 A<D 再做 1 次，得到精确商并向零截断为 8 位有效数字。右分量只读且无 return 依赖；`div_align` 因仍被 inverse/3vec 引用而保留。

已修复的历史除法缺陷包括用 `D-(D%25)` 近似分母造成的结果错误（`1.0000025/1.0000024` 原得 `1.0000001`，修复为 `1.0000000`；`9.0000000/9.0000024` 原得 `0.9999997` 且布局失范，修复为 `0.99999973`），以及负低分量/只按高分量进位导致的归一化错误。

阶段 51 已将旧浮点算术/比较、Promote/Convert 接入 IR：四分量值使用独立 NBT 帧，`LegacyFloatCommands` 负责读写和调用，保留旧四记分板 return ABI。普通/递归/static、旧与 IR 双向调用、早先参数、多实参、常量与连续返回均经真实库命令执行；最终 20 项必要复查通过，0 failures/errors/skips。Native 路径不变；旧浮点算术/比较及跨数值折叠禁止宿主 Float 计算，`16777217` 保持八位十进制精度；identity/toNBT 保留来源 codec。包含 FloatBits 端点的旧浮点范围，其静态顺序不使用宿主比较，整数/native 行为不变；浮点迭代语义未定义，不新增迭代行为。

已知 int/bool/byte/short as legacyfloat 仍沿旧入口并在实际访问时诊断；未用视图不报错，unknown any 视图不做运行时 typecheck，命名 float 视图的来源随后被写成已知标量，再读取视图会诊断。阶段56/57完成有序初始化 RHS 持久化与 const 初始化语义，MCFL 12；验证记录见上。阶段58已为部分语法接入普通模板推断字段声明绑定，其他语法仍沿旧路径；阶段59导入声明环境已实现；阶段60消费端库函数主体导出已在限定路径验证；阶段61已恢复受支持模板方法owner，阶段62已修复模块资源复制；当时待办的阶段63函数权限已完成；阶段86 actual generic父项已限定通过，MCFL19/stdlib292301。阶段87严格限制 `type` 声明范围已限定验证。阶段88候选为从真实legacy实例成员 `list.clear()` 接入小型 NativeCallContext；具体API/最小回归仍待root确认。imported object自动load仍未解决。未知端点范围和浮点/混合迭代的步长及不前进策略仍未定义，保留现有诊断。整个17项迁移仍未完成，模板/泛型/T!、其余控制流/集合及 MNI 尚待统一。

- 26.3 原生 float 的字面量、算术/比较、循环、递归调用、static 写回、擦除与共享视图、集合元素和范围载荷进入 IR；int→float 提升作为 Promote，用于声明、赋值、返回、普通/成员实参和上下文集合字面量。运算与旧入口共享提供器表达式，值保存在 NBT 帧，负零取负保留符号；常量非有限值、反向已知范围和有损 static 写回明确诊断。旧浮点后端现已进入 IR；其余来源转换和完整 MNI 接口仍待迁入；浮点/混合迭代语义未定义并保留现有诊断，不扩展步长或不前进规则。

- 已有标量/数组的显式转换和 toNBT 按 ConversionData 身份进入 Convert，short/double/nbt 载荷接入普通参数/返回；提升和转换遵守折叠开关，long/double→int 保留目标 data get。NBT 常量保存来源编码，byte/short 返回沿用记分板接口并由 IR 按宽度捕获；旧入口和 IR 互调已检查。DataObject 等其余来源仍待迁入，转换矩阵中的未实现组合保持诊断。

下文保留原实施顺序和全阶段验收清单；条目出现不表示已通过。

## 首要问题与交付范围

上一会话只有位置/视图/版本模型，语言 as 与 lastVar 尚未迁入；此次已建立以下可验证的纵向路径：

> 同一 Place 的读写和物化 → 共用擦除载荷 → as 解释视图 → 模板成员访问与重叠写入失效。

首批接入 int、bool、26.3 float、原始 NBT 和数据模板的集中适配；旧浮点保留现有后端，已知标量来源的四分量访问给明确诊断。
其他类型通过明确的内部边界继续迁入，不增加用户可选择的两套语义模式。
形参、返回和模板赋值的行为必须同步验证，不能只实现局部表达式。

## 实施前复核

1. 确认交接提交存在、工作区状态与当前分支；保留用户新增修改。
2. 读取 `migration.md` 和 `conversions.md`，明确哪些缺失属于旧路径，不把目标规范当作当前承诺。
3. 重点读取以下入口：

| 入口 | 当前作用与待迁移位置 |
| --- | --- |
| `analysis/ValueModel.kt` | Place 重叠、FlowFacts、TypedView、StorageLayout、StorageVersions；连接真实读写和缓存 |
| `analysis/TypedIR.kt` | 实际 Read/Write/View/Construct/Call/CaptureIndex、捕获的集合形状、参数与外部位置元数据；继续扩展成员、转换及其余类型 |
| `analysis/PrimitiveCompiler.kt` | 标量/擦除、list/dict/ImmutableList 控制流与列表动态索引、自由函数调用图与帧后端；继续接入成员 |
| `analysis/EffectAnalysis.kt`、`ReturnTypeAnalysis.kt` | 递归外部写入摘要、static 已知子位置及未知列表范围的写入类型证据；扩展其余集合/全局位置及递归擦除返回 |
| `analysis/IRCollectionValidation.kt` | 生成命令前检查列表共同编码、空键和动态索引宏能力；随成员迁入扩大 |
| `antlr/MCFPPExprVisitor.kt` | visitCastExpression 使用 StorageAccess；操作数捕获、调用结果及临时值保存也在这里 |
| `antlr/MCFPPImVisitor.kt` | 旧赋值、分支、模板/集合语法仍直接生成命令或 toDynamic |
| `core/lang/Var.kt` | 声明约束、旧转换、replacedBy、symbol 及变量适配边界 |
| `core/lang/MCAny.kt`、`MCObject.kt` | 统一擦除载荷、类型知识及内部编译器载荷；继续扩展容器和循环 |
| `core/lang/obj/DataTemplateObject.kt` | 模板字段实例、复制约定、旧 cast 与成员变化回调 |
| `type/ReinterpretationCompatibility.kt` | 现有纯兼容检查，应复用而非在 visitor 复制规则 |
| `model/function/Function.kt` | 形参、返回、fieldStore/fieldRestore、调用帧及返回槽 |
| `model/function/SpecializationPolicy.kt` | 普通参数不按常量特化；compiler-only 值保留内部特化约束 |
| `model/function/NativeFunction.kt` | 旧反射/MNI 边界，NoExternalWrites 之外默认保守屏障 |
| `backend/NumericConversions.kt` | 显式值转换，as 路径不得调用 |
| `command/FloatProviders.kt`、`TargetCapabilities.kt` | 原有数值提供器和目标能力，保留已验证行为 |

这些路径相对于 `src/main/kotlin/top/mcfpp`。

## 建议实施顺序

### 1. 将位置与写入版本连接到真实存储

- 明确声明位置、字段路径、集合元素路径和临时表达式结果的身份。
  不因 Concrete 适配对象变成运行时对象重新分配声明 Symbol。
- 提供统一的读、写、materialize、invalidate 接口；布局选择由目标能力和来源编码决定。
- 物化不撤销 Constant 事实；写入递增位置版本并失效重叠字段、视图和同步缓存。
- 已知字段写尽量保留兄弟字段事实；未知索引或未知范围保守失效容器/共享对象。
- 递归和连续调用需要保存所有尚未消费的临时结果；当前标量/擦除 IR 调用已有独立帧槽和位置，仍需将其余类型纳入同一布局规划，不能假设完整帧分配已经完成。
- 编译器对象只在内部静态通道传递，禁止通过 erased/object 声明强制物化到 Minecraft。

先用直接模型测试和一个完整解析到命令的样例验证，再逐步替换旧读写调用。
不能在各 visitor 重新散布 hasStoredInStack、trackLost 或新的等价布尔标记。

### 2. any/object 采用共用载荷槽

- 已知具体类型保持原有表示，跨分支或普通函数边界需要统一表示时使用 NBT 槽和来源的约定编码。
- 区分载荷位置与类型知识：值未知但类型 Exact 时仍能绑定操作；不同实际类型汇合为 Candidates。
- 所有到达路径共同成立的类型和值事实才可保留；信息丢失处警告，未经 as 的具体操作报错。
- 未知载荷支持复制、赋值、传参和返回，不依靠 lastVar 重建对象，也不新增运行时类型标签和成员分派。
- object 只使用其声明签名，不能因优化掌握实际类型而开放成员。
- 已知 any 的重载匹配继续采用实际类型；未知 any 仅直接匹配 any/object。
- type 等可快照的编译器载荷保留现有内部特化；任意可变 Java 对象需要明确引用/版本模型，不能塞入不可变常量缓存键。

### 3. as 下沉为真正的 TypedView

- visitCastExpression 只负责目标类型解析和视图绑定，不调用 NumericConversions、构造器或旧 explicitCast。
- 视图指向来源 Place，目标类型仅决定成员、方法和存储访问解释。
- any 来源不做运行时验证；普通来源复用 checkReinterpretation，不能证明兼容时警告后继续尝试。
- 仅在目标没有该布局的访问能力时给代码生成错误及转换建议；正常未知类型、私有成员和不可访问成员仍是语言错误。
- 取得未使用视图尽量不生成命令。必要物化只保存来源编码，不能悄悄转换成目标数值或补字段。
- 对不兼容视图不推导来源未经证明的常量；字段匹配也不能自动满足目标的无实现抽象能力。
- 目标方法按目标模板定义绑定，不做来源方法动态分派。
- 视图写入使用第 1 步的失效接口；源变量和其他视图下一次读取不能沿用旧常量或旧同步副本。
- 普通模板赋值保留复制约定，只有解释视图共享数据位置。

### 4. 同步迁移调用、效果与旧代码

- 用户函数从 IR 推导效果；递归保守求解；未声明 MNI 和原始命令使用未知效果。
- 未知效果前提交可能被读取的延迟写入，之后撤销可能被修改的位置事实和缓存。
- 原始 function 命令嵌套调用时保持旧目标的内部条件帧，不重置调用方状态。
- 将旧数值 as 样例迁为 toInt/toFloat 等函数，保留专门测试 as 不生成转换操作。
- 标准库、示例和原生签名随语义一起迁移；修改缓存结构/元数据时升级版本并重新生成 bin.mclib。
- 适配器集中放在边界，完成仓库内调用迁移后删除，不能变成长期第二套模型。

## 本阶段必须新增的实际断言

| 范围 | 验收用例 |
| --- | --- |
| any 控制流 | 同类型不同值保持 Exact；不同类型为 Candidates；未知操作报错；显式 as 后可绑定；类型分析不受折叠开关影响 |
| 擦除传递 | any/object 跨分支、形参、返回和集合保存后载荷保持；未知载荷可复制；编译器专用值不会生成 NBT 写入 |
| as 生成 | 不调用构造器、不补字段、不复制模板、不调用 toFloat/toInt、不生成类型检查；未使用视图不做无必要物化 |
| 模板关系 | 名义上转、无法证明的下转、结构相同/额外/缺失/可选字段、嵌套循环、可写不变、只读递归、私有与抽象能力 |
| 视图诊断 | 不兼容但可生成访问时仅警告；布局无访问能力时明确代码生成错误；正常访问控制不被绕过 |
| 别名写入 | 视图 A 修改字段后源与视图 B 读取新值；重叠缓存失效；已知字段不误清兄弟字段；未知索引保守失效 |
| 存储 | scoreboard/NBT/实体路径/帧之间正确搬运；未修改同一位置不重复同步；物化后仍保留可证明常量 |
| 调用 | 嵌套、连续、递归调用不覆盖活跃返回或临时值；提前返回栈平衡；未知 MNI/原始命令屏障无过期事实 |
| 数值与后端 | int/float 提升方向不变；NBT 映射不直接算术；旧/新浮点编码保持来源布局；26.3 无旧库调用 |
| 优化等价性 | 开/关已迁入路径的折叠，结果及副作用顺序相同；命令减少不能改变行为 |

使用单元断言、命令结构检查和独立执行器。服务端缺失时明确记录待验证项，不把执行器当作实际目标验证。
宽松重解释的错误数据不要求安全运行结果，但必须验证诊断、生成方式和优化器不制造错误常量。

## 验证与交付

用户最新要求：不做过度防御与工程化，优先清晰可读；整项任务完成前仅做必要检查。按改动运行直接受影响的测试，缺陷修复后检查对应回归，已有检查通过后不要无理由重复运行。
类型布局、语言签名或 MNI/缓存元数据改变时重建标准库；整项重构完成时进行完整 check。需要这些检查时顺序执行，**不要并行启动 Gradle 构建**：

```sh
./gradlew regenerateStdlib -Dorg.gradle.jvmargs=-Xmx2g -Pkotlin.daemon.jvmargs=-Xmx2g
./gradlew check --rerun-tasks -Dorg.gradle.jvmargs=-Xmx2g -Pkotlin.daemon.jvmargs=-Xmx2g
git diff --check
```

regenerateStdlib 后再执行必要测试或最终 check，使 processResources 复制新索引；纯瞬态 IR 改动不要求每个小阶段重建索引或全量检查。
更新 migration.md、conversions.md、verification.md 和交接记录中的实现边界、测试总数、服务端状态。
交付应同时给出代码、诊断断言、命令结果和已知限制，不能只提交类型/存储模型占位类。

此阶段完成后，继续迁移全部集合、模板构造、浮点、泛型及 MNI，统一成员签名，清除 Concrete 双层继承和旧状态。
最终核心类型检查、控制流、参数匹配和命令生成不能再依赖 MCFPPValue 或 Concrete 子类判定；
该最终条件当前尚未满足。

# 类型系统迁移状态与指南

本次修改是按阶段推进的内部迁移。以下表格区分已经接入编译器的行为和仍未完成的工作；
不能将目标规范中的全部语义视为已经实现。

## 当前已接入

| 范围 | 实现 |
| --- | --- |
| 类型身份 | TypeId；命名空间及声明类别；容器构造器与实参；联合类型去重；T! 使用 T 的身份 |
| 类型别名 | 透明展开、前向引用、别名链和循环诊断；函数签名使用解析后的类型；库索引保存展开后的别名目标 |
| 关系与重载 | 统一方向的隐式转换、名义关系、int 到 float 提升、容器不变、候选验证与歧义诊断；IR 的纯类型查询复用同一排序和默认参数规则，歧义不退到父域候选 |
| object | 语言静态超类型；不依据载荷开放具体成员；DataObject 保持模板基类；type 值经 object/any 使用内部特化传递，dynamic 擦除声明拒绝不可物化的载荷 |
| any/object 载荷 | 共用无运行时类型标签的 NBT 载荷；删除 lastVar；已知 any 按实际类型绑定，未知载荷支持复制、传参、返回与显式视图；含编译器专用字段的 list/dict 通过内部静态载荷传递，普通擦除赋值递归复制；object 保持声明签名限制 |
| NBT 数值 | byte / short / long / double 不再隐式进入 int / float 算术；Byte.build 保留实参；修正错误的成员注入 |
| 声明与值 | 稳定 Symbol；不可变常量快照；独立 TypeKnowledge / ValueKnowledge；StorageAccess 将 Place、TypedView、FlowFacts、StorageVersions 接入实际 NBT 与记分板读写 |
| 基本块编译 | 无所属模板的 int/bool/any/object、string/byte/long 载荷及可编码 list/dict/map/ImmutableList/NBT 数组形参、返回和普通调用；集合字面量、已知键、动态下标、复制与共享视图接入同一 IR。先在私有调用图上求解效果和控制流事实，再绑定重载并生成命令；分支与循环采用不动点事实，break/continue/return 排除不可达前驱；类型分析独立于折叠开关 |
| 循环 IR | while、do…while 和整数区间 for 使用普通基本块。do…while 至少执行一次，continue 转向尾部条件；区间边界只求值一次，迭代变量每轮复制，嵌套变量作用域独立。上界包含在内，递增前检查结束以避免 Int.MAX_VALUE 溢出；IR 区间不逐项展开。直接范围及已证明两端为 int 的命名范围均可迭代，浮点范围、未知端点形参及通用迭代器仍待迁入 |
| 范围值 | 端点保留 int/float 身份，以含可选 left/right 字段的 compound 载荷复制与存储；整数不经过 Float。整数范围的构造、命名/擦除副本、共享视图、集合嵌套、普通参数/返回和 static 替换接入 IR；调用形状证明端点后可迭代。旧入口与 IR 共用精确快照，范围返回槽及捕获独立；iterator 合为 RangeVarData 一份签名，旧常量迭代器惰性生成元素，尚未迁入的运行时迭代明确诊断，MCFL 升至 10 |
| IR 词法作用域 | 重名检查只针对当前作用域，分支/嵌套块可以有独立同名声明；初始化先按外层可见性求值，随后遮蔽名称。声明 ID 稳定，Symbol 保留源码名，记分板/NBT 存储名独立分配；退出块后恢复可见绑定，越界引用直接诊断。foreach 变量属于循环体作用域，局部共享视图继续指向原 Place，递归保存独立局部存储 |
| 常量与存储 | 基本块路径进行分支汇合与循环不动点分析；常量延迟物化；dynamic 保留运行时表示；分支不物化未修改的无关变量 |
| 原始命令与调用 | 原始命令前提交延迟数据，之后撤销类型、值事实和同步缓存；受限 IR 调用图求解 Pure/Writes/Unknown 的递归不动点，static 形参写入映射到实际位置，普通参数副本的局部写入不外泄。未迁入调用与未标注 MNI 保守使用未知效果；已审计数值及 list/dict/map/ImmutableList 查询 MNI 标注 NoExternalWrites；list/dict/map 变更标注 WritesReceiver，由存储接口提交并失效受影响位置 |
| 缓存 | 不可变特化键包含声明、值实参、目标版本和影响生成的选项；真实空值、未知值与错误分离；库索引新增格式头与版本；项目重置清除词法/语法缓存、元数据图缓存、当前编译上下文与旧 load/tick 函数，移除无人读取且保留旧项目的反向子类型列表 |
| 特化策略 | 普通函数的运行时参数不按常量组合复制函数体；泛型缓存忽略普通参数值，保留泛型、T! 和编译器专用载荷；导入和前向引用的运行时函数体按需编译一次 |
| 成员签名 | int / float / bool / dict / list / map / ImmutableList 的常量与运行时操作共用一份签名；集合成员共用值事实和存储边界实现。删除 NBTMapConcreteData 与 NBTListConcreteData，NBT 数组的两种值状态使用相同成员表 |
| 显式转换 | 具体数值源重载与来源 NBT 编码；不支持的运行时转换明确报错；支持矩阵与后端限制见 conversions.md |
| 返回与帧 | IR 调用以独立帧槽捕获参数、返回和活跃临时值，递归标量和集合载荷保持副本独立；static 写回映射实际字段/元素，撤销旧实际类型并传播可证明的输出类型，未改动兄弟字段保留事实。调用返回值有独立 Place，支持直接 as；已知 any 返回可参与运算、条件与重载，保留 any 声明且不计算普通函数的常量返回值。旧 visitor 的模板/其余 NBT 调用适配仍保留 |
| 调用子形状 | 普通调用输入、返回和 static 整体替换传递独立的子类型/嵌套长度快照，擦除普通实参及返回的全部常量值；较早输入不借用后续实参副作用后的形状。返回分支只保留共同证据，写回只恢复效果覆盖的位置，旧后代会清除；未知范围不证明整片已写入。已编译函数返回的容器 TypeId 可在调用方重新登记，旧无宏目标在形状绑定后归一已知负索引；相同类型/形状输入的直接与相互递归返回及 static 写回已求解共同输出，不返回的路径不提供返回证据，递归输入变化时仍保守 |
| 数值与布局 | MCNumber 不再实现 OnScoreboard；单记分板接口由 MCInt 提供；模板浮点字段的分量按实例定位；旧浮点常量编码使用八位十进制有效数字、ties-to-even 舍入和零规范化。阶段 51 的旧浮点算术/比较、Promote/Convert 已进入 IR，四分量值使用独立 NBT 帧，纯 LegacyFloatCommands 负责读写/调用，并保留旧四记分板 return ABI；identity/toNBT 保留来源 codec |
| 原生浮点 IR | 26.3 的 float 算术/比较、循环、普通参数/返回/递归与 static 写回使用 NBT 帧；共用 FloatProviders 表达式。int→float 使用 Promote，覆盖声明/赋值/返回/实参/集合元素，提升前的整数不变；共享 float 视图和动态下标捕获已接入，范围载荷保持浮点端点。非有限常量、有损 static 写回与已知反向范围会诊断；旧浮点算术/比较及转换已迁入 IR，DataObject 等未迁入来源仍走适配入口 |
| 显式转换 IR | 已有标量/数组 ConversionData 重载按原生身份进入 Convert；普通同名函数不被替换。short/double/nbt 载荷支持普通参数与返回，26.3 浮点转换使用提供器，整数窄化/扩大保持编码和来源；long/double→int 即使常量也执行 data get。toNBT 拥有独立结果位置并保留来源格式，byte/short 返回兼容旧记分板接口；不支持的转换和已知浮点越界在生成前诊断。旧浮点算术/比较、Promote/Convert 已接入 IR；旧浮点算术/比较和跨数值折叠禁止宿主 Float 计算，浮点 range 静态顺序不使用宿主比较，整数与 native float 行为不变。已知 int/bool/byte/short as legacyfloat 仍沿旧入口并在实际访问时诊断；未使用视图不报错，unknown any 视图不做运行时 typecheck，命名 float 视图的来源随后被写成已知标量，再读取视图会诊断。DataObject 等来源保留适配边界 |
| as 视图 | 两个表达式 visitor 使用统一 TypedView，复用纯 checkReinterpretation；不调用数值转换、构造器、不补字段、不复制源模板；普通无法证明的重解释警告，any 不生成运行时检查；旧浮点的标量布局访问明确报错 |
| 模板别名与复制 | 显式 as 初始化共享 Place；普通模板赋值及传参仍复制载荷；视图字段写入使重叠事实与同步缓存失效，保留已知兄弟字段；运行时模板副本不继承原型默认值事实 |
| 列表与字典元素 | list/dict 下标接入集中位置读写；已知元素保留实际类型，部分已知字面量保留子事实；普通赋值复制、as 共享；未知下标写入合并可能元素的类型并撤销重叠值；动态下标在右侧调用前捕获，不同下标不共用读寄存器 |
| 集合 IR 事实 | list/dict/ImmutableList 的已知下标/键在分支和 while 中汇合元素实际类型；读取同时捕获子事实及嵌套长度，后续调用改写来源不改变较早操作数的快照。元素写入保留父列表长度和兄弟事实，整体替换清除旧后代，不同长度的可达路径汇合后撤销长度；已知负索引归一并检查越界。运行时编码和空键能力在后端生成命令前检查 |
| 动态列表 IR | Location 保存逻辑范围与独立捕获的索引结果；负数运行时下标在 RHS 改写容器前归一，嵌套下标和 static 写回保留原地址。未知范围读取只保留各可能元素共同成立的类型/形状，不保留常量值；单个未知索引写入合并可能类型并撤销重叠后代，不能证明整片范围都已写入。选中集合的普通副本独立，命名视图共享捕获的位置 |
| 编译器专用集合 | 含 type 等编译器专用元素的集合支持静态已知下标与独立副本；完整静态 as 视图使用 CompilerOnly 布局、共享 Place 与写版本，已知写入重建不可变祖先快照；普通复制读取最新值并脱离来源绑定；可运行时编码性检查实际内容，拒绝 dynamic/显式物化，运行时屏障不物化或失效静态位置；完整静态实参用于不可变特化键；空列表仍有运行时表示 |
| 集合编码与键 | 混合字面量保留全部元素的联合身份；NBT 命令编码使用可见列表载荷，不插入工具库的二进制包装；字典已知字符串键按字面名称转义，dynamic 字符串保留常量事实，未知字典键仍明确诊断。map 把键作为字符串值存储；动态读取采用 compound 宏谓词，写入/查找/删除/合并支持无宏目标。字典字面量字段使用独立捕获位置，不覆盖同名参数 |
| 字典成员 | 删除旧常量成员表；已知键 remove 保留未改动字段事实，完整静态 merge 深合并并保持副本独立，部分已知接收者的已知标量合并保留兄弟事实；整体深合并保留已知标量输入的类型和值，先冻结重叠来源事实。运行时接收者拒绝编译器专用字段；containsKey/remove 与下标使用相同键编码限制；禁止空键路径的目标对已知空键合并输入写入前诊断，未知运行时来源仍按整体路径合并 |
| 字典成员 IR | 可编码 dict 的 clear/remove/merge/containsKey 复用原生成员签名进行类型绑定，并接入分支/while 与位置效果分析。remove 记录具体字段写入，clear/merge 记录接收者写入，查询只读；合并使用已捕获的来源事实与载荷，已知完整输入递归保留未改动字段，未知输入撤销可能被覆盖的事实。动态接收者索引在实参副作用前捕获，字面量实参按成员泛型上下文绑定 |
| 列表成员 | add/addAll/prepend/prependAll/insert/removeAt/remove/clear 共用接收者位置写入；已知变更重排子事实及嵌套长度，自追加先复制来源；未知范围撤销常量并保留可证明的统一元素类型。indexOf/lastIndexOf/contains 使用独立结果和临时列表，不改写来源；完整常量按值查找，浮点查找保留实际后端；旧目标的按值删除不依赖宏或 return |
| 列表变更 IR | add/prepend/addAll/prependAll/insert/removeAt/clear 复用成员签名并接入 IR 与接收者写入效果。已知位置重排完整子树和嵌套长度，批量输入先捕获，空批量保留原事实；长度在合流后未知时仍保留共同元素类型，追加保留已有位置。动态插入参数跨后续调用保存，旧目标编码、宏能力和已知越界在命令生成前检查 |
| 列表查询 IR | list/ImmutableList 的 indexOf/lastIndexOf/contains 与可变列表 remove 接入 IR；查询纯读，按值删除记录接收者写入并只删除首个匹配项。ListValues 与 ListSearch 分别集中常量比较和命令后端，旧入口共用。擦除列表按已证明的元素身份选择可比较位置，未知类型要求 as；已知删除保留剩余子形状，未匹配不撤销事实。运行时结果和临时载荷独立，支持无宏目标与递归写回 |
| 只读列表 | ImmutableList<T> 保留工厂、复制和返回的类型身份；与 list 共用元素编码和存储位置接口，仅提供 indexOf/lastIndexOf/contains 和下标读取。as 视图观察来源写入，普通副本/参数/返回独立；元素赋值和修改列表的成员明确拒绝，完整编译器专用值继续走静态通道 |
| NBT 数组 | [B;] / [I;] / [L;] 字面量分别保持 ByteArray/IntArray/LongArray 身份，含空数组；不可变快照和物化保留原数组格式。下标接入集中位置、长度检查、负索引归一与版本失效；元素分别为 byte/int/long，普通副本/参数/返回独立，as 共享。未知索引先捕获，无宏目标明确诊断 |
| NBT 数组 IR | 三种数组及 byte/long 载荷接入 Construct、位置读写、共享视图、普通/静态参数、返回、分支/while 和递归帧。复用精确格式快照，64 位 long 不经过记分板；已知长度检查越界，未知长度的负数字面下标在支持宏的目标上捕获归一，无宏目标保留旧入口。元素仍为 byte/int/long，NBT 数字不获得普通 int 运算 |
| map IR | 可编码 map 的已知/动态字符串键、六个原生成员、复制/视图、参数/返回及循环/递归接入 IR。Location 捕获完整键谓词；MapFacts 使用 entries 的物理子位置，已知键保留实际类型，未知键保留共同证据，删除后视图继续按键定位。写入为浅覆盖或新增条目，静态效果限制到 entries；MapCommands 由旧入口和 IR 共用，未知键写入/删除/查询不要求宏，未知键直接读取仍要求宏。编译器专用路径尚未迁入 IR |
| map 投影 IR | keys/keyValueSet 为纯读 MapProjection，结果拥有独立 Place 和载荷；字段实际类型、常量与嵌套长度跟随快照复制，后续来源变更不影响投影。键列表复用 MapCommands 的无宏循环，支持递归返回、直接下标与成员查询；字典投影按已知键复制完整 value，未知键及运行时空字段名仍明确诊断 |
| map 成员与位置 | clear/containsKey/isEmpty/remove/merge/size 共用签名；索引写入覆盖已有键或追加新键，merge 浅覆盖完整值并保持键唯一。普通赋值/参数/返回独立复制，as 共享位置；完整静态视图持久更新祖先快照，未知键写入撤销常量并合并可能值类型。keys/keyValueSet 是独立投影；字典投影仅支持已知键，运行时空键明确诊断 |
| 目标能力 | 显式版本表统一配置检查、包格式、浮点后端、函数宏、return run、异构列表和空键路径能力；未知版本不推测能力；旧目标拒绝混合/未证明共同编码的列表构造与元素写入，空键路径遵循目标限制；不支持 return run 的目标使用独立的递归分支条件栈，包含支持宏的 1.20.2 |
| NBT 地址与自动宏捕获 | `NBTAddressKey` 冻结地址 source 与 path segments；按快照比较路径段和长度，父子路径同时检查 source，修复 equals 自递归。自动宏使用独立参数槽，从实际绑定或 scoreboard 捕获值；FloatProviders 不再重复预写动态 index。阶段 52 定向复查 17 项通过 |
| 宿主值对象身份 | `Var`、`Pos3Var`、`Pos2Var`、`PosDimension` 的 8 个 equals/hashCode 覆盖已删除；宿主对象按引用身份比较，语言值仍使用 `CompilerValue`。表达式缓存只移除指定引用，spill 只对同一引用去重；括号子 visitor 共享活跃值列表并保留独立结果字段 |
| 模板构造器候选 | 构造重载通过 `ParameterMatcher.match` 与 `best` 选择，复用类型、完整值、默认实参与歧义规则；仅 Selected 初始化对象，错误值不重复绑定诊断。阶段 54 的 4 项专项测试覆盖声明顺序、T! 完整值、默认实参和无构造副作用的歧义；阶段 55 已迁移普通构造参数特化与 `this`/`preInit` 帧 |
| 模板构造 receiver 与初始化 | 固定 `frame0.this` 使用独立 receiver；普通构造实参不再按常量特化，T!/compiler-only 仍遵循 `SpecializationPolicy`。参数只编码入帧；`preInit` 每次运行，包括 AST-null 默认构造；`FrameExit(function,index)` 统一 IR/旧路径出口，caller 写回后 pop。原/特化模板与 static object 构造器均导出；typed nonconst 字段纳入 preInit，静态赋值先 `replacedBy` 再物化，object nonconst 字段动态化。阶段 55 最终 7 项通过；普通模板复制规则保留 |
| 模板初始化表达式库持久化 | `DataTemplateInfo` 保存有序字段 RHS，`preInit` 为 `LinkedHashMap`；`GenericDataTemplateInfo` 复用既有 body AST。源码构造器编译恢复声明文件与命名空间；导入构造器的 transient file 仍为 null，词法 scope 尚未完整保留。Kryo 循环引用 reader 先登记 reference 再读内层对象。生产与字符串测试共用 `MCFPPFile.resolveImports`。MCFL 12，标准库重建与 TemplateInitialization/ConstructorExecution/LibCacheFormat 共 13 项通过；object 自动 load 未验证 |
| 模板 const 字段真实初始化 | object initializer 在 annotation、完整签名和继承 ready 后、用户函数 body 前编译；真实 constructor `prepareBody` 单次求值 RHS，绑定 field/property/Symbol。typed const 与 inferred const 均支持 runtime 初始化；const 是 readonly，compiler-only snapshot 与 T! 完整值规则独立。两个 receiver（1/2）及 self/forward 诊断已验证；MCFL 12 schema 不变 |
| 普通模板 inferred field 声明（已接入部分语法支持） | 复用 `PrimitiveCompiler` 私有图的 Lowering/FlowAnalysis/ReturnTypeAnalysis 做纯 AST 声明绑定，不发布 IR、不执行用户函数、不生成命令；普通构造参数与未绑定 T! 保持 Unknown，同字段跨 ctor overload 要求同一 TypeId。支持 `this` 单字段和此前字段；anonymous 共用队列，泛型已绑定实例独立分析，继承后 annotations 与参数/返回 adapter 刷新且保留 Symbol/Place。运行时按声明类型初始化可编码默认字段，Unknown erased 不伪造 snapshot，不可编码 NBT child 返回整体 null；不支持语法仍沿 legacy `extraFunction`，未宣称 native/generic/compiler-only/static、多级 this/member method 已迁移 |

基本块路径先建立控制流并求解类型事实，再绑定操作、检查类型，最后进行值分析和命令生成；它不在分析过程中替换 Var 或 Symbol。
现有调用方仍通过集中在该路径出口的 Var 适配对象读取编译结果。
尚不支持的语法在生成任何命令或修改作用域前返回旧编译路径。
这不是面向用户公开的另一种语言模式。

普通整数常量折叠目前只覆盖已经固定语义的有符号 32 位加、减、乘和比较，保留溢出行为。
整数路径仍不折叠除法和余数；26.3 原生浮点路径使用单精度算术折叠，提供器运算、比较与 int→float 提升已用独立命令执行器检查，尚缺实际目标对照。旧浮点算术、比较和数值转换现走 IR；旧浮点算术/比较及跨数值折叠禁止宿主 Float 计算。
动态分支各路径的相同常量可以在汇合后继续折叠，不同常量撤销值信息；循环改写的值不会沿用首次迭代的常量。
内部 CompileSettings.foldIRConstants 开关只控制已经迁入基本块路径的常量折叠，不改变类型绑定；
测试分别开启和关闭该开关，比较结果与原始命令的执行顺序。旧 visitor 的常量求值尚未统一到该开关。

## 源代码迁移

- `ObjectVar` 是内部静态成员访问器，现在叫 `StaticMemberView`；原生库的相关 Java 参数同步改名。
- `object` 是统一超类型；`mcfpp.lang:DataObject` 仍代表数据模板的基类。
- 不再用 `nbt` 表示所有可 NBT 编码的类型，也不能依靠 Kotlin 的 MCInt 继承关系获得 NBT 映射类型的语言算术能力。
- 可变 list / dict / map 的泛型参数不变。ImmutableList 现为只读列表接口，当前类型关系仍采用泛型不变策略，不增加协变转换。
- `ImmutableList<T>` 支持语言类型语法；它限制列表元素槽和列表变更成员，嵌套元素自身的可变接口由元素类型决定。显式 as 仍按重解释规则建立共享位置，普通只读副本仍独立。
- `[B;1b,2b]`、`[I;1,2]` 和 `[L;1l,2l]` 不再降为原始 nbt。读取元素使用原格式对应的 byte/int/long；byte/long 参加普通整数运算须显式 toInt，bool 不能赋给 byte 数组元素。
- `T!` 的完整值要求独立于类型身份；部分已知集合不满足泛型实参及完整编译期值要求。

### map 数据布局变更

map 现在只保存一份 entry 列表，布局为 `{entries:[{key:"first",value:2}]}`。键必须是唯一字符串；每行的 value 使用来源约定的编码，不增加运行时类型标签。对这个布局使用 `as map<int>` 建立同一位置的解释视图；普通 map 赋值和函数参数/返回仍复制。

旧 `{keys:[...],keyValueSet:{...}}` 布局不再由 map 成员使用，已知旧布局在访问时明确诊断。重新编译库索引不会转换已存储数据；as 不补字段，也不会生成迁移命令。删除字典到 map 的旧隐式构造适配，转换须显式构造符合新布局的数据。

覆盖已有键保留该 entry 的位置，新键追加；merge 用来源的整个 value 替换同名键的 value。它与 dict 的递归深合并不同。keys 返回独立字符串列表；keyValueSet 返回独立字典投影，未知键或运行时空键的字典投影暂不可生成。空字符串及含引号/反斜线的 map 键本身支持读写、查找和删除。未知键值的别名读取需要目标支持函数宏；写入、查询、删除和 merge 使用普通命令循环，支持无宏/无 return 目标。

## 尚未完成的迁移

以下内容仍是后续阶段的必要工作，不能算作此次验收已通过：

1. 将其余表达式、形参/返回类型、集合、模板、浮点、未知端点范围/通用迭代器和调用接入同一基本块与存储接口；do…while、直接整数区间及已证明端点的命名范围 for 已接入。
2. 将擦除类型的候选分析扩展到旧 visitor 控制流、其余循环、全部用户调用及其余集合。list/dict/map/ImmutableList 与 NBT 数组的可编码载荷、已知键及动态下标已迁入 IR 的分支与 while；map 投影已接入，其余成员调用和编译器专用集合仍需统一。无宏目标上未知长度的负数字面下标保留旧边界；字典四个成员、列表成员与 map 六个成员已进入 IR，其余成员调用保留旧适配边界。
3. 扩展视图布局能力诊断至所有类型、实体与动态索引；继续迁入模板方法、构造和抽象能力的实际调用路径。
   完整编译器专用 list/dict/type 的命名 as 视图现共享 Place；已知字段/索引写入、整体替换、普通副本及静态 list 的追加、插入和删除有断言。部分已知静态容器、任意 Java 编译器对象及其余原生成员仍未完成位置迁移，未知静态下标或非完整值写入明确诊断。无法安全折叠的静态浮点集合查找仍给出后端诊断。
   语言 as 已从旧 explicitCast 分离；其余内部显式转换适配仍存在，不能重新用作语言 as 入口。
4. 提供完整 toInt / toFloat / toByte / toShort / toLong / toDouble / toNBT 具体源重载，迁移旧数值 as、标准库及示例；明确每个后端的范围与舍入规则。
   已接入的重载与缺少运行时实现的情况见 [转换 API](./conversions.md)。
5. 将已有递归效果摘要扩展到其余集合位置、成员、模板、浮点、全局及实体位置，完成递归擦除返回的完整类型不动点，并为 MNI 提供显式上下文与值/位置接口。当前普通自由函数的标量/擦除以及可编码 list/dict/ImmutableList 签名接入实际 IR 调用；static 已知字段和未知列表范围传播无常量值的写入类型证据，未知范围与调用方旧类型合并，条件改写不能借用调用前类型。泛型、T!、原生成员和其余签名保留适配边界；无法证明的函数使用未知屏障，旧反射 MNI 尚未全面迁移。
6. 将模板构造过程纳入值与位置模型。阶段55 receiver/frame、阶段56有序 RHS持久化已完成；源码构造器编译恢复声明文件/命名空间，导入构造器 transient file仍为null，词法scope缺口保留。阶段57实现 shared `prepareObjectInitializers`：annotations/signatures/inheritance ready后、用户函数 body前编译完整本地 object constructor，复用已有 guard；FieldVisitor不试算 RHS，真实 constructor `prepareBody` 单次求值并补 field/property/Symbol。typed const也登记 RHS，incoming parameters先绑定，const只作 readonly；compiler-only snapshot与T!规则独立。首轮28项27过/1测试夹具失败；修换行后定向方法1项过，未联合复跑28。详见 verification.md。阶段58已为部分受支持语法复用 PrimitiveCompiler 私有图的 Lowering/FlowAnalysis/ReturnTypeAnalysis 做纯 AST 声明绑定：不发布 IR、不执行用户函数、不生成命令；普通构造参数与未绑定 T! 保持 Unknown，同字段跨 ctor overload 要求同一 TypeId。支持 this 单字段/此前字段、anonymous 队列、已绑定 generic 实例、继承后 annotation 与参数/返回 adapter 刷新，保留 Symbol/Place。runtime receiver 按声明类型初始化可编码默认字段，Unknown erased 不伪造 snapshot，不可编码 NBT child 返回整体 null；普通模板局部声明 buildUnConcrete 防 DataOnly 空地址命令。pure AST binding 与 runtime AST generation 分离，不重复执行 RHS。native/generic/compiler-only/static、多级 this/member method 等语法仍走 legacy `extraFunction`。37个不同用例跨轮各自通过，非单次联合37项。阶段59已为导入声明持久化 `namespace` 与 `unsolvedImports`，不保存完整 FileScope/Project；includes 全部读取后，在各自声明文件的 FileScope 恢复 currFile 和声明环境；普通函数、构造器、泛型特化均经统一 compile 入口在各自环境中编译。MCFL13 标准库重建后，TemplateInitialization6、SpecializationPolicy11、IRCall17、LibCacheFormat3、TemplateFieldInference6、ConstructorExecution7 联合50项通过。阶段60新增库导出入口已导出所需的非Native bodyCompiled函数，并按namespace输出，限定磁盘测试通过。阶段61恢复普通/泛型模板方法owner及canonical scope，并恢复FunctionInfo的isAbstract；26个不同用例跨轮各自通过。阶段62修复目录/JAR/ZIP模块resourcePath与资源复制并通过11项联合验证；imported object自动load仍待解决。
7. 将版本缓存扩展到全部实体、集合、调用帧和临时值；未知字典键尚无已验证的运行时路径转义后端，当前明确拒绝生成，map 已用字符串值和 compound 谓词避免成员名拼接；旧目标的原生成员操作、原始 nbt/其余集合仍需全面接入编码能力检查；已有标量/擦除递归样例通过不代表完整帧分配已完成，原始命令直接修改其他函数的物理记分板仍需与统一布局规划核实；删除 hasStoredInStack、trackLost、Concrete 双层体系与双成员表。
8. 阶段55/56/57模板 receiver、构造初始化帧、RHS库持久化及 const runtime 初始化见 verification.md。阶段58有37个不同用例分轮各自通过（非一次联合37项）；阶段59将导入声明环境信息纳入 MCFL13，并经50项必要套件联合验证，`bin.mclib` 为282180 bytes。phase56 consume记录的9119条 `flatExtends` 重复继承警告仍待清理；阶段60新增导出入口已验证物理消费包函数体；阶段61限定恢复方法owner/canonical scope和已保存isAbstract后，26项跨轮全通过。阶段62模块resourcePath/资源复制已通过限定回归；下一步处理函数accessModifier持久化，imported object自动load仍未明确所有权。阶段57提交为 `3865673`、阶段58提交为 `e6f83fe`；阶段59提交为 `6200561`；阶段60限定 consumer body export 已由 TemplateInitialization6 + ConstructorExecution7 联合通过。未运行完整check或服务端；imported object自动load、成员访问控制、未知range端点及浮点/混合迭代仍保留缺口，整体17项迁移未完成。

在这些项目完成前，核心路径仍存在 MCFPPValue / Concrete 判断，不能宣称已经完成原方案阶段 6。
现有持久化浮点数据不会自动转换布局，完整的持久化迁移 API 仍待实现。

## 库索引

库索引采用 MCFL 格式头与版本 12，保存别名目标、接口标记及有序模板字段初始化表达式。阶段 56 因增加字段 RHS 元数据升级并重建标准库；版本 11 及更早索引要求重新编译。旧浮点用户持久化数据不会自动迁移。
集合 IR、形状事实与动态索引的 Location 属于瞬态分析数据；Function.typedIR、runtimeEffect 与 Var.storageBinding 不序列化。本轮 MCFL 12 升级针对模板字段初始化表达式 metadata，不自动转换用户已有的持久化范围或浮点载荷。
类型布局、语言签名或 MNI 元数据改变后，运行：

```sh
./gradlew regenerateStdlib -Dorg.gradle.jvmargs=-Xmx2g -Pkotlin.daemon.jvmargs=-Xmx2g
./gradlew check --rerun-tasks -Dorg.gradle.jvmargs=-Xmx2g -Pkotlin.daemon.jvmargs=-Xmx2g
```

regenerateStdlib 从 src/main/mcfpp 重建 src/main/resources/datapack/bin.mclib，不重写数据包。
随后单独执行 check，确保 processResources 复制的是新索引。

## 验证限制

新增测试断言类型关系、重载、诊断、常量快照、缓存隔离、数据流和生成命令的执行结果。
基本块命令执行器严格拒绝未支持的指令，并检查入口栈帧在各可达返回路径上平衡。
旧测试中仍有仅打印结果的用例；构建成功不能代替全部语言行为验收。
当前没有配置目标 Minecraft 服务端，实际服务端验证尚未完成。
阶段58的37个不同必要用例跨轮各自通过（TemplateFieldInference 6项先5过1fail、修复后定向最后1项通过；其余31项全过），不是一次联合37项。MCFL12 schema/bin 267356不变，未重建stdlib/fullcheck/服务器。阶段59之后MCFL13标准库为282180 bytes，六套件联合50项通过；首次标准库54个语言错误由annotation callback缺失currFile上下文导致，恢复declaration context后解决，详见[验证记录](./verification.md)。phase56 consume记录的9119条 `flatExtends`重复继承字段警告仍待清理；消费端物理库函数体、方法owner及object自动load仍未解决。未运行完整check或实际服务端；最近完整346项仍属于提交72dc557。

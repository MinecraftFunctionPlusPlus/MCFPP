# 类型系统续接记录（2026-10-04）

本轮从 2026-10-04 续接，首次存储/视图纵向路径通过 161 项测试。阶段54已提交`107e8ac`，阶段55已提交`4b5b7bd`，阶段56已提交`0fb7cb8`，阶段57已提交`3865673`。阶段58普通模板inferred字段已接入部分声明绑定支持并提交`e6f83fe`，37个不同必要用例跨轮各自通过。阶段59导入声明环境恢复与MCFL13重建后，50项指定测试联合通过，提交`6200561`。阶段60 consumer函数体导出限定回归13项联合通过，提交`3468341`。阶段61的26个不同用例跨轮各自通过。阶段62目录/JAR/ZIP资源复制与模板初始化联合11项通过。阶段63 MCFL14库权限往返联合14项全部通过，提交`1918db9`。阶段64 MCFL15字段/Property访问权限与while帧修复后，最终定向7项全部通过，提交`bda4d5a`。阶段65字段权限联合11项全过；阶段66当前实例未限定字段寻址的LibFieldAccess4 + ConstructorExecution7联合11项全过，MCFL15/bin286207未变。阶段67 generic类readonly源码特化及namespace类别恢复已验证，MCFL16/bin286243；41个不同用例跨轮各自通过。阶段68未注解generic类T绑定限定语法已验证，17个不同用例跨轮通过，MCFL16/bin不变，已提交`adf50f8968483c77fcca7a482e37a2eef8eae871`。阶段69显式generic类型及跨库canonical特化限定验证通过，MCFL17/bin289989，19个不同用例跨轮各自通过。阶段69已提交`10267379e30bd43af6c38bcd7e6a673d195a6102`。阶段70六个生产文件和一个fixture限定验证通过，36个不同用例跨轮各自通过，提交记录见Git历史；MCFL17/bin289989未变，无stdlib/fullcheck/服务器。阶段70已提交`1d593d19c71f5c42a3adb4aaf0e2dbdaae5c1b16`；阶段71四prod+fixture有限类型值路径6个不同用例跨轮各自通过，最终必要2项复查，提交记录见Git历史，MCFL17/bin289989未变。阶段72限定静态类型集合路径跨轮5个不同用例各过，磁盘4/9/frame0；阶段73最终必要联合2项全过，模型及磁盘4/9/frame0；下一步阶段74尚未实现或测试。最近完整346项仍属于`72dc557`；整个17项重构未完成。

## 当前进度

阶段52 NBT地址与宏捕获已提交`dadb6cc`；阶段53对象身份`890833b`；阶段54候选解析`107e8ac`；阶段55 receiver/帧`4b5b7bd`；阶段56有序RHS持久化与MCFL12`0fb7cb8`；阶段57 const真实初始化`3865673`。阶段58普通模板 inferred 字段已接入限定语法域的声明绑定并提交`e6f83fe`；37个不同必要测试分轮各自通过。阶段59导入声明环境恢复已提交`6200561`，MCFL13标准库重建成功，六套件联合50项全过。阶段60限定消费端函数body导出已实现，TemplateInitialization6 + ConstructorExecution7联合13项通过。阶段60已提交`3468341`；阶段61 owner/scope限定迁移已完成并经26个用例跨轮验证。阶段62模块资源复制已验证，MCFL13/bin282180不变。阶段63函数权限持久化已完成，MCFL14/bin285207，提交`1918db9`。阶段64已完成字段/Property权限修复并提交`bda4d5a`，MCFL15/bin286207；最终字段方法1项+LogicStatement6项通过。阶段65来源权限检查的LibFieldAccess3 + TemplateInitialization8联合11项全过，MCFL15/bin不变。阶段66当前实例未限定字段访问LibFieldAccess4 + ConstructorExecution7联合11项全过，producer、consumer及磁盘执行覆盖5/9/8/6。阶段67 generic类readonly签名、源码特化及namespace类别恢复已限定验证，MCFL16/bin286243；阶段68未注解普通generic类T绑定亦已限定验证，17个不同用例跨轮各自通过。阶段69显式generic类型签名与跨库canonical特化已限定验证，MCFL17/bin289989，19个不同用例跨轮各自通过。阶段细节与分轮测试见verification.md。阶段70声明scope及绑定T/N限定路径已验证，36个不同用例跨轮各自通过（29+Logic6+最终1），MCFL17/bin289989未变；阶段71有限Declaration/Applied TypeValue恢复已验证，6个不同用例跨轮各自通过，最终必要2项复查；阶段72完整静态类型集合限定路径已验证，5个不同用例跨轮各自通过；下一步阶段73冻结Specialized类型值已限定验证，最终必要联合2项全过；下一步阶段74源码联合类型与冻结身份尚未实现或测试；imported object自动load仍独立未解决。
用户随后要求继续完成并按进度提交；该轮实现已提交为 747f0b4，擦除 while 不动点绑定提交为 467343f，list/dict 元素贯通提交为 a1bafaa，集合编码与项目隔离提交为 702fbce，嵌套静态副本及编译上下文隔离提交为 8ce9fde，静态集合擦除通道及元数据缓存隔离提交为 dd3b43a，完整静态 as 视图的共享写入提交为 e73fa03，字典成员统一及输入帧修复提交为 6315054，列表共享成员及查找后端提交为 217a5cd，字典整体合并事实与空键边界提交为 47a7b5c，map 共享成员、位置与 entry 布局迁移提交为 fa64338，只读列表与 NBT 数组迁移提交为 a623399，实际 IR 调用与递归效果分析提交为 f5a9902。集合 IR 控制流与子位置证据提交为 72dc557。动态列表 IR 提交为 9f74d5e。字典成员 IR 提交为 4bed87f。列表变更成员 IR 提交为 21224b0。列表查询与按值删除 IR 提交为 ea7dca8。NBT 数组 IR 提交为 313886b。map 索引与成员 IR 提交为 9245a62。map 投影 IR 提交为 ab1f3cb。do…while 与闭合整数区间循环 IR 提交为 711331e。IR 词法作用域提交为 eef6d44。调用子形状提交为 0c3e65f。整数范围值和命名范围 IR 提交为 8aee6b7。递归返回/写回形状提交为 eb83a60。26.3 原生浮点 IR 提交为 7cd1a69。2026-10-06 继续显式转换 IR，阶段 47 提交 b56ede9，阶段 48 提交 860c799。阶段 49 旧浮点加减提交 `82d955b`，阶段 50 乘除代码提交 `bee57c1`、静态审计文档提交 `dfdb99a`；阶段 51 旧浮点 IR 最终复查 20 项通过。阶段 49 必要检查 46 项、阶段 50 必要检查 36 项。MCFL 保持 11，未改签名/缓存结构且未重建 bin.mclib。最近完整检查仍为 72dc557 的 346 项，整体 17 项迁移未完成。

本次用户要求读取文件，继续上一会话尚未完成的项目任务。读取交接与下一阶段计划后，继续类型系统迁移，未扩展到独立 MNI 元编程计划。
原始语言约束仍以 [上一会话交接](./session-handoff-2026-10-04.md) 为准；当前范围以 [迁移状态](./migration.md) 为准。

## 工作区与构建环境

- 本次工作目录为 `F:/Project_MCFPP/MCFPP`，分支 `kotlin-latest`，起始 HEAD 为 `f47a852`。
- 用户后续明确要求每次取得进度都提交已有改动；已验证实现和文档按阶段提交，提交记录以 Git 历史为准。没有创建 PR 或部署数据包；按用户要求由主代理规划，指定子代理编码与执行。
- 用户最新要求避免过度工程化和防御性代码，优先优雅可读；整项任务完成前只做必要测试。后续按受影响路径验证，不按每个小阶段重复全量构建，索引仅在签名/布局/元数据变化时重建。
- 机器原 JAVA_HOME 指向不存在的目录。使用工作区外的 Temurin 21 和 Gradle 8.14，校验下载包 SHA256 后运行；没有修改项目依赖或 Java 工具链要求。
- Windows 构建需要可用的 Java Unix-domain socket 临时目录；本轮设置 jdk.net.unixdomain.tmpdir 后执行。工具下载、镜像初始化与构建日志位于 `F:/DevCache/.codex/runtime`，后续会话不要将该缓存当成仓库必需文件。
- 记忆插件的本地 CLI 不可用，本次以仓库交接文件和实际源码为事实来源。保留工作区已有的记忆目录及忽略项。

## 本次接入的实现

1. 新增 `analysis/StorageAccess.kt`。StorageBinding 保存实际地址，StoredData 保存不可变事实、位置写入版本和布局缓存；延迟初始化只捕获冻结的编码或物理寄存器名称。
2. 语言 as 的两个 visitor 建立 TypedView，保持同一 Place，复用 checkReinterpretation。未使用的视图不物化，实际访问不转换来源编码；普通无法证明的来源警告，any 不添加运行时检查。
3. MCAny/MCObject 使用同一无标签载荷，删除 lastVar。compilerPayload 仅保留内部静态特化通道，不进入运行时 NBT 或不可变缓存键。
4. 扩展 PrimitiveCompiler 的 int/bool/any/object 声明、形参、返回和分支；Exact/Candidates/Unknown 的分析独立于常量折叠。普通调用只传播类型知识，不利用常量实参执行用户函数。
5. 模板字段从共享位置读取，写入使重叠缓存及事实失效，保留已知兄弟字段。只有显式 as 初始化绑定共享视图，普通赋值、参数与返回复制载荷；副本不采用原型默认值作为事实。
6. 调用前捕获擦除/模板/NBT 参数；static 写回地址移动到调用方帧。表达式先捕获左值的已求值结果，调用保存/恢复活跃临时值；较早参数也跨后续参数中的递归调用保存。
7. 受限 IR 函数推导 Pure/Unknown。未知调用、未标注 MNI 和原始命令前提交可能被观察的数据，之后撤销事实和同步缓存。旧原生函数产生的完整宿主容器变化在失效前重新编码提交，防止丢失变更；已审计数值类使用 NoExternalWrites，完整反射接口仍保留。
8. 库索引升级到 MCFL 版本 3，拒绝旧格式并重新生成 `bin.mclib`。
9. list/dict 下标接入同一存储位置与写版本模型；已知元素保留实际类型，普通副本复制子事实而视图共享位置，未知下标写入合并可能类型并撤销重叠值。动态下标在后续调用前捕获，不同未知下标使用独立寄存器；列表字面量逐项保存求值结果。编译器专用元素拒绝运行时物化，静态副本仍保持独立。
10. 后续集合编码使用可见 NBT 载荷，混合字面量保留完整联合身份；已知复杂字符串键按字面访问，未知字符串键明确给出后端诊断；异构列表、空键路径和宏使用显式目标能力。连续编译清除旧函数标签，并删除无人读取的反向子类型列表，缓存随序列化结构升级为 MCFL 4。
11. 编译器专用字典的普通副本递归复制下层字典和列表，直接 type 字段保留已知编译期值；静态嵌套下标改写不会影响独立副本。
12. 连续项目初始化同步清空词法、语法缓存和当前编译上下文；同一路径文件更新后重新解析，新库函数不继承旧文件域，避免串联历次项目。
13. 集中检查 list/dict 的实际可编码内容；编译器专用擦除载荷和嵌套擦除字段只走静态通道，普通复制递归复制，运行时屏障与调用帧跳过不可物化的值；泛型按完整不可变载荷特化。
14. 元数据图缓存以项目为生命周期；普通/泛型模板和函数标签在新项目中不复用旧对象，当前项目内及 DataObject 基类型的复用仍保留。内存快照确认旧模板缓存是后续完整检查内存耗尽的主要引用来源。
15. 完整编译器专用 list/dict/type 的 as 建立同一 Place 的静态解释视图。CompilerOnly 布局只保存不可变值事实，已知字段/索引写入及整体替换更新共享版本并重建完整祖先快照；缓存读取重新适配最新值。普通集合/擦除副本保持独立，原始命令屏障不物化或撤销静态事实。
16. 原生调用保存真正接收者的宿主快照，按对象身份去重；完整静态宿主变更通过同一写入接口提交，已验证 list 的 clear/add。其余成员、部分已知静态值及任意 Java 对象仍待迁入。
17. 删除 NBTDictionaryConcreteData 及常量字典的独立成员表，所有字典共用四个成员签名及 DictionaryOperations。字典变更只修改接收者，并由 StorageAccess 更新位置事实；已知键删除和标量合并保留兄弟知识，完整静态深合并保持不可变快照与副本独立，未知键及编译器字段写入运行时接收者明确诊断。库索引升级为 MCFL 5。
18. 修复 NBT 临时值降为原始 nbt 及字典工厂丢失泛型身份；旧 visitor 的运行时形参以输入帧为来源，不再捕获未初始化的原型寄存器。dict<int> 参数/返回及含字典操作的 int/bool 形参端到端通过；旧 map 的宿主 clear/merge 桥接行为保留，完整 map 迁移仍未完成。
19. list 的 11 个成员合为一份签名及 ListOperations，变更采用 WritesReceiver，查询采用 NoExternalWrites。完整常量与静态视图使用不可变值更新；运行时变更重排已知元素及嵌套长度，未知范围撤销常量并保留统一类型。动态修改索引先编码捕获，输入先复制后修改目标，负数插入遵循 size + index + 1；MCFL 升至 6。
20. 列表查找使用每次调用独立的结果记分板及来源副本，递归普通命令循环不依赖 return；按值删除通过重建省略首个匹配项支持无宏旧目标。擦除元素类型已知时静态过滤匹配位置，未知元素类型要求显式视图，bool/byte 不混淆。旧浮点正负零的编码查找曾与按原始位折叠不一致；浮点查找保留实际后端。
21. 字典整体深合并后保留已知标量输入的类型和值，冻结重叠来源的事实后再回写。禁止空键路径的目标对已知空键合并输入写入前诊断；当前 NBT 库无法解析含空键的 SNBT 字面量，常量整块编码仍无可用后端。未知运行时来源继续整体深合并，完整编译器专用字典的空键不经过 NBT 编码。
22. map 的 6 个成员合为 NBTMapData/MapOperations，删除 NBTMapConcreteData；采用唯一 entry 列表，以字符串值比较键并浅覆盖整个 value。普通副本/参数/返回独立，as 共享位置，完整静态 map 持久更新祖先快照；已知键写保持位置，未知键写撤销常量并合并可能实际类型。查询/变更标注效果，MCFL 升至 7，旧双份数据不会自动转换。
23. map 的未知键读取使用完整 compound 宏谓词，已知复杂/空字符串键可直接读取，动态键写入、查找、删除及 merge 使用无宏普通命令循环；初始化位于 merge 来源循环之前。keys/keyValueSet 是独立投影，未知键或运行时空键的字典投影明确诊断。字典字面量不再用字段名分配帧槽，修复 value 字段覆盖同名参数的回归。
24. ImmutableList<T> 接入语言类型语法，并使用与 list 相同的元素编码和位置接口；查询成员合为一份只读签名。工厂、复制与返回保留类型，as 观察可变来源的新版本，普通副本独立；元素赋值和变更列表的成员拒绝。常量值共用列表 codec，删除最后的 NBTListConcreteData；泛型继续不变，嵌套元素的可变性由自身类型决定。
25. NBT 数组字面量保留 ByteArray/IntArray/LongArray 身份并支持空数组，不再包装成原始 nbt；不可变快照、复制与物化保留精确数组格式。索引接入 StorageAccess 的实际元素事实、长度、负索引与版本缓存；元素类型为 byte/int/long，未知索引先捕获，无宏目标写前诊断。参数/返回、嵌套及擦除复制使用独立载荷，MCFL 升至 8。
26. 普通自由函数的 int/bool/any/object 调用实际接入 IR；先在私有可达图内准备前向/递归函数、求解类型与效果并重新绑定，再发布 IR 与生成命令。纯类型查询复用同一重载排序和默认参数，歧义停止域回退；临时候选只用于发现，命令生成不接受未解决的调用。
27. EffectAnalysis 求解 Pure/Writes/Unknown 的递归不动点，普通参数副本写入不外泄，static 形参写入映射实际 Place，递归字段组合过长时保守扩大到根位置。ReturnTypeAnalysis 只使用类型证据，static any 输出更新实际类型；已知 any 调用返回可直接参与运算、条件和具体重载，同时保留 any 声明和未知值知识，不执行一般用户函数的编译期求值。
28. IR 调用用独立帧槽捕获参数、返回与活跃临时值，static 写回只撤销受影响的事实；直接返回值具有独立 Place，as 表达式按求值顺序读取。跨目标的递归与相互递归保持帧/条件栈平衡；函数物理存储前缀使用声明命名空间，MCFL 升至 9 并重建标准库。
29. list/dict/ImmutableList 的可编码字面量、已知下标/键、复制与共享视图接入真实 IR 的分支和 while；Construct 建立元素事实，回边不动点阻止借用首轮类型。FlowFacts 保存嵌套列表长度，元素写入保留父长度，整体替换清除旧后代，不同长度汇合后撤销长度；已知负索引归一并检查越界。
30. 集合读取同时捕获子事实和形状，后续 static 调用改写来源不改变较早字面量操作数。普通集合形参/返回使用独立帧载荷并支持递归，static 已知字段写入映射实际位置；类型输出分析仅传播证据，确定改写更新类型，条件改写和未知效果撤销过期类型，保留未改动兄弟事实。集合/擦除视图的导出适配器共用一个 StoredData，普通副本具有独立 Place。
31. IRCollectionValidation 在生成命令前检查旧目标的共同列表编码及空键路径；直接字段赋值不先读取尚不存在的键。上下文字面量保留声明的泛型，空字典保持 dict<any> 的运行时身份，普通集合赋值仍泛型不变；只读槽写入拒绝、嵌套可变元素保留接口。void 调用作为集合元素给出编译诊断，避免内部类型查询异常退出。分析结构均为瞬态字段，本轮保持 MCFL 9 并重新生成标准库索引。
32. 列表动态下标接入同一 IR。Location 保存逻辑范围和独立捕获的索引结果，负数在 RHS 替换容器前归一；嵌套下标、命名共享视图及 static 写回保持原选中地址。未知读取只合并各可能位置共同成立的类型/形状，普通集合副本独立；未知写入撤销重叠后代并合并可能类型，不证明整片范围均已赋值。
33. 宏直接绑定当前帧的 IR 索引槽，带绑定路径的宏函数补齐 `$` 前缀。dynamic 布局保留已证明的下标值，IR 已知字典键复用集中转义。目标能力独立区分宏与 return run，1.20.2 的 IR 控制流使用既有条件栈后端；Location 与效果/类型证据均为瞬态数据，本轮保持 MCFL 9 并复用上一轮索引。
34. 可编码字典的 clear/remove/merge/containsKey 接入 IR，复用原生成员签名与参数排序，字面量实参采用泛型上下文。查询只读，删除具有具体字段效果，清空/合并写接收者；DictionaryFacts 从冻结来源递归合并事实，完整已知输入保留未修改字段，未知来源撤销可能被覆盖的事实。动态接收者使用实参求值前捕获的索引。
35. 列表 add/prepend/addAll/prependAll/insert/removeAt/clear 接入 IR 和接收者写入效果。ListFacts 重排完整元素子树及嵌套长度，批量来源先捕获，空批量不撤销事实；不同长度合流保留共同元素类型，追加保留已有位置。动态插入索引在后续实参调用前捕获数值；负数按成员操作时的长度解释，编码/宏/已知越界在命令生成前检查。
36. list/ImmutableList 的 indexOf/lastIndexOf/contains 和可变列表 remove 接入 IR。查询为 Pure，删除记录接收者效果；IR 与旧入口分别共用 ListValues 常量比较及 ListSearch 命令后端。擦除元素按已证明身份选择匹配位置，未知类型要求视图，不生成运行时类型分派。已知删除保留剩余子形状，未匹配不撤销事实；查询工作区和结果独立，支持无宏目标、动态接收者及递归写回。
37. ByteArray/IntArray/LongArray 和 byte/long 载荷接入 IR 的构造、位置读写、共享视图、普通/静态参数、返回及循环/递归帧。数组快照保持精确格式，long 载荷不经过 32 位记分板；元素运算仍遵守 NBT 数字限制。支持宏的目标对未知长度上的字面负索引捕获归一，无宏目标保留旧入口。修复不可变 long 整数快照恢复时未包装 LongTag 的问题，库格式仍为 MCFL 9。
38. map<T>、字符串下标及六个成员接入 IR；赋值按键浅覆盖或新增 entry，合并先捕获来源，查询纯读，static 变更效果限制到 entries。Location 保存完整字符串键谓词，MapFacts 从物理 entry 路径解析已知键，未知键只保留共同值证据；删除条目后的共享视图继续按键定位。IR 和旧入口共用 MapCommands 的查找/重建/合并循环，未知键写入与成员查询支持无宏目标，未知键直接读取仍检查宏能力。keys/keyValueSet 投影和编译器专用 map 仍走旧入口，MCFL 保持 9。
39. map 的 keys/keyValueSet 接入纯读 MapProjection，结果具有独立 Place、载荷和子事实快照；类型、常量及嵌套长度不会借用后续来源改写。选择器支持直接投影下标及成员查询，键列表运行时提取复用 MapCommands 的无宏循环，字典投影按已知键复制完整 value；未知键及运行时空字段名在后端生成前诊断。编译器专用投影仍保留原入口，MCFL 保持 9。
40. do…while 和直接闭合整数区间 for 接入 IR 基本块。do…while 先体后条件，continue 转向条件、break/return 正常退出；范围两端按顺序捕获一次，每轮复制计数器给独立作用域内的迭代变量，修改该变量不影响进度。整数不经过旧 Float 范围值，递增前检查含上界的结束条件，支持完整 32 位边界和动态空区间。合成变量使用独立声明 token，内部读取不污染用户表达式的类型证据；命名 range、浮点范围和通用迭代器仍保留旧边界。
41. IR 局部声明按当前词法作用域检查重名，同级分支和嵌套块的同名变量具有独立声明 ID 和后台存储。Symbol 保留源码名称，存储名称单独按声明位置分配；初始化读取外层可见绑定，退出块后恢复原绑定。foreach 变量纳入循环体作用域，局部视图共享真实 Place；越界引用直接诊断，防止退回旧入口后泄漏常量分支局部。即使 NBT 局部只写不读，也先初始化内部存储父路径；MCFL 保持 9。
42. 普通调用返回及 static 整体写回传递子类型、嵌套长度和初始化状态。FlowAnalysis 保存返回表达式的独立快照，ReturnTypeAnalysis 每次分析合并返回和写入证据；输入先捕获再清除值知识，返回及写回同样不传播常量值。分支只保留共同形状，效果外兄弟字段不失效，未知范围不证明整片写入；已编译被调函数的容器 TypeId 在调用方结构化登记，旧无宏目标延后到形状绑定后决定负索引回退。递归子形状仍保守，MCFL 保持 9。
43. 范围端点保留 int/float 身份，以可选 left/right 字段的 compound 表示，共用精确快照、复制、版本与存储接口；整数不经 Float。整数范围值接入 IR 构造、命名/擦除/嵌套复制、共享视图、参数/返回及 static 替换，迭代读取已证明的 int 端点并沿用捕获边界的基本块循环。范围构造用 RANGE token 分配临时槽，避免与首端点函数调用碰撞。旧入口左端点跨右端点调用保存，返回槽及返回副本独立；iterator 合为 RangeVarData 一份签名，旧常量迭代器惰性生成元素，运行时未知迭代明确诊断而不强制全部变量动态化或抛 TODO。MCFL 升至 10 并重建标准库。
44. ReturnTypeAnalysis 对同一输入类型/形状的递归帧，从无返回路径开始求解共同输出；直接和相互递归保留擦除实际类型、嵌套长度、范围端点及 static 写回形状。FlowAnalysis 在尚无返回证据的调用处停止该路径，不采用其后返回值；迭代合流移除未共同初始化的子事实，避免递归嵌套输出无限增长。所有跨调用快照清除值知识，递归输入变化仍保守处理，不借用其他载荷的证明。
45. 26.3 原生 float 的算术/比较、循环、普通/递归参数返回、static 写回、擦除视图、集合元素及范围载荷进入 IR；共用 FloatProviders 的表达式构造，帧中保存独立 NBT 浮点值。int→float 使用显式 Promote 指令，覆盖声明、赋值、返回、普通/成员实参和上下文集合字面量；不改变提升前的整数或在 as 中引入转换。负零取负保持符号，非有限常量、反向已知范围与有损 static 写回会诊断。旧模拟浮点和显式转换 MNI 仍保留旧边界，浮点范围尚不可迭代。
46. 既有标量/数组 ConversionData 重载按原生身份进入 Convert，普通同名函数不被替换。short/double/nbt 载荷进入普通参数/返回；int/byte/short 的窄化和扩大保持来源值与目标编码，原生 float 转换用提供器，long/double→int 保留目标 data get，包括常量输入。toNBT 使用独立结果和带来源格式的常量快照，修复 short 编码被重标为 int；DoubleBits 恢复使用 DoubleTag。byte/short 返回保留记分板接口，IR 调用按宽度捕获，避免空 NBT 返回路径并兼容旧入口。Promote/Convert 遵守折叠开关；未知/未实现转换与已知越界有诊断。

## 验证与回归

新增 `StorageViewTest` 的 21 项断言，覆盖无转换/无构造/无补字段、未知载荷复制、编码身份、分支知识、复制与别名、物化缓存、编译器专用特化、普通/静态参数、连续/递归返回、参数求值顺序以及未知副作用屏障。

反向验证暂时关闭 StoredData.write 的版本失效，别名测试实际失败：预期 14，得到旧缓存结果 4。随后恢复实现；最终完整构建、测试总数与标准库结果见 [验证记录](./verification.md)。
全量检查还捕获并修复了累计诊断提前中断、static 浮点写回绑定不随帧移动、模板形参读取旧记分板，以及活跃擦除临时值被递归覆盖的问题。
新增 CollectionStorageTest 的 19 项和完整 189 项均通过；未知下标缓存回归曾实际得到 4 而非 11。旧集合复合赋值测试已迁为命令执行结果断言。
其后 CollectionStorageTest 扩展至 28 项，并新增项目隔离回归；完整 199 项全部通过。检查发现并修复混合列表的包装编码异常，以及全局标签和无人读取的反向子类型列表保留旧项目造成的内存耗尽；缓存结构随之升级为 MCFL 4。
之后 CollectionStorageTest 为 31 项，ProjectIsolationTest 为 3 项，完整 204 项通过。两项隔离回归分别复现词法缓存残留与当前编译上下文残留；该次完整检查通过，保持测试进程原堆内存设置。
最新 CollectionStorageTest 为 39 项，ProjectIsolationTest 为 6 项，特化与调用测试为 11 项，完整 216 项通过。扩展连续编译后仍触发内存耗尽，内存快照确认元数据图缓存保留约 476 MB；项目重置现清除普通/泛型模板与函数标签缓存，三个隔离回归实际由失败转为通过。
随后新增 CompilerOnlyViewTest 的 15 项及一项不可变祖先快照断言，完整 232 项通过。静态视图写入曾实际读取旧 type 值；原生 clear/add 曾遗漏接收者的宿主变更。共享 CompilerOnly 位置、持久快照替换、读取版本更新及原生接收者身份快照修复这些问题；普通静态擦除副本脱离来源绑定。
最新新增 DictionaryMemberTest 的 11 项，完整 243 项通过。字典旧双签名及 remove/merge 后的过度失效在迁入前实际失败；端到端函数调用随后暴露临时类型丢失与形参原型寄存器未初始化，修复后真实命令执行结果通过。标准库重建为 0 错误/0 警告，MCFL 5 拒绝上一格式。
随后新增 ListMemberTest 的 19 项，完整 262 项通过。旧列表成员首批 10 项实际失败 8 项；动态宏索引、未知范围类型及旧浮点正负零查找均有独立失败回归。统一签名、接收者位置更新及无宏/无 return 的查找/删除循环通过联合验证。标准库重建为 0 错误/0 警告，MCFL 6 要求旧索引重编译；NBTListConcreteData 仅为 map/ImmutableList 的未迁入路径保留。
进一步扩展 DictionaryMemberTest 至 16 项，完整 267 项通过。部分合并的空键路径和整体合并后的标量类型均有实际失败回归；当前库的空键 SNBT 解析限制阻止常量整块编码，现给出写前诊断。重叠来源先冻结事实，未知运行时来源的整块深合并与完整静态空键分别验证；标准库 0 错误/0 警告，库格式仍为 MCFL 6。
随后新增 MapMemberTest 的 16 项，完整 283 项通过。擦除 map 的未知键写入先丢失共同类型，修复类型后实际执行仍得到 4 而非预期 14；字面量 value 覆盖同名参数的命令回归修复后得到 14。官方 1.20.2 字节码确认 compound 宏参数的 SNBT 插值；复杂键、自合并、浅覆盖嵌套值、静态共享写入及独立投影均有执行/快照断言。标准库 0 错误/0 警告，MCFL 7 要求旧索引重编译；NBTListConcreteData 仅供 ImmutableList 的未迁入路径保留。
随后新增 ImmutableListTest 的 6 项和 NbtArrayTest 的 11 项，完整 300 项通过。只读列表首批 6 项中 5 项实际失败，数组首批 9 项中 8 项实际失败；类型语法、常量工厂身份和实际元素位置接入后，相关 73 项及补充边界的 17 项均通过。空数组测试取值位置已按真实局部帧修正，嵌套/擦除副本保持独立；标准库 0 错误/0 警告，MCFL 8 要求旧索引重编译，NBTListConcreteData 已全部删除。
随后新增 IRCallTest 的 17 项、EffectAnalysisTest 的 5 项和 TypeBindingTest 的 4 项，联合 91 项及完整 326 项通过。首批 7 个有效调用用例在接入前实际失败；非法循环调用曾丢失回边而导致绑定反复重试，线程快照确认后修复为逐个嵌套语句收集诊断并保留剩余 CFG。直接 any 返回运算和常量条件返回检查分别有失败回归；细化时触发的两处 any 声明类型错误也已修复。标准库 0 错误/0 警告，MCFL 9 拒绝旧索引，完整检查仍使用原测试堆设置。语法样例修正与具体失败证据见 verification.md。
随后新增 CollectionIRTest 的 18 项及 2 项值模型形状断言，联合 151 项、最终集合 18 项及完整 346 项通过。首批 7 项在迁入前失败；扩大回归发现旧目标编码检查被绕过及字典字面量缺少上下文，补查又实证 static 元素旧类型、条件改写借用输入类型、嵌套捕获形状丢失及空字典身份错误。已知键直接赋值不再预读缺失字段，捕获快照与子位置类型输出修复这些问题；void 元素异常退出改为编译诊断。标准库 0 错误/0 警告，瞬态 IR 数据不改变库格式，本轮保持 MCFL 9；完整检查仍使用原测试堆设置。
本轮新增 DynamicIndexIRTest 的 13 项；动态下标、集合、存储、值模型、调用和效果共 6 个套件、106 项必要检查通过，0 失败/错误/跳过。扩展检查修复 IR 复杂键遗漏转义，并校正旧无宏断言中 dynamic 布局与未知值混淆的问题；嵌套双索引及 RHS 替换容器后的负索引均有命令执行结果。最新日志为 `mcfpp-dynamic-index-ir-final.log`，最近完整 346 项属于 72dc557，本轮未重复完整检查或标准库重建。
随后新增 DictionaryIRTest 的 5 项，联合字典成员、动态下标、集合 IR 与效果套件，共 57 项全部通过。两项旧字典回归暴露合并字面量缺少泛型上下文，修复后保留参数类型规则与未知输入边界。最新日志为 `mcfpp-dictionary-ir-final.log`，当前 XML 为 5 个必要套件；MCFL 仍为 9，未重复全量构建或标准库重建。
随后新增 ListIRTest 的 5 项，联合 7 个套件、79 项通过。未知长度下共同类型丢失已修复，已有位置与混合范围分别有正负断言；空批量补查只重跑列表专项 5 项，全部通过。当前 XML 为列表专项，日志为 `mcfpp-list-ir-final.log` 与 `mcfpp-list-ir-empty-batch.log`；MCFL 仍为 9，没有重复全量构建。
随后新增 ListQueryIRTest 的 5 项，联合 6 个套件、53 项全部通过。清理旧后端不再使用的导入及辅助函数后，单独 compileKotlin 通过。日志为 `mcfpp-list-query-ir.log` 与 `mcfpp-list-query-ir-compile.log`；MCFL 仍为 9，没有重复全量构建或标准库重建。
随后新增 NbtArrayIRTest 的 7 项，联合数组旧入口、动态下标和 IR 调用，共 4 个套件、48 项全部通过。首轮发现数组 as 仍退回旧路径，补齐入口后通过；无宏目标的字面负索引保留原后端，并有兼容回归。精确 long 边界、空数组格式、复制与视图、static 写回、循环及递归均有断言。日志为 `mcfpp-array-ir-final.log`；没有重复全量构建或标准库重建。
随后新增 MapIRTest 的 6 项，联合 map 旧入口、动态下标、值模型和效果套件，共 5 个套件、54 项全部通过。检查修复遗漏的 map<T> 类型语法入口；查询循环恢复已有嵌套 execute 写法以兼容执行器。覆盖已知键类型、未知键共同证据、视图重定位、键及接收者求值顺序、浅覆盖合并、自合并、循环与递归 static 效果。日志为 `mcfpp-map-ir-final.log`；没有重复全量构建或标准库重建。
随后新增 MapProjectionIRTest 的 5 项，联合 map IR、旧成员入口及集合 IR，共 4 个套件、45 项首轮全部通过。覆盖投影副本隔离、实际类型和嵌套长度、直接下标、循环回边、键列表递归返回、后续实参静态改写前捕获及投影边界诊断。清理未用导入和新增参数遮蔽后，单独 compileKotlin 通过。日志为 `mcfpp-map-projection-ir.log` 和 `mcfpp-map-projection-ir-compile.log`；没有重复全量构建或标准库重建。
随后新增 LoopIRTest 的 6 项，联合擦除控制流、IR 调用和集合 IR，共 4 个套件、50 项通过。覆盖 do…while 的条件时机、至少一次写入和回边诊断，以及区间边界捕获、变量副本、嵌套作用域、break/continue、整数极值、空区间、返回与递归帧。首轮一处正向样例因 any 声明语法错误失败；修正同类正负样例后全部通过，并确认负向回边实际触发语义诊断。日志为 `mcfpp-loop-ir-final.log`；没有重复全量构建或标准库重建。
2026-10-06 新增 ScopeIRTest 的 5 项，联合循环、集合和调用 IR，共 4 个套件、46 项通过。检查发现越界引用退回旧入口导致局部变量泄漏，已改为直接诊断；测试中误用保留字也已修正。进一步分离源码名与后台存储名后，只复查作用域、循环和调用的 3 个套件、28 项，全部通过。日志为 `mcfpp-scope-ir-final.log` 与 `mcfpp-scope-ir-storage-names.log`，没有重复全量构建或标准库重建。
随后新增 CallShapeIRTest 的 6 项，联合集合、调用、动态索引和数组 IR，共 5 个套件、61 项通过。嵌套返回/透传、较早参数捕获、static 整体替换、不同长度返回分支、数组长度和普通实参不选择分支均有断言。首轮复现无宏目标过早回退及嵌套返回类型缺少调用方登记，修复后通过；返回子值明确断言为 Unknown。日志为 `mcfpp-call-shape-ir-final.log`。该轮检查范围入口后先补齐调用形状这一基础能力，没有直接将旧 Float 端点按 int 读取。
随后新增 RangeIRTest 的 8 项，联合循环、调用形状、IR 调用及库缓存套件，共 5 个套件、40 项通过。修复范围返回槽缺少地址、as range 类型入口遗漏及 any 探索阶段过早回退；旧入口动态范围物化与常量迭代分别断言。最后补齐旧入口返回载荷写出、返回副本与左端点保存后，范围和调用的 2 个套件、25 项通过。日志为 `mcfpp-range-values-final.log`、`mcfpp-range-values-legacy-return.log` 与 `mcfpp-range-values-stdlib.log`。MCFL 10 头部已核验，标准库重建为 0 错误/0 警告，没有重复完整 check。

本轮 CallShapeIRTest 再新增 6 项，联合范围、IR 调用和动态索引共 4 个套件、50 项通过。直接/相互递归、static 整体替换、无返回递归、变化输入反例及递归嵌套形状收敛都有断言；测试整理后单独 compileTestKotlin 通过。日志为 `mcfpp-recursive-shape-final.log` 与 `mcfpp-recursive-shape-cleanup.log`，没有完整 check 或标准库重建。

本轮新增 FloatIRTest 的 6 项，联合原生浮点、IR 调用、范围和集合共 5 个套件、59 项通过。旧常量测试改为读取集中快照；补齐已知浮点范围的端点顺序诊断后，浮点/范围 2 个套件、14 项通过。日志为 `mcfpp-float-ir-final.log` 与 `mcfpp-float-ir-range-bounds.log`。本轮 MCFL 保持 10，没有完整 check 或标准库重建。

本轮新增 ConversionIRTest，常量 NBT 编码修复后转换/浮点/调用共 5 个套件、52 项通过；复现并修复 byte/short 返回接口后，转换/数组/调用 3 个套件、32 项通过。最后扩展旧与 IR 调用方的返回用例，ConversionIRTest 的 8 项通过，当前 XML 为这个套件。日志为 `mcfpp-conversion-ir-final.log`、`mcfpp-conversion-ir-abi.log` 和 `mcfpp-conversion-ir-return-callers.log`，MCFL 保持 10，没有完整 check 或标准库重建。

承接显式转换 IR 提交 `eff418f`，阶段 47 修复旧浮点编码、比较和非有限值诊断，MCFL 升至 11 并重建标准库。新增 LegacyFloatLayoutTest 5 项，联合 ConversionTest、FloatProviderTest、FloatIRTest 与 LibCacheFormatTest 共 36 项通过；这是历史阶段记录。

阶段 48 修复旧 `_scoreto` / `_toscore` 指数缩放：旧值按 `sign * (int0 * 10000 + int1) * 10^(exp-8)` 解释；int→旧浮点采用八位十进制有效数字截断（不同于常量 codec 的 nearest/ties-to-even），零规范为全零，Int.MIN_VALUE/MAX_VALUE 均损失低位，±2147483648 附近结果为 ±2147483600。旧浮点→int 向零截断；未知运行时超范围饱和到 Int.MIN_VALUE/MAX_VALUE，已知非有限/超范围复用 `NumericConversion.floatToIntError` 编译诊断。仍不做 宿主常量折叠，旧转换仍未接入旧浮点 IR。

阶段 48 新增 6 个转换测试，包含实际库函数执行、诊断及后端选择；LegacyFloatConversionTest 6、LegacyFloatLayoutTest 5、ConversionTest 12、FloatProviderTest 10 联合共 33 项通过，0 failures/errors/skips；日志为 `F:/DevCache/.codex/runtime/mcfpp-legacy-float-conversions.log`。MCFL 保持 11，未改索引结构/签名，未重建 bin.mclib；服务端未验证。详见 [验证记录](./verification.md)。

阶段 49 完成 `_add`/`_rmv` 共用主体：零先分流，正幅值对齐一个十进制保护位和 sticky 位，精确十进制加减后向零截断至 8 位有效数字；不做宿主常量折叠，右实体分量只读且不依赖 return。9/10 位工作尾数归一化正确，乘除共用 align 叶子未改；删除无引用的 `rmv_swap`，保留仍被 `math.3vec_float` 使用的 `add_swap`/对齐叶子。Executor 仅扩展 single identity execute as、score 位置 `@s`、交换与 score 条件链。新增 LegacyFloatArithmeticTest 6 项；与 LegacyFloatConversion 6、LegacyFloatLayout 5、Conversion 12、IRCall 17 联合 46 项通过，0 failures/errors/skips。测试含实际库函数执行，但并非 6 项全都执行 mcfunction。日志：`F:/DevCache/.codex/runtime/mcfpp-legacy-float-add-sub.log`。MCFL 11 不变，未改签名/缓存结构、未重建标准库；未 full check、未实际服务器验证。

阶段 50 完成旧浮点四分量乘除：任一符号为零（含分母为零和 0/0）在运算前规范为四零，不执行会失败的 `/=0`/`%=0`，不新增已知零诊断或 IEEE 特殊值；非零乘法保持精确截断，除法固定 7 次长除，若 A<D 再一次，精确商向零截断为 8 位。右分量只读、无 return 依赖；仍被 inverse/3vec 引用的 `div_align` 保留。LegacyFloatMultiplyDivideTest 7 项与加减 6、旧转换 6、旧布局 5、Conversion 12 联合 36 项通过，0 failures/errors/skips。部分测试为 executor/入口断言，并非全是库函数执行。日志：`F:/DevCache/.codex/runtime/mcfpp-legacy-float-mul-div.log`。MCFL 11 未变，未重建标准库、未 full check、未实际服务器验证。

阶段 51 已将旧浮点算术/比较、Promote/Convert 接入 IR。旧浮点四分量放入独立 NBT 帧，纯 `LegacyFloatCommands` 负责读写与调用，返回仍用旧四记分板 ABI。普通/递归/static、旧与 IR 双向调用、早先参数、多实参、常量和连续返回均通过真实库命令执行。旧调用入口在入栈前独立捕获浮点实参；8 处工作区判断改为对象身份；运行时赋给 Concrete 浮点目标时丢弃旧默认值，纯浮点返回也初始化内部 NBT 槽。native 路径不变；旧浮点算术/比较及跨数值折叠禁止宿主 Float 计算，`16777217` 保持八位十进制精度；identity/toNBT 保留来源 codec。包含 FloatBits 端点的旧浮点范围，其静态顺序不使用宿主比较，整数/native 不变；浮点迭代语义未定义，不新增行为。已知 int/bool/byte/short as legacyfloat 仍走旧入口并在实际访问时诊断；未用视图不报错，unknown any 无运行时 typecheck，命名 float 视图的来源随后被写成已知标量，再读取视图会诊断。

测试首轮 51 项（LegacyFloatIR 9、FloatIR 6、ConversionIR 8、IRCall 17、旧转换 6、旧布局 5）有 3 项失败；修 identity/诊断后首次复查因 4 处智能转换编译失败，现已修复。随后 22 项有 1 项失败，其中算术 13 项通过；修复 Concrete 目标默认 0 覆写后，最终复查 LegacyFloatIR 9、LegacyFloatLayout 5、LegacyFloatConversion 6 共 20 项全部通过，0 failures/errors/skips，Gradle exit 0。日志 `F:/DevCache/.codex/runtime/mcfpp-legacy-float-ir-final.log`；历史日志为 `mcfpp-legacy-float-ir.log`、`mcfpp-legacy-float-ir-interop.log`、`mcfpp-legacy-float-ir-interop-final.log`。MCFL 11 未变，无标准库重建、完整 check 或服务器验证。

## 阶段 52：NBT 地址等价与自动宏捕获

`NBTAddressKey` 冻结地址 source 与 path segments；按快照比较路径段和长度，父子路径同时检查 source，修复 equals 自递归。自动宏参数写入独立 arg 槽，来源为实际绑定值或 scoreboard。`FloatProviders.preparePath` 的重复动态 index 预写已删除，保留原读取次数断言。

必要复查 NBTAddressTest 7、FloatProviderTest 10，共 17 项通过，0 failures/errors/skips。首轮 NBTAddressTest 7、FloatProviderTest 10、CollectionStorageTest 39、MapMemberTest 16 共 72 项有 1 项失败（FloatProvider 的 index 读取次数预期 2、实际 4），其余 71 项通过；移除重复预写后只复查直接受影响的 17 项。日志：`F:/DevCache/.codex/runtime/mcfpp-nbt-address.log`、`mcfpp-nbt-address-final.log`。MCFL 11 未变；未重建标准库、未运行完整 check 或实际服务器。

## 阶段 53：宿主值对象身份

删除 `Var`、`Pos3Var`、`Pos2Var`、`PosDimension` 共 8 个 equals/hashCode 覆盖；宿主值统一对象身份，语言值比较仍使用 `CompilerValue`。表达式缓存只移除请求的引用，spill 去重只合并同一对象引用。括号子 visitor 共享父级活跃值列表、保留自身结果字段与函数参数 visitor；修复真实递归表达式 sum 期望 24、错误得到 49。直接 spill 用例通过 stack prepend/remove 建立执行器帧。

首轮 VarIdentityTest 5、NBTAddressTest 7、StorageViewTest 21、ListMemberTest 19、SpecializationPolicyTest 11 共 63 项有 2 项失败；后四套件共 58 项通过。修复后最终复查 VarIdentityTest 5、StorageViewTest 21、LegacyFloatIRTest 9，共 35 项通过，0 failures/errors/skips。日志：`F:/DevCache/.codex/runtime/mcfpp-var-identity.log`、`mcfpp-var-identity-final.log`。MCFL 11 未变；未重建标准库、未运行完整 check 或实际服务器。

## 阶段 54：模板构造器候选解析

构造候选现通过 `ParameterMatcher.match`/`best` 选择，复用类型、完整值、默认实参和歧义规则；删除旧字符串/类型顺序接口，只有 Selected 初始化对象，错误 `UnknownVar` 不重复诊断。常真 if 与静态 false→true else-if 主体内联不再调用悬空函数。模板构造常量特化、`this`/`preInit` 未改。

首轮 ConstructorResolutionTest 4、TypeBindingTest 4、StorageViewTest 21 共 29 项有 3 项失败，后两套件 25 项全过；中间 ConstructorResolutionTest 4 + LogicStatementTest 6 有 1 项失败，LogicStatementTest 全过，剩余为测试误禁公共 stack prepend 前言。最终 ConstructorResolutionTest 4 项全部通过，覆盖声明顺序、T! 未知值拒绝/字面量接受、默认实参在 if/else-if 常量分支中的执行、歧义无构造副作用。日志：`F:/DevCache/.codex/runtime/mcfpp-constructor-resolution.log`、`mcfpp-constructor-resolution-final.log`、`mcfpp-constructor-resolution-complete.log`。MCFL 11 未变；未重建标准库、未运行完整 check 或实际服务器。

## 阶段 55：模板构造 receiver 与初始化帧

receiver 独立放入固定 `frame0.this`；普通构造实参不再按普通常量特化，T!/compiler-only 仍遵循 SpecializationPolicy。参数只编码入帧，不污染 callee facts；receiver 写回保留 source type，Unknown effect barrier 可撤销知识。`preInit` 每次运行（包括 AST-null 隐式默认构造）；FrameExit(function,index) 统一 IR/旧路径出口，entry 插入 pop，caller 在写回后 pop。原/特化模板与 static object 构造器均导出。显式类型非 const 字段登记 preInit；static 赋值经 `replacedBy` 后物化，非 const object 字段动态化。普通模板复制规则保持不变。

首轮 8 套件 80 项有 4 项失败（两个 set 保留字样例、typed initializer 漏登记使 94 得 90、executor 缺 unless score matches），其余 76 项通过。随后 ConstructorExecution 7 + ConstructorResolution 4 共 11 项有 1 项 object ctor 文件空；静态写入/物化修复后最终只复查 ConstructorExecutionTest 7 项，全部通过，0 failures/errors/skips。Resolution 4 项此前已全部通过。测试真实执行初始化值、独立 receiver，并检查 static object 导出到 NBT=4。日志：`F:/DevCache/.codex/runtime/mcfpp-constructor-receiver.log`、`mcfpp-constructor-receiver-final.log`、`mcfpp-constructor-receiver-complete.log`。MCFL 11 未变，无标准库重建、完整 check 或服务器验证。

## 阶段 56：模板初始化表达式库往返

`DataTemplateInfo` 有序保存字段初始化 RHS，`DataTemplate.preInit` 为 `LinkedHashMap`；`GenericDataTemplateInfo` 复用已存在的 body AST。源码构造器编译恢复声明文件与命名空间；`@Transient` 文件字段不入库，导入构造器 `file=null` 仍依赖 caller，声明词法 scope 未完整持久化。Kryo 三个自定义 reader 先 reference 再 nested read，修复循环元数据 reader 使用未完成 caller 做 hash；参考 [Kryo 5.6.2 serializer references](https://github.com/EsotericSoftware/kryo/blob/kryo-parent-5.6.2/README.md#serializer-references)。`MCFPPFile.resolveImports` 由生产和字符串测试共享。MCFL 升为 12，独立标准库重建语言 errors/warnings 0/0，`bin.mclib` 267158 bytes。

首次 12 项运行有 9 fail/3 pass（`NativeFunctionInfo.caller` 尚未建立时递归 hash）；中间 13 项过程发生 worker 中断且 XML stale；诊断轮 TemplateInitialization 3 项中循环引用测试通过，另有 1 fail/1 skipped，定位到 object 的 `Defaults.z` RHS 和 Box import 绑定。修复 reader reference 时序及共享 resolveImports/入口绑定后，最终 XML 为 TemplateInitialization 3、ConstructorExecution 7、LibCacheFormat 3，0 failures/errors/skips，BUILD SUCCESSFUL in 30s。日志 `mcfpp-template-initializers-stdlib-final.log`、`mcfpp-template-initializers-complete.log`。最终 consume 每次有 9119 条 `flatExtends` 重复继承字段语言警告，仍待清理。范围边界：object 测试显式 invoke constructor，不证明 object 自动 load；导入 RHS 的声明词法 scope 完整持久化仍待处理。无完整 check 或服务端验证。

## 阶段 57：模板 const 字段真实初始化

`sharedProject.prepareObjectInitializers` 在 runAnnotation 与完整签名/继承 ready 后、用户函数 body 前编译完整本地 object constructor，复用已有 guard。source inferred object 字段上下文、访问和 parsed annotations 有序暂存；FieldVisitor 不试算 RHS，真实 constructor `prepareBody` 单次求值并补 field/property/Symbol。typed const RHS 也登记，普通 typed const 可每实例初始化；incoming 参数先绑定再运行 RHS，两个不同 receiver（1/2）实证正确。const 只限制 readonly Symbol；compiler-only const 保持完整 `ValueSnapshot` 且不物化，T! 独立校验完整值，inferred mirrored 不继承 T!。

field annotation 在 MCFPPAnnotationVisitor 转存后触发 helper 补 annotation stage；函数 annotation 按真实 AST 声明查找，无 fake args。error RHS 不写默认值；self/forward 引用诊断准确。首轮联合 28 项中 27 pass/1 fail，失败仅因负向测试字符串两个顶层声明间少换行，语法早退后 object lookup 抛 NoSuchElementException；加换行后只定向复查该方法，新 XML 1/0，`2026-10-05T22:24:27.881Z`，exit0/BUILD SUCCESSFUL in6s。`--info` 明确出现 `Cannot infer object field 'first'/'later' before its initializer is evaluated`。其余首轮27项已过；没有联合复跑28项。MCFL12 schema未变；stdlib本轮本地重编20个 Slot inferred fields，bin 267356 bytes（+198）。日志 `mcfpp-template-const-stdlib.log`、`mcfpp-template-const.log`、`mcfpp-template-const-order-final.log`。执行器实际运行 load/storage/constructor/main，helper隔离少数 world bootstrap 命令，不代表服务器验证。旧 consume 的9119 `flatExtends`重复继承警告仍是phase56记录，不是本轮新计数。

## 阶段 58：普通模板推断字段声明绑定（受限支持域）

普通模板 inferred 字段复用 `PrimitiveCompiler` 私有图的 Lowering、FlowAnalysis、ReturnTypeAnalysis 做纯 AST 声明绑定：不发布 IR、不执行用户函数、不生成命令。普通 constructor 参数与未绑定 T! 保持 Unknown；同字段跨 overload 要求同一 TypeId。`this` 单字段与此前字段读取可用；anonymous 统一队列，泛型实参绑定后的实例独立完成，继承后 annotations 与模板参数/return adapter 刷新并保留 Symbol/Place。运行时 receiver 按声明类型初始化可编码默认字段，Unknown erased 不伪造 snapshot，NBT codec 遇不可编码 child 返回整体 null；ordinary template local declaration 使用 `buildUnConcrete` 防 DataOnly 空地址写入。pure binding与runtime AST生成分离，不重复执行RHS。未覆盖 native/generic/compiler-only/static、多级this/member method等继续走legacy `extraFunction`。

测试经历：早期定向检查在test compile阶段因Kotlin `Test` 名称冲突失败；随后worker通信中断、无fresh XML。--info 单类诊断6项中两项discarded-probe断言误把stdlib Slot初始化命令算作用户probe；测试helper现只检查本地source function calls。第三项NBT codec NPE导致worker exit，另有一skip。runtime修复后的6项XML为5 pass/1 fail（产品生成非法空地址NBT命令，严格执行器拒绝）；ImVisitor修复后定向该失败方法通过，fresh XML timestamp `2026-10-05T23:09:30.812Z`，1/0/0/0。随后 Const4 + ConstructorExecution7 + TemplateInitialization3 + IRCall17 共31项通过，fresh XML timestamps `23:09:52.487Z`、`23:09:48.914Z`、`23:09:52.695Z`、`23:09:51.101Z`，各自0 failures/errors/skips。故37个不同必要用例跨轮最终各自通过，非一次联合37项。日志 `mcfpp-template-field-inference.log`、`mcfpp-template-field-inference-final.log`、`mcfpp-template-field-inference-diagnostic.log`、`mcfpp-template-field-inference-runtime-fix.log`、`mcfpp-template-field-inference-dataonly-final.log`、`mcfpp-template-field-inference-regression.log`。无stdlib重建/fullcheck/服务端；MCFL12 schema/bin267356不变。

## 阶段 59：导入声明环境恢复

库缓存仅保存导入声明所需的 `namespace` 与 `unsolvedImports`，不序列化整个 `FileScope`/`Project`。读取所有 includes 后，在各自声明文件的 FileScope 恢复 `currFile` 与声明环境；普通函数、构造器和泛型特化均经统一 compile 入口在各自环境编译。首次 stdlib 重建有54个原生 MNI 类型错误、0警告，根因是延迟 class/field annotation callback 时 `currFile` 为空；回调移入恢复的 declaration context，并保存/恢复 `currTemplate` 后，stdlib 重建成功，Project语言诊断0错误/0警告，MCFL13 `bin.mclib` 282180 bytes。

最终单次联合50项通过、0失败/错误/跳过：TemplateInitialization6、SpecializationPolicy11、IRCall17、LibCacheFormat3、TemplateFieldInference6、ConstructorExecution7；fresh XML timestamp 为 `2026-10-05T23:38:42.679Z` 至 `23:38:55.104Z`。首轮红测3项失败（44→99、5→10、错误数0→1）见验证记录。日志 `mcfpp-library-declaration-scope-stdlib.log`、`mcfpp-library-declaration-scope-stdlib-final.log`、`mcfpp-library-declaration-scope-final.log`。未运行完整check或服务器。消费端物理库函数体、方法 owner 恢复及 imported object 自动 load 仍未解决。

## 阶段 60：消费端库函数主体导出

`DatapackCreator`只导出`bodyCompiled`的非Native函数，按实际namespace ID写入；collector遍历`compiledFunctions`、`GenericDataTemplate.compiledTemplates`和模板接口/对象/companion，以identity visited去重。consumer helper从磁盘读取生成的全部mcfunction执行；main/callee均来自磁盘，generic wrapper存在非空文件，prototype不导出，不从Imports/内存补漏。

红测单方法缺少`fixture.defaults:add_0_int`（fresh XML `2026-10-05T23:52:01.504Z`）；修复后TemplateInitialization6与ConstructorExecution7单次联合13项通过，0失败/错误/跳过，fresh XML `23:53:57.493Z`和`23:54:08.422Z`。MCFL13及bin282180 bytes不变，无stdlib重建/fullcheck/服务器验证。日志 `mcfpp-library-body-export-red.log`、`mcfpp-library-body-export-final.log`。方法owner及imported object自动load仍未解决。

## 阶段 61：恢复导入成员 owner 与模板 scope

`FieldInfo`保留无参get并新增owner参数恢复；`DataTemplateInfo`普通/泛型路径使用canonical field scope；恢复本地方法owner，并把canonical field置于方法`scope.parent`首位。三个`FunctionInfo`恢复已保存`isAbstract`，无schema变化。红测发现grammar把 `first.add<int>(3)` 误解成泛型比较；var之后函数调用现优先解析，bucket仍保持首位。object用独立未登记initializer的空scope生成构造prologue，修复测试用例初始化顺序缺陷，不改变production owner语义。

首轮TemplateInitialization8 + ConstructorExecution7 + SpecializationPolicy11共26项，24通过/2失败。其后只复查两个失败方法并通过，fresh XML `2026-10-06T00:09:23.003Z`，2/0/0/0；合计26个不同用例跨轮各自全通过，非单次最终联合26项。首轮TemplateInitialization XML `00:04:27.892Z` 有2失败；ConstructorExecution `00:04:40.457Z`、SpecializationPolicy `00:04:41.396Z`均全过。实际库往返检查实例927/generic12、object7及frame0，并保留继承owner和静态路径。MCFL13/bin282180不变，无stdlib重建/fullcheck/服务器测试。日志 `mcfpp-library-member-owner-red.log`、`mcfpp-library-member-owner-final.log`、`mcfpp-library-member-owner-complete.log`。

## 阶段 62：模块资源复制与归档路径

`Project.readFromDIR/JAR/ZIP`恢复真实resourcePath；归档module使用`datapack/`前缀，extractTo剥除完整sourceDir/并规整entry路径，base-only module不因无packages字段丢失；JAR/ZIP关闭handle。目录、ZIP、JAR fixture精确复制base-only资源、函数和tag，`copyImport=false`不导出Imports。

红测三项均缺`function/marker.mcfunction`（XML `2026-10-06T00:17:38.651Z`）。最终单次联合LibModuleCopy3 + TemplateInitialization8，11项全通过、0失败/错误/跳过，XML时间戳`00:22:51.552Z`和`00:22:53.265Z`，Gradle exit0/52s。MCFL13/bin282180不变，无stdlib重建/fullcheck/服务器。资源关闭参考链接见verification.md。日志 `mcfpp-library-module-copy-red.log`、`mcfpp-library-module-copy-final.log`。

## 阶段 63：库函数访问修饰符持久化

普通、generic、native函数accessModifier进入MCFL14 metadata。`FuncGetter`按原声明owner检查权限；临时`NoStackFunction`只解包词法caller，不改变运行时storage或函数owner。writer每次重建普通/generic缓存写快照，reader保留canonical cache；`FieldInfo.from`带显式owner恢复，并为本地Var/Property恢复`declaredParentTemplate`。函数主体source权限需要真实调用验证：第2测试后来加入producer main调用合法private/protected成员；另覆盖外部拒绝、子类不能访问base private、模型注入PRIVATE native重复写库/读库。无private-native源码语法。

首轮红测3项均发现库权限丢失。首轮14项13通过/1失败，LibMemberAccess XML `00:36:57.610Z`，TemplateInitialization8与LibCacheFormat3通过；失败根因是修改native模型accessModifier后第二次genIndex复用旧PUBLIC写快照，非IR/clone绕过。修writer后重建stdlib Project语言诊断0/0，最终MCFL14 bin285207 bytes；LibMemberAccess3 + TemplateInitialization8 + LibCacheFormat3单次联合14项全通过，fresh XML `00:49:26.509Z`、`00:49:32.487Z`、`00:49:32.476Z`。中间bin282676 bytes不是最终产物。无fullcheck/服务器。日志 `mcfpp-library-member-access-red.log`、`mcfpp-library-member-access-stdlib.log`、`mcfpp-library-member-access-final.log`、`mcfpp-library-member-access-stdlib-complete.log`、`mcfpp-library-member-access-complete.log`。

## 阶段 64：字段与 Property 访问权限

仅`PropertyInfo.accessModifier`新增持久化；Var权限原已由Kryo保存，runtime adapter复制声明access/owner而binding仍Transient。`Function.accessTo`通过`NoStack`/`Internal`从词法caller沿原声明owner判断，不改变runtime parent。`StorageAccess.inFrame(binding, stackIndex)`创建指定帧偏移的地址view，共享data、Place和versions；旧while body以NoStack共享outer while frame，移除无匹配push并登记真实child，物理while帧结构已修复。`FunctionConditionTest`覆盖官方1.20.3 DataPack单ID、function-return和score短路命令子集。

阶段64最终真实库往返：Base.private构造器/while写入4后读5、Child.protected为6、object静态private为7，frame0平衡；负向仅覆盖外部private/protected字段读取及Intruder读取基类private，均有consumer编译错误；没有外部写入回归。object显式初始化，未验证自动load。红测2项producer分别报3/9个语言权限错误（XML `01:08:01.481Z`）。首轮16项15过/1测试执行器不支持嵌套function条件，四份XML时间`01:12:03.039Z`、`01:12:08.757Z`、`01:12:12.153Z`、`01:12:08.744Z`；补执行器后字段方法1项仍因缺失frame0 NBT失败（`01:17:19.814Z`），FunctionCondition补验1项通过（`01:17:28.966Z`）。frame binding偏移修复后复查字段方法1+ConstructorExecution7仍因while返回帧泄漏失败（`01:24:01.542Z`；Ctor7通过，XML `01:24:05.182Z`）。修正while body NoStack共享外层帧后，最终字段方法1+LogicStatementTest6单次共7全过，fresh XML `01:30:51.910Z`、`01:30:57.600Z`，BUILD SUCCESSFUL。15（首轮其余绿）+1（FunctionCondition补验）+7（ConstructorExecution补验）+7（最终字段/逻辑复查）=30个用例跨轮各自通过，不是最终联合30项。三次标准库Project诊断均0/0；最终MCFL15/bin286207 bytes。日志 `mcfpp-library-field-access-red.log`、`mcfpp-library-field-access-stdlib.log`、`mcfpp-library-field-access-final.log`、`mcfpp-library-field-access-complete.log`、`mcfpp-library-field-access-stdlib-complete.log`、`mcfpp-library-field-access-runtime-complete.log`、`mcfpp-library-field-access-stdlib-final.log`、`mcfpp-library-field-access-runtime-final.log`。无fullcheck/服务器。

## 阶段 65：来源感知的未限定字段权限

既有virtual `getVar(key)`只调用一次，null即返回以保留`Internal.fieldVarSet`和stackIndex；单独沿FunctionScope首parent检查来源，任何更近local（含祖先局部）允许，CompoundDataScope中的Property/Var按声明owner/access检查。两个visitor早拒绝并返回`UnknownVar`，Concrete fallback尊重`isError`。限定为访问权限，不宣称普通实例未限定寻址正确；不改变Internal lookup/putVar。

实现前单方法红测失败：XML `2026-10-06T01:49:30.383Z`，最终IntruderLeak读取继承Base.private未被拒绝。producer/consumer均0错误；正向磁盘执行object=7、local shadow=8、Base限定访问=4已通过。最终LibFieldAccess3 + TemplateInitialization8单次联合11项全过，0 failures/errors/skips，Gradle exit0/1m09s；XML `01:52:42.391Z`、`01:52:52.484Z`。consumer负向检查外部private/protected字段读取及Intruder未限定基类private读取均产生预期语言错误。日志 `mcfpp-unqualified-field-access-red.log`、`mcfpp-unqualified-field-access-final.log`。MCFL15/bin286207不变，未重建stdlib/fullcheck/服务器；整体仍有缺口。

## 阶段 66：当前实例未限定字段寻址

只有来源是`CompoundDataScope`普通实例字段声明、且当前scope已有raw `getVar("this")`返回真实`DataTemplateObject`时，才调用`receiver.getMemberVar(key, caller)`。更近FunctionScope局部原样返回raw结果（含`fieldVarSet` null）；object/static、无owner readonly、无receiver和未解析名保持旧路径。未改全局Internal lookup/putVar或帧布局。

实现前红测在producer `errorCount`断言处失败，5个语言错误，首要诊断为`Symbol not defined: hidden`；XML `2026-10-06T02:01:40.044Z`，未进入库/磁盘执行。修复后LibFieldAccess4 + ConstructorExecution7单次联合11项全过、0 failures/errors/skips，XML `02:04:38.416Z`、`02:04:49.046Z`，BUILD SUCCESSFUL in36s。producer/consumer和真实磁盘执行覆盖constructor未限定写入、nested if/while更新、第二实例、shadow/protected读取，值5/9/8/6、frame0平衡；负向权限consumer仍产生预期错误。日志 `mcfpp-unqualified-instance-field-red.log`、`mcfpp-unqualified-instance-field-final.log`。MCFL15/bin286207未变；无stdlib重建/fullcheck/服务器。阶段65已提交`d181e10`。

## 阶段 67：generic 类 readonly 实参与源码特化

`AbstractTemplateInfo`保存generic kind/parent factory，构造器恢复接受明确owner；完整immutable argument snapshot用于`SpecializationKey`及prototype-based `TypeId`。readonly实参作为CompilerOnly静态绑定，不进入实例/default载荷及runtime参数物化。已知完整动态局部值可作为实参，`Var.assignedBy`转换前freeze完整值，保留`n=3`和后续`n=5`；不能证明完整值的runtime parameter仍拒绝，即使调用点传3。DTO转换过滤static/CompilerOnly绑定，普通`GenericDataTemplate`移除过早constructor body traversal并用lazy compile保持真实`this`上下文。MCFL16/bin286243。

验证完整轮次和红测/worker边界见verification.md。特别是最终41个不同用例跨轮各自通过（不是单次联合41）；第五generic fixture单独通过后，其余33项首轮有3失败，再经6项Logic控制及最终5项（含旧失败复查和旧float ABI）各自验证。无fullcheck/服务端。阶段68已限定支持未注解普通generic class实例化后绑定T；generic object、top-level/method annotation持久化、source abstract/final到model及imported object autoLoad仍未覆盖。阶段69显式类型签名与跨库canonical特化限定验证通过；阶段70声明作用域及绑定T/N限定路径已验证；阶段71有限类型值身份恢复已验证；阶段72完整静态类型集合限定路径已验证，5个不同用例跨轮各自通过；下一步阶段73冻结Specialized类型值已限定验证，最终必要联合2项全过；下一步阶段74源码联合类型与冻结身份尚未实现或测试。

## 阶段 68：未注解 generic 类类型绑定

限定的prototype/instance流程在三个入口完成：prototype保留readonly签名及parents，延后字段、constructor、abstract检查；实例绑定T后注册实际members、继承并刷新field/constructor/read签名。producer Int 4/9共享Compiled、Bool true异型；consumer反序检查`scope.types[T]`、Int/Bool字段及constructor/read签名、private Var/Property owner和TypeId。磁盘consumer 4/9，固定外部score `#generic_bool`经过真实if置1，frame0。

红测XML `03:48:33.608Z` 在producer的field、constructor参数和return三处报`Invalid type: T`。首轮17项为16绿/1失败（LibFieldAccess6 1fail，TemplateInitialization8和LibCacheFormat3全过）；失败在磁盘执行末尾读取data-only `flagScore`。一次观察变量复查仍读到未执行else分支变量，true/else continuation均在；调整为固定外部score观察后，单方法XML `2026-10-06T04:00:36.056Z` 1/0/0/0通过。17个不同用例跨轮各自通过，非单次联合；MCFL16/bin286243不变，无stdlib/fullcheck/服务器。详细日志及XML轮次见verification.md。

## 阶段 69：显式 generic 类型签名与跨库 canonical 特化

专用serializer只写immutable specialization `TypeId`，读取shell；includes完成后进入COMPILE阶段恢复canonical prototype/cache。源码readonly实参以冻结CompilerValue恢复，不依赖mutable genericVar/生成index。实际`prepareHeader`复用FieldVisitor，在声明file上下文、绑定实参前运行；共享`completeTemplateDeclarations`提前解析namespace普通形参/返回并按parent-first完成cached实例继承，再刷新签名；晚期ready compile先flat再annotation/completion。模板参数分支刷新`param.typeName`，避免shell残留`Cell[]`使read overload发生namespace ID碰撞，不改全局getter。阶段69当时TypeValue registry只覆盖builtin/formal types；阶段70限定解决绑定T/N表达式，Declaration/Applied TypeValue仍待71。

新fixture在generic与late Base之前声明`readCell`/`readFlag`及同名Int/Bool overload：source bucket index为Int0/Bool1，fresh consumer自然反序为Bool0/Int1；Bool readonly字面实参为`true`，固定score观察值为1。source与consumer实例对象及生成identifier不同，但冻结TypeId相同。constructor参数、private Var/Property owner、protected Base owner canonical，read overload namespace ID不同。consumer实际磁盘结果4/9/Base2/Bool1、frame0。Plain<3>及缺失Cell实参负例各自独立并诊断。helper进入COMPILE阶段以匹配正式流程。

红测 `mcfpp-generic-template-explicit-type-red.log` XML `04:14:18.082Z`，1失败/producer7 errors/6s。首轮19项 `-final.log` exit1/1m29s：LibFieldAccess8为6过2失败（bare Cell未拒绝、Kryo只读集合add异常）；TemplateInitialization8和LibCacheFormat3通过。必要5项 `-complete.log` exit1/18s：LibFieldAccess2均失败、Cache3通过；库读取成功，但bare Cell提前返回遗漏、字符串helper仍READ_LIB未tryResolve，consumer两处read调用未定义，未进入consumer断言。最终 `-restored-final.log` 正常worker、exit0/24s：LibFieldAccess2与LibCacheFormat3共5全过，XML分别`04:46:28.322Z`、`04:46:33.118Z`。17个首轮绿+最终2项=19个不同用例跨轮各自通过，非单次联合19。MCFL17/bin289989；本轮stdlib Project 0 errors/0 warnings、BUILD SUCCESSFUL in12s；无fullcheck/服务器，详见verification.md。

## 阶段 70：类型表达式声明作用域

ConcreteExprVisitor/MCFPPType/Function/FieldVisitor透传lookup scope和真实caller，FunctionScope checked raw null保留，其他type scope不假设vars、不回退caller.vars，无fake Function。七处binary索引修为`ctx.op[i-1]`；ImVisitor七处whole-scope转换跳过CompilerOnly及无runtime表示，保持trackLost/barrier；DataTemplate两结构检查仅skip isStatic，保留真字段类型与原nullable规则。

fixture以T=bool/N=90影子、Envelope<Int,2>/Bool4反序consumer验证nested Cell<(T)>/Sized<(N+1)>字段/constructor/method参数及返回canonical和private owner。readonly N快照2/4、Sized3/5；最终producer0 errors/0 warnings，consumer0 errors/已知9119 warnings，模型及磁盘4/6/8/bool1/frame0到达。

`-red.log`因N不完整和ctx.op越界fatal，无fresh XML，旧04:46:28 XML不算。`-final.log`联合30为29绿1红，exit1/1m12s；LibField9 XML05:21:31.442Z失败于TypeVar.toDynamic，Member3 05:21:46.932Z、Ctor7 05:21:28.434Z、Spec11 05:21:50.247Z全绿。`-control-final.log`正常worker必要7为Logic6绿（05:31:50.465Z）和新方法红（05:31:47.979Z，producer96 errors，静态T/N被误要求为数据字段），exit1/12s。`-payload-final.log`最终仅1方法XML2026-10-06T05:40:52.111Z为1/0/0/0，worker129正常finish，exit0/SUCCESS16s。29+6+1=36个不同用例跨轮各自通过，非一次联合36或最终7绿。完整日志名前缀`mcfpp-generic-type-declaration-scope`，目录F:/DevCache/.codex/runtime。MCFL17/bin289989未变，无stdlib/fullcheck/服务器，阶段70提交见Git历史。

## 阶段 71：声明/容器类型值身份

阶段71四个生产文件ConcreteExprVisitor、MCFPPType、MCFPPGenericDataTemplateType、FieldVisitor及一个fixture完成限定类型值路径。Concrete在visitExpression统一出口归一最终类型StaticMemberView，裸primary也覆盖；Meta带selector时转回StaticMemberView保留静态成员选择。resolveTypeId仅支持builtin/Wildcard、完整ID校验的Declaration及既有四种单参数Applied工厂；generic type只剥顶层Typed链后的TypeValue并注册已有types map。普通typed MCFPPDataTemplateType字段以unknown buildUnConcrete登记，其他类型/T!仍原build，真实receiver沿既有codec默认，shape校验保留。wire/layout/签名schema未改，MCFL17/bin289989未变，无stdlib/fullcheck/服务器。

新fixture的LeafAlias/Leaf/list<int>在source及fresh consumer字段、constructor、read和自由函数参数full ID/canonical断言及磁盘4/9/7/frame0全部到达；旧70模型与磁盘4/6/8/bool1/frame0亦复查通过。producer两次0 errors/0 warnings，consumer两次0 errors/已知9119 warnings。

日志前缀`mcfpp-generic-type-value-identity`，目录F:/DevCache/.codex/runtime。`-red.log` fresh XML `2026-10-06T05:56:12.123Z` 1fail，producer5 errors，worker130正常finish，BUILD FAILED in6s；裸Leaf/alias快捷primary未成为类型值。初次`-final.log`联合6项LibFieldAccess3 XML `06:02:49.065Z` 2绿1红、Cache3 `06:02:55.136Z` 3绿，总5/6，producer仍5 errors；worker131正常，BUILD FAILED in24s。归一移至Expression后`-expression-final.log`单1 XML `06:12:21.301Z` producer22 errors/0 warnings，worker132正常，BUILD FAILED in7s：晚声明Leaf默认{}先被FieldVisitor冻结，Leaf完成后clone结构校验失败，尚未进入consumer。失败轮helper仍尝试输出debug/index，不能写成未生成文件。

最终`-field-final.log` XML `2026-10-06T06:18:57.009Z` tests2、failures/errors/skips0，worker133正常finish，Gradle exit0/BUILD SUCCESSFUL in15s。最终是必要2项复查，非最终联合6；首轮5绿+新71方法1=6个不同用例跨轮各自通过，旧70在最终2中复查不额外计成第7个。阶段70提交`1d593d19c71f5c42a3adb4aaf0e2dbdaae5c1b16`；阶段71已提交`baa8f0704d58fcbc29706cb28181d821c9cc6138`。普通visitor命名类型值/list<type>、Sequence/Record递归snapshot、Concrete known index留阶段72；Union/Vector/Specialized/Selector/Opaque及全集、object/interface未验。

## 阶段 72：完整静态类型集合

六个生产文件Expr、Concrete、Type、GenericType、GenericDataTemplate、StorageAccess与一个fixture完成限定路径：普通expr统一出口类型值归一、初Meta带selector转静态view；registerSnapshotTypes遍历Typed/TypeValue/Sequence/Record；readonly CompilerOnly绑定复用internal seedParts初始化子facts/长度；Concrete完整已知下标复用Indexable getter，未知/不完整仍拒绝；中间PropertyVar在下一selector前get，最终赋值property不提前读取。

前置readBundle Bundle<[Leaf]>真实wire签名与named types/direct [Leaf]命中同canonical Bundle/Cell；source/fresh Bundle/Cell/Leaf不同对象、full TypeId稳定，仅反转实例顺序。最终producer0/0、consumer0/9119，磁盘4/9/frame0到达。MCFL17/bin289989/schema未变，无stdlib/fullcheck/服务器；TypeId resolver仍阶段71有限范围。

日志前缀mcfpp-generic-type-collection：red XML06:31:46.159Z，producer10/0，worker134正常FAILED7s；必要联合5的-final.log为4绿/1红（LibField2 XML06:40:28.704Z，CollectionStorage2 06:40:35.002Z，CompilerOnlyView1 06:40:35.187Z），worker135正常FAILED27s，新72模型已过但磁盘receiver失败。-runtime-diagnostic.log fresh XML2026-10-06T06:49:46.687Z，worker136正常FAILED10s，确认中间Property未unwrap；临时打印已删除。-receiver-final.log fresh XML2026-10-06T06:58:57.286Z，tests1/failures/errors/skips0，worker137正常exit0/SUCCESS14s。首轮4绿+最终新72方法1=5个不同用例跨轮各过，非最终联合5。完整证据见verification.md。阶段71提交baa8f0704d58fcbc29706cb28181d821c9cc6138（9文件185+/27-）；阶段72已提交ee46fba877b9af3570ddeb1b8f65f8742d22c11d，11文件197+/32-。

## 阶段 73：冻结Specialized类型值

阶段73两个生产文件MCFPPType、MCFPPGenericDataTemplateType（42+/22-）与新增89行fixture完成限定Specialized恢复。共享resolveSpecialization(id)复用现有类型查找、snapshot restore与prototype.compile，使用consumer当前target/options，核对template Declaration的namespace/kind/name及最终FullID；resolver委托该入口，tryResolve保留READ_LIB守卫和currentcanonical快速路径。

前置Holder<Cell<int>>自由函数签名进入真实wire。source/fresh Holder.T、字段/ctor/read/free签名指向canonical Cell，Cell.T及成员为Int；fresh prototypes/Compiled不同于source，FullID及T snapshots稳定。只反转实例构造4→9/9→4，不宣称生成index反转；source/fresh模型及consumer真实磁盘4/9/frame0通过。schema/MCFL17/bin289989未改，无stdlib/fullcheck/实际服务器。Union/Vector/Selector/Opaque及generic object/interface未扩展。

日志F:/DevCache/.codex/runtime/mcfpp-generic-specialized-type-value-red.log：fresh XML2026-10-06T07:14:31.258Z，1fail，source0/0、source模型及库生成/读取已过；consumer completeTemplateDeclarations两次Cannot restore frozen readonly argument T Holder，随后read/unknown未定义，共4 errors/9119 warnings，fresh模型与disk未到；worker138正常FAILED10s。最终-final.log XML2026-10-06T07:21:12.937Z，tests2/failures/errors/skips0（新73+旧72），worker139正常exit0/SUCCESS23s；两个producer0/0、两个consumer0/9119，模型及disk4/9/frame0各过。这是最终必要联合2全过，不是新全量或5项跨轮。阶段72提交ee46fba877b9af3570ddeb1b8f65f8742d22c11d，exact11文件197+/32-；阶段73当前7文件含4docs，提交以Git历史为准。

## 后续仍需完成

- 标量/擦除及可编码 list/dict/map/ImmutableList/NBT 数组、范围值和已证明整数端点的命名范围迭代已迁入 IR，26.3 原生浮点、short/double/nbt 载荷、标量/数组显式转换及 map 两种投影也已接入；旧浮点 IR 最终复查20项通过，旧return ABI保留。阶段58限定语法域实现已提交`e6f83fe`；阶段59导入声明环境已持久化namespace/unsolvedImports，MCFL13重建后50项联合通过；阶段60限定消费端body导出已实现并13项联合通过；阶段61受支持模板方法owner/scope已恢复；阶段62模块资源路径已修复并通过限定回归；阶段63函数accessModifier已持久化并通过MCFL14库往返14项联合验证；阶段64仅新增PropertyInfo.accessModifier持久化并通过MCFL15定向验证。阶段66已验证受限的当前实例未限定字段寻址；阶段67已接通Box<N as int> readonly签名、源码特化和namespace generic类别；阶段68已限定支持未注解普通generic class实例化后绑定T；阶段69已限定显式generic类型签名与跨库canonical特化。阶段70声明scope及绑定T/N限定路径已验证，36个不同用例跨轮各自通过（29+Logic6+最终1），MCFL17/bin289989未变；阶段71有限Declaration/Applied TypeValue恢复已验证，6个不同用例跨轮各自通过，最终必要2项复查；阶段72完整静态类型集合限定路径已验证，5个不同用例跨轮各自通过；下一步阶段73冻结Specialized类型值已限定验证，最终必要联合2项全过；下一步阶段74源码联合类型与冻结身份尚未实现或测试；generic object/interface、annotations、source abstract/final flags、函数自身未绑定dependent readonly formal/return、Union/Vector/Selector/Opaque及全集身份、完整Kryo身份仍未覆盖。imported object自动load、其他未迁入集合成员、编译器专用集合、未知端点范围形参/浮点范围/通用迭代器、模板/泛型/T!等尚未统一。旧转换和DataObject等来源仍走适配；无宏目标上未知长度的负数字面下标仍走旧边界。
- 参数相关 static 已知子位置、未知列表范围、普通集合返回/static 整体替换子形状与递归效果不动点已接入受限 IR 图；同一类型/形状输入的递归返回及写回已求解，输入变化仍保守。继续扩展其余集合、成员、全局、实体及全部调用位置。无法证明的函数仍采用未知屏障，原始命令跨函数修改物理记分板与帧恢复仍需核实。
- 未知字典字符串键的运行时路径后端、其余原生成员/集合的编码能力检查、实体路径、全部布局访问诊断、模板方法与构造仍需完成迁移；map 的字符串值键和可编码投影已接入，但编译器专用值及其余控制语句仍需扩展。本轮递归样例不代表完整帧分配覆盖全部类型。
- MNI 显式上下文、值/位置接口及其余成员签名统一未完成；Concrete 体系、hasStoredInStack、trackLost 等旧状态仍存在。
- 未配置实际 Minecraft 服务端；独立执行器通过不等于实际目标验证。阶段58有37个不同必要测试用例跨轮各自通过，不是联合37项。仍缺服务器验证及其余类型系统迁移工作。

下次优先执行 [下一阶段计划](./next-stage-plan.md) 中标出的剩余工作。整个类型系统重构尚未完成。

## 本轮自检

阶段72补充：5个不同用例跨轮各自通过（首轮4绿+新方法最终1），source/fresh canonical及真实磁盘4/9/frame0到达；最终单方法复查不写成联合5。阶段73最终必要联合2项全过，source/fresh Holder<Cell<int>>模型及磁盘4/9/frame0各到达。完整性保持3/5，Union源码/冻结身份及其余独立边界未完成。

平均3.8/5；阶段58限定语法域复用IR私有图Lowering/FlowAnalysis，阶段59 MCFL13和联合50项通过，阶段60有包含磁盘consumer执行的13项检查全绿；阶段61首轮26项24/2后定向复查2项通过，阶段62目录/JAR/ZIP联合11项全过；阶段63 MCFL14函数权限库往返14项联合全绿；阶段64 MCFL15字段权限/while帧后最终7项全过；阶段65权限11项、阶段66实例字段/构造器11项分别联合全绿；阶段67标准库0/0并MCFL16，41个不同用例跨轮各自通过；阶段68未注解generic类T绑定17个不同用例跨轮各自通过；阶段69标准库0/0、MCFL17且19个不同用例跨轮各自通过，含反序consumer磁盘/TypeId/owner；阶段70共36个不同用例跨轮各自通过（29+Logic6+最终1），提交记录见Git历史，MCFL17/bin289989未变；阶段71六个不同用例跨轮各自通过，最终必要2项复查达成source/fresh canonical及磁盘4/9/7/frame0。完整性保持3/5：函数自身未绑定dependent readonly formal/return、Union/Vector/Selector/Opaque及全集身份、generic object/interface/annotation/T!其他语义、imported object autoLoad、其余成员与IR迁移及实际服务器验证仍未完成。

| 维度 | 评分 | 证据与改进 |
| --- | --- | --- |
| 准确性 | 4/5 | 阶段58分轮37项、59联合50项、60联合13项（含consumer磁盘执行）、61跨轮26项、62联合11项、63联合14项、64跨轮30个不同用例、65联合11项、66联合11项、67跨轮41个、68跨轮17个、69跨轮19个、70跨轮36个、71跨轮6个、72跨轮5个不同用例及73最终必要联合2项分别记录；明确区分分轮各自通过与单次联合，包含红测/worker异常及fresh XML证据 |
| 完整性 | 3/5 | 函数accessModifier、限定字段/Property权限和当前实例未限定寻址已有受限验证；Union/Vector/Selector/Opaque及全集身份、函数自身未绑定dependent readonly formal/return、generic object/interface与annotations、T!扩展、imported object自动load、集合/MNI与服务器验证仍未完成 |
| 清晰度 | 4/5 | 区分标准库编译警告、语言诊断与测试夹具错误，历史保留 |
| 可操作性 | 4/5 | 阶段62目录/ZIP/JAR复制11项通过；阶段63三套件MCFL14权限往返14项通过；阶段64字段/逻辑7项、阶段65权限11项、阶段66实例字段与构造器11项全绿；阶段67 MCFL16及最终5项（含旧float ABI）全绿、41个不同用例跨轮各自通过；阶段68完成未注解Cell<T as type>限定consumer/磁盘验收；阶段69完成MCFL17显式generic类型签名与跨库往返验证；阶段70声明scope及绑定T/N限定路径完成模型和磁盘4/6/8/bool1/frame0验证，36个不同用例跨轮各自通过；阶段71source/fresh Leaf及Applied类型canonical、真实磁盘4/9/7/frame0达成，最终必要2项复查 |
| 简洁性 | 4/5 | 只更新当前阶段事实与交接，不重写历史记录 |

优先改进：阶段74源码联合类型与冻结身份，接通现有MCFPPUnionType factory及有限Union resolver，真实Box静态T/透明alias/前置wire签名及fresh磁盘4/9验证；尚未实现或测试。不扩展Union runtime布局。完整性仍3/5：函数自身未绑定dependent readonly formal/return、Union/Vector/Selector/Opaque及全集身份、generic object/interface/annotations/T!、导入object自动加载、其余控制流/集合/MNI和服务器验证仍有缺口。
自检：用户能复核实现和测试，也会看到整项重构仍未结束；没有把阶段通过写成项目全部完成。

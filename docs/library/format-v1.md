# MCFPP 发布库格式 v1

本规范落实已批准的预编译库提案 revision 3（`docs/library-package-plan.md` §1–2）。它定义第一版正式发布格式；当前 VERSION=87 的 Kryo 开发缓存不属于兼容承诺。本文是待实现的契约，不代表现有 reader/writer 已实现这些行为。编译器接入见 [compiler-bridge.md](compiler-bridge.md)。

## 1. 兼容边界

发布载荷类型为 `compiler.mcfpp.library`。一个载荷有一个库身份，可以贡献多个语言命名空间。库必须包含普通函数、inline/const 函数、泛型函数、构造器、访问器、默认值、字段初始化器和 lambda 后续分析所需的结构。预生成命令不能替代这些正文。

`formatVersion` 是无符号单调递增整数，首版为 1；它和编译器发行号、语言语义版本、运行时 ABI、Mcfpm adapter ABI 各自独立。writer 默认写当前正式版本。reader 必须先检查版本，再读取任何正文：遇到大于自身上限的版本报 `LIB_FORMAT_NEWER`，要求升级编译器；不得尝试“尽力”读取。未来新版保留历史发布解码器，并把旧模型转换为当前会话模型，保留旧操作的含义。优化器内部修复无需改格式；持久化字段、标识或操作含义发生不兼容变化时升级格式。没有“导出任意旧版本”的接口。

`languageSemantics = "mcfpp.source/1"` 固定本版节点、求值顺序及现有语言行为；`runtimeAbi = "mcfpp.runtime/1"` 固定生成器可复用的运行时约定。具体编译器版本仅是可选调试信息。不得通过改写同一 ABI 字符串的含义来规避版本升级。新编译器可以为旧语义保留兼容适配器；不能适配时必须报明确兼容错误，不能把旧库按新含义编译。

## 2. 归档与资源路径

Mcfpm 外层沿用其可复现 ZIP 载荷。库归档根目录固定为：

```text
bin.mclib
module.json
datapack/<module-id>/data/<resource-relative-path>
```

`module.json` 沿用现有 `Module.fromJson` 的模块、base、packages、depends、conflict、provides 描述；其中模块资源统一从 `datapack/<module-id>/data/` 读取。DIR、ZIP、JAR、INNER 的入口都先把路径规范化到这个逻辑根，禁止让 `Module.extract` 根据来源切换不同资源布局。配对资源包、独立数据包和 JVM plugin 继续使用 Mcfpm 自己的载荷类型，不能偷塞到库正文里隐式加载。

所有 entry 使用 `/`，是相对路径，不含空段、`.`、`..`、盘符、绝对路径或反斜杠；归档不允许重复 entry。`module-id` 是一个非空路径段。资源清单列出的路径必须存在，字节长度和 SHA-256 必须吻合。额外未登记资源报错，避免“包里有但所有权不明”的输出。调试源码若另外分发，不是必需载荷，也不能参与正文恢复。

## 3. 字节编码

所有多字节整数均为大端序。`u8/u16/u32/u64` 是定宽无符号整数，`i64` 是二进制补码。长度、计数、引用使用 `u32`；字节偏移和容器长度使用 `u64`。实现必须在转成 JVM `Int`、分配或加法之前检查溢出和边界，不得以读到 EOF 作为正常记录结束。

下文的类型记法也是字段的线序：

| 记法 | 编码 |
|---|---|
| `bool` | `u8`，只允许 0 或 1 |
| `str` | `u32 utf8ByteLength` 后跟严格 UTF-8；不带 BOM，不做 Unicode 归一化 |
| `bytes` | `u32 byteLength` 后跟原始字节 |
| `X?` | `bool present`，为真时跟一个 X |
| `X[]` | `u32 count`，后跟 count 个 X，顺序有含义 |
| `record` | `u32 payloadLength`，后跟固定字段线序的 payload；必须恰好耗尽 |
| `tagged` | `u16 tag` 后跟 `record`；下面每个 tag 表有独立的编号域 |
| `S/T/C/D/E/B/N/Q/A/H/R` | 字符串/类型/常量/声明/词法环境/正文/树节点/词法作用域/注解/注解状态单元/运行时记录表的 `u32` 引用 |

表引用从 1 开始；0 非法，缺省值用 `?`，不混用 0 与 null。每个表为 `u32 count` 后跟 count 个 `record`，记录序号就是引用。跨记录可前向引用；完整校验在所有表解码以后执行。表内不能保留 JVM 对象地址、Opaque 计数器、enum ordinal、ANTLR rule/token 编号或任意类名。JVM provider 描述中的类名是显式外部链接契约，和序列化实现类名是两件事。

文件头按以下顺序编码：

```text
magic[8] = 4d 43 46 50 4c 49 42 00   # MCFPLIB\0
formatVersion:u32 = 1
fileByteLength:u64
sectionCount:u32
section[sectionCount]
```

每段线序为 `id:u16, flags:u16, payloadLength:u64, payload[payloadLength]`。flags bit 0 表示必需，其余位本版为 0。段按 id 升序，仅能出现一次。文件必须恰好 `fileByteLength` 字节，无尾随数据。旧 `MCFL` magic 返回 `LIB_DEVELOPMENT_CACHE`，提示重新导出；不猜测其 VERSION。

| 段 ID | 名称 | 必需 | 内容 |
|---|---|---|---|
| `0x0001` | Manifest | 是 | 下面定义的单个 record |
| `0x0002` | Strings | 是 | `str` 表 |
| `0x0003` | Environments | 是 | E 表 |
| `0x0004` | Types | 是 | T 表 |
| `0x0005` | Constants | 是 | C 表 |
| `0x0006` | Declarations | 是 | D 表 |
| `0x0007` | Scopes | 是 | Q 表 |
| `0x0008` | Nodes | 是 | N 表 |
| `0x0009` | Bodies | 是 | B 表 |
| `0x000a` | Annotations | 是 | A 表，随后 H 表 |
| `0x000b` | Runtime | 是 | R 表 |
| `0x000c` | Resources | 是 | 资源表 |
| `0x8001` | Debug | 否 | 本节末的调试映射 |

不认识的必需段、核心记录 tag、字段或枚举值一律拒绝；未知非必需段可按长度跳过。v1 不允许给既有核心 record 随意附加字段；新增核心能力用新格式版本。长度边界用于诊断、校验和将来的扩展，不意味着旧 reader 可以忽略语义。

Manifest 字段：`library:PackageKey, classifier:str, semantics:str, runtimeAbi:str, target:Target, dependencies:Dependency[], entryEnvironments:E[], rootDeclarations:D[]`。PackageKey 为 `coordinate:str, version:str`；coordinate 是 Mcfpm 已解析的完整坐标规范串，version 是已锁定的版本。Target 为 `minecraftVersion:str, edition:u8=1(Java), requiredCapabilities:str[]`。Dependency 为 `package:PackageKey, classifier:str`；只列当前库直接引用的库，不写锁图全部对象。依赖版本和载荷校验由 Mcfpm 提供并与 manifest 核对。归档 digest 属于外层锁图，不写回库自身造成自引用。

## 4. 声明、绑定与身份

跨包身份为 `PackageKey + classifier + DeclarationKey`。DeclarationKey 不等于 Minecraft 函数路径：它是 `namespace:str, ownerPath:PathPart[], kind:u16, name:str, signature:Signature?`。PathPart 为 `kind:u16, name:str, signature:Signature?`；公开可寻址的 owner 不允许匿名段。kind 使用下面 D payload 的 tag。Signature 为 `readonly:KeyType[], normal:KeyType[], receiver:KeyType?`，是声明类型结构；参数名与返回类型不区分当前语言的函数重载。若现有声明注册器认定两个 key 冲突，必须诊断，不能以返回类型或导出顺序发明新重载规则。

KeyType 是不引用表编号的 tagged 内联结构：1 Builtin(`key:str`)，2 Nominal(`package:PackageKey,classifier:str,namespace:str,path:str[]`)，3 Parameter(`ownerDepth:u32,role:u8,index:u32`)，4 Applied(`base:KeyType,args:KeyType[]`)，5 Specialized(`base:KeyType,args:bytes[]`)，6 Union(`types:KeyType[]`)，7 Function(`execution:u8,parameters:KeyType[],result:KeyType`)，8 Dependent(`canonicalTree:bytes`)，9 Anonymous(`package:PackageKey,classifier:str,origin:AnonymousOrigin`)。ownerDepth=0 是当前签名所属声明，1 是直接外层声明；role 同 T3。Specialized 的每个 bytes 是 C 的标签/字段内联展开（S→str、T→KeyType、C→递归内联值），不能含表引用。Dependent 使用 §6 的类型节点和其可达实参树按固定字段线序内联展开：S→str、节点引用→子节点、Local 参数引用→Parameter、已绑定类型/声明名→Builtin/Nominal/Anonymous；不编码 scope、body id、诊断位置、参数显示名。其他局部值不能出现在公开签名中。Nominal 指向 data/object/enum/alias 等非重载名义类型；AnonymousOrigin 为 `logicalUnit:str, declarationOrdinal:u32, childPath:u32[]`，是捕获时模块内逻辑单元名（通常项目相对源路径）及语义声明/子字段位置，不含字符偏移或会话计数器。无源码的生成声明使用 provider 固定逻辑单元和所生成声明的有序位置。同一 origin 登记为同一个匿名 D，允许当前语言的匿名类型出现在公开签名中，无需增加 alias 语法限制。这样泛型函数的身份不递归包含“它自己的完整 DeclarationKey”，也不依赖 T/D 分配顺序。Union 按展开字节排序去重，其余有序列表保留顺序。

声明引用 `DeclRef` 是 tagged：`1 Local(D)`；`2 Dependency(dependencyIndex:u32, key:DeclarationKey)`。dependencyIndex 从 1 开始，指 Manifest.dependencies。公开引用必须核对可见性；库内部使用 private 实现允许 Local 引用。消费方不能因读到了私有正文而绕过访问控制。依赖私有实现通过依赖的公开声明间接到达，不复制其他库的私有对象。

D 表每项为 `common:DeclCommon, payload:tagged`。DeclCommon 为 `key:DeclarationKey?, origin:AnonymousOrigin?, name:S, owner:D?, environment:E, access:u8, flags:u32, annotations:A[]`。origin 对匿名类型必需，其他声明可省略。`access=1 public, 2 protected, 3 private`；flags bit 0 final，1 static，2 abstract，3 override，4 const，5 nullable，6 forceRuntime，7 dataOnly，其余为 0。不适用于该类声明的 flag 必须为 0。有公开 key 的声明用 key 排序；私有、匿名和局部声明用 owner 的稳定顺序及在语义树中的首次结构路径排序分配 D。D 始终是包内身份：导入后分配新的会话身份，绝不 `TypeId.reserve` 恢复旧编号。

| tag | 声明 | payload 字段（按线序） |
|---|---|---|
| 1 | namespace | `members:D[]` |
| 2 | function | `execution:u8, receiver:T?, readonly:D[], normal:D[], result:T?, body:B?, native:NativeBinding?, tags:TagKey[], linkedRuntime:R[]` |
| 3 | data / object | `category:u8, readonly:D[], parents:Parent[], typeAs:T?, members:D[], memberOrder:D[], companion:D?, inherited:InheritedEffect[]` |
| 4 | constructor | `normal:D[], body:B?, intrinsic:u8` |
| 5 | field / global / local / parameter | `role:u8, storage:u8, type:T?, declaredType:N?, defaultBody:B?, frozen:C?, variance:u8, parameterIndex:u32?` |
| 6 | property | `backing:DeclRef?, getter:Accessor, setter:Accessor` |
| 7 | operator | `operator:S, operand:T?, implementation:DeclRef` |
| 8 | enum | `members:D[]` |
| 9 | enum member | `ordinal:i64, data:C` |
| 10 | type alias | `target:T, declaredTarget:N?` |
| 11 | macro | `binding:MacroBinding` |
| 12 | reference helper | `recipe:ReferenceRecipe` |
| 13 | annotation provider | `provider:AnnotationProviderBinding` |

`execution=1 ordinary, 2 inline, 3 compileTime, 4 native`；category=1 data，2 object，3 typeAs，4 anonymous，5 intersection。field role=1 member，2 global，3 local，4 readonly parameter，5 normal parameter，6 lambda parameter；storage=1 var，2 const，3 external，4 import-var，5 parameter（storage=2 必须与 const flag 一致）；variance=1 invariant，2 in，3 out。constructor intrinsic=0 user body，1 typeAs intrinsic。非抽象的普通、inline、compileTime 及用户构造器必须有正文；native 必须有 NativeBinding；abstract 可无正文且不得当作可执行实现调用。lambda 参数类型与 synthetic function 的 result 可等待目标推断；省略源码函数返回类型应明确保存 void，其他声明的必要 result 不可为空。lambda 参数 D 的 role 6 type 可以为空；声明头的其他必要类型不可丢弃。

D2 的 const flag 按 execution 解释：compileTime 必须设置，ordinary/inline 必须清零；native 时保存作者已准备的 `NativeFunction.returnsConstWhenArgsConst` 布尔事实，可设置或清零。导入恢复该事实，不从读时 JVM annotation 猜测或回退为默认 false；不改变 native 的 readonly/normal arity。

Parent 为 `type:T?, expression:N?`，至少一个存在；含 readonly 参数的父类实参保留 TypeApply/表达式树，不保存 `parentExpressions:List<String>` 再解析。闭合类型可同时保留已绑定 type，二者必须一致。匿名父定义作为本包 D 保存，由 T 引用；不把其他包的 parent layout 递归拷入。

Accessor 为 tagged：`0 Absent()`，`1 Simple()`，`2 Function(target:DeclRef)`，`3 Native(target:DeclRef)`。表达式 getter/setter 转换为有独立 D、B 的访问器，保留接收者、隐式 setter 参数和声明环境；访问器不因名字/签名相同而和普通方法合并。InheritedEffect 为 `origin:T, slot:DeclRef, field:D, property:D?, getterOwner:T?, setterOwner:T?`；表示已完成的声明注解效果，在正常继承展平后核对真实来源 slot，不能让它重放初始化器或制造重复字段。

TagKey 为 `namespace:S, name:S`。标签贡献记录在 Runtime 段，和标签的名字分开；不能导出全局累积标签成员。

E 表记录 `namespace:S, imports:Import[], owner:D?`。Import 为 `targetNamespace:S, member:S?, alias:S?, from:S?, resolvedPackages:PackageKey[]`。保留原 import 的顺序和 wildcard/alias/from 区分（member=null 表示 `*`），同时记录实际绑定库。导入完全部声明后再恢复环境；不能以消费方 imports 或只按同名 namespace 的最后一次注册代替作者环境。

Q 表记录 `owner:D, parent:Q?, bindings:D[]`；bindings 按声明可见顺序，初始化表达式在本地变量进入可见范围前绑定。lambda 捕获以所捕获 D/外层参数引用表示，执行时重新建立闭包，不能持久化某次调用的 Var、借用 token 或执行上下文句柄。

## 5. 类型与常量表

T 表为 tagged；所有结构类型按结构规范化，不从 `toString()` 或 JVM 类型推导身份：

| tag | 类型 | 字段 |
|---|---|---|
| 1 | builtin | `key:S` |
| 2 | declaration | `target:DeclRef` |
| 3 | parameter | `owner:DeclRef, role:u8, index:u32`（index 从 0 开始） |
| 4 | applied | `constructor:T, arguments:T[]` |
| 5 | specialized | `constructor:T, arguments:C[]` |
| 6 | union | `alternatives:T[]`（按结构 key 排序、去重，非空） |
| 7 | intersection | `components:T[]`（保留语言继承顺序）, `declaration:D?` |
| 8 | function | `execution:u8, parameters:T[], result:T` |
| 9 | NBTRef | `value:T` |
| 10 | dependent | `syntax:N, environment:E, owner:D` |
| 11 | unresolved nominal | `kind:S, namespace:S, name:S`（仅 pending 父身份数据） |

T3 role=1 readonly 参数、2 lambda 类型参数预留（v1 writer 不产生）、3 native 类型槽；不能以名字相同合并两个 owner 的参数。函数类型 execution=1 normal，2 paused，3 generator，分别映射现有 `ExecutionCategory` 的稳定 identityName。dependent 只用于尚需 readonly 绑定的语义类型树，不能退回 `UnresolvedType.originalTypeString`。实体限制（数量、实体名）按当前 `TypeSyntax` 的数据保存；不恢复已被现有代码拒绝的历史 `TypeId.Selector` 子类型。

T11 精确保留显式 pending parent API 已有的 `TypeId.Declaration(kind,namespace,name)` 三个身份字段；kind/name 非空，namespace 原样保留（可为空），不补当前 namespace 或 imports。kind 是原身份字符串，不是 D kind 的数字编码。它没有 model、package、D 或可执行名称绑定，不能伪造这些信息；常规类型缺失 ownership 仍须失败，不能退化为 T11。

T11 及包含它的 T/C 结构只能从 `ParentRecord.type` 的显式 pending 身份根到达，该 ParentRecord 的 expression 必须为空。允许 Specialized 的构造器和 readonly 常量身份数据递归包含它；typed 包装、TypeValue、sequence/record 的层级与顺序和全部 IEEE raw bits 原样保存。T/C 规范 key 使用独立 tag 11 和原三字段，不调用模型解析或依据展示字符串恢复。模型校验拒绝游离的符号身份，以及从字段/形参/函数签名、N 类型或字面量、注解、MNI、helper、运行时等其他入口消费含 T11 的 T/C。即使同一引用也是合法 pending 根的子项，仍不能在这些位置使用。

恢复 pending 身份须沿 T/C 数据图得到原 TypeId/CompilerValue，不调用 `type(Specialized)`、不材料化模板，也不把 T11 转为 Lookup。读入后仍是 pending；只有正常的显式父绑定阶段才能把它关联到真实声明。它不能用来绕开声明访问控制或从消费方同名 imports 猜归属。

builtin key 使用语言协议名字：`object,int,entity,bool,byte,short,long,float,double,selector,string,text,nbt,Type,any,void,ByteArray,IntArray,LongArray,list,ImmutableList,map,dict,ScoreRef,NBTRef,null,pos2,pos3,coordinate.dimension,java,range,static-member-view,wildcard`，向量为 `vec` 后接正十进制维数；原生构造器为 `constructor:` 后接其已注册的语言 key。映射必须显式：`MCFPPBaseType.Any/Int/String/Float/Bool/JsonText/Range/Pos2/Pos3` 分别映射 `any/int/string/float/bool/text/range/pos2/pos3`；`MCFPPNBTType.NBT/Byte/Short/Long/Double/ByteArray/IntArray/LongArray` 映射同名语言 key（NBT→nbt）；`MCFPPConcreteType.Type/JavaVar` 映射 `Type/java`；`MCFPPPrivateType.StaticMemberViewType/MCFPPCoordinateDimension/Void/Null/Wildcard` 映射 `static-member-view/coordinate.dimension/void/null/wildcard`。这些是 encoder/decoder 的显式映射，不把旧内部 key 原样发到协议。现有 vector 的 `Applied(vector,dimension)` 规范化为 `vecN`，现有 function 的 func/execution/params 包装规范化为 T8。`static-member-view/wildcard` 只可用作内部类型约束，不能因此允许当前语言禁止的普通值使用。其他现有编译器内建 key 必须在正式 v1 冻结前登记并提供映射；不得把无法识别的内部类退化为 any。遇到新 key 应报 `LIB_TYPE_UNSUPPORTED`。语言源码中的类型拼写见 N 类型节点，不要求展示名和 key 相同。

role=3 的 owner 只能是带 NativeBinding 的 native D2；依赖 owner 的声明 key 也必须为 D2，并在完整目录绑定时核对其 native 实体。index 按该受管精确 JVM 方法的 `@MNIFunction.genericType` 数组显式顺序从 0 开始，不按名字排序，不假定语言参数 `p0` 对应某个 genericType 名称。这个类型槽与语言 readonly/normal 形参独立，不增加 D5，也不改变调用 arity；KeyType.Parameter 使用同一 role，当前 native 签名内 ownerDepth=0。若源类型视图实际绑定到语言 readonly Type 参数，仍用 role=1；其余 native 槽才用 role=3。目录绑定分配每个 owner/index 的 fresh 类型视图，并用实际受管方法的 genericType 元数据核对索引；解码阶段不为此加载或执行 host。

C 表为 tagged，禁止含可变 Var/Tag/StorageLocation：

| tag | 常量 | 字段 |
|---|---|---|
| 1 | null | 无 |
| 2 | integral | `value:i64` |
| 3 | float | `bits:u32`（IEEE-754 raw bits） |
| 4 | double | `bits:u64` |
| 5 | bool | `value:bool` |
| 6 | text | `value:S` |
| 7 | type value | `value:T` |
| 8 | NBT | `snbt:S` |
| 9 | sequence | `elements:C[]` |
| 10 | record | `fields:(name:S,value:C)[]`（按 UTF-8 名字升序，唯一） |
| 11 | typed | `type:T, value:C` |
| 12 | function value | `target:DeclRef, captures:(declaration:DeclRef,value:C)[], type:T` |

Integral 的 byte/short/int/long 区别由 typed 包装表示；不把浮点值转十进制再反算。NBT 是规范 SNBT（compound key 排序，数值后缀明确，列表顺序保留）；只允许用 NBT 解码器读取，它不是 MCFPP 源码。C12 只用于可完整冻结、无调用期借用的函数值；通常 lambda 保留正文和捕获描述，调用时创建值。常量图不得循环；声明/类型之间允许合法递归，由引用表示。

## 6. 持久化语义树

这是一棵保存语言操作的树，允许引用共享子树，不是 TypedIR 的序列化版，也不是 ANTLR 的孩子/终结符转储。每个 N 记录线序为 `tag:u16, scope:Q, payload:record`。每个节点由所属 body 和子字段路径确定稳定身份；N 是本文件紧凑编号，不作为跨包 ABI。节点引用图必须无环。每个正文 B 为 `owner:D, environment:E, role:u8, root:N, captures:DeclRef[]`；role=1 函数，2 构造器，3 默认值，4 初始化器，5 lambda，6 顶层初始化。默认值/初始化器是独立正文，不和某次实例化值混淆。

下列 `e/s/t` 都以 N 编码，分别要求表达式、语句、类型节点；`decl` 是 D。NameUse 为 tagged：`1 Local(target:D)`，`2 Declaration(target:DeclRef)`，`3 Lookup(name:S, namespace:S?, environment:E, candidates:DeclRef[])`。Lookup 仅保留需要 overload/readonly 实参才能决定的查找，candidates 是声明环境中的候选集；未限定的局部变量及已知类型名不能丢成裸字符串。动态 receiver 的成员名保留在 Member 节点，延迟正常成员解析。

### 表达式节点

| tag | 名称 | payload |
|---|---|---|
| `0x0101` | Literal | `constant:C` |
| `0x0102` | Name | `use:NameUse` |
| `0x0103` | This | `owner:DeclRef` |
| `0x0104` | Super | `owner:DeclRef` |
| `0x0105` | TypeValue | `type:t` |
| `0x0106` | Group | `value:e?`（保留空括号） |
| `0x0107` | Unary | `operator:S, operand:e` |
| `0x0108` | Binary | `operator:S, left:e, right:e` |
| `0x0109` | Cast | `value:e, target:t` |
| `0x010a` | Member | `receiver:e, name:S, keyword:u8, candidates:DeclRef[]?` |
| `0x010b` | Index | `receiver:e, index:e?` |
| `0x010c` | PropertyFilter | `receiver:e, entries:(name:S,value:e)[]` |
| `0x010d` | Call | `callee:e, readonly:e[], normal:Argument[], trailingLambda:e?` |
| `0x010e` | Lambda | `declaration:D, parameters:D[], explicitParameters:bool, body:B, tail:e?` |
| `0x010f` | Range | `lower:e?, upper:e?`（至少一个非空） |
| `0x0110` | Coordinate | `dimensions:Dimension[]`（长度 2 或 3） |
| `0x0111` | Selector | `kind:u8` |
| `0x0112` | List | `elements:e[]` |
| `0x0113` | Compound | `entries:(key:S,value:e)[]`（保留源码条目顺序） |
| `0x0114` | NbtArray | `element:u8, values:C[]` |
| `0x0115` | InterpolatedString | `parts:TextPart[]` |

Argument 为 `kind:u8=1(value), expression:e`；v1 不允许 legacy `ref expression`。ScoreRef/NBTRef 是普通值类型，通过正常 Call 构造，不能恢复旧的 ref 参数标志。保留 Argument 包装给当前混合 lambda/value 调用绑定器，不按参数类型提前重排。

Member.keyword=0 普通标识符，1 `map`，2 `set`；Selector.kind=1 a，2 r，3 p，4 s，5 e。Dimension 为 `mode:u8, value:C?`，mode=1 absolute，2 relative `~`，3 local `^`，null 只允许相对/局部的省略零。NbtArray.element=1 byte，2 int，3 long。TextPart 为 tagged：1 `Text(value:S)`，2 `Interpolation(expression:e)`，用于多行字符串和原生命令；引号与转义在捕获时解码，文本片段实际内容保留。

Member.candidates=null 保留既有的实际 receiver 动态成员解析；非 null 时是作者环境冻结的可调用 D2 候选集，保留其顺序，空集合表示无作者候选，不能回退到消费方后来加载的 overload/extension。该集合冻结作者可见的调用声明，不冻结名义成员的继承、泛型材料化或正常动态派发：调用仍需携带求值一次得到的实际 receiver，将 prototype D 映射到该 receiver 对应的真实成员后按既有语义选择实现。extension 由 D2.receiver 描述并保留其实际 caller，不从消费方 receiver.scope 追加新的 extension 候选。不能把候选编号塞进 name、借助解析别名重查，或将带 receiver 的 Member 改写成丢失 receiver 的全局 Name 调用。

Unary operator 只允许 `!,-`。Binary operator 为 `*,/,%,+,-,<,>,<=,>=,==,!=,~=,&&,||,|` 或语言标识符运算符；显式保存左右结合结构，不能依靠重新解析优先级恢复。`&&/||` 保留短路语义，`|` 和命名运算符沿用现有运算符解析。Call 的 receiver/callee、readonly 实参、普通实参及尾 lambda 保留当前 binder 的语法分类与顺序；不能先把它们冻结成 optimizer 的已知值而丢求值步骤。

### 类型语法节点

类型表保存身份；类型节点保存参数化解析仍需要的操作，字段中 `resolved:T?` 是可校验的绑定提示。

| tag | 名称 | payload |
|---|---|---|
| `0x0201` | TypeName | `use:NameUse, resolved:T?` |
| `0x0202` | TypeApply | `base:t, readonly:e[], resolved:T?` |
| `0x0203` | ContainerType | `kind:u8, argument:t`（1 list，2 ImmutableList，3 map，4 dict） |
| `0x0204` | VectorType | `dimensions:u32` |
| `0x0205` | EntityType | `limit:C?, names:S[]` |
| `0x0206` | UnionType | `alternatives:t[]` |
| `0x0207` | IntersectionType | `components:t[]` |
| `0x0208` | AnonymousDataType | `declaration:D` |
| `0x0209` | FunctionType | `parameters:t[], result:t, execution:u8` |
| `0x020a` | VoidType | 无 |
| `0x020b` | InferredType | `expression:e` |

EntityType 的 limit/names 对应当前 grammar 三种 entity 泛型形状，普通 entity 没有这些参数。可空/union 模板字段的 `?` 是字段声明约束，用 D.flags nullable 保存，不能改变普通函数类型的语义。

`InferredType` 是编译器内部的类型推导操作，引用已经保留的 initializer 表达式树；不是新增的源类型语法。T10 的 syntax 可指向该类型节点，expression 必须是表达式 N，允许与 initializer 正文共享同一 N。消费方在实际 readonly 参数和作者 E/D 绑定完成后，沿现有纯类型推导路径取得其类型；解码、投影和类型推导不得执行 initializer、native/macro 或注解构造动作。公开调用的结果类型应从绑定后的真实声明签名取得；不能复制依赖的 private 结果表达式/布局、为 private foreign D 伪造 public key，或提前制造虚假的闭合 Specialized 常量。不能以保存表达式源码后重新解析代替此操作。

### 语句和模板成员节点

| tag | 名称 | payload |
|---|---|---|
| `0x0301` | Block | `statements:s[]` |
| `0x0302` | Local | `declaration:D, initializer:e?` |
| `0x0303` | Expression | `value:e` |
| `0x0304` | Assign | `target:e, operator:S, value:e` |
| `0x0305` | If | `branches:(condition:e,body:s)[], elseBody:s?` |
| `0x0306` | While | `condition:e, body:s` |
| `0x0307` | DoWhile | `body:s, condition:e` |
| `0x0308` | Foreach | `variable:D, iterable:e, body:s` |
| `0x0309` | TryStore | `body:s, destination:NameUse` |
| `0x030a` | Execute | `contexts:(path:ExecutePath,value:e)[], body:s` |
| `0x030b` | Command | `parts:TextPart[]` |
| `0x030c` | Break | 无 |
| `0x030d` | Continue | 无 |
| `0x030e` | Return | `value:e?` |
| `0x030f` | Empty | 无 |
| `0x0401` | TemplateBody | `members:N[]`（以下成员节点） |
| `0x0402` | MemberDeclaration | `declaration:D` |
| `0x0403` | AnnotationApplication | `annotation:A, targets:D[]` |
| `0x0404` | AnnotationConstruction | `provider:DeclRef, arguments:e[], targets:D[]` |

Assign.operator 只允许 `=,+=,-=,*=,/=,%=`，target 必须通过现有 lvalue 检查。ExecutePath 为 tagged：1 `As()`，2 `Members(parts:e[])`；保留 executeContext 的左侧路径，不能当普通变量赋值。`TemplateBody` 是泛型/匿名模板现场实例化的有序成员流；字段声明的默认值、访问器、函数/构造器/运算符正文在 D/B 中引用同一份信息，不再保存第二份 ParserRuleContext。

`AnnotationConstruction` 是尚未构造实例的源声明 recipe，和已构造的 `AnnotationApplication` 分开。provider 必须绑定 D13（跨库时为该声明的 DeclRef）；targets 必须非空且唯一，表示同一有序成员流中被修饰的声明。arguments 按源码实参顺序保留表达式子树，名称继续通过 Q/D/E 绑定，可依赖模板 readonly 参数。例如当前语法 `data Box<T,N:int>{@Provider([N+1]) var value:T;}` 中实参是含 readonly 名称引用的 ListValue/Binary 树，不能提前塞入 A.arguments 的闭合 C，也不能保存注解源码再解析。

节点解码、目录准备和树投影只恢复 recipe，不执行 provider 构造器。正常模板实例化先绑定实际 readonly 参数，再按原作者环境闭合 arguments，沿现有源声明语义为该成员构造一次注解实例，并由正常完成阶段执行动作；重复查找、投影或完成检查不能再次构造或重放。缺 provider、错误的 provider 声明种类、无法闭合的实参或不支持的构造器须明确失败。已经构造的 A/H 保留原身份、共享状态和 phase，不得退回 construction recipe 后再构造。

lambda 的显式参数列表与无箭头的隐式 `it` 形式由 explicitParameters 区分；当前 grammar 不接受显式空箭头列表，writer 不产生该形状。tail 只表示未被分号结束的末尾表达式，不能从最后一个 Expression 语句猜测。B.root 包含 lambda 语句列表，tail 在所属 Lambda 节点中，二者不得重复执行。Lambda.declaration 是一个私有 synthetic function D，其 body 必须引用同一个 B。静态函数转 callback 直接引用该函数的 B，不保存一份源码重新分析。

## 7. 注解、MNI 和引用 helper

A 表每项为 `provider:DeclRef, arguments:C[], parents:A[], phase:u8, state:PortableState?`。phase=1 pending，2 completed。从每个 pending A 沿 parents 可达的图必须无环（即使环内节点自身为 completed）；只由 completed 节点组成且不被 pending parents 路径触及的父环作为已完成状态保留，重复引用保留共享关系。PortableState 为 `schema:S, version:u32, fields:(name:S,value:PortableValue)[]`；PortableValue 是 tagged：1 Constant(C)，2 Declaration(DeclRef)，3 Type(T)，4 Annotation(A)，5 Cell(H)。字段名唯一且排序。H 表为显式状态单元：tag 1 Sequence(`items:PortableValue[]`)，2 Map(`entries:(key:C,value:PortableValue)[]`)，3 Set(`items:C[]`)；Map key/Set item 只允许 primitive/text/null 的 C，按其内联规范字节排序唯一。H 可在不同 A/state 字段间共享，保留 `shared`、`nested[0]`、父注解字段等别名；合法有限容器循环通过 H 表达，先分配单元 shell 再填充。该 arena 只表达 codec 明确声明的语言状态，不能记录 JVM 字段名反射、容器实现类或任意 host object。

provider 必须有显式版本化 codec；内置注解注册固定语言 key 的 codec，JVM 扩展 codec 由受信任 plugin 提供。codec 读取声明过的 portable state，不允许反射序列化任意字段。完成的注解保留事实与效果，不能导入时再执行一次；pending 注解在正常阶段执行。无 codec、已构造 A 缺闭合参数或无法表示的状态必须导出失败并指出 provider/声明，不可静默删除注解。已完成的字段效果由 InheritedEffect/声明表表达；不能把全部状态约化为构造参数而漏掉现有 mutable annotation 的事实。

AnnotationProviderBinding 为 `providerPackage:PackageKey?, ownerClass:S, constructorParameters:S[], schema:S, schemaVersion:u32`。ownerClass 与构造参数是 provider 的显式 JVM 标识；实际恢复通过注册 codec 的 allocateShell/restore，不在导入时重放正常注解动作。共同 arguments/parents 和 provider state 分别存储；不能以 NativeBinding 的 static method 描述符冒充注解构造器。

NativeBinding 为 `providerPackage:PackageKey?, ownerClass:S, method:S, jvmParameters:S[], jvmResult:S, invocation:u8, caller:T?`；invocation=1 static。类名/参数类名沿用 JVM 规范全名，primitive 为 `boolean,byte,short,int,long,float,double,char,void`；数组采用 JVM `Class.getName()` 形式。加载后必须核对完整 descriptor、来源 plugin 和实际 MCFPP API 类型身份，不能只按方法名匹配。

MacroBinding 为 `native:NativeBinding, sourceName:S, parameters:MacroParameter[], result:MacroType`。MacroParameter 为 `name:S, mode:u8, type:MacroType`；mode=1 VALUE，2 REF，3 CONST，4 INLINE，显式映射当前 `ParamMode`，不使用 ordinal。Callback 是 MacroType 的结构，不是第五种参数 mode；具体接受组合继续由现有 MNI 签名验证器判断。MacroType 为 tagged：1 Source(type:T)，2 Callback(parameters:MacroParameter[], result:MacroType)。不要持久化 Method、SourceTypeCatalog、FunctionKey、TypeKey 或 expansion handle；导入在完整类型目录准备好以后重新产生这些会话对象。原始 MNI 描述字符串可放 Debug，但不能作为库类型解析的唯一来源。

ReferenceRecipe 为 `namespace:S, name:S, kind:u8, target:ReferenceTarget?, singleRead:bool, operandTypes:T[], path:ReferencePath[]`。kind=1 score_holder，2 score_entity，3 nbt_storage，4 nbt_entity，5 nbt_block，分别对应现有五个 ReferenceKind。target 为 tagged：1 PlayerName()，2 UUIDText()，3 Selector(kind:u8, filters:Filter[])；Filter 为 `name:S, reverse:bool, type:T`，selector kind 同表达式 Selector。path 为 tagged：1 Field(name:S)，2 Index()。operandTypes 是现有 `Map<TypeId,MCFPPType>` 的类型描述符闭包，按类型结构 key 去重排序；导入后按新会话 typeId 重建 map，它不是形参名/操作数位置表。BoundExecutor、调用期 context borrow 和依赖某个执行作用域的 helper 不能导出；其声明调用仍保留在正文并在消费方所需上下文重新生成。

## 8. 运行时、归属与确定性

R 表是 tagged：1 FunctionBinding(`declaration:DeclRef, resourcePath:S, target:Target, generationOptions:C, policy:u8`)，2 TagContribution(`tag:TagKey, members:DeclRef[], replace:bool`)，3 Scoreboard(`name:S, criterion:S, display:C?`)，4 Initialization(`declaration:DeclRef, order:u32`)。policy=1 linkOnly，2 reanalyzableFallback；普通可分析函数必须仍有 B。预生成函数只有 target/runtime ABI/generation options 全部兼容时才可复用，编译器沿用现有 specialization 策略；普通已知实参不自动引入新函数特化策略。

资源表记录 `path:S, byteLength:u64, sha256:bytes(长度32), owner:DeclRef?, module:S, activation:S?, origin:u8`；origin=1 static，2 precompiled，3 runtimeSupport。每个最终输出路径恰有一个有效来源：如果某声明重新生成函数，屏蔽该声明对应 precompiled 源；不能简单把整个资源目录在最后覆盖生成结果。非同一拥有者的冲突必须诊断；标签按贡献合成，不能依赖最后写入者。runtimeSupport 可以通过明确定义的相同 digest 去重，但不能吞掉内容不同的资源。

只导出本库拥有的 D、注解效果、helper、tag contribution、scoreboard 和资源；依赖引用通过 DeclRef/PackageKey 保存。编译会话 global 累积内容、已编译实例缓存、调用图、临时计数器、绝对路径、ClassLoader、输入流和 mutable compiler state 均不入库。

确定性规则：字符串按 UTF-8 字节排序去重；package/dependency/key/map 按结构字段逐项排序；D 使用 §4 的声明 key/结构路径；N 使用按 D、正文 role 和子字段源顺序的首次深度优先遍历编号；类型/常量按结构 key 去重排序，引用 key 使用所属声明路径，不能包含待分配的表序号。A 从有序声明注解列表首次访问编号；H 从 A 的排序字段/容器项首次访问编号，循环只分配一次。递归声明用 owner 路径/DeclRef 截断结构 key，类型结构自身非法循环报错。有执行意义的语句、成员、参数、继承、实参和资源激活顺序保留，不排序。Debug 可缺省；存在时路径必须项目相对且 writer 设置一致时字节相同。

Debug 单个 record 为 `files:str[], locations:(node:N,fileIndex:u32,start:u32,end:u32,line:u32,column:u32)[], documents:(declaration:D,text:S)[], producer:str?`；offset 以 UTF-16 code unit 计，end exclusive，fileIndex 从 0 开始。它不参与语义绑定。无 Debug 时诊断仍使用 package/key/body/node 定位。会话可从树生成显示文本辅助现有 API，但不得重新解析该文本来恢复正文。

## 9. 校验与错误

解码在隔离模型内完成：检查头/段、长度、UTF-8、tag、引用域、唯一性、节点类别、常量及树无环、声明 owner 图、参数与正文归属、依赖身份、可见性和必需正文。随后进行全库注册、统一绑定和资源核对；任何一步失败都不得留下可用的部分库。

诊断至少包含 code、package/classifier、entry、section、record/byte offset，以及可用的 declaration/body/node。稳定 code：`LIB_FORMAT_NEWER, LIB_FORMAT_UNSUPPORTED, LIB_DEVELOPMENT_CACHE, LIB_CORRUPT, LIB_SEMANTICS_UNSUPPORTED, LIB_RUNTIME_ABI, LIB_TARGET, LIB_DEPENDENCY, LIB_SYMBOL_UNRESOLVED, LIB_DECL_CONFLICT, LIB_TYPE_UNSUPPORTED, LIB_BODY_UNSUPPORTED, LIB_ANNOTATION_CODEC, LIB_RESOURCE_CONFLICT, LIB_IO`。未知版本应先于无关的节点错误报告；缺依赖不能被误报为格式损坏。

输出先建立完整隔离模型并编码到目标同目录临时文件，成功 flush/close 后原子替换已有有效产物；失败清理临时文件并保留旧产物。归档生成同样先完整写临时归档。首次正式发布时冻结 v1 字节样本及其预期语义；本规范的新增实现未通过闭环前，不把它描述为可用的历史兼容证据。

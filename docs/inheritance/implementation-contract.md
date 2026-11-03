# data 继承实现契约

日期：2026-10-15。交付节点：`I_CONTRACT`。本文规定本轮实现与正式回归的目标行为；只修改本文件，不修改产品源码、不运行 Gradle。

## 范围与依据

用户授权修复 `build/inheritance-review/README.md` 中的 12 个探针问题与字段共享问题。原探针是缺陷复现，旧断言不能直接充当修复后的正确性断言。该报告明确留待规范决定的字段覆盖和父构造行为，采用下文的实现默认值；这些是本轮设计决定，并非用户此前确认过的语言规范。

依据当前工作树而非历史版本：

| 源码接点 | 当前事实与实现要求 |
|---|---|
| `src/main/antlr/mcfppParser.g4`：`compoundDeclaration`、`templateFunctionDeclaration`、`templateFieldDeclaration`、`extendName` | 已有多父列表、方法 `override`、字段 `var/const`、泛型父实参和 `super` primary；字段没有 `override` 关键字，父构造委托没有专门语法。沿用这些语法。文档示例用当前的 `x: int` 写法。 |
| `model/compound/DataTemplate.kt`：`resolveDeclaredParents`、`flatExtends`、`validateInheritance` | 已有恢复声明作用域、继承字段 clone、本地/延迟字段优先与部分契约检查。不能把报告中的旧共享行为当作这份源码的已验证结果；仍需正式回归。 |
| `model/compound/CompoundData.kt`：`extends`、`isSubOf` | 已有边插入时的循环检查，但依赖递归 `isSubOf`；完整图验证与安全递归仍须统一。 |
| `model/scope/CompoundDataScope.kt`：`getFunctionCandidates`、`getFunction` | 已有全父候选收集、按声明所有者的子类型关系隐藏同签名方法，以及 `ParameterMatcher` 排序；修复要保留这些已有工作。 |
| `model/function/ParameterMatcher.kt` | 已提供 `sameSignature`、值调用/类型调用选择与共同的 `best` 规则。应复用，不能另造一套父方法重载优先级。 |
| `analysis/DeclarationBinding.kt`：`call`、`PrimaryContext` | 字段推导已用声明签名与共同候选；`super` 仍取第一个父类型，需更换为明确的父成员查询。 |
| `Project.kt`：声明完成流程、`registerAnonymousTemplate`；`model/compound/GenericDataTemplate.kt`：`compile` | 已有父先完成、字段绑定、注解和签名刷新流程；普通、匿名、泛型实例和加载库必须从相同完成边界验证。单独增加 `validateInheritance` 方法而不接入边界不算完成。 |
| `model/function/DataTemplateConstructor.kt`：`prepareBody` | 当前遍历祖先只收集有效字段初值，没有执行父构造体；递归调用原父构造会重复字段/祖先初始化，需要显式执行计划。 |
| `model/function/Function.kt`：`prepareBody`、`incomingReceiver`、`compileBody`、`withFieldInitializerOwner` | 当前只绑定 `this`。父体执行和 `super` 必须保留同一实际 receiver、声明所有者、文件导入环境及当前函数恢复逻辑。 |
| `core/lang/obj/DataTemplateObject.kt`：`getMemberVar`；`core/lang/Var.kt`：`setAs`、`bindDeclaration` | 存储适配器与声明对象不同；需检查 const、nullable、访问和注解效果是否从有效声明传播。不能仅检查模板字段的布尔标志。 |
| `io/info/DataTemplateInfo.kt`、`FunctionInfo.kt`、`FieldInfo.kt` | 已保存父表达式、初值、声明环境、方法 override/abstract/访问；`FieldInfo.from` 仅导出本地成员，并在恢复时绑定 owner。往返应重建继承布局，避免把继承副本错误改成子类原始声明。 |

仓库根为 `F:/Project_MCFPP/MCFPP`；表中 Kotlin 路径相对 `src/main/kotlin/top/mcfpp`，ANTLR 路径相对仓库根。后文类名和方法名沿用这些接点。图中的 `analysis/TemplateFieldInference.kt` 当前不存在；实际推导入口是 `MCFPPFieldVisitor.completeTemplateFields` 与 `DeclarationBinding`。

## 图完成与身份

1. 先注册全部声明头，再在各自的声明文件、导入及泛型绑定环境中解析父表达式；类型签名完成后再检查继承契约。父子文件顺序与声明顺序不得改变诊断或选择结果。
2. 继承图采用未访问/正在访问/完成的 DFS 状态或等价算法。直接循环、间接循环、未使用的循环声明都必须在声明完成阶段报错，包含循环路径；不依赖实例化触发。拒绝的边不能加入可供后续查询的有效继承图。
3. 所有递归祖先、子类型、候选、初始化查询必须有访问保护，错误图不得导致 `StackOverflowError`。字段自包含的无限值布局检测与继承循环是不同检查，不能用 `ifInfinitiveReference` 代替继承图验证。
4. 普通类型身份使用规范声明身份；泛型实例使用声明身份与已绑定只读实参构成的规范实例身份。相同 `A<int>` 经菱形出现两次应去重，`A<int>` 与 `A<bool>` 必须保持不同。不得仅按短名称、显示生成名称或未经规范化的 clone 对象身份判定来源。
5. 图为无环图后，父优先的拓扑遍历以源码父列表顺序作为无依赖节点的稳定顺序。该顺序只用于稳定输出和构造副作用，不能用于消解方法/字段歧义。不引入 C3 或新的动态分派规则。

## 字段、属性与注解

对象仍按字段名称共享一个有效存储槽；父视图与子视图引用同一槽。因此字段类型采用不变契约：同名候选的规范 `typeId`、`nullable`、实例/静态性质和 `const` 必须一致。禁止用隐式转换、类型协变或最后一个父覆盖来合并可写字段。

| 情况 | 规定 |
|---|---|
| 同一原始字段经菱形到达 | 按声明来源和泛型绑定去重，保留一份有效字段。 |
| 无关父类型同名字段，类型或契约不同 | 声明阶段诊断，列出各父来源与冲突项。子本地同名声明不能修复不兼容的两个父契约。 |
| 无关父类型同名字段，契约一致但来源不同 | 没有本地声明时诊断歧义，避免静默选择初值/访问器。允许子以兼容的本地声明明确合并成同一槽。 |
| 兼容的本地同名字段 | 不需要字段 `override` 语法。本地声明为有效声明，保留自己的初值、const、注解、访问器和声明所有者；不得替换成父 `Var`。访问权限不得比任一父更窄。 |
| 父 `var`，子 `const` 或反向变化 | 诊断契约不兼容。保留子声明用于错误恢复，不能悄悄把 const 改为 var。父可写槽仍会通过父方法/父视图写入，收窄成 const 无法保持父承诺。 |
| 父字段/访问器 final | 禁止本地替换。自动生成的默认访问器不作为额外的用户方法 override 要求；显式访问器仍须符合该字段的读写及访问契约。 |

同名字段默认规则的理由是同时满足现有按名字布局、父视图可写性和本地覆盖语法。对父 var→子 const 报错是本轮的类型安全取舍；不是声称原语言已经规定此限制。

继承字段、属性及其可变注解列表必须独立。可以共享不可变声明来源和不可变语法树；不得共享会被 `Annotation.on`、字段替换、getter/setter 绑定修改的字段对象或可变容器。复制后保留父字段已有注解效果，子注解仅作用于子的有效声明，不回写父或兄弟。复制函数 accessor 若会改变 owner/上下文，也必须隔离或保持不可变，不可只浅拷贝 Property 后修改共用函数。

初始化按下述构造阶段执行。本地 `x: int = 9` 在父构造阶段后赋值，最终读为 9；无显式初值的本地声明不额外清零父构造产生的状态。初始化表达式在原声明 owner 的访问权限和导入环境下绑定、在同一实际 receiver 上执行。字段推导只查询类型，不执行表达式；每个实际构造执行一次对应的表达式。

至少保留以下正反例：父 `var x: int = 1`、子 `var x: int = 9`，结果 9；父子同为 `const x: int`，子值 8 且子注解不污染父；父 var/子 const 为带原因的负例。旧 `annotationsApplyToCompletedFieldsAndKeepInheritedReplacementOrder` 中 `assertSame` 与父被污染的断言必须替换，不能直接让旧测试继续代表成功语义。

## 方法候选、菱形与重载

方法签名复用 `ParameterMatcher.sameSignature`：名字、只读参数数量及规范化只读/普通参数类型；返回类型不构成重载。泛型参数重命名不能产生一个伪新签名。

每次成员查询先收集所有可达父方法和本地方法，再按以下顺序处理：

1. 同一原始方法经菱形重复到达时去重。
2. 相同签名中，声明 owner 严格更具体的方法隐藏祖先方法；判断 owner 的继承关系，不看父列表位置。`L:A`、`R:A` 且 `R override f`，`C:L,R` 的唯一有效实现为 `R.f`，调用结果为 2。
3. 仍有多个无关 owner 的同签名声明时，要求兼容的本地 override 明确实现，否则诊断歧义。一个抽象声明与另一个无关具体声明也不靠顺序自动合并；本地 override 可以同时履行兼容契约。
4. 不同签名组成共同重载集合。复用 `ParameterMatcher` 的隐式转换等级、参数类型特异性和所用默认值数量规则；唯一最优才能选择，平局报歧义。`f(int)` 必须胜过另一父的 `f(object)`，与父列表顺序无关。不要新增父距离优先规则。

字段推导、普通调用、泛型实例化和 `super` 查询使用同一候选语义、类型替换和排序核心。`DeclarationBinding` 只产生声明类型结果；值调用可绑定实值，但同一声明类型实参不得因来自不同入口而选不同重载。特别检查现有 `select` 与 `selectDeclaredTypes` 对只读参数和默认值计分的差异：需要统一查询输入，不能仅让它们各自调用名为 `best` 的方法。

## override、abstract 与访问

声明全部完成后统一检查，而非访问子类 body 时取决于父是否已经收集完毕。相同签名的本地方法必须写 `override`；标记 override 却无可覆盖父方法时也报错。父 final 方法不可覆盖。

返回类型允许现有类型关系认可的协变：本地返回类型必须是每个被覆盖返回类型的子类型；void 必须对应 void。访问权限不得缩小，使用实际 enum 顺序 `PUBLIC < PROTECTED < PRIVATE < COMPILE_PRIVATE` 比较。参数签名必须一致，不能以参数协变伪装 override。私有父方法没有可见的 override 槽；子同名私有实现可作为本地方法，不要求 override，也不参与公共继承候选。

抽象类型可保留未履行义务；非抽象、非 interface 类型必须提供每个有效抽象签名的唯一兼容具体实现，声明阶段报错，实例化入口也拒绝产生错误对象。禁止实例化显式 abstract 类型。抽象祖先仍可提供字段初始化与隐式空构造阶段，不能因为抽象类型不允许用户构造函数就跳过其字段初始化。

方法冲突、override 校验和 abstract 义务必须由同一份有效签名视图派生，避免候选表隐藏掉祖先以后丢失仍需校验的返回/访问契约。诊断指向子声明/继承列表，并带父声明来源；错误恢复允许继续分析，但不能让歧义调用生成第一父实现的命令。

## 构造计划与 super

选择执行父构造体是本轮默认：既然保留子类型关系和父方法承诺，派生对象必须经过父的构造阶段。此决定修复报告中“父字段初始化执行而父构造体跳过”的缺口，不保证父构造建立的任意值在子初始化/子体再次赋值后仍不变。

每次顶层构造先选择子构造重载并建立一个 receiver，再生成一次父优先的拓扑计划。每个唯一祖先实例阶段只执行一次，顺序为：

1. 对该 owner 的本地字段按源码顺序执行显式初值；
2. 执行该 owner 选中的构造函数体；
3. 最后进入派生 owner 的字段初值与构造函数体。

祖先字段初值即使被兼容本地字段覆盖，也在祖先阶段执行，让祖先体可读取其声明的初始状态；子初值在子阶段最终覆盖。`A.x=1; A.constructor(){x=7;}`、空子 `C:A` 的结果为 7；若 C 有 `x=9` 则为 9。共享祖先中的副作用在菱形中恰好一次。默认存储建立、const 的声明初始化权限和实际函数体写入检查必须区分；合法初始化不得因多 owner 阶段错误触发重复 const 写入或解锁普通成员中的 const 写入。

祖先阶段自动选择唯一可用空实参构造，允许现有默认参数填充；没有声明构造的普通/抽象祖先有隐式空体阶段。父构造必须可从派生类型访问；没有可访问空实参候选或存在歧义时，在派生声明完成处诊断来源父及所需参数，并在调用处阻止构造。当前语法没有父构造实参委托，因此首期不从子参数按名字猜值、不借 `super.f` 猜构造、不自行增加语法；需要实参的父构造明确报告“需要显式父构造绑定，当前语法未提供”。具有全部默认参数的父构造是正例，真正缺实参是负例。

不能把完整父构造 `invoke` 逐个塞进现有 `prepareBody`：每次完整调用会重新建立 receiver，并再次跑父链/初值。应把“顶层构造计划”与“该 owner 的字段初值及构造体阶段”分开，用现有 receiver、参数绑定、效果跟踪和声明环境能力执行。运行时对象与 compiler-only 对象都需验证；不能只在命令文本中看见父函数名就判定状态传回正确。

`super` 是成员访问的绑定视图：共享当前 `this` 的实际存储，从当前方法的声明 owner 的直接父集合开始查询，排除当前 owner 的本地方法，然后使用同一祖先去重、最具体方法和重载规则。它不能是复制出来的父对象，也不能取 `parent.firstOrNull()`。继承方法中的 super 以该方法原 owner 为锚点，不能随最外层实例类型移动。

单父 `override f(){return super.f()+1;}` 应可执行；多父如果同签名剩余两个无关实现，准确报告 `Ambiguous super member 'f'` 并列来源。原探针 `C:A,B` 的 override 中 `super.f()` 因歧义仍报错，但原因必须由“没有成员函数 receiver”变成真正的歧义。菱形若有唯一更具体实现则可调用该实现。只有抽象候选时报告没有可调用父实现。静态、顶层、无父成员中使用 super 均有对应位置诊断。字段 `super.x` 使用一致的父槽契约，不能绕过 const/访问检查或选择冲突父槽。

## 问题到图节点与验收断言

每条负例至少断言有预期类别诊断、来源准确、无崩溃；正例断言 `Project.errorCount == 0`。精确错误数仅用于已稳定的单一根因，避免把恢复阶段重复错误当规范。执行断言使用现有 `ScoreCommandExecutor`，不把模拟器结果称为真实 Minecraft 验证。

| 原审查问题/额外问题 | 主节点；源码接点 | 必须实现的验收 |
|---|---|---|
| `incompatibleParentsAreAcceptedAsBothSupertypes` | `I_FIELDS`；`DataTemplate.flatExtends`、`validateInheritance` | `A.x:int/B.x:bool` 的 C 声明报冲突，不能零错误声称同时满足两种槽契约；反向父列表结果一致。 |
| `localInitializerIsDiscardedWithLocalDeclaration` | `I_FIELDS` + `I_CONSTRUCT`；`MCFPPFieldVisitor`、`DataTemplateConstructor.prepareBody` | 父 x=1、子 x=9，执行 `C().x==9`，子来源/本地初值保留。 |
| `unrelatedMethodConflictSilentlySelectsFirstParent` | `I_METHODS`；`CompoundDataScope.getFunctionCandidates`、`InheritedMethodResolver`（可提取） | 无本地 override 的 A.f/B.f 同签名在 C 声明报歧义；增加兼容 override 后无错且调用子实现。 |
| `diamondLosesMoreSpecificOverride` | `I_METHODS`；候选收集与 owner 比较 | `C:L,R` 和 `C:R,L` 均选择 R.f，执行值 2；原始 A.f 只算一个声明。 |
| `overloadSelectionStopsBeforeExactMatchInLaterParent` | `I_METHODS`；`ParameterMatcher`、scope 调用入口 | int 实参选择 B.f(int)，执行 2；交换父顺序相同；不可比较最佳候选报歧义。 |
| `inheritedAbstractObligationIsNotChecked` | `I_OBLIGATIONS`；完成边界、`InheritanceContractValidator`（可提取）、构造调用入口 | 具体 C 缺 f 即使未实例化也报错；构造拒绝；abstract C 和具体完整实现分别为正例。 |
| `overrideAcceptsIncompatibleReturnAndNarrowedAccess` | `I_OBLIGATIONS`；全父签名契约 | int→bool 返回与 public→private 各独立负例；合法返回协变和权限扩大为正例。 |
| `parentConstructorBodyDoesNotExecute` | `I_CONSTRUCT`；constructor、`Function` receiver/体执行 | 父 x=1、构造 x=7，子结果 7；子 x=9 的组合结果9；共享祖先副作用仅一次。 |
| `declarationOrderChangesMissingOverrideDiagnostic` | `I_GRAPH` + `I_OBLIGATIONS`；声明完成与 validator | 父/子两种声明顺序均报告缺 override；正确 override 两种顺序均零错；跨文件前向父同样验证。 |
| `indirectInheritanceCycleCausesStackOverflow` | `I_GRAPH`；父绑定、`extends`、`isSubOf`、完成边界 | 直接/间接/未使用循环均有循环路径诊断，无 StackOverflow；合法菱形无错。 |
| `inferenceSelectsDifferentOverloadThanInvocation` | `I_METHODS`；`DeclarationBinding.call`、`ParameterMatcher`、实际成员调用 | 推导为 bool 且实际也调用 B.f(int):bool，无赋值类型错误；以产生可观察副作用的初值证明推导不执行、构造仅执行一次。 |
| `superHasNoRuntimeBinding` | `I_CONSTRUCT`；`Function.prepareBody`、`MCFPPExprVisitor.visitPrimary`、`DeclarationBinding` | 单父 super 可执行并更新同一 receiver；原 A/B 多父探针报告歧义而非成员上下文缺失；唯一菱形实现可执行。 |
| 字段共享/注解污染/const 丢失 | `I_FIELDS`；`flatExtends`、Property/Var 复制、`applyDeclarationAnnotations`、`DataTemplateObject.getMemberVar` | 父子字段及可变注解列表非同一对象，子 DataOnly 不改变父/兄弟；同 const 覆盖保留 const；var→const 有明确负例；实例读写遵守有效 const。 |
| 泛型与 fresh `.mclib` 同等行为 | `I_LIBRARY`；`DataTemplateInfo`、`FunctionInfo`、`FieldInfo`、构造信息与泛型编译 | fresh 进程/清空模型缓存后加载新生成库，复现冲突拒绝、R.f 选择、父构造一次/子初值覆盖、注解隔离和访问/const 检查；同绑定泛型菱形去重，不同绑定同名槽冲突。 |

`I_LIBRARY` 还须给现有 `DataTemplateTest` 加编译诊断断言；已有 union 用例的编译 ERROR 不能因 JUnit 未失败而算成功。源码和库测试都应覆盖返回/访问/abstract 元数据；仅对同一内存模型序列化后立刻用写入缓存读回不构成往返证据。

## 对任务图的范围校正

`I_GRAPH` 与 `I_OBLIGATIONS` 的 writePaths 需补 `Project.kt`、`GenericDataTemplate.kt`，覆盖匿名/泛型/加载库的完成入口。`I_METHODS` 需允许修改已有 `ParameterMatcher.kt`，新 resolver 负责候选与继承来源，不重复转换排序。`I_FIELDS` 删除不存在的 `TemplateFieldInference.kt` 路径，替换为 `MCFPPFieldVisitor.kt`、`Property.kt` 与 `DataTemplateObject.kt`（按实际缺口改最少文件）。`I_LIBRARY` 需允许必要的 `FieldInfo.kt`、`VarInfo.kt`、构造信息序列化文件，先检查再补元数据。

`I_CONSTRUCT` 当前标为 small，实际包含三个可独立验收的阶段，建议拆为：

- `I_INIT_PLAN`：父优先唯一 owner 计划、空实参父构造选择和缺实参诊断；依赖字段/义务完成。涉及 `DataTemplate`、完成阶段与 `DataTemplateConstructor`。
- `I_PARENT_BODY`：同一 receiver 上执行 owner 初值和父体，修复默认/显式/抽象祖先/菱形/const/编译期对象执行；依赖计划。涉及 `Function.kt`、`DataTemplateConstructor.kt` 与 `DataTemplateObject.kt`。
- `I_SUPER`：声明绑定和执行绑定共用父候选视图，准确诊断多父歧义；依赖方法解析与 receiver 支持。涉及 `Function.kt`、`DeclarationBinding.kt`、`MCFPPExprVisitor.kt` 及必要的绑定视图类型。

不拆任务也必须扩大该节点 writePaths 并保留以上三组验收；参数化父构造的新语法是将来独立语言提案，本轮交付明确诊断。

## 本交付核对

本文只交付契约和验收映射；未声称上述产品行为已实现或正式测试通过。已对照报告 12 个具名探针、额外字段共享问题、现有声明/构造/类型选择/库恢复接点逐项核对。

自评：准确性 4/5（源码接点可复查，尚无本轮运行证据）；完整性 4/5（13 项和库往返都有验收，执行结果由后续节点提供）；清晰性 4/5（决定与现状分开，构造阶段有具体例子）；可行动性 4/5（节点/接点/断言齐全，范围调整待协调者落图）；简洁性 4/5（保留必要的语言取舍，后续可在实现完成后移除暂时路径建议）。平均 4.0/5。优先补充实际执行与 fresh 库回归证据，再按实现定稿源码引用。自检：接收者能区分已读取的证据、选定的默认语义和仍待实现的行为。

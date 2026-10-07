# 类型系统迁移状态与指南

本次修改是按阶段推进的内部迁移。以下表格区分已经接入编译器的行为和仍未完成的工作；
不能将目标规范中的全部语义视为已经实现。

## 当前规则（截至2026-10-07，Asia/Shanghai）

用户规则：`type` 仅作为泛型参数；普通 typed/inferred/const 变量、data/object 字段、普通参数和返回值，以及擦除值与集合中的 `TypeValue` 均拒绝。`typealias`、内部 `TypeVar` 解析和现有 readonly 泛型绑定保留。旧字段/集合正向假设已撤回，不作为合法性证据。重构不要求兼容旧 `.mcfpp` 写法。阶段87已实现并限定验证；阶段88–127按验证记录接入受测集合、数值/文本/命令、seed结果、Datapack/Debug、delegated-int、Time、诊断接口及受测legacy入口；当前MCFL57；Std外剩余4个Java类、19个CommandReturn注解及7个旧void wrapper；whole17仍未完成。

## 当前已接入

阶段86（历史）actual generic父项限定通过：8prod+新fixture99行/helper2行+bin，共11文件175+/35-。声明环境内解析完整父文本并tryResolve，actual T/N冻结绑定后解析；两Info nullable parentExpressions为source权威，避免复制specialized parentInfo。共享INDEX_TYPE恢复hook同时接Project与StringTest，compiled父readonly names的vars/properties不继承覆盖子T/N；裸Marker interface只验证canonical父恢复。MCFL19标准库292301 bytes重建0errors/0warnings，最终联合3全绿、cache回归在此前绿，跨轮4不同case各过。

新86 source0errors/9118warnings、fresh0/9121warnings，source盘4/9/bool1/5、fresh4/9/bool1/10/frame0；旧modifier与final负例也通过，负例每次1错误为expected。source已有flatExtends重复警告，未解决该类别。无fullcheck/server，不扩super、source object/interface actualgeneric父或完整Kryo specialized身份。阶段87–89当前限定路径见下；以下85为历史记录。

阶段87：`TypeUsage`统一判定接入源码声明入口、已绑定普通签名、IR/擦除值与集合、延迟字段，普通值位置拒绝保存 `TypeValue`。依赖普通 `type` 存储的旧30个正例撤回，4个合法的直接泛型类型表达式库往返fixture保留；9个新规则方法及相关回归共19个不同用例跨轮各自通过。final3为5项定向复查全绿，而非单次全套通过；各轮验证与修复边界见verification.md。底层runtime carrier保留 `T!` 语言类型及常量要求。MCFL19/bin292301不变，无stdlib/fullcheck/server。

阶段88：`NativeCallContext`作为legacy实例成员 `list.clear()` 的显式调用入口，公开函数、receiver ValueRef/Place、immutable CompilerValue快照与通用receiver写回；private Var桥内部沿用StorageAccess，领域逻辑仍在ListOperations。Java clear用单context ABI，NativeFunction以实际invocationArgs构造context，其他native ABI保持原样。MCFL19→20；stdlib2 Project0/0，三份292301-byte bin资源SHA相同。三项联合回归source/fresh实际文件均执行reset clear/add并得7、frame0；详见verification.md。仅为受测路径，MNI其余入口与whole17仍未完成。

阶段89：剩余10个list原生方法已接入同一context；普通实参和返回值/位置从actual invocationArgs绑定，Java侧不暴露Var，领域逻辑留在ListOperations。`Commands.tempFunction`两个重载在父namespace登记至已有canonical namespace，通过`runInFunction`恢复上下文并标记生成函数bodyCompiled以供库导出。MCFL20→21，标准库资源292007 bytes、SHA256 `4E97F3CBAB14B1EF3A89991DEC1722121768A113AF1D243DFC9ABC7DE498561F`；Cache、ListMember、新列表fixture三个不同用例跨轮各自通过，最终仅fixture复查。首轮consumer namespace NPE及修复详见verification.md。此次未统一其他MNI/operator，whole17仍未完成；阶段90迁移Dictionary/Map/ImmutableList共13个原生方法并完成限定验证。

阶段90：迁移Dictionary 4、Map 6、ImmutableList 3个原生方法，复用既有 `NativeCallContext`，不新建调用context或扩展operator。字典key限制、Map dynamic key与readonly list dynamic needle边界保留。MCFL21→22，stdlib与source/build资源292301 bytes、SHA256 `EEFF5FC16E8751D87D4248E8380BBFCC4980FB08BD1E7BA6B06A1DD849E2B4D6`。新fixture最终source/fresh consumer磁盘结果10/5/227/7/2/11/-1及frame0通过；旧/未知缓存格式回归另在首轮通过。三次执行器/fixture修复失败均止于source端，final5单项复查通过；详见verification.md。whole17仍未完成。

阶段91：int12（含 `~=` range）、float11、bool5个原生运算符共28项接入显式单context入口，Kotlin层直接调用typed core，Bool适配在真实调用作用域内完成。MCFL22→23；stdlib与三份资源292007 bytes、SHA256 `800AF3AFB375C71643E23C11B2EB64261D3691F38F17D2204CC18FC62D477422`。Cache、PrimitiveIR与新fixture三个不同用例跨轮各自通过；首轮执行器缺score比较命令，最终仅复查fixture，不是最终联合3项。source/fresh consumer磁盘结果及frame0通过。legacy循环、private Var桥和其他MNI仍未完成，whole17未完成。

阶段92：删除四个byte/short Java类中的44个已拒绝算术注册及 `MCFPPNBTType.injectedBy` 的4处注册；不涉及没有这些注册的long/double。保留精确格式、转换和统一拒绝诊断。MCFL23→24，stdlib Project0/0，三份bin292301 bytes且SHA256一致。Conversion、TypeKernel、CacheFormat三个不同用例各自通过；TypeKernel五个预期负例分别报告拒绝错误。下一阶段93迁移ConversionData的49个数值转换入口。

阶段93：ConversionData的49个静态入口（36个numeric conversions及13个toNBT）接入显式context，支持nullable receiver/`withArguments`，按declaredVoid与static构造NF；唯一Java名通过原MNIFunction.identifier保留语言别名，Namespace/CompoundData/FieldVisitor匹配effective identifier，`NumericConversions`发布实际结果引用；迁移49个ABI不表示所有转换均可执行，既有unsupported诊断保持。MCFL24→25，stdlib0/0，三份291070-byte资源SHA256为 `D41A947D59084FA3A36D66B940988FF4CD19CA087D54947E5B336B1DADE76832`。Cache、ConversionIR、新fixture三个不同用例跨轮各过；最终仅复查fixture，不是联合3项。String返回NBT的错误引用已通过typed view修复，保留真实payload/address而不扩隐式转换。磁盘值-1/17/ByteTag 1/`hello`/`{value:7}`、frame0通过。阶段94迁移七个toText入口。

阶段94：七个 `toText` 方法接入单receiver context与 `NativeTextOperations`；源码实际覆盖五个Java方法和int两种状态，两个Any入口无source覆盖。移除 `MCString.getMemberFunction` TODO，恢复Var的实例成员/签名查找；String runtime/Concrete lazy `instanceData` 接通，NBT由错误的 `objectData` 转为 `instanceData` 并使用 `commonType`。MCFL25→26，最终三份291070-byte资源SHA256均为 `9ED46C95796CBADE0007D246E630FF094F9018B03772AB57B145B98E412EEAE5`。Cache与text fixture两个用例跨轮各自通过，final3只复查fixture；source/fresh磁盘payload与返回7/frame0通过。无聊天渲染、跨帧寿命或fullcheck/server验证；whole17仍未完成。

阶段95：`conversions.mcfpp` 的49个native RHS改为Java真实方法名 `toTargetFromSource`，`decodeByte` 使用 `ConversionData.toIntFromByte`；FieldVisitor取消语言identifier alias回退，Namespace/CompoundData仍通过annotation identifier支持语言重载。用户无需兼容旧 `.mcfpp` 显式Java引用。MCFL26不变；stdlib重建Project0/0，既有conversion fixture source/fresh定向复查通过，资源291070 bytes、三份SHA256一致。只验证既有路径，未复跑Cache或其他旧绿，whole17仍未完成。

阶段96：接通text runtime assignment/三个构造路径和两项concat operator单context，JsonTextConcreteData去重注入复用JsonTextData。MCFL26→27；标准库Project0/0，三份291364-byte资源SHA256相同。首轮fixture命令执行器不支持text component append；final2在source读取到CompoundTag而预期ListTag；runtime adapter修复后final3单fixture通过，source/fresh consumer实际磁盘均验证12观察、original/copy与return7/frame0。Cache和fixture两个用例跨轮各过，final3非联合2项。未知MNI屏障物化后常量折叠路径仍未完全验证；无聊天渲染、跨帧寿命、fullcheck/server。

阶段97：String/NBT各自的toText入口合并到单一Java类，依据实际receiver状态处理runtime与Concrete调用，并移除重复Concrete类及重复helper；不扩展到其他native或effects。MCFL27→28，标准库Project0/0，三份291176-byte资源SHA256相同。指定text fixture与Cache回归联合通过；即时toText输入为tag literal，但AB/AS拼接仍走临时数组append，未证明concat折叠。验证细节及重复执行说明见verification.md。whole17仍未完成。

阶段98：`EncodedChatComponent`保存不可变SNBT快照，`StorageAccess.restore`将完整text ListTag恢复为immutable encoded component并保留声明类型。T! copy与拼接的source/fresh磁盘fixture得到A/A/AB/AS及return7/frame0；生成命令中joined/suffixed是flat component literal list。MCFL28/wire不变，无stdlib或Cache回归。只验证现有T! text路径，不扩展其他类型/effects，whole17仍未完成。

阶段99：仅将 `DataObjectData.toText` 迁入显式context，复用 `NativeTextOperations` 中的DTO SNBT/runtimeNBTChat实现。source/fresh普通Payload/Box fixture磁盘验证字段值7、返回7、runtime NBT组件及frame0；普通data走 `buildUnConcrete`，不据此宣称Concrete覆盖。JavaVar的host转换及Any host/getDefault/equalNull保持原范围。MCFL28→29；stdlib Project0/0，三份291431-byte bin SHA256一致；fixture与Cache两项最终联合全绿。whole17仍未完成。

阶段100：`PlainChatComponent` 的纯文本编码对text使用Fastjson2 `JSON.toJSONString` 转义单个字符串；引号和反斜线的T!快照/copy/concat及NBT字符串toText在source/fresh磁盘fixture通过。MCFL29不变，无Cache/stdlib；未覆盖控制字符或聊天渲染。whole17仍未完成。

阶段101：`LootTablePredicateData.pass/fail` 两个bool入口使用显式context；`NativePredicateOperations`复用 `CommandBoolPart` 生成谓词条件，并通过现有结果发布路径返回 `ScoreBool`。source/fresh导出命令及Cache回归通过，MCFL30；不涉及world模拟或谓词求值。验证范围与日志见verification.md。

阶段102：16个不受Java primitive-float签名冲突影响的StdCommands void入口迁入显式context；`place`仍可带 `MCFloat` 参数。保留damage的四个primitive-float入口及其他107个 `CommandReturn` 接口。修复`ResourceID.toCommandPart`以声明override并从Java wrapper读取 `id.toCommandPart`。真实fixture验证两个place调用各自生成一次macro，准备参数先于调用；未知target按普通参数处理。MCFL31，source/fresh项目均0 errors，Cache回归在首轮通过；详细轮次见verification.md。不据此声称非宏路径或world执行已覆盖。

阶段103：四个damage void入口迁入显式context，保留语言标识及默认语义，处理ResourceID DamageType与SelectorVar签名差异。动态float受现有 `FloatProviders.enabled` 限制；不新增浮点引擎。damage fixture与Cache回归均通过，source/fresh生成宏及MCFL32资源已验证；未验证world/float执行。详细边界见verification.md。

阶段104迁移System九种print语言入口并完成限定验证；最终fixture复查通过，Cache仅首轮通过，未验证macro/world执行或tellraw渲染。阶段105 delegated-int、阶段106 Time及阶段107两个legacy分支入口已限定验证，细节见verification.md。阶段108删除 `System.typeOf` 并迁移四个诊断void接口；阶段109接通seed的普通CommandResult结果捕获；阶段110迁移StdCommands剩余106个结果入口；阶段111迁移Datapack九项及Debug两项；阶段112迁移Team三个receiver结果入口；阶段113迁移Op/Recipe六个player-target入口；阶段114迁移Worldborder七个命令结果入口；阶段115迁移BossBar七个结果入口；阶段116迁移WorldObject两个结果入口；阶段117迁移Random三个reset结果入口；阶段118迁移EntityTag三个结果入口；阶段119迁移实体joinTeam/leaveTeam。阶段120迁移Entity effect清除两个入口；阶段121迁移两个effect授予入口；阶段122迁移stopRide；阶段123迁移Player XP六个入口；阶段124迁移Player advancement十个入口；阶段125迁移Player状态与ride命令；阶段126迁移entity-target tp；阶段127迁移tell/w消息命令。当前MCFL57，Std外剩余4类、19个CommandReturn注解及7个旧void wrapper；下一步限定为实体属性接口，详见next-stage-plan.md。

阶段126：`EntityTeleportData`将entity-target `tp`接入单context qualified结果并挂到`EntityData`，删除旧`entity<1>`目标重载；坐标tp与setSpawnpoint保持不变。唯一source/fresh fixture验证`@a`到`@p`、canonical readonly/Unknown/null、实际selector kind，以及DTO receiver与多实体destination在capture前拒绝。stdlib与联合Cache回归通过，MCFL56；不模拟world/frame0。

阶段127：`PlayerMessageData`将`PlayerVarData.tell`与`w`接入单context qualified结果，caller为entity并增加`EntityData`第九个`@From`。sender限制player selector是项目API选择，targets按命令合同也要求player selector；整体`execute as sender run tell|w targets message`只捕获一次聚合结果，不表示每个sender有独立结果。source/fresh fixture验证`@p` sender、`@a` targets的两条宏及消息参数来源，并拒绝`@e`和DTO；不模拟发送或JSON。旧方法缺caller且未发布结果，不能视为保留正确旧ABI。MCFL56→57。

阶段108：debug/info/warn/error四个void方法迁入显式context并标记 `NoExternalWrites`；诊断保留编译期concrete内容及runtime宿主 `toString` 语义，不生成runtime NBT。删除 `System.typeOf`，以符合普通值位置禁止保存 `type` 的规则。两个定向方法与标准库均通过，三份MCFL37资源为289476 bytes且SHA256一致；详见verification.md。whole17仍未完成。

阶段109：普通 `CommandResult` 的只读 `result:int` 与 `success:bool` 由seed单context发布；按真实declaredReturnType保留未知事实，以root `{}`和一次双execute-store生成磁盘命令。source/fresh模型及导出合同通过，不模拟世界结果或frame0。MCFL37→38；stdlib、Cache与fixture分轮验证细节见verification.md。StdCommands剩余106个CommandReturn入口仍待迁移，whole17未完成。

阶段110：`StdCommands`剩余106个旧CommandReturn入口已接入单context；当前127个Java入口都为单context签名，含107个CommandResult结果和20个void。源签名（返回类型变化除外）、表达式参数映射及文字词序已比较；source/fresh仅验证seed/help/say导出，不宣称全部命令world验证。MCFL38→39，stdlib与Cache/新fixture指定检查通过；其他Java类中91个CommandReturn注解和7个void wrapper仍未迁移。whole17未完成，详见verification.md。

阶段111：Datapack九个与Debug.start/stop两个静态命令入口迁入单context；Debug.function旧TODO保留。`captureCommandResult`从Std共享抽取，Kryo嵌套Info跨库恢复时按Declaration身份重用已加载namespace canonical模板；不改wire/context。source/fresh受限fixture与MCFL40验证通过，Cache只首轮通过。其他13个Java类仍含80个CommandReturn注解与7个void wrapper，whole17未完成；详见verification.md。

阶段112：Team.register/unregister/clear三个receiver方法迁入单context qualified `CommandResult`；通过现有 `withAdapters` 从DTO真实 `id`/`displayName` 字段读取值并复用共享结果捕获。`Team.mcfpp`显式导入标准库结果类型。fixture严格检查displayName的NBT chat component，以及id的macro准备、单次调用、双store与root初始化。MCFL41；stdlib2与定向fixture通过，Cache在首轮通过。未覆盖MNIMutator、world执行或frame0；其他12个Java类及7个旧void wrapper仍未迁，whole17未完成。

阶段113：Op.op/deop、Recipe.give/take/giveAll/takeAll六个player-target入口接入单context qualified `CommandResult`，entity为普通参数；捕获前证明SelectorVar只选择玩家。onlyIncludingPlayers保留克隆/筛选查询，不把裸@s或反向player筛选视为证明；Recipe读取DTO真实id，Op.deop修正为deop命令。MCFL41→42，stdlib与限定source/fresh fixture通过，Cache在首轮通过；未模拟world/frame0。其他9个Java类仍含71个CommandReturn注解和7个旧void wrapper，whole17未完成。

阶段114：WorldborderData七个MNIFunction（add、setCenter、setDamageAmount、setDamageBuffer、setSize、setWarningDistance、setWarningTime）迁入单context qualified `CommandResult`，保留普通参数、注解/default和命令词序；七个backend入口为薄捕获并复用FloatProviders。`Pos2`增加计算型`hasRuntimeRepresentation=false`，只表达编译期坐标，不做不支持的NBT编码，无serialized backing field或额外wire变化。source/fresh fixture验证七个结果、readonly/unknown模型及严格参数domain；consumer生成一个direct center命令和六个macro，并断言七个独立root、双store及一次初始化。MCFL42→43；stdlib与指定fixture通过，不验证world/frame0。Cache在首轮joint通过；具体分轮见verification.md。BossBar等后续入口仍待迁移，whole17未完成。

阶段115：BossBar七个方法接入单context qualified `CommandResult`，Enum使用capture前guard，entity参数沿用玩家限定；DTO保留既有id:string并新增name:text字段；单参id构造器以id.toText初始化name，二参构造器使用(string,text)。Java旧 `list` 重命名为 `listAll`以避开语言保留字，命令仍为bossbar list，receiver保持实例调用。新增EnumMemberInfo持久化SNBT字符串并在读取时重建Tag。MCFL43→44引入入口，MCFL44→45持久化Enum信息。source/fresh fixture覆盖七调用、literal enum、@a、readonly/unknown结果以及六个macro和一个list direct命令，验证七个独立root及真实字段/参数准备。最终fixture通过；Cache仅MCFL44首轮通过，未声称MCFL45 Cache验证。没有验证world/executor/frame0、旧属性accessor/mutator或静态BossBar.list；其余7个Java类尚有57个CommandReturn注解及7个旧void wrapper，whole17未完成。

阶段116：`WorldObjectData.setDifficulty`与`setWeather`两个静态native方法迁入单context qualified `CommandResult`，无caller参数、default或readonly参数；capture前要求EnumVarConcrete，keyword使用`value.identifier`，weather duration为int。旧Time缓存及weather/time accessor/mutator未改，生产尚未导出World对象。source/fresh fixture用真实`@From FixtureWorld`检查两个canonical readonly/unknown结果及null snapshot、difficulty direct和weather macro；duration按实际NBT读取→score复制→NBT编码→macro slot捕获。标准库重建和两项定向测试通过，MCFL45→46；标准库Project0/0，source/fresh Project分别0/9118与0/9119，负向Project4/9119为预期guard及未发布诊断。实际命令合同、双store及一次root初始化通过；不涉及world执行、executor或frame0，也不据注解推断可省略参数。截至阶段116，Std外剩余6个Java类、55个CommandReturn注解及7个旧void wrapper。

阶段117：`RandomData.reset`、`RandomObjectData.reset`与`resetAll`三个入口接入单context qualified `CommandResult`。flags以完整`CompilerValue.Bool`快照捕获，seed int保留；修复实例/source flag顺序，普通NativeFunction分支传递readonly实参，ImVisitor raw调用使用既有`buildMacroFunction`。`Random.mcfpp`去掉三个旧raw命令末尾的斜杠，测试datapack导出错误现保留异常原因。14个路径包含9项源码/测试、bin及四份文档。MCFL46→47，三份资源296252 bytes、header `4c46434d2f000000`、SHA256 `8D569CF61A35D3D7A7D6371C7C6E6FD4F3AFDE3C09948FD2BBD3BBA2C244547B`。final6 fixture通过source/fresh正向合同及负向检查；未验证整条17项目标、fullcheck、server、world或frame0。

阶段117后，剩余4个Java类含52个CommandReturn注解及7个旧void wrapper；whole17仍ACTIVE未完成。

阶段118：`EntityVarData.addTag`、`removeTag`、`listTag`三个入口接入单context qualified `CommandResult`；新增命名空间解析仅识别`ENTITY`片段，bare `SelectorVar`可保存并恢复完整immutable快照，MNI只在显式qualified解析失败后查找已加载canonical模板。最终fixture的source/fresh往返与DTO负向检查通过，producer/fresh分别0/9118与0/9119，negative 2/9119。predicate selector等其他形式不据此宣称完整快照支持。MCFL48；whole17仍未完成。

阶段119：`joinTeam(Team)`与`leaveTeam()`接入单context qualified `CommandResult`，join从真实`Team.id`捕获参数，leave移除旧伪Team参数。唯一fixture的source/fresh命令合同及DTO负向检查通过；MCFL49。Std外剩余4个Java类、47个CommandReturn注解和7个旧void wrapper；whole17未完成。

阶段120：`EntityVarData.clearEffect`与`clearAllEffects`接入单context qualified `CommandResult`，使用真实`Effect.id`并在捕获前验证Selector。指定fixture与Cache回归通过，source/fresh命令合同和DTO负向断言均通过；MCFL50。仅覆盖这两个入口，未验证world执行或完整Selector/effect行为；whole17未完成。

阶段121：`effect`与`effectInfinite`两个授予入口使用单context结果捕获；bool常量直接生成字面量，动态score及未知bool按实际路径准备宏参数。source/fresh fixture与Cache回归通过，MCFL51；只验证受测参数和命令路径，不宣称Minecraft世界效果或完整返回/帧恢复。Std外剩余4个Java类、43个CommandReturn注解与7个旧void wrapper；whole17未完成。

阶段122：`EntityRideData.stopRide`使用单context结果捕获；仅接受已证明为单实体的selector，DTO与多实体selector在capture前拒绝。stdlib、指定source/fresh fixture与Cache回归通过，MCFL52；验证了两个调用的磁盘命令及负向guard，不代表世界骑乘执行或frame0恢复。Std外剩余4个Java类、42个CommandReturn注解与7个旧void wrapper；whole17未完成，详见verification.md。

阶段123：`PlayerVarData`六个XP入口接入单context qualified `CommandResult`，并新增挂到`EntityData`的`PlayerXpData`。capture前检查player selector；add/set保留multiple-player合同，query要求single player。指定source/fresh fixture与Cache回归通过，MCFL53；实际验证参数捕获和selector负向guard，不模拟世界XP值或frame0。Std外剩余4个Java类、36个CommandReturn注解与7个旧void wrapper；whole17未完成，详见verification.md。

阶段124：Player advancement十个grant/revoke入口接入单context qualified `CommandResult`，真实读取`Advancement`的`ResourceID.id`，capture前检查player selector。source/fresh fixture与Cache回归通过，MCFL54；验证八个动态id宏、两个everything直发调用及DTO/非玩家负向guard，不模拟world advancement或criterion。Std外剩余4个Java类、26个CommandReturn注解与7个旧void wrapper；whole17未完成，详见verification.md。

阶段125：迁移Player `clear()`、`clear(string,int)`、`setGamemode`及Entity ride mount入口；新`PlayerStateData`从`EntityData`导入。保留clear语言重载、capture前player/enum/双方single-selector guard。标准库、指定source/fresh fixture与Cache回归通过，MCFL55；仅验证生成合同与guard，不模拟世界命令或frame0。Std外剩余4个Java类、22个CommandReturn注解及7个旧void wrapper；whole17未完成，详见verification.md。

重构不要求兼容旧 `.mcfpp` 写法；显式引用新旧语法无需并行保留。

阶段85 source template ABSTRACT/FINAL与object FINAL、compiled final、final继承拒绝及abstract默认ctor跳过已限定验证；两个Info保存final，Kryo声明前缀及early/Unsolved壳同步final。8prod34+/19-加两个fixture110行共9文件144+/19-，另更新标准库资源bin。MCFL18真实wire升级；旧/未知缓存格式拒绝回归通过。stdlib重建14s、compiler0/0、287554 bytes；最终必要联合3全绿，正例source/fresh0errors（fresh9119已知warnings）、磁盘4/9/4/4/frame0及独立模型通过；三种final父负例分别source1/fresh1预期错误，拒绝及父关系正确。无fullcheck/server，whole17未完成。

下一86 generic父项实际readonly解析待单fixture RED及API：extendName虽接受Parent<int>，现lookup仍把全文作name；需声明scope/canonical解析、generic实际T/N绑定后处理及全includes/声明环境恢复。最小parent表达式metadata由root定API，可能MCFL19和必要regen，尚未实现。阶段85不覆盖TypeAS修饰、实际generic父参数、interface runtime/完整shape或abstract运行时构造；下面84/83为历史记录。

阶段84 generic object typed static字段与显式constructor初始化限定通过：7prod+73行fixture，生产/测试125+/22-。对象marker共用静态NBT root，constructor/字段完成/导出覆盖compiled object；静态字段绑定及冷unqualified读取建立真实地址，完整静态快照保留、地址附加不代表初始化。StorageAccess派生read/write值保留parent，防止字段value污染同名normal参数。最终必要联合2全绿；source4/9/4/9、fresh9/4/9/4、ordinary7及frame0均从真实文件执行。三不同case跨轮各过，非最终联合3。MCFL17/bin289989/schema未变，无stdlib/fullcheck/server。

阶段84当时的下一85为source/fresh abstract/final flags，两bounded fixtures先RED；source模板/object标志、compiled final、final继承检查、abstract默认ctor跳过和final metadata/Kryo early shell待实现，计划MCFL18及必要stdlib重建，尚无验证。阶段84不解决autoload、generic compiler-only字段或generic object wrapper直接作为Kryo库字段类型；whole17仍未完成。以下83为历史记录。

阶段83（历史）仅两生成identifier表达式的编号separator改'-'，隔离Settings_int-0/relay-0与合法Settings_int_0/relay_0；FullID/key arguments/options不变，无新命名系统、schema、MCFL或stdlib变化。42行source-only fixture真实磁盘4/9/4/9/frame0、object namespaceID/OWNER storage prefix、free function namespaceID/prefix及四targets distinct通过；最终仅新1，四case跨轮各过。试joint4在runtime与target隔离通过后失败于过强Function.prefix比较，ROOT fixture改为owner prefix，未改一般成员prefix语义。MCFL17/bin289989不变，无stdlib/fullcheck/server；82提交6512e115f0fc8d7d2770b60e6516169450829cf2（6文件97+/17-）。

仅generated-vs-legal受测隔离，不扩casefold/跨库同名或整体命名机制。下一84 generic object typed static字段/explicit constructor初始化先单fixture RED，source/fresh磁盘4/9、无自动load，未实现/测试；shared marker/static root/ctor导出按实证接入。整体IR/MNI、interface runtime及其余旧边界仍保留，whole17未完成。

阶段82历史记录：source generic特化磁盘导出限定通过。DatapackCreator仅12+/2-：namespace局部Compound身份visited入口，marker走原genObject、普通模板走原genTemplate，并递归既有compiledTemplates缓存；其余函数输出原样，保留AST-null/std helper，不套imported bodyCompiled过滤。46行source-only fixture无consume或in-memory fallback，Box1/relay1/Settings2、实际init/read/static read文件及磁盘4/9/4/9/frame0通过。

RED source0/0且缓存通过，缺实际Box构造器文件；最终必要联合3全绿（新82、旧80、普通ObjectMethods），旧consumer各0errors/9119已知warnings。MCFL17/bin289989/schema未变，无stdlib/fullcheck/server。81提交2437ccfa9af2f21ddb3b2c9201b25535a4418f28（10文件202+/37-）。下一83先单source RED验证生成Settings/relay名与合法源码名隔离，拟仅现identifier末尾'-'，不造命名系统；尚未实施/测试。只受测source泛型template/object，autoload/字段/interface runtime及整体IR/MNI、empty prototype等旧边界仍保留，whole17未完成。

阶段81历史记录：static generic interface TypeValue限定通过：真实interface声明scope/header-only原型、compiled.isInterface及origin kind身份、默认ctor排除、existing generic wrapper/serializer的exact canonical恢复接通；shared member入口按ABSTRACT实际语法保存声明flag。五prod+115行fixture，无Info/backing字段/codec/Kryo注册/VERSION变更，MCFL17/bin289989不变，无stdlib/fullcheck/server。

Contract<Int/Bool> flags/抽象exchange真实参数返回绑定/noCtor/readonly完整快照/cache、canonical Box/前置签名及fresh独立模型通过；source仅模型+库，consumer实际磁盘4/9/frame0。RED fatal无fresh XML；试joint3旧73/80绿、新81因生产漏记ABSTRACT失败，补一行后最终仅新1绿，三个case跨轮各过。只static interface TypeValue及普通Box runtime int字段，不运行interface/abstract方法，不验generic继承/runtime布局/shape转换/annotations、一般abstract-final flags/defaults全集或legacy interface wrapper/Kryo全集。80提交3301770618f70ce16f79050213d106c4913f4743（18文件224+/176-）。下一82仅source磁盘导出，先单fixture RED，不顺带autoload/接口runtime/名字冲突；whole17未完成。

阶段80历史记录：generic object Settings<N>静态方法路径限定通过。ObjectCompoundData/self companion、共享generic factory/TemplateBody visitor、header-only prototype、Member/Function静态判定及稳定object FullID/有限canonical恢复已接通，删除重复ObjectFieldVisitor与即时body访问；GenericInfo不重造self companion。13prod+83行fixture，不新增Info/class backing字段、Kryo注册或TypeId codec；MCFL17/bin289989不变，无stdlib/fullcheck/server。

source/fresh N4/9两canonical对象及快照/cache.arguments、方法owner/静态this视图、独立模型/FullID保持；真实consumer反序9→4→4及磁盘4/9/4/frame0通过。RED fatal无fresh XML，试final因过强无this断言失败，仅改fixture；最终新1绿，三个不同用例跨轮各过，不是最终联合3。仅static N/runtime int返回，不验字段/init/autoload/interface、StaticMemberView字段强转或genericobject wrapper直接库fieldtype Kryo路径；local空prototype导出边界保留。79提交84c373d3f8c4179392bdf04c0ac14d3bb05dc486（12文件262+/65-）。下一81先generic interface静态TypeValue单fixture RED，未实现/测试，沿既有模型/metadata/kind、按实证定API；whole17未完成。

阶段79历史记录：在generic用户函数header用现有UnresolvedType保留自身readonly依赖类型，实例绑定后由共享resolveBoundSignature解析normal/return；候选匹配与实际特化共用，已知Type仍FullID、Unresolved签名只按Identifier token归一。freezeReadonly复用完整snapshot/types及独立CompilerOnly Symbol/Place/root/parts，类readonly等价接入；ordinary runtime参数不参与常量特化。未执行用户body、未增加Info字段/wire/schema，MCFL17/bin289989未变，无stdlib/fullcheck/server。

relay<T>(Box<(T)>)->Box<(T)>的source/fresh模型及consumer磁盘4/9/7/frame0达到：dynamic int4/9同wrapper、bool7另一个，恰好2；caller T=string影子、canonical normal/return/scopeT、独立模型、FullID/snapshot/cache arguments保持。最终必要联合5全绿；此前RED是producer14错误，试final仅Kotlin编译失败无测试/XML，不能计作测试失败。仅static T/runtime int字段，默认literal延后cast已实现但defaults全集等未验；native/extension新入口、generic object/interface和local空prototype导出仍保留边界。阶段78已提交fa29ec67549817392b86c62f435d3ee459ec7201（11文件160+/25-）。下一80仅generic object Settings<N> source/library/fresh，先单fixture RED，未实现/测试，schema按实际实现核对；whole17未完成。

阶段78历史记录：完成alias有根匿名TypeValue的限定恢复。两个visitor在真实anonymous model中转存字段注解，停止全局annotation pass错误遍历匿名body；cachedTarget/cachedAliasTargets只读已缓存目标，不触发新解析。Declaration template恢复按完整ID匹配、tryResolve后要求template===唯一，歧义诊断/null；匿名名data-N避开合法用户Identifier，未增加registry、TypeId variant或wire字段。

fixture的匿名X带@DataOnly、透明Y及捕获旧合法data_N的named声明具有不同目标/FullID；source X4→Y9、fresh Y9→X4的canonical T/scopeT、前置签名、独立模型、稳定快照和consumer磁盘4/9/frame0通过。四个不同用例跨轮各自通过，最终仅新1复查；初始fatal无fresh XML，annotation轮暴露冻结T恢复，试final轮为fixture声明顺序错误。只覆盖alias有根匿名/static T/runtime int，不实例化X，不保证匿名方法/构造器、源码重建ID稳定、全跨库碰撞或无根匿名。MCFL17/bin289989/schema不变，无stdlib/fullcheck/server；77已提交4925f67c363e0f2856bfc2e34af89441bf167665（7文件147+/23-），78提交记录见Git历史。下一79先单fixture RED验证generic函数自身readonly依赖Box<T>普通形参/返回，优先现有UnresolvedType文字placeholder与共享boundSignature，尚未实现/测试，schema按实际实现核对；whole17未完成。

以下阶段记录保留各轮当时的实现边界与待办。

阶段76一行Selector resolver保持limit/entities/isName原始身份并核对完整ID，未扩registry/schema/runtime。Selection有序原引号过滤器及bare entity null参数经前置wire签名、source/fresh canonical/不可变快照/FullID与consumer实际磁盘4/9/frame0验证；最终仅1项全绿。isName=true、empty source及entity世界/runtime未验证。MCFL17/bin289989不变，无stdlib/fullcheck/server；75提交d6dfb7f57b5844839c00a9df5261c61d9d1acd00（6文件118+/22-），76预计6文件，提交以Git历史为准。下一步77 direct Union TypeValue ordinary/readonly表达式尚未实现或测试，whole17未完成。

阶段75仅MCFPPType Applied分支恢复vector数字维度并校验FullID；原四容器元素类型工厂不变，不新增维度>0约束、registry/wire/runtime vector能力。静态Box T的vec2/vec3前置wire签名、source/fresh维度/快照/canonical/完整身份及consumer磁盘4/9/frame0经必要联合2项（新75+旧71）通过。MCFL17/bin289989/schema不变，无stdlib/fullcheck/server。阶段74提交16e1051e672ba8a6d732d09239e1d13c92d18025（7文件144+/19-），阶段73为186a712f；阶段75预计6文件，提交以Git历史为准。下一步76冻结SelectorTypeValue尚未实现或测试，whole17未完成。

阶段74新增至少一个PIPE '|'的unionType；旧UNION '&'及unionTemplateType/UnionDataTemplate不变，表达式优先级不变。新类型解析透传scope/caller，invalid项诊断null不补Any；MCFPPUnionType归一及有限TypeId.Union resolver完整身份校验已接通。typealias顺序/重复输入静态T、前置Box签名、source/fresh canonical及不可变snapshot/FullID、consumer实际磁盘4/9/frame0经必要联合4项验证。旧DataTemplate.unionTest仅语法smoke；direct readonly union expression与Union runtime值/布局仍未验。wire/schema/MCFL17/bin289989未变，无stdlib/fullcheck/服务器；阶段75冻结向量TypeValue已限定验证，必要联合2项全过；阶段76冻结SelectorTypeValue已限定验证，最终仅1项通过；阶段77 direct Union TypeValue表达式已限定验证，最终必要1项通过；下一步阶段78匿名模板alias冻结身份尚未实现或测试，whole17未完成。

阶段73两个生产文件Type/GenericType共享resolveSpecialization(id)，复用snapshot restore/prototype.compile当前target/options，核对template Declaration与最终FullID；Specialized resolver委托，tryResolve保留READ_LIB及currentcanonical路径。前置Holder<Cell<int>>真实wire、canonical T/字段/ctor/read/free签名、fresh独立模型及稳定FullID/T快照已验证；最终必要联合2项（新73+旧72）全过，source/fresh模型及consumer真实磁盘4/9/frame0。schema/MCFL17/bin289989未改，无stdlib/fullcheck/服务器。阶段72已提交ee46fba877b9af3570ddeb1b8f65f8742d22c11d（11文件197+/32-）；阶段73当前7文件含4docs，提交以Git历史为准。

阶段74已新增'|'联合类型入口；旧'&'联合模板入口及extends-all模型保持。仅typealias输入的静态Union身份与库恢复已验证，direct readonly union expression及Union runtime布局未覆盖。阶段75冻结向量TypeValue已限定验证，必要联合2项全过；阶段76冻结SelectorTypeValue已限定验证，最终仅1项通过；阶段77 direct Union TypeValue表达式已限定验证，最终必要1项通过；下一步阶段78匿名模板alias冻结身份尚未实现或测试；Selector/Opaque、generic object/interface及其余既有缺口保留。

阶段72限定路径已验证，首轮4绿+最终新方法1共5个不同用例跨轮各过，非最终联合5；实际source/fresh模型及磁盘4/9/frame0到达。阶段71提交`baa8f0704d58fcbc29706cb28181d821c9cc6138`（9文件185+/27-），阶段72已提交ee46fba877b9af3570ddeb1b8f65f8742d22c11d，11文件197+/32-。MCFL17/bin289989/schema未变，无stdlib/fullcheck/服务器。阶段73冻结Specialized类型值已限定验证，最终必要联合2项全过；阶段74源码联合类型与冻结身份已限定验证，必要联合4项全过；阶段75冻结向量TypeValue已限定验证，必要联合2项全过；阶段76冻结SelectorTypeValue已限定验证，最终仅1项通过；阶段77 direct Union TypeValue表达式已限定验证，最终必要1项通过；下一步阶段78匿名模板alias冻结身份尚未实现或测试。

阶段72新增普通expr统一出口StaticMemberView→MCFPPTypeVar及初Meta selector静态view；共享registerSnapshotTypes遍历Typed/TypeValue/Sequence/Record；readonly CompilerOnly绑定复用internal seedParts登记子facts/长度。Concrete完整已知索引复用Indexable getter，未知/不完整仍拒绝；下一selector前get中间Property保留DTO receiver，最终赋值property不提前读取。前置readBundle的真实wire签名、named types/direct [Leaf]共享canonical与fresh Bundle/Cell/Leaf不同对象/full ID稳定已覆盖，仅反转实例构造顺序。resolver仍阶段71有限支持，Union/Vector/Specialized/Selector/Opaque及generic object/interface等边界未扩大。

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
| 库字段与 Property 权限 | 仅`PropertyInfo.accessModifier`新增持久化；Var权限原已由Kryo保存，runtime adapter复制声明access/owner，StorageBinding仍Transient。访问检查使用原声明owner，`Function.accessTo`从`NoStack`/`Internal`取得词法caller。`StorageAccess.inFrame(binding, stackIndex)`创建指定帧偏移的地址view，共享data、Place与versions；旧while body共享外层frame并登记真实child，物理帧结构已修复。MCFL15真实库往返覆盖Base.private、Child.protected、object静态private及越权读取；未覆盖入口仍待迁移 |
| 来源感知的未限定字段权限 | 阶段65旧`FunctionScope.getVar(key)`只调用一次并保留raw结果、stackIndex；独立来源walk允许更近FunctionScope local，再按CompoundDataScope Property/Var声明owner检查。visitor早拒绝并返回`UnknownVar`，Concrete fallback尊重`isError` |
| 当前实例未限定字段寻址 | 来源确认普通实例`CompoundDataScope`字段且raw `getVar("this")`返回真实`DataTemplateObject`后，才调用`receiver.getMemberVar`；更近局部保持raw结果，object/static、无owner readonly、无receiver及未解析名保持原路径。阶段66 LibFieldAccess4 + ConstructorExecution7 单次11项通过，验证真实receiver及5/9/8/6字段结果；不改Internal全局lookup/putVar |
| Generic 类 readonly 源特化 | generic kind经`AbstractTemplateInfo`恢复；immutable specialization key与prototype-based `TypeId`使用完整快照，readonly值独立为CompilerOnly静态绑定，构造器恢复接受明确owner。MCFL16；最终5项及跨轮41个不同用例见verification.md |
| 受限旧帧桥 | runtime-bound且非DTO/CompilerOnly的`InternalFunctionScope` clone binding逐层±1并清`readVersion`；while进出barrier清runtime facts/cache；普通runtimeReturn创建无initializer固定目标并在callee内加载稳定register。详细边界及验证见verification.md |
| Generic 类类型实参绑定 | 未注解普通generic class在实例化后绑定T并刷新字段、constructor参数和返回签名，实例保留canonical private Var/Property owner及TypeId；producer/consumer反序恢复与磁盘验证覆盖限定路径。MCFL16，17个不同用例跨轮各自通过，非单轮联合，详见verification.md |
| 显式 generic 类型与跨库特化 | 显式readonly类型实参在原声明scope准备header并于COMPILE阶段恢复canonical prototype/cache；冻结源码实参快照保持TypeId；恢复后刷新模板参数`typeName`，使同名overload输出ID互异，参数缺失/意外参数诊断。MCFL17，builtin/formal type边界内真实库往返，19个不同用例跨轮各自通过；详见verification.md |
| 类型表达式声明作用域 | scope与真实caller透传，绑定T/N不读caller同名影子；nested字段/constructor/method参数及返回canonical和private owner经反序consumer及磁盘4/6/8/bool1/frame0限定验证。36个不同用例跨轮各自通过，MCFL17/bin289989未变，无stdlib/fullcheck/服务器；当时Declaration/Applied TypeValue留阶段71，现限定支持见下一行 |
| 冻结声明/容器类型值 | Concrete完整表达式出口归一；有限builtin/Wildcard、full Declaration和四种单参数Applied恢复；阶段71仅顶层Typed-TypeValue注册；阶段72递归登记见当前进展。普通typed模板字段unknown adapter避免晚声明空默认快照，shape校验不放宽；canonical LeafAlias/Leaf/list<int>及磁盘4/9/7/frame0验证，6个不同用例跨轮通过，MCFL17/bin289989未变 |
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
6. 将模板构造过程纳入值与位置模型。阶段55 receiver/frame、阶段56有序 RHS持久化已完成；源码构造器编译恢复声明文件/命名空间，导入构造器 transient file仍为null，词法scope缺口保留。阶段57实现 shared `prepareObjectInitializers`：annotations/signatures/inheritance ready后、用户函数 body前编译完整本地 object constructor，复用已有 guard；FieldVisitor不试算 RHS，真实 constructor `prepareBody` 单次求值并补 field/property/Symbol。typed const也登记 RHS，incoming parameters先绑定，const只作 readonly；compiler-only snapshot与T!规则独立。首轮28项27过/1测试夹具失败；修换行后定向方法1项过，未联合复跑28。详见 verification.md。阶段58已为部分受支持语法复用 PrimitiveCompiler 私有图的 Lowering/FlowAnalysis/ReturnTypeAnalysis 做纯 AST 声明绑定：不发布 IR、不执行用户函数、不生成命令；普通构造参数与未绑定 T! 保持 Unknown，同字段跨 ctor overload 要求同一 TypeId。支持 this 单字段/此前字段、anonymous 队列、已绑定 generic 实例、继承后 annotation 与参数/返回 adapter 刷新，保留 Symbol/Place。runtime receiver 按声明类型初始化可编码默认字段，Unknown erased 不伪造 snapshot，不可编码 NBT child 返回整体 null；普通模板局部声明 buildUnConcrete 防 DataOnly 空地址命令。pure AST binding 与 runtime AST generation 分离，不重复执行 RHS。native/generic/compiler-only/static、多级 this/member method 等语法仍走 legacy `extraFunction`。37个不同用例跨轮各自通过，非单次联合37项。阶段59已为导入声明持久化 `namespace` 与 `unsolvedImports`，不保存完整 FileScope/Project；includes 全部读取后，在各自声明文件的 FileScope 恢复 currFile 和声明环境；普通函数、构造器、泛型特化均经统一 compile 入口在各自环境中编译。MCFL13 标准库重建后，TemplateInitialization6、SpecializationPolicy11、IRCall17、LibCacheFormat3、TemplateFieldInference6、ConstructorExecution7 联合50项通过。阶段60新增库导出入口已导出所需的非Native bodyCompiled函数，并按namespace输出，限定磁盘测试通过。阶段61恢复普通/泛型模板方法owner及canonical scope，并恢复FunctionInfo的isAbstract；26个不同用例跨轮各自通过。阶段62修复目录/JAR/ZIP模块resourcePath与资源复制并通过11项联合验证；阶段63将普通/generic/native函数accessModifier持久化，MCFL14真实库往返14项联合全过。阶段64仅为PropertyInfo.accessModifier增加持久化；Var权限原已由Kryo保存，runtime adapter复制声明access/owner而StorageBinding仍Transient。阶段65/66限定的字段权限及当前receiver寻址已验证；imported object自动load仍待解决。
7. 将版本缓存扩展到全部实体、集合、调用帧和临时值；未知字典键尚无已验证的运行时路径转义后端，当前明确拒绝生成，map 已用字符串值和 compound 谓词避免成员名拼接；旧目标的原生成员操作、原始 nbt/其余集合仍需全面接入编码能力检查；已有标量/擦除递归样例通过不代表完整帧分配已完成，原始命令直接修改其他函数的物理记分板仍需与统一布局规划核实；删除 hasStoredInStack、trackLost、Concrete 双层体系与双成员表。
8. 阶段55–71模板、库恢复与访问权限结果见verification.md。阶段58的37项、阶段59联合50项、阶段60限定磁盘consumer 13项、阶段61跨轮26项、阶段62联合11项、阶段63联合14项、阶段64跨轮30个不同用例、阶段65联合11项、阶段66联合11项、阶段67跨轮41个不同用例、阶段68跨轮17个不同用例分别按实际轮次记录，不合并描述成一次全过。阶段64仅将PropertyInfo.accessModifier新增持久化并升级至MCFL15，Var权限既有Kryo保存；阶段65/66 MCFL15/bin286207未变。阶段66来源确认普通实例和真实this receiver后，修复受支持的未限定字段读写及nested body路径，不改变全局Internal lookup/putVar。阶段67已限定接通generic类readonly签名/源码实例化和namespace generic类别恢复；完整snapshot冻结SpecializationKey及prototype TypeId，readonly实参保持CompilerOnly静态绑定，已知完整动态局部值可用；MCFL16/bin286243真实库往返通过。阶段68未注解普通generic类类型实参限定绑定通过；阶段69显式类型/跨库canonical特化19个不同用例跨轮通过，MCFL17/bin289989。阶段70声明scope及绑定T/N限定路径36个不同用例跨轮各自通过（29+Logic6+最终1），MCFL17/bin289989未变；阶段69提交`10267379e30bd43af6c38bcd7e6a673d195a6102`，阶段70提交记录见Git历史。阶段71有限Declaration/Applied TypeValue恢复及普通typed模板字段未知adapter已通过source/fresh canonical和磁盘4/9/7/frame0验证，6个不同用例跨轮各自通过，最终必要2项复查；阶段70提交`1d593d19c71f5c42a3adb4aaf0e2dbdaae5c1b16`，阶段71已提交`baa8f0704d58fcbc29706cb28181d821c9cc6138`。阶段72完整静态类型集合限定路径已验证，5个不同用例跨轮各自通过；阶段73冻结Specialized类型值已限定验证，最终必要联合2项全过；阶段74源码联合类型与冻结身份已限定验证，必要联合4项全过；阶段75冻结向量TypeValue已限定验证，必要联合2项全过；阶段76冻结SelectorTypeValue已限定验证，最终仅1项通过；阶段77 direct Union TypeValue表达式已限定验证，最终必要1项通过；阶段78 alias有根匿名冻结身份已限定验证，四个不同用例跨轮各自通过、最终仅新1复查；阶段79 generic用户函数自身readonly依赖Box<(T)> normal/return已限定验证，最终必要联合5全绿；阶段80 generic object静态N/方法已限定验证，三用例跨轮各过、最终新1复查；阶段81 generic interface静态TypeValue已限定验证，三case跨轮各过、最终新1；阶段82 source磁盘导出限定通过、最终联合3全绿；阶段83 generated-vs-legal名字隔离限定通过、四case跨轮各过/最终新1；阶段84静态字段/显式constructor已限定通过、最终联合2；阶段85 source/fresh abstract/final flags已限定通过、MCFL18/stdlib287554及最终联合3；阶段86 actual generic父项已限定通过、MCFL19/stdlib292301及最终联合3；阶段87 `TypeUsage`普通值位置拒绝规则已限定验证；详见本文件当前状态与verification.md。阶段88–90共24个集合原生方法、阶段91共28个int/float/bool运算符已接入显式NativeCallContext并完成限定验证，分轮证据见verification.md。其余dependent类型表达式/重载等价、generic object直接库fieldtype及generic interface继承/runtime布局/shape转换/annotations、Opaque/Selector及全集身份、imported object autoLoad、未覆盖generic语法、未知range端点和浮点/混合迭代仍保留缺口。阶段64提交`bda4d5a`、阶段65提交`d181e10`；更早提交以Git历史为准。未运行完整check或服务端，整体17项迁移未完成。

在这些项目完成前，核心路径仍存在 MCFPPValue / Concrete 判断，不能宣称已经完成原方案阶段 6。
现有持久化浮点数据不会自动转换布局，完整的持久化迁移 API 仍待实现。

## 库索引

当前库索引采用MCFL格式头与版本36：在保留既有generic kind/readonly绑定、权限与类型身份后，包含阶段88–106的原生函数/运算符/转换、文本签名、delegated-int `typeAs`及Time operator信息。generic类型专用serializer仍只序列化冻结身份，不写Compiled/prototype/AST/cache或可变实参图；声明Info仍保存body AST和有序RHS。canonical参数、父类及Time签名恢复遵循各自读取阶段；版本35及更早索引要求重新编译。旧浮点用户持久化数据不会自动迁移。
集合 IR、形状事实与动态索引的 Location 属于瞬态分析数据；Function.typedIR、runtimeEffect 与 Var.storageBinding 不序列化。MCFL17的generic Type wire仅保存稳定immutable身份；泛型参数/父类canonicalization延迟到COMPILE。该generic类型serializer不写Compiled/prototype/Var或template cache图；声明元数据仍沿既有Info保存body AST和有序RHS。runtime IR与StorageBinding仍为Transient。本次不自动转换用户已有的持久化范围或浮点载荷。
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
阶段58的37个不同必要用例跨轮各自通过（TemplateFieldInference 6项先5过1fail、修复后定向最后1项通过；其余31项全过），不是一次联合37项。MCFL12 schema/bin 267356不变，未重建stdlib/fullcheck/服务器。阶段59之后MCFL13标准库为282180 bytes，六套件联合50项通过；首次标准库54个语言错误由annotation callback缺失currFile上下文导致，恢复declaration context后解决，详见[验证记录](./verification.md)。phase56 consume记录的9119条 `flatExtends`重复继承字段警告仍待清理；当时的消费端body与方法owner缺口已在阶段60/61限定解决；object自动load仍未解决。未运行完整check或实际服务端；最近完整346项仍属于提交72dc557。

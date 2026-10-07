# 类型系统续接记录（2026-10-04）

本轮从 2026-10-04 续接，首次存储/视图纵向路径通过 161 项测试。阶段54已提交`107e8ac`，阶段55已提交`4b5b7bd`，阶段56已提交`0fb7cb8`，阶段57已提交`3865673`。阶段58普通模板inferred字段已接入部分声明绑定支持并提交`e6f83fe`，37个不同必要用例跨轮各自通过。阶段59导入声明环境恢复与MCFL13重建后，50项指定测试联合通过，提交`6200561`。阶段60 consumer函数体导出限定回归13项联合通过，提交`3468341`。阶段61的26个不同用例跨轮各自通过。阶段62目录/JAR/ZIP资源复制与模板初始化联合11项通过。阶段63 MCFL14库权限往返联合14项全部通过，提交`1918db9`。阶段64 MCFL15字段/Property访问权限与while帧修复后，最终定向7项全部通过，提交`bda4d5a`。阶段65字段权限联合11项全过；阶段66当前实例未限定字段寻址的LibFieldAccess4 + ConstructorExecution7联合11项全过，MCFL15/bin286207未变。阶段67 generic类readonly源码特化及namespace类别恢复已验证，MCFL16/bin286243；41个不同用例跨轮各自通过。阶段68未注解generic类T绑定限定语法已验证，17个不同用例跨轮通过，MCFL16/bin不变，已提交`adf50f8968483c77fcca7a482e37a2eef8eae871`。阶段69显式generic类型及跨库canonical特化限定验证通过，MCFL17/bin289989，19个不同用例跨轮各自通过。阶段69已提交`10267379e30bd43af6c38bcd7e6a673d195a6102`。阶段70六个生产文件和一个fixture限定验证通过，36个不同用例跨轮各自通过，提交记录见Git历史；MCFL17/bin289989未变，无stdlib/fullcheck/服务器。阶段70已提交`1d593d19c71f5c42a3adb4aaf0e2dbdaae5c1b16`；阶段71四prod+fixture有限类型值路径6个不同用例跨轮各自通过，最终必要2项复查，提交记录见Git历史，MCFL17/bin289989未变。阶段72限定静态类型集合路径跨轮5个不同用例各过，磁盘4/9/frame0；阶段73最终必要联合2项全过，模型及磁盘4/9/frame0；阶段74必要联合4项全过，source/fresh模型及consumer磁盘4/9/frame0；阶段75必要联合2项全过，source/fresh模型及consumer磁盘4/9/frame0；阶段76最终单方法通过，source/fresh模型及consumer磁盘4/9/frame0；阶段77最终必要1项通过，source/fresh模型及consumer磁盘4/9/frame0；阶段78 alias有根匿名冻结身份限定通过，四个不同用例跨轮各自通过，最终仅新1复查；阶段79 generic用户函数自身readonly依赖签名已限定验证，最终必要联合5全绿、磁盘4/9/7/frame0通过；阶段80 generic object静态N/方法已限定验证，三用例跨轮各过、最终仅新1，consumer磁盘4/9/4/frame0；阶段81 generic interface静态TypeValue已限定验证，三个case跨轮各过、最终仅新1；阶段82 source generic特化磁盘导出已限定通过，最终必要联合3全绿、source磁盘4/9/4/9/frame0；阶段83 generated-vs-legal名字隔离已限定验证，四case跨轮各过、最终新1；阶段84 generic object静态字段/显式constructor初始化限定通过，最终联合2全绿，source4/9/4/9、fresh9/4/9/4/frame0；阶段85 abstract/final flags已限定通过，MCFL18/stdlib287554/最终联合3全绿；阶段86 actual generic父项限定通过、MCFL19/stdlib292301/最终联合3；阶段87 `type` 仅用于泛型参数已实现并限定验证，提交`bde5a25`；阶段88 list.clear显式上下文迁移及MCFL20已限定验证，三项回归通过；阶段89剩余10个list native方法接入显式上下文，MCFL21/bin292007，三个不同用例跨轮各自通过、最终仅失败fixture复查1项。阶段90已将Dictionary/Map/ImmutableList共13个方法接入context并限定验证；阶段91的28个int/float/bool运算符入口及阶段92的44个byte/short拒绝注册删除均已限定验证，当前库格式MCFL24；imported object自动load仍独立未解决。最近完整346项仍属于`72dc557`；整个17项重构未完成。

## 当前进度

### 阶段 125：Player 状态与骑乘命令（已限定验证）

`PlayerVarData.clear()`、`clear(string,int)`、`setGamemode`及`EntityRideData.ride`接入单context；clear语言重载、EnumVarConcrete标识符和双方single-selector guard均按限定合同处理。stdlib12s/Project0/0；joint worker47正常、SUCCESSFUL in25s。LibFieldAccess XML `2026-10-07T05:58:41.992Z`、Cache XML `05:58:41.069Z` 均1/0/0/0；source/fresh Project0/9118与0/9119，negative12/9119（2个player guard、1个enum guard、3个ride receiver/target guard及6个未发布结果级联诊断）。三份bin MCFL55、476213 bytes、header `4c46434d37000000`、SHA256 `09DC39D48459E09CCC2B09BFD8184EC0A036670AF74362B6A382768D7958614D`。source/fresh检查动态clear参数宏与三个direct命令，不模拟world或frame0。

### 阶段 124：Player 成就命令（已限定验证）

`PlayerVarData`十个grant/revoke入口接入单context qualified `CommandResult`，新`PlayerAdvancementData`挂到`EntityData`；使用qualified `Advancement`及真实`ResourceID.id`，capture前检查player selector。stdlib11s/Project0/0；joint worker46正常、SUCCESSFUL in26s。LibFieldAccess XML `2026-10-07T05:43:00.220Z`、Cache XML `05:42:59.387Z` 均1/0/0/0；source/fresh Project0/9118与0/9119，negative4/9119（两个非玩家guard及grantAll/revokeAll未发布结果）。三份bin MCFL54、473046 bytes、header `4c46434d36000000`、SHA256 `E2084436826232344C6DE71EC93211F4B5F409E0C5065C2BFF8DF8ECE155F1BB`。source/fresh检查8个动态id宏和2个everything direct命令，不模拟world/criterion或frame0。

### 阶段 123：Player XP 命令（已限定验证）

`PlayerVarData`六个XP入口使用单context qualified `CommandResult`；新增`PlayerXpData`挂到`EntityData`。add/set采用multiple-player合同，query限制single player；capture前验证Player selector。stdlib12s/Project0/0；joint worker45正常、SUCCESSFUL in26s。LibFieldAccess XML `2026-10-07T05:29:58.371Z`、Cache XML `05:29:57.554Z` 均1/0/0/0；source/fresh Project0/9118与0/9119，negative6/9119（3条selector guard及3条未发布结果级联诊断）。三份bin MCFL53、459939 bytes、header `4c46434d35000000`、SHA256 `D6DC92AA4C19913AFBD97C11F6608D4F42FBB9A0854A5E78FA3B36E2AE13D46D`。仅验证四个动态参数宏和两个直接query的生成/捕获，不模拟世界XP值或frame0。

### 阶段 122：停止骑乘命令（已限定验证）

`EntityRideData.stopRide`使用单context结果捕获；仅接受已证明为单实体的selector，DTO与多实体selector在capture前拒绝。stdlib10s/Project0/0；联合worker44正常、SUCCESSFUL in24s。LibFieldAccess XML `2026-10-07T05:14:47.255Z`、Cache XML `05:14:46.352Z` 均1/0/0/0；source/fresh Project0/9118与0/9119，negative4/9119（两场景各有selector guard及未发布结果诊断）。三份bin MCFL52、455477 bytes、header `4c46434d34000000`、SHA256 `71BC9EF7BE9E5257B52242542FF6FB431D64EF725CC6C75DF2C27DC768C84D5B`。仅验证生成命令和负向guard，不涉及世界骑乘、frame0或whole17完成。

### 阶段 121：Entity effect 授予命令（已限定验证）

`EntityVarData.effect`与`effectInfinite`接入单context qualified `CommandResult`。bool常量直接用true/false字面量；动态score按当前score读取，未知bool在MCString临时值写入`false`并在条件成立时覆写`true`。不称MNI默认参数已接通，也不外推到Minecraft世界效果。

标准库SUCCESSFUL in10s、Project0/0；joint worker43正常、SUCCESSFUL in25s。LibFieldAccess XML `2026-10-07T04:59:00.799Z` 与CacheFormat XML `04:59:00.083Z` 均1/0/0/0；source/fresh Project0/9118与0/9119，DTO negative2/9119。fixture验证限定source/fresh命令及bool准备；不验证完整返回行为或帧恢复。三份bin均MCFL51、454764 bytes、header `4c46434d33000000`、SHA256 `5BD4DF48FD5BD8553AB4D5D091566E2187F7A2D22F39D69E1017A01D752E6FD1`。

### 阶段 120：EntityEffect 命令结果接口（已限定验证）

`EntityVarData.clearEffect`与`clearAllEffects`接入单context qualified `CommandResult`；读取真实`Effect.id`并在捕获前验证Selector。fixture验证一个specific-effect macro、一个all-effects direct命令、完整参数副本来源、两个独立结果root/双store/一次初始化及DTO负例；仅限受测路径。

标准库SUCCESSFUL in10s、Project0/0；joint worker42正常、SUCCESSFUL in25s。LibFieldAccess XML `2026-10-07T04:42:55.557Z` 与CacheFormat XML `04:42:54.761Z` 均1/0/0/0；source/fresh Project0/9118与0/9119，negative2/9119，诊断为selector receiver guard及未发布`clearAllEffects`结果。三份bin均MCFL50、451715 bytes、header `4c46434d32000000`、SHA256 `D09790EA77B67656159415475C1E9B9355D871D39C8218D7278D6E4FCAC01D9B`。不涉及world、Executor或frame0。

### 阶段 119：EntityTeam 命令结果接口（已限定验证）

`EntityTeamData.joinTeam`/`leaveTeam`使用单context qualified `CommandResult`。join使用真实`Team.id`并先于`@a`参数捕获；leave不再带旧的伪Team参数。单fixture检查普通参数的整块DTO副本至id宏slot及单次调用、两个结果root/双store/单次初始化、fresh consumer与DTO负例。

stdlib SUCCESSFUL 11s、Project0/0；joint worker40正常、FAILED19s，LibFieldAccess XML `2026-10-07T04:24:52.599Z` 1/1/0/0，失败于source datapack命令准备断言（Team.id宏参数未按期出现），未到fresh或negative；Cache XML `04:24:51.843Z` 1/0/0/0。改为断言完整DTO副本来源后仅复查fixture：worker41正常、SUCCESSFUL21s，XML `2026-10-07T04:28:36.594Z` 1/0/0/0，source/fresh Project0/9118与0/9119，negative2/9119。三份bin MCFL49、449537 bytes，header `4c46434d31000000`，SHA256 `4ED10AC79FF327A131A85E1FBDC5205A0807E0BBD3E83A38373331B536EEC2B1`。不涉及world执行、frame0或whole17完成。

### 阶段 118：EntityTag 命令结果接口（已限定验证）

`EntityVarData.addTag`、`removeTag`、`listTag`接入单context qualified `CommandResult`。命名空间解析仅支持`ENTITY`片段；bare `SelectorVar`的immutable快照按真实kind与完整typeId恢复，MNI仅在显式qualified解析失败时尝试已加载canonical模板。predicate selector等路径仍未覆盖。

stdlib1成功11s/Project0/0；首轮producer因`ENTITY`保留字导入解析失败，Cache XML `2026-10-07T03:22:31.233Z` 1/0/0/0为MCFL48唯一Cache验证。grammar修复后stdlib2成功26s/0/0；final2 producer189/9118，final3 producer9/9118。final4重复执行并覆盖，当前仅保留worker37、FAILED6s及XML `2026-10-07T03:52:30.253Z` 1/1/0/0、producer8/9118；worker36首轮日志/XML已丢失。final5-debug worker38 FAILED28s，XML `2026-10-07T04:04:19.204Z` 1/1/0/0、producer8/9118，诊断显示bare Selector绑定后value仍为Unknown。最终修复后final6 worker39正常、SUCCESS39s，XML `2026-10-07T04:08:58.285Z` 1/0/0/0；source/fresh Project0/9118与0/9119，negative 2/9119，selector guard与未发布`listTag`结果诊断按预期通过。未覆盖predicate selector、world执行、frame0或whole17。

当前状态更新：2026-10-07（Asia/Shanghai）。阶段125 Player状态与ride命令已完成限定验证，MCFL55；阶段124 Player advancement十个入口已完成限定验证，MCFL54；阶段123 Player XP六个入口已完成限定验证，MCFL53；阶段122 stopRide入口已完成限定验证，MCFL52；阶段121 Entity effect授予两个入口已完成限定验证，MCFL51；阶段120 EntityEffect两个命令入口已完成限定验证，MCFL50；阶段119 Team加入/离开两个命令入口已完成限定验证，MCFL49；阶段118 EntityTag三个结果入口已完成限定验证，MCFL48；阶段117 Random命令结果入口已完成限定验证，MCFL47；阶段116 WorldObject命令结果入口已完成限定验证，MCFL46；阶段115 BossBar命令结果入口已完成限定验证，MCFL45；阶段114 Worldborder命令结果入口已完成限定验证，MCFL43；阶段113 Op/Recipe玩家命令入口已完成限定验证，MCFL42；阶段112 Team receiver结果入口已完成限定验证，MCFL41；阶段111 Datapack/Debug结果入口已完成限定验证，MCFL40；阶段110提交`17313ad`，阶段109提交`7806fab`，阶段108提交`122d608`，阶段107提交`b4eb5c9`，其余提交状态以Git历史为准。阶段87普通值位置 `type` 拒绝规则继续生效；当前Std外剩余4个Java类、22个CommandReturn注解及7个旧void wrapper，whole17仍未完成。重构不要求兼容旧 `.mcfpp` 写法。

### 阶段 117：Random命令结果接口（已限定验证）

`RandomData.reset`、`RandomObjectData.reset`与`resetAll`三个入口接入单context qualified `CommandResult`；flags用完整`CompilerValue.Bool`快照，seed int保留。修正实例/source flag顺序、readonly实参传递、raw宏调用导出及`Random.mcfpp`三个旧命令行尾斜杠；测试helper现保留datapack异常原因。单参构造器是唯一实际source/fresh覆盖的构造器路径。

stdlib1 exit0/10s、Project0/0。final1 worker26崩溃18s，XML仍为阶段116旧记录，不能据此计算Cache；final2 worker27正常、FAILED14s，LibFieldAccess XML `2026-10-07T02:48:48.594Z` 1/1/0/0，Cache XML `02:48:47.775Z` 1/0/0/0（MCFL47唯一Cache验证）。source Project0/9118后datapack导出失败，fresh consumer尚未运行。final3 worker28 FAILED10s，宏调用未展开；final4 worker29 FAILED13s，构造器raw命令末尾斜杠断言失败。stdlib2 exit0/4s、Project0/0；final5 worker30 FAILED16s，正向通过而negative仍计数不符。final6仅fixture复查，worker31正常、BUILD SUCCESSFUL in21s，XML `2026-10-07T03:08:42.235Z` 1/0/0/0；source/fresh Project0/9118与0/9119，negative Project2/9119含完整编译期值错误及级联符号错误，按更新后的预期通过。三份资源MCFL47、296252 bytes、SHA256 `8D569CF61A35D3D7A7D6371C7C6E6FD4F3AFDE3C09948FD2BBD3BBA2C244547B`。无fullcheck/server/world/frame0；whole17仍未完成。

### 阶段 116：WorldObject命令结果接口（已限定验证）

`WorldObjectData.setDifficulty`与`setWeather`两个静态native方法接入单context qualified `CommandResult`，无caller参数、default或readonly参数；capture前要求EnumVarConcrete，keyword使用`value.identifier`，weather duration为int。旧Time缓存及weather/time accessor/mutator未改，生产尚未导出World对象。source/fresh fixture用真实`@From FixtureWorld`检查两个canonical readonly/unknown结果及null snapshot、difficulty direct和weather macro；duration实际按NBT读取→score复制→NBT编码→macro slot捕获。

stdlib exit0/9s、Project0/0；首次联合两项 exit0/24s、worker25正常。LibFieldAccess XML `2026-10-07T02:30:25.319Z` 与Cache XML `02:30:24.427Z`均1/0/0/0；source/fresh Project0/9118、0/9119，negative Project4/9119为预期guard及未发布诊断。实际命令合同、双store、一次root初始化及无裸命令/return-run断言通过；不涉及world执行、executor或frame0，也不据注解推断可省略参数。三份bin均MCFL46、295914 bytes、header `4c46434d2e000000`、SHA256 `8D0FFE6F3302B53F99849D9978C38D30D05DBF067EA7DFE42CF3376E256EEB8F`。

截至阶段116，剩余6个Java类、55个CommandReturn注解及7个旧void wrapper；whole17保持ACTIVE。

### 阶段 111：迁移 Datapack 与 Debug 结果接口（已限定验证）

Datapack九个和Debug.start/stop两个命令入口接入单context；Debug.function旧TODO不动。共享captureCommandResult从Std抽出，Std的107处调用只更名并删除旧helper/import，行为保持不变。Kryo嵌入Info跨库后fresh模板身份不一致，经 `MCFPPDataTemplateType.tryResolve` 按完整Declaration身份复用已加载namespace的真实template/interface并同步parentType；不改Info、serializer、wire或context。fixture用真实 `@From Packs`、显式native profileStart/profileStop和普通Box.observe验证source/fresh。

首轮stdlib exit0/11s、Project0/0；joint3 worker13正常但1失败，XML `2026-10-07T00:50:12.136Z` tests2/fail1，source检查通过但fresh canonical检查失败；Cache XML `00:50:11.575Z`已通过。修复后stdlib2 exit0/9s、Project0/0；final2仅两个LibFieldAccess fixture，worker14正常、exit0/27s，XML `00:56:50.658Z` tests2/fail0。source/fresh Project 0/9118、0/9119各两次。consumer实际有三个direct命令及enable动态macro准备/调用、root `{}`与双store；无world/executor/frame0。三份MCFL40资源293272 bytes、SHA256 `04023951C6DA76A2DB1C3D0BAB6528A8387D0D8CD5BC92A949B695E390ABAC9A`。Cache只首轮通过。



### 阶段 115：BossBar命令结果接口（已限定验证）

BossBar七个实例方法接入单context qualified `CommandResult`；guard仅接受EnumVarConcrete，fixture使用literal；unknown enum在capture前拒绝，entity仍走SelectorVar玩家限定。DTO保留既有id:string并新增name:text，单参id构造器用id.toText初始化name，二参构造器为(string,text)。Java `list` 改名`listAll`避开保留字，命令仍为bossbar list。EnumMemberInfo持久化enum数据的SNBT值并在读取时重建Tag。

首轮stdlib1 exit0/10s、Project0/0；joint1 exit1/16s、worker22正常，LibFieldAccess XML `2026-10-07T02:09:58.178Z` 为1/1/0/0，source语法3错误来自保留字`bar.list()`；Cache XML `02:09:57.302Z` 为1/0/0/0。改用listAll后stdlib2 exit0/8s、Project0/0；final2仅fixture复查exit1/17s、worker23正常，XML `2026-10-07T02:13:54.671Z` 1/1/0/0，source0/9118但enum输出为null，fresh未到。加入EnumMemberInfo持久化后MCFL45；stdlib3 exit0/8s、Project0/0，final3仅fixtureexit0/24s、worker24正常，XML `2026-10-07T02:17:15.845Z` 1/0/0/0，source/fresh Project0/9118与0/9119，negative Project4/9119为预期拒绝。Cache只在MCFL44首轮通过，未声称MCFL45有Cache回归。三份资源MCFL45、295914 bytes、header `4c46434d2d000000`、SHA256 `79037281AAB839306736EED308260D0D9550C965B8308F594C9468A60F67A003`。fixture通过模型、snapshot与命令导出断言；未验证world/executor/frame0、旧getter/mutator或静态list。

截至阶段115，剩余7个Java类，57个CommandReturn注解及7个旧void wrapper；BossBar旧getter/mutator未迁，whole17保持ACTIVE。

### 阶段 114：Worldborder命令结果接口（已限定验证）

WorldborderData的add、setCenter、setDamageAmount、setDamageBuffer、setSize、setWarningDistance、setWarningTime七个方法接入单context qualified `CommandResult`；保留普通参数与注解/default/词序，复用 `FloatProviders`。仅计算型Pos2能力标志`hasRuntimeRepresentation=false`，不增加serialized backing field或wire。fixture检查七个结果、readonly/unknown模型和严格参数domain；consumer实际生成一个direct center命令、六个macro、七个独立root/双store/一次初始化。amount的float snapshot从真实binding path捕获；ticks按加载→score复制→NBT编码进入macro参数槽，不能简写成宏直接读取形参。

stdlib exit0/BUILD SUCCESSFUL in34s、Project0/0。首轮joint exit1/BUILD FAILED in30s、worker19正常；LibFieldAccess XML `2026-10-07T01:49:16.349Z` 为1/1/0/0，producer因Pos2字面量被错误送入NBT编码而报1 error/9118 warnings，failure栈在 `NativeFunction.invoke → StorageAccess.flush`，尚未到命令检查/fresh；Cache XML `01:49:15.410Z` 为1/0/0/0。计算型能力标志修复后，final2仅fixture复查仍在source命令断言失败，XML `2026-10-07T01:53:13.213Z` 为1/1/0/0，producer0/9118、fresh未到。fixture改为逐段检查真实float snapshot路径及ticks加载、复制、编码和macro slot后，final3 exit0/BUILD SUCCESSFUL in18s、worker21正常，XML `2026-10-07T01:56:02.846Z` 为1/0/0/0；source/fresh Project0/9118和0/9119。Cache未重跑。三份资源MCFL43、293933 bytes、header `4c46434d2b000000`、SHA256 `6307C3AFB83610B950BD3014DD4A97850542EA0ACB579908B358677AB6033EF1`。未验证world/frame0、size accessor/mutator或WorldObjectData；默认参数不据注解推断可省略。

阶段87普通值位置 `type` 规则继续生效，whole17保持ACTIVE未完成。

### 阶段 113：Op/Recipe玩家命令结果（已限定验证）

Op.op/deop、Recipe.give/take/giveAll/takeAll六个player-target入口接入单context qualified `CommandResult`，entity是普通参数。capture前通过SelectorVar的onlyIncludingPlayers证明玩家限定，保留clone与筛选查询；裸@s及反向player筛选被拒。Recipe从DTO真实id读取值；修正Op.deop使其生成deop。source/fresh fixture含正向、static EntityType、克隆及反向query，以及四个负向调用预期拒绝和不发命令检查。

stdlib exit0/10s、Project0/0；联合2 exit1/21s、worker17正常。LibFieldAccess XML `2026-10-07T01:36:37.507Z` 为1/1/0/0，失败于source check调用`EntitySelector('e').type("minecraft:player", false)`导致 `EntitySelector.type` 内部NPE，尚未进入fresh consumer；CacheFormat XML `01:36:36.770Z` 为1/0/0/0。修复内部EntityType工厂改为查询已加载canonical namespace后，只复查该fixture：exit0/18s，worker18正常，XML `2026-10-07T01:39:33.364Z` 为1/0/0/0。source/fresh Project 0/9118、0/9119；负向8条预期诊断（4个selector guard及4个native result未发布）与无命令断言通过。stdlib/Cache未重复运行。MCFL42三份bin为293933 bytes、header `4c46434d2a000000`、SHA256 `1E700715D1CFB0F20C83B6B07954E39AB68B1FE42B4C9F105CB5028651B8C956`。验证导出命令，不涉及world执行或frame0。

阶段87普通值位置 `type` 规则继续生效，whole17保持ACTIVE未完成。

### 阶段 112：Team receiver命令结果（已限定验证）

`Team.register`/`unregister`/`clear`三个receiver方法接入单context qualified `CommandResult`；现有 `withAdapters` 从DTO读取真实id/displayName字段并解包PropertyVar，复用共享结果捕获。`Team.mcfpp`显式导入标准库结果类型。9个MNIMutator和其他12个Java类/7个旧void wrapper不因本次普通字段路径而视作已迁移。

首轮stdlib exit1/BUILD FAILED in11s、Project3/0，三个Team返回类型均因缺少标准库import而报Invalid qualified type，未启动测试。加import后stdlib2 exit0/BUILD SUCCESSFUL in4s、Project0/0。首轮joint exit1/BUILD FAILED in19s；LibFieldAccess XML `2026-10-07T01:14:09.452Z` 为1/1/0/0，失败位于producer宏准备断言，未到fresh consumer；Cache XML `01:14:08.720Z` 为1/0/0/0。原fixture错误要求displayName宏准备，改为严格检查NBT chat component及实际NBT地址后，final2仅复查Team方法：exit0/BUILD SUCCESSFUL in22s、worker16正常，XML `2026-10-07T01:20:15.674Z` 为1/0/0/0，source/fresh Project为0/9118与0/9119。id路径检查macro准备、唯一调用、双store及root初始化；未验证world、执行器或frame0。Cache只首轮通过。三份bin均MCFL41、294070 bytes、header `4c46434d29000000`、SHA256 `41E4F07C70C24D914114C881FEF85A7CFE73F7E364A3F5FA6B0055BBA4CBAADE`。

阶段87的普通值位置 `type` 拒绝规则继续生效；whole17仍ACTIVE未完成。

### 阶段 110：迁移 StdCommands 命令结果接口（已限定验证）

StdCommands剩余106个旧CommandReturn入口迁到单context；127个Java入口现在均为单context，107个返回qualified CommandResult、20个void。root对比127个语言签名（除返回类型转换）和106条新增命令表达式，映射及文字顺序一致；旧参数名在测试前修正。私有captureResult使用实际declaredReturnType，建立root `{}`和双store，按宏准备顺序单次调用再publish；未知float使用原guard。source/fresh只覆盖seed、help、动态say的模型和导出命令，无世界验证。

stdlib Project0/0、BUILD SUCCESSFUL in11s；joint2 worker12正常、exit0/BUILD SUCCESSFUL in22s。LibFieldAccess/CacheFormat XML分别`2026-10-07T00:38:18.068Z`和`00:38:17.277Z`，均1/0/0/0；source/fresh Project0/9118与0/9119。MCFL39三份资源286624 bytes、SHA256 `A2348C93E75C319D2D6E1B99F62E21A011B4BE7EFA14732C53241442693B1FBD`。没有world、ScoreCommandExecutor或frame0验证。StdCommands外仍有14个Java类（91个CommandReturn注解和7个void wrapper）尚未迁移。

下一阶段111限于Datapack九个方法及Debug.start/stop两个方法，待处理的静态类目前无导出语言对象；Datapack计划以 `@From` 的Packs对象验证，Debug沿现有native声明。FunctionVar/TODO和Op/Recipe的Player!参数合同另列后续；已知 `Op.deop` 构造op命令，后续修正并测试，不声称已修。计划MCFL40；尚未实现或验证。

### 阶段 109：捕获 seed 的命令结果（已限定验证）

普通 `CommandResult` 以canonical声明提供只读 `result:int`/`success:bool`；seed单context按真实declaredReturnType建未知事实，通过root `{}`并在一次命令中双execute-store发布。fixture检查source/fresh模型及实际consumer磁盘合同，无世界执行或frame0结论。`MCFPPFile.runCommand()`使用声明context后标准库重建成功；首轮对字段知识的fixture断言过细，改为四个实际读取变量存在，且它们各自的snapshot均为空后，final2 fixture通过。仅Cache首轮通过，未在此轮联合复跑。

标准库Project0/0、BUILD SUCCESSFUL in13s；final2 worker11正常，BUILD SUCCESSFUL in16s，fresh XML `2026-10-07T00:18:41.752Z` 1/0/0/0，source/fresh Project0/9118和0/9119。consumer文件含root `{}`、单一 `run seed`、同条result/success双store与字段读取路径。MCFL38三份资源291766 bytes、SHA256 `9E44A36392EB129EB3BBF5E9B0C2066EE88941232135543898CFE45AFFB27F3C`。未模拟世界结果，不声称frame0；其他CommandReturn接口仍保留旧实现。

下一阶段110限于StdCommands剩余106个CommandReturn入口，保留标识/defaults/effects/词序，复用单context与seed，宏按准备命令后最终调用一次。fixture将检查seed、nonmacro和动态say macro的导出、readonly与unknown facts；必要Cache回归，不模拟world/frame0。计划MCFL39及stdlib重建；尚未编码或验证。

### 阶段 108：System 诊断接口（已限定验证）

删除无合法普通 `type` 返回消费路径的 `System.typeOf`，debug/info/warn/error四个void方法改为显式context并标记 `NoExternalWrites`。编译期concrete内容与runtime adapter宿主 `toString` 诊断语义保留，不转成runtime NBT。stdlib exit0/BUILD SUCCESSFUL in10s、Project0/0；joint两项exit0/BUILD SUCCESSFUL in28s、worker8正常。LibFieldAccess/CacheFormat XML分别为 `2026-10-06T23:59:35.568Z` 与 `23:59:34.861Z`，均1/0/0/0；source/fresh Project为0/9120和0/9121，末尾负向Project 1 error为预期literal error。磁盘返回值7与frame0通过。MCFL37三份产物289476 bytes、SHA256 `DEC8A22394F86A1C7D1D4BCC6828871C25BBF8FDAB639EA52E6858BE773AE657`。仅此两方法验证，未跑旧greens、fullcheck/server。

下一阶段109计划为普通 `CommandResult` 声明只读 `result:int`/`success:bool`，使用真实declaredReturnType和单context绑定未知结果，通过root `{}`与一次双execute-store发布；具体磁盘命令合同及readonly/unknown facts由source/fresh检查，不模拟世界结果或声明frame0。其他CommandReturn入口仍保留旧实现；计划MCFL38及stdlib重建，尚未编码或验证。

### 阶段104：System print原生入口（已限定验证）

九种print入口接入显式context；合法 `list<*>`/`dict<*>` 通过既有native pattern桥接，闭合pattern codec只保存既有集合名，native签名加载不构造pattern的ScopeVar。删除 `printVar` 及DTO输出TODO占位。历轮stdlib/fixture失败、修复及最终证据见verification.md；最终仅fixture复查通过，Cache首轮独立通过。source检查10个tellraw，consumer成功导出相关main/初始化/observe文件；未用ScoreCommandExecutor，无返回值/frame清理、macro/world执行或渲染结论。阶段104的bin为MCFL33、285692 bytes，三份SHA256一致。

### 阶段105：delegated-int模板基础（已限定验证）

`Seconds as int` 的普通参数初始化、默认构造与dispatch已接通；typed copy保持独立Place，`as int`视图共享Place，并恢复immutable typed snapshot/restore。阶段105的source/fresh磁盘最终结果494、frame0断言通过，4/9为中间读数；三份MCFL34 bin为285804 bytes且SHA256一致。细节见verification.md。

### 阶段106：Time显式原生接口（历史；已限定验证）

Time的18项运算/factory入口迁入单context；declaredReturnType由真实callee提供，Time结果使用独立Place，六倍率不变并移除静态factory缓存。MCFL36后final3仅复查fixture通过，source/fresh分别0/9125与0/9126，结果1027809和frame0断言通过；细节见verification.md。

### 阶段107：动态分支中的局部值读取（历史；已限定验证）

score缓存owner使用实际`Function`对象identity、`Pair.first ===`及`IdentityHashMap`；动态if跳转前由父Function flush可见runtime bindings。仅覆盖两个legacy分支入口，不代表CFG/loop或全量facts迁移。首轮仅因StorageAccess import缺失编译失败，无worker/XML；final2 worker7正常、exit0/BUILD SUCCESSFUL in50s，XML `2026-10-06T23:53:37.602Z` 为1/0/0/0，source/fresh分别0/9118和0/9119，磁盘10/7及frame0通过。MCFL36/bin未变；未跑stdlib、Cache、fullcheck或server。

### 用户最新规则（2026-10-06）

`type` 仅能作为泛型参数；普通 typed/inferred/const 变量、data/object 字段、普通参数与返回值，以及擦除值和集合中的 `TypeValue` 均拒绝。`typealias`、内部 `TypeVar` 解析和现有 readonly 泛型绑定保留；普通值位置一律拒绝。

### 阶段 87：已实现并限定验证

`TypeUsage` 统一判定接入源码声明入口、已绑定普通签名、IR/擦除值与集合、延迟字段。依赖普通 `type` 存储的旧30个正例已撤回，4个合法的直接泛型类型表达式库往返 fixture 保留；9个新规则方法与相关回归跨轮各自通过。验证分轮及失败修复细节见 `verification.md`：最终5项定向复查全过，不能描述为单次全套通过。底层以runtime carrier保留存储形状，但不改变 `T!` 语言类型或放宽常量要求。MCFL19/bin292301未变，无stdlib/fullcheck/server。

### 阶段 88：list.clear 显式调用上下文（已限定验证）

36行 `NativeCallContext`公开Function、receiver的ValueRef/Place、当前immutable CompilerValue快照及通用 `writeReceiver(CompilerValue)`；private Var桥执行显式 `function.runInFunction` 和 `StorageAccess.restore(actualType,payload,...,binding.types)/write`。Java clear改为单context，NativeFunction使用真实invocationArgs，CompoundData识别精确单context签名，其他旧native ABI不变。33行新fixture从真实模板实例owner调用reset内clear/add；source及fresh库consumer均执行磁盘mcfunction得到7，frame0。MCFL19→20，stdlib与资源头 `4c46434d14000000`，292301 bytes，SHA256 `9FC7D934242FEABE333BDFEFCFD5AE128CA306CDC97D8DC841BEACEE734F51AA`；三产物一致。首轮stdlib compileKotlin因List推断过窄失败，类型注解后stdlib2成功，独立测试调用才同步build resource。final worker183 exit0/BUILD SUCCESSFUL in23s，Cache/LibField/ListMember三项分别fresh 16:48:53.890Z、16:48:54.610Z、16:48:59.413Z全绿；source0/9118 warnings、fresh0/9119、IR0/9118，均为已知重复flatExtends warning。未fullcheck/server；整体17项仍未完成。

### 阶段 89：其余 list native 方法（已限定验证）

其余10个 `list` 原生方法已迁入与 `clear` 共用的显式 `NativeCallContext`：普通参数按值/位置绑定，返回值与返回位置使用实际调用结果，Java API不暴露 `Var`/`ValueWrapper`，领域逻辑仍在 `ListOperations`。`NativeFunctionInfo` 中的 methodString ABI升级至MCFL21，标准库重建后资源292007 bytes，三份产物SHA256为`4E97F3CBAB14B1EF3A89991DEC1722121768A113AF1D243DFC9ABC7DE498561F`。`Commands.tempFunction` 两个重载现在沿用父namespace并注册到既有canonical namespace，借助 `runInFunction` 恢复上下文，生成后标记 `bodyCompiled` 以便导出。

验证分轮完成：Cache、ListMember与新列表往返fixture三个不同用例均各自通过；最终仅复查失败fixture，XML `2026-10-06T17:31:52.308Z` 为1/0/0/0，worker正常、BUILD SUCCESSFUL in18s。source为0 errors/9118 warnings，fresh consumer为0/9119；结果与frame0断言从consumer磁盘函数执行。首轮consumer曾因tempFunction namespace NPE报8 errors，根因修复后用例通过。未跑fullcheck/server；本阶段没有统一其他MNI或operator。

### 阶段 93：数值转换显式调用上下文（已限定验证）

ConversionData的49个静态入口（36数值转换、13个toNBT）迁入context。支持nullable receiver与 `withArguments`，NF按declaredVoid/static构造，Java唯一方法名仍以原MNIFunction.identifier保留语言别名；Namespace/CompoundData/FieldVisitor匹配有效标识，`NumericConversions` 发布实际结果引用；ABI迁移不表示全部转换均可执行，unsupported诊断保持。MCFL24→25，stdlib Project0/0、三份291070-byte bin的SHA256均为 `D41A947D59084FA3A36D66B940988FF4CD19CA087D54947E5B336B1DADE76832`。

验证分轮：首轮worker194失败于fixture局部名 `text` 保留字；final2 worker195在producer发现 `toNBT(string)` 返回引用仍为String，consumer未运行。final3 worker196 exit0/BUILD SUCCESSFUL in17s，fresh XML `2026-10-06T19:45:12.763Z`为1/0/0/0，producer0/9119 warnings、fresh0/9120；磁盘结果-1、17、ByteTag 1、String `hello`、NBT `{value:7}`及frame0通过。Cache与ConversionIR此前已通过，三个不同用例跨轮各过；final3仅fixture复查，不是联合3项。typed-view修复保留payload与地址、不添加String→NBT隐式转换；无fullcheck/server。

### 阶段 93 自检

| 维度 | 评分 | 证据与改进 |
| --- | --- | --- |
| 准确性 | 4/5 | 明确记录两轮fixture失败边界、final3 fresh XML及MCFL25三份一致产物。 |
| 完整性 | 3/5 | 49个ConversionData入口受测路径完成，其他MNI及whole17仍未完成。 |
| 清晰性 | 4/5 | 区分跨轮各自通过与最终单fixture复查，明确受测路径与未实现转换的诊断边界。 |
| 可执行性 | 4/5 | 阶段94七个toText方法、MCFL26及必要fixture已限定。 |
| 简洁性 | 4/5 | 当前记录聚焦阶段93结果与范围。 |

平均3.8/5，whole17完整性仍为3/5。

### 阶段 94：toText 原生入口（已限定验证）

七个toText入口接入单receiver context和 `NativeTextOperations`，source覆盖五个Java方法及int两种状态；Any两项仍无source覆盖。移除 `MCString.getMemberFunction` 的TODO override，继承Var的实例成员/签名查找；String runtime/Concrete lazy `instanceData` 与NBT `instanceData`/`commonType`接通，NBT不再误用 `objectData`。MCFL25→26。

stdlib首轮 `mcfpp-native-text-context-stdlib.log` 重建成功，Project0/0，但291364-byte产物属metadata修复前中间版本。首轮final Cache XML `2026-10-06T20:03:51.866Z`通过，text fixture `20:03:52.582Z`失败于 `MCString.getMemberFunction` 的NotImplementedError；删除TODO后final2 XML `20:07:52.114Z`仍有3个 `Function toText<>() not defined` producer errors。metadata修复后stdlib2成功12s、Project0/0；final3 worker199 exit0/BUILD SUCCESSFUL in18s，XML `20:12:46.049Z`为1/0/0/0，source0/9119 warnings、fresh0/9120。fresh consumer真实磁盘payload、score/NBT结果7和frame0通过；Cache及fixture两项跨轮各自通过，最终仅fixture复查。最终source/build资源/build索引均291070 bytes、MCFL26、SHA256 `9ED46C95796CBADE0007D246E630FF094F9018B03772AB57B145B98E412EEAE5`。未验证聊天渲染或跨帧寿命，无fullcheck/server。

### 阶段 94 自检

| 维度 | 评分 | 证据与改进 |
| --- | --- | --- |
| 准确性 | 4/5 | 区分TODO查找、producer缺toText及metadata修复后的source/fresh磁盘结果。 |
| 完整性 | 3/5 | 七个入口完成ABI迁移，其中五个有source/fresh覆盖，Any两个无source覆盖；其他MNI与whole17仍未完成。 |
| 清晰性 | 4/5 | 明确记录final3是单fixture复查，两项跨轮各自通过。 |
| 可执行性 | 4/5 | 阶段95明确改用Java真实方法名并保留语言层identifier重载。 |
| 简洁性 | 4/5 | 只更新当前阶段与下一步，保留此前历史。 |

平均3.8/5，whole17完整性仍为3/5。

### 阶段 95：显式原生转换引用（已限定验证）

`conversions.mcfpp` 的49个native RHS已逐项改成真实Java名 `toTargetFromSource`，既有 `decodeByte` fixture改为 `ConversionData.toIntFromByte`。FieldVisitor仅按真实Java方法名匹配，不再回退语言identifier；Namespace/CompoundData仍用annotation identifier解析正常语言重载。无旧 `.mcfpp` 显式Java引用兼容要求，MCFL26/wire未变。

`mcfpp-native-reference-stdlib.log` 成功9s，Project0/0；final worker200 exit0/BUILD SUCCESSFUL in18s，fresh XML `2026-10-06T20:31:00.566Z` 单fixture 1/0/0/0，source0/9119 warnings、fresh0/9120。source/build资源/build索引三份均291070 bytes、MCFL26、SHA256 `3F507523A551F5CE2A42B2CA93BA5AB01207F4518B5CF47AC80D13A0AA6B58EA`。仅运行既有conversion fixture；未重跑Cache或其他旧绿，无fullcheck/server。

### 阶段 95 自检

| 维度 | 评分 | 证据与改进 |
| --- | --- | --- |
| 准确性 | 4/5 | 记录了真实方法名匹配、唯一fixture及三份MCFL26产物。 |
| 完整性 | 3/5 | 49个stdlib引用与转换fixture路径通过，但whole17与其他MNI仍未完成。 |
| 清晰性 | 4/5 | 区分Java方法名和语言annotation identifier，明确不兼容旧显式引用。 |
| 可执行性 | 4/5 | 阶段96限制text拼接方法和必要缓存回归。 |
| 简洁性 | 4/5 | 仅更新当前阶段和下一步，保留历史。 |

平均3.8/5，whole17完整性仍为3/5。

### 阶段 103：damage void 原生入口（已限定验证）

四个damage void方法接入context，保留语言identifier/default语义；ResourceID DamageType和SelectorVar通过Java参数适配，动态float受 `FloatProviders.enabled` 检查。stdlib一次成功8s、Project0/0；联合2项worker216正常exit0/BUILD SUCCESSFUL in19s。LibFieldAccess XML `2026-10-06T22:23:00.710Z`、Cache `22:22:59.798Z`均1/0/0/0；source0/9118、fresh0/9119。source/fresh函数初始化DamageType并写入 `minecraft:generic`，先准备amount与id再分别发damage宏一次；direct context旧目标动态float报错且不输出命令，常量保留。MCFL32三份bin284899 bytes，SHA256 `768075ABA8DFA56BFC1B294A0F05D0F3CF97BEC9A70B6595CBECFCAD7A09C6A0`。未验证world/float执行，不fullcheck/server。

### 阶段 103 自检

| 维度 | 评分 | 证据与改进 |
| --- | --- | --- |
| 准确性 | 4/5 | 标准库、两份fresh XML、source/fresh错误数、命令顺序和资源SHA可复核。 |
| 完整性 | 3/5 | 四个damage入口限定路径通过；float/world执行及whole17仍未完成。 |
| 清晰性 | 4/5 | 指明动态float目标能力检查和未执行world边界。 |
| 可执行性 | 4/5 | 阶段104限定九种print入口与未知any编码。 |
| 简洁性 | 4/5 | 只保留阶段证据和下一步边界。 |

平均3.8/5，whole17完整性仍为3/5。

### 阶段 102：StdCommands void 原生入口（历史）

16个void方法迁入context；保留四个damage primitive-float方法和107个 `CommandReturn` 方法。`ResourceID.toCommandPart`通过annotation override替换失效的源码覆盖，并由Java wrapper读取 `id.toCommandPart`。stdlib首轮成功10s，Project0/0；joint首轮worker214在15s失败：LibFieldAccess XML `2026-10-06T22:00:36.130Z` 1/1/0/0、producer3/9118，`id.toCommandPart` 未定义后引发place null Command；Cache XML `22:00:35.360Z` 1/0/0/0通过。修复后stdlib2成功7s、Project0/0；仅复查新fixture的worker215 exit0/BUILD SUCCESSFUL in11s，XML `22:07:40.238Z` 1/0/0/0，source0/9118、fresh0/9119。两个place调用实际均为macro；参数准备先于各自唯一调用，未知target先从frame0读取再写入macro槽，宏体没有return-run。Cache未重复运行；不声称非宏路径或world/frame执行已验证。三份MCFL31 bin为285743 bytes、SHA256 `6BB5D52369DC314C2389C3623A99AA862D975BFB02C45A99344B38B1F7D387C6`。

### 阶段 102 自检

| 维度 | 评分 | 证据与改进 |
| --- | --- | --- |
| 准确性 | 4/5 | 记录首轮ResourceID错误与final2宏顺序证据。 |
| 完整性 | 3/5 | 只验证受测宏调用路径；非宏/world行为和whole17仍未完成。 |
| 清晰性 | 4/5 | 明确unknown target是普通参数，且不声称world求值。 |
| 可执行性 | 4/5 | 下一阶段限制为四个damage void入口及现有float能力边界。 |
| 简洁性 | 4/5 | 保留必要构建、测试和资源证据。 |

平均3.8/5，whole17完整性仍为3/5。

### 阶段 101：LootTablePredicate 原生入口（历史）

`LootTablePredicateData.pass/fail` 两个bool方法接入 `NativeCallContext`；`NativePredicateOperations`复用 `CommandBoolPart` 生成 `if/unless predicate`，经现有结果发布路径规范化为 `ScoreBool`，读取predicate id使用 `PropertyVar.get`。静态 `of`/factory、effects和world模拟未改。stdlib重建8s、Project0/0；联合Cache与新fixture由worker213在18s内成功。LibFieldAccess XML `2026-10-06T21:49:26.530Z`、Cache `21:49:25.760Z`均1/0/0/0；source/fresh分别0/9118和0/9119。source/fresh导出函数包含if/unless predicate的store-success-score命令，scope有两个ScoreBool；三份MCFL30资源291353 bytes、SHA256 `FBFA847ACD3BE926FA7DE85948509C012DBB44AE4AD7017E8E2A0E9A508F37B2`。不模拟world predicate或断言frame0，无fullcheck/server。

### 阶段 101 自检

| 维度 | 评分 | 证据与改进 |
| --- | --- | --- |
| 准确性 | 4/5 | 命令、XML、source/fresh诊断和资源hash有记录。 |
| 完整性 | 3/5 | 受测谓词命令路径已通过，world求值及whole17仍未完成。 |
| 清晰性 | 4/5 | 明确没有验证world侧结果。 |
| 可执行性 | 4/5 | 阶段102限定16个void方法和宏命令次序。 |
| 简洁性 | 4/5 | 只记录本阶段证据和边界。 |

平均3.8/5，whole17完整性仍为3/5。

### 阶段 100：PlainChatComponent纯文本转义（已限定验证）

纯text内容改由Fastjson2 `JSON.toJSONString` 编码单个字符串。RED `mcfpp-plain-text-escaping-red.log` worker211 exit1/BUILD FAILED in12s，XML `2026-10-06T21:40:00.543Z` 1/1/0/0；producer10/9118，首因是结果发布时 `ValueSnapshot.of`→`Tag.toNBT` 将未转义引号文本当SNBT解析，10条错误不等于10个独立根因。未到fresh。final `mcfpp-plain-text-escaping-final.log` worker212正常exit0/BUILD SUCCESSFUL in17s，XML `2026-10-06T21:41:53.747Z` 1/0/0/0；source0/9118、fresh0/9119。磁盘执行验证quote/backslash文本的original/copy、joined追加` tail`、NBT string toText、return7/frame0；日志118877/878/882/884是flat literal components。仅覆盖该fixture，不声称控制字符或聊天渲染。MCFL29不变；无fullcheck/server。

### 阶段 100 自检

| 维度 | 评分 | 证据与改进 |
| --- | --- | --- |
| 准确性 | 4/5 | 记录RED首因、final日志与四个磁盘text结果。 |
| 完整性 | 3/5 | JSON字符串路径限定通过，控制字符、聊天渲染和whole17未完成。 |
| 清晰性 | 4/5 | 未把producer诊断总数说成独立根因，也未扩张测试范围。 |
| 可执行性 | 4/5 | 阶段101只接入两个predicate bool入口和命令生成fixture。 |
| 简洁性 | 4/5 | 只补当前阶段和明确边界。 |

平均3.8/5，whole17完整性仍为3/5。

### 阶段 99：DataObjectData.toText（历史）

仅迁移 `DataObjectData.toText` 至显式context，复用 `NativeTextOperations` 的旧DTO SNBT/runtimeNBTChat实现；JavaVar与Any路径不变。baseline日志 `mcfpp-native-template-text-red.log` 实际是GREEN：worker209正常、XML `2026-10-06T21:28:54.597Z` 1/0/0/0、source0/9118、fresh0/9119。MCFL29标准库重建Project0/0、BUILD SUCCESSFUL in10s。joint2 worker210正常、BUILD SUCCESSFUL in19s；LibField XML `2026-10-06T21:34:31.588Z`与Cache XML `21:34:30.893Z`分别1/0/0/0。三份291431-byte bin SHA256 `37576145F097FEEB0FB97CFFA7A35F970750A51B4687D846F82A3A9EE5473B7F`。consumer磁盘函数执行验证Payload.value7、result7、runtime NBT组件与frame0；构造走普通data的 `buildUnConcrete`，不证明Concrete分支。无fullcheck/server。

### 阶段 99 自检

| 维度 | 评分 | 证据与改进 |
| --- | --- | --- |
| 准确性 | 4/5 | 区分green baseline、schema29联合测试及普通data构造边界。 |
| 完整性 | 3/5 | DataObjectData单入口受测通过，Concrete、JavaVar/Any范围及whole17仍未完成。 |
| 清晰性 | 4/5 | 给出source/fresh磁盘组件、值与frame0证据。 |
| 可执行性 | 4/5 | 阶段100仅处理PlainChatComponent JSON转义。 |
| 简洁性 | 4/5 | 保持一个入口、一个fixture和必要Cache回归。 |

平均3.8/5，whole17完整性仍为3/5。

### 阶段 98：T! text快照、copy与即时拼接（历史）

两个生产文件修改不可变text snapshot恢复：完整ListTag恢复为immutable `EncodedChatComponent`，再以声明类型构造。RED `mcfpp-known-text-snapshot-red.log` worker207 exit1/FAILED17s，XML `2026-10-06T21:14:21.835Z` 1/1/0/0；producer2/9118，joined和suffixed均为text→text赋值错误，未进fresh。final `mcfpp-known-text-snapshot-final.log` worker208正常exit0/BUILD SUCCESSFUL in28s，XML `21:19:02.182Z` 1/0/0/0；source0/9118、fresh0/9119。生成consumer磁盘函数并执行得到original=A、copy=A、joined=AB、suffixed=AS、return7/frame0；日志118890/118894显示joined/suffixed直接作为含组件的flat literal list。MCFL28、bin291176/hash2550…不变；无stdlib/Cache/fullcheck/server。仅一RED及一final，无其他suite重跑。

### 阶段 98 自检

| 维度 | 评分 | 证据与改进 |
| --- | --- | --- |
| 准确性 | 4/5 | fresh XML、source/fresh诊断及磁盘literal payload均有证据。 |
| 完整性 | 3/5 | 当前text快照路径受测完成，whole17仍ACTIVE未完成。 |
| 清晰性 | 4/5 | 区分RED producer失败与final source/fresh执行。 |
| 可执行性 | 4/5 | 阶段99限于DataObjectData.toText和一个runtime往返fixture。 |
| 简洁性 | 4/5 | 只记录该阶段的必要错误、修复和验证。 |

平均3.8/5，whole17完整性仍为3/5。

### 阶段 97：统一文本原生方法（历史）

String与NBT的toText入口分别统一到单Java类，依照实际receiver状态分流，移除重复Concrete类和helper；未扩展其他native/effects。阶段96 fixture的两个即时常量concat初始化改为直接toText表达式。stdlib `mcfpp-unified-text-methods-stdlib.log` exit0、Project0/0、BUILD SUCCESSFUL in4s；指定联合 `mcfpp-unified-text-methods-final.log` worker206正常、exit0/BUILD SUCCESSFUL in17s。LibField XML `2026-10-06T21:02:40.295Z` 2/0/0/0，Cache XML `21:02:39.664Z` 1/0/0/0；四个source/fresh Project依次0/9119、0/9120、0/9118、0/9119。两条text fixture的payload/copy、return7/frame0断言通过；三份bin为291176 bytes、MCFL28、SHA256 `2550D9609261BC26BFB713DE15CA6630FFEAF6805B52024C37DCE5930C57381B`。

同一必要检查曾被重复运行：首轮报告的54s/35s日志被后一次同名重定向覆盖，首轮worker号无法由当前日志核实；第二次是代理未识别此前完成摘要而重复执行，应以本段最新fresh XML/worker206为准，不计新增覆盖。命令显示两个toText输入分别写为文本tag literal；joined/suffixed结果仍通过临时数组append生成，未证明AB/AS整体literal fold。无fullcheck/server。阶段87普通值位置type规则继续生效；whole17未完成。

### 阶段 97 自检

| 维度 | 评分 | 证据与改进 |
| --- | --- | --- |
| 准确性 | 4/5 | 记录最新XML/worker/bin，并限定即时输入literal与拼接结果未折叠的边界。 |
| 完整性 | 3/5 | 两个fixture路径与Cache回归通过；text snapshot常量拼接和whole17仍需继续。 |
| 清晰性 | 4/5 | 明确首轮日志被覆盖及重复运行原因，不把它计作新覆盖。 |
| 可执行性 | 4/5 | 阶段98先核对T! text snapshot、copy和concat的实际缺口。 |
| 简洁性 | 3/5 | 本次应先识别摘要中的已完成验证，避免重复构建。 |

平均3.6/5，whole17完整性仍为3/5。

### 阶段 96：text 与拼接运算（历史）

text receiver赋值和三类构造路径已接通；两个concat operator使用单context，`JsonTextConcreteData` 重复注入改为复用 `JsonTextData`。MCFL26→27。标准库成功57s、Project0/0。初始RED worker201/FAILED12s，XML `2026-10-06T20:41:25.805Z` 1/1/0/0；producer1 error/9118 warnings，`observe<>(nbt,nbt) not defined`，text物化实际退成NBT，未到fresh或磁盘断言。首轮Cache XML `20:46:44.428Z`通过；fixture `20:46:40.257Z`失败于source执行器不支持text component append命令，未到consumer。final2 worker203在source观察到CompoundTag而非预期ListTag后失败。修复runtime adapter后final3 worker204 exit0/BUILD SUCCESSFUL in14s，fixture fresh XML `20:51:29.163Z` 1/0/0/0；source0/9118 warnings、fresh0/9119。source及fresh consumer真实磁盘均通过12项payload观察、original/copy及return7/frame0。两个不同用例跨轮各过，final3仅单fixture复查；常量来源的A/B和未知MNI屏障物化后的const-fold路径没有单独完整证明。最终三份291364-byte bin为MCFL27、SHA256 `A674D9E8FB3AD848A9F8CCC5E68CF31E2305271395D0A6B90122E1CD0827AD1A`。无聊天渲染/跨帧寿命/fullcheck/server。

### 阶段 96 自检

| 维度 | 评分 | 证据与改进 |
| --- | --- | --- |
| 准确性 | 4/5 | 区分source执行器命令缺口、ListTag观察失败与最终source/fresh磁盘断言。 |
| 完整性 | 3/5 | 受测text/concat路径完成；未知调用屏障后的常量折叠与whole17仍待继续。 |
| 清晰性 | 4/5 | 说明跨轮两个用例各过、final3仅复查fixture。 |
| 可执行性 | 4/5 | 阶段97限定String/NBT receiver分流并复用现有toText fixture与Cache检查。 |
| 简洁性 | 4/5 | 保留历史，仅新增当前阶段和下一步。 |

平均3.8/5，whole17完整性仍为3/5。

### 阶段 92：移除被拒绝的 byte/short 算术注册（历史）

删除byte/short四个Java类中的44个算术注册，以及 `MCFPPNBTType.injectedBy` 的4处注册；不改long/double、精确格式、转换或统一拒绝guard。MCFL23→24，stdlib重建成功55s，Project0/0；source、stdlib-index和build资源三份bin均为292301 bytes、MCFL24、SHA256 `6EADA06D3343578C0A08E13C691D0CC9742C87071476615260D993A2980A1994`。

final日志worker193正常、exit0/BUILD SUCCESSFUL in44s：Conversion、CacheFormat、TypeKernel三个fresh XML分别为 `2026-10-06T19:17:57.589Z`、`19:18:01.088Z`、`19:18:01.096Z`，均1/0/0/0。Conversion项目0/9118 warnings；TypeKernel的byte/short/long/double算术及byte→int赋值五个拒绝片段各有1个预期错误/9118 warnings。缓存拒绝用例通过。没有新增fixture/fullcheck/server。阶段92已限定验证，whole17仍未完成。

### 阶段 92 自检

| 维度 | 评分 | 证据与改进 |
| --- | --- | --- |
| 准确性 | 4/5 | 明确Project的0错误与TypeKernel五个预期拒绝诊断，并记录同步bin哈希。 |
| 完整性 | 3/5 | 本次byte/short限定范围及缓存格式已验证，其他MNI和whole17仍未完成。 |
| 清晰性 | 4/5 | 区分删除的byte/short注册、未改动的long/double与保留的转换。 |
| 可执行性 | 4/5 | 下一步49个ConversionData入口和两项必要回归已有明确范围。 |
| 简洁性 | 4/5 | 更新当前证据，历史阶段继续保留。 |

平均3.8/5，whole17完整性仍为3/5。

### 阶段 91：int/float/bool 原生运算符（历史）

28个 `MNIOperator` 方法接入显式单context：int12（含 `~=` 右侧 `RangeVar`）、float11、bool5（4个二元和一元 `!`）。Kotlin `NativeOperatorOperations` 直接调用typed core；在真实 `runInFunction` 中规范化非 `ScoreBool` 的 `BaseBool` receiver/参数和结果，再ensure并发布真实引用。CompoundData保留旧2/3参数ABI并接受精确单context ABI。MCFL22→23，stdlib重建一次成功（12s，Project0 errors/0 warnings），292007 bytes；三份bin的SHA256一致，为 `800AF3AFB375C71643E23C11B2EB64261D3691F38F17D2204CC18FC62D477422`。

验证分轮：首轮Cache与PrimitiveIR各1项通过，新fixture因执行器不支持score比较命令失败，未到fresh consumer；补充真实score比较/范围链后，final2单fixture通过，XML `2026-10-06T18:51:39.029Z`，source0/9118 warnings、fresh0/9119。consumer磁盘函数断言int/float/range/bool七个结果 `1/1/1/0/1/0/0` 与frame0。三个不同用例跨轮各自通过，final2仅复查fixture，不是联合3项；无fullcheck/server。阶段91未迁移完其他MNI，legacy循环与private Var桥仍在，whole17保持ACTIVE。

### 阶段 91 自检

| 维度 | 评分 | 证据与改进 |
| --- | --- | --- |
| 准确性 | 4/5 | 记录了首轮执行器缺口、final2新鲜XML、三份bin哈希和磁盘结果。 |
| 完整性 | 3/5 | 28个指定运算符已验证，但其他MNI和legacy循环仍未迁移，whole17未完成。 |
| 清晰性 | 4/5 | 区分首轮失败与最终单fixture复查，没有称最终联合3项全绿。 |
| 可执行性 | 4/5 | 阶段92精确列出44个拒绝的byte/short注册、MCFL24和三项验证。 |
| 简洁性 | 4/5 | 保留历史并集中说明本阶段实施、验证和边界。 |

平均3.8/5，whole17完整性仍为3/5。

### 阶段 90：字典、Map与ImmutableList原生方法（历史）

Dictionary 4、Map 6、ImmutableList 3个原生方法复用现有context/API，未增加新context或operator。字典已知key限制、Map dynamic key、readonly list dynamic needle均保留。MCFL21→22，stdlib Project0/0、292301 bytes；三份资源SHA一致。source/fresh consumer磁盘fixture实测7个结果10/5/227/7/2/11/-1及frame0。缓存回归和新fixture两个不同用例跨轮各自通过，final5只复查fixture；不是最终联合2全绿。其余MNI仍未全部统一，whole17未完成，详情见verification.md。

### 阶段 90 自检（历史）

| 维度 | 评分 | 证据与改进 |
| --- | --- | --- |
| 准确性 | 4/5 | stdlib三份MCFL22产物hash一致；保留首轮producer错误、三次source执行失败与final5 fresh XML证据。 |
| 完整性 | 3/5 | 13个字典/Map/ImmutableList方法的限定范围已验证，其他MNI、旧循环和whole17仍未完成。 |
| 清晰性 | 4/5 | 分开记录字典限制、dynamic key/needle和source/fresh的执行边界。 |
| 可执行性 | 4/5 | 当时阶段91按28个精确算子、Bool运行时表示和MCFL23重建列出最小验证。 |
| 简洁性 | 4/5 | 当时仅补充阶段90结果与下一阶段范围，保留历史。 |

平均3.8/5，whole17完整性仍为3/5。

### 阶段 89 自检（历史）

| 维度 | 评分 | 证据与改进 |
| --- | --- | --- |
| 准确性 | 4/5 | 区分首次consumer namespace错误、修复后的单fixture复查与另外两项此前通过；记录标准库hash及fresh XML。 |
| 完整性 | 3/5 | 11个list方法已迁移，但仅指定库往返、ListMember与缓存回归受测，whole17/MNI仍未完成。 |
| 清晰性 | 4/5 | Java调用上下文、内部Var桥、namespace注册和ListOperations职责分别说明。 |
| 可执行性 | 4/5 | 当时阶段90限定为字典/Map/ImmutableList 13方法及真实库fixture。 |
| 简洁性 | 4/5 | 当前验证分轮和边界集中记录。 |

平均3.8/5，whole17完整性仍为3/5。

### 阶段 88 自检（历史）

| 维度 | 评分 | 证据与改进 |
| --- | --- | --- |
| 准确性 | 4/5 | 标准库失败修复、三份fresh XML及资源hash均按实际轮次记录。 |
| 完整性 | 3/5 | 仅clear及指定回归受测，其他list native方法与whole17/MNI仍未完成。 |
| 清晰性 | 4/5 | 区分Java context、内部StorageAccess桥和ListOperations领域逻辑。 |
| 可执行性 | 4/5 | 当时阶段89聚焦剩余10方法及methodString缓存影响。 |
| 简洁性 | 4/5 | 当前阶段记录聚焦小范围入口与验证。 |

平均3.8/5，whole17完整性仍为3/5。

### 阶段 87 自检

| 维度 | 评分 | 证据与改进 |
| --- | --- | --- |
| 准确性 | 4/5 | RED、compileKotlin中断、final2两失败与final3五项复查按分轮和fresh XML记录；未声称全套单轮通过。 |
| 完整性 | 3/5 | 19个不同用例跨轮各自通过；whole17/MNI及其他迁移仍未完成。 |
| 清晰性 | 4/5 | 明确普通TypeValue拒绝边界、合法泛型绑定和历史正例撤回。 |
| 可执行性 | 4/5 | TypeUsage接入路径已说明；阶段88候选API和必要验证仍待root确认。 |
| 简洁性 | 4/5 | 保留既有历史，只更新当前规则和分轮证据。 |

平均3.8/5，whole17完整性仍为3/5。

阶段52 NBT地址与宏捕获已提交`dadb6cc`；阶段53对象身份`890833b`；阶段54候选解析`107e8ac`；阶段55 receiver/帧`4b5b7bd`；阶段56有序RHS持久化与MCFL12`0fb7cb8`；阶段57 const真实初始化`3865673`。阶段58普通模板 inferred 字段已接入限定语法域的声明绑定并提交`e6f83fe`；37个不同必要测试分轮各自通过。阶段59导入声明环境恢复已提交`6200561`，MCFL13标准库重建成功，六套件联合50项全过。阶段60限定消费端函数body导出已实现，TemplateInitialization6 + ConstructorExecution7联合13项通过。阶段60已提交`3468341`；阶段61 owner/scope限定迁移已完成并经26个用例跨轮验证。阶段62模块资源复制已验证，MCFL13/bin282180不变。阶段63函数权限持久化已完成，MCFL14/bin285207，提交`1918db9`。阶段64已完成字段/Property权限修复并提交`bda4d5a`，MCFL15/bin286207；最终字段方法1项+LogicStatement6项通过。阶段65来源权限检查的LibFieldAccess3 + TemplateInitialization8联合11项全过，MCFL15/bin不变。阶段66当前实例未限定字段访问LibFieldAccess4 + ConstructorExecution7联合11项全过，producer、consumer及磁盘执行覆盖5/9/8/6。阶段67 generic类readonly签名、源码特化及namespace类别恢复已限定验证，MCFL16/bin286243；阶段68未注解普通generic类T绑定亦已限定验证，17个不同用例跨轮各自通过。阶段69显式generic类型签名与跨库canonical特化已限定验证，MCFL17/bin289989，19个不同用例跨轮各自通过。阶段细节与分轮测试见verification.md。阶段70声明scope及绑定T/N限定路径已验证，36个不同用例跨轮各自通过（29+Logic6+最终1），MCFL17/bin289989未变；阶段71有限Declaration/Applied TypeValue恢复已验证，6个不同用例跨轮各自通过，最终必要2项复查；阶段72完整静态类型集合限定路径已验证，5个不同用例跨轮各自通过；阶段73冻结Specialized类型值已限定验证，最终必要联合2项全过；阶段74源码联合类型与冻结身份已限定验证，必要联合4项全过；阶段75冻结向量TypeValue已限定验证，必要联合2项全过；阶段76冻结SelectorTypeValue已限定验证，最终仅1项通过；阶段77 direct Union TypeValue表达式已限定验证，最终必要1项通过；阶段78 alias有根匿名冻结身份已限定验证；阶段79 generic用户函数自身readonly依赖签名已限定验证，最终必要联合5全绿；阶段80 generic object静态N/方法已限定验证；阶段81 generic interface静态TypeValue已限定验证，三个case跨轮各过、最终新1复查；阶段82 source磁盘导出已限定验证、最终联合3全绿；阶段83 generated-vs-legal名字隔离限定通过，四case跨轮各过/最终新1；阶段84 generic object静态字段/显式constructor已限定通过，最终联合2全绿；阶段85 source/fresh abstract/final flags已限定验证，MCFL18/stdlib287554、最终联合3全绿；阶段86 actual generic父项限定通过、MCFL19/stdlib292301、最终联合3全绿；阶段87 `type` 仅用于泛型参数已实现并限定验证，TypeUsage覆盖普通声明、已绑定签名、IR/擦除与集合、延迟字段；依赖普通 `type` 存储的旧30个正例撤回。最终5项复查全绿，19个不同用例跨轮各自通过，详见verification.md。imported object自动load仍独立未解决。
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

验证完整轮次和红测/worker边界见verification.md。特别是最终41个不同用例跨轮各自通过（不是单次联合41）；第五generic fixture单独通过后，其余33项首轮有3失败，再经6项Logic控制及最终5项（含旧失败复查和旧float ABI）各自验证。无fullcheck/服务端。阶段68已限定支持未注解普通generic class实例化后绑定T；generic object、top-level/method annotation持久化、source abstract/final到model及imported object autoLoad仍未覆盖。阶段69显式类型签名与跨库canonical特化限定验证通过；阶段70声明作用域及绑定T/N限定路径已验证；阶段71有限类型值身份恢复已验证；阶段72完整静态类型集合限定路径已验证，5个不同用例跨轮各自通过；阶段73冻结Specialized类型值已限定验证，最终必要联合2项全过；阶段74源码联合类型与冻结身份已限定验证，必要联合4项全过；阶段75冻结向量TypeValue已限定验证，必要联合2项全过；阶段76冻结SelectorTypeValue已限定验证，最终仅1项通过；阶段77 direct Union TypeValue表达式已限定验证，最终必要1项通过；下一步阶段78匿名模板alias冻结身份尚未实现或测试。

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

## 阶段 74：源码联合类型与冻结身份

阶段74保留旧UNION token '&'及unionTemplateType/UnionDataTemplate，新unionType要求至少一个PIPE '|'；primary/var/expression优先级不变。MCFPPType新union解析递归传scope/caller，invalid项诊断并返回null，不补Any；复用既有MCFPPUnionType规范化。有限TypeId.Union resolver要求非空、所有alternative可恢复及最终FullID相同。两prod+fixture+4docs共7文件；wire/schema/MCFL17/bin289989不变，无stdlib/fullcheck/服务器，最新完整346项仍为72dc557。

frozenUnionTypeArgumentsNormalizeAcrossLibraryRoundTrip仅通过typealias Scalar=(int|string)、ReorderedScalar=(string|int|int)输入静态T；Box真实字段仍int。source/fresh alias顺序/重复归一，T不可变snapshot/FullID、同canonical Box、前置readBox参数及fresh独立对象均验证，producer0/0、consumer0/已知9119 warnings；仅consumer实际磁盘4/9/frame0。direct readonly union expression及Union实际runtime值/布局未验证。

红测mcfpp-generic-union-type-value-red.log：fresh XML2026-10-06T07:39:55.386Z，worker140正常exit1/FAILED6s，最早两处mismatched input '|' expecting {')','&',NL}；只到source语法，没有library restore RED。最终-final.log worker142正常exit0/SUCCESS37s；fresh XML LibField1 2026-10-06T07:50:55.410Z、DataTemplate1 07:51:00.071Z、LibCacheFormat1 07:51:00.252Z、TypeKernel1 07:51:00.258Z，全部0fail/error/skip。必要联合4项包含新fixture、unionsNormalizeOrderDuplicatesAndNesting、immutableTypeIdentitiesRoundTripThroughLibrarySerialization及DataTemplateTest.unionTest。旧unionTest实际0errors/4warnings（common representation未证明的as警告）及既存TODO tellraw templateData，无errorCount或disk断言；只算语法smoke，不宣称Union runtime已验证。阶段74提交以Git历史为准。

## 阶段 75：冻结向量TypeValue

阶段75仅一个生产文件MCFPPType.kt与68行fixture及import接通限定Vector身份恢复；加4docs共6文件。Applied分支识别Builtin(vector)，读取唯一Builtin数字key.toIntOrNull，构造MCFPPVectorType(dim)并核对FullID；原四种元素类型工厂不变，不新增>0维数约束、registry、wire或Vector runtime支持。

frozenVectorTypeArgumentsRestoreDimensionsAcrossLibraryRoundTrip以前置readTwo(Box<vec2>)/readThree(Box<vec3>)进入真实wire；Box的T仅静态，字段仍int。source维数2→4/3→9，fresh反序3→9/2→4；实际T dimension2/3、不同完整Specialized ID/不可变snapshot、前置参数与main canonical、source/fresh prototype及Compiled独立、FullID/snapshot稳定均到达，consumer真实磁盘4/9/frame0通过。

RED mcfpp-generic-vector-type-value-red.log：fresh XML2026-10-06T08:02:09.718Z，1failure/0error/skip，worker143正常exit1/FAILED11s；producer0/0、source模型及写库通过，fresh读库后4次Cannot restore frozen T of Box及2次read undefined，共6errors/9119warnings，未到fresh模型或disk。FINAL mcfpp-generic-vector-type-value-final.log：fresh XML2026-10-06T08:07:30.426Z，2/0/0/0，worker144正常exit0/SUCCESS24s；新75与旧71 frozenDeclarationAndContainerTypeArgumentsRestoreCanonicalTypes各source0/0、consumer0/9119，模型及consumer磁盘均过。仅必要联合2项，非fullcheck/server/stdlib；MCFL17/bin289989/schema不变。阶段74提交16e1051e672ba8a6d732d09239e1d13c92d18025（7文件144+/19-），阶段73为186a712f；阶段75提交以Git历史为准。

## 阶段 76：冻结SelectorTypeValue

阶段76仅MCFPPType.resolveTypeId一行Selector分支，复用MCFPPEntityType(limit,entities,isName).takeIf完整ID相等；保持原顺序、null、引号与flag，无registry/schema/runtime扩展。frozenSelectorTypeArgumentsPreserveFiltersAcrossLibraryRoundTrip新增76行及MCEntity import；加4docs预计6文件。

实际fixture覆盖Selection alias entity<2,"minecraft:pig","!minecraft:cow">及bare entity（null limit/types），前置readSelection/readAnyEntity进入真实wire。Box静态T、真实int字段，source selected4→general9，fresh反序general9→selected4；原引号/ordered types/false的完整Selector ID、两Box/snapshot不同、source/fresh prototype与Compiled独立、前置param canonical、FullID/T snapshots稳定及fresh字段通过；consumer真实磁盘4/9/frame0。isName=true未有source入口，entity世界/runtime未验证；null不归一empty，但empty source未覆盖。

RED mcfpp-generic-selector-type-value-red.log：fresh XML2026-10-06T08:16:47.105Z，1fail/0error/skip，worker145正常exit1/FAILED10s；source0/0、模型及写库通过，fresh读库后4次Cannot restore frozen T及2次read undefined，共6errors/9119warnings，fresh模型/disk未到。FINAL mcfpp-generic-selector-type-value-final.log：fresh XML2026-10-06T08:20:24.280Z，1/0/0/0，worker146正常exit0/SUCCESS20s，source0/0、consumer0/9119，模型及consumer磁盘通过。仅FINAL1，不是joint2/fullcheck；既有serializer/wire未改，MCFL17/bin289989/schema不变，无stdlib/fullcheck/server。阶段75已提交d6dfb7f57b5844839c00a9df5261c61d9d1acd00（6文件118+/22-），阶段76提交以Git历史为准。

## 阶段 77：direct Union TypeValue表达式

阶段77两个prod：MCFPPType.data改为lazy CompoundData(Type,mcfpp)，commonType=MCFPPConcreteType.Type并injectedBy新增19行Java MCFPPTypeData；类标注NoExternalWrites，MNIOperator('|')接受type/返回type，returnsConstWhenArgsConst=true，返回MCFPPTypeVar(MCFPPUnionType(actual caller/other types))。复用两visitor既有Native dispatch，无grammar/visitor override、registry、runtime Union或用户函数求值。内建scope在编译器中按需初始化，未新增namespace持久Native签名，oldbin库读/consumer通过；不重建stdlib、不升VERSION。两prod+78行fixture+4docs预计7文件。

unionTypeExpressionsShareCanonicalSpecializationsAcrossLibraryRoundTrip以sourceScalar alias(int|string)作对照，前置readBox Box<(int|string)>；ordinary intType=int/scalarType=(intType|string)→Box<scalarType>(4)，direct Box<(string|int|int)>(9)，fresh反序direct9→named(string|intType)4。source/fresh MetaUnion两alternative IDs/snapshot与alias一致，同Box cache/frontparam/T binding、fresh对象隔离、FullBoxID/T snapshots稳定及consumer实际磁盘4/9/frame0通过。T仅静态，真实字段int；Union runtime值/布局未验证。

RED mcfpp-generic-union-expression-red.log：fresh XML2026-10-06T08:32:26.901Z，1fail/0error/skip，worker147正常exit1/FAILED7s；producer7/0，最早front readonly与ordinary named left及重复readonly的type | type不支持，invalid type/any read/readonly incomplete级联。仍genIndex写lib/debug，write() errorCount断言终止；后续source模型/consumer读库/fresh/disk未到。FINAL mcfpp-generic-union-expression-final.log：fresh XML2026-10-06T08:36:39.179Z，1/0/0/0，worker148正常exit0/SUCCESS23s，source0/0、consumer0/9119，完整模型及consumer磁盘通过。最终必要1，非fullcheck/stdlib/server；MCFL17/bin289989/wire/schema不变。阶段76提交bfa00985dae6954038f6caaf69352d9a74adf107（6文件119+/19-），阶段77提交以Git历史为准。

## 阶段 78：匿名alias冻结类型身份

六个prod入口：两个visitor在真实anonymous model创建时转存field annotation，并停止全局annotation pass错误进入匿名body；TypeAlias.cachedTarget和SimpleLibScope.cachedAliasTargets仅读取已缓存目标，不触发解析。template Declaration恢复按完整ID匹配named与alias目标，tryResolve后要求template===唯一，否则诊断/null，不用语义equals去重。TempPool匿名名改data-N，用户Identifier不能包含'-'，既有NamespaceID保留；无global registry、新TypeId variant或wire字段。

fixture frozenAnonymousAliasTypeArgumentsRestoreCanonicalTypesAcrossLibraryRoundTrip使用匿名X@DataOnly、透明Y及捕获旧合法data_N的named声明，两个目标引用/完整ID不同；source X4→Y9，fresh Y9→X4。前置readBox Box<X>、canonical T.value/scope.types[T]、source/fresh prototype/Compiled独立、FullID/snapshot及consumer磁盘4/9/frame0通过。只验证alias有根匿名、static T/runtime int，不实例化X，不验匿名method/constructor，不保证重新parse/rebuild稳定ID、全部跨library碰撞、无根匿名或Opaque。

日志前缀mcfpp-generic-anonymous-type-value，目录F:/DevCache/.codex/runtime。初始-red.log worker149 fatal AnnotationVisitor.currTemplate!! NPE，FAILED7s，无fresh XML；旧77 XML08:36:39.179Z不属于78，未到write/source模型/consumer。-annotation.log worker150正常exit1/FAILED16s：新78 XML2026-10-06T09:05:17.281Z 1fail，producer0/0、source@DataOnly/canonical/write通过，consumer3 errors/9119 warnings（两次frozen T及read undefined）；既有TemplateFieldInference2 XML09:05:21.696Z均绿。-final.log worker151正常FAILED23s：LibField XML09:13:28.179Z 2tests/1fail，旧71绿；Template XML09:13:33.020Z anonymousForward1绿。新78 producer7 syntax errors源于fixture将named data置于alias之前，仅移动fixture，未改grammar或放宽断言。

最终-final2.log worker152正常exit0/SUCCESS10s，新78 XML2026-10-06T09:17:26.630Z 1/0/0/0，producer0/0、consumer0/9119，全部模型与磁盘通过。最终仅新1方法复查；四个不同用例跨轮各自通过，不是最终联合3或fullcheck。六prod+fixture+四docs预计11文件；MCFL17/bin289989/layout/签名schema未变，无stdlib/fullcheck/server。阶段77已提交4925f67c363e0f2856bfc2e34af89441bf167665（7文件147+/23-）；阶段78提交记录见Git历史。阶段79先以单fixture RED界定generic函数自身readonly依赖Box<T>普通形参/返回的source/library/fresh路径，优先复用UnresolvedType文字placeholder与共享boundSignature；尚未实现/测试，schema需按实际改动核对，whole17未完成。

## 阶段 79：泛型函数readonly依赖签名

七prod：Function.parseDeclaredType仅在generic用户函数自身readonly Identifier terminal出现在type AST时保存现有UnresolvedType文字；member先参数后返回，param/return统一Unknown adapter保留类型。SpecializationPolicy.resolveBoundSignature在声明环境中以独立FunctionScope顺序绑定完整readonly值，ParameterMatcher和compileGeneric共享真实normal/return类型；只Unresolved签名按Identifier token/readonly位置归一，已知类型仍FullID。StorageAccess.freezeReadonly接受已cast输入，登记完整snapshot/types、fresh Symbol及CompilerOnly root/parts；GenericDataTemplate等价复用。compiled移除本地readonly占位后安装fresh绑定，ordinary运行时实参保持Unknown key；不执行用户body或type运行时反射。延期识别仅GenericFunction，共享compileGeneric不代表新增generic extension/native入口已验证。

95行fixture（import+94行method）genericFunctionDependentTypesBindBeforeRuntimeArgumentsAcrossLibraryRoundTrip验证relay<T>(Box<(T)>)->Box<(T)>：source dynamic int4→int9→bool7，fresh bool7→int9→int4，caller影子T=string不污染绑定。相同int/runtime4/9共用一个wrapper、bool独立，恰好2；normal/return canonical Box及origin、scopeT实际Builtin/Meta/type绑定、source/fresh原型/函数/wrapper/template独立、FullID/snapshot/cache.arguments稳定（不比跨fresh declarationSymbolId），consumer真实磁盘4/9/7/frame0通过。仅static T/runtime int字段，不验bool运行时行为、后续复制修改、defaults全集、任意typedef表达式或重载等价、GenericObject/Interface、用户constexpr。dependent default literal已延期到实际bound type cast并在isError时早退，但无新增默认值断言。local writer仍可输出空generic prototype；本fixture验证imported bodyCompiled消费端主体导出，不声称所有writer只导wrapper。

日志前缀mcfpp-generic-function-dependent-types，目录F:/DevCache/.codex/runtime。-red.log worker153正常FAILED8s，fresh XML2026-10-06T09:30:39.558Z 1fail/0error/skip，producer14/0首header T不完整→Symbol T/Invalid Box及return/read级联；genIndex仍尝试lib/debug写出，write.errorCount断言终止，未到source模型/fresh/disk。-final.log compileKotlin FAILED15s，无worker/无fresh XML：MCFPPValue仅interface不继承Var，freezeReadonly错误返回接口导致类型/属性编译失败；只修helper返回Var并检查interface，未改断言。

-final2.log worker154正常exit0/SUCCESS35s，最终必要联合5全部通过；LibField2 fresh XML2026-10-06T09:46:39.847Z，两producer0/0、consumer0/9119；SpecializationPolicy3 XML09:46:45.392Z，三项0/0。新79、旧裸T绑定/普通实参缓存/CompilerOnly容器及78匿名alias@DataOnly实际磁盘均绿，不是跨轮合计或fullcheck。七prod+fixture+四docs预计12文件；未增加Info/class fields/wire/schema，MCFL17/bin289989不变，无stdlib/fullcheck/server。78已提交fa29ec67549817392b86c62f435d3ee459ec7201（11文件160+/25-）；79提交记录见Git历史。下一80先单fixture RED界定generic object Settings<N as int>及(Settings<4>).read()/9的source/library/fresh；已知TypeVisitor prototype cast、kind/type解析缺口待实证，先不扩interface/autoload/字段，wire/version按实际实现核对。whole17未完成。

## 阶段 80：generic object静态身份与方法

阶段80共13个prod文件（含删除47行旧GenericObjectFieldVisitor）：generic object及compiled object使用ObjectCompoundData/self companion，共享一个factory及TemplateBody注册visitor，prototype只准备header。Member.isStatic和Function命名/prepareBody识别静态owner；ObjectType/GenericObjectType保稳定object身份及有限exact object lookup/canonical重绑定，GenericInfo恢复self而不另造companion。复用原generic冻结/缓存/声明完成及lazy body编译，生成运行时命令，不执行用户constexpr。未添加Info/backing class字段、Kryo注册或TypeId codec；MCFL17/bin289989不变，无stdlib/fullcheck/server。

83行fixture genericObjectReadonlyValuesShareCanonicalSpecializationsAcrossLibraryRoundTrip：source Settings<4>→9→4，fresh 9→4→4；2个canonical对象、N CompilerOnly完整snapshot/cache.arguments、prototype/compiled self-companion、method owner/isStatic及canonical StaticMemberView this、source/fresh独立模型与object FullID/快照稳定、有限resolveTypeId返回canonical对象通过。source执行模型/库写入断言，真实磁盘执行来自consumer，结果4/9/4、frame0。仅static N/runtime int返回；StaticMemberView字段39–49强转、字段初始化、autoload、interface、local writer空prototype/漏compiledTemplates递归导出及genericobject wrapper作为直接库fieldtype的Kryo路径未验，不宣称全集。

日志前缀mcfpp-generic-object-readonly，目录F:/DevCache/.codex/runtime。RED worker155 fatal ClassCastException TypeVisitor161，FAILED8s，无fresh XML（旧79 XML09:46:39.847Z不算80）；只SKIPPED，无producer结果/库/source模型/fresh/disk。final联合3 worker156正常FAILED32s：LibField2 XML2026-10-06T10:12:58.042Z旧79绿/新80红，Template1 XML10:13:02.626Z普通ObjectMethods绿。新80 source0/0、部分source模型/key/typeId通过，过强this==null夹具断言失败，实际为StaticMemberView(Settings9)，未到全部source/fresh/disk。仅将fixture改为StaticMemberView及其canonical模板断言，未再改prod。

final2 worker157正常exit0/SUCCESS10s，新80 XML2026-10-06T10:17:31.086Z 1/0/0/0，source0/0、consumer0/9119已知warnings，全部source/fresh模型及反序consumer磁盘通过。最终仅新1复查；三个不同用例跨轮各自通过，不是最终联合3全绿。79已提交84c373d3f8c4179392bdf04c0ac14d3bb05dc486（12文件262+/65-），80提交见Git历史。下一81仅generic interface静态TypeValue，先单fixture RED，尚未实现/测试；优先既有GenericDataTemplate/GenericType+现注册serializer/isInterface及TypeId kind，不预设新wrapper/registry/schema。whole17未完成。

## 阶段 81：generic interface静态TypeValue

阶段81仅五prod：FieldVisitor恢复interface真实currTemplate/typeScope，generic prototype只prepareHeader并finally恢复；共享compile复制isInterface，Specialized身份按origin interface kind；共享BodyVisitor不生成接口默认ctor。Type解析/有限resolveSpecialization及现MCFPPGenericDataTemplateType.tryResolve按exact interface声明和canonical快路径恢复，复用已注册serializer，无新wrapper/registry。shared member声明入口补f.isAbstract=ctx.ABSTRACT()!=null，FunctionInfo原有字段，不改metadata。115行fixture frozenGenericInterfaceTypeArgumentsRestoreBoundSignaturesAcrossLibraryRoundTrip不构造/调用interface，Contract<T>抽象exchange参数/返回绑定Int/Bool，static TypeValue进入普通Box的runtime int字段。

source模型与library写入、fresh独立prototype/Contract/Box/abstract函数、readonly Meta/scopeT/CompilerOnly完整snapshot/cache.arguments、interface FullID/有限resolver、noCtor及前置Box签名通过；source Int4→Bool9、fresh反序Bool9→Int4，真实consumer磁盘4/9/frame0。仅static interface TypeValue/T int,bool，不验generic继承grammar/shape转换、runtime接口布局、annotations、一般source abstract/final flags、defaults全集；legacyInterface wrapper和Kryo全集未激活/验证。sourcewriter递归导出、autoload、静态字段强转及整体IR/MNI旧体系仍未完成。

日志前缀mcfpp-generic-interface-type-value，目录F:/DevCache/.codex/runtime。RED worker158 FAILED7s，两前置Contract<int>/bool Invalid type后FieldVisitor528 currTemplate NPE fatal，SKIPPED/无fresh XML（旧80 XML10:17:31.086Z不计）；无producer完成计数/库/source检查/fresh/disk。joint3 worker159正常FAILED26s，fresh XML2026-10-06T10:37:28.651Z 3tests/1fail(new81)/2pass(old73+old80)，三个producer各0/0，旧consumer各0/9119，磁盘73=4/9、80=4/9/4/frame0。新81 source模型部分flags/noCtor/snapshot/ID/resolver通过，exchange.isAbstract第1557行失败为生产遗漏ABSTRACT标记，未到consumer/disk；仅补shared声明入口一行，未改fixture/schema。

final2 worker160正常exit0/SUCCESS13s，fresh XML2026-10-06T10:43:19.510Z 新1/0/0/0，source0/0、consumer0/9119已知warnings，全部模型及consumer磁盘通过。最终仅新1复查，三个不同case跨轮各过，不是最终联合3全绿；80轮普通ObjectMethods不算81第四case。无Info/backing字段、codec/Kryo注册/VERSION变化，MCFL17/bin289989不变，无stdlib/fullcheck/server。80提交3301770618f70ce16f79050213d106c4913f4743（常规18文件224+/176-），81提交见Git历史；whole17未完成。下一82仅source磁盘导出先单fixture RED，尚未实现/测试。

## 阶段 82：source generic特化磁盘导出

阶段82只改DatapackCreator（12行新增/2行删除）：genNamespace局部exportCompound以IdentityHashMap backing set按对象身份去重，ObjectCompoundData复用genObject、普通DataTemplate复用genTemplate，递归GenericDataTemplate.compiledTemplates；现有template/object根接入。genFunction/genTemplateFunction/genObject及自由函数行为原样，不套imported bodyCompiled过滤、不扩companion/interface/autoload或空prototype整理。46行fixture sourceGenericSpecializationsExportAllRuntimeTargetsToDisk只source，不consume；source Box1/relay1/Settings2缓存复用，实际namespaceID文件与磁盘执行无内存fallback。

RED mcfpp-source-generic-export-red.log：worker161正常FAILED8s，fresh XML2026-10-06T10:58:59.571Z 1fail/0error/skip，source0/0、cache断言通过；真实盘调用缺fixture.fields:box_type_0/_init_box_type_0_0_int（ScoreCommandExecutor283），未到文件/结果断言。FINAL必要联合3：worker162正常exit0/SUCCESS18s；LibField XML2026-10-06T11:02:57.476Z 2/0/0/0（新82+旧80），Template XML11:03:03.015Z 1/0/0/0（普通restoredObjectMethods）。新82 source0/0，Box显式init/read与两Settings read文件存在，relay wrapper实际磁盘调用，结果4/9/4/9、frame0均通过。旧80 source0/0、consumer0/9119且磁盘4/9/4；旧普通object source0/0、consumer0/9119，init/set/read7及来源独立。最终联合3全绿，不是分轮合计或fullcheck。

仅受测source generic template/object输出，不声称所有generic/abstract语法或接口runtime可用；autoload、静态字段强转/字段初始化、interface runtime、legacy wrapper/Kryo全集、空prototype导出及整体IR/MNI旧体系保留。MCFL17/bin289989/schema/注册/codec未变，无stdlib/fullcheck/server。81提交2437ccfa9af2f21ddb3b2c9201b25535a4418f28（10文件202+/37-），82提交见Git历史；whole17未完成。下一83仅internal generated名字与合法source碰撞，先单source fixture RED：Settings<N>与Settings_int_0、relay wrapper与relay_0在真实target/file/prefix上隔离；拟仅两个现有identifier表达式末尾加'-'并保留separator，FullID/key arguments不变，不造命名系统、不预设schema/stdlib变化，尚未实现/测试。

## 阶段 83：生成名字与合法源码名字隔离

阶段83仅两prod表达式：GenericDataTemplate生成标识符的编号分隔符改为'-'（Settings_int-0），SpecializationPolicy generic wrapper改relay-0；共享factory仍覆盖ordinary/object/interface，FullID仍origin Declaration+冻结arguments，key/options/metadata/schema/MCFL17/bin289989不变，无stdlib/fullcheck/server。42行source-only fixture generatedSpecializationNamesDoNotOverwriteLegalSourceDeclarations让合法Settings_int_0与relay_0同时存在，无consume/in-memory fallback；只验证generated-vs-legal名字、真实targets/files与物理owner storage prefix，不扩一般member Function.prefix、casefold或跨库同名机制。

RED mcfpp-generated-name-collision-red.log：worker163正常FAILED8s，fresh XML2026-10-06T11:13:40.509Z 1fail/0error/skip，source0/0/cache过；盘执行缺temp_2462 mcfpp_default，无结果断言，不能写4→9。writer同settings_int_0/static/read及relay_0_int重复，两个调用均旧relay_0_int。试joint4 worker164正常FAILED15s，XML11:17:58.039Z 4tests/1fail；旧79/82/80各绿（source0/0，库fresh0/9119已知warnings），新83盘4/9/4/9/frame0和object namespaceID distinct已过，第1705行失败是ROOT fixture误比较两个read的Function.prefix（既有行为忽略owner），非namespace/生产rename失败。只改为比较各owner.prefix，其他断言不变。

最终-final2.log worker165正常exit0/SUCCESS8s，fresh XML2026-10-06T11:23:47.843Z 新1/0/0/0，当前source0/0，无consume；盘4/9/4/9/frame0、generated Settings_int-0与合法Settings_int_0的object namespaceID/owner prefix、free function namespaceID/prefix及四targets distinct均绿。最终仅新1，四个不同case跨轮各过，非最终联合4全绿。82提交6512e115f0fc8d7d2770b60e6516169450829cf2（6文件97+/17-），83提交见Git历史；whole17未完成。下一84 generic object typed static字段与显式constructor初始化，先单fixture RED验证source/fresh磁盘4/9，不做自动load；已知StaticMemberView concrete casts、constructor guards、compiledobject ctor导出/共享静态NBT root待实证，尚未实现/测试。

## 阶段 84：generic object静态字段与显式初始化

7prod+1fixture共125+/22-。对象marker共享静态NBT root，compiled object constructor/字段完成/导出使用既有通道；静态字段绑定及冷unqualified读取保留状态/完整快照。StorageAccess派生read/write保留parent，避免同名normal参数被global字段替换。显式init，不解决autoload、generic compiler-only字段或直接Kryo object fieldtype。

RED worker166 fatal强cast、FAILED7s/no freshXML；joint3 worker167新84缺value score失败，旧82/ordinary绿；final2 worker168新84绿、ordinary缺frame.value失败。final3 worker169正常exit0/SUCCESS23s，Lib新84 XML2026-10-06T12:15:59.928Z及ordinary Template XML12:15:54.957Z各1/0/0/0，最终必要联合2全绿。source0/0、fresh0/9119，source盘4/9/4/9、fresh9/4/9/4、ordinary7及frame0/实际files均通过；三不同case跨轮各过，不称最终联合3。MCFL17/bin289989/schema不变，无stdlib/fullcheck/server；83已提交41307ff156d7c7be7028f1a644c657d08048730b，84提交见Git历史。

下一85模板/object source abstract/final flags、compiled final、final继承检查、abstract默认ctor跳过及final metadata/Kryo early shell待两bounded fixtures RED与实现，计划MCFL18和stdlib重建，尚无验证。自评4/3/4/4/4、平均3.8，whole17未完成。

## 阶段 85：source/fresh abstract与final标志

8prod34+/19-与两个fixture110行共9文件144+/19-，另标准库bin更新。source template ABSTRACT/FINAL、object FINAL、compiled final、final继承拒绝和abstract默认ctor跳过；两个Info与Kryo声明前缀/early/Unsolved壳保存final，真实wire升MCFL18。单独stdlib SUCCESS14s/compiler0/0，三份artifact均287554 bytes/header4c46434d12000000，同SHA256 3EE448D21ADA523A2CD08565671A309B865C4DAEC52340AAA0C26E75888038D1。

RED worker170正常FAILED9s/fresh12:31:02.661Z 2fail，positive source3errors，negative首Source Child未拒绝、尚未fresh/后两case。最终worker171正常exit0/SUCCESS25s，LibField XML2026-10-06T12:43:21.888Z 2/0/0/0与Cache XML12:43:29.774Z 1/0/0/0，必要联合3全绿。positive source0/0 fresh0/9119，source/fresh磁盘4/9/4/4/frame0及abstract/noCtor/bound int签名/final原型与特化/独立模型全部通过；negative三case各source1/0、fresh1/9119为预期拒绝，父关系正确。旧/未知cache格式拒绝通过，无full/server。84提交004ed4e98136719b49bdec17b1a69ec05adc47ac；85提交见Git历史。

下一86 generic父项实际readonly解析待单fixture RED与API，可能MCFL19/必要stdlib regen；保留85 bare final generic即时拒绝。不扩super新语义、constexpr/registry、TypeAS修饰、interface runtime/shape全套或abstract运行时构造。自评4/3/4/4/4平均3.8，whole17未完成。

## 阶段 86：actual generic父项绑定

8prod+fixture99/helper2+bin共11文件175+/35-。完整父文本在声明环境解析/tryResolve，actual T/N冻结绑定后解析；两Info nullable parentExpressions保存source权威，避免复制specialized父模型。Project INDEX hook与StringTest同阶段共享，lib/std显式普通父关系纳入parent-first complete；flatExtends只排CompiledGeneric父origin readonly名的var/property，保留其余成员。裸Marker interface仅canonical父恢复，不扩source object/interface实际generic父。

MCFL19；stdlib独立SUCCESS36s/compiler0/0，三artifact292301 bytes/header4c46434d13000000/SHA256 097F4A5ABA51792E4F748D25DBE0459BC38F03E97C2339358C763DF92125938C。RED172 fatal FAILED9s/无freshXML，三父全文undefined后Var633 NPE；173 joint3 Lib2fail/Cache1green（13:26:27.137Z/13:26:26.471Z），READ_LIB与complete共2BossBar错误；174 joint3 13:37:09.387Z三fail，仅complete各1error。175/176诊断单1各FAILED8s（13:44:53.283Z/13:51:11.734Z），先误只查std无表，后BossBar无parent；root确认StringTest未调用INDEX hook，不能声称tryResolve单独修好。TEMP全部删除。

final3 worker177正常exit0/SUCCESS1m1s，fresh XML2026-10-06T14:00:39.735Z联合3全绿。新86 source0errors/9118warnings、fresh0/9121warnings；source盘4/9/bool1/5、fresh4/9/bool1/10/frame0，模型/owner/TypeID及Shift4→Offset5、Shift9→Offset10全达。旧85正例source0/9118 fresh0/9119；三负例各source1/9118、fresh1/9119为expected拒绝。cache在173绿，跨轮4不同case各过、最终仅联合3。警告含source、均既有flatExtends重复类别，未解决。无fullcheck/server，85提交7826eebf95df9a2d9f2efdbb774f761661b2a282，86提交见Git历史。

阶段87已实现并限定验证：`TypeUsage` 统一判定接入源码声明入口、已绑定普通签名、IR/擦除值与集合、延迟字段；普通变量/字段/参数/返回及擦除/集合路径拒绝 `TypeValue`。`typealias`、内部 `TypeVar` 解析和现有readonly泛型绑定保留。依赖普通 `type` 存储的旧30个正例撤回，4个合法的直接泛型类型表达式库往返fixture保留；验证分轮详见verification.md，19个不同用例跨轮各自通过，非单次全套通过。底层runtime carrier修复保留 `T!` 语言类型与常量要求；MCFL19/bin292301不变，无stdlib/fullcheck/server。

| 自评轴 | 评分 | 本阶段依据 |
| --- | --- | --- |
| 准确性 | 4/5 | 实际XML与source/fresh磁盘证据，明确受限范围与警告 |
| 完整性 | 3/5 | whole17/MNI等仍未完成 |
| 清晰性 | 4/5 | MCFL19、分轮检查与负例expected错误分别记录 |
| 可执行性 | 4/5 | 共享INDEX hook，87续接范围明确 |
| 简洁性 | 4/5 | 最小父绑定/readonly排除修复，但诊断经历多轮 |

平均3.8/5。

## 后续仍需完成

- 标量/擦除及可编码 list/dict/map/ImmutableList/NBT 数组、范围值和已证明整数端点的命名范围迭代已迁入 IR，26.3 原生浮点、short/double/nbt 载荷、标量/数组显式转换及 map 两种投影也已接入；旧浮点 IR 最终复查20项通过，旧return ABI保留。阶段58限定语法域实现已提交`e6f83fe`；阶段59导入声明环境已持久化namespace/unsolvedImports，MCFL13重建后50项联合通过；阶段60限定消费端body导出已实现并13项联合通过；阶段61受支持模板方法owner/scope已恢复；阶段62模块资源路径已修复并通过限定回归；阶段63函数accessModifier已持久化并通过MCFL14库往返14项联合验证；阶段64仅新增PropertyInfo.accessModifier持久化并通过MCFL15定向验证。阶段66已验证受限的当前实例未限定字段寻址；阶段67已接通Box<N as int> readonly签名、源码特化和namespace generic类别；阶段68已限定支持未注解普通generic class实例化后绑定T；阶段69已限定显式generic类型签名与跨库canonical特化。阶段70声明scope及绑定T/N限定路径已验证，36个不同用例跨轮各自通过（29+Logic6+最终1），MCFL17/bin289989未变；阶段71有限Declaration/Applied TypeValue恢复已验证，6个不同用例跨轮各自通过，最终必要2项复查；阶段72完整静态类型集合限定路径已验证，5个不同用例跨轮各自通过；阶段73冻结Specialized类型值已限定验证，最终必要联合2项全过；阶段74源码联合类型与冻结身份已限定验证，必要联合4项全过；阶段75冻结向量TypeValue已限定验证，必要联合2项全过；阶段76冻结SelectorTypeValue已限定验证，最终仅1项通过；阶段77 direct Union TypeValue表达式已限定验证，最终必要1项通过；阶段78 alias有根匿名冻结身份已限定验证；阶段79 generic用户函数自身readonly依赖签名已限定验证，最终必要联合5全绿；阶段80 generic object静态N/方法已限定验证；阶段81 generic interface静态TypeValue已限定验证，三个case跨轮各过、最终新1复查；阶段82 source磁盘导出已限定验证、最终联合3全绿；阶段83 generated-vs-legal名字隔离限定通过，四case跨轮各过/最终新1；阶段84 generic object静态字段/显式constructor已限定通过，最终联合2全绿；阶段85 source/fresh abstract/final flags已限定验证，MCFL18/stdlib287554、最终联合3全绿；阶段86 actual generic父项限定通过、MCFL19/stdlib292301、最终联合3全绿；阶段87当前严格检查type声明范围；generic object字段/显式init已限于阶段84验证，compiler-only字段/直接库fieldtype未验及generic interface未验继承/运行时布局/转换/annotations、annotations、source abstract/final flags已按85限定验证（TypeAS仍未扩），generic用户函数自身readonly依赖Box<(T)> normal/return已限定验证，其余dependent类型表达式/重载等价尚未覆盖、Union runtime布局、Vector runtime布局、Selector isName=true/empty source/world runtime、Opaque及全集身份、完整Kryo身份仍未覆盖。imported object自动load、其他未迁入集合成员、编译器专用集合、未知端点范围形参/浮点范围/通用迭代器、模板/泛型/T!等尚未统一。旧转换和DataObject等来源仍走适配；无宏目标上未知长度的负数字面下标仍走旧边界。
- 参数相关 static 已知子位置、未知列表范围、普通集合返回/static 整体替换子形状与递归效果不动点已接入受限 IR 图；同一类型/形状输入的递归返回及写回已求解，输入变化仍保守。继续扩展其余集合、成员、全局、实体及全部调用位置。无法证明的函数仍采用未知屏障，原始命令跨函数修改物理记分板与帧恢复仍需核实。
- 未知字典字符串键的运行时路径后端、其余原生成员/集合的编码能力检查、实体路径、全部布局访问诊断、模板方法与构造仍需完成迁移；map 的字符串值键和可编码投影已接入，但编译器专用值及其余控制语句仍需扩展。本轮递归样例不代表完整帧分配覆盖全部类型。
- MNI 显式上下文、值/位置接口及其余成员签名统一未完成；Concrete 体系、hasStoredInStack、trackLost 等旧状态仍存在。
- 未配置实际 Minecraft 服务端；独立执行器通过不等于实际目标验证。阶段58有37个不同必要测试用例跨轮各自通过，不是联合37项。仍缺服务器验证及其余类型系统迁移工作。

下次优先执行 [下一阶段计划](./next-stage-plan.md) 中标出的剩余工作。整个类型系统重构尚未完成。

## 本轮自检

阶段72补充：5个不同用例跨轮各自通过（首轮4绿+新方法最终1），source/fresh canonical及真实磁盘4/9/frame0到达；最终单方法复查不写成联合5。阶段73最终必要联合2项全过，source/fresh Holder<Cell<int>>模型及磁盘4/9/frame0各到达。阶段74必要联合4项全过，仅typealias静态Union身份恢复与consumer磁盘得到验证。这是阶段74时的边界；direct Union表达式后来由77限定验证，Union runtime布局及其余独立边界仍未完成。阶段78四个不同用例跨轮各自通过，最终仅新1；准确记录fatal无fresh XML、fixture语法失败及最终source/fresh模型和磁盘4/9/frame0，完整性仍3/5。

平均3.8/5；阶段58限定语法域复用IR私有图Lowering/FlowAnalysis，阶段59 MCFL13和联合50项通过，阶段60有包含磁盘consumer执行的13项检查全绿；阶段61首轮26项24/2后定向复查2项通过，阶段62目录/JAR/ZIP联合11项全过；阶段63 MCFL14函数权限库往返14项联合全绿；阶段64 MCFL15字段权限/while帧后最终7项全过；阶段65权限11项、阶段66实例字段/构造器11项分别联合全绿；阶段67标准库0/0并MCFL16，41个不同用例跨轮各自通过；阶段68未注解generic类T绑定17个不同用例跨轮各自通过；阶段69标准库0/0、MCFL17且19个不同用例跨轮各自通过，含反序consumer磁盘/TypeId/owner；阶段70共36个不同用例跨轮各自通过（29+Logic6+最终1），提交记录见Git历史，MCFL17/bin289989未变；阶段71六个不同用例跨轮各自通过，最终必要2项复查达成source/fresh canonical及磁盘4/9/7/frame0。完整性保持3/5：generic用户函数自身readonly依赖Box<(T)> normal/return已限定验证，其余dependent类型表达式/重载等价尚未覆盖、Union runtime布局、Vector runtime布局、Selector isName=true/empty source/world runtime、Opaque及全集身份、generic object字段/显式init已限于阶段84验证，compiler-only字段/直接库fieldtype未验及generic interface未验继承/运行时布局/转换/annotations/annotation/T!其他语义、imported object autoLoad、其余成员与IR迁移及实际服务器验证仍未完成。

| 维度 | 评分 | 证据与改进 |
| --- | --- | --- |
| 准确性 | 4/5 | 阶段58分轮37项、59联合50项、60联合13项（含consumer磁盘执行）、61跨轮26项、62联合11项、63联合14项、64跨轮30个不同用例、65联合11项、66联合11项、67跨轮41个、68跨轮17个、69跨轮19个、70跨轮36个、71跨轮6个、72跨轮5个不同用例及73最终必要联合2项及74必要联合4项及75必要联合2项与76最终单方法1项、77最终必要1项、78跨轮4个不同用例/最终新1及79最终必要联合5分别记录（79试final为Kotlin编译失败，无worker/fresh XML）；明确区分分轮各自通过与单次联合，包含红测/worker异常及fresh XML证据；80三用例跨轮各过、最终新1，明确过强无this断言为fixture错误且不计旧XML；81 RED无fresh XML，试joint3漏ABSTRACT是生产缺陷，最终新1绿/三case跨轮各过，80旧ObjectMethods不计第四case；82 RED真实磁盘缺Box init，最终新82/旧80/普通ObjectMethods联合3全绿，source与consumer证据分别记录；83 RED无结果断言、试joint4实际结果先绿但ROOT owner-prefix夹具失败，最终新1/跨轮4case准确区分 |
| 完整性 | 3/5 | 函数accessModifier、限定字段/Property权限和当前实例未限定寻址已有受限验证；Union runtime布局、Vector runtime布局、Selector isName=true/empty source/world runtime、Opaque及全集身份、generic用户函数自身readonly依赖Box<(T)> normal/return已限定验证，其余dependent类型表达式/重载等价尚未覆盖、generic object字段/显式init已限于阶段84验证，compiler-only字段/直接库fieldtype未验及generic interface未验继承/运行时布局/转换/annotations与annotations、T!扩展、imported object自动load、集合/MNI与服务器验证仍未完成 |
| 清晰度 | 4/5 | 区分标准库编译警告、语言诊断与测试夹具错误，历史保留 |
| 可操作性 | 4/5 | 阶段62目录/ZIP/JAR复制11项通过；阶段63三套件MCFL14权限往返14项通过；阶段64字段/逻辑7项、阶段65权限11项、阶段66实例字段与构造器11项全绿；阶段67 MCFL16及最终5项（含旧float ABI）全绿、41个不同用例跨轮各自通过；阶段68完成未注解Cell<T as type>限定consumer/磁盘验收；阶段69完成MCFL17显式generic类型签名与跨库往返验证；阶段70声明scope及绑定T/N限定路径完成模型和磁盘4/6/8/bool1/frame0验证，36个不同用例跨轮各自通过；阶段71source/fresh Leaf及Applied类型canonical、真实磁盘4/9/7/frame0达成，最终必要2项复查；阶段78匿名alias字段注解、透明alias及合法named声明隔离的canonical/快照与consumer磁盘4/9/frame0达成，阶段79联合5通过，依赖签名及readonly完整冻结绑定达到实际磁盘4/9/7/frame0；80 canonical静态object/方法与consumer真实磁盘4/9/4/frame0通过，source仅模型/库写入；81 abstract接口绑定和readonly canonical TypeValue/model及consumer磁盘4/9/frame0通过，82 source实际目标文件及磁盘4/9/4/9/frame0通过，最终联合3全绿；83 generated-vs-legal target/owner prefix及source盘4/9/4/9/frame0通过；84静态字段/显式constructor最终联合2全绿，下一85 abstract/final flags待RED |
| 简洁性 | 4/5 | 只更新当前阶段事实与交接，不重写历史记录 |

当前优先改进：阶段92计划删除44个已被统一拒绝的byte/short算术注册并升级MCFL24；阶段91只验证指定int/float/bool运算符。阶段87普通值位置type拒绝规则继续生效；其他MNI入口、legacy循环和whole17仍未完成。
自检：用户能复核实现和测试，也会看到整项重构仍未结束；没有把阶段通过写成项目全部完成。

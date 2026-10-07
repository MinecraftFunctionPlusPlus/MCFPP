# 当前阶段验证记录

最新状态日期：2026-10-07（Asia/Shanghai）。当前阶段127，MCFL57；Std外剩余4个Java类含19个CommandReturn注解与7个旧void wrapper，whole17仍ACTIVE未完成。`type` 仅能作为泛型参数；普通 typed/inferred/const 变量、data/object 字段、普通参数与返回值，以及擦除值和集合中的 `TypeValue` 均拒绝。`typealias`、内部 `TypeVar` 解析和现有 readonly 泛型绑定保留；普通值位置一律拒绝。

## 当前阶段 127：玩家消息命令（已限定验证）

`PlayerMessageData`将`tell`与`w`接入单context qualified结果，caller为entity并挂到`EntityData`第九个`@From`；旧入口缺caller且未发布结果，不称保留了正确旧ABI。唯一fixture为`nativePlayerMessageCommandsCaptureResultsAcrossLibraryRoundTrip`，Cache方法为`oldAndUnknownCacheFormatsProduceARecompileDiagnostic`，日志前缀`mcfpp-native-player-message-command-results-`。标准库SUCCESSFUL in12s、Project0/0；joint原生exit0、worker49正常结束、SUCCESSFUL in28s。LibFieldAccess XML `2026-10-07T06:30:55.781Z` 与Cache XML `2026-10-07T06:30:54.980Z` 均1/0/0/0；source/fresh Project0/9118与0/9119，negative6/9119（player-selector guard与未发布结果级联）。MCFL57三份bin均478528 bytes、header `4c46434d39000000`、SHA256 `3AF8C24128274596ECE416E7353C945F1FAA34C5EAB7627D32D23D7B60EB53D6`。

source/fresh确认两条`execute as sender run tell|w targets message`宏，message由真实参数绑定进入宏槽；每次调用一个独立root和一次初始化，整体execute结果只捕获一次。sender限制player selector是项目API选择；不表示每个sender分别返回结果。负向覆盖`@e` sender、`@e` targets与DTO receiver；不模拟Minecraft消息发送，不声称world执行或frame0。

标准库重建原生退出码也为0；本批11个提交路径含6个源码/测试文件（110+/16−）、bin及四份文档。fixture读取observe和两个实际宏文件，消息来源为直接绑定或严格唯一一跳复制，复制先于参数准备、准备先于唯一调用。两个规范readonly结果及实际字段读取均无完整快照，结果事实保持Unknown；两个选择器保持真实kind，无普通参数特化。

| 自评维度 | 分数 | 本阶段证据与范围 |
| --- | --- | --- |
| 准确性 | 4/5 | 对照两份fresh XML、Project计数和bin身份。 |
| 完整性 | 3/5 | 覆盖两个消息入口的生成合同；world发送及whole17仍未完成。 |
| 清晰性 | 4/5 | 区分sender API限制与targets命令合同。 |
| 可操作性 | 4/5 | 下一步限定到四个实体属性入口。 |
| 简洁性 | 4/5 | 保留必要的构建、fixture与资源证据。 |

平均3.8/5；whole17仍未完成。

## 历史必要检查：阶段 126 实体间传送命令（已限定验证）

`EntityTeleportData`将entity-target `tp`接入单context qualified结果并挂到`EntityData`；旧entity<1>目标重载删除，坐标tp与setSpawnpoint保持不变。唯一fixture为`nativeEntityTeleportCommandsCaptureResultsAcrossLibraryRoundTrip`，joint Cache方法为`oldAndUnknownCacheFormatsProduceARecompileDiagnostic`。标准库SUCCESSFUL in11s、Project0/0；joint worker48正常、SUCCESSFUL in24s。LibFieldAccess XML `2026-10-07T06:18:09.483Z` 与Cache XML `2026-10-07T06:18:08.748Z` 均1/0/0/0；source/fresh Project0/9118与0/9119，negative4/9119。MCFL56三份bin均476974 bytes、header `4c46434d38000000`、SHA256 `0AE3A1AA1E626CECC2FCB1A55D5BE6141DEC31AD48C89C7F86EBB5BB8F0922FC`。

fixture实际验证`@a`到`@p`两次直接调用、两个独立root、一次`{}`初始化与双store；结果canonical readonly/Unknown/null，实际selector kind保持ALL_PLAYERS与NEAREST_PLAYER。DTO receiver与多实体destination在capture前拒绝；receiver可为multi。source/fresh生成合同通过，不验证Minecraft world执行、frame0或额外函数返回语义。

两次构建原生退出码均为0；日志前缀`mcfpp-native-entity-teleport-command-results-`。source/fresh均按observe的实际namespaceID读取磁盘文件，两份结果各自仅初始化一次，result为int 1、success为byte 1，实际字段读取也无完整快照；无普通参数特化。四条负向诊断为两个selector guard及两个未发布结果级联诊断。

| 自评维度 | 分数 | 本阶段证据与范围 |
| --- | --- | --- |
| 准确性 | 4/5 | 对照两份fresh XML、source/fresh计数和三份bin身份。 |
| 完整性 | 3/5 | 覆盖实体目标tp限定路径；坐标tp、whole17及world执行未完成。 |
| 清晰性 | 4/5 | 区分receiver多选、destination单选与负向guard。 |
| 可操作性 | 4/5 | 下一步限定为Player消息命令及整体execute结果。 |
| 简洁性 | 4/5 | 保留必要构建、fixture与资源证据。 |

平均3.8/5；whole17仍未完成。

## 历史必要检查：阶段 125 Player 状态与骑乘命令（已限定验证）

`PlayerVarData.clear()`、`clear(string,int)`、`setGamemode`及`EntityRideData.ride`接入单context结果捕获，保留clear语言重载；`PlayerStateData`新增到`EntityData`。Player命令先检查player selector，Gamemode要求`EnumVarConcrete`并输出identifier；ride要求实际receiver与target均为单实体selector。标准库SUCCESSFUL in12s、Project0/0；joint worker47正常、SUCCESSFUL in25s。LibFieldAccess XML `2026-10-07T05:58:41.992Z` 与CacheFormat XML `05:58:41.069Z` 均1/0/0/0；source/fresh Project0/9118与0/9119，negative12/9119，包括2个player guard、1个enum guard、3个ride双端guard及6个未发布结果级联诊断。source/fresh检查clear动态id/count参数宏、clearall/gamemode/ride direct命令；不模拟世界命令或frame0。三份bin均MCFL55、476213 bytes、header `4c46434d37000000`、SHA256 `09DC39D48459E09CCC2B09BFD8184EC0A036670AF74362B6A382768D7958614D`。日志前缀`mcfpp-native-player-state-ride-command-results-`。

本阶段13个提交路径含8个源码/测试文件（155+/29−）、bin及四份文档；唯一fixture为`nativePlayerStateAndRideCommandsCaptureResultsAcrossLibraryRoundTrip`，联合`oldAndUnknownCacheFormatsProduceARecompileDiagnostic`。

联合检查原生退出码0。生产EntityData显式导入Gamemode，语言clear重载使用各自Java方法名；count均显式传入，默认注解未接通。source/fresh按真实绑定核对字符串来源和整数读取、复制、编码、宏参数准备顺序；四次结果捕获各有独立root及一次初始化，readonly结果及实际字段读取保持Unknown与空快照，三个bare选择器保留实际kind。

| 自评维度 | 分数 | 证据及改进方向 |
| --- | --- | --- |
| 准确性 | 4/5 | 对照两个fresh XML、source/fresh计数和负向诊断。 |
| 完整性 | 3/5 | 覆盖指定状态与ride路径；predicate组合及whole17仍未完成。 |
| 清晰性 | 4/5 | 区分player/enum/ride guard和命令生成边界。 |
| 可操作性 | 4/5 | 下一步限定entity tp目标和single-selector合同。 |
| 简洁性 | 4/5 | 记录必要的测试、计数和资源证据。 |

平均3.8/5；whole17仍未完成。

## 历史必要检查：阶段 124 Player 成就命令（已限定验证）

`PlayerVarData`十个grant/revoke入口接入单context qualified `CommandResult`，新增`PlayerAdvancementData`挂到`EntityData`。使用qualified `Advancement`并读取真实`ResourceID.id`；capture前检查player selector。标准库SUCCESSFUL in11s、Project0/0；joint worker46正常、SUCCESSFUL in26s。LibFieldAccess XML `2026-10-07T05:43:00.220Z` 与CacheFormat XML `05:42:59.387Z` 均1/0/0/0；source/fresh Project0/9118与0/9119，negative4/9119（2条player guard及`grantAll`/`revokeAll`未发布结果级联诊断）。source/fresh检查8个动态id宏及2个everything direct调用；不模拟world/criterion或frame0。三份bin均MCFL54、473046 bytes、header `4c46434d36000000`、SHA256 `E2084436826232344C6DE71EC93211F4B5F409E0C5065C2BFF8DF8ECE155F1BB`。日志前缀`mcfpp-native-player-advancement-command-results-`。

本阶段11个提交路径含6个源码/测试文件（153+/60−）、bin及四份文档；唯一fixture为`nativePlayerAdvancementCommandsCaptureResultsAcrossLibraryRoundTrip`，联合`oldAndUnknownCacheFormatsProduceARecompileDiagnostic`。不测试criterion或Minecraft world结果。

联合检查原生退出码0。八个宏参数均核对实际Advancement.id或唯一wholeDTO副本的.id来源；十次调用各有独立root及一次初始化、同root双store int1/byte1，canonical readonly结果为Unknown且整体与字段读取快照为空，已知ALL_PLAYERS选择器在连续调用后保持实际kind，无泛型特化。

| 自评维度 | 分数 | 证据及改进方向 |
| --- | --- | --- |
| 准确性 | 4/5 | 与fresh XML、negative计数和资源版本核对。 |
| 完整性 | 3/5 | 十个入口命令生成路径通过；world/criterion及whole17未完成。 |
| 清晰性 | 4/5 | 区分动态宏、everything direct与玩家guard。 |
| 可操作性 | 4/5 | 下一阶段收敛到clear/gamemode/ride三类入口。 |
| 简洁性 | 4/5 | 记录必要构建、测试及bin证据。 |

平均3.8/5；whole17仍未完成。

## 历史必要检查：阶段 123 Player XP 命令（已限定验证）

`PlayerVarData`六个XP入口接入单context qualified `CommandResult`，新增`PlayerXpData`挂到`EntityData`。add/set保留multiple-player合同，query要求single player；capture前验证Player selector。标准库SUCCESSFUL in12s、Project0/0；joint worker45正常、SUCCESSFUL in26s。LibFieldAccess XML `2026-10-07T05:29:58.371Z` 与CacheFormat XML `05:29:57.554Z` 均1/0/0/0；source/fresh Project0/9118与0/9119，negative6/9119（3条selector guard及3条未发布结果级联诊断）。source/fresh均检查四个amount宏和两个直接query；不模拟世界XP值或frame0。三份bin均MCFL53、459939 bytes、header `4c46434d35000000`、SHA256 `D6DC92AA4C19913AFBD97C11F6608D4F42FBB9A0854A5E78FA3B36E2AE13D46D`。日志前缀`mcfpp-native-player-xp-command-results-`。

本阶段11个提交路径含6个源码/测试文件（147+/41−）、bin和四份文档。唯一fixture为`nativePlayerXpCommandsCaptureResultsAcrossLibraryRoundTrip`，联合`oldAndUnknownCacheFormatsProduceARecompileDiagnostic`；三条负向调用分别报告`Experience queries require a single-player selector`或`Command requires a selector that only includes players`，并各有未发布结果的级联诊断。

联合检查原生退出码0。每个动态amount宏按实际参数绑定核对NBT读取、score复制、NBT编码、宏参数准备到调用的顺序；六次调用各有独立root及一次初始化，canonical readonly结果保持Unknown与空快照，实际字段读取也没有伪造已知值；两种bare玩家选择器保留实际kind。

| 自评维度 | 分数 | 证据及改进方向 |
| --- | --- | --- |
| 准确性 | 4/5 | 对照fresh XML、source/fresh计数和三种负向场景诊断。 |
| 完整性 | 3/5 | 六个XP入口的生成路径通过；世界XP值及whole17未完成。 |
| 清晰性 | 4/5 | 区分multiple-player修改与single-player查询合同。 |
| 可操作性 | 4/5 | 下一步限定Player advancement十入口及selector要求。 |
| 简洁性 | 4/5 | 保留所需构建、XML和资源证据。 |

平均3.8/5；whole17仍未完成。

## 历史必要检查：阶段 122 停止骑乘命令（已限定验证）

11个提交路径含6项源码/测试（88+/6−）、bin及四份文档。唯一新增fixture `nativeEntityStopRideCommandsCaptureResultsAcrossLibraryRoundTrip` 与 `oldAndUnknownCacheFormatsProduceARecompileDiagnostic` 联合通过，原生退出码0；日志前缀为 `mcfpp-native-entity-stop-ride-command-results-`，保留stdlib/final日志。source/fresh检查同一已知SELF选择器两次调用、canonical readonly结果的Unknown事实与空快照、两个独立捕获root及各一次初始化。

`EntityRideData.stopRide`使用单context结果捕获；capture前要求receiver是已证明的单实体selector。stdlib Project0/0、SUCCESSFUL in10s；joint worker44正常、SUCCESSFUL in24s。LibFieldAccess XML `2026-10-07T05:14:47.255Z` 与CacheFormat XML `05:14:46.352Z` 均1/0/0/0；source/fresh Project0/9118与0/9119，negative4/9119，两处各产生selector guard及未发布结果诊断。三份bin均MCFL52、455477 bytes、header `4c46434d34000000`、SHA256 `71BC9EF7BE9E5257B52242542FF6FB431D64EF725CC6C75DF2C27DC768C84D5B`。测试仅检查磁盘命令与负向guard，不模拟世界骑乘或frame0。whole17仍未完成。

| 自评维度 | 分数 | 证据及改进方向 |
| --- | --- | --- |
| 准确性 | 4/5 | 依据本轮两个fresh XML、Project计数和实际guard诊断。 |
| 完整性 | 3/5 | 仅覆盖stopRide限定路径；世界行为与whole17未完成。 |
| 清晰性 | 4/5 | 区分命令导出、selector guard与世界执行边界。 |
| 可操作性 | 4/5 | 下一步限定六个Player经验入口及selector范围。 |
| 简洁性 | 4/5 | 只记录必要测试和资源证据。 |

平均3.8/5；whole17仍未完成。

## 历史必要检查：阶段 121 Entity effect 授予命令（已限定验证）

`EntityVarData.effect`与`effectInfinite`两个入口迁入单context qualified `CommandResult`。常量bool直接生成true/false字面量；动态score读取当前score，未知bool在MCString临时值中先写`false`，条件成立后覆写`true`再捕获参数。默认注解不等于默认实参已接通。

标准库SUCCESSFUL in10s、Project0/0；joint worker43正常、SUCCESSFUL in25s。LibFieldAccess XML `2026-10-07T04:59:00.799Z` 与CacheFormat XML `04:59:00.083Z` 均1/0/0/0；source/fresh Project0/9118、0/9119，DTO negative2/9119。指定fixture与Cache回归通过；仅验证受测命令/参数准备，不代表Minecraft世界效果、完整返回行为或帧恢复。

10个提交路径含5项源码/测试（174+/13−）、bin及四份文档。唯一新增fixture `nativeEntityEffectGiveCommandsCaptureResultsAcrossLibraryRoundTrip` 与 `oldAndUnknownCacheFormatsProduceARecompileDiagnostic` 联合通过；日志前缀为 `mcfpp-native-entity-effect-give-command-results-`，保留stdlib/final两份日志。source/fresh各真实执行bool准备前缀，输入0b/1b时两处宏参数均为字符串false/true；同时核对常量分支、独立结果捕获与DTO拒绝。三份bin均MCFL51、454764 bytes、header `4c46434d33000000`、SHA256 `5BD4DF48FD5BD8553AB4D5D091566E2187F7A2D22F39D69E1017A01D752E6FD1`。

| 自评维度 | 分数 | 证据及改进方向 |
| --- | --- | --- |
| 准确性 | 4/5 | 新XML与日志一致，实际执行bool准备；后续需验证世界效果。 |
| 完整性 | 3/5 | 两个授予入口已受测；其余legacy入口与whole17仍待迁移。 |
| 清晰性 | 4/5 | 区分常量文字、动态字符串准备与MNI默认参数限制。 |
| 可操作性 | 4/5 | 记录唯一fixture与日志，下一步限定stopRide。 |
| 简洁性 | 4/5 | 只运行必要联合检查并保留本轮证据。 |

平均3.8/5；whole17仍未完成。

## 历史必要检查：阶段 120 EntityEffect 清除命令（已限定验证）

11个提交路径含6项源码/测试、bin及四份文档；源码/测试新增125行、删除13行。本轮标准库重建后，指定fixture与缓存格式回归联合通过。

`EntityVarData.clearEffect`与`clearAllEffects`接入单context qualified `CommandResult`，读取真实`Effect.id`并在capture前检查Selector。单fixture检查specific-effect macro、all-effects direct、真实字段/参数来源、source/fresh合同及DTO负例；仅限受测命令路径。

标准库SUCCESSFUL in10s、Project0/0；joint worker42正常、SUCCESSFUL in25s。LibFieldAccess XML `2026-10-07T04:42:55.557Z` 与CacheFormat XML `04:42:54.761Z` 均1/0/0/0；source/fresh Project0/9118与0/9119，negative2/9119，预期诊断为`Entity effect commands require a selector receiver`与`Native function 'clearAllEffects' did not publish its result`。MCFL50三份资源451715 bytes、header `4c46434d32000000`、SHA256 `D09790EA77B67656159415475C1E9B9355D871D39C8218D7278D6E4FCAC01D9B`。不涉及world、Executor、frame0或fullcheck/server。

| 维度 | 评分 | 证据与改进 |
| --- | --- | --- |
| 准确性 | 4/5 | 记录两fresh XML、Project计数及两条预期负向诊断。 |
| 完整性 | 3/5 | 仅两个effect清除入口受测；world行为和whole17未完成。 |
| 清晰性 | 4/5 | 区分specific macro与all-effects direct合同。 |
| 可执行性 | 4/5 | 下一阶段只列两个effect授予入口并给出bool捕获边界。 |
| 简洁性 | 4/5 | 限定记录验证范围，不外推到其他effect/selector路径。 |

平均3.8/5；whole17仍未完成。

## 历史必要检查：阶段 119 EntityTeam 命令结果（已限定验证）

`EntityTeamData.joinTeam(Team)`与`leaveTeam()`接入单context qualified `CommandResult`。join先读取真实`Team.id`再传`@a`，leave移除旧伪Team参数。93行source/fresh fixture验证canonical readonly/unknown模型、整块普通参数副本至id宏slot/单次调用、两个结果root/双store/一次初始化及DTO负例。

stdlib SUCCESSFUL in11s，Project0/0。首轮joint worker40正常、FAILED19s；LibFieldAccess XML `2026-10-07T04:24:52.599Z` 1/1/0/0，source Project0/9118后严格Team.id参数准备断言失败，未到fresh/negative；Cache XML `04:24:51.843Z` 1/0/0/0。仅fixture修为检查真实参数副本来源后，final2 worker41正常、SUCCESSFUL in21s，XML `2026-10-07T04:28:36.594Z` 1/0/0/0，source/fresh Project0/9118与0/9119，negative2/9119。MCFL49三份资源449537 bytes，header `4c46434d31000000`，SHA256 `4ED10AC79FF327A131A85E1FBDC5205A0807E0BBD3E83A38373331B536EEC2B1`。不涉及world执行或frame0。

| 维度 | 评分 | 证据与改进 |
| --- | --- | --- |
| 准确性 | 4/5 | 区分首轮参数断言失败与fixture修正后单项复查；Cache仅首轮验证。 |
| 完整性 | 3/5 | 仅两个Team命令入口受测，其他入口及whole17未完成。 |
| 清晰性 | 4/5 | 列明参数来源、导出合同和未覆盖的world执行。 |
| 可执行性 | 4/5 | 下一阶段限定为两个Entity effect清除入口及selector检查。 |
| 简洁性 | 4/5 | 保留足以复核的日志时间和边界，不扩写项目历史。 |

11个提交路径含6项源码/测试、bin及四份文档。MCFL49的Cache仅首轮通过；最终只复查fixture，无额外stdlib、fullcheck或server验证。

平均3.8/5；whole17仍未完成。

## 历史必要检查：阶段 118 EntityTag 命令结果（已限定验证）

`EntityVarData.addTag`、`removeTag`、`listTag`三个入口迁入`EntityTagData`，使用单context qualified `CommandResult`。grammar只新增`ENTITY`命名空间片段；修正SelectorData的27个旧ABI注解caller/return为entity及6个qualified resource参数，Selector公共父模板接入已加载EntityData。MNI仅在显式qualified解析失败后查询已加载canonical模板。bare Selector保存/恢复immutable完整快照，但不代表predicate selector等形式均支持。

stdlib1成功11s/Project0/0；首轮producer因`ENTITY`保留字导入解析失败，Cache XML `2026-10-07T03:22:31.233Z` 1/0/0/0是MCFL48唯一Cache验证。grammar修复后stdlib2成功26s/Project0/0；final2 producer189/9118，final3 producer9/9118。final4重复执行并覆盖，现存worker37、FAILED6s及XML `2026-10-07T03:52:30.253Z` 1/1/0/0、producer8/9118；worker36首轮日志/XML已丢失。final5-debug worker38 FAILED28s，XML `2026-10-07T04:04:19.204Z` 1/1/0/0、producer8/9118；临时诊断显示bare Selector绑定后成为Unknown。最终修复后final6 worker39正常、BUILD SUCCESSFUL in39s，XML `2026-10-07T04:08:58.285Z` 1/0/0/0；source/fresh Project0/9118、0/9119，negative 2/9119。负向诊断为`Entity tag commands require a selector receiver`及`Native function 'listTag' did not publish its result`；source/fresh模型及consumer合同通过。未验证world、executor、frame0或fullcheck/server。

18个提交路径含13项源码/测试、bin及四份文档。三份资源均为MCFL48、445602 bytes，header `4C46434D30000000`、SHA256 `A5F124E496774A0D0BE2A4F1D87044708338B50F5378C3DB62DDA1896FE559B6`。runtime修复后仅复查fixture，未再生成stdlib或重复Cache测试；临时诊断已删除。

| 维度 | 评分 | 证据与改进 |
| --- | --- | --- |
| 准确性 | 4/5 | 区分final4覆盖、诊断性复查与final6通过，标明Cache仅首轮。 |
| 完整性 | 3/5 | 三个EntityTag入口受测通过；其他入口及whole17未完成。 |
| 清晰性 | 4/5 | 记录bare Selector快照修复范围和negative实际诊断。 |
| 可执行性 | 4/5 | 下一步依据26.3命令报告处理Team加入/离开合同。 |
| 简洁性 | 4/5 | 保留关键失败边界，不扩写未测selector/world路径。 |

平均3.8/5；whole17仍未完成。

## 历史必要检查：阶段 117 Random 命令结果（已限定验证）

`RandomData.reset`、`RandomObjectData.reset`与`resetAll`三个入口接入单context qualified `CommandResult`；flags使用完整`CompilerValue.Bool`快照并保留seed int。修正实例/source flag顺序，普通NativeFunction分支传递readonly实参，ImVisitor raw调用经既有`buildMacroFunction`导出；`Random.mcfpp`三个旧raw命令去掉末尾斜杠。单参构造器是唯一实际source/fresh覆盖的构造器路径。

stdlib1 exit0/10s、Project0/0。final1 worker26崩溃、FAILED18s，XML仍是阶段116旧记录，不作为本轮结果。final2 worker27正常、FAILED14s；LibFieldAccess XML `2026-10-07T02:48:48.594Z` 1/1/0/0，Cache XML `02:48:47.775Z` 1/0/0/0，为MCFL47唯一Cache验证；source Project0/9118后datapack导出失败，fresh consumer尚未运行。final3 worker28正常、FAILED10s，宏调用未展开；final4 worker29正常、FAILED13s，单参构造器raw命令尾斜杠断言失败。stdlib2 exit0/4s、Project0/0。final5 worker30正常、FAILED16s，正向source/fresh通过，负向仍因诊断数量不符失败。修改负向预期后final6仅复查fixture：worker31正常、BUILD SUCCESSFUL in21s；XML `2026-10-07T03:08:42.235Z` 1/0/0/0，source/fresh Project0/9118与0/9119，negative Project2/9119；两条预期诊断为完整编译期值缺失及级联`Symbol not defined: flag`，且无命令/结果发布断言通过。三份资源MCFL47、296252 bytes、header `4c46434d2f000000`、SHA256 `8D569CF61A35D3D7A7D6371C7C6E6FD4F3AFDE3C09948FD2BBD3BBA2C244547B`。无fullcheck/server/world/frame0，whole17仍未完成。

### 阶段 117 自检

| 维度 | 评分 | 证据与改进 |
| --- | --- | --- |
| 准确性 | 4/5 | 区分worker崩溃、四次fixture问题和最后通过，明确negative的两条具体诊断。 |
| 完整性 | 3/5 | 三个Random入口及单参构造器限定路径通过；whole17仍未完成。 |
| 清晰性 | 4/5 | 逐轮标明XML、source/fresh及Cache只final2验证。 |
| 可执行性 | 4/5 | 下一步收敛到EntityTag三入口及Selector caller合同。 |
| 简洁性 | 4/5 | 保留关键失败边界，不扩写其他Random入口。 |

平均3.8/5，whole17完整性仍为3/5。

## 历史必要检查：阶段 116 WorldObject 命令结果（已限定验证）

`WorldObjectData.setDifficulty`与`setWeather`两个静态native方法接入单context qualified `CommandResult`，无caller参数、default或readonly参数；capture前要求EnumVarConcrete，keyword使用`value.identifier`，weather duration为int。旧Time缓存及weather/time accessor/mutator未改，生产尚未导出World对象。source/fresh fixture用真实`@From FixtureWorld`检查两个canonical readonly/unknown结果及null snapshot、difficulty direct和weather macro；duration实际按NBT读取→score复制→NBT编码→macro slot捕获。

stdlib exit0/9s、Project0/0；首次联合两项 exit0/24s、worker25正常。LibFieldAccess XML `2026-10-07T02:30:25.319Z` 与Cache XML `02:30:24.427Z`均1/0/0/0；source/fresh Project0/9118、0/9119，negative Project4/9119为预期guard及未发布诊断。实际命令合同、双store、一次root初始化及无裸命令/return-run断言通过；不涉及world执行、executor或frame0，也不据注解推断可省略参数。三份bin均MCFL46、295914 bytes、header `4c46434d2e000000`、SHA256 `8D0FFE6F3302B53F99849D9978C38D30D05DBF067EA7DFE42CF3376E256EEB8F`。

### 阶段 116 自检

| 维度 | 评分 | 证据与改进 |
| --- | --- | --- |
| 准确性 | 4/5 | 记录静态无caller签名、枚举guard、参数捕获和negative边界。 |
| 完整性 | 3/5 | 两个WorldObject入口及磁盘合同通过；world执行与whole17未完成。 |
| 清晰性 | 4/5 | 分开标明source/fresh/negative计数和XML时间。 |
| 可执行性 | 4/5 | 下一步限定Random三个reset入口和不对称参数检查。 |
| 简洁性 | 4/5 | 只保留本阶段必要验证范围。 |

平均3.8/5，whole17完整性仍为3/5。

## 阶段 115：BossBar 命令结果（已限定验证）

BossBar.add/remove/listAll/setColor/setName/setVisiblePlayers/setStyle七个实例方法接入单context qualified `CommandResult`；capture前guard要求EnumVarConcrete，fixture使用literal，unknown enum会被拒绝，entity沿用SelectorVar玩家限定。DTO保留既有id:string并新增name:text；单参id构造器以`id.toText`初始化name，二参构造器为(string,text)。Java `list`更名`listAll`避开语言保留字，生成命令仍为bossbar list。EnumMemberInfo新增SNBT字符串保存enum member数据，读取时重建Tag；无新的runtime enum ABI或snapshot系统。

标准库首轮 `mcfpp-native-bossbar-command-results-stdlib.log` exit0/BUILD SUCCESSFUL in10s、Project0/0。首轮joint exit1/16s、worker22正常；LibFieldAccess XML `2026-10-07T02:09:58.178Z` 为1/1/0/0，producer解析`bar.list()`报3个语法错误；Cache XML `02:09:57.302Z` 为1/0/0/0（MCFL44）。改名listAll后stdlib2 exit0/8s、Project0/0；final2仅fixture复查exit1/17s、worker23正常，XML `2026-10-07T02:13:54.671Z` 为1/1/0/0，source0/9118但命令是`bossbar set $(arg_0) color null`，尚未到fresh consumer。EnumMemberInfo序列化修复使MCFL44→45；stdlib3 exit0/8s、Project0/0。final3仅fixture exit0/24s、worker24正常，XML `2026-10-07T02:17:15.845Z` 为1/0/0/0；source/fresh Project0/9118与0/9119，negative Project4/9119为预期guard及结果未发布错误。final3 fixture通过模型、真实参数捕获、命令与macro、readonly/unknown结果检查；六个macro加一个list direct，七独立root/双store/单初始化。Cache只在MCFL44首轮通过，未声称MCFL45 Cache已验证。三份产物MCFL45、295914 bytes、header `4c46434d2d000000`、SHA256 `79037281AAB839306736EED308260D0D9550C965B8308F594C9468A60F67A003`。没有world/executor/frame0、getter/mutator或静态list覆盖；whole17仍未完成。

### 阶段 115 自检

| 维度 | 评分 | 证据与改进 |
| --- | --- | --- |
| 准确性 | 4/5 | 分开说明语法、enum持久化失败原因及MCFL45最终fixture通过；Cache版本边界明确。 |
| 完整性 | 3/5 | 七个指定实例入口受测通过；其他Java入口及whole17未完成。 |
| 清晰性 | 4/5 | 明确列出每轮XML、source/fresh与预期negative计数。 |
| 可执行性 | 4/5 | 下一步限定WorldObject两个入口和一direct/一macro合同。 |
| 简洁性 | 4/5 | 保留范围内证据，未扩展world行为或旧访问器。 |

平均3.8/5，whole17完整性仍为3/5。

## 阶段 114：Worldborder 命令结果（已限定验证）

WorldborderData七个 `MNIFunction`（add、setCenter、setDamageAmount、setDamageBuffer、setSize、setWarningDistance、setWarningTime）迁入单context qualified `CommandResult`。保留参数、注解/default及命令词序，七个backend入口为薄捕获并复用 `FloatProviders`。`Pos2.hasRuntimeRepresentation=false`是计算属性：坐标只用于编译期表示，避免强制NBT编码；无serialized backing field或额外wire变化。fixture source/fresh检查七结果、readonly/unknown模型及六个macro的确切命令词序和参数槽；consumer生成一个direct center命令和六个macro，检查七独立root、双store和一次初始化。amount按真实binding path编码float snapshot；ticks按加载→score复制→NBT编码进入macro参数槽，不是直接从形参读取。

标准库 `mcfpp-native-worldborder-command-results-stdlib.log` exit0/BUILD SUCCESSFUL in34s，Project0/0。首轮joint `mcfpp-native-worldborder-command-results-final.log` exit1/BUILD FAILED in30s、worker19正常；LibFieldAccess XML `2026-10-07T01:49:16.349Z` 为1/1/0/0，producer1 error/9118 warnings，首错是Pos2坐标值在 `NativeFunction.invoke → StorageAccess.flush` 中进入不支持的NBT常量编码，未到命令断言/fresh consumer；CacheFormat XML `01:49:15.410Z` 为1/0/0/0。计算型representation能力修复后final2只复查fixture，exit1/BUILD FAILED in18s、worker20正常，仍在producer命令准备断言失败，XML `01:53:13.213Z` 为1/1/0/0、producer0/9118，fresh未到。调整fixture以检查amount与ticks的真实捕获链后，final3单fixture exit0/BUILD SUCCESSFUL in18s、worker21正常，XML `01:56:02.846Z` 为1/0/0/0；source/fresh Project分别0/9118与0/9119。Cache只首轮通过。三份bin均MCFL43、293933 bytes、header `4c46434d2b000000`、SHA256 `6307C3AFB83610B950BD3014DD4A97850542EA0ACB579908B358677AB6033EF1`。不涉及world、frame0、size accessors/mutators或WorldObjectData，也不据默认注解宣称参数可省略。Std外仍有8个Java类、64个CommandReturn注解和7个旧void wrapper，whole17保持ACTIVE未完成。

### 阶段 114 自检

| 维度 | 评分 | 证据与改进 |
| --- | --- | --- |
| 准确性 | 4/5 | 区分Pos2常量编码错误、source准备断言失败与final3成功，并精确描述ticks捕获链。 |
| 完整性 | 3/5 | 七个方法的受测路径通过；world、size accessors及whole17未完成。 |
| 清晰性 | 4/5 | 分轮列出XML、Project计数和Cache只首轮通过。 |
| 可执行性 | 4/5 | 下一阶段限定BossBar七项及enum/selector边界。 |
| 简洁性 | 4/5 | 保留必要验证与资源证据，没有扩写未测范围。 |

平均3.8/5，whole17完整性仍为3/5。

## 阶段 113：Op/Recipe player-target 命令结果（已限定验证）

Op.op/deop、Recipe.give/take/giveAll/takeAll六个入口接入单context qualified `CommandResult`，entity采用普通参数；捕获前由SelectorVar的onlyIncludingPlayers检查玩家限定。查询读取真实EntityType.id的编译期字符串，String工厂按模板要求构造字段值树；不再依赖旧CompoundTag/value构造及带引号的getTagStr比较。保留clone/query行为，不把裸@s或反向player筛选当作证明。Recipe读取DTO实际id；Op.deop现发出deop。source/fresh fixture检查四个direct命令、两个实际id macro及六个独立结果root、readonly/unknown模型、双store和单次初始化；负向四个调用产生预期selector guard与native result未发布诊断，且不发命令。

标准库重建 `mcfpp-native-player-command-results-stdlib.log` exit0/BUILD SUCCESSFUL in10s、Project0/0。首轮联合 `mcfpp-native-player-command-results-final.log` exit1/BUILD FAILED in21s、worker17正常；LibFieldAccess XML `2026-10-07T01:36:37.507Z` 为1/1/0/0，NPE发生在source check的 `EntitySelector('e').type("minecraft:player", false)`，堆栈指向 `EntitySelector.type`，fresh consumer未运行。CacheFormat XML `01:36:36.770Z` 为1/0/0/0。将内部EntityType factory改为查已加载canonical namespace后，仅复查失败fixture：`mcfpp-native-player-command-results-final2.log` exit0/BUILD SUCCESSFUL in18s、worker18正常，XML `2026-10-07T01:39:33.364Z` 为1/0/0/0；source/fresh Project 0/9118和0/9119。四个negative调用实际共8个预期诊断及无命令断言通过。stdlib和Cache未重跑。MCFL42三份资源293933 bytes、header `4c46434d2a000000`、SHA256 `1E700715D1CFB0F20C83B6B07954E39AB68B1FE42B4C9F105CB5028651B8C956`。只验证命令导出，不模拟world或声明frame0；9个Java类、71个CommandReturn注解与7个void wrapper及whole17仍未完成。

### 阶段 113 自检

| 维度 | 评分 | 证据与改进 |
| --- | --- | --- |
| 准确性 | 4/5 | 记录首轮NPE、实际修复边界及最终fresh XML；限定为命令导出。 |
| 完整性 | 3/5 | 六个指定入口受测通过；其余9个Java类及whole17未完成。 |
| 清晰性 | 4/5 | 区分source阶段失败、Cache首轮通过与单fixture复查。 |
| 可执行性 | 4/5 | 下一步仅列Worldborder七个方法并保留默认参数限制。 |
| 简洁性 | 4/5 | 保留必要轮次、计数和资源证据。 |

平均3.8/5，whole17完整性仍为3/5。

## 阶段 112：Team receiver命令结果（已限定验证）

`Team.register`/`unregister`/`clear`三个receiver方法接入单context qualified `CommandResult`；从DTO的真实`id`/`displayName`字段经`withAdapters`读取并解包PropertyVar，再复用既有结果捕获。`Team.mcfpp`显式导入标准库结果类型。fixture检查displayName是严格的NBT chat component（实际NBT storage/address），以及id路径的macro准备、单次调用、双store和root初始化。无生产改动发生在最终断言调整中。

首次stdlib日志 `mcfpp-native-team-command-results-stdlib.log` exit1/BUILD FAILED in11s，Project3 errors/0 warnings，Team三个返回类型均为Invalid qualified type，原因是stdlib源码缺标准库import，测试未启动。补import后的stdlib2 exit0/BUILD SUCCESSFUL in4s，Project0/0。首轮joint日志 `mcfpp-native-team-command-results-final.log` exit1/BUILD FAILED in19s；LibFieldAccess XML `2026-10-07T01:14:09.452Z` 为1 test/1 failure/0 errors/0 skipped，失败于producer宏准备断言，fresh consumer未到；CacheFormat XML `2026-10-07T01:14:08.720Z` 为1/0/0/0。根因是fixture把displayName误当成需要macro准备的文本值，实际是NBT chat component；修正fixture为核对真实NBT地址及组件后，final2仅复跑Team fixture。

final2日志 `mcfpp-native-team-command-results-final2.log` exit0/BUILD SUCCESSFUL in22s，Gradle Test Executor 16正常结束；XML `2026-10-07T01:20:15.674Z` 为1/0/0/0。source/fresh Project分别0 errors/9118 warnings、0/9119。receiver字段、模型合同和consumer实际导出命令断言通过；无world模拟、执行器或frame0结论。Cache只首轮通过，没有在final2重跑。三份产物MCFL41、294070 bytes、header `4c46434d29000000`、SHA256 `41E4F07C70C24D914114C881FEF85A7CFE73F7E364A3F5FA6B0055BBA4CBAADE`。其余12个Java类的77个CommandReturn注解、7个旧void wrapper、9个Team MNIMutator及whole17仍未完成。

### 阶段 112 自检

| 维度 | 评分 | 证据与改进 |
| --- | --- | --- |
| 准确性 | 4/5 | 记录stdlib缺import与fixture误判NBT文本两处真实问题，并区分producer与fresh边界。 |
| 完整性 | 3/5 | 三个Team方法路径通过；MNIMutator、其余Java类及whole17未完成。 |
| 清晰性 | 4/5 | 分开记录首轮joint失败、Cache通过和最终fixture复查。 |
| 可执行性 | 4/5 | 下一阶段限制为Op/Recipe六个player-target入口，先明确参数合同。 |
| 简洁性 | 4/5 | 只保留必要轮次、XML、Project和资源证据。 |

平均3.8/5，whole17完整性仍为3/5。

## 阶段 111：Datapack 与 Debug 结果接口（已限定验证）

新增11个显式单context qualified `CommandResult` 入口：Datapack九个，Debug.start/stop两个；旧Debug.function TODO保留。内部 `captureCommandResult` 从Std共享逻辑抽取；Std的107处调用仅更名、删旧helper/import，root已逐处确认其他行为未变。无新context API或生产语言对象。fixture以真实 `@From Packs` 对象、显式native profileStart/profileStop和 `Box.observe(name as string)` 验证。

首轮stdlib exit0/BUILD SUCCESSFUL in11s、Project0/0；joint3 worker13正常结束但exit1/BUILD FAILED in27s。LibFieldAccess XML `2026-10-07T00:50:12.136Z` tests2/fail1/error0/skip0：旧Std fixture通过，新fixture source Project0/9118，模型及source磁盘检查通过；fresh Project0/9119后在fresh模型 `assertSame(canonical, value.templateType)` 失败，尚未到fresh磁盘检查。CacheFormat XML `00:50:11.575Z` 为1/0/0/0。根因是Kryo嵌入Info产生独立DataTemplate对象；`MCFPPDataTemplateType.tryResolve`现按完整Declaration identity复用已加载namespace内真实template/interface，并同步parentType，未改变Info/serializer/wire/context。

修复后stdlib2 exit0/BUILD SUCCESSFUL in9s、Project0/0。final2仅复跑两个LibFieldAccess fixture：worker14正常，exit0/BUILD SUCCESSFUL in27s；XML `2026-10-07T00:56:50.658Z` tests2/fail0/error0/skip0。四个Project摘要依序0/9118、0/9119、0/9118、0/9119。source/fresh模型canonical身份、readonly属性、未知facts及空snapshot通过；consumer磁盘文件含debug start/datapack list/debug stop三次直接调用，以及enable动态macro的准备与单次调用；root `{}`初始化与同root双store int1/byte1符合合同。未验证world、ScoreExecutor或frame0。Cache在首轮MCFL40已通过，模板恢复修复未改格式，因此第二轮没有重复运行。

MCFL39→40，三份bin均293272 bytes、raw header `4c46434d28000000`、SHA256 `04023951C6DA76A2DB1C3D0BAB6528A8387D0D8CD5BC92A949B695E390ABAC9A`。Std之外还有13个Java类（80个CommandReturn注解及7个void旧wrapper），whole17保持ACTIVE未完成；无fullcheck/server。

### 阶段 111 自检

| 维度 | 评分 | 证据与改进 |
| --- | --- | --- |
| 准确性 | 4/5 | 记录首轮canonical失败边界、修复和最终fresh XML，不称最终联合3项全绿。 |
| 完整性 | 3/5 | 11个Datapack/Debug入口受测完成；其他13个Java类与whole17仍未完成。 |
| 清晰性 | 4/5 | 区分source检查与首轮fresh失败，并限定无world/frame结论。 |
| 可执行性 | 4/5 | 下一阶段限定三个Team receiver方法并列明MNIMutator边界。 |
| 简洁性 | 4/5 | 仅记录两轮必要运行及库版本证据。 |

平均3.8/5，whole17完整性仍为3/5。

## 历史必要检查：迁移 StdCommands 命令结果接口（阶段 110）

StdCommands剩余106个旧 `CommandReturn` 入口迁到单context；当前127个Java native入口均使用 `NativeCallContext`，其中107个返回qualified `mcfpp.minecraft.std:CommandResult`、20个void。root对比127个语言签名（仅返回类型按新契约变化）与106个新增命令表达式，参数映射和文字词序一致；16个旧参数名在测试前修正。私有 `captureResult` 使用真实declaredReturnType和root `{}`，双store结果后按宏准备顺序只发一次调用再publish；宏实参继续由真实 `withArguments` 捕获，未知float保留既有guard。没有给未知结果造快照。

`mcfpp-native-commands-result-stdlib.log` exit0/BUILD SUCCESSFUL in11s，Project0/0。joint `mcfpp-native-commands-result-final.log` exit0/BUILD SUCCESSFUL in22s、worker12正常；LibFieldAccess XML `2026-10-07T00:38:18.068Z`与CacheFormat XML `00:38:17.277Z`均1/0/0/0；source/fresh Project分别0/9118与0/9119。source/fresh fixture只覆盖seed、help和动态say：模型保持canonical、readonly与未知facts，导出文件含seed/help双store和say动态macro准备及单次调用。未验证107个命令的world行为，无ScoreCommandExecutor/frame0结论。三份MCFL39资源286624 bytes、raw header `4c46434d27000000`、SHA256 `A2348C93E75C319D2D6E1B99F62E21A011B4BE7EFA14732C53241442693B1FBD`。StdCommands以外仍有14个Java类（91个CommandReturn返回注解及7个void旧wrapper）；whole17仍ACTIVE未完成。

### 阶段 110 自检

| 维度 | 评分 | 证据与改进 |
| --- | --- | --- |
| 准确性 | 4/5 | 记录127个接口比较、106个表达式及两个fresh XML，不扩大为world验证。 |
| 完整性 | 3/5 | StdCommands受测迁移完成；14个其他Java类与whole17仍未完成。 |
| 清晰性 | 4/5 | 区分单context覆盖、实际fixture三命令及剩余边界。 |
| 可执行性 | 4/5 | 下一阶段限Datapack九项和Debug两项，并指出Op.deop已知错误。 |
| 简洁性 | 4/5 | 保留必要范围、计数和版本证据。 |

平均3.8/5，whole17完整性仍为3/5。

## 历史必要检查：捕获 seed 的命令结果（阶段 109）

普通 `CommandResult` 使用既有nominal类型，暴露只读 `result:int` 与 `success:bool`。seed单context根据真实declaredReturnType建立未知事实，通过root `{}`初始化并在一次命令中双 `execute store`，不由ctor/preInit覆盖。consumer读取类型身份、readonly属性与未知事实，并检查实际磁盘命令合同；不模拟seed世界结果，也不声称frame0。

首轮stdlib `mcfpp-native-seed-result-stdlib.log` exit1/BUILD FAILED in12s：Project 1 error/0 warnings，`CommandResult` 在 `StdCommands.seed` 声明环境中无法解析。`MCFPPFile.runCommand()` 补用既有声明context后，stdlib2 exit0/BUILD SUCCESSFUL in13s，Project0/0。联合首轮worker10正常结束但BUILD FAILED in21s；LibFieldAccess XML `2026-10-07T00:15:39.804Z` 为1/1/0/0，source0/9118，canonical身份、readonly属性、整对象snapshot为空及binding整体未知均通过，随后对模板字段逐项要求知识值导致首个 `result` 读回 `null` 而不是 `Unknown`，未到consumer/磁盘合同；CacheFormat XML `00:15:39.166Z` 为1/0/0/0。

将fixture检查改为四个实际读取变量存在，且它们各自的snapshot均为空后，final2只复查fixture：worker11正常，exit0/BUILD SUCCESSFUL in16s；fresh XML `2026-10-07T00:18:41.752Z` 为1/0/0/0。source/fresh Project分别0/9118与0/9119；模型检查通过，consumer磁盘合同确认root `{}`早于唯一 `run seed`，同一命令分别store result与success，读取对应字段路径，无裸seed或return-run。未模拟世界执行结果/未声称frame0。Cache只在首轮通过。三份bin均MCFL38、291766 bytes、raw header `4c46434d26000000`、SHA256 `9E44A36392EB129EB3BBF5E9B0C2066EE88941232135543898CFE45AFFB27F3C`。没有重跑87–108旧greens、fullcheck或server。

### 阶段 109 自检

| 维度 | 评分 | 证据与改进 |
| --- | --- | --- |
| 准确性 | 4/5 | 保留首轮库声明错误、过细fixture断言及最终单fixture结果的边界。 |
| 完整性 | 3/5 | seed声明和导出合同限定验证；StdCommands剩余106个CommandReturn入口与whole17仍未完成。 |
| 清晰性 | 4/5 | 区分未知模型事实和磁盘命令合同，不声称世界执行结果。 |
| 可执行性 | 4/5 | 下一步限定于StdCommands中剩余结果入口。 |
| 简洁性 | 4/5 | 仅保留stdlib、两轮fresh及Cache首轮证据。 |

平均3.8/5，whole17完整性仍为3/5。

## 历史必要检查：System 诊断接口（阶段 108）

删除没有合法普通 `type` 返回消费路径的 `System.typeOf`，并将debug/info/warn/error四个void方法迁入显式 `NativeCallContext`，标记 `NoExternalWrites`。诊断保留编译期concrete内容与runtime adapter的宿主 `toString` 语义，不生成runtime NBT。实现为约40行fixture与约22行backend；MCFL37。

stdlib `mcfpp-native-diagnostics-stdlib.log` exit0/BUILD SUCCESSFUL in10s，Project0/0。joint `mcfpp-native-diagnostics-final.log` exit0/BUILD SUCCESSFUL in28s、worker8正常；LibFieldAccess XML `2026-10-06T23:59:35.568Z`与CacheFormat XML `23:59:34.861Z`均1/0/0/0。source/fresh Project分别0/9120与0/9121；新warnings各2条。负向检查最后一个Project为1 error/9118 warnings，属于预期literal error。source/fresh磁盘返回值7与frame0通过。src/main/resources、build/resources/main与build/stdlib-index三份均为MCFL37、289476 bytes、SHA256 `DEC8A22394F86A1C7D1D4BCC6828871C25BBF8FDAB639EA52E6858BE773AE657`，raw header `4c46434d25000000`。仅这两个方法通过；未重跑87/107旧greens、fullcheck或server。

### 阶段 108 自检

| 维度 | 评分 | 证据与改进 |
| --- | --- | --- |
| 准确性 | 4/5 | 记录两个fresh XML、预期负向Project错误及一致MCFL37产物。 |
| 完整性 | 3/5 | 四个诊断入口限定验证；其他MNI与whole17仍未完成。 |
| 清晰性 | 4/5 | 区分编译期内容和runtime宿主字符串转换，不声称生成NBT。 |
| 可执行性 | 4/5 | 下一步只新增CommandResult的声明返回类型调用路径。 |
| 简洁性 | 4/5 | 仅记录必要构建、测试和磁盘证据。 |

平均3.8/5，whole17完整性仍为3/5。

## 历史必要检查：动态分支中的局部值读取（阶段 107）

4个生产文件和单fixture限定修复legacy两个动态分支入口的score缓存归属：缓存owner使用实际`Function`对象identity、`Pair.first ===`及`IdentityHashMap`，不使用Function语义equals；父函数在动态if跳转前flush可见runtime bindings，使lazy初始化支配分支路径。范围仅为这两个legacy入口，不代表CFG/loop/全量facts迁移；MCFL36不变。

首轮 `mcfpp-branch-read-context-final.log` 在compileKotlin失败18s，4处 `Unresolved reference: StorageAccess`，无worker和fresh XML。补入单行import后，final2仅跑新fixture：worker7正常，exit0/BUILD SUCCESSFUL in50s，fresh XML `2026-10-06T23:53:37.602Z` 为1/0/0/0；source/fresh Project分别0/9118与0/9119。source/fresh真实磁盘结果10/7与frame0断言通过。无stdlib、Cache、其他套件、fullcheck或server验证；MCFL36不变。whole17仍未完成。

### 阶段 107 自检

| 维度 | 评分 | 证据与改进 |
| --- | --- | --- |
| 准确性 | 4/5 | 区分首次Kotlin导入错误与final2单fixture结果，并限定owner身份及分支范围。 |
| 完整性 | 3/5 | 两个legacy分支路径受测通过；CFG、loop、全量facts与whole17仍未完成。 |
| 清晰性 | 4/5 | 说明缓存用Function对象identity，不把分支局部修复概括为完整数据流分析。 |
| 可执行性 | 4/5 | 阶段108限定System.typeOf删除及四种日志入口迁移。 |
| 简洁性 | 4/5 | 仅保留必要编译失败和最终fresh证据。 |

平均3.8/5，whole17完整性仍为3/5。

## 历史必要检查：Time 显式原生接口（阶段 106）

18个Time运算/factory入口接入单context；`declaredReturnType`由真实callee提供，函数仍指向命令所属caller。Time结果创建新的canonical wrapper和独立Place，不clone receiver；六倍率factory不再使用GlobalScope静态缓存，倍率1/20/1200/72000/144000/24000保持不变。`FieldInfo`恢复operator scope，`DataTemplateInfo`先登记canonical模板，再恢复self-signature；MCFL35→36。

首轮MCFL35 stdlib1成功21s、Project0/0；final1 worker4正常，exit1/BUILD FAILED in22s。Cache XML `2026-10-06T23:23:58.692Z`为1/0/0/0；Time fixture XML `23:23:59.713Z`为1/1/0/0，source29/9118，operator表未持久化和恢复，未到执行器。MCFL36 stdlib2重建成功8s、Project0/0。final2 worker5正常、BUILD FAILED in20s，XML `2026-10-06T23:29:15.698Z` 1/1/0/0；source0/9118，但执行器在source check中因共享flag分支的旧AST读缓存缺失 `temp_2829` 而失败，未到consumer。将七种bool结果观察改为现有 `toInt(condition as byte)` 后，final3仅复查fixture：worker6正常、exit0/BUILD SUCCESSFUL in17s，XML `2026-10-06T23:34:47.825Z` 为1/0/0/0；source0/9125、fresh0/9126。实际磁盘结果1027809与helper frame0检查通过，名义Time及11个factory/算术结果Place断言通过；没有独立factoryTotal/arithmeticCode/flags磁盘断言，也未验证所有结果两两Place。Cache仅在MCFL35首轮通过，未在最终MCFL36复跑；不称最终联合2项全绿。三份MCFL36 bin均289309 bytes、SHA256 `543007E30D67400BDE5A121EFCF143A7E8522B45582006EA91D40FB7C4EB89E9`，raw header `4c46434d24000000`。bool转byte产生的7 warnings为既有representation提示，未证明checker兼容；无unit fix、fullcheck或server验证。whole17仍未完成。

### 阶段 106 自检

| 维度 | 评分 | 证据与改进 |
| --- | --- | --- |
| 准确性 | 4/5 | 区分运算符加载缺失、source执行器缺失分数与最终受测fixture通过。 |
| 完整性 | 3/5 | Time单context受测路径完成；分支读缓存、bool表示证明及whole17仍未完成。 |
| 清晰性 | 4/5 | 说明最终磁盘值与缺少的逐结果断言，不把类型view说成已证明兼容。 |
| 可执行性 | 4/5 | 阶段107限定跨分支动态局部值物化与往返。 |
| 简洁性 | 4/5 | 按必要轮次记录证据与边界。 |

平均3.8/5，whole17完整性仍为3/5。

## 历史必要检查：delegated-int 模板基础（阶段 105）

`Seconds as int` 的delegated整数wrapper接入普通参数初始化、默认构造与dispatch；typed copy持有独立Place，`as int`视图共享原Place，immutable typed snapshot/restore与codec、ConstructorInfo恢复已接通。MCFL33→34。

RED `mcfpp-delegated-int-template-red2.log`：worker1正常，exit1/BUILD FAILED in45s；XML `2026-10-06T22:58:31.135Z` 为1/1/0/0，producer4/9118。首因 `No constructor like: [int] defined in class Seconds`，后续三处 `original` 未定义；未到consumer。最初 `red.log` 包含JAVA_HOME错误及wrapper下载失败，没有运行测试worker或产生新XML，不作为测试结果。

`mcfpp-delegated-int-template-stdlib.log`：exit0/BUILD SUCCESSFUL in39s，Project0/0。joint `mcfpp-delegated-int-template-final.log`：worker3正常，exit0/BUILD SUCCESSFUL in34s；LibFieldAccess XML `2026-10-06T23:11:06.462Z` 为1/0/0/0，Cache XML `23:11:12.399Z` 为3/0/0/0，共4项。source/fresh Project分别0/9118、0/9119。source/fresh模型断言通过：Seconds与int分离、copy有独立Place、`as int`视图共享Place；磁盘最终结果494和frame0检查通过，4和9是表达式中间读数。三份bin为MCFL34、285804 bytes、SHA256 `7CD88CD7C40F219871598992A817DAB725FDC6D1BF3DF2ED6F3744D76D16D0F9`，raw header `4c46434d22000000`。无Time/浮点/fullcheck/server扩展；whole17仍未完成。

### 阶段 105 自检

| 维度 | 评分 | 证据与改进 |
| --- | --- | --- |
| 准确性 | 4/5 | 区分无效JAVA_HOME启动、真实RED、producer错误与最终磁盘验证范围。 |
| 完整性 | 3/5 | delegated-int受测路径与Cache回归通过；未扩到Time/浮点或whole17。 |
| 清晰性 | 4/5 | 明确4/9为中间读数，最终磁盘结果为494。 |
| 可执行性 | 4/5 | 阶段106限定Time运算与工厂的显式context迁移。 |
| 简洁性 | 4/5 | 保留必要失败边界及最终两个suite的证据。 |

平均3.8/5，whole17完整性仍为3/5。

## 历史必要检查：System print 原生入口（阶段 104）

九种print入口（text/string/any/int/list/dict/NBT/DTO/bool）接入单context，合法 `list<*>`/`dict<*>` 通过既有native pattern桥接；闭合pattern codec只保存既有集合名，native签名加载不构造pattern的ScopeVar。修复了dict wildcard解析遗漏return及stdlib缓存中的KClass恢复问题，并删除 `printVar` 与DTO输出TODO占位。

分轮验证：stdlib1因两处grammar问题失败8s；stdlib2因dict wildcard返回缺失失败9s；stdlib3成功6s。joint首次在test编译期因错误的 `getFunction` 调用失败11s，无worker/XML；修正后final2的worker217失败11s，fixture读取stdlib缓存时KClass崩溃，未产生producer Project总数；stdlib4成功10s。final3 worker218失败21s，producer4/9119，失败于pattern scope `buildVar`，未到磁盘断言；stdlib5成功14s。final4 worker219失败24s，producer0/9119，source命令断言对参数 `s` 的NBT路径要求过严，未进入consumer。final5仅复查fixture：worker220正常、exit0、BUILD SUCCESSFUL in15s，fresh XML `2026-10-06T22:48:46.728Z` 为1/0/0/0；source0/9119、fresh consumer0/9120。source实际检查10个tellraw：int/bool为score，string/NBT/any/payload为NBT，其余为plain JSON文本；consumer成功导出main、payload初始化、Box.observe及Box初始化函数，实际模型与命令断言通过。没有ScoreCommandExecutor，未断言返回值/frame清理；无macro/world执行或渲染验证。Cache只在首轮独立通过，未复跑；不称最终联合2项全绿。MCFL33，source/resource/build索引三份bin均285692 bytes、SHA256 `D9185309EF9D513D04D234E8FE7D22DA837045F01668A97D1D671622A1951B63`，raw header `4c46434d21000000`。无fullcheck/server；whole17仍未完成。

### 阶段 104 自检

| 维度 | 评分 | 证据与改进 |
| --- | --- | --- |
| 准确性 | 4/5 | 区分compile失败、stdlib缓存崩溃、source断言失败和最终单fixture通过。 |
| 完整性 | 3/5 | 九入口ABI与受测导出路径完成；其他MNI、macro/world执行和whole17未完成。 |
| 清晰性 | 4/5 | 明确source tellraw检查不等于执行器或世界渲染。 |
| 可执行性 | 4/5 | 阶段105限定delegated-int模板字段和实际库往返。 |
| 简洁性 | 4/5 | 按轮次保留必要失败边界与最终证据。 |

平均3.8/5，whole17完整性仍为3/5。

## 历史必要检查：damage void 原生入口（阶段 103）

迁移四个damage void接口至显式context，避免旧Java primitive-float、`DamageType`及selector（实际为 `SelectorVar`）签名不匹配；DamageType实际是ResourceID data。语言identifier和默认语义保留。动态float使用现有 `FloatProviders.enabled` 能力边界；未建新浮点引擎。

`mcfpp-native-damage-context-stdlib.log`：exit0/BUILD SUCCESSFUL in8s，Project0/0。`mcfpp-native-damage-context-final.log`：worker216正常、exit0/BUILD SUCCESSFUL in19s。LibFieldAccess XML `2026-10-06T22:23:00.710Z`与Cache XML `22:22:59.798Z`均1/0/0/0；source/fresh Project分别0/9118、0/9119。source与fresh consumer实际命令初始化DamageType并将 `minecraft:generic` 写入id，准备amount和id后分别调用damage宏一次。旧目标的direct-context动态float拒绝生成命令；常量路径按现有编码保留。三份MCFL32资源均284899 bytes、SHA256 `768075ABA8DFA56BFC1B294A0F05D0F3CF97BEC9A70B6595CBECFCAD7A09C6A0`，raw header `4c46434d20000000`。没有world/float执行或完整damage语义验证；无fullcheck/server。

### 阶段 103 自检

| 维度 | 评分 | 证据与改进 |
| --- | --- | --- |
| 准确性 | 4/5 | fresh XML、source/fresh诊断、宏准备与调用命令及同hash资源可核对。 |
| 完整性 | 3/5 | 基本damage生成路径通过；其余参数组合、float/world执行及whole17仍未完成。 |
| 清晰性 | 4/5 | 区分合法常量与受目标能力限制的动态float。 |
| 可执行性 | 4/5 | 阶段104限定System九种print类型与库导出fixture。 |
| 简洁性 | 4/5 | 保留单次标准库与联合检查证据。 |

平均3.8/5，whole17完整性仍为3/5。

## 历史必要检查：StdCommands void 原生入口（阶段 102）

16个无Java primitive-float签名冲突的void接口迁入单context；`place`仍可有 `MCFloat` 参数。保留四个damage primitive-float入口及其他107个 `CommandReturn` 接口。`ResourceID.toCommandPart` 实现以 `@MNIFunction.override=true` 替换失效的源码覆盖，并在Java wrapper中读取 `id.toCommandPart`；未知target作为普通参数处理。未改变其他Commands宏API或host snapshot。

首轮 `mcfpp-native-void-command-context-stdlib.log` 重建成功10s、Project0/0；joint worker214失败15s。LibFieldAccess XML `2026-10-06T22:00:36.130Z` 为1/1/0/0，producer3/9118，首因是 `id.toCommandPart` 语言成员未定义，随后 `place` 出现null Command转换；Cache XML `22:00:35.360Z` 为1/0/0/0。ResourceID修复后，`mcfpp-native-void-command-context-stdlib2.log` 成功7s、Project0/0；仅复查fixture的 `mcfpp-native-void-command-context-final2.log` worker215正常、exit0/BUILD SUCCESSFUL in11s，XML `2026-10-06T22:07:40.238Z` 为1/0/0/0，source0/9118、fresh0/9119。两个place调用均生成一次macro调用，准备参数先于调用；未知target在frame0读取后复制进macro槽，宏体无return-run。该fixture不证明非宏路径或world/frame执行。MCFL31，三份资源285743 bytes、SHA256 `6BB5D52369DC314C2389C3623A99AA862D975BFB02C45A99344B38B1F7D387C6`，raw header `4c46434d1f000000`。Cache只首轮通过，未重跑；无fullcheck/server。

### 阶段 102 自检

| 维度 | 评分 | 证据与改进 |
| --- | --- | --- |
| 准确性 | 4/5 | 记录ResourceID首轮故障、修复后XML及两个实际宏调用顺序。 |
| 完整性 | 3/5 | 受测宏命令路径通过；非宏路径、world执行及whole17未完成。 |
| 清晰性 | 4/5 | 区分普通未知参数、macro准备和调用，不扩展到world模拟。 |
| 可执行性 | 4/5 | 阶段103限定damage void ABI及动态float拒绝边界。 |
| 简洁性 | 4/5 | 只记录必要两轮、Cache一次通过及验证限制。 |

平均3.8/5，whole17完整性仍为3/5。

## 历史必要检查：LootTablePredicate 原生入口（阶段 101）

`LootTablePredicateData.pass/fail` 两个 bool 入口接入 `NativeCallContext`；`NativePredicateOperations` 复用 `CommandBoolPart` 生成 `if/unless predicate`，通过现有 `publishResult` 规范化并发布 `ScoreBool`，predicate id 经 `PropertyVar.get` 读取。静态 `of`/factory、effects与world模拟未改。

`mcfpp-native-predicate-context-stdlib.log`：标准库重建exit0、BUILD SUCCESSFUL in8s，Project0/0。`mcfpp-native-predicate-context-final.log`：worker213正常，exit0、BUILD SUCCESSFUL in18s；LibFieldAccess XML `2026-10-06T21:49:26.530Z`、Cache XML `21:49:25.760Z`，均1/0/0/0。source/fresh Project分别0/9118、0/9119。source与fresh consumer导出的函数包含 `execute store success score ... if predicate fixture:allowed` 和 `unless predicate`，scope有两个 `ScoreBool`。三份资源均MCFL30、291353 bytes、SHA256 `FBFA847ACD3BE926FA7DE85948509C012DBB44AE4AD7017E8E2A0E9A508F37B2`，raw header `4c46434d1e000000`。只验证命令生成与库往返，不模拟world predicate或声称frame0；无fullcheck/server。

### 阶段 101 自检

| 维度 | 评分 | 证据与改进 |
| --- | --- | --- |
| 准确性 | 4/5 | fresh XML、source/fresh诊断、双谓词命令及同步MCFL30资源均可核对。 |
| 完整性 | 3/5 | 两个predicate入口的受测路径通过；world执行、其余MNI与whole17仍未完成。 |
| 清晰性 | 4/5 | 明确本阶段检查的是生成命令，不是world predicate求值。 |
| 可执行性 | 4/5 | 阶段102限定16个StdCommands void入口及宏命令顺序。 |
| 简洁性 | 4/5 | 仅保留两个套件、产物和执行边界。 |

平均3.8/5，whole17完整性仍为3/5。

## 历史必要检查：转义 PlainChatComponent 文本（阶段 100）

`PlainChatComponent` 对纯text改用项目Fastjson2 `JSON.toJSONString` 编码单个字符串，保证引号/反斜线的JSON转义；不扩展控制字符、style或decoder。无MCFL变化。RED `mcfpp-plain-text-escaping-red.log` worker211、exit1/BUILD FAILED in12s，XML `2026-10-06T21:40:00.543Z` 1/1/0/0；producer10 errors/9118 warnings，首因是在 `toText` 结果发布时 `ValueSnapshot.of`→`Tag.toNBT` 将含引号文本当SNBT解析并失败。10是producer诊断总数，不视作10个独立根因；未到fresh consumer。

final `mcfpp-plain-text-escaping-final.log` worker212正常、exit0/BUILD SUCCESSFUL in17s，XML `2026-10-06T21:41:53.747Z` 1/0/0/0；source0/9118 warnings、fresh0/9119。磁盘mcfunction执行检查original/copied的quote+backslash文本、joined加` tail`、NBT字符串toText、return7/frame0；日志118877、118878、118882、118884为flat literal components。无fullcheck/server；不据此声称控制字符或聊天渲染验证。

### 阶段 100 自检

| 维度 | 评分 | 证据与改进 |
| --- | --- | --- |
| 准确性 | 4/5 | 记录RED首因而未把10个诊断误当独立根因，final磁盘断言有证据。 |
| 完整性 | 3/5 | 指定转义fixture通过；控制字符、聊天渲染及whole17未完成。 |
| 清晰性 | 4/5 | 区分producer解析失败和最终source/fresh磁盘执行。 |
| 可执行性 | 4/5 | 下一阶段限定LootTablePredicateData的两个谓词入口。 |
| 简洁性 | 4/5 | 只覆盖单fixture及必要边界。 |

平均3.8/5，whole17完整性仍为3/5。

## 历史必要检查：迁移 DataObjectData.toText（阶段 99）

将单个 `DataObjectData.toText` Java入口迁入context，复用既有 `NativeTextOperations` 的DTO SNBT/runtimeNBTChat逻辑；没有扩展到JavaVar的toCommandPart、Any host/getDefault/equalNull或effects。MCFL29，source/build/stdlib-index三份bin均291431 bytes、raw header `4c46434d1d000000`、SHA256 `37576145F097FEEB0FB97CFFA7A35F970750A51B4687D846F82A3A9EE5473B7F`。

旧ABI基线 `mcfpp-native-template-text-red.log` 实际为GREEN：worker209正常、BUILD SUCCESSFUL in15s，XML `2026-10-06T21:28:54.597Z` 1/0/0/0，source0/9118 warnings、fresh0/9119；它不是负例。MCFL29标准库重建exit0/BUILD SUCCESSFUL in10s、Project0/0。joint2 worker210正常exit0/BUILD SUCCESSFUL in19s；LibFieldAccess XML `2026-10-06T21:34:31.588Z` 1/0/0/0，Cache XML `21:34:30.893Z` 1/0/0/0；source/fresh Project分别0/9118与0/9119。consumer磁盘mcfunction执行确认 `Payload.value=7`、Box result=7、runtime NBT component SNBT与frame0。该fixture走普通data `buildUnConcrete`，不证明Concrete分支；无fullcheck/server。

### 阶段 99 自检

| 维度 | 评分 | 证据与改进 |
| --- | --- | --- |
| 准确性 | 4/5 | 明确基线日志实际为GREEN，记录MCFL29、source/fresh磁盘结果。 |
| 完整性 | 3/5 | 只迁移DataObjectData入口；Concrete、JavaVar/Any和whole17仍未完成。 |
| 清晰性 | 4/5 | 区分旧ABI基线、schema重建及两项最终联合测试。 |
| 可执行性 | 4/5 | 阶段100限于PlainChatComponent JSON字符串转义和单fixture回归。 |
| 简洁性 | 4/5 | 聚焦一个入口和一个纵向fixture。 |

平均3.8/5，whole17完整性仍为3/5。

## 历史必要检查：恢复 T! 文本快照与即时拼接（阶段 98）

两个生产文件调整 `EncodedChatComponent` 的不可变SNBT快照和 `StorageAccess.restore`：完整text ListTag恢复为immutable encoded component，再以原声明类型构造；没有通用wrapper递归、新parser/cache或effects变更。MCFL28、三份291176-byte bin及SHA256 `2550D9609261BC26BFB713DE15CA6630FFEAF6805B52024C37DCE5930C57381B`不变。

RED `mcfpp-known-text-snapshot-red.log` worker207、exit1/BUILD FAILED in17s；XML `2026-10-06T21:14:21.835Z` 1/1/0/0。producer为2 errors/9118 warnings；`joined`与`suffixed`两个T!声明均报 `Assign error: cannot assign text to text`，未进入fresh consumer。修复后只复查该fixture一次：`mcfpp-known-text-snapshot-final.log` worker208正常、exit0/BUILD SUCCESSFUL in28s，XML `2026-10-06T21:19:02.182Z` 1/0/0/0；producer0/9118 warnings、fresh consumer0/9119。实际磁盘mcfunction执行断言验证original=A、copied=A、joined=AB、suffixed=AS、return7/frame0；生成命令将AB/AS直接写作包含A/B或A/S组件的flat literal list（日志118890、118894）。无stdlib/Cache/fullcheck/server。

### 阶段 98 自检

| 维度 | 评分 | 证据与改进 |
| --- | --- | --- |
| 准确性 | 4/5 | 记录RED producer错误、fresh通过、磁盘payload和literal-flat-list命令证据。 |
| 完整性 | 3/5 | 限定T! text snapshot/copy/concat路径通过；whole17仍未完成。 |
| 清晰性 | 4/5 | 清楚区分RED未到consumer与最终source/fresh执行。 |
| 可执行性 | 4/5 | 下一步只迁移DataObjectData.toText并用runtime源码fixture验证。 |
| 简洁性 | 4/5 | 仅保留必要轮次、范围和下一步边界。 |

平均3.8/5，whole17完整性仍为3/5。

## 历史必要检查：统一文本原生方法（阶段 97）

String与NBT的 `toText` 各由同一Java类处理，按实际receiver状态分流；删除重复的Concrete入口/helper，未扩展到其他native或effects。沿用现有fixture，两个即时常量concat初始化改为直接调用 `toText`。三份bin均为291176 bytes、MCFL28（raw header `4c46434d1c000000`）、SHA256 `2550D9609261BC26BFB713DE15CA6630FFEAF6805B52024C37DCE5930C57381B`。

标准库 `mcfpp-unified-text-methods-stdlib.log` exit0，BUILD SUCCESSFUL in4s，Project0/0。联合 `mcfpp-unified-text-methods-final.log` exit0、worker206正常、BUILD SUCCESSFUL in17s；LibFieldAccess XML `2026-10-06T21:02:40.295Z` 为2/0/0/0，Cache XML `21:02:39.664Z` 为1/0/0/0。source/fresh各项目计数依次为0/9119、0/9120、0/9118、0/9119；两条text fixture的payload、copies、return7和frame0断言通过。生成命令将 `("A").toText()` 与 `("B").toText()` 各自写成文本tag literal，但 `joinedConstant`/`suffixedConstant` 仍由临时数组append生成，没有单条 `AB`/`AS` literal；本轮不证明拼接常量折叠。无fullcheck/server。

这项验证被重复执行：首轮报告的stdlib54s/joint35s日志随后被第二次同名重定向覆盖，首轮worker号无法从现存证据恢复；上述最新XML和worker206作为可核验结果。重复执行是代理未识别摘要中已完成的验证，不应计作额外覆盖。

### 阶段 97 自检

| 维度 | 评分 | 证据与改进 |
| --- | --- | --- |
| 准确性 | 4/5 | 使用当前fresh XML、worker206及三份同hash MCFL28资源；明确区分toText literal与concat未折叠。 |
| 完整性 | 3/5 | 两个现有fixture及缓存格式受测通过；即时拼接fold仍未证明，whole17仍未完成。 |
| 清晰性 | 4/5 | 说明重复运行覆盖首轮日志，并以最新可查证据为准。 |
| 可执行性 | 4/5 | 下一步只调查T! text常量拼接/普通copy的snapshot缺口。 |
| 简洁性 | 3/5 | 阶段执行摘要已清楚；避免未识别已完成任务而重复构建。 |

平均3.6/5，whole17完整性仍为3/5。

## 历史必要检查：text 与拼接运算（阶段 96）

text receiver赋值与三类构造路径归一；两个拼接 `MNIOperator` 接入单context，concat以直接typed core helper处理组件，JsonTextConcreteData重复注入复用JsonTextData。MCFL27。

`mcfpp-native-text-concat-stdlib.log`：标准库成功57s，Project0/0。初始RED `mcfpp-native-text-concat-red.log` worker201/FAILED12s，XML `2026-10-06T20:41:25.805Z` 1/1/0/0；producer1 error/9118 warnings，首因 `observe<>(nbt,nbt) not defined`，对应text物化实际退成NBT，未到fresh或磁盘断言。首轮联合 `mcfpp-native-text-concat-final.log`：Cache XML `2026-10-06T20:46:44.428Z` 1/0/0/0通过；fixture XML `20:46:40.257Z`失败，source执行器不支持生成的 `data modify ... append value {"type":"text","text":"S"}` 字面组件命令，未到consumer。final2 worker203、FAILED10s，fixture XML `20:48:31.164Z`仍失败于source观察，期待ListTag却读到CompoundTag，未到consumer。runtime adapter修复后final3 worker204 exit0/BUILD SUCCESSFUL in14s，fixture XML `20:51:29.163Z` 1/0/0/0；source0/9118 warnings，fresh consumer0/9119。source与fresh consumer磁盘均通过12项命名观察、original/copy组件与return7/frame0断言。Cache和fixture两个不同用例跨轮各自通过，final3只复查fixture，不是最终联合2项。12个观察名中A/B来自constant origin；未知MNI屏障可能物化并丢失已知值，未单独证明每个const-fold分支。没有验证聊天渲染或跨帧寿命，无fullcheck/server。

source/build资源/build索引三份bin均为291364 bytes、MCFL27（raw header `4c46434d1b000000`）、SHA256 `A674D9E8FB3AD848A9F8CCC5E68CF31E2305271395D0A6B90122E1CD0827AD1A`。

### 阶段 96 自检

| 维度 | 评分 | 证据与改进 |
| --- | --- | --- |
| 准确性 | 4/5 | 记录了两轮fixture失败位置、最终source/fresh磁盘断言与同步MCFL27产物。 |
| 完整性 | 3/5 | text赋值与拼接受测路径通过；未知MNI屏障下的常量折叠和whole17仍未完成。 |
| 清晰性 | 4/5 | 区分source执行器失败、source shape失败和final3单fixture复查。 |
| 可执行性 | 4/5 | 阶段97限制为String/NBT到text的receiver分流和已有回归。 |
| 简洁性 | 4/5 | 只记录本阶段验证证据、边界与下一步。 |

平均3.8/5，whole17完整性仍为3/5。

## 历史必要检查：显式原生转换引用（阶段 95）

标准库 `conversions.mcfpp` 的49个native RHS已改为匹配Java真实方法名 `toTargetFromSource`；现有 `decodeByte` fixture明确引用 `ConversionData.toIntFromByte`。FieldVisitor只按真实Java方法名匹配，不再回退到语言重载identifier；Namespace/CompoundData仍以annotation identifier解析正常语言重载。用户不要求兼容旧 `.mcfpp` 显式Java方法引用。MCFL26与库wire不变。

`mcfpp-native-reference-stdlib.log`：stdlib重建exit0/BUILD SUCCESSFUL in9s，Project0/0。`mcfpp-native-reference-final.log`：worker200正常，exit0/BUILD SUCCESSFUL in18s；fresh XML `2026-10-06T20:31:00.566Z`，`nativeStaticConversionsUseArgumentContextAcrossLibraryRoundTrip()` 1/0/0/0，source0/9119 warnings、fresh consumer0/9120。source与fresh库资源及索引均为291070 bytes、MCFL26，SHA256 `3F507523A551F5CE2A42B2CA93BA5AB01207F4518B5CF47AC80D13A0AA6B58EA`。仅复查既有conversion fixture，Cache和其他旧绿未重复运行；无fullcheck/server。

### 阶段 95 自检

| 维度 | 评分 | 证据与改进 |
| --- | --- | --- |
| 准确性 | 4/5 | 记录了严格方法名匹配、唯一定向fixture、MCFL26同步产物和source/fresh诊断。 |
| 完整性 | 3/5 | 49个引用声明及既有转换fixture路径通过，其他MNI与whole17仍未完成。 |
| 清晰性 | 4/5 | 区分Java真实方法名与语言annotation identifier，说明不兼容旧显式引用。 |
| 可执行性 | 4/5 | 阶段96计划限定于text拼接与两项operator，不扩大到聊天渲染。 |
| 简洁性 | 4/5 | 仅记录阶段95结果和下一阶段范围。 |

平均3.8/5，whole17完整性仍为3/5。

## 历史必要检查：toText 显式接收者上下文（阶段 94）

七个 toText 入口迁入单 receiver context，并由 `NativeTextOperations` 执行 typed 操作。当前源码覆盖五个 Java 方法，另有 int 的两种状态；两个 Any 入口仍无 source 覆盖。`MCString.getMemberFunction` 的 TODO override 已移除，继承 Var 的运行时/Concrete 实例成员和签名查找；String runtime/Concrete lazy `instanceData` 已接通，NBT 使用 `instanceData` 与 `commonType`，不再错误写入 `objectData`。MCFL25→26。

`mcfpp-native-text-context-stdlib.log`：首轮stdlib成功，Project 0/0；产物291364 bytes、MCFL26、SHA256 `3D351CE4619D1CD631CEA400D2D169043D4DBE8DEBDA76AAF17B52B80A954890`，这是接通type metadata前的中间产物。首轮final worker正常但失败：Cache XML `2026-10-06T20:03:51.866Z` 1/0/0/0；text fixture `20:03:52.582Z` 1项失败，`MCString.getMemberFunction` 抛 `NotImplementedError`。移除TODO后final2 XML `20:07:52.114Z`仍失败，producer有3个 `Function toText<>() not defined` 错误，未到consumer。接通String/NBT实例类型metadata后，`mcfpp-native-text-context-stdlib2.log` 成功12s、Project0/0；final3 worker199 exit0/BUILD SUCCESSFUL in18s，XML `2026-10-06T20:12:46.049Z` 1/0/0/0，source0/9119 warnings、fresh consumer0/9120。consumer实际磁盘检查六个text payload、score与NBT字段，结果7并通过frame0。Cache与text fixture两个不同用例跨轮各自通过，final3仅复查fixture，不是联合2项；没有验证聊天渲染或跨帧寿命，无fullcheck/server。

最终source/build资源/build索引bin均为291070 bytes、MCFL26（raw header `4c46434d1a000000`）、SHA256 `9ED46C95796CBADE0007D246E630FF094F9018B03772AB57B145B98E412EEAE5`。

### 阶段 94 自检

| 维度 | 评分 | 证据与改进 |
| --- | --- | --- |
| 准确性 | 4/5 | 记录了TODO成员查找与实例metadata两轮缺口，以及最终source/fresh磁盘结果和同步MCFL26产物。 |
| 完整性 | 3/5 | 七个入口完成ABI迁移，其中五个有source/fresh覆盖，Any两个无source覆盖；普通MNI、IR和whole17仍未完成。 |
| 清晰性 | 4/5 | 区分两轮失败边界、最终单fixture复查与跨轮两个用例各过。 |
| 可执行性 | 4/5 | 阶段95改为匹配Java实际方法名的显式转换引用，并保留语言重载identifier。 |
| 简洁性 | 4/5 | 保留历史，只更新阶段94结果与下一阶段范围。 |

平均3.8/5，whole17完整性仍为3/5。

## 历史必要检查：数值转换显式调用上下文（阶段 93）

ConversionData的49个静态入口迁入context：36个数值转换与13个 `toNBT` 转换。context支持nullable receiver与 `withArguments`；NF按声明的void/static属性构造，实例调用仍按Var处理。Java唯一方法名配合原MNIFunction.identifier保留语言层重载名，Namespace/CompoundData/FieldVisitor按有效名称解析入口，`NumericConversions` 发布真实结果引用；这只是49个入口的ABI迁移，不宣称所有转换已有执行实现，既有unsupported诊断保持。MCFL24→25。

`mcfpp-native-conversion-context-stdlib.log`：stdlib重建成功26s，Project0 errors/0 warnings。三份bin均为291070 bytes、MCFL25（raw header `4c46434d19000000`）、SHA256 `D41A947D59084FA3A36D66B940988FF4CD19CA087D54947E5B336B1DADE76832`。

首轮联合 `mcfpp-native-conversion-context-final.log` worker194/FAILED27s：ConversionIR XML `2026-10-06T19:36:28.288Z`、Cache XML `19:36:32.528Z`通过；新fixture XML `19:36:32.533Z`失败。producer因局部变量名 `text` 是保留字而有13个语法错误，未到consumer。改名后final2 worker195/FAILED11s、XML `19:40:56.169Z`：producer仍有1个错误/9119 warnings，`toNBT(string)` 的返回引用类型未转为NBT，consumer未运行。共享toNBT typed-view修复保留原载荷和地址，不扩String→NBT隐式转换。final3 worker196正常、exit0/BUILD SUCCESSFUL in17s，XML `2026-10-06T19:45:12.763Z`为1/0/0/0；producer0/9119、fresh consumer0/9120。consumer从生成的磁盘函数验证255→byte→int为-1、17→float→int为17，bool/String/NBT载荷为ByteTag 1、`hello`、`{value:7}`，并通过frame0。三个不同用例跨轮各自通过，final3仅复查fixture，不是最终联合3项。无fullcheck/server。

### 阶段 93 自检

| 维度 | 评分 | 证据与改进 |
| --- | --- | --- |
| 准确性 | 4/5 | 记录了变量名语法失误、NBT返回适配缺口和final3磁盘结果。 |
| 完整性 | 3/5 | 49个ConversionData入口的指定路径已验证，但其他MNI与whole17仍未完成。 |
| 清晰性 | 4/5 | 区分三项跨轮各过与final3单fixture复查，明确受测路径与未实现转换的诊断边界。 |
| 可执行性 | 4/5 | 下一阶段94按七个toText入口、MCFL26和最小往返用例列出。 |
| 简洁性 | 4/5 | 只补阶段93结果与下一阶段范围，历史保留。 |

平均3.8/5，whole17完整性仍为3/5。

## 历史必要检查：移除被拒绝的 byte/short 算术注册（阶段 92）

删除四个byte/short Java类中的44个 `MNIOperator` 注册，并移除 `MCFPPNBTType.injectedBy` 的4个对应注册；`LibBinFormat.VERSION` 从23升至24。byte/short精确格式、转换和 `Var.rejectNbtArithmetic` 统一拒绝逻辑保留；long/double原本没有这些注册，本阶段未改动。

`mcfpp-nbt-operator-removal-stdlib.log`：stdlib重建exit0/BUILD SUCCESSFUL in55s，Project 0 errors/0 warnings。source `build/stdlib-index/bin.mclib`、资源bin及build资源bin均为MCFL24（raw header `4c46434d18000000`）、292301 bytes、SHA256 `6EADA06D3343578C0A08E13C691D0CC9742C87071476615260D993A2980A1994`。

`mcfpp-nbt-operator-removal-final.log`：worker193正常，exit0/BUILD SUCCESSFUL in44s。ConversionTest XML `2026-10-06T19:17:57.589Z`、LibCacheFormatTest `19:18:01.088Z`、TypeKernelTest `19:18:01.096Z`均为1/0 failures/0 errors/0 skips。Conversion项目0 errors/9118既有warnings；TypeKernel的五个预期负例各产生1个错误/9118 warnings，覆盖byte/short/long/double算术及byte隐式赋值为int；旧/未知缓存格式拒绝回归通过。无新fixture、fullcheck或server；阶段92限定验证完成，不代表其他数值/转换迁移已完成。

### 阶段 92 自检

| 维度 | 评分 | 证据与改进 |
| --- | --- | --- |
| 准确性 | 4/5 | 区分stdlib 0/0与TypeKernel预期拒绝错误，记录三份MCFL24产物同hash。 |
| 完整性 | 3/5 | 44个拒绝注册及缓存拒绝路径已核验；whole17和其他MNI仍未完成。 |
| 清晰性 | 4/5 | 将byte/short注册删除与long/double、转换及统一拒绝guard区分。 |
| 可执行性 | 4/5 | 阶段93列出49个ConversionData入口、MCFL25及最小往返验证。 |
| 简洁性 | 4/5 | 仅记录本阶段改动、验证和下一步。 |

平均3.8/5，whole17完整性仍为3/5。

## 历史必要检查：int/float/bool 原生运算符（阶段 91）

28个 `MNIOperator` 方法迁入显式单context入口：int 12（含 `~=` 的右侧 `RangeVar`）、float 11、bool 5（4个二元及一元 `!`）。Kotlin `NativeOperatorOperations` 直接调用typed core，避免递归dispatch；在 `runInFunction` 内规范化非 `ScoreBool` 的 `BaseBool` receiver/参数和结果，再ensure并发布真实引用。CompoundData保留旧2/3参数ABI并接受精确单context ABI。MCFL22→23。

`mcfpp-native-operator-context-stdlib.log`：标准库重建成功，BUILD SUCCESSFUL in12s，Project 0 errors/0 warnings；MCFL23、292007 bytes。source/build/资源三份bin SHA256一致：`800AF3AFB375C71643E23C11B2EB64261D3691F38F17D2204CC18FC62D477422`。

首轮联合 `mcfpp-native-operator-context-final.log` worker191正常、exit1/BUILD FAILED in19s：Cache XML `2026-10-06T18:45:32.190Z` 与PrimitiveIR XML `18:45:36.586Z`各1/0/0/0；新fixture XML `18:45:32.931Z`为1/1/0/0。source函数已编译0 errors/9118 warnings，但执行器不支持 `execute store success ... if score ...`，未到fresh consumer。执行器补入score比较/范围链及真实function返回值（缺失score仍报错、未知语法仍拒绝）后，`mcfpp-native-operator-context-final2.log` worker192正常、exit0/BUILD SUCCESSFUL in15s；新fixture XML `2026-10-06T18:51:39.029Z`为1/0/0/0，source0/9118、fresh0/9119。source与fresh consumer均执行磁盘函数；结果int/float/range/bool七项为1/1/1/0/1/0/0，frame0通过。三个不同用例跨轮各自通过，最终仅复查fixture，并非最终联合3项。未跑fullcheck/server；legacy循环、私有Var桥及其他MNI仍未完成，whole17保持ACTIVE。

## 历史必要检查：字典、Map与ImmutableList原生方法（阶段 90）

共13个方法迁入现有 `NativeCallContext`：Dictionary 4个、Map 6个、ImmutableList 3个。Java原生层只接收调用context，普通参数和实际结果值/位置经已有引用传递；领域实现分别在 `DictionaryOperations`、`MapOperations`、`ListOperations`。保留字典未知字符串key限制，Map允许dynamic key，readonly list允许dynamic needle。MCFL21→22，bin头为 `4c46434d16000000`。

同阶段修复了四个暴露的问题：`ExecuteBool.and` 对已知bool分支生成正确结果；`ScoreBoolConcrete.negation` 返回新常量而不原地修改输入；`MCString.getTempVar` 通过 `StorageAccess.capture` 创建有效地址；legacy if/elseif/else 将 `makeRuntime` 移到静态判定之后、dispatch之前。旧循环遍历仍保留，未声称whole17 rule 11已完成。

`mcfpp-native-collection-context-stdlib.log`：标准库重建一次成功，exit0/BUILD SUCCESSFUL in13s，Project 0 errors/0 warnings，292301 bytes。source `build/stdlib-index/bin.mclib`、`src/main/resources/datapack/bin.mclib` 与 `build/resources/main/datapack/bin.mclib` 最终大小与SHA256相同：292301 bytes，`EEFF5FC16E8751D87D4248E8380BBFCC4980FB08BD1E7BA6B06A1DD849E2B4D6`。

首次联合 `mcfpp-native-collection-context-final.log` worker186/FAILED15s：Cache XML `2026-10-06T17:51:44.690Z` 为1/0/0/0；新LibField fixture `17:51:45.439Z` producer报2个 `cannot assign dict to dict`，因fixture字典初始化缺少显式 `as dict<int>`。修复后final2 worker187/FAILED11s、XML `17:57:13.560Z`：source编译0/9123，但source执行缺少 `dictionary_cleared` score；final3 worker188/FAILED18s、XML `18:06:23.847Z`：source仍0/9123，在临时storage目标路径为空的执行命令失败；final4 worker189/FAILED13s、XML `18:12:36.327Z`：source仍0/9123，`mapEdit_clearedSize` score未生成/读取，consumer未运行。以上失败轮均未到fresh consumer断言。

最终final5 worker190正常，exit0/BUILD SUCCESSFUL in17s，LibFieldAccess XML `2026-10-06T18:20:53.447Z` 为1/0/0/0；source0/9123 warnings、fresh consumer0/9124 warnings。fixture从生成的source与fresh consumer磁盘函数执行，得到字典10/5、map227/7、只读列表2/11/-1，并通过frame0检查。此前CacheFormat旧/未知格式回归在首轮XML已通过，因此本轮最终仅复查fixture，不是联合2绿。Warnings包含已知flatExtends及StorageAccess类别；无fullcheck/server。仍有旧legacy `makeRuntime`循环与其余MNI待迁移，whole17保持ACTIVE未完成。

## 历史必要检查：其余 list 原生方法显式调用上下文（阶段 89）

其余10个列表原生方法已与阶段88的 `clear` 共用 `NativeCallContext`，共迁移11个方法。Java层通过context传递普通实参、结果值/位置；内部private Var桥复用 `StorageAccess`，领域操作仍由 `ListOperations` 执行。`Commands.tempFunction` 两个重载沿用父namespace、注册到已存在的canonical namespace，使用 `runInFunction` 恢复调用上下文；生成的函数标记 `bodyCompiled`，使库导出器能写出其函数体。

MCFL20→21。`mcfpp-native-list-context-stdlib.log`：exit0/BUILD SUCCESSFUL in11s，Project 0 errors/0 warnings；资源292007 bytes，MCFL21，build/stdlib-index、src资源、build资源SHA256均为`4E97F3CBAB14B1EF3A89991DEC1722121768A113AF1D243DFC9ABC7DE498561F`。阶段89标准库重建一次成功。

首轮 `mcfpp-native-list-context-final.log` 中Cache和ListMember用例通过，新列表fixture在consumer触发tempFunction namespace NPE并产生8个语言错误；source为0/9118 warnings。namespace注册和上下文恢复修复后，仅重跑fixture：`mcfpp-native-list-context-final2.log` worker185正常、exit0/BUILD SUCCESSFUL in18s；fresh XML `2026-10-06T17:31:52.308Z` 为1/0 failures/0 errors/0 skips，source0/9118、consumer0/9119。三项不同用例跨轮各自通过，最终只有该fixture单项复查，并非最终联合三项。测试实际执行consumer磁盘生成函数，列表结果和frame0检查通过。warnings来自既有flatExtends重复继承类别；无fullcheck/server，whole17及其他MNI入口仍未完成。

## 历史必要检查：list.clear 显式调用上下文（阶段 88）

新增36行 `NativeCallContext`，公开函数、receiver的ValueRef/Place、当前immutable `CompilerValue`快照及通用 `writeReceiver(CompilerValue)`；内部private Var桥执行显式 `function.runInFunction` 与 `StorageAccess` 恢复/写回。Java `clear` 改为单一context签名；NativeFunction使用实际 `invocationArgs` context，CompoundData识别精确单context ABI，其他native ABI保留。33行fixture通过真实实例owner在 `reset` 中执行clear/add；source与fresh库consumer均从生成的mcfunction执行得到7，入口栈帧为0。领域操作仍在 `ListOperations`，本阶段没有统一所有StorageAccess/MNI入口。

MCFL19→20：stdlib日志 `mcfpp-native-call-context-stdlib2.log`，exit0/BUILD SUCCESSFUL in16s、Project 0 errors/0 warnings；bin资源292301 bytes，SHA256 `9FC7D934242FEABE333BDFEFCFD5AE128CA306CDC97D8DC841BEACEE734F51AA`，raw header `4c46434d14000000`。首次stdlib尝试因 `List<NativeCallContext>` 类型推断错误于compileKotlin失败13s，无测试worker；显式 `List<Any?>` 修复后重建。首次processResources仍带旧hash，随后独立test调用同步build资源；build/stdlib-index、src资源与build资源三份最终产物大小及hash相同。

最终三项联合 `mcfpp-native-call-context-final.log`：worker183正常，exit0/BUILD SUCCESSFUL in23s。LibCacheFormatTest fresh XML `2026-10-06T16:48:53.890Z`、LibFieldAccessTest `2026-10-06T16:48:54.610Z`、ListMemberTest `2026-10-06T16:48:59.413Z`，每套件1/0 failures/0 errors/0 skips。source项目0 errors/9118 warnings、fresh consumer 0/9119、IR clear回归0/9118；警告是既有flatExtends重复继承字段类别。只证明指定三项，无fullcheck/server；whole17仍未完成。

## 阶段 87：`type` 仅用于泛型参数（已实现并限定验证）

新增 `TypeUsage` 统一判定及源码入口检查，覆盖已绑定普通签名、IR/擦除值与集合、延迟字段声明。普通值位置不保存 `TypeValue`；依赖普通 `type` 存储的旧30个正例撤回，4个合法的直接泛型类型表达式库往返 fixture 保留。9个新规则方法与相关回归跨轮验证；本轮不改变 MCFL19/bin292301，也未重建标准库、运行 fullcheck 或服务器。whole17仍未完成。

阶段87分轮验证：最初 RED `mcfpp-type-only-generic-red.log`，worker180/exit1，fresh `2026-10-06T15:02:56.067Z`，4项3失败；字段仍注册为 `Meta`、普通函数 type 参数未报错，是有效的拒绝缺口证据；locals 实得6个错误而非预期4个，const 漏了 `var` 使预期计数断言先失败，不能据此判断声明是否被拒绝；readonly/typealias 用例通过。首个18项联合因重复 `ValueSnapshot` import 在 compileKotlin 失败、无新 XML。修复后 `final2.log` worker181/BUILD FAILED in1m42s，fresh 18项中16过2失败：locals 用例被 `forced` 语法错误多报；const 初始化回归报 `Cannot build variable 'int' as the compiler cannot track its type`，对应底层 storage adapter 的 `T!` carrier 问题。最终 `final3.log` worker182 正常、exit0/BUILD SUCCESSFUL in39s，5/5通过：TypeVariableDeclarationTest 1（2026-10-06T15:55:51.615Z）、TemplateConstInitializationTest 1（2026-10-06T15:55:46.016Z）、CompilerOnlyViewTest 2（2026-10-06T15:55:52.513Z）、LibFieldAccessTest 1（2026-10-06T15:55:54.382Z），均0失败/错误/跳过。final2的18个不同用例已包含9个新规则方法；final3五项中两个原失败复查通过、两个CompilerOnly方法重复复查，只有阶段84 genericObjectStaticFieldsInitialize...是第19个不同用例。19个不同用例跨轮各自通过，不表示单次19项联合通过。适配修复以底层 runtime carrier 保持存储形状，但保留原 `T!` 语言类型和常量要求。详见 `mcfpp-type-only-generic-{red,final,final2,final3}.log`。阶段86历史验证如下。

## 历史必要检查：实际generic父项绑定（阶段 86）

8prod+2test（fixture99/helper2）+bin共11文件175+/35-；声明环境纯parser/tryResolve、actual T/N绑定后完整父项、Info nullable文本权威、Project/StringTest共享INDEX hook、只排CompiledGeneric父readonly名vars/properties。MCFL19；stdlib独立SUCCESS36s/compiler0/0，三artifact292301 bytes/header4c46434d13000000，SHA256 097F4A5ABA51792E4F748D25DBE0459BC38F03E97C2339358C763DF92125938C。

日志位于F:/DevCache/.codex/runtime/mcfpp-generic-parents-{red,stdlib,final,final2,diagnostic,diagnostic2,final3}.log。RED172 fatal FAILED9s：Parent<int>/Parent<(T)>/Offset<(N+1)>全文未找到→value缺失→Var633 NPE，无fresh XML。trial173 joint3，Lib XML13:26:27.137Z两fail、Cache13:26:26.471Z一green，READ_LIB与complete各BossBar报错共2errors；未模型/consumer。trial174 joint3 XML13:37:09.387Z三fail，各仅complete BossBar1error。diag175单1FAILED8s/XML13:44:53.283Z无表（root误只查std）；diag176单1FAILED8s/XML13:51:11.734Z表显示BossBar无parent。最终根因是StringTest未调用INDEX hook，不是tryResolve单独解决；全部TEMP已删除。

final3 worker177正常exit0/SUCCESS1m1s，XML2026-10-06T14:00:39.735Z 3/0/0/0。新86 source0errors/9118warnings、fresh0/9121warnings，source盘4/9/bool1/5、fresh4/9/bool1/10/frame0及canonical父/字段/read owner、TypeID、Shift4→Offset5/Shift9→Offset10模型全部通过。旧85正例source0/9118 fresh0/9119；三final负例各source1/9118、fresh1/9119为expected拒绝。警告均既有flatExtends重复类别，source也有，不能写source0/0。cache在trial173绿，最终仅联合3，跨轮4不同case各过。无fullcheck/server；不扩super、source object/interface actualgeneric父、Kryo全集或compiler-only字段。当时下一87待RED/实现，现已完成限定验证；whole17未完成。

## 历史必要检查：abstract/final标志持久化（阶段 85）

8prod34+/19-加两个fixture110行共9文件144+/19-，另资源bin更新。source标志/compiled final、ordinary及generic final父拒绝、abstract默认ctor跳过、两Info final及Kryo prefix/early壳已限定接入。MCFL18；不扩TypeAS、实际generic父参数、interface runtime/shape全套或abstract运行时构造。

RED worker170正常FAILED9s，fresh XML2026-10-06T12:31:02.661Z 2fail：positive source3errors/0warnings（abstract标志缺失），negative首Source Child:Closed未拒绝；尚未fresh与后两case。单独stdlib SUCCESS14s、compiler0/0；src/resource、build/stdlib-index、build/resources均header4c46434d12000000、287554 bytes，SHA256 3EE448D21ADA523A2CD08565671A309B865C4DAEC52340AAA0C26E75888038D1。

最终必要联合3 worker171正常exit0/SUCCESS25s：LibFieldAccess XML2026-10-06T12:43:21.888Z 2/0/0/0；LibCacheFormat XML12:43:29.774Z 1/0/0/0。positive source0/0、fresh0/9119，source/fresh实际磁盘4/9/4/4/frame0、abstract/noCtor、Contract绑定int签名、final prototype/compiled及fresh独立模型全部通过；negative三条case各source1/0、fresh1/9119，明确拒绝及父关系通过，不能称负例0errors。旧/未知缓存格式拒绝回归绿。无fullcheck/server；自评4/3/4/4/4平均3.8、whole17未完成；下一86待RED/API实现。

## 历史必要检查：generic object静态字段初始化（阶段 84）

7prod+1fixture共125+/22-：共享对象marker静态root、constructor/字段完成/导出；静态binding/cold read保留标志与完整快照，派生read/write保留parent，避免同名normal参数被替换。显式初始化，不依赖自动load；不验证generic compiler-only字段/直接Kryo object fieldtype。MCFL17/bin289989/schema未变，无stdlib/fullcheck/server。

RED worker166 fatal ClassCastException（StaticMemberView强转ObjectDataTemplate），FAILED7s、无fresh XML，旧83 XML不计84。joint3 worker167新84 source0/0、模型/初始化检查通过但执行缺value score；旧82与ordinary绿。final2 worker168新84绿，ordinary执行缺frame.value失败：派生field adapter丢parent导致同名参数污染；没有以缺score回退掩盖。

final3 worker169正常exit0/SUCCESS23s，必要联合2全部通过：Lib新84 XML2026-10-06T12:15:59.928Z 1/0/0/0；ordinary Template XML12:15:54.957Z 1/0/0/0。source0/0、fresh0/9119已知warnings，source4/9/4/9、fresh9/4/9/4、ordinary7及frame0、真实files均通过。三个不同case跨轮各过，最终仅联合2；未运行全量检查或服务器。自评准确性/完整性/清晰性/可执行性/简洁性4/3/4/4/4，平均3.8；整个17项未完成。下一85 source/fresh abstract/final flags尚未实现或测试。

## 历史必要检查：生成名字与合法源码名字隔离（阶段 83）

阶段83仅两prod表达式：GenericDataTemplate生成标识符的编号分隔符改为'-'（Settings_int-0），SpecializationPolicy generic wrapper改relay-0；共享factory仍覆盖ordinary/object/interface，FullID仍origin Declaration+冻结arguments，key/options/metadata/schema/MCFL17/bin289989不变，无stdlib/fullcheck/server。42行source-only fixture generatedSpecializationNamesDoNotOverwriteLegalSourceDeclarations让合法Settings_int_0与relay_0同时存在，无consume/in-memory fallback；只验证generated-vs-legal名字、真实targets/files与物理owner storage prefix，不扩一般member Function.prefix、casefold或跨库同名机制。

RED mcfpp-generated-name-collision-red.log：worker163正常FAILED8s，fresh XML2026-10-06T11:13:40.509Z 1fail/0error/skip，source0/0/cache过；盘执行缺temp_2462 mcfpp_default，无结果断言，不能写4→9。writer同settings_int_0/static/read及relay_0_int重复，两个调用均旧relay_0_int。试joint4 worker164正常FAILED15s，XML11:17:58.039Z 4tests/1fail；旧79/82/80各绿（source0/0，库fresh0/9119已知warnings），新83盘4/9/4/9/frame0和object namespaceID distinct已过，第1705行失败是ROOT fixture误比较两个read的Function.prefix（既有行为忽略owner），非namespace/生产rename失败。只改为比较各owner.prefix，其他断言不变。

最终-final2.log worker165正常exit0/SUCCESS8s，fresh XML2026-10-06T11:23:47.843Z 新1/0/0/0，当前source0/0，无consume；盘4/9/4/9/frame0、generated Settings_int-0与合法Settings_int_0的object namespaceID/owner prefix、free function namespaceID/prefix及四targets distinct均绿。最终仅新1，四个不同case跨轮各过，非最终联合4全绿。82提交6512e115f0fc8d7d2770b60e6516169450829cf2（6文件97+/17-），83提交见Git历史；whole17未完成。下一84 generic object typed static字段与显式constructor初始化，先单fixture RED验证source/fresh磁盘4/9，不做自动load；已知StaticMemberView concrete casts、constructor guards、compiledobject ctor导出/共享静态NBT root待实证，尚未实现/测试。

## 历史必要检查：source generic特化磁盘导出（阶段 82）

阶段82只改DatapackCreator（12行新增/2行删除）：genNamespace局部exportCompound以IdentityHashMap backing set按对象身份去重，ObjectCompoundData复用genObject、普通DataTemplate复用genTemplate，递归GenericDataTemplate.compiledTemplates；现有template/object根接入。genFunction/genTemplateFunction/genObject及自由函数行为原样，不套imported bodyCompiled过滤、不扩companion/interface/autoload或空prototype整理。46行fixture sourceGenericSpecializationsExportAllRuntimeTargetsToDisk只source，不consume；source Box1/relay1/Settings2缓存复用，实际namespaceID文件与磁盘执行无内存fallback。

RED mcfpp-source-generic-export-red.log：worker161正常FAILED8s，fresh XML2026-10-06T10:58:59.571Z 1fail/0error/skip，source0/0、cache断言通过；真实盘调用缺fixture.fields:box_type_0/_init_box_type_0_0_int（ScoreCommandExecutor283），未到文件/结果断言。FINAL必要联合3：worker162正常exit0/SUCCESS18s；LibField XML2026-10-06T11:02:57.476Z 2/0/0/0（新82+旧80），Template XML11:03:03.015Z 1/0/0/0（普通restoredObjectMethods）。新82 source0/0，Box显式init/read与两Settings read文件存在，relay wrapper实际磁盘调用，结果4/9/4/9、frame0均通过。旧80 source0/0、consumer0/9119且磁盘4/9/4；旧普通object source0/0、consumer0/9119，init/set/read7及来源独立。最终联合3全绿，不是分轮合计或fullcheck。

仅受测source generic template/object输出，不声称所有generic/abstract语法或接口runtime可用；autoload、静态字段强转/字段初始化、interface runtime、legacy wrapper/Kryo全集、空prototype导出及整体IR/MNI旧体系保留。MCFL17/bin289989/schema/注册/codec未变，无stdlib/fullcheck/server。81提交2437ccfa9af2f21ddb3b2c9201b25535a4418f28（10文件202+/37-），82提交见Git历史；whole17未完成。下一83仅internal generated名字与合法source碰撞，先单source fixture RED：Settings<N>与Settings_int_0、relay wrapper与relay_0在真实target/file/prefix上隔离；拟仅两个现有identifier表达式末尾加'-'并保留separator，FullID/key arguments不变，不造命名系统、不预设schema/stdlib变化，尚未实现/测试。

## 历史必要检查：generic interface静态TypeValue（阶段 81）

阶段81仅五prod：FieldVisitor恢复interface真实currTemplate/typeScope，generic prototype只prepareHeader并finally恢复；共享compile复制isInterface，Specialized身份按origin interface kind；共享BodyVisitor不生成接口默认ctor。Type解析/有限resolveSpecialization及现MCFPPGenericDataTemplateType.tryResolve按exact interface声明和canonical快路径恢复，复用已注册serializer，无新wrapper/registry。shared member声明入口补f.isAbstract=ctx.ABSTRACT()!=null，FunctionInfo原有字段，不改metadata。115行fixture frozenGenericInterfaceTypeArgumentsRestoreBoundSignaturesAcrossLibraryRoundTrip不构造/调用interface，Contract<T>抽象exchange参数/返回绑定Int/Bool，static TypeValue进入普通Box的runtime int字段。

source模型与library写入、fresh独立prototype/Contract/Box/abstract函数、readonly Meta/scopeT/CompilerOnly完整snapshot/cache.arguments、interface FullID/有限resolver、noCtor及前置Box签名通过；source Int4→Bool9、fresh反序Bool9→Int4，真实consumer磁盘4/9/frame0。仅static interface TypeValue/T int,bool，不验generic继承grammar/shape转换、runtime接口布局、annotations、一般source abstract/final flags、defaults全集；legacyInterface wrapper和Kryo全集未激活/验证。sourcewriter递归导出、autoload、静态字段强转及整体IR/MNI旧体系仍未完成。

日志前缀mcfpp-generic-interface-type-value，目录F:/DevCache/.codex/runtime。RED worker158 FAILED7s，两前置Contract<int>/bool Invalid type后FieldVisitor528 currTemplate NPE fatal，SKIPPED/无fresh XML（旧80 XML10:17:31.086Z不计）；无producer完成计数/库/source检查/fresh/disk。joint3 worker159正常FAILED26s，fresh XML2026-10-06T10:37:28.651Z 3tests/1fail(new81)/2pass(old73+old80)，三个producer各0/0，旧consumer各0/9119，磁盘73=4/9、80=4/9/4/frame0。新81 source模型部分flags/noCtor/snapshot/ID/resolver通过，exchange.isAbstract第1557行失败为生产遗漏ABSTRACT标记，未到consumer/disk；仅补shared声明入口一行，未改fixture/schema。

final2 worker160正常exit0/SUCCESS13s，fresh XML2026-10-06T10:43:19.510Z 新1/0/0/0，source0/0、consumer0/9119已知warnings，全部模型及consumer磁盘通过。最终仅新1复查，三个不同case跨轮各过，不是最终联合3全绿；80轮普通ObjectMethods不算81第四case。无Info/backing字段、codec/Kryo注册/VERSION变化，MCFL17/bin289989不变，无stdlib/fullcheck/server。80提交3301770618f70ce16f79050213d106c4913f4743（常规18文件224+/176-），81提交见Git历史；whole17未完成。下一82仅source磁盘导出先单fixture RED，尚未实现/测试。

## 历史必要检查：generic object静态身份与方法（阶段 80）

阶段80共13个prod文件（含删除47行旧GenericObjectFieldVisitor）：generic object及compiled object使用ObjectCompoundData/self companion，共享一个factory及TemplateBody注册visitor，prototype只准备header。Member.isStatic和Function命名/prepareBody识别静态owner；ObjectType/GenericObjectType保稳定object身份及有限exact object lookup/canonical重绑定，GenericInfo恢复self而不另造companion。复用原generic冻结/缓存/声明完成及lazy body编译，生成运行时命令，不执行用户constexpr。未添加Info/backing class字段、Kryo注册或TypeId codec；MCFL17/bin289989不变，无stdlib/fullcheck/server。

83行fixture genericObjectReadonlyValuesShareCanonicalSpecializationsAcrossLibraryRoundTrip：source Settings<4>→9→4，fresh 9→4→4；2个canonical对象、N CompilerOnly完整snapshot/cache.arguments、prototype/compiled self-companion、method owner/isStatic及canonical StaticMemberView this、source/fresh独立模型与object FullID/快照稳定、有限resolveTypeId返回canonical对象通过。source执行模型/库写入断言，真实磁盘执行来自consumer，结果4/9/4、frame0。仅static N/runtime int返回；StaticMemberView字段39–49强转、字段初始化、autoload、interface、local writer空prototype/漏compiledTemplates递归导出及genericobject wrapper作为直接库fieldtype的Kryo路径未验，不宣称全集。

日志前缀mcfpp-generic-object-readonly，目录F:/DevCache/.codex/runtime。RED worker155 fatal ClassCastException TypeVisitor161，FAILED8s，无fresh XML（旧79 XML09:46:39.847Z不算80）；只SKIPPED，无producer结果/库/source模型/fresh/disk。final联合3 worker156正常FAILED32s：LibField2 XML2026-10-06T10:12:58.042Z旧79绿/新80红，Template1 XML10:13:02.626Z普通ObjectMethods绿。新80 source0/0、部分source模型/key/typeId通过，过强this==null夹具断言失败，实际为StaticMemberView(Settings9)，未到全部source/fresh/disk。仅将fixture改为StaticMemberView及其canonical模板断言，未再改prod。

final2 worker157正常exit0/SUCCESS10s，新80 XML2026-10-06T10:17:31.086Z 1/0/0/0，source0/0、consumer0/9119已知warnings，全部source/fresh模型及反序consumer磁盘通过。最终仅新1复查；三个不同用例跨轮各自通过，不是最终联合3全绿。79已提交84c373d3f8c4179392bdf04c0ac14d3bb05dc486（12文件262+/65-），80提交见Git历史。下一81仅generic interface静态TypeValue，先单fixture RED，尚未实现/测试；优先既有GenericDataTemplate/GenericType+现注册serializer/isInterface及TypeId kind，不预设新wrapper/registry/schema。whole17未完成。

## 历史必要检查：泛型函数readonly依赖签名（阶段 79）

七prod：Function.parseDeclaredType仅在generic用户函数自身readonly Identifier terminal出现在type AST时保存现有UnresolvedType文字；member先参数后返回，param/return统一Unknown adapter保留类型。SpecializationPolicy.resolveBoundSignature在声明环境中以独立FunctionScope顺序绑定完整readonly值，ParameterMatcher和compileGeneric共享真实normal/return类型；只Unresolved签名按Identifier token/readonly位置归一，已知类型仍FullID。StorageAccess.freezeReadonly接受已cast输入，登记完整snapshot/types、fresh Symbol及CompilerOnly root/parts；GenericDataTemplate等价复用。compiled移除本地readonly占位后安装fresh绑定，ordinary运行时实参保持Unknown key；不执行用户body或type运行时反射。延期识别仅GenericFunction，共享compileGeneric不代表新增generic extension/native入口已验证。

95行fixture（import+94行method）genericFunctionDependentTypesBindBeforeRuntimeArgumentsAcrossLibraryRoundTrip验证relay<T>(Box<(T)>)->Box<(T)>：source dynamic int4→int9→bool7，fresh bool7→int9→int4，caller影子T=string不污染绑定。相同int/runtime4/9共用一个wrapper、bool独立，恰好2；normal/return canonical Box及origin、scopeT实际Builtin/Meta/type绑定、source/fresh原型/函数/wrapper/template独立、FullID/snapshot/cache.arguments稳定（不比跨fresh declarationSymbolId），consumer真实磁盘4/9/7/frame0通过。仅static T/runtime int字段，不验bool运行时行为、后续复制修改、defaults全集、任意typedef表达式或重载等价、GenericObject/Interface、用户constexpr。dependent default literal已延期到实际bound type cast并在isError时早退，但无新增默认值断言。local writer仍可输出空generic prototype；本fixture验证imported bodyCompiled消费端主体导出，不声称所有writer只导wrapper。

日志前缀mcfpp-generic-function-dependent-types，目录F:/DevCache/.codex/runtime。-red.log worker153正常FAILED8s，fresh XML2026-10-06T09:30:39.558Z 1fail/0error/skip，producer14/0首header T不完整→Symbol T/Invalid Box及return/read级联；genIndex仍尝试lib/debug写出，write.errorCount断言终止，未到source模型/fresh/disk。-final.log compileKotlin FAILED15s，无worker/无fresh XML：MCFPPValue仅interface不继承Var，freezeReadonly错误返回接口导致类型/属性编译失败；只修helper返回Var并检查interface，未改断言。

-final2.log worker154正常exit0/SUCCESS35s，最终必要联合5全部通过；LibField2 fresh XML2026-10-06T09:46:39.847Z，两producer0/0、consumer0/9119；SpecializationPolicy3 XML09:46:45.392Z，三项0/0。新79、旧裸T绑定/普通实参缓存/CompilerOnly容器及78匿名alias@DataOnly实际磁盘均绿，不是跨轮合计或fullcheck。七prod+fixture+四docs预计12文件；未增加Info/class fields/wire/schema，MCFL17/bin289989不变，无stdlib/fullcheck/server。78已提交fa29ec67549817392b86c62f435d3ee459ec7201（11文件160+/25-）；79提交记录见Git历史。下一80先单fixture RED界定generic object Settings<N as int>及(Settings<4>).read()/9的source/library/fresh；已知TypeVisitor prototype cast、kind/type解析缺口待实证，先不扩interface/autoload/字段，wire/version按实际实现核对。whole17未完成。

## 历史必要检查：匿名alias冻结类型身份（阶段 78）

六个prod入口：两个visitor在真实anonymous model创建时转存field annotation，并停止全局annotation pass错误进入匿名body；TypeAlias.cachedTarget和SimpleLibScope.cachedAliasTargets仅读取已缓存目标，不触发解析。template Declaration恢复按完整ID匹配named与alias目标，tryResolve后要求template===唯一，否则诊断/null，不用语义equals去重。TempPool匿名名改data-N，用户Identifier不能包含'-'，既有NamespaceID保留；无global registry、新TypeId variant或wire字段。

fixture frozenAnonymousAliasTypeArgumentsRestoreCanonicalTypesAcrossLibraryRoundTrip使用匿名X@DataOnly、透明Y及捕获旧合法data_N的named声明，两个目标引用/完整ID不同；source X4→Y9，fresh Y9→X4。前置readBox Box<X>、canonical T.value/scope.types[T]、source/fresh prototype/Compiled独立、FullID/snapshot及consumer磁盘4/9/frame0通过。只验证alias有根匿名、static T/runtime int，不实例化X，不验匿名method/constructor，不保证重新parse/rebuild稳定ID、全部跨library碰撞、无根匿名或Opaque。

日志前缀mcfpp-generic-anonymous-type-value，目录F:/DevCache/.codex/runtime。初始-red.log worker149 fatal AnnotationVisitor.currTemplate!! NPE，FAILED7s，无fresh XML；旧77 XML08:36:39.179Z不属于78，未到write/source模型/consumer。-annotation.log worker150正常exit1/FAILED16s：新78 XML2026-10-06T09:05:17.281Z 1fail，producer0/0、source@DataOnly/canonical/write通过，consumer3 errors/9119 warnings（两次frozen T及read undefined）；既有TemplateFieldInference2 XML09:05:21.696Z均绿。-final.log worker151正常FAILED23s：LibField XML09:13:28.179Z 2tests/1fail，旧71绿；Template XML09:13:33.020Z anonymousForward1绿。新78 producer7 syntax errors源于fixture将named data置于alias之前，仅移动fixture，未改grammar或放宽断言。

最终-final2.log worker152正常exit0/SUCCESS10s，新78 XML2026-10-06T09:17:26.630Z 1/0/0/0，producer0/0、consumer0/9119，全部模型与磁盘通过。最终仅新1方法复查；四个不同用例跨轮各自通过，不是最终联合3或fullcheck。六prod+fixture+四docs预计11文件；MCFL17/bin289989/layout/签名schema未变，无stdlib/fullcheck/server。阶段77已提交4925f67c363e0f2856bfc2e34af89441bf167665（7文件147+/23-）；阶段78提交记录见Git历史。阶段79先以单fixture RED界定generic函数自身readonly依赖Box<T>普通形参/返回的source/library/fresh路径，优先复用UnresolvedType文字placeholder与共享boundSignature；尚未实现/测试，schema需按实际改动核对，whole17未完成。

## 历史必要检查：direct Union TypeValue表达式（阶段 77）

阶段77两个prod：MCFPPType.data改为lazy CompoundData(Type,mcfpp)，commonType=MCFPPConcreteType.Type并injectedBy新增19行Java MCFPPTypeData；类标注NoExternalWrites，MNIOperator('|')接受type/返回type，returnsConstWhenArgsConst=true，返回MCFPPTypeVar(MCFPPUnionType(actual caller/other types))。复用两visitor既有Native dispatch，无grammar/visitor override、registry、runtime Union或用户函数求值。内建scope在编译器中按需初始化，未新增namespace持久Native签名，oldbin库读/consumer通过；不重建stdlib、不升VERSION。两prod+78行fixture+4docs预计7文件。

unionTypeExpressionsShareCanonicalSpecializationsAcrossLibraryRoundTrip以sourceScalar alias(int|string)作对照，前置readBox Box<(int|string)>；ordinary intType=int/scalarType=(intType|string)→Box<scalarType>(4)，direct Box<(string|int|int)>(9)，fresh反序direct9→named(string|intType)4。source/fresh MetaUnion两alternative IDs/snapshot与alias一致，同Box cache/frontparam/T binding、fresh对象隔离、FullBoxID/T snapshots稳定及consumer实际磁盘4/9/frame0通过。T仅静态，真实字段int；Union runtime值/布局未验证。

RED mcfpp-generic-union-expression-red.log：fresh XML2026-10-06T08:32:26.901Z，1fail/0error/skip，worker147正常exit1/FAILED7s；producer7/0，最早front readonly与ordinary named left及重复readonly的type | type不支持，invalid type/any read/readonly incomplete级联。仍genIndex写lib/debug，write() errorCount断言终止；后续source模型/consumer读库/fresh/disk未到。FINAL mcfpp-generic-union-expression-final.log：fresh XML2026-10-06T08:36:39.179Z，1/0/0/0，worker148正常exit0/SUCCESS23s，source0/0、consumer0/9119，完整模型及consumer磁盘通过。最终必要1，非fullcheck/stdlib/server；MCFL17/bin289989/wire/schema不变。阶段76提交bfa00985dae6954038f6caaf69352d9a74adf107（6文件119+/19-），阶段77提交以Git历史为准。

## 历史必要检查：冻结SelectorTypeValue（阶段 76）

阶段76仅MCFPPType.resolveTypeId一行Selector分支，复用MCFPPEntityType(limit,entities,isName).takeIf完整ID相等；保持原顺序、null、引号与flag，无registry/schema/runtime扩展。frozenSelectorTypeArgumentsPreserveFiltersAcrossLibraryRoundTrip新增76行及MCEntity import；加4docs预计6文件。

实际fixture覆盖Selection alias entity<2,"minecraft:pig","!minecraft:cow">及bare entity（null limit/types），前置readSelection/readAnyEntity进入真实wire。Box静态T、真实int字段，source selected4→general9，fresh反序general9→selected4；原引号/ordered types/false的完整Selector ID、两Box/snapshot不同、source/fresh prototype与Compiled独立、前置param canonical、FullID/T snapshots稳定及fresh字段通过；consumer真实磁盘4/9/frame0。isName=true未有source入口，entity世界/runtime未验证；null不归一empty，但empty source未覆盖。

RED mcfpp-generic-selector-type-value-red.log：fresh XML2026-10-06T08:16:47.105Z，1fail/0error/skip，worker145正常exit1/FAILED10s；source0/0、模型及写库通过，fresh读库后4次Cannot restore frozen T及2次read undefined，共6errors/9119warnings，fresh模型/disk未到。FINAL mcfpp-generic-selector-type-value-final.log：fresh XML2026-10-06T08:20:24.280Z，1/0/0/0，worker146正常exit0/SUCCESS20s，source0/0、consumer0/9119，模型及consumer磁盘通过。仅FINAL1，不是joint2/fullcheck；既有serializer/wire未改，MCFL17/bin289989/schema不变，无stdlib/fullcheck/server。阶段75已提交d6dfb7f57b5844839c00a9df5261c61d9d1acd00（6文件118+/22-），阶段76提交以Git历史为准。

## 历史必要检查：冻结向量TypeValue（阶段 75）

阶段75仅一个生产文件MCFPPType.kt与68行fixture及import接通限定Vector身份恢复；加4docs共6文件。Applied分支识别Builtin(vector)，读取唯一Builtin数字key.toIntOrNull，构造MCFPPVectorType(dim)并核对FullID；原四种元素类型工厂不变，不新增>0维数约束、registry、wire或Vector runtime支持。

frozenVectorTypeArgumentsRestoreDimensionsAcrossLibraryRoundTrip以前置readTwo(Box<vec2>)/readThree(Box<vec3>)进入真实wire；Box的T仅静态，字段仍int。source维数2→4/3→9，fresh反序3→9/2→4；实际T dimension2/3、不同完整Specialized ID/不可变snapshot、前置参数与main canonical、source/fresh prototype及Compiled独立、FullID/snapshot稳定均到达，consumer真实磁盘4/9/frame0通过。

RED mcfpp-generic-vector-type-value-red.log：fresh XML2026-10-06T08:02:09.718Z，1failure/0error/skip，worker143正常exit1/FAILED11s；producer0/0、source模型及写库通过，fresh读库后4次Cannot restore frozen T of Box及2次read undefined，共6errors/9119warnings，未到fresh模型或disk。FINAL mcfpp-generic-vector-type-value-final.log：fresh XML2026-10-06T08:07:30.426Z，2/0/0/0，worker144正常exit0/SUCCESS24s；新75与旧71 frozenDeclarationAndContainerTypeArgumentsRestoreCanonicalTypes各source0/0、consumer0/9119，模型及consumer磁盘均过。仅必要联合2项，非fullcheck/server/stdlib；MCFL17/bin289989/schema不变。阶段74提交16e1051e672ba8a6d732d09239e1d13c92d18025（7文件144+/19-），阶段73为186a712f；阶段75提交以Git历史为准。

## 历史必要检查：源码联合类型与冻结身份（阶段 74）

阶段74保留旧UNION token '&'及unionTemplateType/UnionDataTemplate，新unionType要求至少一个PIPE '|'；primary/var/expression优先级不变。MCFPPType新union解析递归传scope/caller，invalid项诊断并返回null，不补Any；复用既有MCFPPUnionType规范化。有限TypeId.Union resolver要求非空、所有alternative可恢复及最终FullID相同。两prod+fixture+4docs共7文件；wire/schema/MCFL17/bin289989不变，无stdlib/fullcheck/服务器，最新完整346项仍为72dc557。

frozenUnionTypeArgumentsNormalizeAcrossLibraryRoundTrip仅通过typealias Scalar=(int|string)、ReorderedScalar=(string|int|int)输入静态T；Box真实字段仍int。source/fresh alias顺序/重复归一，T不可变snapshot/FullID、同canonical Box、前置readBox参数及fresh独立对象均验证，producer0/0、consumer0/已知9119 warnings；仅consumer实际磁盘4/9/frame0。direct readonly union expression及Union实际runtime值/布局未验证。

红测mcfpp-generic-union-type-value-red.log：fresh XML2026-10-06T07:39:55.386Z，worker140正常exit1/FAILED6s，最早两处mismatched input '|' expecting {')','&',NL}；只到source语法，没有library restore RED。最终-final.log worker142正常exit0/SUCCESS37s；fresh XML LibField1 2026-10-06T07:50:55.410Z、DataTemplate1 07:51:00.071Z、LibCacheFormat1 07:51:00.252Z、TypeKernel1 07:51:00.258Z，全部0fail/error/skip。必要联合4项包含新fixture、unionsNormalizeOrderDuplicatesAndNesting、immutableTypeIdentitiesRoundTripThroughLibrarySerialization及DataTemplateTest.unionTest。旧unionTest实际0errors/4warnings（common representation未证明的as警告）及既存TODO tellraw templateData，无errorCount或disk断言；只算语法smoke，不宣称Union runtime已验证。阶段74提交以Git历史为准。

## 历史必要检查：冻结Specialized类型值（阶段 73）

阶段73两个生产文件MCFPPType、MCFPPGenericDataTemplateType（42+/22-）与新增89行fixture完成限定Specialized恢复。共享resolveSpecialization(id)复用现有类型查找、snapshot restore与prototype.compile，使用consumer当前target/options，核对template Declaration的namespace/kind/name及最终FullID；resolver委托该入口，tryResolve保留READ_LIB守卫和currentcanonical快速路径。

前置Holder<Cell<int>>自由函数签名进入真实wire。source/fresh Holder.T、字段/ctor/read/free签名指向canonical Cell，Cell.T及成员为Int；fresh prototypes/Compiled不同于source，FullID及T snapshots稳定。只反转实例构造4→9/9→4，不宣称生成index反转；source/fresh模型及consumer真实磁盘4/9/frame0通过。schema/MCFL17/bin289989未改，无stdlib/fullcheck/实际服务器。Union/Vector/Selector/Opaque及generic object/interface未扩展。

日志F:/DevCache/.codex/runtime/mcfpp-generic-specialized-type-value-red.log：fresh XML2026-10-06T07:14:31.258Z，1fail，source0/0、source模型及库生成/读取已过；consumer completeTemplateDeclarations两次Cannot restore frozen readonly argument T Holder，随后read/unknown未定义，共4 errors/9119 warnings，fresh模型与disk未到；worker138正常FAILED10s。最终-final.log XML2026-10-06T07:21:12.937Z，tests2/failures/errors/skips0（新73+旧72），worker139正常exit0/SUCCESS23s；两个producer0/0、两个consumer0/9119，模型及disk4/9/frame0各过。这是最终必要联合2全过，不是新全量或5项跨轮。阶段72提交ee46fba877b9af3570ddeb1b8f65f8742d22c11d，exact11文件197+/32-；阶段73当前7文件含4docs，提交以Git历史为准。

## 历史必要检查：完整静态类型集合（阶段 72）

六个生产文件ExprVisitor、ConcreteExprVisitor、MCFPPType、MCFPPGenericDataTemplateType、GenericDataTemplate、StorageAccess与一个fixture完成限定路径。普通表达式统一出口将最终StaticMemberView归一为MCFPPTypeVar；初始Meta带selector转回静态view。共享registerSnapshotTypes遍历Typed/TypeValue/Sequence/Record；readonly CompilerOnly绑定复用internal seedParts登记子facts与长度。Concrete完整已知索引复用Indexable.getByIndex及Property getter，未知/不完整索引仍拒绝。visitSelector消费下一成员前读取中间PropertyVar，使this.cell.read保留DTO receiver；最终赋值property不提前读取。TypeId resolver仍限阶段71支持集，无schema变化。真实库纵向验证限list<type>/Sequence；Record walker代码接通不代表任意Record、Specialized或Opaque已验证。

前置readBundle的Bundle<[Leaf]>签名经过真实wire；named types与direct [Leaf]共享canonical Bundle/Cell。source/fresh Bundle、Cell、Leaf为不同对象，完整TypeId稳定；仅反转两实例构造顺序，不宣称生成index自然反转。最终producer0 errors/0 warnings，consumer0 errors/已知9119 warnings，完整模型与真实磁盘4/9/frame0全部到达。

日志目录F:/DevCache/.codex/runtime，前缀mcfpp-generic-type-collection。-red.log：fresh XML 2026-10-06T06:31:46.159Z，1fail，producer10/0，worker134正常finish，FAILED in7s；helper仍尝试debug/index。-final.log必要联合5：LibField2 XML06:40:28.704Z旧71绿/新72红，CollectionStorage2 XML06:40:35.002Z全绿，CompilerOnlyView1 XML06:40:35.187Z绿；总4/5，worker135正常，FAILED in27s。新72 producer0/0、consumer0/9119及完整模型已过，但Cell.read缺this.value，尚未到磁盘4/9/frame0。

-runtime-diagnostic.log单1：fresh XML 2026-10-06T06:49:46.687Z，worker136正常，FAILED in10s；owner/scope正确，bundle.read未unwrap中间PropertyVar，普通Var调用缺this，ctor及free→Bundle receiver正确。临时打印已删除。最终-receiver-final.log：fresh XML 2026-10-06T06:58:57.286Z，tests1、failures/errors/skips0，worker137正常，exit0/SUCCESS in14s，actual4/9/frame0。首轮4绿+新72最终1=5个不同用例跨轮各自通过，非最终联合5。阶段72提交以Git历史为准；MCFL17/bin289989未变，无stdlib/fullcheck/实际服务器。阶段73Specialized类型值尚未实现或测试。

## 历史必要检查：声明/容器类型值身份（阶段 71）

阶段71四个生产文件ConcreteExprVisitor、MCFPPType、MCFPPGenericDataTemplateType、FieldVisitor及一个fixture完成限定类型值路径。Concrete在visitExpression统一出口归一最终类型StaticMemberView，裸primary也覆盖；Meta带selector时转回StaticMemberView保留静态成员选择。resolveTypeId仅支持builtin/Wildcard、完整ID校验的Declaration及既有四种单参数Applied工厂；generic type只剥顶层Typed链后的TypeValue并注册已有types map。普通typed MCFPPDataTemplateType字段以unknown buildUnConcrete登记，其他类型/T!仍原build，真实receiver沿既有codec默认，shape校验保留。wire/layout/签名schema未改，MCFL17/bin289989未变，无stdlib/fullcheck/服务器。

新fixture的LeafAlias/Leaf/list<int>在source及fresh consumer字段、constructor、read和自由函数参数full ID/canonical断言及磁盘4/9/7/frame0全部到达；旧70模型与磁盘4/6/8/bool1/frame0亦复查通过。producer两次0 errors/0 warnings，consumer两次0 errors/已知9119 warnings。

日志前缀`mcfpp-generic-type-value-identity`，目录F:/DevCache/.codex/runtime。`-red.log` fresh XML `2026-10-06T05:56:12.123Z` 1fail，producer5 errors，worker130正常finish，BUILD FAILED in6s；裸Leaf/alias快捷primary未成为类型值。初次`-final.log`联合6项LibFieldAccess3 XML `06:02:49.065Z` 2绿1红、Cache3 `06:02:55.136Z` 3绿，总5/6，producer仍5 errors；worker131正常，BUILD FAILED in24s。归一移至Expression后`-expression-final.log`单1 XML `06:12:21.301Z` producer22 errors/0 warnings，worker132正常，BUILD FAILED in7s：晚声明Leaf默认{}先被FieldVisitor冻结，Leaf完成后clone结构校验失败，尚未进入consumer。失败轮helper仍尝试输出debug/index，不能写成未生成文件。

最终`-field-final.log` XML `2026-10-06T06:18:57.009Z` tests2、failures/errors/skips0，worker133正常finish，Gradle exit0/BUILD SUCCESSFUL in15s。最终是必要2项复查，非最终联合6；首轮5绿+新71方法1=6个不同用例跨轮各自通过，旧70在最终2中复查不额外计成第7个。阶段70提交`1d593d19c71f5c42a3adb4aaf0e2dbdaae5c1b16`；阶段71已提交`baa8f0704d58fcbc29706cb28181d821c9cc6138`。普通visitor命名类型值/list<type>、Sequence/Record递归snapshot、Concrete known index留阶段72；Union/Vector/Specialized/Selector/Opaque及全集、object/interface未验。

## 历史必要检查：类型表达式声明作用域（阶段 70）

六个生产文件ConcreteExprVisitor、MCFPPType、Function、FieldVisitor、ImVisitor、DataTemplate与一个fixture完成限定纵向路径。类型解析、readonly表达式及bucket/NBT/container/union/!递归透传显式lookup scope和真实词法caller；FunctionScope checked lookup保留raw null屏蔽，其他IScopeWithType不假设有vars、不回退caller.vars、不创建fake Function。Concrete七处binary运算符索引改为`ctx.op[i-1]`。ImVisitor七处whole-scope控制流转换共用helper，跳过CompilerOnly及无runtime表示的值，保留trackLost/barrier；普通赋值及显式dynamic转换约束不变。DataTemplate两个结构检查仅排除isStatic，保留真实字段类型校验和各自原nullable规则。

fixture以caller的T=bool、N=90影子验证Envelope<Int,2>/Bool4声明绑定，fresh consumer反序实例化。nested `Cell<(T)>`、`Sized<(N+1)>`覆盖字段、constructor和method参数/返回canonical签名及private owner；readonly N快照2/4、Sized实参3/5。最终producer 0 errors/0 warnings，consumer 0 errors及已知9119 warnings；模型断言和磁盘4/6/8/bool1/frame0全部到达。

`mcfpp-generic-type-declaration-scope-red.log`首次在N不完整及binary op索引越界处fatal，worker异常退出，无fresh XML；旧`04:46:28` XML不计为阶段70有效assert红测。`-final.log`联合30项29过1失败，exit1/1m12s：LibFieldAccess9 XML `2026-10-06T05:21:31.442Z`新方法因TypeVar.toDynamic NotImplemented失败；LibMemberAccess3 `05:21:46.932Z`、ConstructorExecution7 `05:21:28.434Z`、SpecializationPolicy11 `05:21:50.247Z`全绿。

`-control-final.log`必要7项，正常worker、exit1/12s：Logic6 XML `05:31:50.465Z`全绿，新方法XML `05:31:47.979Z`失败，producer96 errors源于结构检查误把静态T/N要求为运行时字段。`-payload-final.log`最终仅复查该方法：XML `2026-10-06T05:40:52.111Z`，1/0/0/0，worker129正常finish，exit0/BUILD SUCCESSFUL in16s。29+Logic6+最终1=36个不同用例跨轮各自通过，不是联合36项或最终7项全绿。MCFL17/bin289989完全未变，未重建stdlib，未运行fullcheck/服务器。阶段69提交`10267379e30bd43af6c38bcd7e6a673d195a6102`；阶段70提交`1d593d19c71f5c42a3adb4aaf0e2dbdaae5c1b16`；当时阶段71尚未实现，现限定结果见最新记录。

## 历史必要检查：显式 generic 类型与跨库 canonical 特化（阶段 69）

专用serializer只写入immutable specialization `TypeId`，读取shell；所有includes读取完成并进入COMPILE后，再恢复canonical prototype/cache。源码readonly实参以冻结的CompilerValue恢复，不依赖mutable genericVar或生成index。新增arity检查；当时TypeValue registry仅覆盖builtin/formal types如Int/Bool，Declaration/Applied TypeValue仍待支持。准备阶段复用FieldVisitor，实际`prepareHeader`在原声明file上下文中、绑定readonly实参之前运行；共享`completeTemplateDeclarations`提前解析namespace普通形参与返回并按parent-first完成缓存实例继承，随后刷新签名。模板参数签名刷新`param.typeName`，避免恢复shell仍带`Cell[]`时两个read overload生成相同namespace ID；不改全局getter。晚期ready compile先flat再执行annotation/completion；这不是INDEX_TYPE全局预扫描。

fixture在generic与晚声明普通Base之前声明`readCell`/`readFlag`及同名Int/Bool overload。源码bucket index为Int0/Bool1，fresh consumer自然反转为Bool0/Int1；源码Bool实参是`true`，固定score观察结果为1。源码与consumer实例对象及生成identifier不同，但冻结TypeId相同；constructor参数、private Var/Property owner与protected Base owner canonical，两个read overload各自namespace ID不同。consumer实际磁盘执行4/9/Base 2/Bool 1，frame0。负向Plain<3>及缺失Cell实参分别以独立项目验证并产生语言诊断。fixture helper进入COMPILE阶段，与正式项目流程一致。

红测`mcfpp-generic-template-explicit-type-red.log`：XML `2026-10-06T04:14:18.082Z`，1失败，producer 7 errors，6s。首轮19项`-final.log` exit1/1m29s：LibFieldAccess8于`04:23:17.448Z`为6过2失败（bare Cell未拒绝；Kryo尝试向只读集合add时异常），TemplateInitialization8于`04:23:33.298Z`及LibCacheFormat3于`04:23:33.289Z`通过。之后`-complete.log`必要5项exit1/18s：LibFieldAccess2于`04:34:44.076Z`均失败、Cache3于`04:34:48.397Z`通过；库读取成功，但bare Cell早期scope-return遗漏，字符串helper仍处于READ_LIB，未执行tryResolve，consumer两处`read()`未定义，尚未进入consumer模型/磁盘断言。

最终`mcfpp-generic-template-explicit-type-restored-final.log`正常worker、exit0、BUILD SUCCESSFUL in24s：LibFieldAccess2于`04:46:28.322Z`及LibCacheFormat3于`04:46:33.118Z`均全过。生成index反转、source/fresh实例对象和生成identifier不同但冻结TypeId相同、private owner、overload namespace ID及磁盘4/9/2/bool1/frame0断言通过；缺参和不接受readonly实参的普通模板诊断通过。17项首轮通过 + 最终2项 =19个不同用例跨轮各自通过，不是一次联合19项。标准库日志`mcfpp-generic-template-explicit-type-stdlib.log`；无fullcheck/服务器。BoundT/N及Declaration/Applied TypeValue未覆盖。

## 历史必要检查：未注解 generic 类类型绑定（阶段 68）

修改仅限三个入口：generic prototype 的FieldVisitor仍解析readonly签名/parent，但将成员body、default constructor和abstract检查延至T绑定后；AnnotationVisitor保持top-level行为并跳过prototype body；特化复制`isAbstract`，注册实际成员并运行原abstract检查，`flatExtends`后设置`currTemplate`为实例，再由现有annotation visitor转存字段annotations后complete/apply/refresh。范围限于未注解普通generic class；top-level/method annotation持久化、source abstract/final flags到model、generic object/interface、显式`Cell<int>`类型注记未覆盖。

新fixture producer以Int 4/9共享同一Compiled、Bool true使用另一Compiled；consumer按Bool/Int反序实例化并核对`scope.types[T]`、字段/构造器参数/read返回的Int/Bool、private Var/Property canonical owner及稳定TypeId。磁盘consumer读取4/9；通过固定外部score `#generic_bool` 初值0、真实if分支设为1观察bool结果，frame0平衡。literal type/value readonly绑定沿用阶段67；本阶段没有生产while/return/executor改动。

实现前红测 `mcfpp-generic-template-type-binding-red.log`：XML `2026-10-06T03:48:33.608Z`，1项失败，producer expected0/actual3，构造参数、read返回和字段各报`Invalid type: T`，未进入库/consumer。首轮17项联合日志 `mcfpp-generic-template-type-binding-final.log` 正常结束、76s/exit1：LibFieldAccess6 XML `03:51:23.534Z` 为5过1失败，另TemplateInitialization8 `03:51:39.066Z`、LibCacheFormat3 `03:51:39.057Z`全过。producer/consumer的模型、owner、TypeId和实际Int磁盘结果4/9以及frame0已通过；测试初次在读取`flagScore`时误读取data-only变量的score。

观察变量复查 `mcfpp-generic-template-type-binding-complete.log`：XML `03:56:45.151Z`，1失败，仍为执行期读取未运行else分支的scope变量寄存器名；真实true/else continuation存在，不能解释为续接语句丢失或bool产生错误，4/9/frame0已过。最后fixture改用LogicStatementTest已有固定外部score观察方式，单方法 `mcfpp-generic-template-type-binding-observable-final.log` XML `2026-10-06T04:00:36.056Z`，1/0/0/0、worker正常、BUILD SUCCESSFUL in10s；磁盘4/9、`#generic_bool`为1、frame0平衡。16项首轮绿 + 新方法最终单项绿 =17个不同用例跨轮各自通过，不是最终联合17项。MCFL16/bin286243未变，无stdlib重建、fullcheck或服务器。日志均位于`F:/DevCache/.codex/runtime/`。

边界仍有generic object/interface、top-level/method annotations、source abstract/final flags与显式`Cell<int>`类型注记等未验证语法；下一阶段见next-stage-plan.md。

## 历史必要检查：generic 类只读实参与源码特化（阶段 67）

generic prototype 的 readonly 签名与源码实例化入口已接通。`AbstractTemplateInfo` 保存 generic kind/parent factory，构造器恢复接受明确 owner；完整 immutable argument snapshot 用于 `SpecializationKey` 与基于原型的 `TypeId`，producer 和反向 consumer 身份一致。readonly argument 是独立 `CompilerOnly` 静态绑定，不进入实例重定位、默认载荷或runtime参数物化。已知完整的动态局部`n`可作为readonly值；`Var.assignedBy`在动态转换前冻结完整值，保留`n=3`后再次读取`n=5`。移除普通`GenericDataTemplate`过早的constructor body遍历，真实调用复用lazy `compileBody/prepareBody`并保持`this`上下文。DTO参数转换跳过static/CompilerOnly绑定。

新增真实库roundtrip fixture覆盖源码`Box<N as int>`的readonly实参、同参复用/异参特化、private owner与实际磁盘命令：producer readonly特化实参为3/3/5，consumer反序恢复为5/3/3，磁盘运行结果4/9/6并检查`readArgument`读取3/5，frame0平衡。负向fixture使用无法证明完整值的runtime parameter `n`（即使调用点传3也拒绝），普通`Plain<arg>`同样拒绝；已知完整动态局部值不在拒绝范围。

标准库一次 `regenerateStdlib` 成功，Project 0 errors/0 warnings，BUILD SUCCESSFUL in16s；MCFL16，`bin.mclib` 286243 bytes。红测 fresh XML `2026-10-06T02:22:19.229Z`，1项失败，producer报告10个错误，首要诊断为旧路径的 Variable n must be const；这里的 n 是 main 中完整已知的 dynamic 局部值，并非runtime parameter。测试尚未生成库或进入consumer。之后三轮worker异常（`mcfpp-generic-template-roundtrip-final.log` 17s、`-complete.log` 17s、`-stabilized.log` 16s）均无fresh XML；失败位于producer编译，旧XML时间戳不得当作该轮结果。最终修复后单项第五fixture通过：XML `03:00:07.465Z`，1/0/0/0，BUILD SUCCESSFUL in34s。

余下33项回归首轮有30项通过、3项失败：LibFieldAccess4 XML `03:02:15.941Z`（2失败：嵌套owner的StackOverflow与未限定实例执行未终止），ConstructorExecution7 XML `03:02:13.453Z`（1失败：缺少`temp_2098`），SpecializationPolicy11、TemplateInitialization8、LibCacheFormat3分别于`03:02:22.320Z`、`03:02:23.132Z`、`03:02:15.927Z`全过。之后10项控制复查有6过4失败，LibFieldAccess3 XML `03:22:40.533Z`与ConstructorExecution1 `03:22:38.650Z`因执行器取不到runtime return scoreboard失败，LogicStatement6于`03:22:46.568Z`全过。`Function.assignReturnVar`修复后，最终LibFieldAccess3、ConstructorExecution1、LegacyFloatIR1共5项全过，XML依次为`03:27:56.379Z`、`03:27:54.688Z`、`03:28:04.725Z`，0 failures/errors/skips，worker正常、BUILD SUCCESSFUL in45s。30个首轮绿 + 6个逻辑控制 + 最终5项（含原33项中的3个失败用例、第5generic fixture及旧float ABI1项）=41个不同用例跨轮各自通过，不是一次联合41项。后续失败的修复边界：runtime-bound且非DTO/非CompilerOnly的InternalFunctionScope局部读取clone binding逐层加一并清`readVersion`，写入clone逐层减一，不改变源binding/mask；旧while进入/离开barrier清runtime facts/cache。普通runtime return用既有`bindIncomingParameter`创建无initializer的稳定目标，各分支assignedBy后仅失效值知识，再于callee内读取稳定register，避免未初始化score物化和弹帧后访问。`T!`、CompilerOnly与void guard保留；这不宣称普通return跨while/do全面支持。日志位于`F:/DevCache/.codex/runtime/mcfpp-generic-template-roundtrip-{stdlib,red,final,complete,stabilized,bound-values,regressions,control-final,return-final}.log`。无fullcheck/服务器；阶段66提交`1d67cdf`。

## 历史必要检查：当前实例未限定字段寻址（阶段 66）

来源分析确认`CompoundDataScope`中的普通实例字段声明，且当前scope的raw `getVar("this")`返回真实`DataTemplateObject`后，才将该字段转给`receiver.getMemberVar(key, caller)`。更近FunctionScope局部返回原raw结果（包括`fieldVarSet`产生的null）；object/static、无owner readonly、无receiver及未解析名保持原行为。没有改变`Internal`全局lookup/putVar或frame布局。

实现前红测停在producer错误数assert：XML `2026-10-06T02:01:40.044Z`，5个语言错误，首要诊断为`Symbol not defined: hidden`；未生成库或进入磁盘执行。修复后LibFieldAccessTest4 + ConstructorExecutionTest7单次联合11项通过，0 failures/errors/skips、Gradle exit0/36s；fresh XML时间`2026-10-06T02:04:38.416Z`、`02:04:49.046Z`。producer/consumer及真实磁盘命令执行均通过，验证constructor未限定写入、nested if/while更新、第二实例、local shadow和protected字段预期5/9/8/6，frame0平衡；负向访问consumer继续产生预期语言错误。日志 `mcfpp-unqualified-instance-field-red.log`、`mcfpp-unqualified-instance-field-final.log`。MCFL15/bin286207未变，未重建stdlib、fullcheck或服务器；阶段65提交`d181e10`。

## 历史必要检查：来源感知未限定字段权限（阶段 65）

`FunctionScope.getVar(key)`原始virtual lookup恰好调用一次，null时立即返回，以保留`Internal.fieldVarSet`与原始stackIndex；随后单独沿`FunctionScope`首parent链检查vars来源。任何更近的FunctionScope局部（包括祖先局部）允许；CompoundDataScope中的Property/Var按声明owner/access检查。两个visitor在读取或物化拒绝结果前诊断并返回`UnknownVar`；Concrete fallback遵守已有`isError`标记。范围是字段访问权限，不宣称普通实例未限定字段已正确寻址；不改变`Internal`查找/putVar。

红测唯一方法失败于`IntruderLeak`读取继承Base.private未被拒绝（XML `2026-10-06T01:49:30.383Z`）；producer和consumer均0语言错误，正向磁盘执行的object=7、local shadow=8、Base限定字段=4已通过。最终单次LibFieldAccessTest3 + TemplateInitializationTest8共11项全通过，0 failures/errors/skips，Gradle exit0/1m09s；fresh XML时间戳`2026-10-06T01:52:42.391Z`、`01:52:52.484Z`。正负向诊断及真实库恢复通过；日志 `mcfpp-unqualified-field-access-red.log`、`mcfpp-unqualified-field-access-final.log`。MCFL15/bin286207未变，无stdlib重建、fullcheck或服务器；阶段64已提交`bda4d5a`。

## 历史必要检查：库字段与 Property 权限（阶段 64）

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

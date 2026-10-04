# 类型系统重构会话交接（2026-10-04）

## 阅读顺序与当前状态

这是当前会话的工程交接记录，保存用户意图、已实现内容、验证结果和后续工作，不是逐字聊天日志。
下一会话先读本文件，再读 [下一阶段计划](./next-stage-plan.md)、[迁移状态](./migration.md)、
[目标规范](./specification.md) 和 [验证记录](./verification.md)，随后重新核对源码和 Git 状态。

**整个重构方案尚未完成。** 已实现可构建的阶段迁移，不能将模型类或纯查询的存在视为全部语言路径已经接入。
尤其是语言 `as` 仍调用旧 `explicitCast`，any/object 尚未使用完整的公共擦除载荷机制，旧 MNI 和 Concrete 体系仍有大量路径保留。

本会话结束时用户要求：“我将要迁移会话。将当前会话以及下一部分的内容存入文件，随后提交推送仓库更改。”
本文件和下一阶段计划用于该交接；提交范围包括会话开始时已有的浮点后端及算术修改。

## 仓库与基线

- 工作目录：`/home/Alumopper/Projects/MCFPP`。
- 分支：`kotlin-latest`；上游：`origin/kotlin-latest`。
- 远程：`https://github.com/MinecraftFunctionPlusPlus/MCFPP.git`。
- 本轮提交前 HEAD：`c273813`（修复编译器控制流与浮点运行问题）。交接提交本身的哈希以 Git 历史为准。
- 初始工作区已经包含 26.3 浮点数值提供器、算术语法和测试等未提交修改，必须保留。
- 初始 `.codex/config.toml` 和 `MNI-METAPROGRAMMING-PLAN.md` 也作为现有工作保存。
  MNI 计划是另一份拟议设计，不能据此擅自扩展此次类型重构的用户范围。
- 本轮未使用子代理，没有创建远程 PR，没有部署数据包，没有运行实际 Minecraft 服务端。

初始基线在隔离源码中实际执行 `check --rerun-tasks`，结果为 65 个测试通过、8 个任务实际执行。
初始浮点后端已有 10 项断言测试，算术语法已有 8 项；详见 [基线记录](./baseline.md)。
临时基线源码、补丁和运行日志曾放在 `/tmp`，迁移到其他机器时不要依赖这些文件。

## 用户确定的语言规则与边界

这些规则继续约束后续实现，允许修改旧行为并同步迁移仓库内标准库、测试和示例：

1. `object` 是所有普通值类型的统一静态超类型；`any` 是可利用实际类型知识的宽松类型。
   `mcfpp.lang:DataObject` 保留为模板基类，位于 object 下。原始 `nbt` 不是所有 NBT 编码值的语言父类型。
   void、错误恢复、未解析类型和泛型通配符不参与普通值继承。
2. `int` 与 `float` 承担日常算术，混合运算、赋值及传参允许 int → float，不允许隐式反向转换。
   byte/short/long/double 和 NBT 数组表达精确 NBT 格式，不因 Kotlin 继承或存储实现进入普通算术。
   bool 的 ByteTag 编码不改变 bool 类型身份。
3. object 只开放声明的公共操作；知道实际载荷类型不能额外开放成员。
   any 实际类型已知但值未知时允许该类型的操作；无法唯一确定实际类型时，具体操作和重载需要显式 as。
   已知 any 用实际类型匹配参数，未知 any 只能直接传给 any/object；信息丢失处应有警告。
   不新增自动运行时成员分派或强制类型标签。
4. TypeId 与显示名分离；别名透明，泛型身份含所有实参，联合类型规范化。
   类型关系查询纯粹且统一，参数转换方向一致；重载顺序为精确、具体名义父类型、数值提升、object、any。
   可变泛型集合默认不变，结构兼容不加入普通子类型关系。
5. Symbol 声明身份稳定；ValueRef、Place、TypedView、FlowFacts 和 StorageLayout 分担职责。
   Exact/Candidates/Unknown 类型知识与 Constant/Partial/Unknown 值知识独立。
   未初始化、真实空值、不可达和错误分别建模；缓存载荷不能保存可变 Var、Tag 或作用域对象。
6. const 是赋值限制；T! 要求完整编译期值；dynamic 要求运行时表示；编译器专用类型受物化能力限制。
   不能用 `is MCFPPValue` 同时代替这些条件。
7. `x as T` 建立同一数据位置的解释视图，不调用构造器、不补默认字段、不复制模板对象、不进行数值转换。
   any 来源不做运行时类型检查；普通来源不兼容或无法证明时警告后继续尝试生成访问。
   目标没有对应布局访问能力时才给代码生成错误，并建议转换函数；不把宽松警告升级为阻断错误。
8. 数值转换使用 toInt/toFloat/toByte/toShort/toLong/toDouble；toNBT 编码来源类型。
   不提供未知 any 的转换兜底。不能只实现常量路径而伪装成运行时支持。
   常量求值须遵守后端规则，未证明等价时保留运行时操作。
9. 模板结构检查处理必需/可选字段、额外字段、只读递归、可写不变、访问控制、循环结构和抽象能力。
   静态成员及构造器不参与形状比较；使用目标方法，不做来源方法的运行时鸭子分派。
10. 同一底层对象的多个视图必须共享位置；写入失效重叠字段的常量事实及存储缓存，无法定位时保守失效整个对象。
    普通模板赋值仍遵循原有复制/传参约定，不能一并改成引用赋值。
11. 采用基本块和显式读写的轻量 IR，不要求完整 SSA。
    分支取可达路径共有事实，循环做保守不动点，break/continue/返回及不可达路径参与汇合。
    不再依靠进入动态分支时遍历整个作用域调用 toDynamic。
12. 用户函数需要效果摘要；未标注 MNI 和原始命令按未知副作用处理。
    原始命令前提交可能被观察的延迟写入，之后撤销可能被修改的数据事实及缓存。
13. 物化和失效分离，缓存依位置身份和写入版本；形参、返回、递归帧、实体 NBT 应使用同一存储接口。
    旧浮点模拟与 26.3 数值提供器按显式能力表选择，未知目标统一诊断，禁止字符串大小比较和浮点表达式重排。
14. any/object 共用擦除载荷传递机制；已知类型可保留具体表示，需要统一表示时使用来源编码的 NBT 槽。
    编译器对象不能借 object 获得 Minecraft 存储能力，可静态传递者通过内部特化处理。
15. 泛型值实参要求完整不可变常量，键包含声明、实参、目标能力和生成选项。
    保留泛型所需特化，普通参数的常量组合特化不进入默认路径，不为优化执行一般用户函数。
16. 同一语言成员只保留一份签名，可分别提供常量求值器与运行时实现。
    MNI 最终迁入显式上下文、值引用、常量载荷和位置引用；集中适配后删除旧接口分支并升级缓存。
17. 不新增用户编译期函数系统、运行时反射、自动动态分派、通用优化框架、JIT 或自动跨版本存档迁移。
    新旧浮点持久化布局不能自动互换；需重新初始化或使用另行实现的迁移函数。

原计划分为：固定基线 → 类型内核 → 值/位置与 int/bool IR → 控制流/存储 → any/object/as/转换 →
全部类型、浮点、泛型与 MNI → 删除旧体系。每阶段须可构建，内部适配不能变成长期公开的第二套语义。
当前只是阶段性实现，不符合最后两个阶段的完成条件。

## 本会话实际实现

### 类型与调用关系

- `type/TypeId.kt`、`TypeRelations.kt` 实现稳定身份、纯关系/转换/运算查询、容器不变及规范联合类型。
- 透明别名支持前向引用、链和循环诊断；库索引保存展开后的别名目标及接口标记。
- 语言 object 与 DataObject 关系已经接入；内部 `ObjectVar` 改为 `StaticMemberView`，原生签名同步更新。
- `ParameterMatcher.kt` 统一普通、泛型、原生函数匹配，验证单一候选、默认值和歧义，并保留 T! 完整值要求。
- 已修复 Byte.build 忽略实参、集合零哈希、double/LongArray 注入 byte 成员等明确缺陷。
- int/float/bool 的常量与运行时成员共用 instanceData，删除三份对应的 Concrete 原生签名类。

### 值、位置与基本块

- `analysis/CompilerValue.kt` 提供不可变快照；`ValueModel.kt` 提供 Symbol、Place、独立知识状态、FlowFacts、StorageLayout、StorageVersions。
- `TypedIR.kt` 提供基本块、显式读写、转换/视图/调用节点和效果模型；FlowAnalysis 支持分支汇合、循环固定点及失效。
- `PrimitiveCompiler.kt` 已贯通无模板所属、普通 int/bool 形参、返回 void/int/bool 的受限函数路径。
  支持已初始化局部声明、赋值、加减乘、比较、布尔运算、if/else-if/else、while、break/continue、返回及无插值原始命令。
  不支持的语法在生成命令或修改作用域前退回旧 visitor。
- 该路径延迟物化常量，不因无关分支物化整个作用域；dynamic 强制保留运行时表示。
- `CompileSettings.foldIRConstants` 只控制已迁入 IR 的常量优化，不能当作全编译器优化开关。
- 不支持 return 命令的旧目标使用递归条件栈；嵌套原始 function 命令不会重置调用方的条件帧。
- T! 声明在适配对象替换后由 Symbol 保留约束；完整快照代替仅检查 MCFPPValue 标记。

### any/object、返回与特化

- any 直线路径保留实际类型；未知具体操作给错误；object 不额外暴露具体成员。
- type 值可经 any/object 内部特化传参；dynamic 声明拒绝编译器专用载荷。
- 原生 Java 调试视图保留编译器对象，不能因缺少完整常量快照就构造运行时 JavaVar。
  任意可变 Java 对象的通用特化缓存仍未完成。
- `SpecializationPolicy.kt` 忽略普通参数常量值，保留泛型、T! 和可快照的编译器参数；泛型先绑定类型再构造参数/返回。
- 普通函数体按需编译一次；声明查找基于 AST 声明身份；模板构造器仍走旧构造适配和特化路径。
- 整数、布尔以及两种浮点后端的运行时返回使用独立临时位置，避免连续/嵌套普通调用覆盖结果。
  递归临时值的完整帧分配、非标量返回尚未统一。
- 动态分支的最后一个编译返回不能变成函数常量，缺失返回路径会诊断。

### 转换、浮点及存储修复

- `backend/NumericConversions.kt`、`mni/ConversionData.java`、`lang/conversions.mcfpp` 提供具体数值源重载及 toNBT。
  支持矩阵见 [转换文档](./conversions.md)。未知 any 无兜底，其余缺失后端明确报错，包括常量输入。
- byte/short 有符号窄化保留来源，常量与命令在整数边界上对照；int/byte/short 到 long/double 精确扩大。
- long/double → int 保留 data get；旧 int/float 转换保留模拟库调用，修正 inp/res 使用 `int` objective。
- 旧浮点栈与 NBT 使用 `{sign:int,int0:int,int1:int,exp:int}`，26.3 使用 FloatTag；原有 26.3 算术后端保留。
- 修正旧浮点符号/指数工作寄存器错位；工作寄存器不再返回带过期宿主值的 Concrete 对象。
  未证明旧库与宿主浮点一致，因此旧浮点算术及比较保留模拟库调用，旧余数给明确错误。
- `MCNumber` 不再实现 OnScoreboard；MCInt 提供单记分板接口，模板旧浮点分量按实例定位。
- bool 编码为 byte，short 编码为 short；byte/short 丢失跟踪时作用域仍保存对应类型。
- 动态 list 的查找和 clear 修复内部编码/类型问题；这些修复不代表全部集合迁入新模型。

### 结构检查与缓存

- `ReinterpretationCompatibility.kt` 是纯查询，覆盖可选/必需、额外、只读/可写、私有、嵌套/循环及抽象能力。
  **尚未连接语言 as，不能用该测试通过证明视图已经实现。**
- `TargetCapabilities.kt` 统一已支持版本、包格式、浮点后端和 return 能力；未知未来版本不猜测。
- 库缓存采用 MCFL 版本 2，旧缓存要求重建；Kryo 先读取声明身份再读取循环元数据图，避免未填充字段造成空指针。
- `regenerateStdlib` 从源码重建 `src/main/resources/datapack/bin.mclib`，不重写数据包。

## 最后一次验证（已实际执行）

```sh
./gradlew regenerateStdlib -Dorg.gradle.jvmargs=-Xmx2g -Pkotlin.daemon.jvmargs=-Xmx2g
./gradlew check --rerun-tasks -Dorg.gradle.jvmargs=-Xmx2g -Pkotlin.daemon.jvmargs=-Xmx2g
git diff --check
```

- 标准库：0 语言错误、0 语言警告，任务成功。
- 完整 check：140 测试，0 失败、0 错误、0 跳过；8 个任务实际执行，无缓存命中；最后运行耗时 3 分 39 秒。
- TypeKernelTest 21；ValueModelTest 11；PrimitiveIRTest 12；LibCacheFormatTest 3；ConversionTest 12；
  ReinterpretationCompatibilityTest 6；SpecializationPolicyTest 10；原有 FloatProviderTest 10。
- 两种浮点后端及旧无 return 目标有覆盖；独立命令执行器严格拒绝未知指令，不能替代服务端验证。
- Kotlin/Gradle 仍有冗余 cast、未使用参数或弃用提示，不能把“0 语言警告”理解为工具链无警告。
- 旧测试仍有只打印诊断的用例，Gradle 通过不等于所有历史样例都符合新语义。
- **没有实际 Minecraft 服务端验证。** 旧模拟库精度、舍入、异常处理和跨所有存储来源的等价性未完成。

运行日志在本机 `/tmp/mcfpp-final-standard-library.log` 和 `/tmp/mcfpp-final-verification.log`；
正式结论和边界已写入仓库文档，不需要迁移临时日志。
本次验证后只新增交接文档，没有继续修改编译器实现。

## 后续仍必须完成

- 扩展 IR 到其余语法、全部类型、调用和效果摘要，不能只留下模型节点。
- any 候选实际类型贯通分支、参数、返回及集合；删除 lastVar 承载，建立共用擦除载荷位置。
- as 接入真实 TypedView、模板结构查询、别名写入失效和布局能力诊断；迁移旧数值 as。
- 存储版本/缓存接入全部 scoreboard/NBT 同步、实体路径和递归帧，清除过期常量及返回临时覆盖风险。
- 模板构造和剩余常量组合特化迁入模型，全部成员签名与 MNI 接口统一。
- 补齐转换后端和其他 toNBT 重载，按后端验证折叠；旧 visitor 的除法/余数等仍需审计，不应宣称全局等价。
- 删除 Concrete 双层继承、旧转换分支、hasStoredInStack/trackLost 以及临时适配层。
- 迁移标准库、测试、示例和持久化说明，并记录实际目标服务端验证结果。

下一阶段的具体实施入口、顺序和验收用例见 [next-stage-plan.md](./next-stage-plan.md)。

# 显式值转换 API

标准库提供 `toInt`、`toFloat`、`toByte`、`toShort`、`toLong`、`toDouble` 的具体数值源重载。
没有未知 `any` 的兜底重载；已知实际类型的 `any` 按该类型匹配。
bool 不因 ByteTag 编码而匹配 byte 参数。

当前运行时支持如下。`—` 表示编译错误，包括输入为常量的情况；不通过宿主强转假装支持。

| 来源 | toInt | toFloat | toByte | toShort | toLong | toDouble |
| --- | --- | --- | --- | --- | --- | --- |
| int / byte / short | 精确值 | 所选浮点后端 | 有符号 8 位窄化 | 有符号 16 位窄化 | 精确扩大 | 精确扩大 |
| float | 所选浮点后端 | 原值 | — | — | — | — |
| long | data get 后端操作 | — | — | — | 原值 | — |
| double | data get 后端操作 | — | — | — | — | 原值 |

byte/short 窄化采用低 8/16 位再按有符号数解释。运行时先复制来源记分板，再规范余数与符号；不会修改来源。
例如 `toByte(128)` 为 -128，`toShort(65535)` 为 -1。常量折叠与生成命令在整数边界上分别执行并比较。
int/byte/short 到 long/double 的扩大保留全部来源位。

26.3 后端的 int/float 转换采用 `minecraft:from_int` / `minecraft:from_float`。
int 到 float 使用单精度表示，大整数可能失去低位；例如 16777217 表示为 16777216。
已有后端对可求值的 float 到 int 转换采用向零截断，超出 32 位有符号范围的已知值报错。
运行时值的异常结果由目标数值提供器决定；实际服务端验证仍待完成。

旧浮点后端采用现有 `_scoreto` / `_toscore` 操作，包括常量输入。
尚未证明模拟库与宿主浮点的舍入等价，因此这些显式转换不直接用 Kotlin 强转折叠。
旧后端的浮点算术和比较同样保留模拟库调用，不将工作寄存器包装成带宿主常量的对象；旧后端余数明确报错。
long/double 到 int 同样保留 Minecraft `data get` 操作及其范围与舍入行为，不调用 Kotlin 强转折叠。
这些路径的精度和异常输入仍需目标服务端对照验证。

`toNBT` 当前有 int、float、bool、string、byte、short、long、double、原始 nbt、NBT 数组和 DataObject 重载。
它生成来源约定的 NBT 编码，保留 ByteTag / ShortTag / IntTag 的区别。
常量编码复制 Tag，运行时编码通过内部存储搬运完成。
旧浮点分量后端使用 `{sign:int,int0:int,int1:int,exp:int}` 复合编码，栈保存与恢复逐项搬运四个分量。
26.3 后端使用 FloatTag；这两种持久化格式不会自动互相转换。数值提供器后端的已知非有限输入编码会报错。
集合、text 等源重载以及其余数值转换仍待后端实现。

语言 `as` 现在通过 StorageAccess/TypedView 解释同一 Place；两个表达式 visitor 均不再调用旧 explicitCast 或 NumericConversions。
例如 `value as float` 不等于 `toFloat(value)`，不会产生 from_int/_scoreto，也不会把来源 IntTag 改成 FloatTag。
未使用视图不强制物化；实际访问时保留来源编码。旧浮点要求四分量布局，访问已知整数/布尔标量布局会诊断并建议 toFloat。
普通来源无法证明兼容时警告，any 来源不插入类型标签或运行时验证；完整的其余布局诊断和存储迁移仍见 [迁移状态](./migration.md)。

普通 int/bool/any/object 自由函数的 IR 调用返回值也有独立 Place，支持 `(identity(value) as int)` 直接建立视图；读取发生在后续有副作用的调用之前。已知 any 返回的实际类型可以用于运算和重载绑定，调用与返回载荷仍在运行时执行，不生成转换、类型标签或一般用户函数的编译期结果。
普通集合调用还能传递子类型和嵌套长度，static 整体替换按效果位置恢复这些信息并清除旧后代。参数证据来自求值时的快照，调用边界清除全部常量值，返回分支只保留共同形状；因此不会按普通常量实参预执行函数或折叠其返回值。已证明的返回长度可用于旧无宏目标的负索引归一。
可编码 list/dict/ImmutableList 的形参和返回也可使用 IR 帧载荷；已知键及列表已知/动态下标的视图在分支和 while 中共享原 Place，普通集合副本保持独立。Location 捕获运行时下标，负数在 RHS 改写容器前归一；命名视图与 static 写回保持原选中地址。未知范围只保留共同成立的类型与形状；一次写入不能证明整片范围的实际类型。嵌套字面量的较早操作数同时捕获载荷和形状，后续 static 写回不改变该快照；这些路径不执行数值转换。NBT 数组及 map 下标/六个成员/投影也已迁入同一 IR；其余集合成员及未知字典字符串键仍使用内部适配边界。

list/dict/map/ImmutableList 与 NBT 数组的 as 视图也共享来源位置和编码，普通集合赋值则复制。ImmutableList 限制列表槽与变更成员，不把可变来源变成深度不可变数据。含编译器专用元素的集合不能因 as 或 dynamic 自动生成 NBT 表示；这不增加集合的 toNBT 源重载。
可编码字典的四个成员已接入 IR。共享视图的 remove/clear/merge 写回同一位置，普通输入载荷先捕获再合并；merge 字面量可使用接收者的泛型上下文，已有字典变量仍遵守泛型不变规则。
可编码列表的 add/prepend/addAll/prependAll/insert/removeAt/clear 也已接入 IR。变更移动元素原有编码及子形状；批量操作和自追加使用捕获的来源副本，动态插入索引保留求值时的数值，负数按操作发生时的长度解释。list/ImmutableList 的查询及可变列表按值删除也已进入 IR，与旧入口共用值比较和命令后端；类型未知的擦除列表须显式 as，查询不添加运行时类型标签或转换。
NBT 数组字面量、复制与物化保留 ByteArrayTag/IntArrayTag/LongArrayTag 格式。byte/long 数组元素参加 int 运算须显式 toInt；按格式索引不调用转换函数，bool 的 ByteTag 编码也不赋予 byte 数组赋值能力。
三种数组及 byte/long 载荷已接入 IR 的共享视图、普通/静态参数、返回和循环，long 的复制直接保留 NBT，不经 32 位记分板。支持宏的目标对未知长度的负数字面下标按求值时长度归一；无宏目标保留原字面负索引入口。
map 的下标与六个成员也已进入 IR，继续使用 entries 布局和字符串值键；as 不创建缺失的 entries，也不转换旧布局。写入/合并浅覆盖完整 value，已知键读取保留实际类型，未知键只使用共同类型证据；动态键谓词在 RHS 调用前捕获。普通副本独立，命名共享视图在条目移动后继续按键定位。keys/keyValueSet 投影也已进入 IR，结果为独立副本，保留子字段事实与形状；字典投影仍拒绝未知键及运行时空字段名，不引入新的字段名转换后端。
do…while 和直接闭合整数区间 for 已使用 IR 控制流。区间两端要求 int，按顺序求值后捕获，不经旧 range 的 Float 宿主值；包含上界且在递增前检查结束，因此 32 位最大值不会绕回最小值。循环变量是计数器的每轮副本，修改它不改变迭代进度；命名 range 值、浮点范围和通用迭代器尚未迁入这条路径。
IR 局部声明按词法作用域绑定，同名局部变量拥有独立声明和存储；显式 as 初始化仍绑定来源 Place，因此嵌套同名视图不会误连同名的新副本。退出作用域仅恢复名称可见性，不隐式转换或复制来源数据；越界引用明确诊断。
旧目标的列表元素必须具有相同 NBT 格式：bool 和 byte 可共用 ByteTag 编码，int 与 bool 则不能混合；这种物理编码兼容不会改变语言类型身份。混合编码不通过宿主强转修补，构造或写入时明确诊断。

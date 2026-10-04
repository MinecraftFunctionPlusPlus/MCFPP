# 26.3 浮点数编译后端

## 调研结论

依据 [Minecraft Java Edition 26.3 正式版更新说明](https://www.minecraft.net/en-us/article/minecraft-java-edition-26-3)
（核实日期：2026-10-01），26.3 的数值提供器已拆分为
`minecraft:context_int_provider` 和 `minecraft:context_float_provider`。
正式版语法与早期快照不同，不能使用旧的 `sum` / `product` 名称。

- `compute default float <provider> [scale]` 的命令返回值是乘以 scale 后向下取整的整数，不能用于保存一般浮点运算结果。
- `data modify <target> <path> set compute default float <provider>` 直接写入 NBT float，运算及中间值均为 IEEE 754 单精度。
- `minecraft:storage` 提供器读取指定 storage、path；其他 NBT 来源需先复制到 command storage。
- `add` / `mul` 使用 `inputs`；`sub` / `div` / `mod` 使用 `left`、`right`；`negate` 使用 `input`。
- `minecraft:float_value_check` 使用 `value`、`test`，且范围边界包含端点。26.3 的判据类型字段为 `type`。
- `from_int` 将整数提供器转为 float，`from_float` 向零截断为 32 位整数。
- 除零、NaN、Infinity 或整数转换越界可能使原生命令失败，不能假设这些计算写入有效结果。

## 实施方案

1. 通过项目目标版本选择后端，仅在已核实的 `26.3` 启用新后端。
2. 在 `MCFloat` 中保留现有语言类型及常量追踪，动态值改为存储在变量的 NBT 路径；旧版本继续使用 XiaoDouMathLib 的四个记分板分量。
3. 集中生成内联数值提供器，避免为每次运算增加注册表资源文件。单个动态二元运算写入独立临时路径，通常只需一条 `data modify … set compute` 命令；赋值使用 NBT 拷贝。
4. 纯常量表达式继续在编译期以 Kotlin Float 折叠。26.3 遇到已知非有限常量时产生编译错误，避免输出不可执行的提供器。
5. 将比较映射到浮点判据：`>=` / `<` 检查下界，`<=` / `>` 检查上界，`==` / `!=` 检查相等；严格比较通过反转判据实现，不使用整数缩放、差值或 epsilon。
6. 转换、复合赋值、一元负号均走同一后端。26.3 同时实现已注册但旧后端不支持的浮点 `%`。
7. 26.3 的表达式跳过旧后端的共享寄存器准备步骤；每次运行时浮点计算使用独立临时值，括号继续由独立访问器处理。
8. 26.3 的浮点编译不启用 `math.float` 包，也不生成专用于浮点数的 marker。通用数学标准库仍按既有模块配置导出。
9. 函数入参和 static 参数写回在开栈后引用调用者的栈帧；返回值保存在函数专用临时 storage 路径，调用后立即复制为独立临时值，保证多次调用和嵌套调用可组合。
10. 初始化新编译时清理模块状态，避免同一进程从旧版本切换至 26.3 时继续导出旧浮点包；运算符查找使用隐式转换后的操作数类型，支持 float 与 int 混合运算。

例如，动态变量 `a + 0.5` 的计算命令为：

```mcfunction
data modify storage mcfpp:system stack_frame[0].temp_0 set compute default float {type:"minecraft:add",inputs:[{type:"minecraft:storage",storage:"mcfpp:system",path:"stack_frame[0].a"},0.5]}
```

新后端的 NBT float 与旧后端的分量结构不兼容。重新编译目标为 26.3 的数据包后，应重新初始化持久化浮点数据；直接调用 XiaoDouMathLib 函数的用户代码也需要自行适配。

## 验证方案

- 编译回归：动态四则运算、取余、一元负号、复合赋值、所有比较及双向整数转换。
- 语义回归：纯常量折叠、常量/动态混合、左右嵌套括号、负数向零截断及原操作数保持。
- 函数回归：浮点入参、返回值，以及同一函数的连续调用和嵌套调用。
- 来源适配：storage、实体 NBT、带动态索引的路径。
- 版本隔离：26.2 / 1.21.8 继续调用旧库，26.3 不输出浮点库调用或浮点 marker。
- 导出回归：26.3 数据包格式为 121.0，浮点编译不会自动复制 `math.float` 包。
- 运行现有算术与版本测试，再运行完整测试集。生成命令及数值语义测试不能替代 Minecraft 服务端实测；验证记录应明确区分。

## 验证记录

2026-10-01：`./gradlew test` 通过，65 项测试、0 失败、0 错误，包括新增的 10 项原生浮点后端测试。
测试检查生成命令，并通过独立的简化命令执行器验证数值和操作数流向；覆盖连续返回值、嵌套调用、static 参数写回和动态索引刷新。
已验证 26.3 导出元数据为 121.0，且没有自动导出 `math.float`；同一进程反复切换新旧目标版本也通过。
尚未在 Minecraft 26.3 服务端或游戏内实测，也未进行实际运行耗时基准测试。

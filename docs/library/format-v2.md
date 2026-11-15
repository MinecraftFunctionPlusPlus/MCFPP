# MCFPP 库格式 v2

v2 保留 [v1](format-v1.md) 的容器、分节和校验结构，容器版本改为 2。读取器先检查实际容器版本，再选择 v1 或 v2 解码路径；输出统一为 v2。

## 上下文签名

函数声明 payload 的尾部增加有序 `contexts: DeclarationId[]`，引用 `CONTEXT_PARAMETER`（wire role 7）值声明。上下文参数具有独立的名称、类型与序号，独立于 readonly 和普通运行时参数；不会追加到 JVM 参数列表。捕获、绑定、重导出及声明投影都保留该列表。

`LibraryType.Function`、`KeyType.Function` 与语法层 `TypeSyntax.Function` 增加有序上下文类型列表。列表参与类型身份、规范签名与依赖遍历；列表中的类型按不变关系处理。空列表保持原来的编译器函数类型身份。

## 正文语法

`ContextScope`（tag `0x0310`）记录作用域、被提供的表达式列表及正文。调用实参在已有模式与表达式后增加可选参数名；该名称表示显式上下文实参。投影重新生成 `context(value) { ... }` 与 `call(ctx = value)`，并保留源码顺序。

旧 `Execute` tag `0x030a` 保留为迁移检测值；读取该正文报告迁移源码并重新导出的错误，不创建旧执行器对象。

## 旧版读取

v1 函数没有上下文参数列表，函数类型没有上下文列表，调用实参没有名称。读取器为它们填入空列表或空名称。旧 builtin `context.pos` 和 `context.entity` 分别映射到新完整帧 `context.execution` 与 `context.entity_execution`；新的窄能力类型不能套用旧完整帧的成员假设。

库声明默认源码语义为 `mcfpp.source/2`；兼容检查接受 `/1` 和 `/2`。运行时 ABI 保持 `mcfpp.runtime/1`。上下文来源、借用生命周期、执行者身份句柄、位置与旋转句柄均属于编译会话状态，不能写入库常量。

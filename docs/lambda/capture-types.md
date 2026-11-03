# 捕获能力与词法环境

普通同步 lambda 使用现有类型、词法身份和 `StorageAccess` 存储。源码绑定、创建时快照、环境复制、返回闭包与调用已接入编译路径。入门和运行方法见 [教程](tutorial.md)。

## 1. 身份与创建时点

- 自由变量由**词法声明身份**标识：捕获键为 `SymbolId`，不是变量拼写。`LambdaEnvironment` 按 `SymbolId` 查找，两个作用域中同名的 `x` 不会互相覆盖。
- 捕获在 lambda 表达式求值时创建一次。每个 lambda 环境拥有自己的捕获列表；本模型没有共享可变局部盒。
- 捕获的局部绑定只读。重新赋值或复合赋值不能修改外层局部；全局仍按原声明的位置和可写性访问。
- 全局/静态符号（`Var.isStatic`）不进入快照路径：`LambdaCaptureKind.GLOBAL_READ` 保留原声明适配器，每次调用读取全局位置。若需要冻结全局值，源码应先赋给局部变量再捕获。

## 2. 复制能力支持表

`LambdaCaptureRules.contractFor(type)` 返回的族与现有 `StorageAccess` 行为一致：

| 类型族 | 捕获结果 | 具体原因与边界 |
| --- | --- | --- |
| `int`、`bool`、`float`、`string`、`byte`、`short`、`long`、`double`、`range`、`null` | `SCALAR_SNAPSHOT` | 使用现有 `CompilerValue` 常量；创建时冻结，后续外层写入不改变快照。 |
| 枚举 `enum` | `SCALAR_SNAPSHOT` | 枚举值由已有 `EnumVar`/`CompilerValue.Record` 表示，属于闭合值。 |
| `pos2`、`pos3`、坐标维度、`type` | `COMPILER_VALUE_SNAPSHOT` | 没有独立运行时位置；只在存在完整不可变 `CompilerValue` 时捕获。 |
| `nbt`、NBT 数组、`list<E>`、`ImmutableList<E>`、`dict<E>`、`map<E>` | `RUNTIME_REPRESENTATION_COPY` | 通过 `StorageAccess.capture` 建立独立位置，复制当前运行时表示。闭合值优先保留为不可变快照。 |
| 数据模板/对象类型（含 `TypeDataTemplate`） | `RUNTIME_REPRESENTATION_COPY` | 复制已初始化实例字段的现有表示；静态字段、泛型只读参数和未初始化可空字段不进入实例快照。 |
| 实体选择器 `entity[...]` | `RUNTIME_REPRESENTATION_COPY` | 复制选择器程序并保留创建时捕获的操作数；实际实体集合仍在调用时解析，不是冻结的实体。 |
| 联合类型 | 由成员族合并 | 所有成员可复制时取共同族；任一成员没有独立复制契约则拒绝。 |
| `(T1, T2) -> R`、`() -> R` | `RUNTIME_REPRESENTATION_COPY` | `FunctionValue` 复制有限已知目标及拥有的环境载荷；返回和再次复制不借用已释放的创建者帧。 |
| `any` / `object`（实际类型未知） | 拒绝 | 擦除值没有具体实际类型证据。`as` 是重解释（reinterpretation），不是类型证明；应捕获声明类型已知的值，或通过能提供具体实际类型证据的途径获得值。闭合快照或具体运行时生产者存在时可按实际类型捕获。 |
| 其他未列出的类型（含自定义/未知类型） | 拒绝 | 没有显式复制契约；`hasRuntimeRepresentation == true` 本身不等于可安全复制。只有上表明确列出的族可以声明复制契约。 |
| `StaticMemberView`、`void`、内部私有值、未解析/未实例化泛型 | 拒绝 | 引用视图不拥有独立位置；这些内部类型没有值模型或独立生命周期。 |

### 不声称的复制语义

- `RUNTIME_REPRESENTATION_COPY` 只表示“现有运行时表示被复制到独立位置”。它不是任意用户可见深拷贝。
- 不承诺对象身份、引用字段、动态共享视图、跨 tick 持久化或 GC 语义；这些属于后续阶段。
- 不新增共享可变盒。两个闭包在同一构造函数/循环中创建时，各自持有自己的普通值环境。

## 3. 全局位置与暂缓引用

- 全局符号按原全局位置读取，不创建快照。这样闭包调用看到的是调用时的全局值，符合计划第 4 节。
- `ScoreRef` / `NBTRef` 引用捕获暂缓。模型以 `LambdaReferenceKind.SCORE` / `NBT` 显式返回 `REFERENCE_CAPTURE_DEFERRED`，不创建别名或共享盒。
- 外部/本地引用视图如果缺少独立所有权（例如没有运行时表示、没有完整快照的 `TypedView`），返回 `EXTERNAL_REFERENCE_WITHOUT_OWNERSHIP`。
- 普通类型化视图若可读取，仍按值复制；视图本身不会成为捕获环境的可变别名。
- 普通 lambda 调用 MNI `REF` 参数、嵌套回调的 `REF` forwarding 也暂未支持。用户已将这两类互操作移出本轮交付；既有宏直接 `REF` 参数能力不因此移除。

## 4. 诊断

`LambdaCaptureResult.Rejected.diagnostic` 携带稳定代码和具体消息；集成点决定何时调用 `report()` 发布到 `LogProcessor`。

| 代码 | 场景 |
| --- | --- |
| `ERROR_VALUE` | 捕获已经标记为错误的适配器。 |
| `UNBOUND_VALUE` | 值没有词法声明身份（临时值/表达式结果），不能作为自由变量捕获。 |
| `IDENTITY_MISMATCH` | 解析到的声明身份与值携带的 `SymbolId` 不一致。 |
| `UNINITIALIZED_SOURCE` | 局部捕获源尚未初始化。 |
| `NO_COPY_CONTRACT` | 类型没有独立复制契约，消息给出具体原因（擦除值、内部类型、无完整快照等）。 |
| `EXTERNAL_REFERENCE_WITHOUT_OWNERSHIP` | 引用视图没有独立所有权。 |
| `REFERENCE_CAPTURE_DEFERRED` | `ScoreRef`/`NBTRef` 引用捕获暂缓。 |
| `CAPTURE_WRITE_READONLY` | 试图写入捕获绑定。 |
| `COPY_FAILED` | 现有 `StorageAccess.capture` 无法建立独立副本。 |

捕获写入消息：

```text
Cannot modify captured value '<name>': a lambda capture is read-only;
return a new value or accumulate explicitly (for example with fold)
```

## 5. 复用接口与集成边界

- `top.mcfpp.core.lang.LambdaCapture`：一条只读捕获，保存词法 `SymbolId`、声明类型、复制族、创建时 `snapshot` 或独立 `owned` 副本；全局捕获保存 `global` 适配器。
- `top.mcfpp.core.lang.LambdaEnvironment`：不可变、按 `SymbolId` 索引的捕获集合；没有写入/替换 API。
- `top.mcfpp.analysis.lambda.LambdaCaptureRules`：能力表、创建规则、只读写入诊断和引用捕获拒绝。
- `LambdaCaptureScope` 为源码自由变量建立只读环境字段；`FunctionValue` 携带目标与环境，复用普通调用帧、参数与返回值复制。环境不保存隐式执行实体、位置或维度，Minecraft 上下文由调用点提供。

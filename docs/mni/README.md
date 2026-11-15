# MNI 公共 API 与教程（实验性）

MNI 让 **Kotlin 在编译期构造 MCFPP 运行时代码**：MCFPP 仍然表达运行时程序，宏作者只使用 `top.mcfpp.mni.api` 中的结构化代码模型，不接触 ANTLR 解析树、`Function.currFunction`、`Var` 或命令字符串拼接。

API、DSL、示例与工程模板以 Kotlin 为主要设计对象。Java 互操作保留调用 Kotlin JVM 方法的能力，Java 编写便利性不再作为新增 API 的设计约束。内置 provider 与相关注解实现位于 `src/main/kotlin`；已有的低层 builder 调用方式继续可用。

本目录是公开 API 的入口：

- [tutorial.md](tutorial.md)：Kotlin 宏与 DSL 教程，覆盖求值顺序、`VALUE`/`CONST`/`REF`/`INLINE`、执行上下文与声明生成。
- [api-versioning.md](api-versioning.md)：独立于编译器发行版的实验 API 版本与兼容策略。
- [implementation-contract.md](implementation-contract.md)、[source-projection-contract.md](source-projection-contract.md)、[kotlin-compatibility.md](kotlin-compatibility.md)：实现契约与 Kotlin/JVM 兼容范围。

当前 API 版本由 [`ApiVersion`](../../src/main/kotlin/top/mcfpp/mni/api/ApiVersion.kt) 给出：`0.2.0-experimental`。它**不是**编译器版本，也不表示 API 已经稳定或所有计划目标已完成。

## 1. 设计边界

支持：

- 保留既有 `@MNIFunction` 注册/调用路径，不把新宏语义强加到旧 ABI；兼容范围见 [kotlin-compatibility.md](kotlin-compatibility.md)。
- 新增 `@MNIMacro` 宏入口；宏是 `public static` JVM 方法，首参数为 `MacroContext`。
- 普通参数、可写引用、编译期常量和源码内联回调（`VALUE`、`REF`、`CONST`、`INLINE`）。
- 用 builder 构造表达式、局部量、赋值、分支、循环、调用和返回。
- `executeAs`/`at`/`rotated` 等结构化执行上下文，以及 score/NBT/位置/方块句柄。
- 普通运行期字符串 `+`：精确拼接两个 `string` 操作数，保留每个 UTF-16 code unit；通过平衡转义 helper 处理反斜杠/引号，不采用 SNBT 插值语义。`VALUE`/`REF` 的求值与地址规则不变。
- annotation 驱动的声明处理器：在全部声明完成后新增方法、内部函数和白名单元数据。

明确不支持（首期边界）：

- 不新增 MCFPP 的 `comptime`、`quote`、`splice` 语法，也没有任意 MCFPP 函数的编译期解释器。
- 不把普通函数自动变成无栈调用，不提供任意运行时闭包、逃逸回调或延迟回调。
- 宏作者不能访问解析器节点、当前 `Function`、可变 `Var` 或原始命令字符串；公共树中不保存宿主 lambda。
- 首期只有具名 namespace 宏，没有成员宏、宏泛型参数或新的运行时函数分派。
- 本文不把 DSB 功能验收等同于 Minecraft 实机或性能结论；性能与实机验收已按用户要求取消，见第 7 节。

## 2. 注册与调用链

宏与 MNI 函数一样，在 MCFPP 中显式声明并绑定 JVM 方法。provider JAR 由工程的 `jars` 配置加入，CLI 生成的 MNI 工程会自动加载构建产物：

```mcfpp
func damage(health: ScoreRef, amount: int): void = example.mnidamage.KotlinDamageMacro.damage
```

`@MNIMacro(normalParams = ["ScoreRef", "int"], returnType = "void")` 描述实现签名；公开名称、参数名和命名空间来自源码声明。`injectedBy` 不会自动暴露宏。一个命名空间内不能声明两个同名、同 MCFPP 参数类型的宏；同一个 JVM 方法可以绑定到不同源码名称。

从 MCFPP 源码到生成代码的固定顺序：

```text
解析调用
→ 纯源绑定（只保留实参结构、类型、位置和常量证明）
→ 按参数模式捕获/临时化实参序言
→ 执行 Java/Kotlin 宏入口（只构造不可变代码树）
→ 结构/类型/作用域/上下文验证
→ 递归展开嵌套宏与 INLINE 回调
→ 最终验证（不得残留 MacroCall/InlineCall）
→ 降低为既有命令/IR 计划
→ 一次性提交；失败则整个展开回滚
```

宏执行期间不会执行普通函数或旧 MNI；生成树中对旧 MNI 的调用在 lowering 阶段才进入旧协议。

### `@MNIMacro` JVM ABI

Kotlin 宏入口见 [KotlinDamageMacro.kt](../../examples/mni/damage/provider/kotlin/example/mnidamage/KotlinDamageMacro.kt)。公开地址参数为 `Value<ScoreRefT>` / `Value<NBTRefT<T>>`，对应源码中的 `ScoreRef` / `NBTRef<T>`。宏体使用 `context.quote.block { ... }`，通过 DSL 构造不可变代码树。

- 入口必须是 `public static`，provider 类及其外部类必须是 `public`。
- 首参数必须精确为 `MacroContext`。
- 注解的 `normalParams` 按顺序列出类型；模式直接从 JVM wrapper 推导：`Value<T>`、`Ref<T>`、`Const<T>`、`InlineFn<S>`。
- `void` 宏返回 `Block`；非 `void` 宏返回 `Expr<T>`。
- `func` 声明提供源码名称，显式绑定时不需要注解的 `identifier`。
- `normalParams` 中每项是现有 MCFPP 类型的字符串；`INLINE` 使用 `(int, ref int)->bool` 这类函数签名描述。
- 跳过 bridge/synthetic 方法；`suspend`、默认参数 mask、未映射的扩展 receiver/value class 不能作为入口。
- 同名候选只按 MCFPP 名称与参数类型选择；参数模式、返回类型和 JVM wrapper 不参与重载区分。同名同参数签名的宏/普通函数冲突会在声明准备阶段诊断。

Kotlin 入口可以使用顶层函数、`object` 的 `@JvmStatic` 方法或 companion 的 `@JvmStatic` 方法。顶层函数编译进 file facade，其 JVM 方法本身就是 `public static`，**不需要** `@JvmStatic`；`object`/companion 的成员在 Kotlin 层是实例成员，**必须**加 `@JvmStatic` 才生成注册器可发现的静态 JVM 桥。顶层 facade 名可用 `@file:JvmName(...)` 固定。

## 3. 参数模式

| 模式 | 作者看到 | 语义 | 典型用途 |
|---|---|---|---|
| `VALUE` | `Value<T>` | 调用者侧按源码顺序求值**一次**，宏只读取稳定结果 | 普通实参；即使宏未使用，实参副作用仍执行 |
| `REF` | `Ref<T>` | 直接绑定调用点的原可写位置，不复制、不回写 | 原地扣血、NBT 字段/列表元素 |
| `CONST` | `Const<T>` | 只接受可证明的闭合常量；宏在生成期读取 | 展开次数、固定步长、编译期开关 |
| `INLINE` | `InlineFn<S>` | 静态源码体延迟插入；局部捕获为创建时只读值快照，全局保持实时访问 | `repeat`/`raycast` 等可替换行为 |

关键规则：

- `VALUE` 的 `expr()` 返回稳定临时读取；宏作者看到的是已绑定的逻辑值，原实参不会在宏体里重复执行。序言属于最终树，在运行期按原位置执行。
- `CONST` 只做纯常量证明，不执行旧 MNI 或任意 MCFPP 函数。动态值传给 `CONST` 是模式/常量证明错误，不会回退到同名 `VALUE`。
- 源码地址使用 `ScoreRef` / `NBTRef<T>` 普通 VALUE 参数，并从显式 Minecraft 地址创建。`ref expression` 已移除并报告迁移错误。内部 `REF` 位置句柄继续检查可写和精确类型，并保留别名与原位置读写语义。
- `INLINE` 只接受静态 lambda 字面体或普通源码函数引用，精确拒绝动态函数值、native/macro 目标；每次插入都有新的参数、局部量、控制区域和返回区域。
- lambda 的自由局部绑定在创建时按值快照且只读，拒绝赋值、复合赋值和引用写入；类型复制沿用 `LambdaCaptureRules` / `StorageAccess`，不承诺通用深复制。全局按声明身份识别，使用时读取当前值并允许原权限内写入。
- 函数类型为 `(int,int)->int`、`()->void`；lambda 为 `{ i -> ... }`，可写 `{ i: int -> ... }`，单参数目标可隐式使用 `it`，零参数用 `{ ... }`。具名 `func` 声明保持原形式。旧箭头写法只作为迁移反例并诊断拒绝。
- 回调尾表达式返回值，尾随分号丢弃值，`void` 目标丢弃尾值；`return` 只结束当前回调，`break`/`continue` 不得跨回调边界。复用现有 lambda/存储/回收体系。

详细示例见 [tutorial.md](tutorial.md#5-参数模式与公开地址)。

## 4. API 地图

所有公开类型位于 `top.mcfpp.mni.api`，注解位于 `top.mcfpp.annotations`。

| 类型/接口 | 作用 |
|---|---|
| `MacroContext` | `quote()`、`types()`、`diagnostics()`、`helpers()`、`resources()`、`callSite()` |
| `Quote` / `CodeBuilder` | `block`/`expression`、局部量、赋值、分支、循环、调用、字面量、算术和比较 |
| `KotlinQuote` / `CodeScope` | 同一 builder 的 Kotlin 接收者 DSL（`Quote.kotlin()`、`MacroContext.quote`） |
| `Code` / `Expr` / `Stmt` / `Block` | 不可变代码视图；`Block.statements()` 只读 |
| `Value` / `Ref` / `Const` / `InlineFn` / `Local` | 宏输入、可写位置和局部量句柄 |
| `TypeRef` / `FieldRef` / `FunctionRef` / `ParameterView` / `FunctionSignature` / `InlineSignature` | 规范类型、字段、函数和签名描述 |
| `ConstantValue` | 整数、浮点位、bool、string、NBT、序列、record、type、null 的闭合 ADT |
| `CallArgument` / `InlineArgument` | 调用/内联实参的模式化包装；Java 静态工厂 |
| `MinecraftBuilder` | `executeAs`/`at`/`rotated`、score、position、rotation、block |
| `EntityQuery`/`ScoreHolder`/`BlockPredicate` | 安全、闭合的选区/分数持有者/方块谓词描述 |
| `TemplateProcessor` / `TemplateView` / `DeclarationBuilder` | annotation 完成后的声明读取与原子新增 |
| `ApiVersion` | 独立实验 API 版本与兼容性检查 |

`TypeRef`、`FieldRef`、`FunctionRef`、`Block` 等是只读视图；具体 `Immutable*` 实现是编译器内部类型，provider **不得**引用、强转或自行实现这些 sealed 接口。

## 5. 示例

所有示例的宏入口和 processor 主体都只使用公开代码 API。宏由 MCFPP 显式绑定；processor 沿用 `injectedBy`/`importToMNI` 钩子，旧字段注解适配器仍实现 `FieldAnnotation`。示例不出现 ANTLR 或 `Function.currFunction`。

| 示例 | 演示 | lambda 迁移前历史状态 |
|---|---|---|
| [damage](../../examples/mni/damage/README.md) | `REF int` 原位置、`VALUE int` 一次求值、Java/Kotlin 同签名、无宏运行时栈帧 | Java/Kotlin CLI 0 错误/0 警告；DSB 功能 PASS |
| [repeat](../../examples/mni/repeat/README.md) | `VALUE` 计数、运行时 `whileLoop`、源码 `INLINE` 捕获和局部 `return` | Java/Kotlin CLI 0 错误/0 警告；DSB 功能 PASS（新执行） |
| [raycast](../../examples/mni/raycast/README.md) | `CONST` 展开、固定 0.5 步长方块射线、停止/继续回调、采样点而非不同方块数 | Java/Kotlin CLI 0 错误/0 警告；DSB 功能 PASS |
| [nbt-reference](../../examples/mni/nbt-reference/README.md) | `REF` 指向确定 NBT 字段/列表元素，复用 damage provider | Java/Kotlin CLI 0 错误/0 警告；DSB 功能 PASS |
| [score-mapping](../../examples/mni/score-mapping/README.md) | annotation 全声明屏障、生成 `load`/`save`、固定 holder、bool 0/1 与缺失分数读零 | Java/Kotlin CLI 0 错误/0 警告；DSB 功能 PASS |

每个示例目录内的 README 有自己的构建/运行命令和更完整的结果表。示例按自身目录作为工作目录运行，`mcfpp.json` 中的相对路径以当前工作目录解析。

当前示例通过显式返回值累积：`repeat(times: int, initial: int, body: INLINE (int,int)->int): int` 每次传入 `(accumulator,index)`，返回下一累加器，预期 `total=10`、`guarded=8`、`tail=9`、`nested=22`；`repeat(times: int, body: INLINE (int)->void): void` 保留。`raycast(samples: CONST int, initial: int, step: INLINE (int,int)->int, keepGoing: INLINE (int)->bool): int` 在实际 stone 命中时先更新累加器，再由 `keepGoing(index)` 决定后续采样；原 bool 回调重载保留。命中数/最后下标由独立遍历返回，固定场景预期继续为 `4`/`3`、停止为 `1`/`0`、停止后的调用者尾部为 `2`。这些是迁移后预期值，本次文档编辑未重新执行。

## 6. Java/Kotlin 对照

同一个 `damage` 宏的入口：

```java
public static Block damage(MacroContext context, Ref<IntT> health, Value<IntT> amount) {
    return context.quote().block(builder ->
        builder.ifElse(
            builder.lt(health.load(), amount.expr()),
            thenBranch -> thenBranch.assign(health, thenBranch.intLiteral(0)),
            elseBranch -> elseBranch.assign(health,
                elseBranch.sub(health.load(), amount.expr()))));
}
```

```kotlin
@JvmStatic
@MNIMacro(
    identifier = "damage",
    normalParams = [
        "int",
        "int"
    ],
    returnType = "void"
)
fun damage(context: MacroContext, health: Ref<IntT>, amount: Value<IntT>): Block =
    context.quote().kotlin().block {
        ifElse(
            lt(health.load(), amount.expr()),
            { assign(health, intLiteral(0)) },
            { assign(health, sub(health.load(), amount.expr())) }
        )
    }
```

两者产生同一棵代码树和同一展开语义；上面的 Kotlin 片段用 `object`，所以需要 `@JvmStatic`。若改成顶层函数，入口本身已是 file facade 的静态方法，不需要该注解。

## 7. 验证边界

MNI 的检查分别覆盖编译器生成代码、命令解释器执行和 DSB 数据包执行。测试类位于 `src/test/kotlin/top/mcfpp/test/Mni*Test.kt`；DSB 脚本与参数说明见 [示例验证说明](../../examples/mni/benchmark/README.md)。按实际修改选择检查，执行结果保存在对应构建目录。

DSB 不是 Minecraft 服务端：它覆盖数据包命令语义、函数/回调控制流、固定上下文和调用帧，不证明客户端、物理、AI、MSPT 或游戏内耗时。**性能与真实游戏验收已按用户要求取消，不是任何示例或交付的门槛**，也不存在“等待实机/性能”的未完成项。

## 8. 当前边界

- **API 稳定性**：仍为 `0.2.0-experimental`；兼容规则见 [api-versioning.md](api-versioning.md)。
- **验证范围**：检查范围以本次执行的测试和示例为准，历史报告不能代表后续修改的验证结果。

## 9. 相关文档

- [tutorial.md](tutorial.md)：完整 Java/Kotlin 对照、生成期/运行时控制流、上下文、声明生成、配置与 dump。
- [api-versioning.md](api-versioning.md)：`ApiVersion`、版本升级规则和旧 MNI 边界。
- [implementation-contract.md](implementation-contract.md)：公共 API、会话、验证、降低和声明协议的实施契约。
- [source-projection-contract.md](source-projection-contract.md)：纯源绑定、`REF` 地址捕获和 detached plan。
- [kotlin-compatibility.md](kotlin-compatibility.md)：旧 `@MNIFunction` 的 Kotlin/JVM ABI 与构建示例。

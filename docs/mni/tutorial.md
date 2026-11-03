# MNI Java/Kotlin 对照教程

本教程面向 MNI 库作者，代码构造只使用 `top.mcfpp.mni.api` 和 `top.mcfpp.annotations` 的公开 API；processor 注册沿用现有 `injectedBy`/`importToMNI` 钩子（会接触 `Project`/`GlobalScope`，但不接触解析器或当前 `Function`）。所有**新宏与声明 processor 主体**示例都不使用 ANTLR、`Function.currFunction`、可变 `Var`、`Immutable*` 内部实现或命令字符串拼接；第 9 节的旧 `@MNIFunction` 片段只用于说明历史兼容 ABI。

阅读前请先看 [README.md](README.md) 的验证边界：本次文档修订复用协调者已交付的 CLI/测试证据，**没有在写文档时重新运行** Gradle、`javac`、`kotlinc`、测试或 CLI。执行结果的证据范围以第 11 节列出的文件为准；未被保存运行结果覆盖的修复不当作已通过。

版本：`ApiVersion.VERSION`（当前为 `0.1.0-experimental`），见 [api-versioning.md](api-versioning.md)。

2026-10-27 公开引用迁移：源码使用 `ScoreRef` / `NBTRef<T>` 普通值和显式 Minecraft 地址，`ref expression` 始终拒绝。文中的 `Ref<T>` builder 示例描述编译器内部位置能力；旧 CLI/DSB 记录不覆盖新引用 provider 的迁移结果。

## 1. 先理解两条时间线

MNI 宏作者同时面对两条时间线：

1. **生成期（host / Java/Kotlin）**：宏入口被调用，Java/Kotlin 的 `for`、`if`、方法调用、异常和集合操作都在这里执行。它们决定“构造多少代码、构造什么代码”。
2. **运行期（MCFPP / Minecraft）**：生成出来的代码稍后在数据包中执行。只有通过 `CodeBuilder` 构造的节点（赋值、`ifThen`、`whileLoop`、`inlineVoid` 等）才进入运行期。

这两条线不能混用：

- 宏入口里的 Java `if` 不是运行期分支；要生成运行期分支必须用 `builder.ifThen`。
- 宏入口里的 Java `for` 不是运行期循环；要生成运行期循环必须用 `builder.whileLoop`。
- `Quote` 的每个回调在构造时**立即消费一次**，builder 在回调返回后关闭；不能保存 builder 或 lambda 到以后再用。
- 生成期抛出的异常会成为宏展开失败，带调用点/展开链诊断；它不会变成运行期异常。
- 宏入口不能执行普通 MCFPP 函数，也不能“先发命令再恢复成代码”。

编译器内部位置代码的生成期分支与运行期分支（`count` 是生成期整数，`health` 是编译器提供的 `Ref<IntT>`；普通源码不能对局部变量取地址）：

```java
// 生成期控制流：count 来自 CONST，Java for 决定展开多少个赋值节点。
return context.quote().block(b -> {
    for (int i = 0; i < count; i++) {
        b.assign(health, b.sub(health.load(), b.intLiteral(1)));
    }
});
```

```java
// 运行期控制流：只生成一个 If 节点，运行时才决定走哪个分支。
return context.quote().block(b ->
    b.ifThen(b.lt(health.load(), b.intLiteral(0)),
        branch -> branch.assign(health, branch.intLiteral(0))));
```

`whileLoop` 的 condition 回调也只在生成期执行**一次**来构造条件表达式；该表达式在运行期每次迭代求值：

```java
builder.whileLoop(
    condition -> condition.lt(iteration.load(), limit.load()),
    loop -> {
        loop.inlineVoid(body, List.of(InlineArgument.value(iteration.load())));
        loop.assign(iteration, loop.add(iteration.load(), loop.intLiteral(1)));
    });
```

非 `void` 宏用 `Quote.expression(type, body)` 构造表达式区域，所有可达正常出口都必须 `yield`：

```java
return context.quote().expression(context.types().boolType(), b ->
    b.yield(b.boolLiteral(true)));
```

表达式区域不能是 `void`；`yield` 只在该区域合法，不能和回调 `return` 混用。普通语句用 `evaluate(expr)` 追加，忽略一个非 `void` 表达式不会隐式执行它。

## 2. 最小 provider：`damage`

`damage(health, amount)` 接收普通 `VALUE ScoreRef` 与 `VALUE int`。`health` 保存显式 Minecraft 地址；provider 将它传给普通源函数 `applyDamage`，该函数通过同步 `tryRead` 和 `set` 完成扣血。完整 Java/Kotlin provider 见 [damage 示例](../../examples/mni/damage/README.md)。以下内部 `Ref<IntT>` builder 代码保留为编译器位置能力示意，不能再用 `ref local` 从源码调用。

### Java 内部位置 builder 示意

```java
package example.mnidamage;

import top.mcfpp.annotations.MNIMacro;
import top.mcfpp.annotations.MacroParam;
import top.mcfpp.mni.api.Block;
import top.mcfpp.mni.api.IntT;
import top.mcfpp.mni.api.MacroContext;
import top.mcfpp.mni.api.ParamMode;
import top.mcfpp.mni.api.Ref;
import top.mcfpp.mni.api.Value;

public final class JavaDamageMacro {
    private JavaDamageMacro() {
    }

    @MNIMacro(
        identifier = "damage",
        params = {
            @MacroParam(name = "health", type = "int", mode = ParamMode.REF),
            @MacroParam(name = "amount", type = "int", mode = ParamMode.VALUE)
        },
        returnType = "void"
    )
    public static Block damage(MacroContext context, Ref<IntT> health, Value<IntT> amount) {
        return context.quote().block(builder ->
            builder.ifElse(
                builder.lt(health.load(), amount.expr()),
                thenBranch -> thenBranch.assign(health, thenBranch.intLiteral(0)),
                elseBranch -> elseBranch.assign(health,
                    elseBranch.sub(health.load(), amount.expr()))));
    }
}
```

### Kotlin 内部位置 builder 示意

```kotlin
package example.mnidamage

import top.mcfpp.annotations.MNIMacro
import top.mcfpp.annotations.MacroParam
import top.mcfpp.mni.api.Block
import top.mcfpp.mni.api.IntT
import top.mcfpp.mni.api.MacroContext
import top.mcfpp.mni.api.ParamMode
import top.mcfpp.mni.api.Ref
import top.mcfpp.mni.api.Value
import top.mcfpp.mni.api.kotlin

object KotlinDamageMacro {
    @JvmStatic
    @MNIMacro(
        identifier = "damage",
        params = [
            MacroParam(name = "health", type = "int", mode = ParamMode.REF),
            MacroParam(name = "amount", type = "int", mode = ParamMode.VALUE)
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
}
```

Java 使用 `java.util.function.Consumer`，Kotlin 可以用同一 `Consumer`，也可以用 `Quote.kotlin()` 的接收者 DSL。两者产生同一代码树；Kotlin 的 DSL 只包装公共 builder，不另有一套语义。

这里使用 `object`，所以 `@JvmStatic` 是必需的：注册器反射查找的是 `public static` JVM 入口，而 `object` 成员在 Kotlin 层是实例成员，需要静态桥。**顶层函数不需要 `@JvmStatic`**：它本来就会编译成 file facade 的 `public static` 方法。顶层函数可以用 `@file:JvmName` 固定 facade 名，例如：

```kotlin
@file:JvmName("KotlinTopLevelProvider")
package example.provider
```

如果改用顶层 `fun damage(...)`，去掉 `object` 包装和 `@JvmStatic` 即可，入口仍是静态 JVM 方法。

### MCFPP 调用侧

```mcfpp
func applyDamage(health: ScoreRef, amount: int) {
    health.tryRead { current ->
        if (current < amount) { health.set(0); }
        else { health.set(current - amount); }
    }
}
func main() {
    /scoreboard players set #health hp 100
    damage(scoreRef("#health", "hp"), 30)
}
```

`ref expression` 已移除并报告定位明确的迁移错误。`ScoreRef` 与 `NBTRef<T>` 通过普通 VALUE 参数传递描述本身；`ParamMode.REF`、`Ref<T>` 和 `CallArgument.ref(...)` 保留给编译器提供的位置句柄。非 `void` 宏返回 `Expr<T>`，`void` 宏返回 `Block`。

## 3. 参数求值规则

绑定器在宏入口之前生成一段**参数序言**，它属于最终代码树，不是宏作者可以跳过的“预处理”。序言按源码求值顺序（成员 receiver 在最前，然后 readonly、普通实参）为每个实参建立稳定事实。

### 3.1 `VALUE`：从左到右，一次求值，未使用也执行

对每个 `VALUE` 实参，绑定器生成：

```text
LocalDecl(temp, <按原位置完成类型转换的实参表达式>)
```

宏拿到的 `Value.expr()` 是这个临时量的读取，不是原表达式。因此：

- 每个 `VALUE` 实参只执行一次；宏体多次 `expr()` 不重复副作用。
- 即使宏完全不读取某个 `Value`，该 `LocalDecl` 和它的副作用仍保留在序言里。
- 多个实参按源码从左到右的顺序执行；`effect(5) + effect(7)` 的观察顺序是 `5, 7`。
- 实参在绑定位置的类型转换也只发生一次。
- 同一位置作为 `REF` 时不能被自动当作 `VALUE` 复制；需要值必须显式 `ref.load()`。

“一次求值”是运行期语义：宏作者拿到的是已绑定的逻辑值，原实参不会在宏体里重新执行；序言节点属于最终树，在运行期按原位置执行。

公开地址值的别名示例先用 `alias.tryRead { current -> damage(alias, current); }` 得到普通值快照，再把地址与数值作为 VALUE 参数传入。

### 3.2 `CONST`：生成期读取闭合常量

`CONST` 只接受无需执行代码就能证明的完整不可变值：

- 字面量；
- 可证明的只读常量/前序形参；
- 纯常量运算和纯常量转换；
- 不包含 `CALL`、`WRITE`、`UNKNOWN` 效果的表达式。

不能为了证明常量去执行旧 MNI、普通函数或任意 MCFPP 代码；动态值传给 `CONST` 会得到模式/常量诊断，而不会回退到同名 `VALUE` 宏。`Const.intValue()`/`boolValue()` 在类型不符时报带位置异常，不做静默强转。

`CONST` 的价值是让宏在生成期做特化。固定步长射线用 `samples.intValue()` 读取展开次数：

```kotlin
val count = samples.intValue()
require(count >= 0) { "Raycast sample count must not be negative" }
```

然后 Java/Kotlin 的 `for` 在生成期展开 `count` 份采样代码。`CONST` 的源码仍会先经过类型和模式检查，不会被当作运行时变量。

### 3.3 内部 `REF`：原位置、别名与地址捕获

`REF` 绑定的是调用点原可写位置，不是值：

- 位置必须由编译器 API 提供；源码 `ref expression` 始终拒绝。内部位置拒绝临时值、readonly、算术结果与普通 getter/setter 属性。
- 类型必须精确匹配，不能做隐式转换；字段/索引必须可写。
- 不复制到参数区，不在宏结束时回写；多次 `load()`/`assign()` 都落在同一地址。

地址捕获顺序（对应 `SourceCodeProjector.capture`）：

```text
先固定根存储/frame/receiver/context
→ 遇到字段：把 canonical FieldRef 追加到路径
→ 遇到索引：先固定“索引之前的整个前缀”，求值一次索引并保存为局部量，
   再用 CapturedIndex 组合，随后再次固定该次选择
→ 重复以上步骤直到最后一个地址
```

这意味着：

- 索引表达式只执行一次，且在它自己的参数位置执行；后续 `amount` 参数改变索引来源也不会改变已捕获的地址。
- 后一个索引不会重新读取可能已经被前一个前缀修改的根/嵌套集合。
- 别名合法：两个 `ref` 可以指向同一物理地址；写入顺序彼此可见，不做“地址 ID 不同就不相交”的优化。
- `fieldRef(receiver, field)` 从已捕获地址派生相对路径，不重新求值根。
- REF 的执行上下文在创建时固定；换到另一个 `executeAs`/实体后不能按新 `@s` 重解释。

`damage` 的生成结果没有参数区 REF 复制，也没有运行时 `damage` 函数；`if/else` 仍会 lowering 成普通控制流 helper，这是源码控制流，不是宏调用栈。

### 3.4 `INLINE`：延迟绑定的源码回调

本轮同步 lambda 支持值捕获和局部返回。按 2026-10-22 用户决定，普通 lambda 调用 MNI `REF` 参数、嵌套回调 `REF` forwarding 暂缓；不要把宏直接 `REF` 参数的既有能力理解为这两类互操作已支持。引用捕获也暂缓。当前普通 lambda 用法见 [同步 lambda 教程](../lambda/tutorial.md)。

`INLINE` 参数不是宿主 lambda，也不是运行时函数值，而是**源码回调体**。它只在真正插入时绑定，并带上该次插入的实际 `CONST` 事实：

- 直接 lambda 或静态可解析的普通源码函数可以作回调；动态函数值、native/macro 目标不能提供静态体。
- 自由局部绑定在 lambda 创建时按值快照，捕获绑定只读；赋值、复合赋值及通过引用写入均拒绝。类型复制沿用 `LambdaCaptureRules` / `StorageAccess` 的既有契约，不承诺通用深复制。
- 全局绑定按声明身份识别，在使用时读取当前值；符合原权限的全局写入仍合法，不能靠名字把同名局部误当全局。
- 每次插入为回调参数、局部量、控制区域、返回区域和捕获地址分配新身份；同一个回调插入两次不共享局部量。
- 回调里的 `return` 只退出**本次插入**的返回区域，不退出调用者函数，也不退出外层循环。
- 嵌套 INLINE 会随外层插入一起克隆/重绑定，但内层体在自身插入前保持延迟。
- 活动链按静态 callback 身份检测递归；直接/相互递归会报 `Recursive inline callback identity ...`。克隆局部身份不能绕过。
- 回调不能存为运行时值、返回为闭包或交给资源/helper 延迟调用；它只在插入点展开。
- 不生成回调编号，不做运行时按编号分派。

回调结果类型由 `InlineFn<S>` 的实际签名决定：`inlineVoid` 用于 `void`，`inline` 用于非 `void`。实参用 `InlineArgument.value/ref/constant` 显式标注模式。回调不能接受另一个 INLINE 参数。

源码函数类型写作 `(int,int)->int`、`()->void`，没有 `func` 前缀；普通具名声明仍用 `func name(...)`。lambda 写作 `{ i -> ... }`，可标注 `{ i: int -> ... }`；单参数目标可省略参数列表并使用 `it`，零参数目标用 `{ ... }`。迁移旧例 `(i) => { ... }` 已拒绝并给出迁移诊断，不保留旧可变捕获兼容行为。

非 `void` 回调的尾表达式产生返回值；尾随分号丢弃该值，`void` 目标也丢弃尾值。显式 `return` 只结束当前回调，`break`/`continue` 不能越过回调边界。INLINE 只接受静态字面体或普通源码函数引用；动态函数值即使具有匹配类型也不能提供静态体。这些规则复用现有 lambda、存储和回收体系。

### 3.5 字符串 `+`：运行期精确拼接，不是生成期插值

普通 `string + string` 是**运行期**表达式，要求两端都是 `string`，不做数字到字符串的隐式转换；闭合常量可以在生成期折叠，但非常量操作数走运行期拼接。它和 Java/Kotlin 宏入口里的 `String` 拼接是两条时间线：后者的结果只用于构造字面量或代码节点，不会自动变成运行期命令。

当前实现使用编译器拥有的平衡转义 helper 构造一个可精确还原的拼接树：

- 两个操作数按源码顺序各求值一次，先左后右复制到共享 workspace 暂存，目标位置只在整棵树归约完成后写入。
- 结果是逐 UTF-16 code unit 的精确拼接，包括原样控制字符和代理项半值；只在需要跨 SNBT/macro 层时对反斜杠和引号做成对平衡转义，不把值解释成 SNBT 片段或宏插值。
- 不生成用户可见的运行时字符串函数，也不把字符串拼接交给旧 MNI；拼接 helper 是编译器拥有的普通数据包函数，并随事务回滚。
- `VALUE`/`REF` 规则不因字符串而改变：`VALUE string` 仍是调用者侧求值一次后绑定稳定临时量；`REF string` 仍是原可写地址，拼接本身不会复制地址或回写，除非宏显式 `assign` 到该 `REF`。

字符串后端的证据已发布：受影响 JUnit 测试和编译器导出字符串 DSB 均实际通过；结果与复用边界见 [regression-results.md](regression-results.md)。本节只描述当前源码语义，不把 DSB 当作 Minecraft 实机结论。

## 4. 运行时 `VALUE` 循环 vs `CONST` 展开射线

`repeat` 与 `raycast` 是同一套 API 的两种典型形态。

### 4.1 `repeat`：运行时循环，`times` 是 `VALUE`

`repeat(5, { i -> ... })` 的 `times` 在调用者侧求值一次；provider 把它绑定到自己的 `limit` 局部量，用 `whileLoop` 生成运行期循环，用 `i` 局部量计数。循环次数在运行期由 `limit` 决定。

Java（完整方法体）：

```java
@MNIMacro(
    identifier = "repeat",
    params = {
        @MacroParam(name = "times", type = "int", mode = ParamMode.VALUE),
        @MacroParam(name = "body", type = "(int)->void", mode = ParamMode.INLINE)
    },
    returnType = "void"
)
public static Block repeat(MacroContext context, Value<IntT> times, InlineFn<VoidT> body) {
    return context.quote().block(builder -> {
        Local<IntT> iteration = builder.local("i", context.types().intType(), builder.intLiteral(0));
        Local<IntT> limit = builder.local("limit", context.types().intType(), times.expr());
        builder.whileLoop(
            condition -> condition.lt(iteration.load(), limit.load()),
            loop -> {
                loop.inlineVoid(body, List.of(InlineArgument.value(iteration.load())));
                loop.assign(iteration, loop.add(iteration.load(), loop.intLiteral(1)));
            });
    });
}
```

Kotlin（完整方法体）：

```kotlin
@JvmStatic
@MNIMacro(
    identifier = "repeat",
    params = [
        MacroParam(name = "times", type = "int", mode = ParamMode.VALUE),
        MacroParam(name = "body", type = "(int)->void", mode = ParamMode.INLINE)
    ],
    returnType = "void"
)
fun repeat(context: MacroContext, times: Value<IntT>, body: InlineFn<VoidT>): Block =
    context.quote().kotlin().block {
        val iteration = local("i", context.types().intType(), intLiteral(0))
        val limit = local("limit", context.types().intType(), times.expr())
        whileLoop(
            { lt(iteration.load(), limit.load()) },
            {
                inlineVoid(body, listOf(InlineArgument.value(iteration.load())))
                assign(iteration, add(iteration.load(), intLiteral(1)))
            }
        )
    }
```

要点：

- `times.expr()` 是稳定 `VALUE` 临时量；把它再绑定到 `limit` 后，循环条件每次读同一逻辑值，不重复执行源实参。
- 计数自增在回调**外面**；回调 `return` 只结束该次插入，不跳过自增，也不终止循环。
- 回调每次插入有新的 `i`/回调局部身份；嵌套 `repeat` 不会串用计数。
- 生成树中没有运行时 `repeat` 函数、参数区或回调编号分派。

需要累积结果时使用显式返回的重载：

```text
repeat(times: int, body: INLINE (int)->void): void
repeat(times: int, initial: int, body: INLINE (int,int)->int): int
```

第二个重载把 `(accumulator, index)` 传给回调，每次返回值成为下一次累加器；初始值和次数都是 `VALUE`。provider 用 `loop.assign(accumulator, loop.inline(body, ...))` 保存结果，循环结束后 `yield(accumulator.load())`。Java/Kotlin 完整实现见示例 provider。

```mcfpp
external total: int = repeat(5, 0, { accumulator, i -> accumulator + i }); // 10
external guarded: int = repeat(5, 0, { accumulator, i ->
    if (i == 2) { return accumulator; }
    accumulator + i
}); // 8：局部 return 后，后续迭代仍执行
external tail: int = guarded + 1; // 9
external nested: int = repeat(2, 0, { accumulator, i ->
    var inner: int = repeat(2, 0, { subtotal, j -> subtotal + i * 10 + j });
    accumulator + inner
}); // 22：内层创建时快照外层 i
```

这些数值说明迁移后示例的预期行为；本次文档编辑没有执行它们。

### 4.2 `raycast`：`CONST` 展开，固定 0.5 步长

`raycast(samples: CONST int, body: (int)->bool)` 在生成期读取 `samples` 并展开每个采样点：

```text
for index in 0 until count:
    if (running) {                      // 运行期：停止后跳过后续采样
        at(currentPosition + forward * (index * 0.5)) {
            if (blockAt(currentPosition) matches stone) {
                running = body(index)    // 源码 INLINE；true 继续，false 停止
            }
        }
    }
```

Java 关键片段（完整文件见 [JavaRaycastMacro.java](../../examples/mni/raycast/provider/java/example/mniraycast/JavaRaycastMacro.java)）：

```java
public static final double SAMPLE_STEP = 0.5;

public static Block raycast(MacroContext context, Const<IntT> samples, InlineFn<BoolT> body) {
    int count = samples.intValue();
    if (count < 0) throw new IllegalArgumentException("Raycast sample count must not be negative");
    return context.quote().block(builder -> {
        Local<BoolT> running = builder.local("running", context.types().boolType(),
            builder.boolLiteral(true));
        for (int index = 0; index < count; index++) {
            final int sampleIndex = index;
            builder.ifThen(running.load(), active -> {
                MinecraftBuilder mc = active.minecraft();
                PositionHandle point = mc.localOffset(mc.currentPosition(), mc.currentRotation(),
                    0.0, 0.0, sampleIndex * SAMPLE_STEP);
                mc.at(point, sampled -> {
                    MinecraftBuilder sample = sampled.minecraft();
                    Expr<BoolT> hit = sample.blockMatches(
                        sample.blockAt(sample.currentPosition()),
                        BlockPredicate.block("minecraft:stone"));
                    sample.ifThen(hit, hitScope -> {
                        Expr<BoolT> continuation = hitScope.inline(body, List.of(
                            InlineArgument.value(hitScope.intLiteral(sampleIndex))));
                        hitScope.assign(running, continuation);
                    });
                });
            });
        }
    });
}
```

Kotlin 关键片段（完整文件见 [KotlinRaycastMacro.kt](../../examples/mni/raycast/provider/kotlin/example/mniraycast/KotlinRaycastMacro.kt)）：

```kotlin
const val SAMPLE_STEP: Double = 0.5

fun raycast(context: MacroContext, samples: Const<IntT>, body: InlineFn<BoolT>): Block {
    val count = samples.intValue()
    require(count >= 0) { "Raycast sample count must not be negative" }
    return context.quote().kotlin().block {
        val running = local("running", context.types().boolType(), boolLiteral(true))
        for (index in 0 until count) {
            ifThen(running.load()) {
                val active = this
                val mc = active.minecraft()
                val point = mc.localOffset(mc.currentPosition(), mc.currentRotation(),
                    0.0, 0.0, index * SAMPLE_STEP)
                mc.at(point, Consumer { sampled ->
                    val sample = sampled.minecraft()
                    val hit = sample.blockMatches(sample.blockAt(sample.currentPosition()),
                        BlockPredicate.block("minecraft:stone"))
                    sample.ifThen(hit, Consumer { hitScope ->
                        hitScope.assign(running, hitScope.inline(body, listOf(
                            InlineArgument.value(hitScope.intLiteral(index))
                        )))
                    })
                })
            }
        }
    }
}
```

语义要点：

- `samples` 是 `CONST`，所以生成期能读取次数并展开；`count` 不是运行时循环变量。
- 步长固定为 0.5 方块，位置由 `currentPosition()`、`currentRotation()`、`localOffset(...)`、`blockAt(...)` 构造，没有可变位置句柄或动态坐标 API。
- 每个采样点先由 `running` 控制，再进入 `at(samplePoint)`，用真实 `if block ~ ~ ~ minecraft:stone` 判命中；只有命中才插入源码回调并传入采样下标。
- 回调返回 `bool` 写回 `running`：`true` 继续，`false` 跳过剩余采样。回调里的 `return` 只退出该次内联区域；ray 调用之后的调用者语句仍执行。
- **采样点是点，不是去重后的方块**：同一方块可被连续两个采样点命中两次；命中次数不等于不同方块数量。
- 不生成运行时 ray 函数、参数区或编号分派；`at`/`if` 等普通 lowering 仍会生成控制流 helper。

需要累积命中结果时使用下面的返回值重载；原有 bool 回调重载保留：

```text
raycast(samples: CONST int, body: INLINE (int)->bool): void
raycast(samples: CONST int, initial: int,
        step: INLINE (int,int)->int, keepGoing: INLINE (int)->bool): int
```

仅在实际 stone 采样命中时，先执行 `step(accumulator, index)` 并保存下一累加器，再执行 `keepGoing(index)`；`true` 继续，`false` 停止后续采样。固定 0.5 步长和调用点 ambient 上下文不变，不引入 Result 类型或共享可写盒。

```mcfpp
external continueHits: int = raycast(5, 0, { hits, i -> hits + 1 }, { true });
external continueLast: int = raycast(5, -1, { last, i -> i }, { true });
external stopHits: int = raycast(5, 0, { hits, i -> return hits + 1; }, { return false; });
external stopLast: int = raycast(5, -1, { last, i -> i }, { false });
external afterStop: int = stopHits + 1;
```

命中数和最后下标由两次独立返回值遍历取得。在示例固定方块环境中，预期继续结果为 `4`/`3`，停止结果为 `1`/`0`，调用者尾部 `afterStop=2`；当前文档没有重新执行这些迁移后示例。

`repeat` 与 `raycast` 的对照：

| | `repeat` | `raycast` |
|---|---|---|
| 次数/步数 | `VALUE int`，运行期循环 | `CONST int`，生成期展开 |
| 生成节点数 | 与次数无关，固定一个循环体 | 随采样数线性增长 |
| 运行期控制 | `while` 条件 + 计数自增 | 每个采样点的 `if running` + 命中分支 |
| 回调作用 | 每次迭代插入 | 每个命中采样点插入 |
| 停止方式 | 循环条件自然结束/回调只跳过当前插入 | 回调 `false` 写回 `running`，跳过后续采样 |

## 5. 执行上下文

`MinecraftBuilder` 扩展 `CodeBuilder`，提供结构化上下文和方法：

```java
MinecraftBuilder mc = builder.minecraft();
mc.executeAs(mc.entities(EntityQuery.allPlayers()), (actor, self) -> {
    actor.at(actor.position(self), placed -> {
        Ref<IntT> hp = placed.score(self, "hp");
        placed.assign(hp, placed.sub(hp.load(), placed.intLiteral(1)));
    });
});
```

### 5.1 三个组件相互独立

| 操作 | 只改变 | 不改变 | 作用 |
|---|---|---|---|
| `executeAs(targets, body)` | executor | position、rotation | 对集合中每个实体执行 body；`self` 属于子 scope/子 context |
| `at(position, body)` | position | executor、rotation | 在指定位置执行 body |
| `rotated(rotation, body)` | rotation | executor、position | 使用指定旋转执行 body |

每次上下文转换要求新的子 scope 和子执行帧。`executeAs` 产生的 `self` 只属于该子 scope/executor；`currentPosition()`/`currentRotation()` 绑定创建时的对应组件，换到不同的 position/rotation context 后必须重新获取，不能把旧句柄重新解释成新上下文。`executeAs` 的 body 不会隐式增加 `at`，`at` 也不会替换 executor。

`currentPosition()`/`currentRotation()` 绑定当前上下文的对应组件。`localOffset(base, rotation, left, up, forward)` 只产生位置，不替换 ambient rotation；具体 lowering 取决于 rotation 的来源：

- `Absolute` rotation：把左/上/前偏移折算成世界轴偏移，只发 `positioned ~ ~ ~`，不改变 rotation。
- `Current` rotation：发 `positioned ^ ^ ^`，保留当前执行环境的 rotation；入口没有实体执行者、不能假设 `@s` 时也可用。
- `Entity` rotation：需要先 `rotated as @s` 再 `positioned ^ ^ ^`，因此会暂时改变 rotation。后端只在计划帧能忠实恢复父 rotation 时补回；若父 rotation 只是入口的 ambient 能力、没有命令表示或无法忠实重建，则在提交前诊断拒绝。

因此不能说“localOffset 永不旋转”：绝对/当前 rotation 形式不改变 ambient rotation，实体 rotation 形式可能显式旋转并恢复，不能恢复的形状会报错而不是默默替换。

`offset(base, x, y, z)` 是世界轴偏移；`localOffset` 使用显式 rotation 的左/上/前偏移。位置句柄本身不可变。

### 5.2 跨上下文诊断

以下情况在提交前拒绝，而不是默默固定到错误实体：

| 场景 | 典型诊断 |
|---|---|
| 外层 `executeAs` 创建的 REF 在嵌套 `executeAs` 中使用 | `Location cannot be rebound to a different execution context` 或 `Captured address cannot change execution context` |
| 跨 `executeAs` 的 helper REF 实参 | `... cannot cross executeAs without a stable identity` |
| 绑定了实体的 `currentPosition` 被换到另一 position context | `Position reference cannot be rebound to a different position context` |
| `currentRotation` 被换到另一 rotation context | `Rotation reference cannot be rebound to a different rotation context` |
| 实体 rotation 的 `localOffset` 无法恢复父 rotation | `The rotation at plan entry cannot be restored after an explicit entity-rotation local offset` 或 `The parent rotation cannot be restored after a local-offset position` |
| 外层实体 handle 在嵌套 `executeAs` 中复用 | `Entity reference cannot cross executeAs without a stable identity` |
| 绑定实体的 score 失去原 executor | `Score lost its bound entity executor` |

错误保留精确生成节点/源码 span，并在根调用处附加完整展开链。首期没有通用实体身份捕获；固定 `ScoreHolder`（如 `ScoreHolder.named("#scores")`）不依赖 `@s`，可以跨 `executeAs` 使用。同一 executor 内的 REF helper 会按原位置特化，不把 REF 内容复制进参数区。

## 6. 声明生成与 annotation

声明处理器与宏入口是两条注册链，但共用代码模型、类型域、会话和事务。

### 6.1 注册

处理器通过现有 `injectedBy` 静态 import 钩子注册，而不是新的宏入口：

```kotlin
class KotlinScoreMappingProvider private constructor() {
    companion object {
        @JvmStatic
        fun importToMNI(): Array<String> {
            val namespace = GlobalScope.getNamespace(Project.currNamespace) ?: return emptyArray()
            namespace.registerTemplateProcessor(
                "${namespace.identifier}:ScoreMapping", KotlinScoreMappingProcessor::class.java)
            namespace.scope.addAnnotation("Score", KotlinScoreAnnotation::class.java)
            return emptyArray()
        }
    }
}
```

`TemplateProcessor` 实现本身只使用公开 API：

```java
public final class MyProcessor implements TemplateProcessor {
    @Override
    public DeclarationEdits expand(DeclarationContext context, TemplateView template) {
        // 读取 template.fieldDeclarations()/methods()/annotations()。
        // 处理器只构造 edits，不直接发命令；没有新增成员时返回空批次。
        return context.declarations().edits(java.util.Collections.emptyList());
    }
}
```

这是现有集成钩子，不是宏宿主；处理器内部仍然不能访问 ANTLR 或当前 `Function`。

### 6.2 全文件声明屏障

处理器运行顺序固定：

```text
索引所有源声明
→ 完成父图、字段类型和旧 pending annotations
→ 按 (文件, 声明, annotation) 顺序运行新处理器一次
→ 验证并提交生成签名
→ 刷新生成成员的声明契约/可调用索引
→ 检查源方法和生成方法体
→ 降低
```

因此：

- 不能在遍历第一个文件的 annotation 时就检查函数体；必须等所有文件签名完成。
- `TemplateView` 的字段/方法/annotation 是**完成声明后**的只读事实。
- 每个处理器实例只运行一次；生成 annotation 只作为元数据发布，不会递归触发处理器。
- 处理器提交后，生成成员对后续处理器和普通源码查找可见；普通源码可以像调用手写方法一样调用 `player.load()`/`player.save()`。

### 6.3 `AnnotationView` 与原始闭合参数

- 注解参数按位置投影为 `"0"`、`"1"`……，值是 `ConstantValue`，来自原始源码 annotation，不经过宿主 getter。
- `@ScoreMapping("#scores")` 的 holder、`@Score("health_hp")` 的 objective 都从 `arguments().get("0")` 读取。
- 库序列化/复制后仍保留闭合参数；处理器不能依赖运行时对象或反射读取注解。
- 生成 annotation 的参数只接受编译器白名单中的精确形状；`mni:GeneratedMembers` 是非重复、零参数的内置元数据标记，只发布元数据，不再调度处理器。

### 6.4 生成成员

节选（`health`、`save` 来自同一 processor 的已验证映射；完整 Java/Kotlin provider 见示例）：

```java
MethodEdit load = declarations.method(owner, "load", noArgsVoid, body -> {
    CodeBuilder b = body.builder();
    MinecraftBuilder mc = b.minecraft();
    Ref<IntT> score = mc.score(ScoreHolder.named("#scores"), "health_hp");
    b.assign(b.fieldRef(body.self(), health), score.load());
});
MetadataEdit marker = declarations.metadata(owner, "mni:GeneratedMembers", Map.of());
return declarations.edits(java.util.Arrays.<DeclarationEdit>asList(load, save, marker));
```

Kotlin 等价：

```kotlin
val load = declarations.method(owner, "load", noArgsVoid) { body ->
    val builder = body.builder()
    val score = builder.minecraft().score(ScoreHolder.named("#scores"), "health_hp")
    builder.assign(builder.fieldRef(body.self(), health), score.load())
}
```

规则：

- 首期只生成实例方法，不替换已有方法体；`owner` 必须是当前 `TemplateView.type()` 的规范类型。
- `signature` 只描述显式参数，不包含 `self`。`MethodBodyContext.self()` 是未来调用者的原实例位置；字段用 `builder.fieldRef(self, field)` 建立。
- `self`/参数只在方法根 scope 及子 scope 有效，不能捕获到 helper、资源或外层。
- 非 `void` 方法体用 `yield` 产生方法结果；不能借用 annotation 调用者的返回区域。
- `internalFunction` 用于编译器命名的内部 helper；`metadata` 只接受白名单注解。
- `DeclarationEdits` 是一个原子批次：所有新增成员先在 detached scope 中检查名称、签名、访问、继承、字段权限和最终 body，再整体发布。某个处理器失败时，它已构造的成员/helper/资源全部丢弃；之前成功的处理器结果保留，不会出现“半个 edit”。

### 6.5 `score-mapping` 的当前语义

[score-mapping](../../examples/mni/score-mapping/README.md) 展示了真实的 annotation 派生 `load`/`save`：

- 只支持 `int`/`bool` 字段；重复的 `(holder, objective)` 目标、不可写字段和其他类型在发命令前诊断。
- `int` 精确读写；`bool` 保存严格写 `0`/`1`，读取任意非零分数为 `true`。
- 每个分数读取先把自己的临时值清零，再做条件复制；因此真正缺失的 key 读出 `0`/`false`，不会复用上一次调用的临时值。
- 固定 `ScoreHolder.named("#scores")`，不依赖实体/世界上下文。
- batch14 的运行期断言（`MniScoreMappingTest.kt:383-428`）通过：同一个执行器先写入分数，再 `reset` 真实 key，并用同一个生成 `main` 调用两次；缺失 key 下 int 读 `0`、bool 读 `false`，第二次 `save` 仍写 `9`/`1`，且没有失败的 score 操作。
- 当前 `ScoreCommandExecutor` 已把缺失的 score 条件按 false 处理（`if` 不执行、`unless` 执行），`reset` 会删除真实 key；专门测试通过。测试执行器对 `scoreboard players operation` 的缺失 key 仍会报错，不能把该限制当作 Minecraft 语义。
- 同一测试类此前的结构 helper-count 断言失败是历史记录；当前全量运行中该测试类已通过，功能结论另由 DSB 十例覆盖。历史失败保留，不再列为当前缺口。

## 7. helper、资源与诊断

### 7.1 helper

```java
FunctionRef<VoidT> helper = context.helpers().define(
    "increment",
    FunctionSignature.of(List.of(
        ParameterView.of("target", context.types().intType(), ParamMode.REF),
        ParameterView.of("delta", context.types().intType(), ParamMode.VALUE)), context.types().voidType()),
    (builder, parameters) -> {
        Ref<IntT> target = (Ref<IntT>) parameters.get(0);
        Value<IntT> delta = (Value<IntT>) parameters.get(1);
        builder.assign(target, builder.add(target.load(), delta.expr()));
    });
```

- helper 立即构造，参数身份由服务分配；首期只支持 `VALUE`/`REF`。
- `REF` 实参保留原位置和别名；helper 体不能捕获外层局部量或上下文 `self`。
- 同 executor 的 REF helper 按位置/上下文特化；跨 `executeAs` 被拒绝。
- helper 的代码、名称、资源和预算都属于当前事务。

### 7.2 资源与诊断

- `ctx.resources().reserve(ResourceKind.OBJECTIVE/STORAGE/INTERNAL_FUNCTION, hint, ResourceData.of(constant))` 只暂存，提交前不发布；名称确定且隔离，失败的展开不留下资源。
- `ctx.diagnostics().warning(site, message)` 记录警告；`error(site, message)` 标记当前展开失败，暂存输出不可提交。
- `ctx.callSite()` 返回宏调用点，适合作为没有更精确来源时的诊断位置。

## 8. 配置与展开 dump

`mcfpp.json`：

```json
{
  "macroExpansion": {
    "maxDepth": 64,
    "maxNodes": 100000,
    "dump": false
  }
}
```

命令行（与配置文件叠加）：

```text
-macroMaxDepth=64
-macroMaxNodes=100000
-dumpMacroExpansion
-dumpMacroExpansion=false
```

规则：

- 默认最大嵌套深度 64（根调用深度为 1）；更深拒绝并给出展开链。
- 单个编译单元最多生成 100000 个新节点；第 100001 个拒绝。原始源节点不计为新节点；展开生成的序言/桥接/出口节点计入。
- 失败分支已消耗的预算不退还，避免反复失败绕过限制。
- 非法/非正的限制在编译前诊断，不会悄悄恢复默认值。
- `dump=true` 时，在完整展开和最终验证后通过日志输出规范树：节点顺序、局部编号和逻辑键由遍历决定，不使用 JVM identity hash 或全局临时计数。它是调试输出，不是稳定机器格式。
- 错误诊断保留最深的失败 `SourceSpan`，并在根调用处补齐用户调用和完整 `macro → nested macro → inline` 链。

## 9. 旧 MNI 兼容边界

新宏路径不替换旧路径；旧 `@MNIFunction` 行为保持。

### 9.1 旧 `@MNIFunction` ABI

```java
@MNIFunction(
    readOnlyParams = {"int"},
    normalParams = {"int"},
    caller = "void",
    returnType = "int"
)
public static void legacy(MCInt constant, MCInt value, ValueWrapper<MCInt> result) {
    result.set(value); // 实际 provider 在旧域中计算结果
}
```

- JVM 方法必须 `public static` 且返回 `void`。
- 注解里的 `readOnlyParams`/`normalParams` 是 MCFPP 类型字符串（可带 `=default`），不是 JVM 参数名；位置 ABI 的关键是 JVM 参数顺序和 wrapper 类型。
- 位置 ABI 的参数数量 = readonly 参数 + normal 参数 + 非 void caller + 非 void 返回的 `ValueWrapper`。
- readonly/normal/caller 使用 `Var` 子类型；结果使用 `top.mcfpp.util.ValueWrapper`。
- 以单个 `NativeCallContext` 为唯一参数的入口是另一种受支持协议；注解仍描述 MCFPP 可见参数。
- Kotlin 顶层 file facade、`object`/companion 的 `@JvmStatic` 都可加载；`suspend`、默认参数 mask、未映射的扩展 receiver/value class 不受支持。Kotlin 默认参数不会自动成为 MCFPP 默认参数。

旧 MNI 在宏生成树中只是一个普通调用目标，在 lowering 阶段才按旧协议执行；宏构造期间不会 eager 调用它。

### 9.2 新旧边界

| | `@MNIFunction` | `@MNIMacro` |
|---|---|---|
| 首参数 | 无特殊首参 / `NativeCallContext` | 精确 `MacroContext` |
| 返回 | JVM `void` + `ValueWrapper` | `Block` 或 `Expr<T>` |
| 实参求值 | 旧路径 eager 访问 | 纯绑定后按模式捕获/临时化 |
| 参数模式 | readonly/normal/caller | `VALUE`/`REF`/`CONST`/`INLINE` |
| 用途 | 运行时原生函数 | 编译期构造运行时代码 |

新宏不能作为 `@MNIFunction` 的 native 目标，也不能作为静态 INLINE 主体（静态回调只接受有源码体的普通函数）。首期没有成员宏/宏泛型参数；接收者通过显式参数传递。

## 10. 独立实验 API 版本

`ApiVersion` 与编译器发行版解耦：

```java
if (!ApiVersion.isCompatibleWith("0.1")) {
    throw new IllegalStateException(
        "Provider requires MNI API 0.1, compiler has " + ApiVersion.VERSION);
}
```

```kotlin
require(ApiVersion.isCompatibleWith("0.1")) {
    "Provider requires MNI API 0.1, compiler has ${ApiVersion.VERSION}"
}
```

当前 `0.1.0-experimental` 不是稳定承诺。完整规则见 [api-versioning.md](api-versioning.md)。

## 11. 自检与证据边界

以下已保存 CLI/DSB/测试记录属于 **lambda 迁移前的历史证据**，不验证本页新语法、只读局部捕获或新增累加器重载。迁移后的编译与 DSB 结果由协调者另行记录。

本轮文档修订只复用协调者已交付的证据，**没有在写文档时重新运行** Gradle、`javac`、`kotlinc`、CLI 或任何测试；教程片段未经过独立重编译。

已完成并核对：

- 对照 `src/main/kotlin/top/mcfpp/mni/api/` 的真实接口写了 Java/Kotlin 入口、参数模式、上下文、声明和 helper 示例。
- 宏入口、上下文、helper 和 processor 主体示例只使用公开 API，不引用 ANTLR、`Function.currFunction` 或 `Immutable*`；旧 `@MNIFunction`/`Var` 片段只用于兼容对照，明确属于历史层。首期边界（具名 namespace 宏、无成员宏/宏泛型、旧 ABI 保留）不变。
- 确认 `ApiVersion.kt` 只依赖 Kotlin 标准库，不引用编译器版本。
- 按实际接口修正 Kotlin 静态入口说明：顶层函数本身是静态 JVM 方法，`object`/companion 才需要 `@JvmStatic`。
- 按 [`ContextCommandEmitter.kt`](../../src/main/kotlin/top/mcfpp/mni/expand/ContextCommandEmitter.kt) 修正 rotation 说明：绝对/当前 rotation 的 `localOffset` 不改变 ambient rotation；实体 rotation 形式可能旋转并仅在可忠实恢复时补回，否则提交前拒绝。
- 按 [`CompilerPlanStrings.kt`](../../src/main/kotlin/top/mcfpp/mni/expand/CompilerPlanStrings.kt) 更新字符串 `+` 语义：运行期精确拼接、操作数各求值一次且先左后右、平衡转义 helper、目标在归约后写入；`VALUE`/`REF` 规则保持。
- 示例功能复用 [`functional-summary.json`](../../build/inheritance-mni/dsb-check/functional-summary.json)：10 个 Java/Kotlin case 在 DSB 26.3 下全部 PASS，其中 8 例复用 `attempt2`，`repeat` 2 例由 `attempt3` 新执行，原产物哈希未变。命令行与入口见 [`benchmark/README.md`](../../examples/mni/benchmark/README.md)。
- 历史失败保留但已明确其范围：`attempt1` 的 score-mapping 观察探针在调用帧 pop 后读取而失败，batch14 的 `MniScoreMappingTest` 最后结构 helper-count 断言失败；这些是历史观察/测试问题，其功能结论已由 DSB 十例通过覆盖，不再列为当前完成门槛。

已完成的证据边界：

- 全量功能运行的 6 项失败已由受影响定向运行修复；合并证据为 136 项新执行 + 1873 项复用，不是一次新的 2009 项全量运行。详见 [regression-results.md](regression-results.md)。
- 编译器导出字符串 DSB 已实际通过（动态控制字符/中文/emoji、运行期值与字面量 `$(left)`、折叠字面量），0 diagnostics；结果见 [runtime-string-dsb/attempt2/result.json](../../build/inheritance-mni/runtime-string-dsb/attempt2/result.json)。
- 没有 Minecraft 实机或性能数据；按用户要求，真实游戏和性能验收已取消，不是未完成项。`ScoreCommandExecutor`/`ContextCommandExecutor` 与 DSB 都不是游戏服务端。
- API 仍是实验性 `0.1.0-experimental`，不声明 API 已稳定；独立最终验收见 [acceptance.md](../implementation-review/acceptance.md)。

## 12. 继续阅读

- [README.md](README.md)：API 地图、示例状态和验证边界。
- [api-versioning.md](api-versioning.md)：版本字段、兼容策略和升级规则。
- [implementation-contract.md](implementation-contract.md)：实现契约（会话、验证、降低、声明、上下文）。
- [source-projection-contract.md](source-projection-contract.md)：纯源绑定、REF 捕获、detached plan。
- [kotlin-compatibility.md](kotlin-compatibility.md)：旧 `@MNIFunction` 的 Kotlin/JVM 构建与 ABI 示例。
- 示例：[damage](../../examples/mni/damage/README.md)、[repeat](../../examples/mni/repeat/README.md)、[raycast](../../examples/mni/raycast/README.md)、[nbt-reference](../../examples/mni/nbt-reference/README.md)、[score-mapping](../../examples/mni/score-mapping/README.md)。

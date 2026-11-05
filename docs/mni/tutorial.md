# MNI Kotlin 宏教程

MNI 宏在编译期运行 Kotlin，通过结构化代码树生成 MCFPP/Minecraft 的运行时代码。宏作者使用 `top.mcfpp.mni.api` 与 `top.mcfpp.annotations`。

MNI 的 API、DSL、工程模板和示例以 Kotlin 编写体验为设计依据。Java 可以调用生成的 JVM 方法；新增 API 不以 Java lambda、重载或 builder 的便利性为目标。当前 API 版本为 `0.2.0-experimental`，见 [版本策略](api-versioning.md)。

## 1. 创建 Kotlin MNI 工程

使用 JDK 25：

```shell
mcfpp init hello --mni
cd hello
mcfpp build
mcfpp test
```

默认目录：

```text
src/main/kotlin/       Kotlin provider
src/test/kotlin/       Kotlin/JUnit 测试
src/main/mcfpp/        MCFPP 源码与显式宏声明
```

CLI 自动应用 Kotlin/JVM 插件，使用工程根目录的源码；生成的 Gradle 文件放在 `.mcfpp/gradle`，编译产物在 `build/.mcfpp/mni`。通过 `mcfpp install` 更新依赖锁，然后可使用 `mcfpp build --locked --offline`。升级前创建的 MNI 锁需要重新 install，以记录 Kotlin 插件引入的依赖。

独立 provider 也可以编译为 JAR，加入工程的 `jars`。宏使用与 MNI 函数相同的显式声明：

```mcfpp
func sum(left: int, right: int): int = example.macros.MathMacros.sum
```

`@MNIMacro` 描述实现签名；公开名称和参数名由 MCFPP 声明决定。`injectedBy` 不会自动暴露宏。注解的 `normalParams` 只列类型，模式由 `Value`、`Ref`、`Const`、`InlineFn` 推导。

## 2. 最小表达式宏

```kotlin
package example.macros

import top.mcfpp.annotations.MNIMacro
import top.mcfpp.mni.api.*

object MathMacros {
    @JvmStatic
    @MNIMacro(
        normalParams = [
            "int",
            "int"
        ],
        returnType = "int"
    )
    fun sum(context: MacroContext, left: Value<IntT>, right: Value<IntT>): Expr<IntT> =
        context.quote.expression(context.types().intType()) {
            yield(left.value + right.value)
        }
}
```

MCFPP 调用：

```mcfpp
func sum(left: int, right: int): int = example.macros.MathMacros.sum

func main() {
    print(sum(20, 22));
}
```

入口必须是公开的 JVM 静态方法，首参数为 `MacroContext`。`object`、companion 成员使用 `@JvmStatic`；顶层函数天然是静态入口，可用 `@file:JvmName("MathMacros")` 固定 facade 名称。普通实例方法与 `suspend` 不符合入口签名。

`void` 宏返回 `Block`，使用 `context.quote.block { ... }`。其他宏返回 `Expr<T>`，表达式区域的所有可达正常出口必须通过 `yield` 给出结果。

## 3. Kotlin 控制流与生成的控制流

Kotlin 的 `for`、`if`、`while` 在宏展开时执行。DSL 的 `ifThen`、`ifElse`、`whileLoop` 构造运行时代码。

下面是 `repeat` provider 的核心；`times` 为 `Value<IntT>`，`body` 为 `InlineFn<VoidT>`：

```kotlin
context.quote.block {
    val iteration = local("i", literal(0))
    val limit = local("limit", times.value)
    whileLoop({ iteration.value lt limit.value }) {
        inlineVoid(body, iteration.value)
        iteration += 1
    }
}
```

宏展开只构造一次循环体和条件。Minecraft 运行时反复求值生成的条件，执行回调与计数自增。回调的 `return` 只结束当前内联区域，后面的计数自增仍然执行。

若次数为 `Const<IntT>`，可读取 `intValue()`，再用 Kotlin `for` 在生成期展开多份代码。完整实现见 [repeat](../../examples/mni/repeat/README.md) 和 [raycast](../../examples/mni/raycast/README.md)。

DSL 回调在构造时立即执行一次。不要保存 builder 或回调供以后使用；回调返回后作用域关闭。生成树不会保存宿主 lambda。

## 4. 表达式与赋值

在 `CodeScope` 内：

| 写法 | 含义 |
| --- | --- |
| `literal(1)`、`literal(true)`、`literal("text")` | 构造对应字面量 |
| `local("total", literal(0))` | 从初始表达式推导类型，声明运行时局部变量 |
| `input.value` | 读取 `Value<T>` 已绑定的稳定表达式 |
| `target.value` | 读取 `Ref<T>` 指向的位置 |
| `target.value = expression` | 构造位置写入 |
| `a + b`、`a - b`、`a * b`、`a / b`、`a % b` | 构造算术表达式 |
| `counter += 1`、`counter -= 1` | 读取并写入同一位置 |
| `a lt b`、`a le b`、`a gt b`、`a ge b` | 构造比较 |
| `a eq b`、`a ne b` | 构造相等或不等比较 |
| `!condition`、`a and b`、`a or b` | 构造逻辑表达式 |

Kotlin 的 `==` 和 `<` 要返回宿主 Boolean，不能用来构造 `Expr<BoolT>`；使用 `eq`、`lt` 等 DSL 方法。Kotlin 的 `val` 表示宿主绑定不变，绑定的 `Local<T>` 仍然可以生成运行时写入。

`call(function, expr1, expr2)`、`callVoid`、`inline`、`inlineVoid` 的变参形式将每项作为 VALUE 参数。需要 CONST 或编译器内部 REF 时使用显式的 `CallArgument` / `InlineArgument` 列表。

构造表达式不等于执行表达式；独立语句用 `evaluate(expr)` 追加。`yield` 只能用于当前表达式区域。

普通 MCFPP `string + string` 保留 UTF-16 内容，处理引号、反斜杠和空字符串；它生成运行期拼接，宿主 Kotlin 字符串拼接在生成期执行。

## 5. 参数模式与公开地址

- `VALUE`：对应 `Value<T>`。实参按源码顺序求值一次，即使宏体未使用也保留实参副作用。成员 receiver 位于普通实参之前。
- `CONST`：对应 `Const<T>`。只接受可证明的闭合常量；动态表达式会报错，不会退回 VALUE。
- `INLINE`：对应 `InlineFn<S>`。接受静态源码 lambda 或普通源码函数引用。局部捕获按创建时的值快照且只读，全局声明保持实时访问。
- `REF`：对应编译器提供的 `Ref<T>` 内部位置句柄，保留别名、原位置和可写性检查。

公开 MCFPP 源码使用普通 VALUE 类型 `ScoreRef` 和 `NBTRef<T>`，从显式 Minecraft 地址创建。`ref expression` 已移除；不能用它对局部变量取地址。见 [damage](../../examples/mni/damage/README.md) 和 [NBT 引用](../../examples/mni/nbt-reference/README.md)。

## 6. Minecraft 执行上下文

几何、score 与执行上下文操作可以直接放在 DSL 作用域内：

```kotlin
context.quote.block {
    executeAs(entities(EntityQuery.allPlayers())) { self ->
        at(position(0.0, 64.0, 0.0)) {
            rotated(rotation(90.0, 0.0)) {
                score(self, "hp") += 1
            }
        }
    }
}
```

`executeAs` 改变执行者，`at` 改变位置，`rotated` 改变旋转。每个回调获得对应的子作用域；self、score、位置等句柄仍受作用域与上下文检查约束。固定 holder 可以用 `score(ScoreHolder.named("#health"), "hp")`。

`localOffset` 使用明确的旋转和宿主字面量偏移量。当前环境旋转、绝对旋转和实体旋转的 lowering 规则见 [实现契约](implementation-contract.md)；不能把局部坐标表达式当作任意运行时向量 API。

## 7. 声明处理器与 helper

`TemplateProcessor` 在声明完成后读取不可变的 `TemplateView`、字段、方法和注解事实，返回 `DeclarationEdits`。注册仍通过 `importToMNI` / `injectedBy` 完成。

生成方法的 builder 可以进入相同的 DSL：

```kotlin
val method = context.declarations().method(owner, "load", noArgsVoid) { body ->
    body.builder().kotlin {
        val source = score(ScoreHolder.named("#health"), "hp")
        fieldRef(body.self(), healthField).value = source.value
    }
}
```

示意中的 `owner`、`noArgsVoid`、`healthField` 来自处理器已验证的声明事实。完整可编译实现见 [score-mapping](../../examples/mni/score-mapping/README.md)。

处理器可以新增方法、内部函数和白名单元数据，不能覆盖已有可见签名。宏 helper 通过 `context.helpers().define` 构造，参数只使用受支持的 VALUE/REF；helper 不能捕获外层局部变量或执行者句柄。

诊断通过 `context.diagnostics()` 发出，资源通过 `context.resources().reserve` 暂存。展开失败后，代码、声明和资源会整体回滚。

## 8. JVM 互操作与设计边界

内置 provider 和注解已迁到 `src/main/kotlin/top/mcfpp`。既有 provider 类名、注解元数据和 JVM 静态入口用于 MCFPP 注册；Java 可以调用这些 Kotlin 方法。

旧 `@MNIFunction` 入口继续使用原有同步协议，包括 `NativeCallContext` 和 `ValueWrapper`。Kotlin 默认参数不会自动变成 MCFPP 默认参数。兼容说明见 [kotlin-compatibility.md](kotlin-compatibility.md)。

宏代码通过结构化模型生成，不访问 ANTLR、当前可变 Function/Var 或原始命令字符串。现阶段只有具名 namespace 宏；没有任意 MCFPP 编译期解释器、运行时闭包或逃逸回调。

检查命令与实际执行结果应分开记录。历史 Java/Kotlin 对照运行记录只适用于其当时源码；本次修改的验证以实际 Gradle 与 CLI 结果为准。

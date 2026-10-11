# 异常效果系统：Java 对比与 Minecraft 命令展开

日期：2026-10-07。配套设计：[异常效果系统草案](exception-effects-design.md)。

后续架构修订见 [无栈回溯依赖的效果与跨 tick 任务设计](stackless-effects-and-tasks-design.md)。本文件第 5 节的压栈示例说明早期兼容实现，不代表生产后端必须使用模拟调用栈；新版包含不分配帧的异常和定时器目标代码。

这些是**按提案手工展开的目标代码示例**，使用真实 Minecraft 命令；当前编译器尚未实现新异常语法，下面不是运行当前编译器得到的产物，也未在服务器上实测。目标选为 Java Edition 1.20.4，使用该版本支持的 `return run`。符号、storage 和帧字段为便于阅读的示例命名，不是已经固定的 MCFPP ABI。

## 1. 与 Java 异常的关系

首版可以理解为“带自动推导的 checked exceptions，加上适合数据包的编译方式”。在控制流层面，两者都终止抛出点之后的当前计算，交给调用链中最近的匹配处理器；处理器正常结束后继续，不恢复到 throw 后面。Java 的受检异常检查本身可以从效果系统的角度理解，不能宣称 Java 没有静态异常分析。

| 维度 | Java | 本提案 |
| --- | --- | --- |
| 普通函数向外传播 | checked exception 必须捕获或写入 throws | 默认从函数体和调用关系推导，throws 是可选上界 |
| 不受检异常 | RuntimeException、Error 及其子类不要求声明 | 所有显式语言 throw 都受检查；引擎强制停止等不属于语言异常 |
| 入口 | main 可以声明 throws，运行时仍可能出现未捕获异常 | load/tick/事件等入口要求无逃逸语言错误 |
| 错误值 | Throwable 对象及继承体系 | 首版使用名义错误类型、载荷及规范身份，错误类型不可再继承 |
| 运行时传播 | JVM 异常处理器表与帧退出机制 | 编译器生成帧内状态、条件续块与调用收尾 |
| 高阶效果 | 普通函数接口没有本提案所设想的通用效果行机制 | 效果变量是后续扩展，首版尚不提供 |

Java 的异常检查与控制转移规则见 [JLS 第 11 章](https://docs.oracle.com/javase/specs/jls/se25/html/jls-11.html)，JVM 的处理器匹配及帧退出见 [JVMS §2.10](https://docs.oracle.com/javase/specs/jvms/se25/html/jvms-2.html#jvms-2.10)。表中的 MCFPP 行为是设计选择。

例如 Java 普通方法需要这样显式传播受检异常：

```java
int checkout() throws PaymentException {
    return pay();
}
```

提案中的 MCFPP 可以省略传播声明：

```mcfpp
func checkout() -> int {
    return pay();
}
// 若 pay 的契约是 throws(PaymentFailed)，则自动推导相同的逃逸集合。
```

`catch` 后消除对应逃逸错误、保留此前的状态修改，这些基本原则 Java 也有。首版的实际增益主要是推导、入口约束、错误集合的精度和后端代码生成，而非创造了完全不同的异常控制流。

此外，MCFPP 草案中的 `Error` 是全部语言错误的根，含义不对应 Java 的 `java.lang.Error`。现有 Minecraft 命令失败也不会自动变成可捕获错误。

## 2. 示例约定

以下代码块标题中的文件名表示拟议输出文件。复制成 1.20.4 数据包时，函数放在 `data/demo/functions/` 下，目录中的嵌套路径对应函数 ID。

在隔离的演示数据包中，首次运行前执行：

```mcfunction
scoreboard objectives add demo dummy
data modify storage demo:rt stack set value []
```

这里的 stack 初始化仅用于演示启动，不可在活跃调用链中反复执行。生产初始化应在没有活跃调用帧时完成。

`stack[0]` 是当前帧，`stack[1]` 是调用者帧。示例的叶函数使用临时记分板；这些例子没有跨该调用活跃的记分板值，所以不需要 spill/restore。通用编译器必须按活跃性保存和恢复，不能把这个简化照搬到递归或任意函数调用。

## 3. 不会抛出的函数：不增加异常协议

```mcfpp
func add(a as int, b as int) -> int {
    return a + b;
}
```

推导出的逃逸集合是空。假定参数已进入 `#a`、`#b`，返回槽为 `#ret`，仅列出运算主体：

```mcfunction
# add.mcfunction（省略普通参数装载和返回槽交接）
scoreboard players operation #ret demo = #a demo
scoreboard players operation #ret demo += #b demo
```

这里没有异常状态初始化、类型标签、失败检查。普通函数原本需要的参数传递、返回值和递归保护成本仍然存在。

## 4. 本地捕获：可直接化为分支

```mcfpp
error NotPositive(value as int);

func localCheck(value as int) {
    try {
        if (value <= 0) {
            throw NotPositive(value);
        }
        /say Accepted
    } catch (e as NotPositive) {
        /say Rejected
    }
    /say After
}
```

错误只有一种，抛出位置和处理器都在当前函数内，catch 不读取 e，而且分支只执行 say，不会修改条件。优化后可完全消除错误载荷与状态槽。假定参数已装入 `#value`：

```mcfunction
# local_check.mcfunction
execute if score #value demo matches ..0 run function demo:local_check/rejected
execute unless score #value demo matches ..0 run function demo:local_check/accepted
say After
```

```mcfunction
# local_check/rejected.mcfunction
say Rejected
```

```mcfunction
# local_check/accepted.mcfunction
say Accepted
```

value=0 时预期依次输出 Rejected、After；value=3 时输出 Accepted、After。不会先输出 Rejected 再输出 Accepted。这一展开依赖两个分支都不改变 `#value`；任意处理器可能修改条件时，必须冻结分派条件或使用只进入一条路径的控制流。

这种优化来自已知控制流和未使用载荷，不是“effect system”这个名字自动保证的性能；Java 编译器也可以对适当的异常代码做优化。

## 5. 跨函数捕获和 finally：完整简化展开

```mcfpp
error NotPositive(value as int);

func requirePositive(value as int) {
    if (value <= 0) {
        throw NotPositive(value);
    }
    /say Accepted
}

func main() {
    try {
        requirePositive(0);
        /say Main success
    } catch (e as NotPositive) {
        /say Main caught
    } finally {
        /say Cleanup
    }
    /say Finished
}
```

为展示调用协议，此例保留函数边界，不进行跨函数内联或按常量实参特化。requirePositive 的错误集合为 `{NotPositive}`，main 的逃逸集合为空。

**调用者 `main.mcfunction`：**

```mcfunction
# main 的帧。
data modify storage demo:rt stack prepend value {}

# requirePositive 的帧及实参。
data modify storage demo:rt stack prepend value {value:0}
function demo:require_positive

# 先将结果状态复制到 main 帧，再删除被调用帧。
data modify storage demo:rt stack[1].call set from storage demo:rt stack[0].exit
data remove storage demo:rt stack[0]

# 当前 stack[0] 又是 main。
function demo:main/dispatch

# 本例 try/catch 两条路径都正常汇合，因此可以共用这一段 finally。
say Cleanup
say Finished
data remove storage demo:rt stack[0]
```

**被调用者 `require_positive.mcfunction`：**

```mcfunction
data modify storage demo:rt stack[0].exit set value {failed:0b}
execute store result score #value demo run data get storage demo:rt stack[0].value 1

execute if score #value demo matches ..0 run return run function demo:require_positive/raise

say Accepted
return 0
```

**抛出分支 `require_positive/raise.mcfunction`：**

```mcfunction
data modify storage demo:rt stack[0].exit set value {failed:1b,error:{type:"demo:NotPositive",payload:{}}}
data modify storage demo:rt stack[0].exit.error.payload.value set from storage demo:rt stack[0].value
return 0
```

这里的 `demo:NotPositive` 是可读的示意错误身份，正式实现须遵循库元数据的规范编码。错误的 value 字段已复制到独立载荷，退栈后仍能读取。

**分派 `main/dispatch.mcfunction`：**

```mcfunction
execute if data storage demo:rt stack[0].call{failed:1b} run return run function demo:main/catch_not_positive
say Main success
return 0
```

**捕获 `main/catch_not_positive.mcfunction`：**

```mcfunction
say Main caught
return 0
```

requirePositive 的完整契约只允许 NotPositive，因此这里失败即匹配，不必再次比较类型。多种错误部分捕获时，分派器需要检查错误身份；未匹配错误必须进入外层传播路径。

如有处理器读取 e.value，它对应调用者帧中的 `stack[0].call.error.payload.value`。示例没有成功返回值；有返回值的 Invoke 只能在 `failed:0b` 时复制成功结果，禁止在失败路径读取旧返回槽。

### 5.1 两条执行路径

| 步骤 | requirePositive(0) | 改为 requirePositive(3) |
| --- | --- | --- |
| 被调用函数 | 写入 NotPositive 及 value=0，提前结束 | 输出 Accepted |
| 交接与退栈 | 复制失败状态及错误载荷，退回 main 帧 | 复制 `{failed:0b}`，退回 main 帧 |
| 分派 | 输出 Main caught | 输出 Main success |
| finally | 输出 Cleanup | 输出 Cleanup |
| try 之后 | 输出 Finished | 输出 Finished |
| 调用结束 | 两个帧都已删除，stack 恢复为空 | 两个帧都已删除，stack 恢复为空 |

这是按命令路径推演的预期结果，不是服务器执行记录。

`return 0` 在两条路径中都出现，它只控制 Minecraft 函数结束；语言异常完全由 `exit.failed` 表示。因此不能用 Minecraft 命令返回数值 0 推断 MCFPP 抛出了错误。

### 5.2 为什么需要 return run

抛出辅助函数中的 `return 0` 只结束那个辅助函数。被调用者还必须结束自身，才能跳过 `say Accepted`，所以外层使用：

```mcfunction
execute if score #value demo matches ..0 run return run function demo:require_positive/raise
```

`return run` 的功能与 1.20.3 中的相关变更见 [Minecraft 官方发布说明](https://www.minecraft.net/en-us/article/minecraft-java-edition-1-20-3)。旧目标需要将函数拆为受条件保护的续块，不能直接复用这个版本的命令。

本例将 finally 放在分派返回之后，是因为 catch 与正常分支都正常结束。若 try/catch 中含有 return、再次抛出、break/continue，则编译器必须建立保存退出原因的清理块；简单在文件末尾追加 finally 会漏执行或误执行后续语句。

## 6. 成本与后续验证

相对普通调用，第三例增加了出口初始化、状态交接、失败分派以及错误路径的载荷构造。压栈、参数准备、退栈的一部分成本属于原有普通调用，不能全部计为异常系统开销。

JVM 有自身异常表和帧处理机制，MCFPP 要用命令显式实现传播。不能由“采用 effect system”推出运行时一定比 Java 快，也不能将 JVM 机制等同于每次调用后都生成一条失败状态判断。实际性能由后端、优化与工作负载决定。

后续实现验收应覆盖：两个输入的输出次序、入口栈深恢复、payload 退栈后可读、catch 中调用不会覆盖原错误、finally 在非普通出口上的执行、失败时不读成功结果，以及无异常函数的新增异常命令数为零。

## 附录：本轮示例自检

示例用于说明设计和命令控制流；五项平均 4.0/5，尚不作为已执行的性能或正确性证据。

| 维度 | 评分 | 依据与改进 |
| --- | --- | --- |
| 准确性 | 4/5 | Java 与 return 语义引用官方规范；MC 命令仅作路径推演，下一步需服务器验证 |
| 完整性 | 4/5 | 给出无异常、本地捕获、跨调用与简单 finally；复杂非普通退出只解释协议，后续补独立样例 |
| 清晰度 | 4/5 | 展开文件完整并列出双路径；帧交接仍需读者理解 stack[0]/stack[1] |
| 可操作性 | 4/5 | 给出目标版本、初始化与函数目录；尚未打包成服务器测试数据包 |
| 简洁性 | 4/5 | 保留运行机制所需命令；完整展开较长，正文回答应只突出关键段 |

优先改进为真实服务器双路径验证，其次补一个 finally 中再次抛出的样例。自检判断：满足解释与目标代码示例需求；应明确告知用户它们不是当前编译器实产。没有评分不高于 2 的项。

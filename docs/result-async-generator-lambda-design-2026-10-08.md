# MCFPP Result、跨 tick 执行、generator 与 lambda 讨论记录

日期：2026-10-08。

记录范围：自 [2026-10-07 类型系统讨论决策记录](type-system/design-decisions-2026-10-07.md) 之后的讨论，包含错误处理、跨 tick 执行、调度器、计算组合、generator、状态保存、lambda 及相互关系。此前关于名义类型、联合类型、方差、泛型、object/伴随对象、any、ScoreRef 与 NBTRef 的记录继续有效。

本文是设计记录，不表示编译器已经实现这些能力。MCFPP 代码用于说明选定的语法方向和语义；尚未确定的库名称、完整类型签名和辅助 API 会特别说明。mcfunction 示例是手工展开，没有经过当前编译器生成或 Minecraft 服务端运行验证。

## 1. 决策状态与阅读方式

本文区分三种状态：

- **已确认**：用户明确选择或明确修正的方向。
- **设计展开**：为解释已确认方向提出的语义边界和实现方案；不等同于每个实现细节都已逐项确认。
- **待定**：讨论没有完成的具体接口、规则或表示。不得直接拿早期提案补成已确认规范。

最新决定优先于较早提案。尤其不能把旧文档中的 throw 主方案、固定任务限制、旧 lambda 形状或带 tick 语义的 yield 当作本轮结论。

### 1.1 已确认的核心决策

| 主题 | 本轮结论 |
| --- | --- |
| 错误处理 | 采用 Result 方案 |
| 暂停函数 | 使用 `suspend<Game> func test()` 这一声明方向；Game 表示负责执行的调度器 |
| 默认调度器 | Game 内置，每游戏 tick 调度一轮 |
| 创建计算 | 调用暂停函数先创建待执行计算，函数体不立即执行 |
| 推进计算 | 由 await 或 spawn 等执行入口推进 |
| 等待写法 | 使用后缀 `.await()` 等待并取得结果 |
| 单轮执行 | 当前任务连续执行，直到完成或遇到未就绪的等待；经过 await 不自动消耗一个 tick |
| 计算组合 | 参考 Tokio 等库提供组合能力，不把能力限制为少数固定回调 |
| 竞争组合 | 名称采用 `or`；首先完成且按参数顺位优先的分支获胜 |
| or 的败方 | 不再继续执行；已经造成的游戏效果保留；组合只返回胜方的值 |
| yield | 只允许出现在 generator 中，用于产出中间值 |
| generator 的消费 | 使用 `g.await()` 反复取得下一次产出或完成结果 |
| generator 的结束 | 返回 Done，不携带独立的最终返回值 |
| lambda 形状 | 改用 Kotlin 风格：参数写在花括号内，以箭头分隔参数与函数体 |
| lambda 返回 | 允许末尾表达式隐式返回 |
| lambda 修饰符 | suspend / generator 修饰符不能省略，即使目标类型明确 |

### 1.2 本文采用的说明记法

- 用 `Future<T>` 泛指最终产出一个 T 的待执行计算；它的正式类型名和是否显式带 scheduler 参数尚未确定。
- 用 `Task` 表示提交给调度器独立推进的运行实例，用 `TaskHandle` 表示观察或操作该实例的句柄；名称和完整接口尚未确定。
- 用 `Generator<T>` 泛指反复产出 T、最终结束的实例；它不是一个可反复重新执行的函数。
- 用 `Result<T, E>`、`Ok(value)`、`Err(error)` 说明 Result 的逻辑结构。是否采用两个显式类型参数、是否提供默认错误类型、构造器如何命名，仍未定案。
- 用 `Yielded(value)` 与 `Done` 区分 generator 的两种可见取值结果；这是逻辑分类，不要求照此命名或实际分配包装对象。
- `Game.nextTick()`、`Game.spawn(...)`、`join(...)` 等是讨论中的 API 说明写法；它们不代表已经存在的标准库接口。

## 2. 设计目标和 Minecraft 平台边界

### 2.1 保留此前目标

- 高效、开发者友好、学习成本低。
- 优先服务全局定时任务和世界逻辑。
- 不要求用户理解模拟调用栈才能编写跨 tick 代码。
- 当前 NBT 模拟栈是临时实现，不作为新功能的统一前提。
- 不因为使用 lambda、Result、暂停函数或 generator，就统一生成通用 NBT 对象和压栈操作。

Minecraft 的 mcfunction 可以调用其他函数，但没有供 MCFPP 捕获并跨 tick 恢复的语言级调用栈。退出当前命令执行后，要继续一段尚未完成的计算，必须由 MCFPP 保存必要信息并安排后续入口。

这不阻止实现可暂停函数；它决定了实现应采用显式状态和恢复入口。

### 2.2 单线程执行不否定异步抽象

本轮曾讨论“所谓异步实际上都是依次执行的跨 tick 同步函数”。

更准确的划分是：

- 同一时刻实际执行的命令仍然是顺序的。
- 一次语言层面的计算可以尚未完成，就先把控制权交回调用者或调度器。
- 多个计算可以在不同暂停点交替推进。
- 这提供的是协作式并发，不承诺多线程并行。

因此，异步抽象是否合适，取决于是否需要组合、等待和管理尚未完成的计算，而不是是否拥有多个执行线程。

### 2.3 零成本抽象的适用范围

比较对象是具有相同可观察语义的合理手写数据包代码。

可以消除的成本包括不必要的包装、重复复制、已知目标的动态分派、可合并的恢复入口和独立任务记录。

不能无条件消除的成本包括：

- 跨 tick 仍需保留的快照。
- 多个活跃实例之间必须隔离的变量。
- 尚未完成的子计算状态。
- 动态任务集合的实例识别和存储。
- 真实需要的定时、唤醒和取消登记。
- 用户要求可共享观察的结果及其生命周期管理。

“没有独立包装对象”和“没有任何运行状态”不同。语言允许保存计算值，也不意味着每次都必须分配通用 NBT 对象。

## 3. Coroutine、Future、generator 与任务的区分

### 3.1 四个不同概念

| 概念 | 主要语义 |
| --- | --- |
| Coroutine | 可恢复执行，可在中途产出，并可能具有独立的最终返回值 |
| Future | 一次最终完成的计算；推进时可能尚未完成，也可能得到最终结果 |
| 异步 generator / 值序列 | 可以等待下一项，并反复产出，最后明确结束 |
| 已调度任务 | 调度器负责独立推进的根计算 |

Coroutine 不等于异步 generator。暂停、产出数据和等待外部事件是可以组合但不同的能力。

Future 可以通过手写状态和推进协议实现，不依赖对用户开放的 yield 或通用 Coroutine。编译器可以共用状态机转换；rustc 就使用相关转换生成 Coroutine 的 resume 和 Future 的 poll。

概念上的 await 展开是：

~~~text
只创建一次被等待的计算
尝试推进它
若完成：取得结果，继续当前函数
若未完成：保留状态并暂停
恢复后：继续推进原来的计算
~~~

这不是简单地把源代码里的 await 替换成 yield。仍需正确处理子计算状态、恢复位置、唤醒、局部变量和清理。Rust 还需要满足其借用、固定地址和析构规则，MCFPP 不必因此照搬整套 Rust 类型系统。

### 3.2 本轮从其他语言和引擎得到的参考

| 参考 | 对本设计有用的部分 | 不应直接推导出的结论 |
| --- | --- | --- |
| Rust async / Future | 编译器生成具体状态类型，普通等待可以嵌套组合，执行器另行提供 | 所有计算都必须单独堆分配，或必须先公开 Coroutine |
| Tokio | join / select 可在同一任务内推进多个分支，spawn 是独立任务边界 | 每个分支必须 spawn，或所有任务句柄的丢弃规则都适合 MCFPP |
| Kotlin suspend | 可暂停函数与 continuation 可以组合，调度器支持可放在库中 | 必须让每次调用都向用户暴露一个 Future 对象 |
| C# async / await | 等待协议可扩展，Task、值式等待表示和异步序列可以区分 | await 必然换线程、跨帧，或每个调用都必须采用同一种分配方式 |
| Python asyncio | 单线程事件循环也可以提供协作式任务并发 | 没有并行就没有异步 |
| Unity coroutine | 游戏流程可以用可暂停执行表达，帧等待是明确的等待条件 | yield 的所有含义都应合并为“下一 tick” |
| Unity Awaitable | 引擎可以提供专用的下一帧等待，并对实例复用设置约束 | 所有 Awaitable 都天然支持任意次数重复等待 |
| Godot await | 信号和可暂停调用可以统一用于等待 | 必须向用户暴露通用可恢复栈对象 |
| Unreal 定时器 | 简单延时、周期调用和取消可以由专门库 API 承担 | 所有定时逻辑都必须创建完整任务 |

本轮最终选择了“调用创建待执行计算，await 或 spawn 推进”的方向，而不是每次普通暂停调用都立即执行的方向。上述比较用于说明取舍，不表示混合采用这些语言的全部行为。

## 4. 错误处理：确定采用 Result

### 4.1 已确认的主方案

业务失败通过 Result 表达。本文用以下逻辑结构说明：

~~~text
Result<T, E> = Ok(T) | Err(E)
~~~

函数的调用者取得结果值后，根据成功或失败分支继续处理。

采用这一方案的理由是：

- 错误可以沿显式控制流返回，不要求寻找或恢复运行时调用栈。
- 与 Minecraft 的命令返回、计分板状态和额外结果槽容易衔接。
- 同步函数、暂停函数和 generator 可以使用同一种业务错误值。
- 错误载荷、传播和处理位置可以进行静态分析。
- 编译器可以消除只为语言抽象存在的标签和包装，而保留真实需要的分支。

此决定替代早期文档中以 throw / catch 及隐式异常传播为主轴的方案。

### 4.2 逻辑类型不等于 NBT 包装

Result 不要求每次调用构造类似以下的真实对象：

~~~text
{ ok: true, value: ... }
~~~

编译器可以根据实际需要选择：

- 成功/失败标记和一个数值槽。
- 标记和 NBT 载荷路径。
- 直接跳转到成功或失败基本块。
- 对静态已知的分支完全折叠。

某个函数只可能成功，或者结果立即被分支消费时，不应为未使用的通用包装生成额外存储。

错误必须跨函数、跨 tick、存入容器或交给其他计算时，必要的错误状态和载荷仍需保留。

### 4.3 传播语法尚未定案

采用 Result 不自动意味着采用某个具体传播操作符。

例如下面仅是候选语法，**问号操作符尚未确认**：

~~~mcfpp
func process() -> Result<int, ProcessError> {
    var value = load()?;
    return Ok(value + 1);
}
~~~

如果之后采用类似问号的语法，其基本含义应是：

- Ok：取得载荷，继续执行。
- Err：从当前函数返回失败结果。

这可以降为分支和返回，不需要栈回溯。显式模式匹配、访问器、构造器和传播转换的最终写法仍需要单独规范。

不得把本例的 `Ok`、`Err`、`?` 或 `Result<T, E>` 的完整拼写当作已经落定的标准库 API。

### 4.4 Minecraft 命令结果与业务 Result

Minecraft 命令的 success 和数值 result，与语言的业务 Result 不应自动画等号。

- 数值为 0 不必然表示业务失败。
- 一个返回整数的操作可能把任意整数作为合法结果。
- 不同命令的 success 含义需要由包装 API 解释。
- 缺少 NBT 路径、选择器未找到目标或状态未发生变化，是否属于业务错误，由具体操作契约决定。

需要由命令适配层把底层结果转换为约定的 Ok / Err。普通世界状态查询和修改不因为使用 Result 就自动获得事务回滚。

### 4.5 与暂停、generator 和取消的关系

~~~mcfpp
suspend<Game> func buildRegion() -> Result<int, BuildError> {
    // 主体可以跨 tick 执行，最终返回业务 Result。
}

// 位于可暂停上下文中：
var result = buildRegion().await();
~~~

await 负责等待完成；取得的 result 本身仍需处理。await 不应自动把 Err 改成另一套隐式异常。

generator 也可以选择产出 Result。若其产出类型是 Result，`Yielded(Err(error))` 是一项产出，和 Done 不同。它是否在产出错误后结束，应由生成器函数体或后续约定决定，不应由一个普通错误值自动触发。

取消意味着停止推进计算，和某个业务操作主动返回 Err 是不同事件。取消最终如何在句柄、等待结果或清理 API 中表达尚未决定。

以下细节不因本轮选择 Result 而自动确定：未处理 Result 的诊断、根任务错误报告、程序错误/panic 的处理、清理失败的组合、错误类型转换，以及是否保留任何辅助异常语法。

## 5. 暂停函数与 Game 调度器

### 5.1 声明和调用

已确认的声明方向：

~~~mcfpp
suspend<Game> func test() {
    var value = test2().await();
    use(value);
}
~~~

Game 表示负责该计算推进的调度器。它是内置的，以游戏 tick 为调度轮次。

若 test2 是函数，应写 `test2().await()`。若某个变量已经保存了待执行计算，则对该变量调用 `.await()`。

暂停函数声明中的结果类型描述最终完成值；调用表达式取得的是可等待计算。具体计算类型可以由编译器推断，不必要求用户频繁书写其完整类型名。

### 5.2 调用创建计算，主体保持惰性

~~~mcfpp
var work = test2();
~~~

这一行创建待执行计算，不执行 test2 的函数体。保存、传递或组合这个计算值，本身也不自动运行主体。

此前的补充建议是沿用普通调用的参数求值规则：

~~~mcfpp
var work = process(readScore());
~~~

readScore 在构造计算时求值，process 的主体以后才执行。不要把“主体惰性”扩展为“任意实参表达式也自动延迟”。具体参数求值顺序仍应遵循 MCFPP 的普通调用规范。

从未被执行者推进的计算不会运行主体。是否要求对未使用计算报错、警告或显式丢弃，尚未决定。

### 5.3 await 与 spawn 的职责

~~~mcfpp
var work = test2();
var value = work.await();

var handle = Game.spawn(test2());
var another = handle.await();
~~~

- 直接 await 待执行子计算：在当前任务内推进它，必要时连同调用者一起等待。
- spawn：提交为独立调度的根任务，并取得一个句柄。
- 等待已启动任务的句柄：观察该任务的完成，不等同于把它变回当前任务内的普通子调用。

直接 await 不要求为每个子计算分配独立任务记录。

下面两段不能无条件互换：

~~~mcfpp
var handle = Game.spawn(work());
handle.await();
~~~

~~~mcfpp
work().await();
~~~

它们可能具有不同的执行顺序、调度调用和取消关系。即使句柄没有逃逸，也不能仅凭这一点消除 spawn。

### 5.4 await 不自动跨 tick

遇到 await 时，先立即尝试推进被等待的计算：

1. 已经完成：取得结果，继续执行当前函数。
2. 可以继续运行：推进它，直到完成或遇到未就绪等待。
3. 未能完成：保存必要状态，暂停当前流程。
4. 后续恢复并完成：父计算立即取得结果并继续，不再人为多等一个 tick。

示例：

~~~mcfpp
suspend<Game> func child() -> int {
    print("child:start");
    Game.nextTick().await();
    return 42;
}

suspend<Game> func parent() {
    var work = child();
    print("parent");
    var value = work.await();
    print(value);
}
~~~

假设 parent 在 tick N 开始被推进：

| 时间 | 行为 |
| --- | --- |
| N | 创建 work，主体尚未执行 |
| N | 输出 parent |
| N | await 推进 child，输出 child:start |
| N | 遇到下一 tick 等待，父子计算暂停 |
| N+1 | child 返回 42，parent 随即输出 42 |

若某个暂停函数始终立即完成，对它的 await 不会自动让出执行权。因此不能依赖任意 await 来切分长循环。

### 5.5 一 tick 一轮的含义

已确认的是：一轮内任务可连续执行，直到完成或遇到未就绪等待；不是“每个任务每 tick 最多经过一个 await”。

此前曾把“当前任务又遇到 await”与“另一个独立任务被唤醒”混为一谈。已经澄清：

- 当前任务直接 await 子计算，应立即推进子计算。
- 其他独立任务被唤醒，属于就绪队列管理。

讨论中给出的调度器实现展开是：

1. 在本轮准备阶段使到期的 tick 等待变为就绪。
2. 取出就绪任务，推进到暂停或完成。
3. 轮中新增的就绪任务加入本轮队尾。
4. 当前任务交回控制后，继续处理本轮新增任务。
5. 明确要求下一 tick 的操作留到下一 tick 才允许恢复。

这一队列方案不在唤醒通知里递归执行别的任务，以免产生重入和不可控的恢复链。新增独立任务的具体排队规则属于设计展开；同步入口在调度阶段前后 spawn 的首次推进时间、预算上限等没有单独逐项定案。

每 tick 有调度入口，不等于每 tick 扫描全部等待中的计算。定时和事件等待可以在满足条件时才进入就绪队列。

### 5.6 明确的跨 tick 示例

~~~mcfpp
suspend<Game> func count20(counter as ScoreRef) -> int {
    for (var i = 0; i < 20; i++) {
        Game.nextTick().await();
        counter.add(1);
    }
    return 20;
}
~~~

这里的 add 是说明性 API。若初次推进发生在 T，且 Game 按下一 tick 契约恢复，则在 T+1 至 T+20 各增加一次，并在第 20 次增加后完成。

把等待放在第一次增加之前，明确了开始时刻；最后一次增加后没有多余等待。

必须区分“某条件已在下一 tick 就绪”和“预算调度器保证下一 tick 实际执行”。若以后支持可延后执行的调度器，需要明确 API 是严格的下一 tick 阶段恢复，还是不早于下一 tick。不能悄悄改变契约。

## 6. 调度协议与库的扩展边界

### 6.1 语言能力与实现位置分开

本轮讨论倾向开放暂停/恢复和等待协议，不把可等待操作固定为编译器认识的有限名单。

可以划分为：

| 层次 | 责任 |
| --- | --- |
| 语言与编译器 | 识别暂停位置、保存状态、恢复执行、取得结果 |
| 调度器 | 管理根任务的就绪和执行顺序 |
| 时间/事件适配层 | 提供 tick 到期、事件触发或条件变化的通知 |
| 库组合器 | 组合计算，例如顺序等待、join、or、超时 |

接口可以定义在核心库中，由编译器遵守契约；不必为每项扩展增加关键字。第一版只有一个默认 Game 实现，也不意味着未来扩展必须改写编译器白名单。

以下具体设计仍未选择：采用 poll/wake、continuation 风格还是其他等价接口；接口名；scheduler 是类型参数、编译期对象参数还是其他标识形式；自定义调度器的完整公开 API。

### 6.2 保留静态优化空间

Game 在编译期已知时，不必给每个实例保存同样的 scheduler 描述，也不必强制动态分派。

如果用户提供了自定义调度器，编译器必须保留其可观察的执行规则。不能为了生成更少命令，绕开它规定的顺序、预算或观测行为。

同一个调度器内的嵌套等待可以融合。跨调度器等待如何提交、在哪里恢复父计算，尚需具体接口规则；不能隐式让父函数换到另一个 scheduler。

### 6.3 唤醒不是完成

唤醒表示某个计算可能继续推进，不必然表示它已完成。恢复后仍可能得到 Pending。

等待登记需要与当前有效实例和当前等待关系对应，避免重复登记、过期通知、取消后的唤醒和槽复用后唤醒错误实例。实例编号、代数或等待版本是可用的实现技术，不要求统一暴露到语言层。

Minecraft 没有直接通知的条件仍需检测，或要求修改通过可观测 API 进行。例如任意外部命令都能修改的计分板，不能仅靠一个抽象的 wake 接口就自动获得变化通知。

## 7. 计算组合：or 与其他库操作

### 7.1 组合能力不等于独立任务对象

~~~mcfpp
suspend<Game> func runWave() -> int {
    var location = chooseLocation().await();
    var enemies = createEnemies(location).await();
    return waitUntilDefeated(enemies).await();
}
~~~

后续计算依赖前一个结果，已经体现一般性的顺序组合。

不需要为三个子计算分别 spawn。一个根实例可以保留当前阶段、当前子计算状态及后续仍需使用的数据。

“不为每个子计算独立分配任务记录”是优化方向；“语言禁止保存或传递计算值”则是表达能力限制。不能用前者作为后者的理由。

运行时可以选择已经编译进数据包的不同计算代码，也可以创建多个实例，不要求运行时生成新的 mcfunction 文本。

异构集合、动态任务队列和运行时构造的任意长度组合可能需要标签、实例存储和分派。未能完全消除这些成本，不应成为悄悄改变程序含义的理由。

### 7.2 or 的已确认语义

~~~mcfpp
var value = or(first(), second()).await();
~~~

or 创建一个组合计算，await 推进该组合。按本轮解释，具体规则是：

1. 每次推进按参数从左到右检查。
2. 遇到第一个完成的分支立即选择它。
3. 左侧 Pending、右侧完成时，右侧获胜，不为了左侧优先而继续等待。
4. 多个分支在检查时都已就绪，靠前的参数优先。
5. 其余分支不再推进，清理属于这些分支的等待登记和运行状态。
6. 已执行的游戏命令保留，不自动回滚。
7. 组合只返回胜方的值，不返回分支编号或败方计算。

“首先完成”在这一展开中表示首先被组合器观察到完成，不要求保存绝对完成时间戳并追溯比较。

第一个分支立即完成时，后续分支的函数体可能完全未执行。但这不自动消除构造实参时必须发生的普通表达式求值。

若分支结果是 Result，Err 也是一次完成。等待“首个成功结果”属于另一种组合，不是这里的 or。

### 7.3 分支暂停与整体暂停

~~~mcfpp
or(waitOneTick(), immediate()).await();
~~~

如果左侧要等下一 tick、右侧立即完成，则整个组合可以在当前 tick 返回右侧结果，并停止左侧。

若要求整个流程先跨 tick：

~~~mcfpp
Game.nextTick().await();
var value = or(first(), second()).await();
~~~

某个分支遇到等待，不会强制所有其他分支一起停止推进。

### 7.4 已启动任务的句柄不是普通子计算

本轮 or 的讨论以接管尚未独立启动的子计算为基础。

对已独立 spawn 的任务，“不再等待它”与“停止它执行”不同。Tokio 的 JoinHandle 被丢弃后任务可以继续运行，但 MCFPP 没有因此选定相同规则。

是否允许 or 接受任务句柄、是否取得取消权限、败方已完成或共享时如何处理，都需要另外规定。不能因为句柄可以 await，就默认复制冷计算的所有取消行为。

### 7.5 其他组合器的讨论状态

| 操作 | 讨论方向 | 状态 |
| --- | --- | --- |
| 顺序 await | 在一个流程内依次推进计算 | 已确定基本语义 |
| or | 第一个被观察到完成的分支获胜，参数顺位优先 | 已确认 |
| join | 等待全部分支，取得各自结果 | 候选库 API |
| tryJoin | 全部成功，或在某个失败时提前结束 | 候选库 API，需结合 Result |
| timeout | 在操作完成与时间条件之间选择 | 候选库 API |
| chain / then | 可以用可组合计算表达；如另设 API，需要规定是否展开嵌套计算 | 没有确定额外公开语法 |

返回类型是否要求统一 T、是否允许使用已有联合类型承载不同分支结果，以及具体函数名、方法链写法，都未完整定案。

## 8. Generator：按次产出并明确完成

### 8.1 已确认的对外协议

yield 只允许出现在 generator 中。

调用者通过同一个实例反复取值：

~~~text
g.await() → Yielded(value) | Done
~~~

- 遇到 yield value：本次取值完成，得到 Yielded(value)。
- 遇到未就绪的内部 await：本次取值继续等待，不向用户返回 Pending。
- 遇到 return 或函数末尾：得到 Done。
- 不携带独立最终返回值，因此 generator 不使用 return value。

Done 必须能与普通产出区分。如果产出类型允许空值，产出空值仍然是 Yielded(空值)，不能用空值本身充当完成标记。

### 8.2 按需推进的设计展开

generator 在产出后保存恢复位置，等调用者下一次取值时从 yield 后继续。

没有取值请求时不自动在后台继续生产。这样不需要为了保留无人消费的中间值而自动生成缓冲队列。

如果未来需要生产者每 tick 自主运行、消费者可以更慢地消费，那会引入独立任务、队列以及容量/溢出策略，不是当前按需 generator 隐含提供的能力。

### 8.3 yield 与 tick 分开

yield 产出数据，不强制跨 tick。

~~~mcfpp
generator<Game> func values() -> int {
    yield 10;
    Game.nextTick().await();
    yield 20;
    return;
}
~~~

在可暂停上下文中：

~~~mcfpp
var g = values();
var first = g.await();
var second = g.await();
var end = g.await();
~~~

| 请求 | 结果 |
| --- | --- |
| 第一次 | 当前推进中得到 Yielded(10) |
| 第二次 | 等下一 tick 后得到 Yielded(20) |
| 第三次 | 继续执行到 return，立即得到 Done |

第三次请求不额外跨 tick。最后一次 yield 仍然只表示产出，完成通过后续推进明确报告。

上例采用 `generator<Game> func ... -> int` 表示产出 int 的生成器函数；完整声明 grammar 和类型名称仍以实现时的统一语法规范为准。

### 8.4 完成后的等待

讨论中推荐的补充规则是：完成状态保持稳定，后续 g.await() 立即返回 Done，不重新执行函数体。

这条是设计展开，用户明确确认的是“g.await() 取值”和“无独立最终值，只有 Done”。完整规范应正式确认完成后行为和实例释放之间的关系，避免已释放槽被旧句柄错误访问。

### 8.5 一次返回与多次产出的区别

如果只需要一个阶段在下一 tick 把一个结果交给后续阶段，用暂停函数即可：

~~~mcfpp
suspend<Game> func stage() -> int {
    var value = compute();
    Game.nextTick().await();
    return value;
}
~~~

只有同一次运行需要多次产出并从产出位置继续，才需要 generator。

普通计算的 await 取得一次最终结果；generator 的 await 取得下一项或 Done。它们都可使用后缀语法，但接收者类型决定结果协议。

### 8.6 尚未讨论完的 generator 边界

- 同一实例同时存在多个未完成取值请求时如何诊断或串行化。
- 复制一个 generator 值是否共享同一运行实例，以及复制、移动、关闭接口。
- or 是否直接接受 generator，以及败方取消的是一次取值请求还是整个 generator。
- 消费者提前停止取值时，等待登记、捕获和外部资源如何释放。
- generator 中业务错误是作为产出、终止错误还是其他约定；不能仅凭选定 Result 自动推断。

这些是 API 和生命周期细节，不改变本轮确定的 yield / g.await() / Done 主协议。

## 9. 执行状态与 mcfunction 分段

### 9.1 编译为可恢复状态机

实现方向是：

~~~text
源代码
  → TypedIR 控制流图
  → 标记 yield / await
  → 分析跨暂停点活跃的数据
  → 分配实例状态和恢复入口
  → 生成 mcfunction 片段
~~~

应在 IR 上转换，不在已经生成的命令文本上机械切割。

yield 一定结束本次取值；await 是潜在暂停点，只有未就绪时才把控制权交回。同步就绪路径可以连续执行，相关分支和状态写入可以优化。

片段边界还要表达分支、循环、恢复和必要清理。源代码中一个循环内的 yield 会反复经过同一组状态，不需要按执行次数生成新文件。

### 9.2 实例状态保存什么

| 数据 | 保存条件和策略 |
| --- | --- |
| 恢复位置 pc | 记录下一次从哪个入口继续；有时可以用已知恢复入口代替显式编号 |
| 参数与捕获 | 仅保留后续需要的内容；常量和固定目标可消除运行时表示 |
| 局部变量 | 跨暂停点仍需使用才保留 |
| 子计算 | await 尚未完成时保留原来的子实例状态 |
| 等待关系 | 在确实存在挂起请求时保存唤醒和结果交付所需信息 |
| 产出/返回值 | 按调用者的使用和存活期安排结果槽，可消除无用包装 |
| 生命周期信息 | 动态复用、取消或引用逃逸要求时保留实例识别等元数据 |

这是逻辑布局，不意味着统一 NBT compound，也不意味着每个实例必须拥有表中全部字段。

### 9.3 活跃变量与世界状态

~~~mcfpp
var old = counter.get();
Game.nextTick().await();
yield old;
~~~

old 是暂停前快照，恢复后不能改成重新读取 counter。

~~~mcfpp
var temporary = calculate();
print(temporary);
Game.nextTick().await();
yield 1;
~~~

如果 temporary 后面不再使用，就不必为暂停保留它。

活跃性还应覆盖清理路径和存活引用。引用仍指向某位置时，不能因为直接变量名已经不用就提前复用其存储。

暂停期间其他任务或命令可能修改世界。优化器应保留已保存局部快照的事实，同时失效那些依赖可变世界状态、不能跨暂停保持的假设。

### 9.4 直接保留持久槽，避免整体搬运

- 固定实例的整数和布尔状态可直接保存在独立计分板槽中。
- NBT 数据可直接位于实例拥有的持久路径，原地修改。
- 静态已知 ScoreRef / NBTRef 目标可以直接写进命令。
- 动态引用描述若恢复后还要使用，就必须保留。
- 不要求每次暂停把全部计分板局部变量写入 NBT，再在恢复时全部读出。

不能一律断言计分板对所有动态场景都更快。动态寻址、宏调用、分派和存储布局的组合成本需要实测。

### 9.5 多实例和嵌套

~~~mcfpp
var a = numbers(10);
var b = numbers(100);
~~~

a 与 b 可以共享恢复代码，但必须隔离各自的恢复位置和局部状态。

静态可确定的活跃实例可以使用固定槽；动态数量实例需要实例池或其他运行时分配。

顺序 await 的子状态可嵌入父状态；or 等组合中的多个活跃分支需要各自状态。没有必要完整保存原来的 mcfunction 调用链。

非尾递归暂停或运行时增长的计算结构可能需要动态历史数据，不能保证所有情况都能压成一个固定大小记录。

### 9.6 创建一次，恢复同一个等待

~~~mcfpp
var value = operation().await();
~~~

编译器需要区分：

1. operation 的构造阶段。
2. 已有 operation 的推进/等待阶段。
3. 完成后的后续代码。

恢复时继续推进已有子计算，不能重新执行 operation 的构造表达式或已经发生的副作用。

同理，等待下一 tick 时应保存已创建的等待关系或截止条件。恢复时若重新解释为“再等下一个 tick”，可能永远无法完成。

### 9.7 产出和 Pending 的内部 ABI

生成器恢复入口可以逻辑上返回：

~~~text
Pending | Yielded(value) | Done
~~~

一种说明性的编码是：

| 数值标签 | 含义 |
| --- | --- |
| 0 | Pending |
| 1 | Yielded |
| 2 | Done |

标签与载荷分别传递。此处编码是示例，不是固定公共 ABI，更不能把数字标签直接等同于业务 Result 或 Minecraft 命令 success。

yield 不自动把 generator 加入下一 tick 的运行队列。它完成当前取值请求，等下一次 g.await() 再继续。只有真正挂起的内部 await 才需要相应唤醒。

## 10. 手工 mcfunction 展开示例

### 10.1 源程序和适用范围

~~~mcfpp
generator<Game> func numbers(start as int) -> int {
    var n = start;
    yield n;
    n += 1;
    yield n;
    return;
}
~~~

以下示例针对一个固定实例 g0、有效初始化和无重入情形，使用 Java 1.20.3+ 的 return run。它演示数据布局，不是通用实例池、完整调度器或当前编译器的实际输出。

状态含义：

| pc | 下次恢复的行为 |
| --- | --- |
| 0 | 第一次产出 n |
| 1 | n 加一，然后第二次产出 |
| 2 | 返回 Done |

pc、n 都使用计分板；out 是一次结果交付槽。

### 10.2 初始化与构造

初始化目标，只需执行一次：

~~~mcfunction
scoreboard objectives add rt dummy
~~~

构造固定实例，假设调用者已经把运行时实参放入 `#arg0 rt`：

~~~mcfunction
scoreboard players operation #g0.n rt = #arg0 rt
scoreboard players set #g0.pc rt 0
~~~

这里把实参保存槽与 n 合并。n = start 的纯复制不需要在首次推进时重复执行；一般的变量初始化副作用仍必须留在函数体真正开始执行的位置。

### 10.3 恢复入口 demo:g/resume

~~~mcfunction
execute if score #g0.pc rt matches 0 run return run function demo:g/state0
execute if score #g0.pc rt matches 1 run return run function demo:g/state1
return 2
~~~

在此示例的有效状态集合内，剩余状态只有 pc = 2。

选择片段后必须退出恢复入口，否则 state0 修改 pc 后可能又匹配 state1，导致同一次取值越过两个 yield。return run 用于传递选中片段的返回值并结束入口。

### 10.4 第一片段 demo:g/state0

~~~mcfunction
scoreboard players operation #g0.out rt = #g0.n rt
scoreboard players set #g0.pc rt 1
return 1
~~~

### 10.5 第二片段 demo:g/state1

~~~mcfunction
scoreboard players add #g0.n rt 1
scoreboard players operation #g0.out rt = #g0.n rt
scoreboard players set #g0.pc rt 2
return 1
~~~

第二次产出后，下一次取值才观察到 Done。此例在最后一个 yield 后没有其他工作，所以恢复入口可直接处理完成。

### 10.6 调用者取得一次结果

~~~mcfunction
execute store result score #step rt run function demo:g/resume
execute if score #step rt matches 1 run scoreboard players operation #caller.value rt = #g0.out rt
~~~

调用者必须根据 step 区分产出和结束；Done 时 out 中的旧值没有意义。

如果前一次取出的值还会在后续使用，需要保存在调用者自己的位置，不能让后一次 g.await() 覆盖它。编译器证明存活期不冲突时，可以继续合并结果槽。

若实参为 10，连续三次取值对应 Yielded(10)、Yielded(11)、Done。示例没有内部未就绪等待，所以不会返回 Pending，也没有 NBT 状态搬运。

### 10.7 加入异步等待后的变化

存在未就绪 await 时，需要增加等待状态及有效唤醒路径。收到 Pending 的调用者也要保留自己的恢复位置，并在条件就绪后继续同一次取值请求。

通用实现还必须处理实例隔离、取消后的通知失效、结果生命周期和无效实例诊断。这些没有被上面的单实例片段实现。

## 11. Lambda：最终采用 Kotlin 风格

> 2026-10-22 后续决定：当前同步 lambda 的类型标注统一使用冒号，函数类型写 `(int, int) -> int`、`() -> void`，不带 `func` 前缀；具名声明仍使用 `func`。引用捕获、普通 lambda 与 MNI `REF` 互操作、嵌套回调 `REF` forwarding 暂缓。可运行语法与当前支持范围见 [同步 lambda 教程](lambda/tutorial.md)。本节后文保留当时的讨论语境，不作为这些后续决定的覆盖。

### 11.1 已确认的形状调整

早期候选是 `(参数) { 函数体 }`，尾 lambda 曾出现 `fold(0) (sum, item) { ... }`。

用户明确指出两个括号相连不美观，要求照搬 Kotlin 形式。因此最终改为：

~~~mcfpp
var add = { a as int, b as int -> a + b };
~~~

参数在花括号内部，箭头分隔参数列表与主体。MCFPP 的类型标注继续使用 as，不因为采用 Kotlin 的 lambda 外形而改成冒号。

### 11.2 上下文推导和尾 lambda

~~~mcfpp
items.map { item -> item + 1 };
items.fold(0) { sum, item -> sum + item };
~~~

参数类型在目标类型明确时可以省略。最后一个实参是 lambda 时可写在调用括号外；它是唯一实参时，可以省略整个调用括号。

按照“照搬 Kotlin”展开的便利规则还包括：目标明确为单参数时，允许隐式 it。

~~~mcfpp
items.filter { it > 0 };
~~~

隐式 it 是本轮 Kotlin 风格的展开示例，没有另一次单独问答。最终 grammar 和名称绑定规则应明确嵌套作用域及目标类型不明时的诊断。

无参数 lambda：

~~~mcfpp
var action = {
    print("Hello");
};
~~~

### 11.3 末尾表达式隐式返回

用户明确允许末尾表达式隐式返回。

~~~mcfpp
var calculate = { value as int ->
    var doubled = value * 2;
    doubled + 1
};
~~~

该规则用于普通 lambda 和暂停型 lambda。是否同时扩展普通具名函数块体，不在本轮确认范围内。

期望返回 void 时对末尾表达式的处理、分号与尾表达式的精确语法关系，以及多分支返回类型如何推导，需要落入统一 grammar 和类型检查规范。

显式 return 只退出当前 lambda 是前面提出的简化建议，但没有单独确认。不能因“照搬 Kotlin 语法”就自动引入 Kotlin 的非局部 return、标签 return 或依赖 inline 的控制流规则。

### 11.4 修饰符必须显式书写

用户明确不允许省略 suspend / generator 修饰符，即使目标参数类型已经明确。

~~~mcfpp
var later = suspend<Game> { value as int ->
    Game.nextTick().await();
    value + 1
};

var source = generator<Game> {
    yield 1;
    Game.nextTick().await();
    yield 2;
};
~~~

- 普通 lambda 是普通函数值。
- `suspend<Game>` lambda 是暂停函数值。
- `generator<Game>` lambda 是生成器函数值。
- 创建这些函数值与调用它们不同。
- 调用 later(...) 才创建待执行计算。
- 调用 source() 才创建 generator 实例。

不能仅因为 lambda 体中出现 await 或 yield，就悄悄推导并补上缺失的类别修饰符。

generator lambda 不因允许末尾表达式返回就获得独立最终返回值。其数据通过 yield 产出，函数结束得到 Done。

### 11.5 不额外堆叠语法

本轮收束方向是：

- 普通、暂停型、生成器 lambda 共用花括号内参数的形状。
- 暂停和产出能力通过已有修饰符表达。
- 不另外引入一个 task 块与一套不同的 lambda 调用规则。
- 不为尾 lambda 开放任意位置实参省略括号、任意中缀调用等扩展。

带显式修饰符的 lambda 如何作为尾实参书写，仍需 parser 规则确认；普通实参形式始终可以作为说明：

~~~mcfpp
register(suspend<Game> {
    Game.nextTick().await();
    print("Ready");
});
~~~

这里只假设 register 接受暂停函数值，不表示 register 自动调用、spawn 或保存它；这些行为由接收 API 的契约决定。

## 12. 闭包捕获和函数值：保留讨论边界

### 12.1 语法收束不等于捕获规则全部定案

用户希望 lambda 简洁，旧稿里复杂的 capture 子句、借用修饰和多种 task 表达式不应直接成为首版要求。

此前建议的方向是：

- 自由局部值按明确的值复制规则捕获。
- 引用值捕获的是引用描述，仍指向同一个目标。
- 创建 lambda 时获取捕获环境。
- 需要共享修改时利用明确的引用类型，而不是自动为所有可变局部变量建立共享盒。

这些是建议，尚未逐项确认。尤其不能把“照搬 Kotlin 的 lambda 语法”解释成“照搬 Kotlin 对可变局部变量的捕获实现”。

### 12.2 与 ScoreRef、NBTRef 的关系

前一份类型记录已经区分普通值、引用描述和引用目标。

如果捕获的是整数快照，之后外部位置的修改不应改变该快照。

如果捕获的是 ScoreRef 或 NBTRef 的描述，之后读取仍访问同一个目标，目标内容可能已经变化。静态已知描述可以编译进命令，不要求创建真实 NBT 引用对象。

对象值的复制深度、可变捕获、临时引用能否跨 tick、引用失效的 Result 表达，都仍依赖此前尚未完成的类型和生命周期规则。

### 12.3 闭包的物理表示

逻辑上可以理解为代码目标加捕获环境，但不是所有 lambda 都必须拥有真实运行时闭包对象。

| 使用方式 | 可考虑的实现 |
| --- | --- |
| 已知的无捕获 lambda | 直接调用或内联 |
| 未逃逸的值捕获 | 常量、局部槽或调用者已有位置 |
| 逃逸、返回或跨 tick 保存 | 放入生命周期足够长的环境 |
| 运行时选择不同 lambda | 标签/目标信息和相应捕获数据 |
| 同一个暂停 lambda 的多次调用 | 共享函数代码，分别保存各次计算的运行状态 |

闭包仍依赖某个原局部槽时，该槽不能被提前释放或复用。是否通过转移、复制、受限引用或其他所有权规则解决，未最终确定。

### 12.4 函数类型和效果

函数值的类型应保留参数、结果和执行类别。普通、暂停和生成器调用不能在类型擦除或赋值时无条件互换。

函数参数逆变、结果协变属于前一份记录里的应用建议，需要结合完整函数类型和效果兼容规则落实。

普通 lambda 的内联不应成为偷偷改变捕获方式、返回范围或暂停能力的理由。具体 inline API 是否有额外规则，尚未讨论。

## 13. 三类能力的组合边界

### 13.1 Result 描述业务成败，await 描述等待

~~~text
待执行计算的输出为 Result<T, E>
    await
得到 Result<T, E>
    显式处理，或使用将来确定的传播语法
得到业务值或返回业务错误
~~~

不要求在每个 await 外再构造另一套异常框架。

### 13.2 Generator 的完成和业务失败分开

~~~text
Yielded(Ok(value))   一项成功结果
Yielded(Err(error))  一项业务失败结果
Done                序列结束
Pending             内部等待，用户这次 await 尚未结束
~~~

这个例子只说明概念可以组合，不强制所有 generator 都产出 Result，也不预先决定 generator 自身的终止错误协议。

### 13.3 or 的选择与 Result 分开

对于输出 Result 的分支，先被观察到完成的 Err 可以赢得 or。此时返回的是该 Err，其他分支停止。

如果业务想在一个来源失败后继续等待另一个来源成功，需要另外定义组合行为，不能隐式改变 or 的“首次完成”规则。

### 13.4 Lambda 不隐含启动任务

把一个 suspend lambda 传给库，首先只是传递函数值。接收方是否调用、何时调用、是否独立 spawn，由库接口决定。

把普通 lambda 放进暂停函数，也不会自动让普通 lambda 具备 await 能力；已确认要求类别修饰符显式书写。

## 14. 与早期提案的差异及失效部分

| 早期说法或候选 | 本轮记录 |
| --- | --- |
| throw / catch 作为主要错误体系 | 主方案已改为 Result |
| 调用暂停函数就立即执行主体 | 调用先创建待执行计算 |
| 每经过 await 就等下一 tick | await 只有遇到未就绪等待才暂停 |
| 等待子函数必然创建独立任务 | 子计算可以在同一根任务内推进 |
| 不保存通用任务对象，所以不提供一般性组合 | 对象表示是实现选择，不能据此限制语言组合能力 |
| 任意任务对象都能被保证完全消除 | 可观察语义和动态使用决定必要成本 |
| race 采用随机或公平竞争 | 采用 or，按参数顺位优先选择首先观察到完成的分支 |
| 败方作为结果交回 | 当前 or 不返回败方，只返回胜方的值 |
| yield 同时表示值交付与下一 tick | yield 只用于 generator 产出；跨 tick 等待单独表达 |
| generator 使用 next().await() | 用户明确选择 g.await() |
| generator 带独立最终返回值 | 用户明确选择 Done，无独立最终值 |
| lambda 为 (参数) { 主体 } | 改为 Kotlin 风格 { 参数 -> 主体 } |
| fold(0) (sum, item) { ... } | 改为 fold(0) { sum, item -> ... } |
| lambda 必须显式 return | 允许末尾表达式隐式返回 |
| 根据目标类型省略暂停/生成器修饰符 | 用户明确不允许省略 |

旧稿里以下内容也不能直接导入已确认规范：只支持固定键任务、禁止计算值放入任意容器或 any、统一一次消费规则、未消费计划一律报错、默认取消/分离行为、finally 的具体限制，以及复杂 capture 子句。

相关旧文档保留为历史材料：

- [异常效果早期设计](exception-effects-design.md)
- [异常命令展开早期示例](exception-effects-examples.md)
- [无栈效果与任务早期设计](stackless-effects-and-tasks-design.md)
- [异常、任务、lambda 统一早期提案](exceptions-tasks-lambdas-design.md)

这些文档与本文有冲突时，以用户本轮最新明确选择为准；本文的待定项仍保持待定。

## 15. 尚未细化的接口和实现事项

异步主线讨论已经收束。下面记录的是没有展开到底的边界，不把它们冒充为已经决定的细则，也不要求重新选择已确认的方向。

| 范围 | 未定事项 |
| --- | --- |
| Result 类型 | `Result<T>` 还是 `Result<T, E>`、默认错误类型、不可变性、方差、构造与匹配接口 |
| Result 传播 | 问号操作符、错误转换、未处理结果诊断、根任务报告 |
| 程序错误与清理 | panic/断言、取消的表示、清理语法、清理失败及清理能否暂停 |
| 计算值 | 是否一次消费、重复 await、复制/移动/共享、是否有可观察身份 |
| 任务句柄 | 多等待者、完成结果保留、丢弃/取消/分离、任务归属和容量失败 |
| 调度器协议 | poll/wake 或 continuation、公开接口、Game 的标识形式、自定义 scheduler |
| 调度时序 | 调度阶段外 spawn、重入保护、同 tick 队列预算、严格下一 tick 保证 |
| 时间与事件 | nextTick 以外 API、逻辑时钟与游戏 tick、缺少通知条件的检测策略 |
| 跨调度器 | 提交、结果交付、父计算恢复域及显式转换 |
| or 及其他组合 | 返回类型统一、句柄和 generator 参数、资源取消契约、join/timeout 的具体 API |
| Generator 生命周期 | 完成后行为的正式定稿、同时取值、提前关闭、复制、取值取消与实例取消 |
| Lambda 类型与控制流 | 函数类型拼写、显式 return 范围、标签、void 上下文、尾表达式精确规则 |
| 闭包 | 捕获深度、可变捕获、引用生命周期、环境释放、泛型/效果兼容 |
| Minecraft 上下文 | 跨 tick 的执行者、位置、维度等是否显式携带及如何恢复 |
| 加载与持久化 | reload 后活动计算的生命周期、版本变化下能否恢复、是否提供专门持久化工作流 |
| 性能 | 静态槽、动态实例池、分派、宏和 NBT 的实测成本；严格成本诊断 |

公开完整代数效果系统、可复制 continuation 或通用栈恢复，不是实现当前已确认能力的前置条件，也没有在本轮被确定为必须实现的功能。

## 16. 编译器落点与后续验收依据

### 16.1 已观察到的现有基础

讨论时检查了以下文件：

- [TypedIR.kt](../src/main/kotlin/top/mcfpp/analysis/TypedIR.kt)：已有 BasicBlock、Instruction 和 Jump / Branch / Return 等终结形式。
- [PrimitiveCompiler.kt](../src/main/kotlin/top/mcfpp/analysis/PrimitiveCompiler.kt)：后端已经把基本块生成为独立 mcfunction；普通调用路径仍有保存计分板值到 NBT、入栈和出栈的逻辑。

这些代码是可复用的实现基础，不表示已支持本文的 suspend、generator 或 lambda。工作区实现可能继续变化，落地前需要重新检查。

### 16.2 建议的实现顺序

1. 明确 Result 的类型与分支表示，区分业务错误和底层命令返回。
2. 定义可等待计算、generator 取值结果和状态机内部返回协议。
3. 在 IR 中表达 yield、潜在暂停和恢复边。
4. 完成跨暂停点活跃变量分析及世界状态事实失效规则。
5. 为固定实例生成独立计分板/NBT 槽，接入 Game 和明确的下一 tick 等待。
6. 实现父子计算连续推进、or 及取消登记清理。
7. 支持一般计算值和动态实例的正确基线，再逐步消除包装与冗余存储。
8. 接入最终 lambda grammar、类型推导和明确的捕获规则。
9. 对已确认语义做回归验证，再测量命令数量、NBT 操作、存储占用和数据包代码尺寸。

优化必须在保持行为正确的实现上进行。若提供“严格无某类动态成本”的模式，应通过明确诊断限制该模式，不能让普通程序在优化失败时悄悄改变语义。

### 16.3 应覆盖的语义场景

| 场景 | 预期或验证点 |
| --- | --- |
| 仅创建暂停计算 | 主体副作用尚未发生 |
| await 立即完成的子计算 | 当前 tick 直接继续 |
| 子计算真正等待下一 tick | 保留状态，恢复后父计算立即接续 |
| 恢复正在等待的操作 | 不重复构造、不重复副作用、不重新推迟截止时间 |
| 20 次 tick 增加 | 在约定的 20 个 tick 增加，结束后无多余等待 |
| or 两个分支都就绪 | 参数靠前者优先 |
| or 左侧 Pending、右侧完成 | 右侧获胜 |
| or 败方已修改世界 | 停止后续推进，保留已经造成的效果 |
| or 第一分支立即完成 | 后续函数体可以从未执行 |
| generator 连续 yield | 每次 g.await() 只交付一项 |
| generator 内部 await | 调用者继续等待，不得到 Pending 作为普通值 |
| 最后一次 yield 后结束 | 下一次请求报告 Done，不额外跨 tick |
| 多个 generator 实例交错 | 各自局部数据和恢复位置互不覆盖 |
| 产出值仍被调用者使用 | 后续推进不能覆盖先前已取得的值 |
| 暂停前读取世界快照 | 恢复后仍使用该快照 |
| 暂停期间世界改变 | 后续新的世界读取不能错误使用旧事实 |
| 动态实例取消后晚到通知 | 不恢复已失效或被复用的实例 |
| Result 的 Err 经过 await | 得到业务失败值，不隐式换成 throw |
| Kotlin 风格尾 lambda | 不再要求两个相邻参数括号 |
| suspend / generator lambda 缺少修饰符 | 不自动通过目标类型补齐类别 |
| generator lambda 试图最终返回值 | 不符合仅 Done 的完成协议 |

涉及“完成后反复 Done”、同时取值和显式 return 范围的用例，应在对应补充规则正式确定后加入确定性断言。

## 17. 参考来源与记录溯源

本文件以用户讨论和最新选择为主要依据。其他语言与引擎资料用于解释区别和设计取舍，不自动成为 MCFPP 规范。

- [上一份类型系统记录](type-system/design-decisions-2026-10-07.md)：本文的记录边界和类型前提。
- [MCFPP 讨论 #33](https://github.com/MinecraftFunctionPlusPlus/MCFPP/discussions/33)：早期 lambda 外形的参考；最终形状已按后续用户意见改为 Kotlin 风格。本次最后核对时页面未能重新取得，使用的是此前记录的摘录，不能据此声称已核对最新评论。
- [Rust Future](https://doc.rust-lang.org/std/future/trait.Future.html)：推进、Pending、Ready 与唤醒契约。
- [Rust Coroutine](https://doc.rust-lang.org/std/ops/trait.Coroutine.html)：恢复、产出与完成的区分。
- [rustc coroutine 状态机转换](https://doc.rust-lang.org/nightly/nightly-rustc/rustc_mir_transform/coroutine/index.html)：恢复状态、捕获和跨暂停点局部数据。
- [Rust async block](https://doc.rust-lang.org/reference/expressions/block-expr.html#async-blocks)：匿名 Future 类型和状态布局说明。
- [Rust 异步书：构建执行器](https://rust-lang.github.io/async-book/02_execution/04_executor.html)：计算与执行器分工。
- [Tokio join](https://docs.rs/tokio/latest/tokio/macro.join.html)、[select](https://docs.rs/tokio/latest/tokio/macro.select.html)、[try_join](https://docs.rs/tokio/latest/tokio/macro.try_join.html)：同一任务中的多分支组合。
- [Tokio spawn](https://tokio.rs/tokio/tutorial/spawning)、[JoinHandle](https://docs.rs/tokio/latest/tokio/task/struct.JoinHandle.html)：独立任务和句柄生命周期。
- [futures-lite or](https://docs.rs/futures-lite/latest/futures_lite/future/fn.or.html)：先就绪与左侧优先的组合。
- [Tokio yield_now](https://docs.rs/tokio/latest/tokio/task/fn.yield_now.html)：让出执行机会不等于固定时间推进。
- [Stream 协议](https://docs.rs/futures-core/latest/futures_core/stream/trait.Stream.html)：异步下一项、暂未就绪和序列结束的区别。
- [Kotlin 协程规范](https://kotlinlang.org/spec/asynchronous-programming-with-coroutines.html)：可暂停函数、continuation 和库支持。
- [Kotlin lambda](https://kotlinlang.org/docs/lambdas.html)：花括号内参数、尾 lambda、it 和末尾表达式。
- [C# await 规范](https://learn.microsoft.com/en-us/dotnet/csharp/language-reference/language-specification/expressions#1299-await-expressions)、[异步返回类型](https://learn.microsoft.com/en-us/dotnet/csharp/asynchronous-programming/async-return-types)、[yield](https://learn.microsoft.com/en-us/dotnet/csharp/language-reference/statements/yield)：可扩展等待、不同结果表示和异步迭代。
- [Python asyncio 任务](https://docs.python.org/3/library/asyncio-task.html)：协作式任务调度。
- [Unity coroutine](https://docs.unity3d.com/6000.0/Documentation/Manual/Coroutines.html)、[Awaitable](https://docs.unity3d.com/6000.0/Documentation/Manual/async-awaitable-introduction.html)：引擎中的暂停流程和专用等待。
- [Godot await](https://docs.godotengine.org/en/stable/tutorials/scripting/gdscript/gdscript_basics.html#awaiting-signals-or-coroutines)：信号与可暂停调用。
- [Unreal 定时器](https://dev.epicgames.com/documentation/unreal-engine/gameplay-timers-in-unreal-engine)：定时调用与句柄。
- [Minecraft Java 1.20.3](https://www.minecraft.net/en-us/article/minecraft-java-edition-1-20-3)：手工命令示例所使用的 return run 与函数返回机制。

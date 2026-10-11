# MCFPP 异常、跨 tick 与 lambda 的统一设计

日期：2026-10-07。状态：供讨论的语言设计，未实现。所有新增语法、API 和命令展开都是提案；命令示例为手工推导，未经过编译器生成或 Minecraft 服务端验证。

本文将 [无栈效果与任务设计](stackless-effects-and-tasks-design.md) 扩展到 lambda。后端的分层存储、按需调度和会话生命周期继续适用。本文提出一处明确的语义修订：首版 finally 不允许错误逃逸，以避免清理错误覆盖原始错误；尚未表示该变更已获采纳。

## 1. 设计目标与边界

三个功能共享静态分析和控制流表示，但用户分别通过熟悉的语法使用：

| 功能 | 用户模型 | 编译器模型 |
| --- | --- | --- |
| 异常 | 操作失败，调用者处理 | 正常出口、错误出口与静态清理边 |
| 跨 tick | 暂停后继续顺序执行 | 恢复入口、跨暂停活跃值与唤醒登记 |
| lambda | 可传递的函数，可保存少量外部数据 | 已知代码目标 + 捕获环境；尽量消除两者的运行时表示 |

不把完整代数效果、可复制 continuation、通用堆、垃圾回收器或模拟调用栈设为三者的共同前提。内部预留效果参数即可。未来用户效果必须证明实际需求与编译收益，再增加表面语法。

零成本的标准是：相同语义、版本与生命周期保证下，不为未使用的能力生成运行时机制。保存快照、隔离并发实例、动态选择回调和处理失败本身可能需要命令，不能隐去这些成本。

不能保证“规避所有现代语言的问题”。本设计针对隐式共享捕获、丢失异步错误、非局部 return、取消被吞、清理掩盖错误、任务无意重复执行、空闲轮询和抽象引入的统一分配。

## 2. 从讨论 #33 保留什么

已读取 [讨论 #33](https://github.com/MinecraftFunctionPlusPlus/MCFPP/discussions/33) 正文；读取时无评论。讨论比较箭头式、花括号式 lambda，倾向 `(x) { return x; }`，并提出 `list.foreach (x) { ... };` 和更自由的无括号调用。

建议保留参数列表与函数声明一致、显式参数名、块内显式 return，以及一个受限的尾 lambda 形式。暂不引入隐式 it、表达式尾值返回、任意中缀调用或任意省略调用括号。foreach/map 可以是普通库函数，但不因此删除既有循环：break、continue 与可读性仍是循环语法的价值。

```mcfpp
var inc = (x as int) { return x + 1; };
var y = inc(10);

// 参数类型由 foreach 的签名提供。
items.foreach((item) { print(item); });
items.foreach (item) { print(item); };  // 等价糖

// 普通参数使用括号；最后一个 lambda 可以移到括号外。
items.fold(0, (sum, item) { return sum + item; });
items.fold(0) (sum, item) { return sum + item; };

timers.after("world.notice", 100) () {
    /say Ready
};
```

形式上的规则：`callee(args) lambda` 只把 lambda 追加为最后一个实参；`callee lambda` 只用于唯一实参就是 lambda。无普通实参且存在尾 lambda 时，规范打印为后一种形式。lambda 的参数括号不得省略。

`f(x) { ... }` 在允许尾 lambda 的表达式位置解释为 `f((x) { ... })`，不是执行 f(x) 后附加一个块。控制语句自己的条件与语句块由控制语句语法解析。`f()` 后若要另起独立语句，写分号；不能用换行在这两种含义间切换。此优先级必须通过语法测试固定。

若受限糖仍与现有 grammar 产生难以解释的歧义，首版先交付全括号形式；不要扩张到任意 `foreach list1 ...` 调用规则来补洞。当前 parser 的 primary、functionCall、arguments 和函数类型都需要实质扩展，并非添加一个 token 即可。

## 3. 异常：推导出来的失败契约

```mcfpp
error InvalidAmount(amount as int);
error InsufficientFunds;

func debit(amount as int) {
    if (amount < 0) { throw InvalidAmount(amount); }
    if (balance < amount) { throw InsufficientFunds(); }
    balance -= amount;
}

func buy(amount as int) {
    try {
        debit(amount);
    } catch (e as InvalidAmount) {
        print(e.amount);
    } catch (e as InsufficientFunds) {
        /say Not enough funds
    }
}
```

错误声明是名义类型、只读载荷；首版不做异常继承树。调用自动传播未捕获错误，普通函数无需逐层写 throws。公共 API 可以声明 `throws(A, B)` 上界，`throws()` 表示没有语言错误逃逸；省略声明不表示不抛出。

所有外部入口要处理错误，或提供明确的根错误处理器。库函数可以导出错误契约。未知原生命令是否失败不能自动等同于语言异常；需要明确的包装 API 定义成功条件并转换。

throw 放弃当前执行位置的正常后续；catch 匹配执行路径上最近的适用处理器。错误前已经发生的世界修改保留，不自动事务回滚。错误载荷不能引用退出后失效的临时存储。

### 3.1 lambda 不捕获创建处的异常处理器

```mcfpp
var action = () { debit(10); };  // 推导出 throws(InvalidAmount, InsufficientFunds)
try {
    action();                    // 错误在调用处传播与捕获
} catch (e as Error) {
    report(e);
}
```

仅仅在 try 中创建 action，不会让未来调用自动回到该 try。lambda 可以捕获普通数据，但不会把已退出的异常处理上下文一起带走。任务主体内部包围 await 的 try 则仍然有效，因为它属于正在暂停的同一个计算。

### 3.2 首版清理只做可完成的同步收尾

建议 finally 同时满足：不暂停、不向外抛出、不 return 到外层、不 break/continue 到外层。内部局部循环和内部 try/catch 可以使用；内部错误必须当场处理。

```mcfpp
try {
    await runEvent();
} finally {
    clearEventMarker();  // throws()，不暂停
}
```

这取代旧草案“finally 错误覆盖原始错误”的建议，降低错误优先级、取消覆盖和二次失败的复杂度。确实可能失败的结算放在正常业务路径；finally 保留释放标记、解除订阅等收尾。以后需要异步清理时单独设计，不能悄悄改变 finally。

正常离开、return、throw、协作取消都进入已编译的清理边；单纯 await 暂停不会执行 finally。只执行已进入保护区域的清理，按嵌套逆序。还未执行任务主体就取消时，不执行该主体中的 finally。

这些是正常语言执行下的保证；服务器停止、数据包移除、命令链被引擎终止不能保证清理完成。默认 reload 重置旧任务，业务状态应由新的 load 初始化修复。

## 4. 跨 tick：冷计划、显式执行、独立取消

```mcfpp
task func announceLater(message as string) {
    await ticks(100);
    print(message);
}

var plan = announceLater("Ready"); // 捕获参数，主体尚未运行
tasks.replace("world.notice", plan);
```

任务函数调用返回冷计划。实参从左到右立即求值；求值自身的错误立即传播。主体的异常是潜在效果，只有执行主体时才发生。计划一次消费，赋值转移使用权；第二次 await/launch/replace 同一计划报编译错误。重复构造两个计划合法且表示两次执行。

未消费计划的创建语句报错，避免“调用了但什么也没发生”。局部计划离开作用域仍未消费也报错；需要放弃时用明确的 discard(plan)，只释放捕获，不执行未启动主体的 finally。首版计划不允许塞入任意容器/any；普通用户无需学习一整套生命周期参数。

```mcfpp
task func sequence() {
    await announceLater("First");
    await announceLater("Second");
}

tasks.launch(sequence()); // 两条公告顺序执行；与调用者独立
```

| 操作 | 执行与失败边界 |
| --- | --- |
| `await taskCall()` | 同一任务顺序执行；子任务错误在此传播；允许融合状态机 |
| `await ticks(n)` | 从到达等待点起，不早于 n 个游戏 tick；0 归一到下一 tick |
| `tasks.launch(plan)` | 新建独立根任务，最早下一 tick 启动；未来错误由该根的 onError 处理 |
| `tasks.replace(key, plan)` | 取消旧实例，清理后启动最新计划；等候期间的新请求覆盖旧的待启动计划 |
| `tasks.cancel(key)` | 请求取消，允许清理稍后执行；不回滚已发生的世界修改 |
| `timers.after(key, n, callback)` | 同一键替换尚未触发的同步回调，不生成必须等待的任务结果 |

动态延迟、容量不足等登记失败仍是当前同步调用的错误；不能与未来主体错误混为一谈。首版固定键为编译期字符串，统一命名空间。同一键采用稳定的参数/捕获布局与配置；不兼容声明报错，后续再支持显式动态键。

launch/replace 的根错误处理器必须覆盖主体潜在错误，且本身不暂停、不抛出。没有错误的任务不生成处理器。不能写 `try { tasks.launch(plan); } catch ...` 来期待捕获未来主体错误。

取消有独立的控制出口，不属于 Error，catch(Error) 吞不掉取消。运行中只在显式暂停/检查点观察取消；等待中的任务可以直接排入清理。完成与取消同时到达时，先发生的有效终态转换获胜；已经完成不会再执行一次清理。取消到达 Ready 但尚未运行的任务时，执行前先检查取消。

任务取消与清理可以释放运行时持有的闭包数据；业务资源必须显式安排生命周期。对于“先申请外部资源，再把资源交给尚未启动的任务”这种写法，不承诺未启动任务的 finally 能释放它；首版建议在任务主体内部申请资源并立即建立保护区域。

### 4.1 task lambda 与 task 块

```mcfpp
// 可重复调用的任务工厂：调用一次构造一个新计划。
var delayed = task (message as string) {
    await ticks(20);
    print(message);
};
tasks.launch(delayed("Hello"));

// 一次性任务计划：立即捕获外部值，主体延迟执行。
tasks.replace("world.event", task {
    /say Begin
    await ticks(100);
    /say End
});
```

`task (args) { body }` 是任务函数值；`task { body }` 是零参数任务工厂立即调用的糖，结果是计划。前者可重复构造计划，后者只能消费一次。普通 lambda 不能偷偷 await；普通 `func` 也不能因为传入一个暂停回调而自动变成 task。

直接 await 子任务可以继续同步执行，直到真正遇到等待；await 本身不强制额外消耗一个 tick。launch 根任务则统一延迟启动，避免“第一个 await 之前到底何时运行”的差异。

### 4.2 调度、上下文与长期任务

全局任务不隐式捕获 @s、位置、维度或朝向。同步回调继承调用点的执行上下文；跨 tick 恢复使用全局调度上下文，需要空间/实体上下文时显式提供稳定描述并重新建立。局部快照保留；世界的可变事实必须重新读取或验证。

独立任务在同一 tick 内的顺序不保证。任务只在显式让出时切换；并不保证整个步骤事务隔离，原始命令仍可能同步回调/重入。预算只控制开始多少步骤，不能把任意长步骤自动切开；长循环需要显式让出。

默认任务属于本次加载会话；reload 清除旧调度、订阅和任务数据，采用 session/generation 拒绝迟到唤醒。跨 reload 的业务工作流以后使用显式阶段与迁移规则，不保存任意编译后 pc/闭包编号长期恢复。

首版包含定时等待、顺序 await、固定键任务与独立根任务。事件等待作为下一层；结构化 spawn/join、race、超时组合等在取消协议稳定后提供。不要首版同时发布一套 Promise.then 链和另一套 await 机制。

## 5. lambda：不可变环境优先，借用必须显式

### 5.1 默认捕获局部值快照

```mcfpp
var bonus = 5;
var addBonus = (x as int) { return x + bonus; };
bonus = 20;
print(addBonus(10)); // 15
```

只捕获自由局部变量；捕获时机是 lambda 表达式求值时。默认捕获按类型定义的值复制规则取得只读快照，不能在 lambda 内修改这份环境，也不会写回外层变量。每次循环构造 lambda 都获得当次值，不共享一个隐式循环变量盒。

“值快照”必须是类型系统能力，不能对所有类型承诺廉价深拷贝：标量和明确的值类型可自动捕获；NBT 值复制可能与数据量相关，应报告成本。对象引用/可变视图/临时路径不能因为变量名被捕获就变为独立快照。

对不满足快照能力的值，要求用户显式提供 snapshot(value) 所产生的独立值，或选择具有稳定生命周期的 PersistentRef。两者不能由优化器随意互换。持久引用读取的是之后的状态，可能失效，访问错误进入效果分析。snapshot 的深度和失败由具体类型的 API 契约定义。

全局变量名直接表示全局位置，每次执行时读取，不自动冻结；需要快照时先读入局部值再捕获。成员方法引用也必须处理接收者：有数据的 `object.method` 是带接收者捕获的闭包，不能当无捕获函数指针。

### 5.2 修改外层局部值：受限同步借用

首选 fold 等显式返回值；确需原地累积时，提议一个高级可选子句：

```mcfpp
var total = 0;
items.foreach (item) capture(ref total) {
    total += item;
};
```

`capture(ref total)` 只改变列出的捕获方式，其余仍按默认快照。此处 ref 是临时局部位置借用，允许同步调用期间读写；不是堆分配共享盒。lambda 的外部写入效果包含 total。

只有接收参数已证明 noescape、调用不暂停且借用不跨任何暂停点时才允许。noescape 是参数契约：不能返回、存储、转交给逃逸参数或把回调留到本次调用之后；接收普通函数若要暂停，还必须证明借用已结束。首版采用更易解释的保守规则：该借用只传入不暂停的 noescape 函数。

```mcfpp
timers.after("world.bad", 20, () capture(ref total) {
    total += 1;
}); // 编译错误：延迟回调可能比 total 的作用域活得更久
```

任务计划、task 块、task lambda 首版不允许捕获这种临时 ref。普通 task 中一个局部的同步 foreach 可以使用它，只要借用在下一暂停之前结束。由编译器决定是否内联不改变合法性。

若暂不希望增加 capture 子句，可以推迟该高级能力；默认快照、fold 与任务参数已覆盖首版大部分场景。不要为了省一个关键字自动在“同步引用、异步快照”之间改变同一 lambda 的捕获语义。

### 5.3 函数类型与效果保留

建议可书写的类型形式：

```text
func(int) -> int throws()                  // 普通函数值
func(int) -> int throws(InvalidAmount)     // 可失败函数值
task func(int) -> int throws(LoadFailure)  // 调用构造 Task<int, LoadFailure>
```

普通局部变量用推导，不要求用户写这些类型。每个 lambda 内部保留代码身份、环境布局、错误集合和捕获写入效果。赋给更宽函数类型可以扩大错误上界，不能抹除错误或把 task 工厂变为同步函数。

同步高阶库必须支持回调效果推导，例如 foreach 的错误集合包含回调的错误集合；不需要用户在每个调用处写效果泛型。库定义/元数据中保留编译器级 ε，签名可概念化为：

```text
foreach<T, ε>(items, noescape callback: func(T)->void !ε) -> void !ε
限制 ε 不含 Suspend；还要并入遍历实现自身的效果。
```

这里的 ε 和 ! 只是内部记法，尚非新增源码语法。公开库若需要显式声明可转发效果，先作为库契约/元数据能力提供；不能在导出时把所有回调都退化为 throws(Error)。回调参数省略 throws 时应推导/抽象其效果，不能默认吞掉错误。

需要暂停的遍历使用显式 task 版本，例如 `await items.foreachTask(task (item) { ... });`，首版顺序执行各项。不要根据回调隐式选择并发，也不要自动将 map 返回类型变成“任务列表”。

### 5.4 return、break 与闭包生命周期

lambda 中 return 只结束当前 lambda；即使内联也不返回外层函数。foreach 的回调 return 只结束这一次回调；下一项仍可继续。break/continue 只针对词法上位于当前函数/lambda 内的循环，不跨回调边界。需要提早结束遍历时使用 find/any 或普通循环。

闭包是值，首版没有可观察对象身份/相等性；复制闭包遵循捕获值的复制规则，调用不会消费普通闭包。没有可变环境盒、隐式引用计数或 GC。拥有的环境跟随所属局部变量、容器项或任务区域释放；从函数返回闭包时，要把环境值转移到调用者拥有的存储，不能返回被释放帧的路径。

嵌套闭包可以捕获已构造的值闭包，但不能构造按值自包含循环。首版不捕获 TaskPlan/任务结果句柄等一次消费控制值；这些通过显式参数传递。任务工厂可重复调用，每次生成的计划具有自己的必要状态。

动态同签名闭包使用链接期已知目标集合和带标签环境联合。开放式加载任意代码、将闭包写入长期持久化数据、闭包转成字符串命令均不属于安全函数值模型；不支持时明确报错。公共库需要提供闭包代码、布局和效果元数据。

## 6. 三者相遇时的完整示例

```mcfpp
error RewardUnavailable;

func grantReward(amount as int) throws(RewardUnavailable) {
    if (!rewardEnabled) { throw RewardUnavailable(); }
    worldReward += amount;
}

func startEvent(amount as int) {
    tasks.replace("world.reward", task {
        try {
            setEventMarker();
            await ticks(100);
            grantReward(amount);
        } catch (e as RewardUnavailable) {
            /say Reward unavailable
        } finally {
            clearEventMarker();
        }
    });
}
```

假定 set/clearEventMarker 不抛出、不暂停且不重入，固定键登记不会耗尽容量。amount 在创建 task 块时捕获，不在恢复时读取 startEvent 已失效的局部槽。try/catch 是任务内部控制流，100 tick 后仍可处理 grantReward 的错误。finally 在任务完成、错误退出或取消时清理；等待期间标记保留。

再次 startEvent(newAmount) 必须先清理旧实例，再开始新实例；新的参数写入 pending 槽，不能覆盖旧任务清理仍需要的字段。重复替换只保留最新 pending，释放被替换计划的捕获值。这个 barrier 是 API 的真实成本，不能在有清理的例子中省略。

## 7. 编译策略与可检查成本

### 7.1 一个逻辑中间表示，按实际需要选择存储

```text
Callable = CodeTarget + CaptureEnvironment + EffectSummary
TaskPlan = TaskEntry + OwnedArguments + LatentEffects

Invoke(callee, args, normal, error)
Raise(errorValue)
Suspend(wait, resume, errorResume, cancel)
Finish(value)
CancelExit
```

Callable/TaskPlan 是逻辑概念，不要求总有运行时结构体。处理顺序为：类型与效果推导 → 捕获和逃逸检查 → 目标解析/适度特化 → 正常/错误/取消清理边 → await 融合 → 跨调用/暂停活跃性 → 存储布局 → 命令生成。

活跃性覆盖恢复、错误、取消三条路径；只在 finally 使用的数据也必须保存。捕获字段如果没有被读取可以消除，但创建表达式求值的副作用仍必须保留。公开类型契约与私有布局分开，不能用内联后的成功案例证明任意外部回调都安全。

| 情况 | 目标输出 |
| --- | --- |
| 已知 lambda 的同步 map/foreach | 特化循环、直接调用或内联；不分配 closure 对象 |
| 值捕获且未逃逸 | 局部槽/常量；只保存活跃值 |
| 返回的闭包 | 代码身份 + 调用者拥有的必要捕获；不能引用旧帧 |
| 固定键延迟回调 | 专属入口 + 固定捕获槽 + schedule |
| 单例 task | 阶段函数 + 必要持久字段；没有每次调用的帧 |
| 多次独立 launch | 实例区域 + 唤醒票据；不能共用一份捕获槽 |
| 运行时选择 lambda | 标签分派/宏调用 + 环境；报告实际成本 |
| 非尾递归或同步重入 | 保存受覆盖的活跃值，必要时显式激活记录 |

静态槽安全需要调用图与重入分析；“MC 单线程”不足以证明。静态特化不保证代码尺寸免费，按代码目标/类型/效果专门化，并限制膨胀；达到预算可用有限标签分派，严格成本配置下则报诊断。不得按每个运行时数值特化。

### 7.2 命令示例 A：无捕获 lambda 定时器

```mcfpp
timers.after("world.notice", 100) () {
    /say Ready
};
```

在无其他生命周期要求的一次会话内，登记与回调可展开为：

```mcfunction
# register.mcfunction
schedule function demo:timers/notice 100t replace

# timers/notice.mcfunction
say Ready
```

没有闭包对象、任务表或逐 tick 轮询。加载会话清理命令另在 load 初始化中生成，不计作每次登记成本。不同逻辑 key 不能因为回调代码相同就合并 schedule ID。

原生 schedule 的 replace/clear 能力有官方说明，但独立实例不能直接靠同一回调的 append 来隔离捕获数据。[Minecraft 1.15 发布说明](https://feedback.minecraft.net/hc/en-us/articles/360037384972-Minecraft-Java-Edition-Buzzy-Bees)

### 7.3 命令示例 B：闭包捕获是固定键的一份快照

```mcfpp
var amount = readWorldAmount();
timers.after("world.reward", 100) () {
    worldReward = amount;
};
```

假设 amount/worldReward 为整数，已完成 objective 初始化；代码不重入，没有取消清理，读写位置映射到下列比分项：

```mcfunction
# register.mcfunction
scoreboard players operation #reward.capture demo.rt = #amount demo.rt
schedule function demo:timers/reward 100t replace

# timers/reward.mcfunction
scoreboard players operation #world.reward demo.rt = #reward.capture demo.rt
```

相对无捕获版本，多一份持久整数和快照写入；不分配通用环境对象。再次登记该 key 的替换语义允许更新这份捕获。若改为独立 launch，必须分配各实例自己的 amount，不得沿用这一展开。对于回调内再次登记自身的情况，必须保留当前执行还需读取的旧环境，不能直接覆盖。

### 7.4 命令示例 C：跨函数的错误出口

假设 Java 1.20.4 目标，无载荷错误 NotPositive，单一同步非重入调用，且 catch 后直接结束入口：

```mcfunction
# check.mcfunction: 私有 ABI 0 = 正常，1 = 错误
execute if score #input demo.rt matches 1.. run return 0
return 1

# caller.mcfunction
execute store result score #outcome demo.rt run function demo:check
execute if score #outcome demo.rt matches 1 run return run function demo:caught
say Accepted
return 0

# caught.mcfunction
say Rejected
return 0
```

这里使用 function 的 result 传递语言状态；不是把 Minecraft success 当语言异常，也不能用 execute if function 直接测试“正常”——该 API 对返回值的约定不同。语言返回值另有数据槽。若 catch 后还有公共后续或 finally，必须接到相应公共/清理块，不能照搬直接 return。[Minecraft 1.20.3 发布说明](https://www.minecraft.net/en-us/article/minecraft-java-edition-1-20-3)

### 7.5 动态闭包不伪装成零分派

封闭目标集合可生成整数标签分支，各目标直接调用；现代目标也可在安全的编译器生成 ID 上使用函数宏。普通函数引用不能让用户任意字符串变成可调用地址。

宏替换后的命令需要额外处理，官方明确其相对普通预解析函数存在开销；不能只因“一条宏命令”就认定成本更低。捕获布局、环境复制和命令字节数同样进入报告。[Minecraft 1.20.2 发布说明](https://www.minecraft.net/en-us/article/minecraft-java-edition-1-20-2)

## 8. 需要固定的诊断与验证

| 情况 | 预期结果 |
| --- | --- |
| 创建 lambda 后外层局部数值改变 | lambda 使用创建时快照 |
| 全局值在回调前改变 | 回调读取新的全局值 |
| 捕获可变对象而未选择快照/稳定引用 | 指出具体捕获与两种修改方向 |
| 借用局部 ref 交给 after/launch | 编译错误；指明逃逸 API |
| 同步 lambda 中 await | 编译错误；建议 task 回调 API |
| lambda return | 只退出该次 lambda 调用 |
| lambda 在 try 内创建、try 外调用并失败 | 仅调用处处理器有效 |
| task 内 try 跨 await 后失败 | 命中任务中原有 catch |
| catch(Error) 包围取消点 | 取消仍进入清理，不能被 catch 吞掉 |
| finally 错误未处理或暂停 | 编译错误 |
| 同一计划执行两次/未消费 | 编译错误 |
| replace 旧任务清理使用旧捕获 | pending 新捕获不覆盖旧环境 |
| 未启动任务取消 | 不运行主体/finally，释放运行时拥有的捕获 |
| 同一 lambda 的两个并发实例 | 捕获与进度独立 |
| 同步重入同一静态函数 | 必要活跃值得到保存或拒绝严格无帧模式 |
| reload 前的唤醒到达新会话 | 失效，不访问新实例 |

成本验收同时记录命令数、命令字节数、复制数据量、持久字段、恢复入口、分派次数和实际服务端耗时。语法正确、命令少不等于运行性能已验证。只按需求链接调度组件，空闲时无任务轮询；时钟适配与版本能力沿用前文的验证要求。

## 9. 实施顺序

1. 先统一 Callable/效果签名、正常/错误出口、值与物理存储分离；为当前模拟栈保留适配层。
2. 做普通函数引用、无捕获 lambda、同步高阶效果推导及异常；固定 return 与语法消歧测试。
3. 加入值捕获、逃逸分析、返回闭包；可选的局部 ref 捕获随后加入。
4. 交付原生定时器与固定键 task：跨 tick 活跃数据、异常、取消、finally 必须一起验证。
5. 加独立多实例、事件等待与所需调度模块；再提供结构化并发组合。

首版冻结的语言规则应是“默认值捕获、显式暂停、调用处处理错误、取消独立、同步不抛出的清理”。通用代数效果语法、多次恢复、共享可变闭包盒、异步 finally 和持久化闭包留在首版之外。

## 10. 设计自评

总体：4.0/5，适合评审；尚未成为可执行规范。

| 维度 | 分数 | 证据与改进 |
| --- | --- | --- |
| 准确性 | 4 | 已读取讨论、现有 parser 与官方命令文档；命令尚需在锁定版本服务端验证 |
| 完整性 | 4 | 三者及捕获/错误/取消交互均有规则；稳定引用的具体类型 API 需结合类型系统另定 |
| 清晰度 | 4 | 区分闭包、任务工厂、一次性计划；尾 lambda 的 grammar 消歧仍需原型验证 |
| 可执行性 | 4 | 有实现顺序、预期诊断和三个命令展开；尚无编译器原型或性能测量 |
| 简洁性 | 4 | 入门语法少，高级能力单独约束；可选 ref 捕获与函数类型仍增加库作者负担 |

优先改进：验证尾 lambda 语法；用固定键任务原型验证旧实例清理与新捕获隔离；在目标服务端测量三种展开。自检：用户可以据此决定语言规则，但不应据本文认为这些能力已实现或已达到性能指标。

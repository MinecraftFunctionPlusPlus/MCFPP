# 普通同步 lambda

参数写在花括号内，以 `->` 分隔正文。类型标注使用冒号；函数类型不带 `func` 前缀，具名函数仍使用 `func`。

```mcfpp
func plusOne(value: int): int { return value + 1; }

func main() {
    var add: (int, int) -> int = { left, right -> left + right };
    var five = add(2, 3);
    var next: (int) -> int = plusOne;
    var six = next(five);
}
```

参数有明确目标类型时可省略类型。单参数目标允许省略参数列表并使用词法绑定的 `it`。没有目标签名时，应显式标注参数，例如 `{ value: int -> value + 1 }`；不能仅凭 `it` 猜测参数类型。内层单参数隐式 lambda 遮蔽外层 `it`，零参数或显式命名参数的内层 lambda 可以按值捕获外层绑定。

函数类型可用于变量、参数和返回值。函数参数为函数类型时写 `((int) -> int) -> int`；返回函数类型时写 `(int) -> () -> int`，箭头右结合。普通函数引用的重载须能在目标签名下唯一确定。

## 返回和副作用

最后一个没有分号的表达式贡献返回值。`{ 1 + 2 }` 返回 `3`，`{ 1 + 2; }` 没有隐式结果，不能赋给 `() -> int`。目标为 `void` 时可以丢弃尾表达式的值，表达式仍执行。明确的空回调可写 `{ ; }`；期望 `() -> void` 的实参位置也接受 `{}`，其他位置的 `{}` 保持空 NBT compound 语义。

`return` 只结束当前 lambda。`break`、`continue` 只能作用于该 lambda 内的循环，不能跨越回调边界。非 `void` 回调的所有可达完成路径必须产生兼容结果。

## 捕获和返回闭包

```mcfpp
func makeAdder(base: int): (int) -> int {
    return { value -> base + value };
}

func main() {
    var first = makeAdder(3);
    var second = makeAdder(8);
    var seven = first(4);
    var twelve = second(4);
    var copied: (int) -> int = first;
    var eight = copied(5);
}
```

每次创建都复制局部值的当前表示。之后修改创建者的局部变量不改变快照；捕获绑定只读，不能在正文中赋值或复合赋值。通过返回值或 `fold` 累积结果。全局按调用时的原声明位置访问；先复制到局部再捕获，可冻结其当前值。

返回、复制和嵌套返回的闭包拥有独立环境，不依赖已经退出的创建者栈帧。调用可重复执行。成员方法引用按接收者类型的复制契约捕获实例；有限已知目标可在运行时分派，同时使用所选目标的环境。Minecraft 的实体、位置和维度上下文来自调用点。具体类型边界见 [捕获能力表](capture-types.md)。

## 集合操作

```mcfpp
func main() {
    var items = [1, 2, 3, 4];
    var mapped = items.map { it + 10 };
    var selected = items.filter { it % 2 == 0 };
    var total = items.fold(5) { accumulator, item -> accumulator + item };
}
```

结果分别为 `[11, 12, 13, 14]`、`[2, 4]` 和 `15`。原列表保持原值。`fold` 的下一轮累积值来自回调显式结果。`foreach` 忽略回调结果，其中的局部 `return` 只结束当前项；不会提前结束遍历。空列表的 `fold` 返回初始值。四操作的实际源码和观察命令见 [集合示例](../../examples/lambda/collections/main.mcfpp)。

## MNI 和暂缓范围

MNI 回调使用相同的新语法、创建时值捕获、只读局部和局部 `return`。旧 `(args) => { ... }` 已删除，并给出迁移诊断。只接收静态 `INLINE` 正文的宏不能展开动态函数值；普通运行时函数参数则使用函数值调用。迁移示例见 [MNI 教程](../mni/tutorial.md)。

引用捕获、普通 lambda 调用 MNI `REF` 参数、嵌套回调 `REF` forwarding 暂未支持。两项互操作用例按用户决定暂缓，测试正文和原失败证据保留；既有宏直接 `REF` 参数的检查继续执行。本轮也不包含暂停/generator 引擎、任意字符串调用地址、闭包容器元素、长期持久化、共享可变盒或 GC。

函数类型和声明可通过 `.mclib` 导入，在新编译会话重新编译正文和闭包目标/环境。当前库格式为 `83`；旧缓存会明确要求重新编译。这不等于把运行时闭包保存到磁盘后跨 reload 恢复。

## 在本仓库运行

在项目根目录使用 JDK 21、Gradle 8.14。默认资源已包含格式 `83` 标准库，无需添加历史标准库夹具。

```powershell
gradle --offline --no-daemon test
```

`LambdaCallTest`、`LambdaCaptureTest`、`LambdaEscapeTest`、`LambdaDynamicTest`、`LambdaReentryTest`、`LambdaHigherOrderTest` 和 `LambdaLibraryTest` 从真实源码生成用于执行检查的数据包。所有示例入口见 [examples/lambda](../../examples/lambda/README.md)。

本工作区固定的 Windows 编译/DSB 环境可运行 `build/lambda/library-default-check.ps1`，实际检查源编译与新会话库导入的两份生产数据包；脚本会核对运行时哈希。生成后可用已有 DSB 运行器执行清单：

```powershell
& F:/Java/jdk-25.0.4/bin/java.exe -cp build/inheritance-mni/dsb-runtime/datapack-sandbox-cli.jar examples/mni/benchmark/DsbFunctionalCheck.java --output build/lambda/example-report.json build/lambda/library/imported/imported.dps.json
```

DSB 必须执行实际生成的命令并检查输出与空返回栈。历史检查记录只证明其对应源码、依赖和配置；交付阶段的完整回归结果独立记录，不能把已有 DSB 报告当成本次重跑。

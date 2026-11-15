# MCFPP API 文档

在项目目录中运行：

```shell
mcfpp doc
```

默认在项目输出目录的 `docs` 子目录生成 `index.html` 和 `docs.json`。HTML 可以直接在浏览器中打开，导航和搜索均可离线使用。文档模式完成声明、类型、注解和继承检查，随后采集 API；函数体中的未完成实现不会阻止文档生成。语法或声明错误会阻止导出，并保留上一次成功生成的文档。

```shell
mcfpp --project path/to/project --locked --offline doc
mcfpp doc --format html --output api
mcfpp doc --format json --include-private --include-dependencies
```

`--format` 接受 `html`、`json` 和 `both`，默认 `both`。相对 `--output` 路径以项目目录为基准；默认位置跟随项目的 `targetPath`。每次成功导出会更新所选格式，并移除同一目录中另一格式的旧生成文件。其他文件会保留。

默认导出当前项目的公开声明和 `protected` 成员。`--include-private` 包含私有声明；`--include-dependencies` 包含已加载依赖库的 API。局部变量、泛型实例和编译器辅助函数不作为独立文档项。

## 文档注释

使用连续的 `///` 行。前缀后的一个空格作为分隔，进一步缩进、空行和 Markdown 内容会保留。文档可放在声明的注解之前。

```mcfpp
/// 记录累计值。参见 [Counter.read()]。
data Counter {
    /// 当前累计值。
    var value: int = 0

    /// 读取当前值。
    ///
    /// ```mcfpp
    /// var counter = Counter()
    /// var current = counter.read()
    /// ```
    /// @return 当前累计值。
    func read(): int { return this.value }
}

/// 返回输入值。参见 [identity(int)]。
/// @param value 输入值。
///   参数说明可以跨行，保留 Markdown。
/// @return 输入值。
/// @since 1.0.0
func identity(value: int): int { return value }
```

标签从正文后的行首 `@` 开始，后续行属于当前标签。代码块内的 `@` 保留为代码。支持 `@param`、`@return`、`@throws`、`@receiver`、`@property`、`@constructor`、`@sample`、`@see`、`@since`、`@deprecated`、`@version`、`@author`、`@base` 和 `@context`。未知标签原样保留；缺少内容的已知标签报告源码位置，不中断编译。

`[Counter]`、`[Counter.read()]`、`[namespace:Counter.read()]` 和 `@see Counter.read()` 可引用声明。重载使用参数类型定位，例如 `[identity(int)]`。按当前声明、命名空间和导入环境查找；无法唯一解析的符号保留文本并报告警告。目标被可见性或依赖选项筛除时也保留文本。普通 Markdown 链接和代码中的方括号保持原有含义。

继承成员展示原始说明和来源链接；重写成员展示自己的注释。字段及其属性统一展示。命名空间、类型别名、枚举项、构造函数和运算符也支持文档。

## MNI 与库文档

原生函数和宏的文档写在显式 MCFPP 声明处：

```mcfpp
/// 返回由 Kotlin Provider 提供的答案。
/// @return 答案。
func answer(): int = example.mni.Example.answer
```

两者使用相同的解析、标签和链接规则。文档模式绑定实现签名，采集声明注释。Kotlin Provider 本身仍需通过其正常构建。

常规库导出会将文档写入 `.mclib` 的可选声明文档表。导入库后，文档可继续查询和导出；已有的无文档库按空文档处理。此能力沿用库格式 v1。

## 程序接口与 JSON

`DocumentationExport.generate(request, options)` 使用编译会话请求生成不可变的 `DocumentationModel`，返回前关闭会话。`captureCurrentProject(options)` 用于已经完成声明准备的编译器会话；`fromLibraries(title, libraries)` 可以直接读取已解码库模型中的文档。

`DocumentationExport.write(model, directory, format)` 写出产物。快照中不包含编译器对象或解析树。JSON 的 `schemaVersion` 为 `1`，包含声明 ID、包身份、签名、可见性、正文、标签、参数、来源及引用。声明 ID 包含归属和参数类型，重载使用不同锚点。

可运行示例位于 `examples/documentation`：

```shell
mcfpp --project examples/documentation doc
```

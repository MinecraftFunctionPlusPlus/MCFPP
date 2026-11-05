# Playground 可复用编译内核

`top.mcfpp.api.library.PlaygroundCompilerApi` 面向浏览器中的单文件编译。宿主持有一个内核，串行提交 `main.mcfpp` 文本，得到脱离编译器对象的诊断和生成文件内容。

```java
try (var kernel = PlaygroundCompilerApi.openKernel(
        Path.of("new-workspace"), "1.21.8", "playground")) {
    var first = PlaygroundCompilerApi.compile(kernel, "func main() { print(1) }");
    var next = PlaygroundCompilerApi.compile(kernel, "func main() { print(2) }");
}
```

工作目录必须不存在。`openKernel` 预留编译器，标准库准备发生在第一次 `compile` 内；返回的 `timings.preparationNanos` 和 `totalNanos` 包含这部分时间。其余计时字段为 `compilationNanos`、`restorationNanos`，单位都是纳秒。总耗时还包含临时工程创建、读取产物、删除文件等工作。

每次请求使用递增编号的独立源码和产物目录。Java 在返回前读取 UTF-8 文件内容并删除该请求目录，避免浏览器文件系统在目录替换后读到旧后代。失败返回空 `files` 和实际诊断。`kernelUsable=false` 表示准备或恢复失败，宿主应重载运行时，不能在损坏状态上继续排队。

不可变库协议和构建索引由内核保留；已准备的编译器声明用深层基线恢复，导入目录、类型视图、结构正文投影、泛型实例和注解 arena 的私有缓存也一起恢复。源码对象、宏预算、诊断和输出按请求创建。当前编译器仍有进程全局状态，因此内核独占编译器，并不支持多个内核或普通会话并行。普通 `LibraryCompilerApi` 仍用于完整项目、外部库和插件。

`prepareBuiltinImportPlan` 在桌面 JVM 的构建阶段读取 1.21.8 的 `bin.mclib`，生成 `datapack/targets/1.21.8/import-plan.bin`。`processResources` 自动依赖此任务；`shadowJar` 和 `playgroundJar` 都携带该资源。生成内容包含命名空间与符号键目录、函数签名和参数类型引用、成员与继承字段布局、原生 JVM 描述符、注解容器类型提示。文件头绑定库字节的 SHA-256、目标和 schema，运行时遇到不匹配会明确拒绝。

内核仅对带有构建计划、没有外部依赖且声明注解已完成的内置库启用延迟成员。闭合 data 的名义类型头先发布，字段、属性、构造器和成员函数在语义查询时物化。泛型、object、表达式父类和带函数标签的类型保留原准备路径。注解按实际依赖图加载，保留共享容器、父注解和循环引用。普通外部库保持原来的完整语义校验。其他 Minecraft 目标没有这个侧文件时使用原导入路径。

继承布局的依据仍是正式库里的成员、父类型及已完成继承效果记录。计划不保存 Java `Class`、`Method` 或可变 `Var`；原生提供者在运行时仍需解析和校验。此改造减少重复扫描及未用成员的构造，没有取消所有反射，也没有将整个编译器变成纯不可变模型。

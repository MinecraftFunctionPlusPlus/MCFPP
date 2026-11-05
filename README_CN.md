# MCFPP 浏览器 Playground

当前分支为 `teavm-playground`，用于构建文档站内的单文件编译器。
TeaVM 0.16.0 将 Kotlin/Java 编译器预编译为 WasmGC，网站仍使用相邻仓库 MCFPPAPI 中的
VitePress + Vue + TypeScript 页面。

完整桌面版本保留在 `kotlin-latest` 分支的 `079f4c3f` 提交。

## 构建和启动

需要 JDK 25。在此仓库执行：

```powershell
.\gradlew.bat :browser:buildWasmGC
```

然后在 MCFPPAPI 仓库执行：

```powershell
pnpm playground:prepare
pnpm playground:typecheck
pnpm docs:dev
```

访问 `/zh/playground.html`。已有文档服务器时继续使用原服务。
编译与 DSB 执行各自位于 Web Worker 中，源码编译不会发送到服务器。

## 分支范围

- 固定编辑 `main.mcfpp`，默认命名空间 `playground`，目标 Minecraft 1.21.8。
- 包含该目标完整内置标准库。符号、签名、继承布局和原生绑定目录在构建阶段生成。
- 第一次编译准备标准库和可恢复基线，后续编译复用内核；每次请求拥有独立可变状态和产物目录。
- 保留解析、语义检查、优化、数据包生成、诊断及多文件结果查看。
- 已移除桌面 CLI、配置文件加载、包管理、外部库/JAR 加载、自定义源码 MNI 声明及桌面发布安装任务。
- 普通语言 `import` 仍可引用内置标准库中的命名空间。

## 验证

```powershell
.\gradlew.bat test :browser:buildWasmGC
.\gradlew.bat :browser:benchmarkKernel
```

此分支保留协议与算法单元检查。依赖桌面工程、外部提供者或多版本标准库的原有集成套件保留在原分支。
浏览器集成使用 `browser/benchmarks/cases.json`：JVM 和网站验收脚本编译相同源码，
核对实际生成文件、错误恢复、文件切换和 DSB 输出。性能测量应在 Gradle 构建结束后进行。

平台适配、产物路径和验收命令见 [browser/README.md](browser/README.md)。

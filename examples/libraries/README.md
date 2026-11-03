# 从本地 Maven 安装预编译库

这个例子包含作者项目 `producer` 和消费者项目 `consumer`。作者导出 `bin.mclib`、`module.json` 与静态资源；Mcfpm 将它们打包到本地文件 Maven 仓库并执行 install。脚本随后把生成工作目录中的作者源码移到 `source-archive`，再用已安装缓存编译消费者，检查私有实现、静态资源和 lock 摘要。

库发布身份为 `fixture:semantic-lib:1.0.0`，载荷 classifier 为 `mcfpp`，Minecraft 目标为 `26.3`。`producer/mcfpp.toml` 的 `[library]` 身份必须与用于发布的 `producer/mcfpm.toml` 一致。版本使用 SemVer 2，例如 `1.0.0`。

## 构建本地工具

从 MCFPP 仓库根目录操作，假设相邻目录为 `../Mcfpm`。实际路径不同时自行调整。MCFPP 构建和运行使用 JDK 21；Mcfpm 当前构建 toolchain 为 JDK 25，发布字节码仍为 Java 17。先安装两个 JDK，并将 `JDK21_HOME`、`JDK25_HOME` 环境变量设为各自安装目录。

MCFPP 的 `runtimeOnly` 依赖需要 SDK 的三个本地产物：`model`、`core` 和 `mcfpp-adapter`。先在 SDK 仓库发布到本地 Maven，并生成 CLI：

```powershell
$env:JAVA_HOME = $env:JDK25_HOME
Push-Location ../Mcfpm
try {
    ./gradlew.bat :model:publishToMavenLocal :core:publishToMavenLocal :mcfpp-adapter:publishToMavenLocal :cli:installDist
} finally { Pop-Location }
$env:JAVA_HOME = $env:JDK21_HOME
./gradlew.bat shadowJar
```

两边默认 SDK 版本均为 `0.1.0-SNAPSHOT`。自定义版本时，两边构建命令均传相同的 `-Pmcfpm.version=...`。这里的 `publishToMavenLocal` 写入开发机 Maven 缓存；下面的样例仓库位于工作目录内，流程不调用远程发布任务。

## 运行

在 MCFPP 仓库根目录执行：

```powershell
./examples/libraries/run.ps1 `
    -CompilerJar ./build/libs/MCFPP-1.0.2-SNAPSHOT.jar `
    -McfpmCommand ../Mcfpm/cli/build/install/mcfpm/bin/mcfpm.bat `
    -JavaHome21 $env:JDK21_HOME `
    -WorkDirectory ./build/library-example
```

需要 PowerShell。`CompilerJar` 可指向其他发行 JAR，`McfpmCommand` 可指向已安装的 CLI。未指定 `JavaHome21` 时，编译器使用 `PATH` 中的 `java`，Mcfpm launcher 使用其正常的 Java 查找方式。`WorkDirectory` 应是新的目录；脚本保留输入模板和先前运行结果，不覆盖已有 producer/consumer。

也可按顺序分别运行 `-Step producer`、`install`、`consumer`、`verify`，每次传入相同路径参数。`producer` 复制 TOML、依赖 JSON、源码和资源后导出；`install` 创建本地 Maven 载荷和描述符，通过 `mcfpp add` 安装消费者依赖，再移走作者源码；`consumer` 使用已安装库构建；`verify` 检查：

- 消费方 `.mcfunction` 包含 `say installed-private-body`，说明私有依赖正文已生成。
- `consumer/out/Imports/data/fixture.package/tags/function/extra.json` 存在。
- `consumer/mcfpp.lock` 与 install 后保存的 `installed.lock` 摘要一致。

独立用户目录位于 `WorkDirectory/host-home`。MCFPP CLI 读取 `mcfpp-packages.json` 与 `mcfpp.lock`，复用该用户目录的 Mcfpm 缓存；有效锁继续使用。实际编译会话接收 CLI 准备好的依赖。

这个样例用 `-ignoreStdLib` 隔离作者/消费者流程。一般项目默认用 `stdLib:["%std"]` 按 `version` 选择实际编译的标准库 bundle；自定义路径、资源与初始化归属见 [按 Minecraft 目标使用标准库](../../docs/library/compiler-bridge.md#按-minecraft-目标使用标准库)。

## 预编译与兼容

发布库保存声明、类型、常量和中立 AST 正文。导入时在当前编译会话绑定声明，再让现有分析/生成器处理实际调用；消费点已知的值可以参与当前优化策略。预编译不意味着把所有函数固定成作者机器上生成的命令，也不意味着每个普通常量实参都会触发泛型特化。

已发布格式的兼容方向是：新 reader 保持读取已发布旧格式；旧 reader 遇到新格式、必需语义或不支持的 runtime ABI 时明确拒绝。未发布的 Kryo/ANTLR 快照需要用当前作者编译器重新导出。协议细节见 [format-v1.md](../../docs/library/format-v1.md)，嵌入 API 和资源/loader 生命周期见 [compiler-bridge.md](../../docs/library/compiler-bridge.md)。

## 验证范围

这里的可传路径脚本已在 PowerShell 7、JDK 21、实际 SDK CLI 和编译器 fat JAR 上执行，完成 SDK install → 移走作者源码 → 消费者编译，并检查私有方法、静态资源与 lock 不变。标准库另已通过真实源码生成、显式目录和内嵌 `%std` 消费验证，本例仍显式跳过它。验证主机曾使用本机 JVM 设置处理环境问题；它们不属于用户项目配置，也未写入样例脚本。

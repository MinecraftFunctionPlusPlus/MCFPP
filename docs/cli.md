# MCFPP 工程与 CLI

MCFPP 使用 JDK 25。发行目录的 `bin/mcfpp`（Windows 为 `bin/mcfpp.bat`）提供项目、依赖和构建命令；也可执行 `java -jar MCFPP-0.1.0.jar <命令>`。

编译器、生成的 MNI 工程、语言服务与 DSB 共用 JDK 25。构建采用 Gradle 9.2.1、
Kotlin 2.4.10 和 JVM 25 字节码；版本选择依据 [Gradle 的 Java 25 支持](https://docs.gradle.org/9.1.0/release-notes.html#java25)
与 [Kotlin/Gradle 兼容表](https://kotlinlang.org/docs/gradle-configure-project.html)。

## 安装并配置 PATH

完整步骤见 [安装说明](../INSTALL.md)。Windows 用户完整解压 `mcfpp-0.1.0.zip`，设置 `JAVA_HOME` 为 JDK 25，将解压目录的 `bin` 和 `%JAVA_HOME%\bin` 加入用户 `Path`，重新打开终端后执行：

```shell
mcfpp --version
mcfpp --help
```

应显示 `MCFPP 0.1.0 (toolchain java25-v1)`。Windows 使用 `mcfpp.bat`，无需设置 PowerShell 脚本执行策略。Linux/macOS 用户使用 `mcfpp-0.1.0.tar.xz`，将 `bin` 和 JDK 25 的 `bin` 加入 PATH。启动脚本相对自身定位 `lib`，可将完整发行目录移动或重命名。

发行目录包含完整运行时，JVM API 源码 JAR 单独提供。保持 `bin`、`lib` 在同一发行目录中。版本范围和反馈方式见 [0.1.0 发行说明](../RELEASE-NOTES.md)。

## 创建与构建

```shell
mcfpp init hello
cd hello
mcfpp build
```

`mcfpp init hello --mni` 创建 Kotlin 宏、MCFPP 显式绑定及 Kotlin/JUnit 测试。`mcfpp test` 执行 JVM 测试和 `src/test/mcfpp` 中的 [MCFPP 单元测试](unit-testing.md)。第一次使用 MNI 时，CLI 自动准备固定的 Gradle 9.2.1，并应用 Kotlin/JVM 2.4.10 插件以匹配宿主的 Kotlin 运行时；本机需要 JDK 25，可通过 `MCFPP_JAVA_HOME`、`JDK25_HOME` 或 `JAVA_HOME` 指定。

生成数据包和库位于 `build`，JVM 中间产物位于 `build/.mcfpp/mni`。`.mcfpp` 保存生成的 Gradle 工程和临时状态，目录可删除并重建。请提交 `mcfpp.toml`、`mcfpp-packages.json`、`mcfpp.lock`，以及启用 MNI 后的 `mcfpp-mni.lock` 和用户扩展脚本。

例如 `hello` 工程的数据包目录是 `build/hello`。将其整体复制到对应 Minecraft 世界的 `datapacks` 目录，执行 `/reload`，再用 `/function hello:main` 调用示例入口。若生成了 `build/hello-resources`，将其作为资源包安装。

CLI 从当前目录向上查找 `mcfpp.toml`。在其他位置使用 `mcfpp build --project /path/to/project`。

## 项目配置

```toml
schema = 1
name = "hello"
namespace = "hello"
version = "1.21.8"
sourcePath = "src/main/mcfpp"
targetPath = "build"
dataPath = "src/main/resources/data"

[toolchain]
version = "java25-v1"

[mni]
enabled = false
```

`version` 是 Minecraft 目标版本。原有编译字段含义继续适用，包括 `stdLib`、`stdNamespace`、`commentLevel`、`noDatapack`、`noMcLib`、`copyImport`、`args` 和 `[macroExpansion]`、`[library]`。相对路径基于项目根目录；输出目录必须位于工程内部。新入口只接受 schema 1 TOML。

## 依赖

```shell
mcfpp add 'example:utils@^1.0.0'
mcfpp remove example:utils
mcfpp add --mni org.example:helper:1.0.0
mcfpp add --mni org.example:testing:1.0.0 --configuration testImplementation
mcfpp remove --mni org.example:testing --configuration testImplementation
mcfpp install
mcfpp update
```

添加 MNI 依赖会启用本地 Java 构建。字符串 MNI 依赖默认使用 `implementation`；复杂条目使用 `id` 和 `configuration`。MCFPP 包必须使用完整 `group:name` 坐标及显式 SemVer 范围，对象条目还可设置 `features` 和 `optional`。`mcfpp-packages.json` 是严格 JSON，不接受注释或重复字段。

`install` 准备依赖，不编译源码。`build` 和 `test` 自动准备依赖。已有有效锁保持已选版本；声明使锁失效时重新解析；`update` 主动更新声明允许的版本。包变更失败时保留已有声明和锁。

```shell
mcfpp build --locked
mcfpp build --locked --offline
```

`--locked` 要求已有有效锁并禁止修改锁。`--offline` 禁止联网，包括下载 Gradle；需要的制品及插件必须已缓存。Mcfpm 制品复用 `~/.mcfpm/cache`，Gradle 复用 `GRADLE_USER_HOME`（默认 `~/.gradle`）。

## 仓库与 Gradle 扩展

```toml
[mcfpm]
default-repository = "private"

[mcfpm.repositories]
private = "file:///path/to/repository/"

[mcfpm.bindings]
"example" = "private"

[mni]
enabled = true
repositories = ["https://repo.maven.apache.org/maven2/"]
extensions = ["custom.gradle.kts"]

[mni.plugins]
"org.gradle.test-retry" = "1.6.2"
```

MCFPP 仓库配置沿用 Mcfpm 的仓库、绑定及 `options` 规则；默认优先 afox，缺失时查询 Central。MNI 的 `repositories` 用于普通 Maven 依赖。

扩展脚本在基础 Kotlin/JVM 配置后按顺序加载，可配置已声明插件、依赖和任务。`src/main/kotlin` 与 `src/test/kotlin` 分别作为主源码和测试源码；`src/main/java` 与 `src/test/java` 也加入 JVM source set。脚本使用 Gradle API，例如：

```kotlin
tasks.named("classes") {
    // dependsOn("generateMySources")
}
dependencies.add("implementation", "org.example:helper:1.0.0")
```

额外插件在 `[mni.plugins]` 声明，也可以覆盖 `org.jetbrains.kotlin.jvm` 的版本。CLI 使用扩展生效后的实际运行 classpath 加载本地 MNI；JVM 编译失败会停止 MCFPP 编译。旧版 MNI 锁需要执行一次 `mcfpp install`，记录 Kotlin 插件引入的依赖后再使用 `--locked`。

## 首版测试范围

`mcfpp test` 执行 `src/test/kotlin` 和 `src/test/java` 的 JVM 测试，以及 `src/test/mcfpp` 中的 MCFPP 测试。`mcfpp clean` 清理构建输出，保留配置、锁文件及依赖缓存。

## 从源码构建 CLI

先在相邻 Mcfpm 仓库构建并发布 `model`、`core`、`repository-maven` 和 `mcfpp-adapter` 到本地 Maven（SDK 构建使用 JDK 25），再使用 JDK 25 在本仓库运行：

```shell
./gradlew release
```

完整发行物位于 `build/releases/0.1.0`：`mcfpp-0.1.0.zip`、`mcfpp-0.1.0.tar.xz`、独立运行时 JAR、JVM API 源码 JAR、`mcfpp-0.1.0-source.tar.xz`、`SHA256SUMS` 和 `release-manifest.json`。源码快照包含当前工作区的新源码，不依赖其是否已提交到 Git。清单中的源码快照及制品哈希用于对应本次发布的实际内容。

`release` 不以构建速度为目标：生产依赖裁剪、标准库无损分块去重、Zopfli DEFLATE 和 XZ 压缩用于缩小体积。所有 Minecraft 目标版本保留；打包器逐字节校验全部标准库及压缩流。压缩缓存位于 `build/release-compression-cache`，只影响重复构建速度。构建辅助工具位于 `build-support`，压缩工具依赖不会进入运行时。

日常快速打包可运行 `./gradlew shadowJar` 或 `./gradlew installShadowDist`；快速 ZIP/TAR 使用 `shadowDistZip` / `shadowDistTar`。`verifyDistribution` 继续验证快速 JAR。这些任务不执行发行压缩，`releaseSources` 也保留快速源码 ZIP。

本地启动脚本位于 `build/install/mcfpp/bin`，可将此目录加入当前会话 PATH。`installShadowDist`、`shadowDistZip` 和 `shadowDistTar` 使用完整运行时；普通 `installDist`/`distZip` 使用名为 `mcfpp-dev` 的开发发行目录。普通 JAR 的 `plain` classifier 防止它覆盖独立运行时 JAR。

发行 JAR 保留 Kotlin 反射运行时，可直接使用 `java -jar` 编译泛型 NBT 引用。`./gradlew verifyDistribution` 使用快速独立 JAR 编译此类示例，检查运行时依赖是否完整。`sourcesJar` 生成配套的 `MCFPP-0.1.0-sources.jar`，快速 Shadow 发行目录将它放入 `lib`；`release` 将其单独压缩提供。Maven publication 同时包含该源码制品。

打包配置依据 [Shadow application 集成](https://gradleup.com/shadow/application-plugin/) 及 [Shadow 9.2.2 的启动脚本和发行目录实现](https://github.com/GradleUp/shadow/blob/9.2.2/src/main/kotlin/com/github/jengelman/gradle/plugins/shadow/ShadowApplicationPlugin.kt)。

## 历史运行记录（JDK 21）

此前 Microsoft OpenJDK 21.0.12.1 在 C2 编译 `CompilerObjectJournal.capture` 时发生 JVM 原生崩溃；当时同一发行 JAR 用另一套 OpenJDK 21.0.2 的默认 JIT 设置完成了 MNI 构建。当前工具链已迁移到 JDK 25，上述结果保留为历史记录。

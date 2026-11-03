# MCFPP 工程与 CLI

MCFPP 使用 JDK 21。发行目录的 `bin/mcfpp`（Windows 为 `bin/mcfpp.bat`）提供项目、依赖和构建命令；也可执行 `java -jar MCFPP-1.0.2-SNAPSHOT.jar <命令>`。

## 创建与构建

```shell
mcfpp init hello
cd hello
mcfpp build
```

`mcfpp init hello --mni` 创建 Java MNI 示例及 JUnit 测试。`mcfpp test` 执行 Java 测试和 `src/test/mcfpp` 中的 [MCFPP 单元测试](unit-testing.md)。第一次使用 MNI 时，CLI 自动准备固定的 Gradle 8.14；本机需要 JDK 21，可通过 `MCFPP_JAVA_HOME`、`JDK21_HOME` 或 `JAVA_HOME` 指定。工程工具链版本 `java21-v1` 固定模板、Gradle 和 JDK 要求。

生成数据包和库位于 `build`，Java 中间产物位于 `build/.mcfpp/mni`。`.mcfpp` 保存生成的 Gradle 工程和临时状态，目录可删除并重建。请提交 `mcfpp.toml`、`mcfpp-packages.json`、`mcfpp.lock`，以及启用 MNI 后的 `mcfpp-mni.lock` 和用户扩展脚本。

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
version = "java21-v1"

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

扩展脚本在基础 Java 配置后按顺序加载，可配置已声明插件、依赖和任务。脚本使用 Gradle API，例如：

```kotlin
tasks.named("classes") {
    // dependsOn("generateMySources")
}
dependencies.add("implementation", "org.example:helper:1.0.0")
```

额外插件在 `[mni.plugins]` 声明。CLI 使用扩展生效后的实际运行 classpath 加载本地 MNI；Java 编译失败会停止 MCFPP 编译。

## 首版测试范围

`mcfpp test` 执行 `src/test/java` 的 Java 测试。`src/test/mcfpp` 为预留目录，CLI 明确报告其中的测试未执行。`mcfpp clean` 清理构建输出，保留配置、锁文件及依赖缓存。

## 从源码构建 CLI

先在相邻 Mcfpm 仓库构建并发布 `model`、`core`、`repository-maven` 和 `mcfpp-adapter` 到本地 Maven（SDK 构建使用 JDK 25），再使用 JDK 21 在本仓库运行：

```shell
./gradlew shadowJar installDist
```

发行 JAR 位于 `build/libs`，启动脚本位于 `build/install/mcfpp/bin`。这一步只在本机生成开发发行物。

发行 JAR 保留 Kotlin 反射运行时，可直接使用 `java -jar` 编译泛型 NBT 引用。`./gradlew verifyDistribution` 使用独立 JAR 编译此类示例，检查运行时依赖是否完整。`sourcesJar` 生成配套的 `MCFPP-1.0.2-SNAPSHOT-sources.jar`，`installDist` 将它放入 `lib`；Maven publication 同时包含该源码制品。

## 已知运行时问题

本机 Microsoft OpenJDK 21.0.12.1 在 C2 编译 `CompilerObjectJournal.capture` 时发生 JVM 原生崩溃；同一发行 JAR 已用另一套 OpenJDK 21.0.2 的默认 JIT 设置完成 MNI 构建。遇到相同崩溃时，可切换 JDK，或临时使用 `java -XX:TieredStopAtLevel=1 -jar MCFPP-1.0.2-SNAPSHOT.jar build`。该参数限制 JIT 优化层级，未写入发行物的默认启动参数。

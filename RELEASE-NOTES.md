# MCFPP 0.1.0

2026-11-04 · 首个可分发测试版本 / First experimental test release

## 提供的功能 / Included

- `mcfpp` CLI：创建工程、依赖准备、构建、清理和测试；配置文件使用 schema 1 `mcfpp.toml`。
- 编译为 Minecraft 数据包，包含基础语言、data、泛型、lambda、显式 ScoreRef/NBTRef 及按目标版本选择的标准库。
- Kotlin MNI provider、宏 DSL、显式 `func ... = Provider.method` 绑定及 Kotlin/JUnit 工程模板。
- MCFPP 源码测试与 DAP 调试入口；IDEA 插件单独分发。

The CLI provides project scaffolding, dependency preparation, compilation, cleanup and tests. This release includes the language compiler, target-specific standard library, Kotlin MNI macros and templates, source tests and a DAP debug entry point. The IDEA plugin is distributed separately.

## 安装与制品 / Installation and artifacts

按同目录的 [INSTALL.md](INSTALL.md) 配置 JDK 25 和 PATH，即可在任意工程目录运行 `mcfpp`。

Follow [INSTALL.md](INSTALL.md) to set up JDK 25 and PATH.

| 文件 / File | 用途 / Purpose |
| --- | --- |
| `mcfpp-0.1.0.zip` / `mcfpp-0.1.0.tar.xz` | 完整 CLI，含启动脚本、运行时和安装说明 / Complete CLI distribution |
| `MCFPP-0.1.0.jar` | 可用 `java -jar` 启动的完整运行时 / Standalone runtime |
| `MCFPP-0.1.0-sources.jar` | JVM API 配套源码 / JVM API sources |
| `mcfpp-0.1.0-source.tar.xz` | 当前版本的完整项目源码快照，含未提交的新源码 / Frozen project sources, including new files |
| `SHA256SUMS` / `release-manifest.json` | 版本、源码快照和制品 SHA-256 对应关系 / Version and artifact hashes |

源码快照在构建发行物时从实际工作区生成，包含构建脚本、Gradle wrapper、编译器源码、资源、测试、文档和示例。按 `docs/cli.md` 的源码构建步骤准备 Mcfpm SDK 后，运行 `./gradlew release` 重新打包；本地安装使用 `./gradlew installShadowDist`。

The source snapshot captures the actual workspace used for the release. Prepare the Mcfpm SDK as described in `docs/cli.md`, then run `./gradlew release`; use `./gradlew installShadowDist` for a local installation.

`release` 优先缩小产物：只以生产类裁剪依赖，保留反射入口，将各目标版本的标准库无损分块去重，并使用 Zopfli DEFLATE 和 XZ 压缩。所有目标版本继续提供；构建时逐字节校验全部标准库文件及压缩流。API 源码单独提供。`shadowJar`、`shadowDistZip`、`shadowDistTar`、`installShadowDist` 和 `verifyDistribution` 保留快速验证用途，不执行上述发行压缩。

`release` prioritizes size with production dependency roots, lossless shared standard-library chunks, Zopfli DEFLATE and XZ. All target versions remain available, and the packer verifies every library file and compressed stream. API sources are separate. Existing Shadow tasks remain available for quick packaging and verification.

## 测试范围与已知限制 / Scope and limitations

- 已验证环境为 Windows、JDK 25；MNI 使用 Gradle 9.2.1 和 Kotlin 2.4.10。最终包的定向验证记录随源码仓库的发行验收文档提供。
- 本版为实验性版本，语言、MNI API 和库格式仍可能变化。项目 `version` 表示 Minecraft 目标版本，与编译器版本独立。
- 按本版范围未运行全量回归、真实 Minecraft 验收及性能测试；DSB 执行结果不代表真实游戏验收。
- 首次联网准备 MNI 工具链需要可访问相应下载源；离线模式要求已有完整缓存。
- 启动时可能显示 ANTLR 版本和 JVM 弃用提示，已验证流程可继续完成。

This experimental release may change language, MNI and library interfaces. Targeted validation covers Windows and JDK 25; full regression, real Minecraft execution, performance and other operating systems are outside this release's validation. MNI needs network access on first use and complete caches for offline use. Startup may display ANTLR or JVM deprecation messages.

## 问题反馈 / Reporting issues

请附上 `mcfpp --version`、`java -version`、操作系统、目标 Minecraft 版本、最小源码及配置、执行命令与完整日志；发行制品可用 `SHA256SUMS` 对照。

Include the CLI/Java versions, operating system, target Minecraft version, minimal sources and configuration, exact command and full log. Use `SHA256SUMS` to identify the artifact.

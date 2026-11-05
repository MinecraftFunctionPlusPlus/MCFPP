# DSB 源码调试器验收记录

验收时间：2026-11-03 至 2026-11-04。环境为 Windows、IntelliJ IDEA
2026.2.2（262.10315.125），目标 Minecraft 26.3。首次验收使用编译器 JDK 21.0.2
与 DSB JDK 25.0.4；2026-11-04 已统一到 JDK 25.0.4 并完成以下复验。

## 交付物

- IDEA 安装包：语言支持仓库的 `intellij-plugin/build/distributions/mcfpp-intellij-0.6.0.zip`。
- 独立编译器及 DAP 入口：`build/debugger/compiler-work/build/libs/MCFPP-1.0.2-SNAPSHOT.jar`。
- 可直接打开的示例：`examples/debugger`，包含普通函数、结构化值、递归与单个测试。
- DSB 源码基线、补丁和构建说明：语言支持仓库的 `intellij-plugin/src/main/resources/dps/`。

SHA-256：

| 制品 | SHA-256 |
| --- | --- |
| IDEA ZIP | `ddc6ab7eca2ddf39902c70724af832855054b1a1f368bf96df7b96797d449ffa` |
| 编译器 JAR | `1855d04bb174018b3f34eb4e1ae7b8530e93a563c9a6507de9e7d2f6a4ebb2d8` |
| 语言服务 JAR | `d67dfe33d89409bc7a433ec6f971fb0fe03e457d46358eb6a5e8e99a70bc1f51` |
| DSB JAR | `f8e9589df356f4e6a0a335ddc63e6ab9ac428f20ca949ba23979adf0763ec153` |
| DSB 源码补丁 | `075907a544ac9a136cbb5c0021a70cae28c981cefa5239d67641bc7c26e7632d` |

已读取 ZIP 内嵌编译器、DSB 和工具链清单，校验其哈希与上述制品一致。
编译器在隔离目录构建；验收时逐文件比较 `src/main`，与当前工作区一致。

## 统一 JDK 25 后的复验

构建使用 Gradle 9.2.1、Kotlin 2.4.10 和 JDK 25.0.4。已检查编译器、DAP 适配器及
语言服务的 class major 为 69（Java 25）。编译器 `shadowJar sourcesJar test` 成功，
`DebugMetadataTest` 2 项与 `KotlinMniWorkflowTest` 1 项通过；后者实际完成生成项目的
依赖准备、Kotlin provider 构建、测试、离线锁定重建及预期失败检查。

最终编译器重新运行 `verify-debugger.py --case all`，普通函数、测试生命周期、变量、
递归与错误处理全部通过。未传入 `--dsb-java`，也未设置 `DSB_JAVA`，DSB 自动复用
适配器的 JDK。该次工作目录为 `build/debugger/jdk25-acceptance`。

实际打开 IDEA 的 Tools → MCFPP 设置页，仅选择 `F:/Java/jdk-25.0.4`，DSB 覆盖项
留空。读取运行进程确认语言服务、命令服务、调试适配器和调试运行时全部使用该目录的
`bin/java.exe`。函数断点验证并命中，步过调用后 Variables 与 Watch 显示 `count = 2`；
继续后输出用户消息和 `23`，退出码为 0。设置页及调试截图为
`debugger-jdk25-settings.png`、`debugger-jdk25-step.png` 和 `debugger-jdk25-complete.png`。
进程路径记录为 `build/debugger/jdk25-idea-processes.json`，制品哈希记录为
`build/debugger/jdk25-artifacts.json`。

## 首次验收的自动验证记录

`examples/debugger/verify-debugger.py --case all` 已通过真实 DAP stdio、编译器子进程
和 DSB 子进程验证普通函数、测试生命周期、遮蔽变量、常量、字段、集合索引、递归保存的
调用者变量和只读监视；同时验证空行断点、源码过期、暂停时断开、命令错误、旧运行时拒绝
和编译失败。记录在 `build/debugger/acceptance.log`。

该完整运行使用输出过滤改动之前的编译器。最终编译器另行通过 `--case function`，
覆盖嵌套单步、调用者局部变量、循环再次命中、26.3 浮点值和控制台输出；确认用户的
`say` 只执行一次、显式查询输出 `23`，内部存储反馈不再刷屏。

复用的此前验证：编译器 `DebugMetadataTest` 2 项通过，DSB `LiveDebugSessionTest`
6 项通过；后者覆盖副作用之前暂停、继续仅执行一次、运行中暂停和停止、暂停时关闭、
冻结快照、命令错误、浮点以及依照帧执行上下文读取实体、方块和 `@s` 分数。
DSB 相关实现此后未变。26.3 恢复源码与原内置 JAR 的浮点和谓词针对性对照通过。
源码补丁在干净的 `cc9ae9634813b0087be0d9d41384f679ec240ca5` 上通过应用检查。

## 首次验收的实际 IDEA 操作

在隔离的 IDEA 项目中实际点击函数 Debug 与单个测试的 Debug 菜单，观察到：

- 断点显示验证标记，执行源码行高亮；暂停时继续和单步可用，暂停按钮禁用。
- 普通函数在调用前显示 `count = 1`；步入出现 `increment` 和 `main` 两个逻辑帧及参数
  `value = 1`；步过后 `copy = 1`；步出后回到调用者下一行，变量与 Watch 更新为 `count = 2`。
- 浮点运算后显示 `decimal = 2.0`。结构化值可以展开到 `payload.values[0] = 2`
  和 `[1] = 9`，新增 Watch `payload.values[1]` 显示 `9`；内层遮蔽变量为 `7`，常量为 `42`。
- 单个测试按顺序停在原始 `lifecycle.mcfpp` 的第 4、8、12 行，分别对应准备、测试与收尾。
- 继续至结束后显示用户输出和退出码 0，调试控制按钮随会话结束禁用。
- 最终 ZIP 再次启动普通函数调试，确认控制台日志可读且没有 ANSI 转义字符，
  `Debugger demo finished` 与查询值 `23` 正常显示；退出码为 0。
- 结束会话后专用适配器与 DSB 退出；在暂停状态关闭项目并选择 IDEA 的 Terminate 后，
  适配器、运行时、语言服务与该验收 IDEA 进程全部退出。

窗口截图位于语言支持仓库的 `intellij-plugin/build/debugger-*.png`，其中
`debugger-final-step-out.png`、`debugger-test-aftereach.png` 和
`debugger-structured-watch.png` 分别记录调用返回、测试收尾和结构化监视。

使用方法见 [debugger.md](debugger.md)。

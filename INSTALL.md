# MCFPP 0.1.0 安装 / Installation

这是供早期用户试用的首个测试版本。编译器和 Kotlin MNI 使用 JDK 25。首次使用 MNI 需要联网下载 Gradle、Kotlin 插件和依赖；缓存完整后可使用 `--offline`。

This is the first experimental test release. Install JDK 25 before running the CLI. The first MNI build downloads Gradle, the Kotlin plugin and dependencies; subsequent builds can use `--offline` with a complete cache.

## Windows

1. 将 `mcfpp-0.1.0.zip` 完整解压，例如得到 `C:\Tools\mcfpp-0.1.0`。保留 `bin` 和 `lib` 的相对位置。
2. 打开“编辑账户的环境变量”，设置用户变量 `JAVA_HOME` 为 JDK 25 的安装目录，例如 `C:\Java\jdk-25`。
3. 在用户 `Path` 中添加 `C:\Tools\mcfpp-0.1.0\bin` 和 `%JAVA_HOME%\bin`，然后重新打开终端。
4. 执行 `mcfpp --version`，应显示 `MCFPP 0.1.0 (toolchain java25-v1)`。

Extract the entire ZIP, set `JAVA_HOME` to JDK 25, add the extracted `bin` directory and `%JAVA_HOME%\bin` to your user `Path`, and open a new terminal. The Windows launcher is `bin\mcfpp.bat`; invoke it as `mcfpp` from PowerShell or Command Prompt.

也可先在当前 PowerShell 会话中配置 / To try it in the current PowerShell session:

```powershell
$env:JAVA_HOME = 'C:\Java\jdk-25'
$env:Path = "C:\Tools\mcfpp-0.1.0\bin;$env:JAVA_HOME\bin;$env:Path"
mcfpp --version
mcfpp --help
```

若终端仍调用旧版，执行 `Get-Command mcfpp -All` 检查 PATH 顺序。发行目录可以包含空格；只把 `bin` 加入 PATH，保留完整目录。

If an older installation runs, inspect PATH order with `Get-Command mcfpp -All`. Installation paths may contain spaces; keep the complete distribution together.

## Linux / macOS

解压 `mcfpp-0.1.0.tar.xz`，在 shell 配置文件（如 `~/.bashrc` 或 `~/.zshrc`）中设置以下变量，再打开新终端。替换为自己的解压目录和 JDK 25 路径。

Extract `mcfpp-0.1.0.tar.xz`, add these variables to your shell profile, and open a new terminal. Replace the paths with your installation and JDK 25 directories.

```sh
export JAVA_HOME="/path/to/jdk-25"
export PATH="$HOME/tools/mcfpp-0.1.0/bin:$JAVA_HOME/bin:$PATH"
mcfpp --version
mcfpp --help
```

Unix 启动脚本为 `bin/mcfpp`，TAR 保留其执行权限。Windows 是本版实际验证的平台；其他平台的启动脚本已提供，尚未做系统验收。

The TAR preserves executable permissions for `bin/mcfpp`. Windows is the verified platform for this release; other operating systems have not been validated.

## 第一个工程 / First project

```shell
mcfpp init hello
cd hello
mcfpp build
```

在 `mcfpp.toml` 中设置目标 Minecraft `version`，默认是 `1.21.8`。将生成的 `build/hello` 目录整体复制到世界的 `datapacks` 目录；进入对应版本的世界后执行 `/reload`，再执行 `/function hello:main`。这是使用说明，本版未进行真实 Minecraft 验收。

Set the target Minecraft `version` in `mcfpp.toml` (default `1.21.8`). Copy the generated `build/hello` directory into your world's `datapacks` directory, then run `/reload` and `/function hello:main` in the matching Minecraft version. Actual Minecraft execution was not part of this release's validation.

Kotlin MNI 工程 / Kotlin MNI project:

```shell
mcfpp init hello-mni --mni
cd hello-mni
mcfpp build
mcfpp test
```

MCFPP 源码测试需要另行配置 Datapack Sandbox CLI：设置 `DSB_JAR`，或传入 `mcfpp test --dsb-jar /path/to/datapack-sandbox-cli.jar`。仅运行生成的 Kotlin/JUnit 测试不需要 DSB。编译器和 DSB 默认共用当前 JDK。

MCFPP source tests require a separate Datapack Sandbox CLI JAR: set `DSB_JAR` or pass `--dsb-jar`. The generated Kotlin/JUnit test alone does not require DSB. DSB uses the current JDK by default.

也可使用发行包的 `lib/MCFPP-0.1.0.jar`：`java -jar /path/to/MCFPP-0.1.0.jar --help`。

Alternatively, run `java -jar /path/to/MCFPP-0.1.0.jar --help` using the JAR in `lib`.

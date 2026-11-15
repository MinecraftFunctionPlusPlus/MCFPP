# 编译器工程维护指南

当前工程使用一个 Gradle 模块，按 Kotlin 包划分职责。先从编译入口、阶段顺序和状态所有权定位代码，再进入具体语言特性。

## 入口与编译流程

| 入口 | 职责 | 源码阶段遇错策略 |
| --- | --- | --- |
| `cli/ProjectCli.kt`、`project/` | 工程命令、配置、依赖准备，调用编译会话 | 由 `LibraryCompilerSession` 执行 |
| `library/api/LibraryCompilerSession.kt` | 一次编译的独占权、插件、诊断与产物收集、关闭资源 | 出错后停止后续源码阶段 |
| `library/api/PlaygroundCompilerKernel.kt` | 复用内置库基线，为每次请求创建源码与输出目录 | 出错后停止，恢复基线供下次请求使用 |
| `MCFPP.kt` 中的 `compile(config)` | 直接调用的编译入口 | 继续运行源码阶段，随后检查错误数 |

`compiler/CompilationPipeline.kt` 统一维护源码阶段顺序。它只调度阶段；入口负责配置、初始化、读取库、产物生成和资源关闭。

```mermaid
flowchart LR
    A[入口准备配置与库] --> B[indexType]
    B --> C[resolveField]
    C --> D[runAnnotation]
    D --> E[compile]
    E --> F[optimization]
    F --> G[入口生成库索引和数据包]
```

`Project` 中的各阶段入口负责设置 `compileStage`、处理当前工程的源码，以及在原有阶段边界运行 `stageProcessor`。新增阶段时同步检查阶段枚举、回调位置和公共调度顺序。

## 包与责任边界

| 位置 | 维护内容 |
| --- | --- |
| `Project.kt` | 当前编译单元的生命周期、阶段入口、协调器所有权与对象初始化依赖 |
| `antlr/` | 语法树读取、声明索引、表达式与语句访问 |
| `analysis/` | 绑定、类型与效果分析、Typed IR、模板声明协调、存储事实与 lambda 分析 |
| `model/`、`core/lang/`、`type/` | 作用域、函数与模板模型、语言值及类型标识 |
| `backend/` | 语言操作到 Minecraft 命令的生成逻辑 |
| `command/` | 命令表示、组合、目标版本能力与命令构造 |
| `optimization/` | IR 优化、调用约定、帧规划与命令清理 |
| `mni/` | Kotlin MNI 绑定、宏展开、声明投影与事务回滚 |
| `library/` | 库协议与格式、声明导入导出、宿主 API、包管理桥接 |
| `io/` | 源文件、库文件和数据包读写、资源链接与 IR 输出 |

从 `Project` 提取的实现保持明确输入和状态所有权：

- `library/compiler/LibraryInputs.kt` 按原顺序读取库输入，统一暂存、绑定和提交，再恢复声明环境。它不保存工程状态，也不关闭调用方的会话。
- `backend/RuntimeInitialization.kt` 在已有 load 函数上下文内生成运行时依赖初始化命令。调用方随后追加对象初始化函数体，保证构造器使用的资源已经就绪。
- `optimization/EntrypointFrames.kt` 为已识别的入口应用标量、静态帧或动态栈约定。动态退出点按索引倒序插入出栈命令，最后在入口插入入栈命令。
- `library/api/CompilerHostSettings.kt` 统一保存、设置和恢复嵌入式入口借用的全局设置。快照由具体会话或内核持有。

核心分析与生成通过明确的接口协作：

- `analysis/TemplateDeclarations.kt` 持有声明队列、继承头缓存和完成屏障。它通过 `Context` 读取编译阶段与预加载签名，`Project` 保留既有调用入口；协调器的原始集合及恢复槽继续交给 MNI journal 捕获。
- `analysis/PrimitiveCompiler.kt` 负责源码绑定、调用图与 IR 分析。`backend/PrimitiveBackendBindings.kt` 只向生成器提供声明、类型、调用目标和调试位置目录，避免延迟生成时持有完整语法 lowering 对象。
- `backend/PrimitiveBackend.kt` 负责物理布局与命令生成；`optimization/ResultLiveness.kt` 计算调用处仍需保存的临时结果，`analysis/PrimitiveTypes.kt` 提供双方共用的类型能力判断。
- `analysis/StoredData.kt` 定义存储所有者和地址适配器；`analysis/StorageSnapshots.kt` 负责按身份遍历及恢复存储关系。`StorageAccess` 保留既有读写入口，快照过程不执行读值或物化操作。

## 状态与资源的维护约定

`Project.init()` 开始新的编译单元，清理上一轮缓存并建立新的宏展开预算。`closeCompilation()` 释放编译输入和模型引用；调用方持有的宿主类加载器不属于工程的关闭范围。

库读取时，`Project` 持有导入会话及编译器创建的类加载器。所有输入暂存完成后才绑定和提交，模块列表也在提交成功后发布。读取失败时保留原异常，并把清理失败附加为 suppressed 异常。

模板声明完成屏障、匿名模板、对象初始化依赖和阶段回调可被 MNI 保存点观察。移动这类可变状态时，需要同时检查 `mni/expand/ProjectStateJournal.kt`、`Project.journalState()` 和 `TemplateDeclarations.journalState()`；仅提取无状态算法时，避免新增跨编译单元的缓存。

Playground 的复用基线同时包含编译器模型和库导入目录。请求结束后两者一起恢复，返回的数据包内容已经脱离工程状态。未使用的库成员应保持延迟加载，遍历声明环境时也要保留这一约定。

注释优先说明执行顺序、所有权、延迟加载和回滚原因。新增语言行为应进入对应分析或生成模块，公共入口只负责调度和资源生命周期。

## 验证入口

使用 JDK 25。先按 [CLI 源码构建说明](cli.md#从源码构建-cli) 准备相邻 Mcfpm SDK；按本轮影响范围整理完成后，统一运行所需检查。

| 变更范围 | 可复用检查 |
| --- | --- |
| 工程生命周期与模板协调 | `ProjectIsolationTest`、`ProjectWorkflowTest`、`DataInheritanceCompletionTest`、`MniProjectStateJournalTest` |
| 库导入与宿主入口 | `LibraryImportSessionTest`、`LibraryMcfpmCliIntegrationTest`、`PlaygroundCompilerKernelTest` |
| 运行时初始化与数据包 | `DatapackExperienceTest`、`FloatProviderTest`、`TemplateConstInitializationTest` |
| 入口帧与诊断顺序 | `OptimizationPipelineTest`、`MniCompilerDiagnosticRoutingTest` |
| IR 生成与存储快照 | `PrimitiveIRTest`、集合及控制流 IR 测试、`MniStorageJournalTest`、`MniCompilerStateJournalTest` |

例如，仅修改库导入时可运行：

```powershell
.\gradlew.bat test --tests top.mcfpp.test.library.LibraryImportSessionTest --tests top.mcfpp.test.library.PlaygroundCompilerKernelTest
```

验证重构时保留现有测试的断言，检查编译产物、诊断、初始化顺序和重复编译隔离。通过后，仅在新的相关改动或失败需要时补充检查。

# MNI 公共 API 版本与兼容策略

本文说明独立于编译器发行版的 MNI 公共 API 版本。当前实现位于
[`src/main/kotlin/top/mcfpp/mni/api/ApiVersion.kt`](../../src/main/kotlin/top/mcfpp/mni/api/ApiVersion.kt)，
对应 [`tutorial.md`](tutorial.md) 和 [`README.md`](README.md) 描述的 `top.mcfpp.mni.api` 与 `@MNIMacro` 协议。

## 1. 为什么需要独立版本

项目里至少有三种不同的“版本”：

| 版本 | 含义 | 由谁决定 |
|---|---|---|
| 编译器发行版（如 `build/libs/MCFPP-1.0.3-SNAPSHOT.jar`） | 整个 MCFPP 编译器的构建/发行 | Gradle `version` |
| `MCFPP.VERSION` | 编译器对象中的内部常量 | 编译器源码 |
| `ApiVersion.VERSION` | MNI provider 面向的公共 API 契约 | `ApiVersion.kt` |

`ApiVersion` 不读取 `MCFPP.VERSION`、JAR manifest、Git commit、构建时间或 Gradle 版本，因此：

- 编译器可以发布新构建而 MNI API 不变；
- API 可以在编译器发行版号不变时单独升级；
- 第三方 provider 可以只依赖 API 版本做启动检查，而不是猜测编译器发行版；
- 数值相同只是巧合，不构成关联。例如当前 API 是 `0.2.0-experimental`，与 `MCFPP.VERSION` 的 `0.2.0` 没有同步关系。

`ApiVersion` 是**实验性**标识，不是稳定承诺；`ApiVersion.EXPERIMENTAL` 为 `true`，`STABILITY` 为 `"experimental"`。

## 2. 公开字段与函数

```kotlin
object ApiVersion {
    const val MAJOR: Int = 0
    const val MINOR: Int = 2
    const val PATCH: Int = 0
    const val STABILITY: String = "experimental"
    const val VERSION: String = "$MAJOR.$MINOR.$PATCH-$STABILITY"
    const val EXPERIMENTAL: Boolean = true

    @JvmStatic fun current(): String = VERSION
    @JvmStatic fun isCompatibleWith(required: String): Boolean
}
```

Java 与 Kotlin 都可直接读取：

```java
String version = ApiVersion.VERSION;          // "0.2.0-experimental"
boolean experimental = ApiVersion.EXPERIMENTAL;
String current = ApiVersion.current();
boolean ok = ApiVersion.isCompatibleWith("0.2");
```

```kotlin
val version = ApiVersion.VERSION
val ok = ApiVersion.isCompatibleWith("0.2.0")
```

`isCompatibleWith` 接受：

- `major.minor`，如 `"0.2"`；
- `major.minor.patch`，如 `"0.2.0"`；
- 可选的预发布后缀，如 `"0.2.0-experimental"`（后缀被忽略）。

非法或负数字段返回 `false`，不抛异常。

## 3. 当前兼容规则

当前 `MAJOR = 0`，处于实验期：

```text
required.major == current.major
required.minor == current.minor
required.patch <= current.patch
```

即：

| 当前 API | provider 要求 | 结果 | 说明 |
|---|---|---|---|
| `0.2.0` | `0.2` | 兼容 | 同 major/minor |
| `0.2.0` | `0.2.0` | 兼容 | 精确版本 |
| `0.2.0` | `0.2.0-experimental` | 兼容 | 后缀忽略 |
| `0.2.0` | `0.2.1` | 不兼容 | 要求的 patch 更新 |
| `0.2.0` | `0.3` | 不兼容 | 实验期 minor 是边界 |
| `0.2.0` | `1.0` | 不兼容 | major 不同 |
| `0.2.0` | `0.2.` / `abc` | 不兼容 | 非法输入 |

从 `1.0` 起，同一 major 内较新的当前 minor 兼容较旧的要求；`isCompatibleWith` 已实现该分支，但在 `0.x` 阶段不会触发。

### 3.1 实验期的含义

- 不同 **minor** 之间允许破坏源码/语义兼容；升级前要阅读迁移说明并重新编译 provider。
- 同一 major/minor 的 **patch** 只做向后兼容的修复；provider 要求更旧的 patch 可以在更新 patch 上运行。
- **没有** “实验期绝对不破坏二进制”的承诺；只是当前 `0.2` 线内尽量保持 patch 兼容。
- 直到通过独立验收并发布稳定版本前，不把 API 称为 stable，也不把本文当作完成声明。

### 3.2 兼容检查不是编译器强制

`ApiVersion.isCompatibleWith` 是 provider 的**自检工具**，编译器不会仅凭版本字符串拒绝一个 provider。实际加载仍会校验：

- 入口是否 `public static`；
- 注解 `normalParams` 与 JVM 参数数量、顺序、wrapper 类型；
- 类型是否可解析；
- 同名同参数签名是否冲突；
- 序列化库描述符与当前注解元数据是否一致。

如果 provider 用新 API 编译、运行在旧编译器上，可能出现缺类/缺方法或签名校验失败；启动时检查并给出清晰错误比运行到一半失败更好：

```java
public static String[] importToMNI() {
    if (!ApiVersion.isCompatibleWith("0.2")) {
        throw new IllegalStateException(
            "Provider requires MNI API 0.2, compiler has " + ApiVersion.VERSION);
    }
    // register...
    return new String[0];
}
```

```kotlin
@JvmStatic
fun importToMNI(): Array<String> {
    require(ApiVersion.isCompatibleWith("0.2")) {
        "Provider requires MNI API 0.2, compiler has ${ApiVersion.VERSION}"
    }
    // register...
    return emptyArray()
}
```

## 4. 什么算破坏性变更

| 变更 | 0.x 下的版本动作 | 稳定后的建议 |
|---|---|---|
| 删除/重命名公开类型、方法、注解字段、枚举值 | 提升 minor | major |
| 修改公开方法参数/返回类型、泛型边界或 SAM 形状 | 提升 minor | major |
| 修改 `VALUE`/`REF`/`CONST`/`INLINE` 的求值/捕获/别名语义 | 提升 minor | major |
| 修改上下文独立性、跨 executor 规则或诊断时机 | 提升 minor | major |
| 修改声明屏障顺序、生成成员可见性、事务/回滚范围 | 提升 minor | major |
| 让 provider 必须实现新的抽象方法（如 `TemplateProcessor`/`GeneratedBody` 新增方法） | 提升 minor | major |
| 修改 `@MNIMacro` 的注解形状或 JVM ABI | 提升 minor | major |
| 修改 `ApiVersion.isCompatibleWith` 的判定规则 | 提升 minor | major |
| 只改诊断消息文本、dump 格式、日志、内部实现 | patch | patch/minor |

诊断消息、`CodePrinter` 的 dump 文本、日志前缀和临时名**不是**稳定 API；provider 不应解析它们。公开常量值与枚举名是 API。

## 5. 版本升级流程

### 0.2 显式宏声明迁移

每个宏在 MCFPP 中通过 `func name(parameters): result = Owner.method` 声明。`injectedBy` 不再自动注册宏。注解的 `params = [MacroParam(...)]` 改为 `normalParams = ["int", ...]`，无需重复参数名与模式；模式由 JVM 包装类型推导。公开名称和参数名由源码声明决定，同一个 JVM 方法可以绑定多个名称。旧 provider 需要迁移注解、补充源码声明并重新编译。


### 本次 lambda 源码迁移

普通具名 `func` 声明不变；函数类型统一为 `(int,int)->int`、`()->void`，不带 `func` 前缀。lambda 使用 `{ i -> ... }` 或 `{ i: int -> ... }`；单参数目标可隐式使用 `it`，零参数用 `{ ... }`。迁移反例 `(i) => { ... }` 诊断拒绝，不保留旧可变捕获行为。

自由局部绑定改为创建时按值快照且只读，赋值、复合赋值和引用写入均拒绝。复制遵循既有 `LambdaCaptureRules` / `StorageAccess` 类型契约，不作通用深复制承诺；全局按声明身份识别，使用时保持实时读取和合法写入。尾表达式产生回调结果，分号丢弃值，void 目标丢弃尾值；return 保持局部，break/continue 不能离开回调。INLINE 仅接受静态字面体或普通源码函数引用，动态函数值须明确诊断。实现复用现有 lambda/存储/回收体系。

需要修改局部累加器的旧示例应迁移为显式返回下一值：`repeat(times: int, initial: int, body: INLINE (int,int)->int): int` 传入 `(accumulator,index)`；`raycast(samples: CONST int, initial: int, step: INLINE (int,int)->int, keepGoing: INLINE (int)->bool): int` 在实际 stone 命中时先保存 step 的下一累加器，再用 keepGoing(index) 决定后续采样。原 `repeat(times: int, body: INLINE (int)->void): void` 和 `raycast(samples: CONST int, body: INLINE (int)->bool): void` 保留。固定 0.5 步长与 ambient 上下文保持既有含义，不增加 Result 类型或共享盒。

这是源码/捕获语义的破坏性迁移。本文记录迁移规则，未修改 `ApiVersion` 常量，也不以版本自检结果证明旧源码兼容；版本发布动作由协调者按本节流程处理。

1. 修改 `ApiVersion.MAJOR/MINOR/PATCH` 和 `STABILITY`；`VERSION` 自动跟随。
2. 更新 `api-versioning.md` 的兼容表和 `README.md`/`tutorial.md` 中引用的版本。
3. 对破坏性变更给出迁移说明：旧写法、新写法、诊断变化。
4. 用真实 Java/Kotlin provider 编译和至少一个真实 CLI 展开验证；证据由协调者记录，不在本文伪造。
5. 更新实现契约/源投影契约（如果语义变化）。
6. 保持旧 `@MNIFunction` 路径不变；它不随 `ApiVersion` 自动升级。

版本号的语义：

- `PATCH`：向后兼容的修复、文档/诊断调整；
- `MINOR`：`0.x` 期允许破坏；稳定后表示向后兼容的新增；
- `MAJOR`：稳定后的破坏性变更。

## 6. 与旧 `@MNIFunction` 的关系

`ApiVersion` **只覆盖新 MNI 公共 API**：

- `top.mcfpp.mni.api` 的代码模型、builder、上下文、声明与资源；
- `top.mcfpp.annotations.@MNIMacro`；
- `ApiVersion` 自身。

旧 `@MNIFunction` ABI、`NativeCallContext`、`ValueWrapper` 和 `Var` 包装属于历史兼容层，不受 `ApiVersion` 版本号驱动。保留它们是本阶段边界：

- 旧 provider 继续按旧签名校验和加载；
- 新宏不复用旧 `NativeFunction` 的 eager 实参调用；
- 生成代码可以调用旧 MNI，但只在 lowering 阶段执行；
- 未来若要删除/迁移旧 ABI，需要单独的弃用周期，不能仅靠提升 `ApiVersion`。

## 7. 当前状态

下列保存的 CLI/DSB/回归结果均为 **lambda 迁移前历史证据**，不覆盖新语法、只读局部捕获和累加器重载。本次文档编辑未执行迁移后编译或 DSB，实际结果由协调者另行记录。

- API 版本：`0.2.0-experimental`。
- 版本标识已独立于编译器发行版与构建元数据。
- 版本检查是 provider 自检，不是编译器强制，也不替代入口/签名/类型校验。
- 保存的 [`functional-summary.json`](../../build/inheritance-mni/dsb-check/functional-summary.json) 记录 10 个 Java/Kotlin 示例 case 在 DSB 26.3 下全部功能 PASS：8 例复用 `attempt2`，`repeat` 2 例由 `attempt3` 新执行，原产物哈希未变。性能与真实游戏验收已取消，不是版本或交付门槛；DSB 功能通过也不等于游戏实机结论。
- 运行期字符串 `+` 已由 [`CompilerPlanStrings.kt`](../../src/main/kotlin/top/mcfpp/mni/expand/CompilerPlanStrings.kt) 的平衡转义 helper 实现，语义见 [tutorial.md](tutorial.md) 第 3.5 节。
- 功能回归已发布：全量运行的 6 项失败由受影响定向运行修复，合并证据为 136 项新执行 + 1873 项复用（不是新的 2009 项全量同轮运行）；编译器导出字符串 DSB 也已实际通过。统一结果与复用边界见 [regression-results.md](regression-results.md)。
- 本文不声称 API 已通过独立最终验收；API 版本号保持 `0.2.0-experimental`。统一回归结果见 [regression-results.md](regression-results.md)，最终验收记录见 [acceptance.md](../implementation-review/acceptance.md)。

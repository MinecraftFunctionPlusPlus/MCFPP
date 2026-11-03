# MCFPP 单元测试

在 `src/test/mcfpp` 中直接编写 MCFPP 测试。API 使用 JUnit 风格的函数注解和断言；CLI 将正式源码和测试源码编译为数据包，由本地 [Datapack Sandbox](https://dbs.afox.moe/) 执行。

```mcfpp
import mcfpp.test:*

@Test
func addition() {
    assertEquals(3, add(1, 2))
    assertTrue(add(2, 3) == 5)
}
```

正式源码中的 `add` 可以放在同一命名空间，或使用普通 `import` 引入。测试目录相对路径的命名空间推导与 `src/main/mcfpp` 相同，文件也可以显式声明 `namespace`。

## API

- `@Test`：标记测试函数。
- `@BeforeEach` / `@AfterEach`：同一文件中每个测试的准备与收尾，按源码声明顺序执行。
- `@Disabled` / `@Disabled("原因")`：编译并发现测试，跳过运行。注解参数使用圆括号。
- `assertEquals(expected, actual, message)`：支持 `int`、`bool`、`string`。
- `assertNotEquals(expected, actual, message)`：支持 `int`。
- `assertTrue(actual, message)`、`assertFalse(actual, message)`、`fail(message)`。

所有断言的 `message` 都可以省略。注解用于顶层、无参数、非泛型、返回 `void` 的普通函数。首版使用函数组织测试；不提供测试类、参数化测试或 `BeforeAll` / `AfterAll`。

每例使用全新 DSB 世界，先运行 load 标签，再运行该文件的准备函数、测试和收尾函数。断言会累计失败并继续执行，收尾函数因此能在断言失败后运行。DSB 命令错误会终止当前执行，可能使收尾函数无法执行，但后续测试仍使用独立世界。默认提供名为 `MCFPPTest`、位于 `[0, 64, 0]` 的测试玩家。

## 运行

编译器使用 JDK 21；当前 DSB CLI 使用 Java 25。下载 [DSB CLI](https://dbs.afox.moe/workflows/installation.html)，指定其本地路径和 Java 可执行文件：

```powershell
$env:DSB_JAR = 'C:\tools\datapack-sandbox-cli.jar'
$env:DSB_JAVA = 'C:\tools\jdk-25\bin\java.exe'
mcfpp test
mcfpp test --tests 'example.tests:add*'
mcfpp test --list
```

也可传入 `--dsb-jar <路径>`、`--dsb-java <可执行文件>`；后者默认使用 PATH 中的 `java`。`--list` 只编译并发现测试，不启动 DSB。

`mcfpp test` 在启用 MNI 时先执行现有 Java 测试，再执行 MCFPP 测试。`--tests` 使用 `命名空间:函数名` 和 `*` 筛选，只执行 MCFPP 测试；筛选无匹配或测试源码没有 `@Test` 时返回非零状态。`--offline`、`--locked` 沿用工程依赖规则。

产物位于 `<targetPath>/tests/mcfpp`：`discovery.json` 是带源码位置的发现结果；`results.json` 是 schema 1 的逐例结果；`TEST-mcfpp.xml` 可由 CI 的 JUnit 报告工具读取。`cases/<序号>` 保存各例的 DSB Manifest、报告和日志。编译或执行失败均返回非零状态，禁用用例记为 skipped。普通 `mcfpp build` 只编译正式源码。

可运行示例位于 [`examples/unit-testing`](../examples/unit-testing)。

## IntelliJ IDEA

所有工程都提供 **MCFPP Test** 运行配置。插件复用内置 DSB JAR 和 **Settings → Tools → MCFPP** 中的 DSB Java 设置。`@Test` 函数名旁的运行按钮执行单个测试，结果出现在测试树中，可跳转到原始 `.mcfpp` 文件。运行配置的额外参数也可使用 `--tests`。测试 API 的源码随插件提供，用于断言补全和声明导航。

DSB 接入使用官方 [Manifest 协议](https://dbs.afox.moe/en/reference/manifest)：每个用例独立加载编译产物，调用生成的入口，并断言失败计数为零。命令支持范围取决于所用 DSB 版本；具体错误保存在用例报告中。

# 标准库运行检查

`StandardLibraryRuntimeTest` 使用正式的 DatapackCreator 导出数据包及六份 DSB manifest，输出在 `build/stdlib-expansion`。覆盖字符串、集合、map、库导出后重新导入、三个示例，以及实体和物品命令。

仓库使用的 DSB jar 在 `build/inheritance-mni/dsb-runtime/datapack-sandbox-cli.jar`。准备方式见 [MNI 运行检查](../../examples/mni/benchmark/README.md)。在仓库根目录运行：

```powershell
.\gradlew.bat test --tests top.mcfpp.test.StandardLibraryRuntimeTest
$runtimeJar = 'build/inheritance-mni/dsb-runtime/datapack-sandbox-cli.jar'
javac -cp $runtimeJar -d build/stdlib-expansion/dsb-compat tools/stdlib-dsb-compat/CommandTokenizer.java 'tools/stdlib-dsb-compat/NbtPath$Companion.java'
java -cp "build/stdlib-expansion/dsb-compat;$runtimeJar" examples/mni/benchmark/DsbFunctionalCheck.java --minecraft-data-results --output build/stdlib-expansion/runtime-report.json build/stdlib-expansion/values/values.dps.json build/stdlib-expansion/imported/imported.dps.json build/stdlib-expansion/example-rewards/example-rewards.dps.json build/stdlib-expansion/example-area-skill/example-area-skill.dps.json build/stdlib-expansion/example-score-game/example-score-game.dps.json build/stdlib-expansion/commands/commands.dps.json
```

检查使用 DSB 执行生成的函数并断言世界、记分板、物品和 storage 的实际状态，没有预置接口结果。当前 jar 的三处行为通过验证专用适配处理：

- CommandTokenizer 保留 NBT 路径中带引号的节点及其后缀。
- NbtPath 的解析器支持带引号的字段；路径读取、写入和匹配继续使用原 DSB 实现。
- `--minecraft-data-results` 将 data get 的集合大小、UTF-16 字符串长度及布尔值查询转换为 execute store 所需的数值。它同时省略反馈中的重复状态树，保留输出条目及其结果值。

这些检查属于沙箱执行，不能代替原版服务端联机检查。三个示例各自的 README 提供原版世界的构建、入口和预期结果。

# 重构基线（2026-10-04）

基线包括任务开始时工作区已有的浮点数值提供器和算术修改。未回退这些修改。
为隔离重构期间的文件变化，在 `/tmp/mcfpp-baseline-src` 从 HEAD、初始工作区补丁以及
FloatProviders.kt / FloatProviderTest.kt 重建原始源码，执行：

```sh
./gradlew check --rerun-tasks -Dorg.gradle.jvmargs=-Xmx2g -Pkotlin.daemon.jvmargs=-Xmx2g
```

结果：65 个测试，0 失败，0 错误，0 跳过；8 个任务实际执行，没有缓存命中。
浮点基线包含 10 个断言测试，算术语法包含 8 个断言测试。

## 已确认问题

- 类型相等依赖 `typeName`；dict / map 的身份和哈希不一致。
- 集合类型哈希为 `genericHash xor genericHash`，始终为零。
- 可变集合允许协变。
- Byte.build 忽略实参并生成零；double / LongArray 注入了 byte 的成员。
- 普通默认参数匹配反向；泛型匹配同时存在反向和取反错误。
- 单一候选函数直接返回，不验证实参。
- 默认参数匹配会访问参数列表末尾之外。
- 类型、声明身份、常量信息和存储耦合，分支仍会遍历作用域物化所有常量。
- 部分旧测试只打印编译结果，不断言 Project.errorCount；Gradle 通过不等于所有输入语言程序无诊断。
- 标准库二进制索引无格式版本，结构改变会产生不可理解的 Kryo 错误。

## 验证范围

基线浮点测试使用命令结构断言和独立的小型命令执行器。
没有配置可运行的目标 Minecraft 服务端；服务端验证尚未完成，不能由执行器替代。
旧浮点模拟库与宿主浮点的完整等价性尚未验证。

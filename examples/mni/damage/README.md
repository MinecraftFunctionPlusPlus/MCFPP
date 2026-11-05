# MNI `damage` 示例

`damage(scoreRef("#normal", "hp"), amount)` 接收普通 `VALUE ScoreRef` 和 `VALUE int`。引用描述固定的 Minecraft scoreboard 地址；参数从左到右求值一次。provider 调用源码中的 `applyDamage`，由 `tryRead` 得到普通 int 快照，再用 `set` 写回同一目标。

示例覆盖引用工厂直接作为宏实参、宏转发到普通源函数，以及在 `tryRead` 回调中再次调用宏。回调中的引用描述保留原目标，分支中的 `set` 可以写回该目标。

`ref local` 已移除。编译器内部的 `Ref<T>` / `ParamMode.REF` 位置能力继续存在，公开地址值通过 VALUE 传递。

Kotlin provider 同时提供 `damage` 和 `damageNbt`。NBT 示例见 [nbt-reference](../nbt-reference/README.md)。

## 构建

在仓库根目录使用 JDK 25 构建运行时：

```bash
./gradlew shadowJar mniExamplesJar
```

`mniExamplesJar` 编译实际 Kotlin 示例并打包为 `build/libs/mni-example-providers.jar`，示例配置直接加载此 JAR。`project/src/main.mcfpp` 使用 `func damage(health: ScoreRef, amount: int): void = example.mnidamage.KotlinDamageMacro.damage` 显式绑定。

## 运行与结果

```bash
cd examples/mni/damage
java -jar ../../../build/libs/MCFPP-0.1.0.jar build --project mcfpp.toml
```

执行生成的 main 后：

| Scoreboard 地址 | 结果 |
| --- | --- |
| `#normal hp` | `70` |
| `#low hp` | `0` |
| `#alias hp` | `0` |

引用赋值复制描述；`set` 写目标。`tryRead` 的回调只在读取成功时同步执行。普通源函数及访问 helper 可以生成运行时调用。

## 验证

在仓库根目录执行：

```bash
./gradlew test --tests top.mcfpp.test.MniReferenceExampleTest
```

回归测试读取原始示例源码，导出数据包，并使用仓库的命令执行器运行生成的函数，断言上表中的最终数值。此版本也已通过上面的 CLI 构建命令。

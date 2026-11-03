# MNI `damage` 示例

`damage(scoreRef("#normal", "hp"), amount)` 接收普通 `VALUE ScoreRef` 和 `VALUE int`。引用描述固定的 Minecraft scoreboard 地址；参数从左到右求值一次。provider 调用源码中的 `applyDamage`，由 `tryRead` 得到普通 int 快照，再用 `set` 写回同一目标。

`ref local` 已移除。编译器内部的 `Ref<T>` / `ParamMode.REF` 位置能力继续存在，公开地址值通过 VALUE 传递。

Java 与 Kotlin provider 均同时提供 `damage` 和 `damageNbt`。NBT 示例见 [nbt-reference](../nbt-reference/README.md)。

## 构建

在仓库根目录使用 JDK 21 构建运行时：

```bash
./gradlew shadowJar
```

Java provider：

```bash
javac -cp build/libs/MCFPP-1.0.2-SNAPSHOT.jar \
  -d examples/mni/damage/build/classes \
  examples/mni/damage/provider/java/example/mnidamage/JavaDamageMacro.java
jar --create --file examples/mni/damage/build/damage-macro.jar \
  -C examples/mni/damage/build/classes .
```

或使用 Kotlin provider：

```bash
kotlinc examples/mni/damage/provider/kotlin \
  -cp build/libs/MCFPP-1.0.2-SNAPSHOT.jar \
  -d examples/mni/damage/build/classes
jar --create --file examples/mni/damage/build/damage-macro.jar \
  -C examples/mni/damage/build/classes .
```

`project/src/.mcfpp` 默认注入 Kotlin provider；使用 Java 时改为 `//> injectedBy example.mnidamage.JavaDamageMacro`。同一命名空间选择一个 provider。

## 运行与结果

```bash
cd examples/mni/damage
java -jar ../../../build/libs/MCFPP-1.0.2-SNAPSHOT.jar build --project mcfpp.toml
```

执行生成的 main 后：

| Scoreboard 地址 | 结果 |
| --- | --- |
| `#normal hp` | `70` |
| `#low hp` | `0` |
| `#alias hp` | `0` |

引用赋值复制描述；`set` 写目标。`tryRead` 的回调只在读取成功时同步执行。普通源函数及访问 helper 可以生成运行时调用。

旧 `ref local` 版本的历史 CLI/DSB 证据不适用于此次源码和 provider 迁移。当前版本的实际验证由本次集成记录报告；本页未重新执行构建或 CLI。

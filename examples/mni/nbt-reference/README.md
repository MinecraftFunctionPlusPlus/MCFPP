# MNI `NBTRef<int>` 示例

本示例复用 [damage provider](../damage/README.md) 的 `damageNbt` 宏。它接收普通 `VALUE NBTRef<int>`，将地址描述传给源码函数 `applyNbtDamage`，通过同步 `tryRead` 和 `set` 修改显式命名存储。

```mcfpp
func damageNbt(health: NBTRef<int>, amount: int): void = example.mnidamage.KotlinDamageMacro.damageNbt
damageNbt(nbtStorageRef<int>("example:health", "normal.hp"), 30)
```

路径是完整的 Minecraft NBT 路径；`values[1]` 指向该路径当前的元素。普通局部变量、data 字段与编译器列表不开放 `ref` 取地址。

## 构建与运行

先按 [damage README](../damage/README.md) 构建 Kotlin provider。`mcfpp.toml` 使用同一 JAR：

```toml
jars = ["../../../build/libs/mni-example-providers.jar"]
```

```bash
cd examples/mni/nbt-reference
java -jar ../../../build/libs/MCFPP-0.1.0.jar build --project mcfpp.toml
```

执行 `/function example.nbtreference:main` 后，命名存储 `example:health` 中的结果为：

| NBT 路径 | 结果 |
| --- | --- |
| `normal.hp` | `70` |
| `alias.hp` | `10` |
| `values[0]` | `10` |
| `values[1]` | `15` |

`alias.tryRead` 提供普通值快照；后续写入同一引用不会回改该快照。引用描述可保存和传递，引用重赋会更换描述。

此版本已通过上面的 CLI 构建命令。[damage 的验证命令](../damage/README.md#验证) 同时运行本例的回归测试：读取原始源码、导出数据包，并用仓库的命令执行器检查字段、列表元素和嵌套回调写入后的最终数值。

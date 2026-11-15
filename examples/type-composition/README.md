# 类型组合与工厂示例

使用 JDK 25 和目标版本 26.3。在本目录运行：

```shell
mcfpp build
```

也可从仓库根目录执行：

```shell
mcfpp build --project examples/type-composition/mcfpp.toml
```

将生成的 `build/type-composition` 数据包及构建输出中的标准库数据包一起加载，执行：

```mcfunction
function example.type_composition:main
```

预期依次输出 `3`、`7`、`11`、`4`、`9` 和 `spawn`。

- `Marker: Position, Named` 将 `x`、`y`、`name` 与本地 `active` 合并到同一个对象，工厂直接返回完整字段。父工厂不会自动运行。
- `total` 接受 `Position`，也接受具有 `x`、`y` 的 `Marker` 和多出 `extra` 字段的对象字面量。
- `{ x, y }` 使用当前局部变量；`label` 的参数直接写为对象字段合同 `{ name: string }`。
- `Defaults()` 使用字段初值生成的零参数工厂；`Defaults.new(9)` 使用显式重载。

语义与迁移规则见[继承合同](../../docs/inheritance/implementation-contract.md)和[类型系统迁移指南](../../docs/type-system/migration.md)。

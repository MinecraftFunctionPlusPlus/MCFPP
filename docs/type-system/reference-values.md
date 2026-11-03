# ScoreRef 与 NBTRef<T>

`ScoreRef` 和 `NBTRef<T>` 是可以保存、复制、传参和返回的显式 Minecraft 地址。`ScoreRef` 的读取值类型为 `int`；`NBTRef<T>` 的读取值类型为 `T`。

```mcfpp
import mcfpp.lang:*

const points: ScoreRef = scoreRef("#example", "points")
points.set(0)
var found = points.tryRead { value ->
    points.set(value + 1)
}

var saved: NBTRef<int> = nbtStorageRef<int>("example:data", "counter")
saved.set(10)
```

`set(value)` 按目标值类型检查赋值，返回 `void`。`tryRead { value -> ... }` 返回 `bool`，读取一次，成功后同步调用正文。存在的零值读取成功；缺失分数、实体或 NBT 路径时返回 `false`，跳过正文并清理临时结果。正文继承调用点的执行者、位置、旋转和维度。回调参数是普通值快照；修改参数不会自动写回地址。

## 创建地址

| 入口 | 地址 |
| --- | --- |
| `scoreRef(holder, objective)` | 显式固定 scoreboard holder 与 objective |
| `nbtStorageRef<T>(storageId, path)` | 命名 storage 与 NBT 路径 |
| `nbtEntityRef<T>(target, path)` | 既有 entity 目标与 NBT 路径 |
| `nbtBlockRef<T>(dimension, x, y, z, path)` | 明确维度、绝对整数坐标与 NBT 路径 |

实体 schema 投影也生成引用：

```mcfpp
var target = entityByName("Alice")
var health: ScoreRef = target.score["points"]
var air: NBTRef<short> = target.dat.air
target.dat.air = 8s
health.set(10)
```

字段和索引投影保留 schema 的实际 NBT 字段名、引号和索引语义。构造地址时，目标参数、objective、路径和动态索引从左到右求值并捕获一次；之后修改源变量不会重写已保存的地址。选择器在每次访问时按原有 entity 规则查询。列表内容移动后，引用仍访问原来的索引位置。

工厂的 `path` 是完整 Minecraft NBT 路径。例如名为 `a.b` 的单个字段需使用路径字符串 `"\"a.b\""`。有 schema 时可直接使用字段或字典索引投影，编译器负责引用键名。

## 复制与作用域

引用赋值复制地址描述。`var copy = saved` 后，两个引用访问同一地址；`saved = other` 只更换 `saved` 的地址。`const` 限制引用的重赋，仍可使用 `set` 写入目标。`ScoreRef` 与 `NBTRef<int>` 类型独立；可写的 `NBTRef<T>` 对 `T` 不变。

`NBTRef<T>` 要求 `T` 具有运行期存储布局。外部 NBT 符合该布局由调用方保证；`tryRead` 报告命令读取是否成功，不递归检查外部载荷的全部类型结构。

实体读取沿用现有单目标要求；多目标写入沿用 entity 消费者的写入规则。由 `ctx.executor` 派生的引用携带同步借用作用域，允许在活跃作用域内传递和使用；包装、擦除或嵌套存储不能使其逃出作用域。

公开的 `ref expression` 调用语法已移除。原先对普通局部变量取引用的代码应改为显式 Minecraft 地址，并通过 `set` / `tryRead` 访问。

可编译项目见[显式地址引用示例](../../examples/references/README.md)。

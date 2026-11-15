# 实体子类型、展示属性与调度

实体模板包括 `Player`、`Zombie`、`ItemDisplay` 等，基类为 `Entity`。引用使用 `EntityRef<T>`；三种展示实体的模板共同继承 `DisplayEntity`。自定义声明与生成方式见[实体模板与引用](entity-templates.md)。

```mcfpp
const id = UUID("0-0-0-0-1")
const display = entityByUUID<ItemDisplay>(id)
if (!display.exists()) {
    summon<ItemDisplay>(~ ~ ~, {UUID: id, item: {id: "minecraft:stone", count: 1}})
}
```

类型参数由调用者保证正确。`entityByUUID<T>` 和显式 `as` 不执行种类检查。`target.checkType<T>()` 实际查询运行时实体；不存在或种类不符时返回 `false`。`checkType<DisplayEntity>()` 接受三种展示实体。查询要求至多一个目标，多目标请进入 `withEntityCtx` 逐个检查。检查返回布尔值，之后需要具体类型时可显式转换。

`UUID` 是具有独立类型身份的四整数值。`UUID(string)` 接受五段十六进制文本（包括短段），`UUID(IntArray)` 接受四整数。它可作为参数、返回值和闭包捕获，也可直接嵌入 NBT。已知非法输入产生编译错误；非法动态输入产生诊断并保持无效值，不会变成全零 UUID。原有 `entityByUUID(string)` 入口继续可用。

## 展示实体属性

属性通过 setter 写回游戏中的实体。`const` 固定引用，允许通过这些属性操作实体。

```mcfpp
display.transform.y = 4.0
display.transform.scale.x = 2.0
display.transform.leftRotation.w = 1.0
```

`transform.x/y/z` 是显示模型的平移分量，独立于实体位置。`scale.x/y/z` 是缩放，`leftRotation/rightRotation` 使用四元数的 `x/y/z/w` 分量。修改分量保留其余变换。

可将 `DisplayVector3` 赋给 `transform.scale`，将 `DisplayQuaternion` 赋给两个旋转属性，一次写入整个向量或四元数。向量初始分量为零，四元数初始为单位旋转。

公共显示属性：

| 属性 | 值 |
| --- | --- |
| `billboard` | `DisplayBillboard.fixed/vertical/horizontal/center` |
| `brightness` | `DisplayBrightness`，包含 `block`、`sky`；赋 `null` 恢复环境光 |
| `viewRange` | 显示距离系数 |
| `shadowRadius`、`shadowStrength` | 阴影半径、强度 |
| `width`、`height` | 渲染裁剪范围 |
| `glowColorOverride` | 发光轮廓 RGB 整数，`-1` 使用队伍颜色 |
| `interpolationDuration` | 变换插值时长，单位 tick |
| `startInterpolation` | 只写的插值触发属性，映射原版 `start_interpolation` |
| `teleportDuration` | 实体位置和朝向插值时长 |

三种内容接口：

| 类型 | 属性 |
| --- | --- |
| `ItemDisplay` | `item: ItemStackData`、`itemDisplayMode: ItemDisplayMode` |
| `BlockDisplay` | `blockState: BlockState` |
| `TextDisplay` | `text`（富文本或字符串）、`lineWidth`、`textOpacity`、`background`、`defaultBackground`、`shadow`、`seeThrough`、`alignment` |

`ItemDisplayMode` 对应原版九种物品显示模式，`TextAlignment` 提供 `center/left/right`。枚举需导入 `mcfpp.minecraft.entity:*`。`background` 为 ARGB 整数。`textOpacity` 对外使用 `0..255`，后端处理 NBT byte 的有符号编码；实际透明度渲染仍遵循原版规则。

读取沿用实体引用的 `tryRead` 契约：

```mcfpp
display.transform.y.tryRead { height ->
    // height 是本次读取的 float 快照。
}
```

实体不存在时不进入回调。已有实体省略可默认的显示字段时，读取提供该字段的原版默认值。`brightness` 无覆盖时没有可读的亮度值，`startInterpolation` 只能写入。属性 setter 不隐式重启插值；需要时显式写入 `startInterpolation`。

## tick、loop 与 schedule

```mcfpp
tick { frame ->
    display.transform.y = (1.0 - bounce(frame / 50.0)) * 4.0
    return frame < 50
}

loop(10) { frame ->
    return frame < 2
}

schedule(20) {
    /say after-20-ticks
}
```

`tick` 从下一 tick 开始，每 tick 执行一次；`loop(t)` 等待 t tick 后首次执行，此后每隔 t tick 执行。`loop(1)` 等价于 `tick`。参数 `frame` 是从零开始的执行序号，返回 `false` 后停止并释放任务环境。

`schedule(t)` 延迟执行一次，`schedule(0)` 在当前调用处同步执行。`loop` 要求正间隔，`schedule` 要求非负延迟。创建任务时固定间隔、延迟和局部捕获值。局部捕获是只读快照，示例通过 `frame` 计算进度；省略参数列表时可使用隐式参数 `it`。每次登记创建独立实例；本轮回调新登记的延迟任务从下一轮开始计时。

延迟回调使用服务器调度入口的执行上下文，普通 `@s` 在执行时解析；需要的实体应显式捕获。`schedule(0)` 保留当前上下文。数据包加载时清空旧任务登记。

完整示例位于 `examples/display-animation`。`main` 的帧序号为 `0..50`，共更新 51 次，最后写入高度零。

参考：[Minecraft Wiki：Display](https://minecraft.wiki/w/Display)、[官方展示实体说明](https://feedback.minecraft.net/hc/en-us/articles/13987663727757-Minecraft-Java-Edition-1-19-4)、[位置插值](https://feedback.minecraft.net/hc/en-us/articles/19703470383757-Minecraft-Java-Edition-1-20-2)、[文本组件格式变更](https://feedback.minecraft.net/hc/en-us/articles/35298208390797-Minecraft-Java-Edition-1-21-5-Spring-to-Life)。

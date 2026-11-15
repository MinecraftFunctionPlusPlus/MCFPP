# 实体值与执行上下文

上下文由函数依赖声明和同步入口共同管理。完整示例见 [实体上下文示例](../examples/entity-context/README.md)；本文也说明从旧 `execute(...)` 语法迁移的方法。

## 声明函数需要的上下文

```mcfpp
import mcfpp.lang:*
import mcfpp.minecraft.resource:*
import mcfpp.minecraft.std:*

context(ctx: PosContext)
func test() {
    var pig = EntityType()
    pig.id = "minecraft:pig"
    summon(pig, ctx.pos)
}

func main() {
    withPosCtx(~ ~1 ~) { space -> test() }
}
```

`context` 是调用要求，即使正文没有使用 `ctx`，调用者也必须提供它。受控入口的回调参数自动加入词法候选；普通局部变量和普通函数形参不自动加入。候选按最近词法层查找，同层存在多个可匹配来源时报告歧义，可用 `test(ctx = outer)` 指定，或用 `context(outer) { test() }` 在块内显式提供。

普通函数正文只使用自己声明的上下文依赖。调用点碰巧处在某个回调里，不会让普通函数获得未声明的依赖。普通调用及 `context(value) { ... }` 只传递依赖；切换 Minecraft 命令环境使用下面的入口 API。

函数值的类型保留依赖，例如 `context(PosContext) () -> void`。依赖在调用函数值时解析；函数类型之间的上下文列表按顺序保持一致。命名上下文实参与普通实参分别传递，并按源码顺序求值；MNI 的 `NativeCallContext.contextArguments` 单独暴露上下文。

## 类型与成员

采用具名类型的继承关系；本次设计不增加与类型语法。

| 类型 | 父类型 | 提供的成员 |
| --- | --- | --- |
| `Context` | — | 来源与同步生命周期 |
| `DimensionContext` | `Context` | `dimension` |
| `PosContext` | `DimensionContext` | `pos`、`dimension` |
| `RotationContext` | `Context` | `rotation` |
| `AnchorContext` | `Context` | `anchor` |
| `EntityContext` | `Context` | 必定存在的 `executor` |
| `ExecutionContext` | `PosContext`、`RotationContext`、`AnchorContext` | 完整空间，内部允许执行者缺失 |
| `EntityExecutionContext` | `ExecutionContext`、`EntityContext` | 完整空间和必定存在的执行者 |
| `RelationContext` | `EntityExecutionContext` | 关系分支及关系来源 |

按声明类型检查成员。将完整帧作为 `PosContext` 参数传入后，函数只能访问位置和维度；`ExecutionContext` 本身不能读取 `executor`。选择器访问表达式与进入分支后绑定的执行者身份具有不同语义。

## 从乘客示例开始

```mcfpp
var qwq = @s

qwq.withPosCtx { posCtx ->
    posCtx.onPassengers { psgCtx ->
        tp(psgCtx.executor, posCtx.pos)
    }
}
```

有当前执行者时，这段代码将其每只直接乘客传送到该执行者的位置。核心命令语义是：

```mcfunction
execute at @s on passengers run tp @s ~ ~ ~
```

默认优化会在 IR 阶段将这个单命令示例直接合并为上述命令，消除未读取的命令结果和不需要的上下文帧。多语句回调保留一个末端函数，保证每个分支按顺序执行完整正文；局部 `return`、祖先保存、动态操作数准备和清理会保留必要边界。可用 `--disable-pass=context-region-fusion` 关闭上下文融合，或用 `-O0` 关闭可选优化。

运行时维度等动态入口生成的宏正文也参与融合；操作数仍在原调用点准备。若普通回调包含字面量 `$(...)`，编译器保留其函数边界，防止该文本被解释成入口宏的参数。

`onPassengers` 不递归遍历孙辈乘客；没有执行者或没有直接乘客时不执行关系回调。回调结束后，后续语句继续使用进入调用前的执行上下文。

这里只使用 `{ 参数 -> 正文 }` 标准 lambda。上下文 API 根据接收者推导参数类型，不需要额外的缩写语法。

## 普通 entity 保存访问方式

普通实体值统一使用 `entity`：

```mcfpp
var current: entity = @s
var named = entityByName("Alice")
var identified = entityByUUID("0-0-0-0-1")
var copied = current
```

实体值可保存选择器、玩家名或 UUID 文本。名字和 UUID 使用明确的工厂构造；任意字符串不会隐式成为选择器。玩家名接受 1–16 个 ASCII 字母、数字或下划线；UUID 文本接受五段非空十六进制数，各段最大宽度为 `8-4-4-4-12`，可使用示例中的短段。

赋值、参数传递、返回与普通 lambda 捕获复制访问表达式及构造时捕获的参数。以后改变源变量不会改写副本。实际实体在消费该值时查询：例如保存的 `@s` 在使用点解析为使用点的执行者，保存的选择器也会按使用点的世界状态查询。复制 entity 不会保存当时选中的实体集合。需要保留某个回调分支的执行者身份时，使用下文的 `ctx.executor`。

编译器保守维护“至多一个目标”“仅玩家”“已知实体种类”等事实。这些事实不表示目标必定存在，也不产生不同的 `entity<N>` 类型。未知函数形参不会自动获得单目标保证；`@p` 等选择器也必须结合实际 `limit` 判断，运行时未知 `limit` 按可能多目标处理。需要逐实体操作时，进入 `withEntityCtx`；玩家专用操作仍要求玩家事实，单个未知种类实体不足以满足它。实体数据成员按已知种类提供；种类未知时只提供公共成员。

## 执行者和空间分别改变

| 调用 | 回调参数 | 执行语义 |
| --- | --- | --- |
| `target.withPosCtx { ctx -> ... }` | 完整帧 | `execute at target`：设置位置、旋转、维度，保留调用点执行者 |
| `target.withEntityCtx { ctx -> ... }` | `EntityExecutionContext` | `execute as target`：设置执行者，保留调用点位置、旋转、维度 |
| `ctx.onPassengers { next -> ... }` | `RelationContext` | 从接收者绑定的执行者访问直接乘客，每只乘客一个分支 |
| `ctx.onVehicle { next -> ... }` | `RelationContext` | 从接收者绑定的执行者访问其直接载具，零或一个分支 |
| `ctx.onOrigin/onOwner/onController/onLeasher/onTarget/onAttacker` | `RelationContext` | 访问对应 Minecraft 直接关系，零或一个分支 |

最后一行表示各自独立的方法，例如 `ctx.onOwner { owner -> ... }`。关系可用性按编译目标检查；当前声明的关系要求 Minecraft 1.19.4 或更新的目标。

完整帧的类型根据声明可证明的执行者能力选择 `ExecutionContext`、`EntityExecutionContext` 或保留关系的 `RelationContext`。上下文只读，入口产生子上下文。普通 lambda 不会因嵌套而自动改变 Minecraft 环境。

其他修饰器也使用同步回调，均保留未被该修饰器写入的分量，退出后恢复调用点环境：

| 调用 | execute 修饰器 |
| --- | --- |
| `withCurrentCtx { ctx -> ... }` | 获取当前帧 |
| `withPosCtx(pos3 或 ContextPosition) { ctx -> ... }` | `positioned`，绑定位置同时恢复其维度 |
| `target.withPositionedCtx { ctx -> ... }` | `positioned as`，保留当前旋转和维度 |
| `withRotationCtx(rotation(yaw, pitch, relativeYaw, relativePitch)) { ctx -> ... }` | `rotated` |
| `withRotationCtx(frame.rotation) { ctx -> ... }` | 进入绑定旋转 |
| `target.withRotationCtx { ctx -> ... }` | `rotated as` |
| `withFacingCtx(pos3) { ctx -> ... }` | `facing` |
| `target.withFacingCtx(Anchor.feet 或 Anchor.eyes) { ctx -> ... }` | `facing entity` |
| `withDimensionCtx(Dimension 或 frame.dimension) { ctx -> ... }` | `in`，保留原版维度坐标缩放 |
| `withAlignedCtx(Axes.xz) { ctx -> ... }` | `align`，支持七种非空轴组合 |
| `withAnchorCtx(Anchor.eyes 或 frame.anchor) { ctx -> ... }` | `anchored` |
| `withHeightmapCtx(Heightmap.ocean_floor) { ctx -> ... }` | `positioned over`，支持四种高度图 |
| `withSummonedCtx(EntityType) { ctx -> ... }` | `summon`，回调执行者为新实体 |

资源参数使用标准库 `Dimension`、`EntityType` 值；可携带运行时资源 ID。旋转工厂和绑定位置的数字偏移目前要求编译期已知且有限的数值；轴、高度图、锚点选项使用相应枚举。来自不透明外部执行环境、无法重建的独立维度或锚点会报告诊断；恢复祖先位置及其维度可使用 `withPosCtx(frame.pos)`。`positioned over`、`summon` 与关系操作按目标版本检查能力。本次入口覆盖 execute 的上下文修饰器；条件与结果存储继续使用现有命令接口。

`withPosCtx` 的目标与调用点执行者可以不同。例如：

```mcfpp
entityByName("Alice").withPosCtx { space ->
    space.onPassengers { rider ->
        tp(rider.executor, space.pos)
    }
}
```

这里设置的是 Alice 的空间；关系仍从调用点继承的执行者出发。若要访问 Alice 自己的乘客，先建立执行者上下文：

```mcfpp
entityByName("Alice").withEntityCtx { actor ->
    actor.onPassengers { rider ->
        tp(rider.executor, actor.pos)
    }
}
```

此时 `actor.pos` 是调用点空间。若也要使用 Alice 的位置，需要再显式调用 `withPosCtx`。

关系操作只改变执行者，继承**调用关系方法时**的空间。即使在更深层调用 `outer.onPassengers`，关系起点仍是 `outer.executor`，空间来自当前调用点。空目标、空关系不进入回调；不同起点到达同一目标时保留各分支的调用次数，不自动去重。

## 绑定句柄与祖先上下文

`ctx.executor` 是带来源的实体身份句柄；更深层更换执行者后，它仍访问原分支实体，不会退化为当前 `@s`。它可传入 `tp`、实体命令和同步辅助函数。

`ctx.pos` 的类型为 `ContextPosition`，绑定位置和维度快照。更深层改变空间或原实体移动后，祖先位置仍表示进入该上下文时的位置和维度。

`ctx.pos.offset(x, y, z)` 在绑定空间中增加世界坐标偏移；完整帧的 `ctx.local(left, up, forward)` 使用绑定时的旋转、锚点与执行者计算局部坐标，也可继续调用 `.offset(...)`。这些来源分别记录；更换执行者会影响眼高来源，改变旋转也会影响局部坐标方向，因此相关快照必须在第一次冲突切换之前保存。

```mcfpp
func restore(actor: entity, location: ContextPosition) {
    tp(actor, location)
}

func main() {
    @s.withPosCtx { space ->
        @s.withEntityCtx { owner ->
            entityByName("Nether").withPosCtx { inner ->
                restore(owner.executor, space.pos)
            }
        }
    }
}
```

`restore` 同步使用祖先的执行者和位置。相同分量没有改变时直接复用：最初乘客示例中的关系只改执行者，`posCtx.pos` 可直接使用继承的空间。跨分量引用按需保留旧身份或位置，命令实参的查询和副作用仍遵循调用点及原有求值顺序。

使用理解上下文位置的命令接口，如 `tp(target, ctx.pos)`。把已被更深层空间覆盖的祖先位置插入原生命令文本会产生诊断，因为单独的坐标文本无法带上原维度；一条命令要求多个不兼容的绑定位置域时也会诊断。

祖先句柄的内部保留会使用 UUID NBT、短暂的 marker 实体及 `mcfpp:context` 运行时帧。正常同步退出时清理这些临时状态。普通公共 `@e`/`@n` 排除带 `mcfpp_context_anchor` 标签的内部实体；任意原生 Minecraft 命令仍可能观察或删除它们。这些机制没有提供公共持久实体指针 API。

## 同步作用域

上下文回调同步执行，返回 `void`，入口需要可静态绑定的内联 lambda。回调中的 `return` 只结束该次回调；其他乘客分支以及调用后的语句仍会执行。

可以在活跃作用域中建立局部别名、嵌套回调、借用祖先上下文，以及向普通同步辅助函数传递绑定句柄。来源和生命周期随传递保留。空执行者保持为空；`withPosCtx` 不会凭空产生执行者，成功进入关系回调才对应那一分支的实体。

上下文及其绑定句柄不能返回到外层、写入全局或容器、捕获到可能逃逸的闭包，或延迟到后续 tick 使用。擦除为 `object`/`any` 也不能绕过限制；编译器会给出诊断。普通 entity 访问表达式的值复制与这些受作用域限制的句柄具有不同的生命周期。

## 命令结果与字段读取

自由函数 `tp` 接受普通 entity 或活跃的 `ctx.executor`，位置可用 `pos3` 或 `ContextPosition`。它返回 `mcfpp.minecraft.std:CommandResult`，包含本次命令的 `result: int` 与 `success: bool`：

```mcfpp
var moved = tp(@a, 1 2 3)
if (moved.success) {
    /say moved
}
```

标签、骑乘、属性和玩家命令也使用统一目标入口，保留各命令自己的数量/玩家限制。已有属性和经验查询继续返回 `CommandResult`。上下文遍历不汇总各分支的结果；需要使用结果时在该分支内处理。

实体 NBT 字段和计分板值提供同步 `tryRead` 接口，读取实现及真实标准库导入已通过检查：

```mcfpp
var found = target.dat.air.tryRead { air ->
    // air 是本次成功读取的普通 short 值。
}
if (!found) {
    /say no-air-value
}

var scored = target.score["objective"].tryRead { value ->
    // value 是本次成功读取的普通 int 值。
}
```

`target.score["objective"]` 是实时计分板值地址；复数 `.scores` 仍用于选择器分数条件。普通 entity 和作用域内的 executor 使用相同读取接口。

`tryRead` 对地址进行一次读取，成功后同步调用正文并返回 `true`；值为零也属于成功。实体、字段或分数不存在时返回 `false`，不进入正文，每次清理结果临时槽，避免读到旧值。回调继承调用点上下文，接收普通值快照，局部 `return` 遵循普通 lambda 规则。直接写入沿用地址接口；没有失败处理的实体裸值读取将提示改用 `tryRead`，即使位于逐实体回调内也需要处理字段缺失。

## 验证入口

`ContextContractTest` 检查依赖声明、窄视图、缺失依赖、歧义、显式提供、函数值和 MNI 参数。`ContextPreservationTest` 从源码编译实际命令，检查首次冲突前保存、A/B 身份、跨维度位置、眼部局部坐标、修饰器恢复、空分支和临时状态清理。`LibraryFullImportTest` 在新会话中导入包含上下文要求和提供块的库并执行其命令；固定 v1 库样本继续由 `LibraryPublishedFixtureTest` 检查。

命令执行验证使用仓库的严格测试解释器，没有启动实际 Minecraft 游戏。部署示例时同时加载主包与依赖包，详见示例 README。

## 旧源码和库迁移

当前发布库格式见 [库格式 v2](library/format-v2.md)。旧 `execute(as = target) { ... }` 改为 `target.withEntityCtx { actor -> ... }`；旧位置入口改用 `withPosCtx`。旧语法和临时 `Execute` 实现已移除，原生命令文本 `/execute ...` 仍按 Minecraft 命令编译。

v1 容器由明确的旧版读取分支处理；旧上下文名称按旧完整帧含义适配，含旧 execute 正文的库要求迁移源码并重新导出。新上下文参数、函数类型要求、命名实参与提供块使用 v2 编码。运行时 ABI 仍为 `mcfpp.runtime/1`，源码语义为 `mcfpp.source/2`。上下文能力及活跃句柄不能序列化为库常量。

# 实体值与执行上下文

本文说明已实现的标准 lambda 接口。实际标准库已重建，实体接口与新会话 `.mclib` 导入已通过集成检查；完整源码和正常编译入口见 [实体上下文示例](../examples/entity-context/README.md)。该示例已通过正常 CLI 编译及导出命令执行检查。

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

编译器可生成辅助函数。`onPassengers` 不递归遍历孙辈乘客；没有执行者或没有直接乘客时不执行关系回调。回调结束后，后续语句继续使用进入调用前的执行上下文。

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
| `target.withPosCtx { ctx -> ... }` | `PosContext` | `execute at target`：设置位置、旋转、维度，保留调用点执行者 |
| `target.withEntityCtx { ctx -> ... }` | `EntityContext` | `execute as target`：设置执行者，保留调用点位置、旋转、维度 |
| `ctx.onPassengers { next -> ... }` | `RelationContext` | 从接收者绑定的执行者访问直接乘客，每只乘客一个分支 |
| `ctx.onVehicle { next -> ... }` | `RelationContext` | 从接收者绑定的执行者访问其直接载具，零或一个分支 |
| `ctx.onOrigin/onOwner/onController/onLeasher/onTarget/onAttacker` | `RelationContext` | 访问对应 Minecraft 直接关系，零或一个分支 |

最后一行表示各自独立的方法，例如 `ctx.onOwner { owner -> ... }`。关系可用性按编译目标检查；当前声明的关系要求 Minecraft 1.19.4 或更新的目标。

`PosContext` 和 `EntityContext` 具有公共基类 `ExecutionContext`；`RelationContext` 继承 `EntityContext`。上下文只读，调用产生子上下文。只有这些 API 的回调改变 Minecraft 执行上下文，普通 lambda 不会因嵌套而自动改变它。

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

## 已有验证与最终集成

此前的源码编译、真实数据包导出及现有命令解释器执行已覆盖用户原样示例、直接乘客/空分支、调用后上下文恢复、祖先身份、位置和维度、同步辅助函数及局部 `return`。其中 `ContextPreservationTest` 6 项、`EntityCommandTargetTest` 4 项通过结果在本轮复用，未重复执行。

读取与库集成另有已通过的 `EntityReadTest` 6 项、实际标准库 `EntityLibraryTest` 4 项证据，包括读取与新会话辅助库导入，本轮复用。最终示例本轮新执行正常 CLI 编译，0 错误、0 警告；迁移后的 `EntityContextTeleportTest` 直接读取真实示例和随附标准库，3 项全部通过、无跳过，包含成功零分、缺失值和读取回调上下文断言。证据为 `build/entity-context-final-example.log` 与 `build/entity-context/D1E-pass-TEST-top.mcfpp.test.entity.EntityContextTeleportTest.xml`。本轮执行范围为该示例及三场景，未启动实际 Minecraft 游戏。部署时同时加载主包 `build/mcfpp/` 和依赖包 `build/Imports/`，详见示例 README。

## 旧源码和库迁移

当前 `.mclib` 格式为 86。旧格式（包括 85）的二进制库需要从源码重新编译，并使用同版编译器随附的标准库。旧的 `entity<...>` 和未解析的旧 `Player` 别名会提示改用普通 `entity`，由值事实表达数量、玩家及种类约束；用户自己声明的 `data Player` 仍是合法声明。

设计依据：[已批准实施计划](entity-context-implementation-plan.md)、[已批准读取失败合同](entity-read-failure-supplement.md)。

# 实体上下文示例

[源码](src/main.mcfpp) 使用编译器随附的实际标准库，显式导入 `mcfpp.lang:*`；无需替代声明或额外依赖库。目标版本为 26.3。

在仓库根目录编译：

```powershell
./gradlew run --args="build --project examples/entity-context/mcfpp.toml"
```

编译生成主包 `examples/entity-context/build/mcfpp/`（包名取配置文件名）及依赖包 `examples/entity-context/build/Imports/`。将两者同时放入目标世界的数据包目录并加载，主包初始化需要其中的随附库函数。用需要的实体作为执行者调用：

```mcfunction
execute as @e[tag=carrier,limit=1] run function example.entitycontext:main
```

`main` 保留用户原样的 `var qwq = @s`、`withPosCtx`、`onPassengers` 和 `tp` 正文，将执行者的每只**直接乘客**传送到执行者的位置。孙辈乘客需要额外的显式关系遍历；无目标或无乘客时不执行回调。末尾的石头检测使用进入函数时的空间，用于观察回调退出后的续接。

默认优化将主体合并为 `execute at @s on passengers run tp @s ~ ~ ~`，后面直接跟随原来的石头检测；该路径无需上下文辅助函数、上下文帧或未使用的命令结果存储。

`withPosCtx` 对应 `execute at`，保留调用点执行者；`withEntityCtx` 对应 `execute as`，保留调用点空间。因此把 `qwq` 改为其他目标时，`onPassengers` 仍沿继承的执行者访问关系。若要遍历其他目标自己的乘客，应先用 `withEntityCtx`，需要其位置时再加 `withPosCtx`。

`inspect` 展示实体 NBT 与分数读取。可为承载实体设置零分后调用：

```mcfunction
scoreboard objectives add points dummy
scoreboard players set @e[tag=carrier,limit=1] points 0
execute as @e[tag=carrier,limit=1] at @s run function example.entitycontext:inspect
```

有正数 `Air` 字段时输出 `air-present`；零分存在时输出 `score-zero`。字段或分数缺失时分别输出 `air-missing`、`score-missing`，失败不进入读取回调。`air-read` 的石头检测使用调用点空间；读取本身不改变执行上下文。单数 `.score["points"]` 是实时值地址，复数 `.scores` 仍是选择器条件。

`commandResults`（数据包入口 `example.entitycontext:command_results`）会将所有玩家传送到 `1 2 3`，展示成功和失败命令各自的 `CommandResult`；恰有两位玩家时还输出 `moved-result`。名为 `Missing` 的目标应不存在，才能观察该示例的失败分支。

`contexts` 展示具名上下文依赖：`restoreBorrowed(space: PosContext, actor: EntityContext)` 从不同祖先接收位置与执行者；`spawnAt` 将绑定位置交给召唤命令；`withPosCtx(original.pos.offset(...))` 显式进入保存的位置。调用方式为 `restoreBorrowed(space = original, actor = other)`。函数头使用 `context(...)` 声明依赖，普通调用保留当前命令环境。

`EntityContextTeleportTest` 直接读取本源码、加载随附标准库并执行导出命令，覆盖直接乘客、空关系/执行者、续接空间、命令结果以及零分/缺失值读取。`ContextContractTest` 和 `ContextPreservationTest` 检查新的函数依赖与祖先帧语义。验证使用测试解释器，未启动 Minecraft 游戏。更多类型关系与旧库迁移见 [使用说明](../../docs/entity-context.md)。

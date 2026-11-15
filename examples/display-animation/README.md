# 展示实体弹跳

使用本仓库构建的编译器运行：

```powershell
mcfpp build --project examples/display-animation
```

将输出的 `build/display-animation` 数据包加载到世界，执行 `/function example.displayanimation:main`。所需标准库运行时会链接到这个数据包中。示例使用固定 UUID `0-0-0-0-1`，不存在时召唤物品展示实体，随后每 tick 更新显示模型的高度，结束于零。

`demoLoop` 展示每隔 10 tick 执行一次、共执行三次；`demoSchedule` 展示立即执行和延迟 20 tick 执行。

接口说明见 [实体展示与调度](../../docs/entity-display-scheduling.md)。

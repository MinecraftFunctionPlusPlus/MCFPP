# 实体模板

从项目目录运行 `mcfpp build --project mcfpp.toml`，将生成的数据包放入 Minecraft 26.3 世界。

执行 `/function example.entitytemplate:main`，生成一个石头和一个花岗岩方块展示实体。石头来自 `summon(d: nbt)` 覆盖方法中的默认配置，花岗岩由调用参数覆盖该配置。第一个实体执行 `sayImStone()` 并将计数减一，最后输出 `remaining=19, untouched=20`。

语义与存储规则见 [实体模板与引用](../../docs/entity-templates.md)。

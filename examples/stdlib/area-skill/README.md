# 实体范围技能

目标 Minecraft Java 26.3。在本目录运行 `mcfpp build --offline`，将 `build/stdlib_area_skill` 放入世界的 `datapacks`，然后执行 `/reload`。

先执行 `/summon minecraft:zombie ~2 ~ ~ {Tags:["stdlib_skill_target"]}` 创建目标，再由玩家执行 `/function example.stdlib.area_skill:main`。技能选取调用点 8 格内带标签的实体，每个目标受到 2 点伤害并获得 `stdlib_skill_hit` 标签；再次运行不会重复伤害。

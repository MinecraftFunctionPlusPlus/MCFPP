# 记分板与玩家反馈

目标 Minecraft Java 26.3。在本目录运行 `mcfpp build --offline`，将 `build/stdlib_score_game` 放入世界的 `datapacks`，然后执行 `/reload`。

先执行 `/function example.stdlib.score_game:setup`，再执行 `/function example.stdlib.score_game:main`。每名在线玩家获得 5 分并看到动作栏提示；再次运行 main 后累计 10 分。执行 `/scoreboard players get @s stdlib_points` 查看分数。`/function example.stdlib.score_game:cleanup` 删除示例记分板目标。

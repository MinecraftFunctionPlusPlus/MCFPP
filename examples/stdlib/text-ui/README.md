# text 样式与交互

目标 Minecraft Java 26.3。在本目录运行 `mcfpp build --offline`，将 `build/stdlib_text_ui` 放入世界的 `datapacks`，然后执行 `/reload`。

执行 `/function example.stdlib.text_ui:main`。聊天栏显示金色加粗的“任务奖励”、绿色的“25”、当前跳跃键和带下划线的 JSON 文本。悬停第一行可看到奖励说明，点击可在聊天输入框中填写 `/help`。

`rewardLabel` 接受运行时参数并返回 text。示例在函数退出后才显示结果，用于说明内容快照的生命周期。

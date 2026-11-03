# 原版资源与 MCFPP 协同示例

目标 Minecraft Java 1.21.8。执行 `mcfpp build --offline`，得到：

- `build/vanilla_demo`：安装到世界的 `datapacks`。
- `build/vanilla_demo-resources`：安装到客户端的 `resourcepacks` 并启用。

执行 `/reload` 后，运行 `/function demo:setup` 领取自定义外观的物品。
执行 `/advancement grant @s only demo:use_wand` 可直接验证回调链：进度 JSON → `demo:on_use` → MCFPP `handleUse()` → 手写 `demo:effects`。
回调撤销进度，因此可以重复触发。

在 IDEA 插件 0.6.0 中，对 JSON 或命令里的资源 ID 使用 Ctrl+B 跳转、Ctrl+Space 补全；对资源文件使用 Shift+F6 重命名并更新引用。重命名 `handleUse` 会更新 TOML 目标，公开 ID `demo:on_use` 保持稳定。

# 同一工程中的原版数据包与资源包

MCFPP 工程保留原版目录和文件。`mcfpp build` 编译语言代码，再组装原版内容：

```text
src/main/mcfpp/                       MCFPP 源码
src/main/resources/data/<namespace>/  mcfunction、进度、配方、战利品表、标签等
src/main/resources/assets/<namespace>/ 模型、物品定义、贴图、声音、字体等
build/<name>/                        数据包
build/<name>-resources/              资源包
```

通过 `sourcePath`、`dataPath`、`assetsPath`、`targetPath` 可以指定目录。没有 assets 文件时不生成资源包；移除全部 assets 后，下次成功构建会移除旧资源包。两个包独立生成 `pack.mcmeta`，例如 1.21.8 的数据包格式为 81、资源包格式为 64。26.3 分别为 121.0 和 97.1。

## 暴露原版可调用的入口

```toml
[entrypoints]
"demo:on_use" = "demo:handleUse"
```

```mcfpp
func handleUse() {
    /function demo:effects
}
```

入口左侧是稳定的原版函数 ID，右侧是工程内 MCFPP 函数的命名空间和声明名（保留大小写）。目标必须是唯一、公开、无参数、返回 void 的普通顶层函数。泛型、扩展函数、原生函数和成员函数不能作为此类入口。入口 ID 须与生成函数 ID 不同；例如 MCFPP `handleUse` 会生成 `demo:handle_use`，可将它导出为 `demo:on_use`。

包装器使用编译器的普通函数调用流程设置和清理帧。进度奖励、函数标签、schedule 和手写 mcfunction 均可调用公开 ID，执行者和位置沿用原版调用上下文。MCFPP 调用手写函数可使用已有原版命令 `/function demo:effects`。

进度奖励示例：`"rewards": {"function": "demo:on_use"}`。可直接用原版进度表达事件条件，目前无需为这条链路另建事件总线。

## 合并和错误

普通手写数据文件与编译产物同路径时构建失败，避免覆盖编译器函数。确实要覆盖时，可在 TOML 顶层声明 `resourceOverrides = ["demo/function/example.mcfunction"]`；公开入口不可覆盖。函数标签合并并去重，编译器在本包声明的初始化与 tick 条目始终保留；手写 `replace: true` 仍会写入输出，作用于 Minecraft 包之间的合并。

JSON 语法和已识别的本工程资源引用在发布输出前检查。发生冲突或缺失引用时，上一份成功的数据包和资源包保留。检查按资源类型区分同名 ID；可选标签成员、动态宏 ID、Minecraft 命名空间和其他未在本工程声明的命名空间不会被当作缺失资源。静态检查不代替 Minecraft 的完整版本相关 JSON codec 验证。

当前识别函数调用、函数标签、进度父级和奖励函数、execute predicate、loot 表、物品组件 `minecraft:item_model`、模型父级和贴图、物品定义及 blockstate 的模型、bitmap/reference 字体、sounds.json 的声音和事件引用。其他原版文件按原样打包；不会把任意描述字符串误认为资源引用。

## IDEA

插件 0.6.0 导入 data 和 assets 为资源目录，MCFPP 面板提供 Data Pack / Resource Pack 输出入口。原版命令与 JSON 中已识别的资源字段支持按类型补全、Ctrl+B 跳转、本工程缺失和重复 ID 提示，以及文件重命名时更新引用。回调跳转直接进入 MCFPP 声明，重命名声明时更新 `[entrypoints]` 目标并保留公开 ID。资源包附带模型、物品定义、blockstate、字体和声音的基础 JSON schema；完整版本字段规则由游戏决定。

完整示例见 `examples/vanilla-integration`。

格式依据：[1.21.4 物品定义](https://www.minecraft.net/en-us/article/minecraft-java-edition-1-21-4)、[26.3 发布说明](https://www.minecraft.net/en-us/article/minecraft-java-edition-26-3)。

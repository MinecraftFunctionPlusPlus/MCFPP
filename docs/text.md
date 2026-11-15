# text：原始 JSON 文本

`text` 保存 Minecraft 文本组件。它与 `string` 分工明确：string 用于字符串计算，text 用于组合要显示的内容、样式和交互。导入 `mcfpp.lang:*` 后使用 `Text` 构造器。

```mcfpp
var label = Text.of("领取奖励").color("gold").bold()
    .hover(Text.of("点击查看奖励说明"))
    .suggestCommand("/trigger reward");
print(label);
```

## 值构造与组合

| 接口 | 说明 |
| --- | --- |
| `Text.empty()` | 空文本 |
| `Text.of(string/int/bool)` | 创建内容快照，可保存、复制或从函数返回 |
| `string.toText()` | 创建字符串内容快照 |
| `left + right`、`left.append(right)` | 接受 text 或 string，返回新文本 |
| `Text.join(parts)` | 按列表顺序连接文本 |
| `Text.join(parts, separator)` | 用 text 分隔各项；空列表返回空文本 |
| `Text.fromJson(json)` | 从编译期 JSON 字符串创建文本 |
| `value.toJson()` | 将编译期可知的 text 导出为 JSON 字符串 |

动态字符串通过 NBT 字段复制进入文本，引号、反斜杠、换行和 Unicode 不需要手动转义。`Text.of` 的值在构造时确定；后续变量修改或函数退出不会改变它。动态 int 的内容快照需要 1.19.4 或更新目标。

拼接保留各部分的样式边界。例如 `Text.of("红色").color("red") + Text.of("普通")` 的第二部分保持默认样式。所有链式方法返回独立文本，不修改接收者。为拼接后的整体设置颜色时，子组件显式指定的颜色按 Minecraft 的继承规则保留。

## 样式与交互

| 方法 | 参数 |
| --- | --- |
| `color(color)` | 编译期颜色名称、`#RRGGBB` 字符串或 `0..0xffffff` RGB 整数；RGB 要求 1.16 或更新目标 |
| `bold/italic/underlined/strikethrough/obfuscated` | 无参表示开启，也可传入 bool；false 明确关闭 |
| `font(id)` | 字体资源 ID，要求 1.16 或更新目标 |
| `insertion(value)` | Shift 点击时插入的字符串 |
| `hover(text)` | 悬停说明 |
| `runCommand(command)` | 点击执行命令；权限继续由游戏决定 |
| `suggestCommand(command)` | 点击填写聊天栏命令 |
| `openUrl(url)` | 打开 http/https URL |
| `copyToClipboard(value)` | 复制字符串，要求 1.15 或更新目标 |
| `changePage(page)` | 书本跳页，参数为编译期可知的正整数 |

也可通过 `text.color`、`font`、`insertion` 和各布尔样式属性读取或修改文本自身的样式。属性读取采用首个根组件的显式样式；缺失时返回 white、minecraft:default、空字符串或 false。属性赋值修改当前变量，链式方法用于构造新值。

## 翻译与动态组件

```mcfpp
var greeting = Text.translatable("example.greeting", [Text.of("Steve")], "Hello, %s!");
var jump = Text.keybind("key.jump");
var players = Text.selector("@a", Text.of(", "));
var points = Text.score("Steve", "points");
var status = Text.nbtStorage("example:ui", "status", false);
```

`translatable` 支持仅传翻译键、键与 `list<text>` 参数、以及附加 fallback 三种形式。翻译和键位按接收玩家的语言与设置显示。selector、score 和 nbtStorage 是游戏在显示时解析的查询组件；`nbtStorage` 的第三个参数决定是否把目标 NBT 继续解释成文本组件。这些查询与 `Text.of` 的内容快照具有不同用途。

既有动态 `int.toText()` 使用记分板组件，动态 `nbt.toText()` 使用原地址的 NBT 查询。需要把数字内容保存或从函数返回时使用 `Text.of(number)`；NBT 查询地址应在显示时仍然存在。

## JSON 与目标版本

`Text.fromJson` 接受字符串、组件对象或组件数组，保留原始组件数组的首项继承语义。它检查基本内容字段、布尔样式、颜色、分数地址和 NBT 来源；JSON 语法错误及动态解析请求会产生源码诊断。动态内容使用 Text 工厂与链式接口构造。

1.21.5 起，事件使用 `click_event`、`hover_event`，命令事件的内容字段为 `command`，悬停文字字段为 `value`。旧目标生成对应的 `clickEvent`、`hoverEvent` 等字段。原始 JSON 必须使用目标版本的格式。这些变化依据 [Mojang 1.21.5 发布说明](https://www.minecraft.net/en-us/article/minecraft-java-edition-1-21-5)。

运行时结构化 text 的直接显示要求 1.21.5 或更新目标；旧目标可显示编译期可知的 text，动态结构化显示会给出诊断。与普通字符串不同，`fromJson` 与 `toJson` 均不提供运行时通用 JSON 解析或序列化。

可运行示例见 [text UI](../examples/stdlib/text-ui/README.md)。

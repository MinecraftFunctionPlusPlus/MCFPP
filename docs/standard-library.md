# 标准库开发指南

通用接口采用 Kotlin 常用命名，回调使用 MCFPP 的显式参数或 `it`。实体接口通过上下文回调表达执行者和空间。导入 `mcfpp.lang:*`、`mcfpp.minecraft:*` 和所需的资源、命令命名空间。

## 字符串与数值

| 接口 | 行为 |
| --- | --- |
| `s.length()` | 返回 UTF-16 单元数；补充字符占两个单元 |
| `s.isEmpty()` / `s.isNotEmpty()` | 判断是否为空串 |
| `s.substring(start, endExclusive)` | 左闭右开；两端限制到合法范围，结束位置小于开始位置时返回空串 |
| `s.contact(other)` / `contact(s, other)` | 拼接字符串，保留引号、反斜杠及 UTF-16 内容 |
| `s == other` / `s != other` | 比较字符串内容 |
| `contains/startsWith/endsWith/indexOf` | 编译期字符串查询；未找到时 `indexOf` 返回 `-1` |
| `replace(old, new)` / `split(separator)` | 编译期按字面字符串替换、分割；保留分割得到的空项 |
| `trim()` | 编译期去除首尾空白 |
| `lowercase()` / `uppercase()` | 编译期转换 ASCII 字母，其他 UTF-16 单元保留 |
| `toIntOrElse { fallback }` | 编译期解析十进制 int，格式错误或溢出时执行默认值回调 |
| `x.coerceIn(lower, upper)` | int/float 范围限制，调用者保证 lower <= upper |
| `x.coerceAtLeast(lower)` / `x.coerceAtMost(upper)` | int/float 单侧范围限制 |

动态字符串支持长度、空串判断、截取、拼接和相等比较。复杂字符串操作要求接收者及字符串参数在编译期可知；不满足时诊断会指出具体接口。动态截取和拼接要求支持函数宏的目标。拼接复用编译器的转义树，生成的辅助命令多于直接输出文本组件；只需要显示文本时可直接使用 `text`。

## 原始 JSON 文本

`Text.of`、`Text.join` 和 `Text.fromJson` 构造 text；`color/bold/hover/suggestCommand` 等链式接口返回带样式和交互的新文本。翻译、键位、选择器、分数及 NBT 文本由对应工厂创建。参数、版本差异和示例见 [text 开发指南](text.md)。

## 列表与函数式操作

`list<T>` 与 `ImmutableList<T>` 共享查询和变换能力，已有 `foreach` 继续可用。

| 接口 | 回调参数及结果 |
| --- | --- |
| `map` / `filter` / `filterNot` | `(T) -> R` / `(T) -> bool` |
| `mapIndexed` | `(int, T) -> R`，索引从零开始 |
| `flatMap` | `(T) -> list<R>` 或只读列表，按源顺序拼接 |
| `forEach` / `foreach` | `(T) -> void` |
| `forEachIndexed` | `(int, T) -> void` |
| `onEach` | `(T) -> void`，返回接收者的值 |
| `fold(initial)` | `(R, T) -> R` |
| `any/all/none/count` | `(T) -> bool`；any/none/count 也可省略谓词 |
| `indexOfFirst/indexOfLast` | `(T) -> bool`，未找到返回 -1 |
| `getOrElse(index, fallback)` | 默认回调 `() -> T`，负索引或越界时执行 |
| `firstOrElse(fallback)` / `lastOrElse(fallback)` | 空列表时执行 `() -> T` |
| `firstOrElse(predicate, fallback)` | 首个匹配元素，否则执行 `() -> T` |
| `tryFirst(predicate, action)` | 首个匹配元素执行 `(T) -> void`，返回是否找到 |
| `sum()` / `sumOf` | int/float 聚合；sumOf 回调 `(T) -> int/float` |
| `takeWhile/dropWhile` | `(T) -> bool`，分别保留或丢弃连续匹配的前缀 |

`take(n)`、`drop(n)`、`slice(start, endExclusive)`、`reversed()`、`distinct()`、`toList()`、`toImmutableList()` 返回独立集合。负数量按零处理，切片只选取有效区间。`distinct` 使用现有元素相等语义并保留首次出现顺序。

所有变换按源列表顺序执行。`any/all/none/tryFirst/takeWhile` 在结果确定后停止执行回调。`dropWhile` 在首次不匹配后保留余下内容并停止调用谓词。空列表的 `all/none` 为 true，any 为 false，sum 为对应类型的零，fold 为初始值。

```mcfpp
var values = [1, 2, 3, 4];
var reward = values.filter { it % 2 == 0 }.sumOf { it * 5 };
var selected = values.firstOrElse({ it > 10 }, { 0 });
var observed = values.onEach { value -> print(value); };
```

`value.let { value -> result }` 返回回调结果，`value.also { value -> ... }` 返回接收者，`run { ... }` 返回末尾表达式。`repeat(count) { index -> ... }` 使用从零开始的索引；非正次数不执行回调。接收者和实参按源码顺序求值一次，默认回调仅在缺失时调用。捕获、局部 return 和同步上下文遵循[现有 lambda 规则](lambda/tutorial.md)。

## 映射

`map<T>` 提供 `getOrElse(key) { ... }`、`getOrPut(key) { ... }`、`keys()`、`values()`、`entries()`。getOrPut 在缺失时保存默认回调的结果；已有键不会触发默认回调。entries 返回 `list<MapEntry<T>>`，每项包含 `key: string`、`value: T`。

投影是独立的值快照，修改投影不修改源集合。map 维持条目顺序，支持任意字符串键。`dict<T>` 继续用于直接访问 NBT 复合标签；需要这些高级键值对操作时使用 map。

## 常用命令

导入 `mcfpp.minecraft.std:*`。新增执行接口返回 `CommandResult`，使用 `.success` 与 `.result` 读取成功状态和数值结果。

| 接口 | 参数 |
| --- | --- |
| `fillOutline` | Area、BlockState |
| `itemReplace` | entity 或 pos3、Slot、Item、数量（默认 1） |
| `itemReplaceFrom` | 目标 entity/pos3、目标 Slot、来源 entity/pos3、来源 Slot |
| `itemModify` | entity 或 pos3、Slot、ItemModifier |
| `titleTimes` | 玩家目标、淡入/停留/淡出 tick 数 |

来源实体要求可证明至多一个目标。资源 ID 使用已有资源类型，槽位使用 Slot；物品修饰器使用 ItemModifier.id。已有伤害、克隆、击杀和附魔命令分别提供 damageResult、damageByResult、damageFromResult、damageAtResult、cloneResult、killResult、enchantResult 入口。

物品命令要求 Minecraft 1.17 或更新目标，damage 要求 1.19.4 或更新目标；不支持时产生源码诊断。

导入 `mcfpp.minecraft:*` 后可使用 `Scoreboard.addObjective(name, criterion)`、`removeObjective(name)`、`setDisplay(slot, name)`、`clearDisplay(slot)`、`reset(target, objective)` 和 `resetAll(target)`。criterion 默认 dummy，目标、显示槽和条件名称使用编译期命令单词。分数读写使用实体的 score 接口或 ScoreRef。

`GameRules` 按编译版本提供 `GameRule<bool>` 与 `GameRule<int>` 常量：

```mcfpp
GameRules.keepInventory.set(true);
var keep = GameRules.keepInventory.get();
GameRules.randomTickSpeed.set(3);
```

规则名称和可用版本来自 `src/main/resources/stdlib/game-rules.json`；26.x 使用目录中的新版名称，旧目标提供对应版本的旧名称。目录按 Mojang 官方服务端生成的命令报告校核：26.1 的 59 项、1.21.8 的 56 项名称及值类型。通过 `node tools/generate-game-rules.mjs` 更新声明，用 `--check` 检查生成文件。get 返回规则的值类型，set 返回 CommandResult；类型错误、未知规则或目标不支持的规则会产生诊断。

## 实体 DSL

| 入口 | 执行语义 |
| --- | --- |
| `target.forEach { ctx -> ... }` | execute as target，继承调用点空间 |
| `target.forEachAt { ctx -> ... }` | execute as target at @s，使用每个实体的空间 |
| `target.count()` | 返回消费时匹配的实体数量 |
| `target.hasTag(tag)` | 任意匹配实体具有标签时返回 true |
| `target.kill()` | 执行 kill，返回 CommandResult |
| `target.damage(amount, type)` | 单实体伤害；伤害类型默认 GENERIC |
| `players.title/subtitle/actionbar(text)` | 玩家反馈，返回 CommandResult |
| `players.titleTimes(in, stay, out)` | 设置标题时间，单位为 tick |

空目标不进入回调，退出后恢复调用点上下文；祖先 ctx.executor 维持原实体身份。玩家专用操作继续检查玩家事实。多目标逐实体伤害可写在 forEach/forEachAt 内。

既有实体接口：传送使用 teleport/tp；标签使用 addTag/removeTag/listTag；药水效果使用 effect/effectInfinite/clearEffect/clearAllEffects；属性使用 getAttribute/getAttributeBase/setAttributeBase 及修饰器接口；队伍使用 joinTeam/leaveTeam；骑乘使用 ride/stopRide；玩家经验使用 addXpPoints/addXpLevels/setXpPoints/setXpLevels/queryXpPoints/queryXpLevels；消息使用 tell/w。参数与底层含义见提供者上的 API 注释及[实体上下文指南](entity-context.md)。

完整示例见 [集合奖励](../examples/stdlib/rewards/README.md)、[实体范围技能](../examples/stdlib/area-skill/README.md)、[记分板小游戏](../examples/stdlib/score-game/README.md)。

## 构建与运行检查

修改标准库声明后运行 `./gradlew rebuildStdlib`，更新当前支持目标的库产物。`StandardLibraryExpansionTest` 检查公开接口与诊断，`StandardLibraryRuntimeTest` 导出实际数据包并验证库重新导入。沙箱运行命令及适配范围见[运行检查说明](../tools/stdlib-dsb-compat/README.md)。三个示例还可各自通过 `mcfpp build --offline` 构建，按示例 README 在原版世界运行。

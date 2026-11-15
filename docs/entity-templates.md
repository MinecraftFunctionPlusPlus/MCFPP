# 实体模板与引用

`entity` 声明实体模板。模板继承自 `Entity`；`ItemDisplay`、`BlockDisplay`、`TextDisplay` 通过 `DisplayEntity` 继承自 `Entity`。

```mcfpp
import mcfpp.minecraft.entity:*

entity StoneDisplay: BlockDisplay {
    var initialCount: int = 10
    entity var count: int = initialCount

    constructor(n: nbt): BlockDisplay(n)

    override func summon(d: nbt): EntityRef<StoneDisplay> {
        return super.summon({block_state: {Name: "minecraft:stone"}}.merge(d))
    }

    entity func consume() {
        this.count -= 1
    }
}

func main() {
    var template = StoneDisplay({data: {qwq: 1}})
    template.initialCount = 20
    var ref = template.summon()
    ref.consume()
    var remaining = ref.count
}
```

`template` 的类型为 `StoneDisplay`，`ref` 的类型为 `EntityRef<StoneDisplay>`。构造模板准备生成配置；每次 `summon()` 创建一个新实体并返回固定 UUID 的引用。复制引用保留相同的实体身份，引用可以传参、返回和捕获到延迟回调中。实体消失后，引用仍保存该 UUID；`exists()` 可以查询实体是否存在。

## 两套成员

| 声明 | 接收者 | 状态位置 |
| --- | --- | --- |
| `var`、`const` | 模板实例 | 普通模板数据 |
| `func` | 模板实例，`this` 为模板 | 普通模板方法 |
| `entity var`、`entity const` | `EntityRef<T>` | 实体的 `data` NBT |
| `entity func` | `EntityRef<T>`，`this` 为该引用 | 以实体为执行者的方法 |

`entity var` 的初始化表达式在每次 `summon()` 时求值，可以读取当时的模板字段。改变模板字段影响之后的生成；已经生成的实体保有各自的状态。实体方法访问实体字段，不能读取原模板的普通字段。

普通方法与实体方法均按模板继承关系查找。实体模板继承一个实体父模板；抽象模板和 `Player` 等无法生成的种类不能直接生成。

实体字段使用 `data.mcfpp."声明命名空间:声明模板"."字段名"` 保存。继承字段保留其声明者的路径。`data.mcfpp` 和 `mcfpp.entity.*` 实体标签由编译器管理；用户的其他 `data` 内容会保留。字段类型需要有 NBT 存储表示。复合值可以读出、修改后赋回字段。

## 构造与执行

父构造委托使用 `constructor(...): Parent(...)`，可以省略正文，也可以在委托后继续写正文。`initialNBT` 保存生成配置；`nbt.merge(other)` 返回合并后的副本，`other` 覆盖同名项。

实体模板默认提供可覆盖的 `func summon(d: nbt): EntityRef<T>`。覆盖方法是普通模板方法，`this` 指向模板，`super.summon(d)` 调用父实现。省略参数的 `summon()` 会以空 compound NBT 调用同一覆盖链。方法正文使用 `return` 返回引用。

生成时先复制 `initialNBT`，再初始化 `entity var`，最后递归合并 `d`。因此调用者能覆盖默认 NBT；`data.mcfpp` 中的类型身份由编译器写入。未覆盖 `summon` 的子模板会继承用户提供的实现及其声明的返回类型。

`base.merge(overrides)` 返回独立的 `nbt` 副本，不修改输入。compound 递归合并，同名标量和列表由右侧整体替换；非 compound 的已知输入会产生编译错误。该方法属于 `nbt`；`dict.merge` 保持原有的原地合并语义。

`entity func` 自动执行 `execute as`，保留调用位置、旋转和维度。相对坐标沿用调用处的空间。`summon()` 使用当前位置，也可以传入 `pos3` 或带维度的位置上下文。

`EntityRef<Child>` 可以传给接收 `EntityRef<Parent>` 的函数。`entityByUUID<T>(uuid)` 恢复 `EntityRef<T>`；该工厂采用调用者提供的类型。`checkType<T>()` 查询实际实体：内置模板检查实体种类，自定义模板检查生成时保存的继承标签。

完整示例见 [entity-template](../examples/entity-template/src/main.mcfpp)，目标为 Minecraft 26.3。生成数据包后执行 `/function example.entitytemplate:main`；输出的两个计数分别为 19 和 20。

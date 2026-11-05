![](https://user-images.githubusercontent.com/90548686/236462051-b901f99c-bdef-435c-8ca2-0dda37b25285.png)
[![Stargazers over time](https://starchart.cc/MinecraftFunctionPlusPlus/MCFPP.svg?variant=adaptive)](https://starchart.cc/MinecraftFunctionPlusPlus/MCFPP)
[English](./README.md)
------------

## 介绍

MCFPP是一个能被编译为Minecraft数据包的全新的面向对象的语言。它旨在以类似C系语言的语法，进行数据包的编写，并引入编程中常用的概念，从而使数据包的编写更加的便利。

**这个项目仍然处于早期的开发阶段中，功能可能不稳定。部分特性可能会在未来的版本中发生变化。库函数尚不齐全。**

## 快速开始

安装 JDK 25，完整解压 `mcfpp-0.1.0.zip`（Windows）或 `mcfpp-0.1.0.tar.xz`（Linux/macOS），设置 `JAVA_HOME` 并将解压目录的 `bin` 加入 PATH。重新打开终端，运行 `mcfpp --version`。详见 [安装说明](INSTALL.md) 和 [0.1.0 发行说明](RELEASE-NOTES.md)。

[统一工程与 CLI 使用指南](docs/cli.md)：`mcfpp init hello` 创建工程，`mcfpp build` 生成数据包；`mcfpp init hello --mni` 创建 Kotlin MNI 宏和 Kotlin 测试。项目配置使用 `mcfpp.toml`，依赖声明使用 `mcfpp-packages.json`。

[MCFPP API](https://www.mcfpp.top)

使用 JDK 25，通过 `./gradlew release`（Windows：`./gradlew.bat release`）生成发行物，位于 `build/releases/0.1.0`；源码构建所需的 Mcfpm SDK 准备步骤见 [CLI 指南](docs/cli.md#从源码构建-cli)。

`release` 优先缩小产物体积。日常快速打包继续使用 `shadowJar` 或 `installShadowDist`，跳过发行压缩。

[类型规范](./docs/type-system/specification.md) · [迁移状态与指南](./docs/type-system/migration.md)

[ScoreRef / NBTRef 用法与迁移](./docs/type-system/reference-values.md)：保存和传递显式 Minecraft 地址，通过同步 `tryRead` 读取、通过 `set` 写入。

[预编译库作者与消费者样例](./examples/libraries/README.md)：通过本地 Maven 打包、安装并在移走作者源码后编译消费者；嵌入接口见 [编译器桥接](./docs/library/compiler-bridge.md)。

标准库默认通过 `%std` 按项目 `version` 选择对应 Minecraft 目标的实际编译产物；自定义标准库路径、资源及初始化规则见 [按 Minecraft 目标使用标准库](./docs/library/compiler-bridge.md#按-minecraft-目标使用标准库)。

## [后续计划](./TODO_CN.md)

* [ ] 代码优化
* [ ] 基本库

## 相关工程

### [MCSharp](https://github.com/Voziv/MCSharp)

MCSharp是一个CSharp库。利用MCSharp，开发者可以使用CSharp进行数据包的开发。但是，此项目因为技术问题已经被停止。MCFPP继承了部分MCSharp的思想。

### [justMCF](https://github.com/XiLaiTL/JustMCF)

JustMCF是一个简化mcfunction工程的项目。使用JustMCF，你不但可以使用原版的命令，还可以使用项目设计的简化命令，可以使你的命令更加简洁高效。

## 特性

### 基本的逻辑语句

```mcfpp
func example(){
  external i: int = 1
  if(i > 0){
    execute(as = @s) {
      /say Hello Minecraft!
    }
  }
}
```

## 面向对象的编程

```mcfpp
data Example{
  var i: int {
    get {
      return field * 2
    }

    set {
      field = value
    }
  }


  constructor(i: int){
    this.i = i
  }

  func print(){
    print(this.i)
  }
}
```

## 库的调用

```mcfpp
import mcfpp.lang:*

func example(){
    var i: float = 1.5
    var out: int
    out = toInt(i)
    print(out)
}
```

## 泛型

`<T>` 是只读类型参数，亦可显式写作 `<T: type>`；只读值参数写作 `<N: int>`。`type` 不能用于普通值声明，例如 `var t: type = int` 非法。

```mcfpp
data Example<T>{
  var i: T

  constructor(i: T){
    this.i = i
  }

  public func print(){
    print(this.i)
  }
}

func test<T>(i: T){
    print(i)
}
```

## 直接使用原版Minecraft命令

```mcfpp
var qwq = "Minecraft"

func test(){
  /execute as @a run say Hello ${qwq}!
}
```

## 函数、注释与字段声明

变量、具名参数和字段的类型标注使用 `:`，例如 `var x: int`。`const x = 1` 和 `external x: int = 1` 直接声明值，不加 `var`；导入变量仍写 `import var`。函数返回类型使用 `:`；普通注释以 `//` 开头，文档注释以 `///` 开头并使用 Markdown。连续的文档注释行组成一个文档块，`///` 后的一个空格作为分隔，其余缩进和 Markdown 标记保留。`data`、`object data` 和匿名 `data` 体内的字段使用 `var` 或 `const` 声明。

```mcfpp
/// # 计数器
/// 保存当前计数，使用 **粗体** 和 `代码` 描述字段。
data Counter {
  // 字段使用 var 或 const 声明
  var value: int = 0
  func read(): int { return this.value }
}
```

共享契约使用 `abstract data Name` 声明，无方法体的方法写作 `abstract func read(): int;`。具体子模板必须实现继承的抽象方法。`interface` 和 `data abstract` 声明已移除；原有 `dynamic` 变量改用 `external`，仍要求运行期存储。

旧的 `->` 返回类型标记和 `#`、`##`、`#{…}#`、`###` 注释不再接受。版本条件指令 `#if/#elif/#else/#endif` 继续使用原有写法。

## 换行与算术表达式

语句可以用换行或分号分隔，`}` 前最后一条语句无需分号。同一行语句之间和 SNBT 数组内的分号保留。`if`、`else if`、`while` 和 `do ... while` 的条件括号可省略，条件须为 `bool`。把运算符放在行尾时，表达式可以在下一行继续。忽略行首空白后，以单个 `/` 开头的行按原版命令解析；跨行除法须把 `/` 放在上一行。

```mcfpp
func calculate(){
  var x = 12 /
    3
  x += 2
  x *= 3
  var opposite = -x
  /say Calculation complete
}
```

支持 `+=`、`-=`、`*=`、`/=`、`%=`。复合赋值会读取左值、执行对应运算，并将结果写回；不支持 `++` 或 `--`。

## 按 Minecraft 版本编译

在项目配置 `mcfpp.toml` 中设置目标版本，例如 `version = "26.3"`。每次编译只生成该目标版本的数据包；分别修改 `version` 并编译即可得到不同版本的输出。支持目标版本 `26.1`、`26.2`、`26.3` 及已有的旧版本。

目标为 `26.3` 时，浮点运算自动使用原生数值提供器及 NBT float，支持四则运算、取余、比较、复合赋值和整数转换。旧版本继续使用 XiaoDouMathLib。调研依据、实现方案和存储格式迁移说明见 [26.3 浮点后端](docs/float-providers-26.3.md)。

```mcfpp
#if MC >= 26.1
func greet(){
  /say 26.1 or newer
}
#elif MC >= 1.21.6
func greet(){
  /say 1.21.6 to 1.21.8
}
#else
func greet(){
  /say older version
}
#endif
```

条件指令可嵌套，可放在导入、声明和函数体中。条件格式为 `MC` 加 `==`、`!=`、`<`、`<=`、`>` 或 `>=` 与一个版本号，不支持逻辑组合。版本号按整数段比较；旧版本须写完整形式，例如 `1.21.6`，不能简写为 `21.6`。普通注释使用 `//`。

更多语法信息，请参考文档[MCFPP API](https://www.mcfpp.top)

![MCFPP](https://user-images.githubusercontent.com/90548686/236462051-b901f99c-bdef-435c-8ca2-0dda37b25285.png)
[![Stargazers over time](https://starchart.cc/MinecraftFunctionPlusPlus/MCFPP.svg?variant=adaptive)](https://starchart.cc/MinecraftFunctionPlusPlus/MCFPP)

[English](./README.md)

# MCFPP

MCFPP 是一门编译为 Minecraft 数据包的编程语言，提供常见的控制流、带类型的数据模板、泛型函数、原版命令，以及在编译期间运行的 Java/Kotlin 原生接口。

**项目仍处于早期开发阶段。本文对应 `kotlin-latest` 分支的当前源码；已发布的编译器和已有项目可能使用旧语法。**

## 快速开始

安装 **JDK 21**，使用仓库自带的 Gradle wrapper 构建编译器：

```shell
# Windows
.\gradlew.bat build
# Linux / macOS
./gradlew build
```

可执行 JAR 输出至 `build/libs/MCFPP-1.0.2-SNAPSHOT.jar`。创建如下项目：

```text
hello/
  mcfpp.json
  src/
    main.mcfpp
```

`mcfpp.json`：

```json
{
  "version": "26.3",
  "sourcePath": "src",
  "targetPath": "build",
  "namespace": "hello",
  "description": "My first MCFPP data pack"
}
```

`src/main.mcfpp`：

```mcfpp
namespace hello;

func greet() {
  /say Hello Minecraft!
}
```

**在 `hello` 目录中**运行编译器。显式填写的相对源码路径和输出路径按当前工作目录解析：

```shell
java -jar /path/to/MCFPP-1.0.2-SNAPSHOT.jar mcfpp.json
```

将生成的 `build/mcfpp` 数据包复制到世界的 `datapacks` 目录，重新加载后运行 `/function hello:greet`。

完整教程见 [中文文档](https://www.mcfpp.top/zh/quickstart/) 和 [英文文档](https://www.mcfpp.top/en/quickstart/)。

## 语言示例

### 变量、函数与控制流

变量使用 `var 名称 as 类型` 声明，也可根据初值推断类型。函数即使没有参数也必须写括号，返回类型使用 `:`。

```mcfpp
func add(a as int, b as int): int {
  return a + b;
}

func calculate() {
  dynamic var count as int = 0;
  while (count < 3) {
    count += 1;
  }
  if (count == 3) {
    print(add(count, 2));
  }
}
```

语句用换行或分号结束。跨行表达式须把运算符放在上一行末尾。支持 `+=`、`-=`、`*=`、`/=`、`%=`，不支持 `++` 或 `--`。

### 数据模板与泛型

使用 `data` 定义结构化的值，字段必须写出 `var`，可包含构造器和方法。单例或伴随成员使用 `object data`。当前解析器不再提供旧的 `class` 语法。

```mcfpp
/// # Box
/// 保存指定类型的值。
data Box<T as type> {
  var value as T;

  constructor(value as T) {
    this.value = value;
  }

  func read(): T {
    return this.value;
  }
}

func example() {
  var box = Box<int>(5);
  print(box.read());
}
```

`<...>` 中的只读实参必须是完整的编译期确定值。`type` 只能用于这类参数声明；普通变量、字段、函数参数和返回值不能保存类型值。

### 类型视图与数值转换

`object` 是统一静态超类型，`any` 擦除公开类型。`as` 在同一数据位置上建立解释视图，不复制对象、不调用构造器，也不转换数值。数值转换使用专门的转换函数：

```mcfpp
func convert() {
  var number as int = 3;
  var fraction as float = toFloat(number);
  var whole as int = toInt(fraction);
  print(whole);
}
```

`const` 禁止初始化后的重赋，不要求初值在编译期已知，也不冻结可变容器的内容。`dynamic` 要求该值具有可用的运行时表示。

### 原版 Minecraft 命令

忽略缩进后，以单个 `/` 开头的行按原版命令解析，可使用 `${表达式}` 插值：

```mcfpp
func announce() {
  var name = "Minecraft";
  /execute as @a run say Hello ${name}!
}
```

跨行除法须把 `/` 放在上一行。普通注释使用 `//`，Markdown 文档注释使用 `///`，连续的文档注释行组成一个文档块。旧的 `#`、`##`、`#{…}#` 和 `###` 注释不再接受。

## Minecraft 目标版本

在项目配置中设置 `version`，每次编译生成一个目标版本的数据包。目标包括 `1.21`、`1.21.1`–`1.21.8`、`26.1`–`26.3`，以及[编译器目标表中的较早版本](./src/main/kotlin/top/mcfpp/command/TargetCapabilities.kt)。编译器识别的目标不代表标准库的每个 API 都适用于该版本。

目标为 `26.3` 时，浮点算术使用原生数值提供器和 NBT float；较早目标使用 XiaoDouMathLib。两种后端的存储格式不同，已有持久数据不会自动迁移。详细说明见 [浮点后端文档](./docs/float-providers-26.3.md)。

版本条件指令在解析前选择源码：

```mcfpp
#if MC >= 26.3
func backend() {
  /say Native float backend
}
#else
func backend() {
  /say Legacy float backend
}
#endif
```

`#if/#elif/#else/#endif` 可嵌套，可包围导入、声明和语句。条件使用 `MC`、比较符和完整版本号，支持 `==`、`!=`、`<`、`<=`、`>`、`>=`，不支持逻辑组合。版本条件指令与注释是不同的语法。

## 开发与实现状态

`gradlew build` 构建编译器并运行检查。解析器、类型元数据或 MNI 改动后，先运行 `gradlew regenerateStdlib` 重建内置库，再运行 `gradlew check`。

当前原生接口使用单个 `NativeCallContext` 参数。旧元数据格式的库须重新编译，当前格式为 **MCFL77**。

- [类型规范](./docs/type-system/specification.md)
- [迁移指南](./docs/type-system/migration.md)
- [架构与实现边界](./docs/type-system/architecture-migration-report-2026-10-09.md)
- [验证方法](./docs/type-system/verification.md)
- [后续计划](./TODO_CN.md)

异常与效果、任务、生成器和 lambda 目前属于**设计提案**，尚未实现为语言功能。`docs/` 中的相关设计文档描述后续工作。

## 相关工程

[MCSharp](https://github.com/Voziv/MCSharp) 与 [JustMCF](https://github.com/XiLaiTL/JustMCF) 提供了其他 Minecraft 数据包开发思路。

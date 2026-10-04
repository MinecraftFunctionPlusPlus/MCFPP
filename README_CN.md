![](https://user-images.githubusercontent.com/90548686/236462051-b901f99c-bdef-435c-8ca2-0dda37b25285.png)
[![Stargazers over time](https://starchart.cc/MinecraftFunctionPlusPlus/MCFPP.svg?variant=adaptive)](https://starchart.cc/MinecraftFunctionPlusPlus/MCFPP)
[English](./README.md)
------------

## 介绍

MCFPP是一个能被编译为Minecraft数据包的全新的面向对象的语言。它旨在以类似C系语言的语法，进行数据包的编写，并引入编程中常用的概念，从而使数据包的编写更加的便利。

**这个项目仍然处于早期的开发阶段中，功能可能不稳定。部分特性可能会在未来的版本中发生变化。库函数尚不齐全。**

## 快速开始

[MCFPP API](https://www.mcfpp.top)

[类型规范](./docs/type-system/specification.md) · [迁移状态与指南](./docs/type-system/migration.md)

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

```
func example(){
  var i = @s.pos[0];
  if(i > 0){
    execute(as = @s) {
      say("Hello Minecraft!");
    }
  }
}
```

## 面向对象的编程

```
class Example{
  var i as int {
    get {
      return field * 2;
    }
    
    set {
      field = value;
    }
  };
  
  
  constructor(i as int){
    this.i = i;
  }
  
  func print(){
    print(this.i);
  }
}
```

## 库的调用

```
import mcfpp.math;

void example{
    float i = 1.5;
    float out;
    out = pow(i, 2);
    print(out);
}
```

## 泛型

```
class Example<T as type>{
  var i as T;
  
  public Example(i as T){
    this.i = i;
  }
  
  public func print(){
    print(this.i);
  }
}

func test<T as type>(i as T){
    print(i);
}
```

## 直接使用原版Minecraft命令

```
var qwq = "Minecraft";

func test(){
  /execute as @a run say Hello ${qwq}!
}
```

## 换行与算术表达式

语句可以用换行或分号结束。把运算符放在行尾时，表达式可以在下一行继续。行首第一个非空白字符为 `/` 时，整行按原版命令解析；跨行除法须把 `/` 放在上一行。

```mcfpp
func calculate(){
  var x = 12 /
    3;
  x += 2;
  x *= 3;
  var opposite = -x;
  /say Calculation complete
}
```

支持 `+=`、`-=`、`*=`、`/=`、`%=`。复合赋值会读取左值、执行对应运算，并将结果写回；不支持 `++` 或 `--`。

## 按 Minecraft 版本编译

在项目配置 `mcfpp.json` 中设置目标版本，例如 `"version": "26.3"`。每次编译只生成该目标版本的数据包；分别修改 `version` 并编译即可得到不同版本的输出。支持目标版本 `26.1`、`26.2`、`26.3` 及已有的旧版本。

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

条件指令可嵌套，可放在导入、声明和函数体中。条件格式为 `MC` 加 `==`、`!=`、`<`、`<=`、`>` 或 `>=` 与一个版本号，不支持逻辑组合。版本号按整数段比较；旧版本须写完整形式，例如 `1.21.6`，不能简写为 `21.6`。普通 `#` 注释保持原有行为。

更多语法信息，请参考文档[MCFPP API](https://www.mcfpp.top)

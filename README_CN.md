![](https://user-images.githubusercontent.com/90548686/236462051-b901f99c-bdef-435c-8ca2-0dda37b25285.png)
[English](./README.md)
------------

# 介绍

MCFPP是一个能被编译为Minecraft数据包的全新的面向对象的语言。它旨在以类似C系语言的语法，进行数据包的编写，并引入编程中常用的概念，从而使数据包的编写更加的便利。

**这个项目仍然处于早期的开发阶段中，尚不能用于实际运用。部分功能尚未实现，特性可能会在未来的版本中发生变化。库函数尚不齐全。**

# 快速开始

[MCFPP API](https://www.mcfpp.top)

# [计划](./TODO_CN.md)

* [ ] 代码优化
* [ ] 垃圾回收机制
* [ ] 运算符重载  
* [ ] 沙箱  
    * [ ] native的语法糖形式  
* [ ] 分版本编译  
* [ ] 基本库

# 相关工程

## [MCSharp](https://github.com/Voziv/MCSharp)

MCSharp是一个CSharp库。利用MCSharp，开发者可以使用CSharp进行数据包的开发。但是，此项目因为技术问题已经被停止。MCFPP继承了部分MCSharp的思想。

## [justMCF](https://github.com/XiLaiTL/JustMCF)

JustMCF是一个简化mcfunction工程的项目。使用JustMCF，你不但可以使用原版的命令，还可以使用项目设计的简化命令，可以使你的命令更加简洁高效。

# 特性

## 基本的逻辑语句

```
func example(){
  int i = @s.pos[0];
  if(i > 0){
    execute(as = @s) say("Hello Minecraft!");
  }
}
```

## 面向对象的编程

```
class Example{
  int i{
    get {
      return field * 2;
    }
    
    set {
      field = value;
    }
  };
  
  
  constructor(int i){
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
class Example<type T>{
  T i;
  public Example(T i){
    this.i = i;
  }
  public func print(){
    print(this.i);
  }
}

func test<type T>(T i){
    print(i);
}
```

## 直接使用原版Minecraft命令

```
func test(){
  /execute as @a run say Hello Minecraft!
}
```

To see more features, please refer to the [MCFPP API](https://www.mcfpp.top)
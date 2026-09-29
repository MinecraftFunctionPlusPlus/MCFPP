![](https://user-images.githubusercontent.com/90548686/236462051-b901f99c-bdef-435c-8ca2-0dda37b25285.png)
[简体中文](./README_CN.md)
------------
# Introduction

MCFPP is a new object-oriented language that compiles into Minecraft data packs. It aims to simplify data pack creation with syntax similar to C-based languages, incorporating common programming concepts for convenience.

**Note: This project is in early development. Features may change, and library functions are incomplete.**

## Quick Start

[MCFPP API](https://www.mcfpp.top)

## [Future Plans](./TODO_CN.md)

* [ ] Code optimization
* [ ] Basic library

## Related Projects

### [MCSharp](https://github.com/Voziv/MCSharp)

MCSharp is a CSharp library for data pack development, now discontinued due to technical issues. MCFPP inherits some of its ideas.

### [JustMCF](https://github.com/XiLaiTL/JustMCF)

JustMCF simplifies mcfunction projects, allowing both original and streamlined commands for efficiency.

## Features

### Basic Logic Statements

```cpp
func example(){
  var i = @s.pos[0];
  if(i > 0){
    execute(as = @s) {
      say("Hello Minecraft!");
    }
  }
}
```

### Object-Oriented Programming

```cpp
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

### Library Calls

```cpp
import mcfpp.math;

void example{
    float i = 1.5;
    float out;
    out = pow(i, 2);
    print(out);
}
```

### Generics

```cpp
class Example<T as type>{
  var i as T;
  
  public Example(i as T){
    this.i = i;
  }
  
  public func print(){
    print(this.i);
  }
}

func test<T as Type>(i as T){
    print(i);
}
```

### Direct Use of Minecraft Commands

```cpp
var qwq = "Minecraft";

func test(){
  /execute as @a run say Hello ${qwq}!
}
```

## Line breaks and arithmetic expressions

A newline or semicolon ends a statement. An expression can continue on the next line when its operator ends the current line. If `/` is the first non-whitespace character on a line, that line is a vanilla Minecraft command; put a division operator on the previous line when splitting division across lines.

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

Compound assignments `+=`, `-=`, `*=`, `/=`, and `%=` read the target, apply the corresponding operation, and write the result back. `++` and `--` are not supported.

For more syntax information, refer to the [MCFPP API](https://www.mcfpp.top).

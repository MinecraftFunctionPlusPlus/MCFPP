![](https://user-images.githubusercontent.com/90548686/236462051-b901f99c-bdef-435c-8ca2-0dda37b25285.png)
[简体中文](./README_CN.md)
------------
# Introduce
MCFPP is a brand new objected-oriented language that can be compiled into Minecraft Datapack. It aims to write datapacks in a syntax similar to C language, and introduces commonly used concepts in programming, thereby making the writing of datapacks more efficient.

**This project is still in the early stage of development and cannot be used for actual use. Some functions have not been implemented, and features may change in future versions. The library is also not complete.**

# QuickStart
[MCFPP Guider](https://alumopper.github.io/mcfppguide/en-US/quickstart)
# Relative Projects
## [MCSharp](https://github.com/Voziv/MCSharp)

MCSharp is a CSharp library. Using MCSharp, developers can develop datapacks using CSharp. However, this project has been stopped due to technical issues. MCFPP inherits part of the ideas of MCSharp.

## [justMCF](https://github.com/XiLaiTL/JustMCF)

JustMCF is a project to simplify mcfunction projects. Using JustMCF, you can not only use the original commands, but also use the simplified commands designed by the project, which can make your commands more concise and efficient.

# [Update Plan](./TODO.md)
* [ ] Code optimization
* [ ] Garbage collection mechanism
* [ ] More variable features (const, dynamic, import)
* [ ] Interface
* [ ] Inheritance and polymorphism
* [ ] Struct
* [ ] Operator overload
* [ ] Inline Function
* [ ] Sandbox
*    * [ ] Syntactic sugar form of native
* [ ] Versioned compilation
* [ ] Basic library
* [ ] Enum


# Features
## basic logical statements
```
void example(){
  int i = @s.pos[0];
  if(i > 0){
    @s.say("Hello Minecraft!");
  }
}
```
## object-oriented programming
```
class Example{
  int i;
  public Example(int i){
    this.i = i;
  }
  public void print(){
    sys.print(this.i);
  }
}
```
## libraries
```
import mcfpp.math;

void example{
    float i = 1.5;
    float out;
    out = pow(i, 2);
    print(out);
}
```

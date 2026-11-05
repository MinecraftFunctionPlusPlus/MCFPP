![](https://user-images.githubusercontent.com/90548686/236462051-b901f99c-bdef-435c-8ca2-0dda37b25285.png)
[简体中文](./README_CN.md)
------------
# Introduction

MCFPP is a new object-oriented language that compiles into Minecraft data packs. It aims to simplify data pack creation with syntax similar to C-based languages, incorporating common programming concepts for convenience.

**Note: This project is in early development. Features may change, and library functions are incomplete.**

## Quick Start

Install JDK 25 and extract the complete `mcfpp-0.1.0.zip` (Windows) or `mcfpp-0.1.0.tar.xz` (Linux/macOS). Set `JAVA_HOME` and add the extracted `bin` directory to PATH, then open a new terminal and run `mcfpp --version`. See [installation instructions](INSTALL.md) and [0.1.0 release notes](RELEASE-NOTES.md).

See the [project and CLI guide](docs/cli.md). Use `mcfpp init hello` and `mcfpp build`, or `mcfpp init hello --mni` for a Kotlin MNI macro and Kotlin tests. Projects use `mcfpp.toml` and `mcfpp-packages.json`.

[MCFPP API](https://www.mcfpp.top)

Build release artifacts with JDK 25 using `./gradlew release` (`./gradlew.bat release` on Windows); files are staged in `build/releases/0.1.0`. Source builds require the Mcfpm SDK described in the [CLI guide](docs/cli.md).

The release build prioritizes size. Use `shadowJar` or `installShadowDist` for quick local packaging; these tasks skip release compression.

[Type system specification](./docs/type-system/specification.md) · [Migration status and guide](./docs/type-system/migration.md)

[ScoreRef / NBTRef usage and migration](./docs/type-system/reference-values.md): save and pass explicit Minecraft addresses, read with synchronous `tryRead`, and write with `set`.

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

### Object-Oriented Programming

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

### Library Calls

```mcfpp
import mcfpp.lang:*

func example(){
    var i: float = 1.5
    var out: int
    out = toInt(i)
    print(out)
}
```

### Generics

`<T>` declares a readonly type parameter; explicit `<T: type>` is also supported. Readonly value parameters use `<N: int>`. Ordinary values cannot have type `type`; `var t: type = int` is invalid.

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

### Direct Use of Minecraft Commands

```mcfpp
var qwq = "Minecraft"

func test(){
  /execute as @a run say Hello ${qwq}!
}
```

## Functions, comments, and fields

Variables, named parameters, and fields use `:` for type annotations, such as `var x: int`. Declare `const x = 1` and `external x: int = 1` directly, without `var`; variable imports retain `import var`. Function return types use `:`. Ordinary comments start with `//`; documentation comments start with `///` and contain Markdown. Consecutive documentation lines form one block. A single space after `///` is a separator; further indentation and Markdown markers are preserved. Fields in `data`, `object data`, and anonymous `data` bodies use `var` or `const`.

```mcfpp
/// # Counter
/// Stores the current count, with **bold** and `code` documentation.
data Counter {
  // Fields use var or const
  var value: int = 0
  func read(): int { return this.value }
}
```

Declare shared contracts with `abstract data Name` and bodyless methods with `abstract func read(): int;`. Concrete children must implement inherited abstract methods. The `interface` and `data abstract` declarations have been removed. Replace the `dynamic` variable keyword with `external`, which still requires runtime storage.

The old `->` return marker and `#`, `##`, `#{…}#`, and `###` comments are no longer accepted. Version directives `#if/#elif/#else/#endif` retain their syntax.

## Line breaks and arithmetic expressions

Newlines or semicolons separate statements; the final statement before `}` needs no semicolon. Keep semicolons between same-line statements and in SNBT arrays. Parentheses are optional around `if`, `else if`, `while`, and `do ... while` conditions, which must have type `bool`. An expression can continue on the next line when its operator ends the current line. A line starting with a single `/` after optional whitespace is a vanilla Minecraft command; put a division operator on the previous line when splitting division across lines.

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

Compound assignments `+=`, `-=`, `*=`, `/=`, and `%=` read the target, apply the corresponding operation, and write the result back. `++` and `--` are not supported.

## Compile for a Minecraft version

Set the target version in `mcfpp.toml`, for example `version = "26.3"`. Each build produces one data pack for that target; change `version` and build again for another version. Targets `26.1`, `26.2`, `26.3`, and the previously supported older versions are available.

Targeting `26.3` automatically uses native number providers and NBT floats for float arithmetic, comparisons, compound assignments, and integer conversions. Earlier targets keep XiaoDouMathLib. See the [26.3 float backend notes](docs/float-providers-26.3.md) for the implementation and storage migration details.

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

Directives may be nested and can surround imports, declarations, or function statements. A condition compares `MC` with a version using `==`, `!=`, `<`, `<=`, `>`, or `>=`; logical combinations are not supported. Versions are compared component by component. Spell older versions in full, such as `1.21.6`, rather than `21.6`. Ordinary comments use `//`.

For more syntax information, refer to the [MCFPP API](https://www.mcfpp.top).

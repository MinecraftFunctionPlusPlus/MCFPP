![MCFPP](https://user-images.githubusercontent.com/90548686/236462051-b901f99c-bdef-435c-8ca2-0dda37b25285.png)

[简体中文](./README_CN.md)

# MCFPP

MCFPP is a programming language that compiles into Minecraft data packs. It combines familiar control flow with typed data templates, generic functions, Minecraft commands, and a Java/Kotlin native interface that runs during compilation.

**MCFPP is in early development. This README describes the current `kotlin-latest` source; released compilers and existing projects may use older syntax.**

## Quick start

Build the compiler with **JDK 21** and the bundled Gradle wrapper:

```shell
# Windows
.\gradlew.bat build
# Linux / macOS
./gradlew build
```

The executable JAR is written to `build/libs/MCFPP-1.0.2-SNAPSHOT.jar`. Create this project:

```text
hello/
  mcfpp.json
  src/
    main.mcfpp
```

`mcfpp.json`:

```json
{
  "version": "26.3",
  "sourcePath": "src",
  "targetPath": "build",
  "namespace": "hello",
  "description": "My first MCFPP data pack"
}
```

`src/main.mcfpp`:

```mcfpp
namespace hello;

func greet() {
  /say Hello Minecraft!
}
```

Run the compiler **from the `hello` directory**; explicit relative source/output paths are resolved from the working directory:

```shell
java -jar /path/to/MCFPP-1.0.2-SNAPSHOT.jar mcfpp.json
```

Copy the generated `build/mcfpp` data pack into your world's `datapacks` directory, reload the world, and run `/function hello:greet`.

Read the [English documentation](https://www.mcfpp.top/en/quickstart/) or [Chinese documentation](https://www.mcfpp.top/zh/quickstart/) for the full tutorial.

## Language examples

### Variables, functions, and control flow

Variables use `var name as Type`, or infer their type from an initializer. Functions require parentheses, even with no parameters; return types follow `:`.

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

Statements end with a newline or semicolon. Continue an expression by placing its operator at the end of the previous line. Compound assignments `+=`, `-=`, `*=`, `/=`, and `%=` are supported; `++` and `--` are unavailable.

### Data templates and generics

Use `data` for structured values, with explicit `var` fields, constructors, and methods. Use `object data` for singleton or companion members. The former `class` syntax is unavailable in the current parser.

```mcfpp
/// # Box
/// Stores a value of the supplied type.
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

Parameters inside `<...>` require complete compile-time values. `type` is allowed in these parameter declarations; ordinary variables, fields, function parameters, and return values cannot hold type values.

### Type views and numeric conversions

`object` is the static supertype; `any` erases the public type. `as` creates a view of the same data location: it does not copy an object, call a constructor, or convert a number. Use conversion functions for numeric conversion:

```mcfpp
func convert() {
  var number as int = 3;
  var fraction as float = toFloat(number);
  var whole as int = toInt(fraction);
  print(whole);
}
```

`const` prevents reassignment after initialization; it does not require a compile-time value or freeze the contents of a mutable container. `dynamic` requires a usable runtime representation.

### Minecraft commands

A line starting with a single `/` after indentation is a Minecraft command. Use `${expression}` for interpolation:

```mcfpp
func announce() {
  var name = "Minecraft";
  /execute as @a run say Hello ${name}!
}
```

Put a division operator on the preceding line when splitting division across lines. Ordinary comments start with `//`; Markdown documentation comments start with `///`. Consecutive documentation lines form one block. The old `#`, `##`, `#{…}#`, and `###` comment forms are unavailable.

## Minecraft targets

Set `version` in the project configuration. One compilation produces one target data pack. Targets include `1.21`, `1.21.1`–`1.21.8`, `26.1`–`26.3`, and [earlier releases listed in the compiler](./src/main/kotlin/top/mcfpp/command/TargetCapabilities.kt). A recognized target does not guarantee that every standard-library API works on it.

For `26.3`, float arithmetic uses native number providers and NBT floats. Earlier targets use XiaoDouMathLib. The storage formats differ and existing persisted values are not automatically migrated. See the [float backend notes](./docs/float-providers-26.3.md).

Version directives select source before parsing:

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

`#if/#elif/#else/#endif` may be nested and surround imports, declarations, or statements. Conditions compare `MC` with a full version using `==`, `!=`, `<`, `<=`, `>`, or `>=`; logical combinations are unavailable. Version directives are separate from comments.

## Development and implementation status

`gradlew build` builds the compiler and runs its checks. After parser, type metadata, or MNI changes, regenerate the bundled library with `gradlew regenerateStdlib`, then run `gradlew check`.

The current native interface accepts a single `NativeCallContext` parameter. Libraries from an older metadata format must be recompiled; the current format is **MCFL77**.

- [Type system specification](./docs/type-system/specification.md)
- [Migration guide](./docs/type-system/migration.md)
- [Architecture and implementation boundaries](./docs/type-system/architecture-migration-report-2026-10-09.md)
- [Verification methods](./docs/type-system/verification.md)
- [Roadmap](./TODO_CN.md)

Exceptions/effects, tasks, generators, and lambdas are **design proposals**, not implemented language features. Their documents under `docs/` describe future work.

## Related projects

[MCSharp](https://github.com/Voziv/MCSharp) and [JustMCF](https://github.com/XiLaiTL/JustMCF) explore other approaches to Minecraft data pack development.

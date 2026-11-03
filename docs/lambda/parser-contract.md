# Lambda / 函数类型 parser 契约

本文记录 `src/main/antlr/mcfppLexer.g4` 与 `src/main/antlr/mcfppParser.g4` 的语法契约和生成 accessor。绑定、捕获、调用和库往返已接入编译器；源码用法和边界见 [教程](tutorial.md)。

## 1. 语法入口

```mcfpp
var add = { a: int, b: int -> a + b };
var inferred = { a, b -> a + b };
var action = { print("Hello"); };
var tail = { 1 + 2 };
var statement = { 1 + 2; };
items.map { item -> item + 1 };
items.fold(0) { sum, item -> sum + item };
consume(1) { value -> value };
var callback: (int, int) -> int = { a, b -> a + b };
var callback2: () -> void = { ; };
```

- 参数可以显式标注 `name: type`，也可以省略类型；不能混用旧箭头。
- 无参数 lambda 写成 `{ body }`，不写箭头。
- lambda 最后一个未以分号结束的表达式是尾表达式；见 §3。
- 唯一的尾实参可以放在调用括号外，适用于成员/普通调用目标。
- `items.map` 的成员名 `map` 是关键字，grammar 通过 `memberKeyword` 单独接受。
- `{}` 的 parse tree 仍是空 NBT compound；当目标签名明确要求 `() -> void` 时，绑定层可将它作为空回调。没有函数上下文时保持 NBT 语义；明确的空 lambda 可写 `{ ; }`。

## 2. 消歧规则

### 2.1 NBT / 复合字面量

`{a: 1}`、`{key: value}`、`{}` 仍走 `value -> nbtValue -> nbtCompound`。只有带 `->` 的参数形式或没有 `key: value` 形状的 body 才进入 `inlineLambda`。因此：

- `{ a: int, b: int -> a + b }` → lambda
- `{ a, b -> a + b }` → lambda
- `{ a: int }` → NBT compound（`a` 是 key，`int` 是 value）
- `{ print("x"); }` → lambda（`print("x")` 是 statement）
- `{ {a: 1} }` → lambda，尾表达式是 NBT compound

### 2.2 控制块

`if` / `else if` / `while` 的条件使用 `conditionExpression`，它不接受顶层尾 lambda，因此 `if flag { ... }` 中的 `{ ... }` 始终是控制块，不会被吞成 `flag { ... }` 的 lambda 实参。

条件中确实需要尾 lambda 调用时必须加括号：

```mcfpp
if (check { true }) { ... }
while (check { true }) { ... }
```

`do { ... } while check { true };` 没有后续 block，可以不加括号；`doWhileStatement` 仍使用完整 `expression`。

### 2.3 尾 lambda 的位置

尾 lambda 只出现在 `expression` 的顶层 `lambdaCallExpression`：

- `target { ... }`、`target(1) { ... }`
- `receiver.member { ... }`、`receiver.member(1) { ... }`
- 允许在尾 lambda 后再跟选择器，例如 `items.filter { ... }.size`

在更大的二元表达式中需要括号：

```mcfpp
var x = 1 + (items.fold(0) { sum, item -> sum + item });
```

`lambdaCallExpression` 不会以 lambda 字面量本身作为被调用目标，避免把 `{ true } { ... }` 解释成嵌套调用。

## 3. lambda body 与尾表达式

`lambdaBody` 的语法形状：

```antlr
lambdaBody
    :   lambdaTailExpression
    |   (statement separator)+ (lambdaTailExpression | statement)
    |   (statement separator)+
    |   statement
    ;
```

parse tree 契约：

| 源码 | `lambdaTailExpression()` | body 中的 `statement()` |
| --- | --- | --- |
| `{ 1 + 2 }` | 非空 | 空 |
| `{ 1 + 2; }` | 空 | 1 个 expression statement |
| `{ print(1); print(2) }` | `print(2)` | 1 个 statement |
| `{ var x = 1\n x }` | `x` | 1 个 field declaration |
| `{ var x = 1\n x; }` | 空 | 2 个 statement |
| `{ if c { ... } }` | 空 | 1 个 if statement |
| `{ a = 1 }` | 空 | 1 个赋值 statement |

规则：

- 最后一个表达式后直接遇到 `}`（中间可以有换行）→ `lambdaTailExpression`。
- 最后一个表达式后出现分号（即使分号前有换行）→ 归入 `statement` + `separator`，不产生尾表达式节点。`{ 1; }` 没有隐式返回值。
- 普通语句、声明、控制语句、赋值语句可以作为最后一个 body item 且不必带分隔符；多个 item 之间仍要求 `separator`。
- body 不能完全为空；空 body 用 `{ ; }`。
- body 不能把 `var x = 1 x` 这类缺少分隔符的写法当成 statement + tail。

## 4. 函数类型

```antlr
functionType
    :   LPAREN NL* (type (NL* COMMA NL* type)*)? NL* RPAREN
        NL* ARROW NL* functionReturnType
    ;
```

`functionType` 已加入 `typeBody`，因此可用于字段、参数、返回值、泛型元素、union 等所有 `type` 位置。`functionReturnType` 仍是 `type | VOID`，所以 `() -> void` 合法。函数类型参数列表只有类型，不写参数名。

```mcfpp
var f: (int, int) -> int = { a, b -> a + b };
var g: () -> void = { ; };
var h: ((int) -> int) -> void = { x -> x(1); };
func apply(callback: (int) -> int): (int) -> int { return callback; }
```

函数类型不带 `func` 前缀；旧的带前缀类型拼写不是兼容入口。具名声明仍写 `func apply(...)`，使用 `FUNCTION` token。箭头右结合：返回函数类型写 `(int) -> () -> int`；函数参数写 `((int) -> int) -> int`。

## 5. 旧箭头迁移接点

旧源码入口 `(args) => { ... }` **不再可从 `compilationUnit` 进入**：

- `callArgument`、`expression`、`primary` 都不引用 `legacyLambdaSyntax`。
- `legacyLambdaSyntax` 仅作为**诊断专用入口**保留，不会被正常源码解析选中：

```antlr
legacyLambdaSyntax
    :   LPAREN NL* (Identifier (NL* COMMA NL* Identifier)*)? NL* RPAREN
        NL* DOUBLE_ARROW NL* curlBlock
    ;
```

迁移诊断建议：

1. 正常 `compilationUnit()` 解析失败时，检查 token 流是否含 `mcfppLexer.DOUBLE_ARROW`。
2. 对疑似旧实参文本直接调用 `parser.legacyLambdaSyntax()`，可取得旧参数名和旧 `curlBlock`，用于给出“请改为 `{ a, b -> ... }`”的定位诊断。
3. `DOUBLE_ARROW` token 本身保留，不删除，避免破坏词法诊断和历史 token 编号。

## 6. 生成 parser 的 accessor

| 规则 | 主要 accessor |
| --- | --- |
| `inlineLambda` | `lambdaParameterList()`, `lambdaBody()`, `ARROW()`, `LCURL()`, `RCURL()` |
| `lambdaParameterList` | `lambdaParameter()`（`List`） |
| `lambdaParameter` | `Identifier()`, `COLON()`, `type()` |
| `lambdaBody` | `lambdaTailExpression()`, `statement()`（`List`）, `separator()`（`List`） |
| `lambdaTailExpression` | `expression()` |
| `functionType` | `LPAREN()`, `RPAREN()`, `type()`（`List`）, `ARROW()`, `functionReturnType()` |
| `lambdaCallExpression` | 三个分支：`jvmAccessExpression()` + `selector()` + `inlineLambda()`；`functionCall()` + `inlineLambda()`；`varWithSuffix()` + `inlineLambda()` |
| `selector` | `var()` 或 `memberKeyword()` + `arguments()` |
| `memberKeyword` | `MAP()` |
| `conditionExpression` | `primary()` 或 `commonBinaryOperatorExpression()` |

`InlineLambdaContext.Identifier()`（`List<TerminalNode>`）和 `curlBlock()` 仍由兼容 shim 生成，但新 lambda 不会填充它们；新代码请使用 `lambdaParameterList().lambdaParameter()` 和 `lambdaBody()`。

## 7. 兼容 shim 与集成迁移

grammar 中声明了 imaginary token：

```antlr
tokens { LEGACY_ACCESSOR_SHIM }
```

该 token 永远不会由 lexer 产生。它只出现在不可达 shim 分支中，用于让现有只读 consumer 继续编译：

- `inlineLambda` 保留 `Identifier+ curlBlock` shim，因此 `InlineLambdaContext.Identifier()` / `curlBlock()` 访问器仍存在。
- `ifStatement` / `elseIfStatement` / `whileStatement` 保留 `expression` shim，因此这些 context 仍有 `.expression()`；真实条件在 `.conditionExpression()`。
- `doWhileStatement` 没有改条件规则，仍使用 `.expression()`。

这些 shim 不构成合法源码入口，也不能作为语义实现使用。集成时必须迁移：

| 旧 accessor | 新 accessor / 行为 |
| --- | --- |
| `IfStatementContext.expression()` | `conditionExpression()`；`.expression()` 仅 shim，真实解析为 `null` |
| `ElseIfStatementContext.expression()` | `conditionExpression()` |
| `WhileStatementContext.expression()` | `conditionExpression()` |
| `DoWhileStatementContext.expression()` | 不变 |
| `InlineLambdaContext.Identifier()` | `lambdaParameterList().lambdaParameter().map { it.Identifier() }` |
| `InlineLambdaContext.curlBlock()` | `lambdaBody()`；尾表达式在 `lambdaBody().lambdaTailExpression()` |
| `ExpressionContext.primary()` / `.commonBinaryOperatorExpression()` | 顶层还可能是 `lambdaCallExpression()` |
| `SelectorContext.var()` | `map` 等成员由 `memberKeyword()` 表示，可能没有 `var()` |
| `TypeBodyContext` | 新增 `functionType()` 分支 |

已同步的主要 consumer：

- `src/main/kotlin/top/mcfpp/analysis/PrimitiveCompiler.kt`
- `src/main/kotlin/top/mcfpp/antlr/MCFPPImVisitor.kt`
- `src/main/kotlin/top/mcfpp/antlr/MCFPPCompileTimeVisitor.kt`
- `src/main/kotlin/top/mcfpp/mni/expand/SourceBodyBinder.kt`
- `src/main/kotlin/top/mcfpp/mni/expand/SourceCodeBinder.kt`

MNI source callback 使用新的 `lambdaParameterList` / `lambdaBody`，值捕获、只读和局部返回与普通 lambda 一致。只接受静态 `INLINE` 正文的 API 会拒绝动态函数值。两类 lambda/`REF` 互操作暂缓，见教程边界。

## 8. Parser 生成与调用接口

Gradle 的 `generateGrammarSource` 使用 ANTLR 4.13.1，生成包 `top.mcfpp.antlr`，输出到：

```
build/generated-src/antlr/main/top/mcfpp/antlr
```

`compileKotlin` / `compileTestKotlin` 依赖该生成任务。不要手改生成文件。

本地检查（等价于生成任务的核心参数，不写入共享 build 输出）可这样调用：

```bash
java -jar antlr-4.13.1-complete.jar -package top.mcfpp.antlr -o <out> mcfppLexer.g4
java -jar antlr-4.13.1-complete.jar -visitor -long-messages \
  -package top.mcfpp.antlr -lib <grammar-dir> -o <out> mcfppParser.g4
```

`LambdaSyntaxTest` 的调用方式：

```kotlin
val lexer = mcfppLexer(CharStreams.fromString(source))
val parser = mcfppParser(CommonTokenStream(lexer))
parser.removeErrorListeners()
parser.addErrorListener(listener)
val tree = parser.compilationUnit()
```

## 9. Token 编号兼容

`mcfppLexer.g4` 原有 token 编号保持不变。`ARROW` 追加在文件末尾（`REF` 之后），因此不移动既有 token 编号，避免破坏序列化 library parse tree 的 token 编号：

```antlr
mode AddedKeywords;
REF: 'ref';

// Appended token: keep existing token numbers stable for serialized parse trees.
mode DEFAULT_MODE;
ARROW: '->' ;
```

`DOUBLE_ARROW: '=>'` 保留在原位置。

## 10. 验证入口

`LambdaSyntaxTest` 检查上述 grammar 与消歧；`LambdaBindingTest`、`LambdaCallTest` 检查绑定与实际生成，其他 `Lambda*Test` 覆盖捕获、逃逸、动态选择、重入、集合及库往返。源码与 DSB 运行方法见教程。历史阶段检查只作为对应输入状态的先前证据，最终集成结果单独记录。

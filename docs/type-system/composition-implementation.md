# 类型组合与工厂实施记录

本轮依据完整保存在仓库的 [原始计划](./type-composition-and-factory-plan.md)，从提交 `a00ddbff` 在分支
`codex/type-composition-factories` 开始。该提交保存开始实施前的工作区；本地运行日志未纳入提交。
本轮是独立任务，不恢复已有的 `.codex-workflow-graph` 工作流。

## 已确认的设计决定

- 父限定调用采用 `this.Parent.new(args...)`，等价于 `Parent.new(this, args...)`。
  工厂显式声明接收该值的第一个普通参数；工厂自身没有隐式 `this`。
- 对象字面量的重复字段报错，并指出字段名。
- 空模板生成返回 `{}` 的默认工厂。
- 显式工厂每条可达成功路径必须显式 `return`，不采用尾表达式返回。
- 可选 `never` 字段只能缺失。
- 共享视图写入同时满足视图和真实存储的约束；联合字段写入满足所有分支。
- `new` 位于所属模板体内，使用正文规定的模板词法环境与完整 `Self`。
- 对象展开仍属后续工作，不在本轮实现范围。

## 当前实现地图

以下路径均相对仓库根目录。

| 领域 | 实现位置 | 开始实施时的行为 | 现有检查 |
| --- | --- | --- | --- |
| 类型身份 | `type/TypeId.kt`、`type/MCFPPType.kt` | 按 TypeId 比较；无对象形状、字面量类型、never | `TypeKernelTest` |
| 类型关系与组合 | `type/TypeRelations.kt`、`MCFPPUnionType.kt`、`TypeSyntaxResolver.kt`、`model/compound/UnionDataTemplate.kt` | 名义子类型；联合展开去重；交叉采用合成继承模板 | `TypeKernelTest`、`IntersectionTemplateCompletionTest` |
| 字段、常量与存在性 | `analysis/MemberDescriptor.kt`、`ConstantBinding.kt`、`CompilerValue.kt`、`TemplateLiteralSupport.kt` | 不可变常量快照、可选字段与存在性事实已具备；const 类型仍为基本类型 | `DataConstantTest`、`DataConstantCallTest`、`DataPresenceTest` |
| 继承与布局 | `model/compound/DataTemplate.kt`、`model/scope/CompoundDataScope.kt`、`analysis/InheritanceContractValidator.kt` | 已扁平化并保留来源；独立同名字段仍可能歧义；有方法继承检查 | `DataInheritanceFieldsTest`、`DataInheritanceGraphTest`、`DataInheritanceContractTest` |
| 视图与写入 | `analysis/ValueModel.kt`、`StorageAccess.kt`、`type/ReinterpretationCompatibility.kt` | as 共享 Place；普通复制独立；const 与重叠失效已具备；需补真实可变字段类型约束 | `StorageViewTest`、常量调用测试 |
| 语法与声明 | `antlr/mcfppParser.g4`、`MCFPPFieldVisitor.kt`、`MCFPPGenericDataTemplateFieldVisitor.kt` | constructor 声明与隐式默认构造器；new 当前是普通标识符 | `ConstructorResolutionTest`、`TemplateFieldInferenceTest` |
| 字面量与推断 | `antlr/MCFPPExprVisitor.kt`、`analysis/DeclarationBinding.kt` | 无上下文推断 dict；模板上下文禁止多余字段并应用默认值；尚无 shorthand | `DataTemplateLiteralTest` |
| 函数、返回与构造 | `model/function/Function.kt`、`DataTemplateConstructor.kt`、`ConstructorPlan.kt`、`antlr/MCFPPImVisitor.kt` | 构造先分配 this，再执行父链与初始化；普通函数检查显式返回 | `ConstructorExecutionTest`、`DataInheritanceConstructorPlanTest` |
| 库与缓存 | `io/TypeIdentitySerializer.kt`、`TypeDescriptorSerializer.kt`、`library/compiler/`、`library/model/`、`library/format/`、`library/syntax/` | 保存有限类型、声明 AST、构造器与字段初值 | `LibraryTypeTableTest`、`LibraryCompoundTest`、`LibrarySyntaxCodecTest` |

实现文件位于 `src/main/kotlin/top/mcfpp/`，语法位于 `src/main/antlr/`；测试位于
`src/test/kotlin/top/mcfpp/test/`，库测试位于其 `library/` 子目录。

## 基线验证

- 命令：`java -version`；结果：JDK 25.0.4。
- 命令：`gradlew.bat test --tests top.mcfpp.test.TypeKernelTest --tests top.mcfpp.test.DataTemplateLiteralTest --tests top.mcfpp.test.ConstructorResolutionTest --console=plain`。
- 结果：测试未运行，`:prepareBuiltinImportPlan` 的 runtimeClasspath 缺少本地
  `moe.afox.mcfpm:{core,mcfpp-adapter,repository-maven}:0.1.0-SNAPSHOT`。
  Kotlin/Java 编译任务为 UP-TO-DATE，不能据此声称本轮完成新编译。
- 按 `docs/cli.md` 从相邻 `Mcfpm` 工程发布所需 SDK 后，再执行相同基线检查。

## 实施顺序

1. 不可变对象字段合同、字面量类型、never、交叉与联合规范化、中央结构子类型。
2. 使用同一组合规则合并继承字段；保留名义祖先与声明来源。
3. 对象字面量与词法 shorthand；普通函数工厂声明、调用和隐式工厂。
4. 共享位置写入、联合共同字段与常量类型接入。
5. 库保存恢复、迁移说明、聚焦回归及最终全量检查。

各步结果与尚未通过的检查将在实际执行后记录。既有 constructor、父初始化和方法覆盖测试中，
与新合同直接冲突的案例须迁移，不能将旧断言失败记作新行为通过。

## 继续工作检查点（进行中）

用户授权完整实施、先提交现有代码再建分支，以及按子任务调整 agent 模型与推理强度。
原始计划第 15 节中的所有决定已落实在本文“已确认的设计决定”中；对象展开继续推迟。

当前各实现仍在集成，不能声称计划完成或测试通过。协调者负责最终构建、测试与记录。

| agent | 文件所有权与任务 |
| --- | --- |
| `/root/type_map` | 类型包（协调者持有 MCFPPType/TypeSyntaxResolver 除外）、规范化与中央关系测试 |
| `/root/factory_map` | grammar、FieldVisitor/GenericFieldVisitor、TemplateFactory、DataTemplate 工厂登记、标准库构造迁移 |
| `/root/inheritance` | CompoundDataScope、InheritanceContractValidator、DataTemplate.flatExtends、PreparedTemplateHeaderReader、继承测试 |
| `/root/storage_safety` | StorageAccess、ConstantBinding、UnionTypeVar、Var/MCInt/NumericConversions、写约束与常量测试 |
| `/root/serialization` | io 类型编码与 library 类型/工厂/语法保存恢复 |
| `/root/object_binding` | DeclarationBinding、TemplateLiteralSupport、PrimitiveCompiler 字面量绑定与字面量测试迁移 |
| `/root/toolchain` | 本地 SDK 设置与独立 worktree 的原始基线验证 |
| `/root` | ExprVisitor、ImVisitor、Function、MCFPPType、TypeSyntaxResolver，集成和最终验证 |

核心共享 API：`ObjectFieldContract(name,type,optional,kind,origins)`、
`ObjectMemberKind.VAR/CONST`、`MCFPPObjectType.fields/fieldsByName`、
`MCFPPLiteralType(primitive,value)`、`MCFPPNeverType`、
`TypeCompositions.objectShape/intersection/union/widen/literalOrNull/restore`、
`TypeRelations.isNominalSubtype`。结构身份复用保留键的 TypeId.Applied/Specialized，
对象类型复用合成模板的运行时布局；纯形状关系直接使用不可变字段合同。

工厂 API：`TemplateFactory(template,ctx,isImplicit)`，静态普通函数、无隐式 this；
`DataTemplate.factories`、`resolveFactory(args)`。调用必须使用普通函数返回值，不预分配 receiver。
语法 shorthand 的 `nbtKeyValuePair.expression()` 可为空，公共解析入口是
`TemplateLiteralSupport.compoundEntries(compound)`；重复键通过它统一诊断。

SDK 已用 JDK 25.0.4 从相邻 Mcfpm 成功发布到 `F:/DevCache/.m2/repository`；
默认 Maven 用户目录是失效符号链接，验证命令须带
`-Dmaven.repo.local=F:/DevCache/.m2/repository`。独立基线 worktree：
`F:/DevCache/mcfpp-type-composition-baseline`，固定在 `a00ddbff`，运行最初三个测试类。
该 worktree 的结果不覆盖主工作区的后续改动。

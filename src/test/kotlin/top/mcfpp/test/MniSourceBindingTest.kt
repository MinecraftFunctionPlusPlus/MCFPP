package top.mcfpp.test

import org.antlr.v4.runtime.CharStreams
import org.antlr.v4.runtime.CommonTokenStream
import org.antlr.v4.runtime.tree.ParseTree
import top.mcfpp.Project
import top.mcfpp.analysis.StorageAccess
import top.mcfpp.antlr.mcfppLexer
import top.mcfpp.antlr.mcfppParser
import top.mcfpp.core.lang.MCInt
import top.mcfpp.mni.api.*
import top.mcfpp.mni.expand.*
import top.mcfpp.model.function.Function
import top.mcfpp.type.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class MniSourceBindingTest {
    private val intToVoidCallback = SourceCallbackSignature(
        listOf(SourceCallbackParameter(SourceCallbackType.Declared(MCFPPBaseType.Int.typeId))),
        SourceCallbackType.Declared(MCFPPPrivateType.Void.typeId)
    )
    private val noArgsToIntCallback = SourceCallbackSignature(
        emptyList(),
        SourceCallbackType.Declared(MCFPPBaseType.Int.typeId)
    )
    private val catalog = SourceTypeCatalog.snapshot(
        listOf(
            MCFPPBaseType.Int,
            MCFPPBaseType.Bool,
            MCFPPBaseType.Float,
            MCFPPBaseType.String,
            MCFPPBaseType.Any,
            MCFPPBaseType.Object,
            MCFPPPrivateType.Void,
            MCFPPListType(MCFPPBaseType.Int)
        ),
        callbacks = listOf(intToVoidCallback, noArgsToIntCallback)
    )
    private val intType get() = catalog.ref(MCFPPBaseType.Int)
    private val voidType get() = catalog.ref(MCFPPPrivateType.Void)
    private val listType get() = catalog.ref(MCFPPListType(MCFPPBaseType.Int))
    private val objectType get() = catalog.ref(MCFPPBaseType.Object)
    private val sourceSession = SessionId(0)

    @Test fun ordinaryExpressionBindingKeepsCanonicalTypeConstantProofAndSpan() {
        val parsed = parse("func main(){ var result = 1 + 2 * 3; }")
        val expression = find< mcfppParser.ExpressionContext>(parsed.tree) { it.text == "1+2*3" }
        val bound = SnapshotSourceCodeBinder().expression(expression, environment())
        assertTrue(bound.type.sameType(intType))
        assertEquals(ConstantValue.Integral(7), bound.constant)
        assertEquals("binding.mcfpp", bound.source.file)
        assertTrue(bound.source.endOffset > bound.source.startOffset)
        assertIs<BoundExprNode.Binary>(bound.node)
    }

    @Test fun bothArgumentGrammarFormsBindToTheSameResolvedTargetInSourceOrder() {
        val function = function(
            "target",
            listOf(
                ParameterView.of("a", intType, ParamMode.VALUE),
                ParameterView.of("b", intType, ParamMode.VALUE)
            ),
            voidType
        )
        val firstSource = "func main(){ target(1, 2); target(1, second, { i -> target(i, 2); }); }"
        val health = SourceSymbol("second", intType, SymbolId(sourceSession, 7), writable = true)
        val callback = FunctionSignature.of(listOf(ParameterView.of("index", intType, ParamMode.VALUE)), voidType)
        val callbackType = catalog.inlineType(callback)!!
        val mixedFunction = function(
            "target",
            listOf(
                ParameterView.of("a", intType, ParamMode.VALUE),
                ParameterView.of("b", intType, ParamMode.REF),
                ParameterView.of("callback", callbackType, ParamMode.INLINE, callback)
            ),
            voidType,
            FunctionKind.MACRO,
            declaration = "target-mixed"
        )
        val env = environment(
            listOf(health),
            listOf(
                SourceFunction("target", function, defaultedNormalParameters = emptySet()),
                SourceFunction("target", mixedFunction)
            ),
            sourceText = firstSource
        )
        val binder = SnapshotSourceCodeBinder()
        val tree = parse(firstSource).tree
        val calls = collect<mcfppParser.FunctionCallContext>(tree)
        val first = binder.call(calls[0], env)
        val second = InternalReferenceCalls.bind(calls[1], env, setOf(1))
        assertSame(function, first.target.function)
        assertEquals(2, first.arguments.size)
        assertEquals(
            listOf(1L, 2L),
            first.arguments.map {
                ((it as BoundCallArgument.Value).expression.constant as ConstantValue.Integral).value
            }
        )
        assertEquals(3, second.arguments.size)
        assertIs<BoundCallArgument.Reference>(second.arguments[1])
        val callbackArg = assertIs<BoundCallArgument.Callback>(second.arguments[2])
        assertEquals("{ i -> target(i, 2); }", callbackArg.callback.sourceText)
        assertSame(callbackType, callbackArg.callback.callbackType)
        assertTrue(first.source.startOffset < first.arguments[0].source.startOffset)
        assertEquals(BoundEffect.CALL, first.effect)
    }

    @Test fun referenceBindingRetainsSymbolIdentityAndReadonlySourceBindingHasNoCompilerSinks() {
        Project.init()
        val current = Function("source_binding", "fixture.mni", null)
        val healthValue = MCInt(5)
        val symbol = SourceSymbol("health", intType, SymbolId(sourceSession, 41), writable = true)
        val function = function(
            "mutate",
            listOf(ParameterView.of("value", intType, ParamMode.REF)),
            voidType,
            FunctionKind.MACRO
        )
        current.runInFunction {
            current.scope.putVar("health", healthValue)
            StorageAccess.ensure(healthValue)
            val env = environment(listOf(symbol), listOf(SourceFunction("mutate", function)))
            val syntax = find<mcfppParser.FunctionCallContext>(parse("func main(){ mutate(health); }").tree) { it.namespaceID().text == "mutate" }
            val commandsBefore = current.commands.toList()
            val variablesBefore = current.scope.allVars.toList()
            val childrenBefore = current.child.toList()
            val flowBefore = StorageAccess.flowSnapshot(listOf(healthValue))
            val bound = InternalReferenceCalls.bind(syntax, env, setOf(0))
            val location = (bound.arguments.single() as BoundCallArgument.Reference).location
            assertEquals(symbol.symbol, location.root.symbol)
            assertTrue(location.writable)
            assertSame(function, bound.target.function)
            assertEquals(FunctionKind.MACRO, (bound.target.function as ImmutableFunctionRef<*>).kind)
            assertSame(current, Function.currFunction)
            assertEquals(commandsBefore, current.commands.toList())
            assertEquals(variablesBefore, current.scope.allVars.toList())
            assertEquals(childrenBefore, current.child.toList())
            assertEquals(flowBefore, StorageAccess.flowSnapshot(listOf(healthValue)))
            assertEquals(0, Project.errorCount)
        }
    }

    @Test fun dynamicIndicesAreCapturedAsPureSourceExpressionsAndGetterAccessIsNotExecuted() {
        val values = SourceSymbol("values", listType, SymbolId(sourceSession, 51), writable = true)
        val index = SourceSymbol("index", intType, SymbolId(sourceSession, 52))
        val native = function(
            "mutate",
            listOf(ParameterView.of("value", intType, ParamMode.VALUE)),
            voidType,
            FunctionKind.NATIVE
        )
        val indexCall = function("indexCall", emptyList(), intType)
        val macro = function("mutateRef", listOf(ParameterView.of("value", intType, ParamMode.REF)), voidType, FunctionKind.MACRO)
        val source = "func main(){ mutateRef(values[indexCall()]); }"
        val env = environment(
            listOf(values, index),
            listOf(
                SourceFunction("indexCall", indexCall, BoundEffect.CALL),
                SourceFunction("mutateRef", macro, BoundEffect.WRITE),
                SourceFunction("mutate", native, BoundEffect.UNKNOWN)
            ),
            sourceText = source
        )
        val syntax = find<mcfppParser.FunctionCallContext>(parse(source).tree)
        val bound = InternalReferenceCalls.bind(syntax, env, setOf(0))
        val location = (bound.arguments.single() as BoundCallArgument.Reference).location
        val step = assertIs<BoundLocationStep.Index>(location.path.single())
        assertNull(step.constantIndex)
        assertIs<BoundExprNode.Call>(step.expression.node)
        assertEquals(listOf(BoundEffect.CALL, BoundEffect.WRITE), bound.effectOrder)
        assertEquals(BoundEffect.WRITE, bound.effect)
        val oldMniSource = "func main(){ mutate(1); }"
        val oldMni = SnapshotSourceCodeBinder().call(find<mcfppParser.FunctionCallContext>(parse(oldMniSource).tree) { it.namespaceID().text == "mutate" }, env)
        assertSame(native, oldMni.target.function)
        assertEquals(BoundEffect.UNKNOWN, oldMni.effect)
        val getter = parse("func main(){ health.value; }").tree
        val member = find<mcfppParser.ExpressionContext>(getter) { it.text == "health.value" }
        val failure = assertFailsWith<SourceBindingFailure> {
            SnapshotSourceCodeBinder().expression(
                member,
                environment(
                    listOf(
                        SourceSymbol("health", intType, SymbolId(sourceSession, 53))
                    )
                )
            )
        }
        assertTrue(failure.message!!.contains("immutable snapshot"))
    }

    @Test fun overloadsUseConversionRanksAndAmbiguousEqualSignaturesStayAmbiguous() {
        val intOverload = function(
            "select",
            listOf(ParameterView.of("value", intType, ParamMode.VALUE)),
            voidType,
            declaration = "select-int"
        )
        val objectOverload = function(
            "select",
            listOf(ParameterView.of("value", objectType, ParamMode.VALUE)),
            voidType,
            declaration = "select-object"
        )
        val intCall = find<mcfppParser.FunctionCallContext>(parse("func main(){ select(1); }").tree) { it.namespaceID().text == "select" }
        val env = environment(functions = listOf(SourceFunction("select", objectOverload), SourceFunction("select", intOverload)))
        assertSame(intOverload, SnapshotSourceCodeBinder().call(intCall, env).target.function)
        val duplicate = function(
            "select",
            listOf(ParameterView.of("value", intType, ParamMode.VALUE)),
            voidType,
            declaration = "select-int-duplicate"
        )
        assertFailsWith<SourceBindingFailure> {
            SnapshotSourceCodeBinder().call(
                intCall,
                environment(
                    functions = listOf(
                        SourceFunction("select", intOverload),
                        SourceFunction("select", duplicate)
                    )
                )
            )
        }
    }

    @Test fun invalidOperatorsAndCrossCatalogTypesAreRejectedWithoutNameBasedIdentity() {
        val invalid = find<mcfppParser.ExpressionContext>(parse("func main(){ true + 1; }").tree) { it.text == "true+1" }
        assertFailsWith<SourceBindingFailure> { SnapshotSourceCodeBinder().expression(invalid, environment()) }
        val otherCatalog = SourceTypeCatalog.snapshot(
            listOf(MCFPPBaseType.Int, MCFPPPrivateType.Void),
            callbacks = listOf(intToVoidCallback)
        )
        assertFalse(intType.sameType(otherCatalog.ref(MCFPPBaseType.Int)))
        val one = FunctionSignature.of(listOf(ParameterView.of("left", intType, ParamMode.VALUE)), voidType)
        val two = FunctionSignature.of(listOf(ParameterView.of("right", intType, ParamMode.VALUE)), voidType)
        val callbackOne = catalog.inlineType(one)!!
        val callbackTwo = catalog.inlineType(two)!!
        assertSame(callbackOne, callbackTwo)
        assertEquals(TypeRelations.Conversion.EXACT.rank, catalog.conversionRank(callbackOne, callbackTwo))
        val otherCallback = otherCatalog.inlineType(
            FunctionSignature.of(
                listOf(ParameterView.of("argument", otherCatalog.ref(MCFPPBaseType.Int), ParamMode.VALUE)),
                otherCatalog.ref(MCFPPPrivateType.Void)
            )
        )!!
        assertFalse(callbackOne.sameType(otherCallback))
        assertNull(catalog.conversionRank(callbackOne, otherCallback))
    }

    @Test fun int32LiteralBoundsAreEnforcedAndArithmeticFollowsRuntimeWraparound() {
        val binder = SnapshotSourceCodeBinder()
        val maximum = find<mcfppParser.ExpressionContext>(parse("func main(){ 2147483647; }").tree) { it.text == "2147483647" }
        val overflow = find<mcfppParser.ExpressionContext>(parse("func main(){ 2147483647 + 1; }").tree) { it.text == "2147483647+1" }
        val minimum = find<mcfppParser.ExpressionContext>(parse("func main(){ -2147483648; }").tree) { it.text == "-2147483648" }
        val outOfRange = find<mcfppParser.ExpressionContext>(parse("func main(){ 2147483648; }").tree) { it.text == "2147483648" }
        val nonFinite = find<mcfppParser.ExpressionContext>(parse("func main(){ 1e100f; }").tree) { it.text == "1e100f" }
        assertEquals(ConstantValue.Integral(Int.MAX_VALUE.toLong()), binder.expression(maximum, environment()).constant)
        assertEquals(ConstantValue.Integral(Int.MIN_VALUE.toLong()), binder.expression(overflow, environment()).constant)
        assertEquals(ConstantValue.Integral(Int.MIN_VALUE.toLong()), binder.expression(minimum, environment()).constant)
        assertFailsWith<SourceBindingFailure> { binder.expression(outOfRange, environment()) }
        assertFailsWith<SourceBindingFailure> { binder.expression(nonFinite, environment()) }
    }

    @Test fun readonlyArgumentsRemainInEvaluationTreeAndSelectGenericTarget() {
        val generic = function("generic", listOf(ParameterView.of("value", intType, ParamMode.VALUE)), voidType)
        val declaration = SourceFunction("generic", generic, readonlyParameters = listOf(SourceParameter(intType)))
        val source = "func main(){ generic<1>(2); }"
        val call = SnapshotSourceCodeBinder().call(
            find<mcfppParser.FunctionCallContext>(parse(source).tree) { it.namespaceID().text == "generic" },
            environment(functions = listOf(declaration), sourceText = source)
        )
        assertEquals(ConstantValue.Integral(1), call.readonlyArguments.single().constant)
        assertEquals(2, call.evaluationOrder.size)
        assertIs<SourceEvaluationArgument.Readonly>(call.evaluationOrder.first())
        assertIs<SourceEvaluationArgument.Normal>(call.evaluationOrder.last())
    }

    @Test fun staticInlineFunctionSymbolsRetainTheirImmutableDeclarationAndBodySnapshot() {
        val callbackType = catalog.inlineType(
            FunctionSignature.of(
                listOf(ParameterView.of("value", intType, ParamMode.VALUE)),
                voidType
            )
        )!!
        val invoke = function(
            "invoke",
            listOf(
                ParameterView.of(
                    "body",
                    callbackType,
                    ParamMode.INLINE,
                    callbackType.inlineSignature()!!
                )
            ),
            voidType,
            FunctionKind.MACRO
        )
        val callback = function("callback", listOf(ParameterView.of("n", intType, ParamMode.VALUE)), voidType)
        val source = "func main(){ invoke(callback); }"
        val callbackDeclaration = SourceFunction(
            "callback",
            callback,
            sourceBody = SourceFunctionBody("func callback(n: int) {}", SourceSpan("binding.mcfpp", 0, 26, 1, 1, 1, 27))
        )
        val call = SnapshotSourceCodeBinder().call(
            find<mcfppParser.FunctionCallContext>(parse(source).tree),
            environment(functions = listOf(SourceFunction("invoke", invoke), callbackDeclaration), sourceText = source)
        )
        val argument = assertIs<BoundCallArgument.Callback>(call.arguments.single())
        assertSame(callbackDeclaration, argument.callback.functionTarget)
        assertSame(callbackType, argument.callback.callbackType)
        assertEquals("callback", argument.callback.sourceText)
        assertEquals("func callback(n: int) {}", argument.callback.functionTarget!!.sourceBody!!.sourceText)
    }

    @Test fun invalidIndexTypesAndPropertyAssignmentSyntaxAreRejected() {
        val values = SourceSymbol("values", listType, SymbolId(sourceSession, 88), writable = true)
        val badIndex = find<mcfppParser.ExpressionContext>(parse("func main(){ values[true]; }").tree) { it.text == "values[true]" }
        assertFailsWith<SourceBindingFailure> {
            SnapshotSourceCodeBinder().location(badIndex, environment(listOf(values)))
        }
        val assignment = find<mcfppParser.ExpressionContext>(parse("func main(){ values[key = 1]; }").tree) { it.text == "values[key=1]" }
        assertFailsWith<SourceBindingFailure> {
            SnapshotSourceCodeBinder().expression(assignment, environment(listOf(values)))
        }
    }

    @Test fun effectfulArgumentsAndDynamicIndexKeepTheirLeftToRightOrder() {
        val target = function(
            "target",
            listOf(
                ParameterView.of("a", intType, ParamMode.VALUE),
                ParameterView.of("b", intType, ParamMode.VALUE),
                ParameterView.of("destination", intType, ParamMode.REF)
            ),
            voidType,
            FunctionKind.MACRO
        )
        val effectA = function("effectA", emptyList(), intType)
        val effectB = function("effectB", emptyList(), intType)
        val indexCall = function("indexCall", emptyList(), intType)
        val values = SourceSymbol("values", listType, SymbolId(sourceSession, 81), writable = true)
        val source = "func main(){ target(effectA(), effectB(), values[indexCall()]); }"
        val env = environment(
            listOf(values),
            listOf(
                SourceFunction("target", target, BoundEffect.WRITE),
                SourceFunction("effectA", effectA, BoundEffect.CALL),
                SourceFunction("effectB", effectB, BoundEffect.UNKNOWN),
                SourceFunction("indexCall", indexCall, BoundEffect.READ)
            ),
            sourceText = source
        )
        val bound = InternalReferenceCalls.bind(find<mcfppParser.FunctionCallContext>(parse(source).tree) { it.namespaceID().text == "target" }, env, setOf(2))
        assertEquals(listOf(BoundEffect.CALL, BoundEffect.UNKNOWN, BoundEffect.READ, BoundEffect.WRITE), bound.effectOrder)
        assertEquals(BoundEffect.UNKNOWN, bound.effect)
        assertEquals(3, bound.evaluationOrder.size)
    }

    @Test fun variableArgumentsShadowStaticFunctionsBeforeOverloadAndModeSelection() {
        val exact = function(
            "target",
            listOf(ParameterView.of("value", intType, ParamMode.VALUE)),
            voidType,
            declaration = "variable-int"
        )
        val broad = function(
            "target",
            listOf(ParameterView.of("value", objectType, ParamMode.VALUE)),
            voidType,
            declaration = "variable-object"
        )
        val mixed = function(
            "mixed",
            listOf(
                ParameterView.of("a", intType, ParamMode.VALUE),
                ParameterView.of("b", intType, ParamMode.REF)
            ),
            voidType,
            FunctionKind.MACRO
        )
        val namedFunction = SourceFunction(
            "health",
            function("health", emptyList(), intType),
            sourceBody = SourceFunctionBody("func health(): int { return 1; }", SourceSpan.unknown())
        )
        val source = "func main(){ target(health); mixed(health, health); }"
        val env = environment(
            listOf(SourceSymbol("health", intType, SymbolId(sourceSession, 101), true)),
            listOf(SourceFunction("target", broad), SourceFunction("target", exact), SourceFunction("mixed", mixed), namedFunction),
            sourceText = source
        )
        val binder = SnapshotSourceCodeBinder()
        val calls = collect<mcfppParser.FunctionCallContext>(parse(source).tree)
        val ordinary = binder.call(calls[0], env)
        assertSame(exact, ordinary.target.function)
        assertIs<BoundExprNode.Read>(assertIs<BoundCallArgument.Value>(ordinary.arguments.single()).expression.node)
        val mixedCall = InternalReferenceCalls.bind(calls[1], env, setOf(1))
        assertIs<BoundCallArgument.Value>(mixedCall.arguments[0])
        assertIs<BoundCallArgument.Reference>(mixedCall.arguments[1])

        val signature = FunctionSignature.of(emptyList(), intType)
        val callbackType = catalog.inlineType(signature)!!
        val invoke = function(
            "invoke",
            listOf(ParameterView.of("body", callbackType, ParamMode.INLINE, signature)),
            voidType,
            FunctionKind.MACRO
        )
        val shadowSource = "func main(){ invoke(health); }"
        val shadowEnv = environment(
            listOf(SourceSymbol("health", callbackType, SymbolId(sourceSession, 102))),
            listOf(SourceFunction("invoke", invoke), namedFunction),
            sourceText = shadowSource
        )
        val failure = assertFailsWith<SourceBindingFailure> {
            binder.call(find<mcfppParser.FunctionCallContext>(parse(shadowSource).tree), shadowEnv)
        }
        assertTrue(failure.message!!.contains("INLINE requires a static lambda body") &&
            failure.message!!.contains("dynamic function values"), failure.message)
    }

    @Test fun selectedFieldIndicesRetainValueAndReferenceEffectsWithoutCompilerSideEffects() {
        Project.init()
        val current = Function("source_field_index", "fixture.mni", null)
        val sentinel = MCInt(19)
        val target = function(
            "target",
            listOf(
                ParameterView.of("a", intType, ParamMode.VALUE),
                ParameterView.of("b", intType, ParamMode.VALUE),
                ParameterView.of("destination", intType, ParamMode.REF)
            ),
            voidType,
            FunctionKind.MACRO
        )
        val index = function("indexCall", emptyList(), intType)
        val oldNative = function("oldNative", emptyList(), intType, FunctionKind.NATIVE)
        val source = "func main(){ target(record.values[indexCall()], oldNative(), record.values[indexCall()]); }"
        val env = environment(
            listOf(SourceSymbol("record", objectType, SymbolId(sourceSession, 110), true)),
            listOf(
                SourceFunction("target", target, BoundEffect.WRITE),
                SourceFunction("indexCall", index),
                SourceFunction("oldNative", oldNative, BoundEffect.UNKNOWN)
            ),
            sourceText = source,
            fields = listOf(SourceField(objectType, "values", listType, true))
        )
        current.runInFunction {
            current.scope.putVar("sentinel", sentinel)
            StorageAccess.ensure(sentinel)
            current.commands.add(top.mcfpp.command.Command("say source-binding-sentinel"))
            sentinel.stackIndex = 9
            val commands = current.commands.toList()
            val vars = current.scope.vars.toMap()
            val children = current.child.toList()
            val parents = current.parent.toList()
            val constants = Project.constants.toMap()
            val macros = Project.macroFunction.toMap()
            val scoreboards = top.mcfpp.model.scope.GlobalScope.scoreboards.toMap()
            val tags = top.mcfpp.model.scope.GlobalScope.functionTags.mapValues { it.value.functions.toList() }
            val flow = StorageAccess.flowSnapshot(listOf(sentinel))
            val binder = SnapshotSourceCodeBinder()
            val call = InternalReferenceCalls.bind(find<mcfppParser.FunctionCallContext>(parse(source).tree) { it.namespaceID().text == "target" }, env, setOf(2))
            val value = assertIs<BoundCallArgument.Value>(call.arguments[0]).expression
            val indexed = assertIs<BoundExprNode.Index>(value.node)
            assertIs<BoundExprNode.Field>(indexed.receiver.node)
            assertSame(index, assertIs<BoundExprNode.Call>(indexed.index.node).sourceCall.target.function)
            assertTrue(value.type.sameType(intType))
            assertEquals(BoundEffect.CALL, value.effect)
            val location = assertIs<BoundCallArgument.Reference>(call.arguments[2]).location
            assertIs<BoundLocationStep.Field>(location.path[0])
            assertIs<BoundExprNode.Call>(assertIs<BoundLocationStep.Index>(location.path[1]).expression.node)
            assertEquals(listOf(BoundEffect.CALL, BoundEffect.UNKNOWN, BoundEffect.CALL, BoundEffect.WRITE), call.effectOrder)
            for (invalidSource in listOf("record.values[true]", "record.getter")) {
                val syntax = find<mcfppParser.ExpressionContext>(parse("func main(){ $invalidSource; }").tree) { it.text == invalidSource }
                assertFailsWith<SourceBindingFailure> { binder.expression(syntax, env) }
            }
            assertSame(current, Function.currFunction)
            assertEquals(commands, current.commands.toList())
            assertEquals(vars, current.scope.vars)
            assertEquals(9, sentinel.stackIndex)
            assertEquals(children, current.child)
            assertEquals(parents, current.parent)
            assertEquals(constants, Project.constants)
            assertEquals(macros, Project.macroFunction)
            assertEquals(scoreboards, top.mcfpp.model.scope.GlobalScope.scoreboards)
            assertEquals(tags, top.mcfpp.model.scope.GlobalScope.functionTags.mapValues { it.value.functions.toList() })
            assertEquals(flow, StorageAccess.flowSnapshot(listOf(sentinel)))
            assertEquals(0, Project.errorCount)
        }
    }

    @Test fun indexedReadsKeepTheirEffectsAndOldCatalogFactsSurviveMutableCompilerTypes() {
        val mutableList = MCFPPListType(MCFPPBaseType.Int)
        val local = SourceTypeCatalog.snapshot(listOf(MCFPPBaseType.Int, MCFPPBaseType.Bool, MCFPPPrivateType.Void, mutableList))
        val originalList = local.ref(mutableList)
        val symbol = SourceSymbol("values", originalList, SymbolId(sourceSession, 120), true)
        val index = function("indexCall", emptyList(), local.ref(MCFPPBaseType.Int))
        val env = BindingEnvironment(local, listOf(symbol), listOf(SourceFunction("indexCall", index)))
        mutableList.generic[0] = MCFPPBaseType.Bool
        mutableList.parentType.clear()
        val syntax = find<mcfppParser.ExpressionContext>(parse("func main(){ values[indexCall()]; }").tree) { it.text == "values[indexCall()]" }
        val bound = SnapshotSourceCodeBinder().expression(syntax, env)
        assertTrue(bound.type.sameType(local.ref(MCFPPBaseType.Int)))
        assertEquals(BoundEffect.CALL, bound.effect)
        assertSame(originalList, local.resolve("list"))
        assertFailsWith<IllegalArgumentException> { local.source(originalList) }
        val changed = SourceTypeCatalog.snapshot(listOf(MCFPPBaseType.Int, MCFPPBaseType.Bool, MCFPPPrivateType.Void, mutableList))
        val changedEnv = BindingEnvironment(
            changed,
            listOf(
                SourceSymbol(
                    "values",
                    changed.ref(mutableList),
                    SymbolId(sourceSession, 121)
                )
            ),
            listOf(SourceFunction("indexCall", function("indexCall", emptyList(), changed.ref(MCFPPBaseType.Int))))
        )
        assertTrue(SnapshotSourceCodeBinder().expression(syntax, changedEnv).type.sameType(changed.ref(MCFPPBaseType.Bool)))
    }

    @Test fun environmentRejectsForeignTypesThroughoutDeclarationsCallbacksAndDefaults() {
        val foreign = SourceTypeCatalog.snapshot(listOf(MCFPPBaseType.Int, MCFPPPrivateType.Void))
        val foreignInt = foreign.ref(MCFPPBaseType.Int)
        val foreignSignature = FunctionSignature.of(listOf(ParameterView.of("x", foreignInt, ParamMode.VALUE)), voidType)
        val callbackSignature = FunctionSignature.of(listOf(ParameterView.of("x", intType, ParamMode.VALUE)), voidType)
        val callbackType = catalog.inlineType(callbackSignature)!!
        val default = BoundExpr(
            BoundExprNode.Literal(ConstantValue.Integral(1)),
            foreignInt,
            ConstantValue.Integral(1),
            BoundEffect.PURE,
            SourceSpan.unknown()
        )
        val localParameter = listOf(ParameterView.of("x", intType, ParamMode.VALUE))
        val poisonedConstant = ConstantValue.Sequence(listOf(ConstantValue.Record(mapOf("type" to ConstantValue.TypeValue(foreignInt)))))
        val invalidEnvironments: List<() -> BindingEnvironment> = listOf(
            { environment(listOf(SourceSymbol("foreign", foreignInt, SymbolId(sourceSession, 130)))) },
            { environment(functions = listOf(SourceFunction("foreign", function("foreign", emptyList(), foreignInt)))) },
            {
                environment(
                    functions = listOf(
                        SourceFunction(
                            "foreign",
                            function(
                                "foreign",
                                listOf(ParameterView.of("x", foreignInt, ParamMode.VALUE)),
                                voidType
                            )
                        )
                    )
                )
            },
            { environment(fields = listOf(SourceField(foreignInt, "value", intType, false))) },
            { environment(fields = listOf(SourceField(objectType, "value", foreignInt, false))) },
            {
                environment(
                    functions = listOf(
                        SourceFunction(
                            "foreign",
                            function("foreign", emptyList(), voidType),
                            readonlyParameters = listOf(SourceParameter(foreignInt))
                        )
                    )
                )
            },
            {
                environment(
                    functions = listOf(
                        SourceFunction(
                            "foreign",
                            function(
                                "foreign",
                                listOf(ParameterView.of("body", callbackType, ParamMode.INLINE, foreignSignature)),
                                voidType
                            )
                        )
                    )
                )
            },
            {
                environment(
                    functions = listOf(
                        SourceFunction(
                            "foreign",
                            function("foreign", emptyList(), voidType),
                            readonlyParameters = listOf(SourceParameter(callbackType, ParamMode.INLINE, foreignSignature))
                        )
                    )
                )
            },
            {
                environment(
                    functions = listOf(
                        SourceFunction(
                            "foreign",
                            function("foreign", localParameter, voidType),
                            normalDefaultExpressions = mapOf(0 to default)
                        )
                    )
                )
            },
            {
                environment(
                    functions = listOf(
                        SourceFunction(
                            "foreign",
                            function("foreign", emptyList(), voidType),
                            readonlyDefaultExpressions = mapOf(0 to default),
                            readonlyParameters = listOf(SourceParameter(intType))
                        )
                    )
                )
            },
            { environment(listOf(SourceSymbol("constant", intType, SymbolId(sourceSession, 131), constant = poisonedConstant))) },
            {
                environment(
                    functions = listOf(
                        SourceFunction(
                            "foreign",
                            function("foreign", localParameter, voidType),
                            normalDefaultExpressions = mapOf(
                                0 to BoundExpr(
                                    BoundExprNode.Convert(default, intType),
                                    intType,
                                    null,
                                    BoundEffect.PURE,
                                    SourceSpan.unknown()
                                )
                            )
                        )
                    )
                )
            }
        )
        invalidEnvironments.forEachIndexed { index, construct ->
            val failure = assertFailsWith<IllegalArgumentException>("foreign descriptor graph $index") { construct() }
            assertTrue(failure.message!!.contains("catalog"))
        }
        val forged = ImmutableTypeRef<Any>((intType as ImmutableTypeRef<*>).key, "int")
        assertFailsWith<IllegalArgumentException> {
            environment(listOf(SourceSymbol("forged", forged, SymbolId(sourceSession, 132))))
        }
        assertNull(catalog.conversionRank(intType, forged))
        assertNull(catalog.inlineType(foreignSignature))
    }

    @Test fun functionResultReferenceIsRejectedWithSourceSpanWithoutEvaluatingTheCall() {
        assertRejectedTemporaryReference("make()")
    }

    @Test fun parenthesizedArithmeticReferenceIsRejectedWithSourceSpanWithoutEvaluation() {
        assertRejectedTemporaryReference("(1 + 2)")
    }

    @Test fun referenceCastsToDifferentTypesAreRejectedBeforeAnyConversion() {
        assertRejectedTemporaryReference("health as float")
    }

    @Test fun evenSameTypeReferenceCastsAreRejectedInsteadOfDiscarded() {
        assertRejectedTemporaryReference("health as int")
    }

    @Test fun parenthesesDoNotHideReferenceCastsOnSymbolsOrFields() {
        for (expression in listOf("(health as float)", "((health as int))", "(record.value as int)"))
            assertRejectedTemporaryReference(expression)
    }

    @Test fun parenthesesPreserveActualSymbolFieldAndIndexedLocations() {
        val health = SourceSymbol("health", intType, SymbolId(sourceSession, 140), true)
        val record = SourceSymbol("record", objectType, SymbolId(sourceSession, 141), true)
        val values = SourceSymbol("values", listType, SymbolId(sourceSession, 142), true)
        val mutate = function(
            "mutate",
            listOf(ParameterView.of("destination", intType, ParamMode.REF)),
            voidType,
            FunctionKind.MACRO
        )
        val index = function("indexCall", emptyList(), intType)
        for ((expression, expectedRoot) in listOf(
            "((health))" to health,
            "(record.value)" to record,
            "((values[indexCall()]))" to values
        )) {
            val source = "func main(){ mutate($expression); }"
            val env = environment(
                listOf(health, record, values),
                listOf(
                    SourceFunction("mutate", mutate, BoundEffect.WRITE),
                    SourceFunction("indexCall", index)
                ),
                sourceText = source,
                fields = listOf(SourceField(objectType, "value", intType, true))
            )
            val call = InternalReferenceCalls.bind(find<mcfppParser.FunctionCallContext>(parse(source).tree) { it.namespaceID().text == "mutate" }, env, setOf(0))
            val location = assertIs<BoundCallArgument.Reference>(call.arguments.single()).location
            assertSame(expectedRoot, location.root)
            assertTrue(location.type.sameType(intType))
            assertTrue(location.writable)
            assertEquals(expression, source.substring(location.source.startOffset, location.source.endOffset))
            when (expectedRoot) {
                record -> assertIs<BoundLocationStep.Field>(location.path.single())
                values -> {
                    val step = assertIs<BoundLocationStep.Index>(location.path.single())
                    assertSame(index, assertIs<BoundExprNode.Call>(step.expression.node).sourceCall.target.function)
                    assertEquals(listOf(BoundEffect.CALL, BoundEffect.WRITE), call.effectOrder)
                }
                else -> assertTrue(location.path.isEmpty())
            }
        }
    }

    @Test fun locationSyntaxRejectsOperatorsCallsPropertyWritesAndEmptyIndicesWithoutSkippingSyntax() {
        for (expression in listOf(
            "-health", "!health", "health + 1", "health == 1", "health && true",
            "health op health", "make().value", "health.value()", "record[value = make()]", "health[]", "1"
        ))
            assertRejectedTemporaryReference(expression)
    }

    private fun assertRejectedTemporaryReference(temporary: String) {
        Project.init()
        val current = Function("invalid_source_reference", "fixture.mni", null)
        val sentinel = MCInt(23)
        val mutate = function(
            "mutate",
            listOf(ParameterView.of("destination", intType, ParamMode.REF)),
            voidType,
            FunctionKind.MACRO
        )
        val make = function("make", emptyList(), intType, FunctionKind.NATIVE)
        val source = "func main(){ mutate($temporary); }"
        val env = environment(
            symbols = listOf(
                SourceSymbol("health", intType, SymbolId(sourceSession, 150), true),
                SourceSymbol("record", objectType, SymbolId(sourceSession, 151), true)
            ),
            functions = listOf(
                SourceFunction("mutate", mutate, BoundEffect.WRITE),
                SourceFunction("make", make, BoundEffect.UNKNOWN)
            ),
            sourceText = source,
            fields = listOf(SourceField(objectType, "value", intType, true))
        )
        val syntax = find<mcfppParser.FunctionCallContext>(parse(source).tree) { it.namespaceID().text == "mutate" }
        current.runInFunction {
            current.scope.putVar("sentinel", sentinel)
            StorageAccess.ensure(sentinel)
            current.commands.add(top.mcfpp.command.Command("say reject-temporary-sentinel"))
            val commands = current.commands.toList()
            val vars = current.scope.vars.toMap()
            val children = current.child.toList()
            val parents = current.parent.toList()
            val flow = StorageAccess.flowSnapshot(listOf(sentinel))
            val constants = Project.constants.toMap()
            val resources = Project.macroFunction.toMap()
            val scoreboards = top.mcfpp.model.scope.GlobalScope.scoreboards.toMap()
            val failure = assertFailsWith<SourceBindingFailure> { InternalReferenceCalls.bind(syntax, env, setOf(0)) }
            assertEquals("binding.mcfpp", failure.source.file)
            assertTrue(failure.source.endOffset > failure.source.startOffset)
            assertTrue(temporary.contains(source.substring(failure.source.startOffset, failure.source.endOffset)))
            assertTrue(failure.message!!.contains("location"))
            assertSame(current, Function.currFunction)
            assertEquals(commands, current.commands.toList())
            assertEquals(vars, current.scope.vars)
            assertEquals(children, current.child)
            assertEquals(parents, current.parent)
            assertEquals(flow, StorageAccess.flowSnapshot(listOf(sentinel)))
            assertEquals(constants, Project.constants)
            assertEquals(resources, Project.macroFunction)
            assertEquals(scoreboards, top.mcfpp.model.scope.GlobalScope.scoreboards)
            assertEquals(0, Project.errorCount)
        }
    }

    private fun environment(
        symbols: List<SourceSymbol> = emptyList(),
        functions: List<SourceFunction> = emptyList(),
        sourceText: String? = null,
        fields: List<SourceField> = emptyList()
    ) = BindingEnvironment(catalog, symbols, functions, fields, sourceFile = "binding.mcfpp", sourceText = sourceText)

    private fun function(
        name: String,
        params: List<ParameterView>,
        result: TypeRef<*>,
        kind: FunctionKind = FunctionKind.ORDINARY,
        declaration: String = name
    ): FunctionRef<*> {
        val signature = FunctionSignature.of(params, result)
        return ImmutableFunctionRef(FunctionKey(91, declaration), name, signature, kind)
    }

    private data class Parsed(val tree: mcfppParser.CompilationUnitContext)
    private fun parse(source: String): Parsed {
        val lexer = mcfppLexer(CharStreams.fromString(source))
        val parser = mcfppParser(CommonTokenStream(lexer))
        val tree = parser.compilationUnit()
        check(parser.numberOfSyntaxErrors == 0) { "Test source did not parse: $source" }
        return Parsed(tree)
    }

    private inline fun <reified T : ParseTree> find(root: ParseTree, predicate: (T) -> Boolean = { true }): T = collect<T>(root).first { predicate(it) }

    private inline fun <reified T : ParseTree> collect(root: ParseTree): List<T> {
        return ParseCollector(T::class.java).collect(root)
    }

    private class ParseCollector<T : ParseTree>(private val nodeClass: Class<T>) {
        fun collect(root: ParseTree): List<T> {
            val matches = arrayListOf<T>()
            fun visit(node: ParseTree) {
                if (nodeClass.isInstance(node)) matches += nodeClass.cast(node)
                for (index in 0 until node.childCount) visit(node.getChild(index))
            }
            visit(root)
            return matches
        }
    }
}

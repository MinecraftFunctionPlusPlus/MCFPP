package top.mcfpp.test

import org.antlr.v4.runtime.CharStreams
import org.antlr.v4.runtime.CommonTokenStream
import org.antlr.v4.runtime.tree.ParseTree
import top.mcfpp.Project
import top.mcfpp.analysis.StorageAccess
import top.mcfpp.antlr.mcfppLexer
import top.mcfpp.command.Command
import top.mcfpp.core.lang.MCInt
import top.mcfpp.mni.api.*
import top.mcfpp.mni.expand.*
import top.mcfpp.model.function.Function
import top.mcfpp.model.scope.GlobalScope
import top.mcfpp.type.*
import kotlin.test.*
import kotlin.test.Test
import top.mcfpp.antlr.mcfppParser as P

class MniSourceBodyBindingTest {
    private fun callbackShape(modes: List<ParamMode>, result: MCFPPType = MCFPPBaseType.Int) = SourceCallbackSignature(
        modes.map { SourceCallbackParameter(SourceCallbackType.Declared(MCFPPBaseType.Int.typeId), it) },
        SourceCallbackType.Declared(result.typeId)
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
        callbacks = listOf(
            callbackShape(emptyList()),
            callbackShape(listOf(ParamMode.VALUE)),
            callbackShape(emptyList(), MCFPPPrivateType.Void),
            callbackShape(emptyList(), MCFPPBaseType.Float),
            callbackShape(listOf(ParamMode.CONST), MCFPPPrivateType.Void),
            callbackShape(listOf(ParamMode.REF, ParamMode.CONST), MCFPPPrivateType.Void)
        )
    )
    private val intType get() = catalog.ref(MCFPPBaseType.Int)
    private val voidType get() = catalog.ref(MCFPPPrivateType.Void)
    private val session = SessionId(0)
    private fun signature(modes: List<ParamMode> = emptyList(), result: TypeRef<*> = intType): InlineSignature<*> = FunctionSignature.of(modes.mapIndexed { index, mode -> ParameterView.of("p$index", intType, mode) }, result)
    private fun symbol(
        name: String,
        id: Long,
        writable: Boolean = true,
        type: TypeRef<*> = intType,
        constant: ConstantValue? = null
    ) = SourceSymbol(name, type, SymbolId(session, id), writable, constant)
    private fun function(
        name: String,
        parameters: List<ParameterView>,
        result: TypeRef<*>,
        kind: FunctionKind = FunctionKind.ORDINARY
    ) = ImmutableFunctionRef(FunctionKey(187, name), name, FunctionSignature.of(parameters, result), kind)
    private fun env(
        symbols: List<SourceSymbol> = emptyList(),
        functions: List<SourceFunction> = emptyList(),
        text: String? = null,
        file: String = "caller.mcfpp"
    ) = BindingEnvironment(catalog, symbols, functions, sourceText = text, sourceFile = file)

    private fun lambda(
        text: String,
        signature: InlineSignature<*> = signature(),
        symbols: List<SourceSymbol> = emptyList(),
        functions: List<SourceFunction> = emptyList()
    ): Pair<SourceCallbackSyntax, BindingEnvironment> {
        val source = "\n\nfunc main(){ consume($text); }"
        val parser = P(CommonTokenStream(mcfppLexer(CharStreams.fromString(source))))
        val tree = parser.compilationUnit()
        assertEquals(0, parser.numberOfSyntaxErrors)
        val syntax = collect<P.InlineLambdaContext>(tree).first()
        val environment = env(symbols, functions, source)
        return SnapshotSourceCodeBinder().callback(syntax, signature, environment) to environment
    }
    private fun bind(
        text: String,
        signature: InlineSignature<*> = signature(),
        symbols: List<SourceSymbol> = emptyList(),
        functions: List<SourceFunction> = emptyList()
    ): BoundCallback {
        val (syntax, environment) = lambda(text, signature, symbols, functions)
        return SnapshotSourceBodyBinder().bind(syntax, environment)
    }
    private fun bodySpan(text: String, file: String = "declaration.mcfpp") = SourceSpan(file, 21, 21 + text.length, 4, 3, 4, 3 + text.length)
    private fun staticSyntax(target: SourceFunction, symbols: List<SourceSymbol> = emptyList()): Pair<SourceCallbackSyntax, BindingEnvironment> {
        val expected = FunctionSignature.of(target.function.parameters(), target.function.returnType())
        val callbackType = assertNotNull(catalog.inlineType(expected))
        val consume = SourceFunction(
            "consume",
            function(
                "consume",
                listOf(
                    ParameterView.of("callback", callbackType, ParamMode.INLINE, expected)
                ),
                voidType,
                FunctionKind.MACRO
            )
        )
        val source = "func main(){consume(${target.name});}"
        val environment = env(symbols, listOf(target, consume), source)
        val parser = P(CommonTokenStream(mcfppLexer(CharStreams.fromString(source))))
        val tree = parser.compilationUnit()
        assertEquals(0, parser.numberOfSyntaxErrors)
        val call = SnapshotSourceCodeBinder().call(collect<P.FunctionCallContext>(tree).first(), environment)
        return assertIs<BoundCallArgument.Callback>(call.arguments.single()).callback to environment
    }

    @Test fun lambdaCapturesReadonlyValuesAndOwnsItsParametersLocalsAndReturnRegion() {
        val captured = symbol("captured", 10)
        assertFailsWith<SourceBindingFailure> {
            bind("{ i ->captured+=i; return captured;}", signature(listOf(ParamMode.VALUE)), listOf(captured))
        }
        val result = bind("{ i ->var local:int=i; local+=captured; return local;}", signature(listOf(ParamMode.VALUE)), listOf(captured))
        assertEquals(captured.symbol, result.captures.getValue(captured.symbol).symbol)
        assertFalse(result.captures.getValue(captured.symbol).writable)
        val local = assertIs<BoundSourceStatement.Local>(result.body.statements[0])
        val read = assertIs<BoundExprNode.Read>(local.initialValue!!.node)
        assertEquals(result.parameters.single().symbol.symbol, read.location.root.symbol)
        assertNotEquals(local.symbol.symbol, captured.symbol)
        assertNotEquals(local.symbol.symbol, result.parameters.single().symbol.symbol)
        val assignment = assertIs<BoundSourceStatement.Assign>(result.body.statements[1])
        assertEquals(local.symbol.symbol, assignment.location.root.symbol)
        assertEquals(BinaryOperator.ADD, assignment.operator)
        val returned = assertIs<BoundSourceStatement.Return>(result.body.statements[2])
        assertEquals(result.returnRegion, returned.region)
        assertEquals("caller.mcfpp", returned.source.file)
        assertTrue(returned.source.startLine >= 3)
        assertTrue(result.bodySource.startOffset > result.source.startOffset)
    }

    @Test fun nestedBlocksShadowByNewIdentityAndRestoreTheOuterLexicalBinding() {
        val captured = symbol("captured", 12)
        val result = bind(
            "{ i ->if(true){var captured:int=i; captured=3;}; return captured;}",
            signature(listOf(ParamMode.VALUE)),
            listOf(captured)
        )
        val branch = assertIs<BoundSourceStatement.Conditional>(result.body.statements[0])
        val local = assertIs<BoundSourceStatement.Local>(branch.whenTrue.statements[0]).symbol
        assertNotEquals(captured.symbol, local.symbol)
        assertEquals(local.symbol, assertIs<BoundSourceStatement.Assign>(branch.whenTrue.statements[1]).location.root.symbol)
        val outerRead = assertIs<BoundExprNode.Read>(assertIs<BoundSourceStatement.Return>(result.body.statements[1]).value!!.node)
        assertEquals(captured.symbol, outerRead.location.root.symbol)
        assertFalse(outerRead.location.writable)
        assertEquals(result.body.scope, branch.whenTrue.parent)
        assertEquals(setOf(captured.symbol), result.captures.keys)
    }

    @Test fun whileDoAndConditionalReturnsKeepTheirOwnRegionIdentities() {
        val gate = symbol("gate", 13, type = catalog.ref(MCFPPBaseType.Bool))
        val result = bind("{while(gate){if(gate){continue;}; break;}; do {return 2;} while(false); return 3;}", symbols = listOf(gate))
        val loop = assertIs<BoundSourceStatement.Loop>(result.body.statements[0])
        val branch = assertIs<BoundSourceStatement.Conditional>(loop.body.statements[0])
        assertEquals(loop.region, assertIs<BoundSourceStatement.Continue>(branch.whenTrue.statements.single()).region)
        assertEquals(loop.region, assertIs<BoundSourceStatement.Break>(loop.body.statements[1]).region)
        val doLoop = assertIs<BoundSourceStatement.Loop>(result.body.statements[1])
        assertTrue(doLoop.testAfter)
        assertFalse(loop.testAfter)
        assertNotEquals(loop.region, doLoop.region)
        assertEquals(result.returnRegion, assertIs<BoundSourceStatement.Return>(doLoop.body.statements.single()).region)
    }

    @Test fun breakAndContinueCannotCrossTheCallbackBoundaryEvenInsideACallersLoop() {
        for (control in listOf("break", "continue")) {
            assertFailsWith<SourceBindingFailure> { bind("{$control;}", signature(result = voidType)) }
            val callback = signature(result = voidType)
            val run = SourceFunction(
                "run",
                function(
                    "run",
                    listOf(
                        ParameterView.of(
                            "body",
                            catalog.inlineType(callback)!!,
                            ParamMode.INLINE,
                            callback
                        )
                    ),
                    voidType,
                    FunctionKind.MACRO
                )
            )
            val outer = bind("{while(true){run({$control;});}}", callback, functions = listOf(run))
            assertFailsWith<SourceBindingFailure> { outer.nestedCallbacks.single().bind() }
        }
    }

    @Test fun repeatedBindingRetainsStaticLambdaIdentityButAllocatesIndependentLocalAndReturnIdentities() {
        val captured = symbol("captured", 14)
        val (syntax, environment) = lambda("{var local:int=captured; return local;}", symbols = listOf(captured))
        val binder = SnapshotSourceBodyBinder()
        val first = binder.bind(syntax, environment)
        val second = binder.bind(syntax, environment)
        assertEquals(first.identity, second.identity)
        assertNotEquals(first.returnRegion, second.returnRegion)
        assertNotEquals(first.body.scope, second.body.scope)
        assertNotEquals(
            assertIs<BoundSourceStatement.Local>(first.body.statements[0]).symbol.symbol,
            assertIs<BoundSourceStatement.Local>(second.body.statements[0]).symbol.symbol
        )
        assertEquals(captured.symbol, second.captures.getValue(captured.symbol).symbol)
    }

    @Test fun staticFunctionFreeNamesUseTheOriginalDeclarationEnvironmentDespiteCallerShadowing() {
        val original = symbol("captured", 21).copy(global = true)
        val shadow = symbol("captured", 22)
        val formal = symbol("value", 23)
        val body = "{return captured+value;}"
        val target = SourceFunction(
            "sourceFn",
            function("sourceFn", listOf(ParameterView.of("value", intType, ParamMode.VALUE)), intType),
            sourceBody = SourceFunctionBody(body, bodySpan(body)),
            normalSymbols = listOf(formal)
        )
        val (syntax, caller) = staticSyntax(target, listOf(shadow))
        val declaration = env(listOf(original), listOf(target), " ".repeat(21) + body, "declaration.mcfpp")
        val result = SnapshotSourceBodyBinder().bind(syntax, caller, mapOf(target.declarationIdentity to declaration))
        assertSame(target, result.functionTarget)
        assertSame(original, result.captures.getValue(original.symbol))
        assertFalse(shadow.symbol in result.captures)
        assertEquals(formal.symbol, result.parameters.single().symbol.symbol)
        val returned = assertIs<BoundSourceStatement.Return>(result.body.statements.single())
        assertEquals("declaration.mcfpp", returned.source.file)
        assertEquals(4, returned.source.startLine)
        assertEquals(22, returned.source.startOffset)
        assertNotEquals(result.source.file, result.bodySource.file)
    }

    @Test fun staticBodiesRequireTheirDeclarationEnvironmentAndOriginalFormalIdentities() {
        val text = "{return value;}"
        val target = SourceFunction(
            "sourceFn",
            function("sourceFn", listOf(ParameterView.of("value", intType, ParamMode.VALUE)), intType),
            sourceBody = SourceFunctionBody(text, bodySpan(text))
        )
        val (syntax, caller) = staticSyntax(target)
        assertFailsWith<SourceBindingFailure> { SnapshotSourceBodyBinder().bind(syntax, caller) }
        assertFailsWith<SourceBindingFailure> {
            SnapshotSourceBodyBinder().bind(syntax, caller, mapOf(target.declarationIdentity to env(functions = listOf(target), file = "declaration.mcfpp")))
        }
        assertFailsWith<SourceBindingFailure> {
            SnapshotSourceBodyBinder().bind(syntax, caller, mapOf(target.declarationIdentity to env()))
        }
    }

    @Test fun nativeDynamicAndInvisibleTargetsCannotPretendToBeStaticSourceCallbacks() {
        val text = "{return 1;}"
        for (kind in listOf(FunctionKind.NATIVE, FunctionKind.MACRO, FunctionKind.HELPER)) {
            val target = SourceFunction(
                "notSource",
                function("notSource", emptyList(), intType, kind),
                sourceBody = SourceFunctionBody(text, bodySpan(text))
            )
            val descriptor = SourceCallbackSyntax(
                emptyList(),
                signature(),
                catalog.inlineType(signature())!!,
                "notSource",
                SourceSpan("caller.mcfpp", 0, 9, 1, 1, 1, 10),
                target
            )
            val environment = env(functions = listOf(target))
            assertFailsWith<SourceBindingFailure> {
                SnapshotSourceBodyBinder().bind(descriptor, environment, mapOf(target.declarationIdentity to environment))
            }
        }
        val target = SourceFunction("ordinary", function("ordinary", emptyList(), intType))
        val syntax = SourceCallbackSyntax(
            emptyList(),
            signature(),
            catalog.inlineType(signature())!!,
            "ordinary",
            SourceSpan("caller.mcfpp", 0, 8, 1, 1, 1, 9),
            target
        )
        assertFailsWith<SourceBindingFailure> { SnapshotSourceBodyBinder().bind(syntax, env(functions = listOf(target))) }
        assertFailsWith<SourceBindingFailure> { SnapshotSourceBodyBinder().bind(syntax, env()) }
    }

    @Test fun refParametersKeepWritableIdentityAndConstParametersRejectMutation() {
        val descriptor = signature(listOf(ParamMode.REF, ParamMode.CONST), voidType)
        val result = bind("{ place, fixed ->place=fixed;}", descriptor)
        val assignment = assertIs<BoundSourceStatement.Assign>(result.body.statements.single())
        assertEquals(result.parameters[0].symbol.symbol, assignment.location.root.symbol)
        assertTrue(result.parameters[0].symbol.writable)
        assertFalse(result.parameters[1].symbol.writable)
        assertEquals(listOf(ParamMode.REF, ParamMode.CONST), result.parameters.map { it.mode })
        assertFailsWith<SourceBindingFailure> { bind("{ place, fixed ->fixed=place;}", descriptor) }
    }

    @Test fun staticReadonlyBindingsRemainClosedAndKeepTheirOriginalSymbolIdentity() {
        val readonly = symbol("count", 30, false)
        val text = "{return count;}"
        val function = ImmutableFunctionRef(
            FunctionKey(187, "withReadonly"),
            "withReadonly",
            FunctionSignature.of(emptyList(), intType),
            FunctionKind.ORDINARY,
            readonlyParameters = listOf(ParameterView.of("count", intType, ParamMode.CONST))
        )
        val target = SourceFunction(
            "withReadonly",
            function,
            readonlyParameters = listOf(SourceParameter(intType, ParamMode.CONST)),
            sourceBody = SourceFunctionBody(text, bodySpan(text)),
            readonlySymbols = listOf(readonly),
            preparedReadonly = SourcePreparedReadonly(
                function.key,
                listOf(TypedConstant(intType, ConstantValue.Integral(7), SourceSpan.unknown())),
                SourceSpan.unknown()
            )
        )
        val (syntax, caller) = staticSyntax(target)
        val declaration = env(functions = listOf(target), file = "declaration.mcfpp")
        val result = SnapshotSourceBodyBinder().bind(syntax, caller, mapOf(target.declarationIdentity to declaration))
        assertEquals(readonly.symbol, result.readonlyParameters.single().symbol)
        assertFalse(result.readonlyParameters.single().writable)
        assertEquals(ConstantValue.Integral(7), assertIs<BoundSourceStatement.Return>(result.body.statements.single()).value!!.constant)
        assertTrue(result.captures.isEmpty())
    }

    @Test fun staticReadonlyIntegerActualIsConvertedToTheFloatFormalBeforeItsBodyIsBound() {
        val floatType = catalog.ref(MCFPPBaseType.Float)
        val readonly = symbol("count", 45, false, floatType)
        val text = "{return count;}"
        val function = ImmutableFunctionRef(
            FunctionKey(187, "widenedReadonly"),
            "widenedReadonly",
            FunctionSignature.of(emptyList(), floatType),
            FunctionKind.ORDINARY,
            readonlyParameters = listOf(ParameterView.of("count", floatType, ParamMode.CONST))
        )
        val target = SourceFunction(
            "widenedReadonly",
            function,
            readonlyParameters = listOf(SourceParameter(floatType, ParamMode.CONST)),
            sourceBody = SourceFunctionBody(text, bodySpan(text)),
            readonlySymbols = listOf(readonly),
            preparedReadonly = SourcePreparedReadonly(
                function.key,
                listOf(TypedConstant(intType, ConstantValue.Integral(7), SourceSpan.unknown())),
                SourceSpan.unknown()
            )
        )
        val (syntax, caller) = staticSyntax(target)
        val declaration = env(functions = listOf(target), file = "declaration.mcfpp")
        val result = SnapshotSourceBodyBinder().bind(syntax, caller, mapOf(target.declarationIdentity to declaration))
        val formal = result.readonlyParameters.single()
        assertEquals(readonly.symbol, formal.symbol)
        assertFalse(formal.writable)
        assertTrue(formal.type.sameType(floatType))
        assertEquals(ConstantValue.FloatBits(7f.toRawBits()), formal.constant)
        val returned = assertIs<BoundSourceStatement.Return>(result.body.statements.single()).value!!
        assertTrue(returned.type.sameType(floatType))
        assertIs<BoundExprNode.Read>(returned.node)
        assertEquals(ConstantValue.FloatBits(7f.toRawBits()), returned.constant)
        assertEquals(ConstantValue.Integral(7), target.preparedReadonly!!.arguments.single().value)
        assertTrue(result.captures.isEmpty())
    }

    @Test fun closedConstLocalsCanFeedConstMacroArgumentsWithoutExecutingTheMacro() {
        val sink = SourceFunction("sink", function("sink", listOf(ParameterView.of("value", intType, ParamMode.CONST)), voidType, FunctionKind.MACRO))
        val result = bind("{const saved:int=3; sink(saved);}", signature(result = voidType), functions = listOf(sink))
        val local = assertIs<BoundSourceStatement.Local>(result.body.statements[0])
        assertTrue(local.constant)
        assertFalse(local.symbol.writable)
        val call = assertIs<BoundExprNode.Call>(assertIs<BoundSourceStatement.Evaluate>(result.body.statements[1]).expression.node).sourceCall
        assertEquals(ParamMode.CONST, assertIs<BoundCallArgument.Value>(call.arguments.single()).mode)
        assertSame(sink, call.target)
        assertFailsWith<SourceBindingFailure> {
            bind("{const unknown:int=runtime;}", signature(result = voidType), listOf(symbol("runtime", 31)))
        }
    }

    @Test fun writableGlobalLocationsKeepTheCapturedBaseAndDynamicIndexExpressions() {
        val values = symbol("values", 32, type = catalog.ref(MCFPPListType(MCFPPBaseType.Int)))
        val index = symbol("index", 33)
        val mutate = SourceFunction("mutate", function("mutate", listOf(ParameterView.of("place", intType, ParamMode.REF)), voidType, FunctionKind.MACRO))
        assertFailsWith<SourceBindingFailure> {
            bind("{values[index] = 1;}", signature(result = voidType), listOf(values, index), listOf(mutate))
        }
        val globalValues = values.copy(global = true)
        val result = bind("{values[index] = 1;}", signature(result = voidType), listOf(globalValues, index), listOf(mutate))
        val location = assertIs<BoundSourceStatement.Assign>(result.body.statements.single()).location
        assertSame(globalValues, location.root)
        val selected = assertIs<BoundLocationStep.Index>(location.path.single())
        assertEquals(index.symbol, assertIs<BoundExprNode.Read>(selected.expression.node).location.root.symbol)
        assertEquals(setOf(values.symbol, index.symbol), result.captures.keys)
    }

    @Test fun explicitlyBoundConstParametersCanForwardToConstMacrosAndInitializeConstLocals() {
        val descriptor = signature(listOf(ParamMode.CONST), voidType)
        val sink = SourceFunction("sink", function("sink", listOf(ParameterView.of("value", intType, ParamMode.CONST)), voidType, FunctionKind.MACRO))
        val (syntax, environment) = lambda("{ fixed ->sink(fixed); const saved:int=fixed;}", descriptor, functions = listOf(sink))
        assertFailsWith<SourceBindingFailure> { SnapshotSourceBodyBinder().bind(syntax, environment) }
        val constants = linkedMapOf(0 to TypedConstant(intType, ConstantValue.Integral(9), syntax.source))
        val result = SnapshotSourceBodyBinder().bind(syntax, environment, constParameters = constants)
        constants.clear()
        assertEquals(ConstantValue.Integral(9), result.parameters.single().symbol.constant)
        assertFalse(result.parameters.single().symbol.writable)
        val call = assertIs<BoundExprNode.Call>(assertIs<BoundSourceStatement.Evaluate>(result.body.statements[0]).expression.node).sourceCall
        val argument = assertIs<BoundCallArgument.Value>(call.arguments.single())
        assertEquals(ParamMode.CONST, argument.mode)
        assertEquals(ConstantValue.Integral(9), argument.expression.constant)
        val local = assertIs<BoundSourceStatement.Local>(result.body.statements[1])
        assertEquals(ConstantValue.Integral(9), local.symbol.constant)
    }

    @Test fun callbackConstEvidenceRejectsWrongPositionsDomainsTypesAndPayloads() {
        val descriptor = signature(listOf(ParamMode.REF, ParamMode.CONST), voidType)
        val (syntax, environment) = lambda("{ place, fixed ->place=fixed;}", descriptor)
        val binder = SnapshotSourceBodyBinder()
        fun rejected(index: Int, type: TypeRef<*>, value: ConstantValue) {
            assertFailsWith<SourceBindingFailure> {
                binder.bind(syntax, environment, constParameters = mapOf(index to TypedConstant(type, value, syntax.source)))
            }
        }
        rejected(0, intType, ConstantValue.Integral(1))
        rejected(2, intType, ConstantValue.Integral(1))
        rejected(1, catalog.ref(MCFPPBaseType.Float), ConstantValue.FloatBits(1f.toRawBits()))
        rejected(1, SourceTypeCatalog.snapshot(listOf(MCFPPBaseType.Int)).ref(MCFPPBaseType.Int), ConstantValue.Integral(1))
        rejected(1, intType, ConstantValue.Bool(true))
        rejected(1, intType, ConstantValue.Integral(Long.MAX_VALUE))
    }

    @Test fun localAndReturnConversionsUseTheCanonicalTypeDomainAndPreserveSourceSites() {
        val result = bind("{var widened:float=1; return widened;}", signature(result = catalog.ref(MCFPPBaseType.Float)))
        val local = assertIs<BoundSourceStatement.Local>(result.body.statements[0])
        assertIs<BoundExprNode.Convert>(local.initialValue!!.node)
        assertTrue(local.symbol.type.sameType(catalog.ref(MCFPPBaseType.Float)))
        assertFailsWith<SourceBindingFailure> { bind("{return true;}") }
        assertFailsWith<SourceBindingFailure> { bind("{return;}") }
        assertFailsWith<SourceBindingFailure> { bind("{return 1;}", signature(result = voidType)) }
        assertFailsWith<SourceBindingFailure> { bind("{if(1){return 2;}; return 3;}") }
    }

    @Test fun malformedAndUnrepresentedStatementsFailWithTheOriginalSourceLocation() {
        val (syntax, environment) = lambda("{return 1;}")
        val wrongNames = syntax.copy(parameterNames = listOf("invented"))
        assertFailsWith<SourceBindingFailure> { SnapshotSourceBodyBinder().bind(wrongNames, environment) }
        val bad = syntax.copy(sourceText = syntax.sourceText.replace("return 1", "return ?"))
        val failure = assertFailsWith<SourceBindingFailure> { SnapshotSourceBodyBinder().bind(bad, environment) }
        assertEquals("caller.mcfpp", failure.source.file)
        assertTrue(failure.source.startLine >= 3)
        assertFailsWith<SourceBindingFailure> { bind("{return unknown;}") }
        assertFailsWith<SourceBindingFailure> { bind("{var twice:int=1; var twice:int=2; return twice;}") }
        assertFailsWith<SourceBindingFailure> { bind("{for(i:[1]){return 1;}; return 2;}") }
    }

    @Test fun boundTemplateContainersAreImmutable() {
        val captured = symbol("captured", 37)
        val result = bind("{return captured;}", symbols = listOf(captured))
        assertFailsWith<UnsupportedOperationException> { (result.body.statements as MutableList<BoundSourceStatement>).clear() }
        assertFailsWith<UnsupportedOperationException> { (result.captures as MutableMap<SymbolId, SourceSymbol>).clear() }
        assertFailsWith<UnsupportedOperationException> { (result.parameters as MutableList<BoundCallbackParameter>).clear() }
        assertFailsWith<UnsupportedOperationException> { (result.nestedCallbacks as MutableList<SourceCallbackBodyRequest>).clear() }
    }

    @Test fun nestedCallbacksCaptureTheEnclosingLocalButUseIndependentReturnAndLoopRegions() {
        val descriptor = signature()
        val run = SourceFunction(
            "run",
            function(
                "run",
                listOf(
                    ParameterView.of(
                        "body",
                        catalog.inlineType(descriptor)!!,
                        ParamMode.INLINE,
                        descriptor
                    )
                ),
                voidType,
                FunctionKind.MACRO
            )
        )
        val result = bind("{var local:int=3; run({return local;}); return 0;}", functions = listOf(run))
        val local = assertIs<BoundSourceStatement.Local>(result.body.statements[0]).symbol
        val nested = result.nestedCallbacks.single().bind()
        assertEquals(local.symbol, nested.captures.getValue(local.symbol).symbol)
        assertFalse(nested.captures.getValue(local.symbol).writable)
        assertTrue(result.captures.isEmpty())
        assertNotEquals(result.returnRegion, nested.returnRegion)
        assertEquals(nested.returnRegion, assertIs<BoundSourceStatement.Return>(nested.body.statements.single()).region)
        assertTrue(nested.source.startOffset > result.source.startOffset)
    }

    @Test fun elseIfScopesKeepTheCorrectLexicalParentForEveryBranch() {
        val result = bind("{if(false){return 1;} else if(true){var sibling:int=2; return sibling;} else {return 3;}}")
        val conditional = assertIs<BoundSourceStatement.Conditional>(result.body.statements.single())
        val otherwise = assertNotNull(conditional.whenFalse)
        val nested = assertIs<BoundSourceStatement.Conditional>(otherwise.statements.single())
        assertEquals(result.body.scope, otherwise.parent)
        assertEquals(otherwise.scope, nested.whenTrue.parent)
        assertEquals(otherwise.scope, assertNotNull(nested.whenFalse).parent)
    }

    @Test fun nestedConstCallbacksWaitForActualInlineFactsAndRetainTheOriginalLexicalCapture() {
        val descriptor = signature(listOf(ParamMode.CONST), voidType)
        val outer = SourceFunction(
            "outer",
            function(
                "outer",
                listOf(
                    ParameterView.of(
                        "body",
                        catalog.inlineType(descriptor)!!,
                        ParamMode.INLINE,
                        descriptor
                    )
                ),
                voidType,
                FunctionKind.MACRO
            )
        )
        val sink = SourceFunction("sink", function("sink", listOf(ParameterView.of("value", intType, ParamMode.CONST)), voidType, FunctionKind.MACRO))
        val captured = symbol("captured", 44)
        val result = bind(
            "{outer({ fixed ->sink(fixed); const saved:int=fixed; captured + saved;});}",
            signature(result = voidType),
            listOf(captured),
            listOf(outer, sink)
        )
        val request = result.nestedCallbacks.single()
        assertEquals(captured.symbol, request.environment.symbol("captured")!!.symbol)
        assertFailsWith<SourceBindingFailure> { request.bind() }
        val nested = request.bind(mapOf(0 to TypedConstant(intType, ConstantValue.Integral(11), request.syntax.source)))
        assertEquals(request.identity, nested.identity)
        assertEquals(ConstantValue.Integral(11), nested.parameters.single().symbol.constant)
        assertEquals(captured.symbol, nested.captures.getValue(captured.symbol).symbol)
        assertEquals(ConstantValue.Integral(11), assertIs<BoundSourceStatement.Local>(nested.body.statements[1]).symbol.constant)
        assertFailsWith<UnsupportedOperationException> {
            (request.environment.symbols as MutableMap<String, SourceSymbol>).clear()
        }
    }

    @Test fun pureBodyBindingDoesNotImposeTheDefaultExpansionDepthAsAHardSemanticLimit() {
        val descriptor = signature(result = voidType)
        val run = SourceFunction(
            "run",
            function(
                "run",
                listOf(
                    ParameterView.of(
                        "body",
                        catalog.inlineType(descriptor)!!,
                        ParamMode.INLINE,
                        descriptor
                    )
                ),
                voidType,
                FunctionKind.MACRO
            )
        )
        var source = "{}"
        repeat(65) { source = "{run($source);}" }
        var result = bind(source, descriptor, functions = listOf(run))
        var depth = 0
        while (result.nestedCallbacks.isNotEmpty()) {
            result = result.nestedCallbacks.single().bind()
            depth++
        }
        assertEquals(65, depth)
    }

    @Test fun bindingRealStatementsLeavesCompilerCommandsStorageScopesCallgraphAndAmbientContextUntouched() {
        val before = CompilerStateJournal.checkpoint()
        try {
            Project.init()
            val current = Function("source_body_purity", "fixture.source", null)
            current.runInFunction {
                val runtime = MCInt(9, "captured")
                current.scope.putVar("captured", runtime)
                StorageAccess.ensure(runtime)
                current.commands.add(Command("say sentinel"))
                val captured = symbol("captured", 40)
                val native = SourceFunction("host", function("host", emptyList(), intType, FunctionKind.NATIVE))
                val commands = current.commands.toList()
                val variables = current.scope.allVars.toList()
                val children = current.child.toList()
                val namespaces = GlobalScope.localNamespaces.toMap()
                val flow = StorageAccess.flowSnapshot(listOf(runtime))
                val errors = Project.errorCount
                val result = bind(
                    "{var local:int=host(); local=captured; while(false){local=2;}; return local;}",
                    symbols = listOf(captured),
                    functions = listOf(native)
                )
                assertIs<BoundExprNode.Call>(assertIs<BoundSourceStatement.Local>(result.body.statements[0]).initialValue!!.node)
                assertSame(current, Function.currFunction)
                assertEquals(commands, current.commands.toList())
                assertEquals(variables, current.scope.allVars.toList())
                assertEquals(children, current.child.toList())
                assertEquals(namespaces, GlobalScope.localNamespaces.toMap())
                assertEquals(flow, StorageAccess.flowSnapshot(listOf(runtime)))
                assertEquals(errors, Project.errorCount)
            }
        } finally {
            before.restore()
        }
    }

    private inline fun <reified T : ParseTree> collect(root: ParseTree): List<T> = descendants(root).filterIsInstance<T>()

    private fun descendants(root: ParseTree): List<ParseTree> {
        val result = arrayListOf<ParseTree>()
        fun visit(node: ParseTree) {
            result.add(node)
            for (index in 0 until node.childCount) visit(node.getChild(index))
        }
        visit(root)
        return result
    }
}

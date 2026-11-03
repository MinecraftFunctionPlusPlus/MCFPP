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
import top.mcfpp.type.*
import kotlin.test.*
import kotlin.test.Test
import top.mcfpp.antlr.mcfppParser as P

class MniSourceDefaultBindingTest {
    private val typeList = MCFPPListType(MCFPPConcreteType.Type)
    private val domain = listOf(
        MCFPPBaseType.Int,
        MCFPPBaseType.Float,
        MCFPPBaseType.Bool,
        MCFPPBaseType.String,
        MCFPPBaseType.Object,
        MCFPPConcreteType.Type,
        MCFPPPrivateType.Void,
        typeList
    )
    private fun snapshot() = SourceTypeCatalog.snapshot(
        domain,
        emptyList(),
        emptyList(),
        mapOf(MCFPPBaseType.Object.typeId to SourceConstantSchema.Record(mapOf("types" to typeList.typeId)))
    )
    private val catalog = snapshot()
    private val intType get() = catalog.ref(MCFPPBaseType.Int)
    private val floatType get() = catalog.ref(MCFPPBaseType.Float)
    private val span = SourceSpan.unknown()
    private val binder = SourceDefaultBinder()

    @Test fun templatesResolveDeclarationFormalsAndFreeBindingsBeforeTheCallerExists() {
        val a = symbol("a", 1)
        val b = symbol("b", 2)
        val x = symbol("x", 3)
        val y = symbol("y", 4)
        val declaredValue = symbol("declaredValue", 5)
        val source = "func main(){ var first = a + 1; var second = x + b + declaredValue; }"
        val tree = parse(source)
        val declaration = binder.bind(
            declaration("target", listOf(a, b), listOf(x, y)),
            environment(listOf(symbol("a", 90, constant = ConstantValue.Integral(99)), declaredValue), source = source),
            readonlyDefaults = mapOf(1 to expression(tree, "a+1")),
            normalDefaults = mapOf(1 to expression(tree, "x+b+declaredValue"))
        )
        val readonly = assertIs<BoundExprNode.Binary>(declaration.readonlyDefaultExpressions.getValue(1).node)
        assertEquals(a.symbol, read(readonly.left).symbol)
        val normal = assertIs<BoundExprNode.Binary>(declaration.normalDefaultExpressions.getValue(1).node)
        val prefix = assertIs<BoundExprNode.Binary>(normal.left.node)
        assertEquals(
            listOf(x.symbol, b.symbol, declaredValue.symbol),
            listOf(
                read(prefix.left).symbol,
                read(prefix.right).symbol,
                read(normal.right).symbol
            )
        )
        assertNull(declaration.readonlyDefaultExpressions.getValue(1).constant)
        val caller = environment(listOf(symbol("a", 91), symbol("x", 92), symbol("declaredValue", 93)), listOf(declaration))
        val call = SnapshotSourceCodeBinder().call(call("target<3>(8)"), caller)
        assertSame(declaration, call.target)
        assertEquals(declaredValue.symbol, read(assertIs<BoundExprNode.Binary>(call.target.normalDefaultExpressions.getValue(1).node).right).symbol)
        assertEquals("defaults.mcfpp", declaration.normalDefaultExpressions.getValue(1).source.file)
    }

    @Test fun readonlyDefaultsCloseInFormalOrderWithTheSameInt32ArithmeticAsSourceBinding() {
        val a = symbol("a", 11)
        val b = symbol("b", 12)
        val c = symbol("c", 13)
        val source = "func main(){ var b = a + 1; var c = b * 2; }"
        val tree = parse(source)
        val declaration = binder.bind(
            declaration("chain", listOf(a, b, c)),
            environment(source = source),
            mapOf(1 to expression(tree, "a+1"), 2 to expression(tree, "b*2"))
        )
        val prepared = binder.prepareReadonly(declaration, listOf(literal(Int.MAX_VALUE.toLong())), catalog, span)
        assertEquals(
            listOf(Int.MAX_VALUE.toLong(), Int.MIN_VALUE.toLong(), 0L),
            prepared.arguments.map {
                assertIs<ConstantValue.Integral>(it.value).value
            }
        )
        assertEquals(declaration.declarationIdentity, prepared.declaration)
        assertSame(intType, prepared.arguments[1].type)
        assertEquals(declaration.readonlyDefaultExpressions.getValue(1).source, prepared.arguments[1].source)
    }

    @Test fun readonlyNumericConversionIsAClosedTypedProof() {
        val a = symbol("a", 21, floatType)
        val declaration = declaration("floating", listOf(a))
        val complete = binder.completeReadonly(declaration, listOf(literal(7)), catalog, span).single()
        assertSame(floatType, complete.type)
        assertEquals(ConstantValue.FloatBits(7.0f.toRawBits()), complete.constant)
        assertEquals(BoundEffect.PURE, complete.effect)
    }

    @Test fun readonlyFloatNegationUsesThePrecedingValueAndPreservesFloatBits() = withFloatTarget {
        val a = symbol("a", 101, floatType)
        val b = symbol("b", 102, floatType)
        val source = "func main(){ var result = -a; }"
        val declaration = binder.bind(
            declaration("negate", listOf(a, b)),
            environment(source = source),
            readonlyDefaults = mapOf(1 to expression(parse(source), "-a"))
        )
        for (value in listOf(1.25f, 0.0f)) {
            val input = ConstantValue.FloatBits(value.toRawBits())
            val explicit = BoundExpr(BoundExprNode.Literal(input), floatType, input, BoundEffect.PURE, span)
            val result = binder.completeReadonly(declaration, listOf(explicit), catalog, span)[1]
            assertSame(floatType, result.type)
            assertEquals(ConstantValue.FloatBits((-value).toRawBits()), result.constant)
            assertEquals(BoundEffect.PURE, result.effect)
        }
        val direct = "func main(){ var result = -1.25f; }"
        assertEquals(
            ConstantValue.FloatBits((-1.25f).toRawBits()),
            SnapshotSourceCodeBinder().expression(
                expression(parse(direct), "-1.25f"),
                environment(source = direct)
            ).constant
        )
    }

    @Test fun readonlyMixedNumericComparisonsPromoteOperandsEvenWhenTheResultIsBool() = withFloatTarget {
        val a = symbol("a", 103)
        val boolType = catalog.ref(MCFPPBaseType.Bool)
        val b = symbol("b", 104, boolType)
        for ((comparison, expected) in listOf(
            "a < 1.5f" to true,
            "1.5f > a" to true,
            "a == 1.0f" to true,
            "a != 1.0f" to false,
            "a >= 1.5f" to false
        )) {
            val source = "func main(){ var result = $comparison; }"
            val declaration = binder.bind(
                declaration("compare", listOf(a, b)),
                environment(source = source),
                readonlyDefaults = mapOf(1 to expression(parse(source), comparison.replace(" ", "")))
            )
            val result = binder.completeReadonly(declaration, listOf(literal(1)), catalog, span)[1]
            assertSame(boolType, result.type)
            assertEquals(ConstantValue.Bool(expected), result.constant, comparison)
            assertEquals(BoundEffect.PURE, result.effect)
        }
        val direct = "func main(){ var result = 1 < 1.5f; }"
        assertEquals(
            ConstantValue.Bool(true),
            SnapshotSourceCodeBinder().expression(
                expression(parse(direct), "1<1.5f"),
                environment(source = direct)
            ).constant
        )
    }

    @Test fun readonlyDefaultsRejectSelfLaterNormalAndUnprovenFreeSymbolsDespiteNamesakes() {
        val a = symbol("a", 31)
        val b = symbol("b", 32)
        val x = symbol("x", 33)
        for (syntax in listOf("a", "b", "x", "free")) {
            val source = "func main(){ var result = $syntax; }"
            val failure = assertFailsWith<SourceBindingFailure> {
                binder.bind(
                    declaration("bad", listOf(a, b), listOf(x)),
                    environment(
                        listOf(
                            symbol("a", 81, constant = ConstantValue.Integral(99)),
                            symbol("b", 82, constant = ConstantValue.Integral(99)),
                            symbol("free", 83)
                        ),
                        source = source
                    ),
                    readonlyDefaults = mapOf(0 to expression(parse(source), syntax))
                )
            }
            assertEquals("defaults.mcfpp", failure.source.file)
            assertTrue(failure.source.endOffset > failure.source.startOffset)
            assertTrue(failure.message!!.contains("unavailable or later formal"))
        }
    }

    @Test fun normalDefaultsRejectForwardAndSelfReferencesEvenWhenCallerHasConstants() {
        val x = symbol("x", 41)
        val y = symbol("y", 42)
        for (syntax in listOf("x", "y", "y + 1")) {
            val source = "func main(){ var result = $syntax; }"
            val failure = assertFailsWith<SourceBindingFailure> {
                binder.bind(
                    declaration("bad", normal = listOf(x, y)),
                    environment(
                        listOf(
                            symbol("x", 94, constant = ConstantValue.Integral(1)),
                            symbol("y", 95, constant = ConstantValue.Integral(2))
                        ),
                        source = source
                    ),
                    normalDefaults = mapOf(0 to expression(parse(source), syntax.replace(" ", "")))
                )
            }
            assertTrue(failure.message!!.contains("unavailable or later formal"))
        }
    }

    @Test fun normalDefaultCallsKeepNativeEffectsAndDeclarationTargetsWithoutTouchingCompilerState() {
        Project.init()
        val current = Function("default_sentinel", "fixture.defaults", null)
        val sentinel = MCInt(23)
        val x = symbol("x", 51)
        val y = symbol("y", 52)
        val native = declaration(
            "effect",
            normal = listOf(symbol("input", 53)),
            kind = FunctionKind.NATIVE,
            effect = BoundEffect.UNKNOWN
        )
        val source = "func main(){ var result = effect(x); }"
        current.runInFunction {
            current.scope.putVar("sentinel", sentinel)
            StorageAccess.ensure(sentinel)
            current.commands.add(Command("say defaults-sentinel"))
            val commands = current.commands.toList()
            val vars = current.scope.vars.toMap()
            val children = current.child.toList()
            val parents = current.parent.toList()
            val resources = Project.macroFunction.toMap()
            val constants = Project.constants.toMap()
            val flow = StorageAccess.flowSnapshot(listOf(sentinel))
            val declaration = binder.bind(
                declaration("target", normal = listOf(x, y)),
                environment(functions = listOf(native), source = source),
                normalDefaults = mapOf(1 to expression(parse(source), "effect(x)"))
            )
            val template = declaration.normalDefaultExpressions.getValue(1)
            val invocation = assertIs<BoundExprNode.Call>(template.node).sourceCall
            assertSame(native, invocation.target)
            assertEquals(BoundEffect.UNKNOWN, template.effect)
            assertNull(template.constant)
            assertEquals(x.symbol, read(assertIs<BoundCallArgument.Value>(invocation.arguments.single()).expression).symbol)
            assertEquals(1, invocation.evaluationOrder.size)
            assertSame(current, Function.currFunction)
            assertEquals(commands, current.commands.toList())
            assertEquals(vars, current.scope.vars)
            assertEquals(children, current.child)
            assertEquals(parents, current.parent)
            assertEquals(resources, Project.macroFunction)
            assertEquals(constants, Project.constants)
            assertEquals(flow, StorageAccess.flowSnapshot(listOf(sentinel)))
            assertEquals(0, Project.errorCount)
        }
    }

    @Test fun readonlyDefaultsCannotInterpretAFunctionEvenWithPureDeclaredEffect() {
        val source = "func main(){ var result = calculate(); }"
        val target = declaration("calculate", effect = BoundEffect.PURE)
        val failure = assertFailsWith<SourceBindingFailure> {
            binder.bind(
                declaration("bad", listOf(symbol("a", 61))),
                environment(functions = listOf(target), source = source),
                readonlyDefaults = mapOf(0 to expression(parse(source), "calculate()"))
            )
        }
        assertTrue(failure.message!!.contains("cannot evaluate a call"))
        assertEquals("defaults.mcfpp", failure.source.file)
    }

    @Test fun commonSelectorReadsConcretePreparedSignaturesAndKeepsItsSingleConversionRanking() {
        val r = symbol("r", 71)
        val x = symbol("x", 72)
        val original = declaration("pick", listOf(r), listOf(x), key = "pick-original")
        val prepared = binder.prepareReadonly(original, listOf(literal(7)), catalog, span)
        val concrete = declaration("pick", listOf(r), listOf(x), key = "pick-concrete", prepared = prepared)
        val broad = declaration("pick", listOf(symbol("r", 73)), listOf(symbol("x", 74, catalog.ref(MCFPPBaseType.Object))), key = "pick-broad")
        val selected = SnapshotSourceCodeBinder().call(call("pick<7>(1)"), environment(functions = listOf(broad, concrete)))
        assertSame(concrete, selected.target)
        assertEquals(original.declarationIdentity, selected.target.declarationIdentity)
        assertEquals(FunctionKey(909, "pick-concrete"), (selected.target.function as ImmutableFunctionRef<*>).key)
        assertSame(intType, selected.target.function.returnType())
        assertEquals(1, selected.arguments.size)
    }

    @Test fun mismatchedReadonlyFactsAreRejectedWithoutCreatingAnotherSpecialization() {
        val r = symbol("r", 75)
        val original = declaration("prepared", listOf(r))
        val concrete = declaration(
            "prepared",
            listOf(r),
            key = "prepared-7",
            prepared = binder.prepareReadonly(original, listOf(literal(7)), catalog, span)
        )
        val failure = assertFailsWith<SourceBindingFailure> {
            SnapshotSourceCodeBinder().call(call("prepared<8>()"), environment(functions = listOf(concrete)))
        }
        assertTrue(failure.message!!.contains("No overload"))
        assertEquals(FunctionKey(909, "prepared"), concrete.declarationIdentity)
    }

    @Test fun historicalSpecializationsOfOneDeclarationCannotBecomeExtraOverloads() {
        val r = symbol("r", 76)
        val original = declaration("history", listOf(r))
        val copies = listOf(1L, 2L).map { value ->
            declaration(
                "history",
                listOf(r),
                key = "history-$value",
                prepared = binder.prepareReadonly(original, listOf(literal(value)), catalog, span)
            )
        }
        val failure = assertFailsWith<SourceBindingFailure> { environment(functions = copies) }
        assertTrue(failure.message!!.contains("Multiple prepared signatures"))
    }

    @Test fun preparedReadonlyFactsAreDeeplyImmutableAndCannotCrossARebuiltDomain() {
        val type = catalog.ref(MCFPPBaseType.Object)
        val values = arrayListOf<ConstantValue>(ConstantValue.TypeValue(intType))
        val fields = linkedMapOf("types" to ConstantValue.Sequence(values))
        val arguments = arrayListOf(TypedConstant(type, ConstantValue.Record(fields), span))
        val prepared = SourcePreparedReadonly(FunctionKey(909, "identity"), arguments, span)
        arguments.clear()
        values.clear()
        fields.clear()
        val payload = assertIs<ConstantValue.Record>(prepared.arguments.single().value)
        assertSame(intType, assertIs<ConstantValue.TypeValue>(assertIs<ConstantValue.Sequence>(payload.fields.getValue("types")).elements.single()).type)
        val formal = symbol("type", 77, type)
        val original = declaration("identity", listOf(formal), prepared = prepared)
        environment(functions = listOf(original))
        val fresh = snapshot()
        val freshFormal = formal.copy(type = fresh.ref(MCFPPBaseType.Object))
        val freshFunction = declaration(
            "identity",
            listOf(freshFormal),
            prepared = SourcePreparedReadonly(
                prepared.declaration,
                listOf(TypedConstant(freshFormal.type, payload, span)),
                span
            ),
            result = fresh.ref(MCFPPBaseType.Int)
        )
        assertFailsWith<IllegalArgumentException> { BindingEnvironment(fresh, emptyList(), listOf(freshFunction)) }
        assertSame(intType, assertIs<ConstantValue.TypeValue>(assertIs<ConstantValue.Sequence>(payload.fields.getValue("types")).elements.single()).type)
        val freshPayload = ConstantValue.Record(
            mapOf(
                "types" to
                    ConstantValue.Sequence(listOf(ConstantValue.TypeValue(fresh.ref(MCFPPBaseType.Int))))
            )
        )
        val rebuilt = declaration(
            "identity",
            listOf(freshFormal),
            prepared = SourcePreparedReadonly(
                prepared.declaration,
                listOf(TypedConstant(freshFormal.type, freshPayload, span)),
                span
            ),
            result = fresh.ref(MCFPPBaseType.Int)
        )
        BindingEnvironment(fresh, emptyList(), listOf(rebuilt))
    }

    @Test fun cyclicOmittedDefaultExpansionIsRejectedWithoutEnteringAnyBody() {
        val legacy = SourceTypeCatalog.snapshot(domain)
        val type = legacy.ref(MCFPPBaseType.Int)
        val x = symbol("x", 78, type)
        val function = function("cycle", emptyList(), listOf(x), type, FunctionKind.ORDINARY, "cycle")
        val shell = SourceFunction("cycle", function, defaultedNormalParameters = setOf(0))
        val invocation = SourceCall("cycle", shell, emptyList(), emptyList(), span, emptyList(), listOf(BoundEffect.CALL), BoundEffect.CALL)
        val template = BoundExpr(BoundExprNode.Call(invocation), type, null, BoundEffect.CALL, span)
        val complete = SourceFunction("cycle", function, normalDefaultExpressions = mapOf(0 to template), normalSymbols = listOf(x))
        val failure = assertFailsWith<SourceBindingFailure> { BindingEnvironment(legacy, emptyList(), listOf(complete)) }
        assertTrue(failure.message!!.contains("Cyclic default expansion"))
    }

    @Test fun anExplicitRecursiveCallInANormalDefaultIsRetainedRatherThanExecutedOrRejectedAsADefaultCycle() {
        val x = symbol("x", 79)
        val original = declaration("recursive", normal = listOf(x))
        val source = "func main(){ var result = recursive(1); }"
        val result = binder.bind(
            original,
            environment(functions = listOf(original), source = source),
            normalDefaults = mapOf(0 to expression(parse(source), "recursive(1)"))
        )
        val expression = result.normalDefaultExpressions.getValue(0)
        assertIs<BoundExprNode.Call>(expression.node)
        assertEquals(BoundEffect.CALL, expression.effect)
        assertNull(expression.constant)
        environment(functions = listOf(result))
    }

    @Test fun preparedDefaultMapsAndFormalIdentitiesRemainIndependentOfMutablePreparationInputs() {
        val a = symbol("a", 80)
        val b = symbol("b", 81)
        val syntax = linkedMapOf(1 to expression(parse("func main(){ var result = a + 1; }"), "a+1"))
        val formals = arrayListOf(a, b)
        val result = binder.bind(declaration("stable", formals), environment(), readonlyDefaults = syntax, readonlySymbols = formals)
        syntax.clear()
        formals.clear()
        assertEquals(listOf(a, b), result.readonlySymbols)
        assertEquals(ConstantValue.Integral(4), binder.completeReadonly(result, listOf(literal(3)), catalog, span)[1].constant)
        assertEquals(setOf(1), result.readonlyDefaultExpressions.keys)
    }

    private fun symbol(name: String, id: Long, type: TypeRef<*> = intType, constant: ConstantValue? = null) = SourceSymbol(name, type, SymbolId(SessionId(0), id), constant = constant)
    private fun literal(value: Long) = BoundExpr(
        BoundExprNode.Literal(ConstantValue.Integral(value)),
        intType,
        ConstantValue.Integral(value),
        BoundEffect.PURE,
        span
    )
    private fun read(expression: BoundExpr) = assertIs<BoundExprNode.Read>(expression.node).location.root
    private inline fun withFloatTarget(action: () -> Unit) {
        val previous = Project.config.version
        try {
            Project.config.version = "26.3"
            action()
        } finally {
            Project.config.version = previous
        }
    }
    private fun function(
        name: String,
        readonly: List<SourceSymbol>,
        normal: List<SourceSymbol>,
        result: TypeRef<*>,
        kind: FunctionKind,
        key: String
    ): FunctionRef<*> = ImmutableFunctionRef(
        FunctionKey(909, key),
        name,
        FunctionSignature.of(normal.map { ParameterView.of(it.name, it.type, ParamMode.VALUE) }, result),
        kind,
        readonlyParameters = readonly.map { ParameterView.of(it.name, it.type, ParamMode.VALUE) }
    )
    private fun declaration(
        name: String,
        readonly: List<SourceSymbol> = emptyList(),
        normal: List<SourceSymbol> = emptyList(),
        result: TypeRef<*> = intType,
        kind: FunctionKind = FunctionKind.ORDINARY,
        key: String = name,
        prepared: SourcePreparedReadonly? = null,
        effect: BoundEffect = BoundEffect.CALL
    ) = SourceFunction(
        name,
        function(name, readonly, normal, result, kind, key),
        effect,
        readonly.map { SourceParameter(it.type) },
        readonlySymbols = readonly,
        normalSymbols = normal,
        preparedReadonly = prepared
    )
    private fun environment(symbols: List<SourceSymbol> = emptyList(), functions: List<SourceFunction> = emptyList(), source: String? = null) = BindingEnvironment(catalog, symbols, functions, sourceText = source, sourceFile = "defaults.mcfpp")
    private fun parse(source: String): P.CompilationUnitContext {
        val parser = P(CommonTokenStream(mcfppLexer(CharStreams.fromString(source))))
        return parser.compilationUnit().also { check(parser.numberOfSyntaxErrors == 0) { source } }
    }
    private fun expression(tree: ParseTree, text: String): P.ExpressionContext = find(tree, P.ExpressionContext::class.java) { it.text == text }
    private fun call(text: String): P.FunctionCallContext = find(parse("func main(){ $text; }"), P.FunctionCallContext::class.java) { true }
    private fun <T : ParseTree> find(root: ParseTree, type: Class<T>, predicate: (T) -> Boolean): T {
        if (type.isInstance(root) && predicate(type.cast(root))) return type.cast(root)
        for (i in 0 until root.childCount) {
            val child = findOrNull(root.getChild(i), type, predicate)
            if (child != null) return child
        }
        error("Missing syntax: ${type.simpleName}")
    }
    private fun <T : ParseTree> findOrNull(root: ParseTree, type: Class<T>, predicate: (T) -> Boolean): T? {
        if (type.isInstance(root) && predicate(type.cast(root))) return type.cast(root)
        for (i in 0 until root.childCount) findOrNull(root.getChild(i), type, predicate)?.let { return it }
        return null
    }
}

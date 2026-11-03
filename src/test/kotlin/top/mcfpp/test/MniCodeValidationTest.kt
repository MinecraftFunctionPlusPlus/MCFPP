package top.mcfpp.test

import top.mcfpp.mni.api.*
import top.mcfpp.mni.expand.*
import java.util.function.Consumer
import java.util.function.Function
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue

class MniCodeValidationTest {
    private class Fixture(id: Long = 1) {
        val arena = CodeArena(SessionId(id), ScopeId(SessionId(id), 0)) { _, _ -> }
        val site = SourceSpan("caller.mcfpp", 10, 20, 2, 1, 2, 11)
        val generated = SourceSpan("generated.mcfpp", 0, 2, 1, 1, 1, 3)
        val origin = Origin(site, generated)
        val root = arena.rootScope
        val meta = arena.metadata(root, origin)
        val int = ImmutableTypeRef<IntT>(TypeKey(1, 1), "int", kind = TypeKind.INT)
        val bool = ImmutableTypeRef<BoolT>(TypeKey(1, 2), "bool", kind = TypeKind.BOOL)
        val float = ImmutableTypeRef<FloatT>(TypeKey(1, 3), "float", kind = TypeKind.FLOAT)
        val string = ImmutableTypeRef<StringT>(TypeKey(1, 4), "string", kind = TypeKind.STRING)
        val void = ImmutableTypeRef<VoidT>(TypeKey(1, 5), "void", kind = TypeKind.VOID)
        val other = ImmutableTypeRef<Any>(TypeKey(1, 6), "record")
        val types = object : TypeService {
            override fun intType(): TypeRef<IntT> = int
            override fun boolType(): TypeRef<BoolT> = bool
            override fun floatType(): TypeRef<FloatT> = float
            override fun stringType(): TypeRef<StringT> = string
            override fun voidType(): TypeRef<VoidT> = void
            override fun lookup(name: String): TypeRef<*> = error("No name lookup during validation")
            override fun function(name: String, argumentTypes: List<TypeRef<*>>): FunctionRef<*> = error("No function lookup during validation")
        }
        val quote = DefaultQuote(arena, types, origin)
        fun metadata(scope: ScopeId = root, context: ExecutionContextId? = null) = arena.metadata(scope, origin, context)
        fun child() = arena.childScope(root)
        fun integer(value: Long = 1, scope: ScopeId = root) = ImmutableExpr(metadata(scope), int, ExprNode.Literal(ConstantValue.Integral(value)))
        fun boolean(value: Boolean = true, scope: ScopeId = root) = ImmutableExpr(metadata(scope), bool, ExprNode.Literal(ConstantValue.Bool(value)))
        fun stmt(node: StmtNode, scope: ScopeId = root) = ImmutableStmt(metadata(scope), node)
        fun block(vararg nodes: StmtNode, scope: ScopeId = root) = ImmutableBlock(metadata(scope), nodes.map { stmt(it, scope) })
        fun <T> ref(type: TypeRef<T>, writable: Boolean = true, context: ExecutionContextId? = null): ImmutableRef<T> = arena.ref(metadata(context = context), type, NodeLocation(arena.freshSymbol(), executionContextId = context, writable = writable))
        fun <T> function(name: String, result: TypeRef<T>, parameters: List<ParameterView> = emptyList(), kind: FunctionKind = FunctionKind.ORDINARY) = ImmutableFunctionRef(FunctionKey(1, name), name, FunctionSignature.of(parameters, result), kind)
        fun declared(signature: InlineSignature<*>, name: String = "callback") = arena.inline<Any>(meta, signature, CallbackId(1, name), InlineTarget.Declared(FunctionKey(1, name)))
        fun inline(callback: InlineFn<*>, arguments: List<InlineArgument> = emptyList()) = ImmutableExpr(meta, callback.signature().returnType(), ExprNode.InlineCall(callback, arguments))
        fun call(function: FunctionRef<*>, arguments: List<CallArgument> = emptyList(), macro: Boolean = false): Expr<*> = ImmutableExpr(meta, function.returnType(), if (macro) ExprNode.MacroCall(function, arguments) else ExprNode.Call(function, arguments))
        fun environment(
            bindings: List<Ref<*>> = emptyList(),
            functions: List<FunctionRef<*>> = emptyList(),
            inlineTargets: List<FunctionRef<*>> = emptyList(),
            result: ValidationRegion? = null
        ) = ValidationEnvironment(arena, bindings, functions, inlineTargets, result, listOf(int, bool, float, string, void, other))
        fun validate(code: Code, environment: ValidationEnvironment = environment(), phase: ValidationPhase = ValidationPhase.PRE_EXPANSION) = CodeValidator().validate(code, environment, phase)
        fun rejects(code: Code, message: String, environment: ValidationEnvironment = environment(), phase: ValidationPhase = ValidationPhase.PRE_EXPANSION) {
            val error = assertFailsWith<CodeModelException> { validate(code, environment, phase) }
            assertEquals(site, error.source, "Diagnostics must point to the macro caller, rather than generated source")
            assertTrue(error.message!!.contains(message), error.message)
        }
    }

    @Test
    @Suppress("UNCHECKED_CAST")
    fun forgedActualTypesAndUnsupportedPayloadsFailAtTheCallSite() {
        val f = Fixture()
        f.rejects(ImmutableExpr(f.meta, f.int, ExprNode.Literal(ConstantValue.Bool(true))), "Literal payload")
        f.rejects(ImmutableExpr(f.meta, f.bool, ExprNode.Binary(BinaryOperator.ADD, f.boolean(), f.boolean())), "Operator")
        f.rejects(ImmutableExpr(f.meta, f.bool, ExprNode.Unary(UnaryOperator.NEGATE, f.boolean())), "Numeric negation")
        f.rejects(ImmutableExpr(f.meta, f.bool, ExprNode.Binary(BinaryOperator.ADD, f.integer(), f.integer())), "Actual type mismatch")
        f.rejects(ImmutableExpr(f.meta, f.int, ExprNode.Convert(f.boolean(), f.int, ConversionKind.EXPLICIT)), "Unsupported actual type conversion")
        f.rejects(ImmutableExpr(f.meta, f.other, ExprNode.Literal(ConstantValue.Nbt("{}"))), "no supported actual type schema")
        val child = f.child()
        f.rejects(f.block(StmtNode.If(f.integer() as Expr<BoolT>, f.block(scope = child), null)), "actual bool")
        val storage = f.ref(f.int)
        f.rejects(f.block(StmtNode.Assign(storage, f.boolean())), "not assignable", f.environment(listOf(storage)))
        val region = f.arena.freshRegion()
        f.rejects(f.block(StmtNode.Yield(region, f.boolean())), "not assignable", f.environment(result = ValidationRegion(region, f.int)))
    }

    @Test
    fun declarationOrderAndBranchDominanceAreCheckedIndependentlyOfHandleScope() {
        val f = Fixture()
        val local = f.arena.local(f.meta, f.int, "later")
        val declaration = f.stmt(StmtNode.LocalDecl(local, f.integer()))
        f.rejects(ImmutableBlock(f.meta, listOf(f.stmt(StmtNode.Eval(local.load())), declaration)), "no dominating declaration")
        f.rejects(f.block(StmtNode.LocalDecl(local, local.load())), "no dominating declaration")
        f.rejects(ImmutableBlock(f.meta, listOf(declaration, declaration)), "Duplicate local")
        val branchScope = f.child()
        val branchLocal = f.arena.local(f.metadata(branchScope), f.int, "private")
        val branch = f.block(StmtNode.LocalDecl(branchLocal, f.integer(scope = branchScope)), scope = branchScope)
        f.rejects(f.block(StmtNode.If(f.boolean(), branch, null), StmtNode.Eval(branchLocal.load())), "dominating lexical scope")
        val siblingScope = f.child()
        val sibling = f.block(StmtNode.Eval(branchLocal.load()), scope = siblingScope)
        f.rejects(f.block(StmtNode.If(f.boolean(), branch, sibling)), "dominating lexical scope")
        f.rejects(f.block(StmtNode.LocalDecl(local, f.integer()), scope = branchScope), "Local declaration is outside")
        val unbound = ImmutableExpr(f.meta, f.int, ExprNode.Read(NodeLocation(f.arena.freshSymbol())))
        f.rejects(unbound, "no dominating declaration")
        assertIs<PrevalidatedCode>(f.validate(ImmutableBlock(f.meta, listOf(declaration, f.stmt(StmtNode.Eval(local.load()))))))
    }

    @Test
    fun legalBuilderControlFlowSurvivesBothPhasesAndPreservesTheOriginalTree() {
        val f = Fixture()
        val storage = f.ref(f.int)
        val generated = f.quote.block(
            Consumer { b ->
                val local = b.local(f.int, b.intLiteral(0))
                b.whileLoop(
                    Function { condition -> condition.lt(local.load(), condition.intLiteral(2)) },
                    Consumer { body ->
                        body.assign(local, body.add(local.load(), body.intLiteral(1)))
                    }
                )
                b.assign(storage, local.load())
            }
        )
        val environment = f.environment(listOf(storage))
        assertSame(generated, assertIs<PrevalidatedCode>(f.validate(generated, environment)).code)
        assertSame(generated, assertIs<ValidatedCode>(f.validate(generated, environment, ValidationPhase.FINAL)).code)
        val flag = f.ref(f.bool)
        val result = f.quote.expression(
            f.int,
            Consumer { b ->
                b.ifElse(flag.load(), Consumer { yes -> yes.yield(yes.intLiteral(1)) }, Consumer { no -> no.yield(no.intLiteral(2)) })
            }
        )
        assertIs<ValidatedCode>(f.validate(result, f.environment(listOf(flag)), ValidationPhase.FINAL))
    }

    @Test
    fun resultRegionsDistinguishMissingDuplicateForeignAndNonReturningExits() {
        val f = Fixture()
        val region = f.arena.freshRegion()
        val scope = f.child()
        fun expression(body: Block) = ImmutableExpr(f.meta, f.int, ExprNode.ExprBlock(body, f.int, region))
        f.rejects(expression(f.block(scope = scope)), "normal exit without yield")
        f.rejects(expression(f.block(StmtNode.Yield(f.arena.freshRegion(), f.integer()), scope = scope)), "crosses an expression")
        f.rejects(expression(f.block(StmtNode.Yield(region, f.integer()), StmtNode.Yield(region, f.integer()), scope = scope)), "terminal control exit")
        f.rejects(f.block(StmtNode.Yield(region, f.integer())), "outside an expression")
        val yesScope = f.arena.childScope(scope)
        val yes = f.block(StmtNode.Yield(region, f.integer()), scope = yesScope)
        assertIs<PrevalidatedCode>(f.validate(expression(f.block(StmtNode.If(f.boolean(), yes, null), scope = scope))))
        f.rejects(expression(f.block(StmtNode.If(f.boolean(false), yes, null), scope = scope)), "normal exit without yield")
        val loop = f.arena.freshRegion()
        val loopScope = f.arena.childScope(scope)
        val forever = f.block(StmtNode.Continue(loop), scope = loopScope)
        assertIs<PrevalidatedCode>(f.validate(expression(f.block(StmtNode.While(f.boolean(), forever, loop), scope = scope))))
        val exits = f.block(StmtNode.Break(loop), scope = loopScope)
        f.rejects(expression(f.block(StmtNode.While(f.boolean(), exits, loop), scope = scope)), "normal exit without yield")
        f.rejects(f.block(StmtNode.Break(loop)), "current callback loop")
        f.rejects(f.block(StmtNode.Continue(loop)), "current callback loop")
        // Builder conditions are themselves expression regions, including constant true conditions.
        val builtForever = f.quote.expression(
            f.int,
            Consumer { b ->
                b.whileLoop(Function { it.boolLiteral(true) }, Consumer { })
            }
        )
        assertIs<PrevalidatedCode>(f.validate(builtForever))
    }

    @Test
    fun callsRequireBoundTargetsActualSignaturesAndSupportedModes() {
        val f = Fixture()
        val storage = f.ref(f.int)
        val helper = f.function("alias", f.int, listOf(ParameterView.of("a", f.int, ParamMode.REF), ParameterView.of("b", f.int, ParamMode.REF)), FunctionKind.HELPER)
        val environment = f.environment(listOf(storage), listOf(helper))
        val alias = f.call(helper, listOf(CallArgument.ref(storage), CallArgument.ref(storage)))
        assertIs<ValidatedCode>(f.validate(alias, environment, ValidationPhase.FINAL))
        f.rejects(alias, "not bound")
        f.rejects(f.call(helper), "Argument count", environment)
        f.rejects(f.call(helper, listOf(CallArgument.value(f.integer()), CallArgument.ref(storage))), "Argument mode", environment)
        val lie = ImmutableExpr(f.meta, f.bool, ExprNode.Call(helper, listOf(CallArgument.ref(storage), CallArgument.ref(storage))))
        f.rejects(lie, "Actual type mismatch", environment)
        val forged = f.function("alias", f.int, emptyList(), FunctionKind.HELPER)
        f.rejects(f.call(forged), "signature parameter count", environment)
        val ordinaryRef = f.function("ordinaryRef", f.int, helper.parameters())
        f.rejects(f.call(ordinaryRef, listOf(CallArgument.ref(storage), CallArgument.ref(storage))), "does not support", f.environment(listOf(storage), listOf(ordinaryRef)))
        val void = f.function("void", f.void)
        f.rejects(f.call(void), "void type", f.environment(functions = listOf(void)))
        assertIs<ValidatedCode>(f.validate(f.block(StmtNode.Eval(f.call(void))), f.environment(functions = listOf(void)), ValidationPhase.FINAL))
    }

    @Test
    fun expansionNodesAreAllowedOnlyBeforeFinalValidation() {
        val f = Fixture()
        val macro = f.function("macro", f.int, kind = FunctionKind.MACRO)
        val expression = f.call(macro, macro = true)
        val environment = f.environment(functions = listOf(macro))
        assertIs<PrevalidatedCode>(f.validate(expression, environment))
        f.rejects(expression, "MacroCall remains", environment, ValidationPhase.FINAL)
        f.rejects(f.call(macro), "actual target kind", environment)
        val ordinary = f.function("ordinary", f.int)
        f.rejects(f.call(ordinary, macro = true), "actual target kind", f.environment(functions = listOf(ordinary)))
        val target = f.function("callback", f.int)
        val callback = f.declared(target.signature)
        val inline = f.inline(callback)
        val callbacks = f.environment(inlineTargets = listOf(target))
        assertIs<PrevalidatedCode>(f.validate(inline, callbacks))
        f.rejects(inline, "InlineCall remains", callbacks, ValidationPhase.FINAL)
        f.rejects(inline, "no registered static body")
        val native = f.function("callback", f.int, kind = FunctionKind.NATIVE)
        f.rejects(inline, "cannot be used as a static inline body", f.environment(inlineTargets = listOf(native)))
    }

    @Test
    fun nestedInlineSignaturesUseImmutableDescriptorsInsteadOfDisplayNames() {
        val f = Fixture()
        val parameters = arrayListOf(ParameterView.of("input", f.int, ParamMode.REF))
        val required = FunctionSignature.of(parameters, f.int)
        val callbackType = ImmutableTypeRef<Any>(TypeKey(1, 20), "same display name", inlineSignature = required)
        val inherited = ParameterView.of("body", callbackType, ParamMode.INLINE)
        val explicit = ParameterView.of("body", f.other, ParamMode.INLINE, required)
        parameters.clear()
        assertEquals(1, inherited.inlineSignature()!!.parameters().size)
        assertFailsWith<UnsupportedOperationException> { (inherited.inlineSignature()!!.parameters() as MutableList<ParameterView>).clear() }
        val macro = f.function("accept", f.int, listOf(inherited), FunctionKind.MACRO)
        val target = f.function("callback", f.int, listOf(ParameterView.of("differentName", f.int, ParamMode.REF)))
        val callback = f.declared(target.signature)
        val environment = f.environment(functions = listOf(macro), inlineTargets = listOf(target))
        assertIs<PrevalidatedCode>(f.validate(f.call(macro, listOf(CallArgument.inline(callback)), macro = true), environment))
        val explicitMacro = f.function("explicit", f.int, listOf(explicit), FunctionKind.MACRO)
        assertIs<PrevalidatedCode>(f.validate(f.call(explicitMacro, listOf(CallArgument.inline(callback)), true), f.environment(functions = listOf(explicitMacro), inlineTargets = listOf(target))))
        listOf(
            FunctionSignature.of(listOf(ParameterView.of("input", f.bool, ParamMode.REF)), f.int),
            FunctionSignature.of(listOf(ParameterView.of("input", f.int, ParamMode.VALUE)), f.int),
            FunctionSignature.of(listOf(ParameterView.of("input", f.int, ParamMode.REF)), f.bool)
        ).forEach { wrong ->
            val bad = f.declared(wrong)
            val error = assertFailsWith<CodeModelException> { f.validate(f.call(macro, listOf(CallArgument.inline(bad)), true), environment) }
            assertEquals(f.site, error.source)
        }
        val missing = f.function("missing", f.int, listOf(ParameterView.of("body", f.other, ParamMode.INLINE)), FunctionKind.MACRO)
        f.rejects(f.call(missing, listOf(CallArgument.inline(callback)), true), "no nested signature", f.environment(functions = listOf(missing), inlineTargets = listOf(target)))
    }

    @Test
    fun callbackReturnsHaveSeparateRegionsCapturesAndLoopBoundaries() {
        val f = Fixture()
        val captured = f.ref(f.int)
        val returnRegion = f.arena.freshRegion()
        val bodyScope = f.child()
        val signature = FunctionSignature.of(emptyList(), f.int)
        fun callback(body: Block, captures: Set<SymbolId> = setOf(captured.location.root)): ImmutableInlineFn<Any> = f.arena.inline(f.meta, signature, CallbackId(1, "body"), InlineTarget.Body(emptyList(), body, captures, returnRegion))
        val good = callback(f.block(StmtNode.CallbackReturn(returnRegion, captured.load()), scope = bodyScope))
        val environment = f.environment(listOf(captured))
        assertIs<PrevalidatedCode>(f.validate(f.inline(good), environment))
        val flag = f.ref(f.bool)
        val innerLoop = f.arena.freshRegion()
        val loopBodyScope = f.arena.childScope(bodyScope)
        val yesScope = f.arena.childScope(loopBodyScope)
        val noScope = f.arena.childScope(loopBodyScope)
        val loopBody = f.block(
            StmtNode.If(
                flag.load(),
                f.block(StmtNode.CallbackReturn(returnRegion, captured.load()), scope = yesScope),
                f.block(StmtNode.Break(innerLoop), scope = noScope)
            ),
            scope = loopBodyScope
        )
        val earlyReturn = callback(
            f.block(
                StmtNode.While(flag.load(), loopBody, innerLoop),
                StmtNode.CallbackReturn(returnRegion, f.integer()),
                scope = bodyScope
            ),
            setOf(captured.location.root, flag.location.root)
        )
        assertIs<PrevalidatedCode>(f.validate(f.inline(earlyReturn), f.environment(listOf(captured, flag))))
        f.rejects(f.block(StmtNode.CallbackReturn(returnRegion, f.integer())), "outside a callback")
        f.rejects(f.inline(callback(f.block(StmtNode.Yield(returnRegion, f.integer()), scope = bodyScope))), "outside an expression", environment)
        f.rejects(f.inline(callback(f.block(scope = bodyScope))), "normal exit without return", environment)
        f.rejects(f.inline(callback(f.block(StmtNode.CallbackReturn(f.arena.freshRegion(), f.integer()), scope = bodyScope))), "crosses a callback", environment)
        f.rejects(f.inline(callback(f.block(StmtNode.CallbackReturn(returnRegion, f.boolean()), scope = bodyScope))), "not assignable", environment)
        f.rejects(f.inline(callback(f.block(StmtNode.CallbackReturn(returnRegion, captured.load()), scope = bodyScope), emptySet())), "no dominating declaration", environment)
        val loop = f.arena.freshRegion()
        val escaped = callback(f.block(StmtNode.Break(loop), scope = bodyScope), emptySet())
        val loopScope = f.child()
        f.rejects(f.block(StmtNode.While(f.boolean(), f.block(StmtNode.Eval(f.inline(escaped)), scope = loopScope), loop)), "current callback loop", environment)
    }

    @Test
    fun referencesCannotWidenCapabilitiesChangeFieldsOrRebindExecutionContexts() {
        val f = Fixture()
        val readonly = f.ref(f.int, writable = false)
        val forged = ImmutableRef(f.meta, f.int, NodeLocation(readonly.location.root))
        f.rejects(forged.load(), "widens a read-only", f.environment(listOf(readonly)))
        f.rejects(f.block(StmtNode.Assign(readonly, f.integer())), "read-only", f.environment(listOf(readonly)))
        val owner = TypeKey(1, 30)
        val member = ImmutableFieldRef(FieldKey(owner, 1), "value", f.int, false, f.site)
        val record = ImmutableTypeRef<Any>(owner, "Record", fields = listOf(member))
        val base = f.ref(record)
        val writableField = ImmutableRef(f.meta, f.int, NodeLocation(base.location.root, listOf(LocationStep.Field(member))))
        f.rejects(writableField.load(), "widens a read-only", f.environment(listOf(base)))
        val foreign = ImmutableFieldRef(FieldKey(TypeKey(1, 31), 1), "value", f.int, false, f.site)
        f.rejects(ImmutableExpr(f.meta, f.int, ExprNode.Field(base.load(), foreign)), "does not belong", f.environment(listOf(base)))
        val first = f.arena.freshExecutionContext()
        val second = f.arena.freshExecutionContext()
        val bound = f.ref(f.int, context = first)
        val movedRead = ImmutableExpr(f.metadata(context = second), f.int, ExprNode.Read(bound.location))
        f.rejects(movedRead, "cannot be rebound", f.environment(listOf(bound)))
        val lostContext = ImmutableRef(f.meta, f.int, bound.location)
        f.rejects(lostContext.load(), "lost its bound execution context", f.environment(listOf(lostContext)))
    }

    @Test
    fun boundDynamicLocationsRequireDominatingTypedCapturedComponents() {
        val f = Fixture()
        val index = f.ref(f.int)
        val bound = f.arena.ref(f.meta, f.int, NodeLocation(f.arena.freshSymbol(), listOf(LocationStep.CapturedIndex(index.location.root))))
        assertIs<PrevalidatedCode>(f.validate(bound.load(), f.environment(listOf(bound, index))))
        f.rejects(bound.load(), "component has no dominating binding", f.environment(listOf(bound)))
        val wrong = f.ref(f.bool)
        val bad = f.arena.ref(f.meta, f.int, NodeLocation(f.arena.freshSymbol(), listOf(LocationStep.CapturedIndex(wrong.location.root))))
        f.rejects(bad.load(), "component has the wrong actual type", f.environment(listOf(bad, wrong)))
        val base = f.ref(f.int)
        val unboundPath = ImmutableExpr(f.meta, f.int, ExprNode.Read(NodeLocation(base.location.root, listOf(LocationStep.Index(0)))))
        f.rejects(unboundPath, "not supported", f.environment(listOf(base)))
    }

    @Test
    fun sessionsLifetimesAndInputEscapesAreEnforced() {
        val f = Fixture()
        val foreign = Fixture(2)
        f.rejects(foreign.integer(), "another expansion session")
        f.rejects(f.ref(f.int), "cannot escape")
        f.rejects(f.arena.value(f.integer()), "cannot escape")
        f.rejects(f.declared(FunctionSignature.of(emptyList(), f.int)), "cannot escape")
        val code = f.integer()
        f.arena.close()
        f.rejects(code, "closed")
    }

    @Test
    fun canonicalPrintingIgnoresAllocationNoiseAndResetsOnEveryPrint() {
        fun make(id: Long, noise: Int, reverse: Boolean = false): Block {
            val f = Fixture(id)
            repeat(noise) {
                f.arena.freshSymbol()
                f.arena.freshRegion()
                f.arena.freshExecutionContext()
            }
            val external = f.ref(f.int)
            return f.quote.block(
                Consumer { b ->
                    val local = b.local("displayHint$id", f.int, b.intLiteral(1))
                    if (reverse) {
                        b.assign(external, b.intLiteral(2))
                        b.evaluate(local.load())
                    } else {
                        b.evaluate(local.load())
                        b.assign(external, b.intLiteral(2))
                    }
                }
            )
        }
        val printer = CodePrinter()
        val first = make(1, 0)
        val printed = printer.print(first)
        assertEquals(printed, printer.print(make(777, 42)))
        assertNotEquals(printed, printer.print(make(777, 42, reverse = true)))
        assertEquals(printed, printer.print(first))
        assertTrue(!printed.contains("SymbolId") && !printed.contains("SessionId") && !printed.contains("displayHint"))
        assertEquals("{local s0 : \"int\"#t0 = literal 1 : \"int\"#t0; eval read s0 : \"int\"#t0; assign s1 = literal 2 : \"int\"#t0}", printed)
    }

    @Test
    fun printingCallbackCaptureSetsDoesNotDependOnTheirIterationOrder() {
        fun make(id: Long, reverse: Boolean): Block {
            val f = Fixture(id)
            repeat(id.toInt()) {
                f.arena.freshRegion()
                f.arena.freshSymbol()
            }
            val used = f.ref(f.int)
            val unusedFirst = f.ref(f.int)
            val unusedSecond = f.ref(f.int)
            val captures = if (reverse) linkedSetOf(unusedSecond.location.root, used.location.root, unusedFirst.location.root)
            else linkedSetOf(used.location.root, unusedFirst.location.root, unusedSecond.location.root)
            val scope = f.child()
            val region = f.arena.freshRegion()
            val body = f.block(StmtNode.CallbackReturn(region, used.load()), scope = scope)
            val callback = f.arena.inline<Any>(
                f.meta,
                FunctionSignature.of(emptyList(), f.int),
                CallbackId(1, "body"),
                InlineTarget.Body(emptyList(), body, captures, region)
            )
            return f.block(StmtNode.Eval(f.inline(callback)), StmtNode.Assign(unusedFirst, unusedSecond.load()))
        }
        val printer = CodePrinter()
        assertEquals(printer.print(make(1, false)), printer.print(make(2, true)))
    }
}

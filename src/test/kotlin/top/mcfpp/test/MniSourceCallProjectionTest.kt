@file:Suppress("UNUSED_PARAMETER")

package top.mcfpp.test

import top.mcfpp.analysis.CompilerValue
import top.mcfpp.analysis.DeclarationDomainStamp
import top.mcfpp.core.lang.MCInt
import top.mcfpp.mni.NativeCallContext
import top.mcfpp.mni.api.*
import top.mcfpp.mni.expand.*
import top.mcfpp.model.function.FunctionParam
import top.mcfpp.model.function.GenericFunction
import top.mcfpp.model.function.NativeFunction
import top.mcfpp.type.*
import kotlin.test.*
import kotlin.test.Test

class MniSourceCallProjectionTest {
    companion object {
        private var nativeExecutions = 0
        @JvmStatic fun nativeEntry(context: NativeCallContext) {
            nativeExecutions++
            error("Native host executed during projection")
        }
    }

    @Test fun originalDeclarationAliasesReadonlyDefaultsAndNativeDefaultsReachTheDetachedCompleteCall() = SourceProjectionFixture.isolated {
        nativeExecutions = 0
        val declaration = file("projection.declaration")
        val consumer = file("projection.consumer")
        assertTrue(declaration.field.putResolvedAlias("Local", MCFPPBaseType.Int, forced = true))
        assertTrue(consumer.field.putResolvedAlias("Local", MCFPPBaseType.Bool, forced = true))
        consumer.unsolvedImports[declaration.namespace.identifier] = "*"
        consumer.field.importedNamespaceField[declaration.namespace.identifier] = declaration.namespace.scope
        val original = function(declaration, "entry", listOf(UnresolvedType("Local")))
        original.normalParams.single().apply {
            hasDefault = true
            defaultValue = CompilerValue.Integral(11)
            defaultContext = parser("11", "entry-default.mcfpp").value()
        }
        val native = NativeFunction(
            "legacy",
            declaration.namespace.identifier,
            MniSourceCallProjectionTest::class.java.getMethod("nativeEntry", NativeCallContext::class.java)
        ).apply {
            declarationFile = declaration
            returnType = MCFPPBaseType.Int
            normalParams.add(
                FunctionParam(UnresolvedType("Local"), "value", this, hasDefault = true).apply {
                    defaultValue = CompilerValue.Integral(7)
                    defaultContext = parser("7", "native-default.mcfpp").value()
                }
            )
        }
        declaration.namespace.scope.functions["legacy"] = arrayListOf(native)
        val copy = GenericFunction("copy", declaration.namespace.identifier, parser("{}").curlBlock()).apply {
            declarationFile = declaration
            readOnlyParams.add(
                FunctionParam(MCFPPConcreteType.Type, "T", this, hasDefault = true, isReadOnly = true).apply {
                    defaultValue = CompilerValue.TypeValue(MCFPPBaseType.Int.typeId)
                    defaultTypes = mapOf(MCFPPBaseType.Int.typeId to MCFPPBaseType.Int)
                }
            )
            normalParams.add(FunctionParam(MCFPPGenericParamType("T", arrayListOf()), "value", this))
            returnType = UnresolvedType("T")
        }
        declaration.namespace.scope.functions["copy"] = arrayListOf(copy)
        val combine = function(consumer, "combine", listOf(MCFPPBaseType.Int, MCFPPBaseType.Int))
        val caller = caller(consumer)
        caller.scope.vars["value"] = MCInt("caller_value").apply { name = "wrong_caller_default" }
        val result = ready("combine(copy(legacy()),entry())", caller)
        val sentinel = SourceProjectionFixture.Sentinel(caller)
        val plan = session(result).use { session ->
            val environment = SourceProjectionEnvironment.capture(result.environment, session)
            val copyCall = assertIs<BoundExprNode.Call>(assertIs<BoundCallArgument.Value>(result.call.arguments[0]).expression.node).sourceCall
            val entryCall = assertIs<BoundExprNode.Call>(assertIs<BoundCallArgument.Value>(result.call.arguments[1]).expression.node).sourceCall
            assertTrue(environment.binding.type("Local")!!.sameType(environment.types.ref(MCFPPBaseType.Bool)))
            assertTrue(
                environment.declarations.getValue(entryCall.target.declarationIdentity).type("Local")!!
                    .sameType(environment.types.ref(MCFPPBaseType.Int))
            )
            assertEquals(1, copyCall.target.readonlyParameters.size)
            assertTrue(copyCall.readonlyArguments.isEmpty())
            val code = SourceCodeProjector(environment).call(result.call)
            environment.lower(code).also {
                assertEquals(0, nativeExecutions)
                sentinel.unchanged()
                assertTrue(session.transaction.snapshot().resources.isEmpty())
            }
        }
        val calls = operations(plan).filterIsInstance<PlanOperation.Call>()
        assertEquals(listOf("legacy", "copy", "entry", "combine"), calls.map { it.function.name.substringAfter(':') })
        assertTrue(calls.all { it.arguments.size == it.function.parameters.size })
        assertEquals(PlanCallKind.LEGACY_NATIVE, calls[0].function.kind)
        val readonly = assertIs<PlanConstant.TypeValue>(calls[1].readonlyArguments.single().value)
        assertEquals(plan.domain.typeDomains.single(), readonly.type.key.universe)
        assertNotNull(readonly.type.stableSourceId)
        assertEquals("int", readonly.type.name)
        assertTrue(
            operations(plan).filterIsInstance<PlanOperation.Literal>().any {
                it.value == PlanConstant.Integral(11) && it.site.generatedFrom?.file == "entry-default.mcfpp"
            }
        )
        val machine = MniCodeLoweringPlanTest.Machine(plan)
        machine.invokeWithReadonly = { target, constants, arguments ->
            when (target.name.substringAfter(':')) {
                "legacy" -> {
                    assertEquals(listOf(7L), arguments)
                    7L
                }
                "copy" -> {
                    assertEquals("int", (constants.single() as PlanType).name)
                    arguments.single()
                }
                "entry" -> {
                    assertEquals(listOf(11L), arguments)
                    arguments.single()
                }
                "combine" -> (arguments[0] as Long) + (arguments[1] as Long)
                else -> error("Unexpected target")
            }
        }
        assertEquals(18L, machine.run().second)
        assertEquals(0, nativeExecutions)
        assertTrue(listOf(original, native, copy, combine).all { !it.bodyCompiled && it.commands.isEmpty() })
        assertTrue(copy.compiledFunctions.isEmpty())
        sentinel.unchanged()
    }

    @Test fun boundDefaultTemplateReadsTheFirstActualTemporaryAndItsOriginalFreeCapture() = SourceProjectionFixture.isolated {
        val declaration = file("projection.template")
        val consumer = file("projection.templatecaller")
        consumer.unsolvedImports[declaration.namespace.identifier] = "*"
        consumer.field.importedNamespaceField[declaration.namespace.identifier] = declaration.namespace.scope
        val original = function(declaration, "target", listOf(MCFPPBaseType.Int, MCFPPBaseType.Int))
        original.normalParams[1].apply {
            hasDefault = true
            defaultValue = CompilerValue.Integral(0)
        }
        function(consumer, "effect")
        val originalShared = MCInt("shared").apply { name = "declaration_shared" }
        declaration.field.vars["shared"] = originalShared
        val caller = caller(consumer)
        caller.scope.vars["shared"] = MCInt("shared").apply { name = "caller_shared" }
        val prepared = ready("target(effect())", caller)
        val target = prepared.call.target
        val lexical = prepared.environment.declarationEnvironments.getValue(target.declarationIdentity)
        val first = target.normalSymbols[0]
        val shared = assertNotNull(lexical.symbol("shared"))
        val span = SourceSpan("bound-template.mcfpp", 0, 9, 1, 1, 1, 10)
        fun read(symbol: SourceSymbol) = BoundExpr(
            BoundExprNode.Read(
                BoundLocation(
                    symbol,
                    emptyList(),
                    symbol.type,
                    symbol.writable,
                    null,
                    span
                )
            ),
            symbol.type,
            symbol.constant,
            BoundEffect.READ,
            span
        )
        val template = BoundExpr(
            BoundExprNode.Binary(BinaryOperator.ADD, read(first), read(shared)),
            first.type,
            null,
            BoundEffect.READ,
            span
        )
        val withTemplate = SourceFunction(
            target.name, target.function, target.effect, target.readonlyParameters,
            target.defaultedReadonlyParameters, target.defaultedNormalParameters, target.readonlyDefaultExpressions,
            mapOf(1 to template), target.sourceBody, target.readonlySymbols, target.normalSymbols,
            target.preparedReadonly, target.preparedCallSites
        )
        fun replace(environment: BindingEnvironment): BindingEnvironment = BindingEnvironment(
            environment.types,
            environment.symbols.values,
            environment.functions.map { if (it === target) withTemplate else it },
            environment.fields.values,
            environment.sourceText,
            sourceFile = environment.sourceFile
        )
        // This is bound-template evidence. SourceIndex still rejects dynamic composite defaults;
        // the replacement retains the real catalog, compiler handles and declaration identities.
        val environment = CompilerSourceEnvironment(
            replace(prepared.environment.binding),
            prepared.environment.macros,
            prepared.environment.declarationEnvironments.mapValues { replace(it.value) },
            prepared.environment.compilerFunctions,
            prepared.environment.compilerSymbols,
            prepared.environment.fieldCapabilities,
            DeclarationDomainStamp.captureRoots(
                listOf(declaration, consumer, caller) +
                    prepared.environment.compilerFunctions.values + prepared.environment.compilerSymbols.values,
                prepared.call.source
            )
        )
        val result = CompilerCallPreparationResult.Ready(environment, prepared.call.copy(target = withTemplate))
        val sentinel = SourceProjectionFixture.Sentinel(caller)
        lateinit var sourceRoot: SymbolId
        lateinit var callerRoot: SymbolId
        val plan = session(result).use { session ->
            val projection = SourceProjectionEnvironment.capture(environment, session)
            sourceRoot = location(projection.roots.getValue(shared.symbol)).root
            callerRoot = location(projection.roots.getValue(projection.binding.symbol("shared")!!.symbol)).root
            assertNotEquals(sourceRoot, callerRoot)
            projection.lower(SourceCodeProjector(projection).call(result.call)).also { sentinel.unchanged() }
        }
        val machine = MniCodeLoweringPlanTest.Machine(plan)
        machine.storage[MniCodeLoweringPlanTest.Address(sourceRoot)] = 5L
        machine.storage[MniCodeLoweringPlanTest.Address(callerRoot)] = 99L
        machine.invoke = { function, arguments ->
            when (function.name.substringAfter(':')) {
                "effect" -> 7L
                "target" -> {
                    assertEquals(listOf(7L, 12L), arguments)
                    19L
                }
                else -> error("Unexpected target")
            }
        }
        assertEquals(19L, machine.run().second)
        assertEquals(listOf("effect", "target"), machine.calls.map { it.substringAfter(':') })
        assertFalse(original.bodyCompiled)
        sentinel.unchanged()
    }

    @Test fun macroNodesRemainExplicitAndInvalidEvaluationOrderAbortsBeforeAllocation() = SourceProjectionFixture.isolated {
        val file = file("projection.unexpanded")
        file.namespace.declareMniFixtures(MniSourceProjectionTest.Entries::class.java)
        function(file, "effectA")
        function(file, "effectB")
        val caller = caller(file)
        caller.scope.vars["x"] = MCInt("x")
        val result = internalReady("sequence(effectA(),x,effectB(),x)", caller, setOf(1, 3))
        val sentinel = SourceProjectionFixture.Sentinel(caller)
        session(result).use { session ->
            val environment = SourceProjectionEnvironment.capture(result.environment, session)
            val code = SourceCodeProjector(environment).call(result.call)
            val block = assertIs<Block>(code)
            val invocation = assertIs<StmtNode.Eval>(assertIs<ImmutableStmt>(block.statements().last()).node).expression
            assertIs<ExprNode.MacroCall>(assertIs<ImmutableExpr<*>>(invocation).node)
            environment.prevalidate(code)
            val failure = assertFailsWith<ExpansionFailure> { environment.lower(code) }
            assertTrue(failure.message!!.contains("MacroCall remains"))
            sentinel.unchanged()
        }
        session(result).use { session ->
            val environment = SourceProjectionEnvironment.capture(result.environment, session)
            val before = session.budget.usedNodes
            assertTrue(session.transaction.snapshot().resources.isEmpty())
            val invalid = result.call.copy(evaluationOrder = result.call.evaluationOrder.drop(1))
            val failure = assertFailsWith<ExpansionFailure> { SourceCodeProjector(environment).arguments(invalid) }
            assertEquals(result.call.source, failure.diagnostic.source)
            assertTrue(failure.message!!.contains("evaluation order"))
            assertEquals(before, session.budget.usedNodes)
            assertFalse(session.transaction.isOpen)
            sentinel.unchanged()
        }
    }
}

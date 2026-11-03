@file:Suppress("UNUSED_PARAMETER")

package top.mcfpp.test

import top.mcfpp.annotations.MNIMacro
import top.mcfpp.annotations.MacroParam
import top.mcfpp.core.lang.MCInt
import top.mcfpp.mni.api.*
import top.mcfpp.mni.expand.*
import top.mcfpp.type.*
import kotlin.test.*
import kotlin.test.Test

/** Macros whose hosts must never run while the argument binder is exercised. */
class MniBinderConstEntries {
    companion object {
        var executions = 0

        @JvmStatic
        @MNIMacro(
            identifier = "consume",
            params = [
                MacroParam(name = "proof", type = "int", mode = ParamMode.CONST)
            ],
            returnType = "int"
        )
        fun consume(context: MacroContext, proof: Const<IntT>): Expr<IntT> {
            executions++
            error("Macro host executed during argument binding")
        }
    }
}

/** Same-name macros whose only distinguishing data is the canonical parameter signature. */
class MniBinderOverloadEntries {
    companion object {
        @JvmStatic
        @MNIMacro(identifier = "pick", params = [MacroParam(name = "number", type = "int")], returnType = "int")
        fun pickInt(context: MacroContext, number: Value<IntT>): Expr<IntT> = error("Macro host executed during argument binding")

        @JvmStatic
        @MNIMacro(identifier = "pick", params = [MacroParam(name = "flag", type = "bool")], returnType = "int")
        fun pickBool(context: MacroContext, flag: Value<BoolT>): Expr<IntT> = error("Macro host executed during argument binding")
    }
}

class MniArgumentBinderTest {
    @Test fun orderedPreludeEvaluatesEachValueOnceAndKeepsInterleavedCapturedRefAliases() = SourceProjectionFixture.isolated {
        val file = file("binding.references")
        file.namespace.injectedBy(MniSourceProjectionTest.Entries::class.java)
        val effects = listOf("effectA", "first", "second", "effectB").map { function(file, it) }
        val caller = caller(file)
        val nested = MCFPPListType(MCFPPListType(MCFPPBaseType.Int))
        caller.scope.vars["rows"] = nested.buildUnConcrete("rows")
        caller.scope.vars["index"] = MCInt("index")
        val result = internalReady("sequence(effectA(),rows[first()][second()],effectB(),rows[0][1])", caller, setOf(1, 3))
        val sentinel = SourceProjectionFixture.Sentinel(caller)
        lateinit var root: SymbolId
        lateinit var index: SymbolId
        val plan = session(result).use { session ->
            val environment = SourceProjectionEnvironment.capture(result.environment, session)
            root = location(environment.roots.getValue(environment.binding.symbol("rows")!!.symbol)).root
            index = location(environment.roots.getValue(environment.binding.symbol("index")!!.symbol)).root
            val bound = MacroArgumentBinder(environment).bind(result.call)
            assertSame(result.call.target.function, bound.macro.reference)
            assertSame(result.call.source, bound.site)
            assertEquals("sequence", bound.macro.identifier)
            assertEquals(
                listOf(ParamMode.VALUE, ParamMode.REF, ParamMode.VALUE, ParamMode.REF),
                bound.macro.parameters.map { it.mode() }
            )
            assertEquals(4, bound.arguments.size)
            // VALUE inputs are the projected temporaries, not re-evaluated source expressions.
            assertIs<ImmutableValue<*>>(bound.arguments[0])
            assertIs<ImmutableValue<*>>(bound.arguments[2])
            val first = assertIs<Ref<*>>(bound.arguments[1])
            val alias = assertIs<Ref<*>>(bound.arguments[3])
            assertIs<ImmutableRef<*>>(first)
            assertIs<ImmutableRef<*>>(alias)
            val firstLocation = location(first)
            val aliasLocation = location(alias)
            assertEquals(root, firstLocation.root)
            assertEquals(root, aliasLocation.root)
            assertNotSame(firstLocation.capturedBase, aliasLocation.capturedBase)
            assertNotSame(first, alias)

            val meta = session.arena.metadata(
                bound.scope,
                Origin(result.call.source, result.call.source),
                environment.executionContext
            )
            val int = first.type()
            fun add(reference: Ref<*>, amount: Long): Stmt {
                val value = session.arena.expr(meta, int, ExprNode.Literal(ConstantValue.Integral(amount)))
                return session.arena.stmt(
                    meta,
                    StmtNode.Assign(
                        reference,
                        session.arena.expr(meta, int, ExprNode.Binary(BinaryOperator.ADD, reference.load(), value))
                    )
                )
            }
            val region = session.arena.freshRegion()
            val body = session.arena.block(
                meta,
                bound.orderedPrelude.statements() + listOf(
                    add(first, 1),
                    add(alias, 2),
                    session.arena.stmt(meta, StmtNode.Yield(region, alias.load()))
                )
            )
            val code = session.arena.expr(meta, int, ExprNode.ExprBlock(body, int, region))
            environment.lower(code).also { sentinel.unchanged() }
        }
        val selected = MniCodeLoweringPlanTest.Address(root, listOf(0, 1))
        val other = MniCodeLoweringPlanTest.Address(root, listOf(1, 1))
        val indexAddress = MniCodeLoweringPlanTest.Address(index)
        val machine = MniCodeLoweringPlanTest.Machine(plan)
        machine.storage[selected] = 10L
        machine.storage[other] = 40L
        machine.storage[indexAddress] = 0L
        machine.invoke = { target, _ ->
            when (target.name.substringAfter(':')) {
                "effectA" -> 6L
                "first" -> machine.storage[indexAddress]
                "second" -> {
                    machine.storage[indexAddress] = 1L
                    1L
                }
                "effectB" -> {
                    machine.storage[indexAddress] = 2L
                    9L
                }
                else -> error("Unexpected target")
            }
        }
        assertEquals(13L, machine.run().second)
        // Both REF wrappers alias the one physical position captured at their argument position.
        assertEquals(13L, machine.storage[selected])
        assertEquals(40L, machine.storage[other])
        // Every VALUE ran exactly once, in source order, although the host is never invoked.
        assertEquals(listOf("effectA", "first", "second", "effectB"), machine.calls.map { it.substringAfter(':') })
        assertTrue(effects.all { !it.bodyCompiled && it.commands.isEmpty() })
        sentinel.unchanged()
    }

    @Test fun constArgumentsKeepOnlyExistingProofsAndRejectExecutableEffectsBeforeAllocation() = SourceProjectionFixture.isolated {
        MniBinderConstEntries.executions = 0
        val file = file("binding.constants")
        file.namespace.injectedBy(MniBinderConstEntries::class.java)
        function(file, "effect")
        val caller = caller(file)
        // Source selection rejects an effectful expression before a CONST wrapper can exist.
        val rejected = CompilerBindingEnvironment.prepareCall(parser("consume(effect())").functionCall(), caller)
        val diagnostic = assertIs<CompilerCallPreparationResult.Rejected>(rejected).diagnostics.single()
        assertTrue(diagnostic.message.contains("CONST requires a proven constant argument to a macro"))

        val valid = ready("consume(1)", caller)
        // Keep the real parsed proof but declare an executable effect: CONST must still reject it.
        val literal = assertIs<BoundCallArgument.Value>(valid.call.arguments.single()).expression
        val effectExpr = literal.copy(effect = BoundEffect.CALL)
        val invalidArgument = BoundCallArgument.Value(effectExpr, ParamMode.CONST, effectExpr.source)
        val effects = listOf(effectExpr.effect, valid.call.target.effect)
        val invalid = valid.call.copy(
            arguments = listOf(invalidArgument),
            evaluationOrder = listOf(SourceEvaluationArgument.Normal(invalidArgument)),
            effectOrder = effects,
            effect = effects.maxByOrNull { it.ordinal }!!
        )

        session(valid).use { session ->
            val environment = SourceProjectionEnvironment.capture(valid.environment, session)
            val binder = MacroArgumentBinder(environment)
            val before = session.budget.usedNodes
            val failure = assertFailsWith<ExpansionFailure> { binder.bind(invalid) }
            assertEquals(effectExpr.source, failure.diagnostic.source)
            assertTrue(failure.message!!.contains("executable effect"))
            assertEquals(before, session.budget.usedNodes)
            assertFalse(session.transaction.isOpen)
            assertEquals(0, MniBinderConstEntries.executions)
        }

        session(valid).use { session ->
            val environment = SourceProjectionEnvironment.capture(valid.environment, session)
            val bound = MacroArgumentBinder(environment).bind(valid.call)
            assertEquals(ParamMode.CONST, bound.macro.parameters.single().mode())
            val proof = assertIs<ImmutableConst<*>>(bound.arguments.single())
            assertEquals(ConstantValue.Integral(1), proof.value())
            assertSame(valid.call.target.function, bound.macro.reference)
            assertEquals(0, MniBinderConstEntries.executions)
        }
    }

    @Test fun canonicalMacroSelectionUsesThePreparedFunctionKeyRatherThanTheSharedName() = SourceProjectionFixture.isolated {
        val file = file("binding.overloads")
        file.namespace.injectedBy(MniBinderOverloadEntries::class.java)
        val caller = caller(file)
        val result = ready("pick(1)", caller)
        session(result).use { session ->
            val environment = SourceProjectionEnvironment.capture(result.environment, session)
            val resolver = MacroCallResolver(environment)
            val resolved = resolver.resolve(result.call)
            assertSame(resolved, resolver.resolve(result.call.target.function, result.call.source))
            val candidates = environment.macros.namespaces.values.flatMap { it.functions }
                .filter { it.identifier == "pick" }
            assertEquals(2, candidates.size)
            assertSame(result.call.target.function, resolved.reference)
            assertEquals(
                MCFPPBaseType.Int.typeId,
                environment.types.facts(resolved.parameters.single().type()).id
            )
            val other = candidates.single { it.reference !== resolved.reference }
            assertNotEquals(
                (resolved.reference as ImmutableFunctionRef<*>).key,
                (other.reference as ImmutableFunctionRef<*>).key
            )

            val bound = MacroArgumentBinder(environment).bind(result.call)
            assertSame(resolved, bound.macro)
            assertIs<ImmutableValue<*>>(bound.arguments.single())

            // Retargeting the same syntax at the same-name overload must not re-rank arguments.
            val alternative = environment.binding.functions.single { it.function === other.reference }
            val wrong = result.call.copy(target = alternative)
            val failure = assertFailsWith<ExpansionFailure> { MacroArgumentBinder(environment).bind(wrong) }
            assertTrue(failure.message!!.contains("differs from the selected signature"))
        }
    }
}

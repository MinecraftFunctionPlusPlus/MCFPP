package top.mcfpp.test

import top.mcfpp.mni.api.*
import top.mcfpp.mni.expand.*
import java.util.function.Consumer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertNotSame
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class MniBuilderContextFrameTest {
    private class Fixture(id: Long = 1, val access: BuilderAccess = BuilderAccess()) {
        val arena = CodeArena(SessionId(id), ScopeId(SessionId(id), 0)) { _, _ -> }
        val site = SourceSpan("builder-context.mcfpp", 0, 4, 1, 1, 1, 5)
        val origin = Origin(site)
        val int = ImmutableTypeRef<IntT>(TypeKey(990, 1), "int", kind = TypeKind.INT)
        val bool = ImmutableTypeRef<BoolT>(TypeKey(990, 2), "bool", kind = TypeKind.BOOL)
        val float = ImmutableTypeRef<FloatT>(TypeKey(990, 3), "float", kind = TypeKind.FLOAT)
        val string = ImmutableTypeRef<StringT>(TypeKey(990, 4), "string", kind = TypeKind.STRING)
        val void = ImmutableTypeRef<VoidT>(TypeKey(990, 5), "void", kind = TypeKind.VOID)
        val types = object : TypeService {
            override fun intType(): TypeRef<IntT> = int
            override fun boolType(): TypeRef<BoolT> = bool
            override fun floatType(): TypeRef<FloatT> = float
            override fun stringType(): TypeRef<StringT> = string
            override fun voidType(): TypeRef<VoidT> = void
            override fun lookup(name: String): TypeRef<*> = error("No lookup needed")
            override fun function(name: String, argumentTypes: List<TypeRef<*>>): FunctionRef<*> = error("No lookup needed")
        }
        val quote = quote()
        fun quote(
            scope: ScopeId = arena.rootScope,
            context: ExecutionContextId? = null,
            access: BuilderAccess = this.access
        ) = DefaultQuote(arena, types, origin, scope, context, access = access)
        fun storage(): ImmutableRef<IntT> = arena.ref(arena.metadata(arena.rootScope, origin), int, NodeLocation(arena.freshSymbol()))
        fun validate(code: Code, bindings: List<Ref<*>> = emptyList()): ValidatedCode {
            val environment = ValidationEnvironment(arena, bindings, types = listOf(int, bool, float, string, void))
            assertIs<PrevalidatedCode>(CodeValidator().validate(code, environment, ValidationPhase.PRE_EXPANSION))
            return assertIs<ValidatedCode>(CodeValidator().validate(code, environment, ValidationPhase.FINAL))
        }
    }

    private fun frame(builder: CodeBuilder) = (builder as DefaultCodeBuilder).contextFrame()
    private fun metadata(code: Code) = (code as OwnedCode).metadata
    private fun node(statement: Stmt) = (statement as ImmutableStmt).node

    @Test fun registeredDerivedFramesRetainAllThreeIndependentComponents() {
        val f = Fixture()
        val root = f.arena.rootContext(f.arena.rootScope)
        val asScope = f.arena.childScope(f.arena.rootScope)
        val executor = f.arena.executorContext(root, asScope)
        val atScope = f.arena.childScope(asScope)
        val position = f.arena.positionContext(executor, atScope)
        val rotatedScope = f.arena.childScope(atScope)
        val rotation = f.arena.rotationContext(position, rotatedScope)
        val visible = f.arena.childScope(rotatedScope)
        val actual = f.arena.contextFrame(rotation.id, visible, f.site)
        assertSame(rotation, actual)
        assertEquals(executor.id, actual.executor)
        assertEquals(position.id, actual.position)
        assertEquals(rotation.id, actual.rotation)
        assertNotEquals(actual.id, actual.executor)
        assertNotEquals(actual.id, actual.position)
    }

    @Test fun frameLookupRejectsForeignUnknownInvisibleAndClosedCapabilitiesAtTheSource() {
        val f = Fixture()
        val scope = f.arena.childScope(f.arena.rootScope)
        val hidden = f.arena.rootContext(scope)
        val unknown = f.arena.freshExecutionContext()
        for (id in listOf(ExecutionContextId(SessionId(2), hidden.id.value), unknown, hidden.id)) {
            val failure = assertFailsWith<CodeModelException> { f.arena.contextFrame(id, f.arena.rootScope, f.site) }
            assertEquals(f.site, failure.source)
        }
        assertFailsWith<IllegalArgumentException> {
            f.arena.contextFrame(hidden.id, ScopeId(SessionId(2), 0), f.site)
        }
        f.arena.close()
        assertEquals(
            f.site,
            assertFailsWith<CodeModelException> {
                f.arena.contextFrame(hidden.id, scope, f.site)
            }.source
        )
    }

    @Test fun ordinaryQuotesKeepNullContextAndStillValidateAndLowerWithoutAnOptIn() {
        val f = Fixture()
        val value = f.quote.expression(
            f.int,
            Consumer { builder ->
                val local = builder.local(f.int, builder.intLiteral(2))
                builder.yield(builder.add(local.load(), builder.intLiteral(3)))
            }
        )
        assertNull(metadata(value).executionContextId)
        val body = assertIs<ExprNode.ExprBlock>(assertIs<ImmutableExpr<*>>(value).node).block
        assertNull(metadata(body).executionContextId)
        assertTrue(body.statements().all { metadata(it).executionContextId == null })
        assertNull(f.access.rootContextOrNull(f.arena))
        val plan = CodeLowerer().lower(f.validate(value), LoweringTarget(FunctionKey(990, "ordinary")))
        assertTrue(plan.blocks.isNotEmpty())
    }

    @Test fun rootOptInPropagatesToTheBlockAndKeepsEarlierNullNodesValid() {
        val f = Fixture()
        lateinit var actual: ExecutionContextFrame
        lateinit var before: Expr<IntT>
        val body = f.quote.block(
            Consumer { builder ->
                before = builder.intLiteral(1)
                builder.evaluate(before)
                actual = frame(builder)
                assertSame(actual, frame(builder))
                builder.evaluate(builder.intLiteral(2))
            }
        )
        assertNull(metadata(before).executionContextId)
        assertNull(metadata(body.statements()[0]).executionContextId)
        assertEquals(actual.id, metadata(body).executionContextId)
        assertEquals(actual.id, metadata(body.statements()[1]).executionContextId)
        val plan = CodeLowerer().lower(f.validate(body), LoweringTarget(FunctionKey(990, "root"), actual.id))
        assertTrue(plan.blocks.isNotEmpty())
    }

    @Test fun firstActivationInAChildUpdatesItsParentAndLaterSiblingWithoutReplacingEarlierCode() {
        val f = Fixture()
        lateinit var actual: ExecutionContextFrame
        lateinit var before: Expr<IntT>
        val body = f.quote.block(
            Consumer { parent ->
                before = parent.intLiteral(1)
                parent.evaluate(before)
                parent.ifElse(
                    parent.boolLiteral(true),
                    Consumer { child ->
                        actual = frame(child)
                        child.evaluate(child.intLiteral(2))
                    },
                    Consumer { sibling ->
                        assertSame(actual, frame(sibling))
                        sibling.evaluate(sibling.intLiteral(3))
                    }
                )
                assertSame(actual, frame(parent))
                parent.evaluate(parent.intLiteral(4))
            }
        )
        assertNull(metadata(before).executionContextId)
        assertEquals(actual.id, metadata(body).executionContextId)
        val conditional = assertIs<StmtNode.If>(node(body.statements()[1]))
        assertEquals(actual.id, metadata(conditional.thenBlock).executionContextId)
        assertEquals(actual.id, metadata(checkNotNull(conditional.elseBlock)).executionContextId)
        assertEquals(actual.id, metadata(body.statements().last()).executionContextId)
        f.validate(body)
    }

    @Test fun firstActivationInANestedExpressionUpdatesBothExpressionShellsAndResultBlocks() {
        val f = Fixture()
        lateinit var actual: ExecutionContextFrame
        lateinit var nested: Expr<IntT>
        val value = f.quote.expression(
            f.int,
            Consumer { outer ->
                val captured = outer.local(f.int, outer.intLiteral(7))
                nested = f.quote.expression(
                    f.int,
                    Consumer { inner ->
                        actual = frame(inner)
                        inner.yield(captured.load())
                    }
                )
                assertSame(actual, frame(outer))
                outer.yield(nested)
            }
        )
        for (expression in listOf(value, nested)) {
            assertEquals(actual.id, metadata(expression).executionContextId)
            val body = assertIs<ExprNode.ExprBlock>(assertIs<ImmutableExpr<*>>(expression).node).block
            assertEquals(actual.id, metadata(body).executionContextId)
        }
        f.validate(value)
    }

    @Test fun anExplicitDerivedFrameSurvivesBranchesAndNestedQuoteCapture() {
        val f = Fixture()
        val root = f.arena.rootContext(f.arena.rootScope)
        val asScope = f.arena.childScope(f.arena.rootScope)
        val executor = f.arena.executorContext(root, asScope)
        val atScope = f.arena.childScope(asScope)
        val position = f.arena.positionContext(executor, atScope)
        val rotatedScope = f.arena.childScope(atScope)
        val actual = f.arena.rotationContext(position, rotatedScope)
        val quote = f.quote(rotatedScope, actual.id)
        val body = quote.block(
            Consumer { outer ->
                assertSame(actual, frame(outer))
                outer.ifThen(
                    outer.boolLiteral(true),
                    Consumer { child ->
                        assertSame(actual, frame(child))
                        val nested = quote.expression(
                            f.int,
                            Consumer { inner ->
                                assertSame(actual, frame(inner))
                                inner.yield(inner.intLiteral(9))
                            }
                        )
                        assertEquals(actual.id, metadata(nested).executionContextId)
                        child.evaluate(nested)
                    }
                )
            }
        )
        assertEquals(actual.id, metadata(body).executionContextId)
        assertEquals(executor.id, actual.executor)
        assertEquals(position.id, actual.position)
        assertEquals(actual.id, actual.rotation)
        assertNull(f.access.rootContextOrNull(f.arena), "An explicit frame must not allocate an unrelated root")
        f.validate(body)
    }

    @Test fun repeatedAppendMapsTheAmbientFrameAndFreshensLocalsWhilePreservingFreeStorage() {
        val f = Fixture()
        val output = f.storage()
        lateinit var sourceFrame: ExecutionContextFrame
        lateinit var original: Local<IntT>
        val fragment = f.quote.block(
            Consumer { builder ->
                sourceFrame = frame(builder)
                original = builder.local(f.int, builder.intLiteral(1))
                builder.assign(output, original.load())
            }
        )
        val destinationQuote = f.quote(access = BuilderAccess())
        lateinit var destinationFrame: ExecutionContextFrame
        val body = destinationQuote.block(
            Consumer { builder ->
                builder.append(fragment)
                destinationFrame = frame(builder)
                builder.append(fragment)
            }
        )
        assertNotEquals(sourceFrame.id, destinationFrame.id)
        assertEquals(sourceFrame.id, metadata(fragment).executionContextId)
        assertEquals(destinationFrame.id, metadata(body).executionContextId)
        val imported = body.statements().map { assertIs<StmtNode.ScopeBlock>(node(it)).block }
        val locals = imported.map { assertIs<StmtNode.LocalDecl>(node(it.statements()[0])).local as ImmutableLocal<*> }
        assertNotEquals((original as ImmutableLocal<*>).symbol, locals[0].symbol)
        assertNotEquals(locals[0].symbol, locals[1].symbol)
        imported.forEach {
            assertEquals(destinationFrame.id, metadata(it).executionContextId)
            assertSame(output, assertIs<StmtNode.Assign>(node(it.statements()[1])).destination)
        }
        assertNull(output.location.executionContextId)
        f.validate(body, listOf(output))
    }

    @Test fun nullFragmentsRemainValidWhenAppendedAfterFrameActivation() {
        val f = Fixture()
        val fragment = f.quote.block(Consumer { it.evaluate(it.intLiteral(1)) })
        assertNull(metadata(fragment).executionContextId)
        val body = f.quote.block(
            Consumer { builder ->
                frame(builder)
                builder.append(fragment)
                builder.append(fragment)
            }
        )
        assertTrue(body.statements().all { metadata(it).executionContextId == metadata(body).executionContextId })
        body.statements().forEach {
            assertNull(metadata(assertIs<StmtNode.ScopeBlock>(node(it)).block).executionContextId)
        }
        f.validate(body)
    }

    @Test fun aSharedAccessKeepsArenaRootsSeparateAcrossExpansions() {
        val shared = BuilderAccess()
        val first = Fixture(1, shared)
        val second = Fixture(2, shared)
        lateinit var firstFrame: ExecutionContextFrame
        lateinit var secondFrame: ExecutionContextFrame
        val firstBody = first.quote.block(
            Consumer {
                firstFrame = frame(it)
                it.evaluate(it.intLiteral(1))
            }
        )
        val ordinarySecond = second.quote.block(Consumer { it.evaluate(it.intLiteral(2)) })
        assertNull(metadata(ordinarySecond).executionContextId)
        val secondBody = second.quote.block(
            Consumer {
                secondFrame = frame(it)
                it.evaluate(it.intLiteral(3))
            }
        )
        assertNotEquals(firstFrame.id.session, secondFrame.id.session)
        assertNotSame(firstFrame, secondFrame)
        assertSame(firstFrame, shared.rootContextOrNull(first.arena))
        assertSame(secondFrame, shared.rootContextOrNull(second.arena))
        first.validate(firstBody)
        second.validate(secondBody)
    }

    @Test fun inactiveAndClosedBuilderCapabilitiesCannotAcquireAFrame() {
        val f = Fixture()
        lateinit var retained: CodeBuilder
        lateinit var child: CodeBuilder
        f.quote.block(
            Consumer { parent ->
                retained = parent
                parent.ifThen(
                    parent.boolLiteral(true),
                    Consumer { current ->
                        child = current
                        assertFailsWith<CodeModelException> { frame(parent) }
                        frame(current)
                    }
                )
                assertFailsWith<CodeModelException> { frame(child) }
            }
        )
        assertFailsWith<CodeModelException> { frame(retained) }
        assertFailsWith<CodeModelException> { frame(child) }
        f.arena.close()
        assertFailsWith<CodeModelException> { f.quote.block(Consumer { frame(it) }) }
    }

    @Test fun legacyUnregisteredContextStillWorksForOrdinaryCodeButDoesNotGrantAFrame() {
        val f = Fixture()
        val legacy = f.arena.freshExecutionContext()
        val quote = f.quote(context = legacy)
        val fragment = quote.block(Consumer { it.evaluate(it.intLiteral(0)) })
        val body = quote.block(
            Consumer { builder ->
                builder.append(fragment)
                builder.evaluate(builder.intLiteral(1))
                val failure = assertFailsWith<CodeModelException> { frame(builder) }
                assertEquals(f.site, failure.source)
            }
        )
        assertEquals(legacy, metadata(body).executionContextId)
        assertNull(f.access.rootContextOrNull(f.arena))
        f.validate(body)
    }
}

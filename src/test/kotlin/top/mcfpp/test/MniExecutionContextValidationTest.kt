package top.mcfpp.test

import top.mcfpp.mni.api.*
import top.mcfpp.mni.expand.*
import java.util.function.Consumer
import kotlin.test.*
import kotlin.test.Test

class MniExecutionContextValidationTest {
    private class Fixture(id: Long = 610) {
        val int = ImmutableTypeRef<IntT>(TypeKey(610, 1), "int", kind = TypeKind.INT)
        val bool = ImmutableTypeRef<BoolT>(TypeKey(610, 2), "bool", kind = TypeKind.BOOL)
        val float = ImmutableTypeRef<FloatT>(TypeKey(610, 3), "float", kind = TypeKind.FLOAT)
        val string = ImmutableTypeRef<StringT>(TypeKey(610, 4), "string", kind = TypeKind.STRING)
        val void = ImmutableTypeRef<VoidT>(TypeKey(610, 5), "void", kind = TypeKind.VOID)
        val site = SourceSpan("context-caller.mcfpp", 0, 6, 1, 1, 1, 7)
        val generated = SourceSpan("context-body.mcfpp", 10, 16, 2, 1, 2, 7)
        val origin = Origin(site, generated)
        var charged = 0
        val arena = CodeArena(SessionId(id), ScopeId(SessionId(id), 0)) { count, _ -> charged += count }
        val types = object : TypeService {
            override fun intType(): TypeRef<IntT> = int
            override fun boolType(): TypeRef<BoolT> = bool
            override fun floatType(): TypeRef<FloatT> = float
            override fun stringType(): TypeRef<StringT> = string
            override fun voidType(): TypeRef<VoidT> = void
            override fun lookup(name: String): TypeRef<*> = error("No lookup")
            override fun function(name: String, argumentTypes: List<TypeRef<*>>): FunctionRef<*> = error("No lookup")
        }
        val quote = DefaultQuote(arena, types, origin)
        fun build(body: (CodeBuilder) -> Unit) = quote.block(Consumer(body))
        fun meta(scope: ScopeId = arena.rootScope, frame: ExecutionContextFrame? = null) = arena.metadata(scope, origin, frame?.id)
        fun environment(refs: List<Ref<*>> = emptyList(), functions: List<FunctionRef<*>> = emptyList()) = ValidationEnvironment(arena, refs, functions, types = listOf(int, bool, float, string, void))
        fun accept(code: Code, env: ValidationEnvironment = environment()) {
            val before = charged
            for (phase in ValidationPhase.entries) {
                val result = CodeValidator().validate(code, env, phase)
                assertSame(code, result.code)
            }
            assertEquals(before, charged, "Validation must not allocate arena nodes")
        }
        fun reject(code: Code, message: String, env: ValidationEnvironment = environment(), source: SourceSpan? = null) {
            for (phase in ValidationPhase.entries) {
                val error = assertFailsWith<CodeModelException> { CodeValidator().validate(code, env, phase) }
                assertTrue(error.message!!.contains(message), error.message)
                if (source != null) assertEquals(source, error.source)
            }
        }
        fun number(meta: CodeMetadata) = arena.expr(meta, int, ExprNode.Literal(ConstantValue.Integral(1)))
    }

    @Test fun theRealFactoryTreeWithAllOperationsPassesBothPhasesWithoutAllocating() {
        val f = Fixture()
        val tree = f.build { b ->
            // An ordinary local allocated before Minecraft opt-in keeps its stable null binding.
            val global = b.local(f.int, b.intLiteral(0))
            val mc = b.minecraft()
            val one = mc.intLiteral(1)
            val fixed = mc.score(ScoreHolder.named("#hits"), "hits")
            val origin = mc.position(1.0, 2.0, 3.0)
            val rotation = mc.rotation(90.0, 0.0)
            mc.executeAs(mc.entities(EntityQuery.allEntities().withType("minecraft:pig"))) { actor, self ->
                actor.at(actor.position(self)) { placed ->
                    placed.rotated(placed.rotation(self)) { turned ->
                        val step = turned.localOffset(turned.offset(turned.currentPosition(), 0.0, 1.0, 0.0), turned.currentRotation(), 0.0, 0.0, 0.5)
                        val hit = turned.blockMatches(turned.blockAt(step), BlockPredicate.tag("minecraft:logs"))
                        turned.ifThen(hit) { yes ->
                            val hp = yes.minecraft().score(self, "hp")
                            yes.assign(hp, yes.sub(hp.load(), one))
                            yes.assign(fixed, one)
                            yes.assign(global, yes.add(global.load(), one))
                        }
                    }
                }
            }
            mc.at(origin) { placed -> placed.rotated(rotation) { turned -> turned.evaluate(fixed.load()) } }
        }
        f.accept(tree)
    }

    @Test fun sameExecutorEntityScoresAndOrdinaryRefsRemainValidThroughPositionAndRotationChanges() {
        val f = Fixture()
        lateinit var external: Ref<IntT>
        val tree = f.build { b ->
            val mc = b.minecraft()
            mc.executeAs(mc.entities(EntityQuery.allPlayers())) { actor, self ->
                val metadata = (self as OwnedCode).metadata
                external = f.arena.ref(metadata, f.int, NodeLocation(f.arena.freshSymbol(), executionContextId = metadata.executionContextId))
                val hp = actor.score(self, "hp")
                actor.at(actor.position(0.0, 1.0, 2.0)) { placed ->
                    placed.rotated(placed.rotation(20.0, 10.0)) { turned ->
                        turned.assign(external, external.load())
                        turned.assign(hp, hp.load())
                        turned.evaluate(turned.score(self, "hp").load())
                    }
                }
            }
        }
        f.accept(tree, f.environment(listOf(external)))
    }

    @Test fun fixedHolderRefsCanCrossNestedEntityIterationsWithoutChangingTheirAddress() {
        val f = Fixture()
        val tree = f.build { b ->
            val mc = b.minecraft()
            val fixed = mc.score(ScoreHolder.named("#shared"), "shared")
            mc.executeAs(mc.entities(EntityQuery.allPlayers())) { outer, _ ->
                outer.executeAs(outer.entities(EntityQuery.allEntities())) { inner, _ -> inner.assign(fixed, fixed.load()) }
            }
        }
        f.accept(tree)
    }

    @Test fun anExecutorBoundOrdinaryRefCannotBeReinterpretedAfterExecuteAs() {
        val f = Fixture()
        lateinit var ref: Ref<IntT>
        val tree = f.build { b ->
            val mc = b.minecraft()
            val position = mc.currentPosition() as OwnedCode
            ref = f.arena.ref(position.metadata, f.int, NodeLocation(f.arena.freshSymbol(), executionContextId = position.metadata.executionContextId))
            mc.executeAs(mc.entities(EntityQuery.allPlayers())) { actor, _ -> actor.assign(ref, actor.intLiteral(1)) }
        }
        f.reject(tree, "cannot be rebound", f.environment(listOf(ref)), f.site)
    }

    @Test fun anOuterEntityScoreCannotSilentlyBecomeTheInnerExecutorScore() {
        val f = Fixture()
        val tree = f.build { b ->
            val mc = b.minecraft()
            mc.executeAs(mc.entities(EntityQuery.allPlayers())) { outer, self ->
                val hp = outer.score(self, "hp")
                outer.executeAs(outer.entities(EntityQuery.allEntities())) { inner, _ -> inner.assign(hp, hp.load()) }
            }
        }
        f.reject(tree, "cannot cross executeAs", source = f.generated)
    }

    @Test fun entityPositionsAlsoRejectUnstableCrossExecutorReferences() {
        val f = Fixture()
        val tree = f.build { b ->
            val mc = b.minecraft()
            mc.executeAs(mc.entities(EntityQuery.allPlayers())) { outer, self ->
                val position = outer.position(self)
                outer.executeAs(outer.entities(EntityQuery.allEntities())) { inner, _ -> inner.at(position) {} }
            }
        }
        f.reject(tree, "cannot cross executeAs", source = f.generated)
    }

    @Test fun executeAsPreservesExistingCurrentPositionAndRotationComponents() {
        val f = Fixture()
        val tree = f.build { b ->
            val mc = b.minecraft()
            val position = mc.currentPosition()
            val rotation = mc.currentRotation()
            mc.executeAs(mc.entities(EntityQuery.allPlayers())) { actor, _ ->
                val step = actor.localOffset(position, rotation, 0.0, 0.0, 1.0)
                actor.evaluate(actor.blockMatches(actor.blockAt(step), BlockPredicate.block("minecraft:stone")))
            }
        }
        f.accept(tree)
    }

    @Test fun recursivelyCapturedCurrentPositionCannotBeReboundInsideAt() {
        val f = Fixture()
        val tree = f.build { b ->
            val mc = b.minecraft()
            val oldPosition = mc.offset(mc.currentPosition(), 1.0, 0.0, 0.0)
            mc.at(mc.position(1.0, 2.0, 3.0)) { placed ->
                placed.evaluate(placed.blockMatches(placed.blockAt(oldPosition), BlockPredicate.block("minecraft:stone")))
            }
        }
        f.reject(tree, "different position context", source = f.generated)
    }

    @Test fun localOffsetCannotReuseAnObsoleteCurrentRotation() {
        val f = Fixture()
        val tree = f.build { b ->
            val mc = b.minecraft()
            val oldRotation = mc.currentRotation()
            mc.rotated(mc.rotation(90.0, 0.0)) { turned ->
                val step = turned.localOffset(turned.currentPosition(), oldRotation, 0.0, 0.0, 1.0)
                turned.evaluate(turned.blockMatches(turned.blockAt(step), BlockPredicate.block("minecraft:stone")))
            }
        }
        f.reject(tree, "different rotation context", source = f.generated)
    }

    @Test fun absolutePositionsAndRotationsCanBeReusedAcrossAllComponents() {
        val f = Fixture()
        val tree = f.build { b ->
            val mc = b.minecraft()
            val absolute = mc.position(0.0, 64.0, 0.0)
            val rotation = mc.rotation(20.0, 0.0)
            mc.executeAs(mc.entities(EntityQuery.allPlayers())) { actor, _ ->
                actor.at(absolute) { placed ->
                    placed.rotated(rotation) { turned ->
                        val step = turned.localOffset(absolute, rotation, 0.0, 0.0, 1.0)
                        turned.evaluate(turned.blockMatches(turned.blockAt(step), BlockPredicate.block("minecraft:stone")))
                    }
                }
            }
        }
        f.accept(tree)
    }

    @Test fun rawTreesCannotSmuggleEscapedEntityPositionsOutOfTheirScope() {
        val f = Fixture()
        lateinit var escaped: PositionHandle
        lateinit var rootMeta: CodeMetadata
        f.build { b ->
            val mc = b.minecraft()
            rootMeta = (mc.currentPosition() as OwnedCode).metadata
            mc.executeAs(mc.entities(EntityQuery.allPlayers())) { actor, self -> escaped = actor.position(self) }
        }
        val block = ImmutableBlockHandle((escaped as OwnedCode).metadata, escaped)
        val expr = ImmutableExpr(rootMeta, f.bool, ExprNode.BlockMatches(block, BlockPredicate.block("minecraft:stone")))
        f.reject(expr, "outside its lexical scope", source = f.generated)
    }

    @Test fun escapedRotationAndEntityHandlesCannotBeUsedByARawParentContextBody() {
        val f = Fixture()
        lateinit var rotation: RotationHandle
        lateinit var parent: ExecutionContextFrame
        lateinit var parentScope: ScopeId
        f.build { b ->
            val mc = b.minecraft()
            val meta = (mc.currentPosition() as OwnedCode).metadata
            parentScope = meta.scopeId
            parent = f.arena.contextFrame(meta.executionContextId!!, parentScope, f.site)
            mc.executeAs(mc.entities(EntityQuery.allPlayers())) { actor, self -> rotation = actor.rotation(self) }
        }
        val scope = f.arena.childScope(parentScope)
        val frame = f.arena.rotationContext(parent, scope)
        val body = ImmutableBlock(f.meta(scope, frame), emptyList())
        val stmt = ImmutableStmt(f.meta(parentScope, parent), StmtNode.Rotated(rotation, body, parent, frame))
        f.reject(stmt, "outside its lexical scope", source = f.generated)
    }

    @Test fun aFrameWithRealIdsButInventedComponentsIsRejected() {
        val f = Fixture()
        val root = f.arena.rootContext(f.arena.rootScope)
        val scope = f.arena.childScope(f.arena.rootScope)
        val actual = f.arena.executorContext(root, scope)
        val forgedParent = root.copy(position = f.arena.freshExecutionContext())
        val forgedChild = forgedParent.withExecutor(actual.id)
        val meta = f.meta(scope, actual)
        val self = f.arena.entityHandle(meta, actual)
        val targets = f.arena.entitySet(f.meta(frame = root), EntityQuery.allPlayers())
        val stmt = ImmutableStmt(f.meta(frame = root), StmtNode.ExecuteAs(targets, self, ImmutableBlock(meta, emptyList()), forgedParent, forgedChild))
        f.reject(stmt, "lost its parent frame", source = f.generated)
    }

    @Test fun contextFramesMustBeRegisteredAtTheirActualBodyScope() {
        val f = Fixture()
        val root = f.arena.rootContext(f.arena.rootScope)
        val declarationScope = f.arena.childScope(f.arena.rootScope)
        val frame = f.arena.executorContext(root, declarationScope)
        val scope = f.arena.childScope(declarationScope)
        val meta = f.meta(scope, frame)
        val self = f.arena.entityHandle(meta, frame)
        val targets = f.arena.entitySet(f.meta(frame = root), EntityQuery.allPlayers())
        val stmt = ImmutableStmt(f.meta(frame = root), StmtNode.ExecuteAs(targets, self, ImmutableBlock(meta, emptyList()), root, frame))
        f.reject(stmt, "declared outside its context body", source = f.generated)
    }

    @Test fun unregisteredIdsCannotForgeMinecraftFrames() {
        val f = Fixture()
        val root = ExecutionContextFrame.root(f.arena.freshExecutionContext())
        val child = root.withExecutor(f.arena.freshExecutionContext())
        val scope = f.arena.childScope(f.arena.rootScope)
        val meta = f.meta(scope, child)
        val self = ImmutableEntityHandle(meta, child.executor)
        val targets = ImmutableEntitySet(f.meta(frame = root), EntityQuery.allPlayers())
        val stmt = ImmutableStmt(f.meta(frame = root), StmtNode.ExecuteAs(targets, self, ImmutableBlock(meta, emptyList()), root, child))
        f.reject(stmt, "was not allocated", source = f.generated)
    }

    @Test fun foreignHandlesAndRuntimeEntitySetsCannotEscapeAsGeneratedValues() {
        val f = Fixture(620)
        val other = Fixture(621)
        val frame = other.arena.rootContext(other.arena.rootScope)
        val meta = other.meta(frame = frame)
        val block = other.arena.blockHandle(meta, other.arena.position(meta, PositionNode.Absolute(0.0, 0.0, 0.0)))
        val expr = ImmutableExpr(f.meta(), f.bool, ExprNode.BlockMatches(block, BlockPredicate.block("minecraft:stone")))
        f.reject(expr, "another session", source = f.generated)
        val targets = f.arena.entitySet(f.meta(), EntityQuery.allPlayers())
        f.reject(targets, "cannot escape", source = f.site)
    }

    @Test fun actualScoreAndBlockResultTypesAreCheckedEvenWhenConstructedByHand() {
        val f = Fixture()
        val target = MinecraftScoreTarget.Fixed(ScoreHolder.named("#fixed"))
        f.reject(ImmutableExpr(f.meta(), f.bool, ExprNode.ScoreRead(target, "hp")), "actual int", source = f.site)
        val block = f.arena.blockHandle(f.meta(), f.arena.position(f.meta(), PositionNode.Absolute(0.0, 0.0, 0.0)))
        f.reject(ImmutableExpr(f.meta(), f.int, ExprNode.BlockMatches(block, BlockPredicate.block("minecraft:stone"))), "actual bool", source = f.site)
    }

    @Test fun entityIterationWithOnlyAChildYieldDoesNotProduceARequiredExpressionResult() {
        val f = Fixture()
        val root = f.arena.rootContext(f.arena.rootScope)
        val scope = f.arena.childScope(f.arena.rootScope)
        val child = f.arena.executorContext(root, scope)
        val region = f.arena.freshRegion()
        val childMeta = f.meta(scope, child)
        val self = f.arena.entityHandle(childMeta, child)
        val body = f.arena.block(childMeta, listOf(f.arena.stmt(childMeta, StmtNode.Yield(region, f.number(childMeta)))))
        val targets = f.arena.entitySet(f.meta(frame = root), EntityQuery.allPlayers())
        val stmt = f.arena.stmt(f.meta(frame = root), StmtNode.ExecuteAs(targets, self, body, root, child))
        val outer = f.arena.block(f.meta(frame = root), listOf(stmt))
        val expr = f.arena.expr(f.meta(frame = root), f.int, ExprNode.ExprBlock(outer, f.int, region))
        f.reject(expr, "normal exit without yield", source = f.site)
    }

    @Test fun positionAndRotationBodiesPropagateYieldsAndEntityIterationsAllowAFallbackYield() {
        val f = Fixture()
        val at = f.quote.expression(
            f.int,
            Consumer { b ->
                val mc = b.minecraft()
                mc.at(mc.currentPosition()) { placed -> placed.rotated(placed.currentRotation()) { turned -> turned.yield(turned.intLiteral(7)) } }
            }
        )
        f.accept(at)
        val asWithFallback = f.quote.expression(
            f.int,
            Consumer { b ->
                val mc = b.minecraft()
                mc.executeAs(mc.entities(EntityQuery.allPlayers())) { actor, _ -> actor.yield(actor.intLiteral(1)) }
                b.yield(b.intLiteral(2))
            }
        )
        f.accept(asWithFallback)
    }

    @Test fun aContextChangeDoesNotRebindAnEffectfulFunctionCallCreatedEarlier() {
        val f = Fixture()
        val function = ImmutableFunctionRef(FunctionKey(610, "effect"), "effect", FunctionSignature.of(emptyList(), f.int), FunctionKind.ORDINARY)
        val tree = f.build { b ->
            val mc = b.minecraft()
            val call = b.call(function, emptyList())
            mc.at(mc.position(1.0, 2.0, 3.0)) { placed -> placed.evaluate(call) }
        }
        f.reject(tree, "another execution context", f.environment(functions = listOf(function)), f.site)
    }

    private fun rejectHiddenRegisteredFrame(
        f: Fixture,
        scope: ScopeId,
        root: ExecutionContextFrame,
        hidden: ExecutionContextFrame
    ) {
        val before = f.charged
        assertTrue(f.arena.hasContextFrame(hidden.id, f.generated))
        val visibility = assertFailsWith<CodeModelException> { f.arena.contextFrame(hidden.id, scope, f.generated) }
        assertTrue(visibility.message!!.contains("outside its lexical scope"))
        assertEquals(before, f.charged, "Registration lookup must not allocate")

        // All three rejected trees are made by the real factories, including the cached Read.
        val bad = f.arena.metadata(scope, f.origin, hidden.id)
        val good = f.meta(scope, root)
        val reference = f.arena.ref(bad, f.int, NodeLocation(f.arena.freshSymbol(), executionContextId = hidden.id))
        val assignment = f.arena.stmt(good, StmtNode.Assign(reference, f.number(good)))
        f.reject(assignment, "outside its lexical scope", f.environment(listOf(reference)), f.generated)
        f.reject(reference.load(), "outside its lexical scope", f.environment(listOf(reference)), f.generated)
        val literal = f.arena.expr(bad, f.int, ExprNode.Literal(ConstantValue.Integral(1)))
        f.reject(literal, "outside its lexical scope", source = f.generated)
    }

    @Test fun childRegisteredFramesCannotBecomeOpaqueInRootFactoryReferencesReadsOrLiterals() {
        val f = Fixture()
        val root = f.arena.rootContext(f.arena.rootScope)
        val childScope = f.arena.childScope(f.arena.rootScope)
        val hidden = f.arena.executorContext(root, childScope)
        rejectHiddenRegisteredFrame(f, f.arena.rootScope, root, hidden)
    }

    @Test fun registeredFramesCannotBecomeOpaqueInSiblingFactoryReferencesReadsOrLiterals() {
        val f = Fixture()
        val root = f.arena.rootContext(f.arena.rootScope)
        val owner = f.arena.childScope(f.arena.rootScope)
        val sibling = f.arena.childScope(f.arena.rootScope)
        val hidden = f.arena.executorContext(root, owner)
        rejectHiddenRegisteredFrame(f, sibling, root, hidden)
    }

    @Test fun theRegistrationQueryRejectsForeignAndClosedArenasWithoutAllocating() {
        val f = Fixture(640)
        val other = Fixture(641)
        val registered = f.arena.rootContext(f.arena.rootScope)
        val opaque = f.arena.freshExecutionContext()
        val foreign = other.arena.rootContext(other.arena.rootScope)
        val before = f.charged
        assertTrue(f.arena.hasContextFrame(registered.id, f.generated))
        assertFalse(f.arena.hasContextFrame(opaque, f.generated))
        val foreignFailure = assertFailsWith<CodeModelException> { f.arena.hasContextFrame(foreign.id, f.generated) }
        assertTrue(foreignFailure.message!!.contains("another session"))
        assertEquals(f.generated, foreignFailure.source)
        f.arena.close()
        val closed = assertFailsWith<CodeModelException> { f.arena.hasContextFrame(registered.id, f.generated) }
        assertTrue(closed.message!!.contains("closed"))
        assertEquals(f.generated, closed.source)
        assertEquals(before, f.charged)
    }

    @Test fun opaqueLegacyContextsKeepBothTheirPositiveAndNegativeChecks() {
        val f = Fixture()
        val first = f.arena.freshExecutionContext()
        val second = f.arena.freshExecutionContext()
        val firstMeta = f.arena.metadata(f.arena.rootScope, f.origin, first)
        val secondMeta = f.arena.metadata(f.arena.rootScope, f.origin, second)
        val ref = f.arena.ref(firstMeta, f.int, NodeLocation(f.arena.freshSymbol(), executionContextId = first))
        f.accept(ref.load(), f.environment(listOf(ref)))
        val moved = ImmutableExpr(secondMeta, f.int, ExprNode.Read(ref.location))
        f.reject(moved, "cannot be rebound", f.environment(listOf(ref)), f.site)
        val literal = f.arena.expr(firstMeta, f.int, ExprNode.Literal(ConstantValue.Integral(1)))
        val block = ImmutableBlock(secondMeta, listOf(ImmutableStmt(secondMeta, StmtNode.Eval(literal))))
        f.reject(block, "another execution context", source = f.site)
    }

    @Test fun actualHelperRefArgumentsAcceptScoresWithoutInventingNodeLocations() {
        val f = Fixture()
        val helper = ImmutableFunctionRef(
            FunctionKey(610, "helper"),
            "helper",
            FunctionSignature.of(listOf(ParameterView.of("value", f.int, ParamMode.REF)), f.int),
            FunctionKind.HELPER
        )
        lateinit var fixed: Ref<IntT>
        val tree = f.build { b ->
            val mc = b.minecraft()
            fixed = mc.score(ScoreHolder.named("#fixed"), "hp")
            mc.executeAs(mc.entities(EntityQuery.allPlayers())) { actor, self ->
                actor.at(actor.position(self)) { placed ->
                    placed.evaluate(placed.call(helper, listOf(CallArgument.ref(fixed))))
                    placed.evaluate(placed.call(helper, listOf(CallArgument.ref(placed.score(self, "hp")))))
                }
            }
        }
        f.accept(tree, f.environment(listOf(fixed), listOf(helper)))
    }

    @Test fun capturedOriginalAddressesRemainUsableAfterPositionChangesWithTheSameExecutor() {
        val f = Fixture()
        val root = f.arena.rootContext(f.arena.rootScope)
        val meta = f.meta(frame = root)
        val original = f.arena.ref(meta, f.int, NodeLocation(f.arena.freshSymbol(), executionContextId = root.id))
        val address = f.arena.freshAddress()
        val capture = f.arena.stmt(meta, StmtNode.CaptureAddress(address, original.location, f.int))
        val captured = f.arena.ref(meta, f.int, NodeLocation(original.location.root, executionContextId = root.id, capturedBase = address))
        val scope = f.arena.childScope(f.arena.rootScope)
        val child = f.arena.positionContext(root, scope)
        val body = f.arena.block(f.meta(scope, child), listOf(f.arena.stmt(f.meta(scope, child), StmtNode.Assign(captured, captured.load()))))
        val at = f.arena.stmt(meta, StmtNode.At(f.arena.position(meta, PositionNode.Absolute(0.0, 0.0, 0.0)), body, root, child))
        f.accept(f.arena.block(meta, listOf(capture, at)), f.environment(listOf(original)))
    }

    @Test fun executorCompatibilityDoesNotGrantWriteAccessToReadonlyReferences() {
        val f = Fixture()
        val root = f.arena.rootContext(f.arena.rootScope)
        val meta = f.meta(frame = root)
        val readonly = f.arena.ref(meta, f.int, NodeLocation(f.arena.freshSymbol(), executionContextId = root.id, writable = false))
        val scope = f.arena.childScope(f.arena.rootScope)
        val child = f.arena.rotationContext(root, scope)
        val body = f.arena.block(f.meta(scope, child), listOf(f.arena.stmt(f.meta(scope, child), StmtNode.Assign(readonly, readonly.load()))))
        val stmt = f.arena.stmt(meta, StmtNode.Rotated(f.arena.rotation(meta, RotationNode.Absolute(0.0, 0.0)), body, root, child))
        f.reject(stmt, "read-only", f.environment(listOf(readonly)), f.site)
    }

    @Test fun closedArenasAndForeignRootSessionsRemainRejected() {
        val f = Fixture(630)
        val tree = f.build { b -> b.minecraft().evaluate(b.intLiteral(1)) }
        f.arena.close()
        f.reject(tree, "closed", source = f.site)
        val other = Fixture(631)
        other.reject(tree, "another expansion session", source = f.site)
    }
}

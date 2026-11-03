package top.mcfpp.test

import top.mcfpp.mni.api.*
import top.mcfpp.mni.expand.CodeArena
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertNotSame
import kotlin.test.assertSame
import kotlin.test.assertTrue

class MniContextArenaTest {
    private val callSite = SourceSpan("context-call.mcfpp", 1, 5, 1, 2, 1, 6)
    private val generated = SourceSpan("context-generated.mcfpp", 20, 30, 3, 1, 3, 11)
    private val origin = Origin(callSite, generated)
    private val intType = ImmutableTypeRef<IntT>(TypeKey(870, 1), "int", kind = TypeKind.INT)
    private val boolType = ImmutableTypeRef<BoolT>(TypeKey(870, 2), "bool", kind = TypeKind.BOOL)
    private fun newArena(id: Long = 1, charge: (Int, Origin) -> Unit = { _, _ -> }): CodeArena = CodeArena(SessionId(id), ScopeId(SessionId(id), 0), charge)
    private fun meta(arena: CodeArena, scope: ScopeId, frame: ExecutionContextFrame) = arena.metadata(scope, origin, frame.id)
    private fun statement(code: Stmt) = (code as ImmutableStmt).node

    private data class Tree(
        val block: ImmutableBlock,
        val root: ExecutionContextFrame,
        val self: ImmutableEntityHandle,
        val position: ImmutablePositionHandle,
        val rotation: ImmutableRotationHandle,
        val fixed: ImmutableMinecraftScoreRef,
        val external: ImmutableRef<IntT>
    )

    private fun nested(arena: CodeArena): Tree {
        val root = arena.rootContext(arena.rootScope)
        val rootMeta = meta(arena, arena.rootScope, root)
        val targets = arena.entitySet(rootMeta, EntityQuery.allPlayers())
        val position = arena.position(rootMeta, PositionNode.Current(root.position))
        val rotation = arena.rotation(rootMeta, RotationNode.Current(root.rotation))
        val fixed = arena.scoreRef(rootMeta, intType, MinecraftScoreTarget.Fixed(ScoreHolder.named("#hits")), "hits")
        val external = arena.ref(rootMeta, intType, NodeLocation(arena.freshSymbol(), executionContextId = root.id))
        val asScope = arena.childScope(arena.rootScope)
        val asFrame = arena.executorContext(root, asScope)
        val self = arena.entityHandle(meta(arena, asScope, asFrame), asFrame)
        val atScope = arena.childScope(asScope)
        val atFrame = arena.positionContext(asFrame, atScope)
        val rotatedScope = arena.childScope(atScope)
        val rotatedFrame = arena.rotationContext(atFrame, rotatedScope)
        val rotatedMeta = meta(arena, rotatedScope, rotatedFrame)
        val entityPosition = arena.position(rotatedMeta, PositionNode.Entity(self))
        val entityRotation = arena.rotation(rotatedMeta, RotationNode.Entity(self))
        val offset = arena.position(rotatedMeta, PositionNode.LocalOffset(entityPosition, entityRotation, 0.0, 0.0, 0.5))
        val block = arena.blockHandle(rotatedMeta, offset)
        val matches = arena.expr(rotatedMeta, boolType, ExprNode.BlockMatches(block, BlockPredicate.tag("minecraft:logs")))
        val score = arena.scoreRef(rotatedMeta, intType, MinecraftScoreTarget.Entity(self), "hp")
        val rotatedBody = arena.block(
            rotatedMeta,
            listOf(
                arena.stmt(rotatedMeta, StmtNode.Eval(matches)),
                arena.stmt(rotatedMeta, StmtNode.Assign(score, score.load())),
                arena.stmt(rotatedMeta, StmtNode.Assign(external, fixed.load()))
            )
        )
        val rotated = arena.stmt(meta(arena, atScope, atFrame), StmtNode.Rotated(rotation, rotatedBody, atFrame, rotatedFrame))
        val atBody = arena.block(meta(arena, atScope, atFrame), listOf(rotated))
        val at = arena.stmt(meta(arena, asScope, asFrame), StmtNode.At(position, atBody, asFrame, atFrame))
        val asBody = arena.block(meta(arena, asScope, asFrame), listOf(at))
        val executeAs = arena.stmt(rootMeta, StmtNode.ExecuteAs(targets, self, asBody, root, asFrame))
        return Tree(arena.block(rootMeta, listOf(executeAs)), root, self, position, rotation, fixed, external)
    }

    @Test fun contextFactoriesKeepExecutorPositionAndRotationIndependent() {
        val arena = newArena()
        val root = arena.rootContext(arena.rootScope)
        val asScope = arena.childScope(arena.rootScope)
        val asFrame = arena.executorContext(root, asScope)
        val atScope = arena.childScope(asScope)
        val atFrame = arena.positionContext(asFrame, atScope)
        val rotated = arena.rotationContext(atFrame, arena.childScope(atScope))
        assertEquals(asFrame.id, asFrame.executor)
        assertEquals(root.position, asFrame.position)
        assertEquals(root.rotation, asFrame.rotation)
        assertEquals(asFrame.executor, atFrame.executor)
        assertEquals(atFrame.id, atFrame.position)
        assertEquals(root.rotation, atFrame.rotation)
        assertEquals(atFrame.executor, rotated.executor)
        assertEquals(atFrame.position, rotated.position)
        assertEquals(rotated.id, rotated.rotation)
        assertEquals(4, setOf(root.id, asFrame.id, atFrame.id, rotated.id).size)
        assertFailsWith<IllegalArgumentException> { arena.executorContext(root, arena.rootScope) }
    }

    @Test fun everyPositionAndRotationShapeHasScannedOwnedDependencies() {
        val arena = newArena()
        val root = arena.rootContext(arena.rootScope)
        val metadata = meta(arena, arena.rootScope, root)
        val self = arena.entityHandle(metadata, root)
        val absolute = arena.position(metadata, PositionNode.Absolute(1.0, 2.0, 3.0))
        val current = arena.position(metadata, PositionNode.Current(root.position))
        val entity = arena.position(metadata, PositionNode.Entity(self))
        val world = arena.position(metadata, PositionNode.Offset(current, 0.0, 1.0, 2.0))
        val rotations = listOf(
            arena.rotation(metadata, RotationNode.Absolute(90.0, -20.0)),
            arena.rotation(metadata, RotationNode.Current(root.rotation)),
            arena.rotation(metadata, RotationNode.Entity(self))
        )
        val local = arena.position(metadata, PositionNode.LocalOffset(entity, rotations.last(), 1.0, 2.0, 3.0))
        for (position in listOf(absolute, current, entity, world, local)) {
            val block = arena.blockHandle(metadata, position)
            val matches = arena.expr(metadata, boolType, ExprNode.BlockMatches(block, BlockPredicate.block("minecraft:stone")))
            val imported = arena.importTree(matches, arena.rootScope)
            assertSame(block, assertIs<ExprNode.BlockMatches>(imported.node).block)
            assertEquals(generated, imported.source())
        }
        assertSame(current, assertIs<PositionNode.Offset>(world.node).base)
        assertSame(rotations.last(), assertIs<PositionNode.LocalOffset>(local.node).rotation)
    }

    @Test fun repeatedImportsFreshenAllInternalContextsSelfAndScopesWithoutRebindingCaptures() {
        val arena = newArena()
        val tree = nested(arena)
        val first = arena.importTree(tree.block, arena.rootScope)
        val second = arena.importTree(tree.block, arena.rootScope)
        val left = assertIs<StmtNode.ExecuteAs>(statement(first.statements().single()))
        val right = assertIs<StmtNode.ExecuteAs>(statement(second.statements().single()))
        assertNotSame(tree.self, left.self)
        assertNotSame(left.self, right.self)
        assertNotEquals(left.context.id, right.context.id)
        assertNotEquals((left.body as OwnedCode).metadata.scopeId, (right.body as OwnedCode).metadata.scopeId)
        for (asNode in listOf(left, right)) {
            assertEquals(tree.root, asNode.parentContext)
            assertEquals(tree.root.position, asNode.context.position)
            assertEquals(tree.root.rotation, asNode.context.rotation)
            val at = assertIs<StmtNode.At>(statement(asNode.body.statements().single()))
            val rotated = assertIs<StmtNode.Rotated>(statement(at.body.statements().single()))
            assertSame(tree.position, at.position)
            assertSame(tree.rotation, rotated.rotation)
            assertEquals(asNode.context.executor, at.context.executor)
            assertEquals(at.context.position, rotated.context.position)
            assertEquals(asNode.context.executor, rotated.context.executor)
            val assignments = rotated.body.statements().drop(1).map { assertIs<StmtNode.Assign>(statement(it)) }
            val score = assertIs<ImmutableMinecraftScoreRef>(assignments.first().destination)
            assertSame(asNode.self, assertIs<MinecraftScoreTarget.Entity>(score.target).entity)
            assertSame(score.load(), assignments.first().expression)
            assertSame(tree.external, assignments.last().destination)
            assertSame(tree.fixed.load(), assignments.last().expression)
            assertEquals(tree.root.id, tree.external.location.executionContextId)
            val matches = assertIs<ExprNode.BlockMatches>(
                assertIs<ImmutableExpr<*>>(
                    assertIs<StmtNode.Eval>(statement(rotated.body.statements().first())).expression
                ).node
            )
            val local = assertIs<PositionNode.LocalOffset>(assertIs<ImmutablePositionHandle>(assertIs<ImmutableBlockHandle>(matches.block).position).node)
            assertSame(asNode.self, assertIs<PositionNode.Entity>(assertIs<ImmutablePositionHandle>(local.base).node).entity)
            assertSame(asNode.self, assertIs<RotationNode.Entity>(assertIs<ImmutableRotationHandle>(local.rotation).node).entity)
        }
    }

    @Test fun importedContextLocalsAndCapturedAddressesAreFreshButExternalStorageKeepsItsContext() {
        val arena = newArena()
        val root = arena.rootContext(arena.rootScope)
        val rootMeta = meta(arena, arena.rootScope, root)
        val external = arena.ref(rootMeta, intType, NodeLocation(arena.freshSymbol(), executionContextId = root.id))
        val targets = arena.entitySet(rootMeta, EntityQuery.allEntities())
        val childScope = arena.childScope(arena.rootScope)
        val child = arena.executorContext(root, childScope)
        val metadata = meta(arena, childScope, child)
        val self = arena.entityHandle(metadata, child)
        val index = arena.local(metadata, intType, "index")
        val zero = arena.expr(metadata, intType, ExprNode.Literal(ConstantValue.Integral(0)))
        val address = arena.freshAddress()
        val capture = arena.stmt(
            metadata,
            StmtNode.CaptureAddress(
                address,
                NodeLocation(external.location.root, listOf(LocationStep.CapturedIndex(index.symbol)), root.id),
                intType
            )
        )
        val captured = arena.ref(
            metadata,
            intType,
            NodeLocation(
                external.location.root,
                listOf(LocationStep.Index(2)),
                root.id,
                capturedBase = address
            )
        )
        val body = arena.block(
            metadata,
            listOf(
                arena.stmt(metadata, StmtNode.LocalDecl(index, zero)),
                capture,
                arena.stmt(metadata, StmtNode.Assign(captured, captured.load()))
            )
        )
        val code = arena.stmt(rootMeta, StmtNode.ExecuteAs(targets, self, body, root, child))
        val copies = List(2) { assertIs<StmtNode.ExecuteAs>(arena.importTree(code, arena.rootScope).node) }
        val declarations = copies.map { assertIs<StmtNode.LocalDecl>(statement(it.body.statements()[0])).local as ImmutableLocal<*> }
        val captures = copies.map { assertIs<StmtNode.CaptureAddress>(statement(it.body.statements()[1])) }
        assertNotEquals(declarations[0].symbol, declarations[1].symbol)
        assertNotEquals(captures[0].address, captures[1].address)
        copies.forEachIndexed { i, node ->
            assertEquals(declarations[i].symbol, assertIs<LocationStep.CapturedIndex>(captures[i].location.steps.single()).symbol)
            assertEquals(external.location.root, captures[i].location.root)
            assertEquals(root.id, captures[i].location.executionContextId)
            val location = assertIs<ImmutableRef<*>>(assertIs<StmtNode.Assign>(statement(node.body.statements()[2])).destination).location
            assertEquals(captures[i].address, location.capturedBase)
            assertEquals(listOf(LocationStep.Index(2)), location.steps)
            assertEquals(root.id, location.executionContextId)
        }
    }

    @Test fun scoreCachedReadsAreChargedOnceAndClonesShareTheCopiedRead() {
        var charged = 0
        val arena = newArena { count, _ -> charged += count }
        val root = arena.rootContext(arena.rootScope)
        val rootMeta = meta(arena, arena.rootScope, root)
        val targets = arena.entitySet(rootMeta, EntityQuery.allPlayers())
        val scope = arena.childScope(arena.rootScope)
        val child = arena.executorContext(root, scope)
        val metadata = meta(arena, scope, child)
        val self = arena.entityHandle(metadata, child)
        val score = arena.scoreRef(metadata, intType, MinecraftScoreTarget.Entity(self), "hp")
        repeat(10) { assertSame(score.load(), score.load()) }
        val body = arena.block(metadata, listOf(arena.stmt(metadata, StmtNode.Assign(score, score.load()))))
        val code = arena.block(rootMeta, listOf(arena.stmt(rootMeta, StmtNode.ExecuteAs(targets, self, body, root, child))))
        assertEquals(7, charged)
        repeat(2) { iteration ->
            val imported = arena.importTree(code, arena.rootScope)
            assertEquals(7 + 6 * (iteration + 1), charged)
            val asNode = assertIs<StmtNode.ExecuteAs>(statement(imported.statements().single()))
            val assign = assertIs<StmtNode.Assign>(statement(asNode.body.statements().single()))
            assertSame(assign.destination.load(), assign.expression)
        }
    }

    @Test fun invalidDependenciesDoNotConsumeTheNodeBudget() {
        var charged = 0
        val arena = newArena { count, _ -> charged += count }
        val root = arena.rootContext(arena.rootScope)
        val metadata = meta(arena, arena.rootScope, root)
        val foreign = newArena(2)
        val foreignRoot = foreign.rootContext(foreign.rootScope)
        val position = foreign.position(meta(foreign, foreign.rootScope, foreignRoot), PositionNode.Absolute(0.0, 0.0, 0.0))
        assertFailsWith<IllegalArgumentException> { arena.position(metadata, PositionNode.Offset(position, 1.0, 0.0, 0.0)) }
        assertFailsWith<IllegalArgumentException> { arena.scoreRef(metadata, intType, MinecraftScoreTarget.Fixed(ScoreHolder.named("#hits")), "bad objective") }
        @Suppress("UNCHECKED_CAST")
        val lie = boolType as TypeRef<IntT>
        assertFailsWith<IllegalArgumentException> { arena.scoreRef(metadata, lie, MinecraftScoreTarget.Fixed(ScoreHolder.named("#hits")), "hits") }
        assertEquals(0, charged)
    }

    @Test fun scopedEntityPositionRotationAndScoreHandlesCannotEscapeTheirBody() {
        val arena = newArena()
        val root = arena.rootContext(arena.rootScope)
        val scope = arena.childScope(arena.rootScope)
        val child = arena.executorContext(root, scope)
        val childMeta = meta(arena, scope, child)
        val self = arena.entityHandle(childMeta, child)
        val position = arena.position(childMeta, PositionNode.Entity(self))
        val rotation = arena.rotation(childMeta, RotationNode.Entity(self))
        val score = arena.scoreRef(childMeta, intType, MinecraftScoreTarget.Entity(self), "hp")
        val rootMeta = meta(arena, arena.rootScope, root)
        val actions: List<() -> Unit> = listOf(
            { arena.position(rootMeta, PositionNode.Entity(self)) },
            { arena.position(rootMeta, PositionNode.Offset(position, 0.0, 0.0, 1.0)) },
            { arena.position(rootMeta, PositionNode.LocalOffset(arena.position(rootMeta, PositionNode.Absolute(0.0, 0.0, 0.0)), rotation, 0.0, 0.0, 1.0)) },
            { arena.stmt(rootMeta, StmtNode.Eval(score.load())) },
            { arena.blockHandle(rootMeta, position) }
        )
        for (action in actions) {
            val failure = assertFailsWith<CodeModelException>(block = action)
            assertEquals(generated, failure.source)
            assertTrue(failure.message!!.contains("scope"))
        }
    }

    @Test fun unregisteredFramesCannotBeLaunderedThroughRawContextNodes() {
        val arena = newArena()
        val root = arena.rootContext(arena.rootScope)
        val rootMeta = meta(arena, arena.rootScope, root)
        val scope = arena.childScope(arena.rootScope)
        val unregistered = root.withExecutor(arena.freshExecutionContext())
        val childMeta = meta(arena, scope, unregistered)
        val self = ImmutableEntityHandle(childMeta, unregistered.executor)
        val rawBody = ImmutableBlock(childMeta, emptyList())
        val targets = arena.entitySet(rootMeta, EntityQuery.allEntities())
        val raw = ImmutableStmt(rootMeta, StmtNode.ExecuteAs(targets, self, rawBody, root, unregistered))
        val failure = assertFailsWith<CodeModelException> { arena.importTree(raw, arena.rootScope) }
        assertEquals(generated, failure.source)
        assertTrue(failure.message!!.contains("not allocated"))
        assertFailsWith<CodeModelException> { arena.position(childMeta, PositionNode.Current(unregistered.id)) }
    }

    @Test fun rawFixedScoreImportsEnrollItsCachedReadAndRetainTheActualAddress() {
        var charged = 0
        val arena = newArena { count, _ -> charged += count }
        val metadata = arena.metadata(arena.rootScope, origin)
        val target = MinecraftScoreTarget.Fixed(ScoreHolder.named("#fixed"))
        val raw = ImmutableMinecraftScoreRef(metadata, intType, target, "value")
        val code = ImmutableStmt(metadata, StmtNode.Assign(raw, raw.load()))
        val imported = arena.importTree(code, arena.rootScope)
        val assignment = assertIs<StmtNode.Assign>(imported.node)
        val score = assertIs<ImmutableMinecraftScoreRef>(assignment.destination)
        assertNotSame(raw, score)
        assertSame(target, score.target)
        assertSame(score.load(), assignment.expression)
        assertEquals(2, charged)
        assertEquals(generated, score.source())
    }

    @Test fun generatedOriginsSurviveCloningAndAnExplicitImportOriginOnlyChangesCopiedNodes() {
        val arena = newArena()
        val tree = nested(arena)
        val replacement = Origin(SourceSpan("import.mcfpp", 1, 2, 1, 2, 1, 3), callSite)
        val copy = arena.importTree(tree.block, arena.rootScope, replacement)
        val node = assertIs<StmtNode.ExecuteAs>(statement(copy.statements().single()))
        assertSame(replacement, copy.metadata.origin)
        assertEquals(callSite, node.self.source())
        assertSame(replacement, (node.self as OwnedCode).metadata.origin)
        assertSame(tree.position, assertIs<StmtNode.At>(statement(node.body.statements().single())).position)
        assertEquals(generated, tree.position.source())
        assertSame(origin, tree.position.metadata.origin)
    }

    @Test fun childImportsRequireExplicitAmbientFramesAndStorageBindings() {
        val target = newArena()
        val targetRoot = target.rootContext(target.rootScope)
        val targetMeta = meta(target, target.rootScope, targetRoot)
        val targetRef = target.ref(targetMeta, intType, NodeLocation(target.freshSymbol(), executionContextId = targetRoot.id))
        val child = target.childArena(SessionId(2), ScopeId(SessionId(2), 0))
        val tree = nested(child)
        assertFailsWith<IllegalArgumentException> {
            target.importChildTree(
                child,
                tree.block,
                target.rootScope,
                externalBindings = mapOf(tree.external.location.root to targetRef)
            )
        }
        assertFailsWith<IllegalArgumentException> {
            target.importChildTree(
                child,
                tree.block,
                target.rootScope,
                contextBindings = mapOf(tree.root.id to targetRoot)
            )
        }
        val code = target.importChildTree(
            child,
            tree.block,
            target.rootScope,
            externalBindings = mapOf(tree.external.location.root to targetRef),
            contextBindings = mapOf(tree.root.id to targetRoot)
        )
        val execute = assertIs<StmtNode.ExecuteAs>(statement(code.statements().single()))
        assertEquals(targetRoot, execute.parentContext)
        assertEquals(target.sessionId, execute.context.id.session)
        val at = assertIs<StmtNode.At>(statement(execute.body.statements().single()))
        val rotated = assertIs<StmtNode.Rotated>(statement(at.body.statements().single()))
        val assignment = assertIs<StmtNode.Assign>(statement(rotated.body.statements().last()))
        assertSame(targetRef, assignment.destination)
        assertEquals(targetRoot.id, targetRef.location.executionContextId)
        assertEquals(targetRoot.position, assertIs<PositionNode.Current>(assertIs<ImmutablePositionHandle>(at.position).node).context)
    }

    @Test fun frameBindingsRejectUnknownForeignForgedAndConflictingSameRoleComponents() {
        val arena = newArena()
        val root = arena.rootContext(arena.rootScope)
        val tree = nested(arena)
        val code = tree.block
        val unknown = ExecutionContextId(arena.sessionId, 999)
        assertFailsWith<CodeModelException> { arena.importTree(code, arena.rootScope, contextBindings = mapOf(unknown to root)) }
        val forged = ExecutionContextFrame(root.id, tree.root.executor, root.position, root.rotation)
        assertFailsWith<CodeModelException> { arena.importTree(code, arena.rootScope, contextBindings = mapOf(tree.root.id to forged)) }
        val foreign = newArena(3)
        val foreignFrame = foreign.rootContext(foreign.rootScope)
        assertFailsWith<CodeModelException> { arena.importTree(code, arena.rootScope, contextBindings = mapOf(tree.root.id to foreignFrame)) }
        assertFailsWith<CodeModelException> {
            arena.importChildTree(
                foreign,
                nested(foreign).block,
                arena.rootScope,
                contextBindings = mapOf(foreignFrame.id to root)
            )
        }
        // Both source frames share one position; two targets cannot give that position conflicting bindings.
        val asNode = assertIs<StmtNode.ExecuteAs>(statement(tree.block.statements().single()))
        val targetScope = arena.childScope(arena.rootScope)
        val targetAt = arena.positionContext(root, targetScope)
        assertFailsWith<IllegalArgumentException> {
            arena.importTree(
                asNode.body,
                targetScope,
                contextBindings = linkedMapOf(tree.root.id to root, asNode.context.id to targetAt)
            )
        }
    }

    @Test fun aRootFrameCanBindToAnAmbientFrameWithThreeIndependentComponentsWithoutRebindingStorage() {
        val target = newArena()
        val root = target.rootContext(target.rootScope)
        val asScope = target.childScope(target.rootScope)
        val asFrame = target.executorContext(root, asScope)
        val atScope = target.childScope(asScope)
        val atFrame = target.positionContext(asFrame, atScope)
        val rotatedScope = target.childScope(atScope)
        val ambient = target.rotationContext(atFrame, rotatedScope)
        val originalAddress = target.ref(
            meta(target, target.rootScope, root),
            intType,
            NodeLocation(target.freshSymbol(), executionContextId = root.id)
        )
        val child = target.childArena(SessionId(2), ScopeId(SessionId(2), 0))
        val tree = nested(child)
        val childMeta = meta(child, child.rootScope, tree.root)
        val childSelf = child.entityHandle(childMeta, tree.root)
        val childScore = child.scoreRef(childMeta, intType, MinecraftScoreTarget.Entity(childSelf), "ambient")
        val code = child.block(childMeta, listOf(child.stmt(childMeta, StmtNode.Assign(childScore, childScore.load()))) + tree.block.statements())
        val imported = target.importChildTree(
            child,
            code,
            rotatedScope,
            externalBindings = mapOf(tree.external.location.root to originalAddress),
            contextBindings = mapOf(tree.root.id to ambient)
        )
        assertEquals(ambient.id, imported.metadata.executionContextId)
        val score = assertIs<ImmutableMinecraftScoreRef>(assertIs<StmtNode.Assign>(statement(imported.statements()[0])).destination)
        val importedSelf = assertIs<ImmutableEntityHandle>(assertIs<MinecraftScoreTarget.Entity>(score.target).entity)
        assertEquals(ambient.id, importedSelf.metadata.executionContextId)
        assertEquals(asFrame.executor, importedSelf.executor)
        assertEquals(rotatedScope, importedSelf.metadata.scopeId)
        val execute = assertIs<StmtNode.ExecuteAs>(statement(imported.statements()[1]))
        assertEquals(ambient, execute.parentContext)
        assertEquals(atFrame.position, execute.context.position)
        assertEquals(ambient.rotation, execute.context.rotation)
        assertNotEquals(ambient.executor, execute.context.executor)
        val at = assertIs<StmtNode.At>(statement(execute.body.statements().single()))
        val rotated = assertIs<StmtNode.Rotated>(statement(at.body.statements().single()))
        val position = assertIs<ImmutablePositionHandle>(at.position)
        val rotation = assertIs<ImmutableRotationHandle>(rotated.rotation)
        assertEquals(ambient.id, position.metadata.executionContextId)
        assertEquals(atFrame.position, assertIs<PositionNode.Current>(position.node).context)
        assertEquals(ambient.id, rotation.metadata.executionContextId)
        assertEquals(ambient.rotation, assertIs<RotationNode.Current>(rotation.node).context)
        val assignment = assertIs<StmtNode.Assign>(statement(rotated.body.statements().last()))
        assertSame(originalAddress, assignment.destination)
        assertEquals(root.id, originalAddress.location.executionContextId)
    }

    @Test fun closedOrForeignArenaHandlesCannotBeImportedBySupplyingBindings() {
        val target = newArena()
        val child = target.childArena(SessionId(2), ScopeId(SessionId(2), 0))
        val childRoot = child.rootContext(child.rootScope)
        val childMeta = meta(child, child.rootScope, childRoot)
        val position = child.position(childMeta, PositionNode.Current(childRoot.position))
        val targetRoot = target.rootContext(target.rootScope)
        child.close()
        assertFailsWith<CodeModelException> {
            target.importChildTree(
                child,
                position,
                target.rootScope,
                contextBindings = mapOf(childRoot.id to targetRoot)
            )
        }
        target.close()
        assertFailsWith<CodeModelException> { target.entitySet(meta(target, target.rootScope, targetRoot), EntityQuery.allEntities()) }
    }

    @Test fun helperRefParametersBindToTheSameFixedScoreAddressAndCachedReadWithoutExtraAllocation() {
        var charged = 0
        val target = newArena { count, _ -> charged += count }
        val targetMeta = target.metadata(target.rootScope, origin)
        val holder = ScoreHolder.named("#helper_result")
        val score = target.scoreRef(targetMeta, intType, MinecraftScoreTarget.Fixed(holder), "result")
        val child = target.childArena(SessionId(2), ScopeId(SessionId(2), 0))
        val childMeta = child.metadata(child.rootScope, origin)
        val parameter = child.ref(childMeta, intType, NodeLocation(child.freshSymbol()))
        val alias = child.ref(childMeta, intType, NodeLocation(child.freshSymbol()))
        val body = child.block(childMeta, listOf(child.stmt(childMeta, StmtNode.Assign(parameter, alias.load()))))
        val before = charged
        val bound = target.importChildTree(
            child,
            parameter as Ref<IntT>,
            target.rootScope,
            externalBindings = mapOf(parameter.location.root to score)
        )
        assertSame(score, bound)
        assertSame(score.load(), bound.load())
        assertEquals(before, charged, "Binding a whole reference does not allocate another score read")
        repeat(2) { iteration ->
            val imported = target.importChildTree(
                child,
                body,
                target.rootScope,
                externalBindings = mapOf(parameter.location.root to score, alias.location.root to score)
            )
            val assignment = assertIs<StmtNode.Assign>(statement(imported.statements().single()))
            assertSame(score, assignment.destination)
            assertSame(score.load(), assignment.expression)
            assertSame(holder, assertIs<MinecraftScoreTarget.Fixed>(assertIs<ImmutableMinecraftScoreRef>(assignment.destination).target).holder)
            assertEquals("result", assertIs<ExprNode.ScoreRead>(assertIs<ImmutableExpr<*>>(assignment.expression).node).objective)
            assertEquals(before + 2 * (iteration + 1), charged, "Only the assignment and block allocate nodes")
        }
    }

    @Test fun scoreParameterAddressesRemainCapturedWhenAmbientExecutorPositionAndRotationAreRebound() {
        val target = newArena()
        val root = target.rootContext(target.rootScope)
        val selfScope = target.childScope(target.rootScope)
        val selfFrame = target.executorContext(root, selfScope)
        val self = target.entityHandle(meta(target, selfScope, selfFrame), selfFrame)
        val entityScore = target.scoreRef(meta(target, selfScope, selfFrame), intType, MinecraftScoreTarget.Entity(self), "hp")
        val fixedScore = target.scoreRef(
            meta(target, target.rootScope, root),
            intType,
            MinecraftScoreTarget.Fixed(ScoreHolder.named("#saved")),
            "value"
        )
        val atScope = target.childScope(selfScope)
        val at = target.positionContext(selfFrame, atScope)
        val rotatedScope = target.childScope(atScope)
        val rotated = target.rotationContext(at, rotatedScope)
        val ambientScope = target.childScope(rotatedScope)
        val ambient = target.executorContext(rotated, ambientScope)
        val child = target.childArena(SessionId(2), ScopeId(SessionId(2), 0))
        val childFrame = child.rootContext(child.rootScope)
        val childMeta = meta(child, child.rootScope, childFrame)
        val parameter = child.ref(childMeta, intType, NodeLocation(child.freshSymbol(), executionContextId = childFrame.id))
        val body = child.block(childMeta, listOf(child.stmt(childMeta, StmtNode.Assign(parameter, parameter.load()))))
        for (score in listOf(entityScore, fixedScore)) {
            val imported = target.importChildTree(
                child,
                body,
                ambientScope,
                externalBindings = mapOf(parameter.location.root to score),
                contextBindings = mapOf(childFrame.id to ambient)
            )
            val assignment = assertIs<StmtNode.Assign>(statement(imported.statements().single()))
            assertEquals(ambient.id, imported.metadata.executionContextId)
            assertEquals(ambient.id, (imported.statements().single() as OwnedCode).metadata.executionContextId)
            assertSame(score, assignment.destination)
            assertSame(score.load(), assignment.expression)
            assertEquals(score.metadata.executionContextId, (assignment.expression as OwnedCode).metadata.executionContextId)
        }
        assertSame(self, assertIs<MinecraftScoreTarget.Entity>(entityScore.target).entity)
        assertEquals(selfFrame.id, entityScore.metadata.executionContextId)
        assertEquals(selfFrame.executor, self.executor)
        assertNotEquals(ambient.executor, self.executor)
        assertEquals(root.id, fixedScore.metadata.executionContextId)
    }

    @Test fun readOnlyParameterReadsCanUseScoresButTheirReferenceCapabilityCannotBecomeWritable() {
        var charged = 0
        val target = newArena { count, _ -> charged += count }
        val score = target.scoreRef(
            target.metadata(target.rootScope, origin),
            intType,
            MinecraftScoreTarget.Fixed(ScoreHolder.named("#read_only")),
            "value"
        )
        val child = target.childArena(SessionId(2), ScopeId(SessionId(2), 0))
        val parameter = child.ref(
            child.metadata(child.rootScope, origin),
            intType,
            NodeLocation(child.freshSymbol(), writable = false)
        )
        val before = charged
        val failure = assertFailsWith<CodeModelException> {
            target.importChildTree(
                child,
                parameter as Ref<IntT>,
                target.rootScope,
                externalBindings = mapOf(parameter.location.root to score)
            )
        }
        assertEquals(generated, failure.source)
        assertTrue(failure.message!!.contains("read-only reference capability"))
        assertEquals(before, charged)
        val read = target.importChildTree(
            child,
            parameter.load(),
            target.rootScope,
            externalBindings = mapOf(parameter.location.root to score)
        )
        assertSame(score.load(), read)
        assertEquals(before, charged, "Binding a read uses the existing cached score read")
    }

    @Test fun scoreSubstitutionRejectsSuffixCapturedAddressAndIndexSymbolWithoutInventingLocations() {
        var charged = 0
        val target = newArena { count, _ -> charged += count }
        val score = target.scoreRef(
            target.metadata(target.rootScope, origin),
            intType,
            MinecraftScoreTarget.Fixed(ScoreHolder.named("#address")),
            "value"
        )
        val child = target.childArena(SessionId(2), ScopeId(SessionId(2), 0))
        val metadata = child.metadata(child.rootScope, origin)
        val parameter = child.ref(metadata, intType, NodeLocation(child.freshSymbol()))
        val suffix = child.ref(metadata, intType, NodeLocation(parameter.location.root, listOf(LocationStep.Index(0))))
        val address = child.freshAddress()
        val capture = child.stmt(metadata, StmtNode.CaptureAddress(address, parameter.location, intType))
        val captured = child.ref(metadata, intType, NodeLocation(parameter.location.root, capturedBase = address))
        val other = child.ref(metadata, intType, NodeLocation(child.freshSymbol()))
        val indexed = child.expr(
            metadata,
            intType,
            ExprNode.Read(
                NodeLocation(
                    other.location.root,
                    listOf(LocationStep.CapturedIndex(parameter.location.root))
                )
            )
        )
        val destination = target.ref(target.metadata(target.rootScope, origin), intType, NodeLocation(target.freshSymbol()))
        val before = charged
        for (code in listOf<Code>(suffix, captured, capture, indexed)) {
            val failure = assertFailsWith<CodeModelException> {
                target.importChildTree(
                    child,
                    code,
                    target.rootScope,
                    externalBindings = mapOf(parameter.location.root to score, other.location.root to destination)
                )
            }
            assertEquals(generated, failure.source)
            assertTrue(failure.message!!.contains("score reference", ignoreCase = true), failure.message)
        }
        assertEquals(before, charged, "Rejected score addresses allocate no cloned nodes")
        assertEquals("value", score.objective)
    }

    @Test fun scoreIndexAndKeyBindingsAreRejectedBeforeAValidPrefixCanSpendImportBudget() {
        for (key in listOf(false, true)) for (zeroBudget in listOf(false, true)) {
            var importing = false
            var importCharges = 0
            var charged = 0
            val target = newArena { count, at ->
                if (importing) {
                    importCharges += count
                    if (zeroBudget) throw CodeModelException(at.generatedFrom ?: at.callSite, "Import budget exhausted")
                }
                charged += count
            }
            val targetMeta = target.metadata(target.rootScope, origin)
            val score = target.scoreRef(targetMeta, intType, MinecraftScoreTarget.Fixed(ScoreHolder.named("#index")), "value")
            val destination = target.ref(targetMeta, intType, NodeLocation(target.freshSymbol()))
            val child = target.childArena(SessionId(2), ScopeId(SessionId(2), 0))
            val metadata = child.metadata(child.rootScope, origin)
            val parameter = child.freshSymbol()
            val other = child.freshSymbol()
            val step = if (key) LocationStep.CapturedKey(parameter) else LocationStep.CapturedIndex(parameter)
            val indexed = child.expr(metadata, intType, ExprNode.Read(NodeLocation(other, listOf(step))))
            val prefix = child.stmt(
                metadata,
                StmtNode.Eval(
                    child.expr(
                        metadata,
                        intType,
                        ExprNode.Literal(ConstantValue.Integral(1))
                    )
                )
            )
            val body = child.block(metadata, listOf(prefix, child.stmt(metadata, StmtNode.Eval(indexed))))
            val prefixOnly = child.block(metadata, listOf(prefix))
            val before = charged
            importing = true
            val failure = assertFailsWith<CodeModelException> {
                target.importChildTree(
                    child,
                    body,
                    target.rootScope,
                    externalBindings = mapOf(parameter to score, other to destination)
                )
            }
            assertEquals(generated, failure.source)
            assertEquals("A score reference cannot replace a captured index or key symbol at $generated", failure.message)
            assertEquals(0, importCharges, "The complete tree must be prepared before cloning its valid prefix")
            assertEquals(before, charged)
            if (zeroBudget) {
                val budget = assertFailsWith<CodeModelException> {
                    target.importChildTree(child, prefixOnly, target.rootScope)
                }
                assertEquals("Import budget exhausted at $generated", budget.message)
                assertEquals(1, importCharges, "The same budget hook must reject a valid allocation")
            } else {
                val imported = target.importChildTree(child, prefixOnly, target.rootScope)
                assertEquals(1, imported.statements().size)
                assertEquals(3, importCharges, "A valid literal, evaluation and block each consume one node")
                assertEquals(before + 3, charged)
            }
        }
    }

    @Test fun scoreBindingsCheckActualParameterTypesBeforeCloningOrChargingNodes() {
        var charged = 0
        val target = newArena { count, _ -> charged += count }
        val score = target.scoreRef(
            target.metadata(target.rootScope, origin),
            intType,
            MinecraftScoreTarget.Fixed(ScoreHolder.named("#typed")),
            "value"
        )
        val child = target.childArena(SessionId(2), ScopeId(SessionId(2), 0))
        val parameter = child.ref(child.metadata(child.rootScope, origin), boolType, NodeLocation(child.freshSymbol()))
        val before = charged
        val failure = assertFailsWith<CodeModelException> {
            target.importChildTree(
                child,
                parameter as Ref<BoolT>,
                target.rootScope,
                externalBindings = mapOf(parameter.location.root to score)
            )
        }
        assertEquals(generated, failure.source)
        assertTrue(failure.message!!.contains("another type"))
        assertEquals(before, charged)
    }
}

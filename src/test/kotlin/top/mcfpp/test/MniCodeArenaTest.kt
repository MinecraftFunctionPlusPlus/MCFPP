package top.mcfpp.test

import top.mcfpp.mni.api.*
import top.mcfpp.mni.expand.CodeArena
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue

class MniCodeArenaTest {
    private val intType = ImmutableTypeRef<IntT>(TypeKey(1, 1), "int", kind = TypeKind.INT)
    private val boolType = ImmutableTypeRef<BoolT>(TypeKey(1, 2), "bool", kind = TypeKind.BOOL)
    private val origin = Origin(SourceSpan.unknown())

    private fun newArena(id: Long = 1, charge: (Int, Origin) -> Unit = { _, _ -> }): CodeArena {
        val session = SessionId(id)
        return CodeArena(session, ScopeId(session, 0), charge)
    }

    private fun literal(arena: CodeArena, scope: ScopeId = arena.rootScope, value: Long = 1): ImmutableExpr<IntT> = arena.expr(arena.metadata(scope, origin), intType, ExprNode.Literal(ConstantValue.Integral(value)))

    @Test
    fun cachedLoadsConsumeBudgetOnceAndScopeEscapeIsRejected() {
        var count = 0
        val arena = newArena { amount, reportedOrigin ->
            assertSame(origin, reportedOrigin)
            if (count + amount > 3) throw CodeModelException(reportedOrigin.callSite, "Node budget exhausted")
            count += amount
        }
        val meta = arena.metadata(arena.rootScope, origin)
        val local = arena.local(meta, intType, "index")
        val reference = arena.ref(meta, intType, NodeLocation(arena.freshSymbol()))
        repeat(5) {
            assertSame(local.load(), local.load())
            assertSame(reference.load(), reference.load())
        }
        val number = literal(arena)
        arena.constant(number, ConstantValue.Integral(1))
        arena.value(number)
        assertEquals(3, count)
        assertFailsWith<CodeModelException> { literal(arena) }

        val scoped = newArena()
        val child = scoped.childScope(scoped.rootScope)
        val hidden = scoped.local(scoped.metadata(child, origin), intType, "hidden")
        assertFailsWith<CodeModelException> {
            scoped.stmt(scoped.metadata(scoped.rootScope, origin), StmtNode.Eval(hidden.load()))
        }
    }

    @Test
    fun repeatedImportsRenameLocalsAndPreserveFreeStorageAndFunctionBindings() {
        var count = 0
        val arena = newArena { amount, _ -> count += amount }
        val meta = arena.metadata(arena.rootScope, origin)
        val external = arena.ref(meta, intType, NodeLocation(arena.freshSymbol()))
        val boundValue = arena.value(external.load())
        val local = arena.local(meta, intType, "index")
        val declaration = arena.stmt(meta, StmtNode.LocalDecl(local, literal(arena)))
        val sum = arena.expr(meta, intType, ExprNode.Binary(BinaryOperator.ADD, local.load(), boundValue.expr()))
        val assign = arena.stmt(meta, StmtNode.Assign(external, sum))
        val function = ImmutableFunctionRef(
            FunctionKey(1, "consume"),
            "consume",
            FunctionSignature.of(emptyList(), intType),
            FunctionKind.ORDINARY
        )
        val call = arena.expr(
            meta,
            intType,
            ExprNode.Call(
                function,
                listOf(CallArgument.value(local.load()), CallArgument.ref(external))
            )
        )
        val body = arena.block(meta, listOf(declaration, assign, arena.stmt(meta, StmtNode.Eval(call))))
        val before = count
        val first = arena.importTree(body, arena.rootScope)
        assertEquals(8, count - before)
        val second = arena.importTree(body, arena.rootScope)
        assertEquals(16, count - before)
        fun clonedLocal(block: Block) = ((block.statements()[0] as ImmutableStmt).node as StmtNode.LocalDecl).local as ImmutableLocal<*>
        val firstLocal = clonedLocal(first)
        assertNotEquals(local.symbol, firstLocal.symbol)
        assertNotEquals(firstLocal.symbol, clonedLocal(second).symbol)
        val clonedAssign = (first.statements()[1] as ImmutableStmt).node as StmtNode.Assign
        assertSame(external, clonedAssign.destination)
        val clonedSum = (clonedAssign.expression as ImmutableExpr<*>).node as ExprNode.Binary
        assertSame(firstLocal.load(), clonedSum.left)
        assertSame(boundValue.expr(), clonedSum.right)
        val clonedCall = (((first.statements()[2] as ImmutableStmt).node as StmtNode.Eval).expression as ImmutableExpr<*>).node as ExprNode.Call
        assertSame(function, clonedCall.function)
        assertSame(firstLocal.load(), (clonedCall.arguments[0].payload as ArgumentPayload.Value).expression)
        assertSame(external, (clonedCall.arguments[1].payload as ArgumentPayload.Reference).reference)
    }

    @Test
    fun ancestorValueExpressionsRemainBoundWhenImportingANestedTree() {
        val arena = newArena()
        val rootMeta = arena.metadata(arena.rootScope, origin)
        val number = literal(arena)
        val expression = arena.expr(rootMeta, intType, ExprNode.Binary(BinaryOperator.ADD, number, number))
        val boundValue = arena.value(expression)
        val scope = arena.childScope(arena.rootScope)
        val meta = arena.metadata(scope, origin)
        val body = arena.block(meta, listOf(arena.stmt(meta, StmtNode.Eval(boundValue.expr()))))
        val imported = arena.importTree(body, arena.rootScope)
        val evaluated = (imported.statements().single() as ImmutableStmt).node as StmtNode.Eval
        assertSame(boundValue.expr(), evaluated.expression)
    }

    @Test
    fun nestedLocationsRemapCapturedSymbolsAndKeepTheirLexicalScope() {
        val arena = newArena()
        val rootMeta = arena.metadata(arena.rootScope, origin)
        val external = arena.ref(rootMeta, intType, NodeLocation(arena.freshSymbol()))
        val nested = arena.childScope(arena.rootScope)
        val meta = arena.metadata(nested, origin)
        val index = arena.local(meta, intType, "index")
        val selected = arena.ref(
            meta,
            intType,
            NodeLocation(
                external.location.root,
                listOf(LocationStep.CapturedIndex(index.symbol), LocationStep.CapturedKey(index.symbol))
            )
        )
        val inner = arena.block(
            meta,
            listOf(
                arena.stmt(meta, StmtNode.LocalDecl(index, literal(arena, nested))),
                arena.stmt(meta, StmtNode.Assign(selected, selected.load()))
            )
        )
        val body = arena.block(rootMeta, listOf(arena.stmt(rootMeta, StmtNode.ScopeBlock(inner))))
        val imported = arena.importTree(body, arena.rootScope)
        val clonedInner = ((imported.statements()[0] as ImmutableStmt).node as StmtNode.ScopeBlock).block as ImmutableBlock
        assertNotEquals(nested, clonedInner.metadata.scopeId)
        assertEquals(arena.rootScope, arena.scopeParent(clonedInner.metadata.scopeId))
        val clonedIndex = ((clonedInner.statements()[0] as ImmutableStmt).node as StmtNode.LocalDecl).local as ImmutableLocal<*>
        val assignment = (clonedInner.statements()[1] as ImmutableStmt).node as StmtNode.Assign
        val clonedSelection = assignment.destination as ImmutableRef<*>
        assertEquals(clonedInner.metadata.scopeId, clonedSelection.metadata.scopeId)
        assertEquals(external.location.root, clonedSelection.location.root)
        assertEquals(listOf(LocationStep.CapturedIndex(clonedIndex.symbol), LocationStep.CapturedKey(clonedIndex.symbol)), clonedSelection.location.steps)
        assertSame(clonedSelection.load(), assignment.expression)
    }

    @Test
    fun childImportRequiresExplicitBoundaryAndMappingOfFreeBindings() {
        val parent = newArena()
        val child = parent.childArena(SessionId(2), ScopeId(SessionId(2), 0))
        val parentMeta = parent.metadata(parent.rootScope, origin)
        val childMeta = child.metadata(child.rootScope, origin)
        val parentRef = parent.ref(parentMeta, intType, NodeLocation(parent.freshSymbol()))
        val childRef = child.ref(childMeta, intType, NodeLocation(child.freshSymbol()))
        val body = child.block(childMeta, listOf(child.stmt(childMeta, StmtNode.Assign(childRef, literal(child)))))
        assertFailsWith<CodeModelException> { parent.importTree(body, parent.rootScope) }
        assertFailsWith<IllegalArgumentException> { parent.importChildTree(child, body, parent.rootScope) }
        val imported = parent.importChildTree(
            child,
            body,
            parent.rootScope,
            externalBindings = mapOf(childRef.location.root to parentRef)
        )
        val assign = (imported.statements()[0] as ImmutableStmt).node as StmtNode.Assign
        assertSame(parentRef, assign.destination)
        child.close()
        assertEquals(parent.sessionId, (assign.expression as OwnedCode).metadata.sessionId)
        assertFailsWith<CodeModelException> {
            parent.importChildTree(
                child,
                body,
                parent.rootScope,
                externalBindings = mapOf(childRef.location.root to parentRef)
            )
        }
        val foreign = newArena(3)
        assertFailsWith<CodeModelException> { parent.importChildTree(foreign, literal(foreign), parent.rootScope) }
    }

    @Test
    fun explicitStorageMappingPreservesReadOnlyViewsAndChecksExecutionContext() {
        var count = 0
        val parent = newArena { amount, _ -> count += amount }
        val child = parent.childArena(SessionId(2), ScopeId(SessionId(2), 0))
        val parentMeta = parent.metadata(parent.rootScope, origin)
        val childMeta = child.metadata(child.rootScope, origin)
        val destination = parent.ref(parentMeta, intType, NodeLocation(parent.freshSymbol()))
        val readOnly = child.ref(childMeta, intType, NodeLocation(child.freshSymbol(), writable = false))
        val before = count
        val view = parent.importChildTree(
            child,
            readOnly,
            parent.rootScope,
            externalBindings = mapOf(readOnly.location.root to destination)
        )
        assertEquals(false, view.location.writable)
        assertEquals(destination.location.root, view.location.root)
        assertTrue(view !== destination)
        assertSame(view.load(), view.load())
        assertEquals(1, count - before)

        val firstContext = parent.freshExecutionContext()
        val secondContext = parent.freshExecutionContext()
        fun contextual(context: ExecutionContextId) = parent.ref(
            parent.metadata(parent.rootScope, origin, context),
            intType,
            NodeLocation(parent.freshSymbol(), executionContextId = context)
        )
        val original = contextual(firstContext)
        val compatible = contextual(firstContext)
        val incompatible = contextual(secondContext)
        assertSame(
            compatible,
            parent.importTree(
                original,
                parent.rootScope,
                externalBindings = mapOf(original.location.root to compatible)
            )
        )
        assertFailsWith<IllegalArgumentException> {
            parent.importTree(
                original,
                parent.rootScope,
                externalBindings = mapOf(original.location.root to incompatible)
            )
        }
        assertFailsWith<IllegalArgumentException> {
            parent.importTree(
                original,
                parent.rootScope,
                externalBindings = mapOf(original.location.root to destination)
            )
        }
        val childContext = child.freshExecutionContext()
        val contextualChild = child.ref(
            child.metadata(child.rootScope, origin, childContext),
            intType,
            NodeLocation(child.freshSymbol(), executionContextId = childContext)
        )
        assertFailsWith<IllegalArgumentException> {
            parent.importChildTree(
                child,
                contextualChild,
                parent.rootScope,
                externalBindings = mapOf(contextualChild.location.root to compatible)
            )
        }
    }

    @Test
    fun aSymbolCannotBePromotedOutOfItsScopeAndCallbacksCannotCaptureHiddenStorage() {
        val arena = newArena()
        val rootMeta = arena.metadata(arena.rootScope, origin)
        val child = arena.childScope(arena.rootScope)
        val childMeta = arena.metadata(child, origin)
        val hidden = arena.local(childMeta, intType, "hidden")
        assertFailsWith<IllegalArgumentException> { arena.local(rootMeta, intType, "promoted", hidden.symbol) }
        assertFailsWith<CodeModelException> { arena.stmt(rootMeta, StmtNode.Eval(hidden.load())) }
        val body = arena.block(childMeta, emptyList())
        assertFailsWith<CodeModelException> {
            arena.inline<Any>(
                rootMeta,
                FunctionSignature.of(emptyList(), intType),
                CallbackId(1, "invalid-capture"),
                InlineTarget.Body(emptyList(), body, setOf(hidden.symbol), arena.freshRegion())
            )
        }
    }

    @Test
    fun childOwnedDeclarationsAreClonedWithoutEscapingTheirSession() {
        val parent = newArena()
        val child = parent.childArena(SessionId(2), ScopeId(SessionId(2), 0))
        val meta = child.metadata(child.rootScope, origin)
        val local = child.local(meta, intType, "temporary")
        val body = child.block(
            meta,
            listOf(
                child.stmt(meta, StmtNode.LocalDecl(local, literal(child))),
                child.stmt(meta, StmtNode.Eval(local.load()))
            )
        )
        val imported = parent.importChildTree(child, body, parent.rootScope)
        val cloned = ((imported.statements()[0] as ImmutableStmt).node as StmtNode.LocalDecl).local as ImmutableLocal<*>
        assertEquals(parent.sessionId, cloned.symbol.session)
        assertNotEquals(local.symbol, cloned.symbol)
        assertSame(cloned.load(), ((imported.statements()[1] as ImmutableStmt).node as StmtNode.Eval).expression)
        child.close()
        parent.stmt(parent.metadata(parent.rootScope, origin), StmtNode.Eval(cloned.load()))
    }

    @Test
    fun loopRegionsAreFreshAndSharedDagIsChargedOnce() {
        var count = 0
        val arena = newArena { amount, _ -> count += amount }
        val meta = arena.metadata(arena.rootScope, origin)
        val region = arena.freshRegion()
        val nested = arena.childScope(arena.rootScope)
        val nestedMeta = arena.metadata(nested, origin)
        val loopBody = arena.block(nestedMeta, listOf(arena.stmt(nestedMeta, StmtNode.Break(region))))
        val condition = arena.expr(meta, boolType, ExprNode.Literal(ConstantValue.Bool(true)))
        val loop = arena.stmt(meta, StmtNode.While(condition, loopBody, region))
        val body = arena.block(meta, listOf(loop))
        val imported = arena.importTree(body, arena.rootScope)
        val clonedLoop = (imported.statements()[0] as ImmutableStmt).node as StmtNode.While
        assertNotEquals(region, clonedLoop.region)
        assertEquals(clonedLoop.region, ((clonedLoop.body.statements()[0] as ImmutableStmt).node as StmtNode.Break).region)

        val number = literal(arena)
        val shared = arena.expr(meta, intType, ExprNode.Binary(BinaryOperator.ADD, number, number))
        val before = count
        val cloned = arena.importTree(shared, arena.rootScope)
        assertEquals(2, count - before)
        val binary = cloned.node as ExprNode.Binary
        assertSame(binary.left, binary.right)
    }

    @Test
    fun inlineBodyParametersCapturesAndReturnRegionAreRemappedTogether() {
        val arena = newArena()
        val meta = arena.metadata(arena.rootScope, origin)
        val captured = arena.local(meta, intType, "captured")
        val callbackScope = arena.childScope(arena.rootScope)
        val callbackMeta = arena.metadata(callbackScope, origin)
        val parameter = arena.local(callbackMeta, intType, "argument")
        val region = arena.freshRegion()
        val sum = arena.expr(callbackMeta, intType, ExprNode.Binary(BinaryOperator.ADD, captured.load(), parameter.load()))
        val callbackBody = arena.block(
            callbackMeta,
            listOf(arena.stmt(callbackMeta, StmtNode.CallbackReturn(region, sum)))
        )
        val callback = arena.inline<Any>(
            meta,
            FunctionSignature.of(listOf(ParameterView.of("argument", intType, ParamMode.VALUE)), intType),
            CallbackId(1, "body"),
            InlineTarget.Body(listOf(parameter.symbol), callbackBody, setOf(captured.symbol), region)
        )
        val invoke = arena.expr(meta, intType, ExprNode.InlineCall(callback, listOf(InlineArgument.value(literal(arena)))))
        val block = arena.block(
            meta,
            listOf(
                arena.stmt(meta, StmtNode.LocalDecl(captured, literal(arena))),
                arena.stmt(meta, StmtNode.Eval(invoke))
            )
        )
        val imported = arena.importTree(block, arena.rootScope)
        val capturedCopy = ((imported.statements()[0] as ImmutableStmt).node as StmtNode.LocalDecl).local as ImmutableLocal<*>
        val invocation = (((imported.statements()[1] as ImmutableStmt).node as StmtNode.Eval).expression as ImmutableExpr<*>).node as ExprNode.InlineCall
        val callbackCopy = invocation.callback as ImmutableInlineFn<*>
        val target = callbackCopy.target as InlineTarget.Body
        assertSame(callback.callbackId, callbackCopy.callbackId)
        assertNotEquals(parameter.symbol, target.parameters.single())
        assertEquals(setOf(capturedCopy.symbol), target.captures)
        assertNotEquals(region, target.returnRegion)
        val returned = (target.block.statements().single() as ImmutableStmt).node as StmtNode.CallbackReturn
        assertEquals(target.returnRegion, returned.region)
        val clonedSum = (returned.value as ImmutableExpr<*>).node as ExprNode.Binary
        assertSame(capturedCopy.load(), clonedSum.left)
        assertEquals(target.parameters.single(), ((clonedSum.right as ImmutableExpr<*>).node as ExprNode.Read).location.root)
    }

    @Test
    fun closedAncestorInvalidatesDescendantsAndAllocationIsDeterministic() {
        val first = newArena()
        val second = newArena()
        assertEquals(first.freshSymbol(), second.freshSymbol())
        assertEquals(first.freshRegion(), second.freshRegion())
        assertEquals(first.freshExecutionContext(), second.freshExecutionContext())
        assertEquals(first.childScope(first.rootScope), second.childScope(second.rootScope))
        val child = first.childArena(SessionId(2), ScopeId(SessionId(2), 0))
        val grandchild = child.childArena(SessionId(3), ScopeId(SessionId(3), 0))
        child.close()
        assertFailsWith<CodeModelException> { grandchild.freshSymbol() }
        assertTrue(first.containsScope(first.rootScope))
        first.close()
        assertFailsWith<CodeModelException> { first.childScope(first.rootScope) }
    }
}

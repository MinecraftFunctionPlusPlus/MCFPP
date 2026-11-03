package top.mcfpp.mni.expand

import top.mcfpp.mni.api.*

/** Produces a complete, private plan. Publication and legacy invocation belong to CompilerBridge. */
internal class CodeLowerer {
    fun lower(code: ValidatedCode, target: LoweringTarget): LoweringPlan = Build(code, target).lower()

    private class Build(val proof: ValidatedCode, val target: LoweringTarget) {
        private class MutableBlock(
            val id: PlanBlockId,
            val scope: PlanScope,
            val frame: PlanExecutionFrame?,
            val contexts: List<PlanContextRegionId>
        ) {
            val operations = arrayListOf<PlanOperation>()
            var terminator: PlanTerminator? = null
        }
        /**
         * The result binding belongs to the exit's own region, not to the plan root. A callback
         * region therefore keeps the callback result type even when the root function has no
         * result or another result type.
         */
        private data class ResultExit(
            val type: PlanType,
            val result: PlanTemp?,
            val destination: PlanBlockId?,
            val kind: PlanExitKind
        )
        private data class LoopExit(val head: PlanBlockId, val after: PlanBlockId)
        private val blocks = arrayListOf<MutableBlock>()
        private val symbols = linkedMapOf<SymbolId, PlanSymbol>()
        private val scopes = linkedMapOf<ScopeId, PlanScope>()
        private val types = linkedMapOf<TypeKey, PlanType>()
        private val results = linkedMapOf<RegionId, ResultExit>()
        private val loops = linkedMapOf<RegionId, LoopExit>()
        private val addresses = linkedMapOf<AddressId, PlanLocationId>()
        private val contextRegions = arrayListOf<PlanContextRegion>()
        private val activeContexts = arrayListOf<PlanContextRegionId>()
        private val contexts = ContextLowerer(proof.environment.arena, ::scope)
        private var ambientFrame: PlanExecutionFrame? = null
        private var current: MutableBlock? = null
        private var tempSequence = 0
        private var locationSequence = 0
        private val root = proof.code as OwnedCode

        private fun fail(code: Code, message: String): Nothing = throw CodeModelException((code as OwnedCode).metadata.origin.callSite, message)
        private fun site(code: Code): PlanSite = (code as OwnedCode).metadata.origin.let {
            PlanSite(it.callSite, it.generatedFrom, it.expansionFrames)
        }
        private fun type(type: TypeRef<*>): PlanType = (type as ImmutableTypeRef<*>).let {
            if (target.domain != null && it.key.universe !in target.domain.typeDomains)
                fail(root, "Type belongs to another lowering declaration domain")
            if (target.domain?.complete == true && (it.graph == null || it.stableSourceId == null))
                fail(root, "A complete lowering domain requires canonical source type identity")
            types.getOrPut(it.key) { PlanType.from(it) }
        }
        private fun constant(value: ConstantValue): PlanConstant = when (value) {
            is ConstantValue.TypeValue -> PlanConstant.TypeValue(type(value.type))
            is ConstantValue.Sequence -> PlanConstant.Sequence(value.elements.map(::constant))
            is ConstantValue.Record -> PlanConstant.Record(value.fields.mapValues { constant(it.value) })
            else -> PlanConstant.detach(value)
        }
        private fun temp(type: TypeRef<*>) = PlanTemp(++tempSequence, type(type))
        private fun symbol(id: SymbolId): PlanSymbol = symbols.getOrPut(id) { PlanSymbol(symbols.size, id) }
        private fun scope(id: ScopeId): PlanScope {
            scopes[id]?.let { return it }
            val parent = proof.environment.arena.scopeParent(id)
            parent?.let { scope(it) }
            return PlanScope(scopes.size, id, parent).also { scopes[id] = it }
        }
        private fun newBlock(
            code: Code,
            frame: PlanExecutionFrame? = ambientFrame,
            regions: List<PlanContextRegionId> = activeContexts.toList()
        ): MutableBlock {
            // Shared expressions may be declared in an ancestor. Their control blocks execute
            // at the use site; an actual lexical Block establishes its own declared scope.
            val declared = (code as OwnedCode).metadata.scopeId
            val owner = if (code is Block) scope(declared) else current?.scope ?: scope(declared)
            return MutableBlock(PlanBlockId(blocks.size), owner, frame, regions).also { blocks += it }
        }
        private fun emit(operation: PlanOperation) {
            current?.operations?.add(operation)
        }
        private fun end(terminator: PlanTerminator) {
            current?.terminator = terminator
            current = null
        }
        private fun jump(block: MutableBlock, code: Code) = end(PlanTerminator.Jump(block.id, site(code)))

        fun lower(): LoweringPlan {
            // A proof is usable only while its arena is alive; the completed plan has no such dependency.
            try {
                proof.environment.arena.metadata(root.metadata.scopeId, root.metadata.origin, root.metadata.executionContextId)
            } catch (error: IllegalArgumentException) {
                fail(root, error.message ?: "Code arena is unavailable for lowering")
            }
            if (root.metadata.executionContextId != target.executionContext)
                fail(root, "Lowering target has a different execution context")
            ambientFrame = contexts.frame(root.metadata.executionContextId, root.metadata.scopeId, root)
            val entry = newBlock(root)
            current = entry
            val rootResult = proof.environment.resultRegion
            val resultType = when (val code = proof.code) {
                is Expr<*> -> type(code.type())
                else -> rootResult?.type?.let(::type)?.takeIf { it.kind != TypeKind.VOID }
            }
            rootResult?.let {
                results[it.id] = ResultExit(
                    type(it.type),
                    null,
                    null,
                    if (it.callback) PlanExitKind.CALLBACK else PlanExitKind.RESULT
                )
            }
            when (val code = proof.code) {
                is Expr<*> -> end(PlanTerminator.Exit(expression(code), PlanExitKind.EXPRESSION, site(code)))
                is Block -> block(code)
                is Stmt -> statement(code as ImmutableStmt)
                else -> fail(code, "An input handle cannot be lowered as code")
            }
            if (current != null) {
                if (resultType != null) end(PlanTerminator.Unreachable("Validated result region cannot fall through", site(root)))
                else end(PlanTerminator.Exit(null, if (rootResult?.callback == true) PlanExitKind.CALLBACK else PlanExitKind.BLOCK, site(root)))
            }
            // Retain context-region endpoints even when their normal continuation is unreachable;
            // their descriptors must never contain dangling block IDs.
            val reachable = linkedSetOf<PlanBlockId>()
            val pending = ArrayDeque<PlanBlockId>()
            pending.add(entry.id)
            while (pending.isNotEmpty()) {
                val id = pending.removeFirst()
                if (!reachable.add(id)) continue
                when (val exit = blocks[id.index].terminator ?: error("Unterminated lowering block")) {
                    is PlanTerminator.Jump -> pending.add(exit.target)
                    is PlanTerminator.Branch -> {
                        pending.add(exit.ifTrue)
                        pending.add(exit.ifFalse)
                    }
                    is PlanTerminator.ContextEnter -> {
                        val region = contextRegions[exit.region.index]
                        pending.add(region.body)
                        pending.add(region.after)
                    }
                    is PlanTerminator.ContextNext -> {
                        val region = contextRegions[exit.region.index]
                        pending.add(region.after)
                        if (region.change is PlanContextChange.ExecuteAs) pending.add(region.body)
                    }
                    else -> Unit
                }
            }
            return LoweringPlan(
                target, entry.id, resultType,
                blocks.filter { it.id in reachable }.map {
                    PlanBasicBlock(
                        it.id,
                        it.scope,
                        it.operations,
                        finishTerminator(it),
                        it.frame,
                        it.contexts
                    )
                },
                symbols.values.toList(), scopes.values.toList(),
                target.domain ?: PlanDomainStamp(types.keys.map { it.universe }.toSet()),
                contexts.frames(), contextRegions
            )
        }

        private fun finishTerminator(block: MutableBlock): PlanTerminator {
            fun unwind(target: PlanBlockId?): PlanContextUnwind {
                val destination = target?.let { blocks[it.index].contexts }.orEmpty()
                val common = block.contexts.zip(destination).takeWhile { (left, right) -> left == right }.size
                return PlanContextUnwind(block.contexts.drop(common).asReversed())
            }
            return when (val exit = block.terminator!!) {
                is PlanTerminator.Jump -> exit.copy(unwind = unwind(exit.target))
                is PlanTerminator.Branch -> exit.copy(ifTrueUnwind = unwind(exit.ifTrue), ifFalseUnwind = unwind(exit.ifFalse))
                is PlanTerminator.Exit -> exit.copy(unwind = unwind(null))
                else -> exit
            }
        }

        private fun field(field: FieldRef<*>) = (field as ImmutableFieldRef<*>).let {
            val graph = (it.type() as ImmutableTypeRef<*>).graph
            PlanField(it.key, it.name(), type(it.type()), it.writable(), graph?.type(it.key.owner)?.let(::type))
        }
        private fun location(location: NodeLocation, type: TypeRef<*>, code: Code): PlanLocation {
            val base = location.capturedBase?.let { addresses[it] ?: fail(code, "Captured address has no lowered declaration") }
            return PlanLocation(
                symbol(location.root),
                location.steps.map {
                    when (it) {
                        is LocationStep.Field -> PlanLocationStep.Field(field(it.field))
                        is LocationStep.Index -> PlanLocationStep.Index(it.value)
                        is LocationStep.Key -> PlanLocationStep.Key(it.value)
                        is LocationStep.CapturedIndex -> PlanLocationStep.CapturedIndex(symbol(it.symbol))
                        is LocationStep.CapturedKey -> PlanLocationStep.CapturedKey(symbol(it.symbol))
                    }
                },
                type(type),
                location.executionContextId,
                location.writable,
                base
            )
        }
        private fun location(ref: Ref<*>, code: Code): PlanLocation = location(
            when (ref) {
                is ImmutableLocal<*> -> ref.location
                is ImmutableRef<*> -> ref.location
                is ImmutableMinecraftScoreRef -> throw CodeModelException(ref.source(), "Unsupported reference node '${ref.javaClass.name}'")
            },
            ref.type(),
            code
        )
        private fun capture(location: PlanLocation, code: Code): PlanLocationId = if (location.capturedBase != null && location.steps.isEmpty()) location.capturedBase
        else PlanLocationId(++locationSequence, location.type).also { emit(PlanOperation.CaptureLocation(it, location, site(code))) }
        private fun capture(score: PlanScoreAddress, code: Code): PlanLocationId = PlanLocationId(++locationSequence, score.type).also { emit(PlanOperation.CaptureScore(it, score, site(code))) }
        private fun capture(ref: Ref<*>, code: Code): PlanLocationId = if (ref is ImmutableMinecraftScoreRef)
            capture(contexts.score(ref.target, ref.objective, type(ref.type()), ref), code)
        else capture(location(ref, code), code)
        private fun coerce(value: PlanTemp, expected: PlanType, code: Code): PlanTemp = if (value.type.key == expected.key) value
        else PlanTemp(++tempSequence, expected).also { emit(PlanOperation.Convert(it, value, ConversionKind.IMPLICIT, site(code))) }

        private fun expression(expr: Expr<*>): PlanTemp {
            val result = temp(expr.type())
            when (val node = (expr as ImmutableExpr<*>).node) {
                is ExprNode.Literal -> emit(PlanOperation.Literal(result, constant(node.value), site(expr)))
                is ExprNode.Read -> {
                    val address = capture(location(node.location, expr.type(), expr), expr)
                    emit(PlanOperation.Read(result, address, site(expr)))
                }
                is ExprNode.ScoreRead -> {
                    val address = capture(contexts.score(node.target, node.objective, type(expr.type()), expr), expr)
                    emit(PlanOperation.Read(result, address, site(expr)))
                }
                is ExprNode.BlockMatches -> emit(PlanOperation.BlockMatches(result, contexts.block(node.block), contexts.predicate(node.predicate), site(expr)))
                is ExprNode.Unary -> emit(PlanOperation.Unary(result, node.operator, expression(node.operand), site(expr)))
                is ExprNode.Binary -> if (node.operator in setOf(BinaryOperator.AND, BinaryOperator.OR)) logical(expr, node, result)
                else {
                    val left = expression(node.left)
                    val right = expression(node.right)
                    emit(PlanOperation.Binary(result, node.operator, left, right, site(expr)))
                }
                is ExprNode.Convert -> emit(PlanOperation.Convert(result, expression(node.value), node.kind, site(expr)))
                is ExprNode.Field -> emit(PlanOperation.Field(result, expression(node.receiver), field(node.field), site(expr)))
                is ExprNode.Index -> {
                    val receiver = expression(node.receiver)
                    val index = expression(node.index)
                    emit(PlanOperation.Index(result, receiver, index, site(expr)))
                }
                is ExprNode.Call -> call(expr, node, result)
                is ExprNode.ExprBlock -> {
                    val body = newBlock(node.block)
                    val after = newBlock(expr)
                    results[node.region] = ResultExit(type(node.resultType), result, after.id, PlanExitKind.EXPRESSION)
                    jump(body, expr)
                    current = body
                    block(node.block)
                    if (current != null) end(PlanTerminator.Unreachable("Validated expression region cannot fall through", site(expr)))
                    results.remove(node.region)
                    current = after
                }
                is ExprNode.MacroCall -> fail(expr, "MacroCall must be expanded before lowering")
                is ExprNode.InlineCall -> fail(expr, "InlineCall must be expanded before lowering")
            }
            return result
        }

        private fun logical(expr: Expr<*>, node: ExprNode.Binary, result: PlanTemp) {
            val left = expression(node.left)
            emit(PlanOperation.Move(result, left, site(expr)))
            val right = newBlock(node.right)
            val after = newBlock(expr)
            end(
                PlanTerminator.Branch(
                    left,
                    if (node.operator == BinaryOperator.AND) right.id else after.id,
                    if (node.operator == BinaryOperator.AND) after.id else right.id,
                    site(expr)
                )
            )
            current = right
            val value = expression(node.right)
            emit(PlanOperation.Move(result, value, site(expr)))
            jump(after, expr)
            current = after
        }

        private fun call(expr: Expr<*>, node: ExprNode.Call, result: PlanTemp) {
            val function = node.function as ImmutableFunctionRef<*>
            val kind = when (function.kind) {
                FunctionKind.ORDINARY -> PlanCallKind.ORDINARY
                FunctionKind.NATIVE -> PlanCallKind.LEGACY_NATIVE
                FunctionKind.HELPER -> PlanCallKind.HELPER
                FunctionKind.MACRO -> fail(expr, "A macro target cannot enter the backend call plan")
            }
            val parameters = function.parameters().map { PlanParameter(it.name(), type(it.type()), it.mode()) }
            val readonlyParameters = function.readonlyParameters().map { PlanParameter(it.name(), type(it.type()), it.mode()) }
            // The actual receiver is captured first and exactly once, at its original
            // physical address/context, so later argument effects cannot replace it.
            val receiver = node.receiver?.let { PlanArgument.Reference(capture(it, it)) }
            val readonlyArguments = node.readonlyArguments.map { PlanReadonlyArgument(type(it.type), constant(it.value), it.source) }
            val arguments = node.arguments.zip(parameters).map { (argument, parameter) ->
                when (val payload = argument.payload) {
                    is ArgumentPayload.Value -> PlanArgument.Value(coerce(expression(payload.expression), parameter.type, expr))
                    is ArgumentPayload.Reference -> PlanArgument.Reference(capture(payload.reference, payload.reference))
                    is ArgumentPayload.Constant, is ArgumentPayload.Inline -> fail(expr, "Compile-time call arguments remain after expansion")
                }
            }
            emit(
                PlanOperation.Call(
                    result.takeIf { it.type.kind != TypeKind.VOID },
                    PlanFunction(function.key, function.name(), kind, parameters, type(function.returnType()), readonlyParameters),
                    arguments,
                    site(expr),
                    readonlyArguments,
                    receiver
                )
            )
        }

        private fun block(block: Block) {
            block.statements().forEach { if (current != null) statement(it as ImmutableStmt) }
        }

        private fun resultExit(region: RegionId, value: Expr<*>?, code: Code) {
            val exit = results[region] ?: fail(code, "Control exit has no lowered result region")
            val lowered = value?.let(::expression)
            if (exit.destination == null) {
                // The exit value is bound to the region's declared result type. CALLBACK leaves
                // only the lowered callback function; inline callbacks are already independent
                // ExprBlock/loop regions and never reach this root exit.
                end(PlanTerminator.Exit(lowered?.let { coerce(it, exit.type, code) }, exit.kind, site(code)))
            } else {
                val destination = exit.result ?: fail(code, "Expression region has no result binding")
                val result = lowered ?: fail(code, "Expression region requires a returned value")
                emit(PlanOperation.Move(destination, coerce(result, destination.type, code), site(code)))
                end(PlanTerminator.Jump(exit.destination, site(code)))
            }
        }

        private fun statement(stmt: ImmutableStmt) {
            when (val node = stmt.node) {
                is StmtNode.ExecuteAs, is StmtNode.At, is StmtNode.Rotated -> contextStatement(stmt)
                is StmtNode.CaptureAddress -> {
                    // A capture executes even when unused. Its capability is not an external binding.
                    val source = location(node.location, node.type, stmt)
                    addresses[node.address] = PlanLocationId(++locationSequence, source.type).also {
                        emit(PlanOperation.CaptureLocation(it, source, site(stmt)))
                    }
                }
                is StmtNode.LocalDecl -> {
                    val value = expression(node.initialValue)
                    val destination = location(node.local, stmt)
                    emit(PlanOperation.DeclareLocal(destination, node.local.hint(), coerce(value, destination.type, stmt), site(stmt)))
                }
                is StmtNode.Assign -> {
                    val address = capture(node.destination, stmt)
                    val value = expression(node.expression)
                    emit(PlanOperation.Write(address, coerce(value, address.type, stmt), site(stmt)))
                }
                is StmtNode.Eval -> {
                    expression(node.expression)
                }
                is StmtNode.ScopeBlock -> {
                    val nested = newBlock(node.block)
                    val after = newBlock(stmt)
                    jump(nested, stmt)
                    current = nested
                    block(node.block)
                    jump(after, stmt)
                    current = after
                }
                is StmtNode.If -> {
                    val condition = expression(node.condition)
                    val yes = newBlock(node.thenBlock)
                    val no = node.elseBlock?.let { newBlock(it) }
                    val after = newBlock(stmt)
                    end(PlanTerminator.Branch(condition, yes.id, no?.id ?: after.id, site(stmt)))
                    current = yes
                    block(node.thenBlock)
                    jump(after, stmt)
                    if (no != null) {
                        current = no
                        block(node.elseBlock!!)
                        jump(after, stmt)
                    }
                    current = after
                }
                is StmtNode.While -> {
                    val head = newBlock(node.condition)
                    val body = newBlock(node.body)
                    val after = newBlock(stmt)
                    jump(head, stmt)
                    loops[node.region] = LoopExit(head.id, after.id)
                    current = head
                    val condition = expression(node.condition)
                    end(PlanTerminator.Branch(condition, body.id, after.id, site(stmt)))
                    current = body
                    block(node.body)
                    jump(head, stmt)
                    loops.remove(node.region)
                    current = after
                }
                is StmtNode.Yield -> resultExit(node.region, node.value, stmt)
                is StmtNode.CallbackReturn -> resultExit(node.region, node.value, stmt)
                is StmtNode.Break -> end(PlanTerminator.Jump(loops[node.region]?.after ?: fail(stmt, "Break has no lowered loop"), site(stmt)))
                is StmtNode.Continue -> end(PlanTerminator.Jump(loops[node.region]?.head ?: fail(stmt, "Continue has no lowered loop"), site(stmt)))
            }
        }

        private fun contextStatement(stmt: ImmutableStmt) {
            val body: Block
            val parent: ExecutionContextFrame
            val child: ExecutionContextFrame
            val change: PlanContextChange
            when (val node = stmt.node) {
                is StmtNode.ExecuteAs -> {
                    body = node.body
                    parent = node.parentContext
                    child = node.context
                    change = PlanContextChange.ExecuteAs(contexts.query(node.targets), contexts.entity(node.self))
                }
                is StmtNode.At -> {
                    body = node.body
                    parent = node.parentContext
                    child = node.context
                    change = PlanContextChange.At(contexts.position(node.position))
                }
                is StmtNode.Rotated -> {
                    body = node.body
                    parent = node.parentContext
                    child = node.context
                    change = PlanContextChange.Rotated(contexts.rotation(node.rotation))
                }
                else -> throw CodeModelException(stmt.source(), "Unsupported execution context statement")
            }
            val parentFrame = contexts.frame(parent, stmt)
            val childFrame = contexts.frame(child, body)
            if (ambientFrame != parentFrame) throw CodeModelException(stmt.source(), "Context plan lost its parent frame")
            val regionId = PlanContextRegionId(contextRegions.size)
            val after = newBlock(stmt)
            val entry = newBlock(body, childFrame, activeContexts + regionId)
            contextRegions += PlanContextRegion(regionId, parentFrame, childFrame, entry.scope, change, entry.id, after.id, site(stmt))
            end(PlanTerminator.ContextEnter(regionId, site(stmt)))
            activeContexts += regionId
            ambientFrame = childFrame
            try {
                current = entry
                block(body)
                if (current != null) end(PlanTerminator.ContextNext(regionId, site(stmt)))
            } finally {
                activeContexts.removeAt(activeContexts.lastIndex)
                ambientFrame = parentFrame
            }
            current = after
        }
    }
}

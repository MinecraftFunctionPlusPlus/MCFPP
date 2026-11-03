package top.mcfpp.mni.expand

import top.mcfpp.mni.api.*

/** A fresh traversal numbers identities by occurrence, never by physical allocation counters. */
internal class CodePrinter {
    fun print(code: Code): String = Render().code(code)

    private class Render {
        private val symbols = linkedMapOf<SymbolId, String>()
        private val regions = linkedMapOf<RegionId, String>()
        private val contexts = linkedMapOf<ExecutionContextId, String>()
        private val types = linkedMapOf<TypeKey, String>()
        private val fields = linkedMapOf<FieldKey, String>()
        private val addresses = linkedMapOf<AddressId, String>()
        private val activeCallbacks = hashSetOf<CallbackId>()
        private val signatureNames = java.util.IdentityHashMap<InlineSignature<*>, String>()
        private val activeSignatures = java.util.Collections.newSetFromMap(
            java.util.IdentityHashMap<InlineSignature<*>, Boolean>()
        )
        private fun symbol(id: SymbolId) = symbols.getOrPut(id) { "s${symbols.size}" }
        private fun region(id: RegionId) = regions.getOrPut(id) { "r${regions.size}" }
        private fun context(id: ExecutionContextId) = contexts.getOrPut(id) { "c${contexts.size}" }
        private fun address(id: AddressId) = addresses.getOrPut(id) { "a${addresses.size}" }
        private fun type(ref: TypeRef<*>): String {
            val descriptor = ref as ImmutableTypeRef<*>
            return "${quoted(ref.name())}#${types.getOrPut(descriptor.key) { "t${types.size}" }}"
        }
        private fun unsupported(code: Code, kind: String, node: Any): Nothing = throw CodeModelException(code.source(), "Unsupported $kind node '${node.javaClass.name}'")

        private fun quoted(value: String): String = buildString {
            append('"')
            value.forEach { character ->
                when (character) {
                    '"' -> append("\\\"")
                    '\\' -> append("\\\\")
                    '\n' -> append("\\n")
                    '\r' -> append("\\r")
                    '\t' -> append("\\t")
                    else -> if (character.code < 32) append("\\u${character.code.toString(16).padStart(4, '0')}") else append(character)
                }
            }
            append('"')
        }

        private fun constant(value: ConstantValue): String = when (value) {
            is ConstantValue.Integral -> value.value.toString()
            is ConstantValue.Bool -> value.value.toString()
            is ConstantValue.Text -> quoted(value.value)
            is ConstantValue.FloatBits -> "f32(0x${value.bits.toUInt().toString(16).padStart(8, '0')})"
            is ConstantValue.DoubleBits -> "f64(0x${value.bits.toULong().toString(16).padStart(16, '0')})"
            is ConstantValue.Nbt -> "nbt(${quoted(value.snbt)})"
            is ConstantValue.TypeValue -> "type(${type(value.type)})"
            ConstantValue.NullValue -> "null"
            is ConstantValue.Sequence -> value.elements.joinToString(", ", "[", "]", transform = ::constant)
            is ConstantValue.Record -> value.fields.toSortedMap().entries.joinToString(", ", "{", "}") { "${quoted(it.key)}: ${constant(it.value)}" }
        }

        private fun field(field: FieldRef<*>): String {
            val descriptor = field as ImmutableFieldRef<*>
            return "${quoted(field.name())}#${fields.getOrPut(descriptor.key) { "f${fields.size}" }}"
        }

        private fun location(location: NodeLocation): String {
            return buildString {
                if (location.capturedBase == null) append(symbol(location.root))
                else append("address ").append(address(location.capturedBase))
                location.steps.forEach { step ->
                    when (step) {
                        is LocationStep.Field -> append('.').append(field(step.field))
                        is LocationStep.Index -> append('[').append(step.value).append(']')
                        is LocationStep.Key -> append('[').append(quoted(step.value)).append(']')
                        is LocationStep.CapturedIndex -> append("[index:").append(symbol(step.symbol)).append(']')
                        is LocationStep.CapturedKey -> append("[key:").append(symbol(step.symbol)).append(']')
                    }
                }
                location.executionContextId?.let { append('@').append(context(it)) }
                if (!location.writable) append(" readonly")
            }
        }

        private fun frame(frame: ExecutionContextFrame): String = "executor=${context(frame.executor)} position=${context(frame.position)} rotation=${context(frame.rotation)}"

        private fun entitySet(set: EntitySet): String {
            val query = (set as? ImmutableEntitySet)?.query ?: unsupported(set, "entity set", set)
            return buildString {
                append("entities ").append(query.selection.name.lowercase())
                query.entityType?.let { append(" type=").append(quoted(it)) }
                if (query.tags.isNotEmpty()) append(" tags=").append(query.tags.joinToString(",", "[", "]", transform = ::quoted))
                query.limit?.let { append(" limit=").append(it) }
                query.sort?.let { append(" sort=").append(it.name.lowercase()) }
            }
        }

        private fun entity(entity: EntityHandle): String {
            val handle = entity as? ImmutableEntityHandle ?: unsupported(entity, "entity handle", entity)
            return "entity ${context(handle.executor)}"
        }

        private fun position(value: PositionHandle): String {
            val handle = value as? ImmutablePositionHandle ?: unsupported(value, "position handle", value)
            return when (val node = handle.node) {
                is PositionNode.Absolute -> "position(${number(node.x)}, ${number(node.y)}, ${number(node.z)})"
                is PositionNode.Current -> "position current ${context(node.context)}"
                is PositionNode.Entity -> "position entity(${entity(node.entity)})"
                is PositionNode.Offset -> "position offset(${position(node.base)}, ${number(node.x)}, ${number(node.y)}, ${number(node.z)})"
                is PositionNode.LocalOffset -> "position localOffset(${position(node.base)}, ${rotation(node.rotation)}, ${number(node.left)}, ${number(node.up)}, ${number(node.forward)})"
            }
        }

        private fun rotation(value: RotationHandle): String {
            val handle = value as? ImmutableRotationHandle ?: unsupported(value, "rotation handle", value)
            return when (val node = handle.node) {
                is RotationNode.Absolute -> "rotation(${number(node.yaw)}, ${number(node.pitch)})"
                is RotationNode.Current -> "rotation current ${context(node.context)}"
                is RotationNode.Entity -> "rotation entity(${entity(node.entity)})"
            }
        }

        private fun block(block: BlockHandle): String {
            val handle = block as? ImmutableBlockHandle ?: unsupported(block, "block handle", block)
            return "blockAt(${position(handle.position)})"
        }

        private fun predicate(predicate: BlockPredicate): String = (if (predicate.isTag) "tag" else "block") + " ${quoted(predicate.resource)}" +
            predicate.states.entries.joinToString(",", "[", "]") { "${quoted(it.key)}=${quoted(it.value)}" }

        private fun number(value: Double): String = java.lang.Double.toString(value)

        private fun reference(ref: Ref<*>): String = when (ref) {
            is ImmutableRef<*> -> location(ref.location)
            is ImmutableLocal<*> -> location(ref.location)
            is ImmutableMinecraftScoreRef -> "score ${when (val target = ref.target) {
                is MinecraftScoreTarget.Fixed -> "holder ${quoted(target.holder.name)}"
                is MinecraftScoreTarget.Entity -> entity(target.entity)
            }} ${quoted(ref.objective)}"
        }

        private fun call(
            function: FunctionRef<*>,
            arguments: List<CallArgument>,
            readonlyArguments: List<TypedConstant> = emptyList(),
            macro: Boolean = false,
            receiver: Ref<*>? = null
        ): String {
            // Print the declaration's public name, not its catalog/physical key. Keys such as
            // "prepared:3" depend on preparation order and would make repeated dumps differ.
            val name = function.name()
            val prefix = if (macro) "macro" else "call"
            val signature = function.readonlyParameters().takeIf { it.isNotEmpty() }?.joinToString(", ", " readonly<", ">") {
                "${quoted(it.name())}: ${type(it.type())}"
            }.orEmpty()
            val readonly = readonlyArguments.takeIf { it.isNotEmpty() }?.joinToString(", ", " readonly(", ")") {
                "${type(it.type)}=${constant(it.value)}"
            }.orEmpty()
            val actualReceiver = receiver?.let { " receiver ${reference(it)}" }.orEmpty()
            return "$prefix ${quoted(name)}$signature$actualReceiver${arguments.joinToString(", ", "(", ")") { argument(it.payload) }}$readonly"
        }

        private fun signature(descriptor: InlineSignature<*>): String {
            val name = signatureNames[descriptor] ?: "sig${signatureNames.size}".also { signatureNames[descriptor] = it }
            if (!activeSignatures.add(descriptor)) return "@$name"
            return try {
                "$name${descriptor.parameters().joinToString(", ", "(", ")") {
                    "${it.mode().name.lowercase()} ${type(it.type())}" +
                        (it.inlineSignature()?.let { nested -> " ${signature(nested)}" } ?: "")
                }} -> ${type(descriptor.returnType())}"
            } finally {
                activeSignatures.remove(descriptor)
            }
        }

        private fun argument(payload: ArgumentPayload): String = when (payload) {
            is ArgumentPayload.Value -> "value ${expression(payload.expression)}"
            is ArgumentPayload.Reference -> "ref ${reference(payload.reference)}"
            is ArgumentPayload.Constant -> "const ${type(payload.constant.type())} ${constant(payload.constant.value())}"
            is ArgumentPayload.Inline -> "inline ${callback(payload.callback)}"
        }

        private fun callback(value: InlineFn<*>): String {
            val callback = value as ImmutableInlineFn<*>
            val prefix = "callback ${quoted(callback.callbackId.declaration)} ${signature(callback.signature())}"
            if (!activeCallbacks.add(callback.callbackId)) return "$prefix <recursive>"
            try {
                return prefix + when (val target = callback.target) {
                    is InlineTarget.Declared -> " = ${quoted(target.function.declaration)}"
                    is InlineTarget.Body -> {
                        val parameters = target.parameters.joinToString(", ", "[", "]", transform = ::symbol)
                        val returnRegion = region(target.returnRegion)
                        // Captures are a set; list them in the same order as their bound references occur.
                        val body = block(target.block)
                        val used = target.captures.mapNotNull { symbols[it] }.sortedBy { it.drop(1).toInt() }
                        val captures = used.joinToString(", ", "[", "]")
                        " params$parameters captures$captures unusedCaptures=${target.captures.size - used.size} return $returnRegion $body"
                    }
                }
            } finally {
                activeCallbacks.remove(callback.callbackId)
            }
        }

        private fun expression(expr: Expr<*>): String {
            val result = type(expr.type())
            val payload = when (val node = (expr as ImmutableExpr<*>).node) {
                is ExprNode.ScoreRead -> "scoreRead ${when (val target = node.target) {
                    is MinecraftScoreTarget.Fixed -> "holder ${quoted(target.holder.name)}"
                    is MinecraftScoreTarget.Entity -> entity(target.entity)
                }} ${quoted(node.objective)}"
                is ExprNode.BlockMatches -> "blockMatches ${block(node.block)} ${predicate(node.predicate)}"
                is ExprNode.Literal -> "literal ${constant(node.value)}"
                is ExprNode.Read -> "read ${location(node.location)}"
                is ExprNode.Unary -> "${node.operator.name.lowercase()}(${expression(node.operand)})"
                is ExprNode.Binary -> "${node.operator.name.lowercase()}(${expression(node.left)}, ${expression(node.right)})"
                is ExprNode.Convert -> "convert ${node.kind.name.lowercase()} ${type(node.target)}(${expression(node.value)})"
                is ExprNode.Field -> "field ${expression(node.receiver)}.${field(node.field)}"
                is ExprNode.Index -> "index ${expression(node.receiver)}[${expression(node.index)}]"
                is ExprNode.Call -> call(node.function, node.arguments, node.readonlyArguments, receiver = node.receiver)
                is ExprNode.MacroCall -> call(node.function, node.arguments, macro = true)
                is ExprNode.ExprBlock -> "expression ${region(node.region)} ${type(node.resultType)} ${block(node.block)}"
                is ExprNode.InlineCall -> "inline ${callback(node.callback)}${node.arguments.joinToString(", ", "(", ")") { argument(it.payload) }}"
            }
            return "$payload : $result"
        }

        private fun statement(stmt: Stmt): String = when (val node = (stmt as ImmutableStmt).node) {
            is StmtNode.ExecuteAs -> "executeAs ${entitySet(node.targets)} self=${entity(node.self)} {${frame(node.context)}} ${block(node.body)}"
            is StmtNode.At -> "at ${position(node.position)} {${frame(node.context)}} ${block(node.body)}"
            is StmtNode.Rotated -> "rotated ${rotation(node.rotation)} {${frame(node.context)}} ${block(node.body)}"
            is StmtNode.CaptureAddress -> "capture ${address(node.address)} = ${location(node.location)} : ${type(node.type)}"
            is StmtNode.LocalDecl -> "local ${reference(node.local)} : ${type(node.local.type())} = ${expression(node.initialValue)}"
            is StmtNode.Assign -> "assign ${reference(node.destination)} = ${expression(node.expression)}"
            is StmtNode.Eval -> "eval ${expression(node.expression)}"
            is StmtNode.If -> "if ${expression(node.condition)} ${block(node.thenBlock)}" + (node.elseBlock?.let { " else ${block(it)}" } ?: "")
            is StmtNode.While -> "while ${region(node.region)} ${expression(node.condition)} ${block(node.body)}"
            is StmtNode.ScopeBlock -> "scope ${block(node.block)}"
            is StmtNode.CallbackReturn -> "return ${region(node.region)}" + (node.value?.let { " ${expression(it)}" } ?: "")
            is StmtNode.Yield -> "yield ${region(node.region)} ${expression(node.value)}"
            is StmtNode.Break -> "break ${region(node.region)}"
            is StmtNode.Continue -> "continue ${region(node.region)}"
        }

        private fun block(block: Block): String = block.statements().joinToString("; ", "{", "}", transform = ::statement)

        fun code(code: Code): String = when (code) {
            is Expr<*> -> expression(code)
            is Block -> block(code)
            is Stmt -> statement(code)
            is Ref<*> -> "ref ${reference(code)} : ${type(code.type())}"
            is EntitySet -> entitySet(code)
            is EntityHandle -> entity(code)
            is PositionHandle -> position(code)
            is RotationHandle -> rotation(code)
            is BlockHandle -> block(code)
            is Const<*> -> "const ${constant(code.value())} : ${type(code.type())}"
            is Value<*> -> "value ${expression(code.expr())}"
            is InlineFn<*> -> callback(code)
        }
    }
}

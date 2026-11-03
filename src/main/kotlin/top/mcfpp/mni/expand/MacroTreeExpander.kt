package top.mcfpp.mni.expand

import top.mcfpp.mni.api.*
import java.util.Collections
import java.util.IdentityHashMap

/**
 * Rewrites one session-owned tree so that every nested [ExprNode.MacroCall] is replaced by its
 * recursively expanded result. Traversal follows the original evaluation/lexical order and keeps
 * each replacement at the exact argument, initializer, condition or returned-tree position. No
 * host callback is retained in the resulting tree.
 */
internal class MacroTreeExpander(private val expander: MacroExpander) {
    private val visiting = Collections.newSetFromMap(IdentityHashMap<Code, Boolean>())

    fun expand(session: ExpansionSession, code: Code): Code = when (code) {
        is Block -> expandBlock(session, code)
        is Expr<*> -> expandExpression(session, code)
        is Stmt -> expandStatement(session, code)
        else -> expander.fail(session, code.source(), "Unsupported code node '${code.javaClass.name}'")
    }

    private inline fun <T> guard(session: ExpansionSession, code: Code, action: () -> T): T {
        if (!visiting.add(code)) expander.fail(session, code.source(), "Cyclic code graph during macro expansion")
        try {
            return action()
        } finally {
            visiting.remove(code)
        }
    }

    private fun expandBlock(session: ExpansionSession, block: Block): Block = guard(session, block) {
        val metadata = (block as OwnedCode).metadata
        val statements = ArrayList<Stmt>()
        var changed = false
        block.statements().forEach { statement ->
            when (val expanded = expandStatement(session, statement)) {
                is Block -> {
                    val expandedMetadata = (expanded as OwnedCode).metadata
                    if (expandedMetadata.scopeId == metadata.scopeId) {
                        statements.addAll(expanded.statements())
                    } else {
                        statements.add(session.arena.stmt(metadata, StmtNode.ScopeBlock(expanded)))
                    }
                    changed = true
                }
                is Stmt -> {
                    statements.add(expanded)
                    if (expanded !== statement) changed = true
                }
                else -> expander.fail(session, statement.source(), "Statement expansion produced unknown code")
            }
        }
        if (changed) session.arena.block(metadata, statements) else block
    }

    private fun expandStatement(session: ExpansionSession, statement: Stmt): Code = guard(session, statement) {
        val owned = statement as ImmutableStmt
        val metadata = owned.metadata
        when (val node = owned.node) {
            is StmtNode.Eval -> {
                val value = expandExpression(session, node.expression)
                if (value is Block) {
                    val expandedMetadata = (value as OwnedCode).metadata
                    if (expandedMetadata.scopeId == metadata.scopeId) value
                    else session.arena.stmt(metadata, StmtNode.ScopeBlock(value))
                } else {
                    val expression = requireExpression(session, value, node.expression.source())
                    if (expression === node.expression) statement
                    else session.arena.stmt(metadata, StmtNode.Eval(expression))
                }
            }
            is StmtNode.LocalDecl -> {
                val expression = requireExpression(session, expandExpression(session, node.initialValue), node.initialValue.source())
                if (expression === node.initialValue) statement
                else session.arena.stmt(metadata, StmtNode.LocalDecl(node.local, expression))
            }
            is StmtNode.Assign -> {
                val expression = requireExpression(session, expandExpression(session, node.expression), node.expression.source())
                if (expression === node.expression) statement
                else session.arena.stmt(metadata, StmtNode.Assign(node.destination, expression))
            }
            is StmtNode.If -> {
                val condition = requireExpression(session, expandExpression(session, node.condition), node.condition.source())
                val thenBlock = expandBlock(session, node.thenBlock)
                val elseBlock = node.elseBlock?.let { expandBlock(session, it) }
                if (condition === node.condition && thenBlock === node.thenBlock && elseBlock === node.elseBlock) statement
                else session.arena.stmt(metadata, StmtNode.If(condition, thenBlock, elseBlock))
            }
            is StmtNode.While -> {
                val condition = requireExpression(session, expandExpression(session, node.condition), node.condition.source())
                val body = expandBlock(session, node.body)
                if (condition === node.condition && body === node.body) statement
                else session.arena.stmt(metadata, StmtNode.While(condition, body, node.region))
            }
            is StmtNode.ScopeBlock -> {
                val block = expandBlock(session, node.block)
                if (block === node.block) statement else session.arena.stmt(metadata, StmtNode.ScopeBlock(block))
            }
            is StmtNode.Yield -> {
                val value = requireExpression(session, expandExpression(session, node.value), node.value.source())
                if (value === node.value) statement else session.arena.stmt(metadata, StmtNode.Yield(node.region, value))
            }
            is StmtNode.CallbackReturn -> {
                val value = node.value?.let { requireExpression(session, expandExpression(session, it), it.source()) }
                if (value === node.value) statement else session.arena.stmt(metadata, StmtNode.CallbackReturn(node.region, value))
            }
            is StmtNode.ExecuteAs -> {
                val body = expandBlock(session, node.body)
                if (body === node.body) statement
                else session.arena.stmt(metadata, StmtNode.ExecuteAs(node.targets, node.self, body, node.parentContext, node.context))
            }
            is StmtNode.At -> {
                val body = expandBlock(session, node.body)
                if (body === node.body) statement
                else session.arena.stmt(metadata, StmtNode.At(node.position, body, node.parentContext, node.context))
            }
            is StmtNode.Rotated -> {
                val body = expandBlock(session, node.body)
                if (body === node.body) statement
                else session.arena.stmt(metadata, StmtNode.Rotated(node.rotation, body, node.parentContext, node.context))
            }
            is StmtNode.CaptureAddress, is StmtNode.Break, is StmtNode.Continue -> statement
        }
    }

    private fun expandExpression(session: ExpansionSession, expression: Expr<*>): Code = guard(session, expression) {
        val owned = expression as ImmutableExpr<*>
        val metadata = owned.metadata
        val type = expression.type()
        when (val node = owned.node) {
            is ExprNode.Literal, is ExprNode.Read, is ExprNode.ScoreRead, is ExprNode.BlockMatches -> expression
            is ExprNode.Unary -> {
                val operand = requireExpression(session, expandExpression(session, node.operand), node.operand.source())
                if (operand === node.operand) expression
                else session.arena.expr(metadata, type, ExprNode.Unary(node.operator, operand))
            }
            is ExprNode.Binary -> {
                val left = requireExpression(session, expandExpression(session, node.left), node.left.source())
                val right = requireExpression(session, expandExpression(session, node.right), node.right.source())
                if (left === node.left && right === node.right) expression
                else session.arena.expr(metadata, type, ExprNode.Binary(node.operator, left, right))
            }
            is ExprNode.Convert -> {
                val value = requireExpression(session, expandExpression(session, node.value), node.value.source())
                if (value === node.value) expression
                else session.arena.expr(metadata, type, ExprNode.Convert(value, node.target, node.kind))
            }
            is ExprNode.Field -> {
                val receiver = requireExpression(session, expandExpression(session, node.receiver), node.receiver.source())
                if (receiver === node.receiver) expression
                else session.arena.expr(metadata, type, ExprNode.Field(receiver, node.field))
            }
            is ExprNode.Index -> {
                val receiver = requireExpression(session, expandExpression(session, node.receiver), node.receiver.source())
                val index = requireExpression(session, expandExpression(session, node.index), node.index.source())
                if (receiver === node.receiver && index === node.index) expression
                else session.arena.expr(metadata, type, ExprNode.Index(receiver, index))
            }
            is ExprNode.Call -> {
                val arguments = node.arguments.map { expandArgument(session, it) }
                if (arguments.indices.all { arguments[it] === node.arguments[it] }) expression
                // Rebuilding for a changed argument must keep the member receiver projected from
                // the original source position rather than degrading to a receiverless call.
                else session.arena.expr(
                    metadata,
                    type,
                    ExprNode.Call(node.function, arguments, node.readonlyArguments, node.receiver)
                )
            }
            is ExprNode.MacroCall -> expander.expandMacroCall(session, node, expression)
            is ExprNode.ExprBlock -> {
                val block = expandBlock(session, node.block)
                if (block === node.block) expression
                else session.arena.expr(metadata, type, ExprNode.ExprBlock(block, node.resultType, node.region))
            }
            is ExprNode.InlineCall -> expander.expandInlineCall(session, node, expression)
        }
    }

    private fun expandArgument(session: ExpansionSession, argument: CallArgument): CallArgument = when (val payload = argument.payload) {
        is ArgumentPayload.Value -> {
            val expression = requireExpression(session, expandExpression(session, payload.expression), payload.expression.source())
            if (expression === payload.expression) argument else CallArgument(ArgumentPayload.Value(expression))
        }
        is ArgumentPayload.Reference, is ArgumentPayload.Constant, is ArgumentPayload.Inline -> argument
    }

    private fun requireExpression(session: ExpansionSession, code: Code, source: SourceSpan): Expr<*> = code as? Expr<*> ?: expander.fail(session, source, "A void macro expansion cannot be used as an expression")
}

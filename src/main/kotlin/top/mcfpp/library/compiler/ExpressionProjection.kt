package top.mcfpp.library.compiler

import org.antlr.v4.runtime.ParserRuleContext
import org.antlr.v4.runtime.Token
import org.antlr.v4.runtime.tree.TerminalNodeImpl
import top.mcfpp.antlr.mcfppLexer as L
import top.mcfpp.antlr.mcfppParser as P
import top.mcfpp.library.syntax.*
import java.util.IdentityHashMap

/** Handwritten adapter to the current grammar. Use a fresh instance for each body invocation. */
class ExpressionProjection(
    val bindings: ProjectionBindings,
    private val structures: ProjectionStructures = object : ProjectionStructures {},
    val source: ProjectionSource = ProjectionSource()
) {
    private val origins = IdentityHashMap<ParserRuleContext, ProjectionOrigin>()
    fun origin(context: ParserRuleContext): ProjectionOrigin? = origins[context]

    /** These construction helpers also serve statement/template providers sharing this projection. */
    fun <T : ParserRuleContext> node(context: T, semantic: SemanticNode? = null, build: T.() -> Unit): T {
        context.build()
        val children = context.children.orEmpty()
        context.start = children.firstNotNullOfOrNull { when (it) {
            is ParserRuleContext -> it.start
            is org.antlr.v4.runtime.tree.TerminalNode -> it.symbol
            else -> null
        } }
        context.stop = children.asReversed().firstNotNullOfOrNull { when (it) {
            is ParserRuleContext -> it.stop
            is org.antlr.v4.runtime.tree.TerminalNode -> it.symbol
            else -> null
        } }
        if (semantic != null) remember(context, semantic)
        return context
    }

    fun <T : ParserRuleContext> child(parent: ParserRuleContext, child: T): T {
        child.setParent(parent)
        parent.addChild(child)
        return child
    }

    fun token(parent: ParserRuleContext, type: Int, text: String): Token = source.token(type, text).also {
        parent.addChild(TerminalNodeImpl(it))
    }
    fun space() = source.append(" ")

    fun remember(context: ParserRuleContext, semantic: SemanticNode) {
        val use = when (semantic) {
            is Expression.Name -> semantic.use
            is TypeSyntax.Name -> semantic.use
            is TypeSyntax.Apply -> (semantic.base as? TypeSyntax.Name)?.use
            else -> null
        }
        val role = if (semantic is TypeSyntax) NameRole.TYPE else NameRole.VALUE
        val constant = (semantic as? Expression.Literal)?.constant
        val type = when (semantic) { is TypeSyntax.Name -> semantic.resolved; is TypeSyntax.Apply -> semantic.resolved; else -> null }
        origins[context] = ProjectionOrigin(semantic, use, use?.let { bindings.name(it, role) },
            constant, constant?.let(bindings::constant), type, type?.let(bindings::type), bindings.diagnosticOrigin(semantic))
    }

    fun expression(value: Expression): P.ExpressionContext = node(P.ExpressionContext(null, -1), value) {
        val trailing = trailingChain(value)
        if (trailing != null) child(this, trailingCall(trailing.first, trailing.second))
        // Contextual lambda typing reads this grammar alternative to carry the expected result.
        else if (value is Expression.Lambda) child(this, primary(value))
        else child(this, binary(value, 0))
    }

    private data class Selection(val member: Expression.Member, val indices: List<Expression.Index>, val call: Expression.Call?)

    /** Selectors after a trailing lambda belong to that expression rule, rather than to a new grouped receiver. */
    private fun trailingChain(value: Expression): Pair<Expression.Call, List<Selection>>? {
        if (value is Expression.Call && value.trailingLambda != null) return value to emptyList()
        val receiver: Expression
        val segment: Selection
        when (value) {
            is Expression.Member -> { receiver = value.receiver; segment = Selection(value, emptyList(), null) }
            is Expression.Call -> {
                val member = value.callee as? Expression.Member ?: return null
                receiver = member.receiver; segment = Selection(member, emptyList(), value)
            }
            is Expression.Index -> {
                val indices = mutableListOf<Expression.Index>()
                var indexed: Expression = value
                while (indexed is Expression.Index) { indices.add(0, indexed); indexed = indexed.receiver }
                val member = indexed as? Expression.Member ?: return null
                receiver = member.receiver; segment = Selection(member, indices, null)
            }
            else -> return null
        }
        val previous = trailingChain(receiver) ?: return null
        return previous.first to (previous.second + segment)
    }

    fun condition(value: Expression): P.ConditionExpressionContext = node(P.ConditionExpressionContext(null, -1), value) {
        child(this, binary(value, 0))
    }

    private fun precedence(operator: String): Int = when (operator) {
        "||" -> 1; "&&" -> 2; "==", "!=", "~=" -> 3; "<", ">", "<=", ">=" -> 4
        "+", "-" -> 5; "*", "/", "%" -> 6; else -> 0
    }

    private fun operator(operator: String): Int = when (operator) {
        "|" -> L.PIPE; "||" -> L.DISJ; "&&" -> L.CONJ; "==" -> L.EQEQ; "!=" -> L.EXCL_EQ; "~=" -> L.WVEQ
        "<" -> L.LANGLE; ">" -> L.RANGLE; "<=" -> L.LE; ">=" -> L.GE; "+" -> L.ADD; "-" -> L.SUB
        "*" -> L.MULT; "/" -> L.SLASH; "%" -> L.MOD; else -> L.Identifier
    }

    private fun binary(value: Expression, level: Int): ParserRuleContext {
        if (level == 7) return cast(value)
        val result: ParserRuleContext = when (level) {
            0 -> P.CommonBinaryOperatorExpressionContext(null, -1)
            1 -> P.ConditionalOrExpressionContext(null, -1)
            2 -> P.ConditionalAndExpressionContext(null, -1)
            3 -> P.EqualityExpressionContext(null, -1)
            4 -> P.RelationalExpressionContext(null, -1)
            5 -> P.AdditiveExpressionContext(null, -1)
            else -> P.MultiplicativeExpressionContext(null, -1)
        }
        return node(result, value) {
            fun emit(operand: Expression) {
                if (operand is Expression.Binary && precedence(operand.operator) == level) {
                    // Only the left spine is flattened. The right tree keeps its original association.
                    emit(operand.left)
                    space()
                    val op = token(this, operator(operand.operator), operand.operator)
                    when (this) {
                        is P.CommonBinaryOperatorExpressionContext -> this.op.add(op)
                        is P.ConditionalOrExpressionContext -> this.op.add(op)
                        is P.ConditionalAndExpressionContext -> this.op.add(op)
                        is P.EqualityExpressionContext -> this.op.add(op)
                        is P.RelationalExpressionContext -> this.op.add(op)
                        is P.AdditiveExpressionContext -> this.op.add(op)
                        is P.MultiplicativeExpressionContext -> this.op.add(op)
                    }
                    space()
                    child(this, binary(operand.right, level + 1))
                } else child(this, binary(operand, level + 1))
            }
            emit(value)
        }
    }

    private fun cast(value: Expression): P.CastExpressionContext = node(P.CastExpressionContext(null, -1), value) {
        if (value is Expression.Cast) {
            child(this, unary(value.value)); space(); token(this, L.AS, "as"); space(); child(this, type(value.target))
        } else child(this, unary(value))
    }

    private fun unary(value: Expression): P.UnaryExpressionContext = node(P.UnaryExpressionContext(null, -1), value) {
        if (value is Expression.Unary) {
            token(this, if (value.operator == "!") L.EXCL else L.SUB, value.operator)
            child(this, unary(value.operand))
        } else child(this, node(P.RightVarExpressionContext(null, -1), value) { child(this, selected(value)) })
    }

    private fun selected(value: Expression): P.VarWithSelectorContext = node(P.VarWithSelectorContext(null, -1), value) {
        selection(this, value)
    }

    private fun selection(parent: P.VarWithSelectorContext, value: Expression) {
        when (value) {
            is Expression.Member -> {
                selection(parent, value.receiver)
                child(parent, selector(value, emptyList(), null))
            }
            is Expression.Index -> {
                val indices = mutableListOf<Expression.Index>()
                var indexed: Expression = value
                while (indexed is Expression.Index) { indices.add(0, indexed); indexed = indexed.receiver }
                val receiver = indexed
                if (receiver is Expression.Member) {
                    selection(parent, receiver.receiver)
                    child(parent, selector(receiver, indices, null))
                } else if (receiver is Expression.Name) base(parent, value) { variableWithSuffix(receiver, indices) }
                else unsupported("The current grammar only indexes names and selected members")
            }
            is Expression.Call -> {
                if (value.trailingLambda != null) base(parent, value) { grouped(value) }
                else when (val callee = value.callee) {
                    is Expression.Member -> {
                        selection(parent, callee.receiver)
                        child(parent, selector(callee, emptyList(), value))
                    }
                    is Expression.Name -> base(parent, value) { functionVariable(value) }
                    else -> unsupported("The current grammar requires a named call target")
                }
            }
            is Expression.PropertyFilter -> child(parent, node(P.JvmAccessExpressionContext(null, -1), value) {
                child(this, node(P.PropertyOperatorContext(null, -1), value) {
                    child(this, primary(value.receiver)); token(this, L.LSQUARE, "[")
                    value.entries.forEachIndexed { index, entry ->
                        if (index != 0) token(this, L.COMMA, ",")
                        child(this, node(P.PropertyOperatorExpressionContext(null, -1), value) {
                            token(this, L.Identifier, entry.name); token(this, L.ASSIGNMENT, "=")
                            child(this, expression(entry.value))
                        })
                    }
                    token(this, L.RSQUARE, "]")
                })
            })
            else -> base(parent, value) { primary(value) }
        }
    }

    private fun base(parent: ParserRuleContext, value: Expression, primary: () -> P.PrimaryContext) {
        child(parent, node(P.JvmAccessExpressionContext(null, -1), value) {
            child(this, node(P.PropertyOperatorContext(null, -1), value) { child(this, primary()) })
        })
    }

    private fun selector(member: Expression.Member, indices: List<Expression.Index>, call: Expression.Call?): P.SelectorContext =
        node(P.SelectorContext(null, -1), call ?: member) {
            token(this, L.DOT, ".")
            if (member.keyword != MemberKeyword.IDENTIFIER) {
                if (indices.isNotEmpty()) unsupported("Keyword members cannot have index suffixes in the current grammar")
                child(this, node(P.MemberKeywordContext(null, -1), member) {
                    token(this, if (member.keyword == MemberKeyword.MAP) L.MAP else L.SET, member.name)
                })
                call?.let { child(this, arguments(it)) }
            } else child(this, node(P.VarContext(null, -1), call ?: member) {
                if (call != null) child(this, functionCall(call, ProjectedName(member.name)))
                else child(this, suffix(member.name, indices, member))
            })
        }

    private fun primary(value: Expression): P.PrimaryContext = when (value) {
        is Expression.Name -> variableWithSuffix(value, emptyList())
        is Expression.Group -> grouped(value)
        is Expression.Call -> if (value.trailingLambda == null && value.callee is Expression.Name) functionVariable(value) else grouped(value)
        is Expression.This -> node(P.PrimaryContext(null, -1), value) { token(this, L.THIS, "this") }
        is Expression.Super -> node(P.PrimaryContext(null, -1), value) { token(this, L.SUPER, "super") }
        is Expression.TypeValue -> node(P.PrimaryContext(null, -1), value) { child(this, type(value.type)) }
        is Expression.Lambda -> node(P.PrimaryContext(null, -1), value) { child(this, inlineLambda(value)) }
        is Expression.Range -> node(P.PrimaryContext(null, -1), value) { child(this, range(value)) }
        is Expression.Literal, is Expression.Coordinate, is Expression.Selector, is Expression.ListValue,
        is Expression.Compound, is Expression.NbtArray, is Expression.InterpolatedString ->
            node(P.PrimaryContext(null, -1), value) { child(this, this@ExpressionProjection.value(value)) }
        else -> grouped(value)
    }

    private fun grouped(value: Expression): P.PrimaryContext = node(P.PrimaryContext(null, -1), value) {
        child(this, node(P.VarContext(null, -1), value) {
            child(this, node(P.BucketExpressionContext(null, -1), value) {
                token(this, L.LPAREN, "(")
                val inner = if (value is Expression.Group) value.value else value
                inner?.let { child(this, expression(it)) }
                token(this, L.RPAREN, ")")
            })
        })
    }

    private fun variableWithSuffix(name: Expression.Name, indices: List<Expression.Index>): P.PrimaryContext =
        node(P.PrimaryContext(null, -1), indices.lastOrNull() ?: name) {
            child(this, node(P.VarContext(null, -1), name) {
                val bound = bindings.name(name.use, NameRole.VALUE)
                // The author namespace remains in ProjectionOrigin; bare values spell only the identifier.
                child(this, suffix(bound.name, indices, name))
            })
        }

    private fun suffix(name: String, indices: List<Expression.Index>, semantic: Expression): P.VarWithSuffixContext =
        node(P.VarWithSuffixContext(null, -1), semantic) {
            token(this, L.Identifier, name)
            indices.forEach { index -> child(this, node(P.IdentifierSuffixContext(null, -1), index) {
                token(this, L.LSQUARE, "["); index.index?.let { child(this, expression(it)) }; token(this, L.RSQUARE, "]")
            }) }
        }

    private fun functionVariable(call: Expression.Call): P.PrimaryContext = node(P.PrimaryContext(null, -1), call) {
        child(this, node(P.VarContext(null, -1), call) {
            val name = call.callee as? Expression.Name ?: unsupported("A call requires a named target")
            child(this, functionCall(call, bindings.name(name.use, NameRole.VALUE)))
        })
    }

    private fun namespace(parent: ParserRuleContext, name: ProjectedName) {
        name.namespace?.let { namespace ->
            child(parent, node(P.NamespacePathContext(null, -1)) {
                namespace.split('.').forEachIndexed { index, segment ->
                    if (index != 0) token(this, L.DOT, ".")
                    child(this, node(P.NamespaceSegmentContext(null, -1)) { token(this, L.Identifier, segment) })
                }
            })
            token(parent, L.COLON, ":")
        }
    }

    private fun functionCall(call: Expression.Call, name: ProjectedName): P.FunctionCallContext =
        node(P.FunctionCallContext(null, -1), call) {
            child(this, node(P.NamespaceIDContext(null, -1), call.callee) {
                namespace(this, name); token(this, L.Identifier, name.name)
            })
            child(this, arguments(call))
        }

    private fun arguments(call: Expression.Call): P.ArgumentsContext = node(P.ArgumentsContext(null, -1), call) {
        if (call.readonly.isNotEmpty()) child(this, readonly(call.readonly, call))
        child(this, node(P.NormalArgsContext(null, -1), call) {
            token(this, L.LPAREN, "(")
            if (call.normal.any { it.expression is Expression.Lambda }) {
                child(this, node(P.CallArgumentListContext(null, -1), call) {
                    call.normal.forEachIndexed { index, argument ->
                        if (index != 0) token(this, L.COMMA, ",")
                        child(this, node(P.CallArgumentContext(null, -1), argument.expression) {
                            val argumentValue = argument.expression
                            if (argumentValue is Expression.Lambda) child(this, inlineLambda(argumentValue))
                            else child(this, expression(argumentValue))
                        })
                    }
                })
            } else if (call.normal.isNotEmpty()) child(this, expressionList(call.normal.map { it.expression }, call))
            token(this, L.RPAREN, ")")
        })
    }

    private fun readonly(values: List<Expression>, semantic: SemanticNode): P.ReadOnlyArgsContext =
        node(P.ReadOnlyArgsContext(null, -1), semantic) {
            token(this, L.LANGLE, "<")
            if (values.isNotEmpty()) child(this, expressionList(values, semantic))
            token(this, L.RANGLE, ">")
        }

    private fun expressionList(values: List<Expression>, semantic: SemanticNode): P.ExpressionListContext =
        node(P.ExpressionListContext(null, -1), semantic) {
            values.forEachIndexed { index, value ->
                if (index != 0) token(this, L.COMMA, ",")
                child(this, expression(value))
            }
        }

    private fun trailingCall(call: Expression.Call, after: List<Selection> = emptyList()): P.LambdaCallExpressionContext =
        node(P.LambdaCallExpressionContext(null, -1), call) {
            when (val callee = call.callee) {
                is Expression.Name -> child(this, functionCall(call, bindings.name(callee.use, NameRole.VALUE)))
                is Expression.Member -> {
                    val receiver = selected(callee.receiver)
                    // The grammar places the root JVM rule and its selectors directly in this rule.
                    receiver.children.orEmpty().toList().forEach { part ->
                        if (part is ParserRuleContext) child(this, part)
                    }
                    child(this, selector(callee, emptyList(), call))
                }
                is Expression.Index -> {
                    if (call.readonly.isNotEmpty() || call.normal.isNotEmpty())
                        unsupported("An indexed trailing-lambda target cannot carry parenthesized arguments")
                    val indices = mutableListOf<Expression.Index>()
                    var indexed: Expression = callee
                    while (indexed is Expression.Index) { indices.add(0, indexed); indexed = indexed.receiver }
                    val receiver = indexed
                    if (receiver is Expression.Name) {
                        val name = bindings.name(receiver.use, NameRole.VALUE)
                        if (name.namespace != null) unsupported("An indexed target cannot carry a namespace")
                        child(this, suffix(name.name, indices, receiver))
                    } else if (receiver is Expression.Member) {
                        selected(callee).children.orEmpty().toList().forEach { part ->
                            if (part is ParserRuleContext) child(this, part)
                        }
                    } else unsupported("An indexed trailing-lambda target requires a name or member")
                }
                else -> unsupported("A trailing lambda requires a named or selected call target")
            }
            space(); child(this, inlineLambda(call.trailingLambda as Expression.Lambda))
            after.forEach { child(this, selector(it.member, it.indices, it.call)) }
        }

    fun inlineLambda(value: Expression.Lambda): P.InlineLambdaContext = structures.lambda(value, this).also { remember(it, value) }

    private fun range(value: Expression.Range): P.RangeContext = node(P.RangeContext(null, -1), value) {
        fun endpoint(expression: Expression): P.Range1Context = node(P.Range1Context(null, -1), expression) {
            if (expression is Expression.Name || expression is Expression.Group ||
                expression is Expression.Call && expression.callee is Expression.Name && expression.trailingLambda == null) {
                val primary = primary(expression)
                child(this, primary.`var`())
            } else if (isValue(expression)) child(this, value(expression))
            else {
                val primary = grouped(expression)
                child(this, primary.`var`())
            }
        }
        value.lower?.let { num1 = child(this, endpoint(it)) }
        token(this, L.RANGE, "..")
        value.upper?.let { num2 = child(this, endpoint(it)) }
    }

    private fun isValue(value: Expression) = value is Expression.Literal || value is Expression.Coordinate ||
        value is Expression.Selector || value is Expression.ListValue || value is Expression.Compound ||
        value is Expression.NbtArray || value is Expression.InterpolatedString

    fun value(value: Expression): P.ValueContext {
        if (!isValue(value)) unsupported("Default root is not a current grammar value")
        return node(P.ValueContext(null, -1), value) {
            when (value) {
                is Expression.Literal -> literal(this, value)
                is Expression.Selector -> token(this, L.TargetSelector, when (value.kind) {
                    SelectorKind.ALL_PLAYERS -> "@a"; SelectorKind.RANDOM_PLAYER -> "@r"; SelectorKind.NEAREST_PLAYER -> "@p"
                    SelectorKind.SELF -> "@s"; SelectorKind.ALL_ENTITIES -> "@e"
                })
                is Expression.Coordinate -> child(this, coordinate(value))
                is Expression.InterpolatedString -> child(this, string(value))
                else -> child(this, nbt(value))
            }
        }
    }

    private fun literal(parent: P.ValueContext, value: Expression.Literal) {
        when (val constant = bindings.constant(value.constant)) {
            ConstantValue.Null -> token(parent, L.NULL, "null")
            is ConstantValue.Text -> if ('\n' !in constant.value && '\r' !in constant.value)
                token(parent, L.LineString, quote(constant.value))
            else child(parent, string(Expression.InterpolatedString(value.scope, listOf(TextPart.Text(constant.value)))))
            is ConstantValue.Integral, is ConstantValue.FloatBits, is ConstantValue.DoubleBits,
            is ConstantValue.Bool, is ConstantValue.Typed -> {
                val scalar = scalar(value.constant, null, value)
                if (scalar == null) token(parent, L.NULL, "null")
                else child(parent, node(P.NbtValueContext(null, -1), value) { child(this, scalar) })
            }
            // A display placeholder cannot restore such a value. The semantic constant stays in the side table.
            else -> token(parent, L.NULL, "null")
        }
    }

    private fun scalar(reference: ConstantRef, expected: String?, semantic: SemanticNode): ParserRuleContext? {
        var constant = bindings.constant(reference)
        var key = expected
        val visited = mutableSetOf<ConstantRef>()
        var current = reference
        while (constant is ConstantValue.Typed) {
            check(visited.add(current)) { "Cyclic constant projection" }
            val declaredKey = bindings.builtinKey(constant.type) ?: return null
            if (key != null && key != declaredKey) return null
            key = declaredKey
            current = constant.value
            constant = bindings.constant(current)
        }
        val text: String
        val kind: Int
        val context: ParserRuleContext
        when (constant) {
            is ConstantValue.Integral -> {
                key = key ?: if (constant.value in Int.MIN_VALUE..Int.MAX_VALUE) "int" else "long"
                val bounds = when (key) {
                    "byte" -> Byte.MIN_VALUE.toLong()..Byte.MAX_VALUE.toLong()
                    "short" -> Short.MIN_VALUE.toLong()..Short.MAX_VALUE.toLong()
                    "int" -> Int.MIN_VALUE.toLong()..Int.MAX_VALUE.toLong()
                    else -> Long.MIN_VALUE..Long.MAX_VALUE
                }
                if (constant.value !in bounds) return null
                when (key) {
                    "byte" -> { context = P.NbtByteContext(null, -1); kind = L.NBTByte; text = constant.value.toString() + "b" }
                    "short" -> { context = P.NbtShortContext(null, -1); kind = L.NBTShort; text = constant.value.toString() + "s" }
                    "int" -> { context = P.NbtIntContext(null, -1); kind = L.NBTInt; text = constant.value.toString() }
                    "long" -> { context = P.NbtLongContext(null, -1); kind = L.NBTLong; text = constant.value.toString() + "L" }
                    else -> return null
                }
            }
            is ConstantValue.FloatBits -> {
                if (key != null && key != "float") return null
                val number = Float.fromBits(constant.bits)
                if (!number.isFinite()) return null
                text = number.toString() + "f"; kind = L.NBTFloat; context = P.NbtFloatContext(null, -1)
            }
            is ConstantValue.DoubleBits -> {
                if (key != null && key != "double") return null
                val number = Double.fromBits(constant.bits)
                if (!number.isFinite()) return null
                text = number.toString() + "d"; kind = L.NBTDouble; context = P.NbtDoubleContext(null, -1)
            }
            is ConstantValue.Bool -> {
                if (key != null && key != "bool") return null
                val boolean = constant.value
                return node(P.NbtBoolContext(null, -1), semantic) {
                    token(this, if (boolean) L.TRUE else L.FALSE, boolean.toString())
                }
            }
            else -> return null
        }
        return node(context, semantic) {
            if (text.startsWith('-')) token(this, L.SUB, "-")
            token(this, kind, text.removePrefix("-"))
        }
    }

    private fun nbt(value: Expression): P.NbtValueContext = node(P.NbtValueContext(null, -1), value) {
        when (value) {
            is Expression.ListValue -> child(this, node(P.NbtListContext(null, -1), value) {
                token(this, L.LSQUARE, "[")
                value.elements.forEachIndexed { index, element ->
                    if (index != 0) token(this, L.COMMA, ",")
                    child(this, expression(element))
                }
                token(this, L.RSQUARE, "]")
            })
            is Expression.Compound -> child(this, node(P.NbtCompoundContext(null, -1), value) {
                token(this, L.LCURL, "{")
                value.entries.forEachIndexed { index, entry ->
                    if (index != 0) token(this, L.COMMA, ",")
                    child(this, node(P.NbtKeyValuePairContext(null, -1), value) {
                        key = token(this, L.Identifier, entry.key)
                        token(this, L.COLON, ":"); child(this, expression(entry.value))
                    })
                }
                token(this, L.RCURL, "}")
            })
            is Expression.NbtArray -> {
                val array: ParserRuleContext
                val start: Int
                val spelling: String
                val key: String
                when (value.element) {
                    NbtArrayElement.BYTE -> { array = P.NbtByteArrayContext(null, -1); start = L.NBT_BYTE_ARRAY_BEGIN; spelling = "[B;"; key = "byte" }
                    NbtArrayElement.INT -> { array = P.NbtIntArrayContext(null, -1); start = L.NBT_INT_ARRAY_BEGIN; spelling = "[I;"; key = "int" }
                    NbtArrayElement.LONG -> { array = P.NbtLongArrayContext(null, -1); start = L.NBT_LONG_ARRAY_BEGIN; spelling = "[L;"; key = "long" }
                }
                child(this, node(array, value) {
                    token(this, start, spelling)
                    value.values.forEachIndexed { index, reference ->
                        if (index != 0) token(this, L.COMMA, ",")
                        child(this, scalar(reference, key, value) ?: unsupported("Array element has no current scalar representation"))
                    }
                    token(this, L.RSQUARE, "]")
                })
            }
            else -> unsupported("Expression is not an NBT container")
        }
    }

    private fun coordinate(value: Expression.Coordinate): P.CoordinateContext = node(P.CoordinateContext(null, -1), value) {
        value.dimensions.forEachIndexed { index, dimension ->
            if (index != 0) space()
            child(this, node(P.CoordinateDimensionContext(null, -1), value) {
                if (dimension.mode == DimensionMode.ABSOLUTE) child(this,
                    scalar(dimension.value!!, null, value) ?: unsupported("Coordinate has no numeric representation"))
                else {
                    val prefix = if (dimension.mode == DimensionMode.RELATIVE) "~" else "^"
                    val number = dimension.value?.let { ref -> numericText(ref) }
                    token(this, L.RelativeValue, prefix + number.orEmpty())
                }
            })
        }
    }

    private fun numericText(reference: ConstantRef): String = when (val constant = bindings.constant(reference)) {
        is ConstantValue.Typed -> numericText(constant.value)
        is ConstantValue.Integral -> constant.value.toString()
        is ConstantValue.FloatBits -> Float.fromBits(constant.bits).takeIf { it.isFinite() }?.toString()
            ?: unsupported("Coordinate has no finite display value")
        else -> unsupported("Relative coordinate requires an integral or float value")
    }

    private fun string(value: Expression.InterpolatedString): P.MultiLineStringLiteralContext =
        node(P.MultiLineStringLiteralContext(null, -1), value) {
            token(this, L.TRIPLE_QUOTE_OPEN, "\"\"\"")
            value.parts.forEach { part -> child(this, node(P.MultiLineStringContentContext(null, -1), value) {
                when (part) {
                    is TextPart.Text -> token(this, L.MultiLineStrText, part.value)
                    is TextPart.Interpolation -> child(this, node(P.MultiLineStringExpressionContext(null, -1), part.expression) {
                        token(this, L.MultiLineStrExprStart, "\${"); child(this, expression(part.expression)); token(this, L.RCURL, "}")
                    })
                }
            }) }
            token(this, L.TRIPLE_QUOTE_CLOSE, "\"\"\"")
        }

    fun type(value: TypeSyntax): P.TypeContext = node(P.TypeContext(null, -1), value) { child(this, typeBody(value)) }

    fun returnType(value: TypeSyntax): P.FunctionReturnTypeContext = node(P.FunctionReturnTypeContext(null, -1), value) {
        if (value is TypeSyntax.Void) token(this, L.VOID, "void") else child(this, type(value))
    }

    private fun typeBody(value: TypeSyntax): P.TypeBodyContext = node(P.TypeBodyContext(null, -1), value) {
        when (value) {
            is TypeSyntax.Name -> typeName(this, value)
            is TypeSyntax.Inferred -> className(this, ProjectedName("__inferred_type__"), value)
            is TypeSyntax.Apply -> {
                val base = value.base as? TypeSyntax.Name ?: unsupportedType("Current type application requires a named base")
                className(this, bindings.name(base.use, NameRole.TYPE), base)
                child(this, readonly(value.readonly, value))
            }
            is TypeSyntax.Container -> {
                val (kind, spelling) = when (value.kind) {
                    ContainerKind.LIST -> L.LIST to "list"; ContainerKind.IMMUTABLE_LIST -> L.IMMUTABLE_LIST to "ImmutableList"
                    ContainerKind.MAP -> L.MAP to "map"; ContainerKind.DICT -> L.DICT to "dict"
                }
                token(this, kind, spelling); token(this, L.LANGLE, "<"); child(this, type(value.argument)); token(this, L.RANGLE, ">")
            }
            is TypeSyntax.Vector -> token(this, L.VecType, "vec${value.dimensions}")
            is TypeSyntax.Entity -> {
                if (value.limit != null || value.names.isNotEmpty()) {
                    token(this, L.ENTITY, "entity")
                    token(this, L.LANGLE, "<")
                    value.limit?.let { child(this, scalar(it, "int", value)
                        ?: unsupportedType("Entity limit requires an integer")) }
                    value.names.forEachIndexed { index, name ->
                        if (index != 0 || value.limit != null) token(this, L.COMMA, ",")
                        token(this, L.LineString, quote(name))
                    }
                    token(this, L.RANGLE, ">")
                } else child(this, node(P.NormalTypeContext(null, -1), value) { token(this, L.ENTITY, "entity") })
            }
            is TypeSyntax.Union -> child(this, node(P.UnionTypeContext(null, -1), value) {
                token(this, L.LPAREN, "(")
                value.alternatives.forEachIndexed { index, alternative ->
                    if (index != 0) token(this, L.PIPE, "|")
                    child(this, type(alternative))
                }
                token(this, L.RPAREN, ")")
            })
            is TypeSyntax.Intersection -> child(this, node(P.UnionTemplateTypeContext(null, -1), value) {
                token(this, L.LPAREN, "(")
                value.components.forEachIndexed { index, component ->
                    if (index != 0) token(this, L.UNION, "&")
                    child(this, type(component))
                }
                token(this, L.RPAREN, ")")
            })
            is TypeSyntax.AnonymousData -> child(this, structures.anonymousType(value, this@ExpressionProjection).also { remember(it, value) })
            is TypeSyntax.Function -> child(this, node(P.FunctionTypeContext(null, -1), value) {
                token(this, L.LPAREN, "(")
                value.parameters.forEachIndexed { index, parameter ->
                    if (index != 0) token(this, L.COMMA, ",")
                    child(this, type(parameter))
                }
                token(this, L.RPAREN, ")"); token(this, L.ARROW, "->"); child(this, returnType(value.result))
            })
            is TypeSyntax.Void -> unsupportedType("void is only representable as a function return type")
        }
    }

    private fun typeName(parent: P.TypeBodyContext, value: TypeSyntax.Name) {
        val name = bindings.name(value.use, NameRole.TYPE)
        val builtin = if (name.namespace == null) builtins[name.name] else null
        if (builtin != null) child(parent, node(P.NormalTypeContext(null, -1), value) { token(this, builtin, name.name) })
        else className(parent, name, value)
    }

    private fun className(parent: ParserRuleContext, name: ProjectedName, semantic: TypeSyntax) {
        child(parent, node(P.ClassNameContext(null, -1), semantic) {
            namespace(this, name)
            child(this, node(P.ClassWithoutNamespaceContext(null, -1), semantic) { token(this, L.Identifier, name.name) })
        })
    }

    private fun quote(value: String) = "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\""
    private fun unsupported(message: String): Nothing = throw SyntaxProjectionException("LIB_BODY_UNSUPPORTED", message)
    private fun unsupportedType(message: String): Nothing = throw SyntaxProjectionException("LIB_TYPE_UNSUPPORTED", message)

    companion object {
        private val builtins = mapOf("object" to L.OBJECT, "int" to L.INT, "entity" to L.ENTITY,
            "bool" to L.BOOL, "byte" to L.BYTE, "short" to L.SHORT, "long" to L.LONG, "float" to L.FLOAT,
            "double" to L.DOUBLE, "selector" to L.SELECTOR, "string" to L.STRING, "text" to L.JTEXT,
            "nbt" to L.NBT, "Type" to L.TYPE, "any" to L.ANY, "ByteArray" to L.BYTEARRAY,
            "IntArray" to L.INTARRAY, "LongArray" to L.LONGARRAY)
    }
}

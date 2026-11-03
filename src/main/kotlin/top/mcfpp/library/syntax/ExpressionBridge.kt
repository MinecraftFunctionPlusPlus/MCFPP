package top.mcfpp.library.syntax

import org.antlr.v4.runtime.ParserRuleContext
import org.antlr.v4.runtime.Token
import org.antlr.v4.runtime.tree.ErrorNode
import org.antlr.v4.runtime.tree.ParseTree
import top.mcfpp.antlr.mcfppParser as Parser

enum class NameRole { VALUE, TYPE }

/** The declaration/body capturer supplies author-environment bindings and table interning. */
interface ExpressionCaptureBindings {
    fun scope(site: ParserRuleContext): ScopeId
    fun name(name: String, namespace: String?, role: NameRole, site: ParserRuleContext): NameUse
    fun builtinType(key: String, site: ParserRuleContext): TypeRef
    fun internConstant(value: ConstantValue, site: ParserRuleContext): ConstantRef
    /** null keeps dynamic receiver lookup; a non-null list freezes the author's callable candidates. */
    fun memberCandidates(receiver: Expression, name: String, site: ParserRuleContext): List<DeclarationReference>? = null
    fun resolvedType(site: ParserRuleContext): TypeRef? = null
    fun owner(site: ParserRuleContext): DeclarationReference =
        throw SyntaxCaptureException("LIB_BODY_UNSUPPORTED", "This/super requires its declaration owner")

    /** Allocate a synthetic D and B, capture statements there, and capture the distinct tail here. */
    fun lambda(site: Parser.InlineLambdaContext, capture: ExpressionBridge): Expression.Lambda =
        throw SyntaxCaptureException("LIB_BODY_UNSUPPORTED", "Lambda capture requires a declaration/body capturer")

    fun anonymousType(site: Parser.AnonymousTemplateTypeContext, capture: ExpressionBridge): DeclarationId =
        throw SyntaxCaptureException("LIB_TYPE_UNSUPPORTED", "Anonymous data capture requires a declaration capturer")
}

class SyntaxCaptureException(val code: String, message: String) : IllegalArgumentException(message)

/** Converts current parser rules into language operations without retaining or reparsing source. */
class ExpressionBridge(private val bindings: ExpressionCaptureBindings) {
    fun expression(site: ParserRuleContext): Expression {
        requireValid(site)
        return capture(site)
    }

    fun type(site: Parser.TypeContext): TypeSyntax {
        requireValid(site)
        return typeBody(site.typeBody())
    }

    fun returnType(site: Parser.FunctionReturnTypeContext): TypeSyntax {
        requireValid(site)
        return site.type()?.let(::type) ?: TypeSyntax.Void(scope(site))
    }

    fun lambda(site: Parser.InlineLambdaContext): Expression.Lambda {
        requireValid(site)
        if (site.lambdaBody() == null) unsupported("Legacy lambda syntax cannot be exported")
        val captured = bindings.lambda(site, this)
        val explicit = site.lambdaParameterList() != null
        if (captured.explicitParameters != explicit ||
            (explicit && captured.parameters.size != site.lambdaParameterList().lambdaParameter().size))
            unsupported("Lambda capturer did not preserve the explicit parameter list")
        if ((captured.tail != null) != (site.lambdaBody().lambdaTailExpression() != null))
            unsupported("Lambda capturer did not preserve its distinct tail expression")
        return captured
    }

    /** Shared by native commands and interpolated strings; text is already decoded lexer content. */
    fun commandParts(site: Parser.OrgCommandContext): List<TextPart> {
        requireValid(site)
        return site.orgCommandContent().map { content ->
            content.orgCommandExpression()?.let { TextPart.Interpolation(capture(it.expression())) }
                ?: TextPart.Text(content.OrgCommandText().text)
        }
    }

    private fun scope(site: ParserRuleContext) = bindings.scope(site)

    private fun capture(site: ParserRuleContext): Expression = when (site) {
        is Parser.ExpressionContext -> site.lambdaCallExpression()?.let(::lambdaCall)
            ?: site.primary()?.let(::primary) ?: capture(site.commonBinaryOperatorExpression())
        is Parser.ConditionExpressionContext -> site.primary()?.let(::primary)
            ?: capture(site.commonBinaryOperatorExpression())
        is Parser.CommonBinaryOperatorExpressionContext -> fold(site, site.conditionalOrExpression(), site.op)
        is Parser.ConditionalOrExpressionContext -> fold(site, site.conditionalAndExpression(), site.op)
        is Parser.ConditionalAndExpressionContext -> fold(site, site.equalityExpression(), site.op)
        is Parser.EqualityExpressionContext -> fold(site, site.relationalExpression(), site.op)
        is Parser.RelationalExpressionContext -> fold(site, site.additiveExpression(), site.op)
        is Parser.AdditiveExpressionContext -> fold(site, site.multiplicativeExpression(), site.op)
        is Parser.MultiplicativeExpressionContext -> fold(site, site.castExpression(), site.op)
        is Parser.CastExpressionContext -> capture(site.unaryExpression()).let { value ->
            site.type()?.let { Expression.Cast(scope(site), value, type(it)) } ?: value
        }
        is Parser.UnaryExpressionContext -> site.unaryExpression()?.let {
            Expression.Unary(scope(site), if (site.EXCL() != null) "!" else "-", capture(it))
        } ?: capture(site.rightVarExpression())
        is Parser.RightVarExpressionContext -> capture(site.varWithSelector())
        is Parser.VarWithSelectorContext -> site.selector().fold(capture(site.jvmAccessExpression()), ::select)
        is Parser.JvmAccessExpressionContext -> capture(site.propertyOperator())
        is Parser.PropertyOperatorContext -> primary(site.primary()).let { receiver ->
            if (site.propertyOperatorExpression().isEmpty()) receiver else Expression.PropertyFilter(
                scope(site), receiver, site.propertyOperatorExpression().map {
                    PropertyEntry(it.Identifier().text, capture(it.expression()))
                }
            )
        }
        is Parser.PrimaryContext -> primary(site)
        is Parser.VarContext -> variable(site)
        is Parser.VarWithSuffixContext -> suffixed(site, null)
        is Parser.FunctionCallContext -> functionCall(site, null)
        is Parser.BucketExpressionContext -> Expression.Group(scope(site), site.expression()?.let(::capture))
        is Parser.ValueContext -> value(site)
        is Parser.NbtValueContext -> nbt(site)
        is Parser.InlineLambdaContext -> lambda(site)
        is Parser.RangeContext -> Expression.Range(scope(site), site.num1?.let(::capture), site.num2?.let(::capture))
        is Parser.Range1Context -> site.`var`()?.let(::variable) ?: value(site.value())
        else -> unsupported("Rule has no expression operation")
    }

    private fun fold(site: ParserRuleContext, operands: List<ParserRuleContext>, operators: List<Token>): Expression {
        if (operands.size != operators.size + 1) unsupported("Incomplete binary expression")
        var result = capture(operands.first())
        operators.forEachIndexed { index, operator ->
            result = Expression.Binary(scope(site), operator.text, result, capture(operands[index + 1]))
        }
        return result
    }

    private fun primary(site: Parser.PrimaryContext): Expression = when {
        site.range() != null -> capture(site.range())
        site.value() != null -> value(site.value())
        site.inlineLambda() != null -> lambda(site.inlineLambda())
        site.`var`() != null -> variable(site.`var`())
        site.THIS() != null -> Expression.This(scope(site), bindings.owner(site))
        site.SUPER() != null -> Expression.Super(scope(site), bindings.owner(site))
        site.type() != null -> Expression.TypeValue(scope(site), type(site.type()))
        else -> unsupported("Incomplete primary expression")
    }

    private fun variable(site: Parser.VarContext): Expression = site.bucketExpression()?.let(::capture)
        ?: site.functionCall()?.let { functionCall(it, null) }
        ?: suffixed(site.varWithSuffix(), null)

    private fun suffixed(site: Parser.VarWithSuffixContext, receiver: Expression?): Expression {
        val name = site.Identifier().text
        var result: Expression = if (receiver == null)
            Expression.Name(scope(site), bindings.name(name, null, NameRole.VALUE, site))
        else Expression.Member(scope(site), receiver, name, candidates = bindings.memberCandidates(receiver, name, site)?.toList())
        site.identifierSuffix().forEach { suffix ->
            result = Expression.Index(scope(suffix), result, suffix.expression()?.let(::capture))
        }
        return result
    }

    private fun functionCall(site: Parser.FunctionCallContext, receiver: Expression?): Expression.Call {
        val id = site.namespaceID()
        val namespace = id.namespacePath()?.text
        val callee = if (receiver == null)
            Expression.Name(scope(id), bindings.name(id.Identifier().text, namespace, NameRole.VALUE, id))
        else {
            if (namespace != null) unsupported("A member name cannot have a namespace qualifier")
            Expression.Member(scope(id), receiver, id.Identifier().text,
                candidates = bindings.memberCandidates(receiver, id.Identifier().text, site)?.toList())
        }
        return arguments(callee, site.arguments())
    }

    private fun arguments(callee: Expression, site: Parser.ArgumentsContext): Expression.Call {
        val readonly = site.readOnlyArgs()?.expressionList()?.expression().orEmpty().map(::capture)
        val normal = site.normalArgs()
        val values = normal.expressionList()?.expression()?.map { Argument(capture(it)) }
            ?: normal.callArgumentList()?.callArgument()?.map { argument ->
                if (argument.legacy != null) unsupported("Legacy ref expression cannot be exported")
                Argument(argument.inlineLambda()?.let(::lambda) ?: capture(argument.expression()))
            }.orEmpty()
        return Expression.Call(scope(site), callee, readonly, values)
    }

    private fun select(receiver: Expression, site: Parser.SelectorContext): Expression {
        site.memberKeyword()?.let { keyword ->
            val member = Expression.Member(scope(site), receiver, keyword.text,
                if (keyword.MAP() != null) MemberKeyword.MAP else MemberKeyword.SET,
                bindings.memberCandidates(receiver, keyword.text, site)?.toList())
            return site.arguments()?.let { arguments(member, it) } ?: member
        }
        val variable = site.`var`() ?: unsupported("Incomplete member selection")
        variable.functionCall()?.let { return functionCall(it, receiver) }
        variable.varWithSuffix()?.let { return suffixed(it, receiver) }
        unsupported("A parenthesized expression is not a persistent member name")
    }

    private fun lambdaCall(site: Parser.LambdaCallExpressionContext): Expression {
        val lambdaSite = site.inlineLambda()
        // Use child order only while capturing; no token positions become persistent identities.
        val children = site.children.orEmpty()
        val lambdaIndex = children.indexOf(lambdaSite)
        val before = site.selector().filter { children.indexOf(it) < lambdaIndex }
        val after = site.selector().filter { children.indexOf(it) > lambdaIndex }
        var result = site.jvmAccessExpression()?.let(::capture)
            ?: site.functionCall()?.let { functionCall(it, null) }
            ?: suffixed(site.varWithSuffix(), null)
        before.forEach { result = select(result, it) }
        val trailing = lambda(lambdaSite)
        val target = result
        result = if (target is Expression.Call) {
            if (target.trailingLambda != null) unsupported("A call cannot have two trailing lambdas")
            target.copy(trailingLambda = trailing)
        } else Expression.Call(scope(site), target, emptyList(), emptyList(), trailing)
        after.forEach { result = select(result, it) }
        return result
    }

    private fun value(site: Parser.ValueContext): Expression = when {
        site.coordinate() != null -> Expression.Coordinate(scope(site), site.coordinate().coordinateDimension().map(::dimension))
        site.LineString() != null -> literal(site, ConstantValue.Text(decodeString(site.LineString().text)))
        site.multiLineStringLiteral() != null -> interpolated(site.multiLineStringLiteral())
        site.nbtValue() != null -> nbt(site.nbtValue())
        site.NULL() != null -> literal(site, ConstantValue.Null)
        site.TargetSelector() != null -> Expression.Selector(scope(site), when (site.TargetSelector().text) {
            "@a" -> SelectorKind.ALL_PLAYERS
            "@r" -> SelectorKind.RANDOM_PLAYER
            "@p" -> SelectorKind.NEAREST_PLAYER
            "@s" -> SelectorKind.SELF
            "@e" -> SelectorKind.ALL_ENTITIES
            else -> unsupported("Unknown selector kind")
        })
        else -> unsupported("Incomplete literal value")
    }

    private fun nbt(site: Parser.NbtValueContext): Expression = when {
        site.LineString() != null -> literal(site, ConstantValue.Text(decodeString(site.LineString().text)))
        site.nbtList() != null -> Expression.ListValue(scope(site), site.nbtList().expression().map(::capture))
        site.nbtCompound() != null -> Expression.Compound(scope(site), site.nbtCompound().nbtKeyValuePair().map {
            CompoundEntry(it.key.text, capture(it.expression()))
        })
        site.nbtByteArray() != null -> Expression.NbtArray(scope(site), NbtArrayElement.BYTE,
            site.nbtByteArray().nbtByte().map { scalar(it, "byte") })
        site.nbtIntArray() != null -> Expression.NbtArray(scope(site), NbtArrayElement.INT,
            site.nbtIntArray().nbtInt().map { scalar(it, "int") })
        site.nbtLongArray() != null -> Expression.NbtArray(scope(site), NbtArrayElement.LONG,
            site.nbtLongArray().nbtLong().map { scalar(it, "long") })
        else -> {
            val ref = when {
                site.nbtByte() != null -> scalar(site.nbtByte(), "byte")
                site.nbtShort() != null -> scalar(site.nbtShort(), "short")
                site.nbtInt() != null -> scalar(site.nbtInt(), "int")
                site.nbtLong() != null -> scalar(site.nbtLong(), "long")
                site.nbtFloat() != null -> scalar(site.nbtFloat(), "float")
                site.nbtDouble() != null -> scalar(site.nbtDouble(), "double")
                site.nbtBool() != null -> bindings.internConstant(ConstantValue.Bool(site.nbtBool().TRUE() != null), site)
                else -> unsupported("Incomplete NBT scalar")
            }
            Expression.Literal(scope(site), ref)
        }
    }

    private fun scalar(site: ParserRuleContext, key: String): ConstantRef {
        val text = site.text
        val payload = try {
            when (key) {
                "float" -> ConstantValue.FloatBits(text.removeSuffix("f").removeSuffix("F").toFloat().toRawBits())
                "double" -> ConstantValue.DoubleBits(text.dropLast(1).toDouble().toRawBits())
                else -> {
                    val raw = if (key == "int") text else text.dropLast(1)
                    val parsed = integral(raw)
                    val bounds = when (key) {
                        "byte" -> Byte.MIN_VALUE.toLong()..Byte.MAX_VALUE.toLong()
                        "short" -> Short.MIN_VALUE.toLong()..Short.MAX_VALUE.toLong()
                        "int" -> Int.MIN_VALUE.toLong()..Int.MAX_VALUE.toLong()
                        else -> Long.MIN_VALUE..Long.MAX_VALUE
                    }
                    if (parsed !in bounds) unsupported("$key literal is outside its value range")
                    ConstantValue.Integral(parsed)
                }
            }
        } catch (failure: NumberFormatException) {
            throw SyntaxCaptureException("LIB_BODY_UNSUPPORTED", "Invalid $key literal: $text")
        }
        val value = bindings.internConstant(payload, site)
        return bindings.internConstant(ConstantValue.Typed(bindings.builtinType(key, site), value), site)
    }

    private fun integral(text: String): Long {
        val negative = text.startsWith('-')
        val digits = text.removePrefix("-")
        // Decimal spelling (including leading zeros) follows current NBT literal evaluation.
        if (!digits.startsWith("0x")) return text.toLong()
        val magnitude = digits.drop(2).toULong(16)
        if (negative && magnitude == 0x8000_0000_0000_0000uL) return Long.MIN_VALUE
        if (magnitude > Long.MAX_VALUE.toULong()) throw NumberFormatException("Integral overflow")
        return if (negative) -magnitude.toLong() else magnitude.toLong()
    }

    private fun literal(site: ParserRuleContext, value: ConstantValue) =
        Expression.Literal(scope(site), bindings.internConstant(value, site))

    private fun dimension(site: Parser.CoordinateDimensionContext): Dimension {
        site.RelativeValue()?.let { relative ->
            val mode = if (relative.text[0] == '~') DimensionMode.RELATIVE else DimensionMode.LOCAL
            val number = relative.text.drop(1)
            if (number.isEmpty()) return Dimension(mode, null)
            val payload = number.toLongOrNull()?.let { ConstantValue.Integral(it) }
                ?: number.removeSuffix("f").removeSuffix("F").toFloatOrNull()?.let { ConstantValue.FloatBits(it.toRawBits()) }
                ?: unsupported("Invalid relative coordinate")
            return Dimension(mode, bindings.internConstant(payload, site))
        }
        val value = site.nbtInt()?.let { scalar(it, "int") }
            ?: site.nbtFloat()?.let { scalar(it, "float") }
            ?: scalar(site.nbtDouble(), "double")
        return Dimension(DimensionMode.ABSOLUTE, value)
    }

    private fun interpolated(site: Parser.MultiLineStringLiteralContext): Expression.InterpolatedString {
        val parts = site.multiLineStringContent().map { content ->
            content.multiLineStringExpression()?.let { TextPart.Interpolation(capture(it.expression())) }
                ?: TextPart.Text(content.MultiLineStrText()?.text ?: content.MultiLineStringQuote().text)
        }.toMutableList()
        // The closing token may absorb quote characters that belong to the actual string.
        val quotes = site.TRIPLE_QUOTE_CLOSE().text.dropLast(3)
        if (quotes.isNotEmpty()) parts += TextPart.Text(quotes)
        return Expression.InterpolatedString(scope(site), parts.toList())
    }

    private fun typeBody(site: Parser.TypeBodyContext): TypeSyntax {
        site.normalType()?.let { builtin ->
            val key = builtin.text
            return TypeSyntax.Name(scope(site), bindings.name(key, null, NameRole.TYPE, site), bindings.builtinType(key, site))
        }
        site.VecType()?.let { return TypeSyntax.Vector(scope(site), it.text.drop(3).toLong()) }
        if (site.LIST() != null || site.IMMUTABLE_LIST() != null || site.MAP() != null || site.DICT() != null) {
            val kind = when {
                site.LIST() != null -> ContainerKind.LIST
                site.IMMUTABLE_LIST() != null -> ContainerKind.IMMUTABLE_LIST
                site.MAP() != null -> ContainerKind.MAP
                else -> ContainerKind.DICT
            }
            return TypeSyntax.Container(scope(site), kind, type(site.type()))
        }
        if (site.ENTITY() != null) return TypeSyntax.Entity(scope(site), site.nbtInt()?.let { scalar(it, "int") },
            site.LineString().map { decodeString(it.text) })
        site.unionType()?.let { return TypeSyntax.Union(scope(site), it.type().map(::type)) }
        site.unionTemplateType()?.let { return TypeSyntax.Intersection(scope(site), it.type().map(::type)) }
        site.anonymousTemplateType()?.let {
            return TypeSyntax.AnonymousData(scope(site), bindings.anonymousType(it, this))
        }
        site.functionType()?.let {
            return TypeSyntax.Function(scope(site), it.type().map(::type), returnType(it.functionReturnType()))
        }
        val className = site.className()
        val name = className?.classWithoutNamespace()?.Identifier()?.text ?: site.Identifier()?.text
            ?: throw SyntaxCaptureException("LIB_TYPE_UNSUPPORTED", "Type rule has no persistent operation")
        val namespace = className?.namespacePath()?.text
        val base = TypeSyntax.Name(scope(site), bindings.name(name, namespace, NameRole.TYPE, site),
            if (site.readOnlyArgs() == null) bindings.resolvedType(site) else null)
        return site.readOnlyArgs()?.let {
            TypeSyntax.Apply(scope(site), base, it.expressionList()?.expression().orEmpty().map(::capture), bindings.resolvedType(site))
        } ?: base
    }

    private fun requireValid(site: ParseTree) {
        if (site is ErrorNode || (site is ParserRuleContext && site.exception != null))
            unsupported("Recovered parser syntax cannot be exported")
        for (index in 0 until site.childCount) requireValid(site.getChild(index))
    }

    private fun decodeString(text: String): String {
        if (text.length < 2 || text.first() != text.last() || text.first() !in "\"'")
            unsupported("Invalid quoted string")
        val result = StringBuilder()
        var index = 1
        while (index < text.lastIndex) {
            val character = text[index++]
            if (character == '\\') {
                if (index >= text.lastIndex) unsupported("Incomplete string escape")
                val escaped = text[index++]
                if (escaped != '\\' && escaped != text.first()) unsupported("Invalid string escape")
                result.append(escaped)
            } else result.append(character)
        }
        return result.toString()
    }

    private fun unsupported(message: String): Nothing = throw SyntaxCaptureException("LIB_BODY_UNSUPPORTED", message)
}

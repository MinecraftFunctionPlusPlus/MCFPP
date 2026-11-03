package top.mcfpp.mni.expand

import top.mcfpp.mni.api.*
import top.mcfpp.type.MCFPPBaseType
import top.mcfpp.type.TypeId
import top.mcfpp.antlr.mcfppParser as P

/** Declaration-only binding. A caller scope is never used to resolve a default template. */
internal class SourceDefaultBinder(private val binder: SourceCodeBinder = SnapshotSourceCodeBinder()) {
    /** Header/default preparation already has compiler facts, not a partially built SourceFunction. */
    internal fun typedLiteral(
        fact: top.mcfpp.analysis.DeclarationBinding.Bound,
        types: SourceTypeCatalog,
        source: SourceSpan
    ): BoundExpr {
        val proof = fact.constant ?: throw SourceBindingFailure(source, "Readonly compiler value is not closed")
        val type = try {
            types.ref(fact.type.typeId)
        } catch (_: IllegalArgumentException) {
            throw SourceBindingFailure(source, "Readonly compiler type is outside the prepared source domain")
        }
        val value = SourceConstantFacts.compilerConstant(proof, type, types, source)
        SourceConstantFacts.validateClosed(type, value, types, source)
        return BoundExpr(BoundExprNode.Literal(value), type, value, BoundEffect.PURE, source)
    }

    fun bind(
        declaration: SourceFunction,
        environment: BindingEnvironment,
        readonlyDefaults: Map<Int, P.ExpressionContext> = emptyMap(),
        normalDefaults: Map<Int, P.ExpressionContext> = emptyMap(),
        readonlySymbols: List<SourceSymbol> = declaration.readonlySymbols,
        normalSymbols: List<SourceSymbol> = declaration.normalSymbols
    ): SourceFunction {
        val formals = readonlySymbols + normalSymbols
        require(
            readonlySymbols.size == declaration.readonlyParameters.size &&
                normalSymbols.size == declaration.function.parameters().size
        ) { "Default binding requires every declaration formal identity" }
        require(
            formals.map { it.symbol }.distinct().size == formals.size &&
                formals.map { it.name }.distinct().size == formals.size
        ) { "Default formal identities and names must be unique" }
        require(formals.all { it.constant == null }) { "Declaration formals cannot contain call-specific constant values" }
        require(
            readonlyDefaults.keys.all { it in readonlySymbols.indices } &&
                normalDefaults.keys.all { it in normalSymbols.indices }
        ) { "Default parameter index is outside the signature" }
        val names = formals.map { it.name }.toSet()
        // Include every formal, even later ones: shadowing then resolves to its actual identity,
        // and the declaration's predecessor checks reject it instead of finding a free namesake.
        val scope = BindingEnvironment(
            environment.types,
            environment.symbols.values.filter { it.name !in names } + formals,
            environment.functions,
            environment.fields.values,
            environment.sourceText,
            environment.scopeId,
            environment.executionContextId,
            environment.sourceFile,
            structuredSymbols = environment.structuredSymbols
        )
        val readonly = declaration.readonlyDefaultExpressions + readonlyDefaults.mapValues { binder.expression(it.value, scope) }
        val normal = declaration.normalDefaultExpressions + normalDefaults.mapValues { binder.expression(it.value, scope) }
        val result = SourceFunction(
            declaration.name, declaration.function, declaration.effect,
            declaration.readonlyParameters, declaration.defaultedReadonlyParameters, declaration.defaultedNormalParameters,
            readonly, normal, declaration.sourceBody, readonlySymbols, normalSymbols, declaration.preparedReadonly,
            compilerDeclarationId = declaration.compilerDeclarationId
        )
        BindingEnvironment(
            environment.types,
            environment.symbols.values,
            listOf(result),
            environment.fields.values,
            environment.sourceText,
            environment.scopeId,
            environment.executionContextId,
            environment.sourceFile
        )
        readonly.values.forEach(::closedTemplate)
        return result
    }

    /** Bind-time closure proof, allowing symbolic predecessors but no interpretation of calls. */
    private fun closedTemplate(expression: BoundExpr) {
        when (val node = expression.node) {
            is BoundExprNode.Literal -> Unit
            is BoundExprNode.Read -> if (node.location.path.isNotEmpty())
                throw SourceBindingFailure(expression.source, "Readonly default has no closed constant template")
            is BoundExprNode.Unary -> closedTemplate(node.operand)
            is BoundExprNode.Binary -> {
                closedTemplate(node.left)
                closedTemplate(node.right)
            }
            is BoundExprNode.Convert -> closedTemplate(node.value)
            else -> throw SourceBindingFailure(expression.source, "Readonly default cannot evaluate a call, accessor or dynamic location")
        }
    }

    /** Used by declaration preparation and later projection; only immutable facts are evaluated. */
    fun completeReadonly(
        declaration: SourceFunction,
        explicit: List<BoundExpr>,
        types: SourceTypeCatalog,
        source: SourceSpan
    ): List<BoundExpr> {
        if (explicit.size > declaration.readonlyParameters.size)
            throw SourceBindingFailure(source, "Too many readonly arguments")
        BindingEnvironment(types, emptyList(), listOf(declaration))
        val values = arrayListOf<BoundExpr>()
        val replacements = linkedMapOf<SymbolId, BoundExpr>()
        declaration.readonlyParameters.forEachIndexed { index, parameter ->
            val template = explicit.getOrNull(index) ?: declaration.readonlyDefaultExpressions[index]
                ?: throw SourceBindingFailure(source, "Readonly parameter $index has no bound default template")
            val value = close(template, replacements, types)
            val converted = coerce(value, parameter.type, types)
            values += converted
            declaration.readonlySymbols.getOrNull(index)?.let { replacements[it.symbol] = converted }
        }
        return immutableList(values)
    }

    fun prepareReadonly(
        declaration: SourceFunction,
        explicit: List<BoundExpr>,
        types: SourceTypeCatalog,
        source: SourceSpan
    ): SourcePreparedReadonly = SourcePreparedReadonly(
        declaration.declarationIdentity,
        completeReadonly(declaration, explicit, types, source).map { TypedConstant(it.type, requireNotNull(it.constant), it.source) },
        source
    )

    private fun close(expression: BoundExpr, replacements: Map<SymbolId, BoundExpr>, types: SourceTypeCatalog): BoundExpr {
        if (!types.owns(expression.type)) throw SourceBindingFailure(expression.source, "Default type belongs to another source snapshot")
        val read = expression.node as? BoundExprNode.Read
        if (read != null && read.location.path.isEmpty()) {
            val original = read.location.root
            if (!types.owns(original.type))
                throw SourceBindingFailure(expression.source, "Default symbol belongs to another source snapshot")
            replacements[original.symbol]?.let { replacement ->
                return BoundExpr(replacement.node, replacement.type, replacement.constant, BoundEffect.PURE, expression.source)
            }
        }
        val value = when (val node = expression.node) {
            is BoundExprNode.Literal -> node.value
            is BoundExprNode.Read -> {
                if (node.location.path.isNotEmpty()) throw SourceBindingFailure(expression.source, "Readonly location has no closed constant proof")
                replacements[node.location.root.symbol]?.constant ?: node.location.root.constant
            }
            is BoundExprNode.Unary -> SourceConstantFacts.unary(node.operator, close(node.operand, replacements, types).constant)
            is BoundExprNode.Binary -> {
                val left = requireNotNull(close(node.left, replacements, types).constant)
                if (node.operator == BinaryOperator.AND && left == ConstantValue.Bool(false)) ConstantValue.Bool(false)
                else if (node.operator == BinaryOperator.OR && left == ConstantValue.Bool(true)) ConstantValue.Bool(true)
                else SourceConstantFacts.binary(
                    node.operator,
                    types.facts(expression.type).id,
                    left,
                    close(node.right, replacements, types).constant
                )
            }
            is BoundExprNode.Convert -> {
                val input = close(node.value, replacements, types)
                SourceConstantFacts.convert(input.constant, input.type, node.target, types, widening = false)
            }
            else -> throw SourceBindingFailure(expression.source, "Readonly default cannot evaluate a call, accessor or dynamic location")
        } ?: throw SourceBindingFailure(expression.source, "Readonly expression has no proven closed constant")
        constantOwnership(value, types, expression.source)
        SourceConstantFacts.validateClosed(expression.type, value, types, expression.source)
        return BoundExpr(BoundExprNode.Literal(value), expression.type, value, BoundEffect.PURE, expression.source)
    }

    private fun coerce(expression: BoundExpr, expected: TypeRef<*>, types: SourceTypeCatalog): BoundExpr {
        if (!types.owns(expected) || types.conversionRank(expression.type, expected) == null)
            throw SourceBindingFailure(expression.source, "Readonly type differs from the concrete signature")
        val value = SourceConstantFacts.convert(expression.constant, expression.type, expected, types)
            ?: throw SourceBindingFailure(expression.source, "Readonly conversion has no closed constant proof")
        // Nominal/Object widening preserves the proof's concrete type; numeric conversion changes its payload.
        val result = if (expression.type.sameType(expected) || value != expression.constant) expected else expression.type
        return BoundExpr(BoundExprNode.Literal(value), result, value, BoundEffect.PURE, expression.source)
    }

    companion object {
        /** Selection reads prepared facts; it neither fills defaults nor creates specializations. */
        fun acceptsPrepared(declaration: SourceFunction, explicit: List<BoundExpr>, types: SourceTypeCatalog): Boolean {
            val prepared = declaration.preparedReadonly ?: return true
            if (explicit.size > prepared.arguments.size) return false
            return explicit.zip(prepared.arguments).all { (actual, expected) ->
                types.conversionRank(actual.type, expected.type) != null &&
                    actual.constant != null &&
                    SourceConstantFacts.convert(actual.constant, actual.type, expected.type, types) == expected.value
            }
        }

        private fun constantOwnership(value: ConstantValue, types: SourceTypeCatalog, source: SourceSpan) {
            when (value) {
                is ConstantValue.TypeValue -> if (!types.owns(value.type))
                    throw SourceBindingFailure(source, "Readonly constant belongs to another source snapshot")
                is ConstantValue.Sequence -> value.elements.forEach { constantOwnership(it, types, source) }
                is ConstantValue.Record -> value.fields.values.forEach { constantOwnership(it, types, source) }
                else -> Unit
            }
        }

        /** Detect expansion of omitted defaults, without traversing or executing a function body. */
        fun validateCycles(functions: List<SourceFunction>) {
            data class Default(val owner: FunctionKey, val readonly: Boolean, val index: Int)
            val known = functions.associateBy { it.declarationIdentity }
            val active = hashSetOf<Default>()
            val done = hashSetOf<Default>()
            lateinit var visit: (SourceFunction, Boolean, Int, SourceSpan) -> Unit
            lateinit var expression: (BoundExpr) -> Unit
            fun argument(value: BoundCallArgument) {
                when (value) {
                    is BoundCallArgument.Value -> expression(value.expression)
                    is BoundCallArgument.Reference -> value.location.path.forEach { if (it is BoundLocationStep.Index) expression(it.expression) }
                    is BoundCallArgument.Callback -> Unit
                }
            }
            expression = { value ->
                when (val node = value.node) {
                    is BoundExprNode.Call -> {
                        val call = node.sourceCall
                        call.readonlyArguments.forEach(expression)
                        call.arguments.forEach(::argument)
                        val target = known[call.target.declarationIdentity] ?: call.target
                        for (index in call.readonlyArguments.size until target.readonlyParameters.size)
                            visit(target, true, index, call.source)
                        for (index in call.arguments.size until target.function.parameters().size)
                            visit(target, false, index, call.source)
                    }
                    is BoundExprNode.Read -> node.location.path.forEach { if (it is BoundLocationStep.Index) expression(it.expression) }
                    is BoundExprNode.Unary -> expression(node.operand)
                    is BoundExprNode.Binary -> {
                        expression(node.left)
                        expression(node.right)
                    }
                    is BoundExprNode.Convert -> expression(node.value)
                    is BoundExprNode.Field -> expression(node.receiver)
                    is BoundExprNode.Index -> {
                        expression(node.receiver)
                        expression(node.index)
                    }
                    is BoundExprNode.Literal, is BoundExprNode.Unsupported -> Unit
                }
            }
            visit = { function, readonly, index, source ->
                val key = Default(function.declarationIdentity, readonly, index)
                if (key !in done) {
                    if (!active.add(key)) throw SourceBindingFailure(source, "Cyclic default expansion in '${function.name}'")
                    val template = (if (readonly) function.readonlyDefaultExpressions else function.normalDefaultExpressions)[index]
                    template?.let(expression)
                    active.remove(key)
                    done.add(key)
                }
            }
            functions.forEach { function ->
                function.readonlyDefaultExpressions.forEach { (index, value) -> visit(function, true, index, value.source) }
                function.normalDefaultExpressions.forEach { (index, value) -> visit(function, false, index, value.source) }
            }
        }
    }
}

/** The source parser and default closure share exactly one pure arithmetic implementation. */
internal object SourceConstantFacts {
    /** Adapt immutable compiler proofs using the final canonical schema; no Var is reconstructed. */
    fun compilerConstant(
        proof: top.mcfpp.analysis.CompilerValue,
        expected: TypeRef<*>,
        types: SourceTypeCatalog,
        source: SourceSpan
    ): ConstantValue {
        fun reject(): Nothing = throw SourceBindingFailure(source, "Compiler constant does not match the prepared source schema")
        if (!types.owns(expected)) reject()
        val value = if (proof is top.mcfpp.analysis.CompilerValue.Typed) {
            if (proof.type != types.facts(expected).id) reject()
            proof.payload
        } else proof
        val descriptor = expected as ImmutableTypeRef<*>
        val graph = requireNotNull(descriptor.graph)
        val result = when (val schema = graph.constantSchema(descriptor.key)) {
            is ConstantSchema.Sequence -> {
                val sequence = value as? top.mcfpp.analysis.CompilerValue.Sequence ?: reject()
                ConstantValue.Sequence(sequence.elements.map { compilerConstant(it, graph.type(schema.element), types, source) })
            }
            is ConstantSchema.Record -> {
                val record = value as? top.mcfpp.analysis.CompilerValue.Record ?: reject()
                if (record.fields.keys != schema.fields.keys) reject()
                ConstantValue.Record(
                    record.fields.mapValues { (name, member) ->
                        compilerConstant(member, graph.type(schema.fields.getValue(name)), types, source)
                    }
                )
            }
            is ConstantSchema.Scalar -> when (value) {
                is top.mcfpp.analysis.CompilerValue.Integral -> ConstantValue.Integral(value.value)
                is top.mcfpp.analysis.CompilerValue.FloatBits -> if (Float.fromBits(value.bits).isFinite())
                    ConstantValue.FloatBits(value.bits) else reject()
                is top.mcfpp.analysis.CompilerValue.DoubleBits -> if (Double.fromBits(value.bits).isFinite())
                    ConstantValue.DoubleBits(value.bits) else reject()
                is top.mcfpp.analysis.CompilerValue.Bool -> ConstantValue.Bool(value.value)
                is top.mcfpp.analysis.CompilerValue.Text -> ConstantValue.Text(value.value)
                is top.mcfpp.analysis.CompilerValue.TypeValue -> ConstantValue.TypeValue(
                    try {
                        types.ref(value.id)
                    } catch (_: IllegalArgumentException) {
                        reject()
                    }
                )
                is top.mcfpp.analysis.CompilerValue.Nbt -> ConstantValue.Nbt(value.snbt)
                top.mcfpp.analysis.CompilerValue.NullValue -> ConstantValue.NullValue
                else -> reject()
            }
            null -> reject()
        }
        validateClosed(expected, result, types, source)
        return result
    }

    fun compilerUnary(operator: UnaryOperator, value: top.mcfpp.analysis.CompilerValue?): top.mcfpp.analysis.CompilerValue? = primitive(unary(operator, primitiveConstant(value)))

    fun compilerConvert(value: top.mcfpp.analysis.CompilerValue?, from: TypeId, to: TypeId): top.mcfpp.analysis.CompilerValue? {
        val converted = numericConversion(primitiveConstant(value), from, to) ?: return null
        return if (converted is ConstantValue.Text) top.mcfpp.analysis.CompilerValue.Text(converted.value) else primitive(converted)
    }

    fun compilerBinary(
        operation: String,
        result: TypeId,
        left: top.mcfpp.analysis.CompilerValue?,
        right: top.mcfpp.analysis.CompilerValue?
    ): top.mcfpp.analysis.CompilerValue? {
        val operator = when (operation) {
            "+" -> BinaryOperator.ADD
            "-" -> BinaryOperator.SUBTRACT
            "*" -> BinaryOperator.MULTIPLY
            "/" -> BinaryOperator.DIVIDE
            "%" -> BinaryOperator.MODULO
            "==" -> BinaryOperator.EQ
            "!=" -> BinaryOperator.NE
            "<" -> BinaryOperator.LT
            "<=" -> BinaryOperator.LE
            ">" -> BinaryOperator.GT
            ">=" -> BinaryOperator.GE
            "&&" -> BinaryOperator.AND
            "||" -> BinaryOperator.OR
            else -> return null
        }
        val folded = binary(operator, result, primitiveConstant(left), primitiveConstant(right))
        return if (folded is ConstantValue.Text) top.mcfpp.analysis.CompilerValue.Text(folded.value) else primitive(folded)
    }

    private fun primitiveConstant(value: top.mcfpp.analysis.CompilerValue?): ConstantValue? = when (value) {
        is top.mcfpp.analysis.CompilerValue.Typed -> primitiveConstant(value.payload)
        is top.mcfpp.analysis.CompilerValue.Text -> ConstantValue.Text(value.value)
        else -> constant(value)
    }

    fun validateClosed(type: TypeRef<*>, value: ConstantValue, types: SourceTypeCatalog, source: SourceSpan) {
        if (!types.owns(type)) throw SourceBindingFailure(source, "Readonly constant belongs to another source snapshot")
        val descriptor = type as ImmutableTypeRef<*>
        val graph = requireNotNull(descriptor.graph)
        fun reject(): Nothing = throw SourceBindingFailure(source, "Readonly constant does not match its frozen type schema")
        when (val schema = graph.constantSchema(descriptor.key)) {
            is ConstantSchema.Sequence -> {
                val sequence = value as? ConstantValue.Sequence ?: reject()
                sequence.elements.forEach { validateClosed(graph.type(schema.element), it, types, source) }
            }
            is ConstantSchema.Record -> {
                val record = value as? ConstantValue.Record ?: reject()
                if (record.fields.keys != schema.fields.keys) reject()
                schema.fields.forEach { (name, key) -> validateClosed(graph.type(key), record.fields.getValue(name), types, source) }
            }
            is ConstantSchema.Scalar -> {
                val matches = when (schema.shape) {
                    ConstantShape.INT32 -> value is ConstantValue.Integral && value.value in Int.MIN_VALUE.toLong()..Int.MAX_VALUE.toLong()
                    ConstantShape.BOOL -> value is ConstantValue.Bool
                    ConstantShape.FLOAT32 -> value is ConstantValue.FloatBits
                    ConstantShape.FLOAT64 -> value is ConstantValue.DoubleBits
                    ConstantShape.STRING -> value is ConstantValue.Text
                    ConstantShape.NULL -> value === ConstantValue.NullValue
                    ConstantShape.TYPE -> value is ConstantValue.TypeValue && types.owns(value.type)
                    ConstantShape.NBT ->
                        value is ConstantValue.Nbt &&
                            try {
                                top.mcfpp.nbt.tags.Tag.toNBT(value.snbt)
                                true
                            } catch (_: Exception) {
                                false
                            }
                }
                if (!matches) reject()
            }
            null -> reject()
        }
    }

    fun unary(operator: UnaryOperator, value: ConstantValue?): ConstantValue? {
        val input = primitive(value) ?: return null
        val operation = when (operator) {
            UnaryOperator.NOT -> "!"
            UnaryOperator.NEGATE -> "-"
        }
        return constant(top.mcfpp.analysis.PrimitiveEvaluation.unary(operation, input))
    }

    fun convert(
        value: ConstantValue?,
        from: TypeRef<*>,
        to: TypeRef<*>,
        types: SourceTypeCatalog,
        widening: Boolean = true
    ): ConstantValue? = when {
        !types.owns(from) || !types.owns(to) -> null
        from.sameType(to) -> value
        numericConversion(value, types.facts(from).id, types.facts(to).id) != null ->
            numericConversion(value, types.facts(from).id, types.facts(to).id)
        widening && types.isSubtype(from, to) -> value
        else -> null
    }

    fun binary(op: BinaryOperator, resultType: TypeId, left: ConstantValue?, right: ConstantValue?): ConstantValue? {
        if (op == BinaryOperator.AND && left == ConstantValue.Bool(false)) return left
        if (op == BinaryOperator.OR && left == ConstantValue.Bool(true)) return left
        if (left is ConstantValue.Text && right is ConstantValue.Text && op == BinaryOperator.ADD)
            return ConstantValue.Text(left.value + right.value)
        val a = primitive(left) ?: return null
        val b = primitive(right) ?: return null
        // Comparisons return bool, but their operands still use the promoted numeric representation.
        val floating = resultType == MCFPPBaseType.Float.typeId ||
            a is top.mcfpp.analysis.CompilerValue.FloatBits ||
            b is top.mcfpp.analysis.CompilerValue.FloatBits
        val leftValue = if (floating && a is top.mcfpp.analysis.CompilerValue.Integral)
            top.mcfpp.analysis.CompilerValue.FloatBits(a.value.toInt().toFloat().toRawBits()) else a
        val rightValue = if (floating && b is top.mcfpp.analysis.CompilerValue.Integral)
            top.mcfpp.analysis.CompilerValue.FloatBits(b.value.toInt().toFloat().toRawBits()) else b
        val operation = when (op) {
            BinaryOperator.ADD -> "+"
            BinaryOperator.SUBTRACT -> "-"
            BinaryOperator.MULTIPLY -> "*"
            BinaryOperator.DIVIDE -> "/"
            BinaryOperator.MODULO -> "%"
            BinaryOperator.EQ -> "=="
            BinaryOperator.NE -> "!="
            BinaryOperator.LT -> "<"
            BinaryOperator.LE -> "<="
            BinaryOperator.GT -> ">"
            BinaryOperator.GE -> ">="
            BinaryOperator.AND -> "&&"
            BinaryOperator.OR -> "||"
        }
        return constant(top.mcfpp.analysis.PrimitiveEvaluation.binary(operation, leftValue, rightValue))
    }

    private fun primitive(value: ConstantValue?): top.mcfpp.analysis.CompilerValue? = when (value) {
        is ConstantValue.Integral -> value.value.takeIf { it in Int.MIN_VALUE.toLong()..Int.MAX_VALUE.toLong() }
            ?.let { top.mcfpp.analysis.CompilerValue.Integral(it) }
        is ConstantValue.Bool -> top.mcfpp.analysis.CompilerValue.Bool(value.value)
        is ConstantValue.FloatBits -> top.mcfpp.analysis.CompilerValue.FloatBits(value.bits)
        else -> null
    }

    private fun numericConversion(value: ConstantValue?, from: TypeId, to: TypeId): ConstantValue? = when {
        from == to -> value
        from == MCFPPBaseType.Int.typeId &&
            to == MCFPPBaseType.Float.typeId &&
            value is ConstantValue.Integral &&
            value.value in Int.MIN_VALUE.toLong()..Int.MAX_VALUE.toLong() ->
            ConstantValue.FloatBits(value.value.toInt().toFloat().toRawBits())
        else -> null
    }

    private fun constant(value: top.mcfpp.analysis.CompilerValue?): ConstantValue? = when (value) {
        is top.mcfpp.analysis.CompilerValue.Integral -> ConstantValue.Integral(value.value)
        is top.mcfpp.analysis.CompilerValue.Bool -> ConstantValue.Bool(value.value)
        is top.mcfpp.analysis.CompilerValue.FloatBits -> Float.fromBits(value.bits).takeIf { it.isFinite() }?.let { ConstantValue.FloatBits(value.bits) }
        else -> null
    }
}

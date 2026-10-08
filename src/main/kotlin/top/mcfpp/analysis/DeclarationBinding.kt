package top.mcfpp.analysis

import org.antlr.v4.runtime.ParserRuleContext
import org.antlr.v4.runtime.CharStreams
import org.antlr.v4.runtime.CommonTokenStream
import top.mcfpp.antlr.mcfppLexer
import org.antlr.v4.runtime.tree.ParseTree
import top.mcfpp.antlr.mcfppParser as Parser
import top.mcfpp.core.lang.MCFPPTypeVar
import top.mcfpp.core.lang.Var
import top.mcfpp.model.Generic
import top.mcfpp.model.compound.DataTemplate
import top.mcfpp.model.function.*
import top.mcfpp.model.function.Function
import top.mcfpp.model.scope.GlobalScope
import top.mcfpp.nbt.tags.Tag
import top.mcfpp.nbt.tags.primitive.StringTag
import top.mcfpp.type.*
import top.mcfpp.util.StringHelper.splitNamespaceID

/** Type binding uses declaration signatures, never an executable value visitor. */
internal class DeclarationBinding(
    private val function: Function,
    private val fields: Map<String, MCFPPType>,
    private val values: Map<String, Bound> = emptyMap(),
    private val typeBindings: Map<String, MCFPPType> = emptyMap()
) {
    data class Bound(val type: MCFPPType, val constant: CompilerValue? = null,
                     val dependencies: Set<String> = emptySet(), val declaredTypeText: String? = null)
    class Failure(message: String) : RuntimeException(message)
    private val template = function.owner as? DataTemplate
    private val readonly = ((template as? top.mcfpp.model.compound.CompiledGenericDataTemplate)?.originTemplate?.readOnlyParams
        ?.map { it.identifier }.orEmpty() + ((function as? Generic<*>)?.readOnlyParams
        ?: (function as? NativeFunction)?.readOnlyParams.orEmpty()).map { it.identifier }).toSet()

    fun expression(context: Parser.ExpressionContext): Bound = bind(context)
    internal fun value(context: Parser.ValueContext): Bound = literal(context)

    private fun closedValue(argument: Bound): top.mcfpp.core.lang.Var<*> {
        val snapshot = argument.constant ?: throw Failure("Readonly declaration arguments require a complete value")
        val types = mutableMapOf(argument.type.typeId to argument.type)
        MCFPPType.registerSnapshotTypes(snapshot, types)
        return StorageAccess.restore(argument.type, snapshot, "readonly", types)
            ?: throw Failure("Readonly declaration argument '${argument.type}' has no closed layout")
    }

    internal fun constrain(value: Bound, target: MCFPPType, declaration: String? = null): Bound {
        val snapshot = when {
            value.type == target -> value.constant
            target == MCFPPBaseType.Any || target == MCFPPBaseType.Object ->
                value.constant?.let { CompilerValue.Typed(target.typeId, it) }
            target == MCFPPBaseType.Float && value.type == MCFPPBaseType.Int ->
                ((value.constant as? CompilerValue.Typed)?.payload as? CompilerValue.Integral)?.let {
                    CompilerValue.Typed(target.typeId, CompilerValue.FloatBits(it.value.toInt().toFloat().toRawBits()))
                }
            else -> null
        }
        return value.copy(type = target, constant = snapshot, declaredTypeText = declaration ?: value.declaredTypeText)
    }

    private fun variable(name: String): Bound {
        values[name]?.let { return it }
        function.normalParams.firstOrNull { it.identifier == name }?.let {
            val declaration = generateSequence(function.ast?.parent) { it.parent }.firstOrNull {
                it is Parser.TemplateConstructorDeclarationContext || it is Parser.FunctionDeclarationPartContext
            }
            val params = when (declaration) {
                is Parser.TemplateConstructorDeclarationContext -> declaration.normalParams()
                is Parser.FunctionDeclarationPartContext -> declaration.functionParams().normalParams()
                else -> null
            }
            val text = params?.parameterList()?.parameter()?.firstOrNull { it.Identifier().text == name }?.type()?.text
            return Bound(it.type, declaredTypeText = text ?: it.typeName)
        }
        if (name in readonly) function.scope.getVar(name)?.let { value ->
            return Bound(value.type, ValueSnapshot.of(value), setOf(name))
        }
        fields[name]?.let { return Bound(it, declaredTypeText = fieldDeclarationText(name)) }
        val value = function.scope.getVar(name)
        if (value != null) return Bound(value.type, ValueSnapshot.of(value),
            if (name in readonly) setOf(name) else emptySet())
        GlobalScope.getObject(null, name)?.let { return Bound(it.getType()) }
        (function.scope.getType(name) ?: GlobalScope.getEnum(null, name)?.let { MCFPPEnumType(it) })?.let { type ->
            return Bound(MCFPPConcreteType.Type, CompilerValue.Typed(MCFPPConcreteType.Type.typeId,
                CompilerValue.TypeValue(type.typeId)))
        }
        throw Failure("Cannot bind declaration name '$name'")
    }

    private fun bind(node: ParseTree): Bound = when (node) {
        is Parser.ExpressionContext -> node.primary()?.let(::bind) ?: bind(node.commonBinaryOperatorExpression())
        is Parser.CastExpressionContext -> if (node.type() != null) {
            val source = bind(node.unaryExpression())
            val target = type(node.type())
            val snapshot = source.constant?.let { frozen ->
                var represented = frozen
                while (represented is CompilerValue.Typed && represented.payload is CompilerValue.Typed &&
                    represented.type in setOf(MCFPPBaseType.Any.typeId, MCFPPBaseType.Object.typeId)) represented = represented.payload
                if (represented is CompilerValue.Typed && represented.type == target.typeId) represented
                else if (TypeRelations.checkReinterpretation(source.type, target) is ReinterpretationCompatibility.Result.Compatible &&
                    source.type !in setOf(MCFPPBaseType.Any, MCFPPBaseType.Object))
                    CompilerValue.Typed(target.typeId, if (represented is CompilerValue.Typed) represented.payload else represented)
                else null
            }
            Bound(target, snapshot, source.dependencies + dependencies(node.type()), node.type().text)
        } else bind(node.unaryExpression())
        is Parser.UnaryExpressionContext -> node.rightVarExpression()?.let(::bind) ?: bind(node.unaryExpression()).let {
            val value = (it.constant as? CompilerValue.Typed)?.payload
            val type = if (node.EXCL() != null) MCFPPBaseType.Bool else it.type
            val constant = when {
                node.EXCL() != null && value is CompilerValue.Bool -> CompilerValue.Bool(!value.value)
                node.EXCL() == null && it.type == MCFPPBaseType.Int && value is CompilerValue.Integral -> CompilerValue.Integral((-value.value.toInt()).toLong())
                node.EXCL() == null && it.type == MCFPPBaseType.Float && value is CompilerValue.FloatBits && Float.fromBits(value.bits).isFinite() -> CompilerValue.FloatBits((-Float.fromBits(value.bits)).toRawBits())
                else -> null
            }
            Bound(type, constant?.let { payload -> CompilerValue.Typed(type.typeId, payload) }, it.dependencies)
        }
        is Parser.VarWithSelectorContext -> {
            var result = bind(node.jvmAccessExpression().propertyOperator().primary())
            for (selector in node.selector()) result = member(result, selector.`var`())
            result
        }
        is Parser.PrimaryContext -> when {
            node.THIS() != null -> Bound(template?.getType() ?: throw Failure("'this' requires a declaration owner"))
            node.SUPER() != null -> Bound(template?.parent?.filterIsInstance<DataTemplate>()?.firstOrNull()?.getType()
                ?: throw Failure("'super' requires a parent declaration"))
            node.type() != null -> {
                val type = type(node.type())
                Bound(MCFPPConcreteType.Type, CompilerValue.Typed(MCFPPConcreteType.Type.typeId, CompilerValue.TypeValue(type.typeId)), dependencies(node.type()))
            }
            node.range() != null -> range(node.range())
            node.value() != null -> literal(node.value())
            else -> bind(node.`var`())
        }
        is Parser.VarContext -> when {
            node.functionCall() != null -> call(node.functionCall())
            node.bucketExpression() != null -> node.bucketExpression().expression()?.let(::bind)
                ?: throw Failure("An empty expression cannot initialize a declaration")
            else -> suffix(variable(node.varWithSuffix().Identifier().text), node.varWithSuffix())
        }
        is Parser.RightVarExpressionContext -> bind(node.varWithSelector())
        else -> {
            val children = (0 until node.childCount).map(node::getChild).filterIsInstance<ParserRuleContext>()
            if (children.isEmpty()) throw Failure("Cannot bind declaration expression '${node.text}'")
            if (children.size == 1) bind(children.single()) else binary(node, children.map(::bind))
        }
    }

    private fun suffix(value: Bound, node: Parser.VarWithSuffixContext): Bound {
        var result = value
        for (index in node.identifierSuffix()) {
            val key = index.expression()?.let(::bind)?.constant?.let { if (it is CompilerValue.Typed) it.payload else it }
            val payload = (result.constant as? CompilerValue.Typed)?.payload
            val constant = when {
                payload is CompilerValue.Sequence && key is CompilerValue.Integral -> payload.elements.getOrNull(key.value.toInt())
                payload is CompilerValue.Record && key is CompilerValue.Text -> payload.fields[key.value]
                else -> null
            }
            val source = result.declaredTypeText?.let {
                val body = Parser(CommonTokenStream(mcfppLexer(CharStreams.fromString(it)))).type().typeBody()
                if (body.LIST() != null || body.DICT() != null || body.MAP() != null || body.IMMUTABLE_LIST() != null) body.type().text else null
            }
            result = when (val type = result.type) {
                is MCFPPListType -> Bound(type.generic.single(), dependencies = result.dependencies)
                is MCFPPImmutableListType -> Bound(type.generic.single(), dependencies = result.dependencies)
                is MCFPPDictType -> Bound(type.generic.single(), dependencies = result.dependencies)
                is MCFPPMapType -> Bound(type.generic.single(), dependencies = result.dependencies)
                else -> TypeRelations.arrayElementType(type.typeId)?.let { Bound(it, dependencies = result.dependencies) }
                    ?: throw Failure("Declaration type '${result.type}' cannot be indexed")
            }.copy(constant = constant, declaredTypeText = source)
        }
        return result
    }

    private fun member(receiver: Bound, node: Parser.VarContext): Bound {
        val meta = ((receiver.constant as? CompilerValue.Typed)?.payload as? CompilerValue.TypeValue)?.id?.let(MCFPPType::resolveTypeId)
        val owner = (meta as? MCFPPDataTemplateType)?.template?.let { if (it is top.mcfpp.model.compound.ObjectCompoundData) it else it.companionObject }
            ?: (receiver.type as? MCFPPDataTemplateType)?.template
        if (node.functionCall() != null) return call(node.functionCall(), receiver)
        if (node.bucketExpression() != null) throw Failure("An expression cannot name a member")
        val name = node.varWithSuffix().Identifier().text
        if (meta == null && owner === template) {
            values[name]?.let { return suffix(it, node.varWithSuffix()) }
            fields[name]?.let { return suffix(Bound(it, declaredTypeText = fieldDeclarationText(name)), node.varWithSuffix()) }
        }
        if (meta is MCFPPEnumType) {
            val member = meta.enum.members[name] ?: throw Failure("Unknown enum member '$name'")
            return Bound(meta, CompilerValue.Typed(meta.typeId, CompilerValue.Record(mapOf(
                "ordinal" to CompilerValue.Integral(member.value.toLong()), "data" to CompilerValue.Nbt(top.mcfpp.backend.NbtEncoding.snbt(member.data))))))
        }
        val field = (owner?.scope ?: meta?.objectData?.scope ?: receiver.type.instanceData.scope).getVar(name)
        val property = owner?.scope?.getProperty(name)
        if (field != null) {
            val access = property?.accessModifier ?: field.accessModifier
            if (owner != null && function.accessTo(owner) < access) throw Failure("Cannot access declaration member '$name'")
            val origin = (owner as? top.mcfpp.model.compound.CompiledGenericDataTemplate)?.originTemplate
            var declared = origin?.ctx?.templateMemberDeclaration()?.firstNotNullOfOrNull {
                it.templateMember().templateFieldDeclaration()?.takeIf { field -> field.Identifier().text == name }?.templateType()?.text
            }
            receiver.declaredTypeText?.let { text ->
                val syntax = Parser(CommonTokenStream(mcfppLexer(CharStreams.fromString(text)))).type().typeBody()
                origin?.readOnlyParams?.zip(syntax.readOnlyArgs()?.expressionList()?.expression().orEmpty())?.forEach { (formal, actual) ->
                    declared = declared?.replace(Regex("\\b${Regex.escape(formal.identifier)}\\b"), "(${actual.text})")
                }
            }
            val payload = (receiver.constant as? CompilerValue.Typed)?.payload as? CompilerValue.Record
            return suffix(Bound(field.type, payload?.fields?.get(name), receiver.dependencies, declared), node.varWithSuffix())
        }
        throw Failure("Member '$name' is not defined on '${receiver.type}'")
    }

    private fun call(context: Parser.FunctionCallContext, receiver: Bound? = null): Bound {
        val (namespace, name) = context.namespaceID().text.splitNamespaceID()
        val arguments = context.arguments().normalArgs().expressionList()?.expression().orEmpty().map(::bind)
        arguments.firstNotNullOfOrNull { TypeUsage.ordinaryDiagnostic(it.type, it.constant) }?.let { throw Failure(it) }
        val readonlyArguments = context.arguments().readOnlyArgs()?.expressionList()?.expression().orEmpty().map(::bind)
        val meta = ((receiver?.constant as? CompilerValue.Typed)?.payload as? CompilerValue.TypeValue)?.id?.let(MCFPPType::resolveTypeId)
        val owner = (meta as? MCFPPDataTemplateType)?.template?.let { if (it is top.mcfpp.model.compound.ObjectCompoundData) it else it.companionObject }
            ?: (receiver?.type as? MCFPPDataTemplateType)?.template
        val declaration = if (receiver == null) GlobalScope.getTemplate(namespace, name) else null
        if (declaration != null) {
            val values = readonlyArguments.map(::closedValue)
            val actual = if (declaration is top.mcfpp.model.compound.GenericDataTemplate) declaration.compile(values)
                ?: throw Failure("Cannot bind readonly arguments for '$name'") else {
                if (values.isNotEmpty()) throw Failure("Ordinary template '$name' has no readonly parameters")
                declaration
            }
            val constructors = actual.constructors
            val selected = ParameterMatcher.selectTypes(constructors, "", arguments.map { it.type })
            if (selected !is ParameterMatcher.TypeSelection.Selected) throw Failure("Cannot bind constructor '$name' to its declared argument types")
            val typeText = if (readonlyArguments.isEmpty()) context.namespaceID().text else
                context.namespaceID().text + "<" + context.arguments().readOnlyArgs().expressionList().expression().joinToString(",") { it.text } + ">"
            return Bound(actual.getType(), dependencies = readonlyArguments.flatMap { it.dependencies }.toSet(), declaredTypeText = typeText)
        }
        val receiverData = (receiver?.type as? MCFPPEntityType)?.let {
            top.mcfpp.core.lang.entity.SelectorVar.declarationData(it)
        }
        val receiverTypes = (receiver?.type as? MCFPPTypeWithGeneric)?.generic?.firstOrNull()
            ?.let { mapOf("E" to it) }.orEmpty()
        val candidates = if (receiver == null) GlobalScope.getFunctionCandidates(namespace, name,
            function.restoreDeclarationEnvironment()?.field ?: top.mcfpp.io.MCFPPFile.currFile?.field)
            else (owner?.scope ?: meta?.objectData?.scope ?: receiverData?.scope ?: receiver.type.instanceData.scope).getFunctionCandidates(name)
                .fold(mutableListOf<Function>()) { declarations, candidate ->
                    // Scope candidates are ordered from the actual owner outwards. A
                    // member override hides its inherited signature, not other overloads.
                    if (declarations.none { ParameterMatcher.sameSignature(it, candidate) }) declarations.add(candidate)
                    declarations
                }
        val selected = ParameterMatcher.selectDeclaredTypes(candidates, name, readonlyArguments, arguments.map { it.type }, receiverTypes)
        val target = (selected as? ParameterMatcher.TypeSelection.Selected)?.function
            ?: throw Failure("Cannot bind function '$name' to its declared argument types")
        if (owner != null && function.accessTo(owner) < target.accessModifier) throw Failure("Cannot access declaration method '$name'")
        val params = (target as? Generic<*>)?.readOnlyParams ?: (target as? NativeFunction)?.readOnlyParams.orEmpty()
        val result = SpecializationPolicy.resolveDeclaredSignature(target, params, readonlyArguments, receiverTypes)?.returnType
            ?: throw Failure("Cannot bind return type '$name'")
        // Ordinary actual argument knowledge never changes the declared result type.
        val functionDeclaration = generateSequence(target.ast?.parent) { it.parent }.filterIsInstance<Parser.FunctionDeclarationPartContext>().firstOrNull()
        var text = (target.returnType as? UnresolvedType)?.originalTypeString
            ?: functionDeclaration?.functionReturnType()?.type()?.text
        params.zip(context.arguments().readOnlyArgs()?.expressionList()?.expression().orEmpty()).forEach { (param, expression) ->
            text = text?.replace(Regex("\\b${Regex.escape(param.identifier)}\\b"), "(${expression.text})")
        }
        return Bound(result, dependencies = receiver?.dependencies.orEmpty() + readonlyArguments.flatMap { it.dependencies }, declaredTypeText = text)
    }

    private fun binary(node: ParseTree, operands: List<Bound>): Bound {
        val operators = (0 until node.childCount).map(node::getChild).filter { it !is ParserRuleContext }
            .map { it.text }.filter { it !in setOf("\n", "\r\n") }
        var result = operands.first()
        for ((index, next) in operands.drop(1).withIndex()) {
            val operation = operators.getOrNull(index) ?: throw Failure("Cannot bind binary declaration expression '${node.text}'")
            val type = when {
                operation == "|" && result.type == MCFPPConcreteType.Type && next.type == MCFPPConcreteType.Type -> MCFPPConcreteType.Type
                operation in setOf("==", "!=", "<", ">", "<=", ">=") &&
                    result.type in setOf(MCFPPBaseType.Int, MCFPPBaseType.Float) &&
                    next.type in setOf(MCFPPBaseType.Int, MCFPPBaseType.Float) -> MCFPPBaseType.Bool
                operation in setOf("==", "!=", "&&", "||") &&
                    result.type == MCFPPBaseType.Bool && next.type == MCFPPBaseType.Bool -> MCFPPBaseType.Bool
                result.type == MCFPPBaseType.Int && next.type == MCFPPBaseType.Int -> MCFPPBaseType.Int
                result.type in setOf(MCFPPBaseType.Int, MCFPPBaseType.Float) && next.type in setOf(MCFPPBaseType.Int, MCFPPBaseType.Float) -> MCFPPBaseType.Float
                operation == "+" && result.type == MCFPPBaseType.String && next.type == MCFPPBaseType.String -> MCFPPBaseType.String
                else -> {
                    val selected = result.type.instanceData.getOperator(operation, next.type)
                    if (selected == null || selected is UnknownFunction) throw Failure("No declared operator '$operation' for '${result.type}' and '${next.type}'")
                    selected.returnType
                }
            }
            val left = (result.constant as? CompilerValue.Typed)?.payload
            val right = (next.constant as? CompilerValue.Typed)?.payload
            val constant = if (left != null && right != null) {
                if (operation == "|" && left is CompilerValue.TypeValue && right is CompilerValue.TypeValue) {
                    val a = MCFPPType.resolveTypeId(left.id) ?: throw Failure("Unknown readonly type")
                    val b = MCFPPType.resolveTypeId(right.id) ?: throw Failure("Unknown readonly type")
                    CompilerValue.TypeValue(MCFPPUnionType(a, b).typeId)
                } else if (operation == "+" && result.type == MCFPPBaseType.String && next.type == MCFPPBaseType.String &&
                    left is CompilerValue.Text && right is CompilerValue.Text) CompilerValue.Text(left.value + right.value)
                else if (result.type == MCFPPBaseType.Int && next.type == MCFPPBaseType.Int &&
                    operation !in setOf("/", "%") ||
                    result.type == MCFPPBaseType.Bool && next.type == MCFPPBaseType.Bool ||
                    result.type in setOf(MCFPPBaseType.Int, MCFPPBaseType.Float) &&
                    next.type in setOf(MCFPPBaseType.Int, MCFPPBaseType.Float) &&
                    (result.type == MCFPPBaseType.Float || next.type == MCFPPBaseType.Float)) {
                    val floating = result.type == MCFPPBaseType.Float || next.type == MCFPPBaseType.Float
                    fun promoted(value: CompilerValue) = if (floating && value is CompilerValue.Integral)
                        CompilerValue.FloatBits(value.value.toInt().toFloat().toRawBits()) else value
                    PrimitiveEvaluation.binary(operation, promoted(left), promoted(right))
                } else null
            } else null
            result = Bound(type, constant?.let { CompilerValue.Typed(type.typeId, it) }, result.dependencies + next.dependencies)
        }
        return result
    }

    internal fun type(context: Parser.TypeContext): MCFPPType {
        val body = context.typeBody()
        typeBindings[body.text]?.let { return it }
        body.unionType()?.let { return MCFPPUnionType(*it.type().map(::type).toTypedArray()) }
        body.type()?.let { argument ->
            val element = type(argument)
            return when {
                body.LIST() != null -> MCFPPListType(element)
                body.IMMUTABLE_LIST() != null -> MCFPPImmutableListType(element)
                body.DICT() != null -> MCFPPDictType(element)
                else -> MCFPPMapType(element)
            }
        }
        if (body.className() != null && body.readOnlyArgs() != null) {
            val (namespace, name) = body.className().text.splitNamespaceID()
            val declaration = GlobalScope.getTemplate(namespace, name) ?: GlobalScope.getInterface(namespace, name)
                ?: GlobalScope.getObject(namespace, name) as? DataTemplate
                ?: throw Failure("Unknown declaration type '$name'")
            val generic = declaration as? top.mcfpp.model.compound.GenericDataTemplate
                ?: throw Failure("Ordinary declaration '$name' has no readonly parameters")
            val arguments = body.readOnlyArgs().expressionList()?.expression().orEmpty().map(::bind).map(::closedValue)
            return generic.compile(arguments)?.getType()
                ?: throw Failure("Cannot bind readonly type arguments for '$name'")
        }
        return MCFPPType.parseFromContext(context, function.scope, function)
            ?: throw Failure("Invalid declaration type '${context.text}'")
    }

    internal fun declaredType(type: MCFPPType): MCFPPType = when (type) {
        is UnresolvedType -> type(Parser(CommonTokenStream(mcfppLexer(CharStreams.fromString(type.originalTypeString)))).type())
        else -> SpecializationPolicy.bind(type, typeBindings)
    }

    private fun dependencies(node: ParseTree): Set<String> = if (node.childCount == 0) {
        if (node.text in readonly) setOf(node.text) else emptySet()
    } else (0 until node.childCount).flatMap { dependencies(node.getChild(it)) }.toSet()

    private fun fieldDeclarationText(name: String): String? {
        val origin = (template as? top.mcfpp.model.compound.CompiledGenericDataTemplate)?.originTemplate ?: return null
        return origin.ctx.templateMemberDeclaration().firstNotNullOfOrNull {
            it.templateMember().templateFieldDeclaration()?.takeIf { field -> field.Identifier().text == name }
                ?.templateType()?.singleTemplateFieldType()?.type()?.text
        }
    }

    private fun range(context: Parser.RangeContext): Bound {
        val endpoints = linkedMapOf<String, Bound>()
        fun endpoint(name: String, node: Parser.Range1Context?) {
            if (node == null) return
            val bound = if (node.`var`() != null) bind(node.`var`()) else literal(node.value())
            if (bound.type != MCFPPBaseType.Int && bound.type != MCFPPBaseType.Float)
                throw Failure("Range endpoints require int or float")
            endpoints[name] = bound
        }
        endpoint("left", context.num1)
        endpoint("right", context.num2)
        val complete = endpoints.values.all { bound ->
            when (val payload = (bound.constant as? CompilerValue.Typed)?.payload) {
                is CompilerValue.Integral -> bound.type == MCFPPBaseType.Int
                is CompilerValue.FloatBits -> bound.type == MCFPPBaseType.Float && Float.fromBits(payload.bits).isFinite()
                else -> false
            }
        }
        return Bound(MCFPPBaseType.Range,
            if (complete) CompilerValue.Typed(MCFPPBaseType.Range.typeId,
                CompilerValue.Record(endpoints.mapValues { it.value.constant!! })) else null,
            endpoints.values.flatMap { it.dependencies }.toSet())
    }

    private fun literal(context: Parser.ValueContext): Bound {
        context.coordinate()?.let { coordinate ->
            val type = if (coordinate.coordinateDimension().size == 2) MCFPPBaseType.Pos2 else MCFPPBaseType.Pos3
            val dimensions = coordinate.coordinateDimension().map { dimension ->
                val text = dimension.text
                val prefix = text.take(1).takeIf { it == "~" || it == "^" }.orEmpty()
                val number = text.removePrefix(prefix).ifEmpty { "0" }
                val payload = when {
                    dimension.nbtFloat() != null -> CompilerValue.FloatBits(number.trimEnd('f', 'F').toFloat().toRawBits())
                    dimension.nbtDouble() != null -> CompilerValue.DoubleBits(number.trimEnd('d', 'D').toDouble().toRawBits())
                    number.toLongOrNull() != null -> CompilerValue.Integral(number.toLong())
                    else -> CompilerValue.FloatBits(number.toFloat().toRawBits())
                }
                CompilerValue.Typed(MCFPPPrivateType.MCFPPCoordinateDimension.typeId,
                    CompilerValue.Sequence(listOf(CompilerValue.Text(prefix), payload)))
            }
            return Bound(type, CompilerValue.Typed(type.typeId, CompilerValue.Sequence(dimensions)))
        }
        if (context.TargetSelector() != null) return Bound(MCFPPEntityType())
        if (context.NULL() != null) return Bound(MCFPPPrivateType.Null, CompilerValue.Typed(MCFPPPrivateType.Null.typeId, CompilerValue.NullValue))
        if (context.LineString() != null || context.multiLineStringLiteral() != null) {
            val dependencies = linkedSetOf<String>()
            val text = context.LineString()?.text?.let { (Tag.toNBT(it) as StringTag).value } ?: run {
                val contents = context.multiLineStringLiteral().multiLineStringContent().map { part ->
                    part.MultiLineStrText()?.text ?: part.MultiLineStringQuote()?.text ?: run {
                        val bound = bind(part.multiLineStringExpression().expression())
                        dependencies.addAll(bound.dependencies)
                        when (val payload = (bound.constant as? CompilerValue.Typed)?.payload) {
                            is CompilerValue.Text -> payload.value
                            is CompilerValue.Integral -> payload.value.toString()
                            is CompilerValue.Bool -> payload.value.toString()
                            is CompilerValue.FloatBits -> Float.fromBits(payload.bits).toString()
                            is CompilerValue.DoubleBits -> Double.fromBits(payload.bits).toString()
                            else -> null
                        }
                    }
                }
                if (contents.any { it == null }) null else contents.joinToString("") +
                    context.multiLineStringLiteral().TRIPLE_QUOTE_CLOSE().text.drop(3)
            }
            return Bound(MCFPPBaseType.String, text?.let { CompilerValue.Typed(MCFPPBaseType.String.typeId, CompilerValue.Text(it)) }, dependencies)
        }
        val value = context.nbtValue()
        val type = when {
            value.nbtBool() != null -> MCFPPBaseType.Bool
            value.nbtInt() != null -> MCFPPBaseType.Int
            value.nbtFloat() != null -> MCFPPBaseType.Float
            value.nbtDouble() != null -> MCFPPNBTType.Double
            value.nbtByte() != null -> MCFPPNBTType.Byte
            value.nbtShort() != null -> MCFPPNBTType.Short
            value.nbtLong() != null -> MCFPPNBTType.Long
            value.LineString() != null -> MCFPPBaseType.String
            value.nbtList() != null -> MCFPPListType(common(value.nbtList().expression().map(::bind)))
            value.nbtCompound() != null -> MCFPPDictType(common(value.nbtCompound().nbtKeyValuePair().map { bind(it.expression()) }, MCFPPBaseType.Any))
            value.nbtByteArray() != null -> MCFPPNBTType.ByteArray
            value.nbtIntArray() != null -> MCFPPNBTType.IntArray
            value.nbtLongArray() != null -> MCFPPNBTType.LongArray
            else -> MCFPPNBTType.NBT
        }
        val payload = when {
            value.nbtBool() != null -> CompilerValue.Bool(value.text == "true")
            value.nbtInt() != null -> CompilerValue.Integral(value.text.toLong())
            value.nbtByte() != null || value.nbtShort() != null || value.nbtLong() != null -> CompilerValue.Integral(value.text.dropLast(1).toLong())
            value.nbtFloat() != null -> CompilerValue.FloatBits(value.text.trimEnd('f', 'F').toFloat().toRawBits())
            value.nbtDouble() != null -> CompilerValue.DoubleBits(value.text.trimEnd('d', 'D').toDouble().toRawBits())
            value.LineString() != null -> CompilerValue.Text((Tag.toNBT(value.text) as StringTag).value)
            value.nbtList() != null -> {
                val elements = value.nbtList().expression().map(::bind)
                if (elements.all { it.constant != null }) CompilerValue.Sequence(elements.map { it.constant!! }) else null
            }
            value.nbtCompound() != null -> {
                val members = value.nbtCompound().nbtKeyValuePair().map {
                    val key = it.key.text
                    (if (key.startsWith("\"") || key.startsWith("'")) (Tag.toNBT(key) as StringTag).value else key) to bind(it.expression())
                }
                if (members.all { it.second.constant != null }) CompilerValue.Record(members.associate { it.first to it.second.constant!! }) else null
            }
            value.nbtByteArray() != null || value.nbtIntArray() != null || value.nbtLongArray() != null -> {
                val tag = Tag.toNBT(value.text)
                val elements: List<Long> = when (tag) {
                    is top.mcfpp.nbt.tags.collection.ByteArrayTag -> tag.value.map { it.toLong() }
                    is top.mcfpp.nbt.tags.collection.IntArrayTag -> tag.value.map { it.toLong() }
                    is top.mcfpp.nbt.tags.collection.LongArrayTag -> tag.value.toList()
                    else -> throw Failure("Invalid closed NBT array")
                }
                val element = TypeRelations.arrayElementType(type.typeId)!!
                CompilerValue.Sequence(elements.map { CompilerValue.Typed(element.typeId, CompilerValue.Integral(it)) })
            }
            else -> null
        }
        return Bound(type, payload?.let { CompilerValue.Typed(type.typeId, it) })
    }

    private fun common(values: List<Bound>, empty: MCFPPType = MCFPPPrivateType.Wildcard): MCFPPType {
        val types = values.map { it.type }.distinctBy { it.typeId }
        return when (types.size) { 0 -> empty; 1 -> types.single(); else -> MCFPPUnionType(*types.toTypedArray()) }
    }
}

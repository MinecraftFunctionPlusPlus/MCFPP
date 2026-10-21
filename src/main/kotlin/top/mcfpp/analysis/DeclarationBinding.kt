package top.mcfpp.analysis

import org.antlr.v4.runtime.ParserRuleContext
import org.antlr.v4.runtime.CharStreams
import org.antlr.v4.runtime.CommonTokenStream
import org.antlr.v4.runtime.BaseErrorListener
import org.antlr.v4.runtime.RecognitionException
import org.antlr.v4.runtime.Recognizer
import org.antlr.v4.runtime.Token
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
internal class DeclarationBinding private constructor(
    private val declarationFunction: Function?,
    private val fields: Map<String, MCFPPType>,
    private val values: Map<String, Bound>,
    private val typeBindings: Map<String, MCFPPType>,
    private val declarationScope: top.mcfpp.model.scope.IScopeWithType?,
    private val captureValues: Boolean,
    private val literalDefaultsOnly: Boolean,
    private val preparation: DeclarationPreparationContext?,
    private val preparedCalls: Map<Parser.FunctionCallContext, ParameterMatcher.PreparedCallFacts> = emptyMap()
) {
    constructor(function: Function, fields: Map<String, MCFPPType>, values: Map<String, Bound> = emptyMap(),
                typeBindings: Map<String, MCFPPType> = emptyMap(),
                lookupScope: top.mcfpp.model.scope.IScopeWithType = function.scope,
                captureValues: Boolean = false, literalDefaultsOnly: Boolean = false) :
        this(function, fields, values, typeBindings, lookupScope, captureValues, literalDefaultsOnly, null)

    constructor(preparation: DeclarationPreparationContext) :
        this(null, emptyMap(), emptyMap(), emptyMap(), null, false, false, preparation)

    /** Identity-keyed source calls retain independent readonly specializations in the same tree. */
    constructor(preparation: DeclarationPreparationContext,
                preparedCalls: Map<Parser.FunctionCallContext, ParameterMatcher.PreparedCallFacts>) :
        this(null, emptyMap(), emptyMap(), emptyMap(), null, false, false, preparation,
            java.util.Collections.unmodifiableMap(java.util.IdentityHashMap(preparedCalls)))

    private val function: Function get() = checkNotNull(declarationFunction) { "Pure binding cannot enter legacy function lookup" }
    private val lookupScope: top.mcfpp.model.scope.IScopeWithType get() =
        checkNotNull(declarationScope) { "Pure binding cannot enter legacy scope lookup" }
    data class Bound(val type: MCFPPType, val constant: CompilerValue? = null,
                     val dependencies: Set<String> = emptySet(), val declaredTypeText: String? = null,
                     val descriptors: Map<TypeId, MCFPPType> = emptyMap(),
                     val superOwner: DataTemplate? = null,
                     val headerReceiver: PreparedTemplateReservation? = null)
    class Failure(message: String) : RuntimeException(message)
    private val template = declarationFunction?.owner as? DataTemplate
    private val readonly = ((template as? top.mcfpp.model.compound.CompiledGenericDataTemplate)?.originTemplate?.readOnlyParams
        ?.map { it.identifier }.orEmpty() + ((declarationFunction as? Generic<*>)?.readOnlyParams
        ?: (declarationFunction as? NativeFunction)?.readOnlyParams.orEmpty()).map { it.identifier }).toSet()

    fun expression(context: Parser.ExpressionContext): Bound = preparationGuard(context) { bind(context) }
    internal fun value(context: Parser.ValueContext): Bound = preparationGuard(context) { literal(context) }

    private inline fun <T> preparationGuard(site: ParserRuleContext, action: () -> T): T = try {
        preparation?.requireCurrentReader(site)
        action()
    }
        catch (failure: Failure) {
            if (preparation == null) throw failure
            throw PreparationFactFailure(PreparationFactCode.NON_CLOSED_VALUE, preparationSpan(site), failure.message.orEmpty())
        } catch (failure: NumberFormatException) {
            if (preparation == null) throw failure
            throw PreparationFactFailure(PreparationFactCode.NON_CLOSED_VALUE, preparationSpan(site), "Invalid closed numeric value")
        }

    internal fun readonly(context: Parser.ExpressionContext): Bound = requireClosed(expression(context), context)
    internal fun readonlyValue(context: Parser.ValueContext): Bound = requireClosed(value(context), context)

    private fun requireClosed(bound: Bound, site: ParserRuleContext): Bound {
        if (bound.constant == null) throw PreparationFactFailure(PreparationFactCode.NON_CLOSED_VALUE,
            preparationSpan(site), "Readonly value is not closed; dynamic composite values require runtime binding")
        return bound
    }

    private fun unsupported(site: ParserRuleContext, message: String): Nothing =
        throw PreparationFactFailure(PreparationFactCode.UNSUPPORTED_EXPRESSION, preparationSpan(site), message)

    private fun closedValue(argument: Bound): top.mcfpp.core.lang.Var<*> {
        val snapshot = argument.constant ?: throw Failure("Readonly declaration arguments require a complete value")
        val types = argument.descriptors.toMutableMap().apply { put(argument.type.typeId, argument.type) }
        MCFPPType.registerSnapshotTypes(snapshot, types)
        return StorageAccess.restore(argument.type, snapshot, "readonly", types)
            ?: throw Failure("Readonly declaration argument '${argument.type}' has no closed layout")
    }

    internal fun constrain(value: Bound, target: MCFPPType, declaration: String? = null): Bound {
        if (preparation != null) {
            val proof = if (value.type.typeId == target.typeId) value.constant
                else top.mcfpp.mni.expand.SourceConstantFacts.compilerConvert(value.constant, value.type.typeId, target.typeId)
                    ?.let { CompilerValue.Typed(target.typeId, it) }
            return value.copy(type = target, constant = proof, declaredTypeText = declaration ?: value.declaredTypeText)
        }
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

    private fun variable(name: String, site: ParserRuleContext? = null): Bound {
        preparation?.let { return it.variable(name, checkNotNull(site)) }
        if (literalDefaultsOnly && (name == "this" || name == "field" || name == "value" ||
                name in fields || values.containsKey(name) || lookupVariable(name) != null ||
                function.normalParams.any { it.identifier == name }))
            throw Failure("Default depends on a runtime declaration")
        values[name]?.let { return it }
        function.normalParams.firstOrNull { it.identifier == name }?.takeUnless { captureValues }?.let {
            val declaration = generateSequence(function.ast?.parent) { it.parent }.firstOrNull {
                it is Parser.TemplateConstructorDeclarationContext || it is Parser.FunctionDeclarationPartContext
            }
            val params = when (declaration) {
                is Parser.TemplateConstructorDeclarationContext -> declaration.normalParams()
                is Parser.FunctionDeclarationPartContext -> declaration.functionParams().normalParams()
                else -> null
            }
            val text = params?.parameterList()?.parameter()?.firstOrNull { it.Identifier()?.text == name }?.type()?.text
            return Bound(it.type, declaredTypeText = text ?: it.typeName)
        }
        if (name in readonly) lookupVariable(name)?.let { value ->
            return Bound(value.type, StorageAccess.snapshot(value), setOf(name), descriptors = StorageAccess.boundTypes(value))
        }
        fields[name]?.let { return Bound(it, declaredTypeText = fieldDeclarationText(name)) }
        val value = lookupVariable(name)
        if (value != null) return Bound(value.type, StorageAccess.snapshot(value),
            if (name in readonly) setOf(name) else emptySet(), descriptors = StorageAccess.boundTypes(value))
        GlobalScope.getObject(null, name)?.let { return Bound(it.getType()) }
        (lookupScope.getType(name) ?: GlobalScope.getEnum(null, name)?.let { MCFPPEnumType(it) })?.let { type ->
            return Bound(MCFPPConcreteType.Type, CompilerValue.Typed(MCFPPConcreteType.Type.typeId,
                CompilerValue.TypeValue(type.typeId)), descriptors = mapOf(type.typeId to type))
        }
        throw Failure("Cannot bind declaration name '$name'")
    }

    private fun lookupVariable(name: String): Var<*>? {
        val (value, accessible) = when (val scope = lookupScope) {
            is top.mcfpp.model.scope.FunctionScope -> scope.getVar(name, function)
            is top.mcfpp.model.scope.CompoundDataScope -> {
                val value = scope.getVar(name)
                val property = scope.getProperty(name)
                val owner = property?.declaredParentTemplate ?: value?.declaredParentTemplate
                val access = property?.accessModifier ?: value?.accessModifier
                value to (owner == null || access == null || function.accessTo(owner) >= access)
            }
            else -> (scope as? top.mcfpp.model.scope.IScopeWithVar)?.getVar(name) to true
        }
        if (!accessible) throw Failure("Cannot access declaration name '$name'")
        return value
    }

    private fun bind(node: ParseTree): Bound = when (node) {
        is Parser.ExpressionContext -> node.primary()?.let(::bind) ?: bind(node.commonBinaryOperatorExpression())
        is Parser.CastExpressionContext -> if (node.type() != null) {
            val source = bind(node.unaryExpression())
            val target = type(node.type())
            if (preparation != null && source.type.typeId != target.typeId)
                unsupported(node, "Readonly reinterpretation requires prepared conversion facts")
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
            Bound(target, snapshot, source.dependencies + dependencies(node.type()), node.type().text, source.descriptors)
        } else bind(node.unaryExpression())
        is Parser.UnaryExpressionContext -> node.rightVarExpression()?.let(::bind)
            ?: if (preparation != null && node.text == "-2147483648") Bound(MCFPPBaseType.Int,
                CompilerValue.Typed(MCFPPBaseType.Int.typeId, CompilerValue.Integral(Int.MIN_VALUE.toLong())))
            else bind(node.unaryExpression()).let {
            if (preparation != null) {
                val operator = if (node.EXCL() != null) top.mcfpp.mni.api.UnaryOperator.NOT else top.mcfpp.mni.api.UnaryOperator.NEGATE
                val result = top.mcfpp.mni.expand.SourceConstantFacts.compilerUnary(operator, it.constant)
                val type = if (node.EXCL() != null) MCFPPBaseType.Bool else it.type
                return@let Bound(type, result?.let { value -> CompilerValue.Typed(type.typeId, value) }, it.dependencies,
                    descriptors = it.descriptors)
            }
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
            if (preparation != null && node.jvmAccessExpression().propertyOperator().propertyOperatorExpression().isNotEmpty())
                unsupported(node, "Property operators cannot supply readonly facts")
            var result = bind(node.jvmAccessExpression().propertyOperator().primary())
            for (selector in node.selector()) result = member(result, selector.`var`())
            result
        }
        is Parser.PrimaryContext -> when {
            node.THIS() != null -> preparation?.receiver(node)
                ?: Bound(template?.getType() ?: throw Failure("'this' requires a declaration owner"))
            node.SUPER() != null -> if (preparation != null) unsupported(node, "Super requires prepared declaration facts")
                else superBinding { function.superDeclarationOwner() }.let { Bound(it.getType(), superOwner = it) }
            node.type() != null -> {
                val type = type(node.type())
                Bound(MCFPPConcreteType.Type, CompilerValue.Typed(MCFPPConcreteType.Type.typeId, CompilerValue.TypeValue(type.typeId)), dependencies(node.type()), descriptors = mapOf(type.typeId to type))
            }
            node.range() != null -> range(node.range())
            node.value() != null -> literal(node.value())
            else -> bind(node.`var`())
        }
        is Parser.VarContext -> when {
            node.functionCall() != null -> call(node.functionCall())
            node.bucketExpression() != null -> node.bucketExpression().expression()?.let(::bind)
                ?: throw Failure("An empty expression cannot initialize a declaration")
            else -> suffix(variable(node.varWithSuffix().Identifier().text, node), node.varWithSuffix())
        }
        is Parser.RightVarExpressionContext -> bind(node.varWithSelector())
        else -> {
            val children = (0 until node.childCount).map(node::getChild).filterIsInstance<ParserRuleContext>()
            if (children.isEmpty()) throw Failure("Cannot bind declaration expression '${node.text}'")
            if (children.size == 1) bind(children.single()) else binary(node, children.map(::bind))
        }
    }

    /** Optional source spelling is not authoritative; debug type text must not emit diagnostics. */
    private fun sourceTypeBody(text: String): Parser.TypeBodyContext? {
        var invalid = false
        val errors = object : BaseErrorListener() {
            override fun syntaxError(recognizer: Recognizer<*, *>?, offendingSymbol: Any?, line: Int,
                                     charPositionInLine: Int, msg: String?, e: RecognitionException?) {
                invalid = true
            }
        }
        val lexer = mcfppLexer(CharStreams.fromString(text)).apply {
            removeErrorListeners()
            addErrorListener(errors)
        }
        val tokens = CommonTokenStream(lexer).apply { fill() }
        val parser = Parser(tokens).apply {
            removeErrorListeners()
            addErrorListener(errors)
        }
        val parsed = parser.type()
        return if (invalid || parser.numberOfSyntaxErrors != 0 || tokens.LA(1) != Token.EOF) null
            else parsed.typeBody()
    }

    private fun suffix(value: Bound, node: Parser.VarWithSuffixContext): Bound {
        if (preparation != null && node.identifierSuffix().isNotEmpty())
            unsupported(node, "Indexing requires prepared source location facts")
        var result = value
        for (index in node.identifierSuffix()) {
            val keyBinding = index.expression()?.let(::bind)
            val key = keyBinding?.constant?.let { if (it is CompilerValue.Typed) it.payload else it }
            val payload = (result.constant as? CompilerValue.Typed)?.payload
            val constant = when {
                payload is CompilerValue.Sequence && key is CompilerValue.Integral -> payload.elements.getOrNull(key.value.toInt())
                payload is CompilerValue.Record && key is CompilerValue.Text -> payload.fields[key.value]
                else -> null
            }
            val source = result.declaredTypeText?.let(::sourceTypeBody)?.let { body ->
                if (body.LIST() != null || body.DICT() != null || body.MAP() != null || body.IMMUTABLE_LIST() != null) body.type()?.text else null
            }
            result = when (val type = result.type) {
                is top.mcfpp.type.MCFPPVectorType -> {
                    val offset = (key as? CompilerValue.Integral)?.value
                    if (keyBinding?.type != MCFPPBaseType.Int || offset == null || offset !in 0L until type.dimension.toLong())
                        throw Failure("Vector index requires a complete int within its declared dimension")
                    Bound(MCFPPBaseType.Int, dependencies = result.dependencies)
                }
                is MCFPPListType -> Bound(type.generic.single(), dependencies = result.dependencies)
                is MCFPPImmutableListType -> Bound(type.generic.single(), dependencies = result.dependencies)
                is MCFPPDictType -> Bound(type.generic.single(), dependencies = result.dependencies)
                is MCFPPMapType -> Bound(type.generic.single(), dependencies = result.dependencies)
                else -> TypeRelations.arrayElementType(type.typeId)?.let { Bound(it, dependencies = result.dependencies) }
                    ?: throw Failure("Declaration type '${result.type}' cannot be indexed")
            }.copy(constant = constant, declaredTypeText = source, descriptors = result.descriptors)
        }
        return result
    }

    private fun member(receiver: Bound, node: Parser.VarContext): Bound {
        preparation?.let { context ->
            if (node.functionCall() != null || node.bucketExpression() != null)
                unsupported(node, "Owned header member requires a field declaration")
            val field = context.ownedMember(receiver, node.varWithSuffix().Identifier().text, node)
            return suffix(field, node.varWithSuffix())
        }
        receiver.superOwner?.let { owner ->
            if (node.functionCall() != null) return call(node.functionCall(), receiver)
            if (node.bucketExpression() != null) throw Failure("An expression cannot name a super member")
            val field = superBinding { function.superField(owner, node.varWithSuffix().Identifier().text) }
            return suffix(Bound(field.field.type), node.varWithSuffix())
        }
        val meta = ((receiver.constant as? CompilerValue.Typed)?.payload as? CompilerValue.TypeValue)?.id?.let {
            receiver.descriptors[it] ?: MCFPPType.resolveTypeId(it)
        }
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
            receiver.declaredTypeText?.let(::sourceTypeBody)?.let { syntax ->
                origin?.readOnlyParams?.zip(syntax.readOnlyArgs()?.expressionList()?.expression().orEmpty())?.forEach { (formal, actual) ->
                    declared = declared?.replace(Regex("\\b${Regex.escape(formal.identifier)}\\b"), "(${actual.text})")
                }
            }
            val payload = (receiver.constant as? CompilerValue.Typed)?.payload as? CompilerValue.Record
            return suffix(Bound(field.type, payload?.fields?.get(name), receiver.dependencies, declared, receiver.descriptors), node.varWithSuffix())
        }
        throw Failure("Member '$name' is not defined on '${receiver.type}'")
    }

    private fun call(context: Parser.FunctionCallContext, receiver: Bound? = null): Bound {
        if (preparation != null) return preparedCall(context, receiver)
        val (namespace, name) = context.namespaceID().text.splitNamespaceID()
        val arguments = context.arguments().normalArgs().expressionList()?.expression().orEmpty().map(::bind)
        arguments.firstNotNullOfOrNull { TypeUsage.ordinaryDiagnostic(it.type, it.constant) }?.let { throw Failure(it) }
        val readonlyArguments = context.arguments().readOnlyArgs()?.expressionList()?.expression().orEmpty().map(::bind)
        val meta = ((receiver?.constant as? CompilerValue.Typed)?.payload as? CompilerValue.TypeValue)?.id?.let {
            receiver.descriptors[it] ?: MCFPPType.resolveTypeId(it)
        }
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
        val superTarget = receiver?.superOwner?.let { anchor ->
            superBinding { function.selectSuper(anchor, name, readonlyArguments, arguments.map { it.type }) }
        }
        val candidates = if (receiver == null) GlobalScope.getFunctionCandidates(namespace, name,
            function.restoreDeclarationEnvironment()?.field ?: top.mcfpp.io.MCFPPFile.currFile?.field)
            else (owner?.scope ?: meta?.objectData?.scope ?: receiverData?.scope ?: receiver.type.instanceData.scope)
                .getFunctionCandidates(name, function)
        val selected = superTarget?.let { ParameterMatcher.TypeSelection.Selected(it) }
            ?: ParameterMatcher.selectDeclaredTypes(candidates, name, readonlyArguments, arguments.map { it.type }, receiverTypes)
        val target = when (selected) {
            is ParameterMatcher.TypeSelection.Selected -> selected.function
            is ParameterMatcher.TypeSelection.Ambiguous -> throw Failure(ParameterMatcher.ambiguity(name, selected.functions))
            ParameterMatcher.TypeSelection.Missing -> throw Failure("Cannot bind function '$name' to its declared argument types")
        }
        val declarationOwner = target.owner as? DataTemplate
        if (declarationOwner != null && function.accessTo(declarationOwner) < target.accessModifier)
            throw Failure("Cannot access declaration method '$name'")
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

    private fun preparedCall(context: Parser.FunctionCallContext, receiver: Bound?): Bound {
        if (receiver != null) unsupported(context, "Member call requires prepared receiver facts")
        val lexical = checkNotNull(preparation).lexical
        val (namespace, name) = context.namespaceID().text.splitNamespaceID()
        val prepared = preparedCalls[context] ?: unsupported(context, "Call '$name' has no prepared declaration batch")
        val candidates = lexical.functions(namespace, name)
        val cached = java.util.Collections.newSetFromMap(java.util.IdentityHashMap<Function, Boolean>())
        candidates.forEach { cached.addAll(it.compiledFunctions.values) }
        val declarations = candidates.filter { it !in cached }.map { CallableDeclarationId(it.declarationId) }.toSet()
        val preparedIdentities = prepared.declarations.candidates.map { it.declaration }.toSet()
        if (!preparedIdentities.containsAll(declarations))
            unsupported(context, "Call '$name' does not have all original declaration candidates prepared")
        val readonly = context.arguments().readOnlyArgs()?.expressionList()?.expression().orEmpty().map { syntax ->
            PreparedExpressionFact.fromBound(this.readonly(syntax), preparationSpan(syntax), this)
        }
        val normalSyntax = context.arguments().normalArgs()
        val arguments = normalSyntax.callArgumentList()?.callArgument()?.map { argument ->
            if (argument.REF() != null || argument.inlineLambda() != null)
                unsupported(argument, "Reference and inline arguments require prepared source call binding")
            bind(argument.expression())
        } ?: normalSyntax.expressionList()?.expression().orEmpty().map(::bind)
        arguments.firstNotNullOfOrNull { TypeUsage.ordinaryDiagnostic(it.type, it.constant) }?.let { unsupported(context, it) }
        val selected = ParameterMatcher.selectPreparedTypes(prepared, name, readonly, arguments.map { it.type }, declarations)
        val signature = when (selected) {
            is ParameterMatcher.PreparedTypeSelection.Selected -> selected.signature
            is ParameterMatcher.PreparedTypeSelection.Ambiguous -> unsupported(context,
                ParameterMatcher.ambiguity(name, selected.signatures.map { it.function }))
            ParameterMatcher.PreparedTypeSelection.Missing -> unsupported(context, "No prepared signature matches call '$name'")
        }
        checkNotNull(preparation).requirePreparedSignature(signature, context)
        // The body is never interpreted, including functions whose implementation returns a literal.
        return Bound(signature.returnType, dependencies = arguments.flatMap { it.dependencies }.toSet() +
            readonly.flatMap { it.bound.dependencies }, descriptors = prepared.declarations.types)
    }

    private inline fun <T> superBinding(action: () -> T): T = try { action() }
        catch (failure: Function.SuperFailure) { throw Failure(failure.message ?: "Cannot bind super member") }

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
                    if (preparation != null) unsupported(node as ParserRuleContext, "Operator requires prepared declaration facts")
                    val selected = result.type.instanceData.getOperator(operation, next.type)
                    if (selected == null || selected is UnknownFunction) throw Failure("No declared operator '$operation' for '${result.type}' and '${next.type}'")
                    selected.returnType
                }
            }
            val left = (result.constant as? CompilerValue.Typed)?.payload
            val right = (next.constant as? CompilerValue.Typed)?.payload
            val descriptors = (result.descriptors + next.descriptors).toMutableMap()
            val constant = if (preparation != null && operation != "|") {
                top.mcfpp.mni.expand.SourceConstantFacts.compilerBinary(operation, type.typeId, result.constant, next.constant)
            } else if (left != null && right != null) {
                if (operation == "|" && left is CompilerValue.TypeValue && right is CompilerValue.TypeValue) {
                    val a = descriptors[left.id] ?: if (preparation == null) MCFPPType.resolveTypeId(left.id) else null
                    val b = descriptors[right.id] ?: if (preparation == null) MCFPPType.resolveTypeId(right.id) else null
                    if (a == null || b == null) throw Failure("Unknown readonly type")
                    val union = MCFPPUnionType(a, b)
                    descriptors[union.typeId] = union
                    CompilerValue.TypeValue(union.typeId)
                } else if (operation == "+" && result.type == MCFPPBaseType.String && next.type == MCFPPBaseType.String &&
                    left is CompilerValue.Text && right is CompilerValue.Text) CompilerValue.Text(left.value + right.value)
                else if (result.type == MCFPPBaseType.Int && next.type == MCFPPBaseType.Int ||
                    result.type == MCFPPBaseType.Bool && next.type == MCFPPBaseType.Bool ||
                    result.type in setOf(MCFPPBaseType.Int, MCFPPBaseType.Float) &&
                    next.type in setOf(MCFPPBaseType.Int, MCFPPBaseType.Float) &&
                    (result.type == MCFPPBaseType.Float || next.type == MCFPPBaseType.Float)) {
                    val floating = result.type == MCFPPBaseType.Float || next.type == MCFPPBaseType.Float
                    if (!floating && operation in setOf("/", "%") &&
                        right is CompilerValue.Integral && right.value == 0L)
                        throw Failure("Integer division requires a nonzero divisor")
                    fun promoted(value: CompilerValue) = if (floating && value is CompilerValue.Integral)
                        CompilerValue.FloatBits(value.value.toInt().toFloat().toRawBits()) else value
                    PrimitiveEvaluation.binary(operation, promoted(left), promoted(right))
                } else null
            } else null
            result = Bound(type, constant?.let { CompilerValue.Typed(type.typeId, it) }, result.dependencies + next.dependencies, descriptors = descriptors)
        }
        return result
    }

    internal fun type(context: Parser.TypeContext): MCFPPType {
        // The shared syntax context owns template admission and its session's canonical queue.
        preparation?.let { return it.exportType(TypeSyntaxResolver.resolve(context, it.syntax())
            ?: unsupported(context, "Unknown declaration type '${context.text}'"), context) }
        val syntax = object : LegacyTypeSyntaxContext(lookupScope, function) {
            override fun known(body: Parser.TypeBodyContext): MCFPPType? =
                typeBindings[body.text] ?: super.known(body)

            override fun invalid(site: ParserRuleContext, message: String): MCFPPType? = throw Failure(message)

            override fun specialize(head: top.mcfpp.model.compound.GenericDataTemplate,
                                    arguments: List<Parser.ExpressionContext>, site: Parser.TypeBodyContext): MCFPPType? {
                // Bare legacy generic names retain their existing empty-argument preparation.
                if (site.readOnlyArgs() == null) return super.specialize(head, arguments, site)
                return head.compile(arguments.map(::bind).map(::closedValue))?.getType()
                    ?: throw Failure("Cannot bind readonly type arguments for '${head.identifier}'")
            }
        }
        return TypeSyntaxResolver.resolve(context, syntax)
            ?: throw Failure("Invalid declaration type '${context.text}'")
    }

    internal fun declaredType(type: MCFPPType): MCFPPType = when (type) {
        is UnresolvedType -> type(Parser(CommonTokenStream(mcfppLexer(CharStreams.fromString(type.originalTypeString)))).type())
        else -> if (preparation == null) SpecializationPolicy.bind(type, typeBindings) else type
    }

    /** Signature preparation never invokes an open replaceGenericParam implementation. */
    internal fun preparedDeclaredType(declared: MCFPPType, source: top.mcfpp.mni.api.SourceSpan,
                                      maxDepth: Int = 128): MCFPPType {
        val context = checkNotNull(preparation) { "Prepared types require a pure declaration context" }
        val active = java.util.Collections.newSetFromMap(java.util.IdentityHashMap<MCFPPType, Boolean>())
        fun reject(message: String): Nothing = throw PreparationFactFailure(PreparationFactCode.UNPREPARED_TYPE, source, message)
        fun syntax(text: String): MCFPPType {
            val body = sourceTypeBody(text) ?: throw PreparationFactFailure(PreparationFactCode.UNKNOWN_NAME,
                source, "Invalid declared type syntax '$text'")
            return try {
                context.preparedType(TypeSyntaxResolver.resolve(body.parent as Parser.TypeContext, context.syntax(source))
                    ?: reject("Type requires prepared declaration facts"), body, source)
            } catch (failure: PreparationFactFailure) {
                throw PreparationFactFailure(failure.code, source, failure.message.orEmpty(), failure.completion)
            } catch (failure: TypeSyntaxFailure) {
                reject(failure.message.orEmpty())
            }
        }
        fun visit(value: MCFPPType, depth: Int): MCFPPType {
            if (depth > maxDepth || !active.add(value)) reject("Declared type exceeds the preparation depth or contains a cycle")
            try {
                // Check exact shapes before accessing open type properties or generic collections.
                return when (value.javaClass) {
                    UnresolvedType::class.java -> visit(syntax((value as UnresolvedType).originalTypeString), depth + 1)
                    MCFPPGenericParamType::class.java -> visit(syntax((value as MCFPPGenericParamType).identifier), depth + 1)
                    MCFPPListType::class.java -> MCFPPListType(visit((value as MCFPPListType).generic.single(), depth + 1))
                    MCFPPImmutableListType::class.java -> MCFPPImmutableListType(visit((value as MCFPPImmutableListType).generic.single(), depth + 1))
                    MCFPPCompoundType::class.java -> MCFPPCompoundType(visit((value as MCFPPCompoundType).generic.single(), depth + 1))
                    MCFPPDictType::class.java -> MCFPPDictType(visit((value as MCFPPDictType).generic.single(), depth + 1))
                    MCFPPMapType::class.java -> MCFPPMapType(visit((value as MCFPPMapType).generic.single(), depth + 1))
                    MCFPPUnionType::class.java -> MCFPPUnionType(*(value as MCFPPUnionType).types.map { visit(it, depth + 1) }.toTypedArray())
                    MCFPPEntityType::class.java -> (value as MCFPPEntityType).let { selector ->
                        MCFPPEntityType(selector.limit, selector.types?.let {
                            java.util.Collections.unmodifiableList(ArrayList(it))
                        }, selector.isName)
                    }
                    else -> {
                        // The accepted leaf classes and template/alias boundary are owned by H02.
                        val site = Parser(CommonTokenStream(mcfppLexer(CharStreams.fromString("int")))).type()
                        context.preparedType(value, site, source)
                    }
                }
            } finally { active.remove(value) }
        }
        return visit(declared, 0)
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
        if (context.TargetSelector() != null) {
            val selector = top.mcfpp.lib.EntitySelector(context.TargetSelector().text[1])
            val type = MCFPPEntityType(selector.getLimit().takeUnless { it == Int.MAX_VALUE },
                selector.getType().takeUnless { it.isEmpty() }?.map { if (it.value) "!${it.key}" else it.key.toString() })
            val expression = top.mcfpp.lib.SelectorExpression(selector.selectorType, emptyList())
            return Bound(type, expression.snapshot(type.typeId))
        }
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
        return nbtLiteral(context.nbtValue())
    }

    internal fun nbtLiteral(value: Parser.NbtValueContext): Bound {
        val elements = value.nbtList()?.expression()?.map(::bind)
        val members = value.nbtCompound()?.nbtKeyValuePair()?.map {
            val key = it.key.text
            (if (key.startsWith("\"") || key.startsWith("'")) (Tag.toNBT(key) as StringTag).value else key) to bind(it.expression())
        }
        val type = when {
            value.nbtBool() != null -> MCFPPBaseType.Bool
            value.nbtInt() != null -> MCFPPBaseType.Int
            value.nbtFloat() != null -> MCFPPBaseType.Float
            value.nbtDouble() != null -> MCFPPNBTType.Double
            value.nbtByte() != null -> MCFPPNBTType.Byte
            value.nbtShort() != null -> MCFPPNBTType.Short
            value.nbtLong() != null -> MCFPPNBTType.Long
            value.LineString() != null -> MCFPPBaseType.String
            elements != null -> MCFPPListType(common(elements))
            members != null -> MCFPPDictType(common(members.map { it.second }, MCFPPBaseType.Any))
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
            elements != null -> {
                if (elements.all { it.constant != null }) CompilerValue.Sequence(elements.map { it.constant!! }) else null
            }
            members != null -> {
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
        if (preparation != null && (type == MCFPPBaseType.Int && payload is CompilerValue.Integral &&
                payload.value !in Int.MIN_VALUE.toLong()..Int.MAX_VALUE.toLong() ||
                payload is CompilerValue.FloatBits && !Float.fromBits(payload.bits).isFinite() ||
                payload is CompilerValue.DoubleBits && !Double.fromBits(payload.bits).isFinite()))
            unsupported(value, "Numeric value is outside its finite declared representation")
        return Bound(type, payload?.let { CompilerValue.Typed(type.typeId, it) },
            (elements.orEmpty() + members.orEmpty().map { it.second }).flatMap { it.dependencies }.toSet(),
            descriptors = (elements.orEmpty() + members.orEmpty().map { it.second }).flatMap { it.descriptors.entries }
                .associate { it.key to it.value })
    }

    private fun common(values: List<Bound>, empty: MCFPPType = MCFPPPrivateType.Wildcard): MCFPPType {
        val types = values.map { it.type }.distinctBy { it.typeId }
        return when (types.size) { 0 -> empty; 1 -> types.single(); else -> MCFPPUnionType(*types.toTypedArray()) }
    }
}

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
import top.mcfpp.core.lang.context.ContextCallbacks
import top.mcfpp.core.lang.context.ContextTypes
import top.mcfpp.core.lang.context.MCFPPExecutionContextType
import top.mcfpp.library.syntax.DeclarationId
import top.mcfpp.library.syntax.Expression
import top.mcfpp.model.Generic
import top.mcfpp.model.compound.DataTemplate
import top.mcfpp.model.compound.TypeDataTemplate
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
    private val preparedCalls: Map<Parser.FunctionCallContext, ParameterMatcher.PreparedCallFacts> = emptyMap(),
    private val contextPlans: MutableList<ContextCallbacks.Plan> = arrayListOf(),
    private val ambientExecutorFacts: top.mcfpp.lib.EntityFacts? =
        top.mcfpp.core.lang.context.ContextBorrowing.currentContext?.takeIf {
            top.mcfpp.core.lang.context.ContextParameters.currentExecutorKnown }?.executorFacts,
    private val contextDeclarations: Map<DeclarationId, Bound> = emptyMap(),
    private val ambientContextType: MCFPPType = top.mcfpp.core.lang.context.ContextParameters.currentFrameType
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
    private fun bindingSpan(site: ParserRuleContext) = top.mcfpp.mni.expand.StructuredSourceFacts.span(site)

    private fun preparedLocal(declaration: top.mcfpp.library.syntax.DeclarationId, site: ParserRuleContext): Bound {
        val context = checkNotNull(preparation)
        val directory = StructuredBodyContexts.bindings(site) as? StructuredSessionBindings
            ?: throw Failure("LIB_BODY_UNSUPPORTED: Pure local has no author directory")
        val parameter = directory.formal(declaration)
            ?: throw Failure("LIB_BODY_UNSUPPORTED: Pure local $declaration has no formal identity")
        val formal = context.lexical.formals.singleOrNull { it.identity === parameter }
            ?: throw Failure("LIB_BODY_UNSUPPORTED: Pure local $declaration belongs to another declaration")
        return context.variable(formal.name, site)
    }
    private val lookupScope: top.mcfpp.model.scope.IScopeWithType get() =
        checkNotNull(declarationScope) { "Pure binding cannot enter legacy scope lookup" }
    data class Bound(val type: MCFPPType, val constant: CompilerValue? = null,
                     val dependencies: Set<String> = emptySet(), val declaredTypeText: String? = null,
                     val descriptors: Map<TypeId, MCFPPType> = emptyMap(),
                     val superOwner: DataTemplate? = null,
                     val headerReceiver: PreparedTemplateReservation? = null,
                     val entityFacts: top.mcfpp.lib.EntityFacts? = null,
                     val entityAddress: Boolean = false)
    class Failure(message: String) : RuntimeException(message)
    private val template = declarationFunction?.owner as? DataTemplate
    private val readonly = ((template as? top.mcfpp.model.compound.CompiledGenericDataTemplate)?.originTemplate?.readOnlyParams
        ?.map { it.identifier }.orEmpty() + ((declarationFunction as? Generic<*>)?.readOnlyParams
        ?: (declarationFunction as? NativeFunction)?.readOnlyParams.orEmpty()).map { it.identifier }).toSet()

    fun expression(context: Parser.ExpressionContext): Bound = preparationGuard(context) { bind(context) }

    /** Target-directed binding for initializers whose declared type is already known. */
    internal fun expression(context: Parser.ExpressionContext, expected: MCFPPType?): Bound =
        preparationGuard(context) { bind(context, expected) }

    internal fun value(context: Parser.ValueContext): Bound = preparationGuard(context) { literal(context) }
    internal fun value(context: Parser.ValueContext, expected: MCFPPType?): Bound = preparationGuard(context) { literal(context, expected) }

    private inline fun <T> preparationGuard(site: ParserRuleContext, action: () -> T): T = try {
        preparation?.requireCurrentReader(site)
        action()
    }
        catch (failure: Failure) {
            if (preparation == null) throw failure
            throw PreparationFactFailure(PreparationFactCode.NON_CLOSED_VALUE, bindingSpan(site), failure.message.orEmpty())
        } catch (failure: NumberFormatException) {
            if (preparation == null) throw failure
            throw PreparationFactFailure(PreparationFactCode.NON_CLOSED_VALUE, bindingSpan(site), "Invalid closed numeric value")
        }

    internal fun readonly(context: Parser.ExpressionContext, expected: MCFPPType? = null): Bound =
        preparationGuard(context) { requireClosed(readonlyObjectType(context, expected) ?: bind(context), context) }
    internal fun readonlyValue(context: Parser.ValueContext): Bound = requireClosed(value(context), context)

    /** Braces in a readonly type actual can denote an object type without changing value expressions. */
    private fun readonlyObjectType(context: Parser.ExpressionContext, expected: MCFPPType?): Bound? {
        TemplateLiteralSupport.unwrapAmbiguousObject(context)?.let { syntax ->
            if (expected != MCFPPConcreteType.Type || TemplateLiteralSupport.ambiguousObjectEntries(syntax)?.isEmpty() != true)
                return null
            val type = MCFPPObjectType(emptyList())
            return Bound(MCFPPConcreteType.Type, CompilerValue.Typed(MCFPPConcreteType.Type.typeId,
                CompilerValue.TypeValue(type.typeId)), descriptors = mapOf(type.typeId to type))
        }
        val compound = TemplateLiteralSupport.unwrapNbtLiteral(context)?.nbtCompound() ?: return null
        val entries = TemplateLiteralSupport.compoundEntries(compound)
        if (entries.isEmpty() && expected != MCFPPConcreteType.Type) return null
        if (entries.any { (_, entry) -> entry.key.type != mcfppLexer.Identifier || entry.expression() == null }) return null
        val descriptors = linkedMapOf<TypeId, MCFPPType>()
        val dependencies = linkedSetOf<String>()
        val fields = arrayListOf<ObjectFieldContract>()
        for ((name, entry) in entries) {
            val syntax = checkNotNull(entry.expression())
            val nestedExpected = MCFPPConcreteType.Type.takeIf { expected == MCFPPConcreteType.Type }
            val bound = readonlyObjectType(syntax, nestedExpected) ?: bind(syntax)
            if (bound.type != MCFPPConcreteType.Type) return null
            val constant = (bound.constant as? CompilerValue.Typed)?.payload ?: bound.constant
            val identity = (constant as? CompilerValue.TypeValue)?.id ?: return null
            val type = (bound.descriptors[identity] ?: if (preparation == null) MCFPPType.resolveTypeId(identity) else null)
                ?: return null
            fields.add(ObjectFieldContract(name, type))
            descriptors.putAll(bound.descriptors)
            descriptors[type.typeId] = type
            dependencies.addAll(bound.dependencies)
        }
        val type = MCFPPObjectType(fields)
        descriptors[type.typeId] = type
        return Bound(MCFPPConcreteType.Type, CompilerValue.Typed(MCFPPConcreteType.Type.typeId,
            CompilerValue.TypeValue(type.typeId)), dependencies, descriptors = descriptors)
    }

    private fun readonlyFormal(candidates: List<Function>, index: Int): MCFPPType? = candidates.mapNotNull { candidate ->
        ((candidate as? Generic<*>)?.readOnlyParams ?: (candidate as? NativeFunction)?.readOnlyParams.orEmpty())
            .getOrNull(index)?.type
    }.distinctBy { it.typeId }.singleOrNull()

    private fun requireClosed(bound: Bound, site: ParserRuleContext): Bound {
        if (bound.constant == null) throw PreparationFactFailure(PreparationFactCode.NON_CLOSED_VALUE,
            bindingSpan(site), "Readonly value is not closed; dynamic composite values require runtime binding")
        return bound
    }

    private fun unsupported(site: ParserRuleContext, message: String): Nothing =
        throw PreparationFactFailure(PreparationFactCode.UNSUPPORTED_EXPRESSION, bindingSpan(site), message)

    private fun closedValue(argument: Bound): top.mcfpp.core.lang.Var<*> {
        val snapshot = argument.constant ?: throw Failure("Readonly declaration arguments require a complete value")
        val types = argument.descriptors.toMutableMap().apply { put(argument.type.typeId, argument.type) }
        MCFPPType.registerSnapshotTypes(snapshot, types)
        return StorageAccess.restore(argument.type, snapshot, "readonly", types)
            ?: throw Failure("Readonly declaration argument '${argument.type}' has no closed layout")
    }

    internal fun constrain(value: Bound, target: MCFPPType, declaration: String? = null): Bound {
        val literalTargetProof = if (target is MCFPPLiteralType && value.constant != null) {
            val sourceLiteral = value.type as? MCFPPLiteralType
                ?: TypeCompositions.literalOrNull(value.type, value.constant)
            if (sourceLiteral?.typeId == target.typeId) CompilerValue.Typed(target.typeId, target.value) else null
        } else null
        if (preparation != null) {
            val proof = when {
                value.type.typeId == target.typeId -> value.constant
                literalTargetProof != null -> literalTargetProof
                value.type is MCFPPLiteralType && target.typeId == value.type.primitive.typeId && value.constant != null ->
                    CompilerValue.Typed(target.typeId, value.type.value)
                else -> top.mcfpp.mni.expand.SourceConstantFacts.compilerConvert(value.constant, value.type.typeId, target.typeId)
                    ?.let { CompilerValue.Typed(target.typeId, it) }
            }
            return value.copy(type = target, constant = proof, declaredTypeText = declaration ?: value.declaredTypeText)
        }
        val snapshot = when {
            value.type == target -> value.constant
            literalTargetProof != null -> literalTargetProof
            target == MCFPPBaseType.Any || target == MCFPPBaseType.Object ->
                value.constant?.let { CompilerValue.Typed(target.typeId, it) }
            value.type is MCFPPLiteralType && TypeRelations.isSubtype(value.type, target) ->
                value.constant?.let { CompilerValue.Typed(target.typeId, (it as? CompilerValue.Typed)?.payload ?: it) }
            target is MCFPPUnionType && target.types.any { value.type.isSubOf(it) } -> value.constant
            TypeCompositions.objectShape(value.type) != null && TypeCompositions.objectShape(target) != null &&
                TypeRelations.isSubtype(value.type, target) -> value.constant?.let {
                    CompilerValue.Typed(target.typeId, (it as? CompilerValue.Typed)?.payload ?: it)
                }
            target is MCFPPDataTemplateType && value.type is MCFPPDataTemplateType && value.type.isSubOf(target) -> value.constant
            target == MCFPPBaseType.Float && value.type == MCFPPBaseType.Int ->
                ((value.constant as? CompilerValue.Typed)?.payload as? CompilerValue.Integral)?.let {
                    CompilerValue.Typed(target.typeId, CompilerValue.FloatBits(it.value.toInt().toFloat().toRawBits()))
                }
            else -> null
        }
        return value.copy(type = target, constant = snapshot, declaredTypeText = declaration ?: value.declaredTypeText)
    }

    /**
     * Declaration-time binding for a lambda literal. The runtime function-value lowering is a
     * later compiler boundary; this path only proves parameters/returns against a target type.
     */
    private fun bindLambda(syntax: Parser.InlineLambdaContext, expected: MCFPPType?): Bound {
        if (expected != null && expected !is MCFPPFunctionType)
            throw Failure("A lambda initializer requires a function type, not '${expected.typeName}'")
        val targets = if (expected == null) lambdaTargetCandidates(syntax) else emptyList()
        val binding = if (targets.isEmpty()) top.mcfpp.analysis.lambda.LambdaBinding.bind(
            syntax, expected as? MCFPPFunctionType, lambdaEnvironment())
        else top.mcfpp.analysis.lambda.LambdaBinding.bind(syntax, targets, lambdaEnvironment())
        return when (val bound = binding) {
            is top.mcfpp.analysis.lambda.LambdaBinding.Result.Bound ->
                Bound(bound.value.signature, declaredTypeText = bound.value.signature.typeName)
            is top.mcfpp.analysis.lambda.LambdaBinding.Result.Rejected ->
                throw Failure(bound.diagnostic.render())
        }
    }

    private fun bindInlineLambda(syntax: Parser.InlineLambdaContext, expected: MCFPPType?): Bound =
        if (expected !is MCFPPFunctionType && TemplateLiteralSupport.ambiguousObjectEntries(syntax) != null)
            ambiguousObjectLiteral(syntax, expected) else bindLambda(syntax, expected)

    internal fun compoundLiteral(node: ParserRuleContext, expected: MCFPPType?): Bound =
        TemplateLiteralSupport.unwrapNbtLiteral(node)?.let { nbtLiteral(it, expected) }
            ?: TemplateLiteralSupport.unwrapAmbiguousObject(node)?.let { ambiguousObjectLiteral(it, expected) }
            ?: throw Failure("Expression is not an object literal")

    internal fun ambiguousObjectLiteral(syntax: Parser.InlineLambdaContext, expected: MCFPPType?): Bound =
        preparationGuard(syntax) {
            val entries = TemplateLiteralSupport.ambiguousObjectEntries(syntax)
                ?: throw Failure("Expression is not an object shorthand literal")
            val target = if (expected is MCFPPUnionType)
                TemplateLiteralSupport.selectExpectedLiteralType(syntax, expected.types.toList()) { candidate ->
                    try { ambiguousObjectLiteral(syntax, candidate); true } catch (_: Failure) { false }
                } ?: expected else expected
            val dictionary = target as? MCFPPDictType
            val shapeTarget = target?.let(TypeCompositions::objectShape)
            val members = entries.map { (name, expression) ->
                val fieldTarget = dictionary?.generic?.single() ?: shapeTarget?.fieldsByName?.get(name)?.type
                val raw = if (TemplateLiteralSupport.hasInactiveObjectParameter(syntax, name))
                    variable(name, expression, ignoreStructured = true) else bind(expression, fieldTarget)
                val bound = if (dictionary != null) {
                    val element = dictionary.generic.single()
                    if (TypeRelations.resolveImplicitConversion(raw.type, element) == null)
                        throw Failure("Dictionary value for '$name' expects '${element.typeName}', found '${raw.type.typeName}'")
                    constrain(raw, element)
                } else contextualObjectField(raw, fieldTarget)
                name to bound
            }
            val type = when {
                target == MCFPPNBTType.NBT -> MCFPPNBTType.NBT
                dictionary != null -> dictionary
                else -> MCFPPObjectType(members.map { (name, bound) -> ObjectFieldContract(name, bound.type) })
            }
            if (target != null && TypeRelations.resolveImplicitConversion(type, target) == null)
                throw Failure("Object literal '${type.typeName}' does not satisfy '${target.typeName}'")
            val payload = if (members.all { it.second.constant != null })
                CompilerValue.Record(members.associate { it.first to it.second.constant!! }) else null
            val result = Bound(type, payload?.let { CompilerValue.Typed(type.typeId, it) },
                members.flatMap { it.second.dependencies }.toSet(),
                descriptors = members.flatMap { it.second.descriptors.entries }.associate { it.key to it.value } +
                    members.associate { it.second.type.typeId to it.second.type } + mapOf(type.typeId to type))
            if (shapeTarget == null) result else constrain(result, checkNotNull(target))
        }

    /**
     * Binds a direct call whose final argument is a trailing lambda. This is the declaration
     * fact SYNC_CALL consumes; no command or runtime function value is constructed here.
     */
    private fun bindLambdaCall(syntax: Parser.LambdaCallExpressionContext): Bound {
        if (syntax.jvmAccessExpression() != null) {
            var receiver = bind(syntax.jvmAccessExpression().propertyOperator().primary())
            val before = syntax.selector().filter { it.start.tokenIndex < syntax.inlineLambda().start.tokenIndex }
            for (selector in before.dropLast(1)) receiver = bindSelector(receiver, selector)
            before.lastOrNull()?.let { top.mcfpp.core.lang.entity.EntityReadCallbacks.selector(it, syntax.inlineLambda()) }?.let {
                entityReadPlan(receiver.type, it)
                receiver = Bound(MCFPPBaseType.Bool, dependencies = receiver.dependencies)
                for (selector in syntax.selector().drop(before.size)) receiver = bindSelector(receiver, selector)
                return receiver
            }
            val context = before.lastOrNull()?.let { ContextCallbacks.selector(it, syntax.inlineLambda(), receiver.type) }
            if (context != null) {
                val plan = contextPlan(receiver.type, context, receiver.entityFacts)
                if (syntax.selector().size != before.size)
                    throw Failure("Context '${context.name}' returns void; its callback result cannot be selected")
                return Bound(plan.result, dependencies = receiver.dependencies)
            }
            val collection = before.lastOrNull()?.let {
                top.mcfpp.analysis.lambda.LambdaCollections.selector(it, syntax.inlineLambda())
            } ?: throw Failure("Trailing lambdas on this receiver have no collection target")
            if (!top.mcfpp.analysis.lambda.LambdaCollections.supports(receiver.type, collection.name))
                throw Failure("Collection '${collection.name}' requires a list receiver")
            receiver = Bound(collectionPlan(receiver.type, collection).result)
            for (selector in syntax.selector().drop(before.size)) receiver = bindSelector(receiver, selector)
            return receiver
        }
        val call = syntax.functionCall()
        if (preparation == null && call != null && syntax.selector().isEmpty())
            top.mcfpp.mni.expand.MacroCallPipeline.declaredResultType(call, function, values, contextDeclarations,
                syntax.inlineLambda())?.let { return Bound(it) }
        top.mcfpp.analysis.lambda.LambdaCollections.global(call, syntax.inlineLambda(), syntax.varWithSuffix())?.let {
            var result = Bound(collectionPlan(MCFPPPrivateType.Void, it).result)
            for (selector in syntax.selector()) result = bindSelector(result, selector)
            return result
        }
        ContextCallbacks.global(call, syntax.inlineLambda(), syntax.varWithSuffix())?.let {
            return Bound(contextPlan(MCFPPPrivateType.Void, it).result)
        }
        val name = call?.namespaceID()?.text
            ?: syntax.varWithSuffix()?.takeIf { it.identifierSuffix().isEmpty() }?.Identifier()?.text
            ?: throw Failure("Trailing lambdas on receivers require the function-value call boundary")
        if (syntax.selector().isNotEmpty())
            throw Failure("Selecting from a trailing-lambda call result requires the function-value call boundary")
        val candidates = (call?.namespaceID() ?: syntax.varWithSuffix())?.let { StructuredBindings.functions(it, declarationFunction) }
            ?: functionCandidates(name)
        if (candidates.isEmpty()) throw Failure("Cannot resolve function '$name'")
        val normalSyntax = call?.arguments()?.normalArgs()
        if (normalSyntax?.callArgumentList() != null)
            throw Failure("Mixed call arguments with a trailing lambda require the function-value call boundary")
        val normal = normalSyntax?.expressionList()?.expression().orEmpty().map(::bind)
        val readonly = call?.arguments()?.readOnlyArgs()?.expressionList()?.expression().orEmpty().mapIndexed { index, syntax ->
            readonly(syntax, readonlyFormal(candidates, index))
        }
        val lambdaIndex = normal.size
        val matches = arrayListOf<Pair<Function, Map<String, MCFPPType>>>()
        var firstFailure: top.mcfpp.analysis.lambda.LambdaBinding.Diagnostic? = null
        for (function in candidates) {
            val bindings = readonlyTypeBindings(function, readonly) ?: continue
            val parameterTypes = top.mcfpp.analysis.lambda.LambdaBinding.parameterTypes(function, bindings) ?: continue
            if (normal.size + 1 > parameterTypes.size) continue
            if (function.normalParams.drop(normal.size + 1).any { !it.hasDefault }) continue
            val lambdaTarget = parameterTypes[lambdaIndex] as? MCFPPFunctionType ?: continue
            when (val result = top.mcfpp.analysis.lambda.LambdaBinding.bind(
                syntax.inlineLambda(), lambdaTarget, lambdaEnvironment())) {
                is top.mcfpp.analysis.lambda.LambdaBinding.Result.Bound -> Unit
                is top.mcfpp.analysis.lambda.LambdaBinding.Result.Rejected -> {
                    if (firstFailure == null) firstFailure = result.diagnostic
                    continue
                }
            }
            if (normal.indices.any {
                    TypeRelations.resolveImplicitConversion(normal[it].type, parameterTypes[it]) == null
                }) continue
            // The selected declaration and its readonly substitutions own the call result;
            // the lambda's own signature is only used to prove the callback binding.
            matches += function to bindings
        }
        if (matches.isEmpty()) throw Failure(firstFailure?.render()
            ?: "Cannot bind trailing-lambda call '$name' to a declared target signature")
        if (matches.size > 1) throw Failure(ParameterMatcher.ambiguity(name, matches.map { it.first }))
        val selected = matches.single()
        val result = top.mcfpp.analysis.lambda.LambdaBinding.returnType(selected.first, selected.second)
            ?: throw Failure("Cannot bind return type '$name'")
        return Bound(result, dependencies = readonly.flatMap { it.dependencies }.toSet() +
            normal.flatMap { it.dependencies }.toSet())
    }

    private fun readonlyTypeBindings(function: Function, readonly: List<Bound>): Map<String, MCFPPType>? {
        val parameters = (function as? Generic<*>)?.readOnlyParams
            ?: (function as? NativeFunction)?.readOnlyParams.orEmpty()
        if (readonly.size > parameters.size) return null
        if (parameters.drop(readonly.size).any { !it.hasDefault }) return null
        val bindings = linkedMapOf<String, MCFPPType>()
        for ((index, parameter) in parameters.take(readonly.size).withIndex()) {
            val value = readonly[index]
            val payload = (value.constant as? CompilerValue.Typed)?.payload as? CompilerValue.TypeValue
            if (payload == null) {
                if (parameter.type == MCFPPConcreteType.Type) return null
                continue
            }
            bindings[parameter.identifier] = value.descriptors[payload.id]
                ?: MCFPPType.resolveTypeId(payload.id) ?: return null
        }
        return bindings
    }

    /** Resolves a bare ordinary function name as a function value under the target signature. */
    private fun bindFunctionReference(variable: Parser.VarContext, target: MCFPPFunctionType): Bound {
        val suffix = variable.varWithSuffix()
            ?: throw Failure("A function value reference must be a bare function name")
        if (suffix.identifierSuffix().isNotEmpty())
            throw Failure("A function value reference cannot be indexed")
        val name = suffix.Identifier().text
        val boundCandidates = StructuredBindings.functions(suffix, declarationFunction)
        val binding = StructuredBindings.name(suffix, declarationFunction)
        if (binding is StructuredNameBinding.Frame || binding is StructuredNameBinding.Value)
            return this.variable(name, suffix)
        if (preparation == null && boundCandidates == null && (name in fields || name in values || lookupVariable(name) != null))
            return this.variable(name, variable)
        val candidates = boundCandidates ?: functionCandidates(name)
        if (candidates.isEmpty()) throw Failure("Cannot resolve function reference '$name'")
        return when (val bound = top.mcfpp.analysis.lambda.LambdaBinding.resolveReference(
            name, target, candidates, variable
        )) {
            is top.mcfpp.analysis.lambda.LambdaBinding.Result.Bound ->
                Bound(target, declaredTypeText = bound.value.signature.typeName)
            is top.mcfpp.analysis.lambda.LambdaBinding.Result.Rejected ->
                throw Failure(bound.diagnostic.render())
        }
    }

    internal fun lambdaEnvironment(): top.mcfpp.analysis.lambda.LambdaBinding.Environment {
        val scope = declarationScope
        val symbols = LinkedHashMap<String, MCFPPType>()
        declarationFunction?.scope?.let { lexical -> StorageAccess.visibleValues(lexical).forEach { symbols[it.identifier] = it.type } }
        var lexical: top.mcfpp.model.scope.FunctionScope? = declarationFunction?.scope
        while (lexical != null) {
            if (lexical is top.mcfpp.analysis.lambda.LambdaCaptureScope) {
                symbols.putAll(lexical.lexicalTypes())
                StorageAccess.visibleValues(declarationFunction!!.scope).asReversed()
                    .forEach { symbols[it.identifier] = it.type }
                break
            }
            lexical = lexical.parent.firstOrNull() as? top.mcfpp.model.scope.FunctionScope
        }
        fields.forEach { (name, type) -> symbols[name] = type }
        values.forEach { (name, value) -> symbols[name] = value.type }
        declarationFunction?.normalParams?.forEach { symbols[it.identifier] = it.type }
        return top.mcfpp.analysis.lambda.LambdaBinding.Environment(
            caller = declarationFunction,
            scope = scope,
            resolveType = { syntax -> type(syntax) },
            functions = { name -> functionCandidates(name) },
            symbolTypes = symbols,
            // Named call tails reuse the same declaration lookup as every other call.
            callReturnType = { syntax ->
                try {
                    call(syntax).type
                } catch (_: Failure) {
                    null
                } catch (_: PreparationFactFailure) {
                    null
                } catch (_: TypeSyntaxFailure) {
                    null
                }
            },
            lambdaTargets = ::lambdaTargetCandidates,
            callReturnTypeWithArguments = { syntax, argumentTypes ->
                try {
                    // The lambda owns these normal argument bindings. Reuse the same call
                    // binder for overloads/readonly/generics without looking them up in
                    // the creator's scope or evaluating their source expressions.
                    call(syntax, argumentTypes = argumentTypes.takeIf { types -> types.all { it != null } }
                        ?.filterNotNull()).type
                } catch (_: Failure) {
                    null
                } catch (_: PreparationFactFailure) {
                    null
                } catch (_: TypeSyntaxFailure) {
                    null
                }
            }
        )
    }

    /**
     * Target signatures for a nested lambda, resolved from the enclosing call or declared
     * type before lexical `it` scanning. Overloads stay a list so LambdaBinding can report
     * ambiguity instead of guessing one candidate.
     */
    private fun lambdaTargetCandidates(lambda: Parser.InlineLambdaContext): List<MCFPPFunctionType> {
        return try {
            var node: ParseTree = lambda
            var parent: ParseTree? = lambda.parent
            while (parent != null) {
                when (val context = parent) {
                    is Parser.LambdaCallExpressionContext -> {
                        if (context.jvmAccessExpression() != null) {
                            var receiver = bind(context.jvmAccessExpression().propertyOperator().primary())
                            val before = context.selector().filter { it.start.tokenIndex < context.inlineLambda().start.tokenIndex }
                            for (selector in before.dropLast(1)) receiver = bindSelector(receiver, selector)
                            before.lastOrNull()?.let { top.mcfpp.core.lang.entity.EntityReadCallbacks.selector(it, context.inlineLambda()) }?.let {
                                return listOf(top.mcfpp.core.lang.entity.EntityReadCallbacks.signature(receiver.type))
                            }
                            before.lastOrNull()?.let { ContextCallbacks.selector(it, context.inlineLambda(), receiver.type) }?.let {
                                return listOf(ContextCallbacks.target(receiver.type, it))
                            }
                            return emptyList()
                        }
                        val call = context.functionCall()
                        if (call == null) {
                            val name = context.varWithSuffix()?.takeIf { it.identifierSuffix().isEmpty() }
                                ?.Identifier()?.text ?: return emptyList()
                            return callLambdaTargets(name, emptyList(), 0, emptyList(), 0)
                        }
                        val normalSyntax = call.arguments().normalArgs()
                        if (normalSyntax.callArgumentList() != null) return emptyList()
                        val normal = normalSyntax.expressionList()?.expression().orEmpty().map(::bind)
                        return callLambdaTargets(call, normal.size, normal, 0)
                    }
                    is Parser.CallArgumentContext -> {
                        val call = generateSequence(context.parent) { it.parent }
                            .filterIsInstance<Parser.FunctionCallContext>().firstOrNull() ?: return emptyList()
                        val arguments = call.arguments().normalArgs().callArgumentList()?.callArgument().orEmpty()
                        val index = arguments.indexOfFirst { it.inlineLambda() === lambda }
                        if (index < 0) return emptyList()
                        val normal = arguments.take(index).mapNotNull { it.expression()?.let(::bind) }
                        return callLambdaTargets(call, index, normal, arguments.size - index - 1)
                    }
                    is Parser.ExpressionListContext -> {
                        val args = context.parent as? Parser.NormalArgsContext ?: return emptyList()
                        val call = generateSequence(args.parent) { it.parent }
                            .filterIsInstance<Parser.FunctionCallContext>().firstOrNull() ?: return emptyList()
                        val index = context.expression().indexOfFirst { it === node }
                        if (index < 0) return emptyList()
                        val normal = context.expression().take(index).map(::bind)
                        return callLambdaTargets(call, index, normal, context.expression().size - index - 1)
                    }
                    is Parser.FieldDeclarationContext -> {
                        val declared = context.type()?.let { type(it) } as? MCFPPFunctionType
                            ?: return emptyList()
                        return listOf(declared)
                    }
                    is Parser.ExpressionContext, is Parser.PrimaryContext,
                    is Parser.UnaryExpressionContext, is Parser.CastExpressionContext,
                    is Parser.RightVarExpressionContext, is Parser.VarWithSelectorContext,
                    is Parser.VarContext, is Parser.BucketExpressionContext,
                    is Parser.JvmAccessExpressionContext, is Parser.PropertyOperatorContext -> {
                        node = context
                        parent = context.parent
                    }
                    else -> return emptyList()
                }
            }
            emptyList()
        } catch (_: Failure) {
            emptyList()
        } catch (_: PreparationFactFailure) {
            emptyList()
        } catch (_: TypeSyntaxFailure) {
            emptyList()
        }
    }

    private fun callLambdaTargets(
        call: Parser.FunctionCallContext,
        lambdaIndex: Int,
        normal: List<Bound>,
        remainingArguments: Int
    ): List<MCFPPFunctionType> {
        val candidates = StructuredBindings.functions(call.namespaceID(), declarationFunction) ?: functionCandidates(call.namespaceID().text)
        val readonly = call.arguments().readOnlyArgs()?.expressionList()?.expression().orEmpty().mapIndexed { index, syntax ->
            readonly(syntax, readonlyFormal(candidates, index))
        }
        return callLambdaTargets(call.namespaceID().text, readonly, lambdaIndex, normal, remainingArguments,
            StructuredBindings.functions(call.namespaceID(), declarationFunction))
    }

    private fun callLambdaTargets(
        name: String,
        readonly: List<Bound>,
        lambdaIndex: Int,
        normal: List<Bound>,
        remainingArguments: Int,
        boundCandidates: List<Function>? = null
    ): List<MCFPPFunctionType> {
        val targets = LinkedHashSet<MCFPPFunctionType>()
        for (function in boundCandidates ?: functionCandidates(name)) {
            val bindings = readonlyTypeBindings(function, readonly) ?: continue
            val parameterTypes = top.mcfpp.analysis.lambda.LambdaBinding.parameterTypes(function, bindings) ?: continue
            if (lambdaIndex + remainingArguments >= parameterTypes.size) continue
            if (function.normalParams.drop(lambdaIndex + remainingArguments + 1).any { !it.hasDefault }) continue
            if (normal.indices.any { index ->
                    index >= parameterTypes.size ||
                        TypeRelations.resolveImplicitConversion(normal[index].type, parameterTypes[index]) == null
                }) continue
            (parameterTypes[lambdaIndex] as? MCFPPFunctionType)?.let { targets += it }
        }
        return targets.toList()
    }

    private fun functionCandidates(name: String): List<Function> {
        val (namespace, identifier) = name.splitNamespaceID()
        preparation?.let { return it.lexical.functions(namespace, identifier) }
        val caller = declarationFunction ?: return emptyList()
        return GlobalScope.getFunctionCandidates(namespace, identifier,
            caller.restoreDeclarationEnvironment()?.field ?: top.mcfpp.io.MCFPPFile.currFile?.field)
    }

    private fun variable(name: String, site: ParserRuleContext? = null, ignoreStructured: Boolean = false): Bound {
        if (!ignoreStructured && site != null && StructuredBindings.isBound(site)) {
            val binding = StructuredBindings.name(site, declarationFunction)
            if (binding is StructuredNameBinding.Frame) contextDeclarations[binding.declaration]?.let { return it }
            if (preparation != null && binding is StructuredNameBinding.Frame)
                return preparedLocal(binding.declaration, site)
            if (binding is StructuredNameBinding.Type) return Bound(MCFPPConcreteType.Type,
                CompilerValue.Typed(MCFPPConcreteType.Type.typeId, CompilerValue.TypeValue(binding.type.typeId)),
                descriptors = mapOf(binding.type.typeId to binding.type))
            if (preparation != null) throw Failure("LIB_BODY_UNSUPPORTED: Pure bound value requires immutable declaration facts")
            if (literalDefaultsOnly) throw Failure("Default depends on a runtime declaration")
            val value = StructuredBindings.variable(site, function) ?: throw Failure("Bound name '$name' is not a value")
            val ordinaryParameter = binding is StructuredNameBinding.Frame &&
                StructuredBindings.frame(function).normalParams.any { it.identifier == binding.identifier }
            return Bound(value.type, if (captureValues || !ordinaryParameter) ConstantBinding.semanticSnapshot(value) else null,
                dependencies = if (name in readonly) setOf(name) else emptySet(),
                descriptors = StorageAccess.boundTypes(value))
        }
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
            return Bound(it.type, declaredTypeText = text ?: it.typeName, entityFacts = lookupVariable(name)?.let(::entityFacts))
        }
        if (name in readonly) lookupVariable(name)?.let { value ->
            return Bound(value.type, ConstantBinding.semanticSnapshot(value), setOf(name), descriptors = StorageAccess.boundTypes(value), entityFacts = entityFacts(value))
        }
        fields[name]?.let { return Bound(it, declaredTypeText = fieldDeclarationText(name)) }
        val value = lookupVariable(name)
        if (value != null) return Bound(value.type, ConstantBinding.semanticSnapshot(value),
            if (name in readonly) setOf(name) else emptySet(), descriptors = StorageAccess.boundTypes(value), entityFacts = entityFacts(value))
        GlobalScope.getObject(null, name)?.let { return Bound(it.getType()) }
        (lookupScope.getType(name) ?: GlobalScope.getTemplate(null, name)?.getType() ?: GlobalScope.getEnum(null, name)?.let { MCFPPEnumType(it) }
            ?: EntityTypes.named(name))?.let { type ->
            return Bound(MCFPPConcreteType.Type, CompilerValue.Typed(MCFPPConcreteType.Type.typeId,
                CompilerValue.TypeValue(type.typeId)), descriptors = mapOf(type.typeId to type))
        }
        throw Failure("Cannot bind declaration name '$name'")
    }

    private fun entityFacts(value: Var<*>): top.mcfpp.lib.EntityFacts? {
        val borrow = top.mcfpp.core.lang.context.ContextBorrowing.payload(value)
        return when (borrow) {
            is top.mcfpp.core.lang.context.ContextBorrowPayload.Executor -> borrow.binding.facts
            is top.mcfpp.core.lang.context.ContextBorrowPayload.Context -> borrow.owner.executorFacts
            else -> (value as? top.mcfpp.core.lang.entity.EntityTargetAccess)?.entityFacts
                ?: (value as? top.mcfpp.core.lang.context.ExecutionContextVar)?.context?.executorFacts
        }
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

    private fun bind(node: ParseTree): Bound = bind(node, null)

    private fun integralLiteral(node: ParserRuleContext): Bound? = when (val parsed = IntegralLiteralSyntax.parse(node)) {
        is IntegralLiteralSyntax.Result.Value -> Bound(parsed.type, CompilerValue.Typed(parsed.type.typeId, parsed.value))
        is IntegralLiteralSyntax.Result.Invalid -> if (preparation != null) unsupported(node, parsed.diagnostic)
            else throw Failure(parsed.diagnostic)
        null -> null
    }

    private fun bind(node: ParseTree, expected: MCFPPType?): Bound = when (node) {
        is Parser.InlineLambdaContext -> bindInlineLambda(node, expected)
        is Parser.ExpressionContext -> when {
            node.lambdaCallExpression() != null -> bindLambdaCall(checkNotNull(node.lambdaCallExpression()))
            node.primary()?.inlineLambda() != null -> bindInlineLambda(checkNotNull(node.primary()?.inlineLambda()), expected)
            else -> node.primary()?.let { bind(it, expected) }
                ?: bind(node.commonBinaryOperatorExpression(), expected)
        }
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
                    source.type !in setOf(MCFPPBaseType.Any, MCFPPBaseType.Object) || StorageAccess.constantLayoutMatches(represented, target))
                    CompilerValue.Typed(target.typeId, if (represented is CompilerValue.Typed) represented.payload else represented)
                else null
            }
            Bound(target, snapshot, source.dependencies + dependencies(node.type()), node.type().text, source.descriptors)
        } else bind(node.unaryExpression(), expected)
        is Parser.UnaryExpressionContext -> integralLiteral(node) ?: node.rightVarExpression()?.let { bind(it, expected) }
            ?: bind(node.unaryExpression()).let {
            val operandType = (it.type as? MCFPPLiteralType)?.primitive ?: it.type
            if (preparation != null) {
                val operator = if (node.EXCL() != null) top.mcfpp.mni.api.UnaryOperator.NOT else top.mcfpp.mni.api.UnaryOperator.NEGATE
                val result = top.mcfpp.mni.expand.SourceConstantFacts.compilerUnary(operator, it.constant)
                val type = if (node.EXCL() != null) MCFPPBaseType.Bool else operandType
                return@let Bound(type, result?.let { value -> CompilerValue.Typed(type.typeId, value) }, it.dependencies,
                    descriptors = it.descriptors)
            }
            val value = (it.constant as? CompilerValue.Typed)?.payload
            val type = if (node.EXCL() != null) MCFPPBaseType.Bool else operandType
            val constant = when {
                node.EXCL() != null && value is CompilerValue.Bool -> CompilerValue.Bool(!value.value)
                node.EXCL() == null && operandType == MCFPPBaseType.Int && value is CompilerValue.Integral -> CompilerValue.Integral((-value.value.toInt()).toLong())
                node.EXCL() == null && operandType == MCFPPBaseType.Float && value is CompilerValue.FloatBits && Float.fromBits(value.bits).isFinite() -> CompilerValue.FloatBits((-Float.fromBits(value.bits)).toRawBits())
                else -> null
            }
            Bound(type, constant?.let { payload -> CompilerValue.Typed(type.typeId, payload) }, it.dependencies)
        }
        is Parser.VarWithSelectorContext -> {
            if (preparation != null && node.jvmAccessExpression().propertyOperator().propertyOperatorExpression().isNotEmpty())
                unsupported(node, "Property operators cannot supply readonly facts")
            var result = bind(node.jvmAccessExpression().propertyOperator().primary(),
                if (node.selector().isEmpty()) expected else null)
            for ((index, selector) in node.selector().withIndex()) {
                val variable = selector.`var`()
                if (selector.memberKeyword()?.text == "text" && selector.arguments() == null) {
                    val member = top.mcfpp.core.lang.entity.DisplayProperties.memberType(result.type, "text")
                        ?: throw Failure("Unknown display property 'text'")
                    result = Bound(member, dependencies = result.dependencies)
                    continue
                }
                val context = ContextCallbacks.selector(selector, receiver = result.type)
                if (context != null) {
                    result = Bound(contextPlan(result.type, context, result.entityFacts).result)
                    continue
                }
                val collection = top.mcfpp.analysis.lambda.LambdaCollections.selector(selector)
                if (collection != null && top.mcfpp.analysis.lambda.LambdaCollections.supports(result.type, collection.name)) {
                    result = Bound(collectionPlan(result.type, collection).result)
                    continue
                }
                if (variable == null) {
                    result = bindSelector(result, selector)
                    continue
                }
                val name = variable.varWithSuffix()?.takeIf { it.identifierSuffix().isEmpty() }?.Identifier()?.text
                if (index == node.selector().lastIndex && expected is MCFPPFunctionType && name != null &&
                    top.mcfpp.analysis.lambda.LambdaMemberReference.hasMethod(result.type, name)) {
                    top.mcfpp.analysis.lambda.LambdaMemberReference.resolve(result.type, name, expected, selector, function)
                    result = Bound(expected)
                } else result = member(result, variable)
            }
            result
        }
        is Parser.PrimaryContext -> when {
            expected is MCFPPFunctionType && expected.parameters.isEmpty() && expected.result == MCFPPPrivateType.Void &&
                node.value()?.nbtValue()?.nbtCompound()?.text == "{}" -> Bound(expected)
            node.inlineLambda() != null -> bindInlineLambda(checkNotNull(node.inlineLambda()), expected)
            node.`var`()?.varWithSuffix() != null && expected is MCFPPFunctionType ->
                bindFunctionReference(checkNotNull(node.`var`()), expected)
            node.THIS() != null -> {
                if (declarationFunction is TemplateFactory)
                    throw Failure("Factory has no implicit 'this'; declare an ordinary parameter")
                preparation?.receiver(node) ?: values["this"]
                    ?: Bound(template?.getType()?.let { if (function.isEntityMember) MCFPPEntityRefType(it) else it }
                        ?: throw Failure("'this' requires a declaration owner"))
            }
            node.SUPER() != null -> throw Failure("'super' is unavailable: template functions are receiver functions")
            node.type() != null -> {
                val type = type(node.type())
                Bound(MCFPPConcreteType.Type, CompilerValue.Typed(MCFPPConcreteType.Type.typeId, CompilerValue.TypeValue(type.typeId)), dependencies(node.type()), descriptors = mapOf(type.typeId to type))
            }
            node.range() != null -> range(node.range())
            node.value() != null -> literal(node.value(), expected)
            else -> bind(node.`var`(), expected)
        }
        is Parser.VarContext -> when {
            node.functionCall() != null -> call(node.functionCall())
            node.bucketExpression() != null -> node.bucketExpression().expression()?.let { bind(it, expected) }
                ?: throw Failure("An empty expression cannot initialize a declaration")
            else -> {
                val suffixNode = node.varWithSuffix()
                val enumTarget = expected as? MCFPPEnumType
                if (enumTarget != null && suffixNode.identifierSuffix().isEmpty()) {
                    val memberName = suffixNode.Identifier().text
                    val enumMember = try { variable(memberName, node) } catch (_: Failure) { null }
                    if (enumMember != null) suffix(enumMember, suffixNode)
                    else enumTarget.enum.members[memberName]?.let { member ->
                        Bound(enumTarget, CompilerValue.Typed(enumTarget.typeId, CompilerValue.Record(mapOf(
                            "ordinal" to CompilerValue.Integral(member.value.toLong()),
                            "data" to CompilerValue.Nbt(top.mcfpp.backend.NbtEncoding.snbt(member.data))))))
                    } ?: suffix(variable(memberName, node), suffixNode)
                } else suffix(variable(suffixNode.Identifier().text, node), suffixNode)
            }
        }
        is Parser.RightVarExpressionContext -> bind(node.varWithSelector(), expected)
        else -> {
            val children = (0 until node.childCount).map(node::getChild).filterIsInstance<ParserRuleContext>()
            if (children.isEmpty()) throw Failure("Cannot bind declaration expression '${node.text}'")
            if (children.size == 1) bind(children.single(), expected) else binary(node, children.map(::bind))
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
                is MCFPPNBTRefType -> {
                    if (!result.entityAddress) throw Failure("Only explicit entity NBT addresses support address projection")
                    val element = when (val stored = type.valueType) {
                        is MCFPPListType, is MCFPPImmutableListType -> {
                            if (keyBinding?.type != MCFPPBaseType.Int) throw Failure("Entity list address requires an int index")
                            (stored as MCFPPTypeWithGeneric).generic.single()
                        }
                        is MCFPPDictType -> {
                            if (keyBinding?.type != MCFPPBaseType.String) throw Failure("Entity dictionary address requires a string key")
                            stored.generic.single()
                        }
                        else -> TypeRelations.arrayElementType(stored.typeId)
                            ?: throw Failure("Entity NBT address '$stored' cannot be indexed")
                    }
                    Bound(MCFPPNBTRefType(element), dependencies = result.dependencies, entityAddress = true)
                }
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

    internal fun collectionPlan(receiver: MCFPPType, call: top.mcfpp.analysis.lambda.LambdaCollections.Call) =
        top.mcfpp.analysis.lambda.LambdaCollections.plan(receiver, call, lambdaEnvironment()) { node, expected -> bind(node, expected).type }

    internal fun entityReadPlan(reference: MCFPPType, call: top.mcfpp.core.lang.entity.EntityReadCallbacks.Call): top.mcfpp.analysis.lambda.LambdaBinding.BoundLambda {
        val receiver = (reference as? MCFPPReferenceType)?.valueType ?: reference
        val callback = top.mcfpp.core.lang.entity.EntityReadCallbacks.signature(receiver)
        val syntax = call.lambda()
        val explicit = syntax.lambdaParameterList()?.lambdaParameter().orEmpty()
        if (explicit.isNotEmpty() && explicit.size != 1) throw Failure("tryRead callback expects one parameter")
        val parameters = if (explicit.isEmpty()) mapOf("it" to Bound(receiver)) else {
            val parameter = explicit.single()
            val declared = parameter.type()?.let(::type)
            if (declared != null && TypeRelations.resolveImplicitConversion(receiver, declared) == null)
                throw Failure("tryRead passes '$receiver', incompatible with callback parameter '$declared'")
            mapOf(parameter.Identifier().text to Bound(declared ?: receiver))
        }
        val nested = arrayListOf<ContextCallbacks.Plan>()
        val lexical = withContextValues(parameters, nested,
            declarations = callbackDeclarations(syntax, parameters.values.single())).bindContextBody(syntax)
        val environment = ContextCallbacks.environment(lexical.lambdaEnvironment(), nested)
        return when (val result = top.mcfpp.analysis.lambda.LambdaBinding.bind(syntax, callback, environment)) {
            is top.mcfpp.analysis.lambda.LambdaBinding.Result.Bound -> result.value
            is top.mcfpp.analysis.lambda.LambdaBinding.Result.Rejected -> throw Failure(result.diagnostic.render())
        }
    }

    /** Binds a controlled callback without creating a persistent function-value environment. */
    internal fun contextPlan(receiver: MCFPPType, call: ContextCallbacks.Call,
                             receiverFacts: top.mcfpp.lib.EntityFacts? = null): ContextCallbacks.Plan {
        val callback = ContextCallbacks.target(receiver, call, ambientExecutorFacts != null, ambientContextType)
        val executorFacts = when (ContextCallbacks.operation(call)) {
            ContextCallbacks.Operation.ENTITY, ContextCallbacks.Operation.ENTITY_AT -> receiverFacts ?: top.mcfpp.lib.EntityFacts.SINGLE
            ContextCallbacks.Operation.RELATION -> top.mcfpp.lib.EntityFacts.SINGLE
            ContextCallbacks.Operation.SUMMON -> top.mcfpp.lib.EntityFacts.SINGLE
            else -> ambientExecutorFacts
        }
        call.operands().forEach { expression(it) }
        val syntax = call.lambda()
        val explicit = syntax.lambdaParameterList()?.lambdaParameter().orEmpty()
        if (explicit.isNotEmpty() && explicit.size != 1)
            throw Failure("Context '${call.name}' callback expects one parameter, found ${explicit.size}")
        val parameters = if (explicit.isEmpty()) mapOf("it" to Bound(callback.parameters.single(), entityFacts = executorFacts)) else {
            val parameter = explicit.single()
            val declared = parameter.type()?.let { ContextTypes.named(it.text) ?: type(it) }
            if (declared != null && !TypeRelations.isSubtype(callback.parameters.single(), declared))
                throw Failure("Context '${call.name}' passes '${callback.parameters.single()}', incompatible with callback parameter '$declared'")
            mapOf(parameter.Identifier().text to Bound(declared ?: callback.parameters.single(), entityFacts = executorFacts))
        }
        val nested = arrayListOf<ContextCallbacks.Plan>()
        val lexical = withContextValues(parameters, nested, executorFacts,
            callbackDeclarations(syntax, parameters.values.single()), callback.parameters.single())
        val body = lexical.bindContextBody(syntax)
        val environment = ContextCallbacks.environment(body.lambdaEnvironment(), nested)
        val binding = when (val result = top.mcfpp.analysis.lambda.LambdaBinding.bind(syntax, callback, environment)) {
            is top.mcfpp.analysis.lambda.LambdaBinding.Result.Bound -> result.value
            is top.mcfpp.analysis.lambda.LambdaBinding.Result.Rejected -> throw Failure(result.diagnostic.render())
        }
        return ContextCallbacks.Plan(call, receiver, ContextCallbacks.operation(call), ContextCallbacks.relation(call),
            callback, binding, nested).also { contextPlans += it }
    }

    private fun withContextValues(extra: Map<String, Bound>, plans: MutableList<ContextCallbacks.Plan> = contextPlans,
                                  ambient: top.mcfpp.lib.EntityFacts? = ambientExecutorFacts,
                                  declarations: Map<DeclarationId, Bound> = emptyMap(),
                                  ambientType: MCFPPType = ambientContextType) =
        DeclarationBinding(declarationFunction, fields, values + extra, typeBindings, declarationScope,
            captureValues, literalDefaultsOnly, preparation, preparedCalls, plans, ambient, contextDeclarations + declarations, ambientType)

    /** Callback preparation owns facts for these exact parameter declarations before runtime adapters exist. */
    private fun callbackDeclarations(syntax: Parser.InlineLambdaContext, parameter: Bound): Map<DeclarationId, Bound> =
        (StructuredBodyContexts.origin(syntax)?.node as? Expression.Lambda)?.parameters.orEmpty().associateWith { parameter }

    /** Source order and block scopes matter for local aliases and shadowed context names. */
    private fun bindContextBody(syntax: Parser.InlineLambdaContext): DeclarationBinding {
        fun sequence(nodes: List<ParseTree>, initial: DeclarationBinding): DeclarationBinding {
            var lexical = initial
            fun check(node: ParseTree, active: DeclarationBinding) {
                when (node) {
                    is Parser.ExpressionContext -> active.bind(node)
                    is Parser.ConditionExpressionContext -> active.bind(node)
                    is Parser.VarWithSelectorContext -> active.bind(node)
                    is Parser.InlineLambdaContext -> Unit
                    is Parser.CurlBlockContext -> sequence(node.statement(), active)
                    is Parser.BlockContext -> node.curlBlock()?.let { sequence(it.statement(), active) }
                        ?: node.statement()?.let { sequence(listOf(it), active) }
                    else -> for (index in 0 until node.childCount) check(node.getChild(index), active)
                }
            }
            for (node in nodes) {
                val commandBlock = (node as? Parser.StatementContext)?.commandBlock()
                if (commandBlock != null) {
                    lexical = sequence(commandBlock.statement(), lexical)
                    continue
                }
                val declaration = (node as? Parser.StatementContext)?.fieldDeclaration()
                if (declaration != null) {
                    val declared = declaration.type()?.let { ContextTypes.named(it.text) ?: lexical.type(it) }
                    val initializer = declaration.expression()?.let { lexical.bind(it, declared) }
                    if (declared != null && initializer != null &&
                        TypeRelations.resolveImplicitConversion(initializer.type, declared) == null)
                        throw Failure("Context callback local '${declaration.Identifier().text}' cannot assign '${initializer.type}' to '$declared'")
                    val bound = initializer?.let { if (declared != null) lexical.constrain(it, declared) else it }
                        ?: declared?.let { Bound(it) }
                        ?: throw Failure("Context callback local '${declaration.Identifier().text}' requires a type or initializer")
                    val fact = bound.copy(entityAddress = false)
                    val identities = StructuredBodyContexts.declaration(declaration)?.let { mapOf(it to fact) }.orEmpty()
                    lexical = lexical.withContextValues(mapOf(declaration.Identifier().text to fact), declarations = identities)
                } else check(node, lexical)
            }
            return lexical
        }
        val body = syntax.lambdaBody()
        return sequence(body.statement() + listOfNotNull(body.lambdaTailExpression()?.expression()), this)
    }

    private fun bindSelector(receiver: Bound, selector: Parser.SelectorContext): Bound {
        if (selector.memberKeyword()?.text == "text" && selector.arguments() == null)
            return Bound(top.mcfpp.core.lang.entity.DisplayProperties.memberType(receiver.type, "text")
                ?: throw Failure("Unknown display property 'text'"), dependencies = receiver.dependencies)
        if (selector.memberKeyword()?.text == "set" && receiver.type is MCFPPReferenceType) {
            val reference = receiver.type as? MCFPPReferenceType ?: throw Failure("set requires ScoreRef or NBTRef<T>")
            val arguments = selector.arguments() ?: throw Failure("set expects one value argument")
            if (arguments.readOnlyArgs() != null) throw Failure("set does not accept readonly arguments")
            val normal = arguments.normalArgs()
            val values: List<ParseTree> = normal.callArgumentList()?.callArgument()?.map {
                if (it.REF() != null) throw Failure(top.mcfpp.antlr.LegacyReferenceSyntax.MESSAGE)
                it.inlineLambda() ?: it.expression()
            }?.filterNotNull() ?: normal.expressionList()?.expression().orEmpty()
            if (values.size != 1) throw Failure("set expects one value argument")
            val input = bind(values.single(), reference.valueType)
            if (TypeRelations.resolveImplicitConversion(input.type, reference.valueType) == null)
                throw Failure("Cannot assign '${input.type}' to '${reference.valueType}' reference target")
            return Bound(MCFPPPrivateType.Void, dependencies = receiver.dependencies + input.dependencies)
        }
        top.mcfpp.core.lang.entity.EntityReadCallbacks.selector(selector)?.let {
            entityReadPlan(receiver.type, it)
            return Bound(MCFPPBaseType.Bool)
        }
        ContextCallbacks.selector(selector, receiver = receiver.type)?.let { return Bound(contextPlan(receiver.type, it, receiver.entityFacts).result) }
        val collection = top.mcfpp.analysis.lambda.LambdaCollections.selector(selector)
        if (collection != null && top.mcfpp.analysis.lambda.LambdaCollections.supports(receiver.type, collection.name))
            return Bound(collectionPlan(receiver.type, collection).result)
        top.mcfpp.antlr.KeywordMemberCalls.call(selector)?.let { return call(it, receiver) }
        return member(receiver, selector.`var`() ?: throw Failure("Cannot bind receiver member '${selector.text}'"))
    }

    private fun member(receiver: Bound, node: Parser.VarContext): Bound {
        val instanceEnum = receiver.type as? MCFPPEnumType
        val instanceMember = node.varWithSuffix()
        if (instanceEnum != null && instanceMember != null) {
            val payload = (receiver.constant as? CompilerValue.Typed)?.payload ?: receiver.constant
            val record = payload as? CompilerValue.Record
            val ordinal = record?.fields?.get("ordinal") as? CompilerValue.Integral
            val name = instanceMember.Identifier().text
            val intrinsic = when (name) {
                "value" -> {
                    if (ordinal == null || ordinal.value !in Int.MIN_VALUE.toLong()..Int.MAX_VALUE.toLong())
                        throw Failure("Enum ordinal has no runtime producer in the data-only enum encoding")
                    Bound(MCFPPBaseType.Int, CompilerValue.Typed(MCFPPBaseType.Int.typeId, ordinal), receiver.dependencies)
                }
                "identifier" -> {
                    val member = ordinal?.value?.takeIf { it in Int.MIN_VALUE.toLong()..Int.MAX_VALUE.toLong() }
                        ?.let { instanceEnum.enum.getMember(it.toInt()) }
                        ?: throw Failure("Enum identifier has no runtime producer")
                    Bound(MCFPPBaseType.String, CompilerValue.Typed(MCFPPBaseType.String.typeId,
                        CompilerValue.Text(member.identifier)), receiver.dependencies)
                }
                "data" -> Bound(MCFPPNBTType.NBT, record?.fields?.get("data")?.let {
                    CompilerValue.Typed(MCFPPNBTType.NBT.typeId, (it as? CompilerValue.Typed)?.payload ?: it)
                }, receiver.dependencies)
                else -> null
            }
            if (intrinsic != null) return suffix(intrinsic.copy(
                descriptors = receiver.descriptors + (intrinsic.type.typeId to intrinsic.type)), instanceMember)
        }
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
        (meta as? MCFPPDataTemplateType)?.let { outer ->
            node.varWithSuffix()?.Identifier()?.text?.let { name -> NestedTemplates.child(outer.template, name) }?.let { child ->
                val type = child.getType()
                return Bound(MCFPPConcreteType.Type, CompilerValue.Typed(MCFPPConcreteType.Type.typeId,
                    CompilerValue.TypeValue(type.typeId)), descriptors = mapOf(type.typeId to type))
            }
        }
        val owner = (meta as? MCFPPDataTemplateType)?.template?.let { if (it is top.mcfpp.model.compound.ObjectCompoundData) it else it.companionObject }
            ?: (receiver.type as? MCFPPDataTemplateType)?.template
        if (node.functionCall() != null) return call(node.functionCall(), receiver)
        if (node.bucketExpression() != null) throw Failure("An expression cannot name a member")
        val name = node.varWithSuffix().Identifier().text
        val structuralField = objectField(receiver.type, name)
        if (meta == null && structuralField != null &&
            (receiver.type is MCFPPObjectType || receiver.type !is MCFPPDataTemplateType)) {
            val payload = (receiver.constant as? CompilerValue.Typed)?.payload as? CompilerValue.Record
            return suffix(Bound(structuralField, payload?.fields?.get(name), receiver.dependencies,
                descriptors = receiver.descriptors), node.varWithSuffix())
        }
        if (receiver.type is MCFPPEntityRefType) {
            val field = receiver.type.templateType.template.scope.getVar(name)
            if (field?.isEntityMember == true)
                return suffix(Bound(field.type, dependencies = receiver.dependencies), node.varWithSuffix())
            if (field != null) throw Failure("Template field '$name' is not available on an entity reference")
        }
        top.mcfpp.core.lang.entity.DisplayProperties.memberType(receiver.type, name)?.let {
            return suffix(Bound(it, dependencies = receiver.dependencies), node.varWithSuffix())
        }
        if (receiver.type is MCFPPEntityType) {
            if (name == "dat") return suffix(Bound(top.mcfpp.core.lang.entity.EntityMembers.dataForFacts(receiver.entityFacts).getType(),
                dependencies = receiver.dependencies, entityAddress = true), node.varWithSuffix())
            if (name == "score") return suffix(Bound(MCFPPDictType(MCFPPScoreRefType), dependencies = receiver.dependencies), node.varWithSuffix())
        }
        if (receiver.entityAddress) {
            val stored = (receiver.type as? MCFPPNBTRefType)?.valueType ?: receiver.type
            val schema = stored as? MCFPPDataTemplateType
                ?: throw Failure("NBT address '$stored' has no field '$name'")
            schema.tryResolve()
            val field = schema.template.scope.getVar(name) ?: throw Failure("Unknown entity NBT field '$name'")
            if (function.accessTo(schema.template) < field.accessModifier) throw Failure("Cannot access entity NBT field '$name'")
            return suffix(Bound(MCFPPNBTRefType(field.type), dependencies = receiver.dependencies, entityAddress = true), node.varWithSuffix())
        }
        if (receiver.type is MCFPPExecutionContextType) {
            val member = ContextTypes.memberType(receiver.type, name)
                ?: throw Failure("Member '$name' is not defined on execution context '${receiver.type}'")
            return suffix(Bound(member, dependencies = receiver.dependencies,
                entityFacts = receiver.entityFacts.takeIf { name == "executor" }), node.varWithSuffix())
        }
        if (receiver.type is MCFPPMapType) {
            val memberType = when (name) {
                "keys" -> MCFPPListType(MCFPPBaseType.String)
                "keyValueSet" -> MCFPPDictType(receiver.type.generic.single())
                else -> null
            }
            if (memberType != null)
                return suffix(Bound(memberType, dependencies = receiver.dependencies), node.varWithSuffix())
        }
        val property = owner?.scope?.getProperty(name)
        if (property != null && property.accessor == null)
            throw Failure("Member '$name' is not readable on '${receiver.type.typeName}'")
        if (owner === template && (meta == null || owner is top.mcfpp.model.compound.ObjectCompoundData)) {
            values[name]?.let { return suffix(it, node.varWithSuffix()) }
            fields[name]?.let { return suffix(Bound(it, declaredTypeText = fieldDeclarationText(name)), node.varWithSuffix()) }
        }
        if (meta is MCFPPEnumType) {
            val member = meta.enum.members[name] ?: throw Failure("Unknown enum member '$name'")
            return Bound(meta, CompilerValue.Typed(meta.typeId, CompilerValue.Record(mapOf(
                "ordinal" to CompilerValue.Integral(member.value.toLong()), "data" to CompilerValue.Nbt(top.mcfpp.backend.NbtEncoding.snbt(member.data))))))
        }
        val field = (owner?.scope ?: meta?.objectData?.scope ?: receiver.type.instanceData.scope).getVar(name)
        if (field != null) {
            if (field.isEntityMember && receiver.type is MCFPPDataTemplateType)
                throw Failure("Entity field '$name' requires an EntityRef receiver")
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
            val constant = if (property?.accessor is top.mcfpp.model.property.FunctionAccessor || property?.accessor is top.mcfpp.model.property.NativeAccessor) null
                else payload?.fields?.get(name) ?: if (field.isStatic) ConstantBinding.semanticSnapshot(field) else null
            return suffix(Bound(field.type, constant, receiver.dependencies, declared, receiver.descriptors), node.varWithSuffix())
        }
        throw Failure("Member '$name' is not defined on '${receiver.type}'")
    }

    /** Union reads require a field in every branch and preserve every possible read type. */
    private fun objectField(type: MCFPPType, name: String): MCFPPType? {
        if (type is MCFPPUnionType) {
            val fields = type.types.map { objectField(it, name) ?: return null }
            return TypeCompositions.union(*fields.toTypedArray())
        }
        val field = TypeCompositions.objectShape(type)?.fieldsByName?.get(name) ?: return null
        if (!field.readable) throw Failure("Member '$name' is not readable on '${type.typeName}'")
        return field.type
    }

    private fun call(context: Parser.FunctionCallContext, receiver: Bound? = null,
                     argumentTypes: List<MCFPPType>? = null): Bound {
        if (top.mcfpp.antlr.LegacyReferenceSyntax.argument(context.arguments()) != null)
            throw Failure(top.mcfpp.antlr.LegacyReferenceSyntax.MESSAGE)
        if (preparation != null) return preparedCall(context, receiver)
        if (receiver == null) top.mcfpp.mni.expand.MacroCallPipeline.declaredResultType(context, function, values, contextDeclarations)?.let {
            return Bound(it)
        }
        val (namespace, name) = context.namespaceID().text.splitNamespaceID()
        if (receiver != null && top.mcfpp.core.lang.context.ContextPositions.recognizes(receiver.type, name)) {
            val arguments = context.arguments().normalArgs().expressionList()?.expression().orEmpty()
            if (arguments.size != 3 || context.arguments().readOnlyArgs() != null)
                throw Failure("Context position offsets require three numeric arguments")
            arguments.forEach { bind(it) }
            return Bound(ContextTypes.Position, dependencies = receiver.dependencies)
        }
        if (receiver == null) ContextCallbacks.global(context)?.let { return Bound(contextPlan(MCFPPPrivateType.Void, it).result) }
        if (receiver == null) top.mcfpp.analysis.lambda.LambdaCollections.global(context)?.let {
            return Bound(collectionPlan(MCFPPPrivateType.Void, it).result)
        }
        val receiverMeta = ((receiver?.constant as? CompilerValue.Typed)?.payload as? CompilerValue.TypeValue)?.id?.let {
            receiver.descriptors[it] ?: MCFPPType.resolveTypeId(it)
        }
        if (name == "new" && receiver != null && receiverMeta == null)
            throw Failure("Call a factory through its type namespace")
        val explicitFactory = (receiverMeta as? MCFPPDataTemplateType)?.template?.takeIf {
            name == "new" && it !is TypeDataTemplate
        }
        val boundCandidates = if (explicitFactory != null) null
            else if (receiver == null) StructuredBindings.functions(context.namespaceID(), declarationFunction)
            else if (receiver.superOwner == null) StructuredBindings.memberFunctions(context.namespaceID(), receiverMeta ?: receiver.type, function) else null
        val boundName = if (receiver == null) StructuredBindings.name(context.namespaceID(), declarationFunction) else null
        if (boundName is StructuredNameBinding.Macros)
            throw Failure("A bound macro expression requires the formal macro preparation pipeline")
        val callable = if (receiver == null && boundName != null && boundName !is StructuredNameBinding.Functions &&
            boundName !is StructuredNameBinding.Type) StructuredBindings.variable(context.namespaceID(), function)?.type as? MCFPPFunctionType
            else if (receiver == null && namespace == null && boundCandidates == null) lookupVariable(name)?.type as? MCFPPFunctionType else null
        val normal = context.arguments().normalArgs()
        val nodes: List<ParseTree> = normal.callArgumentList()?.callArgument()?.filter { it.ASSIGNMENT() == null }?.map { argument ->
            argument.inlineLambda() ?: argument.expression() ?: throw Failure("REF arguments require a reference-capable call target")
        } ?: normal.expressionList()?.expression().orEmpty()
        val earlyOwner = if (receiver != null) (receiverMeta as? MCFPPDataTemplateType)?.template?.let {
            if (it is top.mcfpp.model.compound.ObjectCompoundData) it else it.companionObject
        } ?: (receiver.type as? MCFPPDataTemplateType)?.template else null
        val earlyDeclaration = explicitFactory ?: if (receiver == null) ((boundName as? StructuredNameBinding.Type)?.type as? MCFPPDataTemplateType)?.template
            ?: if (boundCandidates == null) GlobalScope.getTemplate(namespace, name) else null
            else (receiverMeta as? MCFPPDataTemplateType)?.template?.let { NestedTemplates.child(it, name) }
        val readonlyCandidates = boundCandidates ?: if (receiver == null) functionCandidates(context.namespaceID().text)
            else earlyOwner?.scope?.getFunctionCandidates(name, declarationFunction).orEmpty()
        val readonlyArguments = context.arguments().readOnlyArgs()?.expressionList()?.expression().orEmpty().mapIndexed { index, syntax ->
            val target = (earlyDeclaration as? top.mcfpp.model.compound.GenericDataTemplate)?.readOnlyParams?.getOrNull(index)?.type
                ?: readonlyFormal(readonlyCandidates, index)
            readonly(syntax, target)
        }
        val factoryCandidates: List<Function> = if (earlyDeclaration != null && readonlyArguments.all { it.constant != null }) {
            try {
                val values = readonlyArguments.map(::closedValue)
                val actual = if (earlyDeclaration is top.mcfpp.model.compound.GenericDataTemplate) earlyDeclaration.compile(values)
                else if (values.isEmpty()) earlyDeclaration else null
                when {
                    actual is TypeDataTemplate -> actual.constructors
                    actual != null && actual.isEntityTemplate && explicitFactory == null -> actual.constructors
                    else -> actual?.factories.orEmpty()
                }
            } catch (_: Failure) { emptyList() }
        } else emptyList()
        val candidateFunctions: List<Function> = when {
            callable != null -> emptyList()
            earlyDeclaration != null -> factoryCandidates
            boundCandidates != null -> boundCandidates
            receiver != null -> run {
                val receiverData = (receiver.type as? MCFPPEntityType)?.let { top.mcfpp.core.lang.entity.SelectorVar.declarationData(it) }
                (earlyOwner?.scope ?: receiverMeta?.objectData?.scope ?: receiverData?.scope ?: receiver.type.instanceData.scope)
                    .getFunctionCandidates(name, declarationFunction).filter {
                        if (receiver.type is MCFPPEntityRefType) it.isEntityMember
                        else if (receiver.type is MCFPPDataTemplateType) !it.isEntityMember else true
                    }
            }
            else -> functionCandidates(context.namespaceID().text).ifEmpty { factoryCandidates }
        }
        val earlyReceiverTypes = (receiver?.type as? MCFPPTypeWithGeneric)?.generic?.firstOrNull()?.let { mapOf("E" to it) }.orEmpty()
        val signatures = candidateFunctions.mapNotNull { candidate ->
            val readonly = (candidate as? Generic<*>)?.readOnlyParams ?: (candidate as? NativeFunction)?.readOnlyParams.orEmpty()
            val signature = SpecializationPolicy.resolveDeclaredSignature(candidate, readonly, readonlyArguments, earlyReceiverTypes) ?: return@mapNotNull null
            val count = nodes.size
            if (count > signature.normalTypes.size || candidate.normalParams.drop(count).any { !it.hasDefault }) null
                else signature.normalTypes
        }
        val arguments = arrayListOf<Bound>()
        for ((index, node) in nodes.withIndex()) {
            val parameterIndex = arguments.size
            val targets = if (callable != null) listOfNotNull(callable.parameters.getOrNull(parameterIndex))
                else signatures.filter { types -> arguments.indices.all {
                    TypeRelations.resolveImplicitConversion(arguments[it].type, types[it]) != null
                } }.mapNotNull { it.getOrNull(parameterIndex) }.distinctBy { it.typeId }
            if (targets.size > 1 && (node is Parser.InlineLambdaContext ||
                node is Parser.ExpressionContext && node.primary()?.inlineLambda() != null) &&
                (TemplateLiteralSupport.unwrapAmbiguousObject(node as? ParserRuleContext) == null ||
                    targets.any { it is MCFPPFunctionType }))
                throw Failure("Cannot infer lambda target: overloaded targets are ambiguous")
            val expectedTarget = TemplateLiteralSupport.selectExpectedLiteralType(node as? ParserRuleContext, targets) { candidate ->
                val literal = node as? ParserRuleContext ?: return@selectExpectedLiteralType false
                try { compoundLiteral(literal, candidate); true } catch (_: Failure) { false }
            }
            arguments += argumentTypes?.getOrNull(index)?.let { Bound(it) } ?: bind(node, expectedTarget)
        }
        arguments.firstNotNullOfOrNull { TypeUsage.ordinaryDiagnostic(it.type, it.constant) }?.let { throw Failure(it) }
        if (callable != null) {
            if (readonlyArguments.isNotEmpty() || arguments.size != callable.parameters.size || arguments.indices.any {
                    TypeRelations.resolveImplicitConversion(arguments[it].type, callable.parameters[it]) == null })
                throw Failure("Arguments do not match function value '$name'")
            return Bound(callable.result)
        }
        val meta = receiverMeta
        if (name == "summon" && receiver?.type is MCFPPDataTemplateType && receiver.type.template.isEntityTemplate) {
            if (readonlyArguments.isEmpty() && arguments.isEmpty()) {
                val inherited = receiver.type.template.scope.getFunctionCandidates(name, function).firstOrNull {
                    it.normalParams.size == 1 && it.normalParams.single().type == MCFPPNBTType.NBT
                }
                return Bound(if (inherited == null || inherited is top.mcfpp.core.lang.entity.EntityTemplates.SummonFunction)
                    MCFPPEntityRefType(receiver.type) else inherited.returnType)
            }
            if (readonlyArguments.isEmpty() && arguments.size == 1 && arguments.single().type in
                listOf(MCFPPBaseType.Pos3, top.mcfpp.core.lang.context.ContextTypes.Position))
                return Bound(MCFPPEntityRefType(receiver.type))
        }
        val owner = (meta as? MCFPPDataTemplateType)?.template?.let { if (it is top.mcfpp.model.compound.ObjectCompoundData) it else it.companionObject }
            ?: (receiver?.type as? MCFPPDataTemplateType)?.template
        val declaration = ((boundName as? StructuredNameBinding.Type)?.type as? MCFPPDataTemplateType)?.template
            ?: if (receiver == null && boundCandidates == null) GlobalScope.getTemplate(namespace, name) else null
            ?: earlyDeclaration
        if (declaration != null) {
            val values = readonlyArguments.map(::closedValue)
            val actual = if (declaration is top.mcfpp.model.compound.GenericDataTemplate) declaration.compile(values)
                ?: throw Failure("Cannot bind readonly arguments for '$name'") else {
                if (values.isNotEmpty()) throw Failure("Ordinary template '$name' has no readonly parameters")
                declaration
            }
            val delegated = actual is TypeDataTemplate || actual.isEntityTemplate && explicitFactory == null
            val constructorKind = if (actual is TypeDataTemplate) "delegated conversion" else "constructor"
            val selected = if (delegated) ParameterMatcher.selectTypes(actual.constructors, "", arguments.map { it.type })
                else ParameterMatcher.selectTypes(actual.factories, "new", arguments.map { it.type })
            val factory = when (selected) {
                is ParameterMatcher.TypeSelection.Selected -> selected.function
                is ParameterMatcher.TypeSelection.Ambiguous -> throw Failure(ParameterMatcher.ambiguity(
                    if (delegated) actual.identifier else "${actual.identifier}.new", selected.functions))
                ParameterMatcher.TypeSelection.Missing -> throw Failure(if (delegated)
                    "Cannot bind $constructorKind '${actual.identifier}' to its declared argument types"
                    else "Cannot bind factory '${actual.identifier}.new' to its declared argument types")
            }
            if (function.accessTo(actual) < factory.accessModifier)
                throw Failure(if (delegated) "Cannot access $constructorKind '${actual.identifier}'"
                    else "Cannot access factory '${actual.identifier}.new'")
            val typeText = if (explicitFactory != null) actual.getType().typeName
                else if (readonlyArguments.isEmpty()) context.namespaceID().text else
                context.namespaceID().text + "<" + context.arguments().readOnlyArgs().expressionList().expression().joinToString(",") { it.text } + ">"
            val resultType = actual.getType()
            val emptyImplicit = factory is TemplateFactory && factory.isImplicit &&
                TypeCompositions.objectShape(resultType)?.fields?.isEmpty() == true &&
                actual.preInit.none { (name, _) -> actual.scope.getVar(name)?.declaredParentTemplate === actual }
            val constant = if (emptyImplicit) CompilerValue.Typed(resultType.typeId, CompilerValue.Record(emptyMap())) else null
            return Bound(resultType, constant, dependencies = readonlyArguments.flatMap { it.dependencies }.toSet() +
                arguments.flatMap { it.dependencies }, declaredTypeText = typeText,
                descriptors = if (emptyImplicit) mapOf(resultType.typeId to resultType) else emptyMap())
        }
        val receiverData = (receiver?.type as? MCFPPEntityType)?.let {
            top.mcfpp.core.lang.entity.SelectorVar.declarationData(it)
        }
        val receiverTypes = (receiver?.type as? MCFPPTypeWithGeneric)?.generic?.firstOrNull()
            ?.let { mapOf("E" to it) }.orEmpty()
        val superTarget = receiver?.superOwner?.let { anchor ->
            superBinding { function.selectSuper(anchor, name, readonlyArguments, arguments.map { it.type }) }
        }
        val candidates = (if (receiver == null) boundCandidates ?: GlobalScope.getFunctionCandidates(namespace, name,
            function.restoreDeclarationEnvironment()?.field ?: top.mcfpp.io.MCFPPFile.currFile?.field)
            else boundCandidates ?: (owner?.scope ?: meta?.objectData?.scope ?: receiverData?.scope ?: receiver.type.instanceData.scope)
                .getFunctionCandidates(name, function)).filter {
                    if (receiver?.type is MCFPPEntityRefType) it.isEntityMember
                    else if (receiver?.type is MCFPPDataTemplateType) !it.isEntityMember else true
                }
        val selected = superTarget?.let { ParameterMatcher.TypeSelection.Selected(it) }
            ?: ParameterMatcher.selectDeclaredTypes(candidates, boundCandidates?.firstOrNull()?.identifier ?: name,
                readonlyArguments, arguments.map { it.type }, receiverTypes, receiver?.type)
        val target = when (selected) {
            is ParameterMatcher.TypeSelection.Selected -> selected.function
            is ParameterMatcher.TypeSelection.Ambiguous -> throw Failure(ParameterMatcher.ambiguity(name, selected.functions))
            ParameterMatcher.TypeSelection.Missing -> throw Failure("Cannot bind function '$name' to its declared argument types")
        }
        val declarationOwner = target.owner as? DataTemplate
        if (declarationOwner != null && function.accessTo(declarationOwner) < target.accessModifier)
            throw Failure("Cannot access declaration method '$name'")
        val params = (target as? Generic<*>)?.readOnlyParams ?: (target as? NativeFunction)?.readOnlyParams.orEmpty()
        val result = if (target is top.mcfpp.core.lang.entity.EntityTemplates.SummonFunction && receiver != null)
            MCFPPEntityRefType(receiver.type)
        else SpecializationPolicy.resolveDeclaredSignature(target, params, readonlyArguments, receiverTypes)?.returnType
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
        val readonly = context.arguments().readOnlyArgs()?.expressionList()?.expression().orEmpty().mapIndexed { index, syntax ->
            PreparedExpressionFact.fromBound(this.readonly(syntax, readonlyFormal(candidates, index)), bindingSpan(syntax), this)
        }
        val normalSyntax = context.arguments().normalArgs()
        val arguments = normalSyntax.callArgumentList()?.callArgument()?.map { argument ->
            if (argument.REF() != null) throw Failure(top.mcfpp.antlr.LegacyReferenceSyntax.MESSAGE)
            if (argument.inlineLambda() != null)
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
            val leftType = (result.type as? MCFPPLiteralType)?.primitive ?: result.type
            val rightType = (next.type as? MCFPPLiteralType)?.primitive ?: next.type
            val type = when {
                operation == "|" && result.type == MCFPPConcreteType.Type && next.type == MCFPPConcreteType.Type -> MCFPPConcreteType.Type
                operation in setOf("==", "!=", "<", ">", "<=", ">=") &&
                    leftType in setOf(MCFPPBaseType.Int, MCFPPBaseType.Float) &&
                    rightType in setOf(MCFPPBaseType.Int, MCFPPBaseType.Float) -> MCFPPBaseType.Bool
                operation in setOf("==", "!=", "&&", "||") &&
                    leftType == MCFPPBaseType.Bool && rightType == MCFPPBaseType.Bool -> MCFPPBaseType.Bool
                leftType == MCFPPBaseType.Int && rightType == MCFPPBaseType.Int -> MCFPPBaseType.Int
                leftType in setOf(MCFPPBaseType.Int, MCFPPBaseType.Float) && rightType in setOf(MCFPPBaseType.Int, MCFPPBaseType.Float) -> MCFPPBaseType.Float
                operation == "+" && leftType == MCFPPBaseType.String && rightType == MCFPPBaseType.String -> MCFPPBaseType.String
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
                    val union = if (preparation == null && top.mcfpp.Project.shouldDeferTypeCompositions)
                        TypeCompositions.deferUnion(listOf(a, b)) else TypeCompositions.union(a, b)
                    descriptors[union.typeId] = union
                    CompilerValue.TypeValue(union.typeId)
                } else if (operation == "+" && leftType == MCFPPBaseType.String && rightType == MCFPPBaseType.String &&
                    left is CompilerValue.Text && right is CompilerValue.Text) CompilerValue.Text(left.value + right.value)
                else if (leftType == MCFPPBaseType.Int && rightType == MCFPPBaseType.Int ||
                    leftType == MCFPPBaseType.Bool && rightType == MCFPPBaseType.Bool ||
                    leftType in setOf(MCFPPBaseType.Int, MCFPPBaseType.Float) &&
                    rightType in setOf(MCFPPBaseType.Int, MCFPPBaseType.Float) &&
                    (leftType == MCFPPBaseType.Float || rightType == MCFPPBaseType.Float)) {
                    val floating = leftType == MCFPPBaseType.Float || rightType == MCFPPBaseType.Float
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
        StructuredBindings.type(context, declarationFunction, if (preparation == null) null else { declaration ->
            val value = preparedLocal(declaration, context)
            val payload = (value.constant as? CompilerValue.Typed)?.payload ?: value.constant
            (payload as? CompilerValue.TypeValue)?.let { value.descriptors[it.id] }
        })?.let { return preparation?.exportType(it, context, bindingSpan(context)) ?: it }
        // The shared syntax context owns template admission and its session's canonical queue.
        val pureContext: DeclarationPreparationContext? = preparation
        if (pureContext != null) return pureContext.exportType(TypeSyntaxResolver.resolve(context, pureContext.syntax())
            ?: unsupported(context, "Unknown declaration type '${context.text}'"), context)
        val syntax = object : LegacyTypeSyntaxContext(lookupScope, function) {
            override fun known(body: Parser.TypeBodyContext): MCFPPType? =
                typeBindings[body.text] ?: super.known(body)

            override fun invalid(site: ParserRuleContext, message: String): MCFPPType? = throw Failure(message)

            override fun specialize(head: top.mcfpp.model.compound.GenericDataTemplate,
                                    arguments: List<Parser.ExpressionContext>, site: Parser.TypeBodyContext): MCFPPType? {
                // Bare legacy generic names retain their existing empty-argument preparation.
                if (site.readOnlyArgs() == null) return super.specialize(head, arguments, site)
                return head.compile(arguments.mapIndexed { index, argument ->
                    readonly(argument, head.readOnlyParams.getOrNull(index)?.type)
                }.map(::closedValue))?.getType()
                    ?: throw Failure("Cannot bind readonly type arguments for '${head.identifier}'")
            }
        }
        return TypeSyntaxResolver.resolve(context, syntax)
            ?: throw Failure("Invalid declaration type '${context.text}'")
    }

    internal fun declaredType(type: MCFPPType): MCFPPType {
        val readonlyFacts = values.mapNotNull { (name, value) -> value.constant?.let {
            name to ClosedReadonlyBinding(value.type, it, value.descriptors)
        } }.toMap()
        declarationFunction?.let { owner ->
            SpecializationPolicy.structuredType(owner, type, readonlyFacts)?.let { return it }
        }
        return when (type) {
        is UnresolvedType -> {
            val handle = function.bodyHandle
            if (handle != null) {
                val bindings = handle.repository.body(handle).bindings as? StructuredSessionBindings
                bindings?.dependentType(type, function)
                    ?: throw Failure("LIB_TYPE_UNSUPPORTED: Imported dependent type has no structural session binding")
            } else type(Parser(CommonTokenStream(mcfppLexer(CharStreams.fromString(type.originalTypeString)))).type())
        }
        else -> if (preparation == null) SpecializationPolicy.bind(type, typeBindings) else type
        }
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
                    MCFPPObjectType::class.java -> MCFPPObjectType((value as MCFPPObjectType).fields.map { field ->
                        ObjectFieldContract(field.name, visit(field.type, depth + 1), field.optional, field.kind, field.origins,
                            field.readable, field.writable)
                    })
                    MCFPPLiteralType::class.java -> (value as MCFPPLiteralType).let {
                        MCFPPLiteralType(visit(it.primitive, depth + 1), it.value)
                    }
                    MCFPPIntersectionType::class.java -> TypeCompositions.intersection(*(value as MCFPPIntersectionType)
                        .components.map { visit(it, depth + 1) }.toTypedArray())
                    MCFPPNeverType::class.java -> MCFPPNeverType
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

    private fun literal(context: Parser.ValueContext, expected: MCFPPType? = null): Bound {
        context.nbtValue()?.nbtCompound()?.let(TemplateLiteralSupport::compoundEntries)
        StructuredBindings.constant(context, declarationFunction)?.let { return it }
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
            return Bound(type, expression.snapshot(type.typeId), entityFacts = selector.facts())
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
        return nbtLiteral(context.nbtValue(), expected)
    }

    internal fun nbtLiteral(value: Parser.NbtValueContext, expected: MCFPPType? = null): Bound {
        value.nbtCompound()?.let(TemplateLiteralSupport::compoundEntries)
        StructuredBindings.constant(value, declarationFunction)?.let { return it }
        integralLiteral(value)?.let { return it }
        if (value.nbtCompound() != null) {
            val target = when (expected) {
                is MCFPPUnionType -> TemplateLiteralSupport.resolveCompoundUnionTarget(expected) { candidate ->
                    try { nbtLiteral(value, candidate); true } catch (_: Failure) { false }
                }
                else -> expected
            }
            if (target != null && TemplateLiteralSupport.isTemplateLiteralType(target)) {
                if (preparation != null) unsupported(value, "Template literal binding requires source preparation")
                return bindTemplateCompound(value.nbtCompound(), target as MCFPPDataTemplateType)
            }
            val expectedDict = target as? MCFPPDictType
            val expectedShape = target?.let(TypeCompositions::objectShape)
            val members = TemplateLiteralSupport.compoundEntries(value.nbtCompound()).map { (key, entry) ->
                val fieldTarget = expectedDict?.generic?.single() ?: expectedShape?.fieldsByName?.get(key)?.type
                val raw = entry.expression()?.let { bind(it, fieldTarget) } ?: variable(key, entry)
                val constrained = if (expectedDict != null) {
                    val elem = expectedDict.generic.single()
                    if (TypeRelations.resolveImplicitConversion(raw.type, elem) == null)
                        throw Failure("Dictionary value for '$key' expects '${elem.typeName}', found '${raw.type.typeName}'")
                    constrain(raw, elem)
                } else contextualObjectField(raw, fieldTarget)
                key to constrained
            }
            val type = when {
                target == MCFPPNBTType.NBT -> MCFPPNBTType.NBT
                expectedDict != null -> expectedDict
                else -> MCFPPObjectType(members.map { (name, bound) -> ObjectFieldContract(name, bound.type) })
            }
            val payload = if (members.all { it.second.constant != null })
                CompilerValue.Record(members.associate { it.first to it.second.constant!! }) else null
            val result = Bound(type, payload?.let { CompilerValue.Typed(type.typeId, it) },
                members.flatMap { it.second.dependencies }.toSet(),
                descriptors = members.flatMap { it.second.descriptors.entries }.associate { it.key to it.value } +
                    members.associate { it.second.type.typeId to it.second.type } + mapOf(type.typeId to type))
            if (expectedShape != null) {
                val shapeTarget = checkNotNull(target)
                if (!TypeRelations.isSubtype(type, shapeTarget))
                    throw Failure("Object literal '${type.typeName}' does not satisfy '${shapeTarget.typeName}'")
                return constrain(result, shapeTarget)
            }
            return result
        }
        if (value.nbtList() != null) {
            val target = when (expected) {
                is MCFPPUnionType -> TemplateLiteralSupport.resolveListUnionTarget(expected)
                else -> expected
            }
            val expectedList = target?.takeIf { it is MCFPPListType || it is MCFPPImmutableListType }
            val elemTarget = (expectedList as? MCFPPTypeWithGeneric)?.generic?.single()
            val elements = value.nbtList().expression().map { expr ->
                val raw = bind(expr, elemTarget)
                if (elemTarget != null) {
                    if (TypeRelations.resolveImplicitConversion(raw.type, elemTarget) == null)
                        throw Failure("List element expects '${elemTarget.typeName}', found '${raw.type.typeName}'")
                    constrain(raw, elemTarget)
                } else raw
            }
            val type = expectedList ?: MCFPPListType(common(elements))
            val payload = if (elements.all { it.constant != null })
                CompilerValue.Sequence(elements.map { it.constant!! }) else null
            return Bound(type, payload?.let { CompilerValue.Typed(type.typeId, it) },
                elements.flatMap { it.dependencies }.toSet(),
                descriptors = elements.flatMap { it.descriptors.entries }.associate { it.key to it.value } +
                    mapOf(type.typeId to type))
        }
        val elements: List<Bound>? = null
        val members: List<Pair<String, Bound>>? = null
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

    private fun bindTemplateCompound(compound: Parser.NbtCompoundContext, templateType: MCFPPDataTemplateType): Bound {
        templateType.tryResolve()
        val targetTemplate = templateType.template
        val instanceFields = templateType.instanceFields
        val fieldsByName = instanceFields.associateBy { it.identifier }
        val supplied = linkedMapOf<String, Bound>()
        for ((key, entry) in TemplateLiteralSupport.compoundEntries(compound)) {
            val declared = fieldsByName[key]
            val raw = entry.expression()?.let { bind(it, declared?.type) } ?: variable(key, entry)
            TypeUsage.ordinaryDiagnostic(raw.type, raw.constant)?.let { throw Failure(it) }
            supplied[key] = contextualObjectField(raw, declared?.type)
        }
        val shape = MCFPPObjectType(supplied.map { (name, bound) -> ObjectFieldContract(name, bound.type) })
        if (!TypeRelations.isSubtype(shape, templateType)) {
            val contracts = TypeCompositions.objectShape(templateType)?.fields.orEmpty()
            for (field in contracts) {
                val provided = supplied[field.name]
                if (provided == null && !field.optional)
                    throw Failure("Missing required field '${field.name}' for template '${targetTemplate.namespaceID}'")
                if (provided != null && !TypeRelations.isSubtype(provided.type, field.type))
                    throw Failure("Field '${field.name}' of '${targetTemplate.namespaceID}' expects '${field.type.typeName}', found '${provided.type.typeName}'")
            }
            throw Failure("Object literal '${shape.typeName}' does not satisfy '${templateType.typeName}'")
        }
        val recordFields = linkedMapOf<String, CompilerValue>()
        val dependencies = linkedSetOf<String>()
        val descriptors = linkedMapOf<TypeId, MCFPPType>(templateType.typeId to templateType, shape.typeId to shape)
        var allConstant = true
        for ((key, bound) in supplied) {
            dependencies.addAll(bound.dependencies)
            descriptors.putAll(bound.descriptors)
            descriptors[bound.type.typeId] = bound.type
            val constant = bound.constant
            if (constant == null) allConstant = false else recordFields[key] = constant
        }
        val snapshot = if (allConstant) CompilerValue.Typed(templateType.typeId, CompilerValue.Record(recordFields)) else null
        return Bound(templateType, snapshot, dependencies, descriptors = descriptors)
    }

    /** A known entry keeps its singleton contract until a mutable destination widens it. */
    private fun contextualObjectField(bound: Bound, target: MCFPPType?): Bound {
        val precise = objectFieldValue(bound)
        if (target == null || TypeRelations.isSubtype(precise.type, target)) return precise
        return if (TypeRelations.resolveImplicitConversion(bound.type, target) != null)
            objectFieldValue(constrain(bound, target)) else precise
    }

    private fun objectFieldValue(bound: Bound): Bound {
        if (bound.type == MCFPPPrivateType.Void) throw Failure("A void expression cannot initialize an object field")
        if (bound.type == MCFPPPrivateType.Null) throw Failure("Object fields cannot contain null; omit an optional field instead")
        if (bound.type is MCFPPLiteralType || bound.constant == null) return bound
        if (bound.type !in setOf(MCFPPBaseType.Int, MCFPPBaseType.Float, MCFPPBaseType.Bool, MCFPPBaseType.String)) return bound
        val literal = MCFPPLiteralType(bound.type, bound.constant)
        val payload = (bound.constant as? CompilerValue.Typed)?.payload ?: bound.constant
        return bound.copy(type = literal, constant = CompilerValue.Typed(literal.typeId, payload),
            descriptors = bound.descriptors + (literal.typeId to literal) + (bound.type.typeId to bound.type))
    }

    private fun common(values: List<Bound>, empty: MCFPPType = MCFPPPrivateType.Wildcard): MCFPPType {
        val types = values.map { it.type }.distinctBy { it.typeId }
        return when (types.size) { 0 -> empty; 1 -> types.single(); else -> MCFPPUnionType(*types.toTypedArray()) }
    }
}

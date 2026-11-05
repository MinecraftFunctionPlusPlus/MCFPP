package top.mcfpp.mni.expand

import top.mcfpp.mni.api.*
import top.mcfpp.type.*
import java.util.concurrent.atomic.AtomicLong

/** Immutable, declaration-complete inputs consumed by the source binder. No compiler scope is queried here. */
internal class BindingEnvironment(
    val types: SourceTypeCatalog,
    symbols: Collection<SourceSymbol>,
    functions: Collection<SourceFunction>,
    fields: Collection<SourceField> = emptyList(),
    val sourceText: String? = null,
    val scopeId: ScopeId = ScopeId(SessionId(0), 0),
    val executionContextId: ExecutionContextId? = null,
    val sourceFile: String = "<source>",
    functionNames: Map<String, List<SourceFunction>>? = null,
    callCandidates: Map<SourceSpan, List<SourceFunction>>? = null,
    memberDeclarations: Map<Pair<TypeId, String>, List<SourceFunction>> = emptyMap(),
    structuredSymbols: Map<top.mcfpp.library.syntax.NameUse, SourceSymbol> = emptyMap(),
    val sourceTextOffset: Int = 0
) {
    val symbols: Map<String, SourceSymbol> = immutableMap(symbols.associateBy { it.name })
    val structuredSymbols: Map<top.mcfpp.library.syntax.NameUse, SourceSymbol> = immutableMap(structuredSymbols)
    val functions: List<SourceFunction> = immutableList(functions)
    /**
     * Declared members keyed by their actual receiver type identity. Members are kept out of
     * the unqualified global function view so they can only be selected through a receiver.
     */
    private val memberCandidates: Map<Pair<TypeId, String>, List<SourceFunction>> =
        immutableMap(memberDeclarations.mapValues { immutableList(it.value) })
    /** Complete canonical member declaration view for the session bridge and later receiver emission. */
    val memberFunctionDeclarations: List<SourceFunction> = immutableList(memberDeclarations.values.flatten().distinct())
    private val functionNames: Map<String, List<SourceFunction>> = immutableMap(
        (
            functionNames
                ?: functions.flatMap { function -> listOf(function.name, function.function.name()).distinct().map { it to function } }
                    .groupBy({ it.first }, { it.second })
            ).mapValues { immutableList(it.value) }
    )
    private val callCandidates = immutableMap(
        (
            callCandidates ?: functions.flatMap { function ->
                function.preparedCallSites.map { it to function }
            }.groupBy({ it.first }, { it.second })
            ).mapValues { immutableList(it.value) }
    )
    private val multiplePreparedDeclarations = this.functions.groupBy { it.declarationIdentity }.filterValues {
        it.size > 1 && it.any { function -> function.preparedReadonly != null }
    }
    val fields: Map<Pair<TypeKey, String>, SourceField> = immutableMap(
        fields.associateBy {
            ((it.receiver as? ImmutableTypeRef<*>)?.key ?: error("A source field receiver must use the canonical type catalog")) to it.name
        }
    )

    init {
        require(this.symbols.size == symbols.size) { "Duplicate source symbol name" }
        require(scopeId.session.value == 0L || executionContextId == null || executionContextId.session == scopeId.session)
        require(sourceFile.isNotBlank())
        val ownership = SourceSnapshotOwnership(types)
        this.symbols.values.forEach(ownership::symbol)
        this.functions.forEach(ownership::function)
        val memberSet = this.memberFunctionDeclarations
        memberSet.forEach(ownership::function)
        require(this.memberCandidates.keys.all { (id, name) -> name.isNotBlank() && types.owns(id) }) {
            "A member candidate key is outside the environment's canonical type catalog"
        }
        require(this.functionNames.values.flatten().all { named -> this.functions.any { it === named } }) {
            "A function name view refers to a declaration outside the snapshot"
        }
        require(
            this.callCandidates.values.flatten().all { named ->
                this.functions.any { it === named } || memberSet.any { it === named }
            }
        ) {
            "A prepared call view refers to a declaration outside the snapshot"
        }
        multiplePreparedDeclarations.values.forEach { declarations ->
            val bindings = declarations.map {
                it.preparedReadonly?.arguments?.map { argument ->
                    (argument.type as ImmutableTypeRef<*>).key to argument.value
                }
            }
            val sites = this.callCandidates.values
            if (bindings.any { it == null } ||
                bindings.distinct().size != bindings.size ||
                declarations.any { declaration -> sites.none { candidates -> candidates.any { it === declaration } } }
            )
                throw SourceBindingFailure(
                    declarations.firstNotNullOf { it.preparedReadonly }.source,
                    "Multiple prepared signatures require distinct bindings and explicit original call sites"
                )
        }
        this.callCandidates.forEach { (source, candidates) ->
            if (candidates.groupBy { it.declarationIdentity }.values.any { same ->
                    same.size > 1 && same.any { it.preparedReadonly != null }
                }
            ) throw SourceBindingFailure(source, "Multiple prepared signatures for one declaration at the same source call")
        }
        SourceDefaultBinder.validateCycles(this.functions)
        fields.forEach(ownership::field)
        require(this.fields.size == fields.size) { "Duplicate source field view" }
    }

    internal fun withFrozenCalls(additional: Collection<SourceFunction>,
                                 calls: Map<SourceSpan, List<SourceFunction>>): BindingEnvironment {
        fun key(function: SourceFunction) = (function.function as ImmutableFunctionRef<*>).key
        val merged = functions.toMutableList()
        additional.forEach { candidate ->
            if (merged.none { key(it) == key(candidate) }) merged += candidate
        }
        val selected = calls.mapValues { (source, candidates) -> candidates.map { candidate ->
            merged.firstOrNull { it === candidate }
                ?: merged.singleOrNull { key(it) == key(candidate) }
                ?: throw SourceBindingFailure(source, "Frozen call candidate has no unique prepared declaration")
        } }
        return BindingEnvironment(types, symbols.values, merged, fields.values, sourceText, scopeId,
            executionContextId, sourceFile, functionNames, callCandidates + selected, memberCandidates,
            structuredSymbols, sourceTextOffset)
    }

    /** Source spans stay absolute while a callback stores only its own source slice. */
    fun sourceSlice(start: Int, end: Int): String? {
        val text = sourceText ?: return null
        if (start < sourceTextOffset || end < start || end.toLong() > sourceTextOffset.toLong() + text.length) return null
        return text.substring(start - sourceTextOffset, end - sourceTextOffset)
    }

    fun symbol(name: String): SourceSymbol? = symbols[name]
    fun type(name: String): TypeRef<*>? = (symbols[name]?.constant as? ConstantValue.TypeValue)?.type ?: types.resolve(name)
    fun functions(name: String): List<SourceFunction> = functionNames[name].orEmpty()
    fun functions(name: String, source: SourceSpan): List<SourceFunction> {
        val visible = functions(name)
        callCandidates[source]?.let { candidates -> return candidates.filter { prepared -> visible.any { it === prepared } } }
        if (visible.any { it.declarationIdentity in multiplePreparedDeclarations })
            throw SourceBindingFailure(source, "Multiple prepared signatures require preparation for this original call site")
        return visible
    }
    /**
     * Declared member candidates for one actual receiver type and original call site. Access is
     * already resolved by the declaration preparation that produced this immutable view.
     */
    fun memberFunctions(receiver: TypeRef<*>, name: String, source: SourceSpan): List<SourceFunction> {
        require(types.owns(receiver)) { "Member receiver is outside the environment's canonical catalog" }
        val all = memberCandidates[types.facts(receiver).id to name].orEmpty()
        val selected = callCandidates[source]?.let { prepared ->
            all.filter { candidate -> prepared.any { it === candidate } }
        } ?: all
        if (selected.groupBy { it.declarationIdentity }.values.any { same ->
                same.size > 1 && same.any { it.preparedReadonly != null }
            }
        ) throw SourceBindingFailure(
            source,
            "Multiple prepared member signatures require preparation for this original call site"
        )
        return selected
    }
    fun field(owner: TypeRef<*>, name: String): SourceField? {
        require(types.owns(owner)) { "Field receiver is outside the environment's canonical catalog" }
        return (owner as? ImmutableTypeRef<*>)?.let { fields[it.key to name] }
    }
}

/** Immutable source declaration snapshots. Binding never reaches Var, FunctionScope, or a getter. */
internal data class SourceSymbol(
    val name: String,
    val type: TypeRef<*>,
    val symbol: SymbolId,
    val writable: Boolean = false,
    val constant: ConstantValue? = null,
    val executionContextId: ExecutionContextId? = null,
    val global: Boolean = false
)

/** Prepared by the declaration phase, before selection; never a request to specialize a body. */
internal class SourcePreparedReadonly(val declaration: FunctionKey, arguments: List<TypedConstant>, val source: SourceSpan) {
    val arguments: List<TypedConstant> = immutableList(arguments)
}

internal class SourceFunction(
    val name: String,
    val function: FunctionRef<*>,
    val effect: BoundEffect = BoundEffect.CALL,
    readonlyParameters: List<SourceParameter> = emptyList(),
    defaultedReadonlyParameters: Set<Int> = emptySet(),
    defaultedNormalParameters: Set<Int> = emptySet(),
    readonlyDefaultExpressions: Map<Int, BoundExpr> = emptyMap(),
    normalDefaultExpressions: Map<Int, BoundExpr> = emptyMap(),
    val sourceBody: SourceFunctionBody? = null,
    readonlySymbols: List<SourceSymbol> = emptyList(),
    normalSymbols: List<SourceSymbol> = emptyList(),
    val preparedReadonly: SourcePreparedReadonly? = null,
    preparedCallSites: Set<SourceSpan> = emptySet(),
    val compilerDeclarationId: Long? = null
) {
    val readonlyParameters: List<SourceParameter> = immutableList(
        readonlyParameters.map { parameter ->
            val descriptor = ImmutableParameterView("readonly", parameter.type, parameter.mode, parameter.inlineSignature)
            SourceParameter(parameter.type, parameter.mode, descriptor.inlineSignature())
        }
    )
    val defaultedReadonlyParameters: Set<Int> = immutableSet(defaultedReadonlyParameters + readonlyDefaultExpressions.keys)
    val defaultedNormalParameters: Set<Int> = immutableSet(defaultedNormalParameters + normalDefaultExpressions.keys)
    val readonlyDefaultExpressions: Map<Int, BoundExpr>
    val normalDefaultExpressions: Map<Int, BoundExpr>
    init {
        val snapshot = BoundExpressionSnapshot()
        this.readonlyDefaultExpressions = immutableMap(readonlyDefaultExpressions.mapValues { snapshot.expression(it.value) })
        this.normalDefaultExpressions = immutableMap(normalDefaultExpressions.mapValues { snapshot.expression(it.value) })
    }
    val readonlySymbols: List<SourceSymbol> = immutableList(readonlySymbols)
    val normalSymbols: List<SourceSymbol> = immutableList(normalSymbols)
    /** Original complete source spans survive local environment copies with these declarations. */
    val preparedCallSites: Set<SourceSpan> = immutableSet(preparedCallSites)
    val declarationIdentity: FunctionKey get() = preparedReadonly?.declaration ?: (function as ImmutableFunctionRef<*>).key
    init {
        require(name.isNotBlank())
    }

    internal fun validateDefaults(types: SourceTypeCatalog) {
        val prepared = types.declarationsPrepared
        fun formals(symbols: List<SourceSymbol>, parameters: List<TypeRef<*>>, part: String) {
            require(symbols.isEmpty() || symbols.size == parameters.size) { "$part formal identity count differs from signature" }
            symbols.zip(parameters).forEach { (symbol, type) ->
                require(symbol.type.sameType(type)) { "$part formal identity type differs from signature" }
            }
        }
        formals(readonlySymbols, readonlyParameters.map { it.type }, "Readonly")
        formals(normalSymbols, function.parameters().map { it.type() }, "Normal")
        val all = readonlySymbols + normalSymbols
        require(all.map { it.symbol }.distinct().size == all.size) { "Duplicate formal symbol identity" }
        require(all.map { it.name }.distinct().size == all.size) { "Duplicate formal parameter name" }
        require(
            defaultedReadonlyParameters.all { it in readonlyParameters.indices } &&
                defaultedNormalParameters.all { it in function.parameters().indices }
        ) { "Default parameter index is outside the signature" }
        if (prepared) {
            require(
                function.readonlyParameters().size == readonlyParameters.size &&
                    function.readonlyParameters().zip(readonlyParameters).all { (actual, expected) ->
                        actual.type().sameType(expected.type) && actual.mode() == expected.mode
                    }
            ) { "Prepared readonly parameters differ from the canonical function signature" }
            require(
                defaultedReadonlyParameters == readonlyDefaultExpressions.keys &&
                    defaultedNormalParameters == normalDefaultExpressions.keys
            ) { "Prepared defaults require bound expression templates" }
            val hasDefaults = readonlyDefaultExpressions.isNotEmpty() || normalDefaultExpressions.isNotEmpty()
            require(!hasDefaults || readonlySymbols.size == readonlyParameters.size) {
                "Prepared readonly defaults require formal identities"
            }
            require(!hasDefaults || normalSymbols.size == function.parameters().size) {
                "Prepared normal defaults require formal identities"
            }
        }
        val readonlyIds = readonlySymbols.mapIndexed { index, symbol -> symbol.symbol to index }.toMap()
        val normalIds = normalSymbols.mapIndexed { index, symbol -> symbol.symbol to index }.toMap()
        fun check(index: Int, expression: BoundExpr, readonly: Boolean) {
            val expected = if (readonly) readonlyParameters[index].type else function.parameters()[index].type()
            if (types.conversionRank(expression.type, expected) == null)
                throw SourceBindingFailure(expression.source, "Default type differs from the parameter signature")
            SourceExpressionReads.collect(expression).forEach { symbol ->
                val r = readonlyIds[symbol.symbol]
                val n = normalIds[symbol.symbol]
                val formal = r?.let { readonlySymbols[it] } ?: n?.let { normalSymbols[it] }
                if (formal != null && symbol != formal)
                    throw SourceBindingFailure(expression.source, "Default formal identity has inconsistent facts for '${symbol.name}'")
                if ((readonly && (n != null || r != null && r >= index || r == null && symbol.constant == null)) ||
                    (!readonly && n != null && n >= index)
                ) {
                    throw SourceBindingFailure(expression.source, "Default refers to an unavailable or later formal '${symbol.name}'")
                }
            }
        }
        readonlyDefaultExpressions.forEach { (index, expression) -> check(index, expression, true) }
        normalDefaultExpressions.forEach { (index, expression) -> check(index, expression, false) }
        preparedReadonly?.let { prepared ->
            if (prepared.arguments.size != readonlyParameters.size)
                throw SourceBindingFailure(prepared.source, "Prepared readonly arity differs from the concrete signature")
            prepared.arguments.zip(readonlyParameters).forEach { (argument, parameter) ->
                if (types.conversionRank(argument.type, parameter.type) == null)
                    throw SourceBindingFailure(argument.source, "Prepared readonly type differs from the concrete signature")
                SourceConstantFacts.validateClosed(argument.type, argument.value, types, argument.source)
            }
        }
    }
}

internal data class SourceParameter(
    val type: TypeRef<*>,
    val mode: ParamMode = ParamMode.VALUE,
    val inlineSignature: InlineSignature<*>? = null
)

internal data class SourceField(
    val owner: TypeRef<*>,
    val name: String,
    val type: TypeRef<*>,
    val writable: Boolean,
    val source: SourceSpan = SourceSpan.unknown(),
    val declaration: SourceFieldId? = null,
    val canonical: FieldRef<*>? = null,
    val receiver: TypeRef<*> = owner
)

/** Only traverses bound descriptions; neither call targets nor accessors are executed. */
private object SourceExpressionReads {
    fun collect(root: BoundExpr): List<SourceSymbol> {
        val result = arrayListOf<SourceSymbol>()
        val seen = java.util.Collections.newSetFromMap(java.util.IdentityHashMap<BoundExpr, Boolean>())
        lateinit var visit: (BoundExpr) -> Unit
        fun location(value: BoundLocation) {
            result.add(value.root)
            value.path.forEach { if (it is BoundLocationStep.Index) visit(it.expression) }
        }
        fun argument(value: BoundCallArgument) {
            when (value) {
                is BoundCallArgument.Value -> visit(value.expression)
                is BoundCallArgument.Reference -> location(value.location)
                is BoundCallArgument.Callback -> Unit // Syntax belongs to the later body binder.
            }
        }
        visit = { value ->
            if (seen.add(value)) when (val node = value.node) {
                is BoundExprNode.Read -> location(node.location)
                is BoundExprNode.Unary -> visit(node.operand)
                is BoundExprNode.Binary -> {
                    visit(node.left)
                    visit(node.right)
                }
                is BoundExprNode.Convert -> visit(node.value)
                is BoundExprNode.Field -> visit(node.receiver)
                is BoundExprNode.Index -> {
                    visit(node.receiver)
                    visit(node.index)
                }
                is BoundExprNode.Call -> {
                    node.sourceCall.receiver?.let(::location)
                    node.sourceCall.readonlyArguments.forEach(visit)
                    node.sourceCall.arguments.forEach(::argument)
                    node.sourceCall.evaluationOrder.forEach {
                        when (it) {
                            is SourceEvaluationArgument.Readonly -> visit(it.expression)
                            is SourceEvaluationArgument.Normal -> argument(it.argument)
                        }
                    }
                }
                is BoundExprNode.Literal, is BoundExprNode.Unsupported -> Unit
            }
        }
        visit(root)
        return result
    }
}

/** Validate the entire descriptor graph, including facts hidden inside defaults and callbacks. */
private class SourceSnapshotOwnership(private val types: SourceTypeCatalog) {
    private val seen = java.util.Collections.newSetFromMap(java.util.IdentityHashMap<Any, Boolean>())
    private fun type(type: TypeRef<*>) {
        require(types.owns(type)) { "Type '${type.name()}' is outside the source environment's canonical catalog" }
        if (seen.add(type)) type.inlineSignature()?.let(::signature)
    }
    private fun signature(descriptor: InlineSignature<*>) {
        if (!seen.add(descriptor)) return
        type(descriptor.returnType())
        descriptor.parameters().forEach { parameter ->
            type(parameter.type())
            parameter.inlineSignature()?.let(::signature)
        }
    }
    private fun constant(value: ConstantValue?) {
        when (value) {
            is ConstantValue.TypeValue -> type(value.type)
            is ConstantValue.Sequence -> value.elements.forEach(::constant)
            is ConstantValue.Record -> value.fields.values.forEach(::constant)
            else -> Unit
        }
    }
    fun symbol(symbol: SourceSymbol) {
        type(symbol.type)
        constant(symbol.constant)
    }
    fun field(field: SourceField) {
        type(field.owner)
        type(field.type)
        type(field.receiver)
        if (types.declarationsPrepared || field.canonical != null || field.declaration != null) {
            val canonical = types.sourceField(field.receiver, field.name)
            require(canonical != null && field == canonical && field.canonical === canonical.canonical) {
                "Source field is outside the environment's canonical catalog view"
            }
        }
    }
    fun function(function: SourceFunction) {
        if (!seen.add(function)) return
        type(function.function.returnType())
        function.function.parameters().forEach { parameter ->
            type(parameter.type())
            parameter.inlineSignature()?.let(::signature)
        }
        function.function.readonlyParameters().forEach { parameter ->
            type(parameter.type())
            parameter.inlineSignature()?.let(::signature)
        }
        function.readonlyParameters.forEach { parameter ->
            type(parameter.type)
            parameter.inlineSignature?.let(::signature)
        }
        function.readonlyDefaultExpressions.values.forEach(::expression)
        function.normalDefaultExpressions.values.forEach(::expression)
        function.readonlySymbols.forEach(::symbol)
        function.normalSymbols.forEach(::symbol)
        function.preparedReadonly?.arguments?.forEach { argument ->
            type(argument.type)
            constant(argument.value)
        }
        function.validateDefaults(types)
    }
    private fun location(location: BoundLocation) {
        type(location.type)
        symbol(location.root)
        location.path.forEach { step ->
            when (step) {
                is BoundLocationStep.Field -> field(step.declaration)
                is BoundLocationStep.Index -> expression(step.expression)
            }
        }
    }
    private fun argument(argument: BoundCallArgument) {
        when (argument) {
            is BoundCallArgument.Value -> expression(argument.expression)
            is BoundCallArgument.Reference -> location(argument.location)
            is BoundCallArgument.Callback -> {
                signature(argument.callback.signature)
                type(argument.callback.callbackType)
                argument.callback.functionTarget?.let(::function)
            }
        }
    }
    private fun call(call: SourceCall) {
        function(call.target)
        call.receiver?.let(::location)
        call.readonlyArguments.forEach(::expression)
        call.arguments.forEach(::argument)
        call.evaluationOrder.forEach {
            when (it) {
                is SourceEvaluationArgument.Readonly -> expression(it.expression)
                is SourceEvaluationArgument.Normal -> argument(it.argument)
            }
        }
    }
    private fun expression(bound: BoundExpr) {
        if (!seen.add(bound)) return
        type(bound.type)
        constant(bound.constant)
        when (val node = bound.node) {
            is BoundExprNode.Literal -> constant(node.value)
            is BoundExprNode.Read -> location(node.location)
            is BoundExprNode.Unary -> expression(node.operand)
            is BoundExprNode.Binary -> {
                expression(node.left)
                expression(node.right)
            }
            is BoundExprNode.Convert -> {
                expression(node.value)
                type(node.target)
            }
            is BoundExprNode.Field -> {
                expression(node.receiver)
                field(node.field)
            }
            is BoundExprNode.Index -> {
                expression(node.receiver)
                expression(node.index)
            }
            is BoundExprNode.Call -> call(node.sourceCall)
            is BoundExprNode.Unsupported -> Unit
        }
    }
}

/** The binder consumes these frozen facts, never the mutable compiler descriptor returned by source(). */
internal data class SourceTypeFacts(val id: TypeId, val elementType: TypeId?, val indicesWritable: Boolean,
                                   val captureContract: top.mcfpp.core.lang.LambdaCopyContract)

/** Resolved declaration inputs: no TypeRef, compiler object, parser, or allocated session identity. */
internal sealed interface SourceCallbackType {
    data class Declared(val id: TypeId) : SourceCallbackType
    data class Callback(val signature: SourceCallbackSignature) : SourceCallbackType
}
internal data class SourceCallbackParameter(val type: SourceCallbackType, val mode: ParamMode = ParamMode.VALUE)
internal class SourceCallbackSignature(parameters: List<SourceCallbackParameter>, val returnType: SourceCallbackType) {
    val parameters: List<SourceCallbackParameter> = immutableList(parameters)
    /** Names and source spelling have already been resolved and cannot distinguish callback types. */
    internal fun shape(): String {
        fun type(value: SourceCallbackType): String = when (value) {
            is SourceCallbackType.Declared -> value.id.toString().let { "D${it.length}:$it" }
            is SourceCallbackType.Callback -> value.signature.shape().let { "F${it.length}:$it" }
        }
        return parameters.joinToString(";", "(", ")") { "${it.mode}:${type(it.type)}" } + "->" + type(returnType)
    }
}

private data class SourceCallbackKey(val parameters: List<Pair<TypeKey, ParamMode>>, val result: TypeKey)

/** All callback aliases are resolved before FrozenTypeGraph allocates any public views. */
private class SourceCallbackDomain(
    sourceKeys: Map<TypeId, TypeKey>,
    callbacks: Collection<SourceCallbackSignature>,
    declaredCallbacks: Map<TypeId, SourceCallbackSignature>,
    universe: Long
) {
    private val specifications = linkedMapOf<String, SourceCallbackSignature>()
    private val keys = linkedMapOf<String, TypeKey>()
    private val declaredKeys = declaredCallbacks.keys.map {
        sourceKeys[it]
            ?: throw IllegalArgumentException("Callback source type is outside the complete catalog: $it")
    }.toSet()
    private val redirects = hashMapOf<TypeKey, TypeKey>()
    val signatures: Map<TypeKey, FrozenInlineSignatureRecord>
    val stableIds: Map<TypeKey, String>
    val lookup: Map<SourceCallbackKey, TypeKey>

    private fun canonical(key: TypeKey): TypeKey {
        var result = key
        while (redirects[result] != null) result = redirects.getValue(result)
        return result
    }
    private val sourceKeys = immutableMap(sourceKeys)
    private fun type(value: SourceCallbackType): TypeKey = when (value) {
        is SourceCallbackType.Declared -> sourceKeys[value.id]
            ?: throw IllegalArgumentException("Callback references a type outside the complete catalog: ${value.id}")
        is SourceCallbackType.Callback -> canonical(keys.getValue(value.signature.shape()))
    }
    private fun signature(value: SourceCallbackSignature) = SourceCallbackKey(
        immutableList(value.parameters.map { type(it.type) to it.mode }),
        type(value.returnType)
    )

    init {
        fun collect(value: SourceCallbackSignature) {
            if (specifications.putIfAbsent(value.shape(), value) != null) return
            (value.parameters.map { it.type } + value.returnType).forEach {
                if (it is SourceCallbackType.Callback) collect(it.signature)
            }
        }
        callbacks.forEach(::collect)
        declaredCallbacks.values.forEach(::collect)
        declaredCallbacks.forEach { (id, value) ->
            val previous = keys.put(value.shape(), sourceKeys.getValue(id))
            require(previous == null || previous == sourceKeys.getValue(id)) {
                "Distinct declared callback identities describe the same structural type"
            }
        }
        var next = sourceKeys.size.toLong() + 1
        specifications.keys.sorted().forEach { keys.putIfAbsent(it, TypeKey(universe, next++)) }
        // A nested callback may alias a declared callback. Coalesce the outer signatures too.
        var changed: Boolean
        do {
            changed = false
            specifications.entries.groupBy { signature(it.value) }.values.forEach { equivalent ->
                val candidates = equivalent.map { canonical(keys.getValue(it.key)) }.distinct()
                val declared = candidates.filter { it in declaredKeys }
                require(declared.size <= 1) { "Distinct declared callback identities describe the same structural type" }
                val winner = declared.singleOrNull() ?: candidates.minBy { it.id }
                candidates.filter { it != winner }.forEach {
                    redirects[it] = winner
                    changed = true
                }
            }
        } while (changed)
        val records = linkedMapOf<TypeKey, FrozenInlineSignatureRecord>()
        val ids = linkedMapOf<TypeKey, String>()
        val queries = linkedMapOf<SourceCallbackKey, TypeKey>()
        specifications.toSortedMap().forEach { (shape, value) ->
            val key = canonical(keys.getValue(shape))
            val descriptor = signature(value)
            require(descriptor.parameters.none { it.first == sourceKeys[MCFPPPrivateType.Void.typeId] }) {
                "Callback parameters cannot have type void"
            }
            require(descriptor.parameters.all { it.second != ParamMode.INLINE || it.first in keys.values.map(::canonical) }) {
                "INLINE callback parameters require a prepared callback type"
            }
            records.putIfAbsent(
                key,
                FrozenInlineSignatureRecord(
                    descriptor.parameters.mapIndexed { index, parameter ->
                        FrozenParameterRecord("p$index", parameter.first, parameter.second)
                    },
                    descriptor.result
                )
            )
            ids.putIfAbsent(key, "callback:$shape")
            queries[descriptor] = key
        }
        signatures = immutableMap(records)
        stableIds = immutableMap(ids)
        lookup = immutableMap(queries)
    }
}

/** Canonical immutable type projection for one complete declaration snapshot. */
internal class SourceTypeCatalog private constructor(
    private val universe: Long,
    sourceTypes: List<MCFPPType>,
    fieldDeclarations: Collection<SourceFieldDeclaration> = emptyList(),
    fieldViews: Collection<SourceFieldView> = emptyList(),
    constantSchemas: Map<TypeId, SourceConstantSchema> = emptyMap(),
    val declarationsPrepared: Boolean = false,
    callbacks: Collection<SourceCallbackSignature> = emptyList(),
    callbackTypes: Map<TypeId, SourceCallbackSignature> = emptyMap()
) : MacroTypeResolver {
    val graph: FrozenTypeGraph
    private val sourceById: Map<TypeId, MCFPPType> = immutableMap(sourceTypes.associateBy { it.typeId })
    private val refById: Map<TypeId, ImmutableTypeRef<*>>
    private val sourceByRef: Map<TypeKey, MCFPPType>
    private val callbackRefs: Map<SourceCallbackKey, ImmutableTypeRef<*>>
    private val names: Map<String, List<MCFPPType>>
    private val namedRefs: Map<String, TypeRef<*>>
    private val factsByRef: Map<TypeKey, SourceTypeFacts>
    private val reinterpretationPairs: Set<Pair<TypeKey, TypeKey>>
    private val fieldsById: Map<SourceFieldId, FieldRef<*>>
    private val sourceFields: Map<Pair<TypeKey, String>, SourceField>

    init {
        require(sourceById.size == sourceTypes.size) { "A type snapshot contains duplicate identities" }
        val ordered = sourceTypes.sortedBy { it.typeId.toString() }
        val keys = ordered.mapIndexed { index, type -> type.typeId to TypeKey(universe, index.toLong() + 1) }.toMap()
        val callbackDomain = SourceCallbackDomain(keys, callbacks, callbackTypes, universe)
        fun kind(type: MCFPPType): TypeKind = when (type) {
            MCFPPBaseType.Int -> TypeKind.INT
            MCFPPBaseType.Bool -> TypeKind.BOOL
            MCFPPBaseType.Float -> TypeKind.FLOAT
            MCFPPBaseType.String -> TypeKind.STRING
            MCFPPPrivateType.Void -> TypeKind.VOID
            else -> TypeKind.OTHER
        }
        fun key(id: TypeId): TypeKey = keys[id]
            ?: throw IllegalArgumentException("Referenced type is outside the complete source catalog: $id")
        val declarations = fieldDeclarations.associateBy { it.declaration }
        require(declarations.size == fieldDeclarations.size) { "Duplicate source field declaration identity" }
        val fieldKeys = fieldDeclarations.sortedWith(compareBy({ it.owner.toString() }, { it.declaration.declarationKey }))
            .mapIndexed { index, field -> field.declaration to FieldKey(key(field.owner), index.toLong() + 1) }.toMap()
        val views = fieldViews.toList()
        require(views.distinct().size == views.size) { "Duplicate source field view" }
        views.forEach { view ->
            key(view.receiver)
            require(view.declaration in declarations) { "Source field view refers to an unknown declaration" }
            require(TypeRelations.isSubtype(sourceById.getValue(view.receiver), sourceById.getValue(view.declaration.owner))) {
                "Source field view receiver does not inherit the declaring owner"
            }
        }
        val visible = views.groupBy { it.receiver }
        factsByRef = immutableMap(
            ordered.associate { type ->
                val element = when (type) {
                    is MCFPPListType -> type.generic.singleOrNull()?.typeId
                    is MCFPPImmutableListType -> type.generic.singleOrNull()?.typeId
                    else -> TypeRelations.arrayElementType(type.typeId)?.typeId
                }
                keys.getValue(type.typeId) to SourceTypeFacts(type.typeId, element, type !is MCFPPImmutableListType,
                    top.mcfpp.analysis.lambda.LambdaCaptureRules.contractFor(type))
            }
        )
        require(constantSchemas.keys.all { it in keys }) { "Constant schema type is outside the source catalog" }
        fun constantSchema(type: MCFPPType): ConstantSchema? {
            val prepared = constantSchemas[type.typeId]
            if (prepared != null) return when (prepared) {
                is SourceConstantSchema.Scalar -> ConstantSchema.Scalar(prepared.shape)
                is SourceConstantSchema.Sequence -> ConstantSchema.Sequence(key(prepared.element))
                is SourceConstantSchema.Record -> ConstantSchema.Record(prepared.fields.mapValues { key(it.value) })
            }
            val scalar = when (type) {
                MCFPPBaseType.Int -> ConstantShape.INT32
                MCFPPBaseType.Bool -> ConstantShape.BOOL
                MCFPPBaseType.Float -> ConstantShape.FLOAT32
                MCFPPNBTType.Double -> ConstantShape.FLOAT64
                MCFPPBaseType.String -> ConstantShape.STRING
                MCFPPNBTType.NBT -> ConstantShape.NBT
                MCFPPConcreteType.Type -> ConstantShape.TYPE
                MCFPPPrivateType.Null -> ConstantShape.NULL
                else -> null
            }
            return scalar?.let { ConstantSchema.Scalar(it) }
                ?: factsByRef.getValue(key(type.typeId)).elementType?.let { ConstantSchema.Sequence(key(it)) }
        }
        val conversions = ordered.flatMap { source ->
            ordered.mapNotNull { target ->
                TypeRelations.resolveImplicitConversion(source, target)?.let {
                    FrozenConversionRecord(key(source.typeId), key(target.typeId), ConversionKind.IMPLICIT)
                }
            }
        }
        reinterpretationPairs = immutableSet(
            ordered.flatMap { source ->
                ordered.mapNotNull { target ->
                    if (ReinterpretationCompatibility.check(source, target) is ReinterpretationCompatibility.Result.Compatible)
                        keys.getValue(source.typeId) to keys.getValue(target.typeId) else null
                }
            }
        )
        graph = FrozenTypeGraph.freeze(
            ordered.map { type ->
                val facts = factsByRef.getValue(key(type.typeId))
                FrozenTypeRecord(
                    key(type.typeId), type.typeName, type.typeId.toString(), kind(type),
                    acceptedSources = ordered.filter { TypeRelations.isSubtype(it, type) }.map { key(it.typeId) }.toSet(),
                    fields = visible[type.typeId].orEmpty().map { fieldKeys.getValue(it.declaration) },
                    index = facts.elementType?.let { IndexSchema(key(it), facts.indicesWritable) }, constant = constantSchema(type),
                    inlineSignature = callbackDomain.signatures[key(type.typeId)]
                )
            } + callbackDomain.signatures.filterKeys { it !in keys.values }.map { (callbackKey, signature) ->
                val identity = callbackDomain.stableIds.getValue(callbackKey)
                FrozenTypeRecord(callbackKey, identity, identity, TypeKind.OTHER, inlineSignature = signature)
            },
            declarations.values.map { field ->
                FrozenFieldRecord(fieldKeys.getValue(field.declaration), field.name, key(field.valueType), field.writable, field.source)
            },
            conversions + reinterpretationPairs.map { (from, to) -> FrozenConversionRecord(from, to, ConversionKind.EXPLICIT) } +
                conversions.map { it.copy(kind = ConversionKind.EXPLICIT) }
        )
        refById = immutableMap(keys.mapValues { graph.type(it.value) })
        callbackRefs = immutableMap(callbackDomain.lookup.mapValues { graph.type(it.value) })
        sourceByRef = immutableMap(keys.entries.associate { (id, key) -> key to sourceById.getValue(id) })
        names = immutableMap(
            sourceTypes.flatMap { type -> listOf(type.typeName, type.simpleName).distinct().map { it to type } }
                .groupBy({ it.first }, { it.second }).mapValues { (_, matches) -> matches.distinctBy { it.typeId } }
        )
        namedRefs = immutableMap(names.mapNotNull { (name, matches) -> matches.singleOrNull()?.let { name to refById.getValue(it.typeId) } }.toMap())
        fieldsById = immutableMap(fieldKeys.mapValues { graph.field(it.value) })
        sourceFields = immutableMap(
            views.associate { view ->
                val field = declarations.getValue(view.declaration)
                val receiver = refById.getValue(view.receiver)
                (key(view.receiver) to field.name) to SourceField(
                    refById.getValue(field.owner),
                    field.name,
                    refById.getValue(field.valueType),
                    field.writable,
                    field.source,
                    field.declaration,
                    fieldsById.getValue(field.declaration),
                    receiver
                )
            }
        )
    }

    fun field(id: SourceFieldId): FieldRef<*> = fieldsById[id]
        ?: throw IllegalArgumentException("Field identity is outside the immutable source catalog")
    fun field(owner: TypeRef<*>, name: String): FieldRef<*>? = sourceField(owner, name)?.canonical
    fun sourceField(receiver: TypeRef<*>, name: String): SourceField? {
        require(owns(receiver)) { "Field receiver is outside the canonical source catalog" }
        return sourceFields[(receiver as ImmutableTypeRef<*>).key to name]
    }
    fun indexSchema(type: TypeRef<*>): IndexSchema? = ordinaryKey(type)?.let(graph::index)
    fun constantSchema(type: TypeRef<*>): ConstantSchema? = ordinaryKey(type)?.let(graph::constantSchema)
    fun permits(source: TypeRef<*>, target: TypeRef<*>, kind: ConversionKind): Boolean {
        if (!owns(source) || !owns(target)) return false
        val from = (source as ImmutableTypeRef<*>).key
        val to = (target as ImmutableTypeRef<*>).key
        return graph.permits(from, to, kind)
    }
    private fun ordinaryKey(type: TypeRef<*>): TypeKey? {
        require(owns(type)) { "Type reference is outside the canonical source catalog" }
        return (type as ImmutableTypeRef<*>).key.takeIf { graph.owns(type) }
    }

    fun ref(type: MCFPPType): TypeRef<*> = refById[type.typeId]
        ?: throw IllegalArgumentException("Type '${type.typeName}' is outside the immutable binding snapshot")
    /** Compiler bridge only. Expression binding must use facts(), which survives descriptor mutation. */
    fun source(type: TypeRef<*>): MCFPPType {
        require(owns(type)) { "Type reference is outside the immutable binding snapshot" }
        val source = sourceByRef[(type as ImmutableTypeRef<*>).key]
            ?: throw IllegalArgumentException("Callback types have no compiler descriptor")
        require(source.typeId == facts(type).id) { "Compiler type changed since the declaration snapshot" }
        return source
    }
    fun ref(id: TypeId): TypeRef<*> = refById[id]
        ?: throw IllegalArgumentException("Type identity is outside the immutable binding snapshot")
    fun facts(type: TypeRef<*>): SourceTypeFacts {
        require(owns(type)) { "Type reference is outside the immutable binding snapshot" }
        return factsByRef[(type as ImmutableTypeRef<*>).key]
            ?: throw IllegalArgumentException("Callback types have no ordinary source type facts")
    }
    fun owns(type: TypeRef<*>): Boolean = graph.owns(type)
    fun owns(id: TypeId): Boolean = sourceById.containsKey(id)
    fun resolve(id: TypeId): MCFPPType? = sourceById[id]
    fun resolveName(name: String): MCFPPType? = names[name]?.singleOrNull()

    override fun resolve(typeName: String): TypeRef<*>? = namedRefs[typeName]

    /** Read-only lookup. A new callback signature requires a new complete declaration preparation. */
    override fun inlineType(signature: InlineSignature<*>): TypeRef<*>? {
        val visiting = java.util.Collections.newSetFromMap(java.util.IdentityHashMap<InlineSignature<*>, Boolean>())
        fun describe(value: InlineSignature<*>): SourceCallbackKey? {
            if (!owns(value.returnType())) return null
            val parameters = value.parameters().map { parameter ->
                if (!owns(parameter.type())) return null
                (parameter.type() as ImmutableTypeRef<*>).key to parameter.mode()
            }
            return SourceCallbackKey(parameters, (value.returnType() as ImmutableTypeRef<*>).key)
        }
        fun valid(value: InlineSignature<*>): Boolean {
            if (!visiting.add(value)) return true
            if (describe(value) == null) return false
            return value.parameters().all { parameter ->
                val canonical = parameter.type().inlineSignature()
                val explicit = parameter.inlineSignature()
                (parameter.mode() != ParamMode.INLINE || canonical != null) &&
                    (explicit == null || canonical != null && describe(explicit) == describe(canonical) && valid(explicit))
            }
        }
        return if (valid(signature)) callbackRefs[describe(signature)] else null
    }

    fun isSubtype(source: TypeRef<*>, target: TypeRef<*>): Boolean = (source as? ImmutableTypeRef<*>)?.let { from ->
        (target as? ImmutableTypeRef<*>)?.let { to ->
            if (!owns(from) || !owns(to)) false
            else target.isAssignableFrom(source)
        }
    } ?: false

    @Synchronized fun conversionRank(source: TypeRef<*>, target: TypeRef<*>): Int? {
        val fromRef = source as? ImmutableTypeRef<*> ?: return null
        val toRef = target as? ImmutableTypeRef<*> ?: return null
        if (!owns(fromRef) || !owns(toRef)) return null
        if (fromRef.key == toRef.key) return TypeRelations.Conversion.EXACT.rank
        if (!graph.permits(fromRef.key, toRef.key, ConversionKind.IMPLICIT)) return null
        val from = factsByRef[fromRef.key]?.id ?: return null
        val to = factsByRef[toRef.key]?.id ?: return null
        return when {
            to == MCFPPBaseType.Object.typeId -> TypeRelations.Conversion.OBJECT.rank
            to == MCFPPBaseType.Any.typeId -> TypeRelations.Conversion.ANY.rank
            from == MCFPPBaseType.Int.typeId && to == MCFPPBaseType.Float.typeId -> TypeRelations.Conversion.INT_TO_FLOAT.rank
            toRef.isAssignableFrom(fromRef) -> TypeRelations.Conversion.NOMINAL.rank
            else -> null
        }
    }

    fun canExplicitlyConvert(source: TypeRef<*>, target: TypeRef<*>): Boolean {
        if (!owns(source) || !owns(target)) return false
        return permits(source, target, ConversionKind.EXPLICIT)
    }

    companion object {
        private val nextUniverse = AtomicLong(0)
        fun snapshot(types: Collection<MCFPPType>, callbacks: Collection<SourceCallbackSignature> = emptyList()): SourceTypeCatalog = SourceTypeCatalog(nextUniverse.incrementAndGet(), types.toList(), callbacks = callbacks)
        fun snapshot(
            types: Collection<MCFPPType>,
            fields: Collection<SourceFieldDeclaration>,
            fieldViews: Collection<SourceFieldView>,
            constantSchemas: Map<TypeId, SourceConstantSchema> = emptyMap(),
            callbacks: Collection<SourceCallbackSignature> = emptyList(),
            callbackTypes: Map<TypeId, SourceCallbackSignature> = emptyMap()
        ): SourceTypeCatalog = SourceTypeCatalog(
            nextUniverse.incrementAndGet(),
            preparedDomain(types),
            fields.toList(),
            fieldViews.toList(),
            constantSchemas.toMap(),
            declarationsPrepared = true,
            callbacks = callbacks.toList(),
            callbackTypes = callbackTypes.toMap()
        )

        /** Read only prepared parent facts; never resolve a declaration or query its members. */
        fun preparedDomain(types: Collection<MCFPPType>): List<MCFPPType> {
            val originals = types.toList()
            val collected = originals.associateByTo(linkedMapOf()) { it.typeId }
            require(collected.size == originals.size) { "A type snapshot contains duplicate identities" }
            val pending = java.util.ArrayDeque(originals)
            val seen = java.util.Collections.newSetFromMap(java.util.IdentityHashMap<MCFPPType, Boolean>())
            while (pending.isNotEmpty()) {
                val type = pending.removeFirst()
                if (!seen.add(type)) continue
                type.parentType.forEach { parent ->
                    collected.putIfAbsent(parent.typeId, parent)
                    pending.addLast(parent)
                }
            }
            return collected.values.toList()
        }
    }
}

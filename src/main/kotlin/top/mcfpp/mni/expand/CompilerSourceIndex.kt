package top.mcfpp.mni.expand

import org.antlr.v4.runtime.ParserRuleContext
import org.antlr.v4.runtime.misc.Interval
import org.antlr.v4.runtime.tree.ParseTree
import top.mcfpp.Project
import top.mcfpp.analysis.CallableDeclarationId
import top.mcfpp.analysis.CompilerValue
import top.mcfpp.analysis.CompletionTarget
import top.mcfpp.analysis.DeclarationBinding
import top.mcfpp.analysis.DeclarationCompletionRequest
import top.mcfpp.analysis.DeclarationDomainStamp
import top.mcfpp.analysis.DeclarationLexicalSnapshot
import top.mcfpp.analysis.DeclarationPreparationContext
import top.mcfpp.analysis.HeaderPhase
import top.mcfpp.analysis.PreparationDiagnostic
import top.mcfpp.analysis.PreparationFactCode
import top.mcfpp.analysis.PreparationFactFailure
import top.mcfpp.analysis.PreparedCompilerFormal
import top.mcfpp.analysis.PreparedCompilerSignature
import top.mcfpp.analysis.PreparedExpressionFact
import top.mcfpp.analysis.PreparedHeaderBatch
import top.mcfpp.analysis.SignatureDeclarationStamp
import top.mcfpp.analysis.SignaturePreparationRequest
import top.mcfpp.analysis.SignaturePreparationResult
import top.mcfpp.analysis.SignaturePreparationSession
import top.mcfpp.core.lang.Var
import top.mcfpp.io.MCFPPFile
import top.mcfpp.io.info.MacroLoadingSnapshot
import top.mcfpp.mni.api.*
import top.mcfpp.model.Member.AccessModifier
import top.mcfpp.model.Namespace
import top.mcfpp.model.compound.CompiledGenericDataTemplate
import top.mcfpp.model.compound.DataTemplate
import top.mcfpp.model.compound.GenericDataTemplate
import top.mcfpp.model.function.Function
import top.mcfpp.model.function.NativeFunction
import top.mcfpp.model.property.SimpleAccessor
import top.mcfpp.model.property.SimpleMutator
import top.mcfpp.model.scope.*
import top.mcfpp.type.*
import top.mcfpp.util.StringHelper.splitNamespaceID
import java.util.Collections
import java.util.IdentityHashMap
import top.mcfpp.antlr.mcfppParser as P

/** Compiler handles are captured before session references or backend layouts are allocated. */
internal class CompilerSourceEnvironment internal constructor(
    val binding: BindingEnvironment,
    macros: MacroLoadingSnapshot,
    declarations: Map<FunctionKey, BindingEnvironment>,
    functions: Map<FunctionKey, Function>,
    symbols: Map<SymbolId, Var<*>>,
    fields: Set<FieldKey>,
    private val revision: DeclarationDomainStamp,
    /** Present only for declaration-processor preparation, never for ordinary call binding. */
    val declarationView: TemplateView? = null,
    preparedMacros: PreparedMacroCatalog? = null
) {
    internal constructor(
        binding: BindingEnvironment, macros: PreparedMacroCatalog,
        declarations: Map<FunctionKey, BindingEnvironment>, functions: Map<FunctionKey, Function>,
        symbols: Map<SymbolId, Var<*>>, fields: Set<FieldKey>, revision: DeclarationDomainStamp,
        declarationView: TemplateView? = null
    ) : this(binding, macros.sourceSnapshot, declarations, functions, symbols, fields, revision, declarationView, macros)

    val macros = preparedMacros ?: PreparedMacroCatalog(macros)
    val declarationEnvironments: Map<FunctionKey, BindingEnvironment> = immutableMap(declarations)
    val compilerFunctions: Map<FunctionKey, Function> = immutableMap(functions)
    val compilerSymbols: Map<SymbolId, Var<*>> = immutableMap(symbols)
    val fieldCapabilities: Set<FieldKey> = immutableSet(fields)
    fun requireCurrent() { macros.requireCurrent(); revision.requireCurrent() }

    internal fun withBinding(view: BindingEnvironment): CompilerSourceEnvironment {
        require(view.types === binding.types && view.scopeId == binding.scopeId &&
            view.executionContextId == binding.executionContextId) {
            "A frozen call view must retain its prepared type, scope and execution context"
        }
        return CompilerSourceEnvironment(view, macros, declarationEnvironments, compilerFunctions,
            compilerSymbols, fieldCapabilities, revision, declarationView)
    }
}

internal sealed interface CompilerCallPreparationResult {
    class Ready(val environment: CompilerSourceEnvironment, val call: SourceCall) : CompilerCallPreparationResult
    class Rejected(diagnostics: List<PreparationDiagnostic>) : CompilerCallPreparationResult {
        val diagnostics: List<PreparationDiagnostic> = immutableList(diagnostics)
    }
}

/** The caller supplies original syntax before visiting eager arguments. */
internal object CompilerBindingEnvironment {
    fun prepareCall(syntax: P.FunctionCallContext, caller: Function = Function.currFunction,
                    trailing: P.InlineLambdaContext? = null): CompilerCallPreparationResult = CompilerSourceIndex(caller).prepareCallDeclarations(syntax, trailing)
    /** Member syntax lives in the surrounding selector, not in the final function-call rule. */
    fun prepareMemberCall(syntax: P.VarWithSelectorContext, caller: Function = Function.currFunction): CompilerCallPreparationResult = CompilerSourceIndex(caller).prepareMemberCallDeclarations(syntax)
}

/** One original syntax tree, one complete source domain. Each completion invalidates the entire
 * round, including ordinary declarations, defaults, callback descriptors and Java/Kotlin macros.
 */
internal class CompilerSourceIndex(
    private val caller: Function,
    private val maxRounds: Int = 32,
    private val declarationTarget: DataTemplate? = null,
    /** Original processor request whose closed arguments become an own view. */
    private val processorRequest: TemplateProcessorRequest? = null,
    private val nativeDirectory: PreparedNativeDirectory? = PreparedNativeDirectory.current()
) {
    private fun preparationSpan(site: ParserRuleContext): SourceSpan = StructuredSourceFacts.span(site)
    init {
        require(maxRounds > 0)
    }
    private val symbolIds = IdentityHashMap<Any, SymbolId>()
    private var nextSymbol = 0L
    private fun symbol(identity: Any): SymbolId = symbolIds.getOrPut(identity) { SymbolId(SessionId(0), ++nextSymbol) }

    /**
     * Prepare the real canonical source domain for one completed declaration processor.
     *
     * The original annotation syntax is used only as the retained source root. The synthetic
     * caller is owned by [declarationTarget], so the ordinary accessibility walk yields the
     * template's legitimate private self fields without widening any capability. No preview or
     * manufactured public handle is used.
     */
    fun prepareDeclarationEnvironment(syntax: ParserRuleContext): CompilerSourceEnvironment {
        val source = preparationSpan(syntax)
        try {
            repeat(maxRounds) {
                val result = try {
                    Round(syntax, source).prepare()
                } catch (completion: CompletionRequired) {
                    RoundResult.Completion(completion.batch)
                }
                when (result) {
                    is RoundResult.Complete -> return result.environment
                    is RoundResult.Completion -> when (val completion = CompilerDeclarationPreparation().complete(result.batch)) {
                        is DeclarationCompletionResult.Rejected -> throw SourceBindingFailure(
                            source,
                            completion.diagnostics.joinToString { it.message }
                        )
                        is DeclarationCompletionResult.Completed -> completion.revision.requireCurrent()
                    }
                }
            }
            throw SourceBindingFailure(source, "Declaration preparation exceeded its completion round budget")
        } catch (failure: PreparationFactFailure) {
            throw failure
        }
    }

    fun prepareCallDeclarations(syntax: P.FunctionCallContext, trailing: P.InlineLambdaContext? = null): CompilerCallPreparationResult {
        val binder = SnapshotSourceCodeBinder()
        val root = if (trailing != null) syntax.parent as? P.LambdaCallExpressionContext ?: syntax else syntax
        return prepare(root) { environment -> binder.call(syntax, environment, trailing) }
    }

    fun prepareMemberCallDeclarations(syntax: P.VarWithSelectorContext): CompilerCallPreparationResult {
        val binder = SnapshotSourceCodeBinder()
        return prepare(syntax) { environment -> binder.memberCall(syntax, environment) }
    }

    private fun prepare(syntax: ParserRuleContext, bindCall: (BindingEnvironment) -> SourceCall): CompilerCallPreparationResult {
        val source = preparationSpan(syntax)
        try {
            repeat(maxRounds) {
                val result = try {
                    Round(syntax, source).prepare()
                } catch (completion: CompletionRequired) {
                    RoundResult.Completion(completion.batch)
                }
                when (result) {
                    is RoundResult.Complete -> return CompilerCallPreparationResult.Ready(
                        result.environment,
                        bindCall(result.environment.binding)
                    )
                    is RoundResult.Completion -> when (val completion = CompilerDeclarationPreparation().complete(result.batch)) {
                        is DeclarationCompletionResult.Rejected -> return CompilerCallPreparationResult.Rejected(completion.diagnostics)
                        is DeclarationCompletionResult.Completed -> completion.revision.requireCurrent()
                    }
                }
            }
            throw SourceBindingFailure(source, "Declaration preparation exceeded its completion round budget")
        } catch (failure: PreparationFactFailure) {
            return CompilerCallPreparationResult.Rejected(
                listOf(
                    PreparationDiagnostic(
                        failure.code,
                        failure.source,
                        failure.message.orEmpty(),
                        failure
                    )
                )
            )
        } catch (failure: SourceBindingFailure) {
            return CompilerCallPreparationResult.Rejected(
                listOf(
                    PreparationDiagnostic(
                        PreparationFactCode.UNSUPPORTED_EXPRESSION,
                        failure.source,
                        failure.message.orEmpty(),
                        failure
                    )
                )
            )
        } catch (failure: IllegalArgumentException) {
            return CompilerCallPreparationResult.Rejected(
                listOf(
                    PreparationDiagnostic(
                        PreparationFactCode.UNPREPARED_TYPE,
                        source,
                        failure.message.orEmpty(),
                        failure
                    )
                )
            )
        } catch (failure: ReflectiveOperationException) {
            return CompilerCallPreparationResult.Rejected(
                listOf(
                    PreparationDiagnostic(
                        PreparationFactCode.UNPREPARED_TYPE,
                        source,
                        "Cannot restore macro provider: ${failure.message}",
                        failure
                    )
                )
            )
        } catch (failure: LinkageError) {
            return CompilerCallPreparationResult.Rejected(
                listOf(
                    PreparationDiagnostic(
                        PreparationFactCode.UNPREPARED_TYPE,
                        source,
                        "Cannot link macro provider: ${failure.message}",
                        failure
                    )
                )
            )
        }
    }

    private sealed interface RoundResult {
        class Complete(val environment: CompilerSourceEnvironment) : RoundResult
        class Completion(val batch: PreparedHeaderBatch) : RoundResult
    }

    private inner class Round(val syntax: ParserRuleContext, val source: SourceSpan) {
        private val file = caller.declarationFile ?: MCFPPFile.currFile
        private val declarationMethods = arrayListOf<Function>()
        private val lexical = DeclarationLexicalSnapshot.capture(
            file,
            caller.declarationEnvironment?.namespace ?: caller.namespace,
            caller.declarationEnvironment?.imports.orEmpty(),
            caller.scope
        )
        private val namespaces = arrayListOf<Namespace>()
        private val roots = arrayListOf<Any>(caller).apply { addAll(lexical.domainRoots) }
        private val types = linkedMapOf<TypeId, MCFPPType>()
        private val signatures = linkedMapOf<top.mcfpp.analysis.PreparedSignatureIdentity, PreparedCompilerSignature>()
        private val preparedKeys = linkedMapOf<top.mcfpp.analysis.PreparedSignatureIdentity, FunctionKey>()
        private val ownerKeys = linkedMapOf<TypeId, Int>()
        private val callSignatures = linkedMapOf<SourceSpan, List<top.mcfpp.analysis.PreparedSignatureIdentity>>()
        private val callNames = linkedMapOf<SourceSpan, String>()
        private val memberRequests = arrayListOf<MemberRequest>()
        private val memberReceivers = IdentityHashMap<Function, MutableSet<TypeId>>()
        private val compilerSymbols = linkedMapOf<SymbolId, Var<*>>()
        private val fieldDeclarations = linkedMapOf<SourceFieldId, SourceFieldDeclaration>()
        private val fieldViews = linkedSetOf<SourceFieldView>()
        private val fieldAccess = linkedMapOf<SourceFieldId, Pair<DataTemplate?, AccessModifier>>()
        private val schemas = linkedMapOf<TypeId, SourceConstantSchema>()
        private val scopeNames = linkedSetOf<String>()
        private val requestedBindings = arrayListOf<BindingRequest>()
        private val discovered = Collections.newSetFromMap(IdentityHashMap<Function, Boolean>())
        private val structuredFunctions = IdentityHashMap<Function, MutableSet<Function>>()
        private val structuredUses = IdentityHashMap<Function, MutableMap<top.mcfpp.library.syntax.NameUse, String>>()
        private val rawValues = IdentityHashMap<Function, Map<String, Var<*>>>()
        private var callerValues: Map<String, Var<*>> = emptyMap()
        private val valueTypes = IdentityHashMap<Var<*>, MCFPPType>()
        private val macroTypeNames = linkedMapOf<String, MutableMap<String, MCFPPType>>()
        private val lexicalTypeNames = IdentityHashMap<Function, MutableMap<String, MCFPPType>>()
        private val metadataTypeIds = IdentityHashMap<TypeRef<*>, TypeId>()
        private var completion: PreparedHeaderBatch? = null

        fun prepare(): RoundResult {
            DeclarationDomainStamp.captureRoots(roots, source)
            baseline.forEach(::collect)
            nativeDirectory?.let { directory -> directory.requireOpen(); directory.sourceTypes.forEach(::collect) }
            discover(caller, lexical)
            declarationTarget?.let { target ->
                // The completed target is the actual lexical owner of the synthetic processor
                // caller. Collect its canonical type/fields and every visible member signature
                // before the immutable catalog is frozen.
                collect(target.getType())
                target.declarationMetadata().forEach { metadata ->
                    metadata.arguments().values.forEach(::collectMetadataType)
                }
                declarationMethods += declarationMethodCandidates(target)
                declarationMethods.forEach { function ->
                    (function.owner as? DataTemplate)?.let { collect(it.getType()) }
                    collect(function.returnType)
                    function.normalParams.forEach { collect(it.type) }
                    (function as? top.mcfpp.model.Generic<*>)?.readOnlyParams?.forEach { collect(it.type) }
                }
            }
            val calls = arrayListOf<P.FunctionCallContext>()
            fun visit(node: ParseTree) {
                if (node is P.InlineLambdaContext) return
                if (node is P.FunctionCallContext) calls.add(node)
                for (index in 0 until node.childCount) visit(node.getChild(index))
            }
            visit(syntax)
            val callerTypeNames = lexicalTypeNames.getOrPut(caller) { linkedMapOf() }
            fun typeSyntax(node: ParseTree) {
                if (node is P.InlineLambdaContext) return
                if (node is P.TypeContext)
                    callerTypeNames[node.text] = top.mcfpp.analysis.StructuredBindings.type(node, caller)
                        ?: prepareType(UnresolvedType(node.text), lexical, preparationSpan(node))
                else for (index in 0 until node.childCount) typeSyntax(node.getChild(index))
            }
            typeSyntax(syntax)
            for (call in calls) {
                val name = call.namespaceID().text
                val facts = call.arguments().readOnlyArgs()?.expressionList()?.expression().orEmpty().map {
                    val session = SignaturePreparationSession()
                    completing(session, lexical, preparationSpan(it)) {
                        val binder = DeclarationBinding(DeclarationPreparationContext(lexical, headers = session))
                        PreparedExpressionFact.fromBound(binder.readonly(it), preparationSpan(it), binder).also {
                            requireHeaders(session, preparationSpan(call))
                        }
                    }
                }
                facts.forEach { fact ->
                    fact.bound.descriptors.values.forEach(::collect)
                    constant(fact.value)
                }
                val member = memberSelector(call)
                // A member's original call site is the whole selector expression, matching the
                // receiver+arguments SourceCall span; the inner function-call span is only a suffix.
                val site = member?.let { preparationSpan(it) } ?: preparationSpan(call)
                callNames[site] = name
                callSignatures[site] = emptyList()
                if (member == null) requestedBindings.add(BindingRequest(
                    top.mcfpp.analysis.StructuredBindings.functions(call.namespaceID(), caller) ?: visibleFunctions(name, lexical, caller), facts, caller, site,
                    top.mcfpp.analysis.StructuredAuthorAccess.capture(call.namespaceID(), caller)))
                else memberRequests.add(MemberRequest(member, name, facts, site))
            }
            val called = Collections.newSetFromMap(IdentityHashMap<Function, Boolean>())
            requestedBindings.filter { it.source != null }.forEach { called.addAll(it.functions) }
            fun prepareFunctions(
                found: List<Function>,
                facts: List<PreparedExpressionFact>,
                site: SourceSpan?,
                preparationCaller: Function,
                authorAccess: top.mcfpp.analysis.StructuredAuthorAccess? = null
            ): RoundResult? {
                if (found.isEmpty()) return null
                roots.addAll(found)
                val session = SignaturePreparationSession()
                return when (
                    val prepared = session.prepare(
                        SignaturePreparationRequest(
                            found,
                            facts,
                            source,
                            caller = preparationCaller,
                            authorAccess = authorAccess
                        )
                    )
                ) {
                    is SignaturePreparationResult.NeedsCompletion -> RoundResult.Completion(prepared.staged)
                    is SignaturePreparationResult.Rejected -> throwDiagnostics(prepared.diagnostics)
                    is SignaturePreparationResult.Ready -> {
                        site?.let { callSignatures[it] = prepared.declarations.signatures.map { signature -> signature.identity } }
                        prepared.declarations.signatures.forEach { signature ->
                            signatures.putIfAbsent(signature.identity, signature)
                            roots.addAll(signature.lexical.domainRoots)
                            discover(signature.function, signature.lexical)
                            (signature.readonly + signature.normal).forEach { formal ->
                                collect(formal.type)
                                formal.defaultTypes.values.forEach(::collect)
                            }
                            collect(signature.returnType)
                            signature.readonlyArguments.forEach { fact ->
                                fact.bound.descriptors.values.forEach(::collect)
                                constant(fact.value)
                            }
                        }
                        null
                    }
                }
            }
            var requestIndex = 0
            while (requestIndex < requestedBindings.size) {
                val (discoveredFunctions, facts, owner, callSite, authorAccess) = requestedBindings[requestIndex++]
                // An actual call supplies this declaration's binding. A discovery-only default
                // must not introduce an additional specialization beside those original calls.
                val functions = if (callSite == null) discoveredFunctions.filter { it !in called } else discoveredFunctions
                prepareFunctions(functions, facts, callSite, owner, authorAccess)?.let { return it }
            }
            // Parse provider type metadata through the same pure compiler type walker, before
            // MacroLoadingSnapshot freezes callback and ordinary types in one graph.
            for (namespace in namespaces) {
                val declaration = DeclarationLexicalSnapshot.capture(null, namespace.identifier)
                roots.addAll(declaration.domainRoots)
                val names = macroTypeNames.getOrPut(namespace.identifier) { linkedMapOf() }
                namespace.macroDeclarations().forEach { descriptor ->
                    val prepared = MacroSignatureParser.prepare(descriptor.method(Project.classLoader)) ?: return@forEach
                    fun describe(type: PreparedMacroType, returnPosition: Boolean = false) {
                        when (type) {
                            is PreparedMacroType.Source -> if (type.expression !in names)
                                names[type.expression] = if (returnPosition && type.expression == "void") MCFPPPrivateType.Void
                                else prepareType(UnresolvedType(type.expression), declaration, type.source)
                            is PreparedMacroType.Callback -> {
                                type.signature.parameters.forEach { describe(it.type) }
                                describe(type.signature.returnType, returnPosition = true)
                            }
                        }
                    }
                    prepared.parameters.forEach { describe(it.type) }
                    describe(prepared.returnType, returnPosition = true)
                }
            }
            callerValues = values(caller, lexical, includeLocals = true)
            callerValues.values.forEach { value ->
                valueTypes[value] = prepareType(value.type, lexical, source)
                DeclarationLexicalSnapshot.rawValue(value)?.let {
                    it.descriptors.values.forEach(::collect)
                    it.constant?.let(::constant)
                }
            }
            val discoveryStart = requestedBindings.size
            if (memberRequests.isNotEmpty()) {
                // Resolve only the actual receiver prefix against the already-prepared caller
                // symbols, then prepare that receiver type's original declared member views.
                val preview = previewEnvironment()
                val binder = SnapshotSourceCodeBinder()
                memberRequests.forEach { request ->
                    val receiver = binder.receiverLocation(request.syntax, preview)
                    val receiverId = preview.types.facts(receiver.type).id
                    val receiverType = types[receiverId] ?: throw SourceBindingFailure(
                        request.site,
                        "Member receiver type is outside the prepared declaration snapshot"
                    )
                    val memberDomain = (receiverType as? MCFPPDataTemplateType)?.template ?: receiverType.instanceData
                    val candidates = memberDomain.scope.getFunctionCandidates(request.name, caller).filter { function ->
                        !function.actualCallBody && accessible(caller, function.owner as? DataTemplate, function.accessModifier)
                    }
                    candidates.forEach { function -> memberReceivers.getOrPut(function) { linkedSetOf() }.add(receiverId) }
                    prepareFunctions(candidates, request.facts, request.site, caller)?.let { return it }
                }
            }
            // Member declarations can discover further global declarations; finish that discovery
            // before freezing default expressions and validation facts for every signature.
            var discoveryIndex = discoveryStart
            while (discoveryIndex < requestedBindings.size) {
                val (discoveredFunctions, facts, owner, callSite, authorAccess) = requestedBindings[discoveryIndex++]
                val functions = if (callSite == null) discoveredFunctions.filter { it !in called } else discoveredFunctions
                prepareFunctions(functions, facts, callSite, owner, authorAccess)?.let { return it }
            }
            signatures.values.forEach { signature ->
                val declarationValues = values(signature.function, signature.lexical, includeLocals = false)
                rawValues[signature.function] = declarationValues
                declarationValues.values.forEach { value ->
                    valueTypes[value] = prepareType(value.type, signature.lexical, signature.source)
                    DeclarationLexicalSnapshot.rawValue(value)?.let {
                        it.descriptors.values.forEach(::collect)
                        it.constant?.let(::constant)
                    }
                }
                (signature.readonly + signature.normal).forEach { formal ->
                    defaultFact(signature, formal)?.let {
                        collect(it.type)
                        it.descriptors.values.forEach(::collect)
                        it.constant?.let(::constant)
                    }
                }
            }
            completion?.let { return RoundResult.Completion(it) }
            val macroDiagnostics = arrayListOf<top.mcfpp.io.info.MacroLoadingDiagnostic>()
            val macroSnapshot = MacroLoadingSnapshot.prepare(
                macroNamespaces(),
                types.values,
                preparedNamespaceTypeNames = macroTypeNames,
                report = { macroDiagnostics.add(it) },
                fields = fieldDeclarations.values,
                fieldViews = fieldViews,
                constantSchemas = schemas,
                callbacks = signatures.values.filter { it.function.hasBody }.map { signature ->
                    SourceCallbackSignature(
                        signature.normal.map { SourceCallbackParameter(SourceCallbackType.Declared(it.type.typeId), ParamMode.VALUE) },
                        SourceCallbackType.Declared(signature.returnType.typeId)
                    )
                } + nativeDirectory?.callbacks.orEmpty()
            )
            if (macroDiagnostics.isNotEmpty()) throw SourceBindingFailure(source, macroDiagnostics.joinToString { it.message })
            val preparedMacros = PreparedMacroCatalog(macroSnapshot, nativeDirectory)
            val catalog = macroSnapshot.types
            val universe = (catalog.ref(MCFPPPrivateType.Void) as ImmutableTypeRef<*>).key.universe
            val declarations = linkedMapOf<FunctionKey, Function>()
            val memberCandidates = linkedMapOf<Pair<TypeId, String>, MutableList<SourceFunction>>()
            val projected = signatures.values.map { signature ->
                val projected = project(signature, catalog, universe)
                declarations[(projected.function as ImmutableFunctionRef<*>).key] = signature.function
                memberReceivers[signature.function]?.forEach { receiver ->
                    memberCandidates.getOrPut(receiver to signature.function.identifier) { arrayListOf() }.add(projected)
                }
                projected
            }
            val members = memberCandidates.mapValues { (_, values) -> immutableList(values) }
            val boundMacros = preparedMacros.namespaces.values.flatMap { it.functions }
            nativeDirectory?.validateOrdinary(signatures.values, catalog, boundMacros)
            val macros = boundMacros.map {
                val macro = it
                SourceFunction(
                    it.identifier,
                    it.reference,
                    preparedCallSites = callNames.filterValues { name ->
                        name == macro.identifier || name == macro.reference.name()
                    }.keys
                )
            }
            val allFunctions = projected + macros
            val projectedBySignature = signatures.keys.zip(projected).toMap()
            val callerFunctions = availableFunctions(caller, lexical, allFunctions, declarations)
            val callerFields = fields(caller, catalog)
            val binding = environment(
                caller, lexical, callerFunctions, callerFields, catalog,
                sourceBuffer(syntax), source.file, callerValues,
                callSignatures.mapValues { (site, identities) ->
                    identities.map { projectedBySignature.getValue(it) } + macros.filter {
                        val name = callNames.getValue(site)
                        (name == it.name || name == it.function.name()) && it in callerFunctions
                    }
                },
                members
            )
            val environments = linkedMapOf<FunctionKey, BindingEnvironment>()
            signatures.values.forEach { signature ->
                val key = declarationKey(signature, universe)
                if (key !in environments) {
                    val function = projected.first { declarations[(it.function as ImmutableFunctionRef<*>).key] === signature.function }
                    val body = function.sourceBody
                    environments[key] = environment(
                        signature.function,
                        signature.lexical,
                        availableFunctions(signature.function, signature.lexical, allFunctions, declarations),
                        fields(signature.function, catalog),
                        catalog,
                        body?.sourceBuffer ?: signature.function.ast?.let(::sourceBuffer),
                        body?.source?.file ?: signature.function.declarationFile?.absolutePath ?: "<declaration>"
                    )
                }
            }
            val revision = DeclarationDomainStamp.captureRoots(roots + types.values + signatures.values.map { it.function }, source)
            val capabilities = callerFields.map { (requireNotNull(it.canonical) as ImmutableFieldRef<*>).key }.toSet()
            val view = declarationTarget?.let { target ->
                declarationView(target, catalog, callerFields)
            }
            return RoundResult.Complete(
                CompilerSourceEnvironment(
                    binding,
                    macroSnapshot,
                    environments,
                    declarations,
                    compilerSymbols,
                    capabilities,
                    revision,
                    view,
                    preparedMacros
                )
            )
        }

        /** All visible member declarations, including inherited slots, in stable declaration order. */
        private fun declarationMethodCandidates(target: DataTemplate): List<Function> {
            val names = linkedSetOf<String>()
            val seen = Collections.newSetFromMap(IdentityHashMap<DataTemplate, Boolean>())
            fun visit(template: DataTemplate) {
                if (!seen.add(template)) return
                names.addAll(template.scope.functions.keys)
                template.parent.filterIsInstance<DataTemplate>().forEach(::visit)
            }
            visit(target)
            val candidates = Collections.newSetFromMap(IdentityHashMap<Function, Boolean>())
            names.forEach { name ->
                target.scope.getFunctionCandidates(name, caller).forEach { function ->
                    if (!function.actualCallBody) candidates.add(function)
                }
            }
            return candidates.toList()
        }

        /**
         * Project the completed canonical member facts into the processor-facing view. The view
         * contains no Var, Function, ANTLR node or mutable compiler collection.
         */
        private fun declarationView(
            target: DataTemplate,
            catalog: SourceTypeCatalog,
            accessible: List<SourceField>
        ): TemplateView {
            val owner = catalog.ref(target.getType())
            val accessibleByKey = accessible.associateBy { (requireNotNull(it.canonical) as ImmutableFieldRef<*>).key }
            val fields = owner.fields().map { field ->
                val canonical = field as ImmutableFieldRef<*>
                val fact = accessibleByKey[canonical.key]
                    ?: catalog.sourceField(owner, field.name())
                    ?: throw SourceBindingFailure(
                        this.source,
                        "Completed field '${field.name()}' has no canonical declaration view"
                    )
                val declaration = fact.declaration
                    ?: throw SourceBindingFailure(
                        this.source,
                        "Completed field '${field.name()}' lost its declaration identity"
                    )
                val (_, access) = fieldAccess[declaration]
                    ?: throw SourceBindingFailure(
                        this.source,
                        "Completed field '${field.name()}' lost its declared owner"
                    )
                val variable = target.scope.getVar(field.name())
                ImmutableFieldDeclarationView(
                    canonical,
                    catalog.graph.type(canonical.key.owner),
                    declarationAccess(access),
                    variable?.nullable ?: false,
                    variable?.annotations?.map { annotationView(it, fact.source, catalog) }.orEmpty()
                )
            }
            val methods = declarationMethods.mapNotNull { function ->
                val functionOwner = function.owner as? DataTemplate ?: return@mapNotNull null
                val ownerRef = catalog.ref(functionOwner.getType())
                val readonly = (function as? top.mcfpp.model.Generic<*>)?.readOnlyParams.orEmpty().map {
                    ParameterView.of(it.identifier, catalog.ref(it.type), ParamMode.CONST)
                }
                @Suppress("UNCHECKED_CAST")
                val signature: FunctionSignature<*> = FunctionSignature.of(
                    function.normalParams.map {
                        ParameterView.of(it.identifier, catalog.ref(it.type), ParamMode.VALUE)
                    },
                    catalog.ref(function.returnType) as TypeRef<Any?>
                )
                ImmutableMethodView(
                    FunctionKey(
                        (ownerRef as ImmutableTypeRef<*>).key.universe,
                        "declaration-view:${function.declarationId.value}"
                    ),
                    function.identifier, ownerRef, signature, readonly,
                    declarationAccess(function.accessModifier), function.isStatic, function.isAbstract, function.isFinal,
                    function.annotations.map {
                        annotationView(it, function.ast?.let(::preparationSpan) ?: source, catalog)
                    },
                    function.ast?.let(::preparationSpan) ?: source
                )
            }
            val annotations = arrayListOf<AnnotationView>()
            annotations.addAll(target.annotations.map { annotationView(it, source, catalog) })
            annotations.addAll(target.declarationMetadata().map { projectMetadata(it, catalog) })
            processorRequest?.let { request ->
                annotations.add(
                    ImmutableAnnotationView(
                        request.registration.annotationName,
                        CompilerAnnotationFacts.constants(request.arguments, catalog),
                        request.source
                    )
                )
            }
            return ImmutableTemplateView(owner, fields, methods, annotations, source)
        }

        private fun collectMetadataType(value: ConstantValue) {
            when (value) {
                is ConstantValue.TypeValue -> {
                    val reference = value.type
                    val stable = (reference as? ImmutableTypeRef<*>)?.stableSourceId
                    val id = stable?.let(::stableTypeId)
                        ?: stable?.let { sourceId -> types.keys.firstOrNull { it.toString() == sourceId } }
                        ?: throw SourceBindingFailure(
                            source,
                            "Generated metadata type '${reference.name()}' is outside the completed declaration domain"
                        )
                    metadataTypeIds[reference] = id
                    collect(
                        MCFPPType.resolveTypeId(id)
                            ?: throw SourceBindingFailure(
                                source,
                                "Generated metadata type '$id' is outside the completed declaration domain"
                            )
                    )
                }
                is ConstantValue.Sequence -> value.elements.forEach(::collectMetadataType)
                is ConstantValue.Record -> value.fields.values.forEach(::collectMetadataType)
                else -> Unit
            }
        }

        /** Re-anchor generated type values in this preparation's canonical catalog. */
        private fun projectMetadata(view: AnnotationView, catalog: SourceTypeCatalog): AnnotationView = ImmutableAnnotationView(
            view.name(),
            view.arguments().mapValues { (_, value) ->
                projectConstant(value, catalog)
            },
            view.source()
        )

        private fun projectConstant(value: ConstantValue, catalog: SourceTypeCatalog): ConstantValue = when (value) {
            is ConstantValue.TypeValue -> ConstantValue.TypeValue(
                catalog.ref(
                    metadataTypeIds[value.type]
                        ?: throw SourceBindingFailure(
                            source,
                            "Generated metadata type '${value.type.name()}' is outside the completed declaration domain"
                        )
                )
            )
            is ConstantValue.Sequence -> ConstantValue.Sequence(value.elements.map { projectConstant(it, catalog) })
            is ConstantValue.Record -> ConstantValue.Record(
                value.fields.mapValues { (_, child) ->
                    projectConstant(child, catalog)
                }
            )
            else -> value
        }

        private fun stableTypeId(stable: String): TypeId? {
            fun body(prefix: String): String? = if (stable.startsWith(prefix) && stable.endsWith(")")) stable.substring(prefix.length, stable.length - 1)
            else null
            body("Builtin(key=")?.let { return TypeId.Builtin(it) }
            body("Opaque(declaration=")?.let { value -> value.toLongOrNull()?.let { return TypeId.Opaque(it) } }
            val declaration = body("Declaration(kind=") ?: return null
            val fields = declaration.split(", ")
            if (fields.size != 3) return null
            val kind = fields[0].removePrefix("kind=")
            val namespace = fields[1].removePrefix("namespace=")
            val name = fields[2].removePrefix("name=")
            return TypeId.Declaration(kind, namespace, name)
        }

        private fun declarationAccess(access: AccessModifier): DeclarationAccess = when (access) {
            AccessModifier.PUBLIC -> DeclarationAccess.PUBLIC
            AccessModifier.PROTECTED -> DeclarationAccess.PROTECTED
            AccessModifier.PRIVATE -> DeclarationAccess.PRIVATE
            AccessModifier.COMPILE_PRIVATE -> DeclarationAccess.COMPILE_PRIVATE
        }

        private fun annotationView(
            annotation: top.mcfpp.model.annotation.Annotation,
            source: SourceSpan,
            catalog: SourceTypeCatalog
        ): AnnotationView {
            val name = if (annotation.identifier.contains(':')) annotation.identifier
            else "${annotation.namespace}:${annotation.identifier}"
            // Positional keys preserve the source grammar: it has no named annotation arguments.
            return ImmutableAnnotationView(
                name,
                CompilerAnnotationFacts.constants(annotation.compilerArguments(), catalog),
                source
            )
        }

        private fun discover(function: Function, declaration: DeclarationLexicalSnapshot) {
            if (!discovered.add(function)) return
            val body = function.bodyHandle?.project()?.context
            fun structured(node: ParseTree) {
                if (node is ParserRuleContext) {
                    val origin = top.mcfpp.model.function.StructuredBodyContexts.origin(node)
                    if (origin?.node is top.mcfpp.library.syntax.Expression.Name) origin.nameUse?.let { use ->
                        origin.boundName?.name?.let { name -> structuredUses.getOrPut(function) { linkedMapOf() }[use] = name }
                    }
                    if (origin?.node is top.mcfpp.library.syntax.Expression.Name && origin.nameUse != null) {
                        (top.mcfpp.analysis.StructuredBindings.name(node, function) as? top.mcfpp.analysis.StructuredNameBinding.Type)?.let { collect(it.type) }
                        val found = top.mcfpp.analysis.StructuredBindings.functions(node, function).orEmpty()
                        val own = structuredFunctions.getOrPut(function) {
                            Collections.newSetFromMap(IdentityHashMap<Function, Boolean>())
                        }
                        val added = found.filter { own.add(it) }
                        if (added.isNotEmpty()) requestedBindings.add(BindingRequest(added, emptyList(), function,
                            authorAccess = top.mcfpp.analysis.StructuredAuthorAccess.capture(node, function)))
                    }
                    if (node is P.TypeContext) top.mcfpp.analysis.StructuredBindings.type(node, function)?.let {
                        lexicalTypeNames.getOrPut(function) { linkedMapOf() }[node.text] = it
                        collect(it)
                    }
                    top.mcfpp.analysis.StructuredBindings.constant(node, function)?.let { fact ->
                        collect(fact.type); fact.descriptors.values.forEach(::collect); fact.constant?.let(::constant)
                    }
                }
                for (index in 0 until node.childCount) structured(node.getChild(index))
            }
            body?.let(::structured)
            val visible = visibleNamespaces(declaration)
            visible.forEach { namespace -> if (namespaces.none { it === namespace }) namespaces.add(namespace) }
            roots.addAll(visible)
            val names = linkedSetOf<String>()
            visible.forEach { namespace ->
                val scopes = listOf(namespace.scope) + namespace.scope.journalFileFields
                scopes.forEach { scope -> names.addAll(scope.functions.keys) }
                namespace.macroDeclarations().forEach { descriptor -> names.add(descriptor.identifier) }
            }
            (function.owner as? DataTemplate)?.scope?.functions?.keys?.let(names::addAll)
            function.declarationFile?.field?.functions?.keys?.let(names::addAll)
            scopeNames.addAll(names)
            names.forEach { name -> requestedBindings.add(BindingRequest(visibleFunctions(name, declaration, function), emptyList(), function)) }
            val aliases = lexicalTypeNames.getOrPut(function) { linkedMapOf() }
            val scopes = listOfNotNull(function.declarationFile?.field) + visible.flatMap { listOf(it.scope) + it.scope.journalFileFields }
            scopes.flatMap { it.journalTypeAliases.keys }.distinct().forEach { name ->
                // Resolve spelling through the original lexical relationship, including shadowing.
                try {
                    aliases[name] = prepareType(UnresolvedType(name), declaration, source)
                } catch (failure: PreparationFactFailure) {
                    if (failure.code != PreparationFactCode.UNKNOWN_NAME) throw failure
                }
            }
        }

        /** Merge local/library metadata without mutating the registered namespaces. */
        private fun macroNamespaces(): List<Namespace> = namespaces.groupBy { it.identifier }.map { (name, originals) ->
            Namespace(name).apply {
                journalMacroDeclarations.addAll(originals.flatMap { it.macroDeclarations() }.distinct())
                originals.flatMap { listOf(it.scope) + it.scope.journalFileFields }.forEach { original ->
                    original.functions.forEach { (identifier, overloads) ->
                        scope.functions.getOrPut(identifier) { arrayListOf() }.addAll(
                            overloads.filter { function ->
                                scope.functions[identifier].orEmpty().none { it === function }
                            }
                        )
                    }
                }
            }
        }

        private fun prepareType(type: MCFPPType, declaration: DeclarationLexicalSnapshot, site: SourceSpan): MCFPPType {
            val session = SignaturePreparationSession()
            return completing(session, declaration, site) {
                val prepared = DeclarationBinding(DeclarationPreparationContext(declaration, headers = session))
                    .preparedDeclaredType(type, site)
                requireHeaders(session, site)
                collect(prepared)
                prepared
            }
        }

        private fun requireHeaders(session: SignaturePreparationSession, site: SourceSpan) {
            session.stagedHeaderBatch().headers.firstOrNull { it.phase != HeaderPhase.COMPLETE }?.let { header ->
                throw PreparationFactFailure(
                    PreparationFactCode.UNPREPARED_TYPE,
                    site,
                    "Prepared type requires canonical declaration materialization",
                    CompletionTarget.Header(header)
                )
            }
        }

        private inline fun <T> completing(
            session: SignaturePreparationSession,
            declaration: DeclarationLexicalSnapshot,
            site: SourceSpan,
            action: () -> T
        ): T = try {
            action()
        } catch (failure: PreparationFactFailure) {
            val target = failure.completion ?: throw failure
            val domain = DeclarationDomainStamp.captureRoots(roots + declaration.domainRoots, site)
            val request = DeclarationCompletionRequest(
                CallableDeclarationId(caller.declarationId), caller,
                declaration, site, failure.message.orEmpty(), emptyList(), emptyList(), true,
                SignatureDeclarationStamp(declaration.namespace, caller.identifier, declaration.imports, emptyList(), domain), target
            )
            val staged = session.stagedHeaderBatch()
            completion = PreparedHeaderBatch(
                listOf(request.declaration),
                listOf(request),
                staged.headers,
                domains = listOf(domain),
                productionOptions = session.generationOptions
            )
            throw CompletionRequired(checkNotNull(completion))
        }

        private fun collect(type: MCFPPType) {
            if (type is MCFPPTypeAliasType) {
                collect(type.cachedTarget ?: throw SourceBindingFailure(source, "Unresolved type alias requires declaration completion"))
                return
            }
            if (types.putIfAbsent(type.typeId, type) != null) return
            roots.add(type)
            when (type) {
                is MCFPPTypeWithGeneric -> type.generic.forEach(::collect)
                is MCFPPUnionType -> type.types.forEach(::collect)
            }
            type.parentType.forEach(::collect)
            if (type is top.mcfpp.core.lang.context.MCFPPExecutionContextType) {
                val owner = top.mcfpp.core.lang.context.ContextTypes.ExecutionContext.typeId
                for (name in listOf("executor", "pos")) {
                    val member = requireNotNull(top.mcfpp.core.lang.context.ContextTypes.memberType(type, name))
                    collect(member)
                    val identity = SourceFieldId(owner, "context-field:$name")
                    val declaration = SourceFieldDeclaration(identity, owner, name, member.typeId, false, source)
                    val previous = fieldDeclarations.putIfAbsent(identity, declaration)
                    require(previous == null || previous == declaration) { "Conflicting context field declaration" }
                    fieldViews.add(SourceFieldView(type.typeId, identity))
                    fieldAccess[identity] = null to AccessModifier.PUBLIC
                }
            }
            if (type is MCFPPDataTemplateType) {
                val template = type.checkpointPrivateState().elements.single()
                if (template is GenericDataTemplate || !template.hasCompletedDeclaration)
                    throw SourceBindingFailure(source, "Source fields require a completed concrete template declaration")
                roots.add(template)
                val readonly = (template as? CompiledGenericDataTemplate)?.originTemplate?.readOnlyParams?.map { it.identifier }.orEmpty()
                template.scope.allVars.filter { it.identifier !in readonly }.forEach { value ->
                    val property = template.scope.getProperty(value.identifier)
                    if (property?.accessor != null && property.accessor !is SimpleAccessor) return@forEach
                    if (property?.mutator != null && property.mutator !is SimpleMutator) return@forEach
                    val origin = template.scope.fieldOrigins[value.identifier]?.singleOrNull()?.owner as? DataTemplate
                        ?: value.declaredParentTemplate ?: template
                    val ownerType = origin.getType()
                    collect(ownerType)
                    val fieldType = prepareType(
                        value.type,
                        DeclarationLexicalSnapshot.capture(
                            template.declarationFile,
                            template.declarationEnvironment?.namespace ?: template.namespace,
                            template.declarationEnvironment?.imports.orEmpty()
                        ),
                        source
                    )
                    val identity = SourceFieldId(origin.inheritanceIdentity, "field:${value.identifier}")
                    val declaration = SourceFieldDeclaration(
                        identity,
                        origin.inheritanceIdentity,
                        value.identifier,
                        fieldType.typeId,
                        !value.isConst && !value.isFinal && (property == null || property.mutator is SimpleMutator),
                        source
                    )
                    val previous = fieldDeclarations.putIfAbsent(identity, declaration)
                    require(previous == null || previous == declaration) { "Conflicting canonical source field facts" }
                    fieldViews.add(SourceFieldView(type.typeId, identity))
                    fieldAccess[identity] = origin to (property?.accessModifier ?: value.accessModifier)
                }
            }
        }

        private fun constant(value: CompilerValue) {
            when (value) {
                is CompilerValue.Typed -> {
                    val payload = value.payload
                    if (payload is CompilerValue.Record && payload.fields.values.all { it is CompilerValue.Typed }) {
                        val schema = SourceConstantSchema.Record(payload.fields.mapValues { (it.value as CompilerValue.Typed).type })
                        val old = schemas[value.type]
                        require(old == null || old is SourceConstantSchema.Record && old.fields == schema.fields) {
                            "Closed values of one type require a consistent record schema"
                        }
                        schemas[value.type] = schema
                    }
                    constant(payload)
                }
                is CompilerValue.Sequence -> value.elements.forEach(::constant)
                is CompilerValue.Record -> value.fields.values.forEach(::constant)
                else -> Unit
            }
        }

        private fun defaultFact(signature: PreparedCompilerSignature, formal: PreparedCompilerFormal): DeclarationBinding.Bound? {
            if (!formal.hasDefault) return null
            formal.defaultValue?.let { proof ->
                val actual = (proof as? CompilerValue.Typed)?.type?.let { formal.defaultTypes[it] } ?: formal.type
                return DeclarationBinding.Bound(actual, proof, descriptors = formal.defaultTypes)
            }
            if (formal.readonly) return signature.readonlyArguments[signature.readonly.indexOf(formal)].bound
            val syntax = formal.defaultSyntax ?: throw SourceBindingFailure(
                signature.source,
                "Declared default has neither source syntax nor a closed compiler value"
            )
            val fact = DeclarationBinding(DeclarationPreparationContext(signature.lexical)).value(syntax)
            if (fact.constant == null) throw SourceBindingFailure(
                preparationSpan(syntax),
                "Dynamic composite declaration defaults require source value projection"
            )
            return fact
        }

        private fun project(signature: PreparedCompilerSignature, catalog: SourceTypeCatalog, universe: Long): SourceFunction {
            val declaration = declarationKey(signature, universe)
            // These keys identify immutable targets only within this catalog, never generated resources.
            // Full structural identity distinguishes arbitrary text payloads and owner specializations.
            val key = preparedKeys.getOrPut(signature.identity) { FunctionKey(universe, "prepared:${preparedKeys.size + 1}") }
            val reference = ImmutableFunctionRef(
                key,
                "${signature.function.namespace}:${signature.function.identifier}",
                FunctionSignature.of(
                    signature.normal.map { ImmutableParameterView(it.name, catalog.ref(it.type), ParamMode.VALUE) },
                    catalog.ref(signature.returnType)
                ),
                if (signature.function is NativeFunction) FunctionKind.NATIVE else FunctionKind.ORDINARY,
                readonlyParameters = signature.readonly.map { ImmutableParameterView(it.name, catalog.ref(it.type), ParamMode.CONST) }
            )
            fun formal(value: PreparedCompilerFormal) = SourceSymbol(value.name, catalog.ref(value.type), symbol(value.identity))
            fun defaults(values: List<PreparedCompilerFormal>): Map<Int, BoundExpr> = values.mapIndexedNotNull { index, value ->
                defaultFact(signature, value)?.let {
                    index to SourceDefaultBinder().typedLiteral(
                        it,
                        catalog,
                        value.defaultSyntax?.let(::preparationSpan) ?: signature.source
                    )
                }
            }.toMap()
            val readonly = signature.readonlyArguments.map { fact -> SourceDefaultBinder().typedLiteral(fact.bound, catalog, fact.source) }
            val body = (signature.function.bodyHandle?.project()?.context ?: signature.function.ast)?.let {
                val structured = StructuredCallbackSyntax.capture(it)
                SourceFunctionBody(sourceSlice(it), preparationSpan(it), structured, sourceBuffer(it),
                    signature.function.bodyHandle != null || structured != null)
            }
            return SourceFunction(
                signature.function.identifier, reference,
                readonlyParameters = signature.readonly.map { SourceParameter(catalog.ref(it.type), ParamMode.CONST) },
                readonlyDefaultExpressions = defaults(signature.readonly), normalDefaultExpressions = defaults(signature.normal),
                sourceBody = body, readonlySymbols = signature.readonly.map(::formal), normalSymbols = signature.normal.map(::formal),
                preparedReadonly = SourcePreparedReadonly(declaration, readonly.map { TypedConstant(it.type, requireNotNull(it.constant), it.source) }, source),
                preparedCallSites = callSignatures.filterValues { signature.identity in it }.keys,
                compilerDeclarationId = signature.function.declarationId.value
            )
        }

        private fun declarationKey(signature: PreparedCompilerSignature, universe: Long): FunctionKey {
            val owner = signature.owner?.let { ownerKeys.getOrPut(it) { ownerKeys.size + 1 }.toString() } ?: "global"
            return FunctionKey(universe, "compiler:${signature.declaration.symbol.value}:owner:$owner")
        }

        private fun fields(function: Function, catalog: SourceTypeCatalog): List<SourceField> = fieldViews.mapNotNull { view ->
            val (owner, access) = fieldAccess.getValue(view.declaration)
            if (!accessible(function, owner, access)) null else catalog.sourceField(
                catalog.ref(view.receiver),
                fieldDeclarations.getValue(view.declaration).name
            )
        }

        private fun environment(
            function: Function,
            declaration: DeclarationLexicalSnapshot,
            functions: List<SourceFunction>,
            fields: List<SourceField>,
            catalog: SourceTypeCatalog,
            text: String?,
            file: String,
            values: Map<String, Var<*>> = rawValues[function].orEmpty(),
            calls: Map<SourceSpan, List<SourceFunction>>? = null,
            members: Map<Pair<TypeId, String>, List<SourceFunction>> = emptyMap()
        ): BindingEnvironment {
            val symbols = values.map { (name, value) ->
                val id = symbol(value.symbol ?: value)
                compilerSymbols[id] = value
                val raw = DeclarationLexicalSnapshot.rawValue(value)
                // Runtime callables and owned reference descriptions retain their physical
                // payload and metadata; a closed record is not a source constant schema.
                val constant = if (value.type is MCFPPFunctionType || referencePayload(value)) null
                    else raw?.constant?.let { SourceDefaultBinder().typedLiteral(raw, catalog, source).constant }
                SourceSymbol(name, catalog.ref(valueTypes[value] ?: value.type), id,
                    !value.isConst && !value.isFinal && value.symbol?.readonly != true,
                    constant.takeUnless { value.isStatic }, global = value.isStatic)
            } + lexicalTypeNames[function].orEmpty().filterKeys { it !in values }.map { (name, type) ->
                SourceSymbol(
                    name,
                    catalog.ref(MCFPPConcreteType.Type),
                    symbol(type),
                    constant = ConstantValue.TypeValue(catalog.ref(type))
                )
            }
            val names = linkedMapOf<String, List<SourceFunction>>()
            functions.forEach { function ->
                for (name in listOf(function.name, function.function.name()).distinct())
                    names[name] = names[name].orEmpty() + function
            }
            return BindingEnvironment(
                catalog, symbols, functions, fields, text, sourceFile = file, functionNames = names,
                callCandidates = calls, memberDeclarations = members,
                structuredSymbols = structuredUses[function].orEmpty().mapNotNull { (use, name) ->
                    symbols.firstOrNull { it.name == name }?.let { use to it }
                }.toMap()
            )
        }

        private fun referencePayload(value: Var<*>): Boolean {
            fun contains(type: MCFPPType, seen: Set<TypeId> = emptySet()): Boolean {
                if (type is MCFPPReferenceType) return true
                if (type.typeId in seen) return false
                val next = seen + type.typeId
                return when (type) {
                    is MCFPPDataTemplateType -> type.instanceFields.any { contains(it.type, next) }
                    is MCFPPTypeWithGeneric -> type.generic.any { contains(it, next) }
                    is MCFPPUnionType -> type.types.any { contains(it, next) }
                    else -> false
                }
            }
            if (contains(valueTypes[value] ?: value.type)) return true
            val binding = value.storageBinding ?: return false
            return binding.data.facts.entries().any { (place, fact) ->
                place.root == binding.place.root && place.path.take(binding.place.path.size) == binding.place.path &&
                    fact.reference != null
            }
        }

        /** Read-only preview used only to discover the actual receiver type before member preparation. */
        private fun previewEnvironment(): BindingEnvironment {
            val catalog = SourceTypeCatalog.snapshot(types.values, fieldDeclarations.values, fieldViews, schemas)
            val symbols = callerValues.map { (name, value) ->
                SourceSymbol(
                    name,
                    catalog.ref(valueTypes[value] ?: value.type),
                    symbol(value),
                    !value.isConst && !value.isFinal && value.symbol?.readonly != true
                )
            }
            val fields = fieldViews.mapNotNull { view ->
                catalog.sourceField(catalog.ref(view.receiver), fieldDeclarations.getValue(view.declaration).name)
            }
            return BindingEnvironment(
                catalog,
                symbols,
                emptyList(),
                fields,
                sourceText = sourceBuffer(syntax),
                sourceFile = source.file
            )
        }

        private fun availableFunctions(
            function: Function,
            declaration: DeclarationLexicalSnapshot,
            all: List<SourceFunction>,
            handles: Map<FunctionKey, Function>
        ): List<SourceFunction> {
            val visible = Collections.newSetFromMap(IdentityHashMap<Function, Boolean>())
            visible.add(function)
            visible.addAll(structuredFunctions[function].orEmpty())
            scopeNames.forEach { name -> visible.addAll(visibleFunctions(name, declaration, function)) }
            return all.filter { source ->
                val original = handles[(source.function as ImmutableFunctionRef<*>).key]
                if (original != null) original in visible
                else visibleNamespaces(declaration).any { namespace ->
                    source.function.name() == "${namespace.identifier}:${source.name}" &&
                        macroVisible(namespace.identifier, source.name, declaration)
                }
            }
        }

        private fun macroVisible(namespace: String, name: String, declaration: DeclarationLexicalSnapshot): Boolean {
            if (namespace == declaration.namespace) return true
            val originalFile = declaration.domainRoots.filterIsInstance<MCFPPFile>().firstOrNull()
            if (originalFile?.field?.importedNamespaceField?.containsKey(namespace) == true) return true
            val selected = originalFile?.field?.importField?.get(namespace)
            if (selected != null && name in selected.functions) return true
            return declaration.imports[namespace]?.let { it == "*" || it == name } == true
        }

        private fun values(function: Function, declaration: DeclarationLexicalSnapshot, includeLocals: Boolean): Map<String, Var<*>> {
            val values = linkedMapOf<String, Var<*>>()
            val seen = Collections.newSetFromMap(IdentityHashMap<IScope, Boolean>())
            fun visit(scope: IScope?) {
                if (scope == null || !seen.add(scope)) return
                when (scope) {
                    is FunctionScope -> {
                        scope.vars.forEach { (name, value) -> values.putIfAbsent(name, value) }
                        scope.parent.firstOrNull()?.let(::visit)
                    }
                    is CompoundDataScope -> scope.allVars.forEach { value ->
                        val property = scope.getProperty(value.identifier)
                        if (property?.accessor != null && property.accessor !is SimpleAccessor) return@forEach
                        val owner = value.declaredParentTemplate
                        if (owner == null || accessible(function, owner, value.accessModifier)) values.putIfAbsent(value.identifier, value)
                    }
                    is SimpleLibScope -> {
                        scope.vars.forEach { (name, value) -> values.putIfAbsent(name, value) }
                        if (scope is NamespaceScope) scope.journalFileFields.forEach(::visit)
                    }
                }
            }
            visit(if (includeLocals) function.scope else function.declarationFile?.field)
            function.declarationFile?.field?.let(::visit)
            val own = visibleNamespaces(declaration).filter { it.identifier == declaration.namespace }
            own.forEach { visit(it.scope) }
            function.declarationFile?.field?.importField?.values?.forEach(::visit)
            function.declarationFile?.field?.importedNamespaceField?.values?.forEach(::visit)
            for ((namespace, member) in declaration.imports) {
                visibleNamespaces(declaration).filter { it.identifier == namespace }.forEach { owner ->
                    val imported = listOf(owner.scope) + owner.scope.journalFileFields
                    imported.forEach { scope ->
                        scope.vars.forEach { (name, value) ->
                            if (member == "*" || member == name) values.putIfAbsent(name, value)
                        }
                    }
                }
            }
            if (includeLocals) file?.field?.let(::visit)
            return values
        }
    }

    private class CompletionRequired(val batch: PreparedHeaderBatch) : RuntimeException()
    private data class BindingRequest(
        val functions: List<Function>,
        val facts: List<PreparedExpressionFact>,
        val caller: Function,
        val source: SourceSpan? = null,
        val authorAccess: top.mcfpp.analysis.StructuredAuthorAccess? = null
    )
    private data class MemberRequest(
        val syntax: P.VarWithSelectorContext,
        val name: String,
        val facts: List<PreparedExpressionFact>,
        val site: SourceSpan
    )

    /** The final selector of a member expression is the only supported member-call position. */
    private fun memberSelector(call: P.FunctionCallContext): P.VarWithSelectorContext? {
        val variable = call.parent as? P.VarContext ?: return null
        if (variable.functionCall() !== call) return null
        val selector = variable.parent as? P.SelectorContext ?: return null
        val syntax = selector.parent as? P.VarWithSelectorContext ?: return null
        return syntax.takeIf { it.selector().lastOrNull() === selector }
    }

    private fun visibleNamespaces(lexical: DeclarationLexicalSnapshot): List<Namespace> {
        val names = linkedSetOf(lexical.namespace)
        names.addAll(lexical.imports.keys)
        val sourceFile = lexical.domainRoots.filterIsInstance<MCFPPFile>().firstOrNull()
        sourceFile?.field?.importField?.values?.forEach { names.add(it.identifier) }
        sourceFile?.field?.importedNamespaceField?.values?.forEach { names.add(it.identifier) }
        names.addAll(GlobalScope.stdNamespaces.keys)
        return names.flatMap { name ->
            listOfNotNull(
                GlobalScope.localNamespaces[name],
                GlobalScope.importedLibNamespaces[name],
                GlobalScope.libNamespaces[name],
                GlobalScope.stdNamespaces[name]
            )
        }.distinct()
    }

    private fun visibleFunctions(name: String, lexical: DeclarationLexicalSnapshot, function: Function): List<Function> {
        val (namespace, identifier) = name.splitNamespaceID()
        val members = if (namespace == null) (function.owner as? DataTemplate)?.scope?.functions?.get(identifier).orEmpty() else emptyList()
        val candidates = members + lexical.functions(namespace, identifier)
        return candidates.filter { candidate ->
            !candidate.actualCallBody &&
                accessible(
                    function,
                    candidate.owner as? DataTemplate,
                    candidate.accessModifier
                )
        }.distinctBy { it.declarationId }
    }

    private fun accessible(function: Function, owner: DataTemplate?, access: AccessModifier): Boolean = access == AccessModifier.PUBLIC || owner != null && function.accessTo(owner) >= access

    private fun throwDiagnostics(diagnostics: List<PreparationDiagnostic>): Nothing {
        val diagnostic = diagnostics.first()
        throw PreparationFactFailure(diagnostic.code, diagnostic.source, diagnostics.joinToString { it.message })
    }
    private fun sourceBuffer(syntax: ParserRuleContext): String? = syntax.start.inputStream?.let {
        if (it.size() == 0) "" else it.getText(Interval(0, it.size() - 1))
    }
    private fun sourceSlice(syntax: ParserRuleContext): String = syntax.start.inputStream?.getText(
        Interval(syntax.start.startIndex, syntax.stop.stopIndex)
    )
        ?: throw SourceBindingFailure(preparationSpan(syntax), "A source function requires its original body buffer")

    private companion object {
        val baseline = listOf(
            MCFPPBaseType.Int, MCFPPBaseType.Bool, MCFPPBaseType.Float, MCFPPBaseType.String,
            MCFPPBaseType.Any, MCFPPBaseType.Object, MCFPPConcreteType.Type, MCFPPPrivateType.Void,
            MCFPPPrivateType.Null, MCFPPNBTType.NBT, MCFPPNBTType.Double
        )
    }
}

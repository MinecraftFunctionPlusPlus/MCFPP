package top.mcfpp.analysis

import org.antlr.v4.runtime.ParserRuleContext
import top.mcfpp.io.MCFPPFile
import top.mcfpp.mni.api.SourceSpan
import top.mcfpp.model.Generic
import top.mcfpp.model.compound.DataTemplate
import top.mcfpp.model.compound.GenericDataTemplate
import top.mcfpp.model.compound.GenericObjectDataTemplate
import top.mcfpp.model.compound.ObjectDataTemplate
import top.mcfpp.model.compound.RawPreparationFact
import top.mcfpp.model.function.Function
import top.mcfpp.model.function.NativeFunction
import top.mcfpp.model.scope.*
import top.mcfpp.type.*
import java.util.Collections
import top.mcfpp.antlr.mcfppParser as Parser

internal enum class PreparationFactCode { UNKNOWN_NAME, UNPREPARED_TYPE, FORMAL_DEPENDENCY, NON_CLOSED_VALUE, UNSUPPORTED_EXPRESSION }

internal class PreparationFactFailure(
    val code: PreparationFactCode,
    val source: SourceSpan,
    message: String,
    val completion: CompletionTarget? = null
) : RuntimeException(message)

internal fun preparationSpan(site: ParserRuleContext): SourceSpan {
    val first = DeclarationSyntaxToken.read(site.start)
    val last = DeclarationSyntaxToken.read(site.stop ?: site.start)
    val start = first.start.coerceAtLeast(0)
    val lines = last.spelling().split('\n')
    return SourceSpan(
        first.name.takeIf { it.isNotBlank() } ?: "<declaration>",
        start,
        (last.stop + 1).coerceAtLeast(start),
        first.line.coerceAtLeast(1),
        first.column + 1,
        last.line.coerceAtLeast(first.line) + lines.size - 1,
        if (lines.size == 1) last.column + lines.single().length + 1 else lines.last().length + 1
    )
}

/** Shared terminal/boundary admission. Reading a span never calls a source or stream getter. */
private class DeclarationSyntaxToken private constructor(
    val token: org.antlr.v4.runtime.CommonToken,
    val pair: org.antlr.v4.runtime.misc.Pair<*, *>,
    val lexer: Any?,
    val stream: Any?,
    val name: String,
    val start: Int,
    val stop: Int,
    val line: Int,
    val column: Int,
    private val text: String?,
    private val buffer: Any?,
    private val size: Int
) {
    fun spelling(): String {
        text?.let { return it }
        if (start < 0 || stop < start || start >= size) return ""
        return buildString {
            for (index in start..minOf(stop, size - 1)) when (val data = buffer) {
                is ByteArray -> append((data[index].toInt() and 255).toChar())
                is CharArray -> append(data[index])
                is IntArray -> appendCodePoint(data[index])
            }
        }
    }
    companion object {
        private fun slot(owner: Any, name: String): Any? {
            var type: Class<*>? = owner.javaClass
            while (type != null) {
                val field = type.declaredFields.firstOrNull { it.name == name && !java.lang.reflect.Modifier.isStatic(it.modifiers) }
                if (field != null) {
                    check(field.trySetAccessible()) { "Cannot read syntax backing slot '$name'" }
                    return field.get(owner)
                }
                type = type.superclass
            }
            error("Missing syntax backing slot '$name'")
        }
        fun read(value: org.antlr.v4.runtime.Token?): DeclarationSyntaxToken {
            check(value?.javaClass === org.antlr.v4.runtime.CommonToken::class.java) { "Unsupported syntax token" }
            val token = value as org.antlr.v4.runtime.CommonToken
            val source = slot(token, "source")
            check(source?.javaClass === org.antlr.v4.runtime.misc.Pair::class.java) { "Unsupported token source pair" }
            val pair = source as org.antlr.v4.runtime.misc.Pair<*, *>
            val lexer = pair.a
            check(lexer == null || lexer.javaClass === top.mcfpp.antlr.mcfppLexer::class.java) { "Unsupported declaration token source" }
            val stream = pair.b
            // Lexer.getSourceName() reads _input, which need not equal the token's source pair.
            check(lexer == null || slot(lexer, "_input") === stream) { "Mismatched declaration lexer input" }
            var name = "<declaration>"
            var buffer: Any? = null
            var size = 0
            if (stream != null) {
                val shape = stream.javaClass
                val slotName = when (shape) {
                    streamClass("CodePoint8BitCharStream") -> "byteArray"
                    streamClass("CodePoint16BitCharStream") -> "charArray"
                    streamClass("CodePoint32BitCharStream") -> "intArray"
                    else -> error("Unsupported declaration character stream")
                }
                name = slot(stream, "name") as? String ?: error("Invalid character stream name")
                size = slot(stream, "size") as? Int ?: error("Invalid character stream size")
                buffer = slot(stream, slotName)
                val length = when (slotName) {
                    "byteArray" -> (buffer as? ByteArray)?.size
                    "charArray" -> (buffer as? CharArray)?.size
                    else -> (buffer as? IntArray)?.size
                }
                check(length != null && size in 0..length) { "Invalid character stream buffer" }
            }
            fun integer(name: String) = slot(token, name) as? Int ?: error("Invalid token slot '$name'")
            val text = slot(token, "text")
            check(text == null || text is String) { "Invalid token text" }
            integer("type")
            integer("channel")
            integer("index")
            return DeclarationSyntaxToken(
                token, pair, lexer, stream, name, integer("start"), integer("stop"),
                integer("line"), integer("charPositionInLine"), text as String?, buffer, size
            )
        }
        private fun streamClass(name: String): Class<*> = Class.forName(
            "org.antlr.v4.runtime.CodePointCharStream\$$name",
            false,
            DeclarationSyntaxToken::class.java.classLoader
        )
    }
}

/** Relationships and value proofs from the declaration, never from the current caller. */
internal class DeclarationLexicalSnapshot private constructor(
    val namespace: String,
    imports: Map<String, String>,
    private val unqualified: List<ScopeView>,
    private val qualified: Map<String, List<ScopeView>>,
    localTypes: Map<String, MCFPPType>,
    freeValues: Map<String, DeclarationBinding.Bound>,
    private val freeNames: Set<String>,
    formals: List<Formal>,
    internal val headerSession: SignaturePreparationSession?,
    private val referencedHeads: List<TemplateHead>,
    internal val domainRoots: List<Any>
) {
    /** The relationship and header spelling are captured without consulting a type getter. */
    internal class TemplateHead(val model: DataTemplate) {
        private val supported = model.javaClass in setOf(
            DataTemplate::class.java,
            ObjectDataTemplate::class.java,
            GenericDataTemplate::class.java,
            GenericObjectDataTemplate::class.java
        )
        val namespace = if (supported) model.namespace else ""
        val name = if (supported) model.identifier else ""
        val flags = if (supported) model.isAbstract to model.isFinal else false to false
        val readonly = frozenList(
            (if (supported) model as? GenericDataTemplate else null)?.readOnlyParams.orEmpty().map {
                Formal(it.identifier, it, it.type ?: UnresolvedType(it.typeIdentifier), true)
            }
        )
        private val readonlySpellings = (if (supported) model as? GenericDataTemplate else null)?.readOnlyParams.orEmpty()
            .map { it.typeIdentifier }
        private val readonlyTypes = (if (supported) model as? GenericDataTemplate else null)?.readOnlyParams.orEmpty()
            .map { it.type }
        private val readonlyTypeGraph = TypeGraphStamp(readonly.map { it.type })
        val file = if (supported) model.declarationFile else null
        val declarationImports = frozenMap(if (supported) model.declarationEnvironment?.imports.orEmpty() else emptyMap())
        internal var declaration: DeclarationLexicalSnapshot? = null
            private set
        internal fun freezeDeclaration(value: DeclarationLexicalSnapshot) {
            check(declaration == null)
            declaration = value
        }
        fun requireCurrent(site: ParserRuleContext) {
            if (!supported ||
                model.namespace != namespace ||
                model.identifier != name ||
                (model.isAbstract to model.isFinal) != flags ||
                (model as? GenericDataTemplate)?.readOnlyParams.orEmpty().map { it.identifier } != readonly.map { it.name } ||
                (model as? GenericDataTemplate)?.readOnlyParams.orEmpty().map { it.typeIdentifier } != readonlySpellings ||
                (model as? GenericDataTemplate)?.readOnlyParams.orEmpty().withIndex().any { (index, parameter) ->
                    parameter.type !== readonlyTypes[index]
                } ||
                !readonlyTypeGraph.matches()
            )
                throw PreparationFactFailure(
                    PreparationFactCode.UNPREPARED_TYPE,
                    preparationSpan(site),
                    "Template declaration changed; prepare a fresh lexical domain"
                )
        }
    }

    /** Raw mutable syntax carriers only: never compute an identity or resolve a template slot. */
    internal class TypeGraphStamp(roots: List<MCFPPType>) {
        private class State(
            val children: List<MCFPPType> = emptyList(),
            val text: String? = null,
            val names: List<String>? = null,
            val target: Any? = null
        )
        private val states = java.util.IdentityHashMap<MCFPPType, State>()

        init {
            val pending = java.util.ArrayDeque<MCFPPType>()
            roots.forEach(pending::addLast)
            while (pending.isNotEmpty()) {
                val type = pending.removeFirst()
                if (states.containsKey(type)) continue
                val state = rawState(type)
                states[type] = state
                state.children.forEach(pending::addLast)
            }
        }

        fun matches(): Boolean = states.all { (type, before) ->
            val after = rawState(type)
            before.children.size == after.children.size &&
                before.children.indices.all {
                    before.children[it] === after.children[it]
                } &&
                before.text == after.text &&
                before.names == after.names &&
                before.target === after.target
        }

        private fun rawState(type: MCFPPType): State = when (type.javaClass) {
            MCFPPListType::class.java, MCFPPImmutableListType::class.java, MCFPPCompoundType::class.java,
            MCFPPDictType::class.java, MCFPPMapType::class.java ->
                State(children = ArrayList((type as MCFPPTypeWithGeneric).generic))
            MCFPPUnionType::class.java -> State(children = (type as MCFPPUnionType).types.toList())
            MCFPPFunctionType::class.java -> State(children = (type as MCFPPFunctionType).generic)
            MCFPPNBTRefType::class.java -> State(children = listOf((type as MCFPPNBTRefType).valueType))
            MCFPPGenericParamType::class.java -> State(text = (type as MCFPPGenericParamType).identifier)
            MCFPPEntityType::class.java -> State(names = (type as MCFPPEntityType).types?.let { ArrayList(it) })
            MCFPPDataTemplateType::class.java, MCFPPObjectDataTemplateType::class.java,
            MCFPPGenericDataTemplateType::class.java, MCFPPGenericObjectDataTemplateType::class.java ->
                State(target = (type as MCFPPDataTemplateType).checkpointPrivateState().elements.single())
            MCFPPEnumType::class.java -> State(target = (type as MCFPPEnumType).enum)
            // Immutable leaves and unresolved spelling need no getters. Unsupported classes are
            // also left opaque here; the preparation shape gate rejects them before consumption.
            else -> State()
        }
    }
    internal class Formal(
        val name: String,
        val identity: Any,
        val type: MCFPPType,
        val readonly: Boolean,
        val defaultSyntax: Parser.ValueContext? = null,
        val defaultValue: CompilerValue? = null,
        defaultTypes: Map<TypeId, MCFPPType> = emptyMap()
    ) {
        val defaultTypes: Map<TypeId, MCFPPType> = frozenMap(defaultTypes)
    }
    private class ScopeView(
        val namespace: String,
        val types: Map<String, MCFPPType>,
        val unresolved: Set<String>,
        val heads: Map<String, TemplateHead>,
        val values: Map<String, DeclarationBinding.Bound>,
        val valueNames: Set<String>,
        val functions: Map<String, List<Function>>,
        val aliases: Map<String, MCFPPTypeAliasType> = emptyMap()
    )
    val imports: Map<String, String> = frozenMap(imports)
    val localTypes: Map<String, MCFPPType> = frozenMap(localTypes)
    val freeValues: Map<String, DeclarationBinding.Bound> = frozenMap(freeValues.mapValues { freeze(it.value) })
    val formals: List<Formal> = frozenList(formals)

    fun value(name: String, site: ParserRuleContext): DeclarationBinding.Bound? {
        fun missing(): Nothing = throw PreparationFactFailure(
            PreparationFactCode.NON_CLOSED_VALUE,
            preparationSpan(site),
            "Declaration variable '$name' has no prepared value fact"
        )
        if (name in freeNames) return freeValues[name] ?: missing()
        for (scope in unqualified) if (name in scope.valueNames) return scope.values[name] ?: missing()
        return null
    }

    fun functions(namespace: String?, name: String): List<Function> {
        val scopes = if (namespace == null) unqualified else qualified[namespace].orEmpty()
        // Namespace views already contain the complete namespace overload collection.
        return if (namespace != null) scopes.firstNotNullOfOrNull { it.functions[name]?.takeIf { it.isNotEmpty() } }.orEmpty()
        else scopes.groupBy { it.namespace }.values.flatMap { sameNamespace ->
            sameNamespace.firstNotNullOfOrNull { it.functions[name]?.takeIf { it.isNotEmpty() } }.orEmpty()
        }
    }

    fun type(namespace: String?, name: String, site: ParserRuleContext): MCFPPType? {
        if (namespace == null) localTypes[name]?.let { return it }
        for (scope in if (namespace == null) unqualified else qualified[namespace].orEmpty()) {
            scope.types[name]?.let { return it }
            scope.aliases[name]?.takeIf { it.cachedTarget == null }?.let {
                throw PreparationFactFailure(
                    PreparationFactCode.UNPREPARED_TYPE,
                    preparationSpan(site),
                    "Declaration alias '$name' requires controlled resolution",
                    CompletionTarget.Alias(it)
                )
            }
            if (name in scope.unresolved || name in scope.heads)
                throw PreparationFactFailure(
                    PreparationFactCode.UNPREPARED_TYPE,
                    preparationSpan(site),
                    "Declaration type '$name' requires controlled declaration preparation"
                )
        }
        return null
    }

    fun template(namespace: String?, name: String, site: ParserRuleContext): TemplateHead? {
        if (namespace == null && name in localTypes) return null
        for (scope in if (namespace == null) unqualified else qualified[namespace].orEmpty()) {
            if (name in scope.types || name in scope.unresolved) return null
            scope.heads[name]?.let {
                it.requireCurrent(site)
                return it
            }
        }
        return null
    }

    fun template(model: DataTemplate, site: ParserRuleContext): TemplateHead? = ((unqualified + qualified.values.flatten()).flatMap { it.heads.values } + referencedHeads).asSequence()
        .firstOrNull { it.model === model }?.also { it.requireCurrent(site) }

    companion object {
        private class CaptureGraph {
            val heads = java.util.IdentityHashMap<DataTemplate, TemplateHead>()
            val pending = java.util.ArrayDeque<TemplateHead>()
            fun head(model: DataTemplate): TemplateHead = heads[model] ?: TemplateHead(model).also {
                heads[model] = it
                if (model.javaClass == GenericDataTemplate::class.java || model.javaClass == GenericObjectDataTemplate::class.java)
                    pending.addLast(it)
            }
        }
        fun capture(function: Function, headers: SignaturePreparationSession? = null): DeclarationLexicalSnapshot = capture(
            function.declarationFile,
            function.declarationEnvironment?.namespace ?: function.namespace,
            function.declarationEnvironment?.imports.orEmpty(),
            null,
            ((function as? Generic<*>)?.readOnlyParams ?: (function as? NativeFunction)?.readOnlyParams.orEmpty())
                .map { Formal(it.identifier, it, it.type, true, it.defaultContext, it.defaultValue, it.defaultTypes) } +
                function.normalParams.map { Formal(it.identifier, it, it.type, false, it.defaultContext, it.defaultValue, it.defaultTypes) },
            headers
        )

        /** Header readers can supply original file/owner formals without allocating a Function. */
        fun capture(
            file: MCFPPFile?,
            namespace: String,
            imports: Map<String, String> = emptyMap(),
            scope: FunctionScope? = null,
            formals: List<Formal> = emptyList(),
            headers: SignaturePreparationSession? = null
        ): DeclarationLexicalSnapshot {
            val graph = CaptureGraph()
            val result = capture(file, namespace, imports, scope, formals, headers, graph)
            // Iterative capture also freezes each prototype's own imports and formal lookup. Shared
            // head records close cycles without recursive snapshot construction or executing syntax.
            while (graph.pending.isNotEmpty()) {
                val head = graph.pending.removeFirst()
                head.freezeDeclaration(
                    capture(
                        head.file,
                        head.namespace,
                        head.declarationImports,
                        null,
                        head.readonly,
                        headers,
                        graph
                    )
                )
            }
            return result
        }

        private fun capture(
            file: MCFPPFile?,
            namespace: String,
            imports: Map<String, String>,
            scope: FunctionScope?,
            formals: List<Formal>,
            headers: SignaturePreparationSession?,
            graph: CaptureGraph
        ): DeclarationLexicalSnapshot {
            val actualNamespace = file?.namespace?.identifier ?: namespace
            val declaredImports = LinkedHashMap(file?.unsolvedImports ?: imports)
            fun declarations(name: String): List<NamespaceScope> = listOfNotNull(
                GlobalScope.localNamespaces[name]?.scope,
                GlobalScope.libNamespaces[name]?.scope,
                GlobalScope.stdNamespaces[name]?.scope
            )
            val references = java.util.Collections.newSetFromMap(java.util.IdentityHashMap<TemplateHead, Boolean>())
            val domainRoots = arrayListOf<Any>()
            file?.let(domainRoots::add)
            scope?.let(domainRoots::add)
            formals.forEach {
                domainRoots.add(it.identity)
                domainRoots.add(it.type)
                domainRoots.addAll(it.defaultTypes.values)
            }
            val visitedTypes = java.util.Collections.newSetFromMap(java.util.IdentityHashMap<MCFPPType, Boolean>())
            fun reference(type: MCFPPType) {
                if (!visitedTypes.add(type)) return
                when (type.javaClass) {
                    MCFPPDataTemplateType::class.java, MCFPPObjectDataTemplateType::class.java,
                    MCFPPGenericDataTemplateType::class.java, MCFPPGenericObjectDataTemplateType::class.java -> {
                        val target = (type as MCFPPDataTemplateType).checkpointPrivateState().elements.single()
                        val owner = if (target.javaClass == top.mcfpp.model.compound.CompiledGenericDataTemplate::class.java ||
                            target.javaClass == top.mcfpp.model.compound.CompiledGenericObjectDataTemplate::class.java
                        )
                            (target as top.mcfpp.model.compound.CompiledGenericDataTemplate).originTemplate else target
                        references.add(graph.head(owner))
                    }
                    MCFPPListType::class.java, MCFPPImmutableListType::class.java, MCFPPCompoundType::class.java,
                    MCFPPDictType::class.java, MCFPPMapType::class.java -> (type as MCFPPTypeWithGeneric).generic.forEach(::reference)
                    MCFPPUnionType::class.java -> (type as MCFPPUnionType).types.forEach(::reference)
                }
            }
            formals.forEach {
                reference(it.type)
                it.defaultTypes.values.forEach(::reference)
            }
            fun views(source: SimpleLibScope): ScopeView {
                if (source.javaClass !in setOf(SimpleLibScope::class.java, NamespaceScope::class.java, FileScope::class.java))
                    throw PreparationFactFailure(PreparationFactCode.UNSUPPORTED_EXPRESSION, SourceSpan.unknown(), "Unknown lexical scope shape")
                domainRoots.add(source)
                val scopes = listOf(source) + (source as? NamespaceScope)?.journalFileFields.orEmpty()
                val types = linkedMapOf<String, MCFPPType>()
                val unresolved = linkedSetOf<String>()
                val aliases = linkedMapOf<String, MCFPPTypeAliasType>()
                val heads = linkedMapOf<String, TemplateHead>()
                fun head(name: String, model: DataTemplate) {
                    heads.putIfAbsent(name, graph.head(model))
                }
                val values = linkedMapOf<String, DeclarationBinding.Bound>()
                val valueNames = linkedSetOf<String>()
                val functions = linkedMapOf<String, MutableList<Function>>()
                for (part in scopes) {
                    for ((name, type) in part.journalTypeAliases) {
                        if (type.javaClass == MCFPPTypeAliasType::class.java) aliases.putIfAbsent(name, type as MCFPPTypeAliasType)
                        val target = if (type is MCFPPTypeAliasType) type.cachedTarget else type
                        if (target == null) unresolved.add(name) else {
                            types.putIfAbsent(name, target)
                            reference(target)
                        }
                    }
                    part.template.forEach { (name, model) -> head(name, model) }
                    part.genericTemplate.forEach { (key, model) -> head(key.first, model) }
                    part.enums.forEach { types.putIfAbsent(it.identifier, MCFPPEnumType(it)) }
                    part.objects.filterIsInstance<DataTemplate>().forEach { head(it.identifier, it) }
                    part.vars.forEach { (name, value) ->
                        if (valueNames.add(name)) rawValue(value)?.let { values[name] = it }
                    }
                    part.functions.forEach { (name, overloads) -> functions.getOrPut(name) { arrayListOf() }.addAll(overloads) }
                }
                return ScopeView(
                    (source as? NamespaceScope)?.identifier ?: actualNamespace,
                    frozenMap(types),
                    Collections.unmodifiableSet(unresolved),
                    frozenMap(heads),
                    frozenMap(values),
                    Collections.unmodifiableSet(valueNames),
                    frozenMap(functions.mapValues { frozenList(it.value) }),
                    frozenMap(aliases)
                )
            }
            val own = declarations(actualNamespace).map(::views)
            val qualified = linkedMapOf(actualNamespace to own)
            val selected = arrayListOf<ScopeView>()
            val wildcard = arrayListOf<ScopeView>()
            // Use actual resolved relations when available. No import registration or alias resolution.
            if (file != null) {
                file.field.importField.forEach { (name, imported) ->
                    val view = views(imported)
                    selected.add(view)
                    qualified[name] = qualified[name].orEmpty() + view
                }
                file.field.importedNamespaceField.forEach { (name, _) ->
                    val imported = declarations(name).map(::views)
                    wildcard.addAll(imported)
                    qualified[name] = qualified[name].orEmpty() + imported
                }
            }
            // Persisted libraries have no file. Rebuild a view from registered declarations only.
            for ((name, member) in declaredImports) {
                if (member == "*" &&
                    file?.field?.importedNamespaceField?.containsKey(name) == true ||
                    member != "*" &&
                    file?.field?.importField?.containsKey(name) == true
                ) continue
                val imported = declarations(name).map(::views)
                if (member == "*") {
                    wildcard.addAll(imported)
                    qualified[name] = imported
                } else {
                    val filtered = imported.map { view ->
                        ScopeView(
                            view.namespace,
                            frozenMap(view.types.filterKeys { it == member }),
                            view.unresolved.filter { it == member }.toSet(),
                            frozenMap(view.heads.filterKeys { it == member }),
                            emptyMap(),
                            emptySet(),
                            emptyMap(),
                            frozenMap(view.aliases.filterKeys { it == member })
                        )
                    }
                    selected.addAll(filtered)
                    qualified[name] = qualified[name].orEmpty() + filtered
                }
            }
            val free = linkedMapOf<String, DeclarationBinding.Bound>()
            val freeNames = linkedSetOf<String>()
            val localTypes = linkedMapOf<String, MCFPPType>()
            val visited = Collections.newSetFromMap(java.util.IdentityHashMap<FunctionScope, Boolean>())
            var current = scope
            while (current != null && visited.add(current)) {
                if (current.javaClass !in setOf(FunctionScope::class.java, InternalFunctionScope::class.java, NoStackFunctionScope::class.java,
                        top.mcfpp.analysis.lambda.LambdaCaptureScope::class.java))
                    throw PreparationFactFailure(PreparationFactCode.UNSUPPORTED_EXPRESSION, SourceSpan.unknown(), "Unknown formal scope shape")
                current.types.forEach { (name, type) ->
                    localTypes.putIfAbsent(name, type)
                    reference(type)
                }
                current.allVars.forEach { value -> if (freeNames.add(value.identifier)) rawValue(value)?.let { free[value.identifier] = it } }
                current = current.parent.firstOrNull() as? FunctionScope
            }
            val localView = file?.field?.let(::views)
            localView?.let { view ->
                view.types.forEach { (name, type) -> localTypes.putIfAbsent(name, type) }
                view.valueNames.forEach { name -> if (freeNames.add(name)) view.values[name]?.let { free[name] = it } }
            }
            // File-local heads have the same priority as file-local aliases. Keep overload/value
            // lookup on the complete namespace view rather than a partial file collection.
            val localHeads = localView?.let {
                ScopeView(
                    actualNamespace,
                    emptyMap(),
                    emptySet(),
                    it.heads,
                    emptyMap(),
                    emptySet(),
                    emptyMap()
                )
            }
            if (localHeads != null) qualified[actualNamespace] = listOf(localHeads) + qualified[actualNamespace].orEmpty()
            val implicit = GlobalScope.stdNamespaces.values.map { standard ->
                val view = views(standard.scope)
                val heads = ScopeView(
                    standard.identifier,
                    emptyMap(),
                    emptySet(),
                    view.heads,
                    emptyMap(),
                    emptySet(),
                    view.functions
                )
                qualified.putIfAbsent(standard.identifier, listOf(heads))
                heads
            }
            return DeclarationLexicalSnapshot(
                actualNamespace, declaredImports, frozenList(listOfNotNull(localHeads) + own + selected + wildcard + implicit),
                frozenMap(qualified.mapValues { frozenList(it.value) }), localTypes, free,
                Collections.unmodifiableSet(freeNames), formals, headers, frozenList(references.toList()), frozenList(domainRoots)
            )
        }

        internal fun rawValue(value: top.mcfpp.core.lang.Var<*>): DeclarationBinding.Bound? {
            val binding = value.storageBinding ?: return null
            val symbol = value.symbol ?: return null
            val type = binding.data.types[symbol.declaredType] ?: return null
            val fact = binding.data.facts.read(binding.place)
            val proof = if (binding.trustConstants &&
                fact?.state == ValueState.INITIALIZED &&
                fact.type == TypeKnowledge.Exact(symbol.declaredType)
            ) (fact.value as? ValueKnowledge.Constant)?.value else null
            return freeze(DeclarationBinding.Bound(type, proof, descriptors = binding.data.types))
        }

        private fun freeze(value: DeclarationBinding.Bound) = value.copy(
            dependencies = Collections.unmodifiableSet(LinkedHashSet(value.dependencies)),
            descriptors = frozenMap(value.descriptors)
        )
        private fun <K, V> frozenMap(values: Map<K, V>): Map<K, V> = Collections.unmodifiableMap(LinkedHashMap(values))
        private fun <T> frozenList(values: List<T>): List<T> = Collections.unmodifiableList(ArrayList(values))
    }
}

/** A comparable version of the original reachable declaration domain, not a rollback action.
 * Only admitted compiler shapes are read. Selected JVM backing slots avoid resolving/open getters.
 * Raw private records supply relationships that cannot be reconstructed from public value views.
 */
internal class DeclarationDomainStamp private constructor(
    private val roots: List<Any>,
    val source: SourceSpan,
    private val facts: java.util.IdentityHashMap<Any, RawPreparationFact>,
    private val types: DeclarationLexicalSnapshot.TypeGraphStamp,
    private val hasOpaqueAnnotation: Boolean
) {
    /** Ownership of the complete original input domain, including already ready candidates.
     * Enrollment checks the original version; refresh deliberately captures the same roots after
     * controlled completion. Neither operation reconstructs a domain from incomplete requests.
     */
    class CompletionCapability private constructor(private val original: DeclarationDomainStamp) {
        fun requireCurrent() = original.requireCurrent()

        fun refresh(): DeclarationDomainStamp = captureOwnedRoots(original.roots, original.source)

        /** Compiler journals consume this capability without exporting a mutable root list. */
        internal fun enroll(accept: (Any) -> Unit) {
            requireCurrent()
            original.roots.forEach(accept)
        }

        companion object {
            internal fun checked(stamp: DeclarationDomainStamp): CompletionCapability {
                stamp.requireCurrent()
                return CompletionCapability(stamp)
            }
        }
    }

    fun forCompletion(): CompletionCapability = CompletionCapability.checked(this)

    fun requireCurrent() {
        checked(source) {
            if (hasOpaqueAnnotation) throw PreparationFactFailure(
                PreparationFactCode.UNSUPPORTED_EXPRESSION,
                source,
                "Register declaration annotations before adopting a completion batch"
            )
            val capture = Capture(source)
            val current = capture.capture(roots)
            val changed = facts.entries.firstOrNull { (owner, before) -> current[owner] != before }
            val difference = when {
                capture.hasOpaqueAnnotation -> "annotation admission changed"
                changed != null -> changed.key.javaClass.name + ": " + factDifference(changed.value, current[changed.key])
                facts.size != current.size ->
                    "reachable fact count changed from ${facts.size} to ${current.size}; added " +
                        current.keys.firstOrNull { !facts.containsKey(it) }?.javaClass?.name
                !types.matches() -> "raw type graph changed"
                else -> null
            }
            if (difference != null)
                throw PreparationFactFailure(
                    PreparationFactCode.UNPREPARED_TYPE,
                    source,
                    "Declaration domain changed; prepare a fresh completion batch ($difference)"
                )
        }
    }

    companion object {
        /** Describe only our sealed facts. Never format or hash their compiler identity leaves. */
        private fun factDifference(before: RawPreparationFact, after: RawPreparationFact?, path: String = "fact"): String {
            if (after == null) return "$path is no longer reachable"
            fun label(value: RawPreparationFact): String = when (value) {
                is RawPreparationFact.Scalar -> when (val scalar = value.value) {
                    is String -> scalar.take(120)
                    else -> scalar.toString() // Scalar admits only null/String/Boolean/Long/Int.
                }
                is RawPreparationFact.Identity -> value.value?.javaClass?.name ?: "null"
                is RawPreparationFact.Sequence -> "row"
                is RawPreparationFact.Unordered -> "unordered"
            }
            return when {
                before is RawPreparationFact.Scalar && after is RawPreparationFact.Scalar ->
                    "$path scalar '${label(before)}' -> '${label(after)}'"
                before is RawPreparationFact.Identity && after is RawPreparationFact.Identity ->
                    "$path identity ${label(before)} -> ${label(after)}"
                before is RawPreparationFact.Sequence && after is RawPreparationFact.Sequence -> {
                    val index = before.values.indices.firstOrNull { it >= after.values.size || before.values[it] != after.values[it] }
                    if (index == null) "$path length ${before.values.size} -> ${after.values.size}"
                    else factDifference(before.values[index], after.values.getOrNull(index), "$path[$index]")
                }
                before is RawPreparationFact.Unordered && after is RawPreparationFact.Unordered -> {
                    val unmatched = after.values.toMutableList()
                    val missing = before.values.filter { item ->
                        val index = unmatched.indexOf(item)
                        if (index < 0) true else {
                            unmatched.removeAt(index)
                            false
                        }
                    }
                    val first = missing.firstOrNull()
                    if (first == null) "$path added ${unmatched.size} entries"
                    else {
                        val key = (first as? RawPreparationFact.Sequence)?.values?.firstOrNull()
                        val matching = if (key == null) null else unmatched.firstOrNull {
                            it is RawPreparationFact.Sequence && it.values.firstOrNull() == key
                        }
                        factDifference(first, matching ?: unmatched.firstOrNull(), "$path{${key?.let(::label) ?: "entry"}}")
                    }
                }
                else -> "$path shape ${before.javaClass.simpleName} -> ${after.javaClass.simpleName}"
            }
        }

        fun capture(
            lexical: DeclarationLexicalSnapshot,
            function: Function,
            source: SourceSpan,
            additional: List<Any> = emptyList()
        ): DeclarationDomainStamp = captureRoots(lexical.domainRoots + function + additional, source)
        fun captureRoots(input: List<Any>, source: SourceSpan): DeclarationDomainStamp = checked(source) {
            val roots = Collections.unmodifiableList(ArrayList(input + GlobalScope))
            captureOwnedRoots(roots, source)
        }
        private fun captureOwnedRoots(roots: List<Any>, source: SourceSpan): DeclarationDomainStamp = checked(source) {
            val capture = Capture(source)
            val facts = capture.capture(roots)
            DeclarationDomainStamp(roots, source, facts, DeclarationLexicalSnapshot.TypeGraphStamp(capture.types), capture.hasOpaqueAnnotation)
        }
        /** Only expected shape/admission/reflection failures become input diagnostics. */
        private fun <T> checked(source: SourceSpan, action: () -> T): T = try {
            action()
        } catch (failure: IllegalStateException) {
            throw invalid(source, failure)
        } catch (failure: IllegalArgumentException) {
            throw invalid(source, failure)
        } catch (failure: ReflectiveOperationException) {
            throw invalid(source, failure)
        } catch (failure: SecurityException) {
            throw invalid(source, failure)
        }
        private fun invalid(source: SourceSpan, cause: Exception) = PreparationFactFailure(
            PreparationFactCode.UNSUPPORTED_EXPRESSION,
            source,
            cause.message ?: "Unsupported raw declaration shape"
        ).also {
            it.initCause(cause)
        }
    }

    private class Capture(private val source: SourceSpan) {
        private val facts = java.util.IdentityHashMap<Any, RawPreparationFact>()
        private val pending = java.util.ArrayDeque<Any>()
        val types = arrayListOf<MCFPPType>()
        var hasOpaqueAnnotation = false
            private set
        private fun fail(message: String): Nothing = throw PreparationFactFailure(
            PreparationFactCode.UNSUPPORTED_EXPRESSION,
            source,
            message
        )
        private fun ref(value: Any?): RawPreparationFact {
            if (value != null) pending.addLast(value)
            return RawPreparationFact.identity(value)
        }
        private fun raw(value: Any?): RawPreparationFact = when (value) {
            null, is String, is Boolean, is Int, is Long -> RawPreparationFact.scalar(value)
            is Byte -> RawPreparationFact.scalar(value.toInt())
            is Short -> RawPreparationFact.scalar(value.toInt())
            is Float -> RawPreparationFact.scalar(value.toRawBits())
            is Double -> RawPreparationFact.scalar(value.toRawBits())
            is kotlin.Enum<*> -> RawPreparationFact.scalar(value.name)
            is CompilerValue, is TypeId -> RawPreparationFact.closed(value)
            is SymbolId -> RawPreparationFact.scalar(value.value)
            is RawPreparationFact -> value.also(::follow)
            is ParserRuleContext -> syntax(value)
            is Class<*>, is java.lang.reflect.Method, is kotlin.reflect.KClass<*> -> RawPreparationFact.identity(value)
            else -> ref(value)
        }
        private fun follow(value: RawPreparationFact) {
            when (value) {
                is RawPreparationFact.Identity -> value.value?.let { item ->
                    // Raw records also contain private implementation records and syntax roots.
                    // Their contents are already represented by the owning validated record.
                    if (item is MCFPPType ||
                        item is top.mcfpp.model.compound.CompoundData ||
                        item is Function ||
                        item is top.mcfpp.core.lang.Var<*> ||
                        item is top.mcfpp.model.property.Property ||
                        item is top.mcfpp.model.annotation.Annotation ||
                        item is IScope
                    ) pending.addLast(item)
                }
                is RawPreparationFact.Sequence -> value.values.forEach(::follow)
                is RawPreparationFact.Unordered -> value.values.forEach(::follow)
                is RawPreparationFact.Scalar -> Unit
            }
        }
        fun capture(roots: List<Any>): java.util.IdentityHashMap<Any, RawPreparationFact> {
            pending.addAll(roots)
            while (pending.isNotEmpty()) {
                val value = pending.removeFirst()
                if (facts.containsKey(value)) continue
                if (facts.size >= 65536) fail("Declaration domain exceeds its finite preparation budget")
                // Install before following edges; recursive fields and parent graphs are legal.
                facts[value] = RawPreparationFact.scalar(null)
                facts[value] = node(value)
            }
            return facts
        }
        private fun slots(owner: Any, names: Array<out String>, required: Boolean): RawPreparationFact {
            val selection = backingShapes.get(owner.javaClass).select(names)
            if (required && !selection.complete) fail("Missing required declaration backing slot in ${owner.javaClass.name}")
            val rows = ArrayList<RawPreparationFact>(selection.slots.size)
            for (slot in selection.slots) {
                val field = slot.field
                check(field.trySetAccessible()) { "Cannot read declaration backing slot ${field.name}" }
                rows.add(RawPreparationFact.row(slot.label, raw(field.get(owner))))
            }
            return RawPreparationFact.unordered(rows)
        }
        private fun fields(owner: Any, vararg names: String) = slots(owner, names, required = false)
        private fun required(owner: Any, vararg names: String) = slots(owner, names, required = true)
        private fun slot(owner: Any, name: String): Any? {
            val field = backingShapes.get(owner.javaClass).slots.firstOrNull { it.field.name == name }?.field
                ?: fail("Missing required declaration backing slot '$name'")
            check(field.trySetAccessible()) { "Cannot read declaration backing slot '$name'" }
            return field.get(owner)
        }
        private fun relation(owner: Any, name: String, key: (Any?) -> Boolean, item: (Any?) -> Boolean) {
            val value = slot(owner, name)
            check(value is Map<*, *> && value.javaClass.name in maps) { "Unsupported declaration relation '$name'" }
            check(value.entries.all { key(it.key) && item(it.value) }) { "Invalid declaration relation role '$name'" }
        }
        private fun formals(owner: Any, name: String) {
            val value = slot(owner, name)
            check(value is List<*> && value.javaClass.name in lists) { "Unsupported declaration formal list '$name'" }
            check(value.all { it?.javaClass === top.mcfpp.model.function.FunctionParam::class.java }) {
                "Invalid declaration formal role '$name'"
            }
        }
        private fun admitted(type: Class<*>, names: Set<String>): Boolean = type.name in names &&
            Class.forName(type.name, false, DeclarationDomainStamp::class.java.classLoader) === type
        private fun supported(owner: Any, packageName: String, names: Set<String>) {
            if (!admitted(owner.javaClass, names.map { "$packageName.$it" }.toSet()))
                fail("Unsupported declaration domain shape: ${owner.javaClass.name}")
        }
        /** Generated input syntax is read through raw children/token slots, never getText(). */
        private fun syntax(root: ParserRuleContext): RawPreparationFact {
            val active = Collections.newSetFromMap(java.util.IdentityHashMap<Any, Boolean>())
            fun tokenFact(value: org.antlr.v4.runtime.Token?, optional: Boolean = false): RawPreparationFact {
                if (value == null) {
                    check(optional) { "Missing required declaration syntax token" }
                    return RawPreparationFact.scalar(null)
                }
                val checked = DeclarationSyntaxToken.read(value)
                return RawPreparationFact.row(
                    RawPreparationFact.identity(checked.token),
                    required(checked.token, "type", "text", "line", "charPositionInLine", "channel", "index", "start", "stop"),
                    RawPreparationFact.identity(checked.pair),
                    RawPreparationFact.identity(checked.lexer),
                    ref(checked.stream)
                )
            }
            fun visit(node: org.antlr.v4.runtime.tree.ParseTree, depth: Int): RawPreparationFact {
                if (depth > 512 || !active.add(node)) fail("Invalid or excessive declaration syntax graph")
                val fact = when {
                    node is ParserRuleContext && node.javaClass.enclosingClass === Parser::class.java -> {
                        val children: List<*>? = node.children
                        if (children != null && children.javaClass.name !in lists) fail("Unsupported syntax children container")
                        RawPreparationFact.row(
                            RawPreparationFact.identity(node),
                            tokenFact(node.start),
                            tokenFact(node.stop, optional = true),
                            RawPreparationFact.sequence(
                                children.orEmpty().map {
                                    if (it !is org.antlr.v4.runtime.tree.ParseTree) fail("Invalid syntax child role")
                                    visit(it, depth + 1)
                                }
                            )
                        )
                    }
                    node.javaClass === org.antlr.v4.runtime.tree.TerminalNodeImpl::class.java ||
                        node.javaClass === org.antlr.v4.runtime.tree.ErrorNodeImpl::class.java -> {
                        RawPreparationFact.row(
                            RawPreparationFact.identity(node),
                            tokenFact((node as org.antlr.v4.runtime.tree.TerminalNodeImpl).symbol)
                        )
                    }
                    else -> fail("Unsupported declaration syntax receiver: ${node.javaClass.name}")
                }
                active.remove(node)
                return fact
            }
            return visit(root, 0)
        }
        private fun list(value: List<*>): RawPreparationFact {
            if (value.javaClass.name !in lists) fail("Unsupported declaration list: ${value.javaClass.name}")
            return RawPreparationFact.sequence(value.map(::raw))
        }
        private fun map(value: Map<*, *>): RawPreparationFact {
            if (value.javaClass.name !in maps) fail("Unsupported declaration map: ${value.javaClass.name}")
            return RawPreparationFact.unordered(value.entries.map { RawPreparationFact.row(raw(it.key), raw(it.value)) })
        }
        private fun node(value: Any): RawPreparationFact = when (value) {
            is ByteArray -> RawPreparationFact.sequence(value.map { RawPreparationFact.scalar(it.toInt()) })
            is CharArray -> RawPreparationFact.sequence(value.map { RawPreparationFact.scalar(it.code) })
            is IntArray -> RawPreparationFact.sequence(value.map { RawPreparationFact.scalar(it) })
            is Array<*> -> {
                if (value.javaClass.componentType !== MCFPPType::class.java || value.any { it !is MCFPPType })
                    fail("Unsupported declaration array role")
                RawPreparationFact.sequence(value.map(::raw))
            }
            is List<*> -> list(value)
            is Map<*, *> -> map(value)
            is Set<*> -> {
                if (value.javaClass.name !in sets) fail("Unsupported declaration set: ${value.javaClass.name}")
                RawPreparationFact.unordered(value.map(::raw))
            }
            is Pair<*, *> -> RawPreparationFact.row(raw(value.first), raw(value.second))
            GlobalScope -> {
                fun registrations(values: Map<*, *>): RawPreparationFact {
                    if (values.javaClass.name !in maps) fail("Unsupported namespace registration container")
                    return RawPreparationFact.unordered(
                        values.entries.map {
                            if (it.key !is String || it.value?.javaClass !== top.mcfpp.model.Namespace::class.java)
                                fail("Invalid namespace registration role")
                            val namespace = it.value as top.mcfpp.model.Namespace
                            val annotations: Map<*, *> = namespace.scope.annotations
                            if (annotations.javaClass.name !in maps || annotations.entries.any { entry -> entry.key !is String || entry.value !is Class<*> })
                                fail("Invalid annotation registration relation")
                            RawPreparationFact.row(
                                RawPreparationFact.scalar(it.key),
                                RawPreparationFact.identity(namespace),
                                RawPreparationFact.identity(annotations),
                                RawPreparationFact.unordered(
                                    annotations.entries.map { entry ->
                                        RawPreparationFact.row(raw(entry.key), RawPreparationFact.identity(entry.value))
                                    }
                                )
                            )
                        }
                    )
                }
                RawPreparationFact.row(
                    registrations(GlobalScope.localNamespaces),
                    registrations(GlobalScope.importedLibNamespaces),
                    registrations(GlobalScope.libNamespaces),
                    registrations(GlobalScope.stdNamespaces)
                )
            }
            is top.mcfpp.model.Namespace -> RawPreparationFact.row(raw(value.identifier), ref(value.scope), ref(value.journalMacroDeclarations))
            is CompoundDataScope -> value.preparationRelations().also(::follow)
            is SimpleLibScope -> {
                supported(value, "top.mcfpp.model.scope", setOf("SimpleLibScope", "NamespaceScope", "FileScope"))
                relation(value, "vars", { it is String }, { it is top.mcfpp.core.lang.Var<*> })
                relation(value, "typeAlias", { it is String }, { it is MCFPPType })
                relation(value, "template", { it is String }, { it is DataTemplate })
                relation(
                    value,
                    "genericTemplate",
                    { it is Pair<*, *> && it.first is String && it.second is List<*> },
                    { it is GenericDataTemplate }
                )
                relation(value, "functions", { it is String }, { it is List<*> && it.javaClass.name in lists && it.all { member -> member is Function } })
                val relations = arrayListOf(
                    required(
                        value, "vars", "functions", "template", "genericTemplate",
                        "typeAlias", "annotations", "objects", "enums", "parent"
                    )
                )
                if (value is NamespaceScope) relations.add(ref(value.journalFileFields))
                if (value is FileScope) {
                    relations.add(required(value, "importField", "importedNamespaceField"))
                    relations.add(RawPreparationFact.sequence(value.checkpointNamespaceField().elements.map(::ref)))
                }
                RawPreparationFact.sequence(relations)
            }
            is FunctionScope -> {
                if (value.javaClass !== top.mcfpp.analysis.lambda.LambdaCaptureScope::class.java)
                    supported(value, "top.mcfpp.model.scope", setOf("FunctionScope", "InternalFunctionScope", "NoStackFunctionScope"))
                relation(value, "types", { it is String }, { it is MCFPPType })
                relation(value, "vars", { it is String }, { it is top.mcfpp.core.lang.Var<*> })
                RawPreparationFact.row(required(value, "vars", "types", "fieldVarSet", "fieldTypeSet", "parent"),
                    if (value is top.mcfpp.analysis.lambda.LambdaCaptureScope)
                        required(value, "lexical", "sources", "aliases", "incomingEnvironment") else RawPreparationFact.scalar(null))
            }
            is top.mcfpp.io.MCFPPFile -> {
                if (value.javaClass !== MCFPPFile::class.java) fail("Unsupported declaration file receiver")
                required(value, "namespace", "field", "unsolvedImports")
            }
            is top.mcfpp.io.info.DeclarationEnvironmentInfo -> required(value, "namespace", "imports")
            is top.mcfpp.model.compound.DataTemplate -> {
                supported(
                    value,
                    "top.mcfpp.model.compound",
                    setOf(
                        "DataTemplate",
                        "TypeDataTemplate",
                        "UnsolvedTemplate",
                        "UnsolvedObjectTemplate",
                        "ObjectDataTemplate",
                        "GenericDataTemplate",
                        "GenericObjectDataTemplate",
                        "CompiledGenericDataTemplate",
                        "CompiledGenericObjectDataTemplate"
                    )
                )
                val privateFacts = value.preparationFacts() // exact receiver/role preflight before public slots
                if (value is GenericDataTemplate) required(value, "readOnlyParams", "ctx")
                if (value is top.mcfpp.model.compound.CompiledGenericDataTemplate) required(value, "originTemplate", "identity")
                val privateRows = RawPreparationFact.unordered(
                    privateFacts.values.map { (key, fact) ->
                        RawPreparationFact.row(raw(key), fact.also(::follow))
                    }
                )
                // Loading metadata is opaque input. Observe only the raw slot identity;
                // resolving it here would change the domain while checking its version.
                val loadingInputs = when (value) {
                    is top.mcfpp.model.compound.UnsolvedTemplate -> value.checkpointUnsolvedState().elements
                    is top.mcfpp.model.compound.UnsolvedObjectTemplate -> value.checkpointUnsolvedState().elements
                    else -> emptyList()
                }
                RawPreparationFact.row(
                    privateRows,
                    required(
                        value, "identifier", "namespace", "parent", "parentID", "scope",
                        "annotations", "commonType", "isAbstract", "isFinal", "declarationFile", "declarationEnvironment",
                        "alwaysDynamic", "companionObject", "constructors", "deferredFields", "pendingFieldAnnotations",
                        "pendingDeclarationAnnotations", "processedExtends"
                    ),
                    fields(value, "readOnlyParams", "originTemplate", "ctx"),
                    if (value is top.mcfpp.model.compound.TypeDataTemplate) required(value, "typeAs")
                        else RawPreparationFact.scalar(null),
                    RawPreparationFact.sequence(loadingInputs.map { RawPreparationFact.identity(it) })
                )
            }
            is top.mcfpp.model.compound.CompoundData -> {
                supported(value, "top.mcfpp.model.compound", setOf("CompoundData", "Enum"))
                RawPreparationFact.row(
                    required(value, "identifier", "namespace", "parent", "parentID", "scope", "annotations", "commonType", "processedExtends"),
                    fields(value, "members")
                )
            }
            is Function -> {
                supported(value, "top.mcfpp.model.function", functionShapes)
                formals(value, "normalParams")
                if (value is Generic<*> || value is NativeFunction) formals(value, "readOnlyParams")
                if (value is NativeFunction) required(value, "javaMethod", "javaMethodName", "caller", "returnsConstWhenArgsConst")
                RawPreparationFact.row(
                    required(
                        value, "identifier", "namespace", "normalParams", "returnType", "owner", "scope", "accessModifier",
                        "isFinal", "isAbstract", "isOverride", "annotations", "runtimeEffect", "declarationFile", "declarationEnvironment", "hasDefaultValue"
                    ),
                    fields(
                        value, "readOnlyParams",
                        "accessModifier", "isStatic", "isFinal", "isAbstract", "isOverride", "annotations", "runtimeEffect", "scope",
                        "declarationFile", "declarationEnvironment", "javaMethod", "javaMethodName", "caller", "returnsConstWhenArgsConst"
                    ),
                    RawPreparationFact.row(
                        RawPreparationFact.scalar("top.mcfpp.model.function.Function.ownerType"),
                        raw(value.declarationOwnerTypeState())
                    )
                )
            }
            is top.mcfpp.model.function.FunctionParam -> {
                relation(value, "defaultTypes", { it is TypeId }, { it is MCFPPType })
                required(
                    value, "identifier", "type", "typeName", "function",
                    "hasDefault", "isReadOnly", "defaultValue", "defaultTypes", "defaultContext"
                )
            }
            is top.mcfpp.core.lang.Var<*> -> {
                if (!admitted(value.javaClass, variableShapes)) fail("Unsupported declaration field: ${value.javaClass.name}")
                val proof = DeclarationLexicalSnapshot.rawValue(value)
                RawPreparationFact.row(
                    required(
                        value, "identifier", "type", "isConst", "isStatic", "nullable", "accessModifier",
                        "isFinal", "annotations", "parent", "declaredParentTemplate", "symbol"
                    ),
                    fields(value, "property", "field", "caller"),
                    top.mcfpp.core.lang.context.ContextBorrowPayload.direct(value)?.let {
                        RawPreparationFact.closed(it.identity)
                    } ?: RawPreparationFact.scalar(null),
                    proof?.constant?.let { RawPreparationFact.closed(it) } ?: RawPreparationFact.scalar(null),
                    // rawValue owns this defensive LinkedHashMap snapshot; no borrowed view is admitted.
                    proof?.descriptors?.let { descriptors ->
                        RawPreparationFact.unordered(
                            descriptors.entries.map {
                                RawPreparationFact.row(raw(it.key), raw(it.value))
                            }
                        )
                    } ?: RawPreparationFact.scalar(null)
                )
            }
            is top.mcfpp.model.property.Property -> required(
                value, "identifier", "accessor", "mutator", "parent", "declaredParentTemplate",
                "accessModifier", "isStatic", "isFinal", "isInherited"
            )
            is top.mcfpp.model.annotation.Annotation -> {
                if (value.hasDeclarationStateSupport()) value.preparationFacts().also(::follow)
                else {
                    // Legacy pure readers may retain an opaque annotation identity. Such a
                    // snapshot is explicitly incapable of adoption; no subclass state is trusted.
                    hasOpaqueAnnotation = true
                    val base = top.mcfpp.model.annotation.Annotation::class.java
                    RawPreparationFact.row(
                        RawPreparationFact.scalar("opaque annotation"),
                        RawPreparationFact.sequence(
                            listOf("identifier", "namespace", "params", "parents").map { name ->
                                val field = base.getDeclaredField(name)
                                check(field.trySetAccessible()) { "Cannot read annotation declaration slot" }
                                RawPreparationFact.row(raw(name), raw(field.get(value)))
                            }
                        )
                    )
                }
            }
            is MCFPPType -> {
                val owned = top.mcfpp.model.compound.CompoundData.isOwnedCompoundDataType(value)
                if (!owned && !admitted(value.javaClass, typeShapes) && value.javaClass !in DeclarationPreparationContext.leafClasses)
                    fail("Unsupported declaration type: ${value.javaClass.name}")
                if (value is MCFPPTypeWithGeneric && value !is MCFPPFunctionType) {
                    val arguments = slot(value, "generic")
                    if (arguments !is List<*> || arguments.javaClass.name !in lists || arguments.any { it !is MCFPPType })
                        fail("Invalid declaration generic argument relation")
                    if (value.javaClass === MCFPPNBTRefType::class.java &&
                        arguments.singleOrNull() !== slot(value, "valueType"))
                        fail("Invalid declaration reference pointee relation")
                }
                types.add(value)
                val privateState = when (value) {
                    is MCFPPTypeAliasType -> value.rawPreparationFacts()
                    is MCFPPDataTemplateType -> value.rawPreparationFacts()
                    else -> RawPreparationFact.scalar(null)
                }.also(::follow)
                RawPreparationFact.row(
                    privateState,
                    required(value, "parentType"),
                    if (value is MCFPPFunctionType) RawPreparationFact.row(
                        RawPreparationFact.sequence(value.parameters.map(::raw)), raw(value.result), raw(value.executionCategory)
                    ) else RawPreparationFact.scalar(null),
                    if (value.javaClass === MCFPPNBTRefType::class.java) required(value, "valueType")
                        else RawPreparationFact.scalar(null),
                    fields(
                        value, "generic", "types", "identifier", "enum", "dimension",
                        "limit", "isName", "originalTypeString", "typeAs", "typeId", "type", "owner", "t"
                    )
                )
            }
            is top.mcfpp.model.compound.EnumMember -> required(value, "identifier", "value", "data")
            is Effect -> when (value) {
                is Effect.Writes -> RawPreparationFact.row(ref(value.places), ref(value.contents))
                else -> RawPreparationFact.identity(value)
            }
            is Symbol -> RawPreparationFact.row(
                required(value, "id", "name", "declaredType", "mutable", "forceRuntime", "isLiteral", "readonly"),
                value.contextScope?.let { scope -> RawPreparationFact.row(
                    RawPreparationFact.scalar(scope.session.id), RawPreparationFact.scalar(scope.id), RawPreparationFact.scalar(scope.isActive))
                } ?: RawPreparationFact.scalar(null))
            // Place is final and its constructor defensively owns this immutable path.
            is Place -> RawPreparationFact.row(raw(value.root.value), RawPreparationFact.sequence(value.path.map(::raw)))
            is PathSegment.Field -> raw(value.name)
            is PathSegment.Index -> raw(value.index)
            PathSegment.UnknownIndex -> RawPreparationFact.scalar("unknown index")
            else -> {
                val shape = value.javaClass
                when {
                    admitted(shape, streamShapes) -> required(
                        value,
                        "size",
                        "name",
                        when (shape.simpleName) {
                            "CodePoint8BitCharStream" -> "byteArray"
                            "CodePoint16BitCharStream" -> "charArray"
                            else -> "intArray"
                        }
                    )
                    admitted(shape, accessorShapes.map { "top.mcfpp.model.property.$it" }.toSet()) -> fields(value, "function")
                    shape === top.mcfpp.model.compound.DataTemplateParam::class.java -> required(value, "identifier", "type", "typeIdentifier", "variance")
                    shape === top.mcfpp.io.info.MacroFunctionInfo::class.java -> required(value, "providerClass", "methodName", "jvmParameters", "returnClass", "identifier", "sourceParameters", "sourceReturnType")
                    shape === top.mcfpp.io.info.MacroParameterInfo::class.java -> required(value, "name", "type", "mode")
                    admitted(shape, setOf("top.mcfpp.model.compound.DataTemplate\$DeferredFieldDeclaration")) -> required(value, "context", "access", "annotations")
                    shape === top.mcfpp.nbt.tags.primitive.IntTag::class.java || shape === top.mcfpp.nbt.tags.primitive.StringTag::class.java -> required(value, "value")
                    else -> fail("Unsupported reachable declaration fact: ${shape.name}")
                }
            }
        }
        private class BackingSlot(val field: java.lang.reflect.Field, val label: RawPreparationFact)
        private class SlotSelection(val slots: List<BackingSlot>, val complete: Boolean)

        /** Class metadata is stable; each capture still reads the current backing values. */
        private class BackingShape(owner: Class<*>) {
            val names = linkedSetOf<String>()
            val slots = arrayListOf<BackingSlot>()
            private val selections = java.util.concurrent.ConcurrentHashMap<List<String>, SlotSelection>()

            fun select(names: Array<out String>): SlotSelection {
                val key = names.asList()
                selections[key]?.let { return it }
                val selection = SlotSelection(slots.filter { it.field.name in key }, this.names.containsAll(key))
                return selections.putIfAbsent(names.toList(), selection) ?: selection
            }

            init {
                var type: Class<*>? = owner
                while (type != null && type != Any::class.java) {
                    for (field in type.declaredFields) {
                        names.add(field.name)
                        if (!java.lang.reflect.Modifier.isStatic(field.modifiers)) {
                            slots.add(BackingSlot(field, RawPreparationFact.scalar(type.name + "." + field.name)))
                        }
                    }
                    type = type.superclass
                }
            }
        }
        companion object {
            private val backingShapes = object : ClassValue<BackingShape>() {
                override fun computeValue(type: Class<*>): BackingShape = BackingShape(type)
            }

            private val lists = setOf(
                "java.util.ArrayList",
                "java.util.LinkedList",
                "java.util.Arrays\$ArrayList",
                "kotlin.collections.EmptyList",
                "java.util.Collections\$EmptyList",
                "java.util.Collections\$SingletonList",
                "java.util.ImmutableCollections\$List12",
                "java.util.ImmutableCollections\$ListN"
            )
            private val maps = setOf(
                "java.util.HashMap",
                "java.util.LinkedHashMap",
                "java.util.IdentityHashMap",
                "kotlin.collections.EmptyMap",
                "java.util.Collections\$EmptyMap",
                "java.util.Collections\$SingletonMap",
                "java.util.ImmutableCollections\$Map1",
                "java.util.ImmutableCollections\$MapN"
            )
            private val sets = setOf(
                "java.util.HashSet",
                "java.util.LinkedHashSet",
                "kotlin.collections.EmptySet",
                "java.util.Collections\$EmptySet",
                "java.util.Collections\$SingletonSet",
                "java.util.ImmutableCollections\$Set12",
                "java.util.ImmutableCollections\$SetN"
            )
            private val functionShapes = setOf(
                "Function", "NativeFunction", "GenericFunction", "ExtensionFunction", "GenericExtensionFunction",
                "InlineFunction", "InternalFunction", "NoStackFunction", "MCFunction", "DataTemplateConstructor", "TypeDataTemplateConstructor",
                "UnknownFunction", "UnresolvedFunction", "UnresolvedNativeFunction"
            )
            private val streamShapes = setOf("CodePoint8BitCharStream", "CodePoint16BitCharStream", "CodePoint32BitCharStream")
                .map { "org.antlr.v4.runtime.CodePointCharStream\$$it" }.toSet()
            private val accessorShapes = setOf("FunctionAccessor", "NativeAccessor", "SimpleAccessor", "FunctionMutator", "NativeMutator", "SimpleMutator")
            private val variableShapes = setOf(
                "MCInt", "MCFloat", "MCAny", "MCObject", "PropertyVar", "MCFPPTypeVar", "UnknownVar",
                "UnresolvedVar", "RangeVar", "VectorVar", "Pos3Var", "Pos2Var", "PosDimension", "JsonText", "ImmutableList",
                "UnionTypeVar", "Null", "Void", "FunctionValue"
            ).map { "top.mcfpp.core.lang.$it" }.toSet() +
                setOf(
                    "NBTBasedData", "NBTList", "NBTDictionary", "NBTMap", "MCString", "MCByte", "MCShort", "MCLong", "MCDouble",
                    "NBTByteArray", "NBTIntArray", "NBTLongArray", "EntityUUIDVar"
                ).map { "top.mcfpp.core.lang.nbt.$it" } +
                setOf("DataTemplateObject", "TypeDataTemplateObject", "StaticMemberView", "EnumVar").map { "top.mcfpp.core.lang.obj.$it" } +
                setOf("top.mcfpp.core.lang.bool.ScoreBool", "top.mcfpp.core.lang.entity.SelectorVar",
                    "top.mcfpp.core.lang.ref.ReferenceVar") +
                setOf("ExecutionContextVar", "BoundExecutorVar", "BoundPositionVar").map { "top.mcfpp.core.lang.context.$it" }
            private val typeShapes = setOf(
                "MCFPPType", "MCFPPConcreteType", "MCFPPGenericParamType", "UnresolvedType", "MCFPPTypeAliasType",
                "MCFPPNotCompiledGenericType", "MCFPPDataTemplateType", "MCFPPTypeDataTemplateType", "MCFPPObjectDataTemplateType", 
                "MCFPPEnumType", "MCFPPGenericDataTemplateType", "MCFPPGenericObjectDataTemplateType", "MCFPPUnionType",
                "MCFPPListType", "MCFPPImmutableListType", "MCFPPCompoundType", "MCFPPDictType", "MCFPPMapType", "MCFPPFunctionType",
                "MCFPPReferenceType", "MCFPPScoreRefType", "MCFPPNBTRefType"
            ).map { "top.mcfpp.type.$it" }.toSet()
        }
    }
}

/** Input identities are the original owned AST declarations, never synthesized compiler members. */
internal class PreparedOwnedHeaderField(
    val owner: PreparedTemplateReservation,
    val declaration: Parser.TemplateFieldDeclarationContext,
    bound: DeclarationBinding.Bound
) {
    val name: String = declaration.Identifier().text
    val bound = bound.copy(
        dependencies = Collections.unmodifiableSet(LinkedHashSet(bound.dependencies)),
        descriptors = Collections.unmodifiableMap(LinkedHashMap(bound.descriptors))
    )
}

/** All formal names shadow free declarations, even before their values become available. */
internal class DeclarationPreparationContext private constructor(
    val lexical: DeclarationLexicalSnapshot,
    val readonlyIndex: Int = lexical.formals.count { it.readonly },
    predecessors: Map<String, DeclarationBinding.Bound> = emptyMap(),
    typeBindings: Map<String, MCFPPType> = emptyMap(),
    private val headers: SignaturePreparationSession?,
    private val reader: HeaderReadAccess?
) {
    constructor(
        lexical: DeclarationLexicalSnapshot,
        readonlyIndex: Int = lexical.formals.count { it.readonly },
        predecessors: Map<String, DeclarationBinding.Bound> = emptyMap(),
        typeBindings: Map<String, MCFPPType> = emptyMap(),
        headers: SignaturePreparationSession? = lexical.headerSession
    ) :
        this(lexical, readonlyIndex, predecessors, typeBindings, headers, null)

    private class HeaderReadAccess(
        val session: SignaturePreparationSession,
        val owner: PreparedTemplateReservation,
        val lexical: DeclarationLexicalSnapshot,
        val fields: Map<String, PreparedOwnedHeaderField>,
        val fieldNames: Set<String>,
        val allowReceiver: Boolean = true
    )

    private val admitted = Collections.newSetFromMap(java.util.IdentityHashMap<MCFPPType, Boolean>())

    internal fun requireCurrentReader(site: ParserRuleContext) {
        val access = reader ?: return
        access.session.requireHeaderReader(access.owner, preparationSpan(site))
        if (access.lexical.template(access.owner.prototype, site) == null)
            throw PreparationFactFailure(
                PreparationFactCode.UNPREPARED_TYPE,
                preparationSpan(site),
                "Header prototype is outside its original declaration domain"
            )
        if (access.owner.type.checkpointPrivateState().elements.single() !== access.owner.model)
            throw PreparationFactFailure(
                PreparationFactCode.UNPREPARED_TYPE,
                preparationSpan(site),
                "Header receiver no longer refers to its canonical shell"
            )
    }

    /** Local signature facts carry the exact capability under which RESERVED types were admitted. */
    internal fun requirePreparedSignature(signature: PreparedCompilerSignature, site: ParserRuleContext) {
        val required = signature.headerReader ?: return
        requireCurrentReader(site)
        val access = reader
        if (access == null || access.owner !== required.owner || access.session !== required.lexical.headerSession)
            throw PreparationFactFailure(
                PreparationFactCode.UNPREPARED_TYPE,
                preparationSpan(site),
                "Prepared header call belongs to a foreign or unavailable reader"
            )
        access.session.requireHeaderReader(required.owner, preparationSpan(site))
        if (required.lexical.template(required.owner.prototype, site) == null)
            throw PreparationFactFailure(
                PreparationFactCode.UNPREPARED_TYPE,
                preparationSpan(site),
                "Prepared header call no longer has its original declaration domain"
            )
    }

    internal fun receiver(site: ParserRuleContext): DeclarationBinding.Bound {
        requireCurrentReader(site)
        val access = reader ?: throw PreparationFactFailure(
            PreparationFactCode.UNSUPPORTED_EXPRESSION,
            preparationSpan(site),
            "Receiver requires prepared declaration facts"
        )
        if (!access.allowReceiver) throw PreparationFactFailure(
            PreparationFactCode.UNSUPPORTED_EXPRESSION,
            preparationSpan(site),
            "A called declaration cannot borrow the header receiver"
        )
        return DeclarationBinding.Bound(access.owner.type, headerReceiver = access.owner)
    }

    internal fun ownedMember(
        receiver: DeclarationBinding.Bound,
        name: String,
        site: ParserRuleContext
    ): DeclarationBinding.Bound {
        requireCurrentReader(site)
        val access = reader
        if (access == null || receiver.headerReceiver !== access.owner)
            throw PreparationFactFailure(
                PreparationFactCode.UNSUPPORTED_EXPRESSION,
                preparationSpan(site),
                "Member access requires the current owned header receiver"
            )
        return ownedField(access, name, site)
            ?: throw PreparationFactFailure(PreparationFactCode.UNKNOWN_NAME, preparationSpan(site), "Unknown owned field '$name'")
    }

    private fun ownedField(access: HeaderReadAccess, name: String, site: ParserRuleContext): DeclarationBinding.Bound? {
        access.fields[name]?.let {
            return exportFact(it.bound, site)
        }
        if (name in access.fieldNames)
            throw PreparationFactFailure(
                PreparationFactCode.UNPREPARED_TYPE,
                preparationSpan(site),
                "Owned field '$name' requires prepared header facts"
            )
        return null
    }

    /** Reuse the shape-checked copier; canonical template shells remain identity leaves. */
    internal fun exportType(type: MCFPPType, site: ParserRuleContext, source: SourceSpan? = null): MCFPPType = DeclarationBinding(this).preparedDeclaredType(preparedType(type, site, source), source ?: preparationSpan(site))

    private fun exportFact(fact: DeclarationBinding.Bound, site: ParserRuleContext): DeclarationBinding.Bound = fact.copy(
        type = exportType(fact.type, site),
        dependencies = Collections.unmodifiableSet(LinkedHashSet(fact.dependencies)),
        descriptors = Collections.unmodifiableMap(fact.descriptors.mapValues { (_, type) -> exportType(type, site) })
    )

    /** Nested formal declarations retain shell admission, but never inherit the caller's fields. */
    private fun formalContext(
        declaration: DeclarationLexicalSnapshot,
        index: Int,
        previous: Map<String, DeclarationBinding.Bound>,
        bindings: Map<String, MCFPPType>
    ): DeclarationPreparationContext = DeclarationPreparationContext(
        declaration,
        index,
        previous,
        bindings,
        headers,
        reader?.let { HeaderReadAccess(it.session, it.owner, it.lexical, emptyMap(), emptySet(), false) }
    )

    /** Existing wrappers are inspected through the raw private slot, never the resolving getter. */
    private fun admitWrapper(type: MCFPPDataTemplateType, site: ParserRuleContext, source: SourceSpan?): Boolean {
        val origin = source ?: preparationSpan(site)
        val target = type.checkpointPrivateState().elements.single()
        if (target.javaClass == DataTemplate::class.java || target.javaClass == ObjectDataTemplate::class.java) {
            if (lexical.template(target, site) == null) return false
            if (!target.hasCompletedDeclaration) throw PreparationFactFailure(
                PreparationFactCode.UNPREPARED_TYPE,
                origin,
                "Template wrapper requires declaration completion",
                CompletionTarget.Declaration.of(target)
            )
            admitted.add(type)
            return true
        }
        if (target.javaClass != top.mcfpp.model.compound.CompiledGenericDataTemplate::class.java &&
            target.javaClass != top.mcfpp.model.compound.CompiledGenericObjectDataTemplate::class.java
        ) return false
        val compiled = target as top.mcfpp.model.compound.CompiledGenericDataTemplate
        if (type.javaClass != MCFPPGenericDataTemplateType::class.java &&
            type.javaClass != MCFPPGenericObjectDataTemplateType::class.java
        ) return false
        if (type.typeId != compiled.inheritanceIdentity) return false
        val frozen = lexical.template(compiled.originTemplate, site) ?: return false
        val session = headers ?: return false
        val identity = compiled.inheritanceIdentity as? TypeId.Specialized ?: return false
        val key = SpecializationKey(
            compiled.originTemplate.declarationId,
            identity.arguments.map { SpecializationArgument.Constant(it) },
            session.generationOptions
        )
        if (compiled.originTemplate.compiledTemplates[key] !== compiled &&
            compiled.originTemplate.materializedTemplate(key) !== compiled
        ) return false
        val descriptors = listOf(
            MCFPPBaseType.Int,
            MCFPPBaseType.Float,
            MCFPPBaseType.Bool,
            MCFPPBaseType.String,
            MCFPPBaseType.Any,
            MCFPPBaseType.Object,
            MCFPPConcreteType.Type,
            MCFPPPrivateType.Null
        ).associateBy { it.typeId } + typeBindings.values.mapNotNull { candidate ->
            if (candidate.javaClass in leafClasses) candidate.typeId to candidate else null
        }
        if (key.arguments.size != frozen.readonly.size) return false
        val binder = DeclarationBinding(this)
        val declaration = frozen.declaration ?: return false
        val previous = linkedMapOf<String, DeclarationBinding.Bound>()
        val bindings = linkedMapOf<String, MCFPPType>()
        // This final compiler scope's allVars getter only copies its raw map. Do not use args,
        // getVar, Var.type, or restore an adapter to recover a canonical readonly descriptor.
        val slots = if (compiled.scope.javaClass == top.mcfpp.model.scope.CompoundDataScope::class.java)
            compiled.scope.allVars.associateBy { it.identifier } else emptyMap()
        val facts = key.arguments.mapIndexed { index, argument ->
            val proof = (argument as? SpecializationArgument.Constant)?.value as? CompilerValue.Typed ?: return false
            val formalBinder = DeclarationBinding(formalContext(declaration, index, previous, bindings))
            val sourceType = formalBinder.preparedDeclaredType(frozen.readonly[index].type, origin)
            if (proof.type != sourceType.typeId) return false
            val stored = slots[frozen.readonly[index].name]?.let { DeclarationLexicalSnapshot.rawValue(it) }
            if (stored != null && stored.constant != proof) return false
            PreparedExpressionFact.fromBound(
                DeclarationBinding.Bound(
                    sourceType,
                    proof,
                    descriptors = descriptors + stored?.descriptors.orEmpty() + (sourceType.typeId to sourceType)
                ),
                origin,
                binder
            ).also { fact ->
                previous[frozen.readonly[index].name] = fact.bound
                val value = (fact.value as CompilerValue.Typed).payload
                if (value is CompilerValue.TypeValue) bindings[frozen.readonly[index].name] = fact.bound.descriptors[value.id]
                    ?: throw PreparationFactFailure(PreparationFactCode.UNPREPARED_TYPE, preparationSpan(site), "Readonly type has no prepared descriptor")
            }
        }
        val header = if (reader == null) session.stageHeader(compiled.originTemplate, facts, origin)
        else session.stageReaderHeader(reader.owner, compiled.originTemplate, facts, origin)
        requireComplete(header, site)
        admitted.add(type)
        return true
    }

    private fun requireComplete(header: PreparedTemplateReservation, site: ParserRuleContext) {
        if (reader != null) {
            requireCurrentReader(site)
            if (reader.session.ownsReaderHeaderType(reader.owner, header.type, preparationSpan(site))) return
        }
        if (header.phase != HeaderPhase.COMPLETE) throw PreparationFactFailure(
            PreparationFactCode.UNPREPARED_TYPE,
            preparationSpan(site),
            "Template header requires controlled declaration completion",
            CompletionTarget.Header(header)
        )
    }
    private val predecessors = Collections.unmodifiableMap(
        predecessors.mapValues { (_, fact) ->
            fact.copy(
                dependencies = Collections.unmodifiableSet(LinkedHashSet(fact.dependencies)),
                descriptors = Collections.unmodifiableMap(LinkedHashMap(fact.descriptors))
            )
        }
    )
    private val typeBindings = Collections.unmodifiableMap(LinkedHashMap(typeBindings))
    init {
        val readonly = lexical.formals.filter { it.readonly }
        require(lexical.formals.map { it.name }.distinct().size == lexical.formals.size) { "Duplicate declaration formal name" }
        require(readonlyIndex in 0..readonly.size)
        require(predecessors.keys.all { name -> readonly.take(readonlyIndex).any { it.name == name } })
        require(predecessors.values.all { it.constant != null }) { "Readonly predecessors need closed compiler proofs" }
    }

    fun variable(name: String, site: ParserRuleContext): DeclarationBinding.Bound {
        requireCurrentReader(site)
        val formal = lexical.formals.firstOrNull { it.name == name }
        if (formal != null) {
            predecessors[name]?.let {
                preparedFact(it, site)
                return exportFact(it.copy(dependencies = it.dependencies + name), site)
            }
            throw PreparationFactFailure(
                PreparationFactCode.FORMAL_DEPENDENCY,
                preparationSpan(site),
                "Readonly default cannot depend on unavailable formal '$name' (self, forward, cycle or runtime parameter)"
            )
        }
        reader?.let { ownedField(it, name, site)?.let { field -> return field } }
        lexical.value(name, site)?.let {
            preparedFact(it, site)
            return exportFact(it, site)
        }
        val type = (typeBindings[name] ?: lexical.type(null, name, site))?.let { exportType(it, site) }
        if (type != null) return DeclarationBinding.Bound(
            MCFPPConcreteType.Type,
            CompilerValue.Typed(MCFPPConcreteType.Type.typeId, CompilerValue.TypeValue(type.typeId)),
            descriptors = mapOf(type.typeId to type)
        )
        throw PreparationFactFailure(PreparationFactCode.UNKNOWN_NAME, preparationSpan(site), "Unknown declaration name '$name'")
    }

    private fun preparedFact(fact: DeclarationBinding.Bound, site: ParserRuleContext) {
        preparedType(fact.type, site)
        fun value(proof: CompilerValue) {
            when (proof) {
                is CompilerValue.TypeValue -> preparedType(
                    fact.descriptors[proof.id]
                        ?: throw PreparationFactFailure(
                            PreparationFactCode.UNPREPARED_TYPE,
                            preparationSpan(site),
                            "Type constant requires prepared descriptor facts"
                        ),
                    site
                )
                is CompilerValue.Typed -> value(proof.payload)
                is CompilerValue.Sequence -> proof.elements.forEach(::value)
                is CompilerValue.Record -> proof.fields.values.forEach(::value)
                else -> Unit
            }
        }
        fact.constant?.let(::value)
    }

    /** Check shapes before reading any open identity/name/template property. */
    fun preparedType(type: MCFPPType, site: ParserRuleContext, source: SourceSpan? = null): MCFPPType {
        requireCurrentReader(site)
        val active = Collections.newSetFromMap(java.util.IdentityHashMap<MCFPPType, Boolean>())
        val done = Collections.newSetFromMap(java.util.IdentityHashMap<MCFPPType, Boolean>())
        fun reject(): Nothing = throw PreparationFactFailure(
            PreparationFactCode.UNPREPARED_TYPE,
            preparationSpan(site),
            "Type requires prepared header facts"
        )
        fun visit(value: MCFPPType) {
            if (done.contains(value)) return
            if (!active.add(value)) reject() // These legacy identity getters cannot represent a recursive container.
            when (value.javaClass) {
                MCFPPDataTemplateType::class.java, MCFPPObjectDataTemplateType::class.java,
                MCFPPGenericDataTemplateType::class.java, MCFPPGenericObjectDataTemplateType::class.java ->
                    if (value !in admitted &&
                        !(
                            reader != null &&
                                reader.session.ownsReaderHeaderType(
                                    reader.owner,
                                    value,
                                    source ?: preparationSpan(site)
                                )
                            ) &&
                        !admitWrapper(value as MCFPPDataTemplateType, site, source)
                    ) reject()
                MCFPPListType::class.java, MCFPPImmutableListType::class.java,
                MCFPPCompoundType::class.java, MCFPPDictType::class.java, MCFPPMapType::class.java ->
                    (value as MCFPPTypeWithGeneric).generic.forEach(::visit)
                MCFPPUnionType::class.java -> (value as MCFPPUnionType).types.forEach(::visit)
                MCFPPFunctionType::class.java -> (value as MCFPPFunctionType).generic.forEach(::visit)
                MCFPPNBTRefType::class.java -> visit((value as MCFPPNBTRefType).valueType)
                in leafClasses -> Unit
                else -> reject()
            }
            active.remove(value)
            done.add(value)
        }
        visit(type)
        return type
    }

    fun syntax(source: SourceSpan? = null): TypeSyntaxContext = object : TypeSyntaxContext() {
        private fun origin(site: ParserRuleContext): SourceSpan = source ?: preparationSpan(site)
        override fun invalid(site: ParserRuleContext, message: String): MCFPPType? = throw PreparationFactFailure(PreparationFactCode.UNKNOWN_NAME, preparationSpan(site), message)
        override fun known(body: Parser.TypeBodyContext): MCFPPType? = typeBindings[body.text]?.let { finishNamed(it, body) }
        override fun named(namespace: String?, identifier: String, site: Parser.TypeBodyContext): MCFPPType? {
            requireCurrentReader(site)
            // All formal names shadow free templates, even before a readonly binding is available.
            if (namespace == null && lexical.formals.any { it.name == identifier }) {
                typeBindings[identifier]?.let { return exportType(it, site) }
                throw PreparationFactFailure(
                    PreparationFactCode.FORMAL_DEPENDENCY,
                    preparationSpan(site),
                    "Type depends on unavailable formal '$identifier'"
                )
            }
            val head = lexical.template(namespace, identifier, site)
            if (head != null) {
                val prototype = head.model
                if (prototype is GenericDataTemplate)
                    return specialize(prototype, site.readOnlyArgs()?.expressionList()?.expression().orEmpty(), site)
                if (site.readOnlyArgs() != null) return invalid(site, "Ordinary template '$identifier' does not accept readonly arguments")
                if (!prototype.hasCompletedDeclaration) throw PreparationFactFailure(
                    PreparationFactCode.UNPREPARED_TYPE,
                    preparationSpan(site),
                    "Template '$identifier' requires declaration completion",
                    CompletionTarget.Declaration.of(prototype)
                )
                return (
                    if (prototype is ObjectDataTemplate) MCFPPObjectDataTemplateType(prototype, arrayListOf())
                    else MCFPPDataTemplateType(prototype, arrayListOf())
                    ).also { admitted.add(it) }
            }
            return lexical.type(namespace, identifier, site) ?: invalid(site, "Unknown declaration type '$identifier'")
        }
        override fun specialize(
            head: GenericDataTemplate,
            arguments: List<Parser.ExpressionContext>,
            site: Parser.TypeBodyContext
        ): MCFPPType {
            val frozen = lexical.template(null, head.identifier, site)
                ?.takeIf { it.model === head } ?: lexical.template(head.namespace, head.identifier, site)
                ?.takeIf { it.model === head }
                ?: throw PreparationFactFailure(PreparationFactCode.UNPREPARED_TYPE, preparationSpan(site), "Template is outside the frozen declaration domain")
            val session = headers ?: throw PreparationFactFailure(
                PreparationFactCode.UNPREPARED_TYPE,
                preparationSpan(site),
                "Template application requires a signature preparation session"
            )
            if (arguments.size != frozen.readonly.size) throw PreparationFactFailure(
                PreparationFactCode.FORMAL_DEPENDENCY,
                preparationSpan(site),
                "Template readonly argument count does not match '${frozen.name}'"
            )
            if (frozen.readonly.map { it.name }.distinct().size != frozen.readonly.size)
                throw PreparationFactFailure(PreparationFactCode.FORMAL_DEPENDENCY, preparationSpan(site), "Duplicate template readonly formal name")
            val actual = DeclarationBinding(this@DeclarationPreparationContext)
            val declaration = frozen.declaration ?: throw PreparationFactFailure(
                PreparationFactCode.UNPREPARED_TYPE,
                preparationSpan(site),
                "Prototype has no frozen declaration environment"
            )
            val previous = linkedMapOf<String, DeclarationBinding.Bound>()
            val bindings = linkedMapOf<String, MCFPPType>()
            val facts = arguments.mapIndexed { index, argument ->
                val binder = DeclarationBinding(formalContext(declaration, index, previous, bindings))
                val target = binder.preparedDeclaredType(frozen.readonly[index].type, origin(argument))
                val raw = actual.readonly(argument)
                if (TypeRelations.resolveImplicitConversion(raw.type, target) == null)
                    throw PreparationFactFailure(PreparationFactCode.NON_CLOSED_VALUE, preparationSpan(argument), "Readonly template argument does not adapt to its formal type")
                val converted = if (target == MCFPPBaseType.Any || target == MCFPPBaseType.Object)
                    raw.copy(type = target, constant = CompilerValue.Typed(target.typeId, checkNotNull(raw.constant)))
                else actual.constrain(raw, target)
                val fact = PreparedExpressionFact.fromBound(converted, origin(argument), actual)
                previous[frozen.readonly[index].name] = fact.bound
                val value = (fact.value as CompilerValue.Typed).payload
                if (value is CompilerValue.TypeValue) bindings[frozen.readonly[index].name] = fact.bound.descriptors[value.id]
                    ?: throw PreparationFactFailure(PreparationFactCode.UNPREPARED_TYPE, preparationSpan(argument), "Readonly type has no prepared descriptor")
                fact
            }
            val header = if (reader == null) session.stageHeader(head, facts, origin(site))
            else session.stageReaderHeader(reader.owner, head, facts, origin(site))
            requireComplete(header, site)
            return header.type.also { admitted.add(it) }
        }
        override fun finishNamed(type: MCFPPType, site: Parser.TypeBodyContext): MCFPPType? {
            if (site.readOnlyArgs() != null &&
                type !in admitted &&
                !(reader != null && reader.session.ownsReaderHeaderType(reader.owner, type, origin(site)))
            )
                throw PreparationFactFailure(PreparationFactCode.UNPREPARED_TYPE, preparationSpan(site), "Type requires prepared header facts")
            return exportType(type, site, source)
        }
    }

    companion object {
        /** The capability admits shells, while every name and formal still belongs to the callee. */
        internal fun forHeaderDeclaration(
            lexical: DeclarationLexicalSnapshot,
            owner: PreparedTemplateReservation,
            ownerLexical: DeclarationLexicalSnapshot,
            readonlyIndex: Int = 0,
            predecessors: Map<String, DeclarationBinding.Bound> = emptyMap(),
            typeBindings: Map<String, MCFPPType> = emptyMap(),
            source: SourceSpan = owner.source
        ): DeclarationPreparationContext {
            fun reject(message: String): Nothing = throw PreparationFactFailure(PreparationFactCode.UNPREPARED_TYPE, source, message)
            val session = ownerLexical.headerSession ?: reject("Header call requires its preparation session")
            session.requireHeaderReader(owner, source)
            if (lexical.headerSession !== session) reject("Called declaration belongs to a different preparation session")
            val head = ownerLexical.template(owner.prototype, owner.prototype.ctx)
                ?: reject("Header owner is outside its frozen declaration domain")
            val declaration = head.declaration ?: reject("Header owner has no original declaration environment")
            if (lexical.formals.map { it.name }.distinct().size != lexical.formals.size)
                throw PreparationFactFailure(PreparationFactCode.FORMAL_DEPENDENCY, source, "Duplicate declaration formal name")
            val readonly = lexical.formals.filter { it.readonly }
            if (readonlyIndex !in 0..readonly.size ||
                predecessors.keys.any { name ->
                    readonly.take(readonlyIndex).none { it.name == name }
                } ||
                predecessors.values.any { it.constant == null }
            )
                throw PreparationFactFailure(PreparationFactCode.FORMAL_DEPENDENCY, source, "Invalid called declaration formal progress")
            return DeclarationPreparationContext(
                lexical,
                readonlyIndex,
                predecessors,
                typeBindings,
                session,
                HeaderReadAccess(session, owner, declaration, emptyMap(), emptySet(), false)
            )
        }

        /** Only session-owned shells grant this capability; the original declaration supplies lookup. */
        internal fun forHeader(
            lexical: DeclarationLexicalSnapshot,
            owner: PreparedTemplateReservation,
            ownedFields: List<PreparedOwnedHeaderField> = emptyList(),
            source: SourceSpan = owner.source
        ): DeclarationPreparationContext {
            fun reject(message: String): Nothing = throw PreparationFactFailure(PreparationFactCode.UNPREPARED_TYPE, source, message)
            val session = lexical.headerSession ?: reject("Header reader requires its preparation session")
            session.requireHeaderReader(owner, source)
            val head = lexical.template(owner.prototype, owner.prototype.ctx)
                ?: reject("Header prototype is outside the frozen declaration domain")
            val declaration = head.declaration ?: reject("Header prototype has no original declaration environment")
            if (owner.readonlyFacts.size != head.readonly.size) reject("Header readonly formals have changed")
            if (head.readonly.map { it.name }.distinct().size != head.readonly.size) reject("Duplicate header readonly formal name")
            val declarations = owner.prototype.ctx.templateMemberDeclaration().mapNotNull { it.templateMember().templateFieldDeclaration() }
            val names = Collections.unmodifiableSet(declarations.mapTo(linkedSetOf()) { it.Identifier().text })
            val access = HeaderReadAccess(session, owner, declaration, emptyMap(), names)
            val previous = linkedMapOf<String, DeclarationBinding.Bound>()
            val bindings = linkedMapOf<String, MCFPPType>()
            for ((index, formal) in head.readonly.withIndex()) {
                val binder = DeclarationBinding(DeclarationPreparationContext(declaration, index, previous, bindings, session, access))
                val target = binder.preparedDeclaredType(formal.type, source)
                val fact = PreparedExpressionFact.fromBound(owner.readonlyFacts[index].bound, source, binder)
                if (fact.type.typeId != target.typeId) reject("Header readonly fact differs from its converted formal type")
                previous[formal.name] = fact.bound
                val value = (fact.value as CompilerValue.Typed).payload
                if (value is CompilerValue.TypeValue) bindings[formal.name] = fact.bound.descriptors[value.id]
                    ?: reject("Header readonly type lacks its canonical descriptor")
            }
            val base = DeclarationPreparationContext(declaration, head.readonly.size, previous, bindings, session, access)
            base.requireCurrentReader(owner.prototype.ctx)
            val binder = DeclarationBinding(base)
            val fields = linkedMapOf<String, PreparedOwnedHeaderField>()
            for (field in ownedFields) {
                if (field.owner !== owner || declarations.none { it === field.declaration })
                    reject("Field fact is not an original declaration of this header")
                if (fields.containsKey(field.name)) reject("Duplicate owned header field '${field.name}'")
                val origin = preparationSpan(field.declaration)
                val type = binder.preparedDeclaredType(field.bound.type, origin)
                val descriptors = field.bound.descriptors.mapValues { (_, descriptor) -> binder.preparedDeclaredType(descriptor, origin) }
                if (descriptors.any { (key, descriptor) -> key != descriptor.typeId }) reject("Field descriptor identity changed")
                val value = field.bound.copy(
                    type = type,
                    descriptors = descriptors,
                    superOwner = null,
                    headerReceiver = null,
                    constant = field.bound.constant.takeIf { field.declaration.CONST() != null }
                )
                val frozen = if (value.constant == null) value else PreparedExpressionFact.fromBound(value, origin, binder).bound
                fields[field.name] = PreparedOwnedHeaderField(owner, field.declaration, frozen)
            }
            return DeclarationPreparationContext(
                declaration,
                head.readonly.size,
                previous,
                bindings,
                session,
                HeaderReadAccess(session, owner, declaration, Collections.unmodifiableMap(fields), names)
            )
        }

        internal val leafClasses: Set<Class<*>> = setOf(
            MCFPPBaseType.Int::class.java, MCFPPBaseType.Bool::class.java, MCFPPBaseType.Float::class.java,
            MCFPPBaseType.String::class.java, MCFPPBaseType.Object::class.java, MCFPPBaseType.Any::class.java,
            MCFPPBaseType.JsonText::class.java, MCFPPBaseType.Range::class.java,
            MCFPPBaseType.Pos2::class.java, MCFPPBaseType.Pos3::class.java,
            MCFPPConcreteType.Type::class.java, MCFPPConcreteType.JavaVar::class.java,
            MCFPPNBTType.NBT::class.java, MCFPPNBTType.Byte::class.java, MCFPPNBTType.Short::class.java,
            MCFPPNBTType.Long::class.java, MCFPPNBTType.Double::class.java,
            MCFPPNBTType.ByteArray::class.java, MCFPPNBTType.IntArray::class.java, MCFPPNBTType.LongArray::class.java,
            MCFPPPrivateType.Null::class.java, MCFPPPrivateType.Wildcard::class.java,
            MCFPPPrivateType.Void::class.java, MCFPPPrivateType.MCFPPCoordinateDimension::class.java,
            MCFPPEntityType::class.java, MCFPPEntityType.Player::class.java,
            MCFPPVectorType::class.java, MCFPPEnumType::class.java,
            MCFPPFunctionType::class.java,
            MCFPPScoreRefType::class.java,
            top.mcfpp.core.lang.context.MCFPPExecutionContextType::class.java,
            top.mcfpp.core.lang.context.MCFPPBoundPositionType::class.java
        )
    }
}

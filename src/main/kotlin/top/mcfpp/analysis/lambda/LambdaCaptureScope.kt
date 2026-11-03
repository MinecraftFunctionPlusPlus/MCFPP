package top.mcfpp.analysis.lambda

import top.mcfpp.analysis.*
import top.mcfpp.core.lang.*
import top.mcfpp.model.function.Function
import top.mcfpp.model.scope.FunctionScope
import top.mcfpp.type.MCFPPNBTType
import top.mcfpp.util.TempPool

/** Resolves free names against their lexical declarations, never a creator's live frame. */
internal class LambdaCaptureScope(private val target: Function, private val lexical: FunctionScope,
                                  private val controlledContext: Boolean = false) :
    FunctionScope(target.scope.parent) {
    init {
        target.isDerivedImplementation = true
    }

    private val sources = linkedMapOf<SymbolId, Var<*>>()
    private val aliases = linkedMapOf<String, Var<*>>()
    private var incomingEnvironment: Var<*>? = null

    /** This ABI parameter already has capture aliases in the compiled lambda body. */
    internal fun isEnvironmentParameter(name: String): Boolean = incomingEnvironment?.identifier == name

    /** MNI transactions retain the actual aliases/sources, not only inherited local vars. */
    internal fun checkpointCaptures(): Pair<List<Any>, () -> Unit> {
        val savedSources = LinkedHashMap(sources)
        val savedAliases = LinkedHashMap(aliases)
        val savedEnvironment = incomingEnvironment
        return (listOf(lexical) + savedSources.values + savedAliases.values + listOfNotNull(savedEnvironment)) to {
            sources.clear(); sources.putAll(savedSources)
            aliases.clear(); aliases.putAll(savedAliases)
            incomingEnvironment = savedEnvironment
        }
    }

    override val allVars: Collection<Var<*>> get() = vars.values + aliases.values

    private fun environment(): Var<*> = incomingEnvironment ?: run {
        val name = TempPool.getVarIdentify()
        target.appendNormalParam(MCFPPNBTType.NBT, name)
        val value = target.normalParams.last().buildVar()
        putVar(name, value)
        val binding = StorageAccess.bindIncomingParameter(value)
        // Later bindIncomingParameters must retain the already established child layouts.
        val declaration = value.symbol!!.copy(readonly = true)
        value.symbol = declaration
        binding.data.declarations[binding.place] = declaration
        incomingEnvironment = value
        value
    }

    override fun getVar(key: String): Var<*>? {
        vars[key]?.let { it.stackIndex = 0; return it }
        aliases[key]?.let { it.stackIndex = 0; return it }
        var scope: top.mcfpp.model.scope.IScope? = lexical
        var outer: Var<*>? = null
        while (scope is FunctionScope) {
            outer = scope.vars[key]
            if (outer != null) break
            // Nested lambdas must first resolve a free name through the enclosing capture
            // scope, so both environments preserve the same lexical SymbolId.
            if (scope is LambdaCaptureScope) {
                outer = scope.getVar(key)
                if (outer != null) break
            }
            scope = scope.parent.firstOrNull()
        }
        val fileDeclaration = target.declarationFile?.field?.getVar(key)?.symbol?.id
        if (outer == null || outer.isStatic || fileDeclaration != null && outer.symbol?.id == fileDeclaration) {
            val global = outer ?: super.getVar(key) ?: return null
            return global.clone().also { copy ->
                copy.storageBinding = global.storageBinding?.copy(trustConstants = false)
                copy.isStatic = true
            }
        }
        val source = outer
        return boundCapture(source, key)
    }

    /** Resolve a persisted lexical D through its actual source, even after a namesake is declared. */
    internal fun boundCapture(source: Var<*>, key: String = source.identifier): Var<*> {
        source.bindDeclaration()
        val symbol = source.symbol!!
        aliases.values.firstOrNull { it.symbol?.id == symbol.id }?.let { return it }
        if (controlledContext && top.mcfpp.core.lang.context.ContextBorrowing.provenance(source).isNotEmpty()) {
            val borrowed = StorageAccess.capturePayload(source.type, source, key)
                ?: return source.clone().apply { isError = true }
            val declaration = borrowed.symbol?.copy(readonly = true, lambdaCaptureName = key)
            borrowed.symbol = declaration
            borrowed.storageBinding?.let { binding -> declaration?.let { binding.data.declarations[binding.place] = it } }
            aliases[key] = borrowed
            return borrowed
        }
        sources.putIfAbsent(symbol.id, source)
        val alias = if (!StorageAccess.hasRuntimeRepresentation(source)) {
            StorageAccess.snapshot(source)?.let { snapshot ->
                StorageAccess.restore(source.type, snapshot, key, StorageAccess.boundTypes(source))
            } ?: return source.clone().apply { isError = true }
        } else {
            val root = StorageAccess.ensure(environment())
            val binding = root.field(captureKey(symbol.id))
            val actual = StorageAccess.actualType(source)
            binding.data.types.putAll(StorageAccess.boundTypes(source))
            binding.data.types[actual.typeId] = actual
            binding.data.facts.initialize(binding.place, ValueFacts(
                TypeKnowledge.Exact(actual.typeId), ValueKnowledge.Unknown))
            source.storageBinding?.let { original ->
                StorageAccess.copyRuntimeEvidence(original, binding)
            }
            StorageAccess.adapter(source.type, key, binding)
        }
        val declaration = symbol.copy(readonly = true, lambdaCaptureName = key)
        alias.symbol = declaration
        alias.storageBinding?.let { it.data.declarations[it.place] = declaration }
        // Capturing a free name does not declare that name in the body. A later local
        // declaration may shadow it, while normal duplicate-local checks still use vars.
        aliases[key] = alias
        return alias
    }

    /** Run only back in the creator: capture copies are emitted at expression evaluation. */
    fun captures(): List<LambdaCaptureResult> = sources.values.map { LambdaCaptureRules.capture(it, it.symbol) }

    /** Type binding may inspect a nested body before runtime name lookup creates its aliases. */
    fun lexicalTypes(): Map<String, top.mcfpp.type.MCFPPType> {
        val result = linkedMapOf<String, top.mcfpp.type.MCFPPType>()
        if (lexical is LambdaCaptureScope) result.putAll(lexical.lexicalTypes())
        StorageAccess.visibleValues(lexical).asReversed().forEach { result[it.identifier] = it.type }
        return result
    }

    companion object {
        fun captureKey(symbol: SymbolId) = "capture_${symbol.value}"
    }
}

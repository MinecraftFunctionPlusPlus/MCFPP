package top.mcfpp.core.lang

import top.mcfpp.analysis.CompilerValue
import top.mcfpp.analysis.StorageAccess
import top.mcfpp.core.lang.nbt.NBTBasedData
import top.mcfpp.model.function.Function
import top.mcfpp.model.function.UnknownFunction
import top.mcfpp.model.Member
import top.mcfpp.model.scope.GlobalScope
import top.mcfpp.type.MCFPPFunctionType
import top.mcfpp.type.MCFPPType

/** One import's private callable directory. Values keep only this revocable session capability. */
class FunctionTargetResolver(resolve: (String) -> Function?) : AutoCloseable {
    private var resolver: ((String) -> Function?)? = resolve
    internal fun resolve(identifier: String): Function? {
        val lookup = checkNotNull(resolver) { "The function target compilation session is closed" }
        return lookup(identifier)?.also { function ->
            check(function.namespaceID.toString() == identifier) { "Function target resolver returned another declaration" }
            function.bodyHandle?.requireOpen()
        }
    }
    override fun close() { resolver = null }
}

/** Owned function payload. Captures use independent fields keyed by lexical SymbolId. */
class FunctionValue(identifier: String, signature: MCFPPFunctionType) : Var<FunctionValue>(identifier) {
    override var type: MCFPPType = signature

    constructor(identifier: String, signature: MCFPPFunctionType, target: Function) : this(identifier, signature) {
        StorageAccess.initializeLiteral(this, CompilerValue.Record(mapOf(
            "target" to CompilerValue.Text(target.namespaceID.toString()),
            "environment" to CompilerValue.Record(emptyMap())
        )))
    }

    internal fun initializeEnvironment(captures: List<LambdaCapture>, types: Map<top.mcfpp.type.TypeId, MCFPPType> = emptyMap()) {
        val binding = StorageAccess.ensure(this).field("environment")
        for (capture in captures.filter { it.isCreationTimeValue }) {
            val source = capture.owned ?: capture.snapshot?.let {
                StorageAccess.restore(capture.declaredType, it, top.mcfpp.util.TempPool.getVarIdentify(), types)
            } ?: run {
                top.mcfpp.util.LogProcessor.error("Cannot create capture '${capture.name}': no owned representation for '${capture.declaredType.typeName}'")
                isError = true
                return
            }
            // Compiler-only immutable values are compiled into the isolated body. Only
            // existing runtime representations enter the owned runtime environment.
            if (!StorageAccess.hasRuntimeRepresentation(source)) continue
            val destination = binding.field("capture_${capture.symbol.value}")
            val value = StorageAccess.adapter(capture.declaredType, capture.name, destination)
            StorageAccess.write(value, source)
        }
    }

    internal fun environment(): Var<*> = StorageAccess.adapter(top.mcfpp.type.MCFPPNBTType.NBT,
        "environment", StorageAccess.ensure(this).field("environment"))

    fun knownTarget(): Function? = knownTargets().singleOrNull()

    fun bindTargets(resolver: FunctionTargetResolver): FunctionValue = apply {
        StorageAccess.bindFunctionTargets(this, resolver)
    }

    /** Only compiler-owned catalog entries may become executable targets. */
    internal fun knownTargets(): List<Function> {
        val catalog = candidates()
        if (catalog.isEmpty()) return emptyList()
        val closed = StorageAccess.snapshot(this)
        val payload = (closed as? CompilerValue.Typed)?.payload ?: closed
        val target = ((payload as? CompilerValue.Record)?.fields?.get("target") as? CompilerValue.Text)?.value
        val selected = if (target != null) {
            if (target !in catalog) return emptyList()
            setOf(target)
        } else catalog
        val functions = (GlobalScope.localNamespaces.values + GlobalScope.libNamespaces.values + GlobalScope.stdNamespaces.values)
            .flatMap { it.scope.functions.values.flatten() }
            .flatMap { listOf(it) + it.compiledFunctions.values }
            .associateBy { it.namespaceID.toString() }
        val binding = storageBinding
        val sessions = binding?.let { it.data.callableBindings[it.place] }.orEmpty()
        return selected.sorted().map { id ->
            val owners = sessions[id].orEmpty()
            if (owners.isEmpty()) functions[id] ?: return emptyList()
            else {
                val targets = owners.map { it.resolve(id) ?: return emptyList() }
                val target = targets.first()
                check(targets.all { it === target }) { "A callable target belongs to conflicting compilation sessions: $id" }
                target
            }
        }
    }

    override fun clone(): FunctionValue = FunctionValue(identifier, type as MCFPPFunctionType).also { it.setAs(this) }

    internal fun candidates(): Set<String> = storageBinding?.let { it.data.callableTargets[it.place] }.orEmpty()

    override fun doAssignedBy(b: Var<*>): FunctionValue { StorageAccess.write(this, b); return this }
    override fun getTempVar(): FunctionValue = StorageAccess.capture(this) as? FunctionValue ?: clone().apply { isError = true }
    override fun storeToStack() {}
    override fun getFromStack() {}
    override fun getMemberVar(key: String, accessModifier: Member.AccessModifier): Pair<Var<*>?, Boolean> = null to false
    override fun getMemberFunction(key: String, readOnlyArgs: List<Var<*>>, normalArgs: List<Var<*>>,
                                   accessModifier: Member.AccessModifier): Pair<Function, Boolean> = UnknownFunction(key) to false
    override fun toNBTVar(): NBTBasedData = NBTBasedData(identifier).also { it.setAs(this) }
}

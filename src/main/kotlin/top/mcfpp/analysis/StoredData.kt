package top.mcfpp.analysis

import top.mcfpp.core.lang.FunctionTargetResolver
import top.mcfpp.core.lang.MCInt
import top.mcfpp.core.lang.context.ContextBorrowing
import top.mcfpp.core.lang.nbt.MCString
import top.mcfpp.core.lang.nbt.NBTBasedData
import top.mcfpp.lib.NBTPath
import top.mcfpp.type.MCFPPType
import top.mcfpp.type.TypeId
import top.mcfpp.util.LogProcessor
import top.mcfpp.util.TempPool

/** A live address adapter. Facts and synchronization caches contain immutable values/layouts. */
class StorageBinding(
    val data: StoredData,
    val place: Place,
    path: NBTPath,
    val view: ValueRef.TypedView? = null,
    val trustConstants: Boolean = true,
    val location: Location = data.location(place, path)
) {
    val path: NBTPath get() = data.address(location)
    fun copy(data: StoredData = this.data, place: Place = this.place, path: NBTPath? = null,
             view: ValueRef.TypedView? = this.view, trustConstants: Boolean = this.trustConstants,
             location: Location = if (data === this.data && place == this.place && path == null) this.location else data.location(place, path ?: this.path)) =
        StorageBinding(data, place, path ?: this.path, view, trustConstants, location)
    fun field(name: String) = copy(place = place.field(name), path = path.memberIndex(name))
}

/** 存储所有者统一持有事实、布局与版本；多个地址适配器可以引用同一份存储状态。 */
class StoredData(val root: Place, var path: NBTPath, private var initialize: (() -> Unit)? = null,
                 var layout: StorageLayout = StorageLayout.Nbt(path.source.toString(), path.toCommandPart().toString()),
                 internal val contextScope: top.mcfpp.core.lang.context.ContextScope? = ContextBorrowing.currentScope) {
    val facts = FlowFacts()
    val versions = StorageVersions()
    val types = mutableMapOf<TypeId, MCFPPType>()
    val declarations = mutableMapOf<Place, Symbol>()
    /** Possible compiler-owned code targets, not a claim that the runtime value is constant. */
    val callableTargets = mutableMapOf<Place, Set<String>>()
    internal val callableBindings = mutableMapOf<Place, Map<String, Set<FunctionTargetResolver>>>()
    private val registers = mutableMapOf<Pair<Location, TypeId>, StorageLayout.Scoreboard>()
    private val childLayouts = mutableMapOf<Place, StorageLayout>()
    private val sources = mutableMapOf<SymbolId, StoredData>()
    internal fun retainedSources(): List<StoredData> = sources.values.toList()

    internal fun checkpointStorageState(): () -> Unit {
        val savedPath = path
        val savedInitialize = initialize
        val savedLayout = layout.checkpointCopy()
        val savedFacts = facts.fork()
        val restoreVersions = versions.checkpoint()
        val savedTypes = LinkedHashMap(types)
        val savedDeclarations = LinkedHashMap(declarations)
        val savedTargets = callableTargets.mapValues { it.value.toSet() }
        val savedBindings = callableBindings.mapValues { (_, targets) -> targets.mapValues { it.value.toSet() } }
        val savedRegisters = LinkedHashMap(registers)
        val savedChildren = childLayouts.mapValues { (_, value) -> value.checkpointCopy() }
        val savedSources = LinkedHashMap(sources)
        return {
            path = savedPath
            initialize = savedInitialize
            layout = savedLayout
            facts.replaceWith(savedFacts)
            restoreVersions()
            types.clear()
            types.putAll(savedTypes)
            declarations.clear()
            declarations.putAll(savedDeclarations)
            callableTargets.clear()
            callableTargets.putAll(savedTargets)
            callableBindings.clear()
            callableBindings.putAll(savedBindings)
            registers.clear()
            registers.putAll(savedRegisters)
            childLayouts.clear()
            childLayouts.putAll(savedChildren)
            sources.clear()
            sources.putAll(savedSources)
        }
    }
    internal fun retainSource(source: StoredData) { if (source !== this) sources[source.root.root] = source }
    internal fun inheritSources(source: StoredData) { sources.putAll(source.sources); retainSource(source) }
    internal fun source(place: Place): StoredData? = if (place.root == root.root) this else sources[place.root]
    internal fun location(place: Place, address: NBTPath): Location {
        val captured = linkedMapOf<Int, ValueRef>()
        val capturedLocations = linkedMapOf<Int, Location>()
        val predicates = linkedSetOf<Int>()
        val frameOffset = top.mcfpp.lib.SelectorExpression.frame(address) - top.mcfpp.lib.SelectorExpression.frame(path)
        val tail = address.pathList.drop(path.pathList.size)
        for ((relative, segment) in tail.withIndex()) {
            val index = root.path.size + relative
            if (place.path.getOrNull(index) != PathSegment.UnknownIndex) continue
            val value = when (segment) {
                is top.mcfpp.lib.IntPath -> segment.value
                is top.mcfpp.lib.NBTPredicatePath -> segment.value.also { predicates += index }
                is top.mcfpp.lib.MemberPath -> segment.value
                else -> continue
            }
            val original = value.valueRef()
            val ref = if (original is ValueRef.TypedView) ValueRef.Read(original.type, original.place) else original
            captured[index] = ref
            value.storageBinding?.let { binding ->
                retainSource(binding.data)
                capturedLocations[index] = binding.location.inFrame(-frameOffset)
            }
        }
        return Location(place, frameOffset = frameOffset, captured = captured, predicates = predicates,
            capturedLocations = capturedLocations)
    }
    internal fun address(location: Location): NBTPath {
        var address = StorageAccess.offsetFrame(path, location.frameOffset)
        for ((index, segment) in location.place.path.withIndex()) {
            if (index < root.path.size) continue
            address = when (segment) {
                is PathSegment.Field -> address.memberIndex(StorageAccess.quotedKey(segment.name))
                is PathSegment.Index -> address.intIndex(segment.index)
                PathSegment.UnknownIndex -> {
                    val ref = location.captured[index] ?: error("Dynamic location has no captured operand")
                    val type = types[ref.type] ?: MCFPPType.resolveTypeId(ref.type) ?: error("Unknown captured operand type")
                    val value = when (ref) {
                        is ValueRef.Constant -> StorageAccess.literal(type, ref.value, types = types)
                        is ValueRef.Read -> {
                            val owner = source(ref.place) ?: error("Captured operand has no owning location")
                            StorageAccess.adapter(type, TempPool.getVarIdentify(), StorageBinding(owner, ref.place,
                                owner.path, location = (location.capturedLocations[index] ?: Location(ref.place)).inFrame(location.frameOffset)))
                        }
                        else -> error("A dynamic address requires a captured Place")
                    }
                    when {
                        index in location.predicates -> address.nbtIndex(value as NBTBasedData)
                        value is MCInt -> address.intIndex(value)
                        value is MCString -> address.memberIndex(value)
                        else -> error("Unsupported captured address operand")
                    }
                }
            }
        }
        return address
    }
    fun layoutAt(place: Place): StorageLayout = childLayouts[place] ?: layout
    fun registerLayout(place: Place, layout: StorageLayout, replace: Boolean = true) {
        if (place != root) {
            if (replace) childLayouts[place] = layout else childLayouts.putIfAbsent(place, layout)
        }
    }
    internal fun retainCompilerValue() {
        layout = StorageLayout.CompilerOnly
        initialize = null
        versions.invalidate(root)
    }
    fun materialize(place: Place = root) {
        val actualLayout = layoutAt(place)
        if (actualLayout == StorageLayout.CompilerOnly) {
            LogProcessor.error("Compiler-only place cannot be materialized")
            return
        }
        initialize?.let { it(); initialize = null }
        versions.materialize(place, actualLayout)
    }

    internal fun overwriteRoot() { initialize = null }
    internal fun refreshWriter(writer: () -> Unit) { initialize = writer }
    internal fun relocate(path: NBTPath, layout: StorageLayout, writer: (() -> Unit)?) {
        this.path = path
        this.layout = layout
        initialize = writer
        registers.clear()
        versions.invalidate(root)
    }

    internal fun allocate(path: NBTPath, layout: StorageLayout, writer: (() -> Unit)?) {
        check(this.layout == StorageLayout.Constant)
        this.path = path
        this.layout = layout
        initialize = writer
    }

    fun register(place: Place, type: TypeId, objective: String, location: Location = Location(place)): StorageLayout.Scoreboard =
        if (PathSegment.UnknownIndex in place.path) StorageLayout.Scoreboard(TempPool.getVarIdentify(), objective)
        else registers.getOrPut(location to type) { StorageLayout.Scoreboard(TempPool.getVarIdentify(), objective) }

    fun write(place: Place, fact: ValueFacts) {
        versions.invalidate(place)
        if (layout == StorageLayout.CompilerOnly) facts.writeConstant(place, fact) else facts.write(place, fact)
    }

    fun barrier() {
        val protected = declarations.filter { (place, declaration) -> ConstantBinding.protected(declaration, place, facts) }.keys
        fun isProtected(place: Place) = protected.any { it.root == place.root && place.path.take(it.path.size) == it.path }
        if (layout == StorageLayout.CompilerOnly) {
            for ((place, physical) in childLayouts) {
                if (physical == StorageLayout.CompilerOnly || isProtected(place)) continue
                if (facts.read(place)?.state == ValueState.INITIALIZED) materialize(place)
                versions.invalidate(place)
                for ((child, fact) in facts.entries()) {
                    if (child.root != place.root || child.path.take(place.path.size) != place.path || isProtected(child)) continue
                    facts.refine(child, fact.copy(type = TypeKnowledge.Unknown, value = ValueKnowledge.Unknown, readableLayout = null,
                        presence = Presence.Unknown,
                        state = if (fact.state == ValueState.UNINITIALIZED) ValueState.MAYBE_INITIALIZED else fact.state))
                }
            }
            return
        }
        materialize()
        versions.invalidate(root)
        facts.barrier(protected)
    }
}

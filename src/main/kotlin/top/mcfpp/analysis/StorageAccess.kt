package top.mcfpp.analysis

import top.mcfpp.Project
import top.mcfpp.backend.NbtEncoding
import top.mcfpp.command.Command
import top.mcfpp.command.Commands
import top.mcfpp.command.FloatProviders
import top.mcfpp.core.lang.*
import top.mcfpp.core.lang.bool.BaseBool
import top.mcfpp.core.lang.bool.ScoreBool
import top.mcfpp.core.lang.nbt.NBTBasedData
import top.mcfpp.core.lang.nbt.MCString
import top.mcfpp.core.lang.nbt.NBTList
import top.mcfpp.core.lang.nbt.NBTDictionary
import top.mcfpp.core.lang.nbt.NBTMap
import top.mcfpp.core.lang.nbt.NBTArray
import top.mcfpp.core.lang.obj.DataTemplateObject
import top.mcfpp.lib.EncodedChatComponent
import top.mcfpp.lib.ListChatComponent
import top.mcfpp.lib.NBTPath
import top.mcfpp.lib.SbObject
import top.mcfpp.model.compound.ObjectCompoundData
import top.mcfpp.model.function.Function
import top.mcfpp.nbt.tags.CompoundTag
import top.mcfpp.nbt.tags.Tag
import top.mcfpp.nbt.tags.collection.ListTag
import top.mcfpp.nbt.tags.primitive.IntTag
import top.mcfpp.type.*
import top.mcfpp.util.LogProcessor
import top.mcfpp.util.NBTUtil
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

class StoredData(val root: Place, var path: NBTPath, private var initialize: (() -> Unit)? = null,
                 var layout: StorageLayout = StorageLayout.Nbt(path.source.toString(), path.toCommandPart().toString())) {
    val facts = FlowFacts()
    val versions = StorageVersions()
    val types = mutableMapOf<TypeId, MCFPPType>()
    val declarations = mutableMapOf<Place, Symbol>()
    private val registers = mutableMapOf<Pair<Location, TypeId>, StorageLayout.Scoreboard>()
    private val childLayouts = mutableMapOf<Place, StorageLayout>()
    private val sources = mutableMapOf<SymbolId, StoredData>()
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
        if (layout == StorageLayout.CompilerOnly) {
            for ((place, physical) in childLayouts) {
                if (physical == StorageLayout.CompilerOnly || declarations[place]?.readonly == true) continue
                if (facts.read(place)?.state == ValueState.INITIALIZED) materialize(place)
                versions.invalidate(place)
                for ((child, fact) in facts.entries()) {
                    if (child.root != place.root || child.path.take(place.path.size) != place.path || declarations[child]?.readonly == true) continue
                    facts.refine(child, fact.copy(type = TypeKnowledge.Unknown, value = ValueKnowledge.Unknown, readableLayout = null,
                        state = if (fact.state == ValueState.UNINITIALIZED) ValueState.MAYBE_INITIALIZED else fact.state))
                }
            }
            return
        }
        materialize()
        versions.invalidate(root)
        facts.barrier(declarations.filterValues { it.readonly }.keys)
    }
}

/** Central boundary between Place/TypedView and the remaining Var-based backends. */
object StorageAccess {
    fun flowSnapshot(values: Iterable<Var<*>>): Map<StoredData, FlowFacts> =
        values.mapNotNull { value -> value.storageBinding?.also { binding ->
            value.symbol?.let { binding.data.declarations.putIfAbsent(binding.place, it) }
        }?.data }.distinct().associateWith { it.facts.fork() }

    fun restoreFlow(state: Map<StoredData, FlowFacts>) {
        for ((data, facts) in state) {
            data.facts.replaceWith(facts)
            data.versions.invalidate(data.root)
        }
    }

    fun joinFlow(branches: List<Map<StoredData, FlowFacts>>): Map<StoredData, FlowFacts> {
        val stores = branches.flatMap { it.keys }.toSet()
        return stores.associateWith { data ->
            branches.map { it[data] ?: FlowFacts() }.reduce(FlowFacts::join)
        }
    }

    fun exitFlow(state: Map<StoredData, FlowFacts>) = state.mapValues { (_, facts) -> facts.fork().apply { reachable = false } }

    fun widenLoop(entry: Map<StoredData, FlowFacts>): Map<StoredData, FlowFacts> = entry.mapValues { (data, facts) ->
        if (facts.read(data.root)?.state == ValueState.INITIALIZED && data.layout != StorageLayout.CompilerOnly) {
            val declaration = data.declarations[data.root]
            val type = declaration?.declaredType?.let(data.types::get)
            if (type != null && type.hasRuntimeRepresentation) ensure(adapter(type, declaration.name,
                StorageBinding(data, data.root, data.path))).data.materialize()
        }
        val widened = facts.fork()
        for ((place, fact) in facts.entries()) {
            if (data.layout != StorageLayout.CompilerOnly && data.declarations[place]?.readonly != true)
                widened.refine(place, fact.copy(value = ValueKnowledge.Unknown))
        }
        widened
    }

    fun finishLoop(entry: Map<StoredData, FlowFacts>, backEdges: List<Map<StoredData, FlowFacts>>,
                   breakExits: List<Map<StoredData, FlowFacts>>, maySkip: Boolean = true,
                   naturalExits: List<Map<StoredData, FlowFacts>>? = null): Map<StoredData, FlowFacts> {
        for ((data, incoming) in entry) {
            if (data.layout != StorageLayout.CompilerOnly) continue
            for ((place, fact) in incoming.entries()) {
                if (data.declarations[place]?.readonly == true) continue
                if (backEdges.any { edge -> edge[data]?.let { it.reachable && it.read(place) != fact } == true }) {
                    LogProcessor.error("Cannot represent a loop-carried change to compiler-only value '${data.declarations[place]?.name ?: place.root}'")
                }
            }
        }
        for ((data, incoming) in entry) for ((place, declaration) in data.declarations) {
            if (incoming.read(place) == null || declaration.mutable || declaration.readonly) continue
            if (incoming.read(place)?.state == ValueState.UNINITIALIZED && backEdges.any { edge ->
                edge[data]?.let { it.reachable && it.read(place)?.state != ValueState.UNINITIALIZED } == true
            }) LogProcessor.error("Constant '${declaration.name}' may be assigned more than once by this loop")
        }
        var header = entry
        do {
            val previous = header
            header = joinFlow(listOf(entry) + backEdges + listOf(previous))
        } while (header != previous)
        val exits = breakExits + (naturalExits ?: if (maySkip) listOf(header) else emptyList())
        return if (exits.isEmpty()) exitFlow(header) else joinFlow(exits)
    }

    fun invalidateReads(values: Iterable<Var<*>>) {
        val stores = linkedSetOf<StoredData>()
        for (value in values) {
            value.storageReadVersion = null
            value.storageBinding?.data?.let(stores::add)
        }
        stores.forEach { data ->
            data.versions.invalidate(data.root)
            val score = data.layout as? StorageLayout.Scoreboard
            if (score != null) {
                val type = data.declarations[data.root]?.declaredType
                data.refreshWriter(scoreWriter(data.path, score.player, score.objective,
                    scoreTag(type)))
            }
        }
    }

    fun iterationLength(value: Var<*>): MCInt? {
        if (value.type !is top.mcfpp.type.MCFPPListType && value.type !is top.mcfpp.type.MCFPPImmutableListType) {
            LogProcessor.error("Value is not an iterable list")
            return null
        }
        val binding = ensure(value)
        if (binding.data.facts.read(binding.place)?.state != ValueState.INITIALIZED) {
            LogProcessor.error("Cannot iterate a list without an initialized producer")
            return null
        }
        if (!hasRuntimeRepresentation(value)) {
            LogProcessor.error("Compiler-only list iteration requires a closed element sequence")
            return null
        }
        binding.data.materialize(binding.place)
        val score = StorageLayout.Scoreboard(TempPool.getVarIdentify(), top.mcfpp.lib.SbObject.MCFPP_TEMP.toString())
        emit(Command("execute store result score ${score.player} ${score.objective} run data get")
            .build(binding.path.toCommandPart()))
        return publishScore(MCInt(), score)
    }

    fun iterationElement(value: Var<*>, index: MCInt): Var<*> {
        val type = when (val declared = value.type) {
            is top.mcfpp.type.MCFPPListType -> declared.generic[0]
            is top.mcfpp.type.MCFPPImmutableListType -> declared.generic[0]
            else -> return error(value.type, "Value is not an iterable list")
        }
        return read(element(value, index, type))
    }

    fun closedIterationElements(value: Var<*>): List<Var<*>>? {
        if (hasRuntimeRepresentation(value)) return null
        val complete = snapshot(value) ?: return null
        val sequence = (if (complete is CompilerValue.Typed) complete.payload else complete) as? CompilerValue.Sequence ?: return null
        val elementType = when (val declared = value.type) {
            is MCFPPListType -> declared.generic.single()
            is MCFPPImmutableListType -> declared.generic.single()
            else -> return null
        }
        return sequence.elements.mapIndexed { index, part ->
            restore(elementType, part, "${value.identifier}_$index", boundTypes(value)) ?: return null
        }
    }

    /** Allocates the declaration of a return slot without publishing a result. */
    fun declareReturnSlot(value: Var<*>): StorageBinding {
        if (value.storageBinding == null) declare(value, Symbol(SymbolId.fresh(), value.identifier,
            value.type.typeId, mutable = true))
        return ensure(value)
    }

    /** Return exits share the same facts as assignments; missing exits are not producers. */
    fun finishReturns(value: Var<*>, exits: List<Map<StoredData, FlowFacts>>): Boolean {
        val binding = value.storageBinding ?: return false
        if (exits.isEmpty()) return false
        val merged = joinFlow(exits)
        val fact = merged[binding.data]?.read(binding.place) ?: return false
        if (fact.state != ValueState.INITIALIZED) return false
        restoreFlow(merged)
        return true
    }

    fun hasActualPayload(value: Var<*>): Boolean {
        if (snapshot(value) != null) return true
        val binding = value.storageBinding ?: return false
        if (binding.data.facts.read(binding.place)?.state != ValueState.INITIALIZED) return false
        if (selectorProgram(binding) != null) return true
        val actual = actualType(value)
        if (hasRuntimeRepresentation(value)) return true
        if (actual !is MCFPPDataTemplateType) return false
        return actual.instanceFields.all { field ->
            val child = adapter(field.type, field.identifier, binding.field(field.identifier))
            field.nullable && binding.data.facts.read(child.storageBinding!!.place) == null || hasActualPayload(child)
        }
    }

    fun actualView(value: Var<*>): Var<*> {
        val binding = value.storageBinding ?: return error(value.type, "Value has no initialized producer")
        val actual = (binding.data.facts.read(binding.place)?.type as? TypeKnowledge.Exact)?.type
            ?.let(binding.data.types::get)
            ?: return error(value.type, "Actual type is unknown; use 'as' before a concrete operation")
        if (actual in erasedTypes) return error(value.type, "Erased type has no concrete producer evidence")
        return read(view(value, actual, diagnose = false))
    }

    fun selectorProgram(binding: StorageBinding): top.mcfpp.lib.SelectorExpression? {
        val value = binding.data.facts.read(binding.place)?.value
        if (value is ValueKnowledge.Program) return value.expression
        var closed = (value as? ValueKnowledge.Constant)?.value ?: return null
        while (closed is CompilerValue.Typed && closed.payload is CompilerValue.Typed) closed = closed.payload
        return (closed as? CompilerValue.Typed)?.let(top.mcfpp.lib.SelectorExpression::restore)
    }

    fun capturePayload(type: MCFPPType, source: Var<*>, name: String,
                       sourceFrameOffset: Int = 0, targetFrame: Int = 0): Var<*>? {
        val binding = source.storageBinding ?: return null
        val expression = selectorProgram(binding)
        if (expression == null) {
            if (source.type != type && !source.type.isSubOf(type) && type !in erasedTypes) return null
            val closed = snapshot(source)
            if (closed == null) {
                val actual = actualType(source) as? MCFPPDataTemplateType ?: return null
                if (!hasActualPayload(source)) return null
                val result = type.buildUnConcrete(name)
                val target = declare(result, Symbol(SymbolId.fresh(), name, type.typeId, mutable = true))
                target.data.retainCompilerValue()
                target.data.types.putAll(binding.data.types)
                target.data.types[actual.typeId] = actual
                target.data.path = NBTPath.stack.intIndex(targetFrame).memberIndex(name)
                target.data.write(target.place, ValueFacts(TypeKnowledge.Exact(actual.typeId), ValueKnowledge.Unknown))
                if (actual.instanceFields.any { it.type.hasRuntimeRepresentation }) emit(Commands.dataSetValue(target.path, CompoundTag()))
                for (field in actual.instanceFields) {
                    val original = inFrame(binding, sourceFrameOffset).field(field.identifier)
                    if (original.data.facts.read(original.place)?.state != ValueState.INITIALIZED) continue
                    val input = adapter(field.type, field.identifier, original)
                    val destination = target.field(field.identifier)
                    if (hasRuntimeRepresentation(input)) {
                        target.data.registerLayout(destination.place, StorageLayout.Nbt(destination.path.source.toString(), destination.path.toCommandPart().toString()))
                        encodeTo(destination.path, input)
                        target.data.facts.copyFrom(binding.data.facts, original.place, destination.place)
                    } else {
                        val captured = capturePayload(field.type, input, TempPool.getVarIdentify(), 0, targetFrame) ?: return null
                        val child = captured.storageBinding ?: return null
                        target.data.registerLayout(destination.place, StorageLayout.CompilerOnly)
                        target.data.types.putAll(child.data.types)
                        target.data.inheritSources(child.data)
                        target.data.facts.copyFrom(child.data.facts, child.place, destination.place)
                    }
                }
                return adapter(type, name, target)
            }
            val result = captureClosed(source, closed, name) ?: return null
            val captured = ensure(result)
            val formal = type.buildUnConcrete(name)
            val target = declare(formal, Symbol(SymbolId.fresh(), name, type.typeId, mutable = true))
            target.data.retainCompilerValue()
            target.data.path = NBTPath.stack.intIndex(targetFrame).memberIndex(name)
            target.data.types.putAll(captured.data.types)
            target.data.types.putIfAbsent(type.typeId, type)
            target.data.facts.copyFrom(captured.data.facts, captured.place, target.place)
            return adapter(type, name, target)
        }
        if (type !is MCFPPEntityType && type !in erasedTypes) return null
        val actualSource = inFrame(binding, sourceFrameOffset)
        val selector = expression.selector(binding.data.types, actualSource) ?: return null
        val symbol = Symbol(SymbolId.fresh(), name, type.typeId, mutable = true)
        val place = Place(symbol.id)
        val path = NBTPath.stack.intIndex(targetFrame).memberIndex(name)
        val data = StoredData(place, path, layout = StorageLayout.CompilerOnly)
        val copied = top.mcfpp.lib.SelectorExpression.capture(selector, data, actualFrame = targetFrame) ?: return null
        data.declarations[place] = symbol
        EffectAnalysis.declared(place)
        data.types.putAll(binding.data.types)
        data.types.putIfAbsent(source.type.typeId, source.type)
        data.types.putIfAbsent(type.typeId, type)
        data.facts.initialize(place, ValueFacts(TypeKnowledge.Exact(source.type.typeId),
            ValueKnowledge.Program(copied)))
        return adapter(type, name, StorageBinding(data, place, path,
            view = ValueRef.TypedView(type.typeId, ValueRef.Read(source.type.typeId, place), place))).apply {
            this.symbol = symbol
        }
    }

    fun rebasePayload(value: Var<*>, frameOffset: Int): Var<*> {
        val binding = value.storageBinding ?: return value
        return adapter(value.type, value.identifier, inFrame(binding, frameOffset)).apply { symbol = value.symbol }
    }

    fun binary(left: Var<*>, right: Var<*>, operation: String): Var<*> {
        if (left.isError) return left
        if (right.isError) return right
        if (operation in setOf("==", "!=") && (left.type == MCFPPPrivateType.Null || right.type == MCFPPPrivateType.Null)) {
            val other = if (left.type == MCFPPPrivateType.Null) right else left
            fun nullPayload(value: CompilerValue): Boolean = value == CompilerValue.NullValue ||
                value is CompilerValue.Typed && nullPayload(value.payload)
            val closed = snapshot(other)
            if (closed != null) return literal(MCFPPBaseType.Bool,
                CompilerValue.Bool(nullPayload(closed) == (operation == "==")))
            if (!other.nullable) return literal(MCFPPBaseType.Bool, CompilerValue.Bool(operation == "!="))
            val source = ensure(other)
            if (!hasRuntimeRepresentation(other)) {
                LogProcessor.error("Nullable compiler-only value has no runtime presence representation")
                return UnknownVar(other.identifier).apply { type = MCFPPBaseType.Bool; isError = true }
            }
            source.data.materialize(source.place)
            val result = ScoreBool()
            emit(Command("scoreboard players set ${result.name} ${result.boolObject} 0"))
            emit(Command.buildAll("execute", if (operation == "==") "unless" else "if", "data", source.path,
                "run scoreboard players set ${result.name} ${result.boolObject} 1"))
            return publishBoolean(result, StorageLayout.Scoreboard(result.name, result.boolObject.toString()))
        }
        if (left.type == right.type && left.type in setOf(MCFPPNBTType.Byte, MCFPPNBTType.Short)) {
            val first = top.mcfpp.backend.NumericConversions.convert(left, MCFPPBaseType.Int)
            val second = top.mcfpp.backend.NumericConversions.convert(right, MCFPPBaseType.Int)
            if (first.isError) return first
            if (second.isError) return second
            val result = binary(first, second, operation)
            return if (result.isError || operation !in setOf("+", "-", "*", "/", "%")) result
                else top.mcfpp.backend.NumericConversions.convert(result, left.type)
        }
        val a = (snapshot(left) as? CompilerValue.Typed)?.payload
        val b = (snapshot(right) as? CompilerValue.Typed)?.payload
        if (left.symbol?.forceRuntime != true && right.symbol?.forceRuntime != true &&
            left.type == right.type && left.type in setOf(MCFPPBaseType.Int, MCFPPBaseType.Float, MCFPPBaseType.Bool) && a != null && b != null) {
            PrimitiveEvaluation.binary(operation, a, b)?.let { result ->
                return literal(if (result is CompilerValue.Bool) MCFPPBaseType.Bool else left.type, result)
            }
        }
        return left.binaryComputation(right, operation)
    }

    fun unary(value: Var<*>, operation: String): Var<*> {
        if (value.isError) return value
        if (value.type in setOf(MCFPPNBTType.Byte, MCFPPNBTType.Short) && operation in setOf("+", "-")) {
            val widened = top.mcfpp.backend.NumericConversions.convert(value, MCFPPBaseType.Int)
            if (widened.isError) return widened
            val result = unary(widened, operation)
            return if (result.isError) result else top.mcfpp.backend.NumericConversions.convert(result, value.type)
        }
        val closed = (snapshot(value) as? CompilerValue.Typed)?.payload
        if (value.symbol?.forceRuntime != true && value.type in setOf(MCFPPBaseType.Int, MCFPPBaseType.Float, MCFPPBaseType.Bool) && closed != null)
            PrimitiveEvaluation.unary(operation, closed)?.let { return literal(value.type, it) }
        if (value is MCFloat && operation == "-") return value.negate()
        if (value is MCInt && operation == "-") return binary(MCInt(0), value, "-")
        return value.unaryComputation(operation)
    }
    /** Literals own immutable facts before any runtime representation is allocated. */
    fun literal(type: MCFPPType, value: CompilerValue, name: String = TempPool.getVarIdentify(), types: Map<TypeId, MCFPPType> = emptyMap()): Var<*> {
        val payload = (value as? CompilerValue.Typed)?.payload ?: value
        if (type == MCFPPBaseType.Float && payload is CompilerValue.FloatBits && !Float.fromBits(payload.bits).isFinite()) {
            LogProcessor.error("Float values must be finite")
            return UnknownVar(name).apply { this.type = type; isError = true }
        }
        val adapter = if (type == MCFPPPrivateType.Null) Null else type.buildUnConcrete(name)
        return initializeLiteral(adapter, value, types)
    }

    fun <T : Var<*>> initializeLiteral(adapter: T, value: CompilerValue, types: Map<TypeId, MCFPPType> = emptyMap()): T {
        val type = adapter.type
        val name = adapter.identifier
        val closed = if (value is CompilerValue.Typed) {
            require(value.type == type.typeId || type in erasedTypes || type is MCFPPUnionType &&
                type.types.any { it.typeId == value.type }) { "Literal type does not match its payload" }
            if (value.type == type.typeId) value else CompilerValue.Typed(type.typeId, value)
        } else CompilerValue.Typed(type.typeId, value)
        val symbol = Symbol(SymbolId.fresh(), name, type.typeId, mutable = false, isLiteral = true)
        val place = Place(symbol.id)
        val data = StoredData(place, adapter.nbtPath.clone(), layout = StorageLayout.Constant)
        data.types.putAll(types)
        data.types[type.typeId] = type
        data.declarations[place] = symbol
        MCFPPType.registerSnapshotTypes(closed, data.types)
        EffectAnalysis.declared(place)
        var actual = closed
        while ((actual.type in erasedTypes.map { it.typeId } || actual.type == type.typeId && type is MCFPPUnionType) && actual.payload is CompilerValue.Typed)
            actual = actual.payload as CompilerValue.Typed
        data.facts.initialize(place, ValueFacts(TypeKnowledge.Exact(actual.type), ValueKnowledge.Constant(closed)))
        adapter.symbol = symbol
        adapter.storageBinding = StorageBinding(data, place, data.path)
        seedSnapshot(data, place, closed)
        return adapter
    }

    private fun seedSnapshot(data: StoredData, place: Place, value: CompilerValue) {
        var payload = value
        while (payload is CompilerValue.Typed) payload = payload.payload
        if (payload is CompilerValue.Sequence) data.facts.setLength(place, payload.elements.size)
        val children = when (payload) {
            is CompilerValue.Sequence -> payload.elements.mapIndexed { index, child -> place.index(index) to child }
            is CompilerValue.Record -> payload.fields.map { (key, child) -> place.field(key) to child }
            else -> emptyList()
        }
        for ((childPlace, child) in children) {
            var actual = child
            while (actual is CompilerValue.Typed && actual.type in erasedTypes.map { it.typeId } && actual.payload is CompilerValue.Typed)
                actual = actual.payload
            val type = (actual as? CompilerValue.Typed)?.type
            data.facts.initialize(childPlace, ValueFacts(type?.let(TypeKnowledge::Exact) ?: TypeKnowledge.Unknown,
                ValueKnowledge.Constant(child)))
            seedSnapshot(data, childPlace, child)
        }
    }

    /** Descriptor provenance belongs to the existing binding, never a process-wide cache. */
    fun boundTypes(value: Var<*>): Map<TypeId, MCFPPType> = value.storageBinding?.data?.types.orEmpty()

    fun resolveTypeValue(value: Var<*>): MCFPPType? {
        var snapshot = snapshot(value) ?: return null
        while (snapshot is CompilerValue.Typed) snapshot = snapshot.payload
        val id = (snapshot as? CompilerValue.TypeValue)?.id ?: return null
        return boundTypes(value)[id] ?: MCFPPType.resolveTypeId(id)
    }

    /** Element accesses are consumed now; no mutable adapter is retained by the collection. */
    fun listLiteral(type: MCFPPType, elements: List<Var<*>>, name: String = TempPool.getVarIdentify()): Var<*> {
        val values = elements.map { top.mcfpp.analysis.StorageAccess.snapshot(it) }
        val types = linkedMapOf<TypeId, MCFPPType>()
        elements.forEach { types.putAll(boundTypes(it)); types[it.type.typeId] = it.type }
        if (values.all { it != null }) return literal(type, CompilerValue.Sequence(values.filterNotNull()), name, types)
        if (elements.any { it.isError || !hasRuntimeRepresentation(it) }) {
            LogProcessor.error("Runtime list literal requires physically encodable elements")
            return UnknownVar(name).apply { this.type = type }
        }
        val result = type.buildUnConcrete(name)
        val binding = ensure(result)
        emit(Commands.dataSetValue(binding.path, ListTag()))
        publishNbt(result)
        binding.data.types.putAll(types)
        elements.forEachIndexed { index, element ->
            val captured = capture(element)
            if (captured.isError) { result.isError = true; return result }
            val source = ensure(captured)
            source.data.materialize(source.place)
            emit(Command.buildAll("data modify", binding.path, "append from", source.path))
            binding.data.facts.copyFrom(source.data.facts, source.place, binding.place.index(index))
            binding.data.inheritSources(source.data)
        }
        binding.data.facts.setLength(binding.place, elements.size)
        return result
    }

    fun dictionaryLiteral(type: MCFPPType, elements: Map<String, Var<*>>, name: String = TempPool.getVarIdentify()): Var<*> {
        val values = elements.mapValues { top.mcfpp.analysis.StorageAccess.snapshot(it.value) }
        val types = linkedMapOf<TypeId, MCFPPType>()
        elements.values.forEach { types.putAll(boundTypes(it)); types[it.type.typeId] = it.type }
        if (values.values.all { it != null }) return literal(type,
            CompilerValue.Record(values.mapValues { it.value!! }), name, types)
        if (elements.values.any { it.isError || !hasRuntimeRepresentation(it) }) {
            LogProcessor.error("Runtime dictionary literal requires physically encodable fields")
            return UnknownVar(name).apply { this.type = type }
        }
        val result = type.buildUnConcrete(name)
        val binding = ensure(result)
        emit(Commands.dataSetValue(binding.path, CompoundTag()))
        publishNbt(result)
        binding.data.types.putAll(types)
        for ((key, element) in elements) {
            val captured = capture(element)
            if (captured.isError) { result.isError = true; return result }
            encodeTo(binding.path.memberIndex(key), captured)
            val source = ensure(captured)
            binding.data.facts.copyFrom(source.data.facts, source.place, binding.place.field(key))
            binding.data.inheritSources(source.data)
        }
        return result
    }

    fun rangeIterationBounds(value: Var<*>): Pair<Var<*>, Var<*>>? {
        if (value.type != MCFPPBaseType.Range) return null
        val root = ensure(value)
        val endpoints = listOf("left", "right").map { name ->
            val binding = root.field(name)
            val fact = binding.data.facts.read(binding.place)
            if (fact?.state != ValueState.INITIALIZED || fact.type != TypeKnowledge.Exact(MCFPPBaseType.Int.typeId)) {
                LogProcessor.error("Range iteration requires both initialized int endpoints")
                return null
            }
            adapter(MCFPPBaseType.Int, value.identifier + "_" + name, binding)
        }
        return endpoints[0] to endpoints[1]
    }

    fun intRegister(value: MCInt): StorageLayout.Scoreboard {
        val binding = ensure(value)
        val primary = binding.data.layout
        if (primary is StorageLayout.Scoreboard && binding.place == binding.data.root && binding.location.frameOffset == 0) return primary
        val register = binding.data.register(binding.place, value.type.typeId, value.sbObject.toString(), binding.location)
        if (!binding.data.versions.isMaterialized(binding.place, register, Function.currFunction, binding.location)) {
            val constant = ((snapshot(value) as? CompilerValue.Typed)?.payload as? CompilerValue.Integral)?.value
            if (constant != null) emit(Command("scoreboard players set ${register.player} ${register.objective} ${constant.toInt()}"))
            else { binding.data.materialize(binding.place); loadScore(binding, register) }
            binding.data.versions.materialize(binding.place, register, Function.currFunction, binding.location)
        }
        return register
    }

    fun publishScore(value: MCInt, score: StorageLayout.Scoreboard): MCInt {
        val symbol = Symbol(SymbolId.fresh(), value.identifier, value.type.typeId, mutable = true)
        val place = Place(symbol.id)
        val path = NBTPath.stack.intIndex(0).memberIndex(value.identifier)
        val data = StoredData(place, path, scoreWriter(path, score.player, score.objective, scoreTag(value.type.typeId)), score)
        data.types[value.type.typeId] = value.type
        data.declarations[place] = symbol
        data.facts.initialize(place, ValueFacts(TypeKnowledge.Exact(value.type.typeId), ValueKnowledge.Unknown))
        value.symbol = symbol
        value.storageBinding = StorageBinding(data, place, path)
        value.name = score.player
        EffectAnalysis.declared(place)
        return value
    }

    /** Called only after commands have produced the value at its allocated NBT location. */
    fun <T : Var<*>> publishNbt(value: T): T {
        val binding = value.storageBinding ?: run {
            check(value.nbtPath.pathList.isNotEmpty()) { "NBT producer has no emitted address" }
            val symbol = value.symbol ?: Symbol(SymbolId.fresh(), value.identifier, value.type.typeId, mutable = true)
            value.symbol = symbol
            val place = Place(symbol.id)
            val data = StoredData(place, value.nbtPath.clone())
            data.declarations[place] = symbol
            EffectAnalysis.declared(place)
            StorageBinding(data, place, data.path).also { value.storageBinding = it }
        }
        binding.data.types[value.type.typeId] = value.type
        binding.data.write(binding.place, ValueFacts(TypeKnowledge.Exact(value.type.typeId), ValueKnowledge.Unknown))
        return value
    }

    fun publishBoolean(value: ScoreBool, score: StorageLayout.Scoreboard): ScoreBool {
        val symbol = Symbol(SymbolId.fresh(), value.identifier, value.type.typeId, mutable = true)
        val place = Place(symbol.id)
        val path = NBTPath.stack.intIndex(0).memberIndex(value.identifier)
        val data = StoredData(place, path, scoreWriter(path, score.player, score.objective, "byte"), score)
        data.types[value.type.typeId] = value.type
        data.declarations[place] = symbol
        data.facts.initialize(place, ValueFacts(TypeKnowledge.Exact(value.type.typeId), ValueKnowledge.Unknown))
        value.name = score.player
        value.symbol = symbol
        value.storageBinding = StorageBinding(data, place, path)
        EffectAnalysis.declared(place)
        return value
    }

    fun booleanRegister(value: ScoreBool): StorageLayout.Scoreboard {
        val proxy = MCInt(value.identifier).apply { type = value.type; storageBinding = ensure(value) }
        return intRegister(proxy)
    }

    fun publishLegacyFloat(value: MCFloat, components: List<StorageLayout.Scoreboard>): MCFloat {
        require(components.size == 4)
        val symbol = Symbol(SymbolId.fresh(), value.identifier, value.type.typeId, mutable = true)
        val place = Place(symbol.id)
        val path = NBTPath.stack.intIndex(0).memberIndex(value.identifier)
        val layout = StorageLayout.LegacyFloat(components.toList())
        val writer = {
            emit(Commands.dataSetValue(path, CompoundTag()))
            listOf("sign", "int0", "int1", "exp").zip(components).forEach { (key, score) ->
                scoreWriter(path.memberIndex(key), score.player, score.objective, "int")()
            }
        }
        val data = StoredData(place, path, writer, layout)
        data.types[value.type.typeId] = value.type
        data.declarations[place] = symbol
        data.facts.initialize(place, ValueFacts(TypeKnowledge.Exact(value.type.typeId), ValueKnowledge.Unknown))
        data.versions.materialize(place, layout, Function.currFunction)
        value.symbol = symbol
        value.storageBinding = StorageBinding(data, place, path)
        value.nbtPath = path.clone()
        EffectAnalysis.declared(place)
        return value
    }

    fun legacyFloatRegisters(value: MCFloat): List<StorageLayout.Scoreboard>? {
        val binding = ensure(value)
        if (binding.data.facts.read(binding.place)?.state != ValueState.INITIALIZED) {
            LogProcessor.error("Legacy float has no initialized producer")
            value.isError = true
            return null
        }
        val data = binding.data
        val primary = data.layout as? StorageLayout.LegacyFloat
        val components = if (primary != null && binding.place == data.root && binding.location.frameOffset == 0) primary.components else
            listOf("sign", "int0", "int1", "exp").map { key ->
                val field = binding.field(key)
                data.register(field.place, MCFPPBaseType.Int.typeId, SbObject.MCFPP_TEMP.toString(), field.location)
            }
        val layout = StorageLayout.LegacyFloat(components)
        if (!data.versions.isMaterialized(binding.place, layout, Function.currFunction, binding.location)) {
            data.materialize(binding.place)
            listOf("sign", "int0", "int1", "exp").zip(components).forEach { (key, score) ->
                loadScore(binding.field(key), score)
            }
            data.versions.materialize(binding.place, layout, Function.currFunction, binding.location)
        }
        return components
    }

    fun declare(value: Var<*>, symbol: Symbol): StorageBinding {
        val previous = value.storageBinding
        check(previous == null || value.symbol?.isLiteral == true) { "Declaration already has storage" }
        val initializer = previous?.let { constantFor(value.type, it) }
        value.symbol = symbol
        val place = Place(symbol.id)
        val data = StoredData(place, value.nbtPath.clone(), layout = StorageLayout.Constant)
        data.types[value.type.typeId] = value.type
        data.declarations[place] = symbol
        previous?.data?.types?.let(data.types::putAll)
        data.facts.initialize(place, ValueFacts(TypeKnowledge.Exact(value.type.typeId),
            initializer?.let(ValueKnowledge::Constant) ?: ValueKnowledge.Unknown,
            if (initializer == null) ValueState.UNINITIALIZED else ValueState.INITIALIZED))
        initializer?.let { seedSnapshot(data, place, it) }
        EffectAnalysis.declared(place)
        return StorageBinding(data, place, data.path).also { value.storageBinding = it }
    }

    /** Selector mutation updates known structure while runtime operands remain unknown values. */
    fun updateSelector(value: top.mcfpp.core.lang.entity.SelectorVar, selector: top.mcfpp.lib.EntitySelector = value.value) {
        val existing = value.storageBinding
        val binding = ensure(value)
        val expression = (top.mcfpp.lib.SelectorExpression.capture(selector, binding.data, selectorProgram(binding),
                top.mcfpp.lib.SelectorExpression.frame(binding.path))) ?: run {
            LogProcessor.error("Selector operands cannot be captured in the current storage layout")
            return
        }
        binding.data.types[value.type.typeId] = value.type
        binding.data.write(binding.place, ValueFacts(TypeKnowledge.Exact(value.type.typeId),
            ValueKnowledge.Program(expression)))
        EffectAnalysis.recordWrite(binding.place, contents = true)
        value.storageReadVersion = null
    }

    /** Freeze a readonly payload into an independent compiler-only declaration. */
    internal fun freezeReadonly(value: Var<*>, name: String): Var<*>? {
        val snapshot = top.mcfpp.analysis.StorageAccess.snapshot(value) ?: return null
        val types = HashMap(value.storageBinding?.data?.types.orEmpty())
        types.putIfAbsent(value.type.typeId, value.type)
        if (value is MCFPPTypeVar) types[value.value.typeId] = value.value
        MCFPPType.registerSnapshotTypes(snapshot, types)
        val frozen = restore(value.type, snapshot, name, types) ?: return null
        frozen.isConst = true
        frozen.isStatic = true
        val declaration = Symbol(SymbolId.fresh(), name, frozen.type.typeId, mutable = false, readonly = true)
        frozen.symbol = declaration
        val place = Place(declaration.id)
        val data = StoredData(place, frozen.nbtPath.clone(), layout = StorageLayout.CompilerOnly)
        data.types.putAll(types)
        data.declarations[place] = declaration
        data.facts.initialize(place, ValueFacts(TypeKnowledge.Exact((snapshot as? CompilerValue.Typed)?.type
            ?: frozen.type.typeId), ValueKnowledge.Constant(snapshot)))
        seedSnapshot(data, place, snapshot)
        frozen.storageBinding = StorageBinding(data, place, data.path)
        return frozen
    }

    private val erasedTypes get() = setOf(MCFPPBaseType.Any, MCFPPBaseType.Object)

    /** Runtime parameters arrive in the callee frame; no writer may capture an uninitialized prototype register. */
    fun bindIncomingParameter(value: Var<*>): StorageBinding {
        val binding = value.storageBinding ?: bindStoredValue(value)
        if (value.symbol?.readonly == true) return binding
        binding.data.types[value.type.typeId] = value.type
        fun readableLayout(type: MCFPPType): TypeId? = (binding.data.types[type.typeId] ?: type)
            .takeIf { it is MCFPPDataTemplateType && it.hasRuntimeRepresentation }?.typeId
        binding.data.facts.forgetDescendants(binding.place)
        binding.data.facts.refine(binding.place, ValueFacts(if (value.type in erasedTypes) TypeKnowledge.Unknown
            else TypeKnowledge.Exact(value.type.typeId), ValueKnowledge.Unknown, ValueState.INITIALIZED,
            readableLayout = readableLayout(value.type)))
        fun fields(type: MCFPPType, parent: StorageBinding, seen: Set<TypeId> = emptySet()) {
            if (type !is MCFPPDataTemplateType) return
            if (type.typeId in seen) return
            for (field in type.instanceFields) {
                val child = parent.field(field.identifier)
                registerMember(field, child)
                binding.data.facts.refine(child.place, ValueFacts(if (field.type in erasedTypes) TypeKnowledge.Unknown
                    else TypeKnowledge.Exact(field.type.typeId), ValueKnowledge.Unknown, ValueState.INITIALIZED,
                    readableLayout = readableLayout(field.type)))
                fields(field.type, child, seen + type.typeId)
            }
        }
        fields(value.type, binding)
        return binding
    }

    fun bindStaticField(value: Var<*>) {
        val ownerPath = value.nbtPath.clone()
        val binding = ensure(value)
        if (binding.place != binding.data.root || binding.path == ownerPath) return
        val data = binding.data
        val layout = when {
            !value.type.hasRuntimeRepresentation -> StorageLayout.CompilerOnly
            data.layout is StorageLayout.Scoreboard -> data.layout
            else -> StorageLayout.Nbt(ownerPath.source.toString(), ownerPath.toCommandPart().toString())
        }
        val writer = if (layout is StorageLayout.Scoreboard)
            scoreWriter(ownerPath, layout.player, layout.objective, scoreTag(value.type.typeId))
        else (data.facts.read(data.root)?.value as? ValueKnowledge.Constant)?.value?.let(::snapshotTag)
            ?.let(NbtEncoding::snbt)?.let { encoded ->
                { emit(Command.buildAll("data modify", ownerPath, "set value", encoded)) }
            }?.takeIf { layout != StorageLayout.CompilerOnly }
        data.relocate(ownerPath, layout, writer)
        value.storageReadVersion = null
    }

    private fun bindStoredValue(value: Var<*>): StorageBinding {
        value.storageBinding?.let { return it }
        value.bindDeclaration()
        value.storageBinding?.let { return it }
        if (value.nbtPath.pathList.isEmpty()) value.nbtPath = NBTPath.getNormalStackPath(value)
        val incomingPath = value.nbtPath.clone()
        val binding = declare(value, value.symbol!!)
        if (value.type.hasRuntimeRepresentation) binding.data.allocate(incomingPath,
            StorageLayout.Nbt(incomingPath.source.toString(), incomingPath.toCommandPart().toString()), null)
        else binding.data.retainCompilerValue()
        return binding
    }

    /** Allocate a runtime receiver without pretending erased defaults are complete constants. */
    internal fun initializeTemplateReceiver(value: DataTemplateObject) {
        val binding = ensure(value)
        if (value.type.hasRuntimeRepresentation || (value.type as? MCFPPDataTemplateType)?.instanceFields?.any { it.type.hasRuntimeRepresentation } == true)
            emit(Commands.dataSetValue(binding.path, CompoundTag()))
        binding.data.write(binding.place, ValueFacts(TypeKnowledge.Exact(value.type.typeId), ValueKnowledge.Unknown))
        binding.data.facts.forgetDescendants(binding.place)
        for (field in (value.type as MCFPPDataTemplateType).instanceFields) {
            val child = binding.field(field.identifier)
            registerMember(field, child)
            binding.data.facts.refine(child.place, ValueFacts(TypeKnowledge.Exact(field.type.typeId),
                ValueKnowledge.Unknown, ValueState.UNINITIALIZED))
        }
    }

    internal fun registerMember(declaration: Var<*>, binding: StorageBinding) {
        binding.data.types.putIfAbsent(declaration.type.typeId, declaration.type)
        binding.data.declarations.putIfAbsent(binding.place, Symbol(binding.place.root, declaration.identifier,
            declaration.type.typeId, mutable = !declaration.isConst, readonly = declaration.symbol?.readonly == true))
        val actual = (binding.data.facts.read(binding.place)?.type as? TypeKnowledge.Exact)?.type
            ?.let(binding.data.types::get)
        if (binding.data.layout == StorageLayout.CompilerOnly && declaration.type.hasRuntimeRepresentation &&
            actual?.hasRuntimeRepresentation != false)
            binding.data.registerLayout(binding.place, StorageLayout.Nbt(binding.path.source.toString(), binding.path.toCommandPart().toString()), replace = false)
    }

    fun ensure(value: Var<*>): StorageBinding {
        value.storageBinding?.let { existing ->
            if (existing.data.layout != StorageLayout.Constant) return existing
            val data = existing.data
            val rootFact = data.facts.read(data.root)
            val rootValue = (rootFact?.value as? ValueKnowledge.Constant)?.value
            if (!value.type.hasRuntimeRepresentation || rootValue != null && !runtimeSnapshot(rootValue, data.types)) {
                data.retainCompilerValue()
                data.path = NBTPath.stack.intIndex(0).memberIndex(value.symbol?.name ?: value.identifier)
                return existing
            }
            val rootName = value.symbol?.name ?: value.identifier
            val rootPath = NBTPath.stack.intIndex(0).memberIndex(rootName)
            val frozen = (rootFact?.value as? ValueKnowledge.Constant)?.value?.let(::snapshotTag)
                ?.let(NbtEncoding::snbt)
            val writer: (() -> Unit)? = frozen?.let { encoded ->
                { emit(Command.buildAll("data modify", rootPath, "set value", encoded)) }
            }
            data.allocate(rootPath, StorageLayout.Nbt(rootPath.source.toString(), rootPath.toCommandPart().toString()), writer)
            var path = rootPath
            for (segment in existing.place.path) path = when (segment) {
                is PathSegment.Field -> path.memberIndex(segment.name)
                is PathSegment.Index -> path.intIndex(segment.index)
                PathSegment.UnknownIndex -> return error(value.type, "A dynamic access requires its captured location").storageBinding ?: existing
            }
            return existing.copy(path = path).also { value.storageBinding = it; value.nbtPath = path.clone() }
        }
        if (value.identifier.isBlank()) value.identifier = TempPool.getVarIdentify()
        val symbol = value.symbol ?: Symbol(SymbolId.fresh(), value.identifier, value.type.typeId, mutable = true)
        declare(value, symbol)
        return ensure(value)
    }

    internal fun seedParts(data: StoredData, parent: Place, value: Var<*>) {
        data.types[value.type.typeId] = value.type
        data.types.putAll(boundTypes(value))
        snapshot(value)?.let { seedSnapshot(data, parent, it) }
    }

    private fun emit(command: Command) {
        if (command.isMacro && top.mcfpp.command.TargetCapabilities.forVersion(top.mcfpp.Project.config.version)?.functionMacros != true) {
            LogProcessor.error("Target '${top.mcfpp.Project.config.version}' cannot access a runtime index without function macros")
            return
        }
        Function.addCommands(command.buildMacroFunction())
    }

    /** Freeze source codecs/addresses, including partially known containers, before delayed materialization. */
    private fun frozenWriter(value: Var<*>, path: NBTPath): () -> Unit {
        if (!hasRuntimeRepresentation(value)) {
            LogProcessor.error("Compiler-only value '${actualType(value)}' cannot be stored in a runtime collection")
            return {}
        }
        if (!collectionEncodingSupported(value)) { reportListEncoding(); return {} }
        constantEncoding(value)?.let { tag ->
            val snbt = top.mcfpp.backend.NbtEncoding.snbt(tag)
            return { emit(Command.buildAll("data modify", path, "set value", snbt)) }
        }
        value.storageBinding?.let { binding ->
            val source = binding.path.clone()
            val data = binding.data
            return { data.materialize(binding.place); emit(Commands.dataSetFrom(path, source)) }
        }
        LogProcessor.error("Cannot encode a value without an initialized physical producer")
        return {}
    }

    private fun copyWriter(destination: NBTPath, source: NBTPath): () -> Unit {
        val path = source.clone()
        return { emit(Commands.dataSetFrom(destination, path)) }
    }

    fun quotedKey(key: String) = if (key.matches(Regex("[A-Za-z0-9_+-]+"))) key
        else top.mcfpp.backend.NbtEncoding.snbt(top.mcfpp.nbt.tags.primitive.StringTag(key))

    fun element(container: Var<*>, index: Var<*>, type: MCFPPType): Var<*> {
        val payload = snapshot(index).let { if (it is CompilerValue.Typed) it.payload else it }
        val number = (payload as? CompilerValue.Integral)?.value?.toInt()
        val key = when (payload) {
            is CompilerValue.Text -> payload.value
            is CompilerValue.Nbt -> (NbtEncoding.parse(payload.snbt) as? top.mcfpp.nbt.tags.primitive.StringTag)?.value
            else -> null
        }
        if (index is MCString && key == null) {
            LogProcessor.error("Cannot generate dictionary access with an unknown string key: no verified NBT-path escaping backend is available")
            return UnknownVar(TempPool.getVarIdentify()).apply { isError = true }
        }
        if (index is MCInt && number == null && top.mcfpp.command.TargetCapabilities.forVersion(top.mcfpp.Project.config.version)?.functionMacros != true) {
            LogProcessor.error("Target '${top.mcfpp.Project.config.version}' cannot access a runtime index without function macros")
            return UnknownVar(TempPool.getVarIdentify()).apply { isError = true }
        }
        if (key?.isEmpty() == true && hasRuntimeRepresentation(container) && top.mcfpp.command.TargetCapabilities
                .forVersion(top.mcfpp.Project.config.version)?.emptyNbtPathKeys != true) {
            LogProcessor.error("Target '${top.mcfpp.Project.config.version}' cannot traverse an empty NBT path key")
            return UnknownVar(TempPool.getVarIdentify()).apply { isError = true }
        }
        val root = ensure(container)
        if (root.data.facts.readAccess(root.place, root.data.declarations, root.data.types)?.state != ValueState.INITIALIZED)
            return error(type, "Cannot access an uninitialized collection '${container.identifier}'")
        if (root.data.layout == StorageLayout.CompilerOnly && number == null && key == null) {
            LogProcessor.error("Compiler-only collection access requires a known constant index and element")
            return UnknownVar(TempPool.getVarIdentify()).apply { isError = true }
        }
        root.data.types[type.typeId] = type
        val size = root.data.facts.length(root.place)
        val normalized = number?.let { if (it < 0) size?.let { size -> size + it } else it }
        if (index is MCInt && normalized != null && size != null && normalized !in 0 until size) {
            LogProcessor.error("Index $number out of bounds for length $size")
            return UnknownVar(TempPool.getVarIdentify()).apply { isError = true }
        }
        val place = if (index is MCInt && normalized != null) root.place.index(normalized)
            else if (index is MCString && key != null) root.place.field(key) else root.place.unknownIndex()
        val selected = if (key != null || number != null) null else {
            // The evaluated index belongs to the caller's frame and survives later/recursive RHS calls.
            capture(index).also { if (!it.isError) ensure(it).data.materialize() }
        }
        if (selected?.isError == true) return selected
        val path = if (index is MCInt) if (selected != null) root.path.intIndex(selected as MCInt) else root.path.intIndex(number!!)
            else if (selected != null) root.path.memberIndex(selected as MCString) else root.path.memberIndex(quotedKey(key!!))
        if (root.data.facts.read(place) == null && key == null) {
            val actual = if (type in erasedTypes) root.data.facts.read(root.place.unknownIndex())?.type
                ?: if (place.path.last() == PathSegment.UnknownIndex) root.data.facts.children(root.place).values
                    .map { it.type }.reduceOrNull(TypeKnowledge::join) ?: TypeKnowledge.Unknown else TypeKnowledge.Unknown
                else TypeKnowledge.Exact(type.typeId)
            root.data.facts.initialize(place, ValueFacts(actual, ValueKnowledge.Unknown))
        }
        return adapter(type, TempPool.getVarIdentify(), root.copy(place = place, path = path)).apply { parent = container }
    }

    fun view(source: Var<*>, target: MCFPPType, diagnose: Boolean = true): Var<*> {
        if (source.isError) return source
        val compatibility = TypeRelations.checkReinterpretation(source.type, target)
        if (diagnose && compatibility is ReinterpretationCompatibility.Result.Unproven && (source !is MCAny || source is MCObject))
            LogProcessor.warn("Unproven reinterpretation from '${source.type}' to '$target': ${compatibility.reason}")
        if (!hasRuntimeRepresentation(source) && !hasActualPayload(source)) {
            return error(target, "Compiler-only value has no complete immutable value accessible as '$target'")
        }
        val binding = ensure(source)
        if (binding.data.layoutAt(binding.place) == StorageLayout.CompilerOnly && !staticLayoutAccessible(actualType(source), target) ||
            binding.data.layoutAt(binding.place) != StorageLayout.CompilerOnly && !target.hasRuntimeRepresentation)
            return error(target, "Source layout cannot be accessed as '$target'")
        val ref = ValueRef.TypedView(target.typeId, ValueRef.Read(source.type.typeId, binding.place), binding.place)
        val trusted = binding.trustConstants && (target in erasedTypes || binding.data.layoutAt(binding.place) == StorageLayout.CompilerOnly || actualType(source) == target ||
            source !is MCAny && compatibility is ReinterpretationCompatibility.Result.Compatible || closedMapEncoding(binding, target))
        val re = adapter(target, source.identifier, binding.copy(view = ref, trustConstants = trusted))
        re.symbol = source.symbol
        re.isConst = source.isConst
        if (trusted && snapshot(re) != null) {
            return read(re)
        }
        return re
    }

    /** A closed ordered entries payload proves the existing map encoding, not a language subtype. */
    private fun closedMapEncoding(binding: StorageBinding, target: MCFPPType): Boolean {
        if (target !is MCFPPMapType) return false
        var value = (binding.data.facts.read(binding.place)?.value as? ValueKnowledge.Constant)?.value ?: return false
        while (value is CompilerValue.Typed) value = value.payload
        val entries = (value as? CompilerValue.Record)?.fields?.get("entries") ?: return false
        fun payload(value: CompilerValue): CompilerValue = if (value is CompilerValue.Typed) payload(value.payload) else value
        val sequence = payload(entries) as? CompilerValue.Sequence ?: return false
        val elementType = target.generic.single()
        return sequence.elements.all { entry ->
            val fields = (payload(entry) as? CompilerValue.Record)?.fields ?: return@all false
            if (fields["key"]?.let(::payload) !is CompilerValue.Text) return@all false
            var member = fields["value"] as? CompilerValue.Typed ?: return@all false
            while (member.type in erasedTypes.map { it.typeId } && member.payload is CompilerValue.Typed)
                member = member.payload as CompilerValue.Typed
            elementType in erasedTypes || member.type == elementType.typeId ||
                binding.data.types[member.type]?.let { TypeRelations.resolveImplicitConversion(it, elementType) } == TypeRelations.Conversion.EXACT
        }
    }

    fun adapter(type: MCFPPType, name: String, binding: StorageBinding): Var<*> {
        // A view is only an access to existing evidence, never a second literal restoration.
        val value = type.buildUnConcrete(name)
        value.type = type
        value.symbol = binding.data.declarations[binding.place]
        value.storageBinding = if (binding.view != null) binding.copy(view = ValueRef.TypedView(type.typeId,
            if (binding.view.place == binding.place) binding.view.source else ValueRef.Read(
                (binding.data.facts.read(binding.place)?.type as? TypeKnowledge.Exact)?.type ?: type.typeId, binding.place),
            binding.place)) else binding
        value.nbtPath = binding.path.clone()
        return value
    }

    fun snapshot(value: Var<*>): CompilerValue? {
        val binding = value.storageBinding ?: return null
        return constantFor(value.type, binding, value is DataTemplateObject)
    }

    private fun constantFor(type: MCFPPType, binding: StorageBinding, template: Boolean = false): CompilerValue? {
        if (!binding.trustConstants) return null
        val fact = binding.data.facts.read(binding.place) ?: return null
        if (fact.state != ValueState.INITIALIZED) return null
        val delegated = binding.view != null && (fact.type as? TypeKnowledge.Exact)?.type
            ?.let(binding.data.types::get)?.let { it is MCFPPTypeDataTemplateType && it.typeAs == type } == true
        if (type !in erasedTypes && type !is MCFPPUnionType && fact.type != TypeKnowledge.Exact(type.typeId) && !template &&
            !delegated && !closedMapEncoding(binding, type) &&
            !(binding.data.layoutAt(binding.place) == StorageLayout.CompilerOnly && (fact.type as? TypeKnowledge.Exact)?.type
                ?.let(binding.data.types::get)?.let { staticLayoutAccessible(it, type) } == true)) return null
        val constant = when (val value = fact.value) {
            is ValueKnowledge.Constant -> value.value
            is ValueKnowledge.Program -> value.expression.snapshot((fact.type as? TypeKnowledge.Exact)?.type ?: type.typeId)
            else -> null
        } ?: run {
            if (binding.data.layoutAt(binding.place) != StorageLayout.CompilerOnly || type !is MCFPPDataTemplateType ||
                type is MCFPPTypeDataTemplateType) return null
            val fields = linkedMapOf<String, CompilerValue>()
            val producerType = (fact.type as? TypeKnowledge.Exact)?.type?.let(binding.data.types::get)
                as? MCFPPDataTemplateType ?: type
            for (field in producerType.instanceFields) {
                val child = binding.field(field.identifier)
                val childFact = binding.data.facts.read(child.place) ?: return null
                if (childFact.state != ValueState.INITIALIZED) {
                    if (field.nullable && childFact.state == ValueState.UNINITIALIZED) continue
                    return null
                }
                fields[field.identifier] = constantFor(field.type, child) ?: return null
            }
            CompilerValue.Typed(producerType.typeId, CompilerValue.Record(fields))
        }
        var represented = constant
        if (type !in erasedTypes) while (represented is CompilerValue.Typed &&
            represented.type in erasedTypes.map { it.typeId } && represented.payload is CompilerValue.Typed)
            represented = represented.payload
        if (represented is CompilerValue.Typed && represented.type == type.typeId) return represented
        if (type is MCFPPUnionType && represented is CompilerValue.Typed && type.types.any { it.typeId == represented.type })
            return represented
        if (type is MCFPPDataTemplateType && represented is CompilerValue.Typed &&
            binding.data.types[represented.type]?.isSubOf(type) == true) return represented
        val payload = if (type in erasedTypes) {
            if (represented is CompilerValue.Typed) represented else (fact.type as? TypeKnowledge.Exact)?.type
                ?.let { CompilerValue.Typed(it, represented) } ?: return null
        } else if (represented is CompilerValue.Typed) represented.payload else represented
        if (binding.data.layoutAt(binding.place) == StorageLayout.CompilerOnly && type is MCFPPDataTemplateType &&
            type !is MCFPPTypeDataTemplateType && payload is CompilerValue.Record) {
            val fields = linkedMapOf<String, CompilerValue>()
            for (field in type.instanceFields) {
                val part = payload.fields[field.identifier]
                if (part == null) {
                    if (field.nullable) continue
                    return null
                }
                fields[field.identifier] = part
            }
            return CompilerValue.Typed(type.typeId, CompilerValue.Record(fields))
        }
        return CompilerValue.Typed(type.typeId, payload)
    }

    private fun staticLayoutAccessible(actual: MCFPPType, target: MCFPPType) = target in erasedTypes || actual == target ||
        target is MCFPPUnionType && target.types.any { actual.isSubOf(it) } ||
        actual is MCFPPDataTemplateType && target is MCFPPDataTemplateType && actual.isSubOf(target) ||
        (actual is MCFPPListType || actual is MCFPPImmutableListType) && (target is MCFPPListType || target is MCFPPImmutableListType) ||
        (actual is MCFPPDictType || actual is MCFPPMapType) && (target is MCFPPDictType || target is MCFPPMapType)

    /** Loading a register is materialization, not a logical write. */
    fun read(value: Var<*>): Var<*> {
        if (value.isError) return value
        if (value.storageBinding == null && value.isStatic) {
            (value.declaredParentTemplate as? ObjectCompoundData)?.let { owner ->
                value.nbtPath = owner.nbtPath.memberIndex(value.identifier)
                bindStaticField(value)
            }
        }
        val binding = value.storageBinding ?: return value
        val data = binding.data
        val state = data.facts.readAccess(binding.place, data.declarations, data.types)?.state
        if (state != ValueState.INITIALIZED) {
            LogProcessor.error("Cannot read uninitialized variable '${value.identifier}'")
            return UnknownVar(value.identifier).apply { type = value.type; isError = true }
        }
        val version = Function.currFunction to data.versions.version(binding.place)
        // Selector operands must be relocated from the current binding after a caller-frame rebase.
        if (selectorProgram(binding) == null &&
            value.storageReadVersion?.let { it.first === version.first && it.second == version.second } == true) return value
        if (data.layoutAt(binding.place) == StorageLayout.CompilerOnly) {
            // A receiver address is usable while its constructor is filling individual fields.
            // Consumers of a whole value still require a complete snapshot at capture/write.
            if (value is DataTemplateObject && snapshot(value) == null) return value
            if (snapshot(value) == null && selectorProgram(binding) == null) return error(value.type, "Compiler-only place has no known value for '${value.type}'")
            return adapter(value.type, value.identifier, binding).apply {
                setAs(value)
                parent = value.parent
                storageReadVersion = version
            }
        }
        if (value is DataTemplateObject || value is NBTList || value is NBTDictionary || value is NBTMap) {
            value.storageReadVersion = version
            return value
        }
        if (value is MCAny) return value
        if (value is top.mcfpp.core.lang.obj.TypeDataTemplateObject) {
            value.storageReadVersion = version
            return value
        }
        val constant = snapshot(value)
        if (constant != null && value.symbol?.forceRuntime != true) {
            restore(value.type, constant, value.identifier)?.let { re ->
                re.setAs(value)
                re.parent = value.parent
                re.storageReadVersion = version
                return re
            }
        }
        val re = adapter(value.type, value.identifier, binding).apply { setAs(value); parent = value.parent; storageReadVersion = version }
        data.materialize(binding.place)
        when (re) {
            is MCInt -> {
                val register = data.register(binding.place, re.type.typeId, re.sbObject.toString(), binding.location)
                re.name = register.player
                re.isDataOnly = false
                loadScore(binding, register)
            }
            is ScoreBool -> {
                val register = data.register(binding.place, re.type.typeId, re.boolObject.toString(), binding.location)
                re.name = register.player
                re.isDataOnly = false
                loadScore(binding, register)
            }
            is MCFloat -> if (!FloatProviders.enabled) {
                if ((data.facts.read(binding.place)?.type as? TypeKnowledge.Exact)?.type in
                    setOf(MCFPPBaseType.Int.typeId, MCFPPBaseType.Bool.typeId, MCFPPNBTType.Byte.typeId, MCFPPNBTType.Short.typeId))
                    return error(re.type, "Legacy float requires its four-component layout; use toFloat(value) for numeric conversion")
                val registers = listOf("sign" to re.sign, "int0" to re.int0, "int1" to re.int1, "exp" to re.exp)
                for ((key, score) in registers) {
                    val field = binding.field(key)
                    val register = data.register(field.place, re.type.typeId, score.sbObject.toString(), field.location)
                    score.name = register.player
                    loadScore(field, register)
                }
            }
        }
        return re
    }

    private fun loadScore(binding: StorageBinding, layout: StorageLayout.Scoreboard) {
        if (binding.data.versions.isMaterialized(binding.place, layout, Function.currFunction, binding.location)) return
        emit(Command("execute store result score ${layout.player} ${layout.objective} run data get")
            .build(binding.path.toCommandPart()).build("1"))
        binding.data.versions.materialize(binding.place, layout, Function.currFunction, binding.location)
    }

    /** Receiver calls mutate the same payload without changing the source's nominal type. */
    fun writeReceiver(target: Var<*>, source: Var<*>) {
        if (source.isError) return
        val binding = target.storageBinding ?: error("Missing receiver storage binding")
        if (target.symbol?.readonly == true || binding.data.declarations.any { (place, declaration) -> declaration.readonly &&
                place.root == binding.place.root && binding.place.path.take(place.path.size) == place.path }) {
            LogProcessor.error("Cannot modify a readonly value '${target.identifier}'")
            return
        }
        if (binding.data.facts.read(binding.place)?.state != ValueState.INITIALIZED) {
            LogProcessor.error("Cannot update contents of an uninitialized value '${target.identifier}'")
            return
        }
        if (binding.data.layoutAt(binding.place) != StorageLayout.CompilerOnly && !hasRuntimeRepresentation(source) && hasActualPayload(source)) {
            if (binding.view != null) {
                LogProcessor.error("A reinterpretation view cannot change its source storage layout")
                return
            }
            binding.data.retainCompilerValue()
        }
        if (binding.data.layoutAt(binding.place) == StorageLayout.CompilerOnly) {
            val errors = Project.errorCount
            val result = writePayload(target, source)
            if (!result.isError && Project.errorCount == errors) EffectAnalysis.recordWrite(binding.place, contents = true)
            return
        }
        val original = ensure(source)
        if (original.data.facts.read(original.place)?.state != ValueState.INITIALIZED) {
            LogProcessor.error("Cannot copy contents from an uninitialized value '${source.identifier}'")
            return
        }
        val incoming = original.data.facts.withoutValues()
        val errors = Project.errorCount
        binding.data.materialize(binding.place)
        encodeTo(binding.path, source)
        if (Project.errorCount != errors) return
        binding.data.types.putAll(original.data.types)
        binding.data.inheritSources(original.data)
        binding.data.write(binding.place, incoming.read(original.place)!!)
        binding.data.facts.forgetDescendants(binding.place)
        binding.data.facts.copyFrom(incoming, original.place, binding.place, includeRoot = false)
        EffectAnalysis.recordWrite(binding.place, contents = true)
    }

    fun write(target: Var<*>, source: Var<*>): Var<*> {
        if (!canWrite(target)) {
            return UnknownVar(target.identifier).apply { type = target.type; isError = true }
        }
        val errors = Project.errorCount
        val result = writePayload(target, source)
        if (!result.isError && Project.errorCount == errors) EffectAnalysis.recordWrite(ensure(target).place)
        return result
    }

    private fun writePayload(target: Var<*>, source: Var<*>): Var<*> {
        if (source.isError) return UnknownVar(target.identifier).apply { type = target.type; isError = true }
        val sourcePlace = ensure(source)
        val sourceAccess = sourcePlace.data.facts.readAccess(sourcePlace.place, sourcePlace.data.declarations, sourcePlace.data.types)
        if (sourceAccess?.state != ValueState.INITIALIZED) {
            LogProcessor.error("Cannot assign uninitialized value '${source.identifier}'")
            return UnknownVar(target.identifier).apply { type = target.type }
        }
        val binding = ensure(target)
        if (source.type == MCFPPPrivateType.Null) {
            if (!target.nullable) {
                LogProcessor.error("Cannot assign null to non-nullable '${target.identifier}'")
                return target.clone().apply { isError = true }
            }
            if (binding.data.layoutAt(binding.place) != StorageLayout.CompilerOnly)
                emit(Command.buildAll("data remove", binding.path))
            binding.data.write(binding.place, ValueFacts(TypeKnowledge.Exact(MCFPPPrivateType.Null.typeId),
                ValueKnowledge.Constant(CompilerValue.NullValue)))
            return adapter(target.type, target.identifier, binding).apply { setAs(target) }
        }
        if (target is top.mcfpp.core.lang.entity.SelectorVar && source is top.mcfpp.core.lang.entity.SelectorVar) {
            val sourceBinding = source.storageBinding
            val selector = sourceBinding?.let(::selectorProgram)
                ?.selector(sourceBinding.data.types, sourceBinding) ?: source.value
            val expression = top.mcfpp.lib.SelectorExpression.capture(selector, binding.data,
                actualFrame = top.mcfpp.lib.SelectorExpression.frame(binding.path))
                ?: return target.clone().apply { isError = true }
            binding.data.types.putAll(source.storageBinding?.data?.types.orEmpty())
            binding.data.types[source.type.typeId] = source.type
            binding.data.write(binding.place, ValueFacts(TypeKnowledge.Exact(source.type.typeId),
                ValueKnowledge.Program(expression)))
            return adapter(target.type, target.identifier, binding).apply { setAs(target) }
        }
        val snapshot = top.mcfpp.analysis.StorageAccess.snapshot(source)
        if (binding.data.layoutAt(binding.place) != StorageLayout.CompilerOnly && !hasRuntimeRepresentation(source) && hasActualPayload(source)) {
            val receiver = Function.currFunction.constructedReceiver
            val actualConstructorField = binding.view == null && Function.currFunction.actualCallBody &&
                receiver?.storageBinding?.data === binding.data
            val erasedDestination = binding.view == null && target.type in erasedTypes
            val freshNominalRoot = binding.view == null && binding.place == binding.data.root &&
                binding.data.facts.read(binding.place)?.state == ValueState.UNINITIALIZED &&
                target.type is MCFPPDataTemplateType && actualType(source).isSubOf(target.type)
            if (actualConstructorField || erasedDestination || freshNominalRoot) {
                if (binding.place == binding.data.root) binding.data.retainCompilerValue()
                binding.data.registerLayout(binding.place, StorageLayout.CompilerOnly)
            }
        }
        val static = binding.data.layoutAt(binding.place) == StorageLayout.CompilerOnly
        if (static && snapshot == null) {
            val actual = actualType(source) as? MCFPPDataTemplateType
            val original = source.storageBinding
            if (actual != null && original != null && hasActualPayload(source)) {
                binding.data.types.putAll(original.data.types)
                binding.data.types[actual.typeId] = actual
                binding.data.write(binding.place, ValueFacts(TypeKnowledge.Exact(actual.typeId), ValueKnowledge.Unknown))
                if (actual.instanceFields.any { it.type.hasRuntimeRepresentation }) emit(Commands.dataSetValue(binding.path, CompoundTag()))
                for (field in actual.instanceFields) {
                    val input = adapter(field.type, field.identifier, original.field(field.identifier))
                    if (original.data.facts.read(input.storageBinding!!.place)?.state != ValueState.INITIALIZED) continue
                    val destinationBinding = binding.field(field.identifier)
                    registerMember(field, destinationBinding)
                    val destination = adapter(field.type, field.identifier, destinationBinding).apply { nullable = field.nullable }
                    if (writePayload(destination, input).isError) return target.clone().apply { isError = true }
                }
                return target
            }
            LogProcessor.error("Compiler-only place requires a complete compile-time value")
            return target.clone().apply { isError = true }
        }
        if (!static && !hasRuntimeRepresentation(source)) {
            LogProcessor.error("Compiler-only value '${actualType(source)}' cannot be written to a runtime place")
            return target.clone().apply { isError = true }
        }
        if (!static && (!collectionEncodingSupported(source) || !listWriteSupported(target, source))) {
            reportListEncoding()
            return target.clone().apply { isError = true }
        }
        val original = source.storageBinding
        val parts = FlowFacts()
        original?.let { parts.copyFrom(it.data.facts, it.place, binding.place, includeRoot = false) }
        if (original != null && original.data.facts.read(original.place)?.state != ValueState.INITIALIZED) {
            fun projectionFields(type: MCFPPType, from: Place, to: Place, seen: Set<TypeId> = emptySet()) {
                val accessedType = (original.data.facts.readAccess(from, original.data.declarations,
                    original.data.types)?.type as? TypeKnowledge.Exact)?.type
                val descriptor = accessedType?.let(original.data.types::get) ?: type
                if (descriptor !is MCFPPDataTemplateType || descriptor.typeId in seen) return
                for (field in descriptor.instanceFields) {
                    val sourceField = from.field(field.identifier)
                    val evidence = original.data.facts.readAccess(sourceField, original.data.declarations, original.data.types)
                    if (evidence?.state == ValueState.INITIALIZED) {
                        val targetField = to.field(field.identifier)
                        parts.refine(targetField, evidence.copy(value = ValueKnowledge.Unknown))
                        projectionFields(field.type, sourceField, targetField, seen + descriptor.typeId)
                    }
                }
            }
            projectionFields(actualType(source), original.place, binding.place)
        }
        val seeded = if (original == null) StoredData(binding.place, binding.path, layout = StorageLayout.CompilerOnly).also {
            seedParts(it, binding.place, source)
        } else null
        if (!static) {
            if (binding.place == binding.data.root && source.storageBinding?.data !== binding.data)
                binding.data.overwriteRoot()
            binding.data.materialize(binding.place)
            encodeTo(binding.path, source)
        }
        val writtenDescriptor = (sourceAccess?.type as? TypeKnowledge.Exact)?.type
            ?.let(sourcePlace.data.types::get) ?: actualType(source)
        binding.data.types[writtenDescriptor.typeId] = writtenDescriptor
        val represented = (binding.data.facts.read(binding.place)?.type as? TypeKnowledge.Exact)?.type
            ?.let(binding.data.types::get)?.let { it as? MCFPPTypeDataTemplateType }
            ?.takeIf { binding.view != null && it.typeAs == target.type }
        val writtenSnapshot = if (represented != null && snapshot != null) CompilerValue.Typed(represented.typeId,
            if (snapshot is CompilerValue.Typed) snapshot.payload else snapshot) else snapshot
        val writtenType = if (represented != null) TypeKnowledge.Exact(represented.typeId)
            else if (source is MCAny) source.typeKnowledge else TypeKnowledge.Exact(writtenDescriptor.typeId)
        binding.data.write(binding.place, ValueFacts(writtenType,
            writtenSnapshot?.let(ValueKnowledge::Constant) ?: ValueKnowledge.Unknown,
            readableLayout = sourceAccess?.readableLayout?.takeIf { !static &&
                (writtenType as? TypeKnowledge.Exact)?.type == it }))
        if (PathSegment.UnknownIndex !in binding.place.path) {
            binding.data.facts.forgetDescendants(binding.place)
            original?.data?.let { incoming ->
                incoming.types.forEach { (id, type) -> binding.data.types.putIfAbsent(id, type) }
                binding.data.inheritSources(incoming)
            }
            binding.data.facts.copyFrom(parts, binding.place, binding.place, includeRoot = false)
            seeded?.let {
                binding.data.types.putAll(it.types)
                binding.data.facts.copyFrom(it.facts, binding.place, binding.place, includeRoot = false)
            }
        }
        return adapter(target.type, target.identifier, binding).apply {
            setAs(target)
            parent = target.parent
            if (this is OnScoreboard && target is OnScoreboard) isDataOnly = target.isDataOnly
            annotations.addAll(target.annotations)
            storageReadVersion = null
        }
    }

    fun canWrite(target: Var<*>): Boolean {
        val binding = ensure(target)
        if (binding.data.declarations.any { (place, declaration) -> declaration.readonly &&
                place.root == binding.place.root && binding.place.path.take(place.path.size) == place.path }) {
            LogProcessor.error("Cannot modify readonly value '${target.identifier}'")
            return false
        }
        val accessDeclaration = target.symbol
        val sourceDeclaration = binding.data.declarations[binding.place]
        val constantDeclaration = accessDeclaration?.mutable == false || sourceDeclaration?.mutable == false || target.isConst
        if (!constantDeclaration || binding.data.facts.read(binding.place)?.state == ValueState.UNINITIALIZED) return true
        LogProcessor.error("Cannot reassign constant '${target.identifier}'")
        return false
    }

    fun materialize(value: Var<*>) {
        if (!hasRuntimeRepresentation(value)) {
            LogProcessor.error("Compiler-only value '${actualType(value)}' cannot be materialized")
            return
        }
        val binding = ensure(value)
        binding.data.materialize(binding.place)
    }

    fun capture(value: Var<*>): Var<*> {
        if (value.isError) return value
        val sourceBinding = ensure(value)
        val fact = sourceBinding.data.facts.readAccess(sourceBinding.place, sourceBinding.data.declarations, sourceBinding.data.types)
        if (fact?.state != ValueState.INITIALIZED) {
            LogProcessor.error("Cannot capture uninitialized variable '${value.identifier}'")
            return UnknownVar(value.identifier).apply { type = value.type; isError = true }
        }
        val loaded = read(value)
        if (loaded.isError) return loaded
        snapshot(loaded)?.let { closed ->
            return (captureClosed(loaded, closed, TempPool.getVarIdentify())
                ?: error(loaded.type, "Cannot capture the actual payload through '${loaded.type}'")).apply { isTemp = true }
        }
        if (!actualType(loaded).hasRuntimeRepresentation) {
            return capturePayload(loaded.type, loaded, TempPool.getVarIdentify())
                ?: error(loaded.type, "Cannot capture an incomplete compiler-only payload '${loaded.identifier}'")
        }
        val captured = run {
            val result = loaded.type.buildUnConcrete(TempPool.getVarIdentify()).apply {
                isTemp = true
                nbtPath = NBTPath.stack.intIndex(0).memberIndex(identifier)
            }
            declare(result, Symbol(SymbolId.fresh(), result.identifier, result.type.typeId, mutable = true))
            write(result, loaded)
        }
        if (captured.nbtPath.pathList.isEmpty()) captured.nbtPath = NBTPath.getNormalStackPath(captured)
        return captured
    }

    private fun captureClosed(source: Var<*>, closed: CompilerValue, name: String): Var<*>? {
        val actual = actualType(source)
        var payload = if (actual is MCFPPTypeDataTemplateType && source.type == actual.typeAs)
            constantFor(actual, ensure(source)) ?: return null else closed
        while (payload is CompilerValue.Typed && payload.type != actual.typeId && payload.payload is CompilerValue.Typed)
            payload = payload.payload
        val captured = restore(actual, payload, name, boundTypes(source)) ?: return null
        val binding = ensure(captured)
        if (source.symbol?.forceRuntime == true || source.storageBinding?.let { it.data.declarations[it.place]?.forceRuntime } == true) {
            val declaration = captured.symbol!!.copy(forceRuntime = true)
            captured.symbol = declaration
            binding.data.declarations[binding.place] = declaration
        }
        binding.data.types.putIfAbsent(source.type.typeId, source.type)
        return adapter(source.type, name, binding.copy(view = ValueRef.TypedView(source.type.typeId,
            ValueRef.Read(actual.typeId, binding.place), binding.place)))
    }

    data class Spill(val value: Var<*>, val path: NBTPath, val layout: StorageLayout)

    /** Expression temporaries outlive calls but must not share the callee's scratch slots. */
    fun spill(values: Collection<Var<*>>): List<Spill> = values.distinct().mapNotNull { value ->
        if (value.isError || !hasRuntimeRepresentation(value) || snapshot(value) != null) return@mapNotNull null
        val binding = ensure(value)
        if (binding.data.facts.read(binding.place)?.state != ValueState.INITIALIZED) return@mapNotNull null
        val physical = when (value) {
            is MCInt -> intRegister(value)
            is ScoreBool -> booleanRegister(value)
            is MCFloat -> if (!FloatProviders.enabled) StorageLayout.LegacyFloat(legacyFloatRegisters(value) ?: return@mapNotNull null)
                else binding.data.layoutAt(binding.place)
            else -> binding.data.layoutAt(binding.place)
        }
        val slot = NBTPath.stack.intIndex(0).memberIndex(TempPool.getVarIdentify())
        encodeTo(slot, value)
        Spill(value, slot, physical)
    }

    fun restore(spills: List<Spill>) {
        for ((value, slot, physical) in spills) {
            fun score(player: String, objective: String, path: NBTPath = slot) {
                emit(Command("execute store result score $player $objective run data get").build(path.toCommandPart()).build("1"))
            }
            when (physical) {
                is StorageLayout.Scoreboard -> score(physical.player, physical.objective)
                is StorageLayout.LegacyFloat -> listOf("sign", "int0", "int1", "exp").zip(physical.components)
                    .forEach { (key, part) -> score(part.player, part.objective, slot.memberIndex(key)) }
                else -> value.storageBinding?.let { emit(Commands.dataSetFrom(it.path, slot)) }
            }
            value.storageBinding?.let {
                if (physical is StorageLayout.Scoreboard || physical is StorageLayout.LegacyFloat)
                    emit(Commands.dataSetFrom(it.path, slot))
                it.data.versions.invalidate(it.place)
                value.storageReadVersion = Function.currFunction to it.data.versions.version(it.place)
            }
        }
    }

    internal fun offsetFrame(path: NBTPath, offset: Int): NBTPath = path.clone().apply {
        val first = pathList.firstOrNull() as? top.mcfpp.lib.MemberPath
        val member = first?.value as? top.mcfpp.core.lang.nbt.MCString
        val text = member?.takeIf { snapshot(it) != null }?.value?.value
        val index = text?.let { Regex("stack_frame\\[(\\d+)]").matchEntire(it) }?.groupValues?.get(1)?.toInt()
        if (index != null) {
            pathList[0] = top.mcfpp.lib.MemberPath(top.mcfpp.core.lang.nbt.MCString(
                top.mcfpp.nbt.tags.primitive.StringTag("stack_frame[${index + offset}]")))
        } else if (text == "stack_frame") {
            val element = pathList.getOrNull(1) as? top.mcfpp.lib.IntPath
            val frame = element?.value as? MCInt
            if (frame != null && snapshot(frame) != null)
                pathList[1] = top.mcfpp.lib.IntPath(MCInt(frame.value + offset))
        }
    }

    internal fun inFrame(binding: StorageBinding, offset: Int): StorageBinding =
        if (offset == 0) binding else binding.copy(location = binding.location.inFrame(offset))

    fun address(value: Var<*>, location: Location): NBTPath = ensure(value).data.address(location)

    /** Rebase an address while a callee frame is active; data identity and write versions stay shared. */
    fun callerValue(value: Var<*>): Var<*> = value.clone().apply {
        nbtPath = offsetFrame(nbtPath, 1)
        storageBinding = storageBinding?.let { inFrame(it, 1) }
        storageReadVersion = null
    }

    fun restoreScore(value: Var<*>, player: String, objective: String): Boolean {
        val binding = value.storageBinding ?: return false
        binding.data.materialize(binding.place)
        loadScore(binding, StorageLayout.Scoreboard(player, objective))
        return true
    }

    fun barrier(values: Collection<Var<*>>) {
        values.mapNotNull { it.storageBinding?.data }.distinct().forEach(StoredData::barrier)
    }

    /** Apply an actual callee's mapped writes, rather than invalidating unrelated visible roots. */
    fun applyEffect(values: Collection<Var<*>>, effect: Effect) {
        when (effect) {
            Effect.Pure, Effect.ReadsRuntime -> return
            Effect.Unknown -> barrier(values)
            is Effect.Writes -> for (data in values.mapNotNull { it.storageBinding?.data }.distinct()) {
                for (written in effect.places.filter { it.root == data.root.root }) {
                    val readonly = data.declarations.filterValues { it.readonly }.keys
                    val affected = if (data.layout == StorageLayout.CompilerOnly) data.facts.entries().map { it.key }.filter {
                        it.overlaps(written) && data.layoutAt(it) != StorageLayout.CompilerOnly
                    } else listOf(written)
                    for (place in affected) {
                        if (readonly.any { it.root == place.root && place.path.take(it.path.size) == it.path }) continue
                        data.versions.invalidate(place)
                        for ((child, fact) in data.facts.entries()) {
                            if (!child.overlaps(place) || readonly.any { it.root == child.root && child.path.take(it.path.size) == it.path }) continue
                            data.facts.refine(child, fact.copy(type = TypeKnowledge.Unknown, value = ValueKnowledge.Unknown, readableLayout = null,
                                state = if (fact.state == ValueState.UNINITIALIZED) ValueState.MAYBE_INITIALIZED else fact.state))
                        }
                        data.facts.invalidate(place, readonly)
                    }
                }
            }
        }
    }

    fun flush(values: Collection<Var<*>>) {
        values.filter { value -> value.storageBinding?.let { binding ->
            binding.data.facts.read(binding.place)?.state == ValueState.INITIALIZED &&
                binding.data.layoutAt(binding.place) != StorageLayout.CompilerOnly && hasRuntimeRepresentation(value)
        } == true }
            .map { ensure(it).data }.distinct().forEach(StoredData::materialize)
    }

    fun visibleValues(scope: top.mcfpp.model.scope.IScope): List<Var<*>> {
        val visited = java.util.Collections.newSetFromMap(java.util.IdentityHashMap<top.mcfpp.model.scope.IScope, Boolean>())
        val values = mutableListOf<Var<*>>()
        fun collect(current: top.mcfpp.model.scope.IScope) {
            if (!visited.add(current)) return
            if (current is top.mcfpp.model.scope.IScopeWithVar) values.addAll(current.allVars)
            current.parent.filterNotNull().forEach(::collect)
        }
        collect(scope)
        return values
    }

    fun encodeTo(path: NBTPath, source: Var<*>) {
        if (!hasRuntimeRepresentation(source)) {
            LogProcessor.error("Compiler-only value '${actualType(source)}' cannot be stored in an erased runtime payload")
            return
        }
        if (!collectionEncodingSupported(source)) { reportListEncoding(); return }
        constantEncoding(source)?.let { emit(Command.buildAll("data modify", path, "set value", NbtEncoding.snbt(it))); return }
        source.storageBinding?.let {
            val binding = ensure(source)
            if (binding.data.facts.readAccess(binding.place, binding.data.declarations, binding.data.types)?.state != ValueState.INITIALIZED) {
                LogProcessor.error("Cannot encode uninitialized value '${source.identifier}'")
                return
            }
            binding.data.materialize(binding.place)
            if (path.toCommandPart().toString() != binding.path.toCommandPart().toString())
                emit(Commands.dataSetFrom(path, binding.path))
            return
        }
        LogProcessor.error("Cannot encode a value without an initialized physical producer '${source.identifier}'")
    }

    fun constantEncoding(value: Var<*>): Tag<*>? {
        if (!hasRuntimeRepresentation(value) || !collectionEncodingSupported(value)) return null
        return snapshot(value)?.let(::snapshotTag)
    }

    /** Representation follows the actual producer and immutable child evidence. */
    fun hasRuntimeRepresentation(value: Var<*>): Boolean {
        val type = actualType(value)
        if (!type.hasRuntimeRepresentation) return false
        val closed = snapshot(value)
        if (closed != null) return runtimeSnapshot(closed, boundTypes(value)) && snapshotTag(closed) != null
        val binding = value.storageBinding ?: return type.hasRuntimeRepresentation
        if (binding.data.layoutAt(binding.place) == StorageLayout.CompilerOnly) return false
        return binding.data.facts.entries().none { (place, fact) ->
            place != binding.place && place.root == binding.place.root &&
                place.path.take(binding.place.path.size) == binding.place.path && fact.state == ValueState.INITIALIZED &&
                (binding.data.layoutAt(place) == StorageLayout.CompilerOnly ||
                    (fact.type as? TypeKnowledge.Exact)?.type?.let(binding.data.types::get)?.hasRuntimeRepresentation == false)
        }
    }

    private fun runtimeSnapshot(value: CompilerValue, types: Map<TypeId, MCFPPType>): Boolean = when (value) {
        is CompilerValue.Typed -> types[value.type]?.hasRuntimeRepresentation != false && runtimeSnapshot(value.payload, types)
        is CompilerValue.Sequence -> value.elements.all { runtimeSnapshot(it, types) }
        is CompilerValue.Record -> value.fields.values.all { runtimeSnapshot(it, types) }
        is CompilerValue.TypeValue -> false
        else -> true
    }

    private val supportsMixedLists get() = top.mcfpp.command.TargetCapabilities
        .forVersion(top.mcfpp.Project.config.version)?.heterogeneousLists == true

    internal fun collectionEncodingSupported(value: Var<*>): Boolean {
        if (supportsMixedLists) return true
        fun supported(closed: CompilerValue): Boolean = when (closed) {
            is CompilerValue.Typed -> supported(closed.payload)
            is CompilerValue.Sequence -> {
                val encodings = closed.elements.map { snapshotTag(it)?.javaClass }
                (encodings.size <= 1 || encodings.all { it != null } && encodings.distinct().size == 1) && closed.elements.all(::supported)
            }
            is CompilerValue.Record -> closed.fields.values.all(::supported)
            else -> true
        }
        snapshot(value)?.let { return supported(it) }
        val binding = value.storageBinding ?: return true
        if (value.type !is MCFPPListType && value.type !is MCFPPImmutableListType) return true
        val children = binding.data.facts.children(binding.place)
        if (children.size <= 1) return true
        val encodings = children.values.map { fact ->
            (fact.type as? TypeKnowledge.Exact)?.type?.let(binding.data.types::get)?.let(::encoding)
        }
        return encodings.all { it != null } && encodings.distinct().size == 1
    }

    private fun listWriteSupported(target: Var<*>, source: Var<*>): Boolean {
        if (supportsMixedLists || target.parent !is NBTList) return true
        val binding = target.storageBinding!!
        val parent = Place(binding.place.root, binding.place.path.dropLast(1))
        val expected = sourceEncoding(source) ?: return false
        return binding.data.facts.children(parent).values.all {
            val type = (it.type as? TypeKnowledge.Exact)?.type?.let(binding.data.types::get) ?: return@all false
            encoding(type) == expected
        }
    }

    internal fun sourceEncoding(value: Var<*>): Class<out Tag<*>>? {
        value.storageBinding?.let { binding ->
            val type = (binding.data.facts.read(binding.place)?.type as? TypeKnowledge.Exact)?.type
                ?.let(binding.data.types::get) ?: return null
            return encoding(type)
        }
        if (value is NBTBasedData) constantEncoding(value)?.let { return it.javaClass }
        return encoding(actualType(value))
    }

    internal fun encoding(type: MCFPPType): Class<out Tag<*>>? = when (type.typeId) {
        MCFPPBaseType.Range.typeId -> CompoundTag::class.java
        MCFPPBaseType.Any.typeId, MCFPPBaseType.Object.typeId, MCFPPNBTType.NBT.typeId -> null
        MCFPPBaseType.Float.typeId -> if (FloatProviders.enabled) top.mcfpp.nbt.tags.primitive.FloatTag::class.java else CompoundTag::class.java
        MCFPPNBTType.Byte.typeId -> top.mcfpp.nbt.tags.primitive.ByteTag::class.java
        MCFPPNBTType.Short.typeId -> top.mcfpp.nbt.tags.primitive.ShortTag::class.java
        MCFPPNBTType.Long.typeId -> top.mcfpp.nbt.tags.primitive.LongTag::class.java
        MCFPPNBTType.Double.typeId -> top.mcfpp.nbt.tags.primitive.DoubleTag::class.java
        MCFPPNBTType.ByteArray.typeId -> top.mcfpp.nbt.tags.collection.ByteArrayTag::class.java
        MCFPPNBTType.IntArray.typeId -> top.mcfpp.nbt.tags.collection.IntArrayTag::class.java
        MCFPPNBTType.LongArray.typeId -> top.mcfpp.nbt.tags.collection.LongArrayTag::class.java
        else -> when (type) {
            is MCFPPUnionType -> type.types.map(::encoding).distinct().singleOrNull()
            is MCFPPListType, is MCFPPImmutableListType, is MCFPPCompoundType, is MCFPPDataTemplateType -> type.nbtType
            else -> if (type in setOf(MCFPPBaseType.Int, MCFPPBaseType.Bool, MCFPPBaseType.String)) type.nbtType else null
        }
    }

    internal fun reportListEncoding() {
        LogProcessor.error("Target '${top.mcfpp.Project.config.version}' cannot materialize or modify a list with mixed or unproven NBT element encodings; convert elements to a common encoding or select a target with heterogeneous lists")
    }

    internal fun scoreTag(type: TypeId?): String = when (type) {
        MCFPPBaseType.Bool.typeId, MCFPPNBTType.Byte.typeId -> "byte"
        MCFPPNBTType.Short.typeId -> "short"
        else -> "int"
    }
    internal fun scoreWriter(path: NBTPath, player: String, objective: String, tag: String): () -> Unit = {
        emit(Command("execute store result").build(path.toCommandPart())
            .build("$tag 1 run scoreboard players get $player $objective"))
    }

    internal fun snapshotTag(value: CompilerValue, type: TypeId? = null): Tag<*>? = when (value) {
        is CompilerValue.Typed -> if (value.type is TypeId.Declaration && value.type.kind == "enum")
            (value.payload as? CompilerValue.Record)?.fields?.get("data")?.let { snapshotTag(it) }
        else snapshotTag(value.payload, value.type)
        is CompilerValue.Integral -> when (type) {
            MCFPPNBTType.Byte.typeId -> top.mcfpp.nbt.tags.primitive.ByteTag(value.value.toByte())
            MCFPPNBTType.Short.typeId -> top.mcfpp.nbt.tags.primitive.ShortTag(value.value.toShort())
            MCFPPNBTType.Long.typeId -> top.mcfpp.nbt.tags.primitive.LongTag(value.value)
            else -> IntTag(value.value.toInt())
        }
        is CompilerValue.Bool -> top.mcfpp.nbt.tags.primitive.ByteTag(value.value)
        is CompilerValue.Nbt -> NbtEncoding.parse(value.snbt)
        is CompilerValue.Text -> top.mcfpp.nbt.tags.primitive.StringTag(value.value)
        is CompilerValue.FloatBits -> if (FloatProviders.enabled) top.mcfpp.nbt.tags.primitive.FloatTag(Float.fromBits(value.bits)) else {
            val parts = MCFloat.floatToMCFloat(Float.fromBits(value.bits))
            CompoundTag().apply { for ((i, key) in listOf("sign", "int0", "int1", "exp").withIndex()) put(key, IntTag(parts[i])) }
        }
        is CompilerValue.DoubleBits -> top.mcfpp.nbt.tags.primitive.DoubleTag(Double.fromBits(value.bits))
        is CompilerValue.Sequence -> {
            val elements = value.elements.map { snapshotTag(it) }
            if (elements.any { it == null }) null else when (type) {
                MCFPPNBTType.ByteArray.typeId -> if (elements.all { it is top.mcfpp.nbt.tags.primitive.ByteTag })
                    top.mcfpp.nbt.tags.collection.ByteArrayTag(elements.map { (it as top.mcfpp.nbt.tags.primitive.ByteTag).value }.toByteArray()) else null
                MCFPPNBTType.IntArray.typeId -> if (elements.all { it is IntTag })
                    top.mcfpp.nbt.tags.collection.IntArrayTag(elements.map { (it as IntTag).value }.toIntArray()) else null
                MCFPPNBTType.LongArray.typeId -> if (elements.all { it is top.mcfpp.nbt.tags.primitive.LongTag })
                    top.mcfpp.nbt.tags.collection.LongArrayTag(elements.map { (it as top.mcfpp.nbt.tags.primitive.LongTag).value }.toLongArray()) else null
                else -> top.mcfpp.nbt.tags.collection.ListTag().apply { elements.forEach { add(it!!) } }
            }
        }
        is CompilerValue.Record -> {
            fun isNull(part: CompilerValue): Boolean = part == CompilerValue.NullValue ||
                part is CompilerValue.Typed && isNull(part.payload)
            val fields = value.fields.filterValues { !isNull(it) }.mapValues { snapshotTag(it.value) }
            if (fields.values.any { it == null }) null else CompoundTag().apply { fields.forEach { (key, tag) -> put(key, tag!!) } }
        }
        else -> null
    }

    private fun numericTag(type: MCFPPType) = when (type) {
        MCFPPNBTType.Byte -> "byte"
        MCFPPNBTType.Short -> "short"
        else -> "int"
    }

    fun actualType(value: Var<*>): MCFPPType {
        val binding = value.storageBinding
        val producer = (binding?.data?.facts?.read(binding.place)?.type as? TypeKnowledge.Exact)?.type
            ?.let { binding.data.types[it] }
        return producer ?: value.type
    }

    internal fun restore(type: MCFPPType, snapshot: CompilerValue, name: String,
                        types: Map<TypeId, MCFPPType> = emptyMap()): Var<*>? {
        if (type is MCFPPDataTemplateType && snapshot is CompilerValue.Typed && snapshot.type == type.typeId) {
            val producer = types[snapshot.type] as? MCFPPDataTemplateType
            if (producer != null && producer !== type) {
                val restored = restore(producer, snapshot, name, types) ?: return null
                return adapter(type, name, ensure(restored))
            }
        }
        if (type is MCFPPUnionType) {
            var represented = snapshot
            if (represented is CompilerValue.Typed && represented.type == type.typeId) represented = represented.payload
            val actual = represented as? CompilerValue.Typed ?: return null
            val producer = types[actual.type] ?: type.types.firstOrNull { it.typeId == actual.type } ?: return null
            if (type.types.none { producer.isSubOf(it) }) return null
            val restored = restore(producer, actual, name, types) ?: return null
            val binding = ensure(restored)
            return adapter(type, name, binding.copy(view = ValueRef.TypedView(type.typeId,
                ValueRef.Read(producer.typeId, binding.place), binding.place)))
        }
        if (type is MCFPPDataTemplateType && snapshot is CompilerValue.Typed && snapshot.type != type.typeId) {
            val producer = types[snapshot.type] as? MCFPPDataTemplateType ?: return null
            if (!producer.isSubOf(type)) return null
            val restored = restore(producer, snapshot, name, types) ?: return null
            val binding = ensure(restored)
            binding.data.types.putAll(types)
            return adapter(type, name, binding.copy(view = ValueRef.TypedView(type.typeId,
                ValueRef.Read(producer.typeId, binding.place), binding.place)))
        }
        val payload = if (snapshot is CompilerValue.Typed) snapshot.payload else snapshot
        if (type is MCFPPVectorType) {
            if (snapshot !is CompilerValue.Typed || snapshot.type != type.typeId || payload !is CompilerValue.Sequence ||
                payload.elements.size != type.dimension) return null
            if (payload.elements.any { part ->
                val typed = part as? CompilerValue.Typed ?: return null
                val number = typed.payload as? CompilerValue.Integral ?: return null
                typed.type != MCFPPBaseType.Int.typeId || number.value !in Int.MIN_VALUE.toLong()..Int.MAX_VALUE.toLong()
            }) return null
            return initializeLiteral(type.buildUnConcrete(name), snapshot, types)
        }
        if (type == MCFPPPrivateType.MCFPPCoordinateDimension || type == MCFPPBaseType.Pos2 || type == MCFPPBaseType.Pos3) {
            if (snapshot !is CompilerValue.Typed || snapshot.type != type.typeId || payload !is CompilerValue.Sequence) return null
            if (type == MCFPPPrivateType.MCFPPCoordinateDimension) {
                if (payload.elements.size != 2) return null
                val prefix = (payload.elements[0] as? CompilerValue.Text)?.value ?: return null
                if (prefix !in setOf("", "~", "^")) return null
                val number: Number = when (val numeric = payload.elements[1]) {
                    is CompilerValue.Integral -> numeric.value
                    is CompilerValue.FloatBits -> Float.fromBits(numeric.bits)
                    is CompilerValue.DoubleBits -> Double.fromBits(numeric.bits)
                    else -> return null
                }
                return PosDimension(prefix, number, name)
            }
            val names = if (type == MCFPPBaseType.Pos2) listOf("x", "z") else listOf("x", "y", "z")
            if (payload.elements.size != names.size) return null
            val dimensions = payload.elements.mapIndexed { index, part ->
                restore(MCFPPPrivateType.MCFPPCoordinateDimension, part, names[index], types) as? PosDimension ?: return null
            }
            return if (type == MCFPPBaseType.Pos2) Pos2Var(name).apply { value = ArrayList(dimensions) }
            else Pos3Var(name).apply { value = ArrayList(dimensions) }
        }
        val enumType = type as? MCFPPEnumType
        if (enumType != null) {
            if (snapshot !is CompilerValue.Typed || snapshot.type != enumType.typeId || payload !is CompilerValue.Record) return null
            val ordinal = (payload.fields["ordinal"] as? CompilerValue.Integral)?.value ?: return null
            if (ordinal !in Int.MIN_VALUE.toLong()..Int.MAX_VALUE.toLong()) return null
            val member = enumType.enum.getMember(ordinal.toInt()) ?: return null
            val data = payload.fields["data"] as? CompilerValue.Nbt ?: return null
            if (data.snbt != NbtEncoding.snbt(member.data)) return null
            return type.build(name, member)
        }
        if (type is MCFPPEntityType && snapshot is CompilerValue.Typed && snapshot.type == type.typeId) {
            val expression = top.mcfpp.lib.SelectorExpression.restore(snapshot) ?: return null
            val selector = expression.selector(types) ?: return null
            return top.mcfpp.core.lang.entity.SelectorVar(selector, name).takeIf { it.type.typeId == type.typeId }
        }
        if (type is MCFPPTypeDataTemplateType) {
            val delegate = restore(type.typeAs, CompilerValue.Typed(type.typeAs.typeId, payload), name, types) ?: return null
            return type.buildUnConcrete(name).let { it as top.mcfpp.core.lang.obj.TypeDataTemplateObject }.apply {
                delegateVar = delegate.also { it.parent = this }
            }
        }
        if (type is MCFPPDataTemplateType && payload is CompilerValue.Record) {
            if (snapshot !is CompilerValue.Typed || snapshot.type != type.typeId) return null
            for (field in type.instanceFields) {
                val part = payload.fields[field.identifier] ?: if (field.nullable) continue else return null
                restore(field.type, part, field.identifier, types) ?: return null
            }
            return type.buildUnConcrete(name).also { initializeLiteral(it, snapshot, types) }
        }
        if (type == MCFPPNBTType.NBT) return snapshotTag(snapshot, type.typeId)?.let { type.build(name, it) }
        if (type == MCFPPBaseType.Range && payload is CompilerValue.Record) {
            fun endpoint(name: String): Number? {
                val part = payload.fields[name] ?: return null
                val raw = if (part is CompilerValue.Typed) part.payload else part
                return when (raw) {
                    is CompilerValue.Integral -> raw.value.toInt()
                    is CompilerValue.FloatBits -> Float.fromBits(raw.bits)
                    else -> return null
                }
            }
            return RangeVar(endpoint("left") to endpoint("right"), name)
        }
        if (type in erasedTypes) {
            var actual = if (snapshot is CompilerValue.Typed && snapshot.type !in erasedTypes.map { it.typeId }) snapshot else payload
            while (actual is CompilerValue.Typed && actual.type in erasedTypes.map { it.typeId }) actual = actual.payload
            if (actual !is CompilerValue.Typed) return null
            val actualType = types[actual.type] ?: return null
            val restored = restore(actualType, actual, name, types) ?: return null
            val binding = ensure(restored)
            binding.data.types.putAll(types)
            binding.data.types[actualType.typeId] = actualType
            return adapter(type, name, binding.copy(view = ValueRef.TypedView(type.typeId,
                ValueRef.Read(actualType.typeId, binding.place), binding.place)))
        }
        if (payload is CompilerValue.Sequence && (type is MCFPPListType || type is MCFPPImmutableListType)) {
            val generic = (type as MCFPPTypeWithGeneric).generic.single()
            if (payload.elements.any { part ->
                val id = (part as? CompilerValue.Typed)?.type ?: return null
                val producer = types[id] ?: MCFPPType.resolveTypeId(id) ?: return null
                generic !== MCFPPBaseType.Any && generic !== MCFPPPrivateType.Wildcard && !producer.isSubOf(generic)
            }) return null
            return initializeLiteral(type.buildUnConcrete(name), payload, types)
        }
        if (payload is CompilerValue.Record && type is MCFPPDictType) {
            val generic = type.generic.single()
            if (payload.fields.values.any { part ->
                val id = (part as? CompilerValue.Typed)?.type ?: return null
                val producer = types[id] ?: MCFPPType.resolveTypeId(id) ?: return null
                generic !== MCFPPBaseType.Any && !producer.isSubOf(generic)
            }) return null
            return initializeLiteral(type.buildUnConcrete(name), payload, types)
        }
        if (payload is CompilerValue.Record && type is MCFPPMapType) {
            fun unwrapped(part: CompilerValue): CompilerValue = if (part is CompilerValue.Typed) unwrapped(part.payload) else part
            val entries = payload.fields["entries"]?.let(::unwrapped) as? CompilerValue.Sequence ?: return null
            val keys = linkedSetOf<String>()
            for (entry in entries.elements) {
                val row = unwrapped(entry) as? CompilerValue.Record ?: return null
                val key = row.fields["key"]?.let(::unwrapped) ?: return null
                val text = when (key) {
                    is CompilerValue.Text -> key.value
                    is CompilerValue.Nbt -> (Tag.toNBT(key.snbt) as? top.mcfpp.nbt.tags.primitive.StringTag)?.value
                    else -> null
                } ?: return null
                if (!keys.add(text)) return null
                val part = row.fields["value"] ?: return null
                val id = (part as? CompilerValue.Typed)?.type ?: return null
                val elementType = types[id] ?: MCFPPType.resolveTypeId(id) ?: return null
                if (type.generic.single() !== MCFPPBaseType.Any && !elementType.isSubOf(type.generic.single())) return null
            }
            return initializeLiteral(type.buildUnConcrete(name), payload, types)
        }
        if (payload is CompilerValue.Sequence && type in setOf(MCFPPNBTType.ByteArray, MCFPPNBTType.IntArray, MCFPPNBTType.LongArray)) {
            return snapshotTag(payload, type.typeId)?.let { type.build(name, it) }
        }
        val raw: Any = when (payload) {
            is CompilerValue.Typed -> return restore(type, payload, name, types)
            is CompilerValue.TypeValue -> return types[payload.id]?.let { MCFPPTypeVar(it, name) }
            is CompilerValue.Integral -> when (type) {
                MCFPPNBTType.Byte -> payload.value.toByte()
                MCFPPNBTType.Short -> payload.value.toShort()
                MCFPPNBTType.Long -> top.mcfpp.nbt.tags.primitive.LongTag(payload.value)
                else -> payload.value.toInt()
            }
            is CompilerValue.Bool -> payload.value
            is CompilerValue.FloatBits -> Float.fromBits(payload.bits)
            is CompilerValue.DoubleBits -> top.mcfpp.nbt.tags.primitive.DoubleTag(Double.fromBits(payload.bits))
            is CompilerValue.Nbt -> NbtEncoding.parse(payload.snbt)
            is CompilerValue.Text -> top.mcfpp.nbt.tags.primitive.StringTag(payload.value)
            else -> return null
        }
        if (type == MCFPPBaseType.JsonText) {
            val encoded = raw as? ListTag ?: return null
            val components = ListChatComponent()
            for (component in encoded) {
                components.components.add(EncodedChatComponent(NbtEncoding.snbt(component)))
            }
            return type.build(name, components)
        }
        if (type is MCFPPDataTemplateType || type in erasedTypes) return null
        return type.build(name, raw)
    }

    private fun error(type: MCFPPType, message: String): Var<*> {
        LogProcessor.error("Cannot generate reinterpretation access: $message; use a conversion function when a value conversion is intended")
        return UnknownVar(TempPool.getVarIdentify()).apply { this.type = type; isError = true }
    }
}

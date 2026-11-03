package top.mcfpp.mni.expand

import top.mcfpp.Project
import top.mcfpp.analysis.*
import top.mcfpp.backend.LegacyFloatCommands
import top.mcfpp.command.FloatProviders
import top.mcfpp.lib.StorageSource
import top.mcfpp.mni.api.*
import top.mcfpp.mni.api.SymbolId
import top.mcfpp.nbt.tags.Tag
import top.mcfpp.type.*
import java.util.IdentityHashMap
import top.mcfpp.core.lang.context.ContextBorrowing
import top.mcfpp.core.lang.context.ContextBorrowPayload
import top.mcfpp.core.lang.context.ContextAccessException
import top.mcfpp.core.lang.ref.ReferenceMetadata
import top.mcfpp.core.lang.Var

internal data class CompilerConstantLocation(val root: SymbolId, val path: List<PathSegment> = emptyList())

private fun <K> joinReferenceTrees(left: Map<K, ReferenceMetadata>, right: Map<K, ReferenceMetadata>): Map<K, ReferenceMetadata> =
    (left.keys + right.keys).associateWith { key ->
        val first = left[key]
        val second = right[key]
        if (first != null && second != null) first.join(second) else (first ?: second!!).invalidated()
    }

private fun projectReferences(tree: Map<List<PathSegment>, ReferenceMetadata>, part: PathSegment): Map<List<PathSegment>, ReferenceMetadata> {
    val projected = linkedMapOf<List<PathSegment>, ReferenceMetadata>()
    tree.forEach { (path, metadata) ->
        val first = path.firstOrNull() ?: return@forEach
        if (first == part || first == PathSegment.UnknownIndex || part == PathSegment.UnknownIndex) {
            val rest = path.drop(1)
            projected[rest] = projected[rest]?.join(metadata) ?: metadata
        }
    }
    return projected
}

/** Every plan register is a runtime snapshot, including mutable join registers. */
internal class CompilerPlanValues(private val out: CompilerPlanEmitter) {
    private val strings = CompilerPlanStrings(out)

    private data class State(
        val registers: Map<Int, CompilerValue> = emptyMap(),
        val roots: Map<SymbolId, CompilerValue> = emptyMap(),
        val addresses: Map<PlanLocationId, CompilerConstantLocation> = emptyMap(),
        val fields: Map<CompilerConstantLocation, CompilerValue> = emptyMap(),
        val borrows: Map<Int, ContextBorrowPayload> = emptyMap(),
        val borrowedRoots: Map<SymbolId, ContextBorrowPayload> = emptyMap(),
        val referenceRegisters: Map<Int, Map<List<PathSegment>, ReferenceMetadata>> = emptyMap(),
        val references: Map<CompilerConstantLocation, ReferenceMetadata> = emptyMap()
    ) {
        // A fact remains constant after a branch only when every incoming path agrees.
        fun join(other: State) = State(
            registers.filter { (key, value) -> other.registers[key] == value },
            roots.filter { (key, value) -> other.roots[key] == value },
            addresses.filter { (key, value) -> other.addresses[key] == value },
            fields.filter { (key, value) -> other.fields[key] == value },
            borrows.filter { (key, value) -> other.borrows[key] == value },
            borrowedRoots.filter { (key, value) -> other.borrowedRoots[key] == value },
            (referenceRegisters.keys + other.referenceRegisters.keys).associateWith {
                joinReferenceTrees(referenceRegisters[it].orEmpty(), other.referenceRegisters[it].orEmpty())
            },
            joinReferenceTrees(references, other.references)
        )
        fun read(location: CompilerConstantLocation?): CompilerValue? {
            location ?: return null
            fields[location]?.let { return it }
            var value = roots[location.root] ?: return null
            for (part in location.path) {
                value = when (val payload = unwrap(value)) {
                    is CompilerValue.Record -> (part as? PathSegment.Field)?.let { payload.fields[it.name] }
                    is CompilerValue.Sequence -> (part as? PathSegment.Index)?.let { payload.elements.getOrNull(it.index) }
                    else -> null
                } ?: return null
            }
            return value
        }
        fun borrow(location: CompilerConstantLocation?): ContextBorrowPayload? {
            location ?: return null
            var value = borrowedRoots[location.root] ?: return null
            for (part in location.path) value = value.member((part as? PathSegment.Field)?.name ?: return null) ?: return null
            return value
        }
        fun referenceTree(location: CompilerConstantLocation?): Map<List<PathSegment>, ReferenceMetadata> {
            location ?: return emptyMap()
            var tree = references.filterKeys { it.root == location.root }.mapKeys { it.key.path }
            location.path.forEach { tree = projectReferences(tree, it) }
            return tree
        }
    }
    private val before = IdentityHashMap<PlanOperation, State>()
    private val after = IdentityHashMap<PlanOperation, State>()
    private val exits = linkedMapOf<PlanBlockId, State>()
    private var current = State()
    private var produced = State()
    private val returnedReferences = IdentityHashMap<PlanOperation.Call, Map<List<PathSegment>, ReferenceMetadata>>()

    /** Forward must analysis. Only identical facts on every incoming edge survive a join. */
    fun analyze() {
        before.clear(); after.clear(); exits.clear()
        val initial = linkedMapOf<SymbolId, CompilerValue>()
        val fields = linkedMapOf<CompilerConstantLocation, CompilerValue>()
        val borrows = linkedMapOf<SymbolId, ContextBorrowPayload>()
        val references = linkedMapOf<CompilerConstantLocation, ReferenceMetadata>()
        out.plan.symbols.forEach { symbol ->
            val frozen = runCatching { out.bindings.storage(symbol.binding) }.getOrNull() ?: return@forEach
            frozen.borrow?.let {
                ContextBorrowing.requireUsable(it)
                borrows[symbol.binding] = it
            }
            if (frozen.data != null && frozen.location != null) {
                ContextBorrowing.payload(frozen.location.place, out.bindings.stored(frozen.data).facts::get)?.let {
                    ContextBorrowing.requireUsable(it)
                    borrows[symbol.binding] = it
                }
                val base = frozen.location.place
                out.bindings.stored(frozen.data).facts.forEach { (place, evidence) ->
                    if (place.root == base.root && place.path.take(base.path.size) == base.path && evidence.state == ValueState.INITIALIZED) {
                        evidence.reference?.let { metadata ->
                            metadata.borrows.forEach(ContextBorrowing::requireUsable)
                            references[CompilerConstantLocation(symbol.binding, place.path.drop(base.path.size))] = metadata
                        }
                    }
                }
            }
            if (frozen.trustConstants && frozen.data != null && frozen.location != null) {
                val fact = out.bindings.stored(frozen.data).facts[frozen.location.place]
                (fact?.value as? ValueKnowledge.Constant)?.value?.let { initial[symbol.binding] = it }
                out.bindings.stored(frozen.data).facts.forEach { (place, evidence) ->
                    val base = frozen.location.place
                    if (place.root == base.root && place.path.take(base.path.size) == base.path && evidence.state == ValueState.INITIALIZED) {
                        (evidence.value as? ValueKnowledge.Constant)?.value?.let {
                            fields[CompilerConstantLocation(symbol.binding, place.path.drop(base.path.size))] = it
                        }
                    }
                }
            }
        }
        val entries = linkedMapOf(out.plan.entry to State(roots = initial, fields = fields, borrowedRoots = borrows, references = references))
        val pending = ArrayDeque<PlanBlockId>().apply { add(out.plan.entry) }
        val blocks = out.plan.blocks.associateBy { it.id }
        while (pending.isNotEmpty()) {
            val id = pending.removeFirst()
            val block = blocks.getValue(id)
            var state = entries.getValue(id)
            for (operation in block.operations) {
                before[operation] = state
                state = transfer(operation, state)
                after[operation] = state
            }
            exits[id] = state
            val successors = when (val exit = block.terminator) {
                is PlanTerminator.Jump -> listOf(exit.target)
                is PlanTerminator.Branch -> listOf(exit.ifTrue, exit.ifFalse)
                is PlanTerminator.ContextEnter -> out.plan.contextRegions[exit.region.index].let { listOf(it.body, it.after) }
                is PlanTerminator.ContextNext -> out.plan.contextRegions[exit.region.index].let {
                    if (it.change is PlanContextChange.ExecuteAs) listOf(it.body, it.after) else listOf(it.after)
                }
                else -> emptyList()
            }
            for (next in successors) {
                val old = entries[next]
                val merged = old?.join(state) ?: state
                if (old != merged) {
                    entries[next] = merged
                    pending.add(next)
                }
            }
        }
    }
    fun enter(operation: PlanOperation) {
        current = before[operation] ?: State()
        produced = after[operation] ?: State()
    }
    fun enterExit(block: PlanBlockId) {
        current = exits[block] ?: State()
        produced = current
    }
    fun known(value: PlanTemp): CompilerValue? = current.registers[value.index]
    fun referenceTree(value: PlanTemp): Map<List<PathSegment>, ReferenceMetadata> = current.referenceRegisters[value.index].orEmpty()
    fun referenceTree(location: CompilerConstantLocation?): Map<List<PathSegment>, ReferenceMetadata> = current.referenceTree(location)
    fun referenceBorrows(value: PlanTemp) = referenceTree(value).values.flatMap { it.borrows }.toSet()
    fun attachReferences(value: PlanTemp, adapter: Var<*>): Var<*> = adapter.also {
        attachReferences(referenceTree(value), it)
    }
    fun attachReferences(tree: Map<List<PathSegment>, ReferenceMetadata>, adapter: Var<*>) {
        attachReferences(tree, StorageAccess.ensure(adapter))
    }
    fun attachReferences(tree: Map<List<PathSegment>, ReferenceMetadata>, binding: StorageBinding) {
        tree.forEach { (path, metadata) ->
            metadata.borrows.forEach(ContextBorrowing::requireUsable)
            val place = Place(binding.place.root, binding.place.path + path)
            val fact = binding.data.facts.read(place) ?: ValueFacts(TypeKnowledge.Unknown, ValueKnowledge.Unknown)
            binding.data.facts.refine(place, fact.copy(reference = metadata))
        }
    }
    fun recordReturn(operation: PlanOperation.Call, returned: Var<*>) {
        val binding = StorageAccess.ensure(returned)
        returnedReferences[operation] = binding.data.facts.entries().filterKeys {
            it.root == binding.place.root && it.path.take(binding.place.path.size) == binding.place.path
        }.mapNotNull { (place, fact) -> fact.reference?.let { place.path.drop(binding.place.path.size) to it } }.toMap()
        analyze()
    }
    fun resultReferences(): Map<List<PathSegment>, ReferenceMetadata> = out.plan.blocks.mapNotNull { block ->
        val value = (block.terminator as? PlanTerminator.Exit)?.value ?: return@mapNotNull null
        exits[block.id]?.referenceRegisters?.get(value.index).orEmpty()
    }.reduceOrNull(::joinReferenceTrees).orEmpty()
    fun finalReferences(location: CompilerConstantLocation): Map<List<PathSegment>, ReferenceMetadata> =
        out.plan.blocks.filter { it.terminator is PlanTerminator.Exit }.mapNotNull { exits[it.id] }
            .map { it.referenceTree(location) }.reduceOrNull(::joinReferenceTrees).orEmpty()
    fun borrowed(value: PlanTemp): ContextBorrowPayload? = current.borrows[value.index]
    fun borrowed(location: CompilerConstantLocation?): ContextBorrowPayload? = current.borrow(location)
    fun finalBorrow(location: CompilerConstantLocation): ContextBorrowPayload? {
        val values = out.plan.blocks.filter { it.terminator is PlanTerminator.Exit }.mapNotNull { exits[it.id] }.map { it.borrow(location) }
        return values.firstOrNull()?.takeIf { value -> values.all { it == value } }
    }
    fun borrowAdapter(value: PlanTemp, name: String): top.mcfpp.core.lang.Var<*>? = borrowed(value)?.let { payload ->
        try {
            ContextBorrowing.requireUsable(payload)
            payload.adapter(name, out.type(value.type)).also { it.bindDeclaration(name) }
        } catch (failure: ContextAccessException) { out.fail(failure.message.orEmpty()) }
    }
    fun knownRoot(root: SymbolId): CompilerValue? = current.roots[root]
    fun captured(id: PlanLocationId): CompilerConstantLocation? = produced.addresses[id]
    fun readConstant(location: CompilerPlanLocation): CompilerValue? = current.read(location.logical)
    fun finalConstant(location: CompilerConstantLocation): CompilerValue? {
        val states = out.plan.blocks.filter { it.terminator is PlanTerminator.Exit }.mapNotNull { exits[it.id] }
        val values = states.map { it.read(location) }
        return values.firstOrNull()?.takeIf { value -> values.all { it == value } }
    }
    fun compilerOnly(value: PlanTemp): Boolean = !out.type(value.type).hasRuntimeRepresentation ||
        known(value)?.let { containsType(it) } == true

    private fun transfer(operation: PlanOperation, state: State): State {
        var result = state
        fun put(register: PlanTemp, value: CompilerValue?) {
            result = result.copy(registers = if (value == null) result.registers - register.index else result.registers + (register.index to value),
                borrows = result.borrows - register.index, referenceRegisters = result.referenceRegisters - register.index)
        }
        fun references(register: PlanTemp, tree: Map<List<PathSegment>, ReferenceMetadata>) {
            result = result.copy(referenceRegisters = result.referenceRegisters + (register.index to tree))
        }
        fun writeReferences(location: CompilerConstantLocation?, tree: Map<List<PathSegment>, ReferenceMetadata>) {
            if (location == null) {
                result = result.copy(references = result.references.mapValues { it.value.invalidated() })
                return
            }
            val wildcard = location.path.indexOf(PathSegment.UnknownIndex)
            if (wildcard >= 0) {
                val prefix = location.path.take(wildcard)
                result = result.copy(references = result.references.mapValues { (old, metadata) ->
                    if (old.root == location.root && old.path.take(prefix.size) == prefix) metadata.invalidated() else metadata
                })
            }
            val retained = result.references.filterKeys { old -> old.root != location.root ||
                !(old.path.take(location.path.size) == location.path || location.path.take(old.path.size) == old.path) }
            result = result.copy(references = retained + tree.mapKeys { CompilerConstantLocation(location.root, location.path + it.key) })
        }
        fun borrow(register: PlanTemp, payload: ContextBorrowPayload?) {
            if (payload != null) {
                payload.adapter("borrow_type_check", out.type(register.type))
                result = result.copy(registers = result.registers - register.index, borrows = result.borrows + (register.index to payload))
            }
        }
        fun write(location: CompilerConstantLocation?, value: CompilerValue?) {
            if (location == null) {
                result = result.copy(roots = emptyMap(), fields = emptyMap(), borrowedRoots = emptyMap())
                return
            }
            val closed = if (location.path.isEmpty()) value else value?.let { state.roots[location.root]?.replacing(location.path, it) }
            val retained = result.fields.filterKeys { old ->
                old.root != location.root || !(old.path.take(location.path.size) == location.path || location.path.take(old.path.size) == old.path)
            }
            result = result.copy(
                roots = if (closed == null) result.roots - location.root else result.roots + (location.root to closed),
                fields = if (value == null) retained else retained + (location to value),
                borrowedRoots = result.borrowedRoots - location.root
            )
        }
        fun writeBorrow(location: CompilerConstantLocation?, payload: ContextBorrowPayload?) {
            if (payload == null) return
            if (location == null || location.path.isNotEmpty()) out.fail("Scoped context values cannot escape into projected fields or containers")
            result = result.copy(borrowedRoots = result.borrowedRoots + (location.root to payload))
        }
        fun address(location: PlanLocation): CompilerConstantLocation? {
            val base = location.capturedBase?.let { state.addresses[it] ?: return null }
                ?: CompilerConstantLocation(location.root.binding)
            val tail = location.steps.map { step ->
                when (step) {
                    is PlanLocationStep.Field -> PathSegment.Field(step.field.name)
                    is PlanLocationStep.Index -> PathSegment.Index(step.value)
                    is PlanLocationStep.Key -> PathSegment.Field(step.value)
                    is PlanLocationStep.CapturedIndex -> (state.roots[step.symbol.binding]?.let(::unwrap) as? CompilerValue.Integral)
                        ?.let { PathSegment.Index(it.value.toInt()) } ?: PathSegment.UnknownIndex
                    is PlanLocationStep.CapturedKey -> (state.roots[step.symbol.binding]?.let(::unwrap) as? CompilerValue.Text)
                        ?.let { PathSegment.Field(it.value) } ?: PathSegment.UnknownIndex
                }
            }
            return CompilerConstantLocation(base.root, base.path + tail)
        }
        when (operation) {
            is PlanOperation.Literal -> put(operation.result, typedConstant(operation.result.type, operation.value))
            is PlanOperation.Move -> {
                put(operation.result, state.registers[operation.value.index])
                borrow(operation.result, state.borrows[operation.value.index])
                references(operation.result, state.referenceRegisters[operation.value.index].orEmpty())
            }
            is PlanOperation.CaptureLocation -> {
                val captured = address(operation.location)
                result = result.copy(addresses = if (captured == null) state.addresses - operation.result else state.addresses + (operation.result to captured))
            }
            is PlanOperation.DeclareLocal -> {
                val location = address(operation.location)
                write(location, state.registers[operation.initialValue.index])
                writeBorrow(location, state.borrows[operation.initialValue.index])
                writeReferences(location, state.referenceRegisters[operation.initialValue.index].orEmpty())
            }
            is PlanOperation.Read -> {
                val location = state.addresses[operation.location]
                put(operation.result, state.read(location))
                borrow(operation.result, state.borrow(location))
                references(operation.result, state.referenceTree(location))
            }
            is PlanOperation.Write -> {
                val location = state.addresses[operation.location]
                write(location, state.registers[operation.value.index])
                writeBorrow(location, state.borrows[operation.value.index])
                writeReferences(location, state.referenceRegisters[operation.value.index].orEmpty())
            }
            is PlanOperation.Convert -> {
                val source = state.registers[operation.operand.index]
                val numeric = operation.operand.type.kind in setOf(TypeKind.INT, TypeKind.FLOAT) &&
                    operation.result.type.kind in setOf(TypeKind.INT, TypeKind.FLOAT) &&
                    operation.operand.type.key != operation.result.type.key
                put(operation.result, if (numeric) null else source?.let { CompilerValue.Typed(out.type(operation.result.type).typeId, it) })
                borrow(operation.result, state.borrows[operation.operand.index])
                references(operation.result, state.referenceRegisters[operation.operand.index].orEmpty())
            }
            is PlanOperation.Field -> {
                put(operation.result, (state.registers[operation.receiver.index]?.let(::unwrap) as? CompilerValue.Record)?.fields?.get(operation.field.name))
                borrow(operation.result, state.borrows[operation.receiver.index]?.member(operation.field.name))
                references(operation.result, projectReferences(state.referenceRegisters[operation.receiver.index].orEmpty(), PathSegment.Field(operation.field.name)))
            }
            is PlanOperation.Index -> {
                val list = state.registers[operation.receiver.index]?.let(::unwrap) as? CompilerValue.Sequence
                val index = state.registers[operation.index.index]?.let(::unwrap) as? CompilerValue.Integral
                put(operation.result, if (index == null) null else list?.elements?.getOrNull(index.value.toInt()))
                references(operation.result, projectReferences(state.referenceRegisters[operation.receiver.index].orEmpty(),
                    index?.let { PathSegment.Index(it.value.toInt()) } ?: PathSegment.UnknownIndex))
            }
            is PlanOperation.Binary -> {
                val left = state.registers[operation.left.index]?.let(::unwrap) as? CompilerValue.Text
                val right = state.registers[operation.right.index]?.let(::unwrap) as? CompilerValue.Text
                put(
                    operation.result,
                    if (operation.operator == BinaryOperator.ADD && left != null && right != null)
                        CompilerValue.Typed(out.type(operation.result.type).typeId, CompilerValue.Text(left.value + right.value)) else null
                )
            }
            is PlanOperation.Unary -> put(operation.result, null)
            is PlanOperation.Call -> {
                operation.result?.let { put(it, null) }
                operation.result?.let { references(it, returnedReferences[operation].orEmpty()) }
                result = result.copy(roots = emptyMap(), fields = emptyMap(),
                    references = result.references.mapValues { it.value.invalidated() })
            }
            is PlanOperation.BlockMatches -> put(operation.result, null)
            is PlanOperation.CaptureScore -> Unit
        }
        return result
    }
    fun slot(value: PlanTemp): String {
        out.type(value.type)
        return out.slot("value_${value.index}")
    }
    fun path(value: PlanTemp) = out.path("value_${value.index}")

    fun constant(value: PlanConstant): CompilerValue = when (value) {
        is PlanConstant.Integral -> CompilerValue.Integral(value.value)
        is PlanConstant.FloatBits -> CompilerValue.FloatBits(value.bits)
        is PlanConstant.DoubleBits -> CompilerValue.DoubleBits(value.bits)
        is PlanConstant.Bool -> CompilerValue.Bool(value.value)
        is PlanConstant.Text -> CompilerValue.Text(value.value)
        is PlanConstant.Nbt -> CompilerValue.Nbt(value.snbt)
        is PlanConstant.TypeValue -> CompilerValue.TypeValue(out.type(value.type).typeId)
        PlanConstant.NullValue -> CompilerValue.NullValue
        is PlanConstant.Sequence -> CompilerValue.Sequence(value.elements.map(::constant))
        is PlanConstant.Record -> CompilerValue.Record(value.fields.mapValues { constant(it.value) })
    }

    fun literal(result: PlanTemp, value: PlanConstant) {
        val closed = typedConstant(result.type, value)
        val tag = StorageAccess.snapshotTag(closed)
        if (tag == null) {
            if (isNull(closed)) out.emit("data remove ${slot(result)}")
            else if (out.type(result.type).hasRuntimeRepresentation && !containsType(closed)) out.fail("Constant has no valid runtime encoding")
            return
        }
        out.emitLiteral("data modify ${slot(result)} set value ${Tag.toSNBT(tag)}")
    }

    /** Canonical schemas carry element/field types, including NBT array element widths. */
    fun typedConstant(type: PlanType, value: PlanConstant): CompilerValue {
        val graph = out.bindings.binding.types.graph
        val payload = when (val schema = graph.constantSchema(type.key)) {
            is ConstantSchema.Sequence -> if (value is PlanConstant.Sequence) CompilerValue.Sequence(
                value.elements.map { typedConstant(PlanType.from(graph.type(schema.element)), it) }
            ) else constant(value)
            is ConstantSchema.Record -> if (value is PlanConstant.Record) CompilerValue.Record(
                value.fields.mapValues { (key, child) ->
                    typedConstant(PlanType.from(graph.type(schema.fields.getValue(key))), child)
                }
            ) else constant(value)
            else -> constant(value)
        }
        return CompilerValue.Typed(out.type(type).typeId, payload)
    }

    fun copy(result: PlanTemp, value: PlanTemp) {
        borrowed(value)?.let { ContextBorrowing.requireUsable(it); return }
        if (compilerOnly(value)) {
            val closed = requireClosed(value)
            if (isNull(closed) && out.type(result.type).hasRuntimeRepresentation) out.emit("data remove ${slot(result)}")
            return
        }
        copyTo(slot(result), value)
    }
    fun copyTo(destination: String, value: PlanTemp) {
        if (borrowed(value) != null) out.fail("Scoped context values cannot be serialized into runtime storage")
        if (compilerOnly(value)) {
            if (isNull(requireClosed(value))) {
                out.emit("data remove $destination")
                return
            }
            out.fail("Compiler-only constant has no runtime storage representation")
        }
        if (destination != slot(value)) {
            if (known(value)?.let(::isNull) == true) out.emit("data remove $destination")
            else {
                // Replacing a list element must preserve its index; removing it first shifts its siblings.
                val source = slot(value)
                out.emit("execute unless data $source run data remove $destination")
                out.emit("execute if data $source run data modify $destination set from $source")
            }
        }
    }
    fun requireClosed(value: PlanTemp): CompilerValue {
        if (borrowed(value) != null) out.fail("Scoped context borrow is not a serializable closed value")
        return known(value) ?: out.fail("Compiler-only value differs across incoming control-flow paths")
    }
    fun resultConstant(): CompilerValue? {
        val results = out.plan.blocks.mapNotNull { block ->
            val value = (block.terminator as? PlanTerminator.Exit)?.value ?: return@mapNotNull null
            exits[block.id]?.registers?.get(value.index) to value.type
        }
        val closed = results.firstOrNull()?.first ?: return null
        if (results.any { it.first != closed }) return null
        return closed.takeIf { !out.type(results.first().second).hasRuntimeRepresentation || containsType(it) || isNull(it) }
    }
    fun compilerTypes(value: CompilerValue): Map<TypeId, MCFPPType> {
        val types = linkedMapOf<TypeId, MCFPPType>()
        fun add(id: TypeId) {
            types[id] = out.bindings.binding.types.resolve(id) ?: out.fail("Closed constant type is outside the frozen source catalog")
        }
        fun visit(part: CompilerValue) {
            when (part) {
                is CompilerValue.Typed -> {
                    add(part.type)
                    visit(part.payload)
                }
                is CompilerValue.TypeValue -> add(part.id)
                is CompilerValue.Sequence -> part.elements.forEach(::visit)
                is CompilerValue.Record -> part.fields.values.forEach(::visit)
                else -> Unit
            }
        }
        visit(value)
        return types
    }

    fun score(value: PlanTemp): String = out.score("value").also { score ->
        out.emit("scoreboard players set $score 0")
        out.emit("execute store result score $score run data get ${slot(value)} 1")
    }
    private fun fromScore(result: PlanTemp, score: String) {
        val tag = if (result.type.kind == TypeKind.BOOL) "byte" else "int"
        out.emit("execute store result ${slot(result)} $tag 1 run scoreboard players get $score")
    }
    fun read(result: PlanTemp, location: CompilerPlanLocation) {
        produced.borrows[result.index]?.let { ContextBorrowing.requireUsable(it); return }
        if (location.layout == StorageLayout.CompilerOnly ||
            !out.type(result.type).hasRuntimeRepresentation ||
            produced.registers[result.index]?.let(::containsType) == true
        ) {
            if (produced.registers[result.index] == null) out.fail("Compiler-only location has no common closed value at this read")
            return
        }
        if (produced.registers[result.index]?.let(::isNull) == true) {
            out.emit("data remove ${slot(result)}")
            return
        }
        when (val layout = location.layout) {
            is StorageLayout.Scoreboard -> {
                val score = out.score("read")
                out.emit("scoreboard players set $score 0")
                out.emit("execute if score ${layout.player} ${layout.objective} = ${layout.player} ${layout.objective} run scoreboard players operation $score = ${layout.player} ${layout.objective}")
                fromScore(result, score)
            }
            is StorageLayout.LegacyFloat -> {
                out.emit("data modify ${slot(result)} set value {}")
                layout.components.zip(listOf("sign", "int0", "int1", "exp")).forEach { (score, field) ->
                    out.emit("execute store result ${slot(result)}.$field int 1 run scoreboard players get ${score.player} ${score.objective}")
                }
            }
            else -> {
                out.emit("data remove ${slot(result)}")
                out.emit("data modify ${slot(result)} set from ${location.address ?: out.fail("Read has no physical address")}")
            }
        }
    }

    private fun provider(value: PlanTemp): String = FloatProviders.storageProvider(
        (path(value).source as StorageSource).storage,
        path(value).pathToCommandPart().analyze()
    )
    private fun floatCompute(result: PlanTemp, provider: String) = out.emit("data modify ${slot(result)} set compute default float $provider")

    fun unary(operation: PlanOperation.Unary) {
        if (operation.operand.type.kind == TypeKind.FLOAT) {
            if (FloatProviders.enabled) floatCompute(operation.result, "{type:\"minecraft:negate\",input:${provider(operation.operand)}}")
            else {
                copy(operation.result, operation.operand)
                val sign = out.score("sign")
                out.emit("execute store result score $sign run data get ${slot(operation.result)}.sign 1")
                val negative = out.score("negative")
                out.emit("scoreboard players set $negative -1")
                out.emit("scoreboard players operation $sign *= $negative")
                out.emit("execute store result ${slot(operation.result)}.sign int 1 run scoreboard players get $sign")
            }
            return
        }
        val input = score(operation.operand)
        val result = out.score("unary")
        out.emit("scoreboard players set $result 0")
        when (operation.operator) {
            UnaryOperator.NOT -> out.emit("execute if score $input matches 0 run scoreboard players set $result 1")
            UnaryOperator.NEGATE -> out.emit("scoreboard players operation $result -= $input")
        }
        fromScore(operation.result, result)
    }

    fun binary(operation: PlanOperation.Binary) {
        val spelling = when (operation.operator) {
            BinaryOperator.ADD -> "+"
            BinaryOperator.SUBTRACT -> "-"
            BinaryOperator.MULTIPLY -> "*"
            BinaryOperator.DIVIDE -> "/"
            BinaryOperator.MODULO -> "%"
            BinaryOperator.EQ -> "=="
            BinaryOperator.NE -> "!="
            BinaryOperator.LT -> "<"
            BinaryOperator.LE -> "<="
            BinaryOperator.GT -> ">"
            BinaryOperator.GE -> ">="
            BinaryOperator.AND, BinaryOperator.OR -> out.fail("Logical operators must lower to guarded basic blocks")
        }
        if (operation.left.type.kind == TypeKind.STRING) {
            if (operation.operator == BinaryOperator.ADD) {
                val left = known(operation.left)?.let(::unwrap) as? CompilerValue.Text
                val right = known(operation.right)?.let(::unwrap) as? CompilerValue.Text
                if (left != null && right != null) {
                    val tag = StorageAccess.snapshotTag(CompilerValue.Text(left.value + right.value))!!
                    out.emitLiteral("data modify ${slot(operation.result)} set value ${Tag.toSNBT(tag)}")
                } else {
                    strings.add(operation)
                }
            } else {
                val compared = out.scratch("compare")
                copyTo(compared, operation.left)
                val different = out.score("different")
                out.emit("execute store success score $different run data modify $compared set from ${slot(operation.right)}")
                if (operation.operator == BinaryOperator.NE) fromScore(operation.result, different)
                else {
                    val equal = out.score("equal")
                    out.emit("scoreboard players set $equal 0")
                    out.emit("execute if score $different matches 0 run scoreboard players set $equal 1")
                    fromScore(operation.result, equal)
                }
            }
            return
        }
        if (operation.left.type.kind == TypeKind.FLOAT) {
            if (FloatProviders.enabled) {
                if (operation.result.type.kind == TypeKind.FLOAT)
                    floatCompute(operation.result, FloatProviders.arithmeticProvider(provider(operation.left), provider(operation.right), spelling))
                else {
                    val result = out.score("comparison")
                    out.emit("execute store success score $result ${FloatProviders.comparisonClause(provider(operation.left), provider(operation.right), spelling)}")
                    fromScore(operation.result, result)
                }
            } else {
                Project.enableModulePackage("math.float", "stdlib")
                if (operation.result.type.kind == TypeKind.FLOAT)
                    LegacyFloatCommands.arithmetic(path(operation.left), path(operation.right), spelling, path(operation.result), out::emit)
                else {
                    val result = out.score("comparison")
                    LegacyFloatCommands.comparison(path(operation.left), path(operation.right), spelling, out.score("compare"), result, out::emit)
                    fromScore(operation.result, result)
                }
            }
            return
        }
        val left = score(operation.left)
        val right = score(operation.right)
        val result = out.score("binary")
        if (operation.result.type.kind == TypeKind.INT) {
            out.emit("scoreboard players operation $result = $left")
            out.emit("scoreboard players operation $result $spelling= $right")
        } else {
            out.emit("scoreboard players set $result 0")
            val relation = if (spelling == "==" || spelling == "!=") "=" else spelling
            out.emit("execute ${if (spelling == "!=") "unless" else "if"} score $left $relation $right run scoreboard players set $result 1")
        }
        fromScore(operation.result, result)
    }

    fun convert(operation: PlanOperation.Convert) {
        val source = out.type(operation.operand.type)
        val target = out.type(operation.result.type)
        val graph = out.bindings.binding.types.graph
        if (!graph.permits(operation.operand.type.key, operation.result.type.key, operation.kind)) out.fail("Conversion has no frozen representation proof")
        if (source === MCFPPBaseType.Int && target === MCFPPBaseType.Float) {
            val input = score(operation.operand)
            if (FloatProviders.enabled) floatCompute(operation.result, "{type:\"minecraft:from_int\",input:${FloatProviders.scoreProvider(input.substringBefore(' '), input.substringAfter(' '))}}")
            else {
                Project.enableModulePackage("math.float", "stdlib")
                LegacyFloatCommands.fromInt(input, path(operation.result), out::emit)
            }
            return
        }
        if (source === MCFPPBaseType.Float && target === MCFPPBaseType.Int) {
            val input = out.score("convert").also {
                if (FloatProviders.enabled) out.emit("execute store result score $it run compute default integer {type:\"minecraft:from_float\",input:${provider(operation.operand)}}")
                else {
                    Project.enableModulePackage("math.float", "stdlib")
                    LegacyFloatCommands.toInt(path(operation.operand), it, out::emit)
                }
            }
            fromScore(operation.result, input)
            return
        }
        // Every remaining enrolled conversion is a representation view, including erased,
        // structural and wide-NBT views. Never squeeze a view through a 32-bit score.
        copy(operation.result, operation.operand)
    }

    fun field(operation: PlanOperation.Field) {
        out.type(operation.field.type)
        produced.borrows[operation.result.index]?.let { ContextBorrowing.requireUsable(it); return }
        if (!out.type(operation.result.type).hasRuntimeRepresentation) {
            if (produced.registers[operation.result.index] == null) out.fail("Compiler-only field has no closed value")
            return
        }
        out.emit("data remove ${slot(operation.result)}")
        out.emit("data modify ${slot(operation.result)} set from ${slot(operation.receiver)}.${StorageAccess.quotedKey(operation.field.name)}")
    }
    fun index(operation: PlanOperation.Index) {
        if (!out.type(operation.result.type).hasRuntimeRepresentation) {
            if (produced.registers[operation.result.index] == null) out.fail("Compiler-only indexed value has no closed value")
            return
        }
        val index = out.locations.captureValue(operation.index)
        out.emit("data remove ${slot(operation.result)}")
        out.emit("data modify ${slot(operation.result)} set from ${slot(operation.receiver)}[$index]")
    }
    companion object {
        fun unwrap(value: CompilerValue): CompilerValue = if (value is CompilerValue.Typed) unwrap(value.payload) else value
        fun isNull(value: CompilerValue) = unwrap(value) == CompilerValue.NullValue
        fun containsType(value: CompilerValue): Boolean = when (value) {
            is CompilerValue.Typed -> containsType(value.payload)
            is CompilerValue.TypeValue -> true
            is CompilerValue.Sequence -> value.elements.any(::containsType)
            is CompilerValue.Record -> value.fields.values.any(::containsType)
            else -> false
        }
    }
}

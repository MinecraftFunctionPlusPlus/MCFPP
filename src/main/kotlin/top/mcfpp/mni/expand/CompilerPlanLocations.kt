package top.mcfpp.mni.expand

import top.mcfpp.analysis.*
import top.mcfpp.core.lang.Var
import top.mcfpp.lib.NBTPath
import top.mcfpp.mni.api.*
import top.mcfpp.nbt.tags.Tag
import top.mcfpp.type.MCFPPBaseType
import java.util.Collections
import java.util.IdentityHashMap

/** A dynamic path component whose value was frozen into one plan workspace slot. */
internal data class CompilerFrozenOperand(val position: Int, val slot: NBTPath, val type: PlanType)

/** An address, never a copied REF payload. Dynamic components name captured frame slots. */
internal class CompilerPlanLocation(
    val address: String?,
    val layout: StorageLayout?,
    val type: PlanType,
    val data: StoredData? = null,
    val place: Place? = null,
    val writable: Boolean = true,
    val logical: CompilerConstantLocation? = null,
    val physical: Location? = null,
    val executionContext: ExecutionContextId? = null,
    dynamic: List<CompilerFrozenOperand> = emptyList()
) {
    val dynamic: List<CompilerFrozenOperand> = immutableList(dynamic)

    /** A helper specialization preserves the original address; only the live frame index moves. */
    internal fun withAddress(value: String?): CompilerPlanLocation = CompilerPlanLocation(value, layout, type, data, place, writable, logical, physical, executionContext, dynamic)
}

internal class CompilerPlanLocations(private val out: CompilerPlanEmitter) {
    private val addresses = linkedMapOf<PlanLocationId, CompilerPlanLocation>()
    private val locals = linkedMapOf<top.mcfpp.mni.api.SymbolId, CompilerPlanLocation>()
    private val localData = Collections.newSetFromMap(IdentityHashMap<StoredData, Boolean>())
    private var captureOrdinal = 0
    private val compilerWrites = linkedMapOf<CompilerConstantLocation, CompilerPlanLocation>()
    private val referenceWrites = linkedMapOf<CompilerConstantLocation, CompilerPlanLocation>()
    private val authorizedFields = linkedSetOf<FieldKey>()

    /**
     * Add the exactly checked field capabilities of a generated detached body.
     *
     * The source environment authorizes the caller's original field access; a generated body was
     * checked against its declaration validator's own accessible-field set. Both are explicit
     * compiler proofs; this is never an unchecked "allow all fields" switch.
     */
    fun authorize(fields: Set<FieldKey>) {
        authorizedFields.addAll(fields)
    }

    private fun fieldAuthorized(key: FieldKey): Boolean = key in authorizedFields || key in out.bindings.fieldCapabilities

    fun get(id: PlanLocationId): CompilerPlanLocation = addresses[id] ?: out.fail("Address is not captured before its use")

    /**
     * Reopen a captured location as a genuine physical adapter over its original address.
     *
     * The adapter shares the captured data/place/layout, so a native or ordinary member
     * write still targets the caller's storage. It never copies the current payload and
     * never reads the live caller again. Scoreboard components become an independent
     * synthetic data root because only their player/objective identifies the storage.
     */
    fun adapter(id: PlanLocationId, name: String): Var<*> {
        val location = get(id)
        val compilerType = out.type(location.type)
        out.values.borrowed(location.logical)?.let { payload ->
            top.mcfpp.core.lang.context.ContextBorrowing.requireUsable(payload)
            return payload.adapter(name, compilerType).also { it.bindDeclaration(name) }
        }
        if (!compilerType.hasRuntimeRepresentation)
            out.fail("A compiler-only value cannot be reopened as a physical receiver")
        val binding = if (location.data != null && location.place != null) {
            val data = location.data
            val place = location.place
            val physical = location.physical?.let(::staticPhysical)
                ?: out.fail("Captured receiver has a dynamic address; a full frozen dynamic adapter is required before it can be reopened physically")
            data.types.putIfAbsent(compilerType.typeId, compilerType)
            if (data.facts.read(place) == null)
                data.facts.write(
                    place,
                    ValueFacts(
                        TypeKnowledge.Exact(compilerType.typeId),
                        ValueKnowledge.Unknown,
                        ValueState.INITIALIZED
                    )
                )
            data.declarations.putIfAbsent(
                place,
                top.mcfpp.analysis.Symbol(
                    place.root,
                    name,
                    compilerType.typeId,
                    mutable = location.writable,
                    readonly = !location.writable
                )
            )
            StorageBinding(data, place, data.path, location = physical)
        } else {
            val layout = location.layout ?: out.fail("Captured receiver has no physical layout")
            if (layout == StorageLayout.CompilerOnly)
                out.fail("Compiler-only value cannot be reopened as a physical receiver")
            val root = Place(top.mcfpp.analysis.SymbolId.fresh())
            val data = StoredData(root, out.path("receiver_${captureOrdinal++}"), layout = layout)
            data.types[compilerType.typeId] = compilerType
            data.declarations[root] = top.mcfpp.analysis.Symbol(
                root.root,
                name,
                compilerType.typeId,
                mutable = location.writable,
                readonly = !location.writable
            )
            data.facts.initialize(root, ValueFacts(TypeKnowledge.Exact(compilerType.typeId), ValueKnowledge.Unknown))
            StorageBinding(data, root, data.path)
        }
        val layout = binding.data.layoutAt(binding.place)
        val adapterName = if (layout is StorageLayout.Scoreboard) layout.player else name
        return StorageAccess.adapter(compilerType, adapterName, binding).also {
            out.values.attachReferences(out.values.referenceTree(location.logical), it)
        }
    }

    /**
     * Reopen a member receiver whose path still contains frozen dynamic components.
     *
     * The native adapter deliberately rejects a stored root with a pre-existing dynamic
     * address. A source member receiver, however, has already evaluated and frozen every
     * index into a plan address slot, so the same original data/place can be reopened with
     * a [Location] whose captured operands point at those immutable slots. Writes then
     * resolve the frozen `$(aN)` components instead of reading the caller's live index.
     */
    fun memberReceiver(id: PlanLocationId, name: String): Var<*> {
        val location = get(id)
        if (location.dynamic.isEmpty() || location.data == null || location.place == null)
            return adapter(id, name)
        val compilerType = out.type(location.type)
        if (!compilerType.hasRuntimeRepresentation)
            out.fail("A compiler-only value cannot be reopened as a physical member receiver")
        val data = location.data
        val place = location.place
        val captured = linkedMapOf<Int, ValueRef>()
        val capturedLocations = linkedMapOf<Int, Location>()
        location.dynamic.forEach { operand ->
            val type = out.type(operand.type)
            if (!type.hasRuntimeRepresentation)
                out.fail("A compiler-only dynamic address component cannot be reopened")
            val slot = out.adapter(operand.type, "${name}_key${operand.position}", operand.slot)
            val binding = requireNotNull(slot.storageBinding) {
                "A frozen dynamic address component has no physical slot binding"
            }
            // StoredData.address resolves captured ValueRef roots through this source table.
            data.retainSource(binding.data)
            captured[operand.position] = ValueRef.Read(type.typeId, binding.place)
            capturedLocations[operand.position] = binding.location
        }
        data.types.putIfAbsent(compilerType.typeId, compilerType)
        if (data.facts.read(place) == null)
            data.facts.write(
                place,
                ValueFacts(
                    TypeKnowledge.Exact(compilerType.typeId),
                    ValueKnowledge.Unknown,
                    ValueState.INITIALIZED
                )
            )
        data.declarations.putIfAbsent(
            place,
            top.mcfpp.analysis.Symbol(
                place.root,
                name,
                compilerType.typeId,
                mutable = location.writable,
                readonly = !location.writable
            )
        )
        val dynamic = Location(place, captured = captured, capturedLocations = capturedLocations)
        val binding = StorageBinding(data, place, data.path, location = dynamic)
        val layout = binding.data.layoutAt(binding.place)
        val adapterName = if (layout is StorageLayout.Scoreboard) layout.player else name
        return StorageAccess.adapter(compilerType, adapterName, binding).also {
            out.values.attachReferences(out.values.referenceTree(location.logical), it)
        }
    }

    /** Return a macro operand whose value is assigned at this exact operation position. */
    fun captureValue(value: PlanTemp): String {
        val key = "a_${captureOrdinal++}"
        out.values.copyTo("${out.slot("addresses")}.$key", value)
        return "\$($key)"
    }

    fun capture(id: PlanLocationId, location: PlanLocation) {
        out.type(location.type)
        // A root's declared type and a projected field/index type are different layers. Only a
        // direct capture of the root itself may be compared with the root's declared type; for a
        // projection the leaf type is carried by [PlanLocation.type] and applied after the steps.
        val directRoot = location.steps.isEmpty()
        val base = location.capturedBase?.let(::get)
            ?: root(location.root.binding, location.type.takeIf { directRoot }, location)
        if (directRoot && base.type.key != location.type.key)
            out.fail(
                "A captured root address changed its declared type from " +
                    "'${base.type.name}' to '${location.type.name}'"
            )
        // A REF helper parameter's own location is context-free; the substituted original address
        // carries the caller context. Preserve that binding through the specialized body.
        val context = location.executionContext ?: base.executionContext
        requireLocationContext(base, context, "A captured location")
        var address = base.address
        var place = base.place
        var physical = base.physical
        val dynamic = arrayListOf<CompilerFrozenOperand>().apply { addAll(base.dynamic) }
        for (step in location.steps) {
            if (address == null) out.fail("A score address cannot have field or index steps")
            address += when (step) {
                is PlanLocationStep.Field -> {
                    out.type(step.field.type)
                    if (!fieldAuthorized(step.field.key))
                        out.fail("Field is outside the captured field capability set")
                    place = place?.field(step.field.name)
                    physical = physical?.child(PathSegment.Field(step.field.name))
                    ".${StorageAccess.quotedKey(step.field.name)}"
                }
                is PlanLocationStep.Index -> {
                    place = place?.index(step.value)
                    physical = physical?.child(PathSegment.Index(step.value))
                    "[${step.value}]"
                }
                is PlanLocationStep.Key -> {
                    place = place?.field(step.value)
                    physical = physical?.child(PathSegment.Field(step.value))
                    ".${StorageAccess.quotedKey(step.value)}"
                }
                is PlanLocationStep.CapturedIndex -> {
                    val frozen = captureRoot(step.symbol)
                    place = place?.unknownIndex()
                    place?.let {
                        dynamic += CompilerFrozenOperand(
                            it.path.size - 1,
                            frozen.slot,
                            PlanType.from(out.bindings.binding.types.ref(MCFPPBaseType.Int))
                        )
                    }
                    physical = null
                    "[${frozen.value}]"
                }
                is PlanLocationStep.CapturedKey -> {
                    val key = out.values.knownRoot(step.symbol.binding)?.let(CompilerPlanValues::unwrap) as? CompilerValue.Text
                        ?: out.fail("Dynamic string keys require a safe path encoding backend; the original address cannot be represented")
                    place = place?.field(key.value)
                    physical = physical?.child(PathSegment.Field(key.value))
                    ".${StorageAccess.quotedKey(key.value)}"
                }
            }
        }
        val layout = if (base.data != null && place != null) {
            // Generated locals belong to this emitter, after the source snapshot was frozen.
            // Every other root must still carry the source bridge's enrollment proof.
            if (base.data in localData) base.data.layoutAt(place)
            else out.bindings.stored(base.data).layoutAt(place)
        }
        else if (location.steps.isEmpty()) base.layout else null
        // A field projection's own declared type is the final layer. This is deliberately separate
        // from the root comparison above: for `self.value` the root stays `Box` while this checks
        // that the projected `int` is exactly the selected field's declared type.
        (location.steps.lastOrNull() as? PlanLocationStep.Field)?.let { field ->
            if (field.field.type.key != location.type.key)
                out.fail(
                    "A captured field projection changed its declared type from " +
                        "'${field.field.type.name}' to '${location.type.name}'"
                )
        }
        addresses[id] = CompilerPlanLocation(
            address, layout, location.type,
            base.data, place, location.writable && base.writable, out.values.captured(id), physical,
            context, dynamic
        )
    }

    fun captureScore(id: PlanLocationId, address: PlanScoreAddress) {
        out.type(address.type)
        val holder = when (val target = address.target) {
            is PlanScoreTarget.Fixed -> target.holder
            is PlanScoreTarget.Entity -> {
                out.requireSameExecutor(address.frame, "An entity score address")
                "@s"
            }
        }
        if (holder.startsWith('@') && holder != "@s")
            out.fail("Only the current executor @s can represent a context-bound score address")
        addresses[id] = CompilerPlanLocation(
            null,
            StorageLayout.Scoreboard(holder, address.objective),
            address.type,
            executionContext = address.frame?.binding
        )
    }

    fun declare(location: PlanLocation, initial: PlanTemp) {
        if (location.capturedBase != null || location.steps.isNotEmpty()) out.fail("Local declaration must name its own root")
        val address = out.slot("local_${location.root.index}")
        val type = out.type(location.type)
        val symbol = top.mcfpp.analysis.Symbol(top.mcfpp.analysis.SymbolId.fresh(), "mni_local_${location.root.index}", type.typeId, true)
        val place = Place(symbol.id)
        val data = StoredData(place, out.path("local_${location.root.index}"))
        localData += data
        data.types[type.typeId] = type
        data.declarations[place] = symbol
        data.facts.initialize(place, ValueFacts(TypeKnowledge.Exact(type.typeId), ValueKnowledge.Unknown))
        val local = CompilerPlanLocation(
            address,
            if (!out.type(location.type).hasRuntimeRepresentation) StorageLayout.CompilerOnly else null,
            location.type,
            data = data, place = place, logical = CompilerConstantLocation(location.root.binding), physical = Location(place)
        )
        locals[location.root.binding] = local
        write(local, initial)
    }

    /**
     * Resolve one capture root without conflating its declared type with a projected leaf type.
     *
     * [directType] is non-null only when the capture names the root itself. A projected
     * field/index capture passes null, so a `Box` self substitution is never compared with the
     * `int` field type that the applied steps produce. The returned root location is only an
     * address/layout carrier for a projection: [capture] always installs [PlanLocation.type]
     * as the captured location's final type.
     */
    private fun root(
        symbol: top.mcfpp.mni.api.SymbolId,
        directType: PlanType?,
        location: PlanLocation
    ): CompilerPlanLocation {
        out.substitution(symbol)?.let { substituted ->
            if (directType != null && substituted.type.key != directType.key)
                out.fail(
                    "A specialized REF address changed its declared type from " +
                        "'${substituted.type.name}' to '${directType.name}'"
                )
            return substituted
        }
        locals[symbol]?.let { local ->
            if (directType != null && local.type.key != directType.key)
                out.fail(
                    "A local REF address changed its declared type from " +
                        "'${local.type.name}' to '${directType.name}'"
                )
            return local
        }
        val frozen = out.bindings.storage(symbol)
        if (frozen.contextBound) requireContext(location.executionContext, frozen, "A context-bound storage address")
        frozen.data?.let { data ->
            val stored = out.bindings.stored(data)
            val storedLocation = frozen.location ?: out.fail("Captured stored data is missing its Location")
            return stored(stored, storedLocation, directType, location.executionContext)
        }
        val address = frozen.path?.let { frozenPath(it) }
        return CompilerPlanLocation(
            address,
            frozen.layout,
            directType ?: projectedRootType(symbol, location),
            executionContext = location.executionContext
        )
    }

    /** Fixed addresses keep their metadata but are safe in any executor. */
    private fun requireLocationContext(
        location: CompilerPlanLocation,
        context: ExecutionContextId?,
        description: String
    ) {
        if (!out.contextSensitive(location)) return
        requireContext(context, null, description)
        when (val layout = location.layout) {
            is StorageLayout.Scoreboard -> requirePlayer(layout.player, context, description)
            is StorageLayout.LegacyFloat -> layout.components.forEach {
                requirePlayer(it.player, context, description)
            }
            else -> Unit
        }
    }

    private fun requireContext(context: ExecutionContextId?, stored: CompilerStorageLocation?, description: String) {
        if (context == null) out.fail("$description requires an execution context")
        val frame = out.locationFrame(context) ?: out.fail("$description has an unregistered execution context")
        out.requireSameExecutor(frame, description)
        when (val layout = stored?.layout) {
            is StorageLayout.Scoreboard -> requirePlayer(layout.player, context, description)
            is StorageLayout.LegacyFloat -> layout.components.forEach { requirePlayer(it.player, context, description) }
            else -> Unit
        }
    }

    private fun requirePlayer(player: String, context: ExecutionContextId?, description: String) {
        if (!player.startsWith('@')) return
        if (player != "@s")
            out.fail("Only the current executor @s can represent a context-bound $description")
        if (context == null) out.fail("$description requires an execution context")
    }

    /**
     * Address/layout metadata for a projected root. It is never published as the captured
     * location type; the first field step names the actual receiver owner, and [stored] uses the
     * same width-neutral int sentinel when a captured operand only needs an address carrier.
     */
    private fun projectedRootType(
        symbol: top.mcfpp.mni.api.SymbolId,
        location: PlanLocation
    ): PlanType {
        val declared = runCatching {
            PlanType.from(out.bindings.binding.types.ref(out.bindings.original(symbol).type))
        }.getOrNull()
        return declared
            ?: (location.steps.firstOrNull() as? PlanLocationStep.Field)?.field?.ownerType
            ?: PlanType.from(out.bindings.binding.types.ref(MCFPPBaseType.Int))
    }

    private class CapturedRoot(val value: String, val slot: NBTPath)

    private fun captureRoot(symbol: PlanSymbol): CapturedRoot {
        val base = locals[symbol.binding]
        val key = "a_${captureOrdinal++}"
        val destination = "${out.slot("addresses")}.$key"
        if (base != null) {
            out.emit("data modify $destination set from ${base.address}")
        } else {
            val physical = out.bindings.storage(symbol.binding)
            if (physical.contextBound) out.fail("Context-bound dynamic operands require context lowering")
            val score = physical.layout as? StorageLayout.Scoreboard
            if (score != null) {
                out.emit("data modify $destination set value 0")
                out.emit("execute store result $destination int 1 run scoreboard players get ${score.player} ${score.objective}")
            } else if (physical.data != null && physical.location != null) {
                val frozen = out.bindings.stored(physical.data)
                val address = stored(frozen, physical.location, null).address ?: out.fail("Dynamic operand is not physically readable")
                out.emit("data modify $destination set from $address")
            } else {
                val address = physical.path?.let { frozenPath(it) } ?: out.fail("Dynamic operand has no frozen address")
                out.emit("data modify $destination set from $address")
            }
        }
        return CapturedRoot("\$($key)", out.path("addresses").memberIndex(key))
    }

    /** Consume only the frozen root/path/layout tables, including retained dynamic sources. */
    private fun stored(
        stored: CompilerStoredData,
        location: Location,
        type: PlanType?,
        executionContext: ExecutionContextId? = null
    ): CompilerPlanLocation {
        var address = frozenPath(stored.path, location.frameOffset)
        for ((position, segment) in location.place.path.withIndex()) {
            if (position < stored.root.path.size) continue
            address += when (segment) {
                is PathSegment.Field -> ".${StorageAccess.quotedKey(segment.name)}"
                is PathSegment.Index -> "[${segment.index}]"
                PathSegment.UnknownIndex -> {
                    val reference = location.captured[position] ?: location.keys[position]
                        ?: out.fail("Frozen dynamic location has no captured address operand")
                    val stringKey = position in location.keys || reference.type == MCFPPBaseType.String.typeId
                    val value = if (stringKey) {
                        val text = ((reference as? ValueRef.Constant)?.value?.let(CompilerPlanValues::unwrap) as? CompilerValue.Text)?.value
                            ?: out.fail("Frozen dynamic string keys require a safe path encoding backend")
                        StorageAccess.quotedKey(text)
                    } else reference(reference, stored, location.capturedLocations[position]?.inFrame(location.frameOffset))
                    when {
                        position in location.predicates -> "[$value]"
                        position in location.keys || reference.type == MCFPPBaseType.String.typeId -> ".$value"
                        else -> "[$value]"
                    }
                }
            }
        }
        val layout = stored.layoutAt(location.place)
        val effective = type ?: PlanType.from(
            out.bindings.binding.types.ref(
                MCFPPBaseType.Int
            )
        ) // Used only as address metadata for a captured operand.
        return CompilerPlanLocation(
            address,
            layout,
            effective,
            stored.original,
            location.place,
            physical = staticPhysical(location),
            executionContext = executionContext
        )
    }

    /**
     * Only an address whose path is fully frozen can be reopened as a physical adapter.
     * A root location may already contain [PathSegment.UnknownIndex] or captured operands
     * from its original StorageBinding, so the absence of a new plan step is not sufficient.
     */
    private fun staticPhysical(location: Location?): Location? = location?.takeIf { candidate ->
        candidate.place.path.none { it == PathSegment.UnknownIndex } &&
            candidate.indices.isEmpty() &&
            candidate.keys.isEmpty() &&
            candidate.captured.isEmpty() &&
            candidate.predicates.isEmpty() &&
            candidate.capturedLocations.isEmpty()
    }

    private fun reference(value: ValueRef, owner: CompilerStoredData, location: Location?): String = when (value) {
        is ValueRef.Constant -> closed(value.value)
        is ValueRef.TypedView -> reference(value.source, owner, value.location)
        is ValueRef.Read -> {
            val source = owner.source(value.place) ?: out.fail("Captured address source is not enrolled")
            val frozen = out.bindings.stored(source)
            val address = stored(frozen, location ?: Location(value.place), null)
            captureAddress(address, value.type == MCFPPBaseType.Bool.typeId)
        }
        is ValueRef.Result -> out.fail("An instruction result without an enrolled physical address cannot be captured")
    }
    private fun captureAddress(address: CompilerPlanLocation, boolean: Boolean = false): String {
        val key = "a_${captureOrdinal++}"
        val destination = "${out.slot("addresses")}.$key"
        val score = address.layout as? StorageLayout.Scoreboard
        if (score != null) {
            out.emit("data modify $destination set value 0")
            out.emit("execute store result $destination ${if (boolean) "byte" else "int"} 1 run scoreboard players get ${score.player} ${score.objective}")
        } else out.emit("data modify $destination set from ${address.address ?: out.fail("Captured operand has no address")}")
        return "\$($key)"
    }

    private fun closed(value: CompilerValue): String = when (value) {
        is CompilerValue.Typed -> closed(value.payload)
        is CompilerValue.Text -> value.value
        else -> StorageAccess.snapshotTag(value)?.let { Tag.toSNBT(it) }
            ?: out.fail("Address operand has no runtime encoding")
    }
    private fun operand(operand: CompilerAddressOperand): String {
        operand.constant?.let { return closed(it) }
        if (operand.contextBound) out.fail("Context-bound address operands require context lowering")
        operand.score?.let { score ->
            return captureAddress(
                CompilerPlanLocation(
                    null,
                    StorageLayout.Scoreboard(score.player, score.objective),
                    PlanType.from(out.bindings.binding.types.ref(MCFPPBaseType.Int))
                )
            )
        }
        val data = operand.data ?: out.fail("Frozen address operand has no enrolled storage")
        val owner = out.bindings.stored(data)
        return reference(operand.reference ?: out.fail("Frozen address operand has no reference"), owner, operand.location)
    }
    private fun frozenPath(path: CompilerNbtPath, frameOffset: Int = 0): String {
        val source = path.source as? CompilerNbtSource.Storage
            ?: out.fail("Entity/block NBT roots require execution-context lowering")
        var address = "storage ${source.storage} "
        path.steps.forEachIndexed { index, step ->
            address += when (step) {
                is CompilerNbtStep.Member -> {
                    if (step.value.constant == null) out.fail("Frozen dynamic NBT members require a safe path encoding backend")
                    (if (index == 0) "" else ".") + operand(step.value)
                }
                is CompilerNbtStep.Index -> "[${operand(step.value)}]"
                is CompilerNbtStep.Predicate -> "[${operand(step.value)}]"
                CompilerNbtStep.Iterator -> "[]"
            }
        }
        if (frameOffset != 0) address = Regex("^(storage \\S+ stack_frame\\[)(\\d+)(])").replace(address) {
            it.groupValues[1] + (it.groupValues[2].toInt() + frameOffset) + it.groupValues[3]
        }
        return address
    }

    fun write(location: CompilerPlanLocation, value: PlanTemp) {
        if (!location.writable) out.fail("Cannot write a readonly captured location")
        out.values.referenceBorrows(value).forEach { payload ->
            top.mcfpp.core.lang.context.ContextBorrowing.requireUsable(payload)
            val data = location.data ?: out.fail("A scoped reference requires a local destination")
            val place = location.place ?: out.fail("A scoped reference requires a stable local destination")
            val declaration = data.declarations[place]
            val destination = declaration?.contextScope ?: data.contextScope
            if (place.path.isNotEmpty() || destination == null || !payload.owner.scope.contains(destination))
                out.fail("Scoped references cannot escape into globals, outer scopes or containers")
            destination.requireActive()
        }
        out.values.borrowed(value)?.let { payload ->
            top.mcfpp.core.lang.context.ContextBorrowing.requireUsable(payload)
            payload.adapter("borrow_write_type", out.type(location.type))
            if (location.data != null && location.place != null) {
                val declaration = location.data.declarations[location.place]
                val destination = if (declaration != null) declaration.contextScope else location.data.contextScope
                if (location.place.path.isNotEmpty() || destination == null || !payload.owner.scope.contains(destination))
                    out.fail("Scoped context values cannot escape into globals, outer scopes or containers")
                destination.requireActive()
                val logical = location.logical ?: out.fail("Scoped source assignment has no stable identity")
                compilerWrites[logical] = location
            }
            return
        }
        if (location.layout == StorageLayout.CompilerOnly || !out.type(location.type).hasRuntimeRepresentation) {
            out.values.requireClosed(value)
            if (location.data != null && location.place != null) {
                val logical = location.logical ?: out.fail("Compiler-only write has no stable source identity")
                compilerWrites[logical] = location
            }
            return
        }
        when (val layout = location.layout) {
            is StorageLayout.Scoreboard -> {
                val source = out.values.score(value)
                out.emit("scoreboard players operation ${layout.player} ${layout.objective} = $source")
            }
            is StorageLayout.LegacyFloat -> layout.components.zip(listOf("sign", "int0", "int1", "exp")).forEach { (score, field) ->
                out.emit("execute store result score ${score.player} ${score.objective} run data get ${out.values.slot(value)}.$field 1")
            }
            else -> out.values.copyTo(location.address ?: out.fail("Write has no physical address"), value)
        }
        if (location.data != null && location.place != null) {
            location.data.write(location.place, ValueFacts(TypeKnowledge.Exact(out.type(location.type).typeId), ValueKnowledge.Unknown))
            location.logical?.let { referenceWrites[it] = location }
        }
    }

    /** Source initializers are one-shot compiler callbacks, so emit them in the dominating caller. */
    fun initializeSources() {
        val seen = Collections.newSetFromMap(IdentityHashMap<StoredData, Boolean>())
        fun materialize(data: StoredData) {
            if (!seen.add(data)) return
            val frozen = out.bindings.stored(data)
            frozen.sources.values.forEach(::materialize)
            if (frozen.layout != StorageLayout.CompilerOnly) data.materialize(frozen.root)
        }
        out.plan.symbols.forEach { symbol ->
            runCatching { out.bindings.storage(symbol.binding) }.getOrNull()?.data?.let(::materialize)
        }
    }

    fun finishCompilerWrites() {
        referenceWrites.forEach { (logical, location) ->
            val data = requireNotNull(location.data)
            val place = requireNotNull(location.place)
            out.values.attachReferences(out.values.finalReferences(logical), StorageBinding(data, place, data.path))
        }
        compilerWrites.forEach { (logical, location) ->
            out.values.finalBorrow(logical)?.let { payload ->
                top.mcfpp.core.lang.context.ContextBorrowing.requireUsable(payload)
                val data = requireNotNull(location.data)
                val place = requireNotNull(location.place)
                data.retainCompilerValue()
                data.facts.writeConstant(place, ValueFacts(TypeKnowledge.Exact(out.type(location.type).typeId),
                    ValueKnowledge.Borrow(payload)))
                return@forEach
            }
            val closed = out.values.finalConstant(logical)
                ?: out.fail("Compiler-only source assignment differs across control-flow exits")
            val data = requireNotNull(location.data)
            val place = requireNotNull(location.place)
            data.versions.invalidate(place)
            data.facts.writeConstant(place, ValueFacts(TypeKnowledge.Exact(out.type(location.type).typeId), ValueKnowledge.Constant(closed)))
        }
    }
}

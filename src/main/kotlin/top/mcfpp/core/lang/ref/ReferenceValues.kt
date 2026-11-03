package top.mcfpp.core.lang.ref

import top.mcfpp.analysis.*
import top.mcfpp.backend.ReferenceAccessHelpers
import top.mcfpp.command.Commands
import top.mcfpp.core.lang.Var
import top.mcfpp.core.lang.context.ContextBorrowPayload
import top.mcfpp.core.lang.context.ContextBorrowing
import top.mcfpp.core.lang.entity.EntityTargetAccess
import top.mcfpp.lib.NBTPath
import top.mcfpp.lib.SelectorExpression
import top.mcfpp.model.function.Function
import top.mcfpp.type.*
import top.mcfpp.util.TempPool

/** Public address values use the ordinary value store and its NBT copy/ABI machinery. */
object ReferenceValues {
    private fun unwrap(value: CompilerValue): CompilerValue = if (value is CompilerValue.Typed) unwrap(value.payload) else value

    fun metadata(value: Var<*>): ReferenceMetadata? = value.storageBinding?.let { binding ->
        binding.data.facts.readAccess(binding.place, binding.data.declarations, binding.data.types)?.reference
    }
    fun descriptor(value: Var<*>): ReferenceDescriptor? = metadata(value)?.staticDescriptor

    /** Restored constants derive their evidence from the descriptor itself, without a global cache. */
    internal fun metadataFromSnapshot(value: CompilerValue): ReferenceMetadata? {
        val fields = unwrap(value) as? CompilerValue.Record ?: return null
        val key = (unwrap(fields.fields["kind"] ?: return null) as? CompilerValue.Text)?.value ?: return null
        val kind = ReferenceKind.entries.firstOrNull { it.key == key } ?: return null
        val target = fields.fields["target"]?.let(::unwrap) as? CompilerValue.Record
        val targetKind = target?.fields?.get("kind")?.let(::unwrap) as? CompilerValue.Text
        val shape = when (targetKind?.value) {
            "player_name" -> ReferenceTargetShape.PlayerName
            "uuid_text" -> ReferenceTargetShape.UUIDText
            "bound_executor" -> return null // A frozen record cannot reconstruct a lexical capability.
            "selector" -> {
                val name = (target!!.fields["selectorKind"]?.let(::unwrap) as? CompilerValue.Text)?.value ?: return null
                val selectorKind = top.mcfpp.lib.EntitySelector.Companion.SelectorType.entries.firstOrNull { it.name == name }
                    ?: return null
                val filters = (target.fields["filters"]?.let(::unwrap) as? CompilerValue.Sequence)?.elements?.map {
                    val filter = unwrap(it) as? CompilerValue.Record ?: return null
                    val operand = filter.fields["operand"] as? CompilerValue.Typed ?: return null
                    ReferenceTargetShape.Filter((filter.fields["name"] as? CompilerValue.Text)?.value ?: return null,
                        (filter.fields["reverse"] as? CompilerValue.Bool)?.value ?: return null, operand.type)
                } ?: return null
                ReferenceTargetShape.Selector(selectorKind, filters)
            }
            else -> null
        }
        val pathShape = (fields.fields["pathSuffix"]?.let(::unwrap) as? CompilerValue.Sequence)?.elements.orEmpty().map {
            val part = unwrap(it) as? CompilerValue.Record ?: return null
            when ((part.fields["kind"]?.let(::unwrap) as? CompilerValue.Text)?.value) {
                "field" -> ReferencePathShape.Field((part.fields["key"]?.let(::unwrap) as? CompilerValue.Text)?.value ?: return null)
                "index" -> ReferencePathShape.Index
                else -> return null
            }
        }
        val descriptor = ReferenceDescriptor(kind, fields, shape, pathShape)
        val facts = when (shape) {
            ReferenceTargetShape.PlayerName -> top.mcfpp.lib.EntityFacts.PLAYER
            ReferenceTargetShape.UUIDText -> top.mcfpp.lib.EntityFacts.SINGLE
            is ReferenceTargetShape.Selector -> {
                val filters = (target!!.fields["filters"] as CompilerValue.Sequence).elements
                val snapshot = CompilerValue.Typed(MCFPPEntityType.NormalSelector.typeId, CompilerValue.Record(mapOf(
                    "kind" to CompilerValue.Text(shape.kind.name), "predicates" to CompilerValue.Sequence(filters))))
                SelectorExpression.restore(snapshot)?.target(MCFPPType.builtinTypesById())?.facts ?: top.mcfpp.lib.EntityFacts.UNKNOWN
            }
            else -> top.mcfpp.lib.EntityFacts.UNKNOWN
        }
        return ReferenceMetadata(setOf(descriptor), targetFacts = facts)
    }

    /** Includes nested data/container references, so erasure or wrapping cannot hide a borrow. */
    fun provenance(value: Var<*>): Set<ContextBorrowPayload> {
        val binding = value.storageBinding ?: return emptySet()
        return metadata(value)?.borrows.orEmpty() + binding.data.facts.entries().filterKeys { place -> place.root == binding.place.root &&
            place.path.take(binding.place.path.size) == binding.place.path }
            .values.flatMap { it.reference?.borrows.orEmpty() }.toSet()
    }

    fun payload(value: Var<*>): NBTPath = StorageAccess.ensure(value).path

    /** A dispatch marker identifies retained capabilities; it cannot restore an executor. */
    internal fun borrowIdentity(borrow: ContextBorrowPayload.Executor): String =
        "${borrow.owner.session.id}_${borrow.owner.scope.id}_${borrow.owner.components.executor.value}"

    /** Rebuild a known entity command shape over operands owned by the current descriptor payload. */
    fun entityTarget(value: Var<*>, descriptor: ReferenceDescriptor = ReferenceValues.descriptor(value)
        ?: error("Reference entity shape is unknown")): top.mcfpp.lib.EntityTarget? {
        val binding = StorageAccess.ensure(value)
        fun operand(type: MCFPPType, segments: List<Any>): SelectorExpression.Operand {
            var child = binding
            for (segment in segments) child = when (segment) {
                is String -> child.field(segment)
                is Int -> child.copy(place = child.place.index(segment), path = child.path.intIndex(segment))
                else -> error("Unknown entity operand segment")
            }
            child.data.types[type.typeId] = type
            if (child.data.facts.read(child.place)?.state != ValueState.INITIALIZED)
                child.data.facts.refine(child.place, ValueFacts(TypeKnowledge.Exact(type.typeId), ValueKnowledge.Unknown))
            val closed = child.data.facts.read(child.place)?.value as? ValueKnowledge.Constant
            return if (closed != null) SelectorExpression.Operand(ValueRef.Constant(type.typeId, closed.value))
            else SelectorExpression.Operand(ValueRef.Read(type.typeId, child.place), child.location)
        }
        return when (val shape = descriptor.targetShape) {
            ReferenceTargetShape.PlayerName -> top.mcfpp.lib.EntityTarget.PlayerName(
                operand(MCFPPBaseType.String, listOf("target", "operand")).adapter(binding.data.types, binding.data, 0)
                    as top.mcfpp.core.lang.nbt.MCString)
            ReferenceTargetShape.UUIDText -> top.mcfpp.lib.EntityTarget.UUIDText(
                operand(MCFPPBaseType.String, listOf("target", "operand")).adapter(binding.data.types, binding.data, 0)
                    as top.mcfpp.core.lang.nbt.MCString)
            is ReferenceTargetShape.Selector -> SelectorExpression(shape.kind, shape.filters.mapIndexed { index, filter ->
                val type = binding.data.types[filter.type] ?: MCFPPType.resolveTypeId(filter.type) ?: return null
                SelectorExpression.Filter(filter.name, filter.reverse, operand(type, listOf("target", "filters", index, "operand")))
            }, SelectorExpression.frame(binding.path)).target(binding.data.types, binding)
            else -> null
        }
    }

    /** Read an owned descriptor field at the reference's current location, including unknown runtime values. */
    fun field(value: Var<*>, name: String, type: MCFPPType): Var<*> {
        val binding = StorageAccess.ensure(value).field(name)
        binding.data.types[type.typeId] = type
        if (binding.data.facts.read(binding.place)?.state != ValueState.INITIALIZED)
            binding.data.facts.refine(binding.place, ValueFacts(TypeKnowledge.Exact(type.typeId), ValueKnowledge.Unknown))
        return StorageAccess.adapter(type, value.identifier + "_" + name, binding)
    }

    internal fun pathIndex(value: Var<*>, index: Int): Var<*> {
        val root = StorageAccess.ensure(value).field("pathSuffix")
        val binding = root.copy(place = root.place.index(index), path = root.path.intIndex(index)).field("operand")
        binding.data.types[MCFPPBaseType.Int.typeId] = MCFPPBaseType.Int
        if (binding.data.facts.read(binding.place)?.state != ValueState.INITIALIZED)
            binding.data.facts.refine(binding.place, ValueFacts(TypeKnowledge.Exact(MCFPPBaseType.Int.typeId), ValueKnowledge.Unknown))
        return StorageAccess.adapter(MCFPPBaseType.Int, "reference_index_$index", binding)
    }

    /** Callers supply explicit Minecraft address operands, already evaluated from left to right. */
    fun create(type: MCFPPReferenceType, kind: ReferenceKind, fields: Map<String, Var<*>>,
               entity: Var<*>? = null, name: String = TempPool.getVarIdentify(),
               pathSuffix: List<ReferencePathPart> = emptyList()): ReferenceVar {
        require((type == MCFPPScoreRefType) == (kind == ReferenceKind.SCORE_HOLDER || kind == ReferenceKind.SCORE_ENTITY))
        val required = when (kind) {
            ReferenceKind.SCORE_HOLDER -> setOf("holder", "objective")
            ReferenceKind.SCORE_ENTITY -> setOf("objective")
            ReferenceKind.NBT_STORAGE -> setOf("storage", "path")
            ReferenceKind.NBT_ENTITY -> setOf("path")
            ReferenceKind.NBT_BLOCK -> setOf("dimension", "x", "y", "z", "path")
        }
        require(fields.keys == required) { "Invalid explicit address fields for $kind: ${fields.keys}" }
        require((entity != null) == (kind == ReferenceKind.SCORE_ENTITY || kind == ReferenceKind.NBT_ENTITY))
        val values = linkedMapOf<String, CompilerValue>("kind" to CompilerValue.Text(kind.key))
        val dynamic = arrayListOf<Pair<List<Any>, Var<*>>>()
        val types = linkedMapOf<TypeId, MCFPPType>(type.typeId to type)
        fun capture(path: List<Any>, value: Var<*>): CompilerValue? {
            require(!value.isError && StorageAccess.hasActualPayload(value)) { "Address operand has no initialized payload" }
            types.putAll(StorageAccess.boundTypes(value)); types[value.type.typeId] = value.type
            return StorageAccess.snapshot(value) ?: run {
                val owned = StorageAccess.capture(value)
                require(!owned.isError && StorageAccess.hasRuntimeRepresentation(owned)) { "Address operand cannot be captured" }
                dynamic += path to owned
                null
            }
        }
        for ((key, value) in fields) capture(listOf(key), value)?.let { values[key] = it }
        require(pathSuffix.isEmpty() || kind !in setOf(ReferenceKind.SCORE_HOLDER, ReferenceKind.SCORE_ENTITY))
        val pathShape = pathSuffix.map { part -> when (part) {
            is ReferencePathPart.Field -> ReferencePathShape.Field(part.name)
            is ReferencePathPart.Index -> ReferencePathShape.Index
        } }
        if (pathSuffix.isNotEmpty()) values["pathSuffix"] = CompilerValue.Sequence(pathSuffix.mapIndexed { index, part ->
            when (part) {
                is ReferencePathPart.Field -> CompilerValue.Record(mapOf("kind" to CompilerValue.Text("field"), "key" to CompilerValue.Text(part.name)))
                is ReferencePathPart.Index -> {
                    require(part.value.type == MCFPPBaseType.Int) { "NBT reference indices require int" }
                    val item = linkedMapOf<String, CompilerValue>("kind" to CompilerValue.Text("index"))
                    capture(listOf("pathSuffix", index, "operand"), part.value)?.let { item["operand"] = it }
                    CompilerValue.Record(item)
                }
            }
        })
        var shape: ReferenceTargetShape? = null
        val borrows = linkedSetOf<ContextBorrowPayload>()
        var targetFacts = top.mcfpp.lib.EntityFacts.UNKNOWN
        if (entity != null) {
            val borrow = ContextBorrowing.payload(entity)
            if (borrow != null) {
                require(borrow is ContextBorrowPayload.Executor) { "Only an entity executor can address entity data" }
                ContextBorrowing.requireUsable(borrow)
                borrows += borrow
                shape = ReferenceTargetShape.BoundExecutor
                targetFacts = top.mcfpp.lib.EntityFacts.SINGLE
                values["target"] = CompilerValue.Record(mapOf("kind" to CompilerValue.Text("bound_executor"),
                    "borrow" to CompilerValue.Text(borrowIdentity(borrow))))
            } else {
                val binding = StorageAccess.ensure(entity)
                val expression = StorageAccess.selectorProgram(binding)
                    ?: error("Entity address needs a complete entity access description")
                targetFacts = (entity as? EntityTargetAccess)?.entityFacts ?: targetFacts
                val target = linkedMapOf<String, CompilerValue>()
                val direct = expression.directTarget
                if (direct != null) {
                    shape = when (direct.kind) {
                        SelectorExpression.DirectKind.PLAYER_NAME -> ReferenceTargetShape.PlayerName
                        SelectorExpression.DirectKind.UUID_TEXT -> ReferenceTargetShape.UUIDText
                    }
                    target["kind"] = CompilerValue.Text(direct.kind.name.lowercase())
                    val operand = direct.operand.adapter(binding.data.types, binding.data,
                        SelectorExpression.frame(binding.path) - expression.ownerFrame) ?: error("Missing entity address operand")
                    capture(listOf("target", "operand"), operand)?.let { target["operand"] = it }
                } else {
                    shape = ReferenceTargetShape.Selector(expression.kind, expression.predicates.map {
                        ReferenceTargetShape.Filter(it.identifier, it.reverse, it.operand.reference.type)
                    })
                    target["kind"] = CompilerValue.Text("selector")
                    target["selectorKind"] = CompilerValue.Text(expression.kind.name)
                    target["filters"] = CompilerValue.Sequence(expression.predicates.mapIndexed { index, filter ->
                        val item = linkedMapOf<String, CompilerValue>("name" to CompilerValue.Text(filter.identifier),
                            "reverse" to CompilerValue.Bool(filter.reverse))
                        val operand = filter.operand.adapter(binding.data.types, binding.data,
                            SelectorExpression.frame(binding.path) - expression.ownerFrame) ?: error("Missing entity filter operand")
                        capture(listOf("target", "filters", index, "operand"), operand)?.let { item["operand"] = it }
                        CompilerValue.Record(item)
                    })
                }
                values["target"] = CompilerValue.Record(target)
            }
        }
        val entityKind = kind == ReferenceKind.SCORE_ENTITY || kind == ReferenceKind.NBT_ENTITY
        val helper = ReferenceAccessHelpers.register(kind, shape, !entityKind || targetFacts.selectingSingleEntity, types, pathShape)
        values["write"] = CompilerValue.Text(helper.writeId)
        helper.readId?.let { values["read"] = CompilerValue.Text(it) }
        val record = CompilerValue.Record(values)
        val result = if (dynamic.isEmpty()) StorageAccess.literal(type, record, name, types) as ReferenceVar else {
            val runtime = type.buildUnConcrete(name)
            runtime.nbtPath = NBTPath.stack.intIndex(0).memberIndex(name)
            Function.addCommand(Commands.dataSetValue(runtime.nbtPath, StorageAccess.snapshotTag(record)!!))
            for ((segments, input) in dynamic) {
                var path = runtime.nbtPath
                for (segment in segments) path = when (segment) {
                    is String -> path.memberIndex(StorageAccess.quotedKey(segment))
                    is Int -> path.intIndex(segment)
                    else -> error("Unknown descriptor segment")
                }
                StorageAccess.encodeTo(path, input)
            }
            StorageAccess.publishNbt(runtime)
            runtime.storageBinding!!.data.types.putAll(types)
            runtime
        }
        val binding = StorageAccess.ensure(result)
        val fact = binding.data.facts.read(binding.place)!!
        binding.data.facts.refine(binding.place, fact.copy(reference = ReferenceMetadata(
            setOf(ReferenceDescriptor(kind, record, shape, pathShape)), borrows, targetFacts)))
        return result
    }
}

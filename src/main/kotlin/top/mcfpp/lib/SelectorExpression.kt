package top.mcfpp.lib

import top.mcfpp.analysis.*
import top.mcfpp.command.Command
import top.mcfpp.command.Commands
import top.mcfpp.core.lang.*
import top.mcfpp.core.lang.entity.SelectorVar
import top.mcfpp.core.lang.nbt.*
import top.mcfpp.core.lang.obj.DataTemplateObject
import top.mcfpp.type.*
import top.mcfpp.model.function.Function
import top.mcfpp.nbt.tags.primitive.StringTag
import top.mcfpp.util.TempPool
import java.util.Collections

/** Immutable direct-target structure, including operands captured before their source changes. */
class SelectorExpression private constructor(
    val selectorKind: EntitySelector.Companion.SelectorType?,
    predicates: List<Filter>,
    val ownerFrame: Int,
    val directTarget: DirectTarget?
) {
    constructor(kind: EntitySelector.Companion.SelectorType, predicates: List<Filter>, ownerFrame: Int = 0) :
        this(kind, predicates, ownerFrame, null)

    enum class DirectKind { PLAYER_NAME, UUID_TEXT }
    data class DirectTarget(val kind: DirectKind, val operand: Operand)

    /** Legacy selector-only access; direct targets never acquire a synthetic selector kind. */
    val kind: EntitySelector.Companion.SelectorType get() = selectorKind
        ?: error("A named or UUID entity target has no selector kind")

    val predicates: List<Filter> = Collections.unmodifiableList(ArrayList(predicates))
    override fun equals(other: Any?) = other is SelectorExpression && selectorKind == other.selectorKind &&
        predicates == other.predicates && ownerFrame == other.ownerFrame && directTarget == other.directTarget
    override fun hashCode() = 31 * (31 * (31 * (selectorKind?.hashCode() ?: 0) + predicates.hashCode()) + ownerFrame) +
        (directTarget?.hashCode() ?: 0)
    data class Operand(val reference: ValueRef, val location: Location? = null) {
        fun adapter(types: Map<TypeId, MCFPPType>, owner: StoredData?, frameOffset: Int): Var<*>? {
            val type = types[reference.type] ?: MCFPPType.resolveTypeId(reference.type) ?: return null
            if (reference is ValueRef.Constant) {
                StorageAccess.restore(type, reference.value, TempPool.getVarIdentify(), types)?.let { return it }
                return null
            }
            val place = (reference as? ValueRef.Read)?.place ?: return null
            val data = owner?.source(place) ?: return null
            val actual = location?.inFrame(frameOffset) ?: return null
            val binding = StorageBinding(data, place, data.path, location = actual)
            val name = data.declarations[place]?.name ?: TempPool.getVarIdentify()
            if (type == MCFPPBaseType.Range) return SelectorRangeOperand(binding.path).apply {
                storageBinding = binding
                isTemp = true
            }
            return StorageAccess.adapter(type, name, binding).apply { isTemp = true }
        }
    }
    data class Filter(val identifier: String, val reverse: Boolean, val operand: Operand)

    fun target(types: Map<TypeId, MCFPPType>, binding: StorageBinding? = null): EntityTarget? {
        val direct = directTarget ?: return selector(types, binding)?.let { EntityTarget.Selector(it) }
        val offset = binding?.let { frame(it.path) - ownerFrame } ?: 0
        val text = direct.operand.adapter(types, binding?.data, offset) as? MCString ?: return null
        return when (direct.kind) {
            DirectKind.PLAYER_NAME -> EntityTarget.PlayerName(text)
            DirectKind.UUID_TEXT -> EntityTarget.UUIDText(text)
        }
    }

    fun selector(types: Map<TypeId, MCFPPType>, binding: StorageBinding? = null): EntitySelector? {
        val selector = EntitySelector(selectorKind ?: return null)
        val offset = binding?.let { frame(it.path) - ownerFrame } ?: 0
        for (filter in predicates) {
            val operand = filter.operand.adapter(types, binding?.data, offset) ?: return null
            selector.addPredicate(predicate(filter.identifier, filter.reverse, operand) ?: return null)
        }
        return selector
    }
    fun snapshot(type: TypeId): CompilerValue? {
        directTarget?.let { direct ->
            val constant = direct.operand.reference as? ValueRef.Constant ?: return null
            return CompilerValue.Typed(type, CompilerValue.Record(mapOf(
                "targetKind" to CompilerValue.Text(direct.kind.name),
                "operand" to constant.value
            )))
        }
        val filters = predicates.map { filter ->
            val constant = filter.operand.reference as? ValueRef.Constant ?: return null
            CompilerValue.Record(mapOf(
                "name" to CompilerValue.Text(filter.identifier),
                "reverse" to CompilerValue.Bool(filter.reverse),
                "operand" to constant.value
            ))
        }
        return CompilerValue.Typed(type, CompilerValue.Record(mapOf(
            "kind" to CompilerValue.Text((selectorKind ?: return null).name), "predicates" to CompilerValue.Sequence(filters)
        )))
    }
    companion object {
        fun capture(target: EntityTarget, ownerData: StoredData, previous: SelectorExpression? = null,
                    actualFrame: Int = 0): SelectorExpression? = when (target) {
            is EntityTarget.Selector -> capture(target.selector, ownerData, previous?.takeIf { it.selectorKind != null }, actualFrame)
            is EntityTarget.PlayerName -> captureOperand(target.name, ownerData, actualFrame)?.let {
                SelectorExpression(null, emptyList(), actualFrame, DirectTarget(DirectKind.PLAYER_NAME, it))
            }
            is EntityTarget.UUIDText -> captureOperand(target.uuid, ownerData, actualFrame)?.let {
                SelectorExpression(null, emptyList(), actualFrame, DirectTarget(DirectKind.UUID_TEXT, it))
            }
        }

        private fun captureOperand(value: Var<*>, ownerData: StoredData, actualFrame: Int): Operand? {
            if (value.isError) return null
            val snapshot = StorageAccess.snapshot(value)
            if (snapshot != null) return Operand(ValueRef.Constant(value.type.typeId, snapshot))
            val captured = StorageAccess.capture(value)
            if (captured.isError || !StorageAccess.hasRuntimeRepresentation(captured)) return null
            val slot = TempPool.getVarIdentify()
            val path = NBTPath.stack.intIndex(actualFrame).memberIndex(slot)
            StorageAccess.encodeTo(path, captured)
            val frozen = captured.type.buildUnConcrete(slot).apply {
                isTemp = true
                nbtPath = path
                StorageAccess.publishNbt(this)
            }
            val binding = StorageAccess.ensure(frozen)
            val produced = StorageAccess.ensure(captured)
            binding.data.types.putAll(produced.data.types)
            binding.data.facts.copyFrom(produced.data.facts.withoutValues(), produced.place, binding.place, includeRoot = false)
            ownerData.retainSource(binding.data)
            ownerData.types.putAll(binding.data.types)
            return Operand(ValueRef.Read(frozen.type.typeId, binding.place), binding.location)
        }

        fun capture(selector: EntitySelector, ownerData: StoredData, previous: SelectorExpression? = null, actualFrame: Int = 0): SelectorExpression? {
            val owner = previous?.ownerFrame ?: actualFrame
            val filters = selector.predicates.mapIndexed { index, filter ->
                previous?.predicates?.getOrNull(index)?.let { return@mapIndexed it }
                if (filter is ScoresPredicate) return null
                val operand = captureOperand(filter.v, ownerData, actualFrame) ?: return null
                Filter(filter.identifier, (filter as? CanReverseEntitySelectorPredicate)?.reverse ?: false, operand)
            }
            return SelectorExpression(selector.selectorType, filters, owner)
        }
        fun frame(path: NBTPath): Int {
            val first = path.pathList.firstOrNull() as? MemberPath
            val member = (first?.value as? MCString)?.takeIf { StorageAccess.snapshot(it) != null }
            val name = member?.value?.value
            Regex("stack_frame\\[(\\d+)]").matchEntire(name.orEmpty())?.let { return it.groupValues[1].toInt() }
            return if (name == "stack_frame") ((path.pathList.getOrNull(1) as? IntPath)?.value as? MCInt)
                ?.takeIf { StorageAccess.snapshot(it) != null }?.value ?: 0 else 0
        }
        fun snapshot(selector: EntitySelector, type: TypeId): CompilerValue? {
            val filters = selector.predicates.map { filter ->
                if (filter is ScoresPredicate) return null
                val snapshot = top.mcfpp.analysis.StorageAccess.snapshot(filter.v) ?: return null
                Filter(filter.identifier, (filter as? CanReverseEntitySelectorPredicate)?.reverse ?: false,
                    Operand(ValueRef.Constant(filter.v.type.typeId, snapshot)))
            }
            return SelectorExpression(selector.selectorType, filters).snapshot(type)
        }
        fun restore(snapshot: CompilerValue.Typed): SelectorExpression? {
            if (snapshot.type != MCFPPEntityType.NormalSelector.typeId && snapshot.type !is TypeId.Selector) return null
            val payload = snapshot.payload
            if (payload is CompilerValue.Text) {
                val kind = EntitySelector.Companion.SelectorType.entries.firstOrNull { it.name == payload.value } ?: return null
                return SelectorExpression(kind, emptyList())
            }
            val record = payload as? CompilerValue.Record ?: return null
            (record.fields["targetKind"] as? CompilerValue.Text)?.let { targetKind ->
                val kind = DirectKind.entries.firstOrNull { it.name == targetKind.value } ?: return null
                val operand = record.fields["operand"] as? CompilerValue.Typed ?: return null
                if (operand.type != MCFPPBaseType.String.typeId || operand.payload !is CompilerValue.Text) return null
                if (record.fields.keys != setOf("targetKind", "operand")) return null
                return SelectorExpression(null, emptyList(), 0,
                    DirectTarget(kind, Operand(ValueRef.Constant(operand.type, operand))))
            }
            val kindName = (record.fields["kind"] as? CompilerValue.Text)?.value ?: return null
            val kind = EntitySelector.Companion.SelectorType.entries.firstOrNull { it.name == kindName } ?: return null
            val filters = (record.fields["predicates"] as? CompilerValue.Sequence)?.elements?.map {
                val filter = it as? CompilerValue.Record ?: return null
                val name = (filter.fields["name"] as? CompilerValue.Text)?.value ?: return null
                val reverse = (filter.fields["reverse"] as? CompilerValue.Bool)?.value ?: return null
                val operand = filter.fields["operand"] as? CompilerValue.Typed ?: return null
                Filter(name, reverse, Operand(ValueRef.Constant(operand.type, operand)))
            } ?: return null
            return SelectorExpression(kind, filters)
        }
        private fun predicate(name: String, reverse: Boolean, value: Var<*>): EntitySelectorPredicate? = when (name) {
            "x" -> XPredicate(value as MCInt)
            "y" -> YPredicate(value as MCInt)
            "z" -> ZPredicate(value as MCInt)
            "dx" -> DXPredicate(value as MCInt)
            "dy" -> DYPredicate(value as MCInt)
            "dz" -> DZPredicate(value as MCInt)
            "distance" -> DistancePredicate(value as RangeVar)
            "x_rotation" -> XRotationPredicate(value as RangeVar)
            "y_rotation" -> YRotationPredicate(value as RangeVar)
            "level" -> LevelPredicate(value as RangeVar)
            "tag" -> TagPredicate(value as MCString, reverse)
            "team" -> TeamPredicate(value as MCString, reverse)
            "name" -> NamePredicate(value as MCString, reverse)
            "gamemode" -> GamemodePredicate(value as MCString, reverse)
            "sort" -> SortPredicate(value as MCString)
            "limit" -> LimitPredicate(value as MCInt)
            "type" -> TypePredicate(value as DataTemplateObject, reverse)
            "predicate" -> PredicatePredicate(value as DataTemplateObject, reverse)
            "advancements" -> AdvancementsPredicate(value as DataTemplateObject, reverse)
            "nbt" -> NBTPredicate(value as NBTBasedData)
            else -> null
        }
    }
}

/** Runtime range text preserves missing endpoints without inventing numeric defaults. */
private class SelectorRangeOperand(private val captured: NBTPath) : RangeVar() {
    override fun toCommandPart(): Command {
        fun endpoint(name: String): NBTBasedData {
            val path = NBTPath.stack.intIndex(0).memberIndex(TempPool.getVarIdentify())
            Function.addCommand(Commands.dataSetValue(path, StringTag("")))
            Function.addCommand(Command("execute if data").build(captured.memberIndex(name).toCommandPart())
                .build("run").build(Commands.dataSetFrom(path, captured.memberIndex(name))))
            return NBTBasedData().apply {
                nbtPath = path
                isTemp = true
                StorageAccess.publishNbt(this)
            }
        }
        return Command("").buildMacro(endpoint("left"), false).build("..", false)
            .buildMacro(endpoint("right"), false)
    }
}

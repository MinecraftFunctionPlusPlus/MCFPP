package top.mcfpp.core.lang.ref

import top.mcfpp.analysis.CompilerValue
import top.mcfpp.core.lang.context.ContextBorrowPayload
import top.mcfpp.core.lang.Var
import top.mcfpp.lib.EntityFacts
import top.mcfpp.lib.EntitySelector
import top.mcfpp.type.TypeId
import java.util.Collections

enum class ReferenceKind(val key: String) {
    SCORE_HOLDER("score_holder"), SCORE_ENTITY("score_entity"),
    NBT_STORAGE("nbt_storage"), NBT_ENTITY("nbt_entity"), NBT_BLOCK("nbt_block")
}

/** The immutable command shape contains types, never operand locations in another frame. */
sealed interface ReferenceTargetShape {
    data class Filter(val name: String, val reverse: Boolean, val type: TypeId)
    class Selector(val kind: EntitySelector.Companion.SelectorType, filters: List<Filter>) : ReferenceTargetShape {
        val filters: List<Filter> = Collections.unmodifiableList(ArrayList(filters))
        override fun equals(other: Any?) = other is Selector && kind == other.kind && filters == other.filters
        override fun hashCode() = 31 * kind.hashCode() + filters.hashCode()
    }
    data object PlayerName : ReferenceTargetShape
    data object UUIDText : ReferenceTargetShape
    data object BoundExecutor : ReferenceTargetShape
}

sealed interface ReferencePathPart {
    data class Field(val name: String) : ReferencePathPart
    data class Index(val value: Var<*>) : ReferencePathPart
}

sealed interface ReferencePathShape {
    data class Field(val name: String) : ReferencePathShape
    data object Index : ReferencePathShape
}

/** Known address fragments are optimization evidence alongside an owned runtime payload. */
data class ReferenceDescriptor(
    val kind: ReferenceKind,
    val fields: CompilerValue.Record,
    val targetShape: ReferenceTargetShape? = null,
    val pathShape: List<ReferencePathShape> = emptyList()
)

/** Evidence follows a reference through erasure, runtime copies and flow merges. */
class ReferenceMetadata(
    descriptors: Set<ReferenceDescriptor> = emptySet(),
    borrows: Set<ContextBorrowPayload> = emptySet(),
    val targetFacts: EntityFacts = EntityFacts.UNKNOWN
) {
    val descriptors: Set<ReferenceDescriptor> = Collections.unmodifiableSet(LinkedHashSet(descriptors))
    val borrows: Set<ContextBorrowPayload> = Collections.unmodifiableSet(LinkedHashSet(borrows))
    val staticDescriptor: ReferenceDescriptor? get() = descriptors.singleOrNull()
    fun join(other: ReferenceMetadata) = ReferenceMetadata(
        if (descriptors.isEmpty() || other.descriptors.isEmpty()) emptySet() else descriptors + other.descriptors,
        borrows + other.borrows,
        EntityFacts(if (targetFacts.upperBound != null && other.targetFacts.upperBound != null)
            maxOf(targetFacts.upperBound, other.targetFacts.upperBound) else null,
            targetFacts.playersOnly && other.targetFacts.playersOnly,
            if (targetFacts.knownKinds != null && other.targetFacts.knownKinds != null)
                targetFacts.knownKinds + other.targetFacts.knownKinds else null))
    /** A write barrier may replace an address; borrowed lifetime evidence must remain conservative. */
    fun invalidated() = ReferenceMetadata(borrows = borrows)
    override fun equals(other: Any?) = other is ReferenceMetadata && descriptors == other.descriptors &&
        borrows == other.borrows && targetFacts == other.targetFacts
    override fun hashCode() = listOf(descriptors, borrows, targetFacts).hashCode()
}

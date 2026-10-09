package top.mcfpp.lib

import top.mcfpp.core.lang.MCInt
import top.mcfpp.core.lang.nbt.MCString
import top.mcfpp.core.lang.nbt.NBTBasedData
import top.mcfpp.nbt.tags.Tag
import java.util.Collections

/** A snapshot of an address expression in the current execution context. */
class NBTAddressKey private constructor(val source: Any, val segments: List<Any>) {
    override fun equals(other: Any?) = other is NBTAddressKey && source == other.source && segments == other.segments
    override fun hashCode() = 31 * source.hashCode() + segments.hashCode()

    companion object {
        fun of(path: NBTPath) = NBTAddressKey(sourceKey(path.source), Collections.unmodifiableList(path.pathList.map(::segmentKey)))
    }
}

internal class AddressIdentity(private val value: Any) {
    override fun equals(other: Any?) = other is AddressIdentity && value === other.value
    override fun hashCode() = System.identityHashCode(value)
}

private data class SourceKey(val kind: String, val value: Any)
private data class DynamicSelector(val text: String, val identity: AddressIdentity)
private data class Coordinate(val prefix: String, val number: Number)
private data class SegmentKey(val kind: String, val value: Any)

internal fun sourceKey(source: NBTSource): Any = when (source) {
    is StorageSource -> SourceKey("storage", source.storage)
    is EntitySource -> {
        val command = source.entity.value.toCommandPart()
        SourceKey("entity", if (command.isMacro) DynamicSelector(command.toString(), AddressIdentity(source.entity.value)) else command.toString())
    }
    is BlockSource -> SourceKey("block", listOf(source.pos.x, source.pos.y, source.pos.z).map { Coordinate(it.prefix, it.number) })
    else -> AddressIdentity(source)
}

internal fun segmentKey(segment: Path): Any = when (segment) {
    is MemberPath -> SegmentKey("member", (segment.value as? MCString)?.takeIf {
        top.mcfpp.analysis.StorageAccess.snapshot(it) != null
    }?.value?.value ?: AddressIdentity(segment.value))
    is IntPath -> SegmentKey("index", (segment.value as? MCInt)?.takeIf {
        top.mcfpp.analysis.StorageAccess.snapshot(it) != null
    }?.value ?: AddressIdentity(segment.value))
    is NBTPredicatePath -> SegmentKey("predicate", (segment.value as? NBTBasedData)?.takeIf { top.mcfpp.analysis.StorageAccess.snapshot(it) != null }?.let { Tag.toSNBT(it.value) } ?: AddressIdentity(segment.value))
    is IteratorPath -> SegmentKey("iterator", Unit)
    else -> AddressIdentity(segment)
}

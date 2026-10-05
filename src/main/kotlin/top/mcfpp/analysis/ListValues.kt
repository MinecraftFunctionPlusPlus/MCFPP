package top.mcfpp.analysis

import top.mcfpp.nbt.tags.CompoundTag
import top.mcfpp.nbt.tags.Tag
import top.mcfpp.nbt.tags.collection.ListTag
import top.mcfpp.nbt.tags.primitive.DoubleTag
import top.mcfpp.nbt.tags.primitive.FloatTag
import top.mcfpp.type.MCFPPBaseType
import top.mcfpp.type.MCFPPNBTType

/** Language value equality; erased wrappers do not hide the actual element identity. */
object ListValues {
    private fun unbox(value: CompilerValue): CompilerValue = if (value is CompilerValue.Typed &&
        value.type in setOf(MCFPPBaseType.Any.typeId, MCFPPBaseType.Object.typeId)) unbox(value.payload) else value

    private fun equal(left: CompilerValue, right: CompilerValue): Boolean {
        val a = unbox(left)
        val b = unbox(right)
        if (a is CompilerValue.Typed && b is CompilerValue.Typed) return a.type == b.type && equal(a.payload, b.payload)
        if (a is CompilerValue.Sequence && b is CompilerValue.Sequence) return a.elements.size == b.elements.size &&
            a.elements.zip(b.elements).all { (x, y) -> equal(x, y) }
        if (a is CompilerValue.Record && b is CompilerValue.Record) return a.fields.keys == b.fields.keys &&
            a.fields.all { (key, value) -> equal(value, b.fields.getValue(key)) }
        if (a is CompilerValue.Nbt && b is CompilerValue.Nbt) return Tag.toNBT(a.snbt) == Tag.toNBT(b.snbt)
        return a == b
    }

    private fun foldableTag(tag: Tag<*>): Boolean = when (tag) {
        is FloatTag, is DoubleTag -> false
        is ListTag -> tag.all(::foldableTag)
        is CompoundTag -> tag.value.values.all(::foldableTag)
        else -> true
    }
    private fun foldable(value: CompilerValue): Boolean = when (value) {
        is CompilerValue.FloatBits, is CompilerValue.DoubleBits -> false
        is CompilerValue.Typed -> value.type !in setOf(MCFPPBaseType.Float.typeId, MCFPPNBTType.Double.typeId) && foldable(value.payload)
        is CompilerValue.Sequence -> value.elements.all(::foldable)
        is CompilerValue.Record -> value.fields.values.all(::foldable)
        is CompilerValue.Nbt -> foldableTag(Tag.toNBT(value.snbt))
        else -> true
    }

    fun indexOf(elements: List<CompilerValue>, needle: CompilerValue, last: Boolean = false): Int? {
        if (!elements.all(::foldable) || !foldable(needle)) return null
        return if (last) elements.indexOfLast { equal(it, needle) } else elements.indexOfFirst { equal(it, needle) }
    }
}

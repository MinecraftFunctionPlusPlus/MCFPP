package top.mcfpp.backend

import top.mcfpp.nbt.tags.CompoundTag
import top.mcfpp.nbt.tags.Tag
import top.mcfpp.nbt.tags.collection.ListTag
import top.mcfpp.nbt.tags.primitive.StringTag

/** Command encoding uses the visible payload, without the library's binary-list wrappers. */
object NbtEncoding {
    fun snbt(tag: Tag<*>): String = when (tag) {
        is CompoundTag -> tag.value.toSortedMap().entries.joinToString(",", "{", "}") { (key, value) ->
            "${Tag.toSNBT(StringTag(key))}:${snbt(value)}"
        }
        is ListTag -> tag.joinToString(",", "[", "]", transform = ::snbt)
        else -> Tag.toSNBT(tag)
    }
}

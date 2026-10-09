package top.mcfpp.backend

import top.mcfpp.nbt.tags.CompoundTag
import top.mcfpp.nbt.tags.Tag
import top.mcfpp.nbt.tags.collection.ListTag
import top.mcfpp.nbt.tags.primitive.StringTag

/** Command encoding uses the visible payload, without the library's binary-list wrappers. */
object NbtEncoding {
    /** The library parser normalizes signed floating zero; preserve the original numeric tokens. */
    fun parse(snbt: String): Tag<*> {
        val text = snbt.trim()
        val floating = Regex("[-+]?(?:[0-9]+(?:\\.[0-9]*)?|\\.[0-9]+)(?:[eE][-+]?[0-9]+)?[fFdD]")
        if (floating.matches(text)) return if (text.last().lowercaseChar() == 'f')
            top.mcfpp.nbt.tags.primitive.FloatTag(text.dropLast(1).toFloat())
        else top.mcfpp.nbt.tags.primitive.DoubleTag(text.dropLast(1).toDouble())
        val parsed = Tag.toNBT(text)
        if (parsed is CompoundTag) {
            for (entry in separated(text.substring(1, text.length - 1), ',')) {
                val pair = separated(entry, ':')
                if (pair.size < 2) continue
                val rawKey = pair.first().trim()
                val key = if (rawKey.startsWith('"') || rawKey.startsWith('\''))
                    (Tag.toNBT(rawKey) as StringTag).value else rawKey
                parsed.put(key, parse(pair.drop(1).joinToString(":")))
            }
        } else if (parsed is ListTag) {
            return ListTag().apply {
                separated(text.substring(1, text.length - 1), ',').filter { it.isNotBlank() }.forEach { add(parse(it)) }
            }
        }
        return parsed
    }

    private fun separated(text: String, separator: Char): List<String> {
        val parts = ArrayList<String>()
        var start = 0
        var depth = 0
        var quote: Char? = null
        var escaped = false
        text.forEachIndexed { index, char ->
            if (quote != null) {
                if (escaped) escaped = false else if (char == '\\') escaped = true else if (char == quote) quote = null
            } else when (char) {
                '"', '\'' -> quote = char
                '{', '[' -> depth++
                '}', ']' -> depth--
                separator -> if (depth == 0) { parts += text.substring(start, index); start = index + 1 }
            }
        }
        parts += text.substring(start)
        return parts
    }

    fun snbt(tag: Tag<*>): String = when (tag) {
        is CompoundTag -> tag.value.toSortedMap().entries.joinToString(",", "{", "}") { (key, value) ->
            "${Tag.toSNBT(StringTag(key))}:${snbt(value)}"
        }
        is ListTag -> tag.joinToString(",", "[", "]", transform = ::snbt)
        else -> Tag.toSNBT(tag)
    }
}

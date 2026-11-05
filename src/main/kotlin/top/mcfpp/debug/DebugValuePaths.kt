package top.mcfpp.debug

import com.alibaba.fastjson2.JSON

/** A read-only path grammar, shared by Watches and physical NBT location decoding. */
internal object DebugValuePaths {
    fun read(root: Any?, path: String): Any? {
        require(path.isNotBlank()) { "Enter a variable, field or constant index path" }
        var value = root
        var index = 0
        var first = true
        while (index < path.length) {
            if (path[index] == '[') {
                val end = path.indexOf(']', index)
                require(end > index) { "Unclosed index" }
                val number = path.substring(index + 1, end).toIntOrNull() ?: error("Only constant integer indices are supported")
                val list = value as? List<*> ?: error("Value is not a list")
                val position = if (number < 0) list.size + number else number
                require(position in list.indices) { "Index $number is outside the list" }
                value = list[position]; index = end + 1
            } else {
                if (!first) { require(path[index] == '.') { "Only variable, field and index paths are supported" }; index++ }
                require(index < path.length) { "Missing field name" }
                val name: String
                if (path[index] == '"') {
                    val start = index++
                    var escaped = false
                    while (index < path.length) {
                        val c = path[index++]
                        if (c == '"' && !escaped) break
                        escaped = c == '\\' && !escaped
                    }
                    name = JSON.parseObject(path.substring(start, index), String::class.java)
                } else {
                    val start = index
                    while (index < path.length && (path[index].isLetterOrDigit() || path[index] in "_$:-")) index++
                    require(index > start) { "Invalid field path" }
                    name = path.substring(start, index)
                }
                val map = value as? Map<*, *> ?: error("Value has no readable fields")
                require(map.containsKey(name)) { "Unknown or uninitialized field '$name'" }
                value = map[name]
            }
            first = false
        }
        return value
    }
}

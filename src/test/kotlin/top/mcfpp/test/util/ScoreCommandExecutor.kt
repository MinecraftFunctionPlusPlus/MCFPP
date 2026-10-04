package top.mcfpp.test.util

import top.mcfpp.core.lang.MCInt
import top.mcfpp.core.lang.bool.ScoreBool
import top.mcfpp.nbt.tags.CompoundTag
import top.mcfpp.nbt.tags.Tag
import top.mcfpp.nbt.tags.primitive.ByteTag
import top.mcfpp.nbt.tags.primitive.IntTag
import top.mcfpp.nbt.tags.primitive.ShortTag
import top.mcfpp.nbt.tags.primitive.StringTag
import top.mcfpp.nbt.tags.collection.ListTag

/** Strict executor for the scoreboard and control-flow command subset covered by these tests. */
class ScoreCommandExecutor(commands: List<String>, functions: Map<String, List<String>> = emptyMap()) {
    val values = mutableMapOf<String, Int>()
    val messages = mutableListOf<String>()
    val branchGuards = mutableListOf<Int>()
    var stackDepth = 0
        private set
    private val frames = mutableListOf<MutableMap<String, Tag<*>>>()
    private val storage = mutableMapOf<String, MutableMap<String, Tag<*>>>()
    private data class Segment(val name: String?, val index: Int?, val end: Int, val predicate: CompoundTag? = null)
    private fun segments(path: String): List<Segment> {
        val result = mutableListOf<Segment>()
        var cursor = 0
        while (cursor < path.length) {
            when (path[cursor]) {
                '.' -> cursor++
                '[' -> {
                    var end = cursor + 1
                    var quote: Char? = null
                    var escaped = false
                    var depth = 0
                    while (end < path.length) {
                        val char = path[end]
                        if (quote != null) {
                            if (char == quote && !escaped) quote = null
                            escaped = char == '\\' && !escaped
                        } else when (char) {
                            '"', '\'' -> { quote = char; escaped = false }
                            '[' -> depth++
                            ']' -> if (depth == 0) break else depth--
                        }
                        end++
                    }
                    check(end > cursor) { "Invalid NBT path $path" }
                    val selector = path.substring(cursor + 1, end)
                    result += if (selector.startsWith("{")) Segment(null, null, end + 1, Tag.toNBT(selector) as CompoundTag)
                        else Segment(null, selector.toInt(), end + 1)
                    cursor = end + 1
                }
                '"', '\'' -> {
                    val quote = path[cursor]
                    val start = cursor++
                    var escaped = false
                    while (cursor < path.length) {
                        val char = path[cursor++]
                        if (char == quote && !escaped) break
                        escaped = char == '\\' && !escaped
                    }
                    result += Segment((Tag.toNBT(path.substring(start, cursor)) as StringTag).value, null, cursor)
                }
                else -> {
                    val start = cursor
                    while (cursor < path.length && path[cursor] != '.' && path[cursor] != '[') cursor++
                    result += Segment(path.substring(start, cursor), null, cursor)
                }
            }
        }
        return result
    }
    private fun matches(value: Tag<*>, predicate: CompoundTag) = value is CompoundTag &&
        predicate.value.all { (key, part) -> value[key] == part }
    private fun element(value: Tag<*>, segment: Segment): Tag<*> = when {
        segment.name != null -> (value as CompoundTag)[segment.name] ?: error("Missing NBT member ${segment.name}")
        segment.predicate != null -> (value as ListTag).filter { matches(it, segment.predicate) }.singleOrNull()
            ?: error("NBT predicate must select one entry")
        else -> (value as ListTag).let { it[if (segment.index!! < 0) it.size + segment.index else segment.index] }
    }
    private fun address(source: String, path: String): Pair<MutableMap<String, Tag<*>>, String> {
        val frame = Regex("stack_frame\\[(\\d+)]\\.(.*)").matchEntire(path)
        return if (source == "mcfpp:system" && frame != null) frames[frame.groupValues[1].toInt()] to frame.groupValues[2]
            else storage.getOrPut(source) { mutableMapOf() } to path
    }
    fun readNbt(source: String, path: String): Tag<*> {
        Regex("stack_frame\\[(\\d+)]").matchEntire(path)?.let { frame ->
            if (source == "mcfpp:system") return CompoundTag().apply {
                frames[frame.groupValues[1].toInt()].forEach { (key, value) -> put(key, value) }
            }
        }
        val (root, key) = address(source, path)
        root[key]?.let { return it }
        val parts = segments(key)
        for (size in parts.size - 1 downTo 1) {
            var value = root[key.substring(0, parts[size - 1].end)] ?: continue
            for (part in parts.drop(size)) value = element(value, part)
            return value
        }
        error("Missing NBT $source $path")
    }
    private fun writeNbt(source: String, path: String, value: Tag<*>) {
        val (root, key) = address(source, path)
        val parts = segments(key)
        for (size in parts.size - 1 downTo 1) {
            var parent = root[key.substring(0, parts[size - 1].end)] ?: continue
            for (part in parts.drop(size).dropLast(1)) parent = element(parent, part)
            val last = parts.last()
            if (last.name != null) (parent as CompoundTag).put(last.name, value)
            else if (last.predicate != null) (parent as ListTag).value.indices.filter { matches(parent[it], last.predicate) }.forEach { parent[it] = value.copy() }
            else (parent as ListTag).let { it[if (last.index!! < 0) it.size + last.index else last.index] = value }
            return
        }
        check(parts.none { it.index != null || it.predicate != null }) { "Cannot write an index without an existing list: $path" }
        root.keys.removeAll { it.startsWith("$key.") || it.startsWith("$key[") }
        root[key] = value
    }
    private fun removeNbt(source: String, path: String) {
        val (root, key) = address(source, path)
        if (root.remove(key) != null) return
        val parts = segments(key)
        for (size in parts.size - 1 downTo 1) {
            var parent = root[key.substring(0, parts[size - 1].end)] ?: continue
            for (part in parts.drop(size).dropLast(1)) parent = element(parent, part)
            val last = parts.last()
            if (last.name != null) (parent as CompoundTag).value.remove(last.name)
            else if (last.predicate != null) (parent as ListTag).value.removeAll { matches(it, last.predicate) }
            else (parent as ListTag).value.removeAt(if (last.index!! < 0) parent.size + last.index else last.index)
            return
        }
    }
    private fun mergeNbt(destination: CompoundTag, source: CompoundTag) {
        for ((name, value) in source.value) {
            val old = destination[name]
            if (old is CompoundTag && value is CompoundTag) mergeNbt(old, value)
            else destination.put(name, value.copy())
        }
    }
    init {
        val nbtPath = """(?:"(?:\\.|[^"\\])*"|'(?:\\.|[^'\\])*'|[^\s"'])+"""
        val set = Regex("scoreboard players set (\\S+ \\S+) (-?\\d+)")
        val add = Regex("scoreboard players (add|remove) (\\S+ \\S+) (\\d+)")
        val operation = Regex("scoreboard players operation (\\S+ \\S+) (=|\\+=|-=|\\*=|%=) (\\S+ \\S+)")
        val compare = Regex("execute (if|unless) score (\\S+ \\S+) (=|<|>|<=|>=) (\\S+ \\S+) run (.*)")
        val matches = Regex("execute if score (\\S+ \\S+) matches (-?\\d+|(?:-?\\d+)?\\.\\.(?:-?\\d+)?) run (.*)")
        val guardStore = Regex("execute store result storage mcfpp:system ir_branch_stack\\[0].condition byte 1 run scoreboard players get (\\S+ \\S+)")
        val guardTest = Regex("execute (if|unless) data storage mcfpp:system ir_branch_stack\\[0]\\{condition:1b} run (.*)")
        val save = Regex("execute store result storage (\\S+) ($nbtPath) (int|byte|short) 1 run scoreboard players get (\\S+ \\S+)")
        val restore = Regex("execute store result score (\\S+ \\S+) run data get storage (\\S+) ($nbtPath)(?: 1(?:\\.0)?)?")
        val setNbt = Regex("data modify storage (\\S+) ($nbtPath) set value (.*)")
        val copyNbt = Regex("data modify storage (\\S+) ($nbtPath) set from storage (\\S+) ($nbtPath)")
        val mergeNbtValue = Regex("data modify storage (\\S+) ($nbtPath) merge value (.*)")
        val mergeNbtFrom = Regex("data modify storage (\\S+) ($nbtPath) merge from storage (\\S+) ($nbtPath)")
        val clearCompound = Regex("data modify storage mcfpp:system stack_frame\\[(\\d+)]\\.(\\S+) set value \\{\\}")
        val storeTest = Regex("execute store success score (\\S+ \\S+) (if|unless) score (\\S+ \\S+) = (\\S+ \\S+)")
        val storeMatch = Regex("execute store success score (\\S+ \\S+) (if|unless) score (\\S+ \\S+) matches (-?\\d+)")
        val insertNbt = Regex("data modify storage (\\S+) ($nbtPath) (append|prepend|insert -?\\d+) from storage (\\S+) ($nbtPath)")
        val compareNbt = Regex("execute store success score (\\S+ \\S+) run data modify storage (\\S+) ($nbtPath) set from storage (\\S+) ($nbtPath)")
        val removeNbt = Regex("data remove storage (\\S+) ($nbtPath)")
        val testNbt = Regex("execute store success score (\\S+ \\S+) if data storage (\\S+) ($nbtPath)")
        val macroCall = Regex("function (\\S+) with storage (\\S+) ($nbtPath)")
        var steps = 0
        var branchStackInitialized = false
        lateinit var execute: (String) -> Boolean
        fun run(body: List<String>) {
            for (command in body.filterNot { it.startsWith("#") }) if (execute(command)) break
        }
        execute = command@{ command ->
            check(++steps < 10000) { "Command execution did not terminate" }
            if (command == "data modify storage mcfpp:system stack_frame prepend value {}") { stackDepth++; frames.add(0, mutableMapOf()); return@command false }
            if (command == "data remove storage mcfpp:system stack_frame[0]") { stackDepth--; check(stackDepth >= 0); frames.removeAt(0); return@command false }
            if (command == "execute unless data storage mcfpp:system ir_branch_stack run data modify storage mcfpp:system ir_branch_stack set value []") {
                if (!branchStackInitialized) { branchGuards.clear(); branchStackInitialized = true }
                return@command false
            }
            if (command == "data modify storage mcfpp:system ir_branch_stack set value []") { branchGuards.clear(); branchStackInitialized = true; return@command false }
            if (command == "data modify storage mcfpp:system ir_branch_stack prepend value {condition:0b}") { branchGuards.add(0, 0); return@command false }
            if (command == "data remove storage mcfpp:system ir_branch_stack[0]") { branchGuards.removeAt(0); return@command false }
            guardStore.matchEntire(command)?.let { branchGuards[0] = values.getValue(it.groupValues[1]); return@command false }
            guardTest.matchEntire(command)?.let {
                if ((branchGuards[0] == 1) == (it.groupValues[1] == "if")) return@command execute(it.groupValues[2])
                return@command false
            }
            if (Regex("return -?\\d+").matches(command)) return@command true
            macroCall.matchEntire(command)?.let { call ->
                val arguments = readNbt(call.groupValues[2], call.groupValues[3]) as CompoundTag
                val replacements = arguments.value.mapValues { (_, value) ->
                    when (value) {
                        is StringTag -> value.value
                        is CompoundTag -> top.mcfpp.backend.NbtEncoding.snbt(value)
                        else -> value.value.toString()
                    }
                }
                val body = functions.getValue(call.groupValues[1]).map { line ->
                    if (!line.startsWith("$")) line else Regex("\\$\\(([^)]+)\\)").replace(line.removePrefix("$")) { parameter ->
                        replacements.getValue(parameter.groupValues[1])
                    }
                }
                run(body)
                return@command false
            }
            if (command.startsWith("return run function ")) {
                run(functions.getValue(command.removePrefix("return run function ")))
                return@command true
            }
            if (command.startsWith("function ")) {
                run(functions.getValue(command.removePrefix("function ")))
                return@command false
            }
            set.matchEntire(command)?.let { values[it.groupValues[1]] = it.groupValues[2].toInt(); return@command false }
            add.matchEntire(command)?.let {
                val target = it.groupValues[2]
                val delta = it.groupValues[3].toInt() * if (it.groupValues[1] == "add") 1 else -1
                values[target] = values.getValue(target) + delta
                return@command false
            }
            save.matchEntire(command)?.let {
                val value = values.getValue(it.groupValues[4])
                val tag = when (it.groupValues[3]) { "byte" -> ByteTag(value.toByte()); "short" -> ShortTag(value.toShort()); else -> IntTag(value) }
                writeNbt(it.groupValues[1], it.groupValues[2], tag)
                return@command false
            }
            clearCompound.matchEntire(command)?.let {
                val prefix = it.groupValues[2] + "."
                frames[it.groupValues[1].toInt()].keys.removeAll { key -> key.startsWith(prefix) }
                writeNbt("mcfpp:system", "stack_frame[${it.groupValues[1]}].${it.groupValues[2]}", CompoundTag())
                return@command false
            }
            restore.matchEntire(command)?.let {
                val value = readNbt(it.groupValues[2], it.groupValues[3])
                values[it.groupValues[1]] = if (value is ListTag) value.size else (value.value as Number).toInt()
                return@command false
            }
            setNbt.matchEntire(command)?.let {
                writeNbt(it.groupValues[1], it.groupValues[2], Tag.toNBT(it.groupValues[3])); return@command false
            }
            copyNbt.matchEntire(command)?.let {
                val value = readNbt(it.groupValues[3], it.groupValues[4])
                writeNbt(it.groupValues[1], it.groupValues[2], value.copy()); return@command false
            }
            mergeNbtValue.matchEntire(command)?.let {
                mergeNbt(readNbt(it.groupValues[1], it.groupValues[2]) as CompoundTag, Tag.toNBT(it.groupValues[3]) as CompoundTag)
                return@command false
            }
            mergeNbtFrom.matchEntire(command)?.let {
                val source = readNbt(it.groupValues[3], it.groupValues[4]).copy() as CompoundTag
                mergeNbt(readNbt(it.groupValues[1], it.groupValues[2]) as CompoundTag, source)
                return@command false
            }
            compareNbt.matchEntire(command)?.let {
                val value = readNbt(it.groupValues[4], it.groupValues[5])
                val changed = readNbt(it.groupValues[2], it.groupValues[3]) != value
                writeNbt(it.groupValues[2], it.groupValues[3], value.copy())
                values[it.groupValues[1]] = if (changed) 1 else 0
                return@command false
            }
            insertNbt.matchEntire(command)?.let {
                val path = it.groupValues[5]
                val value = readNbt(it.groupValues[4], path.removeSuffix("[]"))
                val elements = if (path.endsWith("[]")) (value as ListTag).value.map { tag -> tag.copy() } else listOf(value.copy())
                val list = readNbt(it.groupValues[1], it.groupValues[2]) as ListTag
                val position = when (it.groupValues[3]) {
                    "append" -> list.size
                    "prepend" -> 0
                    else -> it.groupValues[3].removePrefix("insert ").toInt().let { index -> if (index < 0) list.size + index + 1 else index }
                }
                if (position in 0..list.size) list.value.addAll(position, elements)
                return@command false
            }
            removeNbt.matchEntire(command)?.let {
                removeNbt(it.groupValues[1], it.groupValues[2])
                return@command false
            }
            testNbt.matchEntire(command)?.let {
                val exists = try { readNbt(it.groupValues[2], it.groupValues[3]); true }
                    catch (_: IllegalStateException) { false } catch (_: IndexOutOfBoundsException) { false }
                values[it.groupValues[1]] = if (exists) 1 else 0
                return@command false
            }
            storeTest.matchEntire(command)?.let {
                val condition = values.getValue(it.groupValues[3]) == values.getValue(it.groupValues[4])
                values[it.groupValues[1]] = if (condition == (it.groupValues[2] == "if")) 1 else 0
                return@command false
            }
            storeMatch.matchEntire(command)?.let {
                val condition = values.getValue(it.groupValues[3]) == it.groupValues[4].toInt()
                values[it.groupValues[1]] = if (condition == (it.groupValues[2] == "if")) 1 else 0
                return@command false
            }
            operation.matchEntire(command)?.let {
                val target = it.groupValues[1]
                val right = values.getValue(it.groupValues[3])
                values[target] = when (it.groupValues[2]) {
                    "=" -> right
                    "+=" -> values.getValue(target) + right
                    "-=" -> values.getValue(target) - right
                    "*=" -> values.getValue(target) * right
                    "%=" -> Math.floorMod(values.getValue(target), right)
                    else -> error(command)
                }
                return@command false
            }
            compare.matchEntire(command)?.let {
                val left = values.getValue(it.groupValues[2])
                val right = values.getValue(it.groupValues[4])
                val condition = when (it.groupValues[3]) {
                    "=" -> left == right
                    "<" -> left < right
                    ">" -> left > right
                    "<=" -> left <= right
                    ">=" -> left >= right
                    else -> error(command)
                }
                if (condition == (it.groupValues[1] == "if")) return@command execute(it.groupValues[5])
                return@command false
            }
            matches.matchEntire(command)?.let {
                val value = values.getValue(it.groupValues[1])
                val range = it.groupValues[2]
                val bounds = range.split("..")
                val match = if (bounds.size == 1) value == range.toInt() else
                    (bounds[0].isEmpty() || value >= bounds[0].toInt()) && (bounds[1].isEmpty() || value <= bounds[1].toInt())
                if (match) return@command execute(it.groupValues[3])
                return@command false
            }
            if (command.startsWith("say ")) { messages.add(command.removePrefix("say ")); return@command false }
            error("Unsupported command: $command")
        }
        run(commands)
    }
    fun read(value: MCInt) = values.getValue("${value.name} ${value.sbObject}")
    fun read(value: ScoreBool) = values.getValue("${value.name} ${value.boolObject}")
}

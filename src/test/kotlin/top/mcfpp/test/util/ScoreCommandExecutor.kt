package top.mcfpp.test.util

import com.alibaba.fastjson2.JSON
import com.alibaba.fastjson2.JSONObject
import com.alibaba.fastjson2.JSONReader
import top.mcfpp.core.lang.MCInt
import top.mcfpp.core.lang.bool.ScoreBool
import top.mcfpp.nbt.tags.CompoundTag
import top.mcfpp.nbt.tags.Tag
import top.mcfpp.nbt.tags.primitive.ByteTag
import top.mcfpp.nbt.tags.primitive.IntTag
import top.mcfpp.nbt.tags.primitive.ShortTag
import top.mcfpp.nbt.tags.primitive.StringTag
import top.mcfpp.nbt.tags.collection.ListTag
import top.mcfpp.nbt.tags.collection.ByteArrayTag
import top.mcfpp.nbt.tags.collection.IntArrayTag
import top.mcfpp.nbt.tags.collection.LongArrayTag
import top.mcfpp.nbt.tags.primitive.LongTag
import top.mcfpp.nbt.tags.primitive.FloatTag
import top.mcfpp.nbt.tags.primitive.DoubleTag

/** Strict executor for the scoreboard and control-flow command subset covered by these tests. */
class ScoreCommandExecutor(commands: List<String>, functions: Map<String, List<String>> = emptyMap(), targetVersion: String = "26.3",
                           initialScores: Map<String, Int> = emptyMap(), commandBudget: Int = 10000) {
    val values = initialScores.toMutableMap()
    val objectives = linkedSetOf<String>()
    val bootstrapMarkers = mutableListOf<CompoundTag>()
    val messages = mutableListOf<String>()
    val failedScoreOperations = mutableListOf<String>()
    val branchGuards = mutableListOf<Int>()
    var stackDepth = 0
        private set
    private val frames = mutableListOf<MutableMap<String, Tag<*>>>()
    private var completedRootFrame: Map<String, Tag<*>>? = null
    private val storage = mutableMapOf<String, MutableMap<String, Tag<*>>>()
    val failedComputations = mutableListOf<String>()
    private data class Segment(val name: String?, val index: Int?, val end: Int, val predicate: CompoundTag? = null)
    private fun literalTag(snbt: String): Tag<*> {
        // The library's SNBT parser normalizes negative zero; Minecraft parses these numeric tokens directly.
        val numeric = Regex("[+-]?(?:\\d+(?:\\.\\d*)?|\\.\\d+)(?:[eE][+-]?\\d+)?[fFdD]")
        if (numeric.matches(snbt)) return when (snbt.last().lowercaseChar()) {
            'f' -> FloatTag(snbt.dropLast(1).toFloat())
            else -> DoubleTag(snbt.dropLast(1).toDouble())
        }
        return Tag.toNBT(snbt)
    }
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
        else -> {
            val index = segment.index!!
            when (value) {
                is ListTag -> value[if (index < 0) value.size + index else index]
                is ByteArrayTag -> value.value.let { ByteTag(it[if (index < 0) it.size + index else index]) }
                is IntArrayTag -> value.value.let { IntTag(it[if (index < 0) it.size + index else index]) }
                is LongArrayTag -> value.value.let { LongTag(it[if (index < 0) it.size + index else index]) }
                else -> error("Cannot index NBT $value")
            }
        }
    }
    private fun writeElement(parent: Tag<*>, index: Int, value: Tag<*>) {
        when (parent) {
            is ListTag -> parent[if (index < 0) parent.size + index else index] = value
            is ByteArrayTag -> parent.value.let { it[if (index < 0) it.size + index else index] = (value as ByteTag).value }
            is IntArrayTag -> parent.value.let { it[if (index < 0) it.size + index else index] = (value as IntTag).value }
            is LongArrayTag -> parent.value.let { it[if (index < 0) it.size + index else index] = (value as LongTag).value }
            else -> error("Cannot write an NBT index in $parent")
        }
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
        return getNbt(root, key, "$source $path")
    }
    private fun getNbt(root: Map<String, Tag<*>>, key: String, description: String = key): Tag<*> {
        root[key]?.let { return it }
        val parts = segments(key)
        for (size in parts.size - 1 downTo 1) {
            var value = root[key.substring(0, parts[size - 1].end)] ?: continue
            for (part in parts.drop(size)) value = element(value, part)
            return value
        }
        error("Missing NBT $description")
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
            else writeElement(parent, last.index!!, value)
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
    private fun provider(source: Any): Float {
        if (source is Number) return source.toFloat()
        val value = source as JSONObject
        fun input(name: String) = provider(value[name]!!)
        return when (value.getString("type")) {
            "minecraft:storage" -> (readNbt(value.getString("storage"), value.getString("path")).value as Number).toFloat()
            "minecraft:score" -> values.getValue("${value.getJSONObject("target").getString("name")} ${value.getString("score")}").toFloat()
            "minecraft:from_int" -> input("input")
            "minecraft:from_float" -> input("input").toInt().toFloat()
            "minecraft:add" -> value.getJSONArray("inputs").map(::provider).fold(0f, Float::plus)
            "minecraft:mul" -> value.getJSONArray("inputs").map(::provider).fold(1f, Float::times)
            "minecraft:sub" -> input("left") - input("right")
            "minecraft:div" -> input("left") / input("right")
            "minecraft:mod" -> input("left") % input("right")
            "minecraft:negate" -> -input("input")
            else -> error("Unsupported float provider: $value")
        }
    }
    init {
        val nbtPath = """(?:"(?:\\.|[^"\\])*"|'(?:\\.|[^'\\])*'|[^\s"'])+"""
        val set = Regex("scoreboard players set (\\S+ \\S+) (-?\\d+)")
        val add = Regex("scoreboard players (add|remove) (\\S+ \\S+) (\\d+)")
        val operation = Regex("scoreboard players operation (\\S+ \\S+) (=|\\+=|-=|\\*=|/=|%=|><) (\\S+ \\S+)")
        val compare = Regex("execute (if|unless) score (\\S+ \\S+) (=|<|>|<=|>=) (\\S+ \\S+) (?:run (.*)|((?:if|unless) (?:score|function) .*))")
        val matches = Regex("execute (if|unless) score (\\S+ \\S+) matches (-?\\d+|(?:-?\\d+)?\\.\\.(?:-?\\d+)?) (?:run (.*)|((?:if|unless) (?:score|function) .*))")
        val functionCondition = Regex("execute (if|unless) function (\\S+) (?:run (.*)|((?:if|unless) (?:score|function) .*))")
        val asIdentity = Regex("execute as (\\S+) run (.*)")
        val guardStore = Regex("execute store result storage mcfpp:system ir_branch_stack\\[0].condition byte 1 run scoreboard players get (\\S+ \\S+)")
        val guardTest = Regex("execute (if|unless) data storage mcfpp:system ir_branch_stack\\[0]\\{condition:1b} run (.*)")
        val save = Regex("execute store result storage (\\S+) ($nbtPath) (int|byte|short|long|double) 1 run scoreboard players get (\\S+ \\S+)")
        val saveOperation = Regex("execute store result storage (\\S+) ($nbtPath) int 1 run (scoreboard players operation (\\S+ \\S+) = \\S+ \\S+)")
        val restore = Regex("execute store result score (\\S+ \\S+) run data get storage (\\S+) ($nbtPath)(?: 1(?:\\.0)?)?")
        val setNbt = Regex("data modify storage (\\S+) ($nbtPath) set value (.*)")
        val copyNbt = Regex("data modify storage (\\S+) ($nbtPath) set from storage (\\S+) ($nbtPath)")
        val sliceString = Regex("data modify storage (\\S+) ($nbtPath) set string storage (\\S+) ($nbtPath) (-?\\d+) (-?\\d+)")
        val computeFloat = Regex("data modify storage (\\S+) ($nbtPath) set compute default float (.*)")
        val computeInt = Regex("execute store result score (\\S+ \\S+) run compute default integer (.*)")
        val floatCheck = Regex("execute store success score (\\S+ \\S+) (if|unless) predicate (.*)")
        val mergeNbtValue = Regex("data modify storage (\\S+) ($nbtPath) merge value (.*)")
        val mergeNbtFrom = Regex("data modify storage (\\S+) ($nbtPath) merge from storage (\\S+) ($nbtPath)")
        val clearCompound = Regex("data modify storage mcfpp:system stack_frame\\[(\\d+)]\\.(\\S+) set value \\{\\}")
        val storedScoreConditions = Regex("execute store success score (\\S+ \\S+) ((?:if|unless) score .*)")
        val scoreCondition = Regex("(if|unless) score (\\S+ \\S+) (?:(=|<|>|<=|>=) (\\S+ \\S+)|matches (-?\\d+|(?:-?\\d+)?\\.\\.(?:-?\\d+)?))(?: ((?:if|unless) score .*))?")
        val storedFunctionResult = Regex("execute store result score (\\S+ \\S+) run function (\\S+)")
        val conditionalStoredFunctionResult = Regex("execute ((?:if|unless) score .*) store result score (\\S+ \\S+) run function (\\S+)")
        val insertNbt = Regex("data modify storage (\\S+) ($nbtPath) (append|prepend|insert -?\\d+) from storage (\\S+) ($nbtPath)")
        val appendNbtValue = Regex("data modify storage (\\S+) ($nbtPath) append value (.*)")
        val compareNbt = Regex("execute store success score (\\S+ \\S+) run data modify storage (\\S+) ($nbtPath) set from storage (\\S+) ($nbtPath)")
        val removeNbt = Regex("data remove storage (\\S+) ($nbtPath)")
        val testNbt = Regex("execute store success score (\\S+ \\S+) if data storage (\\S+) ($nbtPath)")
        val conditionalNbt = Regex("execute (if|unless) data storage (\\S+) ($nbtPath) run (.*)")
        val macroCall = Regex("function (\\S+) with storage (\\S+) ($nbtPath)")
        var steps = 0
        val recentCommands = ArrayDeque<String>()
        var branchStackInitialized = false
        var identity: String? = null
        fun scoreKey(key: String): String {
            val player = key.substringBefore(' ')
            if (!player.startsWith("@")) return key
            check(player == "@s") { "Unsupported score selector: $player" }
            return "${identity ?: error("@s needs an executor identity")} ${key.substringAfter(' ')}"
        }
        fun testScoreConditions(conditions: String): Boolean {
            var remaining = conditions
            while (remaining.isNotEmpty()) {
                val condition = scoreCondition.matchEntire(remaining) ?: error("Unsupported score conditions: $remaining")
                val left = values.getValue(scoreKey(condition.groupValues[2]))
                val match = if (condition.groupValues[3].isNotEmpty()) {
                    val right = values.getValue(scoreKey(condition.groupValues[4]))
                    when (condition.groupValues[3]) {
                        "=" -> left == right
                        "<" -> left < right
                        ">" -> left > right
                        "<=" -> left <= right
                        ">=" -> left >= right
                        else -> error(remaining)
                    }
                } else {
                    val range = condition.groupValues[5]
                    val bounds = range.split("..")
                    if (bounds.size == 1) left == range.toInt() else
                        (bounds[0].isEmpty() || left >= bounds[0].toInt()) && (bounds[1].isEmpty() || left <= bounds[1].toInt())
                }
                if (match != (condition.groupValues[1] == "if")) return false
                remaining = condition.groupValues[6]
            }
            return true
        }
        lateinit var execute: (String) -> Boolean
        var returnedValue = 0
        fun run(body: List<String>): Int {
            val outerValue = returnedValue
            returnedValue = 0
            try {
                for (command in body.map(String::trim).filterNot { it.isEmpty() || it.startsWith("#") }) if (execute(command)) break
                return returnedValue
            } finally {
                returnedValue = outerValue
            }
        }
        execute = command@{ command ->
            recentCommands.addLast(command)
            if (recentCommands.size > 24) recentCommands.removeFirst()
            check(++steps < commandBudget) {
                "Command execution did not terminate: target=$targetVersion steps=$steps frameDepth=$stackDepth\n" +
                    recentCommands.joinToString("\n")
            }
            Regex("scoreboard objectives add (\\S+) (\\S+)(?: (.+))?").matchEntire(command)?.let {
                check(objectives.add(it.groupValues[1])) { "Objective already exists: ${it.groupValues[1]}" }
                return@command false
            }
            if (command.startsWith("summon ")) {
                check(command.startsWith("summon item 0 0 0 ")) { "Unsupported summon: $command" }
                val marker = Tag.toNBT(command.removePrefix("summon item 0 0 0 ")) as? CompoundTag
                    ?: error("Invalid bootstrap marker")
                val tags = marker.value["Tags"] as? ListTag ?: error("Missing bootstrap tags")
                check(tags.value.size == 1 && (tags.value.single() as? StringTag)?.value == "mcfpp_ptr_marker")
                check((marker.value["UUID"] as? IntArrayTag)?.value?.size == 4)
                check((marker.value["Age"]?.value as? Number)?.toInt() == -32768)
                check((marker.value["NoGravity"] as? ByteTag)?.value?.toInt() == 1)
                check((marker.value["Invulnerable"] as? ByteTag)?.value?.toInt() == 1)
                check(((marker.value["Item"] as? CompoundTag)?.value?.get("id") as? StringTag)?.value == "stone")
                bootstrapMarkers.add(marker.copy() as CompoundTag)
                return@command false
            }
            asIdentity.matchEntire(command)?.let {
                val next = it.groupValues[1]
                check(!next.startsWith("@")) { "Only one explicit executor identity is supported" }
                val previous = identity
                identity = next
                try { return@command execute(it.groupValues[2]) } finally { identity = previous }
            }
            if (command == "data modify storage mcfpp:system stack_frame prepend value {}") { stackDepth++; frames.add(0, mutableMapOf()); return@command false }
            if (command == "data remove storage mcfpp:system stack_frame[0]") {
                stackDepth--; check(stackDepth >= 0)
                val removed = frames.removeAt(0)
                if (frames.isEmpty()) completedRootFrame = removed.mapValues { it.value.copy() }
                return@command false
            }
            if (command == "execute unless data storage mcfpp:system ir_branch_stack run data modify storage mcfpp:system ir_branch_stack set value []") {
                if (!branchStackInitialized) { branchGuards.clear(); branchStackInitialized = true }
                return@command false
            }
            if (command == "data modify storage mcfpp:system ir_branch_stack set value []") { branchGuards.clear(); branchStackInitialized = true; return@command false }
            if (command == "data modify storage mcfpp:system ir_branch_stack prepend value {condition:0b}") { branchGuards.add(0, 0); return@command false }
            if (command == "data remove storage mcfpp:system ir_branch_stack[0]") { branchGuards.removeAt(0); return@command false }
            guardStore.matchEntire(command)?.let { branchGuards[0] = values.getValue(scoreKey(it.groupValues[1])); return@command false }
            guardTest.matchEntire(command)?.let {
                if ((branchGuards[0] == 1) == (it.groupValues[1] == "if")) return@command execute(it.groupValues[2])
                return@command false
            }
            if (Regex("return -?\\d+").matches(command)) {
                returnedValue = command.removePrefix("return ").toInt()
                return@command true
            }
            macroCall.matchEntire(command)?.let { call ->
                val arguments = readNbt(call.groupValues[2], call.groupValues[3]) as CompoundTag
                val replacements = arguments.value.mapValues { (_, value) ->
                    when (value) {
                        is StringTag -> value.value
                        is CompoundTag -> top.mcfpp.backend.NbtEncoding.snbt(value)
                        is FloatTag, is DoubleTag -> java.text.DecimalFormat("#", java.text.DecimalFormatSymbols(java.util.Locale.ROOT))
                            .apply { maximumFractionDigits = 15 }.format(value.value as Number)
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
                returnedValue = run(functions.getValue(command.removePrefix("return run function ")))
                return@command true
            }
            if (command.startsWith("function ")) {
                run(functions.getValue(command.removePrefix("function ")))
                return@command false
            }
            set.matchEntire(command)?.let { values[scoreKey(it.groupValues[1])] = it.groupValues[2].toInt(); return@command false }
            saveOperation.matchEntire(command)?.let {
                execute(it.groupValues[3])
                writeNbt(it.groupValues[1], it.groupValues[2], IntTag(values.getValue(scoreKey(it.groupValues[4]))))
                return@command false
            }
            add.matchEntire(command)?.let {
                val target = scoreKey(it.groupValues[2])
                val delta = it.groupValues[3].toInt() * if (it.groupValues[1] == "add") 1 else -1
                values[target] = values.getValue(target) + delta
                return@command false
            }
            save.matchEntire(command)?.let {
                val value = values.getValue(scoreKey(it.groupValues[4]))
                val tag = when (it.groupValues[3]) {
                    "byte" -> ByteTag(value.toByte())
                    "short" -> ShortTag(value.toShort())
                    "long" -> LongTag(value.toLong())
                    "double" -> DoubleTag(value.toDouble())
                    else -> IntTag(value)
                }
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
                values[scoreKey(it.groupValues[1])] = when (value) {
                    is ListTag -> value.size
                    is ByteArrayTag -> value.value.size
                    is IntArrayTag -> value.value.size
                    is LongArrayTag -> value.value.size
                    is StringTag -> value.value.length
                    else -> {
                        val number=(value.value as Number).toDouble()
                        if(targetVersion in listOf("1.20.1","1.20.2")) {
                            val truncated=number.toInt()
                            if(number<truncated.toDouble()) truncated-1 else truncated
                        } else kotlin.math.floor(number).toInt()
                    }
                }
                return@command false
            }
            setNbt.matchEntire(command)?.let {
                writeNbt(it.groupValues[1], it.groupValues[2], literalTag(it.groupValues[3])); return@command false
            }
            sliceString.matchEntire(command)?.let {
                val value = (readNbt(it.groupValues[3], it.groupValues[4]) as StringTag).value
                fun position(raw: String): Int = raw.toInt().let { if (it < 0) value.length + it else it }
                val start = position(it.groupValues[5])
                val end = position(it.groupValues[6])
                check(start in 0..value.length && end in start..value.length)
                writeNbt(it.groupValues[1], it.groupValues[2], StringTag(value.substring(start, end)))
                return@command false
            }
            computeFloat.matchEntire(command)?.let {
                val expression = JSON.parse(it.groupValues[3], JSONReader.Feature.AllowUnQuotedFieldNames)
                val result = provider(expression)
                writeNbt(it.groupValues[1], it.groupValues[2], FloatTag(if (result.isFinite()) result else 0f))
                return@command false
            }
            computeInt.matchEntire(command)?.let {
                val expression = JSON.parseObject(it.groupValues[2], JSONReader.Feature.AllowUnQuotedFieldNames)
                check(expression.getString("type") == "minecraft:from_float")
                // Preserve the int result: converting it back through Float would round Int.MAX_VALUE.
                val number=provider(expression["input"]!!)
                val failed=!number.isFinite() || number.toDouble()<Int.MIN_VALUE.toDouble() || number.toDouble()>=2147483648.0
                if(failed) failedComputations.add(command)
                values[scoreKey(it.groupValues[1])] = if(failed) 0 else number.toInt()
                return@command false
            }
            floatCheck.matchEntire(command)?.let {
                val predicate = JSON.parseObject(it.groupValues[3], JSONReader.Feature.AllowUnQuotedFieldNames)
                check(predicate.getString("type") == "minecraft:float_value_check")
                val actual = provider(predicate["value"]!!)
                val test = predicate["test"]!!
                val matched = if (test is JSONObject && !test.containsKey("type"))
                    (!test.containsKey("min") || actual >= provider(test["min"]!!)) &&
                    (!test.containsKey("max") || actual <= provider(test["max"]!!)) else actual == provider(test)
                values[scoreKey(it.groupValues[1])] = if (matched == (it.groupValues[2] == "if")) 1 else 0
                return@command false
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
                values[scoreKey(it.groupValues[1])] = if (changed) 1 else 0
                return@command false
            }
            appendNbtValue.matchEntire(command)?.let {
                val list = readNbt(it.groupValues[1], it.groupValues[2]) as ListTag
                list.value.add(Tag.toNBT(it.groupValues[3]))
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
            conditionalNbt.matchEntire(command)?.let {
                val path = it.groupValues[3]
                val predicateAt = path.indexOf('{')
                val actualPath = if (predicateAt < 0) path else path.substring(0, predicateAt)
                val present = try {
                    val value = readNbt(it.groupValues[2], actualPath)
                    predicateAt < 0 || matches(value, Tag.toNBT(path.substring(predicateAt)) as CompoundTag)
                } catch (_: IllegalStateException) { false } catch (_: IndexOutOfBoundsException) { false }
                if (present == (it.groupValues[1] == "if")) return@command execute(it.groupValues[4])
                return@command false
            }
            testNbt.matchEntire(command)?.let {
                val exists = try { readNbt(it.groupValues[2], it.groupValues[3]); true }
                    catch (_: IllegalStateException) { false } catch (_: IndexOutOfBoundsException) { false }
                values[scoreKey(it.groupValues[1])] = if (exists) 1 else 0
                return@command false
            }
            storedScoreConditions.matchEntire(command)?.let {
                values[scoreKey(it.groupValues[1])] = if (testScoreConditions(it.groupValues[2])) 1 else 0
                return@command false
            }
            storedFunctionResult.matchEntire(command)?.let {
                values[scoreKey(it.groupValues[1])] = run(functions.getValue(it.groupValues[2]))
                return@command false
            }
            conditionalStoredFunctionResult.matchEntire(command)?.let {
                if (testScoreConditions(it.groupValues[1])) {
                    values[scoreKey(it.groupValues[2])] = run(functions.getValue(it.groupValues[3]))
                }
                return@command false
            }
            operation.matchEntire(command)?.let {
                val target = scoreKey(it.groupValues[1])
                val right = values.getValue(scoreKey(it.groupValues[3]))
                if (it.groupValues[2] == "><") {
                    val source = scoreKey(it.groupValues[3])
                    val left = values.getValue(target)
                    values[target] = right
                    values[source] = left
                    return@command false
                }
                if (it.groupValues[2] in setOf("/=", "%=") && right == 0) {
                    failedScoreOperations.add(command)
                    return@command false
                }
                values[target] = when (it.groupValues[2]) {
                    "=" -> right
                    "+=" -> values.getValue(target) + right
                    "-=" -> values.getValue(target) - right
                    "*=" -> values.getValue(target) * right
                    "/=" -> Math.floorDiv(values.getValue(target), right)
                    "%=" -> Math.floorMod(values.getValue(target), right)
                    else -> error(command)
                }
                return@command false
            }
            compare.matchEntire(command)?.let {
                val left = values.getValue(scoreKey(it.groupValues[2]))
                val right = values.getValue(scoreKey(it.groupValues[4]))
                val condition = when (it.groupValues[3]) {
                    "=" -> left == right
                    "<" -> left < right
                    ">" -> left > right
                    "<=" -> left <= right
                    ">=" -> left >= right
                    else -> error(command)
                }
                if (condition == (it.groupValues[1] == "if")) return@command execute(
                    if (it.groupValues[6].isNotEmpty()) "execute ${it.groupValues[6]}" else it.groupValues[5])
                return@command false
            }
            matches.matchEntire(command)?.let {
                val value = values.getValue(scoreKey(it.groupValues[2]))
                val range = it.groupValues[3]
                val bounds = range.split("..")
                val match = if (bounds.size == 1) value == range.toInt() else
                    (bounds[0].isEmpty() || value >= bounds[0].toInt()) && (bounds[1].isEmpty() || value <= bounds[1].toInt())
                if (match == (it.groupValues[1] == "if")) return@command execute(
                    if (it.groupValues[5].isNotEmpty()) "execute ${it.groupValues[5]}" else it.groupValues[4])
                return@command false
            }
            functionCondition.matchEntire(command)?.let {
                val nonzero = run(functions.getValue(it.groupValues[2])) != 0
                if (nonzero == (it.groupValues[1] == "if")) return@command execute(
                    if (it.groupValues[4].isNotEmpty()) "execute ${it.groupValues[4]}" else it.groupValues[3])
                return@command false
            }
            if (command.startsWith("say ")) { messages.add(command.removePrefix("say ")); return@command false }
            if (command.startsWith("tellraw @a ")) {
                val diagnostic = JSON.parseObject(command.removePrefix("tellraw @a "))
                check(diagnostic.getString("type") == "text")
                messages.add(diagnostic.getString("text"))
                return@command false
            }
            if (command.startsWith("say ")) {
                messages.add(command.removePrefix("say "))
                return@command false
            }
            error("Unsupported command: $command")
        }
        run(commands)
    }
    private fun readProduced(value: top.mcfpp.core.lang.Var<*>, register: String): Int {
        val binding = requireNotNull(value.storageBinding) { "No producer location for '$register'" }
        val layout = binding.data.layoutAt(binding.place)
        if (layout is top.mcfpp.analysis.StorageLayout.Scoreboard)
            return values.getValue("${layout.player} ${layout.objective}")
        val address = binding.path.toCommandPart().analyze()
        val match = Regex("storage (\\S+) (.+)").matchEntire(address)
            ?: error("Unsupported produced address: $address")
        val source = match.groupValues[1]
        val path = match.groupValues[2]
        val completedPath = Regex("stack_frame\\[0]\\.(.+)").matchEntire(path)
        val tag = if (source == "mcfpp:system" && frames.isEmpty() && completedPath != null) {
            val completed = requireNotNull(completedRootFrame) { "No completed root frame for $address" }
            getNbt(completed, completedPath.groupValues[1])
        } else readNbt(source, path)
        return when (tag) {
            is IntTag -> tag.value
            is ByteTag -> tag.value.toInt()
            is ShortTag -> tag.value.toInt()
            else -> error("Expected an int/byte producer at $address, got $tag")
        }
    }
    fun read(value: MCInt) = readProduced(value, "${value.name} ${value.sbObject}")
    fun read(value: ScoreBool) = readProduced(value, "${value.name} ${value.boolObject}")
}

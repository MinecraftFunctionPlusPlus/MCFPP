package top.mcfpp.test.util

import top.mcfpp.core.lang.MCInt
import top.mcfpp.core.lang.bool.ScoreBool
import top.mcfpp.nbt.tags.CompoundTag
import top.mcfpp.nbt.tags.Tag
import top.mcfpp.nbt.tags.primitive.ByteTag
import top.mcfpp.nbt.tags.primitive.IntTag
import top.mcfpp.nbt.tags.primitive.ShortTag

/** Strict executor for the scoreboard and control-flow command subset covered by these tests. */
class ScoreCommandExecutor(commands: List<String>, functions: Map<String, List<String>> = emptyMap()) {
    val values = mutableMapOf<String, Int>()
    val messages = mutableListOf<String>()
    val branchGuards = mutableListOf<Int>()
    var stackDepth = 0
        private set
    private val frames = mutableListOf<MutableMap<String, Tag<*>>>()
    private val storage = mutableMapOf<String, MutableMap<String, Tag<*>>>()
    private fun address(source: String, path: String): Pair<MutableMap<String, Tag<*>>, String> {
        val frame = Regex("stack_frame\\[(\\d+)]\\.(.*)").matchEntire(path)
        return if (source == "mcfpp:system" && frame != null) frames[frame.groupValues[1].toInt()] to frame.groupValues[2]
            else storage.getOrPut(source) { mutableMapOf() } to path
    }
    fun readNbt(source: String, path: String): Tag<*> {
        val (root, key) = address(source, path)
        root[key]?.let { return it }
        val parts = key.split('.')
        for (size in parts.size - 1 downTo 1) {
            var value = root[parts.take(size).joinToString(".")] ?: continue
            for (part in parts.drop(size)) value = (value as CompoundTag)[part] ?: error("Missing NBT $path")
            return value
        }
        error("Missing NBT $source $path")
    }
    private fun writeNbt(source: String, path: String, value: Tag<*>) {
        val (root, key) = address(source, path)
        val parts = key.split('.')
        for (size in parts.size - 1 downTo 1) {
            var parent = root[parts.take(size).joinToString(".")] as? CompoundTag ?: continue
            for (part in parts.drop(size).dropLast(1)) parent = parent[part] as CompoundTag
            parent.put(parts.last(), value)
            return
        }
        root[key] = value
    }
    init {
        val set = Regex("scoreboard players set (\\S+ \\S+) (-?\\d+)")
        val add = Regex("scoreboard players (add|remove) (\\S+ \\S+) (\\d+)")
        val operation = Regex("scoreboard players operation (\\S+ \\S+) (=|\\+=|-=|\\*=|%=) (\\S+ \\S+)")
        val compare = Regex("execute (if|unless) score (\\S+ \\S+) (=|<|>|<=|>=) (\\S+ \\S+) run (.*)")
        val matches = Regex("execute if score (\\S+ \\S+) matches (-?\\d+|(?:-?\\d+)?\\.\\.(?:-?\\d+)?) run (.*)")
        val guardStore = Regex("execute store result storage mcfpp:system ir_branch_stack\\[0].condition byte 1 run scoreboard players get (\\S+ \\S+)")
        val guardTest = Regex("execute (if|unless) data storage mcfpp:system ir_branch_stack\\[0]\\{condition:1b} run (.*)")
        val save = Regex("execute store result storage (\\S+) (\\S+) (int|byte|short) 1 run scoreboard players get (\\S+ \\S+)")
        val restore = Regex("execute store result score (\\S+ \\S+) run data get storage (\\S+) (\\S+?)(?: 1(?:\\.0)?)?")
        val setNbt = Regex("data modify storage (\\S+) (\\S+) set value (.*)")
        val copyNbt = Regex("data modify storage (\\S+) (\\S+) set from storage (\\S+) (\\S+)")
        val clearCompound = Regex("data modify storage mcfpp:system stack_frame\\[(\\d+)]\\.(\\S+) set value \\{\\}")
        val storeTest = Regex("execute store success score (\\S+ \\S+) (if|unless) score (\\S+ \\S+) = (\\S+ \\S+)")
        val storeMatch = Regex("execute store success score (\\S+ \\S+) (if|unless) score (\\S+ \\S+) matches (-?\\d+)")
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
            restore.matchEntire(command)?.let { values[it.groupValues[1]] = (readNbt(it.groupValues[2], it.groupValues[3]).value as Number).toInt(); return@command false }
            setNbt.matchEntire(command)?.let {
                writeNbt(it.groupValues[1], it.groupValues[2], Tag.toNBT(it.groupValues[3])); return@command false
            }
            copyNbt.matchEntire(command)?.let {
                val value = readNbt(it.groupValues[3], it.groupValues[4])
                writeNbt(it.groupValues[1], it.groupValues[2], Tag.toNBT(Tag.toSNBT(value))); return@command false
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

package top.mcfpp.test.util

import top.mcfpp.nbt.tags.CompoundTag
import top.mcfpp.nbt.tags.Tag
import top.mcfpp.nbt.tags.collection.ByteArrayTag
import top.mcfpp.nbt.tags.collection.IntArrayTag
import top.mcfpp.nbt.tags.collection.ListTag
import top.mcfpp.nbt.tags.collection.LongArrayTag
import top.mcfpp.nbt.tags.primitive.ByteTag
import top.mcfpp.nbt.tags.primitive.DoubleTag
import top.mcfpp.nbt.tags.primitive.FloatTag
import top.mcfpp.nbt.tags.primitive.IntTag
import top.mcfpp.nbt.tags.primitive.LongTag
import top.mcfpp.nbt.tags.primitive.ShortTag
import top.mcfpp.nbt.tags.primitive.StringTag
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sin

data class ContextPosition(val x: Double, val y: Double, val z: Double) {
    operator fun plus(other: ContextPosition) = ContextPosition(x + other.x, y + other.y, z + other.z)
}

data class ContextRotation(val yaw: Double, val pitch: Double)

data class ContextEntity(
    val name: String,
    val type: String = "minecraft:pig",
    var tags: Set<String> = emptySet(),
    var position: ContextPosition,
    var rotation: ContextRotation,
    var dimension: String = "minecraft:overworld",
    val relations: Map<String, List<String>> = emptyMap(),
    val uuid: java.util.UUID = java.util.UUID.nameUUIDFromBytes(name.toByteArray(Charsets.UTF_8)),
    val attributes: Map<String, Double> = emptyMap(),
    val xpPoints: Int = 0,
    val xpLevels: Int = 0,
    val nbt: Map<String, Tag<*>> = emptyMap()
)

data class ContextBlock(
    val resource: String,
    val states: Map<String, String> = emptyMap(),
    val tags: Set<String> = emptySet(),
    val nbt: Map<String, Tag<*>> = emptyMap()
)

/** One actual `if block` evaluation, observed at the context in which it ran. */
data class ContextBlockTest(
    val executor: String?,
    val position: ContextPosition,
    val rotation: ContextRotation,
    val resource: String,
    val isTag: Boolean,
    val states: Map<String, String>,
    val matched: Boolean,
    val dimension: String = "minecraft:overworld"
)

data class ContextTeleport(val target: String, val from: ContextPosition, val to: ContextPosition,
                           val fromDimension: String, val dimension: String, val rotation: ContextRotation)

/** Selected identities for native damage checks; this executor does not simulate damage physics. */
data class ContextDamage(val target: String, val amount: Double, val kind: String?, val attacker: String?, val origin: String?)

/**
 * Strict executor for the actual production command shapes emitted for MC execution contexts.
 *
 * It consumes committed `Function` command lines (a generated root plus its registered functions),
 * never the lowering plan and never a rewritten/handcrafted command. Selectors fan out per entity,
 * `as`/`on` change only the executor, `at` changes position/rotation/dimension, `positioned`/`rotated` change one
 * component, local coordinates use the ambient rotation, block predicates read the supplied world,
 * `tp` updates entity space, and `return`/`return run function` implement function exit and goto dispatch.
 *
 * [ScoreCommandExecutor] consumes one immutable command list at construction and keeps its
 * scoreboard/NBT state private, so it cannot be re-entered for each nested execution context.
 * This executor reuses its documented command semantics instead of copying its full feature set.
 * Unsupported commands, selectors, coordinates and NBT shapes fail immediately rather than being
 * silently skipped. This is a test command interpreter, not a claim about Minecraft execution.
 */
class ContextCommandExecutor(
    commands: List<String>,
    private val functions: Map<String, List<String>> = emptyMap(),
    entities: List<ContextEntity> = emptyList(),
    rootContext: ContextEntity? = null,
    rootPosition: ContextPosition = ContextPosition(0.0, 0.0, 0.0),
    rootRotation: ContextRotation = ContextRotation(0.0, 0.0),
    private val world: (Int, Int, Int) -> ContextBlock? = { _, _, _ -> null },
    initialScores: Map<String, Int> = emptyMap(),
    private val macros: Map<String, String> = emptyMap(),
    private val commandBudget: Int = 20_000,
    rootDimension: String = rootContext?.dimension ?: "minecraft:overworld",
    private val worldInDimension: ((String, Int, Int, Int) -> ContextBlock?)? = null
) {
    val entities: MutableList<ContextEntity> = entities.map { it.copy(relations = it.relations.mapValues { (_, names) -> names.toList() }) }.toMutableList()
    private val rootContext = rootContext?.let { original -> this.entities.firstOrNull { it.name == original.name } ?: original.copy() }
    val values = linkedMapOf<String, Int>().apply { putAll(initialScores) }
    val objectives = linkedSetOf<String>()
    val blockTests = arrayListOf<ContextBlockTest>()
    val teleports = arrayListOf<ContextTeleport>()
    val damages = arrayListOf<ContextDamage>()
    val enabledTriggers = arrayListOf<Pair<String, String>>()
    val messages = arrayListOf<String>()
    val executed = arrayListOf<String>()

    /**
     * One entry per non-comment original command-list line, appended before the line is
     * interpreted. The initial driver invocation is also a root line; measurement callers exclude
     * it when they count only function-body lines. `execute` wrappers appear once, never together
     * with the command they run; [executed] remains the leaf-command trace and is not changed for
     * this count.
     */
    val executedFunctionLines = arrayListOf<String>()

    private val storage = linkedMapOf<String, MutableMap<String, Tag<*>>>()
    private val frames = arrayListOf<MutableMap<String, Tag<*>>>()
    private var steps = 0
    private var spawned = 0
    private val recent = ArrayDeque<String>()

    private data class ExecContext(
        val executor: ContextEntity?,
        val position: ContextPosition,
        val rotation: ContextRotation,
        val dimension: String
    )

    private data class CommandResult(
        val returned: Boolean = false,
        val value: Int = 0,
        val result: Int = 0,
        val success: Boolean = true
    )

    init {
        this.entities.forEach { entity -> storage["entity:${entity.name}"] = entity.nbt.mapValues { it.value.copy() }.toMutableMap() }
        run(commands, ExecContext(this.rootContext, rootPosition, rootRotation, rootDimension))
    }

    fun score(player: String, objective: String): Int =
        values["$player $objective"] ?: error("Missing score $player $objective")

    fun readStorage(source: String, path: String): Tag<*> = readNbt(source, path)

    fun readEntity(name: String, path: String): Tag<*> = readNbt("entity:$name", path)

    fun readBlock(dimension: String, x: Int, y: Int, z: Int, path: String): Tag<*> =
        readNbt("block:$dimension:$x,$y,$z", path)

    private fun run(commands: List<String>, context: ExecContext): CommandResult {
        for (raw in commands) {
            val command = raw.trim()
            if (command.isEmpty() || command.startsWith("#")) continue
            executedFunctionLines += command
            val result = execute(command, context)
            if (result.returned) return result
        }
        return CommandResult()
    }

    private fun runFunction(name: String, context: ExecContext): CommandResult {
        val body = functions[name] ?: error("Unknown function $name")
        return run(body, context)
    }

    private fun execute(command: String, context: ExecContext): CommandResult {
        recent.addLast(command)
        if (recent.size > 30) recent.removeFirst()
        check(++steps < commandBudget) {
            "Command execution did not terminate: steps=$steps\n" + recent.joinToString("\n")
        }
        executed += command

        if (command.startsWith("execute ")) return executeChain(command.removePrefix("execute "), context)

        if (command.startsWith("return ")) {
            val value = command.removePrefix("return ")
            return when {
                value.startsWith("run ") -> {
                    val result = execute(value.removePrefix("run "), context)
                    CommandResult(returned = true, value = result.result, result = result.result, success = result.success)
                }
                value == "fail" -> CommandResult(returned = true, success = false)
                else -> CommandResult(true, value.toInt())
            }
        }
        if (command == "return") return CommandResult(true, 0)

        if (command.startsWith("function ")) {
            val call = command.removePrefix("function ")
            val withStorage = Regex("^(\\S+) with storage (\\S+) (\\S+)$").matchEntire(call)
            if (withStorage != null) return macroCall(withStorage, context)
            val result = runFunction(call, context)
            return CommandResult(result = result.value, success = result.success)
        }

        if (command.startsWith("scoreboard ")) return scoreboard(command, context)

        if (command.startsWith("data ")) return data(command, context)
        if (command.startsWith("tp ")) return teleport(command.removePrefix("tp "), context)
        if (command.startsWith("teleport ")) return teleport(command.removePrefix("teleport "), context)
        if (command.startsWith("attribute ")) {
            val (parts, after) = take(command.removePrefix("attribute "), 5)
            check(after.isEmpty() && parts[2] == "base" && parts[3] == "get") { "Unsupported attribute command: $command" }
            val selected = select(parts[0], context)
            check(selected.size <= 1) { "Attribute query requires a single entity" }
            val entity = selected.singleOrNull() ?: return CommandResult(success = false)
            val value = entity.attributes[parts[1]] ?: return CommandResult(success = false)
            return CommandResult(result = floor(value * parts[4].toDouble()).toInt(), success = true)
        }
        if (command.startsWith("xp query ") || command.startsWith("experience query ")) {
            val (parts, after) = take(command.substringAfter("query "), 2)
            check(after.isEmpty() && parts[1] in setOf("points", "levels")) { "Unsupported xp command: $command" }
            val selected = select(parts[0], context)
            check(selected.size <= 1) { "XP query requires a single player" }
            val player = selected.singleOrNull()?.takeIf { it.type == "minecraft:player" }
                ?: return CommandResult(success = false)
            return CommandResult(result = if (parts[1] == "points") player.xpPoints else player.xpLevels, success = true)
        }
        if (command.startsWith("damage ")) return damage(command.removePrefix("damage "), context)
        if (command.startsWith("tag ")) {
            val (parts, after) = take(command.removePrefix("tag "), 3)
            check(after.isEmpty() && parts[1] in setOf("add", "remove")) { "Unsupported tag command: $command" }
            val selected = select(parts[0], context)
            var changed = 0
            selected.forEach { entity ->
                val tags = if (parts[1] == "add") entity.tags + parts[2] else entity.tags - parts[2]
                if (tags != entity.tags) changed++
                entity.tags = tags
            }
            return CommandResult(result = changed, success = changed > 0)
        }
        if (command.startsWith("kill ")) {
            val (selector, after) = token(command.removePrefix("kill "))
            check(after.isEmpty()) { "Unsupported kill command: $command" }
            val selected = select(selector, context)
            entities.removeAll(selected.toSet())
            return CommandResult(result = selected.size, success = selected.isNotEmpty())
        }

        if (command.startsWith("say ")) {
            messages += command.removePrefix("say ")
            return CommandResult()
        }
        if (command.startsWith("tellraw ")) {
            check(command.startsWith("tellraw @a ")) { "Unsupported tellraw recipient: $command" }
            val diagnostic = com.alibaba.fastjson2.JSON.parseObject(command.removePrefix("tellraw @a "))
            check(diagnostic.getString("type") == "text") { "Only literal text components are supported: $command" }
            messages += diagnostic.getString("text")
            return CommandResult()
        }
        error("Unsupported command: $command")
    }

    private fun executeChain(chain: String, context: ExecContext): CommandResult {
        if (chain.startsWith("run ")) return execute(chain.removePrefix("run "), context)

        if (chain.startsWith("summon ")) {
            val (type, after) = token(chain.removePrefix("summon "))
            check(type == "minecraft:marker") { "Unsupported execute summon entity: $type" }
            val marker = ContextEntity("__context_anchor_${spawned++}", type,
                position = context.position, rotation = ContextRotation(0.0, 0.0), dimension = context.dimension)
            entities += marker
            // Vanilla withEntity changes the executor, retaining the source position/rotation.
            return executeChain(after, context.copy(executor = marker))
        }

        if (chain.startsWith("as ")) return fanOut(chain.removePrefix("as "), context) { target ->
            context.copy(executor = target)
        }
        if (chain.startsWith("at ")) return fanOut(chain.removePrefix("at "), context) { target ->
            context.copy(position = target.position, rotation = target.rotation, dimension = target.dimension)
        }
        if (chain.startsWith("in ")) {
            val (dimension, after) = token(chain.removePrefix("in "))
            return executeChain(after, context.copy(dimension = dimension))
        }
        if (chain.startsWith("on ")) {
            val (relation, after) = token(chain.removePrefix("on "))
            return each(related(relation, context), after) { context.copy(executor = it) }
        }
        if (chain.startsWith("positioned as ")) return fanOut(chain.removePrefix("positioned as "), context) { target ->
            context.copy(position = target.position)
        }
        if (chain.startsWith("positioned ")) {
            val (coordinates, after) = take(chain.removePrefix("positioned "), 3)
            return executeChain(after, context.copy(position = position(coordinates, context)))
        }
        if (chain.startsWith("rotated as ")) return fanOut(chain.removePrefix("rotated as "), context) { target ->
            context.copy(rotation = target.rotation)
        }
        if (chain.startsWith("rotated ")) {
            val (angles, after) = take(chain.removePrefix("rotated "), 2)
            return executeChain(after, context.copy(rotation = rotation(angles, context)))
        }
        if (chain.startsWith("if ")) return condition(chain.removePrefix("if "), context, true)
        if (chain.startsWith("unless ")) return condition(chain.removePrefix("unless "), context, false)
        if (chain.startsWith("store ")) return store(chain.removePrefix("store "), context)
        error("Unsupported execute clause: $chain")
    }

    private fun fanOut(rest: String, context: ExecContext,
                       transform: (ContextEntity) -> ExecContext): CommandResult {
        val (selector, after) = token(rest)
        return each(select(selector, context), after, transform)
    }

    private fun each(targets: List<ContextEntity>, after: String,
                     transform: (ContextEntity) -> ExecContext): CommandResult {
        if (targets.isEmpty()) return CommandResult(result = 0, success = false)
        var last = CommandResult()
        for (target in targets) {
            val result = executeChain(after, transform(target))
            if (result.returned) return result
            last = result
        }
        return last
    }

    private fun related(relation: String, context: ExecContext): List<ContextEntity> {
        check(relation in setOf("vehicle", "passengers", "controller", "owner", "leasher", "target", "attacker", "origin")) {
            "Unsupported relation: $relation"
        }
        val origin = context.executor ?: return emptyList()
        val names = origin.relations[relation] ?: if (relation == "vehicle")
            entities.filter { origin.name in it.relations["passengers"].orEmpty() }.map { it.name } else emptyList()
        if (relation != "passengers") check(names.size <= 1) { "A $relation relation has at most one target" }
        return names.map { name -> entities.singleOrNull { it.name == name } ?: error("Unknown relation entity $name") }
    }

    private fun teleport(rest: String, context: ExecContext): CommandResult {
        val (selector, destination) = token(rest)
        val targets = select(selector, context)
        val parts = destination.split(' ')
        val destinationPosition: ContextPosition
        val destinationDimension: String
        val destinationRotation: ContextRotation?
        if (parts.size == 1) {
            val matches = select(parts.single(), context)
            check(matches.size <= 1) { "Teleport destination must be a single entity" }
            val entity = matches.singleOrNull() ?: return CommandResult(success = false)
            destinationPosition = entity.position
            destinationDimension = entity.dimension
            destinationRotation = entity.rotation
        } else {
            check(parts.size == 3 || parts.size == 5) { "Unsupported teleport arguments: $rest" }
            destinationPosition = position(parts.take(3), context)
            destinationDimension = context.dimension
            destinationRotation = if (parts.size == 5) rotation(parts.drop(3), context) else null
        }
        targets.forEach { target ->
            val before = target.position
            val beforeDimension = target.dimension
            target.position = destinationPosition
            target.dimension = destinationDimension
            if (destinationRotation != null) target.rotation = destinationRotation
            teleports += ContextTeleport(target.name, before, destinationPosition, beforeDimension, destinationDimension, target.rotation)
        }
        return CommandResult(result = targets.size, success = targets.isNotEmpty())
    }

    private fun damage(rest: String, context: ExecContext): CommandResult {
        val (targetText, afterTarget) = token(rest)
        val (amount, afterAmount) = token(afterTarget)
        val targets = select(targetText, context)
        check(targets.size <= 1) { "Damage requires a single victim" }
        val victim = targets.singleOrNull()
        val numericAmount = amount.toDouble()
        var missing = victim == null
        var remaining = afterAmount
        var kind: String? = null
        var attacker: String? = null
        var origin: String? = null
        if (remaining.isNotEmpty()) {
            val (resource, afterKind) = token(remaining)
            check(resource != "by" && resource != "from" && resource != "at") { "Damage source requires a damage type" }
            kind = resource
            remaining = afterKind
        }
        if (remaining.isNotEmpty()) {
            val (by, afterBy) = token(remaining)
            check(by == "by") { "Unsupported damage clause: $remaining" }
            val (source, afterSource) = token(afterBy)
            val selected = select(source, context)
            check(selected.size <= 1) { "Damage attacker requires a single entity" }
            attacker = selected.singleOrNull()?.name
            missing = missing || attacker == null
            remaining = afterSource
        }
        if (remaining.isNotEmpty()) {
            val (from, afterFrom) = token(remaining)
            check(from == "from") { "Unsupported damage clause: $remaining" }
            val (source, afterSource) = token(afterFrom)
            val selected = select(source, context)
            check(selected.size <= 1) { "Damage origin requires a single entity" }
            origin = selected.singleOrNull()?.name
            missing = missing || origin == null
            remaining = afterSource
        }
        check(remaining.isEmpty()) { "Unsupported damage arguments: $remaining" }
        if (missing) return CommandResult(success = false)
        damages += ContextDamage(victim!!.name, numericAmount, kind, attacker, origin)
        return CommandResult(result = 1, success = true)
    }

    private fun condition(rest: String, context: ExecContext, expected: Boolean): CommandResult {
        val (matched, after) = test(rest, context)
        if (matched != expected) return CommandResult(result = 0, success = false)
        return executeChain(after, context)
    }

    private fun test(rest: String, context: ExecContext): Pair<Boolean, String> {
        Regex("^dimension (\\S+)").find(rest)?.let { match ->
            return (context.dimension == match.groupValues[1]) to rest.substring(match.range.last + 1).trimStart()
        }
        Regex("^score (\\S+ \\S+) (=|<=|>=|<|>) (\\S+ \\S+)").find(rest)?.let { match ->
            val left = scores[scoreKey(match.groupValues[1], context)]
            val right = scores[scoreKey(match.groupValues[3], context)]
            val result = left != null && right != null && when (match.groupValues[2]) {
                "=" -> left == right
                "<" -> left < right
                ">" -> left > right
                "<=" -> left <= right
                ">=" -> left >= right
                else -> error(match.groupValues[2])
            }
            return result to rest.substring(match.range.last + 1).trimStart()
        }
        Regex("^score (\\S+ \\S+) matches (\\S+)").find(rest)?.let { match ->
            val value = scores[scoreKey(match.groupValues[1], context)]
            val bounds = match.groupValues[2].split("..")
            val result = value != null && if (bounds.size == 1) value == bounds[0].toInt() else
                (bounds[0].isEmpty() || value >= bounds[0].toInt()) &&
                    (bounds[1].isEmpty() || value <= bounds[1].toInt())
            return result to rest.substring(match.range.last + 1).trimStart()
        }
        Regex("^block (\\S+) (\\S+) (\\S+) (\\S+)").find(rest)?.let { match ->
            val at = position(match.groupValues.subList(1, 4), context)
            val predicate = match.groupValues[4]
            val result = blockMatches(at, predicate, context)
            return result to rest.substring(match.range.last + 1).trimStart()
        }
        Regex("^data storage (\\S+) (\\S+)").find(rest)?.let { match ->
            val path = match.groupValues[2]
            val predicateAt = path.indexOf('{')
            val actual = if (predicateAt < 0) path else path.substring(0, predicateAt)
            val present = try {
                val value = readNbt(match.groupValues[1], actual)
                predicateAt < 0 || matches(value, Tag.toNBT(path.substring(predicateAt)) as CompoundTag)
            } catch (_: IllegalStateException) {
                false
            } catch (_: IndexOutOfBoundsException) {
                false
            }
            return present to rest.substring(match.range.last + 1).trimStart()
        }
        Regex("^function (\\S+)").find(rest)?.let { match ->
            val result = runFunction(match.groupValues[1], context)
            return (result.value != 0 && result.success) to rest.substring(match.range.last + 1).trimStart()
        }
        if (rest.startsWith("entity ")) {
            val (selector, after) = token(rest.removePrefix("entity "))
            return select(selector, context).isNotEmpty() to after
        }
        error("Unsupported execute condition: $rest")
    }

    private fun store(rest: String, context: ExecContext): CommandResult {
        val (mode, afterMode) = token(rest)
        check(mode == "result" || mode == "success") { "Unsupported store mode: $mode" }
        val (target, afterTarget) = token(afterMode)
        return when (target) {
            "score" -> {
                val (holder, afterHolder) = token(afterTarget)
                val (objective, afterObjective) = token(afterHolder)
                val inner = executeChain(afterObjective, context)
                val value = if (mode == "result") inner.result else if (inner.success) 1 else 0
                values[scoreKey("$holder $objective", context)] = value
                inner
            }
            "storage" -> {
                val (source, afterSource) = token(afterTarget)
                val (path, afterPath) = token(afterSource)
                val (type, afterType) = token(afterPath)
                val (scaleText, afterScale) = token(afterType)
                val inner = executeChain(afterScale, context)
                val value = (if (mode == "result") inner.result else if (inner.success) 1 else 0) *
                    scaleText.toInt()
                writeNbt(source, path, numericTag(type, value))
                inner
            }
            else -> error("Unsupported store target: $target")
        }
    }

    private fun macroCall(match: MatchResult, context: ExecContext): CommandResult {
        val name = match.groupValues[1]
        val arguments = readNbt(match.groupValues[2], match.groupValues[3]) as? CompoundTag
            ?: error("Macro arguments are not a compound")
        val template = macros[name] ?: macros[name.removePrefix("mcfpp:dynamic/")]
            ?: error("Unknown macro function $name")
        val body = template.lines().map { line ->
            if (!line.startsWith("$")) line else Regex("\\$\\(([^)]+)\\)").replace(line.removePrefix("$")) {
                when (val value = arguments[it.groupValues[1]] ?: error("Missing macro argument ${it.groupValues[1]}")) {
                    is StringTag -> value.value
                    is CompoundTag, is IntArrayTag, is LongArrayTag, is ByteArrayTag, is ListTag -> top.mcfpp.backend.NbtEncoding.snbt(value)
                    else -> value.value.toString()
                }
            }
        }
        val result = run(body, context)
        return CommandResult(result = result.value, success = result.success)
    }

    private fun select(selector: String, context: ExecContext): List<ContextEntity> {
        if (!selector.startsWith("@")) {
            // The entity factory accepts shortened UUID groups, which resolve to the same UUID.
            val uuid = runCatching { java.util.UUID.fromString(selector) }.getOrNull()
            return entities.filter { it.name == selector || uuid != null && it.uuid == uuid }
        }
        val base = selector.substringBefore('[')
        var result = when (base) {
            "@a" -> entities.filter { it.type == "minecraft:player" }
            "@e", "@n" -> entities.toList()
            "@s" -> listOfNotNull(context.executor)
            "@p" -> entities.filter { it.type == "minecraft:player" }
            "@r" -> error("Random selectors are not deterministic in this test executor")
            else -> error("Unsupported selector: $selector")
        }
        val arguments = if (selector.contains('[')) selector.substringAfter('[').substringBeforeLast(']') else ""
        var limit: Int? = if (base == "@p" || base == "@n") 1 else null
        var sort: String? = if (base == "@p" || base == "@n") "nearest" else null
        if (arguments.isNotBlank()) for (argument in selectorArguments(arguments)) {
            val key = argument.substringBefore('=')
            val value = argument.substringAfter('=', "")
            when (key) {
                "type" -> result = result.filter { it.type == value }
                "tag" -> result = result.filter { if (value.startsWith("!")) value.drop(1) !in it.tags else value in it.tags }
                "name" -> {
                    val reversed = value.startsWith("!")
                    val rawName = if (reversed) value.drop(1) else value
                    val name = if (rawName.startsWith('"') || rawName.startsWith('\''))
                        (literalTag(rawName) as StringTag).value else rawName
                    result = result.filter { (it.name == name) != reversed }
                }
                "nbt" -> {
                    val predicate = Tag.toNBT(value) as CompoundTag
                    check(predicate.value.keys == setOf("UUID")) { "Unsupported selector NBT: $value" }
                    val uuid = predicate["UUID"] as? IntArrayTag ?: error("UUID selector requires an int array")
                    result = result.filter { uuidNbt(it).value.contentEquals(uuid.value) }
                }
                "limit" -> limit = value.toInt()
                "sort" -> sort = value
                else -> error("Unsupported selector argument: $argument")
            }
        }
        result = when (sort) {
            "nearest" -> result.sortedBy { distance(it.position, context.position) }
            "furthest" -> result.sortedByDescending { distance(it.position, context.position) }
            "arbitrary", null -> result
            "random" -> error("Random selectors are not deterministic in this test executor")
            else -> error("Unsupported selector sort: $sort")
        }
        return limit?.let { result.take(it) } ?: result
    }

    private fun selectorArguments(arguments: String): List<String> {
        val result = ArrayList<String>()
        var start = 0
        var depth = 0
        var quote: Char? = null
        var escaped = false
        arguments.forEachIndexed { index, char ->
            if (quote != null) {
                if (char == quote && !escaped) quote = null
                escaped = char == '\\' && !escaped
            } else when (char) {
                '\"', '\'' -> { quote = char; escaped = false }
                '[', '{' -> depth++
                ']', '}' -> depth--
                ',' -> if (depth == 0) { result += arguments.substring(start, index); start = index + 1 }
            }
        }
        check(depth == 0 && quote == null) { "Unclosed selector arguments: $arguments" }
        result += arguments.substring(start)
        return result
    }

    private fun uuidNbt(entity: ContextEntity): IntArrayTag = IntArrayTag(intArrayOf(
        (entity.uuid.mostSignificantBits shr 32).toInt(), entity.uuid.mostSignificantBits.toInt(),
        (entity.uuid.leastSignificantBits shr 32).toInt(), entity.uuid.leastSignificantBits.toInt()))

    private fun distance(first: ContextPosition, second: ContextPosition): Double {
        val x = first.x - second.x
        val y = first.y - second.y
        val z = first.z - second.z
        return x * x + y * y + z * z
    }

    private fun position(tokens: List<String>, context: ExecContext): ContextPosition {
        check(tokens.size == 3) { "A position requires three coordinates" }
        if (tokens.all { it.startsWith("^") }) {
            val left = tokens[0].drop(1).ifEmpty { "0" }.toDouble()
            val up = tokens[1].drop(1).ifEmpty { "0" }.toDouble()
            val forward = tokens[2].drop(1).ifEmpty { "0" }.toDouble()
            val yaw = Math.toRadians(context.rotation.yaw)
            val pitch = Math.toRadians(context.rotation.pitch)
            fun trig(value: Double) = if (kotlin.math.abs(value) < 1.0e-9) 0.0 else value
            val l = ContextPosition(trig(cos(yaw)), 0.0, trig(sin(yaw)))
            val u = ContextPosition(trig(sin(yaw) * sin(pitch)), trig(cos(pitch)),
                trig(-cos(yaw) * sin(pitch)))
            val f = ContextPosition(trig(-sin(yaw) * cos(pitch)), trig(-sin(pitch)),
                trig(cos(yaw) * cos(pitch)))
            return context.position + ContextPosition(
                l.x * left + u.x * up + f.x * forward,
                l.y * left + u.y * up + f.y * forward,
                l.z * left + u.z * up + f.z * forward)
        }
        check(tokens.none { it.startsWith("^") }) { "Mixed local and absolute coordinates are unsupported" }
        fun axis(token: String, value: Double): Double = when {
            token == "~" -> value
            token.startsWith("~") -> value + token.drop(1).toDouble()
            else -> token.toDouble()
        }
        return ContextPosition(axis(tokens[0], context.position.x),
            axis(tokens[1], context.position.y), axis(tokens[2], context.position.z))
    }

    private fun rotation(tokens: List<String>, context: ExecContext): ContextRotation {
        check(tokens.size == 2) { "A rotation requires yaw and pitch" }
        fun angle(token: String, value: Double): Double = when {
            token == "~" -> value
            token.startsWith("~") -> value + token.drop(1).toDouble()
            else -> token.toDouble()
        }
        return ContextRotation(angle(tokens[0], context.rotation.yaw),
            angle(tokens[1], context.rotation.pitch))
    }

    private fun blockMatches(position: ContextPosition, predicate: String, context: ExecContext): Boolean {
        val resource = predicate.substringBefore('[')
        val isTag = resource.startsWith("#")
        val states = if (!predicate.contains('[')) emptyMap() else
            predicate.substringAfter('[').substringBeforeLast(']').split(',').filter { it.isNotBlank() }
                .associate { it.substringBefore('=') to it.substringAfter('=') }
        val x = floor(position.x).toInt()
        val y = floor(position.y).toInt()
        val z = floor(position.z).toInt()
        val block = worldInDimension?.invoke(context.dimension, x, y, z) ?: if (worldInDimension == null) world(x, y, z) else null
        val matched = block != null &&
            (if (isTag) resource.removePrefix("#") in block.tags else block.resource == resource) &&
            states.all { (key, value) -> block.states[key] == value }
        blockTests += ContextBlockTest(context.executor?.name, position, context.rotation,
            resource.removePrefix("#"), isTag, states, matched, context.dimension)
        return matched
    }

    private fun scoreboard(command: String, context: ExecContext): CommandResult {
        Regex("scoreboard objectives add (\\S+) (\\S+)(?: (.+))?").matchEntire(command)?.let {
            check(objectives.add(it.groupValues[1])) { "Objective already exists: ${it.groupValues[1]}" }
            return CommandResult()
        }
        Regex("scoreboard players set (\\S+) (\\S+) (-?\\d+)").matchEntire(command)?.let {
            values[scoreKey("${it.groupValues[1]} ${it.groupValues[2]}", context)] = it.groupValues[3].toInt()
            return CommandResult(result = 1)
        }
        Regex("scoreboard players (add|remove) (\\S+) (\\S+) (\\d+)").matchEntire(command)?.let {
            val key = scoreKey("${it.groupValues[2]} ${it.groupValues[3]}", context)
            val delta = it.groupValues[4].toInt() * if (it.groupValues[1] == "add") 1 else -1
            values[key] = (values[key] ?: 0) + delta
            return CommandResult(result = values.getValue(key))
        }
        Regex("scoreboard players operation (\\S+) (\\S+) (=|\\+=|-=|\\*=|/=|%=|><) (\\S+) (\\S+)")
            .matchEntire(command)?.let {
                val target = scoreKey("${it.groupValues[1]} ${it.groupValues[2]}", context)
                val source = scoreKey("${it.groupValues[4]} ${it.groupValues[5]}", context)
                if (it.groupValues[3] == "><") {
                    val left = values[target] ?: 0
                    values[target] = values.getValue(source)
                    values[source] = left
                    return CommandResult(result = 1)
                }
                val left = values[target] ?: 0
                val right = values.getValue(source)
                if (it.groupValues[3] in setOf("/=", "%=") && right == 0) {
                    return CommandResult(result = 0, success = false)
                }
                values[target] = when (it.groupValues[3]) {
                    "=" -> right
                    "+=" -> left + right
                    "-=" -> left - right
                    "*=" -> left * right
                    "/=" -> Math.floorDiv(left, right)
                    "%=" -> Math.floorMod(left, right)
                    else -> error(command)
                }
                return CommandResult(result = 1)
            }
        if (command.startsWith("scoreboard players get ")) {
            val (parts, after) = take(command.removePrefix("scoreboard players get "), 2)
            check(after.isEmpty()) { "Unsupported score query: $command" }
            if (parts[0].startsWith("@") && select(parts[0], context).isEmpty()) return CommandResult(success = false)
            val key = scoreKey("${parts[0]} ${parts[1]}", context)
            val value = values[key] ?: return CommandResult(result = 0, success = false)
            return CommandResult(result = value, success = true)
        }
        Regex("scoreboard players reset (\\S+)(?: (\\S+))?").matchEntire(command)?.let {
            val holder = scoreKey("${it.groupValues[1]} ${it.groupValues[2].ifEmpty { "objective" }}", context)
            if (it.groupValues[2].isEmpty()) {
                values.keys.removeIf { key -> key.substringBefore(' ') == holder.substringBefore(' ') }
            } else values.remove(holder)
            return CommandResult(result = 1)
        }
        Regex("scoreboard players enable (\\S+) (\\S+)").matchEntire(command)?.let {
            val selected = select(it.groupValues[1], context)
            check(selected.all { entity -> entity.type == "minecraft:player" }) { "Only players can enable a trigger" }
            selected.forEach { entity -> enabledTriggers.add(entity.name to it.groupValues[2]) }
            return CommandResult(result = selected.size, success = selected.isNotEmpty())
        }
        error("Unsupported scoreboard command: $command")
    }

    private fun scoresKey(key: String): String = key

    private val scores: Map<String, Int>
        get() = values

    private fun scoreKey(key: String, context: ExecContext): String {
        val (player, objective) = token(key)
        if (!player.startsWith("@")) return scoresKey(key)
        val executor = select(player, context).singleOrNull() ?: error("Score target requires one entity: $player")
        check(objective.isNotEmpty()) { "A score target requires an objective" }
        return "${executor.name} $objective"
    }

    private fun data(command: String, context: ExecContext): CommandResult {
        if (command == "data modify storage mcfpp:system stack_frame prepend value {}") {
            frames.add(0, mutableMapOf())
            return CommandResult(result = 1)
        }
        if (command == "data remove storage mcfpp:system stack_frame[0]") {
            check(frames.isNotEmpty()) { "No stack frame to remove" }
            frames.removeAt(0)
            return CommandResult(result = 1)
        }
        Regex("^data modify storage (\\S+) ($NBT_PATH) set value (.*)$").matchEntire(command)?.let {
            writeNbt(it.groupValues[1], it.groupValues[2], literalTag(it.groupValues[3]))
            return CommandResult(result = 1, success = true)
        }
        Regex("^data modify storage (\\S+) ($NBT_PATH) set string storage (\\S+) ($NBT_PATH) (-?\\d+) (-?\\d+)$")
            .matchEntire(command)?.let {
                val value = (readNbt(it.groupValues[3], it.groupValues[4]) as StringTag).value
                fun index(raw: String): Int = raw.toInt().let { position -> if (position < 0) value.length + position else position }
                val start = index(it.groupValues[5])
                val end = index(it.groupValues[6])
                check(start in 0..value.length && end in start..value.length) { "Invalid string slice: $command" }
                val result = StringTag(value.substring(start, end))
                val old = try { readNbt(it.groupValues[1], it.groupValues[2]) } catch (_: IllegalStateException) { null }
                writeNbt(it.groupValues[1], it.groupValues[2], result)
                val changed = old != result
                return CommandResult(result = if (changed) 1 else 0, success = changed)
            }
        Regex("^data modify storage (\\S+) ($NBT_PATH) set from storage (\\S+) ($NBT_PATH)$")
            .matchEntire(command)?.let {
                val value = try { readNbt(it.groupValues[3], it.groupValues[4]).copy() }
                    catch (_: IllegalStateException) { return CommandResult(success = false) }
                    catch (_: IndexOutOfBoundsException) { return CommandResult(success = false) }
                val old = try { readNbt(it.groupValues[1], it.groupValues[2]) } catch (_: IllegalStateException) { null }
                writeNbt(it.groupValues[1], it.groupValues[2], value)
                val changed = old != value
                return CommandResult(result = if (changed) 1 else 0, success = changed)
            }
        Regex("^data modify storage (\\S+) ($NBT_PATH) set from entity (\\S+) ($NBT_PATH)$")
            .matchEntire(command)?.let {
                val selected = select(it.groupValues[3], context)
                check(selected.size <= 1) { "Entity NBT source must be single" }
                val entity = selected.singleOrNull() ?: return CommandResult(success = false)
                val value = if (it.groupValues[4] == "UUID") uuidNbt(entity) else try {
                    readEntity(entity.name, it.groupValues[4]).copy()
                } catch (_: IllegalStateException) { return CommandResult(success = false) }
                val old = try { readNbt(it.groupValues[1], it.groupValues[2]) } catch (_: IllegalStateException) { null }
                writeNbt(it.groupValues[1], it.groupValues[2], value)
                val changed = old != value
                return CommandResult(result = if (changed) 1 else 0, success = changed)
            }
        Regex("^data modify entity (\\S+) ($NBT_PATH) set from storage (\\S+) ($NBT_PATH)$")
            .matchEntire(command)?.let {
                val selected = select(it.groupValues[1], context)
                check(selected.size <= 1) { "Entity NBT destination must be single" }
                val entity = selected.singleOrNull() ?: return CommandResult(success = false)
                val value = readNbt(it.groupValues[3], it.groupValues[4]).copy()
                val source = "entity:${entity.name}"
                val old = try { readNbt(source, it.groupValues[2]) } catch (_: IllegalStateException) { null }
                writeNbt(source, it.groupValues[2], value)
                val changed = old != value
                return CommandResult(result = if (changed) 1 else 0, success = changed)
            }
        Regex("^data modify storage (\\S+) ($NBT_PATH) set from block (\\S+) (\\S+) (\\S+) ($NBT_PATH)$")
            .matchEntire(command)?.let {
                val source = blockSource(it.groupValues.subList(3, 6), context) ?: return CommandResult(success = false)
                val value = try { readNbt(source, it.groupValues[6]).copy() }
                    catch (_: IllegalStateException) { return CommandResult(success = false) }
                val old = try { readNbt(it.groupValues[1], it.groupValues[2]) } catch (_: IllegalStateException) { null }
                writeNbt(it.groupValues[1], it.groupValues[2], value)
                return CommandResult(result = if (old != value) 1 else 0, success = old != value)
            }
        Regex("^data modify block (\\S+) (\\S+) (\\S+) ($NBT_PATH) set from storage (\\S+) ($NBT_PATH)$")
            .matchEntire(command)?.let {
                val source = blockSource(it.groupValues.subList(1, 4), context) ?: return CommandResult(success = false)
                val value = readNbt(it.groupValues[5], it.groupValues[6]).copy()
                val old = try { readNbt(source, it.groupValues[4]) } catch (_: IllegalStateException) { null }
                writeNbt(source, it.groupValues[4], value)
                return CommandResult(result = if (old != value) 1 else 0, success = old != value)
            }
        Regex("^data remove entity (\\S+) ($NBT_PATH)$").matchEntire(command)?.let {
            val entity = select(it.groupValues[1], context).singleOrNull() ?: return CommandResult(success = false)
            val source = "entity:${entity.name}"
            try { readNbt(source, it.groupValues[2]) } catch (_: IllegalStateException) { return CommandResult(success = false) }
            removeNbt(source, it.groupValues[2])
            return CommandResult(result = 1)
        }
        Regex("^data modify storage (\\S+) ($NBT_PATH) merge value (.*)$").matchEntire(command)?.let {
            mergeNbt(readNbt(it.groupValues[1], it.groupValues[2]) as CompoundTag,
                Tag.toNBT(it.groupValues[3]) as CompoundTag)
            return CommandResult(result = 1)
        }
        Regex("^data modify storage (\\S+) ($NBT_PATH) merge from storage (\\S+) ($NBT_PATH)$")
            .matchEntire(command)?.let {
                mergeNbt(readNbt(it.groupValues[1], it.groupValues[2]) as CompoundTag,
                    readNbt(it.groupValues[3], it.groupValues[4]).copy() as CompoundTag)
                return CommandResult(result = 1)
            }
        Regex("^data modify storage (\\S+) ($NBT_PATH) (append|prepend) value (.*)$").matchEntire(command)?.let {
            val list = readNbt(it.groupValues[1], it.groupValues[2]) as ListTag
            val value = literalTag(it.groupValues[4])
            if (it.groupValues[3] == "append") list.value.add(value) else list.value.add(0, value)
            return CommandResult(result = 1)
        }
        Regex("^data modify storage (\\S+) ($NBT_PATH) (append|prepend) from storage (\\S+) ($NBT_PATH)$")
            .matchEntire(command)?.let {
                val value = try { readNbt(it.groupValues[4], it.groupValues[5]).copy() }
                    catch (_: IllegalStateException) { return CommandResult(success = false) }
                val list = readNbt(it.groupValues[1], it.groupValues[2]) as ListTag
                if (it.groupValues[3] == "append") list.value.add(value) else list.value.add(0, value)
                return CommandResult(result = 1)
            }
        Regex("^data remove storage (\\S+) ($NBT_PATH)$").matchEntire(command)?.let {
            removeNbt(it.groupValues[1], it.groupValues[2])
            return CommandResult(result = 1)
        }
        Regex("^data get storage (\\S+) ($NBT_PATH)(?: (\\S+))?$").matchEntire(command)?.let {
            val value = try {
                readNbt(it.groupValues[1], it.groupValues[2])
            } catch (_: IllegalStateException) {
                return CommandResult(result = 0, success = false)
            }
            val number = when (value) {
                is ListTag -> value.size
                is ByteArrayTag -> value.value.size
                is IntArrayTag -> value.value.size
                is LongArrayTag -> value.value.size
                is StringTag -> value.value.length
                else -> (value.value as Number).toDouble().let { floor(it).toInt() }
            }
            return CommandResult(result = number, success = true)
        }
        error("Unsupported data command: $command")
    }

    private fun blockSource(coordinates: List<String>, context: ExecContext): String? {
        val at = position(coordinates, context)
        val x = floor(at.x).toInt()
        val y = floor(at.y).toInt()
        val z = floor(at.z).toInt()
        val block = (worldInDimension?.invoke(context.dimension, x, y, z)
            ?: if (worldInDimension == null) world(x, y, z) else null) ?: return null
        val source = "block:${context.dimension}:$x,$y,$z"
        storage.getOrPut(source) { block.nbt.mapValues { it.value.copy() }.toMutableMap() }
        return source
    }

    private data class Segment(val name: String?, val index: Int?, val end: Int, val predicate: CompoundTag? = null)

    private fun literalTag(snbt: String): Tag<*> {
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
        return if (source == "mcfpp:system" && frame != null) {
            val index = frame.groupValues[1].toInt()
            check(index in frames.indices) { "Missing stack frame $index" }
            frames[index] to frame.groupValues[2]
        } else storage.getOrPut(source) { mutableMapOf() } to path
    }

    private fun readNbt(source: String, path: String): Tag<*> {
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
            else if (last.predicate != null) (parent as ListTag).value.indices
                .filter { matches(parent[it], last.predicate) }.forEach { parent[it] = value.copy() }
            else writeElement(parent, last.index!!, value)
            return
        }
        check(parts.none { it.index != null || it.predicate != null }) {
            "Cannot write an index without an existing list: $path"
        }
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

    private fun numericTag(type: String, value: Int): Tag<*> = when (type) {
        "byte" -> ByteTag(value.toByte())
        "short" -> ShortTag(value.toShort())
        "long" -> LongTag(value.toLong())
        "double" -> DoubleTag(value.toDouble())
        "float" -> FloatTag(value.toFloat())
        else -> IntTag(value)
    }

    private fun token(text: String): Pair<String, String> {
        val trimmed = text.trimStart()
        var cursor = 0
        var quote: Char? = null
        var escaped = false
        var depth = 0
        while (cursor < trimmed.length) {
            val char = trimmed[cursor]
            if (quote != null) {
                if (char == quote && !escaped) quote = null
                escaped = char == '\\' && !escaped
            } else when (char) {
                '"', '\'' -> { quote = char; escaped = false }
                '[', '{' -> depth++
                ']', '}' -> depth--
                ' ' -> if (depth == 0) break
            }
            cursor++
        }
        check(cursor > 0) { "Expected a token in: $text" }
        return trimmed.substring(0, cursor) to trimmed.substring(cursor).trimStart()
    }

    private fun take(text: String, count: Int): Pair<List<String>, String> {
        val result = arrayListOf<String>()
        var rest = text
        repeat(count) {
            val (value, after) = token(rest)
            result += value
            rest = after
        }
        return result to rest
    }

    private companion object {
        const val NBT_PATH = """(?:"(?:\\.|[^"\\])*"|'(?:\\.|[^'\\])*'|[^\s"'])+"""
    }
}

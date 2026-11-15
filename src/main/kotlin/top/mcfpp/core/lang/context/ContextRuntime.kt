package top.mcfpp.core.lang.context

import top.mcfpp.Project
import top.mcfpp.analysis.StorageAccess
import top.mcfpp.command.Command
import top.mcfpp.command.TargetCapabilities
import top.mcfpp.core.lang.Pos3Var
import top.mcfpp.core.lang.entity.EntityTargetAccess
import top.mcfpp.core.lang.entity.SelectorVar
import top.mcfpp.core.lang.nbt.MCString
import top.mcfpp.lib.EntityTarget
import top.mcfpp.lib.EntitySelector
import top.mcfpp.lib.NBTPath
import top.mcfpp.lib.StorageSource
import top.mcfpp.model.function.Function
import top.mcfpp.model.function.InternalFunction
import top.mcfpp.model.function.NoStackFunction
import top.mcfpp.model.scope.GlobalScope
import top.mcfpp.type.MCFPPPrivateType
import top.mcfpp.util.TempPool
import top.mcfpp.util.LogProcessor

/**
 * Synchronous, compiler-private preservation of command components. A region frame is
 * independent of language call frames: a helper may borrow a handle without moving it.
 * Only commands and NBT addresses survive lowering; source capabilities do not.
 */
internal object ContextRuntime : ContextCommandEncoder {
    const val STORAGE = "mcfpp:context"
    const val ANCHOR_TAG = "mcfpp_context_anchor"
    private const val ABSENT = "@e[tag=mcfpp_context_absent,tag=!mcfpp_context_absent]"

    internal enum class Component { EXECUTOR, POSITION, ROTATION }
    internal data class Key(val component: Component, val provenance: ContextSourceId, val dimension: ContextSourceId? = null,
                            val transform: PositionTransform = PositionTransform())
    private fun positionKey(value: BoundPosition) = Key(Component.POSITION, value.provenance, value.dimension, value.transform)
    private fun positioned(transform: PositionTransform): String = buildString {
        transform.local?.let { values -> append(" positioned "); append(values.joinToString(" ") { "^$it" }) }
        if (transform.world.any { it != 0.0 }) { append(" positioned "); append(transform.world.joinToString(" ") { "~$it" }) }
    }
    internal data class Capture(val key: Key, val name: String)
    private data class Slot(val capture: Capture, val depth: Int) {
        val path get() = "frames[$depth].${capture.name}"
    }

    class Region internal constructor(
        val context: ExecutionContext,
        val ambient: ExecutionContext,
        val parent: Region?
    ) {
        private val captures = LinkedHashMap<Key, Capture>()

        internal fun capture(key: Key): Capture = captures.getOrPut(key) {
            Capture(key, "context_saved_${TempPool.getVarIdentify()}")
        }

        /** Called after the body succeeds, never while a native plan is speculating. */
        fun before(): List<Command> = captures.values.flatMap { capture ->
            val path = "frames[0].${capture.name}"
            val format = macro("data modify storage $STORAGE $path.target set value \"@e[nbt={UUID:\$(uuid)},limit=1]\"", path)
            val initialize = Command("data modify storage $STORAGE $path set value {target:\"$ABSENT\"}")
            if (capture.key.component == Component.EXECUTOR) listOf(
                initialize,
                Command("execute if entity @s run data modify storage $STORAGE $path.uuid set from entity @s UUID"),
                Command("execute if data storage $STORAGE $path.uuid run").build(format)
            ) else {
                val save = function("context_save_position", listOf(
                    Command("tag @s add $ANCHOR_TAG"),
                    // execute summon retains the source rotation; explicitly copy it to the marker.
                    Command("tp @s ~ ~ ~ ~ ~"),
                    Command("data modify storage $STORAGE $path.uuid set from entity @s UUID"),
                    format
                ))
                listOf(initialize, Command("execute${positioned(capture.key.transform)} summon minecraft:marker run function ${save.namespaceID}"))
            }
        }

        fun after(): List<Command> = captures.values.flatMap { capture ->
            val path = "frames[0].${capture.name}"
            buildList {
                if (capture.key.component != Component.EXECUTOR) add(macro("kill \$(target)", path))
                add(Command("data remove storage $STORAGE $path"))
            }
        }
    }

    private val active = ThreadLocal<Region?>()

    fun <T> withRegion(ambient: ExecutionContext, context: ExecutionContext, body: (Region) -> T): T {
        val previous = active.get()
        check(previous == null || previous.context === ambient) { "Context lowering region is not the active parent" }
        val region = Region(context, ambient, previous)
        active.set(region)
        return try { body(region) } finally { active.set(previous) }
    }

    fun pushFrame(): List<Command> = listOf(
        Command("execute unless data storage $STORAGE frames run data modify storage $STORAGE frames set value []"),
        Command("data modify storage $STORAGE frames prepend value {}")
    )

    fun popFrame(): Command = Command("data remove storage $STORAGE frames[0]")

    private fun current(): ExecutionContext = ContextBorrowing.currentContext
        ?: throw ContextAccessException("A bound command operand requires an active context callback")

    private fun macros() {
        if (TargetCapabilities.forVersion(Project.config.version)?.functionMacros != true)
            throw ContextAccessException("Preserving an ancestor context requires a target with function macros")
    }

    private fun saved(key: Key): Slot {
        macros()
        var region = active.get()
        var depth = 0
        fun matches(context: ExecutionContext): Boolean = when (key.component) {
            Component.EXECUTOR -> context.components.executor == key.provenance
            Component.POSITION -> context.components.position == key.provenance && context.components.dimension == key.dimension &&
                (key.transform.localComponents?.let { source -> source.rotation == context.components.rotation &&
                    source.executor == context.components.executor && source.anchor == context.components.anchor } ?: true)
            Component.ROTATION -> context.components.rotation == key.provenance
        }
        while (region != null) {
            if (matches(region.ambient) && !matches(region.context)) {
                if (region.parent == null) break
                return Slot(region.capture(key), depth + 1)
            }
            region = region.parent
            depth++
        }
        throw ContextAccessException("The ancestor command component has no active preservation boundary")
    }

    private fun target(path: String): Command {
        val value = MCString("context_target_${TempPool.getVarIdentify()}").apply {
            nbtPath = address(path).memberIndex("target")
        }
        StorageAccess.publishNbt(value)
        return Command().buildMacro(value, false)
    }

    override fun executor(value: BoundExecutor): Command {
        val context = current()
        value.requireUsableIn(context.scope)
        return if (value.provenance == context.components.executor) Command("@s")
        else target(saved(Key(Component.EXECUTOR, value.provenance)).path)
    }

    override fun position(value: BoundPosition): Command {
        value.requireUsableIn(current().scope)
        if (!value.matches(current())) {
            LogProcessor.error("An ancestor position, including its dimension, requires a context-aware command such as tp")
            return Command("")
        }
        return try { Command(value.coordinates()) } catch (failure: ContextAccessException) {
            LogProcessor.error(failure.message.orEmpty())
            Command("")
        }
    }

    internal fun rotationEntry(value: ExecutionContext): Command {
        value.requireUsableIn(current().scope)
        if (value.components.rotation == current().components.rotation) return Command("execute")
        return Command("execute rotated as").build(target(saved(Key(Component.ROTATION, value.components.rotation)).path))
    }

    /** Restore position/dimension without replacing the caller's rotation or executor. */
    internal fun positionEntry(value: BoundPosition): ContextEntryOperations.Entry {
        value.requireUsableIn(current().scope)
        if (value.matches(current()) && value.transform == PositionTransform())
            return ContextEntryOperations.Entry(Command("execute"), ContextChange(), direct = true)
        macros()
        if (TargetCapabilities.forVersion(Project.config.version)?.executeRelations.isNullOrEmpty())
            throw ContextAccessException("Preserving a context position requires execute summon support")
        val before = arrayListOf<Command>()
        val after = arrayListOf<Command>()
        val positionPath = if (value.matches(current())) {
            val path = "frames[0].context_entry_position_${TempPool.getVarIdentify()}"
            val capture = function("context_entry_position", listOf(
                Command("tag @s add $ANCHOR_TAG"), Command("tp @s ~ ~ ~ ~ ~"),
                Command("data modify storage $STORAGE $path.uuid set from entity @s UUID"),
                macro("data modify storage $STORAGE $path.target set value \"@e[nbt={UUID:\$(uuid)},limit=1]\"", path)))
            before += Command("data modify storage $STORAGE $path set value {}")
            before += Command("execute${positioned(value.transform)} summon minecraft:marker run function ${capture.namespaceID}")
            after += macro("kill \$(target)", path)
            after += Command("data remove storage $STORAGE $path")
            path
        } else saved(positionKey(value)).path
        val path = "frames[0].context_entry_rotation_${TempPool.getVarIdentify()}"
        val capture = function("context_entry_rotation", listOf(
            Command("tag @s add $ANCHOR_TAG"), Command("tp @s ~ ~ ~ ~ ~"),
            Command("data modify storage $STORAGE $path.uuid set from entity @s UUID"),
            macro("data modify storage $STORAGE $path.target set value \"@e[nbt={UUID:\$(uuid)},limit=1]\"", path)))
        return ContextEntryOperations.Entry(
            Command("execute at").build(target(positionPath)).build("rotated as").build(target(path)),
            ContextChange(position = true, dimension = true),
            before + listOf(Command("data modify storage $STORAGE $path set value {}"),
                Command("execute summon minecraft:marker run function ${capture.namespaceID}")),
            after + listOf(macro("kill \$(target)", path), Command("data remove storage $STORAGE $path")))
    }

    /** Compiler anchors never participate in ordinary public entity queries. */
    fun entity(value: EntityTargetAccess): Command {
        val selector = ((value as? SelectorVar)?.entityTargetOrNull() as? EntityTarget.Selector)?.selector
        return if (selector?.selectorType in setOf(EntitySelector.Companion.SelectorType.ALL_ENTITIES, EntitySelector.Companion.SelectorType.NEAREST_ENTITY))
            selector!!.clone().tag(ANCHOR_TAG, true).toCommandPart()
        else value.toEntityTargetCommand()
    }

    override fun operation(operands: List<ContextCommandOperand>, changesExecutor: Boolean, build: (List<Command>) -> Command): Command {
        val positions = operands.filterIsInstance<ContextCommandOperand.Position>()
        positions.forEach { it.value.requireUsableIn(current().scope) }
        val domains = positions.map { positionKey(it.value) }.distinct()
        if (domains.size > 1) throw ContextAccessException("One command cannot combine incompatible bound position/dimension contexts")
        val boundPosition = positions.firstOrNull()?.value
        val changed = boundPosition != null && (!boundPosition.matches(current()) || boundPosition.transform != PositionTransform())
        val freezeExecutor = changesExecutor && operands.any(::dependsOnExecutor)
        if (!changed && !freezeExecutor) return build(operands.map { operand -> when (operand) {
            is ContextCommandOperand.Executor -> executor(operand.value)
            is ContextCommandOperand.Position -> position(operand.value)
            is ContextCommandOperand.Entity -> entity(operand.value)
            is ContextCommandOperand.Coordinates -> operand.value.toCommandPart()
        } })

        macros()
        if (TargetCapabilities.forVersion(Project.config.version)?.functionReturnRun != true)
            throw ContextAccessException("Preserving command operands requires a target with function return support")
        if (changed && operands.any { it is ContextCommandOperand.Coordinates && it.value is Pos3Var })
            throw ContextAccessException("One command cannot combine a bound position with another coordinate context")
        val savedPosition = if (changed && !boundPosition!!.matches(current())) saved(positionKey(boundPosition)).path else null
        val name = "context_operation_${TempPool.getVarIdentify()}"
        val path = "operations[0]"
        val wrapper = function("context_operation", emptyList())
        wrapper.runInFunction {
            Function.addCommand(Command("execute unless data storage $STORAGE operations run data modify storage $STORAGE operations set value []"))
            Function.addCommand(Command("data modify storage $STORAGE operations prepend value {result:0,success:0b}"))
            // Sequence tags identify the whole original target set, retaining a single core command.
            Function.addCommand(Command("execute store result storage $STORAGE $path.sequence int 1 run scoreboard players add #mcfpp_context mcfpp_temp 1"))
            val cleanups = ArrayList<Command>()
            val parts = operands.mapIndexed { index, operand -> when (operand) {
                is ContextCommandOperand.Executor -> if (freezeExecutor)
                    freeze(executor(operand.value), index, name, path, cleanups) else executor(operand.value)
                is ContextCommandOperand.Position -> Command("~ ~ ~")
                is ContextCommandOperand.Coordinates -> operand.value.toCommandPart()
                is ContextCommandOperand.Entity -> {
                    // This happens before any internal execute at, in original argument order.
                    freeze(entity(operand.value), index, name, path, cleanups)
                }
            } }
            val oldPosition = savedPosition ?: if (changed) {
                val position = "$path.position"
                val savePosition = function("context_call_position", listOf(
                    Command("tag @s add $ANCHOR_TAG"), Command("tp @s ~ ~ ~ ~ ~"),
                    Command("data modify storage $STORAGE $position.uuid set from entity @s UUID"),
                    macro("data modify storage $STORAGE $position.target set value \"@e[nbt={UUID:\$(uuid)},limit=1]\"", position)))
                Function.addCommand(Command("data modify storage $STORAGE $position set value {}"))
                Function.addCommand(Command("execute${positioned(boundPosition!!.transform)} summon minecraft:marker run function ${savePosition.namespaceID}"))
                cleanups += macro("kill \$(target)", position)
                position
            } else null
            val core = Command("execute store result storage $STORAGE $path.result int 1 store success storage $STORAGE $path.success byte 1")
            if (oldPosition != null) {
                val rotation = "$path.rotation"
                Function.addCommand(Command("data modify storage $STORAGE $rotation set value {}"))
                val saveRotation = function("context_call_rotation", listOf(
                    Command("tag @s add $ANCHOR_TAG"),
                    Command("tp @s ~ ~ ~ ~ ~"),
                    Command("data modify storage $STORAGE $rotation.uuid set from entity @s UUID"),
                    macro("data modify storage $STORAGE $rotation.target set value \"@e[nbt={UUID:\$(uuid)},limit=1]\"", rotation)
                ))
                Function.addCommand(Command("execute summon minecraft:marker run function ${saveRotation.namespaceID}"))
                core.build("at").build(target(oldPosition)).build("rotated as").build(target(rotation))
                cleanups += macro("kill \$(target)", rotation)
            }
            core.build("run").build(build(parts))
            Function.addCommands(core.buildMacroFunction())
            Function.addCommands(cleanups.toTypedArray())
            // No user command runs between these scalar reads and return; only capabilities
            // and per-operation tags/anchors require the isolated runtime stack.
            Function.addCommand(Command("execute store result score #mcfpp_context_result mcfpp_temp run data get storage $STORAGE $path.result 1"))
            Function.addCommand(Command("execute store result score #mcfpp_context_success mcfpp_temp run data get storage $STORAGE $path.success 1"))
            Function.addCommand(Command("data remove storage $STORAGE operations[0]"))
            Function.addCommand(Command("execute if score #mcfpp_context_success mcfpp_temp matches 0 run return fail"))
            Function.addCommand(Command("return run scoreboard players get #mcfpp_context_result mcfpp_temp"))
        }
        return Command("function ${wrapper.namespaceID}")
    }

    private fun dependsOnExecutor(operand: ContextCommandOperand): Boolean = when (operand) {
        is ContextCommandOperand.Executor -> {
            operand.value.requireUsableIn(current().scope)
            operand.value.provenance == current().components.executor
        }
        is ContextCommandOperand.Entity -> when (val target = (operand.value as? SelectorVar)?.entityTargetOrNull()) {
            is EntityTarget.Selector -> target.selector.selectorType == EntitySelector.Companion.SelectorType.SELF
            is EntityTarget.PlayerName, is EntityTarget.UUIDText -> false
            null -> true
        }
        else -> false
    }

    private fun freeze(value: Command, index: Int, name: String, path: String, cleanups: MutableList<Command>): Command {
        Function.addCommand(macro("data modify storage $STORAGE $path.tag_$index set value \"mcfpp_ctx_${name}_${index}_\$(sequence)\"", path))
        val tag = stringAt("$path.tag_$index")
        Function.addCommands(Command("tag").build(value).build("add").buildMacro(tag).buildMacroFunction())
        cleanups.addAll(Command("tag @e[tag=").buildMacro(tag, false).build("] remove", false).buildMacro(tag).buildMacroFunction())
        return Command("@e[tag=").buildMacro(tag, false).build("]", false)
    }

    private fun stringAt(path: String): MCString = MCString("context_string_${TempPool.getVarIdentify()}").apply {
        nbtPath = address(path)
        StorageAccess.publishNbt(this)
    }

    private fun address(path: String): NBTPath {
        val list = path.substringBefore('[')
        val index = path.substringAfter('[').substringBefore(']').toInt()
        var address = NBTPath(StorageSource(STORAGE)).memberIndex(list).intIndex(index)
        path.substringAfter("].").split('.').forEach { address = address.memberIndex(it) }
        return address
    }

    private fun macro(body: String, path: String): Command {
        val name = TempPool.getFunctionIdentify("context_macro")
        Project.macroFunction[name] = "$$body"
        return Command("function mcfpp:dynamic/$name with storage $STORAGE $path")
    }

    private fun function(name: String, commands: List<Command>): Function {
        val creator = Function.currFunction
        // Expression/native wrappers are lexical sandboxes, not runtime callers. Keep
        // their borrowed scopes out of the registered graph, and mark this raw helper
        // reachable so Project does not add an entrance's language stack protocol.
        var caller = creator
        while (caller is NoStackFunction || caller is InternalFunction)
            caller = caller.parent.firstOrNull() ?: break
        return Function(TempPool.getFunctionIdentify(name), creator.namespace, null).apply {
            actualCallBody = true
            bodyCompiled = true
            returnType = MCFPPPrivateType.Void
            declarationFile = creator.declarationFile
            parent.add(caller)
            caller.child.add(this)
            GlobalScope.getOrCreateNamespace(namespace).scope.addFunction(this, false)
            runInFunction { Function.addCommands(commands.toTypedArray()) }
        }
    }
}

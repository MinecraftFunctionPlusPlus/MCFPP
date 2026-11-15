package top.mcfpp.core.lang.context

import top.mcfpp.Project
import top.mcfpp.analysis.CompilerValue
import top.mcfpp.analysis.StorageAccess
import top.mcfpp.command.Command
import top.mcfpp.command.TargetCapabilities
import top.mcfpp.core.lang.Pos3Var
import top.mcfpp.core.lang.Var
import top.mcfpp.core.lang.entity.EntityTargetAccess
import top.mcfpp.core.lang.context.ContextCallbacks.Operation

/** One source of truth for the source-language entry's component writes and command prefix. */
internal object ContextEntryOperations {
    data class Entry(val prefix: Command, val change: ContextChange,
                     val before: List<Command> = emptyList(), val after: List<Command> = emptyList(),
                     val direct: Boolean = false)

    fun prepare(operation: Operation, entity: EntityTargetAccess?, operands: List<Var<*>>, parent: ExecutionContext): Entry {
        fun entityPart() = ContextRuntime.entity(entity ?: throw ContextAccessException("This context entry requires an entity"))
        fun first() = operands.firstOrNull() ?: throw ContextAccessException("Missing context operand")
        fun prefix(clause: String, part: Command) = Command("execute $clause").build(part)
        fun spatial(change: ContextChange, command: Command) = Entry(command, change)
        return when (operation) {
            Operation.CURRENT -> Entry(Command("execute"), ContextChange(), direct = true)
            Operation.ENTITY -> spatial(ContextChange(executor = true, executorFacts = entity?.entityFacts), prefix("as", entityPart()))
            Operation.ENTITY_AT -> spatial(ContextChange(executor = true, executorFacts = entity?.entityFacts,
                position = true, rotation = true, dimension = true), prefix("as", entityPart()).build("at @s"))
            Operation.POSITION -> if (entity != null)
                spatial(ContextChange(position = true, rotation = true, dimension = true), prefix("at", entityPart()))
            else when (val position = first()) {
                is BoundPositionVar -> ContextRuntime.positionEntry(position.requireBinding())
                is Pos3Var -> spatial(ContextChange(position = true, anchor = ContextAnchor.FEET), prefix("positioned", position.toCommandPart()))
                else -> throw ContextAccessException("withPosCtx requires pos3 or ContextPosition")
            }
            Operation.POSITIONED -> spatial(ContextChange(position = true), prefix("positioned as", entityPart()))
            Operation.ROTATION -> {
                val value = operands.firstOrNull()
                if (entity != null) spatial(ContextChange(rotation = true), prefix("rotated as", entityPart()))
                else if (value is BoundComponentVar) {
                    if (value.component != "rotation") throw ContextAccessException("Expected ContextRotation")
                    val source = value.requireContext()
                    if (source.components.rotation == parent.components.rotation)
                        Entry(Command("execute"), ContextChange(), direct = true)
                    else spatial(ContextChange(rotation = true), ContextRuntime.rotationEntry(source))
                } else spatial(ContextChange(rotation = true), prefix("rotated", rotation(first())))
            }
            Operation.FACING -> {
                val part = if (entity != null) Command("entity").build(entityPart()).build(option(first(), "Anchor", listOf("feet", "eyes")))
                else (first() as? Pos3Var)?.toCommandPart() ?: throw ContextAccessException("withFacingCtx requires pos3")
                spatial(ContextChange(rotation = true), prefix("facing", part))
            }
            Operation.DIMENSION -> {
                val value = first()
                if (value is BoundComponentVar && value.component == "dimension" && value.requireContext().components.dimension == parent.components.dimension)
                    return Entry(Command("execute"), ContextChange(), direct = true)
                val resource = if (value is BoundComponentVar) {
                    if (value.component != "dimension") throw ContextAccessException("Expected ContextDimension")
                    val id = value.requireContext().dimensionResource
                        ?: throw ContextAccessException("An opaque inherited dimension cannot be reconstructed by execute in; enter its ContextPosition instead")
                    ResourceOperand(Command(id), id)
                } else resource(value, "Dimension")
                spatial(ContextChange(position = true, dimension = true, dimensionResource = resource.id), prefix("in", resource.command))
            }
            Operation.ALIGN -> spatial(ContextChange(position = true), Command("execute align ${option(first(), "Axes", listOf("x", "y", "z", "xy", "xz", "yz", "xyz"))}"))
            Operation.ANCHOR -> {
                val value = first()
                if (value is BoundComponentVar && value.component == "anchor" && value.requireContext().components.anchor == parent.components.anchor)
                    return Entry(Command("execute"), ContextChange(), direct = true)
                val mode = if (value is BoundComponentVar) {
                    if (value.component != "anchor") throw ContextAccessException("Expected ContextAnchor")
                    value.requireContext().anchorMode ?: throw ContextAccessException("An opaque inherited anchor cannot be reconstructed")
                } else ContextAnchor.valueOf(option(value, "Anchor", listOf("feet", "eyes")).uppercase())
                spatial(ContextChange(anchor = mode), Command("execute anchored ${mode.name.lowercase()}"))
            }
            Operation.HEIGHTMAP -> {
                modern("positioned over")
                spatial(ContextChange(position = true), Command("execute positioned over ${option(first(), "Heightmap", listOf("world_surface", "motion_blocking", "motion_blocking_no_leaves", "ocean_floor"))}"))
            }
            Operation.SUMMON -> {
                modern("summon")
                val resource = resource(first(), "EntityType")
                if (resource.id in setOf("minecraft:player", "minecraft:fishing_bobber")) throw ContextAccessException("Cannot summon '${resource.id}'")
                spatial(ContextChange(executor = true), prefix("summon", resource.command))
            }
            Operation.RELATION -> error("Relations use their bound origin")
        }
    }

    private fun modern(operation: String) {
        if (TargetCapabilities.forVersion(Project.config.version)?.executeRelations.isNullOrEmpty())
            throw ContextAccessException("Target '${Project.config.version}' does not support execute $operation")
    }

    internal fun payload(value: CompilerValue?): CompilerValue? = if (value is CompilerValue.Typed) payload(value.payload) else value
    private fun fields(value: Var<*>): Map<String, CompilerValue> =
        (payload(StorageAccess.snapshot(value)) as? CompilerValue.Record)?.fields
            ?: throw ContextAccessException("'${value.type}' requires a complete immutable context operand")
    private fun option(value: Var<*>, expected: String, names: List<String>): String {
        if (value.type.simpleName.substringAfterLast(':') != expected) throw ContextAccessException("Expected $expected, found '${value.type}'")
        val ordinal = (payload(fields(value)["ordinal"]) as? CompilerValue.Integral)?.value?.toInt()
        return names.getOrNull(ordinal ?: -1) ?: throw ContextAccessException("Invalid $expected value")
    }
    private data class ResourceOperand(val command: Command, val id: String?)
    private fun resource(value: Var<*>, expected: String): ResourceOperand {
        if (value.type.simpleName.substringAfterLast(':') != expected) throw ContextAccessException("Expected $expected, found '${value.type}'")
        val snapshot = payload(StorageAccess.snapshot(value)) as? CompilerValue.Record
        val member = value.getMemberVar("id", top.mcfpp.model.Member.AccessModifier.PUBLIC).first
        val idValue = snapshot?.fields?.get("id") ?: member?.let { StorageAccess.snapshot(it) }
        val id = (payload(idValue) as? CompilerValue.Text)?.value
        if (id != null) {
            if (!id.matches(Regex("[a-z0-9_.-]+:[a-z0-9_./-]+"))) throw ContextAccessException("Invalid resource id '$id'")
            return ResourceOperand(Command(id), id)
        }
        if (member == null || !value.type.hasRuntimeRepresentation) throw ContextAccessException("$expected requires a resource id")
        val runtime = top.mcfpp.core.lang.nbt.MCString("context_resource").apply {
            nbtPath = StorageAccess.ensure(value).path.memberIndex("id")
        }
        StorageAccess.publishNbt(runtime)
        return ResourceOperand(Command().buildMacro(runtime, false), null)
    }
    private fun rotation(value: Var<*>): Command {
        if (value.type.simpleName.substringAfterLast(':') != "Rotation") throw ContextAccessException("Expected Rotation or ContextRotation")
        val fields = fields(value)
        fun number(name: String): Double = when (val v = payload(fields[name])) {
            is CompilerValue.FloatBits -> Float.fromBits(v.bits).toDouble()
            is CompilerValue.DoubleBits -> Double.fromBits(v.bits)
            is CompilerValue.Integral -> v.value.toDouble()
            else -> throw ContextAccessException("Rotation.$name must be a known angle")
        }.also { if (!it.isFinite()) throw ContextAccessException("Rotation angle must be finite") }
        fun angle(name: String, relative: String) = (if ((payload(fields[relative]) as? CompilerValue.Bool)?.value == true) "~" else "") + number(name)
        return Command("${angle("yaw", "relativeYaw")} ${angle("pitch", "relativePitch")}")
    }
}

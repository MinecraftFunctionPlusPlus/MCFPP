package top.mcfpp.command

import top.mcfpp.core.lang.Var
import top.mcfpp.core.lang.ref.ReferenceKind
import top.mcfpp.core.lang.ref.ReferenceValues
import top.mcfpp.core.lang.ref.ReferencePathShape
import top.mcfpp.analysis.StorageAccess
import top.mcfpp.lib.NBTPath
import top.mcfpp.type.MCFPPBaseType

/** Command construction shared by direct accesses and descriptor helpers. */
internal object ReferenceCommands {
    private fun path(reference: Var<*>, shape: List<ReferencePathShape>): Command {
        val result = field(reference, "path")
        shape.forEachIndexed { index, part -> when (part) {
            is ReferencePathShape.Field -> result.build("." + StorageAccess.quotedKey(part.name), false)
            ReferencePathShape.Index -> result.build("[", false).build(ReferenceValues.pathIndex(reference, index).toCommandPart(), false).build("]", false)
        } }
        return result
    }
    private fun field(reference: Var<*>, name: String): Command =
        ReferenceValues.field(reference, name, if (name in setOf("x", "y", "z")) MCFPPBaseType.Int else MCFPPBaseType.String)
            .toCommandPart()

    private fun address(reference: Var<*>, kind: ReferenceKind, target: Command?): Command = when (kind) {
        ReferenceKind.NBT_STORAGE -> Command("storage").build(field(reference, "storage"))
        ReferenceKind.NBT_ENTITY -> Command("entity").build(requireNotNull(target))
        ReferenceKind.NBT_BLOCK -> Command("block").build(field(reference, "x")).build(field(reference, "y")).build(field(reference, "z"))
        else -> error("A score has no NBT source")
    }

    fun read(reference: Var<*>, kind: ReferenceKind, destination: NBTPath, success: Command,
             target: Command? = null, pathShape: List<ReferencePathShape> = emptyList()): Command {
        val operation = if (kind == ReferenceKind.SCORE_HOLDER || kind == ReferenceKind.SCORE_ENTITY) {
            success.build("execute store result").build(destination.toCommandPart()).build("int 1 run scoreboard players get")
                .build(target ?: field(reference, "holder")).build(field(reference, "objective"))
        } else success.build("data modify").build(destination.toCommandPart()).build("set from")
            .build(address(reference, kind, target)).build(path(reference, pathShape))
        return when (kind) {
            ReferenceKind.SCORE_ENTITY -> Command("execute if entity").build(requireNotNull(target)).build("run").build(operation)
            ReferenceKind.NBT_BLOCK -> Command("execute in").build(field(reference, "dimension")).build("run").build(operation)
            else -> operation
        }
    }

    fun write(reference: Var<*>, kind: ReferenceKind, source: NBTPath, target: Command? = null,
              multiple: Boolean = false, pathShape: List<ReferencePathShape> = emptyList()): Command {
        if (kind == ReferenceKind.SCORE_HOLDER || kind == ReferenceKind.SCORE_ENTITY)
            return Command("execute store result score").build(target ?: field(reference, "holder"))
                .build(field(reference, "objective")).build("run data get").build(source.toCommandPart()).build("1")
        val operation = Command("data modify").build(address(reference, kind, if (multiple) Command("@s") else target))
            .build(path(reference, pathShape)).build("set from").build(source.toCommandPart())
        return when {
            kind == ReferenceKind.NBT_ENTITY && multiple -> Command("execute as").build(requireNotNull(target)).build("run").build(operation)
            kind == ReferenceKind.NBT_BLOCK -> Command("execute in").build(field(reference, "dimension")).build("run").build(operation)
            else -> operation
        }
    }
}

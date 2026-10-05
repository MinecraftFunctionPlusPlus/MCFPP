package top.mcfpp.backend

import top.mcfpp.command.Command
import top.mcfpp.command.Commands
import top.mcfpp.core.lang.MCInt
import top.mcfpp.lib.NBTPath
import top.mcfpp.lib.SbObject
import top.mcfpp.model.function.Function
import top.mcfpp.nbt.tags.collection.ListTag

/** Runtime entry-list operations. Callers freeze inputs and commit the resulting list. */
object MapCommands {
    private fun score() = MCInt().apply { sbObject = SbObject.MCFPP_TEMP; hasAssigned = true; isTemp = true }
    fun key(value: MCInt) = "${value.name} ${value.sbObject}"

    fun overlay(workspace: NBTPath, remove: Boolean, emit: (Command) -> Unit) {
        val working = workspace.memberIndex("remaining")
        val incoming = workspace.memberIndex("incoming")
        val output = workspace.memberIndex("output")
        val probe = workspace.memberIndex("probe")
        val remaining = score()
        val changed = score()
        val found = score()
        emit(Commands.dataSetFrom(working, workspace.memberIndex("source")))
        emit(Commands.dataSetValue(output, ListTag()))
        emit(Command("scoreboard players set ${key(found)} 0"))
        emit(Command("execute store result score ${key(remaining)} run data get").build(working.toCommandPart()))
        val loop = Commands.tempFunction("map_overlay", Function.currFunction) { function ->
            fun add(command: Command) = Function.addCommands(command.buildMacroFunction())
            add(Commands.dataSetFrom(probe, working.intIndex(0).memberIndex("key")))
            add(Command("execute store success score ${key(changed)} run")
                .build(Commands.dataSetFrom(probe, incoming.memberIndex("key"))))
            if (!remove) add(Command("execute if score ${key(changed)} matches 0 run")
                .build(Commands.dataSetFrom(working.intIndex(0).memberIndex("value"), incoming.memberIndex("value"))))
            add(Command("execute if score ${key(changed)} matches 0 run scoreboard players set ${key(found)} 1"))
            val append = Commands.dataAppendFrom(output, working.intIndex(0))
            add(if (remove) Command("execute if score ${key(changed)} matches 1 run").build(append) else append)
            add(Command("data remove").build(working.intIndex(0).toCommandPart()))
            add(Command("scoreboard players remove ${key(remaining)} 1"))
            add(Command("execute if score ${key(remaining)} matches 1.. run").build(Commands.function(function)))
        }
        emit(Command("execute if score ${key(remaining)} matches 1.. run").build(loop.first))
        if (!remove) emit(Command("execute if score ${key(found)} matches 0 run").build(Commands.dataAppendFrom(output, incoming)))
    }

    fun contains(workspace: NBTPath, emit: (Command) -> Unit): MCInt {
        val working = workspace.memberIndex("remaining")
        val needle = workspace.memberIndex("needle")
        val probe = workspace.memberIndex("probe")
        val remaining = score()
        val changed = score()
        val result = score()
        emit(Commands.dataSetFrom(working, workspace.memberIndex("source")))
        emit(Command("scoreboard players set ${key(result)} 0"))
        emit(Command("execute store result score ${key(remaining)} run data get").build(working.toCommandPart()))
        val loop = Commands.tempFunction("map_find", Function.currFunction) { function ->
            fun add(command: Command) = Function.addCommands(command.buildMacroFunction())
            add(Commands.dataSetFrom(probe, working.intIndex(0).memberIndex("key")))
            add(Command("execute store success score ${key(changed)} run").build(Commands.dataSetFrom(probe, needle)))
            add(Command("execute if score ${key(changed)} matches 0 run scoreboard players set ${key(result)} 1"))
            add(Command("data remove").build(working.intIndex(0).toCommandPart()))
            add(Command("scoreboard players remove ${key(remaining)} 1"))
            add(Command("execute if score ${key(remaining)} matches 1.. run")
                .build(Command("execute if score ${key(result)} matches 0 run").build(Commands.function(function))))
        }
        emit(Command("execute if score ${key(remaining)} matches 1.. run").build(loop.first))
        return result
    }

    fun merge(workspace: NBTPath, emit: (Command) -> Unit) {
        val incoming = workspace.memberIndex("rows")
        val overlay = workspace.memberIndex("overlay")
        val output = workspace.memberIndex("output")
        val remaining = score()
        emit(Commands.dataSetFrom(output, workspace.memberIndex("source")))
        emit(Commands.dataSetValue(overlay, top.mcfpp.nbt.tags.CompoundTag()))
        emit(Command("execute store result score ${key(remaining)} run data get").build(incoming.toCommandPart()))
        val loop = Commands.tempFunction("map_merge", Function.currFunction) { function ->
            fun add(command: Command) = Function.addCommands(command.buildMacroFunction())
            add(Commands.dataSetFrom(overlay.memberIndex("source"), output))
            add(Commands.dataSetFrom(overlay.memberIndex("incoming"), incoming.intIndex(0)))
            overlay(overlay, false, ::add)
            add(Commands.dataSetFrom(output, overlay.memberIndex("output")))
            add(Command("data remove").build(incoming.intIndex(0).toCommandPart()))
            add(Command("scoreboard players remove ${key(remaining)} 1"))
            add(Command("execute if score ${key(remaining)} matches 1.. run").build(Commands.function(function)))
        }
        emit(Command("execute if score ${key(remaining)} matches 1.. run").build(loop.first))
    }
}

package top.mcfpp.backend

import top.mcfpp.command.Command
import top.mcfpp.command.Commands
import top.mcfpp.core.lang.MCInt
import top.mcfpp.lib.NBTPath
import top.mcfpp.lib.SbObject
import top.mcfpp.model.function.Function
import top.mcfpp.nbt.tags.collection.ListTag

/** Shared command backend. The workspace owns frozen source/needle payloads and per-call scratch data. */
object ListSearch {
    private fun score() = MCInt().apply { sbObject = SbObject.MCFPP_TEMP; hasAssigned = true; isDynamic = true; isTemp = true }
    fun key(value: MCInt) = "${value.name} ${value.sbObject}"
    private fun write(command: Command) = Function.addCommand(command)

    fun find(workspace: NBTPath, eligible: List<Int>?, last: Boolean, emit: (Command) -> Unit): MCInt {
        val list = workspace.memberIndex("remaining")
        val needle = workspace.memberIndex("needle")
        val probe = workspace.memberIndex("probe")
        val result = score()
        val cursor = score()
        val size = score()
        val changed = score()
        emit(Commands.dataSetFrom(list, workspace.memberIndex("source")))
        emit(Command("scoreboard players set ${key(result)} -1"))
        emit(Command("scoreboard players set ${key(cursor)} 0"))
        emit(Command("execute store result score ${key(size)} run data get").build(list.toCommandPart()))
        val loop = Commands.tempFunction("list_find", Function.currFunction) { function ->
            write(Commands.dataSetFrom(probe, list.intIndex(0)))
            write(Command("execute store success score ${key(changed)} run").build(Commands.dataSetFrom(probe, needle)))
            val match = Command("execute if score ${key(changed)} matches 0 run scoreboard players operation ${key(result)} = ${key(cursor)}")
            if (eligible == null) write(match) else eligible.forEach { index ->
                write(Command("execute if score ${key(cursor)} matches $index run").build(match))
            }
            write(Command("data remove").build(list.intIndex(0).toCommandPart()))
            write(Command("scoreboard players add ${key(cursor)} 1"))
            val recurse = Command("execute if score ${key(cursor)} < ${key(size)} run").build(Commands.function(function))
            write(if (last) recurse else Command("execute if score ${key(result)} matches -1 run").build(recurse))
        }
        emit(Command("execute if score ${key(size)} matches 1.. run").build(loop.first))
        return result
    }

    /** Build output only when a match exists; the caller writes it back to its captured receiver address. */
    fun remove(workspace: NBTPath, index: MCInt, emit: (Command) -> Unit) {
        val list = workspace.memberIndex("remaining")
        val output = workspace.memberIndex("output")
        val cursor = score()
        val size = score()
        val rebuild = Commands.tempFunction("list_remove", Function.currFunction) {
            write(Commands.dataSetFrom(list, workspace.memberIndex("source")))
            write(Commands.dataSetValue(output, ListTag()))
            write(Command("scoreboard players set ${key(cursor)} 0"))
            write(Command("execute store result score ${key(size)} run data get").build(list.toCommandPart()))
            val loop = Commands.tempFunction("list_keep", Function.currFunction) { function ->
                write(Command("execute unless score ${key(cursor)} = ${key(index)} run").build(Commands.dataAppendFrom(output, list.intIndex(0))))
                write(Command("data remove").build(list.intIndex(0).toCommandPart()))
                write(Command("scoreboard players add ${key(cursor)} 1"))
                write(Command("execute if score ${key(cursor)} < ${key(size)} run").build(Commands.function(function)))
            }
            write(Command("execute if score ${key(size)} matches 1.. run").build(loop.first))
        }
        emit(Command("execute if score ${key(index)} matches 0.. run").build(rebuild.first))
    }
}

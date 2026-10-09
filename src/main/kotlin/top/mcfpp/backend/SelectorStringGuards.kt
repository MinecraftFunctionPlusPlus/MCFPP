package top.mcfpp.backend

import top.mcfpp.command.Command
import top.mcfpp.command.Commands
import top.mcfpp.core.lang.MCInt
import top.mcfpp.lib.NBTPath
import top.mcfpp.model.function.Function
import top.mcfpp.nbt.tags.CompoundTag
import top.mcfpp.nbt.tags.primitive.StringTag
import top.mcfpp.util.TempPool

/** Validate captured selector strings without interpolating their characters into commands. */
internal object SelectorStringGuards {
    fun prepare(source: NBTPath, word: Boolean, owner: Function): Pair<Array<Command>, String> {
        val valid = MCInt().apply { isTemp = true }
        val index = MCInt().apply { isTemp = true }
        val end = MCInt().apply { isTemp = true }
        val length = MCInt().apply { isTemp = true }
        val characterValid = MCInt().apply { isTemp = true }
        val state = NBTPath.stack.intIndex(0).memberIndex(TempPool.getVarIdentify())
        val character = state.memberIndex("character")
        val initialization = Commands.fakeFunction(owner) {
            Function.addCommand(Commands.dataSetValue(state, CompoundTag()))
            Function.addCommand("scoreboard players set ${valid.name} ${valid.sbObject} 1")
            Function.addCommand("scoreboard players set ${index.name} ${index.sbObject} 0")
            Function.addCommand(Command("execute store result score ${length.name} ${length.sbObject} run data get").build(source.toCommandPart()))
            for (value in listOf(valid, index, length)) top.mcfpp.analysis.StorageAccess.publishScore(value,
                top.mcfpp.analysis.StorageLayout.Scoreboard(value.name, value.sbObject.toString()))
        }
        val body = Commands.tempFunction("selector_string", owner) { scan ->
            Function.addCommand(Commands.sbPlayerOperation(end, "=", index))
            Function.addCommand("scoreboard players add ${end.name} ${end.sbObject} 1")
            top.mcfpp.analysis.StorageAccess.publishScore(end, top.mcfpp.analysis.StorageLayout.Scoreboard(end.name, end.sbObject.toString()))
            Function.addCommands(Command("data modify").build(character.toCommandPart()).build("set string")
                .build(source.toCommandPart()).buildMacro(index).buildMacro(end).buildMacroFunction())
            if (word) {
                Function.addCommand("scoreboard players set ${characterValid.name} ${characterValid.sbObject} 0")
                for (letter in "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789_.+-") {
                    val pattern = NbtEncoding.snbt(CompoundTag("character" to StringTag(letter.toString())))
                    Function.addCommand(Command("execute if data").build(state.toCommandPart()).build(pattern, false)
                        .build("run scoreboard players set ${characterValid.name} ${characterValid.sbObject} 1"))
                }
                Function.addCommand("execute if score ${characterValid.name} ${characterValid.sbObject} matches 0 run scoreboard players set ${valid.name} ${valid.sbObject} 0")
            } else {
                for (letter in listOf("\"", "\\", "\r", "\n")) {
                    val pattern = NbtEncoding.snbt(CompoundTag("character" to StringTag(letter)))
                    Function.addCommand(Command("execute if data").build(state.toCommandPart()).build(pattern, false)
                        .build("run scoreboard players set ${valid.name} ${valid.sbObject} 0"))
                }
            }
            Function.addCommand("scoreboard players add ${index.name} ${index.sbObject} 1")
            Function.addCommand("execute if score ${index.name} ${index.sbObject} < ${length.name} ${length.sbObject} run function ${scan.namespaceID}")
        }
        val commands = Commands.fakeFunction(owner) {
            Function.addCommand(Command("execute if score ${length.name} ${length.sbObject} matches 1.. run").build(body.first))
            Function.addCommand("execute if score ${valid.name} ${valid.sbObject} matches 0 run tellraw @a {\"type\":\"text\",\"text\":\"Invalid runtime selector ${if (word) "tag/team word" else "name"}\"}")
        }
        return (initialization + commands) to "if score ${valid.name} ${valid.sbObject} matches 1"
    }
}

package top.mcfpp.backend

import top.mcfpp.command.Command
import top.mcfpp.command.Commands
import top.mcfpp.core.lang.MCInt
import top.mcfpp.lib.NBTPath
import top.mcfpp.lib.EntityTargetText
import top.mcfpp.lib.SelectorExpression
import top.mcfpp.model.function.Function
import top.mcfpp.nbt.tags.CompoundTag
import top.mcfpp.nbt.tags.primitive.StringTag
import top.mcfpp.util.TempPool

/** Validate captured selector strings without interpolating their characters into commands. */
internal object SelectorStringGuards {
    fun prepare(source: NBTPath, word: Boolean, owner: Function): Pair<Array<Command>, String> =
        prepare(source, word, owner, null)

    fun prepareEntityTarget(source: NBTPath, kind: SelectorExpression.DirectKind, owner: Function): Pair<Array<Command>, String> =
        prepare(source, true, owner, kind)

    private fun prepare(source: NBTPath, word: Boolean, owner: Function,
                        targetKind: SelectorExpression.DirectKind?): Pair<Array<Command>, String> {
        val uuid = targetKind == SelectorExpression.DirectKind.UUID_TEXT
        val allowed = when (targetKind) {
            SelectorExpression.DirectKind.PLAYER_NAME -> EntityTargetText.PLAYER_CHARACTERS
            SelectorExpression.DirectKind.UUID_TEXT -> EntityTargetText.HEX_CHARACTERS
            null -> if (word) "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789_.+-" else null
        }
        val lengthRange = when (targetKind) {
            SelectorExpression.DirectKind.PLAYER_NAME -> "1..16"
            SelectorExpression.DirectKind.UUID_TEXT -> "9..36"
            null -> null
        }
        val diagnostic = when (targetKind) {
            SelectorExpression.DirectKind.PLAYER_NAME -> "Invalid runtime entityByName target; ${EntityTargetText.description(targetKind)}"
            SelectorExpression.DirectKind.UUID_TEXT -> "Invalid runtime entityByUUID target; ${EntityTargetText.description(targetKind)}"
            null -> "Invalid runtime selector ${if (word) "tag/team word" else "name"}"
        }
        val valid = MCInt().apply { isTemp = true }
        val index = MCInt().apply { isTemp = true }
        val end = MCInt().apply { isTemp = true }
        val length = MCInt().apply { isTemp = true }
        val characterValid = MCInt().apply { isTemp = true }
        val group = if (uuid) MCInt().apply { isTemp = true } else null
        val groupDigits = if (uuid) MCInt().apply { isTemp = true } else null
        val state = NBTPath.stack.intIndex(0).memberIndex(TempPool.getVarIdentify())
        val character = state.memberIndex("character")
        val initialization = Commands.fakeFunction(owner) {
            Function.addCommand(Commands.dataSetValue(state, CompoundTag()))
            Function.addCommand("scoreboard players set ${valid.name} ${valid.sbObject} 1")
            Function.addCommand("scoreboard players set ${index.name} ${index.sbObject} 0")
            Function.addCommand("scoreboard players set ${length.name} ${length.sbObject} 0")
            Function.addCommand(Command("execute store result score ${length.name} ${length.sbObject} run data get").build(source.toCommandPart()))
            if (lengthRange != null) Function.addCommand("execute unless score ${length.name} ${length.sbObject} matches $lengthRange run scoreboard players set ${valid.name} ${valid.sbObject} 0")
            for (value in listOfNotNull(group, groupDigits)) Function.addCommand("scoreboard players set ${value.name} ${value.sbObject} 0")
            for (value in listOfNotNull(valid, index, length, group, groupDigits)) top.mcfpp.analysis.StorageAccess.publishScore(value,
                top.mcfpp.analysis.StorageLayout.Scoreboard(value.name, value.sbObject.toString()))
        }
        val body = Commands.tempFunction("selector_string", owner) { scan ->
            Function.addCommand(Commands.sbPlayerOperation(end, "=", index))
            Function.addCommand("scoreboard players add ${end.name} ${end.sbObject} 1")
            top.mcfpp.analysis.StorageAccess.publishScore(end, top.mcfpp.analysis.StorageLayout.Scoreboard(end.name, end.sbObject.toString()))
            Function.addCommands(Command("data modify").build(character.toCommandPart()).build("set string")
                .build(source.toCommandPart()).buildMacro(index).buildMacro(end).buildMacroFunction())
            if (allowed != null) {
                Function.addCommand("scoreboard players set ${characterValid.name} ${characterValid.sbObject} 0")
                for (letter in allowed) {
                    val pattern = NbtEncoding.snbt(CompoundTag("character" to StringTag(letter.toString())))
                    Function.addCommand(Command("execute if data").build(state.toCommandPart()).build(pattern, false)
                        .build("run scoreboard players set ${characterValid.name} ${characterValid.sbObject} 1"))
                }
                val invalidCharacter = Command("execute if score ${characterValid.name} ${characterValid.sbObject} matches 0")
                if (uuid) invalidCharacter.build("unless data").build(state.toCommandPart())
                    .build(NbtEncoding.snbt(CompoundTag("character" to StringTag("-"))), false)
                Function.addCommand(invalidCharacter.build("run scoreboard players set ${valid.name} ${valid.sbObject} 0"))
            } else {
                for (letter in listOf("\"", "\\", "\r", "\n")) {
                    val pattern = NbtEncoding.snbt(CompoundTag("character" to StringTag(letter)))
                    Function.addCommand(Command("execute if data").build(state.toCommandPart()).build(pattern, false)
                        .build("run scoreboard players set ${valid.name} ${valid.sbObject} 0"))
                }
            }
            if (group != null && groupDigits != null) {
                Function.addCommand("execute if score ${characterValid.name} ${characterValid.sbObject} matches 1 run scoreboard players add ${groupDigits.name} ${groupDigits.sbObject} 1")
                EntityTargetText.UUID_GROUP_WIDTHS.forEachIndexed { number, width ->
                    Function.addCommand("execute if score ${group.name} ${group.sbObject} matches $number if score ${groupDigits.name} ${groupDigits.sbObject} matches ${width + 1}.. run scoreboard players set ${valid.name} ${valid.sbObject} 0")
                }
                fun onDash(command: String) = Command("execute if data").build(state.toCommandPart())
                    .build(NbtEncoding.snbt(CompoundTag("character" to StringTag("-"))), false).build(command)
                Function.addCommand(onDash("if score ${groupDigits.name} ${groupDigits.sbObject} matches 0 run scoreboard players set ${valid.name} ${valid.sbObject} 0"))
                Function.addCommand(onDash("run scoreboard players add ${group.name} ${group.sbObject} 1"))
                Function.addCommand(onDash("run scoreboard players set ${groupDigits.name} ${groupDigits.sbObject} 0"))
            }
            Function.addCommand("scoreboard players add ${index.name} ${index.sbObject} 1")
            Function.addCommand("execute if score ${index.name} ${index.sbObject} < ${length.name} ${length.sbObject} run function ${scan.namespaceID}")
        }
        val commands = Commands.fakeFunction(owner) {
            Function.addCommand(Command("execute if score ${length.name} ${length.sbObject} matches ${lengthRange ?: "1.."} run").build(body.first))
            if (group != null && groupDigits != null) {
                Function.addCommand("execute unless score ${group.name} ${group.sbObject} matches 4 run scoreboard players set ${valid.name} ${valid.sbObject} 0")
                Function.addCommand("execute if score ${groupDigits.name} ${groupDigits.sbObject} matches 0 run scoreboard players set ${valid.name} ${valid.sbObject} 0")
            }
            Function.addCommand("execute if score ${valid.name} ${valid.sbObject} matches 0 run tellraw @a {\"type\":\"text\",\"text\":\"$diagnostic\"}")
        }
        return (initialization + commands) to "if score ${valid.name} ${valid.sbObject} matches 1"
    }
}

package top.mcfpp.backend

import top.mcfpp.analysis.CompilerValue
import top.mcfpp.analysis.StorageAccess
import top.mcfpp.command.Command
import top.mcfpp.command.Commands
import top.mcfpp.core.lang.MCInt
import top.mcfpp.core.lang.UnknownVar
import top.mcfpp.core.lang.Var
import top.mcfpp.core.lang.entity.SelectorVar
import top.mcfpp.core.lang.nbt.MCString
import top.mcfpp.core.lang.nbt.NBTBasedData
import top.mcfpp.lib.EntitySelector
import top.mcfpp.lib.EntityTarget
import top.mcfpp.lib.EntityTargetText
import top.mcfpp.lib.NBTPath
import top.mcfpp.lib.SelectorExpression
import top.mcfpp.model.function.Function
import top.mcfpp.nbt.tags.collection.IntArrayTag
import top.mcfpp.nbt.tags.primitive.StringTag
import top.mcfpp.type.*
import top.mcfpp.util.LogProcessor
import top.mcfpp.util.MCUUID
import top.mcfpp.util.TempPool
import java.util.UUID

/** UUID values have a single four-int encoding, including inside ordinary NBT literals. */
internal object UUIDOperations {
    fun fromText(input: Var<*>): Var<*> {
        val text = input as? MCString ?: return fail("UUID requires a string")
        if (StorageAccess.snapshot(text) != null) {
            if (!EntityTargetText.isValid(SelectorExpression.DirectKind.UUID_TEXT, text.value.value))
                return fail(EntityTargetText.description(SelectorExpression.DirectKind.UUID_TEXT))
            return literal(MCUUID(UUID.fromString(text.value.value)).uuidNBT)
        }
        StorageAccess.materialize(text)
        val result = MCFPPUUIDType.buildUnConcrete(TempPool.getVarIdentify())
        val destination = StorageAccess.ensure(result).path
        // An invalid value cannot accidentally identify the all-zero UUID or an arbitrary entity.
        Function.addCommand(Commands.dataSetValue(destination, IntArrayTag(intArrayOf())))
        val (validation, condition) = SelectorStringGuards.prepareEntityTarget(StorageAccess.ensure(text).path,
            SelectorExpression.DirectKind.UUID_TEXT, Function.currFunction)
        Function.addCommands(validation)
        val owner = Function.currFunction
        val state = NBTPath.stack.intIndex(0).memberIndex(TempPool.getVarIdentify())
        val remaining = state.memberIndex("remaining")
        val objective = MCInt().sbObject
        fun score(name: String) = "$name $objective"
        val prefix = TempPool.getVarIdentify()
        fun variable(name: String) = score("${prefix}_$name")
        val group = variable("group")
        val low = variable("low")
        val high = variable("high")
        val digit = variable("digit")
        val carry = variable("carry")
        val sixteen = variable("sixteen")
        val boundary = variable("boundary")
        val wide = variable("wide")
        val partials = (0..4).map { variable("part$it") }
        val scan = Function(TempPool.getFunctionIdentify("uuid_scan"), owner.namespace, null)
        val finish = Commands.tempFunction("uuid_group", owner) {
            partials.forEachIndexed { index, part ->
                Function.addCommand("execute if score $group matches $index run scoreboard players operation $part = $low")
            }
            Function.addCommand("scoreboard players set $low 0")
            Function.addCommand("scoreboard players add $group 1")
        }.first
        top.mcfpp.model.scope.GlobalScope.getOrCreateNamespace(owner.namespace).scope.addFunction(scan, false)
        owner.child.add(scan)
        scan.parent.add(owner)
        scan.runInFunction {
            Function.addCommand(Command("data modify").build(state.memberIndex("character").toCommandPart())
                .build("set string").build(remaining.toCommandPart()).build("0 1"))
            Function.addCommand(Command("data modify").build(remaining.toCommandPart()).build("set string")
                .build(remaining.toCommandPart()).build("1"))
            Function.addCommand("scoreboard players set $digit -1")
            "0123456789abcdef".forEachIndexed { index, char ->
                for (letter in setOf(char, char.uppercaseChar()))
                    Function.addCommand(Command("execute if data").build(state.toCommandPart())
                        .build("{character:\"$letter\"}", false).build("run scoreboard players set $digit $index"))
            }
            val digitStep = Commands.tempFunction("uuid_digit", scan) {
                Function.addCommand("scoreboard players operation $carry = $low")
                Function.addCommand("scoreboard players operation $carry /= $boundary")
                Function.addCommand("execute if score $carry matches ..-1 run scoreboard players add $carry 16")
                Function.addCommand("scoreboard players operation $high *= $sixteen")
                Function.addCommand("scoreboard players operation $high += $carry")
                Function.addCommand("scoreboard players operation $low *= $sixteen")
                Function.addCommand("scoreboard players operation $low += $digit")
            }.first
            Function.addCommand("execute if score $digit matches 0..15 run $digitStep")
            Function.addCommand("execute if score $digit matches -1 run $finish")
            Function.addCommand(Command("execute unless data").build(state.toCommandPart())
                .build("{remaining:\"\"}", false).build("run function ${scan.namespaceID}"))
        }
        scan.bodyCompiled = true
        val parse = Commands.tempFunction("uuid_parse", owner) {
            Function.addCommand(Commands.dataSetValue(state, top.mcfpp.nbt.tags.CompoundTag()))
            Function.addCommand(Commands.dataSetFrom(remaining, StorageAccess.ensure(text).path))
            for (slot in partials + listOf(group, low, high)) Function.addCommand("scoreboard players set $slot 0")
            Function.addCommand("scoreboard players set $sixteen 16")
            Function.addCommand("scoreboard players set $boundary 268435456")
            Function.addCommand("scoreboard players set $wide 65536")
            Function.addCommand("function ${scan.namespaceID}")
            Function.addCommand("scoreboard players operation ${partials[4]} = $low")
            Function.addCommand("scoreboard players operation ${partials[1]} *= $wide")
            Function.addCommand("scoreboard players operation ${partials[1]} += ${partials[2]}")
            Function.addCommand("scoreboard players operation ${partials[3]} *= $wide")
            // Only the final group can overflow 32 bits. Earlier groups fit their declared widths.
            Function.addCommand("scoreboard players operation ${partials[3]} += $high")
            Function.addCommand(Commands.dataSetValue(destination, IntArrayTag(intArrayOf(0, 0, 0, 0))))
            listOf(partials[0], partials[1], partials[3], partials[4]).forEachIndexed { index, slot ->
                Function.addCommand(Command("execute store result").build(destination.intIndex(index).toCommandPart())
                    .build("int 1 run scoreboard players get $slot"))
            }
        }.first
        Function.addCommand("execute $condition run $parse")
        StorageAccess.publishNbt(result)
        return result
    }

    fun fromWords(input: Var<*>): Var<*> {
        val tag = StorageAccess.constantEncoding(input) as? IntArrayTag
        if (tag != null) return if (tag.value.size == 4) literal(tag) else fail("UUID requires exactly four integers")
        val result = MCFPPUUIDType.buildUnConcrete(TempPool.getVarIdentify())
        val path = StorageAccess.ensure(result).path
        Function.addCommand(Commands.dataSetValue(path, IntArrayTag(intArrayOf())))
        StorageAccess.materialize(input)
        val length = MCInt()
        Function.addCommand(Command("execute store result score ${length.name} ${length.sbObject} run data get")
            .build(StorageAccess.ensure(input).path.toCommandPart()))
        Function.addCommand(Command("execute if score ${length.name} ${length.sbObject} matches 4 run")
            .build(Commands.dataSetFrom(path, StorageAccess.ensure(input).path)))
        Function.addCommand("execute unless score ${length.name} ${length.sbObject} matches 4 run tellraw @a {\"type\":\"text\",\"text\":\"UUID requires exactly four integers\"}")
        StorageAccess.publishNbt(result)
        return result
    }

    fun target(value: Var<*>): SelectorVar {
        val tag = StorageAccess.constantEncoding(value) as? IntArrayTag
        if (tag != null && tag.value.size == 4) return SelectorVar(EntityTarget.UUIDText(MCString(StringTag(MCUUID(tag).uuid.toString()))))
        StorageAccess.materialize(value)
        val result = MCString()
        val path = StorageAccess.ensure(result).path
        Function.addCommand(Commands.dataSetValue(path, StringTag("")))
        StorageAccess.publishNbt(result)
        val digit = MCString()
        val digitPath = StorageAccess.ensure(digit).path
        Function.addCommand(Commands.dataSetValue(digitPath, StringTag("")))
        StorageAccess.publishNbt(digit)
        val word = MCInt()
        val nibble = MCInt()
        val sixteen = MCInt(16)
        val divisor = StorageAccess.intRegister(sixteen)
        val length = MCInt()
        val prepend = Commands.tempFunction("uuid_prepend", Function.currFunction) {
            Function.addCommands(Command("data modify").build(path.toCommandPart()).build("set value \"")
                .buildMacro(digit, false).buildMacro(result, false).build("\"", false).buildMacroFunction())
        }.first
        val encode = Commands.tempFunction("uuid_text", Function.currFunction) {
            var digits = 0
            for (index in 3 downTo 0) {
                Function.addCommand(Command("execute store result score ${word.name} ${word.sbObject} run data get")
                    .build(StorageAccess.ensure(value).path.intIndex(index).toCommandPart()))
                repeat(8) {
                    Function.addCommand("scoreboard players operation ${nibble.name} ${nibble.sbObject} = ${word.name} ${word.sbObject}")
                    Function.addCommand("scoreboard players operation ${nibble.name} ${nibble.sbObject} %= ${divisor.player} ${divisor.objective}")
                    Function.addCommand("scoreboard players operation ${word.name} ${word.sbObject} /= ${divisor.player} ${divisor.objective}")
                    "0123456789abcdef".forEachIndexed { number, char ->
                        Function.addCommand(Command("execute if score ${nibble.name} ${nibble.sbObject} matches $number run")
                            .build(Commands.dataSetValue(digitPath, StringTag(char.toString()))))
                    }
                    Function.addCommand(prepend)
                    digits++
                    if (digits in setOf(12, 16, 20, 24)) {
                        Function.addCommand(Commands.dataSetValue(digitPath, StringTag("-")))
                        Function.addCommand(prepend)
                    }
                }
            }
        }.first
        Function.addCommand(Command("execute store result score ${length.name} ${length.sbObject} run data get")
            .build(StorageAccess.ensure(value).path.toCommandPart()))
        Function.addCommand("execute if score ${length.name} ${length.sbObject} matches 4 run $encode")
        return SelectorVar(EntityTarget.UUIDText(result))
    }

    private fun literal(tag: IntArrayTag) = StorageAccess.literal(MCFPPUUIDType, CompilerValue.Nbt(NbtEncoding.snbt(tag)))
    private fun fail(message: String): Var<*> {
        LogProcessor.error(message)
        return UnknownVar("uuid").apply { type = MCFPPUUIDType; isError = true }
    }
}

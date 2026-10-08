package top.mcfpp.backend

import top.mcfpp.command.Command
import top.mcfpp.command.Commands
import top.mcfpp.core.lang.MCFloat
import top.mcfpp.lib.NBTPath
import top.mcfpp.nbt.tags.CompoundTag

/** Four-component command ABI. Values outside this boundary remain independent NBT snapshots. */
object LegacyFloatCommands {
    private val work = LegacyFloatComparison.Components("float_sign int", "float_int0 int", "float_int1 int", "float_exp int")
    private val right = LegacyFloatComparison.Components(
        "${MCFloat.tempFloatEntityUUID} float_sign", "${MCFloat.tempFloatEntityUUID} float_int0",
        "${MCFloat.tempFloatEntityUUID} float_int1", "${MCFloat.tempFloatEntityUUID} float_exp")

    private fun fields(components: LegacyFloatComparison.Components) = listOf(
        "sign" to components.sign, "int0" to components.int0, "int1" to components.int1, "exp" to components.exp)

    fun load(source: NBTPath, components: LegacyFloatComparison.Components, emit: (Command) -> Unit) {
        MCFloat.requireLegacyBackend()
        for ((field, score) in fields(components))
            emit(Command("execute store result score $score run data get").build(source.memberIndex(field).toCommandPart()).build("1"))
    }

    fun store(destination: NBTPath, components: LegacyFloatComparison.Components, emit: (Command) -> Unit) {
        MCFloat.requireLegacyBackend()
        emit(Commands.dataSetValue(destination, CompoundTag()))
        for ((field, score) in fields(components))
            emit(Command("execute store result").build(destination.memberIndex(field).toCommandPart())
                .build("int 1 run scoreboard players get $score"))
    }

    fun fromInt(source: String, destination: NBTPath, emit: (Command) -> Unit) {
        MCFloat.requireLegacyBackend()
        emit(Command("scoreboard players operation inp int = $source"))
        emit(Command("function math.float:hpo/float/_scoreto"))
        store(destination, work, emit)
    }

    fun toInt(source: NBTPath, destination: String, emit: (Command) -> Unit) {
        load(source, work, emit)
        emit(Command("function math.float:hpo/float/_toscore"))
        emit(Command("scoreboard players operation $destination = res int"))
    }

    fun arithmetic(left: NBTPath, operand: NBTPath, operation: String, destination: NBTPath, emit: (Command) -> Unit) {
        val function = when (operation) {
            "+" -> "_add"
            "-" -> "_rmv"
            "*" -> "_mult"
            "/" -> "_div"
            else -> error("Unsupported legacy float operation: $operation")
        }
        load(operand, right, emit)
        load(left, work, emit)
        emit(Command("execute as ${MCFloat.tempFloatEntityUUID} run function math.float:hpo/float/$function"))
        store(destination, work, emit)
    }

    fun comparison(left: NBTPath, operand: NBTPath, operation: String, comparison: String, result: String,
                   emit: (Command) -> Unit) {
        load(operand, right, emit)
        load(left, work, emit)
        LegacyFloatComparison.emit(work, right, operation, comparison, result) { emit(Command(it)) }
    }
}

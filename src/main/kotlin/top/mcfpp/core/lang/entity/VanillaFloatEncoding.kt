package top.mcfpp.core.lang.entity

import top.mcfpp.analysis.*
import top.mcfpp.command.Command
import top.mcfpp.command.Commands
import top.mcfpp.command.FloatProviders
import top.mcfpp.core.lang.*
import top.mcfpp.core.lang.nbt.NBTBasedData
import top.mcfpp.lib.NBTPath
import top.mcfpp.model.function.Function
import top.mcfpp.nbt.tags.CompoundTag
import top.mcfpp.nbt.tags.primitive.FloatTag

/** Minecraft entity float tags and the legacy arithmetic representation have different encodings. */
internal object VanillaFloatEncoding {
    fun encode(value: Var<*>): Var<*> {
        val closed = StorageAccess.snapshot(value)
        val number = ((closed as? CompilerValue.Typed)?.payload ?: closed) as? CompilerValue.FloatBits
        if (number != null) return NBTBasedData(FloatTag(Float.fromBits(number.bits)))
        if (FloatProviders.enabled) return value
        val float = value as MCFloat
        float.loadWork() ?: return UnknownVar("float").apply { isError = true }
        val mantissa = MCInt()
        val power = MCInt()
        val factor = MCInt(10000)
        val score = StorageAccess.intRegister(factor)
        Function.addCommand("scoreboard players operation ${mantissa.name} ${mantissa.sbObject} = float_int0 int")
        Function.addCommand("scoreboard players operation ${mantissa.name} ${mantissa.sbObject} *= ${score.player} ${score.objective}")
        Function.addCommand("scoreboard players operation ${mantissa.name} ${mantissa.sbObject} += float_int1 int")
        Function.addCommand("scoreboard players operation ${mantissa.name} ${mantissa.sbObject} *= float_sign int")
        Function.addCommand("scoreboard players operation ${power.name} ${power.sbObject} = float_exp int")
        Function.addCommand("scoreboard players remove ${power.name} ${power.sbObject} 8")
        StorageAccess.publishScore(mantissa, StorageLayout.Scoreboard(mantissa.name, mantissa.sbObject.toString()))
        StorageAccess.publishScore(power, StorageLayout.Scoreboard(power.name, power.sbObject.toString()))
        val encoded = NBTBasedData()
        Function.addCommands(Command("data modify").build(StorageAccess.ensure(encoded).path.toCommandPart())
            .build("set value").buildMacro(mantissa).build("e", false).buildMacro(power, false).build("f", false).buildMacroFunction())
        return StorageAccess.publishNbt(encoded)
    }

    fun decode(source: NBTPath, destination: NBTPath) {
        if (FloatProviders.enabled) { Function.addCommand(Commands.dataSetFrom(destination, source)); return }
        MCFloat.requireLegacyBackend()
        Function.addCommand(Commands.dataSetValue(destination, CompoundTag("sign" to 0, "int0" to 0, "int1" to 0, "exp" to 0)))
        val sign = MCInt()
        val magnitude = MCInt()
        val mantissa = MCInt()
        val whole = MCInt()
        val fraction = MCInt()
        val factor = MCInt(10000)
        val divisor = StorageAccess.intRegister(factor)
        Function.addCommand(Command("execute store result score ${sign.name} ${sign.sbObject} run data get").build(source.toCommandPart()))
        for (exponent in -44..39) {
            for (negative in listOf(false, true)) {
                val condition = "execute ${if (negative) "if" else "unless"} score ${sign.name} ${sign.sbObject} matches ..-1"
                val polarity = if (negative) "-" else ""
                Function.addCommand(Command("$condition store result score ${magnitude.name} ${magnitude.sbObject} run data get")
                    .build(source.toCommandPart()).build("${polarity}1e${1 - exponent}"))
                val branch = Commands.tempFunction("display_float_read", Function.currFunction) {
                    Function.addCommand(Command("execute store result score ${mantissa.name} ${mantissa.sbObject} run data get")
                        .build(source.toCommandPart()).build("${polarity}1e${8 - exponent}"))
                    Function.addCommand("scoreboard players operation ${whole.name} ${whole.sbObject} = ${mantissa.name} ${mantissa.sbObject}")
                    Function.addCommand("scoreboard players operation ${fraction.name} ${fraction.sbObject} = ${mantissa.name} ${mantissa.sbObject}")
                    Function.addCommand("scoreboard players operation ${whole.name} ${whole.sbObject} /= ${divisor.player} ${divisor.objective}")
                    Function.addCommand("scoreboard players operation ${fraction.name} ${fraction.sbObject} %= ${divisor.player} ${divisor.objective}")
                    Function.addCommand(Commands.dataSetValue(destination, CompoundTag("sign" to if (negative) -1 else 1, "int0" to 0, "int1" to 0, "exp" to exponent)))
                    for ((field, score) in listOf("int0" to whole, "int1" to fraction))
                        Function.addCommand(Command("execute store result").build(destination.memberIndex(field).toCommandPart())
                            .build("int 1 run scoreboard players get ${score.name} ${score.sbObject}"))
                }.first
                Function.addCommand("$condition if score ${magnitude.name} ${magnitude.sbObject} matches 1..9 run $branch")
            }
        }
    }
}

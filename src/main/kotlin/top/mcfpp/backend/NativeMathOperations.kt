package top.mcfpp.backend

import top.mcfpp.Project
import top.mcfpp.analysis.StorageAccess
import top.mcfpp.analysis.StorageLayout
import top.mcfpp.command.Command
import top.mcfpp.command.Commands
import top.mcfpp.command.FloatProviders
import top.mcfpp.core.lang.MCFloat
import top.mcfpp.core.lang.MCInt
import top.mcfpp.core.lang.Var
import top.mcfpp.core.lang.obj.DataTemplateObject
import top.mcfpp.mni.NativeCallContext
import top.mcfpp.model.compound.DataTemplate
import top.mcfpp.model.function.Function
import top.mcfpp.nbt.tags.CompoundTag
import top.mcfpp.type.MCFPPBaseType
import top.mcfpp.util.LogProcessor
import top.mcfpp.util.TempPool

/** Backend boundary for scalar math. Never folds runtime library algorithms with host Math. */
object NativeMathOperations {
    private const val legacy = "math.float:hpo/float/"
    private fun emit(command: String) = Function.addCommand(command)
    private fun emit(command: Command) = Function.addCommands(command.buildMacroFunction())
    private fun score(value: MCInt): String = StorageAccess.intRegister(value).let { "${it.player} ${it.objective}" }
    private fun integerResult(read: (String) -> Unit): MCInt {
        val result = MCInt().apply { isTemp = true }
        val address = "${result.name} ${result.sbObject}"
        read(address)
        return StorageAccess.publishScore(result, StorageLayout.Scoreboard(result.name, result.sbObject.toString()))
    }
    private fun provider(type: String, vararg fields: Pair<String, String>) =
        "{type:\"minecraft:$type\"" + fields.joinToString("") { ",${it.first}:${it.second}" } + "}"
    private fun inputs(type: String, values: List<String>) = provider(type, "inputs" to values.joinToString(",", "[", "]"))
    private fun float(value: Var<*>) = StorageAccess.read(value) as MCFloat
    private fun fp(value: Var<*>) = FloatProviders.expression(float(value))
    private fun ip(value: Var<*>) = StorageAccess.intRegister(value as MCInt).let { FloatProviders.scoreProvider(it.player, it.objective) }

    fun apply(context: NativeCallContext, operation: String) = context.withArguments { args ->
        if (args.any { it.isError }) return@withArguments
        if (!validate(args, operation)) return@withArguments
        val result = when {
            operation == "sinCos" -> sinCos(context, float(args[0]))
            operation == "atan2Fixed" -> angle(args[0] as MCInt, args[1] as MCInt)
            context.declaredReturnType == MCFPPBaseType.Int -> integer(args, operation)
            FloatProviders.enabled -> nativeFloat(args, operation)
            else -> legacyFloat(args, operation)
        }
        context.publishResult(result)
    }

    private fun validate(args: List<Var<*>>, operation: String): Boolean {
        fun knownFloat(index: Int) = (args[index] as? MCFloat)?.let { if (StorageAccess.snapshot(it) != null) it.value else null }
        fun knownInt(index: Int) = (args[index] as? MCInt)?.let { if (StorageAccess.snapshot(it) != null) it.value else null }
        val reason = when (operation) {
            "sqrt" -> if ((knownFloat(0) ?: 0f) < 0f) "sqrt requires x >= 0" else null
            "isqrt" -> if ((knownInt(0) ?: 0) < 0) "isqrt requires x >= 0" else null
            "absInt" -> if (knownInt(0) == Int.MIN_VALUE) "abs result exceeds the int range" else null
            "powInt" -> when {
                (knownInt(1) ?: 0) < 0 -> "Integer pow requires a nonnegative exponent"
                knownInt(0) == 0 && knownInt(1) == 0 -> "pow(0, 0) is undefined"
                else -> null
            }
            "powFloat", "powFloatInt" -> if (knownFloat(0) == 0f && (knownFloat(1) == 0f || knownInt(1) == 0)) "pow(0, 0) is undefined" else null
            "nroot" -> when {
                (knownInt(1) ?: 2) < 2 -> "nroot requires n >= 2"
                (knownFloat(0) ?: 0f) < 0f && knownInt(1)?.rem(2) == 0 -> "An even root requires x >= 0"
                else -> null
            }
            "floorDiv", "floorMod" -> if (knownInt(1) == 0) "Division by zero" else null
            else -> null
        }
        if (reason != null) LogProcessor.error(reason)
        return reason == null
    }

    private fun integer(args: List<Var<*>>, operation: String): MCInt {
        if (FloatProviders.enabled && operation != "isqrt") {
            val values = args.map(::ip)
            val expression = when (operation) {
                "absInt" -> provider("abs", "input" to values[0])
                "minInt", "maxInt" -> inputs(if (operation == "minInt") "min" else "max", values)
                "powInt" -> provider("pow", "base" to values[0], "exponent" to values[1])
                "floorDiv", "floorMod" -> provider(if (operation == "floorDiv") "floor_div" else "floor_mod", "left" to values[0], "right" to values[1])
                else -> error(operation)
            }
            return integerResult {
                emit("scoreboard players set $it 0")
                emit("execute store result score $it run compute default integer $expression")
            }
        }
        val values = args.map { score(it as MCInt) }
        return integerResult { target ->
            when (operation) {
                "absInt" -> {
                    emit("scoreboard players operation $target = ${values[0]}")
                    val negativeOne = score(MCInt(-1))
                    emit("execute if score $target matches ..-1 run scoreboard players operation $target *= $negativeOne")
                    emit("execute if score $target matches -2147483648 run scoreboard players set $target 0")
                }
                "minInt", "maxInt" -> {
                    emit("scoreboard players operation $target = ${values[0]}")
                    emit("scoreboard players operation $target ${if (operation == "minInt") "<" else ">"} ${values[1]}")
                }
                "floorDiv", "floorMod" -> {
                    emit("scoreboard players operation $target = ${values[0]}")
                    emit("execute unless score ${values[1]} matches 0 run scoreboard players operation $target ${if (operation == "floorDiv") "/=" else "%="} ${values[1]}")
                    emit("execute if score ${values[1]} matches 0 run scoreboard players set $target 0")
                    if (operation == "floorDiv") emit("execute if score ${values[0]} matches -2147483648 if score ${values[1]} matches -1 run scoreboard players set $target 0")
                }
                "powInt" -> {
                    emit("scoreboard players operation mp_base int = ${values[0]}")
                    emit("scoreboard players operation mp_n int = ${values[1]}")
                    emit("function math:scalar/ipow")
                    emit("scoreboard players operation $target = mp_result int")
                }
                "isqrt" -> {
                    emit("scoreboard players operation ms_input int = ${values[0]}")
                    emit("scoreboard players set ms_low int 0")
                    emit("scoreboard players set ms_high int 46341")
                    if (FloatProviders.enabled) {
                        val root = provider("from_float", "input" to provider("sqrt", "input" to provider("from_int", "input" to ip(args[0]))))
                        emit("execute if score ms_input int matches 1.. store result score ms_low int run compute default integer $root")
                        emit("scoreboard players operation ms_high int = ms_low int")
                        emit("scoreboard players add ms_high int 2")
                        emit("execute if score ms_low int matches 1.. run scoreboard players remove ms_low int 1")
                        emit("execute if score ms_high int matches 46342.. run scoreboard players set ms_high int 46341")
                    }
                    emit("execute if score ms_input int matches 1.. run function math:scalar/isqrt")
                    emit("scoreboard players operation $target = ms_low int")
                }
                else -> error(operation)
            }
        }
    }

    private fun nativeFloat(args: List<Var<*>>, operation: String): MCFloat {
        val expression = when (operation) {
            "minFloat", "maxFloat" -> inputs(if (operation == "minFloat") "min" else "max", args.map(::fp))
            "hypot2", "hypot3" -> inputs("length", args.map(::fp))
            "powFloat" -> provider("pow", "base" to fp(args[0]), "exponent" to fp(args[1]))
            "powFloatInt" -> {
                val x = fp(args[0])
                val n = ip(args[1])
                val exponent = provider("from_int", "input" to n)
                val magnitude = provider("pow", "base" to provider("abs", "input" to x), "exponent" to exponent)
                // from_int can round away the low bit of a large integer exponent.
                // Determine parity in the integer provider before restoring a negative base's sign.
                val negative = "{type:\"minecraft:inverted\",term:{type:\"minecraft:float_value_check\",value:$x,test:{min:0.0}}}"
                val odd = "{type:\"minecraft:int_value_check\",value:${provider("floor_mod", "left" to n, "right" to "2")},test:1}"
                provider("conditional", "condition" to "{type:\"minecraft:all_of\",terms:[$negative,$odd]}",
                    "on_true" to provider("negate", "input" to magnitude),
                    "on_false" to provider("pow", "base" to x, "exponent" to exponent))
            }
            "nroot" -> {
                val x = fp(args[0])
                val n = ip(args[1])
                val root = provider("pow", "base" to provider("abs", "input" to x),
                    "exponent" to provider("div", "left" to "1.0", "right" to provider("from_int", "input" to n)))
                val negative = "{type:\"minecraft:inverted\",term:{type:\"minecraft:float_value_check\",value:$x,test:{min:0.0}}}"
                val odd = "{type:\"minecraft:int_value_check\",value:${provider("mod", "left" to n, "right" to "2")},test:1}"
                val valid = "{type:\"minecraft:any_of\",terms:[{type:\"minecraft:inverted\",term:$negative},$odd]}"
                // Even roots of negative values and invalid degrees leave the initialized zero.
                val signed = provider("conditional", "condition" to negative, "on_true" to provider("negate", "input" to root), "on_false" to root)
                provider("conditional", "condition" to "{type:\"minecraft:all_of\",terms:[{type:\"minecraft:int_value_check\",value:$n,test:{min:2}},$valid]}", "on_true" to signed)
            }
            else -> provider(when (operation) { "absFloat" -> "abs"; "trunc" -> "truncate"; else -> operation }, "input" to fp(args[0]))
        }
        return evaluate(expression)
    }

    private fun evaluate(expression: String): MCFloat {
        val result = MCFloat().apply { isTemp = true }
        StorageAccess.ensure(result)
        emit(Commands.dataSetValue(result.nbtPath, top.mcfpp.nbt.tags.primitive.FloatTag(0f)))
        emit(Command("data modify").build(result.nbtPath.toCommandPart()).build("set compute default float $expression"))
        return StorageAccess.publishNbt(result)
    }

    private fun legacyFloat(args: List<Var<*>>, operation: String): MCFloat {
        MCFloat.requireLegacyBackend()
        val x = float(args[0])
        when (operation) {
            "hypot2", "hypot3" -> {
                var sum = x.times(x) as MCFloat
                for (arg in args.drop(1)) {
                    val v = float(arg)
                    sum = sum.plus(v.times(v)) as MCFloat
                }
                return legacyFloat(listOf(sum), "sqrt")
            }
            "minFloat", "maxFloat" -> {
                val right = float(args[1])
                val test = (if (operation == "minFloat") x.isSmallerOrEqual(right) else x.isBiggerOrEqual(right)) as top.mcfpp.core.lang.bool.ScoreBool
                val choice = StorageAccess.booleanRegister(test)
                x.loadWork()
                val result = MCFloat.captureWorkResult()
                val destination = StorageAccess.legacyFloatRegisters(result)!!
                val source = StorageAccess.legacyFloatRegisters(right)!!
                destination.zip(source).forEach { (to, from) ->
                    emit("execute if score ${choice.player} ${choice.objective} matches 0 run scoreboard players operation ${to.player} ${to.objective} = ${from.player} ${from.objective}")
                }
                return StorageAccess.publishLegacyFloat(result, destination)
            }
            "powFloatInt", "nroot" -> {
                val degree = score(args[1] as MCInt)
                x.loadWork()
                emit("scoreboard players operation ${if (operation == "nroot") "mr_n" else "mf_n"} int = $degree")
                emit("function ${legacy}${if (operation == "nroot") "nroot" else "pow"}")
            }
            "sin", "cos" -> {
                legacySinCos(x)
                return fromFixed(operation)
            }
            else -> {
                x.loadWork()
                when (operation) {
                    "absFloat" -> emit("execute unless score float_sign int matches 0 run scoreboard players set float_sign int 1")
                    "sqrt" -> {
                        emit("execute if score float_sign int matches 1 run function ${legacy}_sqrt")
                        emit("execute unless score float_sign int matches 1 run function ${legacy}zero")
                    }
                    "floor", "ceil", "trunc", "round" -> {
                        emit("scoreboard players set mr_mode int ${listOf("trunc", "floor", "ceil", "round").indexOf(operation)}")
                        emit("function ${legacy}round")
                    }
                    else -> error(operation)
                }
            }
        }
        return MCFloat.captureWorkResult()
    }

    private fun fromFixed(name: String): MCFloat {
        val value = integerResult { emit("scoreboard players operation $it = $name int") }
        return NumericConversions.promoteToFloat(value).div(MCFloat(10000f)) as MCFloat
    }

    private fun legacySinCos(x: MCFloat) {
        // Reduce in the decimal representation before conversion; large angles cannot overflow int.
        x.loadWork()
        emit("function ${legacy}angle")
        emit("function math:scalar/sincos")
    }

    private fun sinCos(context: NativeCallContext, x: MCFloat): DataTemplateObject {
        val s: MCFloat
        val c: MCFloat
        if (FloatProviders.enabled) {
            s = evaluate(provider("sin", "input" to fp(x)))
            c = evaluate(provider("cos", "input" to fp(x)))
        } else {
            legacySinCos(x)
            s = fromFixed("sin")
            c = fromFixed("cos")
        }
        val result = context.declaredReturnType.buildUnConcrete(TempPool.getVarIdentify()) as DataTemplateObject
        StorageAccess.ensure(result)
        emit(Commands.dataSetValue(result.nbtPath, CompoundTag()))
        StorageAccess.publishNbt(result)
        DataTemplate.assignField(result, "sin", s)
        DataTemplate.assignField(result, "cos", c)
        return result
    }

    private fun angle(y: MCInt, x: MCInt): MCFloat {
        val sy = score(y)
        val sx = score(x)
        emit("scoreboard players operation sin int = $sy")
        emit("scoreboard players operation cos int = $sx")
        emit("function math:scalar/atan2")
        val degrees = fromFixed("theta")
        return degrees.times(MCFloat(0.017453292519943295f)) as MCFloat
    }
}

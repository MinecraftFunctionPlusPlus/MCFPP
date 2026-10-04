package top.mcfpp.command

import com.alibaba.fastjson2.JSON
import top.mcfpp.Project
import top.mcfpp.core.lang.*
import top.mcfpp.core.lang.bool.ScoreBool
import top.mcfpp.core.lang.bool.ScoreBoolConcrete
import top.mcfpp.lib.NBTPath
import top.mcfpp.lib.IntPath
import top.mcfpp.lib.MemberPath
import top.mcfpp.core.lang.nbt.MCStringConcrete
import top.mcfpp.lib.StorageSource
import top.mcfpp.model.function.Function
import top.mcfpp.nbt.tags.primitive.FloatTag
import top.mcfpp.nbt.tags.primitive.StringTag
import top.mcfpp.util.LogProcessor

/** Native single-precision float backend introduced in Java Edition 26.3. */
object FloatProviders {
    val enabled: Boolean get() = TargetCapabilities.forVersion(Project.config.version)?.floatBackend == FloatBackend.NUMBER_PROVIDER

    private fun quoted(value: String) = JSON.toJSONString(value)

    private fun temporary() = MCFloat().apply { isTemp = true }

    /** A new stack frame has already been pushed when normal parameters are copied. */
    fun callerValue(value: MCFloat): MCFloat = MCFloat(value).apply {
        val first = nbtPath.pathList.firstOrNull() as? MemberPath
        val member = first?.value as? MCStringConcrete
        val index = member?.value?.value?.let { Regex("stack_frame\\[(\\d+)]").matchEntire(it) }
            ?.groupValues?.get(1)?.toInt()
        if (index != null) {
            // getNormalStackPath represents the root and index in one member.
            nbtPath.pathList[0] = MemberPath(MCStringConcrete(StringTag("stack_frame[${index + 1}]")))
        }
    }

    fun snapshot(value: MCFloat): MCFloat {
        if (value.isError) return value
        val result = temporary()
        preparePath(value.nbtPath)
        emit(Commands.dataSetFrom(result.nbtPath, value.nbtPath))
        return result
    }

    private fun preparePath(path: NBTPath) {
        path.pathList.filterIsInstance<IntPath>().map { it.value }
            .filter { it !is MCIntConcrete && !it.isDataOnly }.forEach {
                // A previous macro use may have cached an earlier score value.
                it.hasStoredInStack = false
                it.storeToStack()
            }
    }

    private fun emit(command: Command) = Function.addCommands(command.buildMacroFunction())

    /** Providers can only read command storage. Snapshot other sources and macro paths. */
    private fun storage(path: NBTPath): String {
        var sourcePath = path
        if (path.source !is StorageSource || path.pathToCommandPart().isMacro) {
            sourcePath = temporary().nbtPath
            preparePath(path)
            emit(Commands.dataSetFrom(sourcePath, path))
        }
        return "{type:\"minecraft:storage\",storage:${quoted((sourcePath.source as StorageSource).storage)}," +
                "path:${quoted(sourcePath.pathToCommandPart().toString())}}"
    }

    private fun provider(value: MCFloat): String =
        if (value is MCFloatConcrete) value.value.toString() else storage(value.nbtPath)

    private fun valid(vararg values: MCFloat): Boolean {
        if (values.any { it is MCFloatConcrete && !it.value.isFinite() }) {
            LogProcessor.error("Minecraft 26.3 float providers require finite float values")
            return false
        }
        return true
    }

    private fun invalidFloat() = temporary().apply { isError = true }

    private fun evaluate(provider: String): MCFloat {
        val result = temporary()
        emit(Command("data modify").build(result.nbtPath.toCommandPart())
            .build("set compute default float $provider"))
        return result
    }

    fun assign(target: MCFloat, value: MCFloat): MCFloat {
        if (!valid(value)) return MCFloat(target).apply { parent = target.parent; isError = true }
        if (value is MCFloatConcrete && !target.isDataOnly) return MCFloatConcrete(target, value.value)
        preparePath(target.nbtPath)
        if (value is MCFloatConcrete) {
            emit(Commands.dataSetValue(target.nbtPath, FloatTag(value.value)))
        } else {
            preparePath(value.nbtPath)
            emit(Commands.dataSetFrom(target.nbtPath, value.nbtPath))
        }
        return if (target is MCFloatConcrete) MCFloat(target).apply { parent = target.parent } else target
    }

    fun materialize(value: MCFloatConcrete): MCFloat {
        val result = MCFloat(value).apply { parent = value.parent }
        if (!valid(value)) return result.apply { isError = true }
        preparePath(result.nbtPath)
        emit(Commands.dataSetValue(result.nbtPath, FloatTag(value.value)))
        return result
    }

    fun arithmetic(left: MCFloat, right: MCFloat, operation: String): Var<*> {
        if (!valid(left, right)) return invalidFloat()
        if (left is MCFloatConcrete && right is MCFloatConcrete) {
            val value = when (operation) {
                "+" -> left.value + right.value
                "-" -> left.value - right.value
                "*" -> left.value * right.value
                "/" -> left.value / right.value
                "%" -> left.value % right.value
                else -> error("Unsupported float operation: $operation")
            }
            val result = MCFloatConcrete(value)
            return if (valid(result)) result else invalidFloat()
        }
        val a = provider(left)
        val b = provider(right)
        val expression = when (operation) {
            "+" -> "{type:\"minecraft:add\",inputs:[$a,$b]}"
            "*" -> "{type:\"minecraft:mul\",inputs:[$a,$b]}"
            "-" -> "{type:\"minecraft:sub\",left:$a,right:$b}"
            "/" -> "{type:\"minecraft:div\",left:$a,right:$b}"
            "%" -> "{type:\"minecraft:mod\",left:$a,right:$b}"
            else -> error("Unsupported float operation: $operation")
        }
        return evaluate(expression)
    }

    fun negate(value: MCFloat): Var<*> {
        if (!valid(value)) return invalidFloat()
        return if (value is MCFloatConcrete) MCFloatConcrete(-value.value)
        else evaluate("{type:\"minecraft:negate\",input:${provider(value)}}")
    }

    fun compare(left: MCFloat, right: MCFloat, operation: String): Var<*> {
        if (!valid(left, right)) return ScoreBool().apply { isError = true }
        if (left is MCFloatConcrete && right is MCFloatConcrete) {
            return ScoreBoolConcrete(when (operation) {
                ">" -> left.value > right.value
                "<" -> left.value < right.value
                ">=" -> left.value >= right.value
                "<=" -> left.value <= right.value
                "==" -> left.value == right.value
                "!=" -> left.value != right.value
                else -> error("Unsupported float comparison: $operation")
            })
        }
        val a = provider(left)
        val b = provider(right)
        val test = when (operation) {
            ">", "<=" -> "{max:$b}"
            "<", ">=" -> "{min:$b}"
            "==", "!=" -> b
            else -> error("Unsupported float comparison: $operation")
        }
        val clause = if (operation in listOf(">", "<", "!=")) "unless" else "if"
        val result = ScoreBool()
        Function.addCommand("execute store success score ${result.name} ${result.boolObject} $clause predicate " +
                "{type:\"minecraft:float_value_check\",value:$a,test:$test}")
        return result
    }

    fun fromInt(value: MCInt): MCFloat {
        if (value is MCIntConcrete) return MCFloatConcrete(value.value.toFloat())
        val input = if (value.isDataOnly) storage(value.nbtPath) else
            "{type:\"minecraft:score\",target:{type:\"minecraft:fixed\",name:${quoted(value.name)}}," +
                    "score:${quoted(value.sbObject.toString())}}"
        return evaluate("{type:\"minecraft:from_int\",input:$input}")
    }

    fun toInt(value: MCFloat): Var<*> {
        if (!valid(value)) return MCInt().apply { isError = true }
        if (value is MCFloatConcrete) {
            if (value.value.toDouble() < Int.MIN_VALUE.toDouble() || value.value.toDouble() >= 2147483648.0) {
                LogProcessor.error("Minecraft 26.3 float-to-int conversion is outside the 32-bit integer range")
                return MCInt().apply { isError = true }
            }
            return MCIntConcrete(value.value.toInt())
        }
        val result = MCInt().apply { isTemp = true }
        Function.addCommand("execute store result score ${result.name} ${result.sbObject} run " +
                "compute default integer {type:\"minecraft:from_float\",input:${provider(value)}}")
        return result
    }
}

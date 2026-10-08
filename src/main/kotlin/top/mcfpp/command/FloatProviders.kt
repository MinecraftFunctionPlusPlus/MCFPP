package top.mcfpp.command

import com.alibaba.fastjson2.JSON
import top.mcfpp.Project
import top.mcfpp.core.lang.*
import top.mcfpp.core.lang.bool.ScoreBool
import top.mcfpp.core.lang.bool.ScoreBoolConcrete
import top.mcfpp.lib.NBTPath
import top.mcfpp.lib.StorageSource
import top.mcfpp.model.function.Function
import top.mcfpp.nbt.tags.primitive.FloatTag
import top.mcfpp.util.LogProcessor

/** Native single-precision float backend introduced in Java Edition 26.3. */
object FloatProviders {
    val enabled: Boolean get() = TargetCapabilities.forVersion(Project.config.version)?.floatBackend == FloatBackend.NUMBER_PROVIDER

    private fun quoted(value: String) = JSON.toJSONString(value)

    private fun temporary() = MCFloat().apply { isTemp = true }

    /** A new stack frame has already been pushed when normal parameters are copied. */
    fun callerValue(value: MCFloat): MCFloat = top.mcfpp.analysis.StorageAccess.callerValue(value) as MCFloat

    fun snapshot(value: MCFloat): MCFloat {
        if (value.isError) return value
        val result = temporary()
        emit(Commands.dataSetFrom(result.nbtPath, value.nbtPath))
        return result
    }

    // Macro lowering captures live index values directly into its own argument compound.
    private fun emit(command: Command) = Function.addCommands(command.buildMacroFunction())

    /** Providers can only read command storage. Snapshot other sources and macro paths. */
    private fun storage(path: NBTPath): String {
        var sourcePath = path
        if (path.source !is StorageSource || path.pathToCommandPart().isMacro) {
            sourcePath = temporary().nbtPath
            emit(Commands.dataSetFrom(sourcePath, path))
        }
        return storageProvider((sourcePath.source as StorageSource).storage, sourcePath.pathToCommandPart().toString())
    }

    // Shared command expressions; the IR backend supplies already captured operands.
    fun storageProvider(storage: String, path: String) =
        "{type:\"minecraft:storage\",storage:${quoted(storage)},path:${quoted(path)}}"

    fun scoreProvider(name: String, objective: String) =
        "{type:\"minecraft:score\",target:{type:\"minecraft:fixed\",name:${quoted(name)}},score:${quoted(objective)}}"

    fun arithmeticProvider(left: String, right: String, operation: String): String = when (operation) {
        "+" -> "{type:\"minecraft:add\",inputs:[$left,$right]}"
        "*" -> "{type:\"minecraft:mul\",inputs:[$left,$right]}"
        "-" -> "{type:\"minecraft:sub\",left:$left,right:$right}"
        "/" -> "{type:\"minecraft:div\",left:$left,right:$right}"
        "%" -> "{type:\"minecraft:mod\",left:$left,right:$right}"
        else -> error("Unsupported float operation: $operation")
    }

    fun comparisonClause(left: String, right: String, operation: String): String {
        val test = when (operation) {
            ">", "<=" -> "{max:$right}"
            "<", ">=" -> "{min:$right}"
            "==", "!=" -> right
            else -> error("Unsupported float comparison: $operation")
        }
        val clause = if (operation in listOf(">", "<", "!=")) "unless" else "if"
        return "$clause predicate {type:\"minecraft:float_value_check\",value:$left,test:$test}"
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
        if (value is MCFloatConcrete) {
            emit(Commands.dataSetValue(target.nbtPath, FloatTag(value.value)))
        } else {
            emit(Commands.dataSetFrom(target.nbtPath, value.nbtPath))
        }
        return if (target is MCFloatConcrete) MCFloat(target).apply { parent = target.parent } else target
    }

    fun materialize(value: MCFloatConcrete): MCFloat {
        val result = MCFloat(value).apply { parent = value.parent }
        if (!valid(value)) return result.apply { isError = true }
        emit(Commands.dataSetValue(result.nbtPath, FloatTag(value.value)))
        return result
    }

    fun arithmetic(left: MCFloat, right: MCFloat, operation: String): Var<*> {
        if (!valid(left, right)) return invalidFloat()
        if (left is MCFloatConcrete && right is MCFloatConcrete) {
            return MCFloatConcrete(arithmeticValue(left.value, right.value, operation))
        }
        return evaluate(arithmeticProvider(provider(left), provider(right), operation))
    }

    /** The selected data-set provider uses safe getFloat; invalid results become positive zero. */
    internal fun arithmeticValue(left: Float, right: Float, operation: String): Float {
        val result = when (operation) {
            "+" -> (0f + left) + right // Sum starts at +0, including for two negative zero inputs.
            "*" -> (1f * left) * right
            "-" -> left - right
            "/" -> left / right
            "%" -> left % right
            else -> error("Unsupported float operation: $operation")
        }
        return if (result.isFinite()) result else 0f
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
        val result = ScoreBool()
        Function.addCommand("execute store success score ${result.name} ${result.boolObject} " +
                comparisonClause(provider(left), provider(right), operation))
        return result
    }

    fun fromInt(value: MCInt): MCFloat {
        if (value is MCIntConcrete) return MCFloatConcrete(value.value.toFloat())
        val input = if (value.isDataOnly) storage(value.nbtPath) else scoreProvider(value.name, value.sbObject.toString())
        return evaluate("{type:\"minecraft:from_int\",input:$input}")
    }

    /** Storage providers use Number.floatValue; non-finite results fall back to zero. */
    fun fromStoredNumber(value: Var<*>): MCFloat {
        top.mcfpp.analysis.StorageAccess.flush(listOf(value))
        return evaluate(storage(value.nbtPath))
    }

    fun toInt(value: MCFloat): Var<*> {
        if (!valid(value)) return MCInt().apply { isError = true }
        if (value is MCFloatConcrete) {
            top.mcfpp.analysis.NumericConversion.floatToIntError(value.value)?.let {
                LogProcessor.error(it)
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

package top.mcfpp.antlr

import top.mcfpp.core.lang.MCFloatConcrete
import top.mcfpp.core.lang.MCFloat
import top.mcfpp.core.lang.MCIntConcrete
import top.mcfpp.core.lang.MCFPPValue
import top.mcfpp.core.lang.UnknownVar
import top.mcfpp.core.lang.Var
import top.mcfpp.type.MCFPPBaseType
import top.mcfpp.util.LogProcessor
import top.mcfpp.model.function.Function
import top.mcfpp.command.Commands
import top.mcfpp.command.FloatProviders

/** Negate numeric values without changing the operand. */
internal fun negateNumber(value: Var<*>, concreteOnly: Boolean = false): Var<*>? {
    if (FloatProviders.enabled && value is MCFloat && !concreteOnly) return FloatProviders.negate(value)
    if (value is MCFloatConcrete && value !== MCFloat.ssObj) return MCFloatConcrete(-value.value)
    if (value === MCFloat.ssObj && concreteOnly) return null
    if (value is MCFloat && !concreteOnly) {
        val result = MCFloat().apply { isTemp = true }
        if (value === MCFloat.ssObj) {
            Function.addCommand(Commands.sbPlayerOperation(result.sign, "=", MCFloat.ssObj.sign))
            Function.addCommand(Commands.sbPlayerOperation(result.int0, "=", MCFloat.ssObj.int0))
            Function.addCommand(Commands.sbPlayerOperation(result.int1, "=", MCFloat.ssObj.int1))
            Function.addCommand(Commands.sbPlayerOperation(result.exp, "=", MCFloat.ssObj.exp))
        } else {
            result.assignedBy(value)
        }
        val negativeOne = MCIntConcrete(-1)
        Function.addCommand("scoreboard players set ${negativeOne.name} ${negativeOne.sbObject} -1")
        Function.addCommand(Commands.sbPlayerOperation(result.sign, "*=", negativeOne))
        return result
    }
    val minusOne = when (value.type) {
        MCFPPBaseType.Int -> MCIntConcrete(-1)
        else -> {
            LogProcessor.error("Unary '-' is not supported for ${value.type.typeName}")
            return UnknownVar("invalid_unary_minus").apply { isError = true }
        }
    }
    return if (concreteOnly) {
        if (value !is MCFPPValue<*>) null else value.constBinaryComputation(minusOne, "*")
    } else {
        value.binaryComputation(minusOne, "*")
    }
}

/** Float operators use shared work registers, so preserve both operands before invoking them. */
internal fun computeFloatCompound(left: MCFloat, right: Var<*>, operation: String): Var<*> {
    if (FloatProviders.enabled) {
        val operand = if (right is MCFloat) right else right.implicitCast(MCFPPBaseType.Float)
        if (operand !is MCFloat || operand.isError) {
            LogProcessor.error("Float operation '$operation' requires a numeric operand")
            return UnknownVar("invalid_float_operand").apply { isError = true }
        }
        if (operation !in listOf("+", "-", "*", "/", "%")) {
            LogProcessor.error("Float operation '$operation' is not supported")
            return UnknownVar("invalid_float_operation").apply { isError = true }
        }
        return FloatProviders.arithmetic(left, operand, operation)
    }
    val suffix = when (operation) {
        "+" -> "_add"
        "-" -> "_rmv"
        "*" -> "_mult"
        "/" -> "_div"
        else -> {
            LogProcessor.error("Float operation '$operation' is not supported")
            return UnknownVar("invalid_float_operation").apply { isError = true }
        }
    }
    val operand = if (right.type == MCFPPBaseType.Float) right else right.implicitCast(MCFPPBaseType.Float)
    if (operand.isError || operand !is MCFloat) {
        LogProcessor.error("Float operation '$operation' requires a numeric operand")
        return UnknownVar("invalid_float_operand").apply { isError = true }
    }
    if (operand === MCFloat.ssObj) {
        Function.addCommand(Commands.sbPlayerOperation(MCFloat.tempFloat.sign, "=", MCFloat.ssObj.sign))
        Function.addCommand(Commands.sbPlayerOperation(MCFloat.tempFloat.int0, "=", MCFloat.ssObj.int0))
        Function.addCommand(Commands.sbPlayerOperation(MCFloat.tempFloat.int1, "=", MCFloat.ssObj.int1))
        Function.addCommand(Commands.sbPlayerOperation(MCFloat.tempFloat.exp, "=", MCFloat.ssObj.exp))
    } else {
        operand.toTempEntity()
    }
    left.getTempVar()
    Function.addCommand("execute as ${MCFloat.tempFloatEntityUUID} run function math.float:hpo/float/$suffix")
    val result = MCFloat().apply { isTemp = true }
    Function.addCommand(Commands.sbPlayerOperation(result.sign, "=", MCFloat.ssObj.sign))
    Function.addCommand(Commands.sbPlayerOperation(result.int0, "=", MCFloat.ssObj.int0))
    Function.addCommand(Commands.sbPlayerOperation(result.int1, "=", MCFloat.ssObj.int1))
    Function.addCommand(Commands.sbPlayerOperation(result.exp, "=", MCFloat.ssObj.exp))
    return result
}

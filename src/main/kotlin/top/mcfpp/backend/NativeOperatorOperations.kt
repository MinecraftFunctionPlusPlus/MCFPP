package top.mcfpp.backend

import top.mcfpp.core.lang.MCFloat
import top.mcfpp.core.lang.MCInt
import top.mcfpp.core.lang.MCFPPTypeVar
import top.mcfpp.core.lang.RangeVar
import top.mcfpp.core.lang.bool.ScoreBool
import top.mcfpp.mni.NativeCallContext
import top.mcfpp.type.MCFPPUnionType

/** Native operators reuse core operations without entering operator lookup again. */
object NativeOperatorOperations {
    fun unionType(context: NativeCallContext) = context.withAdapters { receiver, arguments ->
        val left = receiver as MCFPPTypeVar
        val right = arguments[0] as MCFPPTypeVar
        context.publishResult(MCFPPTypeVar(MCFPPUnionType(left.value, right.value)))
    }

    fun integer(context: NativeCallContext, operator: String) = context.withAdapters { receiver, arguments ->
        val left = receiver as MCInt
        val right = arguments[0] as MCInt
        context.publishResult(when (operator) {
            "+" -> left.plus(right)
            "-" -> left.minus(right)
            "*" -> left.times(right)
            "/" -> left.div(right)
            "%" -> left.rem(right)
            ">" -> left.isBigger(right)
            "<" -> left.isSmaller(right)
            ">=" -> left.isBiggerOrEqual(right)
            "<=" -> left.isSmallerOrEqual(right)
            "==" -> left.isEqual(right)
            "!=" -> left.isNotEqual(right)
            else -> error("Unsupported native int operator '$operator'")
        })
    }

    fun floating(context: NativeCallContext, operator: String) = context.withAdapters { receiver, arguments ->
        val left = receiver as MCFloat
        val right = arguments[0] as MCFloat
        context.publishResult(when (operator) {
            "+" -> left.plus(right)
            "-" -> left.minus(right)
            "*" -> left.times(right)
            "/" -> left.div(right)
            "%" -> left.rem(right)
            ">" -> left.isBigger(right)
            "<" -> left.isSmaller(right)
            ">=" -> left.isBiggerOrEqual(right)
            "<=" -> left.isSmallerOrEqual(right)
            "==" -> left.isEqual(right)
            "!=" -> left.isNotEqual(right)
            else -> error("Unsupported native float operator '$operator'")
        })
    }

    fun logical(context: NativeCallContext, operator: String) = context.withAdapters { receiver, arguments ->
        val left = receiver as ScoreBool
        context.publishResult(if (operator == "!") left.negation() else {
            val right = arguments[0] as ScoreBool
            when (operator) {
                "==" -> left.isEqual(right)
                "!=" -> left.isNotEqual(right)
                "&&" -> left.and(right)
                "||" -> left.or(right)
                else -> error("Unsupported native bool operator '$operator'")
            }
        })
    }

    fun inRange(context: NativeCallContext) = context.withAdapters { receiver, arguments ->
        context.publishResult((receiver as MCInt).inRange(arguments[0] as RangeVar))
    }
}

package top.mcfpp.backend

import top.mcfpp.analysis.StorageAccess
import top.mcfpp.core.lang.MCInt
import top.mcfpp.core.lang.RangeVar
import top.mcfpp.core.lang.Var
import top.mcfpp.core.lang.obj.TypeDataTemplateObject
import top.mcfpp.mni.NativeCallContext
import top.mcfpp.model.compound.TypeDataTemplate
import top.mcfpp.type.MCFPPTypeDataTemplateType
import top.mcfpp.util.TempPool

/** Time uses the integer core while each arithmetic result retains a fresh nominal wrapper. */
object NativeTimeOperations {
    private fun integer(value: TypeDataTemplateObject) = StorageAccess.read(value.delegateVar) as MCInt

    private fun publishTime(context: NativeCallContext, value: Var<*>) {
        val type = context.declaredReturnType as MCFPPTypeDataTemplateType
        val result = (type.buildUnConcrete(TempPool.getVarIdentify()) as TypeDataTemplateObject).apply { isTemp = true }
        TypeDataTemplate.defaultConstructor(value, result)
        context.publishResult(result)
    }

    fun factory(context: NativeCallContext, multiplier: Int) = context.withArguments { arguments ->
        val value = StorageAccess.read(arguments[0]) as MCInt
        publishTime(context, if (multiplier == 1) value else value.times(MCInt(multiplier)))
    }

    fun arithmetic(context: NativeCallContext, operator: String) = context.withAdapters { receiver, arguments ->
        val left = integer(receiver as TypeDataTemplateObject)
        val right = integer(arguments[0] as TypeDataTemplateObject)
        publishTime(context, when (operator) {
            "+" -> left.plus(right)
            "-" -> left.minus(right)
            "*" -> left.times(right)
            "/" -> left.div(right)
            "%" -> left.rem(right)
            else -> error("Unsupported Time arithmetic '$operator'")
        })
    }

    fun comparison(context: NativeCallContext, operator: String) = context.withAdapters { receiver, arguments ->
        val left = integer(receiver as TypeDataTemplateObject)
        val right = integer(arguments[0] as TypeDataTemplateObject)
        context.publishResult(when (operator) {
            ">" -> left.isBigger(right)
            "<" -> left.isSmaller(right)
            ">=" -> left.isBiggerOrEqual(right)
            "<=" -> left.isSmallerOrEqual(right)
            "==" -> left.isEqual(right)
            "!=" -> left.isNotEqual(right)
            else -> error("Unsupported Time comparison '$operator'")
        })
    }

    fun inRange(context: NativeCallContext) = context.withAdapters { receiver, arguments ->
        context.publishResult(integer(receiver as TypeDataTemplateObject).inRange(arguments[0] as RangeVar))
    }
}

package top.mcfpp.model.scope

import top.mcfpp.core.lang.Var
import top.mcfpp.model.CanSelectMember
import top.mcfpp.model.Member
import top.mcfpp.model.function.Function
import top.mcfpp.util.LogProcessor

/**
 * 获取函数用的visitor
 */
object MCFPPFuncGetter{

    /**
     * 获取成员函数
     *
     * @param curr 一个变量，用于从中选择函数
     * @param identifier 函数的名字
     * @param readOnlyArgs 函数的只读参数
     * @param normalArgs 函数的普通参数
     * @return
     */
    private fun getFunction(
        curr: Var<*>,
        identifier: String,
        readOnlyArgs: List<Var<*>>,
        normalArgs: ArrayList<Var<*>>
    ): Function {
        val func = curr.getMemberFunction(identifier, readOnlyArgs, normalArgs, Member.AccessModifier.PUBLIC)
        val owner = func.first.parentTemplate()
        val accessible = if (owner != null) {
            Function.currFunction.accessTo(owner) >= func.first.accessModifier
        } else {
            func.second
        }
        if (!accessible){
            LogProcessor.error("Cannot access member $identifier")
        }
        return func.first
    }

    fun getFunction(
        selector: CanSelectMember,
        identifier: String,
        readOnlyArgs: List<Var<*>>,
        normalArgs: ArrayList<Var<*>>
    ): Function {
        return when(selector){
            is Var<*> -> getFunction(selector, identifier, readOnlyArgs, normalArgs)
            else -> throw Exception()
        }
    }
}

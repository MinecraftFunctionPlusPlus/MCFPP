package top.mcfpp.model.scope

import top.mcfpp.core.lang.Var
import top.mcfpp.core.lang.obj.DataTemplateObject
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
        //是类的成员方法或扩展方法
        val accessModifier : Member.AccessModifier = if(curr is DataTemplateObject){
            //类指针
            if(Function.currFunction.ownerType == Function.Companion.OwnerType.CLASS){
                Function.currFunction.parentTemplate()!!.getAccess(curr.templateType)
            }else{
                Member.AccessModifier.PUBLIC
            }
        }
        else{
            //基本类型
            Member.AccessModifier.PUBLIC
        }
        //开始选择函数
        val func = curr.getMemberFunction(identifier, readOnlyArgs, normalArgs, accessModifier)
        if (!func.second){
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
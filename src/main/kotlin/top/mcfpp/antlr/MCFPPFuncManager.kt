package top.mcfpp.antlr

import top.mcfpp.core.lang.*
import top.mcfpp.model.CanSelectMember
import top.mcfpp.model.function.Function
import top.mcfpp.model.Member
import top.mcfpp.type.MCFPPClassType
import top.mcfpp.util.LogProcessor

/**
 * 获取函数用的visitor
 */
class MCFPPFuncManager{

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
        val accessModifier : Member.AccessModifier = if(curr is ClassPointer){
            //类指针
            if(Function.currFunction.ownerType == Function.Companion.OwnerType.CLASS){
                Function.currFunction.parentClass()!!.getAccess(curr.clazz)
            }else{
                Member.AccessModifier.PUBLIC
            }
        }else if(curr is DataTemplateObject){
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


    /**
     * 获取一个类的静态函数
     *
     * @param type
     * @param identifier
     * @param readOnlyArgs
     * @param normalArgs
     * @return
     */
    private fun getFunction(
        type: MCFPPClassType,
        identifier : String,
        readOnlyArgs: List<Var<*>>,
        normalArgs: ArrayList<Var<*>>
    ): Function {
        //是类的成员方法
        val accessModifier = if(Function.currFunction.ownerType == Function.Companion.OwnerType.CLASS){
            Function.currFunction.parentClass()!!.getAccess(type.cls)
        }else{
            Member.AccessModifier.PUBLIC
        }
        //开始选择函数
        val func = type.getMemberFunction(identifier, readOnlyArgs, normalArgs, accessModifier)
        if (!func.second){
            LogProcessor.error("Cannot access member $identifier in class ${type.cls.identifier}")
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
            is MCFPPClassType -> getFunction(selector, identifier, readOnlyArgs, normalArgs)
            is Var<*> -> getFunction(selector, identifier, readOnlyArgs, normalArgs)
            else -> throw Exception()
        }
    }
}
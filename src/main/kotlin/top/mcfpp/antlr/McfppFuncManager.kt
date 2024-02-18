package top.mcfpp.antlr

import top.mcfpp.lang.*
import top.mcfpp.lang.type.MCFPPType
import top.mcfpp.lib.Function
import top.mcfpp.lib.GlobalField
import top.mcfpp.lib.Member
import top.mcfpp.util.LogProcessor
import kotlin.reflect.KFunction

/**
 * 获取函数用的visitor
 */
class McfppFuncManager{

    /**
     * 获取一个全局函数
     *
     * @param namespace
     * @param identifier
     * @param args
     * @return
     */
    fun getFunction(namespace: String?, identifier: String, args: ArrayList<String>): Function{
        return GlobalField.getFunction(namespace, identifier, args)
    }

    /**
     * 获取成员函数
     *
     * @param primaryCtx
     * @param sctCtx
     * @param args
     * @return
     */
    fun getFunction(
        curr: Var<*>,
        identifier: String,
        args: ArrayList<String>
    ): Function{
        //是类的成员方法或扩展方法
        val getter : KFunction<Pair<Function, Boolean>> = curr::getMemberFunction
        val accessModifier : Member.AccessModifier = if(curr is ClassPointer){
            //类指针
            if(Function.currFunction.ownerType == Function.Companion.OwnerType.CLASS){
                Function.currFunction.parentClass()!!.getAccess(curr.clsType)
            }else{
                Member.AccessModifier.PUBLIC
            }
        }else{
            //基本类型
            Member.AccessModifier.PUBLIC
        }
        //开始选择函数
        val func = getter.call(identifier,args,accessModifier)
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
     * @param args
     * @return
     */
    fun getFunction(
        type: CompoundDataType,
        identifier : String,
        args: ArrayList<String>
    ): Function{
        //是类的成员方法
        val accessModifier = if(Function.currFunction.ownerType == Function.Companion.OwnerType.CLASS){
            Function.currFunction.parentClass()!!.getAccess(type.dataType)
        }else{
            Member.AccessModifier.PUBLIC
        }
        val argTypesList = args.map { MCFPPType.parse(it) }
        //开始选择函数
        val func = type.getMemberFunction(identifier,argTypesList,accessModifier)
        if (!func.second){
            LogProcessor.error("Cannot access member $identifier in class ${type.dataType.identifier}")
        }
        return func.first
    }

    fun getFunction(
        selector: CanSelectMember,
        identifier: String,
        args: ArrayList<String>
    ): Function{
        return when(selector){
            is CompoundDataType -> getFunction(selector, identifier, args)
            is Var<*> -> getFunction(selector, identifier, args)
            else -> throw Exception()
        }
    }
}
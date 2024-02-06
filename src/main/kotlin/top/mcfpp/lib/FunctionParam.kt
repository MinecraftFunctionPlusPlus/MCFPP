package top.mcfpp.lib

import top.mcfpp.antlr.mcfppParser
import top.mcfpp.lang.Var
import top.mcfpp.util.LogProcessor
import top.mcfpp.util.StringHelper

/**
 * 函数的参数。用于函数声明的时候。
 */
class FunctionParam(

    /**
     * 参数类型
     */
    var type: String,

    /**
     * 参数的名字
     */
    var identifier: String,

    /**
     * 参数是否为静态的
     */
    var isStatic: Boolean = false,

    /**
     * 参数是否是确定的
     */
    var isConcrete: Boolean = false
) {

    companion object {

        val baseType: ArrayList<String> = arrayListOf("any","int", "bool", "string", "float", "entity", "selector","string", "jstring", "nbt")
        val nbtType: ArrayList<String> = arrayListOf("nbt", "list","dict","map","string","jstring","selector","entity")

        /**
         * 是否是给定类型的子类型
         *
         * @param type
         * @return
         */
        fun isSubOf(subType: String ,parentType: String): Boolean{
            if(subType == parentType) return true   //相同类型返回true
            if(parentType == "any") return true   //any是所有类型的基类型
            if(nbtType.contains(subType) && parentType == "nbt") return true   //nbt类型是所有nbt类型的基类
            if(baseType.contains(subType)) return false   //除此之外基本类型不是其他类型的基类
            //不是基本类型，获取类
            val typeWithNamespace = StringHelper.splitNamespaceID(parentType)
            val typeClass = GlobalField.getClass(typeWithNamespace.first, typeWithNamespace.second)
            val thisTypeWithNamespace = StringHelper.splitNamespaceID(subType)
            val thisTypeClass = GlobalField.getClass(thisTypeWithNamespace.first, thisTypeWithNamespace.second)
            if(typeClass == null){
                LogProcessor.error("Undefined class:$parentType")
                return true
            }
            if(thisTypeClass == null){
                LogProcessor.error("Undefined class:${subType}")
                return true
            }
            return thisTypeClass.isSub(typeClass)
        }

        /**
         * 将一个参数列表转换为对应的字符串列表
         * @param params 参数列表
         * @return 它的字符串列表
         */
        fun toStringList(params: ArrayList<FunctionParam>): ArrayList<String> {
            val qwq: ArrayList<String> = ArrayList()
            for (param in params) {
                qwq.add(param.type)
            }
            return qwq
        }

        /**
         * 将一个变量列表转换为对应的字符串列表
         * @param params 参数列表
         * @return 它的字符串列表
         */
        fun getVarTypes(params: ArrayList<Var>): ArrayList<String> {
            val qwq: ArrayList<String> = ArrayList()
            for (param in params) {
                qwq.add(param.type)
            }
            return qwq
        }

        fun getParams(params: mcfppParser.ParameterListContext): ArrayList<String>{
            val re = ArrayList<String>()
            for (param in params.parameter()) {
                re.add(param.type().text)
            }
            return re
        }
    }
}
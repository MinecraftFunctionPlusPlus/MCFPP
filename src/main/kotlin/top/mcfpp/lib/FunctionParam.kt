package top.mcfpp.lib

import top.mcfpp.antlr.mcfppParser
import top.mcfpp.lang.Var
import top.mcfpp.lang.type.MCFPPType

/**
 * 函数的参数。用于函数声明的时候。
 */
class FunctionParam(

    /**
     * 参数类型
     */
    var type: MCFPPType,

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

    constructor(type: String,identifier: String,isStatic: Boolean = false,isConcrete: Boolean = false) :
            this(MCFPPType.parse(type),identifier,isStatic,isConcrete) {}
    companion object {

        /**
         * 是否是给定类型的子类型
         *
         * @param type
         * @return
         */

        fun isSubOf(subType: MCFPPType ,parentType: MCFPPType): Boolean{
            return subType.isSubOf(parentType)
        }

        /**
         * 将一个参数列表转换为对应的字符串列表
         * @param params 参数列表
         * @return 它的字符串列表
         */
        fun toStringList(params: ArrayList<FunctionParam>): ArrayList<String> {
            val qwq: ArrayList<String> = ArrayList()
            for (param in params) {
                qwq.add(param.type.toString())
            }
            return qwq
        }

        /**
         * 将一个变量列表转换为对应的字符串列表
         * @param params 参数列表
         * @return 它的字符串列表
         */
        fun getVarTypes(params: ArrayList<Var<*>>): ArrayList<String> {
            val qwq: ArrayList<String> = ArrayList()
            for (param in params) {
                qwq.add(param.type.toString())
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
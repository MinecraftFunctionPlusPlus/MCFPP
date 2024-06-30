package top.mcfpp.model.function

import top.mcfpp.antlr.mcfppParser
import top.mcfpp.command.Command
import top.mcfpp.lang.Var
import top.mcfpp.lang.type.MCFPPBaseType
import top.mcfpp.lang.type.MCFPPGenericType
import top.mcfpp.lang.type.MCFPPType
import top.mcfpp.model.field.SimpleFieldWithType

/**
 * 函数的参数。用于函数声明的时候。
 */
class FunctionParam(

    /**
     * 参数类型
     */
    var type : MCFPPType,
    /**
     * 参数的名字
     */
    var identifier: String,

    /**
     * 属于哪个函数
     */
    val function: Function,

    /**
     * 参数是否为静态的
     */
    var isStatic: Boolean = false,

    /**
     * 是否有缺省值
     */
    var hasDefault: Boolean = false
) {

    var typeIdentifier: String = type.typeName

    var defaultCommand = ArrayList<Command>()

    fun buildVar(): Var<*>{
        return Var.build(identifier, type, function)
    }

    companion object {

        /**
         * 是否是给定类型的子类型
         *
         * @param subType 子类型
         * @param parentType 父类型
         * @return
         */

        fun isSubOf(subType: MCFPPType ,parentType: MCFPPType): Boolean{
            return subType.isSubOf(parentType)
        }

        /**
         * 将一个变量列表转换为对应的字符串列表
         * @param args 参数列表
         * @return 它的字符串列表
         */
        fun getArgTypeNames(args: ArrayList<Var<*>>): ArrayList<String> {
            val qwq: ArrayList<String> = ArrayList()
            for (arg in args) {
                qwq.add(arg.type.toString())
            }
            return qwq
        }

        fun getArgTypes(args: ArrayList<Var<*>>) : ArrayList<MCFPPType>{
            val qwq: ArrayList<MCFPPType> = ArrayList()
            for (arg in args){
                qwq.add(arg.type)
            }
            return qwq
        }

        fun parseReadonlyAndNormalParamTypes(params: mcfppParser.FunctionParamsContext): Pair<ArrayList<MCFPPType>,ArrayList<MCFPPType>>{
            val r = ArrayList<MCFPPType>()
            val n = ArrayList<MCFPPType>()
            val typeScope = SimpleFieldWithType()
            //解析只读参数
            params.readOnlyParams()?.let {
                for (param in it.parameterList()?.parameter()?: emptyList()) {
                    val type = MCFPPType.parseFromIdentifier(param.type().text, typeScope)
                    r.add(type)
                    if(type == MCFPPBaseType.Type){
                        typeScope.putType(param.Identifier().text, MCFPPGenericType(param.Identifier().text, listOf(MCFPPBaseType.Any)))
                    }
                }
            }
            for (param in (params.normalParams().parameterList()?.parameter()?: emptyList())) {
                n.add(MCFPPType.parseFromIdentifier(param.type().text, typeScope))
            }
            return r to n
        }

        fun parseNormalParamTypes(params: mcfppParser.NormalParamsContext): ArrayList<MCFPPType>{
            val n = ArrayList<MCFPPType>()
            val typeScope = SimpleFieldWithType()
            for (param in (params.parameterList()?.parameter()?: emptyList())) {
                n.add(MCFPPType.parseFromIdentifier(param.type().text, typeScope))
            }
            return n
        }

        fun ArrayList<MCFPPType>.typeToStringList(): ArrayList<String> {
            val qwq: ArrayList<String> = ArrayList()
            for (param in this) {
                qwq.add(param.toString())
            }
            return qwq
        }

        /**
         * 将一个参数列表转换为对应的字符串列表
         * @return 它的字符串列表
         */
        fun ArrayList<FunctionParam>.paramToStringList(): ArrayList<String> {
            val qwq: ArrayList<String> = ArrayList()
            for (param in this) {
                qwq.add(param.typeIdentifier)
            }
            return qwq
        }
    }
}
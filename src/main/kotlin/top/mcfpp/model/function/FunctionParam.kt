package top.mcfpp.model.function

import top.mcfpp.antlr.mcfppParser
import top.mcfpp.core.lang.DataTemplateObject
import top.mcfpp.core.lang.Var
import top.mcfpp.type.MCFPPBaseType
import top.mcfpp.type.MCFPPGenericParamType
import top.mcfpp.type.MCFPPType
import top.mcfpp.model.field.SimpleFieldWithType
import top.mcfpp.type.MCFPPConcreteType
import top.mcfpp.util.LogProcessor
import top.mcfpp.util.TextTranslator
import top.mcfpp.util.TextTranslator.translate

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
    var hasDefault: Boolean = false,

    /**
     *
     */
    var isReadOnly: Boolean = false
) {

    var typeIdentifier: String = type.typeName

    var defaultVar: Var<*>? = null

    fun buildVar(): Var<*>{
        val qwq = if(isReadOnly){
            type.build(identifier, function)
        }else{
            type.buildUnConcrete(identifier, function)
        }
        if(qwq is DataTemplateObject){
            qwq.toFunctionParam()
        }
        return qwq
    }

    companion object {

        /**
         * 是否是给定类型的子类型
         *
         * @param subType 子类型
         * @param parentType 父类型
         * @return
         */

        fun isSubOf(subType: MCFPPType, parentType: MCFPPType): Boolean{
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
                    val type = MCFPPType.parseFromIdentifier(param.type().text, typeScope)?: run {
                        LogProcessor.error(TextTranslator.INVALID_TYPE_ERROR.translate(param.type().text))
                        MCFPPBaseType.Any
                    }
                    r.add(type)
                    if(type == MCFPPConcreteType.Type){
                        typeScope.putType(param.Identifier().text, MCFPPGenericParamType(param.Identifier().text, listOf(MCFPPBaseType.Any)))
                    }
                }
            }
            for (param in (params.normalParams().parameterList()?.parameter()?: emptyList())) {
                n.add(MCFPPType.parseFromIdentifier(param.type().text, typeScope)?: run {
                    LogProcessor.error(TextTranslator.INVALID_TYPE_ERROR.translate(param.type().text))
                    MCFPPBaseType.Any
                })
            }
            return r to n
        }

        fun parseNormalParamTypes(params: mcfppParser.NormalParamsContext): ArrayList<MCFPPType>{
            val n = ArrayList<MCFPPType>()
            val typeScope = SimpleFieldWithType()
            for (param in (params.parameterList()?.parameter()?: emptyList())) {
                n.add(MCFPPType.parseFromIdentifier(param.type().text, typeScope)?: run {
                    LogProcessor.error(TextTranslator.INVALID_TYPE_ERROR.translate(param.type().text))
                    MCFPPBaseType.Any
                })
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
package top.mcfpp.model.function

import top.mcfpp.Project
import top.mcfpp.antlr.mcfppParser.FunctionBodyContext
import top.mcfpp.type.MCFPPBaseType
import top.mcfpp.type.MCFPPType
import top.mcfpp.model.CompoundData
import top.mcfpp.util.StringHelper

open class ExtensionFunction: Function {

    /**
     * 创建一个函数
     * @param name 函数的标识符
     */
    constructor(name: String, owner: CompoundData, namespace: String = Project.currNamespace, returnType: MCFPPType = MCFPPBaseType.Void, context: FunctionBodyContext):super(name, namespace, returnType, context){
        this.owner = owner
    }

    override val namespaceID: String
        /**
         * 获取这个函数的命名空间id，即xxx:xxx形式。可以用于命令
         * @return 函数的命名空间id
         */
        get() {
            val re: StringBuilder =
                if(isStatic){
                    StringBuilder("$namespace:${owner!!.identifier}/ex_static/$identifier")
                }else{
                    StringBuilder("$namespace:${owner!!.identifier}/ex/$identifier")
                }
            for (p in normalParams) {
                re.append("_").append(p.typeIdentifier)
            }
            return StringHelper.toLowerCase(re.toString())
        }

    /**
     * 获取这个函数的不带有命名空间的id。仍然包含了参数信息
     */
    override val nameWithNamespace: String
        get() {
            val re: StringBuilder =
                if(isStatic){
                    StringBuilder("${owner!!.identifier}/ex_static/$identifier")
                }else{
                    StringBuilder("${owner!!.identifier}/ex/$identifier")
                }
            for (p in normalParams) {
                re.append("_").append(p.typeIdentifier)
            }
            return StringHelper.toLowerCase(re.toString())
        }

    /**
     * 函数会给它的域中的变量的minecraft标识符加上的前缀。
     */
    @get:Override
    override val prefix: String
        get() = Project.currNamespace + "_func_" + identifier + "_"

}
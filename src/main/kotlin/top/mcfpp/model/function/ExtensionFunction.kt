package top.mcfpp.model.function

import top.mcfpp.Project
import top.mcfpp.antlr.mcfppParser.FunctionBodyContext
import top.mcfpp.lib.NamespaceID
import top.mcfpp.model.compound.CompoundData
import top.mcfpp.model.compound.ObjectClass

open class ExtensionFunction: Function {

    /**
     * 创建一个函数
     * @param name 函数的标识符
     */
    constructor(name: String, owner: CompoundData, namespace: String = Project.currNamespace, context: FunctionBodyContext):super(name, namespace, context){
        this.owner = owner
    }

    override val namespaceID: NamespaceID
        get() {
            val n = if(ownerType == Companion.OwnerType.NONE){
                NamespaceID(namespace, identifier)
            }else{
                if(parentClass() is ObjectClass){
                    NamespaceID(namespace, owner!!.identifier)
                        .appendIdentifier("ex_static", false)
                }else{
                    NamespaceID(namespace, owner!!.identifier)
                        .appendIdentifier("ex")
                }
            }
            val re = StringBuilder(identifier)
            for (p in normalParams) {
                re.append("_").append(p.typeName)
            }
            return n.appendIdentifier(re.toString())
        }

    /**
     * 函数会给它的域中的变量的minecraft标识符加上的前缀。
     */
    @get:Override
    override val prefix: String
        get() = Project.currNamespace + "_func_" + identifier + "_"

}
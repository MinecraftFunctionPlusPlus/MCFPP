package top.mcfpp.model.function

import top.mcfpp.Project
import top.mcfpp.antlr.mcfppParser.FunctionBodyContext
import top.mcfpp.lib.NamespaceID
import top.mcfpp.model.compound.CompoundData

open class ExtensionFunction(
    name: String,
    owner: CompoundData,
    namespace: String = Project.currNamespace,
    context: FunctionBodyContext
) : Function(name, namespace, context) {

    init {
        this.owner = owner
    }

    override val namespaceID: NamespaceID
        get() {
            val n = if(ownerType == Companion.OwnerType.NONE){
                NamespaceID(namespace, identifier)
            }else{
                NamespaceID(namespace, owner!!.identifier).appendIdentifier("ex")
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
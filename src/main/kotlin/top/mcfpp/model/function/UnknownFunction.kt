package top.mcfpp.model.function

import top.mcfpp.Project
import top.mcfpp.model.CanSelectMember
import top.mcfpp.core.lang.UnknownVar
import top.mcfpp.core.lang.Var

open class UnknownFunction: Function {

    /**
     * 未知函数中的额外信息
     */
    var data : Any? = null

    /**
     * 创建一个全局函数，它有指定的命名空间
     * @param identifier 函数的标识符
     * @param namespace 函数的命名空间
     */
    constructor(identifier: String, namespace: String = Project.currNamespace):super(identifier,namespace, context = null){
        returnVar = UnknownVar("return")
    }

    override fun invoke(normalArgs: ArrayList<Var<*>>, caller: CanSelectMember?): Var<*> {
        addComment("[Failed to compile]invoke unknown function $namespaceID")
        return returnVar
    }

    override fun toString(): String {
        return "$identifier(${normalParamTypeList.joinToString(",")})"
    }
}
package top.mcfpp.lib

import top.mcfpp.Project
import top.mcfpp.lang.CanSelectMember
import top.mcfpp.lang.UnknownVar
import top.mcfpp.lang.Var

class UnknownFunction: Function {
    /**
     * 创建一个全局函数，它有指定的命名空间
     * @param identifier 函数的标识符
     * @param namespace 函数的命名空间
     */
    constructor(identifier: String, namespace: String = Project.currNamespace):super(identifier,namespace){
        returnVar = UnknownVar("return")
    }

    override fun invoke(args: ArrayList<Var<*>>, caller: CanSelectMember?) {
        Function.addCommand("[Failed to compile]invoke unknown function $namespaceID")
    }
}
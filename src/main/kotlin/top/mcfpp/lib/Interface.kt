package top.mcfpp.lib

import top.mcfpp.Project
import top.mcfpp.annotations.InsertCommand

open class Interface : CompoundData {

    /**
     * 生成一个接口。它拥有指定的标识符和默认的命名空间
     * @param identifier 类的标识符
     */
    constructor(identifier: String) : this(identifier, Project.currNamespace)

    /**
     * 生成一个类，它拥有指定的标识符和命名空间
     * @param identifier 类的标识符
     * @param namespace 类的命名空间
     */
    @InsertCommand
    constructor(identifier: String, namespace: String) {
        this.identifier = identifier
        this.namespace = namespace
    }

    companion object{
        /**
         * 当前编译器正在编译的接口
         */
        var currInterface: Interface? = null
    }
}
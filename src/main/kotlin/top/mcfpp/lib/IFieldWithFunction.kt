package top.mcfpp.lib

import top.mcfpp.lang.type.MCFPPType

interface IFieldWithFunction: IField {

    /**
     * 根据所给的函数名和参数获取一个函数
     * @param key 函数名
     * @param argsTypes 参数类型
     * @return 如果此缓存中存在这个函数，则返回这个函数的对象，否则返回null
     */
    fun getFunction(key: String, argsTypes: List<MCFPPType>): Function?

    /**
     * 添加一个函数
     *
     * @param function
     */
    fun addFunction(function: Function, force: Boolean): Boolean

    /**
     * 这个域中是否有这个函数
     *
     * @param function
     * @return
     */
    fun hasFunction(function: Function): Boolean

    fun forEachFunction(operation: (Function) -> Any?)
}
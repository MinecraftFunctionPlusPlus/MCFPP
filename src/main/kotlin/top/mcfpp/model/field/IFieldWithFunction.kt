package top.mcfpp.model.field

import top.mcfpp.core.lang.Var
import top.mcfpp.model.function.Function

interface IFieldWithFunction: IField {

    /**
     * 根据所给的函数名和参数获取一个函数
     * @param key 函数名
     * @param normalArgs 参数类型
     * @return 如果此缓存中存在这个函数，则返回这个函数的对象，否则返回[top.mcfpp.model.function.UnknownFunction]
     */
    fun getFunction(key: String, readOnlyArgs: List<Var<*>>, normalArgs: List<Var<*>>): Function

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
    fun hasFunction(function: Function, considerParent: Boolean = true): Boolean

    fun forEachFunction(operation: (Function) -> Any?)
}
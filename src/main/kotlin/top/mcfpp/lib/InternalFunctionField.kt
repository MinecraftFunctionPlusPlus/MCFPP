package top.mcfpp.lib

import top.mcfpp.lang.Var


/**
 * 内联函数的域。内联函数的域的父级可能是一个内联函数域，也可能是一个普通函数的域。
 *
 * @see InternalFunction
 */
class InternalFunctionField: FunctionField {

    /**
     * 创建一个缓存，并指定它的父级
     * @param parent 父级缓存。若没有则设置为null
     * @param cacheContainer 此缓存所在的容器
     */
    constructor(parent: IField?, cacheContainer: FieldContainer?):super(parent,cacheContainer)

    /**
     * 复制一个域。
     * @param functionField 原来的域
     */
    constructor(functionField: FunctionField):super(functionField)


    /**
     * 从域中取出一个变量。如果此域中没有，则从父域中寻找。同时记录当前栈的级数，也就是向父级追溯了多少级栈
     * @param key 变量的标识符
     * @return 变量的对象。若不存在，则返回null。
     */
    override fun getVar(key: String): Var? {
        if (containVar(key)) {
            val re: Var? = vars.getOrDefault(key, null)
            re!!.stackIndex = 0
            return re
        }
        val re: Var? = (parent as IFieldWithVar).getVar(key)
        if (re != null) {
            re.stackIndex++
        }
        return re
    }

    override fun clone(): InternalFunctionField{
        return InternalFunctionField(this)
    }
}
package top.mcfpp.model.field

import top.mcfpp.`var`.lang.Var

interface IFieldWithVar : IField {

    /**
     * 向此缓存中添加一个新的变量键值对。如果已存在此对象，将不会进行覆盖。
     * @param key 变量的标识符
     * @param var 变量的对象
     * @param forced 是否在存在此变量的情况下仍然强制将原变量替换
     * @return 如果缓存中已经存在此对象，则返回false，否则返回true。
     */
    fun putVar(key: String, `var`: Var<*>, forced: Boolean = false): Boolean

    /**
     * 从缓存中取出一个变量。如果此缓存中没有，则从父缓存中寻找。
     * @param key 变量的标识符
     * @return 变量的对象。若不存在，则返回null。
     */
    fun getVar(key: String): Var<*>?

    /**
     * 缓存中是否包含某个变量
     * @param id 变量名
     * @return 如果包含则返回true，否则返回false
     */
    fun containVar(id: String): Boolean

    /**
     * 移除缓存中的某个变量
     *
     * @param id 变量名
     * @return 若变量存在，则返回被移除的变量，否则返回空
     */
    fun removeVar(id : String): Var<*>?

    fun forEachVar(action: (Var<*>) -> Unit)

    /**
     * 获取此缓存中的全部变量。不会从父缓存搜索。
     * @return 一个包含了此缓存全部变量的集合。
     */
    val allVars: Collection<Var<*>>
}
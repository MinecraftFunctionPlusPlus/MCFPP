package top.mcfpp.lib.field

import top.mcfpp.lang.Var

class SimpleFieldWithVar : IFieldWithVar {

    /**
     * 变量
     */
    protected val vars: HashMap<String, Var<*>> = HashMap()
    /**
     * 向此缓存中添加一个新的变量键值对。如果已存在此对象，将不会进行覆盖。
     * @param key 变量的标识符
     * @param var 变量的对象
     * @return 如果缓存中已经存在此对象，则返回false，否则返回true。
     */
    override fun putVar(key: String, `var`: Var<*>, forced: Boolean): Boolean {
        if(forced){
            vars[key] = `var`
            return true
        }
        return if (vars.containsKey(key)) {
            false
        } else {
            vars[key] = `var`
            true
        }
    }

    /**
     * 从缓存中取出一个变量。如果此缓存中没有，则从父缓存中寻找。
     * TODO 这里真的有从父缓存找吗
     * @param key 变量的标识符
     * @return 变量的对象。若不存在，则返回null。
     */
    override fun getVar(key: String): Var<*>? {
        val re: Var<*>? = vars.getOrDefault(key, null)
        if (re != null) {
            re.stackIndex = 0
        }
        return re
    }


    override val allVars: Collection<Var<*>>
        /**
         * 获取此缓存中的全部变量。不会从父缓存搜索。
         * @return 一个包含了此缓存全部变量的集合。
         */
        get() = vars.values

    /**
     * 缓存中是否包含某个变量
     * @param id 变量名
     * @return 如果包含则返回true，否则返回false
     */
    override fun containVar(id: String): Boolean {
        return vars.containsKey(id)
    }

    /**
     * 移除缓存中的某个变量
     *
     * @param id 变量名
     * @return 若变量存在，则返回被移除的变量，否则返回空
     */
    override fun removeVar(id : String): Var<*>?{
        return vars.remove(id)
    }

    override fun forEachVar(action: (Var<*>) -> Any?) {
        for (v in vars.values){
            action(v)
        }
    }
}
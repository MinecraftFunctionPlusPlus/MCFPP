package top.mcfpp.lib

import org.jetbrains.annotations.Nullable
import top.mcfpp.lang.Var
import java.util.HashMap

class ClassField : IField {
    /**
     * 变量
     */
    private val vars: HashMap<String, Var> = HashMap()

    fun forEachVar(operation: (Var) -> Any?){
        for (`var` in vars.values){
            operation(`var`)
        }
    }

    /**
     * 函数
     */
    private var functions: ArrayList<Function> = ArrayList()

    fun forEachFunction(operation: (Function) -> Any?){
        for (function in functions){
            operation(function)
        }
    }

    /**
     * 父级域。
     */
    @Nullable
    var parent: IField?

    /**
     * 这个域在哪一个容器中
     */
    @Nullable
    var container: FieldContainer? = null
    /**
     * 创建一个域，并指定它的父级
     * @param parent 父级域。若没有则设置为null
     * @param cacheContainer 此域所在的容器
     */
    constructor(parent: IField?, cacheContainer: FieldContainer?) {
        this.parent = parent
        container = cacheContainer
    }

    /**
     * 复制一个域。
     * @param field 原来的缓存
     */
    constructor(field: ClassField) {
        parent = field.parent
        //变量复制
        for (key in field.vars.keys) {
            val `var`: Var? = field.vars[key]
            vars[key] = `var`!!.clone() as Var
        }
        functions.addAll(field.functions)
    }
    //region Var
    /**
     * 向此缓存中添加一个新的变量键值对。如果已存在此对象，将不会进行覆盖。
     * @param key 变量的标识符
     * @param var 变量的对象
     * @param forced 是否在存在此变量的情况下仍然强制将原变量替换
     * @return 如果缓存中已经存在此对象，则返回false，否则返回true。
     */
    fun putVar(key: String, `var`: Var, forced: Boolean = false): Boolean {
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
     * @param key 变量的标识符
     * @return 变量的对象。若不存在，则返回null。
     */
    fun getVar(key: String): Var? {
        return vars.getOrDefault(key, null)
    }


    val allVars: Collection<Var>
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
    fun containVar(id: String): Boolean {
        return vars.containsKey(id)
    }

    /**
     * 移除缓存中的某个变量
     *
     * @param id 变量名
     * @return 若变量存在，则返回被移除的变量，否则返回空
     */
    fun removeVar(id : String): Var?{
        return vars.remove(id)
    }

//endregion

    /**
     * 根据所给的函数名和参数获取一个函数
     * @param key 函数名
     * @param argsTypes 参数类型
     * @param namespace 命名空间
     * @return 如果此缓存中存在这个函数，则返回这个函数的对象，否则返回null
     */
    @Nullable
    fun getFunction(namespace: String, key: String, argsTypes: List<String>): Function? {
        for (f in functions) {
            if (f.namespace == namespace && f.name == key && f.params.size == argsTypes.size) {
                if (f.params.size == 0) {
                    return f
                }
                var hasFoundFunc = true
                //参数比对
                for (i in argsTypes.indices) {
                    if (argsTypes[i] != f.params[i].type) {
                        hasFoundFunc = false
                        break
                    }
                }
                if (hasFoundFunc) {
                    return f
                }
            }
        }
        return null
    }

    /**
     * 添加一个函数
     *
     * @param function
     */
    fun addFunction(function: Function){
        functions.add(function)
    }

    fun hasFunction(function: Function): Boolean{
        return functions.contains(function)
    }


}
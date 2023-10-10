package top.mcfpp.lib

import org.jetbrains.annotations.Nullable
import top.mcfpp.lang.Var
import java.util.HashMap

/**
 * 一个域，储存了字段和方法。
 *
 */
class CompoundDataField : IFieldWithFunction, IFieldWithVar {

    /**
     * 字段
     */
    private val vars: HashMap<String, Var> = HashMap()

    /**
     * 遍历每一个字段
     *
     * @param operation 要对字段执行的操作
     * @receiver
     */
    fun forEachVar(operation: (Var) -> Any?){
        for (`var` in vars.values){
            operation(`var`)
        }
    }

    /**
     * 方法
     */
    private var functions: ArrayList<Function> = ArrayList()

    /**
     * 遍历每一个方法
     *
     * @param operation 要对方法进行的操作
     * @receiver
     */
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
    constructor(field: CompoundDataField) {
        parent = field.parent
        //变量复制
        for (key in field.vars.keys) {
            val `var`: Var? = field.vars[key]
            vars[key] = `var`!!.clone() as Var
        }
        functions.addAll(field.functions)
    }
    //region Var
    override fun putVar(key: String, `var`: Var, forced: Boolean): Boolean {
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

    override fun getVar(key: String): Var? {
        return vars.getOrDefault(key, null)
    }


    override val allVars: Collection<Var>
        get() = vars.values

    override fun containVar(id: String): Boolean {
        return vars.containsKey(id)
    }

    override fun removeVar(id : String): Var?{
        return vars.remove(id)
    }

//endregion

    //region Function
    @Nullable
    override fun getFunction(key: String, argsTypes: List<String>): Function? {
        for (f in functions) {
            if (f.identifier == key && f.params.size == argsTypes.size) {
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

    override fun addFunction(function: Function, force: Boolean): Boolean{
        if(hasFunction(function)){
            if(force){
                functions[functions.indexOf(function)] = function
                return true
            }
            return false
        }
        functions.add(function)
        return true
    }

    override fun hasFunction(function: Function): Boolean{
        return functions.contains(function)
    }
    //endregion
}
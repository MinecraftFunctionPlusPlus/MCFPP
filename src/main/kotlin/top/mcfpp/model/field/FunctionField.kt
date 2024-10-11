package top.mcfpp.model.field

import org.jetbrains.annotations.Nullable
import top.mcfpp.core.lang.Var
import top.mcfpp.type.MCFPPType
import top.mcfpp.model.FieldContainer
import kotlin.collections.HashMap
import kotlin.collections.HashSet

/**
 * 一个函数的域。储存了这个函数中的变量
 *
 * @constructor Create empty Function field
 */
open class FunctionField : IFieldWithVar, IFieldWithType {

    val fieldVarSet = HashSet<String>()

    val fieldTypeSet = HashSet<String>()

    /**
     * 变量
     */
    protected val vars: LinkedHashMap<String, Var<*>> = LinkedHashMap()

    /**
     * 类型
     */
    protected val types : HashMap<String, MCFPPType> = HashMap()

    /**
     * 父级域。函数的父级域可能是全局，也可能是类
     */
    var parent : IField?

    /**
     * 这个缓存在哪一个容器中
     */
    @Nullable
    var container: FieldContainer? = GlobalField

    /**
     * 创建一个缓存，并指定它的父级
     * @param parent 父级缓存。若没有则设置为null
     * @param cacheContainer 此缓存所在的容器
     */
    constructor(parent: IField?, cacheContainer: FieldContainer?) {
        this.parent = parent
        container = cacheContainer
    }


    //region Var
    /**
     * 向此缓存中添加一个新的变量键值对。如果已存在此对象，将不会进行覆盖。
     * @param key 变量的标识符
     * @param var 变量的对象
     * @return 如果缓存中已经存在此对象，则返回false，否则返回true。
     */
    override fun putVar(key: String, `var`: Var<*>, forced: Boolean): Boolean {
        fieldVarSet.add(key)
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

    fun replaceVar(key: String, `var`: Var<*>): Boolean {
        return if (vars.containsKey(key)) {
            vars[key] = `var`
            true
        } else {
            false
        }
    }

    /**
     * 从缓存中取出一个变量。如果此缓存中没有，则从父缓存中寻找。
     * TODO 这里真的有从父缓存找吗，我是说比如这个函数的类什么的，InternalFunction那边是对的
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
        fieldVarSet.remove(id)
        return vars.remove(id)
    }

    override fun forEachVar(action: (Var<*>) -> Unit) {
        for (v in vars.values){
            action(v)
        }
    }

    override fun putType(key: String, type: MCFPPType, forced: Boolean): Boolean {
        fieldTypeSet.add(key)
        if(forced){
            types[key] = type
            return true
        }
        return if (types.containsKey(key)) {
            false
        } else {
            types[key] = type
            true
        }
    }

    override fun getType(key: String) : MCFPPType? {
        var re = types.getOrDefault(key, null)
        if(re == null && parent is IFieldWithType){
            re = (parent as IFieldWithType).getType(key)
        }
        return re
    }
    override fun containType(id: String): Boolean {
        return types.containsKey(id)
    }

    override fun removeType(id: String): MCFPPType? {
        return types.remove(id)
    }

    override fun forEachType(action: (MCFPPType) -> Any?) {
        for (t in types.values){
            action(t)
        }
    }

    override val allTypes: Collection<MCFPPType>
        get() = types.values

    open fun clone(): FunctionField {
        return clone(this)
    }

    companion object{
        /**
         * 复制一个域。
         * @param functionField 原来的域
         */
        fun clone(functionField: FunctionField): FunctionField {
            val newFunctionField = FunctionField(functionField.parent,null)
            //变量复制
            for (key in functionField.vars.keys) {
                val `var`: Var<*>? = functionField.vars[key]
                newFunctionField.vars[key] = `var`!!.clone()
            }
            newFunctionField.fieldVarSet.addAll(functionField.fieldVarSet)
            //类型复制
            for (key in functionField.types.keys) {
                val type = functionField.types[key]
                newFunctionField.types[key] = type!!
            }
            newFunctionField.fieldTypeSet.addAll(functionField.fieldTypeSet)
            return newFunctionField
        }
    }

//endregion
}
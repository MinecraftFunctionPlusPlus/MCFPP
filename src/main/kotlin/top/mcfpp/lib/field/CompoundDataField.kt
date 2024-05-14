package top.mcfpp.lib.field

import org.jetbrains.annotations.Nullable
import top.mcfpp.lang.Var
import top.mcfpp.lang.type.MCFPPType
import top.mcfpp.lib.*
import top.mcfpp.lib.function.Function
import top.mcfpp.lib.function.UnknownFunction
import top.mcfpp.lib.generic.Generic
import top.mcfpp.util.LazyWrapper
import java.util.HashMap

/**
 * 一个域，储存了字段和方法。
 *
 */
class CompoundDataField : IFieldWithFunction, IFieldWithVar, IFieldWithType {

    /**
     * 字段
     */
    private val vars: HashMap<String, LazyWrapper<Var<*>>> = HashMap()

    /**
     * 类型
     */
    private val types : HashMap<String, MCFPPType> = HashMap()

    /**
     * 遍历每一个字段
     *
     * @param action 要对字段执行的操作
     * @receiver
     */
    override fun forEachVar(action: (Var<*>) -> Any?){
        for (`var` in vars.values){
            //TODO 未检查获取变量的情况
            action(`var`.get()!!)
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
    override fun forEachFunction(operation: (Function) -> Any?){
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
            val `var`: LazyWrapper<Var<*>>? = field.vars[key]
            vars[key] = `var`!!.clone()
        }
        functions.addAll(field.functions)
    }

    //region Var<*>
    override fun putVar(key: String, `var`: Var<*>, forced: Boolean): Boolean {
        if(forced){
            vars[key] = LazyWrapper(`var`)
            return true
        }
        return if (vars.containsKey(key)) {
            false
        } else {
            vars[key] = LazyWrapper(`var`)
            true
        }
    }

    override fun getVar(key: String): Var<*>? {
        return vars.getOrDefault(key, null)?.get()
    }


    override val allVars: Collection<Var<*>>
        get() {
            val vs = ArrayList<Var<*>>()
            for (lv in vars.values){
                vs.add(lv.get()!!)
            }
            return vs
        }

    override fun containVar(id: String): Boolean {
        return vars.containsKey(id)
    }

    override fun removeVar(id : String): Var<*>?{
        return vars.remove(id)?.get()
    }

//endregion

    override fun putType(key: String, type: MCFPPType, forced: Boolean): Boolean {
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

    override fun getType(key: String) = types.getOrDefault(key, null)

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


    //region Function
    @Nullable
    override fun getFunction(key: String, readOnlyParams: List<MCFPPType>, normalParams: List<MCFPPType>): Function {
        for (f in functions) {
            if(f is Generic<*> && f.isSelf(key, readOnlyParams, normalParams)){
                return f
            }
            if(f.isSelf(key, normalParams)){
                return f
            }
        }
        return UnknownFunction(key)
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
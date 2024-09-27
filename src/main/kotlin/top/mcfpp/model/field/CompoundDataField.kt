package top.mcfpp.model.field

import org.jetbrains.annotations.Nullable
import top.mcfpp.core.lang.DataTemplateObject
import top.mcfpp.core.lang.Var
import top.mcfpp.type.MCFPPType
import top.mcfpp.model.*
import top.mcfpp.model.accessor.Property
import top.mcfpp.model.function.Function
import top.mcfpp.model.function.UnknownFunction
import top.mcfpp.model.generic.Generic
import java.util.HashMap

/**
 * 一个域，储存了字段和方法。
 *
 */
class CompoundDataField : IFieldWithFunction, IFieldWithVar, IFieldWithType, IFieldWithProperty {

    /**
     * 字段
     */
    private val vars: HashMap<String, Var<*>> = HashMap()

    /**
     * 类型
     */
    private val types : HashMap<String, MCFPPType> = HashMap()

    /**
     *
     */
    private val property: HashMap<String, Property> = HashMap()

    /**
     * 遍历每一个字段
     *
     * @param action 要对字段执行的操作
     * @receiver
     */
    override fun forEachVar(action: (Var<*>) -> Unit){
        for (`var` in vars.values){
            action(`var`)
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
    var parent: ArrayList<IField?>

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
    constructor(parent: ArrayList<IField?>, cacheContainer: FieldContainer?) {
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
            val `var`: Var<*> = field.vars[key]!!
            vars[key] = `var`.clone()
        }
        //函数
        functions.addAll(field.functions)
        //类型
        types.putAll(field.types)
        //属性
        property.putAll(field.property)
    }

    //region Var<*>
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

    override fun getVar(key: String): Var<*>? {
        return vars.getOrDefault(key, null)
    }


    override val allVars: Collection<Var<*>>
        get() {
            val vs = ArrayList<Var<*>>()
            for (lv in vars.values){
                vs.add(lv)
            }
            return vs
        }

    override fun containVar(id: String): Boolean {
        return vars.containsKey(id)
    }

    override fun removeVar(id : String): Var<*>?{
        return vars.remove(id)
    }

    //endregion

    //region type
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

    //endregion

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
        for (f in functions) {
            if(f is Generic<*> && f.isSelfWithDefaultValue(key, readOnlyParams, normalParams)){
                return f
            }
            if(f.isSelfWithDefaultValue(key, normalParams)){
                return f
            }
        }
        parent.forEach {
            if(it is IFieldWithFunction){
                val re = it.getFunction(key, readOnlyParams, normalParams)
                if(re !is UnknownFunction) return re
            }
        }
        return UnknownFunction(key)
    }

    override fun addFunction(function: Function, force: Boolean): Boolean{
        if(hasFunction(function, false)){
            if(force){
                functions[functions.indexOf(function)] = function
                return true
            }
            return false
        }
        functions.add(function)
        return true
    }

    override fun hasFunction(function: Function, considerParent: Boolean): Boolean{
        val qwq = functions.contains(function)
        return if(considerParent && !qwq && parent.isNotEmpty()) {
            parent.any { it is IFieldWithFunction && it.hasFunction(function, true) }
        }else{
            qwq
        }
    }
    //endregion

    //region property
    override fun putProperty(key: String, property: Property, forced: Boolean): Boolean {
        if(forced){
            this.property[key] = property
            return true
        }
        return if (this.property.containsKey(key)) {
            false
        } else {
            this.property[key] = property
            true
        }
    }

    override fun getProperty(key: String): Property? {
        return property.getOrDefault(key, null)
    }

    override fun containProperty(id: String): Boolean {
        return property.containsKey(id)
    }

    override fun removeProperty(id: String): Property? {
        return property.remove(id)
    }

    override fun forEachProperty(action: (Property) -> Unit) {
        for (p in property.values){
            action(p)
        }
    }

    override val allProperties: Collection<Property>
        get() {
            val ps = ArrayList<Property>()
            for (lv in property.values){
                ps.add(lv)
            }
            return ps
        }
    //endregion

    fun createDataTemplateInstance(selector: DataTemplateObject): CompoundDataField{
        val re = CompoundDataField(this)
        re.allVars.forEach {
            it.parent = selector
            it.name = selector.identifier + "_" + it.identifier
            it.nbtPath.pathList.removeLast()
            it.nbtPath.memberIndex(selector.identifier)
            it.nbtPath.memberIndex(it.identifier)
        }
        return re
    }
}
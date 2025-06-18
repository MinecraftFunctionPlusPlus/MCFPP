package top.mcfpp.model.field

import org.jetbrains.annotations.Nullable
import top.mcfpp.core.lang.Var
import top.mcfpp.model.compound.*
import top.mcfpp.model.function.Function
import top.mcfpp.model.function.UnknownFunction
import top.mcfpp.type.MCFPPType

/**
 * 一个域。在编译过程中，编译器读取到的变量，函数等会以键值对的方式储存在其中。键为函数的id或者变量的
 * 标识符，而值则是这个函数或者变量的对象。
 *
 * 同一个域中的函数
 *
 * 域应该是一个链式的结构，主要分为如下几种情况：<br></br>
 * * 类的静态变量 ---> 类的成员变量 --> 函数 ---> 匿名内部函数<br></br>
 *
 * * 类的静态变量 ---> 静态函数 ---> 匿名内部函数<br></br>
 *
 * * 函数 ---> 匿名内部函数<br></br>
 * 寻找变量的时候，应当从当前的作用域开始寻找。<br></br>
 *
 * 变量和类都分别储存在一张哈希表中，键名即它在声明的时候的名字，而值则代表了它的对象。
 * 值得注意的是，变量声明时的名字虽然和最终编译出的名字有关，但不相同。
 *
 * 函数储存在一个列表中
 */
class NamespaceField: SimpleLibField{

    private var fileFields = ArrayList<FileField>()

    constructor(): super(){
        parent.add(GlobalField)
    }

    /**
     * 复制一个缓存。
     * @param cache 原来的缓存
     */
    constructor(cache: NamespaceField) : super(cache) {
        parent = cache.parent
        fileFields = cache.fileFields
    }

    //region function
    /**
     * 根据所给的函数名和参数获取一个函数
     * @param key 函数名
     * @param normalArgs 参数类型
     * @return 如果此缓存中存在这个函数，则返回这个函数的对象，否则返回null
     */
    @Nullable
    override fun getFunction(key: String, readOnlyArgs: List<Var<*>>, normalArgs: List<Var<*>>): Function {
        var re = super.getFunction(key, readOnlyArgs, normalArgs)
        var i = 0
        while (re is UnknownFunction && i < fileFields.size){
            re = fileFields[i].getFunction(key, readOnlyArgs, normalArgs)
            i++
        }
        return re
    }

    override fun hasFunction(function: Function, considerParent: Boolean): Boolean{
        return functions.containsKey(function.identifier) && functions[function.identifier]!!.contains(function) || fileFields.any { it.hasFunction(function, considerParent) }
    }
    //endregion

    //region class
    override fun forEachClass(operation: (Class) -> Any?){
        for (`class` in classes.values()){
            operation(`class`)
            fileFields.forEach { it.forEachClass(operation) }
        }
    }

    /**
     * 根据所给的id获取一个类
     *
     * @param identifier
     */
    override fun getClass(identifier: String, readOnlyParam: List<MCFPPType>): GenericClass? {
        super.getClass(identifier, readOnlyParam)?.let { return it }
        for(field in fileFields){
            field.getClass(identifier, readOnlyParam)?.let { return it }
        }
        return null
    }

    override fun getClass(identifier: String): Class? {
        super.getClass(identifier)?.let { return it }
        for(field in fileFields){
            field.getClass(identifier)?.let { return it }
        }
        return null
    }

    override fun hasClass(cls: Class): Boolean{
        return classes.containsValue(cls) || fileFields.any { it.hasClass(cls) }
    }

    override fun hasNotGenericClass(cls: String): Boolean{
        return classes.values().any { it.identifier == cls && it !is GenericClass } || fileFields.any { it.hasNotGenericClass(cls) }
    }

    override fun hasNotGenericClass(cls: Class): Boolean{
        return classes.values().any { it == cls && it !is GenericClass } || fileFields.any { it.hasNotGenericClass(cls) }
    }


    override fun hasClass(identifier: String): Boolean{
        return classes.containsKey(identifier) || fileFields.any { it.hasClass(identifier) }
    }
    //endregion

    //region template

    override fun forEachTemplate(operation: (DataTemplate) -> Any?){
        for (t in template.values){
            operation(t)
            fileFields.forEach { it.forEachTemplate(operation) }
        }
    }

    /**
     * 获取一个模板。可能不存在
     *
     * @param identifier 模板的标识符
     * @return 获取到的模板。如果不存在，则返回null
     */
    override fun getTemplate(identifier: String): DataTemplate? {
        return template[identifier]?:fileFields.firstOrNull { it.hasTemplate(identifier) }?.getTemplate(identifier)
    }

    /**
     * 是否存在此模板
     *
     * @param identifier 模板的标识符
     * @return
     */
    override fun hasTemplate(identifier: String): Boolean {
        return template.containsKey(identifier) || fileFields.any { it.hasTemplate(identifier) }
    }

    /**
     * 是否存在此模板
     *
     * @param template 模板
     * @return
     */
    override fun hasTemplate(template: DataTemplate): Boolean {
        return this.template.containsKey(template.identifier) || fileFields.any { it.hasTemplate(template) }
    }

    //endregion

    //region interface

    override fun forEachInterface(operation: (Interface) -> Any?){
        for(`interface` in interfaces.values){
            operation(`interface`)
            fileFields.forEach { it.forEachInterface(operation) }
        }
    }

    /**
     * 获取一个接口。可能不存在
     *
     * @param identifier 接口的标识符
     * @return 获取到的接口。如果不存在，则返回null
     */
    override fun getInterface(identifier: String): Interface? {
        return interfaces[identifier] ?: fileFields.firstOrNull { it.hasInterface(identifier) }?.getInterface(identifier)
    }

    /**
     * 是否存在此接口
     *
     * @param identifier 接口的标识符
     * @return
     */
    override fun hasInterface(identifier: String): Boolean {
        return interfaces.containsKey(identifier) || fileFields.any { it.hasInterface(identifier) }
    }

    /**
     * 是否存在此接口
     *
     * @param itf 接口
     * @return
     */
    override fun hasInterface(itf: Interface): Boolean {
        return interfaces.containsKey(itf.identifier) || fileFields.any { it.hasInterface(itf) }
    }
    //endregion

    override fun hasDeclaredType(identifier: String): Boolean{
        return hasEnum(identifier) || hasTemplate(identifier) || hasInterface(identifier) || hasClass(identifier)
                || fileFields.any { it.hasDeclaredType(identifier) }
    }

    override fun hasDeclaredType(type: CompoundData): Boolean{
        if(type !is GenericClass){
            val identifier = type.identifier
            return hasEnum(identifier) || hasTemplate(identifier) || hasInterface(identifier) || hasNotGenericClass(identifier)
                    || fileFields.any { it.hasDeclaredType(identifier) }
        }else{
            return hasClass(type) || fileFields.any { it.hasClass(type) }
        }
    }

    override fun getDeclaredType(identifier: String): CompoundData?{
        return getEnum(identifier) ?: getTemplate(identifier) ?: getInterface(identifier) ?: getClass(identifier)
        ?: fileFields.firstOrNull { it.hasDeclaredType(identifier) }?.getDeclaredType(identifier)
    }

    override fun getType(key: String): MCFPPType? {
        return (getEnum(key) ?: getTemplate(key) ?: getInterface(key) ?: getClass(key))?.getType()?: typeAlias[key]
        ?: fileFields.firstOrNull { it.containType(key) }?.getType(key)
    }

    override fun containType(id: String): Boolean {
        return hasEnum(id) || hasTemplate(id) || hasInterface(id) || hasClass(id) || typeAlias.containsKey(id)
                || fileFields.any { it.containType(id) }
    }

    override fun forEachType(action: (MCFPPType) -> Any?) {
        forEachEnum { action(it.getType()) }
        forEachTemplate { action(it.getType()) }
        forEachInterface { action(it.getType()) }
        forEachClass { action(it.getType()) }
        typeAlias.values.forEach { action(it) }
        fileFields.forEach { it.forEachType(action) }
    }

    override val allTypes: Collection<MCFPPType>
        get() {
            val list = mutableListOf<MCFPPType>()
            forEachEnum { list.add(it.getType()) }
            forEachTemplate { list.add(it.getType()) }
            forEachInterface { list.add(it.getType()) }
            forEachClass { list.add(it.getType()) }
            list.addAll(typeAlias.values)
            fileFields.forEach { list.addAll(it.allTypes) }
            return list
        }
}
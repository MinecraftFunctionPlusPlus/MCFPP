package top.mcfpp.model.scope

import top.mcfpp.core.lang.Var
import top.mcfpp.model.annotation.Annotation
import top.mcfpp.model.compound.CompoundData
import top.mcfpp.model.compound.DataTemplate
import top.mcfpp.model.compound.Enum
import top.mcfpp.model.compound.Interface
import top.mcfpp.model.function.Function
import top.mcfpp.type.*

open class SimpleLibScope
    : SimpleScopeWithFunction, IScopeWithTemplate, IScopeWithInterface, IScopeWithType,
    SimpleScopeWithEnum,
    SimpleScopeWithObject,
    SimpleScopeWithAnnotation,
    SimpleScopeWithVar
{
    /**
     * 变量
     */
    final override val vars: HashMap<String, Var<*>> = HashMap()

    /**
     * 函数
     */
    final override var functions: HashMap<String, ArrayList<Function>> = HashMap()

    /**
     * 模板
     */
    protected var template: HashMap<String, DataTemplate> = HashMap()

    /**
     * 接口
     */
    protected var interfaces: HashMap<String, Interface> = HashMap()

    /**
     * 类型别名
     */
    protected var typeAlias: HashMap<String, MCFPPType> = HashMap()

    final override var enums: ArrayList<Enum> = ArrayList()

    final override var annotations: HashMap<String, java.lang.Class<out Annotation>> = HashMap()

    final override var objects: ArrayList<CompoundData> = ArrayList()

    final override var parent: ArrayList<IScope?> = ArrayList()

    constructor()

    /**
     * 复制一个
     * @param cache 原来的缓存
     */
    constructor(cache: SimpleLibScope) {
        //变量复制
        for (key in cache.vars.keys) {
            val `var`: Var<*>? = cache.vars[key]
            vars[key] = `var`!!.clone()
        }
        functions.putAll(cache.functions)
    }

    //region template

    override fun forEachTemplate(operation: (DataTemplate) -> Any?){
        for (t in template.values){
            operation(t)
        }
    }

    /**
     * 向域中添加一个模板
     *
     * @param identifier 模板的标识符
     * @param template 模板
     * @param force 是否强制添加。如果为true，则即使已经添加过相同标识符的模板，也会覆盖原来的模板进行添加。
     * @return 是否添加成功。如果已经存在相同标识符的模板，且不是强制添加则为false
     */
    override fun addTemplate(identifier: String, template: DataTemplate, force: Boolean): Boolean {
        return if (force){
            this.template[identifier] = template
            true
        }else{
            if(!this.template.containsKey(identifier)){
                this.template[identifier] = template
                true
            }else{
                false
            }
        }
    }

    /**
     * 移除一个模板
     *
     * @param identifier 这个模板的标识符
     * @return 是否移除成功。如果不存在此模板，则返回false
     */
    override fun removeTemplate(identifier: String): DataTemplate? {
        return if(template.containsKey(identifier)) {
            template.remove(identifier)
        }else{
            null
        }
    }

    /**
     * 获取一个模板。可能不存在
     *
     * @param identifier 模板的标识符
     * @return 获取到的模板。如果不存在，则返回null
     */
    override fun getTemplate(identifier: String): DataTemplate? {
        return template[identifier]
    }

    /**
     * 是否存在此模板
     *
     * @param identifier 模板的标识符
     * @return
     */
    override fun hasTemplate(identifier: String): Boolean {
        return template.containsKey(identifier)
    }

    /**
     * 是否存在此模板
     *
     * @param template 模板
     * @return
     */
    override fun hasTemplate(template: DataTemplate): Boolean {
        return this.template.containsKey(template.identifier)
    }

    //endregion

    //region interface

    override fun forEachInterface(operation: (Interface) -> Any?){
        for(`interface` in interfaces.values){
            operation(`interface`)
        }
    }

    /**
     * 向域中添加一个接口
     *
     * @param identifier 接口的标识符
     * @param itf 接口
     * @param force 是否强制添加。如果为true，则即使已经添加过相同标识符的接口，也会覆盖原来的接口进行添加。
     * @return 是否添加成功。如果已经存在相同标识符的接口，且不是强制添加则为false
     */
    override fun addInterface(identifier: String, itf: Interface, force: Boolean): Boolean {
        return if (force){
            interfaces[identifier] = itf
            true
        }else{
            if(!interfaces.containsKey(identifier)){
                interfaces[identifier] = itf
                true
            }else{
                false
            }
        }
    }

    /**
     * 移除一个接口
     *
     * @param identifier 这个接口的标识符
     * @return 是否移除成功。如果不存在此接口，则返回false
     */
    override fun removeInterface(identifier: String): Interface? {
        return if(interfaces.containsKey(identifier)) {
            interfaces.remove(identifier)
        }else{
            null
        }
    }

    /**
     * 获取一个接口。可能不存在
     *
     * @param identifier 接口的标识符
     * @return 获取到的接口。如果不存在，则返回null
     */
    override fun getInterface(identifier: String): Interface? {
        return interfaces[identifier]
    }

    /**
     * 是否存在此接口
     *
     * @param identifier 接口的标识符
     * @return
     */
    override fun hasInterface(identifier: String): Boolean {
        return interfaces.containsKey(identifier)
    }

    /**
     * 是否存在此接口
     *
     * @param itf 接口
     * @return
     */
    override fun hasInterface(itf: Interface): Boolean {
        return interfaces.containsKey(itf.identifier)
    }
    //endregion

    open fun hasDeclaredType(identifier: String): Boolean{
        return hasEnum(identifier) || hasTemplate(identifier) || hasInterface(identifier)
    }

    open fun hasDeclaredType(type: CompoundData): Boolean{
        val identifier = type.identifier
        return hasEnum(identifier) || hasTemplate(identifier) || hasInterface(identifier)
    }

    open fun getDeclaredType(identifier: String): CompoundData?{
        return getEnum(identifier) ?: getTemplate(identifier) ?: getInterface(identifier)
    }

    open fun addDeclaredType(type: CompoundData): Boolean {
        val qwq = when (type) {
            is Enum -> addEnum(type.identifier, type)

            is DataTemplate -> addTemplate(type.identifier, type)

            is Interface -> addInterface(type.identifier, type)

            else -> throw IllegalArgumentException("Unknown type: $type")
        }
        putType(type.identifier, type.getType())
        return qwq
    }

    override fun putType(key: String, type: MCFPPType, forced: Boolean): Boolean {
        return when (type) {
            is MCFPPEnumType -> addEnum(type.enum.identifier,  type.enum, forced)

            is MCFPPDataTemplateType -> addTemplate(type.template.identifier, type.template, forced)

            is MCFPPInterfaceType -> addInterface(type.i.identifier, type.i, forced)

            is MCFPPTypeAliasType -> {
                if (forced) {
                    typeAlias[key] = type
                }else{
                    if(!typeAlias.containsKey(key)){
                        typeAlias[key] = type
                        return false
                    }
                    typeAlias[key] = type
                }
                return true
            }

            else -> throw IllegalArgumentException("Unknown type: $type")
        }
    }

    override fun getType(key: String): MCFPPType? {
        return (getEnum(key) ?: getTemplate(key) ?: getInterface(key))?.getType()?: typeAlias[key]
    }

    override fun containType(id: String): Boolean {
        return hasEnum(id) || hasTemplate(id) || hasInterface(id) || typeAlias.containsKey(id)
    }

    override fun removeType(id: String): MCFPPType? {
        return when {
            hasEnum(id) -> removeEnum(id)
            hasTemplate(id) -> removeTemplate(id)
            hasInterface(id) -> removeInterface(id)
            else -> null
        }?.getType()?: typeAlias.remove(id)
    }

    override fun forEachType(action: (MCFPPType) -> Any?) {
        forEachEnum { action(it.getType()) }
        forEachTemplate { action(it.getType()) }
        forEachInterface { action(it.getType()) }
        typeAlias.values.forEach { action(it) }
    }

    override val allTypes: Collection<MCFPPType>
        get() {
            val list = mutableListOf<MCFPPType>()
            forEachEnum { list.add(it.getType()) }
            forEachTemplate { list.add(it.getType()) }
            forEachInterface { list.add(it.getType()) }
            list.addAll(typeAlias.values)
            return list
        }
}
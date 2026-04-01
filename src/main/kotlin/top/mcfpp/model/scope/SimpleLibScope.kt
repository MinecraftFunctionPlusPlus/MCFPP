package top.mcfpp.model.scope

import top.mcfpp.core.lang.Var
import top.mcfpp.model.annotation.Annotation
import top.mcfpp.model.compound.*
import top.mcfpp.model.compound.Enum
import top.mcfpp.model.function.Function
import top.mcfpp.type.*

open class SimpleLibScope
    : SimpleScopeWithFunction, SimpleScopeWithTemplate, SimpleScopeWithInterface,
    SimpleScopeWithEnum, SimpleScopeWithObject, SimpleScopeWithAnnotation, SimpleScopeWithVar,
    IScopeWithType
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
    final override var template: HashMap<String, DataTemplate> = HashMap()

    final override var genericTemplate: HashMap<Pair<String, List<MCFPPType>>, GenericDataTemplate> = HashMap()

    /**
     * 接口
     */
    final override var interfaces: HashMap<String, DataTemplate> = HashMap()

    final override var genericInterfaces: HashMap<Pair<String, List<MCFPPType>>, GenericDataTemplate> = HashMap()

    /**
     * 类型别名
     */
    protected var typeAlias: HashMap<String, MCFPPType> = HashMap()

    final override var enums: ArrayList<Enum> = ArrayList()

    final override var annotations: HashMap<String, Class<out Annotation>> = HashMap()

    final override var objects: ArrayList<CompoundData> = ArrayList()

    final override var genericObjects: HashMap<Pair<String, List<MCFPPType>>, GenericObjectDataTemplate> = HashMap()

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

    open fun hasDeclaredType(identifier: String): Boolean{
        return hasEnum(identifier) || hasTemplate(identifier) || hasInterface(identifier) || hasObject(identifier)
    }

    open fun hasDeclaredType(type: CompoundData): Boolean{
        val identifier = type.identifier
        return hasEnum(identifier) || hasTemplate(identifier) || hasInterface(identifier) || hasObject(identifier)
    }

    open fun getDeclaredType(identifier: String): CompoundData?{
        return getEnum(identifier) ?: getTemplate(identifier) ?: getInterface(identifier) ?: getObject(identifier)
    }

    open fun addDeclaredType(type: CompoundData): Boolean {
        val qwq = when (type) {
            is Enum -> addEnum(type.identifier, type)

            is DataTemplate -> {
                if(type is ObjectDataTemplate || type is GenericObjectDataTemplate){
                    addObject(type.identifier, type)
                }else if(!type.isInterface){
                    addTemplate(type.identifier, type)
                }else{
                    addInterface(type.identifier, type)
                }
            }

            else -> throw IllegalArgumentException("Unknown type: $type")
        }
        putType(type.identifier, type.getType())
        return qwq
    }

    override fun putType(key: String, type: MCFPPType, forced: Boolean): Boolean {
        return when (type) {
            is MCFPPEnumType -> addEnum(type.enum.identifier,  type.enum, forced)

            is MCFPPDataTemplateType -> {
                if(type.template is ObjectDataTemplate || type.template is GenericObjectDataTemplate){
                    addObject(type.template.identifier, type.template, forced)
                }else{
                    addTemplate(type.template.identifier, type.template, forced)
                }
            }

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
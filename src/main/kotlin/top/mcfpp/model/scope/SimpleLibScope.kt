package top.mcfpp.model.scope

import top.mcfpp.core.lang.Var
import top.mcfpp.model.annotation.Annotation
import top.mcfpp.model.compound.*
import top.mcfpp.model.compound.Enum
import top.mcfpp.model.function.Function
import top.mcfpp.type.*

open class SimpleLibScope
    : SimpleScopeWithFunction, SimpleScopeWithTemplate,
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
     * 类型别名
     */
    protected var typeAlias: HashMap<String, MCFPPType> = HashMap()

    /** Compiler journal slot. Reading this never resolves an alias or allocates a type. */
    internal var journalTypeAliases: HashMap<String, MCFPPType>
        get() = typeAlias
        set(value) { typeAlias = value }

    internal fun checkpointTypeAliases(): NamespaceRelationCheckpoint<MCFPPType> {
        check(javaClass in setOf<Class<*>>(SimpleLibScope::class.java, NamespaceScope::class.java, FileScope::class.java)) {
            "Namespace relation journal cannot capture scope ${javaClass.name}"
        }
        return NamespaceRelationJournal.map({ typeAlias }, { typeAlias = it })
    }

    final override var enums: ArrayList<Enum> = ArrayList()

    final override var annotations: HashMap<String, Class<out Annotation>> = HashMap()

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

    open fun hasDeclaredType(identifier: String): Boolean{
        return hasEnum(identifier) || hasTemplate(identifier) || hasObject(identifier)
    }

    open fun hasDeclaredType(type: CompoundData): Boolean{
        val identifier = type.identifier
        return hasEnum(identifier) || hasTemplate(identifier) || hasObject(identifier)
    }

    open fun getDeclaredType(identifier: String): CompoundData?{
        return getEnum(identifier) ?: getTemplate(identifier) ?: getObject(identifier)
    }

    open fun addDeclaredType(type: CompoundData): Boolean {
        val qwq = when (type) {
            is Enum -> addEnum(type.identifier, type)

            is DataTemplate -> {
                if(type is ObjectDataTemplate || type is GenericObjectDataTemplate){
                    addObject(type.identifier, type)
                }else{
                    addTemplate(type.identifier, type)
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

            is MCFPPTypeAliasType -> {
                if (!forced && containType(key)) return false
                typeAlias[key] = type
                return true
            }

            else -> throw IllegalArgumentException("Unknown type: $type")
        }
    }

    override fun getType(key: String): MCFPPType? {
        return (getEnum(key) ?: getTemplate(key))?.getType() ?: resolveAlias(key)
    }

    protected fun resolveAlias(key: String): MCFPPType? = typeAlias[key]?.let {
        if (it is MCFPPTypeAliasType) it.resolve(this) else it
    }

    fun resolveTypeAliases() {
        typeAlias.keys.toList().forEach { resolveAlias(it) }
    }

    internal fun cachedAliasTargets(): List<MCFPPType> = typeAlias.values.mapNotNull {
        if (it is MCFPPTypeAliasType) it.cachedTarget else it
    }

    /** Export resolved targets, never parser contexts or a second alias identity. */
    fun resolvedAliases(): Map<String, MCFPPType> = typeAlias.keys.mapNotNull { name ->
        resolveAlias(name)?.let { name to it }
    }.toMap()

    fun putResolvedAlias(name: String, target: MCFPPType, forced: Boolean = false): Boolean {
        if (!forced && containType(name)) return false
        typeAlias[name] = target
        return true
    }

    override fun containType(id: String): Boolean {
        return hasEnum(id) || hasTemplate(id) || typeAlias.containsKey(id)
    }

    override fun removeType(id: String): MCFPPType? {
        return when {
            hasEnum(id) -> removeEnum(id)
            hasTemplate(id) -> removeTemplate(id)
            else -> null
        }?.getType()?: typeAlias.remove(id)
    }

    override fun forEachType(action: (MCFPPType) -> Any?) {
        forEachEnum { action(it.getType()) }
        forEachTemplate { action(it.getType()) }
        typeAlias.values.forEach { action(it) }
    }

    override val allTypes: Collection<MCFPPType>
        get() {
            val list = mutableListOf<MCFPPType>()
            forEachEnum { list.add(it.getType()) }
            forEachTemplate { list.add(it.getType()) }
            list.addAll(typeAlias.values)
            return list
        }
}

/** Relation identities only. Element internals belong to declaration, object and storage journals. */
internal class NamespaceRelationCheckpoint<E : Any> internal constructor(
    elements: List<E>, private val restoreState: () -> Unit,
    private val recapture: () -> NamespaceRelationCheckpoint<E>
) {
    val elements: List<E> = java.util.Collections.unmodifiableList(ArrayList(elements))
    fun restore() = restoreState()
    fun captureCurrent(): NamespaceRelationCheckpoint<E> = recapture()
}

/** Keep former containers enrolled even after their owner points at a replacement. */
internal object NamespaceRelationJournal {
    fun <E : Any> list(current: () -> ArrayList<E>, replace: (ArrayList<E>) -> Unit,
                       accepts: (Any?) -> Boolean): NamespaceRelationCheckpoint<E> =
        list(current, replace, accepts, emptyList(), emptyList())

    private fun <E : Any> list(current: () -> ArrayList<E>, replace: (ArrayList<E>) -> Unit,
                              accepts: (Any?) -> Boolean, retained: List<ArrayList<E>>,
                              oldElements: List<E>): NamespaceRelationCheckpoint<E> {
        val original = current()
        val roots = identities(listOf(original) + retained)
        // Preflight all containers before touching any of their elements or invoking collection methods.
        roots.forEach { check(it.javaClass == java.util.ArrayList::class.java) {
            "Namespace relation journal cannot capture list ${it.javaClass.name}"
        } }
        val contents = roots.map { root ->
            val erased: List<*> = root
            check(erased.all(accepts)) { "Namespace relation journal found an unsupported list element" }
            root.toList()
        }
        val elements = identities(oldElements + contents.flatten())
        return NamespaceRelationCheckpoint(elements, {
            replace(original)
            roots.zip(contents).forEach { (root, values) -> root.clear(); root.addAll(values) }
        }, { list(current, replace, accepts, roots, elements) })
    }

    fun map(current: () -> HashMap<String, MCFPPType>, replace: (HashMap<String, MCFPPType>) -> Unit):
        NamespaceRelationCheckpoint<MCFPPType> = map(current, replace, emptyList(), emptyList())

    private fun map(current: () -> HashMap<String, MCFPPType>, replace: (HashMap<String, MCFPPType>) -> Unit,
                    retained: List<HashMap<String, MCFPPType>>, oldElements: List<MCFPPType>):
        NamespaceRelationCheckpoint<MCFPPType> {
        val original = current()
        val roots = identities(listOf(original) + retained)
        roots.forEach { check(it.javaClass in mapClasses) {
            "Namespace relation journal cannot capture map ${it.javaClass.name}"
        } }
        val contents = roots.map { root ->
            val erased: Map<*, *> = root
            // Validate erased keys before copying/rehashing; source types are opaque identity roots.
            check(erased.entries.all { it.key is String && it.value is MCFPPType }) {
                "Namespace relation journal found an unsupported alias entry"
            }
            LinkedHashMap(root)
        }
        val elements = identities(oldElements + contents.flatMap { it.values })
        return NamespaceRelationCheckpoint(elements, {
            replace(original)
            roots.zip(contents).forEach { (root, values) -> root.clear(); root.putAll(values) }
        }, { map(current, replace, roots, elements) })
    }

    private val mapClasses = setOf<Class<*>>(java.util.HashMap::class.java, java.util.LinkedHashMap::class.java)
    private fun <E : Any> identities(values: List<E>): List<E> {
        val seen = java.util.Collections.newSetFromMap(java.util.IdentityHashMap<E, Boolean>())
        return values.filter { seen.add(it) }
    }
}
